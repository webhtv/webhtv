package com.fongmi.android.tv.download;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 单个分片下载器：支持 HTTP Range 断点续传、自定义 headers，以及 HLS AES-128 分片解密。 */
public class SegmentDownloader {

    public interface Listener {
        void onProgress(DownloadTask.FileSegment seg, long bytesDownloaded);
        void onComplete(DownloadTask.FileSegment seg);
        void onError(DownloadTask.FileSegment seg, String msg);
    }

    private static final int BUF = 64 * 1024;

    public static void download(DownloadTask.FileSegment seg, Map<String, String> headers,
                                AtomicBoolean cancelled, Listener listener) {
        boolean createdNewFile = false;   // 本次调用是否新建了 .part 文件
        long written = 0;                 // 本次调用实际写入的字节数
        try {
            // 已被作废的一轮（暂停/取消之后才轮到执行）：连文件都不要创建，否则就是 0B 垃圾分片
            if (cancelled != null && cancelled.get()) return;

            File part = new File(seg.partFile);
            boolean encrypted = seg.keyBytes != null && seg.keyBytes.length > 0 && seg.keyIv != null && seg.keyIv.length == 16;
            // 加密分片解密后长度会变化，按字节续传会错位，因此加密分片始终整片重下
            long resumeBytes = (!encrypted && part.exists()) ? part.length() : 0;
            if (encrypted && part.exists()) part.delete();
            long requestStart = seg.start + resumeBytes;
            long requestEnd = seg.end;

            // 续传起点已越过分片末尾（分片其实已完整，或 .part 因异常写入超长）：
            // 直接按完成处理。否则会退化成 "bytes=N-" 的开放式 Range，把分片后面的数据也拉下来。
            if (seg.end >= 0 && requestStart > seg.end) {
                seg.downloaded = seg.end - seg.start + 1;
                seg.done = true;
                if (listener != null) listener.onComplete(seg);
                return;
            }

            if (cancelled != null && cancelled.get()) throw new IOException("Cancelled");

            HttpURLConnection conn = (HttpURLConnection) new URL(seg.url).openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("GET");
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) conn.setRequestProperty(h.getKey(), h.getValue());
            }

            // 直链分片: end >= start 时用两端 Range；hls 分片: end = -1，用开区间
            if (requestEnd >= requestStart) {
                conn.setRequestProperty("Range", "bytes=" + requestStart + "-" + requestEnd);
            } else if (resumeBytes > 0) {
                conn.setRequestProperty("Range", "bytes=" + requestStart + "-");
            }
            conn.setRequestProperty("Accept-Encoding", "identity");  // 禁止 gzip，保证字节数准确

            int code = conn.getResponseCode();
            // 建连可能耗时几十秒，这里补一次中断检查，让暂停/取消尽快生效
            if (cancelled != null && cancelled.get()) throw new IOException("Cancelled");
            if (code == HttpURLConnection.HTTP_OK) {
                // 服务器不支持 Range；已有残留 part 则丢弃重下
                if (resumeBytes > 0) { resumeBytes = 0; part.delete(); }
            } else if (code == 416) {
                // Range Not Satisfiable —— 分片已完整才成立；本地没有任何数据时不能算完成，
                // 否则后续合并会因为缺少 .part 而失败
                if (!part.exists()) throw new IOException("Range not satisfiable and no local data: " + seg.url);
                // hls 分片 end = -1，此时用已落地 part 文件长度作为完成量
                seg.downloaded = seg.end >= 0 ? (seg.end - seg.start + 1) : part.length();
                seg.done = true;
                if (listener != null) listener.onComplete(seg);
                return;
            } else if (code != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("HTTP " + code);
            }

            long localDownloaded = resumeBytes;  // 本分片内已下载字节（不含全局起始偏移）
            byte[] buf = new byte[BUF];
            int read;
            // 先把第一块读出来：确认真的有数据再建 .part 文件。
            // 旧实现是先建文件再读，请求失败/被中断时就会留下大量 0B 分片。
            try (InputStream in = conn.getInputStream()) {
                read = in.read(buf);
                if (read <= 0) {
                    // 空响应：没有可续传内容就是失败（URL 失效/被拒），不要留下 0B 分片冒充成功
                    if (resumeBytes <= 0) throw new IOException("Empty response for " + seg.url);
                    read = 0;
                } else {
                    FileOutputStream fos = new FileOutputStream(part, resumeBytes > 0);
                    createdNewFile = resumeBytes <= 0;
                    try (BufferedOutputStream out = new BufferedOutputStream(fos, BUF)) {
                        out.write(buf, 0, read);
                        written += read;
                        localDownloaded += read;
                        seg.downloaded = localDownloaded;
                        if (listener != null) listener.onProgress(seg, localDownloaded);
                        while ((read = in.read(buf)) != -1) {
                            if (cancelled != null && cancelled.get()) throw new IOException("Cancelled");
                            out.write(buf, 0, read);
                            written += read;
                            localDownloaded += read;
                            seg.downloaded = localDownloaded;
                            if (listener != null) listener.onProgress(seg, localDownloaded);
                        }
                    }
                }
            }

            if (encrypted) {
                decrypt(part, seg.keyBytes, seg.keyIv);
                localDownloaded = part.length();
                seg.downloaded = localDownloaded;
            }
            seg.downloaded = (seg.end >= 0 && !encrypted) ? (seg.end - seg.start + 1) : localDownloaded;
            seg.done = true;
            if (listener != null) listener.onComplete(seg);
        } catch (Exception e) {
            if (seg.errorMsg == null) seg.errorMsg = e.getMessage();
            if (listener != null) listener.onError(seg, e.getMessage());
        } finally {
            // 本次新建、且一个字节都没写进去的 .part 直接删掉，杜绝 0B 分片堆积
            if (!seg.done && createdNewFile && written == 0) {
                try { new File(seg.partFile).delete(); } catch (Exception ignored) {}
            }
        }
    }

    /** HLS AES-128：CBC + PKCS7 填充，原地解密分片文件。 */
    private static void decrypt(File part, byte[] key, byte[] iv) throws Exception {
        byte[] data = new byte[(int) part.length()];
        try (FileInputStream in = new FileInputStream(part)) {
            int off = 0, r;
            while (off < data.length && (r = in.read(data, off, data.length - off)) > 0) off += r;
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] plain = cipher.doFinal(data);
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(plain);
        }
    }
}
