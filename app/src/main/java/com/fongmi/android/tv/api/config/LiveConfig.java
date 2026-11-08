package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.LiveApi;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LiveConfig extends BaseConfig {

    private static final String TAG = LiveConfig.class.getSimpleName();

    private Live home;
    private List<Live> lives;
    private List<Rule> rules;
    private List<HlsAdRule> hlsRules;
    private List<String> ads;

    public static LiveConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static String getResp() {
        return get().getHome().getCore().getResp();
    }

    public static int getHomeIndex() {
        return get().getLives().indexOf(get().getHome());
    }

    public static Live getNextHome() {
        int index = getHomeIndex() + 1;
        return index > 0 && index < get().getLives().size() ? get().getLives().get(index) : null;
    }

    public static boolean isOnly() {
        return get().getLives().size() == 1;
    }

    public static boolean isEmpty() {
        return get().getHome().isEmpty();
    }

    public static boolean hasLoadedLives() {
        return !get().getLives().isEmpty();
    }

    public static boolean hasUrl() {
        return !TextUtils.isEmpty(getUrl());
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public LiveConfig init() {
        return config(Config.live());
    }

    public LiveConfig config(Config config) {
        this.config = config;
        if (config.isEmpty()) return this;
        this.sync = config.getUrl().equals(VodConfig.getUrl());
        return this;
    }

    public LiveConfig clear() {
        ads = null;
        home = null;
        lives = null;
        rules = null;
        hlsRules = null;
        RuleConfig.get().invalidate();
        HlsRuleConfig.invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.live();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.live();
    }

    @Override
    protected void load(Config config) throws Throwable {
        if (config.isEmpty()) {
            initLive(config, new JsonObject());
            return;
        }
        String json = Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
        if (Json.isObj(json)) checkJson(config, Json.parse(json).getAsJsonObject());
        else parseText(config, json);
    }

    @Override
    protected boolean isLoaded() {
        return !getLives().isEmpty() && !getHome().getGroups().isEmpty();
    }

    @Override
    protected void onLoadSuccess() {
        InterfaceAdRuleLearningService.schedule(getConfig().getDesc(), getConfig().getUrl(), getAds(), getRules());
    }

    @Override
    public synchronized void ensureLoaded() {
        try {
            if (isLoaded()) return;
            super.ensureLoaded();
            LiveApi.parse(getHome());
            LiveApi.parseXml(getHome());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (sync) return;
        load(new Callback());
    }

    private void parseText(Config config, String text) {
        Live live = new Live(UrlUtil.getName(config.getUrl()), config.getUrl()).sync();
        lives = new ArrayList<>(List.of(live));
        LiveParser.text(live, text);
        finishLive(config, "");
    }

    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, LIVE));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        CustomCspSetting.inject(object);
        initList(object);
        initLive(config, object);
    }

    public void parse(JsonObject object) {
        initLive(getConfig(), object);
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setHlsRules(HlsAdRule.arrayFrom(fetchArray(object, "hlsRules")));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, false);
        setLives(Json.safeListElement(object, "lives").stream().map(e -> Live.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        finishLive(config, spider);
    }

    private void finishLive(Config config, String spider) {
        CustomCspSetting.inject(getLives(), spider);
        getLives().removeIf(Live::isEmpty);
        // 旧版本曾把简繁转换后的名字写进主键，历史行因此按“译文名”落库；这里按归一后的名字兜一次，
        // 让已经在繁体态保存过收藏/屏蔽/上次频道的用户在升级后仍能匹配上，而不是静默丢失。
        // 归一化显式传 pass=false，与当前界面语言无关，否则同一份数据在两种语言下会得到不同键。
        Map<String, Live> items = new HashMap<>();
        for (Live item : Live.findAll()) items.putIfAbsent(Trans.s2t(false, item.getName()), item);
        getLives().forEach(live -> live.sync(items.get(Trans.s2t(false, live.getName()))));
        // 同理，Config.home 也可能是旧版本写入的译文名，两种写法都认。
        setHome(config, getLives().isEmpty() ? new Live() : getLives().stream().filter(item -> matchesHome(item, config.getHome())).findFirst().orElse(getLives().get(0)), false);
    }

    /**
     * 上次频道是否就是这一条直播源。
     * <p>
     * 旧版本把简繁转换后的名字写进 {@code Config.home}，所以除了当前语言的显示名，
     * 还要把候选名与配置值都做一次双向简繁归一后再比，避免升级后静默退回第一个频道。
     */
    private boolean matchesHome(Live item, String home) {
        if (TextUtils.isEmpty(home)) return false;
        if (item.getName().equals(home) || item.getDisplayName().equals(home)) return true;
        // 归一必须与当前界面语言无关，因此显式传 pass=false 强制转换，而不是用受语言状态影响的 s2t(String)。
        return Trans.s2t(false, item.getName()).equals(Trans.s2t(false, home));
    }

    public void setKeep(Channel channel) {
        if (home != null && !channel.getGroup().isHidden()) home.keep(channel).save();
    }

    public void applyKeepsToGroups(List<Group> items) {
        Set<String> key = Keep.getLive().stream().map(Keep::getKey).collect(Collectors.toSet());
        items.stream().filter(group -> !group.isKeep())
                .flatMap(group -> group.getChannel().stream())
                .filter(channel -> key.contains(channel.getName()))
                .forEach(channel -> items.get(0).add(channel));
    }

    public int[] findKeepPosition(List<Group> items) {
        String[] splits = getHome().getKeep().split(AppDatabase.SYMBOL);
        if (splits.length < 3) return new int[]{1, 0};
        for (int i = 0; i < items.size(); i++) {
            Group group = items.get(i);
            // 旧版本把简繁转换后的组名写进了 keep 键，因此这里用与语言无关的归一名比对（见 Group.matchesName）。
            if (group.matchesName(splits[0])) {
                int j = group.find(splits[1]);
                if (j != -1) {
                    group.getChannel().get(j).setIndex(splits[2]);
                    return new int[]{i, j};
                }
            }
        }
        return new int[]{1, 0};
    }

    public int[] findByChannelNumber(String number, List<Group> items) {
        int num = Integer.parseInt(number);
        for (int i = 0; i < items.size(); i++) {
            int j = items.get(i).find(num);
            if (j != -1) return new int[]{i, j};
        }
        return new int[]{-1, -1};
    }

    public List<Live> getLives() {
        return lives == null ? lives = new ArrayList<>() : lives;
    }

    private void setLives(List<Live> lives) {
        this.lives = lives;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    public List<HlsAdRule> getHlsRules() {
        return hlsRules == null ? Collections.emptyList() : hlsRules;
    }

    private void setHlsRules(List<HlsAdRule> rules) {
        this.hlsRules = rules;
        HlsRuleConfig.invalidate();
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Live getHome() {
        return home == null ? new Live() : home;
    }

    public void setHome(Live home) {
        setHome(getConfig(), home, true);
    }

    public Live getLive(String key) {
        return getLives().stream().filter(item -> item.getName().equals(key)).findFirst().orElse(new Live());
    }

    private void setHome(Config config, Live live, boolean save) {
        home = live;
        home.setSelected(true);
        config.setHome(home.getName());
        if (save) config.save();
        getLives().forEach(item -> item.setSelected(home));
        if (!save && (home.isBoot() || LiveSetting.isBoot())) ConfigEvent.boot();
    }

    private static class Loader {
        static volatile LiveConfig INSTANCE = new LiveConfig();
    }
}
