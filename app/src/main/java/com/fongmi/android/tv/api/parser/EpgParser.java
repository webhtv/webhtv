package com.fongmi.android.tv.api.parser;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.MergeMeta;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.setting.LiveEpgSetting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import org.simpleframework.xml.core.Persister;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import okhttp3.Request;
import okhttp3.Response;

/**
 * EPG 存储与加载。
 *
 * 存储设计（本次重构的核心）：
 *   物理上 = 一个数据文件 shards.dat（所有频道的节目单首尾相接）
 *          + 一个索引文件 shards.idx（频道名 → offset,length，纯文本）
 *   逻辑上 = 仍是「一个频道一段」，读取时只把该频道的段读进内存。
 *
 * 这样同时拿到两件事：
 *   · 不 OOM：任何时刻内存里只有「单个频道」的节目单，不会一次性反序列化全量。
 *   · 超快：1 次 open（原来 103 次）、顺序/大块读（原来 103 次随机小文件读）。
 *
 * 电视 eMMC 的随机小文件读极慢，这是原来 13~22s 的真正瓶颈；
 * 顺序读一个大文件则快得多。
 */
public class EpgParser {

    private static final String TAG = "EpgParser";
    public static final long UPDATE_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);
    public static final int KEEP_DAYS = 7;
    public static final String MERGED_META_NAME = "merged_meta.json";

    // 新版：单数据文件 + 偏移量索引
    public static final String DATA_FILE_NAME = "shards.dat";
    public static final String IDX_FILE_NAME = "shards.idx";

    private static final Gson GSON = new Gson();

    // 后台合并延迟：等首屏绑定跑完再动磁盘
    private static final long BACKGROUND_MERGE_DELAY_MS = 25000L;

    // 首屏绑定的自有截止时间（兜底）
    private static final long BIND_DEADLINE_MS = 40000L;

    // 首屏优先绑定的频道数：绑完这些就返回让 UI 刷新，其余后台补
    private static final int FAST_BIND_COUNT = 40;

    private static final boolean PARALLEL_BIND = true;
    private static final int MIN_PARALLEL_CHANNELS = 24;
    private static final int BIND_THREADS = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
    private static final ExecutorService BIND_EXECUTOR = Executors.newFixedThreadPool(BIND_THREADS, r -> {
        Thread t = new Thread(r, "epg-bind");
        t.setDaemon(true);
        return t;
    });

    /**
     * 保留：仅用于 meta 文件的读写互斥
     */
    public static final Object SYNC_LOCK = new Object();

    /**
     * 数据文件写入互斥（首次写入 / 后台合并 / 整理，都会整体重写 dat）
     */
    private static final Object DATA_LOCK = new Object();

    /**
     * 索引缓存：频道名 → {offset, length}
     */
    private static volatile Map<String, long[]> IDX_CACHE;
    private static final Object IDX_LOCK = new Object();

    /**
     * 分片内存缓存：频道名 → 节目单。命中后零 IO、零解析。
     * 一个频道的节目单通常几十 KB，几百个频道也就几 MB，不会 OOM。
     */
    private static final ConcurrentHashMap<String, List<Tv.Programme>> MEM =
            new ConcurrentHashMap<>();

    // parseFull 失败日志限流
    private static final AtomicInteger PARSE_ERR_COUNT = new AtomicInteger();

    // ==================================================================
    // 手写 TypeAdapter：去掉 Gson 对 Tv.Programme 的反射反序列化。
    // 反射来自 Gson 的 ReflectiveTypeAdapterFactory，与 Tv.java 的注解无关，
    // 所以 Tv.java 一个字都不用改。
    // ==================================================================
    private static final Field F_START;
    private static final Field F_STOP;
    private static final Field F_CHANNEL;
    private static final Field F_TITLE;
    private static final Field F_TITLE_TEXT;

    static {
        Field start = null, stop = null, channel = null, title = null, titleText = null;
        try {
            start = Tv.Programme.class.getDeclaredField("start");
            stop = Tv.Programme.class.getDeclaredField("stop");
            channel = Tv.Programme.class.getDeclaredField("channel");
            title = Tv.Programme.class.getDeclaredField("title");
            titleText = Tv.Title.class.getDeclaredField("text");
            start.setAccessible(true);
            stop.setAccessible(true);
            channel.setAccessible(true);
            title.setAccessible(true);
            titleText.setAccessible(true);
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "Programme字段初始化失败：" + e);
        }
        F_START = start;
        F_STOP = stop;
        F_CHANNEL = channel;
        F_TITLE = title;
        F_TITLE_TEXT = titleText;
    }

    private static void put(Field f, Object target, Object value) {
        if (f == null) return;
        try {
            f.set(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static Tv.Title newTitle(String text) {
        Tv.Title t = new Tv.Title();
        put(F_TITLE_TEXT, t, text);
        return t;
    }

    private static final TypeAdapter<Tv.Programme> PROGRAMME_ADAPTER = new TypeAdapter<Tv.Programme>() {

        @Override
        public void write(JsonWriter out, Tv.Programme p) throws java.io.IOException {
            if (p == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("start").value(p.getStart());
            out.name("stop").value(p.getStop());
            out.name("channel").value(p.getChannel());
            out.name("title");
            out.beginArray();
            out.beginObject();
            out.name("text").value(p.getTitle());
            out.endObject();
            out.endArray();
            out.endObject();
        }

        @Override
        public Tv.Programme read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            Tv.Programme p = new Tv.Programme();
            String start = null, stop = null, channel = null, title = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                if ("start".equals(name)) {
                    start = in.nextString();
                } else if ("stop".equals(name)) {
                    stop = in.nextString();
                } else if ("channel".equals(name)) {
                    channel = in.nextString();
                } else if ("title".equals(name)) {
                    in.beginArray();
                    while (in.hasNext()) {
                        in.beginObject();
                        while (in.hasNext()) {
                            if ("text".equals(in.nextName())) {
                                String v = in.nextString();
                                if (title == null && v != null && !v.isEmpty()) title = v;
                            } else {
                                in.skipValue();
                            }
                        }
                        in.endObject();
                    }
                    in.endArray();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            put(F_START, p, start == null ? "" : start);
            put(F_STOP, p, stop == null ? "" : stop);
            put(F_CHANNEL, p, channel == null ? "" : channel);
            if (title != null) {
                List<Tv.Title> list = new ArrayList<>(1);
                list.add(newTitle(title));
                put(F_TITLE, p, list);
            }
            return p;
        }
    };

    /**
     * 台名归一化：移除横杠、空白，转大写，解决 CCTV‑1 / CCTV1 匹配失败
     */
    private static String normalizeChannelName(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.trim().replaceAll("[-\\s]+", "").toUpperCase();
    }

    private static File getEpgCacheDir() {
        return new File(Path.files(), "epg");
    }

    private static File getDataFile() {
        return new File(getEpgCacheDir(), DATA_FILE_NAME);
    }

    private static File getIdxFile() {
        return new File(getEpgCacheDir(), IDX_FILE_NAME);
    }

    private static File getMergeMetaFile() {
        File dir = new File(Path.files(), "epg");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, MERGED_META_NAME);
    }

    private static File getCacheFile(String url) {
        return new File(getEpgCacheDir(), Util.md5(url) + ".xml");
    }

    private static boolean hasData() {
        File dat = getDataFile();
        File idx = getIdxFile();
        return dat.exists() && dat.length() > 0 && idx.exists();
    }

    /**
     * 返回「成功绑定 EPG 的频道数」，且全程不再向外抛异常。
     */
    public static int start(Live live) {
        if (live == null || live.getGroups().isEmpty()) return 0;
        int bindCount = 0;
        try {
            ZoneId zoneId = zoneIdOf(live.getTimeZone());
            MergeMeta meta = loadMergeMeta();
            boolean hasData = hasData();
            boolean moreDay = System.currentTimeMillis() - meta.lastMergeRun >= TimeUnit.HOURS.toMillis(24);
            if (!hasData || moreDay) {
                SpiderDebug.log(TAG, (!hasData ? "没有本地Epg数据" : "本地Epg数据已过期") + "，快速初始化并后台更新");
                FileUtil.deleteEpgDir();
                bindCount = buildInitialEpgAndSave(live, zoneId);
                startBackgroundMerge(live, zoneId);
            } else {
                SpiderDebug.log(TAG, "本地Epg数据未过期，正常加载");
                long t0 = System.currentTimeMillis();
                bindCount = loadMergedIndex(live, zoneId);
                SpiderDebug.log(TAG, "loadMergedIndex耗时=" + (System.currentTimeMillis() - t0) + "ms, bind=" + bindCount);
                long age = System.currentTimeMillis() - meta.lastMergeRun;
                if (meta.lastMergeRun == 0 || age > UPDATE_INTERVAL_MS) {
                    startBackgroundMerge(live, zoneId);
                }
            }
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "start()异常：" + e);
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            SpiderDebug.log(TAG, "start()堆栈：" + sw);
        }
        return bindCount;
    }

    // ==================================================================
    // 索引读写
    // ==================================================================

    private static Map<String, long[]> loadIdx() {
        Map<String, long[]> cached = IDX_CACHE;
        if (cached != null) return cached;
        synchronized (IDX_LOCK) {
            if (IDX_CACHE != null) return IDX_CACHE;
            Map<String, long[]> map = new HashMap<>();
            File f = getIdxFile();
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f);
                     BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8), 1 << 16)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        int p1 = line.indexOf('\t');
                        if (p1 <= 0) continue;
                        int p2 = line.indexOf('\t', p1 + 1);
                        if (p2 <= 0) continue;
                        try {
                            map.put(line.substring(0, p1),
                                    new long[]{Long.parseLong(line.substring(p1 + 1, p2)),
                                            Integer.parseInt(line.substring(p2 + 1))});
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } catch (Exception e) {
                    SpiderDebug.log(TAG, "索引读取失败：" + e);
                }
            }
            IDX_CACHE = map;
            return map;
        }
    }

    private static void writeIdx(Map<String, long[]> idx) throws Exception {
        File f = getIdxFile();
        File tmp = new File(f.getParent(), IDX_FILE_NAME + ".tmp");
        File parent = tmp.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(tmp);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8), 1 << 16)) {
            for (Map.Entry<String, long[]> e : idx.entrySet()) {
                long[] v = e.getValue();
                bw.write(e.getKey() + "\t" + v[0] + "\t" + v[1] + "\n");
            }
        }
        if (tmp.exists() && tmp.length() > 0) {
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } else if (tmp.exists()) {
            tmp.delete();
        }
        synchronized (IDX_LOCK) {
            IDX_CACHE = idx;
        }
    }

    /** 把一个频道的节目单序列化成字节（用于计算 offset 长度） */
    private static byte[] toJsonBytes(List<Tv.Programme> progs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(1024, progs.size() * 96));
        OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        JsonWriter w = new JsonWriter(osw);
        w.setIndent("");
        w.beginArray();
        for (Tv.Programme p : progs) PROGRAMME_ADAPTER.write(w, p);
        w.endArray();
        w.flush();
        osw.flush();
        return baos.toByteArray();
    }

    /** 从数据文件读取指定段（只有这一段进内存） */
    private static List<Tv.Programme> readRange(RandomAccessFile raf, long offset, int len) throws Exception {
        byte[] buf = new byte[len];
        raf.seek(offset);
        raf.readFully(buf);
        List<Tv.Programme> list = new ArrayList<>();
        JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(buf), StandardCharsets.UTF_8));
        reader.beginArray();
        while (reader.hasNext()) list.add(PROGRAMME_ADAPTER.read(reader));
        reader.endArray();
        return list;
    }

    /**
     * 全量重写数据文件 + 索引。
     * 内存峰值 = 单个频道的节目单，不会 OOM。
     */
    private static void writeAllData(Map<String, List<Tv.Programme>> map, ZoneId zoneId) throws Exception {
        LocalDate keepFrom = LocalDate.now().minusDays(KEEP_DAYS);
        File dat = getDataFile();
        File tmp = new File(dat.getParent(), DATA_FILE_NAME + ".tmp." + Thread.currentThread().getId());
        File parent = tmp.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        Map<String, long[]> idx = new LinkedHashMap<>();
        long pos = 0;
        try (FileOutputStream fos = new FileOutputStream(tmp);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 1 << 16)) {
            for (Map.Entry<String, List<Tv.Programme>> entry : map.entrySet()) {
                Set<String> seen = new HashSet<>();
                List<Tv.Programme> out = new ArrayList<>();
                collectByDate(entry.getValue(), zoneId, keepFrom, seen, out, true, entry.getKey());
                if (out.isEmpty()) continue;
                byte[] bytes = toJsonBytes(out);
                bos.write(bytes);
                idx.put(entry.getKey(), new long[]{pos, bytes.length});
                pos += bytes.length;
            }
        }
        synchronized (DATA_LOCK) {
            if (dat.exists()) dat.delete();
            tmp.renameTo(dat);
        }
        writeIdx(idx);
        // 丢弃已不存在的频道缓存
        MEM.keySet().retainAll(idx.keySet());
    }

    /**
     * 把某个源的数据合并进数据文件（整体重写，但内存只占单频道）。
     * sourceByName 为 null 时等价于「仅做过期整理」。
     */
    private static void mergeIntoData(Map<String, List<Tv.Programme>> sourceByName, ZoneId zoneId) throws Exception {
        LocalDate keepFrom = LocalDate.now().minusDays(KEEP_DAYS);
        Map<String, long[]> oldIdx = loadIdx();
        File dat = getDataFile();
        File newTmp = new File(dat.getParent(), DATA_FILE_NAME + ".tmp.m." + Thread.currentThread().getId());

        TreeSet<String> names = new TreeSet<>();
        names.addAll(oldIdx.keySet());
        if (sourceByName != null) names.addAll(sourceByName.keySet());

        Map<String, long[]> newIdx = new LinkedHashMap<>();
        long pos = 0;
        RandomAccessFile raf = null;
        try (FileOutputStream fos = new FileOutputStream(newTmp);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 1 << 16)) {
            if (dat.exists() && !oldIdx.isEmpty()) {
                try {
                    raf = new RandomAccessFile(dat, "r");
                } catch (Exception e) {
                    SpiderDebug.log(TAG, "打开旧数据文件失败：" + e);
                }
            }
            for (String name : names) {
                List<Tv.Programme> old = new ArrayList<>();
                long[] p = oldIdx.get(name);
                if (raf != null && p != null) {
                    try {
                        old = readRange(raf, p[0], (int) p[1]);
                    } catch (Exception e) {
                        SpiderDebug.log(TAG, "读取旧段失败 " + name + "：" + e);
                    }
                }
                List<Tv.Programme> neu = sourceByName == null ? null : sourceByName.get(name);
                Set<String> seen = new HashSet<>();
                List<Tv.Programme> out = new ArrayList<>();
                if (!old.isEmpty()) collectByDate(old, zoneId, keepFrom, seen, out, true, name);
                if (neu != null && !neu.isEmpty()) collectByDate(neu, zoneId, keepFrom, seen, out, false, name);
                if (out.isEmpty()) {
                    MEM.remove(name);
                    continue;
                }
                byte[] bytes = toJsonBytes(out);
                bos.write(bytes);
                newIdx.put(name, new long[]{pos, bytes.length});
                pos += bytes.length;
                MEM.put(name, out);
            }
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {
                }
            }
        }
        synchronized (DATA_LOCK) {
            if (dat.exists()) dat.delete();
            newTmp.renameTo(dat);
        }
        writeIdx(newIdx);
        MEM.keySet().retainAll(newIdx.keySet());
    }

    private static byte[] readAllBytes(File f) throws Exception {
        int len = (int) f.length();
        byte[] buf = new byte[len];
        try (FileInputStream fis = new FileInputStream(f);
             BufferedInputStream bis = new BufferedInputStream(fis, 1 << 16)) {
            int off = 0;
            while (off < len) {
                int r = bis.read(buf, off, len - off);
                if (r < 0) break;
                off += r;
            }
        }
        return buf;
    }

    private static List<Tv.Programme> parseProgrammes(byte[] bytes) throws Exception {
        List<Tv.Programme> list = new ArrayList<>();
        JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        reader.beginArray();
        while (reader.hasNext()) list.add(PROGRAMME_ADAPTER.read(reader));
        reader.endArray();
        return list;
    }

    // ==================================================================
    // 首次加载 / 后台合并
    // ==================================================================

    private static int buildInitialEpgAndSave(Live live, ZoneId zoneId) {
        List<String> urls = LiveEpgSetting.getXmlUrls(live);
        if (urls.isEmpty()) return 0;
        for (String url : urls) {
            try {
                SpiderDebug.log(TAG, "首次尝试源：" + url);
                File cacheFile = getCacheFile(url);
                if (!download(url, cacheFile)) {
                    SpiderDebug.log(TAG, "首次下载源失败：" + url);
                    continue;
                }
                Map<String, List<Tv.Programme>> indexMap = parseSourceByName(cacheFile, zoneId);
                synchronized (DATA_LOCK) {
                    writeAllData(indexMap, zoneId);
                }
                synchronized (SYNC_LOCK) {
                    MergeMeta meta = loadMergeMeta();
                    String urlMd5 = Util.md5(url);
                    String remoteEtag = fetchRemoteTag(url);
                    MergeMeta.SourceItem sourceItem = new MergeMeta.SourceItem();
                    sourceItem.etag = remoteEtag;
                    meta.sources.put(urlMd5, sourceItem);
                    meta.lastMergeRun = System.currentTimeMillis();
                    saveMergeMeta(meta);
                }
                int n = loadMergedIndex(live, zoneId);
                SpiderDebug.log(TAG, "首次加载Epg成功并持久化, source=" + url + ", bind=" + n);
                return n;
            } catch (Exception e) {
                SpiderDebug.log(TAG, "首次加载源失败 url=" + url + "：" + e);
            }
        }
        return 0;
    }

    private static void startBackgroundMerge(Live live, ZoneId zoneId) {
        new Thread(() -> {
            try {
                Thread.sleep(BACKGROUND_MERGE_DELAY_MS);
            } catch (InterruptedException e) {
                SpiderDebug.log(TAG, "启动后台更新延迟出错：" + e);
                return;
            }
            boolean updated = syncEpgSourcesInternal(live, zoneId);
            if (updated) {
                synchronized (SYNC_LOCK) {
                    try {
                        MergeMeta meta = loadMergeMeta();
                        meta.lastMergeRun = System.currentTimeMillis();
                        saveMergeMeta(meta);
                        SpiderDebug.log(TAG, "远程Epg数据已更新至本地。");
                    } catch (Exception e) {
                        SpiderDebug.log(TAG, "更新epg数据异常" + e);
                    }
                }
            }
        }).start();
    }

    private static boolean syncEpgSourcesInternal(Live live, ZoneId zoneId) {
        if (live == null || live.getGroups().isEmpty()) {
            SpiderDebug.log(TAG, "Epg远程更新时Live为空！");
            return false;
        }
        List<String> urls = LiveEpgSetting.getXmlUrls(live);
        if (urls.isEmpty()) return false;
        MergeMeta meta = loadMergeMeta();
        boolean needMerge = false;

        SpiderDebug.log(TAG, "准备Epg远程更新...");
        for (String url : urls) {
            try {
                String urlMd5 = Util.md5(url);
                String remoteEtag = fetchRemoteTag(url);
                MergeMeta.SourceItem sourceItem = meta.sources.get(urlMd5);
                if (sourceItem != null && remoteEtag != null && remoteEtag.equals(sourceItem.etag)) {
                    continue;
                }
                SpiderDebug.log(TAG, "远程Epg有变化：" + url);
                File cacheFile = getCacheFile(url);
                if (!download(url, cacheFile)) continue;

                Map<String, List<Tv.Programme>> sourceByName = parseSourceByName(cacheFile, zoneId);
                synchronized (DATA_LOCK) {
                    mergeIntoData(sourceByName, zoneId);
                }

                if (sourceItem == null) sourceItem = new MergeMeta.SourceItem();
                sourceItem.etag = remoteEtag;
                meta.sources.put(urlMd5, sourceItem);
                needMerge = true;
                if (cacheFile.exists()) cacheFile.delete();
            } catch (Exception e) {
                SpiderDebug.log(TAG, "后台更新Epg出错 url=" + url + "：" + e);
            }
        }

        if (!needMerge) {
            SpiderDebug.log(TAG, "远程Epg数据无变化，未更新");
            cleanStaleEpgCache(meta, urls);
            return false;
        }
        cleanStaleEpgCache(meta, urls);
        return true;
    }

    /**
     * 解析单个 EPG 源文件，得到 归一化频道名→该频道节目单列表。
     * 仅持有该源的数据，内存峰值 = 单个源。
     */
    private static Map<String, List<Tv.Programme>> parseSourceByName(File cacheFile, ZoneId zoneId) throws Exception {
        InputStream rawIn = null;
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        try {
            rawIn = new BufferedInputStream(new FileInputStream(cacheFile));
            byte[] header = new byte[2];
            rawIn.mark(2);
            int readCnt = rawIn.read(header);
            if (readCnt == 2 && (header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
                rawIn.close();
                rawIn = new GZIPInputStream(new BufferedInputStream(new FileInputStream(cacheFile)));
            } else {
                rawIn.reset();
            }
            parser.setInput(rawIn, "UTF-8");

            Tv tv = new Tv();
            setField(tv, "channel", new ArrayList<>());
            setField(tv, "programme", new ArrayList<>());

            Tv.Channel currentChannel = null;
            Tv.DisplayName currentDisplayName = null;
            Tv.Programme currentProg = null;
            Tv.Title currentTitle = null;
            StringBuilder textBuf = new StringBuilder();
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        textBuf.setLength(0);
                        if ("tv".equals(tagName)) {
                            setField(tv, "date", parser.getAttributeValue(null, "date"));
                        } else if ("channel".equals(tagName)) {
                            currentChannel = new Tv.Channel();
                            setField(currentChannel, "id", parser.getAttributeValue(null, "id"));
                            setField(currentChannel, "displayName", new ArrayList<>());
                        } else if ("display-name".equals(tagName)) {
                            currentDisplayName = new Tv.DisplayName();
                        } else if ("programme".equals(tagName)) {
                            currentProg = new Tv.Programme();
                            setField(currentProg, "start", parser.getAttributeValue(null, "start"));
                            setField(currentProg, "stop", parser.getAttributeValue(null, "stop"));
                            setField(currentProg, "channel", parser.getAttributeValue(null, "channel"));
                            setField(currentProg, "title", new ArrayList<>());
                        } else if ("title".equals(tagName)) {
                            currentTitle = new Tv.Title();
                        }
                        break;
                    case XmlPullParser.TEXT:
                        textBuf.append(parser.getText());
                        break;
                    case XmlPullParser.END_TAG:
                        String text = textBuf.toString().trim();
                        if ("display-name".equals(tagName) && currentChannel != null && currentDisplayName != null) {
                            setField(currentDisplayName, "text", text);
                            @SuppressWarnings("unchecked")
                            List<Tv.DisplayName> dnList = (List<Tv.DisplayName>) fieldOf(currentChannel, "displayName").get(currentChannel);
                            dnList.add(currentDisplayName);
                            currentDisplayName = null;
                        } else if ("channel".equals(tagName) && tv != null && currentChannel != null) {
                            tv.getChannel().add(currentChannel);
                            currentChannel = null;
                        } else if ("title".equals(tagName) && currentProg != null && currentTitle != null) {
                            setField(currentTitle, "text", text);
                            @SuppressWarnings("unchecked")
                            List<Tv.Title> titleList = (List<Tv.Title>) fieldOf(currentProg, "title").get(currentProg);
                            titleList.add(currentTitle);
                            currentTitle = null;
                        } else if ("programme".equals(tagName) && tv != null && currentProg != null) {
                            tv.getProgramme().add(currentProg);
                            currentProg = null;
                        }
                        break;
                }
                eventType = parser.next();
            }

            Map<String, String> localXmlToBizName = new HashMap<>();
            for (Tv.Channel ch : tv.getChannel()) {
                String rawBizName = null;
                @SuppressWarnings("unchecked")
                List<Tv.DisplayName> dnList = (List<Tv.DisplayName>) fieldOf(ch, "displayName").get(ch);
                for (Tv.DisplayName dn : dnList) {
                    String txt = dn.getText().trim();
                    if (!txt.isEmpty()) {
                        rawBizName = txt;
                        break;
                    }
                }
                if (rawBizName != null && !rawBizName.isEmpty()) {
                    localXmlToBizName.put(ch.getId(), normalizeChannelName(rawBizName));
                }
            }

            Map<String, List<Tv.Programme>> sourceByName = new HashMap<>();
            for (Tv.Programme p : tv.getProgramme()) {
                String bizName = localXmlToBizName.get(p.getChannel());
                if (bizName == null) continue;
                sourceByName.computeIfAbsent(bizName, k -> new ArrayList<>()).add(p);
            }
            return sourceByName;
        } finally {
            if (rawIn != null) rawIn.close();
        }
    }

    private static Field fieldOf(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void collectByDate(List<Tv.Programme> programmes, ZoneId zoneId, LocalDate keepFrom,
                                      Set<String> seen, List<Tv.Programme> out, boolean takeExisting, String bizName) {
        for (Tv.Programme p : programmes) {
            OffsetDateTime start = parseFull(p.getStart(), zoneId);
            if (start.isEqual(Instant.EPOCH.atOffset(ZoneOffset.UTC))) continue;
            LocalDate progDate = start.atZoneSameInstant(zoneId).toLocalDate();
            if (progDate.isBefore(keepFrom)) continue;
            if (takeExisting) {
                out.add(p);
                seen.add(bizName + "|" + progDate.format(Formatters.DATE));
            } else {
                if (!seen.contains(bizName + "|" + progDate.format(Formatters.DATE))) out.add(p);
            }
        }
    }

    private static void cleanStaleEpgCache(MergeMeta meta, List<String> validUrls) {
        File cacheDir = getEpgCacheDir();
        File[] allFiles = cacheDir.listFiles();
        if (allFiles == null) return;
        Set<String> validUrlMd5Set = new HashSet<>();
        for (String url : validUrls) validUrlMd5Set.add(Util.md5(url));
        for (File f : allFiles) {
            String name = f.getName();
            if (name.equals(MERGED_META_NAME) || name.equals(DATA_FILE_NAME) || name.equals(IDX_FILE_NAME) || name.endsWith(".tmp")) {
                continue;
            }
            if (f.isDirectory()) continue;
            boolean keep = false;
            for (String md5 : validUrlMd5Set) {
                if (name.startsWith(md5)) {
                    keep = true;
                    break;
                }
            }
            if (!keep) f.delete();
        }
    }

    private static MergeMeta loadMergeMeta() {
        File metaFile = getMergeMetaFile();
        MergeMeta meta = new MergeMeta();
        if (metaFile.exists()) {
            synchronized (SYNC_LOCK) {
                try (FileInputStream fis = new FileInputStream(metaFile);
                     BufferedInputStream bis = new BufferedInputStream(fis);
                     InputStreamReader isr = new InputStreamReader(bis, StandardCharsets.UTF_8);
                     JsonReader reader = new JsonReader(isr)) {
                    meta = GSON.fromJson(reader, MergeMeta.class);
                } catch (Exception e) {
                    SpiderDebug.log(TAG, "meta文件解析损坏，使用空meta：" + e);
                }
            }
        }
        return meta;
    }

    private static void saveMergeMeta(MergeMeta meta) {
        synchronized (SYNC_LOCK) {
            try {
                File metaFile = getMergeMetaFile();
                File tempFile = new File(metaFile.getParent(), MERGED_META_NAME + ".tmp");
                File parent = tempFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos);
                     OutputStreamWriter osw = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
                     JsonWriter writer = new JsonWriter(osw)) {
                    GSON.toJson(meta, MergeMeta.class, writer);
                    writer.flush();
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (metaFile.exists()) metaFile.delete();
                    tempFile.renameTo(metaFile);
                } else if (tempFile.exists()) {
                    tempFile.delete();
                }
            } catch (Exception e) {
                SpiderDebug.log(TAG, "保存meta出错：" + e);
            }
        }
    }

    private static boolean download(String url, File file) {
        try {
            com.fongmi.android.tv.utils.Download.create(url, file).get();
            return file.exists() && file.length() > 0;
        } catch (Exception e) {
            SpiderDebug.log(TAG, url + "下载出错：" + e);
            return false;
        }
    }

    private static String fetchRemoteTag(String url) {
        try {
            Request request = new Request.Builder().url(url).head().build();
            try (Response res = OkHttp.client().newCall(request).execute()) {
                if (!res.isSuccessful()) return null;
                String etag = res.header(HttpHeaders.ETAG);
                if (etag != null && !etag.isEmpty()) return etag;
                String lastModified = res.header(HttpHeaders.LAST_MODIFIED);
                if (lastModified != null && !lastModified.isEmpty()) return lastModified;
                String contentLength = res.header(HttpHeaders.CONTENT_LENGTH);
                return contentLength != null ? contentLength : String.valueOf(System.currentTimeMillis());
            }
        } catch (InterruptedIOException e) {
            SpiderDebug.log(TAG, url + "获取etag被中断：" + e.getMessage());
            Thread.interrupted();
            return null;
        } catch (Exception e) {
            SpiderDebug.log(TAG, url + "获取etag出错：" + e);
            return null;
        }
    }

    private static String readCacheContent(File file, String url) throws Exception {
        byte[] bytes = Path.readToByte(file);
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            File xml = new File(getEpgCacheDir(), file.getName() + ".xml");
            try {
                FileUtil.gzipDecompress(file, xml);
                bytes = Path.readToByte(xml);
                if (bytes == null || bytes.length == 0) return "";
            } finally {
                xml.delete();
            }
        }
        if (bytes.length == 0) return "";
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.isEmpty()) return "";
        if (content.charAt(0) == '\uFEFF') content = content.substring(1);
        String head = content.trim();
        if (head.startsWith("<!DOCTYPE html") || head.startsWith("<html") || head.startsWith("<HTML")) {
            SpiderDebug.log(TAG, url + "缓存内容是html，弃用");
            return "";
        }
        return content;
    }

    private static Tv parseTv(String content) throws Exception {
        return new Persister().read(Tv.class, content, false);
    }

    // ==================================================================
    // 绑定：把数据文件里的节目单挂到 Channel 上
    // ==================================================================

    private static List<Channel> snapshotChannels(Live live) {
        List<Channel> all = new ArrayList<>();
        for (int attempt = 0; attempt < 3; attempt++) {
            all.clear();
            try {
                for (Group group : new ArrayList<>(live.getGroups())) {
                    if (group == null) continue;
                    List<Channel> chs = group.getChannel();
                    if (chs == null || chs.isEmpty()) continue;
                    all.addAll(new ArrayList<>(chs));
                }
                return all;
            } catch (ConcurrentModificationException e) {
                SpiderDebug.log(TAG, "快照groups时并发修改，重试 " + (attempt + 1));
                try {
                    Thread.sleep(30);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } catch (Throwable e) {
                SpiderDebug.log(TAG, "快照groups异常：" + e);
                break;
            }
        }
        return all;
    }

    private static String normKeyOf(Channel ch) {
        if (ch == null) return "";
        String key = ch.getTvgName();
        if (key == null || key.isEmpty()) key = ch.getName();
        if (key == null || key.isEmpty()) return "";
        return normalizeChannelName(key.trim());
    }

    private static int loadMergedIndex(Live live, ZoneId zoneId) {
        if (!hasData()) return 0;
        List<Channel> channels = snapshotChannels(live);
        if (channels.isEmpty()) {
            SpiderDebug.log(TAG, "快照频道为空，无法绑定");
            return 0;
        }

        // 首屏优先：只等前 FAST_BIND_COUNT 个频道，绑完立刻返回让 UI 刷新
        int fastEnd = Math.min(channels.size(), FAST_BIND_COUNT);
        int bindCount = fastEnd > 0 ? bindRange(channels, 0, fastEnd, zoneId) : 0;

        // 剩余频道后台补（不取消、不中断），顺便把 MEM 预热好，下次进入就是毫秒级
        if (fastEnd < channels.size()) {
            final int restFrom = fastEnd;
            final int restTo = channels.size();
            try {
                BIND_EXECUTOR.submit(() -> {
                    int n = bindRange(channels, restFrom, restTo, zoneId);
                    SpiderDebug.log(TAG, "后台补绑完成 " + restFrom + "~" + restTo + " 共" + n);
                    return n;
                });
            } catch (Throwable e) {
                SpiderDebug.log(TAG, "后台补绑提交失败：" + e);
            }
        }
        SpiderDebug.log(TAG, "loadMergedIndex首屏完成，频道总数=" + channels.size() + " 首屏绑定=" + bindCount);
        return bindCount;
    }

    /**
     * 并行绑定 [from, to)：
     *   1. 先查 MEM，命中的直接用（零 IO）
     *   2. 未命中的按 offset 升序排序后分段读 —— 段内是顺序读，对 eMMC 极友好
     *   3. 读到的回填 MEM
     *   4. 最后统一挂到 Channel 上
     */
    private static int bindRange(List<Channel> channels, int from, int to, ZoneId zoneId) {
        int size = to - from;
        if (size <= 0) return 0;

        Map<String, long[]> idx = loadIdx();
        Map<String, List<Channel>> byName = new LinkedHashMap<>();
        for (int i = from; i < to; i++) {
            Channel ch = channels.get(i);
            if (ch == null) continue;
            String key = normKeyOf(ch);
            if (key.isEmpty()) continue;
            byName.computeIfAbsent(key, k -> new ArrayList<>(2)).add(ch);
        }
        if (byName.isEmpty()) return 0;

        List<String> miss = new ArrayList<>();
        for (String key : byName.keySet()) if (!MEM.containsKey(key)) miss.add(key);

        if (!miss.isEmpty()) {
            final File dat = getDataFile();
            miss.sort((a, b) -> {
                long[] pa = idx.get(a);
                long[] pb = idx.get(b);
                if (pa == null) return 1;
                if (pb == null) return -1;
                return Long.compare(pa[0], pb[0]);
            });
            int parts = Math.min(BIND_THREADS, miss.size());
            int per = (miss.size() + parts - 1) / parts;
            List<Future<?>> futures = new ArrayList<>(parts);
            for (int s = 0; s < miss.size(); s += per) {
                final int a = s;
                final int b = Math.min(s + per, miss.size());
                try {
                    futures.add(BIND_EXECUTOR.submit((Callable<Integer>) () -> {
                        int n = 0;
                        // 每个任务只 open 一次，段内顺序读
                        try (RandomAccessFile raf = new RandomAccessFile(dat, "r")) {
                            for (int i = a; i < b; i++) {
                                String key = miss.get(i);
                                long[] p = idx.get(key);
                                if (p == null) continue;
                                try {
                                    MEM.put(key, readRange(raf, p[0], (int) p[1]));
                                    n++;
                                } catch (Exception e) {
                                    SpiderDebug.log(TAG, "读取段失败 " + key + "：" + e);
                                }
                            }
                        } catch (Exception e) {
                            SpiderDebug.log(TAG, "打开数据文件失败：" + e);
                        }
                        return n;
                    }));
                } catch (Throwable t) {
                    SpiderDebug.log(TAG, "绑定任务提交失败：" + t);
                }
            }
            long deadline = System.currentTimeMillis() + BIND_DEADLINE_MS;
            boolean swallowInterrupt = false;
            for (Future<?> f : futures) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                try {
                    f.get(remain, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // 外部超时中断：不取消任务，让它把 MEM 预热完
                    swallowInterrupt = true;
                } catch (java.util.concurrent.TimeoutException e) {
                    SpiderDebug.log(TAG, "绑定到达截止时间，先返回已读到的部分");
                    break;
                } catch (Throwable e) {
                    SpiderDebug.log(TAG, "绑定任务异常：" + e);
                }
            }
            if (swallowInterrupt) {
                SpiderDebug.log(TAG, "已吞掉外部中断标志，避免污染线程池中的复用线程");
            }
        }

        int count = 0;
        for (Map.Entry<String, List<Channel>> e : byName.entrySet()) {
            List<Tv.Programme> progs = MEM.get(e.getKey());
            if (progs == null || progs.isEmpty()) continue;
            for (Channel ch : e.getValue()) {
                try {
                    if (bindChannel(ch, e.getKey(), progs, zoneId)) count++;
                } catch (Throwable t) {
                    SpiderDebug.log(TAG, "bindChannel异常：" + t);
                }
            }
        }
        return count;
    }

    private static boolean bindChannel(Channel ch, String normLookup, List<Tv.Programme> progList, ZoneId zoneId) {
        List<Epg> epgList = new ArrayList<>();
        Map<String, Epg> dateGroup = new LinkedHashMap<>();
        for (Tv.Programme programme : progList) {
            OffsetDateTime startDate = parseFull(programme.getStart(), zoneId);
            OffsetDateTime endDate = parseFull(programme.getStop(), zoneId);
            if (startDate.isEqual(Instant.EPOCH.atOffset(ZoneOffset.UTC)) || endDate.isEqual(Instant.EPOCH.atOffset(ZoneOffset.UTC))) {
                continue;
            }
            String dateStr = startDate.atZoneSameInstant(zoneId).format(Formatters.DATE);
            Epg epg = dateGroup.get(dateStr);
            if (epg == null) {
                epg = Epg.create(normLookup, dateStr);
                dateGroup.put(dateStr, epg);
            }
            // 复用已解析的 startDate / endDate，不再二次 parseFull
            epg.getList().add(getEpgData(startDate, endDate, zoneId, programme));
        }
        epgList.addAll(dateGroup.values());
        ch.setDataList(epgList);
        return true;
    }

    public static Epg getEpg(String xml, String key, ZoneId zoneId) {
        try {
            String content = sanitizeXml(xml);
            if (content.isEmpty()) return new Epg();
            Tv tv = parseTv(content);
            String rawDate = tv.getDate();
            String date = rawDate.isEmpty() ? LocalDate.now(zoneId).format(Formatters.DATE) : parseFull(rawDate, zoneId).atZoneSameInstant(zoneId).format(Formatters.DATE);
            Epg epg = Epg.create(key, date);
            for (Tv.Programme programme : tv.getProgramme()) {
                OffsetDateTime startDate = parseFull(programme.getStart(), zoneId);
                OffsetDateTime endDate = parseFull(programme.getStop(), zoneId);
                if (startDate.isEqual(Instant.EPOCH.atOffset(ZoneOffset.UTC)) || endDate.isEqual(Instant.EPOCH.atOffset(ZoneOffset.UTC))) {
                    continue;
                }
                epg.getList().add(getEpgData(programme, zoneId));
            }
            return epg;
        } catch (Exception e) {
            SpiderDebug.log(TAG, "获取Epg数据失败 key=" + key + "：" + e);
            return new Epg();
        }
    }

    private static String sanitizeXml(String xml) {
        if (xml == null || xml.isEmpty()) return "";
        String s = xml;
        if (s.charAt(0) == '\uFEFF') s = s.substring(1);
        String head = s.trim();
        if (head.startsWith("<!DOCTYPE html") || head.startsWith("<html") || head.startsWith("<HTML")) return "";
        return s;
    }

    private static EpgData getEpgData(Tv.Programme programme, ZoneId zoneId) {
        OffsetDateTime startDate = parseFull(programme.getStart(), zoneId);
        OffsetDateTime endDate = parseFull(programme.getStop(), zoneId);
        return getEpgData(startDate, endDate, zoneId, programme);
    }

    private static EpgData getEpgData(OffsetDateTime startDate, OffsetDateTime endDate, ZoneId zoneId, Tv.Programme programme) {
        try {
            EpgData data = new EpgData();
            data.setTitle(programme.getTitle());
            data.setStart(startDate.atZoneSameInstant(zoneId).format(Formatters.TIME));
            data.setEnd(endDate.atZoneSameInstant(zoneId).format(Formatters.TIME));
            data.setStartTime(startDate.toInstant().toEpochMilli());
            data.setEndTime(endDate.toInstant().toEpochMilli());
            data.trans();
            return data;
        } catch (Exception e) {
            SpiderDebug.log(TAG, "getEpgData出错：" + e);
            return new EpgData();
        }
    }

    private static ZoneId zoneIdOf(String tz) {
        if (tz == null || tz.isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(tz);
        } catch (Exception ignored) {
            return ZoneId.systemDefault();
        }
    }

    private static OffsetDateTime parseFull(String source, ZoneId zoneId) {
        String s = source.trim();
        int len = s.length();
        try {
            if (len >= 20)
                return OffsetDateTime.parse(s, s.charAt(len - 3) == ':' ? Formatters.EPG_FULL_COLON : Formatters.EPG_FULL);
            return java.time.LocalDateTime.parse(len > 14 ? s.substring(0, 14) : s, Formatters.EPG_FULL_NO_TZ).atZone(zoneId).toOffsetDateTime();
        } catch (Exception e) {
            // 十万级调用，失败日志限流，避免日志 IO 拖慢首屏
            if (PARSE_ERR_COUNT.incrementAndGet() <= 3) {
                SpiderDebug.log(TAG, "parseFull出错 src=" + s + "：" + e);
            }
            return OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
