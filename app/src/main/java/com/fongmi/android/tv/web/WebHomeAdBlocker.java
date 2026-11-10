package com.fongmi.android.tv.web;

import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * WebHome 网页广告过滤（原生层）。
 *
 * 【跨域的正确含义】
 * 指的是「点击后打开的新页面属于别的站点」，即 &lt;a href&gt; 指向外站，
 * 不是图片资源跨域。影视站的广告几乎必然是外跳链接。
 * JS 层（webhome-adblock.js）负责隐藏这些 &lt;a&gt; 及其容器，
 * 原生层负责 JS 挡不住的部分：location.href= 强制跳转、window.open 弹窗、
 * 以及按域名清单直接断掉广告资源请求。
 *
 * 三层分工：
 *   1. shouldOverrideUrlLoading —— 拦外跳导航（JS 阻止不了的重定向）
 *   2. onCreateWindow           —— 拦弹窗（广告最常用的手段）
 *   3. shouldInterceptRequest   —— 按广告域名清单断资源（含 &lt;img&gt;/&lt;script&gt;/&lt;iframe&gt;，
 *                                  这些是 WebView 直接发的，JS 完全看不到）
 *
 * 参考 hikerView FloatWebView：
 *   - 广告域名清单（实测 40 个国内影视站域名）
 *   - getCoreDomainFromUrl 核心域名算法（去 www/m/wap 前缀 + 复合后缀）
 */
public class WebHomeAdBlocker {

    private static volatile WebHomeAdBlocker instance;

    // ---------------- 广告域名清单 ----------------
    // 前 40 个来自 hikerView FloatWebView 的实测清单，覆盖国内影视站；
    // 后面补充通用广告/统计域名。
    // 判定用 contains，因此写 "googleads." 这种带点的前缀即可覆盖其子域。
    private static final String[] AD_DOMAINS = {
            // —— hikerView FloatWebView 实测清单 ——
            "doubleclick.net", "googleads.", "adnxs.com", "tencentads.", "baiduads.", "sinaads.",
            "m3u8.xiangodt.com", "6080kan.cc", "mimg.0c1q0l.cn", "www.googletagmanager.com",
            "mc.usihnbcq.cn", "mg.g1mm3d.cn", "mscs.svaeuzh.cn", "cnzz.hhttm.top", "tp.vinuxhome.com",
            "cnzz.mmstat.com", "www.baihuillq.com", "stj.v1vo.top", "img.mosflower.cn", "tips.gamevvip.com",
            "ehwe.yhdtns.com", "xdn.cqqc3.com", "www.jixunkyy.cn", "sp.chemacid.cn", "um.cavuc.com",
            "mav.mavuz.com", "wofwk.aoidf3.com", "xc.hubeijieshikj.cn", "tj.tianwenhu.com", "xg.gars57.cn",
            "k.jinxiuzhilv.com", "ppl.xunzhuo123.com", "xomk.jiangjunmh.top", "img.xunzhuo123.com",
            "xg.huataisangao.cn", "thy.dacedsfa.cn", "xg.whsbpw.cn", "f5w.as12df.top", "www.92424.cn",
            "k.wudejia.com", "vivovip.mmszxc.top", "qiu.xixiqiu.com", "cdnjs.hnfenxun.com", "cms.qdwght.com",

            // —— 通用广告 / 统计 / 追踪 ——
            "google-analytics.com", "googlesyndication.com", "googleadservices.com",
            "amazon-adsystem.com", "criteo.com", "pubmatic.com", "rubiconproject.com",
            "openx.net", "casalemedia.com", "appnexus.com", "quantserve.com",
            "scorecardresearch.com", "outbrain.com", "taboola.com", "mgid.com",
            "revcontent.com", "popads.net", "popcash.net", "propellerads.com",
            "adsterra.com", "juicyads.com", "exoclick.com", "hilltopads.net",
            "clickadu.com", "trafficjunky.net",
            "hm.baidu.com", "pos.baidu.com", "cbjs.baidu.com", "cpro.baidustatic.com",
            "cb.baidu.com", "tanx.com", "alimama.com", "mmstat.com",
            "union.sogou.com", "cnzz.com", "51.la", "talkingdata.com",
            "umeng.com", "sensorsdata.cn", "qstat.net", "union.jd.com",
            "gclick.cn", "adsame.com", "max.mobad.cn", "sdk.mobad.cn"
    };

    // 指向这些的文件直链不是外跳（海报大图 / 播放地址），不能拦
    private static final String[] MEDIA_EXT = {
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".svg", ".avif",
            ".mp4", ".m3u8", ".mpd", ".mkv", ".flv", ".webm", ".m4a", ".mp3",
            ".ts", ".avi", ".mov", ".rmvb"
    };

    private static final Set<String> MEDIA_EXT_SET = new HashSet<>(Arrays.asList(MEDIA_EXT));

    // 纯流媒体分片/播放列表：绝不可能是广告创意（一部片几百上千个 .ts）
    private static final Set<String> STREAM_EXT = new HashSet<>(Arrays.asList(
            ".ts", ".m3u8", ".mpd"));

    // IP 字面量正则预编译：String.matches 每次都会重新编译 Pattern，
    // 而 coreDomain 在每次资源请求里都可能被调用，代价很高。
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    /**
     * 极低开销取后缀：不整串 toLowerCase（避免为长 URL 拷贝一份），
     * 只看路径段末尾那几个字符。
     */
    private static String extOf(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int end = url.length();
        int q = url.indexOf('?');
        if (q >= 0) end = q;
        int h = url.indexOf('#');
        if (h >= 0 && h < end) end = h;
        if (end <= 0) return "";
        int scheme = url.indexOf("://");
        if (scheme >= 0) {
            // 绝对 URL：必须有主机之后的 '/'（即路径），
            // 否则那个点是主机名的一部分（https://a6.com → 不是 ".com"）
            int slash = url.indexOf('/', scheme + 3);
            if (slash < 0 || slash >= end) return "";
        }
        int sl = url.lastIndexOf('/', end - 1);
        int d = url.lastIndexOf('.', end - 1);
        if (d < 0 || d <= sl) return "";
        return url.substring(d, end).toLowerCase(Locale.ROOT);
    }

    /** 是否流媒体分片/播放列表（.ts / .m3u8 / .mpd）。 */
    private static boolean isStreamSegment(String url) {
        return STREAM_EXT.contains(extOf(url));
    }

    // 跳板特征：同域但明显是中转
    private static final String[] REDIRECT_HINT = {
            "?url=", "&url=", "?u=http", "&u=http", "?to=http", "&to=http",
            "?goto=", "&goto=", "?go=http", "&go=http", "?redirect=", "&redirect=",
            "/go.php", "/jump.php", "/out.php", "/link.php", "/redirect.php", "/click.php"
    };

    private static final String[] COMMON_PREFIX = {
            "www.", "m.", "wap.", "mobile.", "wap2.", "www2.", "www3.",
            "tv.", "v.", "cdn.", "img.", "static.", "s.", "pic.", "res."
    };

    private static final String[] SPECIAL_SUFFIX = {
            ".com.cn", ".net.cn", ".org.cn", ".gov.cn", ".edu.cn", ".ac.cn",
            ".com.hk", ".com.mo", ".com.tw", ".net.tw", ".co.uk", ".org.uk",
            ".gov.uk", ".ac.uk", ".co.jp", ".ne.jp", ".or.jp", ".go.jp",
            ".co.au", ".com.au", ".net.au", ".org.au", ".co.ca", ".com.ca",
            ".org.ca", ".co.in", ".com.in", ".net.in", ".co.kr", ".com.sg",
            ".co.sg", ".com.my", ".co.my"
    };

    private final List<String> adDomains = new ArrayList<>(Arrays.asList(AD_DOMAINS));
    private final Set<String> whitelist = new HashSet<>();   // 用户/运行时加白
    // 只用于去重日志。必须设上限 —— 广告每次跳的 URL 都带随机参数
    // （?pb=f2279eb8...），无上限会一直增长直到 OOM。
    private final Set<String> logOnce = new HashSet<>();
    private static final int LOG_ONCE_MAX = 300;

    /** 去重日志（带上限保护）。 */
    private void logOnce(String tag, String url) {
        synchronized (logOnce) {
            if (logOnce.size() > LOG_ONCE_MAX) logOnce.clear();
            if (logOnce.add(url)) SpiderDebug.log("webhome-adblock", tag + "=%s", url);
        }
    }

    private WebHomeAdBlocker() {
        // v3.46：启动时先读 Java 层持久化配置。
        // 页面 302 / 切站源时 JS 可能还没同步，靠这份值才不会退回默认(开)。
        loadPersisted();
        // 默认白名单：本地网关与嗅探播放必须放行
        whitelist.add("127.0.0.1");
        whitelist.add("localhost");
    }

    public static WebHomeAdBlocker get() {
        if (instance == null) {
            synchronized (WebHomeAdBlocker.class) {
                if (instance == null) instance = new WebHomeAdBlocker();
            }
        }
        return instance;
    }

    // ---------------- 配置 ----------------

    /** 追加广告域名（保留内置清单）。 */
    public synchronized void addAdDomains(List<String> extra) {
        if (extra == null) return;
        for (String s : extra) {
            if (!TextUtils.isEmpty(s) && !adDomains.contains(s)) adDomains.add(s.trim());
        }
    }

    /** 加白：核心域名或完整 host。 */
    public synchronized void whitelist(String domain) {
        if (!TextUtils.isEmpty(domain)) whitelist.add(domain.toLowerCase(Locale.ROOT));
    }

    public synchronized void removeWhitelist(String domain) {
        if (!TextUtils.isEmpty(domain)) whitelist.remove(domain.toLowerCase(Locale.ROOT));
    }

    public synchronized List<String> getAdDomains() {
        return new CopyOnWriteArrayList<>(adDomains);
    }

    // ---------------- 工具 ----------------

    /** 提取核心域名（移植自 FloatWebView.getCoreDomainFromUrl）。 */
    public static String coreDomain(String url) {
        String host = hostOf(url);
        if (TextUtils.isEmpty(host)) return "";
        String hostname = host.toLowerCase(Locale.ROOT);
        // 纯 IP / localhost 原样返回
        if (IPV4.matcher(hostname).matches()) return hostname;
        if ("localhost".equals(hostname)) return hostname;
        for (String p : COMMON_PREFIX) {
            if (hostname.startsWith(p)) {
                hostname = hostname.substring(p.length());
                break;
            }
        }
        String core = null;
        for (String suf : SPECIAL_SUFFIX) {
            if (hostname.length() > suf.length() && hostname.endsWith(suf)) {
                String prefix = hostname.substring(0, hostname.length() - suf.length());
                String[] pp = prefix.split("\\.");
                core = pp[pp.length - 1];
                break;
            }
        }
        if (core == null) {
            String[] parts = hostname.split("\\.");
            core = parts.length >= 2 ? parts[parts.length - 2] : hostname;
        }
        return core;
    }

    private static String hostOf(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            String s = url.trim();
            int scheme = s.indexOf("://");
            if (scheme < 0) return "";
            int start = scheme + 3;
            int end = s.length();
            for (int i = start; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '/' || c == '?' || c == '#' || c == ':') { end = i; break; }
            }
            return s.substring(start, end);
        } catch (Throwable e) {
            return "";
        }
    }

    private boolean isWhitelisted(String url) {
        if (TextUtils.isEmpty(url)) return true;
        String host = hostOf(url).toLowerCase(Locale.ROOT);
        String core = coreDomain(url);
        if (whitelist.contains(host)) return true;
        return !TextUtils.isEmpty(core) && whitelist.contains(core);
    }

    /** 是否图片/视频直链。 */
    public static boolean isMediaUrl(String url) {
        // 原来是 20 次 endsWith 扫整串小写副本；改为只取后缀 + HashSet O(1)。
        return MEDIA_EXT_SET.contains(extOf(url));
    }

    /** 是否跳板（同域中转外站）。 */
    public static boolean isRedirectUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String s = url.toLowerCase(Locale.ROOT);
        for (String hint : REDIRECT_HINT) {
            if (s.contains(hint)) return true;
        }
        return false;
    }

    /** 是否命中广告域名清单。 */
    public synchronized boolean isAdDomain(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String s = url.toLowerCase(Locale.ROOT);
        for (String ad : adDomains) {
            if (s.contains(ad)) return true;
        }
        return false;
    }

    /**
     * 是否「外跳链接」——点了会离开当前站。
     * 这是广告的核心判据，与 JS 层 isOutboundLink 保持一致。
     */
    public synchronized boolean isOutbound(String pageUrl, String targetUrl) {
        if (TextUtils.isEmpty(targetUrl)) return false;
        String s = targetUrl.trim();
        if (s.startsWith("#") || s.startsWith("javascript:")) return false;
        if (s.startsWith("data:") || s.startsWith("blob:")) return false;
        if (isWhitelisted(s)) return false;
        // 图片/视频直链是海报或播放地址，不是外跳
        if (isMediaUrl(s)) return false;
        // 命中广告域名清单：高置信
        if (isAdDomain(s)) return true;
        // 跳板
        if (isRedirectUrl(s)) return true;
        // 跨域
        String pc = coreDomain(pageUrl);
        String tc = coreDomain(s);
        return !TextUtils.isEmpty(pc) && !TextUtils.isEmpty(tc) && !pc.equals(tc);
    }

    // ---------------- 拦截入口 ----------------

    /**
     * 资源层拦截：shouldInterceptRequest 里调用。
     * 只按广告域名清单断请求，不做"所有跨域图片"一刀切——
     * 海报常放在 CDN 上，一刀切会误杀（这点 JS 层同理）。
     */
    public WebResourceResponse intercept(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        String url = request.getUrl().toString();
        // ★ 流媒体分片直接放行（放在最前，连 cfgFor 都不用跑）。
        //   m3u8 一部片几百上千个 .ts，若不短路，每个分片都要：
        //     整串 toLowerCase 拷贝 + ~90 次 adDomains contains + 正则/切分。
        //   而 .ts/.m3u8/.mpd 是纯流媒体片段与播放列表，不可能是广告创意。
        if (isStreamSegment(url)) return null;
        boolean[] cfg = cfgFor(url);
        if (!cfg[0]) return null;
        // ⚠️ v3.50：资源层拦截原来只认总开关(cfg[0]) —— 面板「全部关闭」时
        // 总开关仍在，广告域资源照样被无条件断掉（"全关还在拦"的旁路之一）。
        // 现在跟导航层一致：只有「拦跨域跳转」(cfg[1]) 或「302/跳板」(cfg[3])
        // 任一开着时才断广告域资源；两者都关 = 什么都不拦。
        if (!cfg[1] && !cfg[3]) return null;
        if (!isAdDomain(url)) return null;
        if (isWhitelisted(url)) return null;
        logOnce("blocked resource", url);
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    // ---------------- 站点域（JS 层同步） ----------------

    // JS 脚本 boot 时会把当前站点主域同步过来，用于更准的同站判定。
    // 为空则退回用 pageUrl 推导（原有逻辑）。
    private volatile String siteHost = "";

    // ⚠️ v3.44：JS 面板里的开关必须同步到这里并【优先于 Setting 的默认值】。
    // 之前只同步了 siteHost / blacklist，开关值没被使用 ——
    // shouldBlockNavigation 一直读 Setting.isWebHomeAdblockNav()（默认 true），
    // 结果就是用户在面板里关掉「拦跨域跳转」依然被拦（"开关没用"）。
    // ⚠️ v3.45：唯一开关来源 —— 只认 JS 面板同步过来的值。
    // 不再读 Setting：那套默认值是"另一个真相源"，面板改了它不变，
    // 结果就是"开关没用"。现在规则就一句：关了放行，开了拦截。
    private volatile boolean jsSynced = false;          // 是否收到过 JS 同步
    private volatile boolean jsEnabled = true;          // 总开关
    private volatile boolean jsBlockAllCrossNav = true; // 拦跨域跳转
    private volatile boolean jsAllowCrossNav = false;   // 放行跨域
    // v3.48：302/跳板重定向（脚本与点击都够不着，由 shouldOverrideUrlLoading 兜底）
    private volatile boolean jsBlockNavJump = true;     // 拦广告域/跳板重定向
    // v3.49：记住 JS 面板当前作用域。cfgFor 必须据此决定站点表是否参与判定——
    // 「全局」作用域下若仍优先站点表，JS 改全局（含关总开关）就完全到不了原生层。
    private volatile String jsScope = "global";

    // ── v3.46 持久化 ────────────────────────────────────────────
    // 关掉「拦跨域跳转」后切站源会 302 到新域，新域上悬浮球还没加载出来，
    // JS 来不及同步 → Java 回到默认(开) → 又被拦。
    // 所以开关必须存在 Java 层（Prefers），与 JS 双向同步：
    //   JS 改 → 写 Prefers；Java 判定 → 读 Prefers。
    private static final String CFG_KEY = "web_home_adblock_cfg";
    /** 全放行：脚本未启用时用它，三处判定都会直接放行。 */
    private static final boolean[] PASS_ALL = new boolean[]{false, false, false, false};
    private final java.util.Map<String, boolean[]> siteCfg =
            new java.util.concurrent.ConcurrentHashMap<>();

    private void loadPersisted() {
        try {
            String raw = Prefers.getString(CFG_KEY);
            if (TextUtils.isEmpty(raw)) return;
            JsonObject o = new Gson().fromJson(raw, JsonObject.class);
            if (o == null) return;
            JsonObject g = o.getAsJsonObject("global");
            if (g != null) {
                jsSynced = true;
                jsEnabled = g.has("enabled") ? g.get("enabled").getAsBoolean() : jsEnabled;
                jsBlockAllCrossNav = g.has("blockAllCrossNav") ? g.get("blockAllCrossNav").getAsBoolean() : jsBlockAllCrossNav;
                jsAllowCrossNav = g.has("allowCrossNav") ? g.get("allowCrossNav").getAsBoolean() : jsAllowCrossNav;
                jsBlockNavJump = g.has("blockNavJump") ? g.get("blockNavJump").getAsBoolean() : jsBlockNavJump;
            }
            if (o.has("scope")) jsScope = "site".equals(o.get("scope").getAsString()) ? "site" : "global";
            JsonObject st = o.getAsJsonObject("sites");
            if (st != null) {
                for (String h : st.keySet()) {
                    JsonObject c = st.getAsJsonObject(h);
                    if (c == null) continue;
                    siteCfg.put(h, new boolean[]{
                            c.has("enabled") ? c.get("enabled").getAsBoolean() : true,
                            c.has("blockAllCrossNav") ? c.get("blockAllCrossNav").getAsBoolean() : true,
                            c.has("allowCrossNav") ? c.get("allowCrossNav").getAsBoolean() : false,
                            c.has("blockNavJump") ? c.get("blockNavJump").getAsBoolean() : true
                    });
                }
            }
        } catch (Throwable ignored) { }
    }

    private void persist() {
        try {
            JsonObject o = new JsonObject();
            JsonObject g = new JsonObject();
            g.addProperty("enabled", jsEnabled);
            g.addProperty("blockAllCrossNav", jsBlockAllCrossNav);
            g.addProperty("allowCrossNav", jsAllowCrossNav);
            g.addProperty("blockNavJump", jsBlockNavJump);
            o.add("global", g);
            o.addProperty("scope", jsScope);
            JsonObject st = new JsonObject();
            for (java.util.Map.Entry<String, boolean[]> e : siteCfg.entrySet()) {
                JsonObject c = new JsonObject();
                c.addProperty("enabled", e.getValue()[0]);
                c.addProperty("blockAllCrossNav", e.getValue()[1]);
                c.addProperty("allowCrossNav", e.getValue()[2]);
                c.addProperty("blockNavJump", e.getValue().length > 3 ? e.getValue()[3] : true);
                st.add(e.getKey(), c);
            }
            o.add("sites", st);
            Prefers.put(CFG_KEY, o.toString());
        } catch (Throwable ignored) { }
    }

    // ⚠️ v3.47：hostOf 原本就有（上方），这里不再重复定义。

    /**
     * 取当前页生效配置：站点级优先 → 全局 → 默认。
     *
     * ⚠️ v3.47 总闸：用户在设置里【未启用】广告过滤脚本时，全部放行、什么都不做。
     * 注意区分两种"JS 没同步"：
     *   ① 未启用脚本（web_home_adblock=false）→ 必须全放行，Java 不介入
     *   ② 已启用但 JS 还没 boot（如 302 切站源）→ 读 Java 层持久化配置，不能退回默认
     * 之前把 Setting 一律移除，①②被混为一谈，① 场景下 Java 仍在拦。
     */
    private boolean[] cfgFor(String pageUrl) {
        if (!Setting.isWebHomeAdblock()) return PASS_ALL;
        boolean[] global = new boolean[]{jsEnabled, jsBlockAllCrossNav, jsAllowCrossNav, jsBlockNavJump};
        // ★ v3.49：只有当前处于「仅本站」作用域，站点覆盖才参与判定。
        //   「全局」作用域下若仍优先站点表，JS 改全局（含关总开关）
        //   就完全到不了原生层 —— 表现就是"全关还拦、总开关也没用"，
        //   只有彻底不启用脚本（Setting 关）才放行。
        if (!"site".equals(jsScope)) return global;
        String h = hostOf(pageUrl);
        if (!TextUtils.isEmpty(h)) {
            boolean[] c = siteCfg.get(h);
            if (c != null) return c;
        }
        return global;
    }

    /**
     * 接收 JS 面板同步的完整配置。
     *
     * payload: {
     *   "enabled":true, "siteHost":"a6.com",
     *   "blockAllCrossNav":true, "allowCrossNav":false,
     *   "whitelist":["..."], "blacklist":["..."]
     * }
     */
    public synchronized void applyJsConfig(boolean enabled, String host,
                                           boolean blockAllCrossNav, boolean allowCrossNav,
                                           boolean blockNavJump,
                                           java.util.List<String> white,
                                           java.util.List<String> black) {
        applyJsConfig(enabled, host, blockAllCrossNav, allowCrossNav, blockNavJump, white, black, "global");
    }

    /**
     * scope="site" → 只存到该站点；scope="global" → 存为全局。
     * 两种都会立刻写 Prefers，保证下次启动/302 后仍在。
     */
    public synchronized void applyJsConfig(boolean enabled, String host,
                                           boolean blockAllCrossNav, boolean allowCrossNav,
                                           boolean blockNavJump,
                                           java.util.List<String> white,
                                           java.util.List<String> black, String scope) {
        jsSynced = true;
        boolean[] v = new boolean[]{enabled, blockAllCrossNav, allowCrossNav, blockNavJump};
        // ⚠️ v3.46：无论站点还是全局，都【同时更新全局】。
        // 只存站点的话，切站源 302 到新域后按域查不到配置 → 退回默认(开) → 又被拦
        // （正是用户报的现象）。跨域拦截本就该全局一致。
        jsEnabled = enabled;
        jsBlockAllCrossNav = blockAllCrossNav;
        jsAllowCrossNav = allowCrossNav;
        jsBlockNavJump = blockNavJump;
        jsScope = "site".equals(scope) ? "site" : "global";
        if ("site".equals(scope) && !TextUtils.isEmpty(host)) {
            siteCfg.put(host.trim().toLowerCase(Locale.ROOT), v);
        }
        if (!TextUtils.isEmpty(host)) siteHost = host.trim().toLowerCase(Locale.ROOT);
        if (white != null) {
            for (String d : white) {
                if (!TextUtils.isEmpty(d)) whitelist(d.trim());
            }
        }
        if (black != null) {
            for (String d : black) {
                if (!TextUtils.isEmpty(d)) addBlockedDomain(d);
            }
        }
        SpiderDebug.log("webhome-adblock", "jsConfig enabled=%s host=%s blockAllCrossNav=%s allowCrossNav=%s blockNavJump=%s white=%s black=%s scope=%s",
                enabled, siteHost, blockAllCrossNav, allowCrossNav, blockNavJump,
                white == null ? 0 : white.size(), black == null ? 0 : black.size(), scope);
        persist();   // v3.46：立刻落盘，302/切站源后仍生效
    }

    /** 供 JS 通过 addJavascriptInterface 调用，同步站点主域。 */
    @android.webkit.JavascriptInterface
    public void setSiteHost(String host) {
        if (!TextUtils.isEmpty(host)) siteHost = host.trim().toLowerCase(Locale.ROOT);
    }

    /** 供 JS 调用：把 JS 面板里拉黑的域名同步到原生层。 */
    @android.webkit.JavascriptInterface
    public void addBlockedDomain(String domain) {
        if (TextUtils.isEmpty(domain)) return;
        String d = domain.trim().toLowerCase(Locale.ROOT);
        synchronized (adDomains) {
            if (!adDomains.contains(d)) adDomains.add(d);
        }
    }

    // 导航类后缀：这些才是"要打开新页面"的目标，值得拦。
    // （媒体直链由 isMediaUrl 放行，不在此列）
    private static final String[] DOC_EXT = {
            ".htm", ".html", ".shtml", ".php", ".asp", ".aspx", ".jsp", ".do"
    };

    private static boolean isDocUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String s = url.toLowerCase(Locale.ROOT);
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);
        int h = s.indexOf('#');
        if (h > 0) s = s.substring(0, h);
        for (String ext : DOC_EXT) {
            if (s.endsWith(ext)) return true;
        }
        // 无后缀的目录型 URL（https://858012.xyz/ 、 https://255iqryr.com:1280/）
        // 也算文档 —— 广告跳板大量是这种
        int scheme = s.indexOf("://");
        if (scheme < 0) return false;
        String rest = s.substring(scheme + 3);
        int slash = rest.indexOf('/');
        if (slash < 0) return true;                       // 纯域名
        String path = rest.substring(slash);
        return path.equals("/") || !path.contains(".");   // 根路径或路径无点
    }

    /**
     * 导航层拦截：shouldOverrideUrlLoading 里调用。返回 true 表示已拦截（不跳转）。
     *
     * JS 能隐藏 &lt;a&gt;，但挡不住 location.href= / meta refresh / 脚本触发的重定向，
     * 这一层专门兜底 —— 也正是 JS 层完全够不着的那部分。
     *
     * v3.42：默认拦掉【所有跨域文档导航】。
     *   WebHome 不需要跨域浏览，播放视频也是跨域「取资源」而非「跳转」，
     *   媒体直链由 isMediaUrl 放行，不会误伤。
     */
    public boolean shouldBlockNavigation(String pageUrl, String targetUrl) {
        // v3.46：读持久化配置（站点级优先 → 全局），不再读 Setting
        boolean[] cfg = cfgFor(pageUrl);
        if (!cfg[0]) return false;
        if (TextUtils.isEmpty(targetUrl)) return false;
        if (isWhitelisted(targetUrl)) return false;
        if (isMediaUrl(targetUrl)) return false;

        // 1) 高置信：广告域名清单 / 跳板重定向
        // v3.48：这一类（脚本与点击都够不着的 302/跳板）纳入「302/跳板跳转」
        // 开关（cfg[3]）。关掉后同样放行。
        boolean confirmed = isAdDomain(targetUrl) || isRedirectUrl(targetUrl);
        if (confirmed && cfg[3]) {
            logOnce("blocked nav", targetUrl);
            return true;
        }

        // 2) 拦掉所有跨域文档导航 —— 读持久化配置
        if (cfg[1]) {
            // 只拦"文档类"目标：带页面后缀，或无后缀的目录/根路径。
            // 这样 .mp4/.m3u8/.jpg 等媒体直链（上面已放行）与其他资源不受影响。
            if (!isDocUrl(targetUrl)) return false;
            if (isOutbound(pageUrl, targetUrl)) {
                logOnce("blocked crossNav", targetUrl);
                return true;
            }
        }
        return false;
    }

    // ── 回退保护 ───────────────────────────────────────────────
    // goBack() 会重新加载上一页，而上一页的广告脚本又会再次自动跳转 ——
    // 形成「拦 → 回退 → 再跳 → 再拦」的死循环，表现就是页面一直转圈。
    // 所以必须限流：同一目标在窗口期内只回退一次，且总次数设上限。
    private static final long GO_BACK_WINDOW_MS = 3000L;
    private static final int GO_BACK_MAX = 3;

    private String lastGoBackUrl = "";
    private long lastGoBackAt = 0;
    private int goBackCount = 0;
    private long goBackWindowStart = 0;

    /**
     * 拦截后是否需要回退。
     *
     * shouldOverrideUrlLoading 返回 true 时页面停在原处，通常无需回退；
     * 但若 onPageStarted 已经触发（跳转走到了一半，页面变空白），
     * 需要把用户拉回跳转前的页面 —— 否则会卡在空白页。
     *
     * ⚠️ 注意：goBack() 会重新加载上一页，若上一页脚本又自动跳转，
     *   会陷入死循环（一直转圈）。这里用三重限流挡住：
     *     ① 同一 URL 在 3 秒窗口内只回退一次
     *     ② 窗口内总回退次数上限 3 次
     *     ③ 超限后不再回退（只 stopLoading），避免无限循环
     */
    public boolean needGoBack(WebView view, String targetUrl) {
        if (view == null) return false;
        try {
            String cur = view.getUrl();
            if (TextUtils.isEmpty(cur) || TextUtils.isEmpty(targetUrl)) return false;
            // 当前页已经变成被拦的目标（说明跳转已部分生效）才需要回退
            if (!cur.equals(targetUrl)) return false;
            if (!view.canGoBack()) return false;

            long now = System.currentTimeMillis();

            // ① 同一 URL 窗口期内只回退一次
            if (targetUrl.equals(lastGoBackUrl) && now - lastGoBackAt < GO_BACK_WINDOW_MS) {
                return false;
            }

            // 窗口重置
            if (now - goBackWindowStart > GO_BACK_WINDOW_MS * 4) {
                goBackWindowStart = now;
                goBackCount = 0;
            }
            // ② 总次数上限
            if (goBackCount >= GO_BACK_MAX) {
                SpiderDebug.log("webhome-adblock", "goBack limit reached, skip (avoid loop) url=%s", targetUrl);
                return false;
            }

            goBackCount++;
            lastGoBackUrl = targetUrl;
            lastGoBackAt = now;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 供控制器在拦截后调用：先停掉正在进行的加载，再按需回退。 */
    public void onNavigationBlocked(WebView view, String targetUrl) {
        if (view == null) return;
        try {
            // stopLoading 优先：阻止当前这次加载继续，避免页面停在半成品状态
            view.stopLoading();
        } catch (Throwable ignored) { }
        try {
            if (needGoBack(view, targetUrl)) view.goBack();
        } catch (Throwable ignored) { }
    }

    /**
     * 弹窗拦截（手势版）：WebChromeClient.onCreateWindow 里调用。
     *
     * onCreateWindow 拿不到弹窗目标 URL，只能按「页面 + 是否用户手势」判定。
     * ⚠️ v3.50：原先这里直接读 Setting.isWebHomeAdblock()/isWebHomeAdblockPopup()
     * —— 那是 App 层的另一个真相源，与 JS 面板无关。结果：面板里把总开关、
     * 「拦跨域跳转」全关掉，非手势的自动弹窗仍然被无条件拦下（"全关还在拦"）。
     * 现在改为与导航/资源拦截一致：只认 JS 面板同步过来的配置。
     *   未启用脚本(Setting)       → 放行
     *   面板总开关关(cfg[0]=false) → 放行
     *   自动弹窗(非手势) = 脚本发起的跨域导航 → 归「拦跨域跳转」(cfg[1]) 或
     *                                            「302/跳板」(cfg[3]) 管
     *   手势弹窗                   → 仅当 App 显式勾选「严格弹窗拦截」时才拦
     */
    public boolean shouldBlockPopupByGesture(String pageUrl, boolean isUserGesture) {
        if (!Setting.isWebHomeAdblock()) return false;
        boolean[] cfg = cfgFor(pageUrl);
        if (!cfg[0]) return false;
        if (isUserGesture) return Setting.isWebHomeAdblockPopup();
        return cfg[1] || cfg[3];
    }
 
    /**
     * 弹窗拦截：已知目标 URL 时使用（广告弹窗目标几乎必然是外站）。
     */
    public boolean shouldBlockPopup(String pageUrl, String targetUrl) {
        boolean[] cfg = cfgFor(pageUrl);
        if (!cfg[0]) return false;
        if (isWhitelisted(targetUrl)) return false;
        if (isMediaUrl(targetUrl)) return false;
        // v3.49：与 shouldBlockNavigation 对齐 —— 广告域/跳板归「302/跳板跳转」(cfg[3])，
        // 跨域外站归「拦跨域跳转」(cfg[1])。两者都关时不再拦弹窗，
        // 避免出现"开关关了、弹窗仍被无条件拦"的旁路。
        boolean confirmed = isAdDomain(targetUrl) || isRedirectUrl(targetUrl);
        boolean hit = (confirmed && cfg[3]) || (cfg[1] && isOutbound(pageUrl, targetUrl));
        if (hit) logOnce("blocked popup", targetUrl);
        return hit;
    }
}
