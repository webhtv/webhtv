package com.fongmi.android.tv.playback;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class PlaybackFieldPolicy {

    private static final Set<String> PROTOCOL = set("schema", "event", "eventId", "timestamp", "sessionId", "dedupeKey", "scope", "deletedAt");
    private static final Set<String> OBJECT = set("cid", "configKey", "configName", "historyKey", "siteKey", "siteName", "vodId", "vodName", "vodPic", "flag", "sourceBindingKey", "episodeName", "mediaType", "tmdbId", "seasonNumber", "tmdbEpisodeNumber");
    private static final Set<String> PROGRESS = set("state", "positionMs", "durationMs", "progress", "speed", "speedOverride", "completed");
    private static final Set<String> STANDARD = set("appVersion", "client");
    private static final Set<String> FULL = set("episodeUrl", "episodeIndex", "clientKey");
    private static final Set<String> OPTIONAL = set("episodeUrl", "episodeIndex", "appVersion", "client", "clientKey");
    /**
     * 只读 API 额外放行的字段。
     *
     * <p>{@code episodeId} 是单集地址的 SHA-256（见
     * {@link PlaybackRecord#episodeId(String)}）：它让对端能稳定区分同一剧的
     * 不同集，又不把带时效签名的播放地址（{@code episodeUrl}）交出去。
     *
     * <p>为什么不直接放 {@code episodeUrl}：那个地址常带 CDN 签名/token，
     * 且对端只需要"是不是同一集"这个布尔语义，不需要能重新发起播放。
     *
     * <p>本字段只被 {@link #apiSafe()} 在**运行时**读取，不参与任何静态初始化，
     * 因此没有声明顺序约束。{@link #customSafe()} 刻意不含它：webhook 的自定义字段
     * 白名单由 {@code PlaybackWebhookDialog.FIELD_KEYS} 驱动，那里没有 {@code episodeId}。
     */
    private static final Set<String> API_SAFE_EXTRA = set("episodeId");

    private static final Set<String> CUSTOM_SAFE = customSafe();

    private final Set<String> fields;
    private final boolean hashHistoryKey;

    private PlaybackFieldPolicy(Set<String> fields, boolean hashHistoryKey) {
        this.fields = fields;
        this.hashHistoryKey = hashHistoryKey;
    }

    public static PlaybackFieldPolicy apiSafe() {
        Set<String> fields = base();
        fields.remove("event");
        fields.remove("eventId");
        fields.addAll(API_SAFE_EXTRA);
        return new PlaybackFieldPolicy(fields, false);
    }

    public static PlaybackFieldPolicy webhook(WebhookConfig config) {
        String preset = config == null ? WebhookConfig.PRESET_BASIC : WebhookConfig.normalizePreset(config.fieldPreset);
        if (WebhookConfig.PRESET_ANONYMOUS.equals(preset)) return anonymous();
        if (WebhookConfig.PRESET_CUSTOM.equals(preset)) return custom(config);
        Set<String> fields = base();
        if (WebhookConfig.PRESET_STANDARD.equals(preset)) fields.addAll(STANDARD);
        if (WebhookConfig.PRESET_FULL.equals(preset)) {
            fields.addAll(STANDARD);
            fields.addAll(FULL);
        }
        return new PlaybackFieldPolicy(fields, false);
    }

    public boolean includes(String field) {
        return fields.contains(field);
    }

    public boolean hashHistoryKey() {
        return hashHistoryKey;
    }

    private static PlaybackFieldPolicy anonymous() {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(PROTOCOL);
        fields.add("historyKey");
        fields.addAll(PROGRESS);
        return new PlaybackFieldPolicy(fields, true);
    }

    private static Set<String> base() {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(PROTOCOL);
        fields.addAll(OBJECT);
        fields.addAll(PROGRESS);
        return fields;
    }

    private static PlaybackFieldPolicy custom(WebhookConfig config) {
        Set<String> fields = new LinkedHashSet<>(PROTOCOL);
        if (config == null || config.fields == null || config.fields.isEmpty()) {
            fields.addAll(OBJECT);
            fields.addAll(PROGRESS);
            return new PlaybackFieldPolicy(fields, false);
        }
        for (String field : config.fields) if (CUSTOM_SAFE.contains(field)) fields.add(field);
        return new PlaybackFieldPolicy(fields, false);
    }

    private static Set<String> customSafe() {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(OBJECT);
        fields.addAll(PROGRESS);
        fields.addAll(OPTIONAL);
        // 不含 API_SAFE_EXTRA：episodeId 是给 API 端点用的派生字段，webhook 的
        // 自定义字段白名单由 PlaybackWebhookDialog.FIELD_KEYS 驱动，那里没有它，
        // 放进这里只会造成"能被配置但界面上发现不了"的隐式放宽。
        return fields;
    }

    private static Set<String> set(String... fields) {
        return new LinkedHashSet<>(Arrays.asList(fields));
    }
}
