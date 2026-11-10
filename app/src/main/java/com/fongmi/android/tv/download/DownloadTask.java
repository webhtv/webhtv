package com.fongmi.android.tv.download;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 下载任务数据模型。纯 POJO，无 Android 依赖（方便单元测试）。 */
public class DownloadTask {
    public enum Type { DIRECT, HLS }  // 直链 / m3u8 分片
    public enum Status { PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

    public String id;            // UUID
    public String name;          // 文件名（不含扩展名）
    public Type type;
    public String url;           // 原始播放地址
    public Map<String, String> headers = new HashMap<>();  // Referer/UA/Cookie 等反爬 header
    public String ext = "mp4";   // 输出扩展名（直链: 原扩展名，hls: mp4）
    public FileSegment[] segments;  // 分片列表（直链为 N 个 Range 分片，hls 为 N 个 ts 分片）
    public long totalSize;       // 预计总大小（直链从 Content-Length 拿，hls 累加各分片大小）
    public long downloadedBytes; // 已下载总字节
    public int completedSegments; // 已完成分片数
    public int totalSegments;
    public Status status = Status.PENDING;
    public String errorMsg;
    public String partDirPath;   // 该任务的分片临时目录（.parts_<id>），取消时用于清理
    public String outputDir;     // 该任务的输出目录（创建时固化，之后修改全局设置不影响进行中的任务）

    public static class FileSegment {
        public int index;
        public String url;          // hls 时为绝对分片 URL，直链时 = 原始 url
        public long start;          // 直链: Range 起始字节；hls: 0
        public long end;            // 直链: Range 结束字节；hls: 分片大小（下载后填充）
        public long downloaded;     // 已下载字节（断点续传用）
        public String partFile;     // 分片临时文件路径
        public boolean done;
        public String errorMsg;
        public byte[] keyBytes;     // HLS AES-128 密钥（16 字节），无加密则 null
        public byte[] keyIv;        // 该分片的 AES-128 IV（16 字节），无加密则 null
    }
}
