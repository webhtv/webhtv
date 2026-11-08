package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.gson.ExtAdapter;
import com.fongmi.android.tv.gson.HeaderAdapter;
import com.fongmi.android.tv.setting.SiteNameStore;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Entity
public class Site implements Parcelable {

    /** 与 {@link #TAG_CLOSE} 逐位配对，覆盖半角与常见的全角括号写法。 */
    private static final String TAG_OPEN = "[「【〔［";
    private static final String TAG_CLOSE = "]」】〕］";

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;

    @Ignore
    @SerializedName("name")
    private String name;

    @Ignore
    @SerializedName("api")
    private String api;

    @Ignore
    @SerializedName("ext")
    @JsonAdapter(ExtAdapter.class)
    private String ext;

    @Ignore
    @SerializedName("jar")
    private String jar;

    @Ignore
    @SerializedName("click")
    private String click;

    @Ignore
    @SerializedName("playUrl")
    private String playUrl;

    @Ignore
    @SerializedName(value = "homePage", alternate = {"home_page", "webHome", "web_home"})
    private String homePage;

    @Ignore
    @SerializedName("chromeMode")
    private String chromeMode;

    @Ignore
    @SerializedName("webHomeChrome")
    private JsonElement webHomeChrome;

    @Ignore
    @SerializedName("extensions")
    private JsonElement extensions;

    @Ignore
    @SerializedName("type")
    private Integer type;

    @Ignore
    @SerializedName("hide")
    private Integer hide;

    @Ignore
    @SerializedName("indexs")
    private Integer indexs;

    @Ignore
    @SerializedName("timeout")
    private Integer timeout;

    @SerializedName("searchable")
    private Integer searchable;

    @SerializedName("changeable")
    private Integer changeable;

    @Ignore
    @SerializedName("quickSearch")
    private Integer quickSearch;

    @Ignore
    @SerializedName("categories")
    private List<String> categories;

    @Ignore
    @SerializedName("header")
    @JsonAdapter(HeaderAdapter.class)
    private Map<String, String> header;

    @Ignore
    @SerializedName("style")
    private Style style;

    @Ignore
    private boolean selected;

    /**
     * 该 Site 属于哪条配置（桥接解析非激活配置时的缓存命名空间）。
     *
     * <p>只在 {@code VodSiteCatalog} 由 configKey 构造的 Catalog 上被设置；激活配置（原生
     * {@code VodConfig} 路径）与数据库里的 Site 一律为空，{@code transient} + {@code @Ignore}
     * 保证它既不进 Room 也不进 Gson、不进 Parcel——纯运行时标记。
     */
    @Ignore
    private transient String namespace;

    public Site() {
    }

    protected Site(Parcel in) {
        this.key = in.readString();
        this.name = in.readString();
        this.api = in.readString();
        this.ext = in.readString();
        this.jar = in.readString();
        this.click = in.readString();
        this.playUrl = in.readString();
        this.homePage = in.readString();
        this.chromeMode = in.readString();
        String chrome = in.readString();
        this.webHomeChrome = TextUtils.isEmpty(chrome) ? null : App.gson().fromJson(chrome, JsonElement.class);
        this.type = (Integer) in.readValue(Integer.class.getClassLoader());
        this.indexs = (Integer) in.readValue(Integer.class.getClassLoader());
        this.timeout = (Integer) in.readValue(Integer.class.getClassLoader());
        this.searchable = (Integer) in.readValue(Integer.class.getClassLoader());
        this.changeable = (Integer) in.readValue(Integer.class.getClassLoader());
        this.categories = in.createStringArrayList();
        this.style = in.readParcelable(Style.class.getClassLoader());
        this.selected = in.readByte() != 0;
    }

    public static Site objectFrom(JsonElement element, String spider) {
        try {
            Site site = App.gson().fromJson(element, Site.class);
            if (site.getJar().isEmpty()) site.setJar(spider);
            site.setApi(UrlUtil.convert(site.getApi()));
            site.setExt(UrlUtil.convert(site.getExt()));
            site.setHomePage(UrlUtil.convert(site.getHomePage()));
            return site.trans();
        } catch (Exception e) {
            return new Site();
        }
    }

    public static Site get(String key, String name) {
        Site site = new Site();
        site.setKey(key);
        site.setName(name);
        return site;
    }

    public static List<Site> findAll() {
        return AppDatabase.get().getSiteDao().findAll();
    }

    public String getKey() {
        return TextUtils.isEmpty(key) ? "" : key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return SiteNameStore.getDisplayName(this);
    }

    public String getApi() {
        return TextUtils.isEmpty(api) ? "" : api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getExt() {
        return TextUtils.isEmpty(ext) ? "" : ext;
    }

    public void setExt(String ext) {
        this.ext = ext.trim();
    }

    public String getJar() {
        return TextUtils.isEmpty(jar) ? "" : jar;
    }

    public void setJar(String jar) {
        this.jar = jar;
    }

    public void setClick(String click) {
        this.click = click;
    }

    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

    public String getClick() {
        return TextUtils.isEmpty(click) ? "" : click;
    }

    public String getPlayUrl() {
        return TextUtils.isEmpty(playUrl) ? "" : playUrl;
    }

    public String getHomePage() {
        return TextUtils.isEmpty(homePage) ? "" : homePage;
    }

    public void setHomePage(String homePage) {
        this.homePage = homePage;
    }

    public String getChromeMode() {
        if (!TextUtils.isEmpty(chromeMode)) return chromeMode.trim();
        try {
            JsonObject object = getWebHomeChrome();
            return object.has("mode") ? object.getAsJsonPrimitive("mode").getAsString().trim() : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public void setChromeMode(String chromeMode) {
        this.chromeMode = chromeMode;
    }

    public JsonObject getWebHomeChrome() {
        JsonObject object = new JsonObject();
        try {
            if (webHomeChrome != null && webHomeChrome.isJsonObject()) object = webHomeChrome.getAsJsonObject().deepCopy();
            else if (webHomeChrome != null && webHomeChrome.isJsonPrimitive()) object.addProperty("mode", webHomeChrome.getAsString());
        } catch (Throwable ignored) {
        }
        if (!TextUtils.isEmpty(chromeMode) && !object.has("mode")) object.addProperty("mode", chromeMode.trim());
        return object;
    }

    public void setWebHomeChrome(JsonElement webHomeChrome) {
        this.webHomeChrome = webHomeChrome;
    }

    public JsonElement getExtensions() {
        return extensions;
    }

    public void setExtensions(JsonElement extensions) {
        this.extensions = extensions;
    }

    public Integer getType() {
        return type == null ? 0 : type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getHide() {
        return hide == null ? 0 : hide;
    }

    public void setHide(Integer hide) {
        this.hide = hide;
    }

    public Integer getIndexs() {
        return indexs == null ? 0 : indexs;
    }

    public long getTimeout() {
        return timeout == null ? Constant.TIMEOUT_PLAY : TimeUnit.SECONDS.toMillis(Math.max(timeout, 1));
    }

    public Integer getSearchable() {
        return searchable == null ? 1 : searchable;
    }

    public Integer getChangeable() {
        return changeable == null ? 1 : changeable;
    }

    public Integer getQuickSearch() {
        return quickSearch == null ? 1 : quickSearch;
    }

    public void setQuickSearch(Integer quickSearch) {
        this.quickSearch = quickSearch;
    }

    public List<String> getCategories() {
        return categories == null ? Collections.emptyList() : categories;
    }

    public List<String> getGroups() {
        return SiteNameStore.getGroups(this);
    }

    public boolean inGroup(String group) {
        return TextUtils.isEmpty(group) || getGroups().contains(group.trim());
    }

    public static List<String> getGroups(List<Site> sites) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        for (Site site : sites) {
            if (site == null || site.isHide()) continue;
            groups.addAll(site.getGroups());
        }
        return new ArrayList<>(groups);
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public Map<String, String> getHeader() {
        return header == null ? new HashMap<>() : header;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public Style getStyle(Style style) {
        return getStyle() != null ? getStyle() : style != null ? style : Style.rect();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setSelected(Site item) {
        this.selected = item.equals(this);
    }

    public boolean isHide() {
        return getHide() == 1;
    }

    public boolean isIndex() {
        return getIndexs() == 1;
    }

    public boolean isSearchable() {
        return getSearchable() == 1;
    }

    public void setSearchable(Integer searchable) {
        this.searchable = searchable;
    }

    public Site setSearchable(boolean searchable) {
        if (getSearchable() != 0) setSearchable(searchable ? 1 : 2);
        return this;
    }

    public boolean isChangeable() {
        return getChangeable() == 1;
    }

    public void setChangeable(Integer changeable) {
        this.changeable = changeable;
    }

    public Site setChangeable(boolean changeable) {
        if (getChangeable() != 0) setChangeable(changeable ? 1 : 2);
        return this;
    }

    public boolean isQuickSearch() {
        return getQuickSearch() == 1;
    }

    public boolean hasHomePage() {
        return !getHomePage().isEmpty();
    }

    public boolean isEmpty() {
        return getKey().isEmpty() && getName().isEmpty();
    }

    public Site fetchExt() {
        if (!getExt().startsWith("http")) return this;
        String extend = OkHttp.string(getExt());
        if (!extend.isEmpty()) setExt(extend);
        return this;
    }

    /**
     * 方括号里的内容（{@code [采]}、{@code 「盘」}、{@code 【漫】}……）是站点的能力标签，不是普通文案：
     * 它们既会被 {@code GroupRuleConfig} 抽成站点分组名（分组名同时是分组排序的持久化键），
     * 也会被音频源、短剧、小说、漫画与 TMDB 的站点规则直接比对。
     * <p>
     * 因此这里只翻译标签之外的文案，标签本身一律原样保留。否则繁体界面下 {@code 荐片[采][广]} 会变成
     * {@code 薦片[采][廣]}：标签与用户规则文本不再互等，分组名还会随语言漂移，把已保存的分组顺序与规则勾选态全部打散。
     */
    public Site trans() {
        if (Trans.pass()) return this;
        this.name = transName(name);
        setCategories(getCategories().stream().map(Trans::s2t).toList());
        return this;
    }

    private static String transName(String name) {
        if (TextUtils.isEmpty(name)) return name;
        StringBuilder result = new StringBuilder(name.length());
        int index = 0;
        while (index < name.length()) {
            int open = TAG_OPEN.indexOf(name.charAt(index));
            if (open < 0) {
                int next = index + 1;
                while (next < name.length() && TAG_OPEN.indexOf(name.charAt(next)) < 0) next++;
                result.append(Trans.s2t(name.substring(index, next)));
                index = next;
                continue;
            }
            int close = name.indexOf(TAG_CLOSE.charAt(open), index + 1);
            if (close < 0) {
                result.append(Trans.s2t(name.substring(index)));
                break;
            }
            result.append(name, index, close + 1);
            index = close + 1;
        }
        return result.toString();
    }

    public Site sync(Site item) {
        if (item == null) return this;
        if (getChangeable() != 0) setChangeable(Math.max(1, item.getChangeable()));
        if (getSearchable() != 0) setSearchable(Math.max(1, item.getSearchable()));
        return this;
    }

    /** 缓存命名空间；未设置时返回空串（= 激活配置路径）。 */
    public String getNamespace() {
        return namespace == null ? "" : namespace.trim();
    }

    public Site setNamespace(String namespace) {
        this.namespace = namespace == null || namespace.isEmpty() ? null : namespace;
        return this;
    }

    public Site recent() {
        // recent 是 ?do=js/?do=py 子路由唯一的定位凭据，必须和 getSpider 的槽位键同形，
        // 否则桥接配置的脚本请求会落到别的配置（或落空）。命名空间为空时与改动前一致。
        BaseLoader.get().setRecent(getNamespace(), getKey(), getApi(), getJar());
        return this;
    }

    public Spider spider() {
        // 带命名空间：桥接配置（非激活）的 spider 必须落在自己的缓存槽里，否则同名 siteKey
        // 会把别的配置的实例写进激活配置的槽位。命名空间为空时键与改动前逐字节相同。
        return BaseLoader.get().getSpider(getNamespace(), getKey(), getApi(), getExt(), getJar());
    }

    public void save() {
        AppDatabase.get().getSiteDao().insertOrUpdate(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Site it)) return false;
        return Objects.equals(getKey(), it.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.key);
        dest.writeString(this.name);
        dest.writeString(this.api);
        dest.writeString(this.ext);
        dest.writeString(this.jar);
        dest.writeString(this.click);
        dest.writeString(this.playUrl);
        dest.writeString(this.homePage);
        dest.writeString(this.chromeMode);
        dest.writeString(this.webHomeChrome == null ? "" : this.webHomeChrome.toString());
        dest.writeValue(this.type);
        dest.writeValue(this.indexs);
        dest.writeValue(this.timeout);
        dest.writeValue(this.searchable);
        dest.writeValue(this.changeable);
        dest.writeStringList(this.categories);
        dest.writeParcelable(this.style, flags);
        dest.writeByte(this.selected ? (byte) 1 : (byte) 0);
    }

    public static final Creator<Site> CREATOR = new Creator<>() {
        @Override
        public Site createFromParcel(Parcel source) {
            return new Site(source);
        }

        @Override
        public Site[] newArray(int size) {
            return new Site[size];
        }
    };
}
