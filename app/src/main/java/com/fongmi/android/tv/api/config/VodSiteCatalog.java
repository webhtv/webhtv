package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.TmdbSourceCredentialIngress;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.playback.PlaybackConfigIdentity;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按配置标识读取站源清单，且<b>不触碰当前激活配置</b>。
 *
 * <p>{@link VodConfig} 是"当前激活源"的全局单例：它的解析路径会顺带写入 live/wall 配置行、
 * 注册全局 spider 加载器、覆盖 WebHomeExtensionRegistry 与配置行上的 logo/notice。
 * PC 端桥接需要一次拿到某一配置的全部站源并逐个切换，因此这里只做纯解析：
 * 当前激活配置仍归 VodConfig，非激活配置归本类。
 */
public final class VodSiteCatalog {

    private static final String TAG = "VodSiteCatalog";
    private static final int MAX_DEPOT_DEPTH = 3;
    /** 配置解析要发网络请求，/proxy 又会被逐段调用，按标识短缓存。 */
    private static final long CACHE_TTL = 60_000L;
    private static final int CACHE_SIZE = 8;

    /**
     * 解析结果缓存。进程内唯一，因此必须有 {@link #invalidate()} 这个失效入口：
     * 配置行被改写或删除后如果还能命中，网关会继续按旧站源服务最多 {@link #CACHE_TTL} 毫秒。
     */
    private static final Map<String, Entry> CACHE = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, VodSiteCatalog.Entry> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private VodSiteCatalog() {
    }

    /** 一份配置解析出来的、可直接对外提供网关服务的内容。 */
    public static final class Catalog {
        private final List<Site> sites;
        private final List<Doh> doh;
        private final List<Rule> rules;
        private final List<String> flags;

        public Catalog(List<Site> sites, List<Doh> doh, List<Rule> rules, List<String> flags) {
            this.sites = sites == null ? Collections.<Site>emptyList() : sites;
            this.doh = doh == null ? Collections.<Doh>emptyList() : doh;
            this.rules = rules == null ? Collections.<Rule>emptyList() : rules;
            this.flags = flags == null ? Collections.<String>emptyList() : flags;
        }

        public List<Site> getSites() {
            return sites;
        }

        public List<Doh> getDoh() {
            return doh;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public List<String> getFlags() {
            return flags;
        }

        /** 与 {@link VodConfig#getSite(String)} 一致：先精确、再忽略大小写，未命中给空站点。 */
        public Site find(String key) {
            if (key == null || key.isEmpty()) return new Site();
            for (Site site : sites) {
                if (site == null || site.getKey().isEmpty()) continue;
                if (site.getKey().equals(key)) return site;
            }
            for (Site site : sites) {
                if (site == null || site.getKey().isEmpty()) continue;
                if (site.getKey().equalsIgnoreCase(key)) return site;
            }
            return new Site();
        }
    }

    /** 配置标识 → 站源清单。默认实现查数据库；单元测试可注入替身。 */
    public interface Source {
        Catalog load(String interfaceKey) throws Exception;
    }

    /**
     * 默认实现：按 interfaceKey 从数据库取配置行再解析。
     * 未知标识抛 IllegalArgumentException，由调用方转成 400。
     * 进程内共享，缓存才能跨请求命中。
     */
    public static Source database() {
        return Database.INSTANCE;
    }

    private static final class Database {
        static final Source INSTANCE = newDatabase();
    }

    private static Source newDatabase() {
        return new Source() {
            @Override
            public Catalog load(String interfaceKey) throws Exception {
                String id = namespace(interfaceKey);
                if (id.isEmpty()) throw new IllegalArgumentException("缺少配置标识");
                synchronized (CACHE) {
                    Entry entry = CACHE.get(id);
                    if (entry != null && System.currentTimeMillis() - entry.at < CACHE_TTL) return entry.catalog;
                }
                Catalog catalog = catalogOf(findConfig(id), id);
                // 时间戳取解析<b>之后</b>：解析慢的配置若沿用开始时间，写进缓存时可能已经接近
                // 甚至超过 TTL，表现为"出生即过期"，每次请求都重新拉一遍订阅。
                synchronized (CACHE) {
                    CACHE.put(id, new Entry(catalog, System.currentTimeMillis()));
                }
                return catalog;
            }
        };
    }

    /** 丢弃全部已解析配置。配置行被改写或删除后必须调用，否则最多 60 秒仍在服务旧站源。 */
    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /**
     * 配置标识的规范形式（去首尾空白）。
     *
     * <p>同时充当 spider 缓存的命名空间来源：激活配置路径必须解析出空串，
     * 这样它的缓存键才与改动前逐字节相同。
     */
    public static String namespace(String interfaceKey) {
        return interfaceKey == null ? "" : interfaceKey.trim();
    }

    /**
     * 只读查配置行，<b>绝不新建</b>：桥接推送要落到已存在的配置上。
     * 旧协议里 Config.find(url, type) 查不到就 insert，会在安卓库里凭空多出一个"桥接源"，
     * 因此按配置标识定位时必须走这条纯查询路径。
     */
    public static Config findConfig(String interfaceKey) {
        String id = namespace(interfaceKey);
        if (id.isEmpty()) throw new IllegalArgumentException("缺少配置标识");
        Config exact = AppDatabase.get().getConfigDao().findByInterfaceKey(id, BaseConfig.VOD);
        // 新旧协议都把 sha256(config-url)、历史地址与 legacy key 当 configKey 发过来，
        // 进度写入用的是 PlaybackConfigIdentity.cidForKey。两边必须认同一套标识，
        // 否则同一个 key 在进度同步能解析、在站源/历史推送却报"未知"。
        Config legacy = exact != null ? null : legacyConfig(id);
        return resolve(id, exact, legacy);
    }

    /** 既有解析器的兜底：命中即按 cid 只读取行，绝不使用 Config.find(url, type) 的插入语义。 */
    private static Config legacyConfig(String id) {
        int cid = PlaybackConfigIdentity.cidForKey(id);
        return cid > 0 ? Config.find(cid) : null;
    }

    /**
     * 纯解析：精确命中优先，其次沿用既有播放身份解析出的行；都没有就是未知标识。
     *
     * <p>{@code exact}/{@code legacy} 由调用方只读查得，本方法不碰数据库，
     * 因此单测能直接锁住"未知标识必须报错且绝不新建配置行"。
     */
    public static Config resolve(String id, Config exact, Config legacy) {
        Config config = exact != null ? exact : legacy;
        if (config == null || isBlank(config.getUrl())) {
            throw new IllegalArgumentException("未知的配置标识: " + id);
        }
        return config;
    }

    public static List<Site> sitesOf(Config config) throws Exception {
        return catalogOf(config, "").getSites();
    }

    /** 解析一份配置：只读网络 + 纯函数变换，不写数据库、不切换全局状态。 */
    public static Catalog catalogOf(Config config) throws Exception {
        return catalogOf(config, "");
    }

    /**
     * 同上，但把 {@code namespace} 记到每个站源上，使它们的 spider 落在自己的缓存槽里。
     *
     * <p>激活配置走空命名空间，因此与原行为逐字节等价；只有按 configKey 桥接解析非激活配置时
     * 才传入该 configKey。
     */
    public static Catalog catalogOf(Config config, String namespace) throws Exception {
        if (config == null || isBlank(config.getUrl())) throw new IllegalArgumentException("配置为空");
        return catalogOf(objectOf(config.getUrl(), 0), namespace);
    }

    /** 由已解析的配置对象抽取站源；CustomCspSetting 只合并进传入对象。 */
    public static Catalog catalogOf(JsonObject object) {
        return catalogOf(object, "");
    }

    /**
     * 同上，并把 {@code namespace} 记到抽取出来的每个站源上。
     *
     * <p>命名空间非空时，这些 Site 的 {@link Site#spider()} 会落到该命名空间的缓存槽，
     * 因此两条配置里的同名 siteKey 不会互相污染；空命名空间（激活配置路径）不写任何标记，
     * 与原行为逐字节等价。
     */
    public static Catalog catalogOf(JsonObject object, String namespace) {
        if (object == null) throw new IllegalArgumentException("配置为空");
        CustomCspSetting.inject(object);
        String spider = Json.safeString(object, "spider");
        List<Site> sites = new ArrayList<>();
        for (JsonElement element : Json.safeListElement(object, "sites")) {
            Site site = Site.objectFrom(element, spider);
            if (site == null || site.getKey().isEmpty()) continue;
            sites.add(site);
        }
        // 与 VodConfig.initSite 对齐的两步，都只读、不写库、不切全局状态：
        // 站源上用户改过的 searchable/changeable 要回填，自定义 CSP 站源要合并进来，
        // 否则桥接清单与原生加载出来的不是同一份。
        Map<String, Site> known = new HashMap<>();
        for (Site site : Site.findAll()) known.put(site.getKey(), site);
        for (Site site : sites) site.sync(known.get(site.getKey()));
        CustomCspSetting.inject(sites);
        // 打标记放在两处合并之后：自定义 CSP 站源也是这份桥接清单的一部分，必须一起带上命名空间。
        if (!isBlank(namespace)) for (Site site : sites) site.setNamespace(namespace);
        return new Catalog(sites, dohOf(object), rulesOf(object), Json.safeListString(object, "flags"));
    }

    private static JsonObject objectOf(String configUrl, int depth) throws Exception {
        if (depth > MAX_DEPOT_DEPTH) throw new IllegalArgumentException("配置订阅层级过深");
        // 猫源（bundle）只复用已在服务的那一个：换源会杀掉激活配置的子进程，见 CatSource.reuse。
        String url = CatSource.isBundle(configUrl) ? CatSource.reuse(configUrl) : UrlUtil.convert(configUrl);
        String raw = Decoder.getJson(url, TAG);
        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);
        JsonObject object = CatSource.normalize(url, Json.parse(ingress.getSanitizedJson()));
        // 订阅列表：取首条继续解析，且不像 VodConfig 那样把中间配置写进数据库。
        if (!object.has("sites") && object.has("urls")) {
            List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
            if (items.isEmpty()) throw new IllegalArgumentException("配置订阅为空");
            return objectOf(items.get(0).getUrl(), depth + 1);
        }
        return object;
    }

    private static List<Doh> dohOf(JsonObject object) {
        if (!object.has("doh") || !object.get("doh").isJsonArray()) return Collections.emptyList();
        return Doh.arrayFrom(object.get("doh"));
    }

    private static List<Rule> rulesOf(JsonObject object) {
        if (!object.has("rules") || !object.get("rules").isJsonArray()) return Collections.emptyList();
        return Rule.arrayFrom(object.get("rules"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class Entry {
        final Catalog catalog;
        final long at;

        Entry(Catalog catalog, long at) {
            this.catalog = catalog;
            this.at = at;
        }
    }
}
