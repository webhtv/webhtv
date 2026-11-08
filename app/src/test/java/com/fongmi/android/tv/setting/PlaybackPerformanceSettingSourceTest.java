package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackPerformanceSettingSourceTest {

    @Test
    public void consolidatedProfileIdsKeepCustomValueStable() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String method = methodBody(source, "public static void applyAuto()", "public static void applyRecommended()");

        assertTrue(source.contains("PROFILE_CUSTOM = 2"));
        assertTrue(source.contains("PROFILE_LIGHTWEIGHT = 3"));
        assertTrue(source.contains("PROFILE_AUTO = 4"));
        assertFalse(source.contains("PROFILE_ORIGINAL"));
        assertTrue(method.contains("putCurrentProfile(PROFILE_AUTO)"));
    }

    @Test
    public void consolidatedPresetsApplyAutoAndLightweightDefaults() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String auto = methodBody(source, "public static void applyAuto()", "public static void applyRecommended()");
        String lightweight = methodBody(source, "public static void applyLightweight()", "private static void applyLightweightProfile");
        String kernelSource = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "KernelPerformanceSetting.java"));
        String kernelPreset = methodBody(kernelSource, "public static void applyPreset(int kernel, int profile)", "static void applyPreloadPreset");

        assertContainsAll(auto,
                "clearOverrides(kernel)",
                "applyAutoProfile(kernel)",
                "putCurrentProfile(PROFILE_AUTO)");
        assertContainsAll(lightweight,
                "clearOverrides(kernel)",
                "applyLightweightProfile(kernel)",
                "putCurrentProfile(PROFILE_LIGHTWEIGHT)");
        assertContainsAll(kernelPreset,
                "profile == PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT",
                "profile == PlaybackPerformanceSetting.PROFILE_COMPATIBLE");
        assertFalse(kernelPreset.contains("PROFILE_ORIGINAL"));
    }

    @Test
    public void performanceDialogExposesConsolidatedPresets() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));

        assertTrue(source.contains("PlaybackPerformanceSetting.applyAuto()"));
        assertTrue(source.contains("PlaybackPerformanceSetting.applyLightweight()"));
        assertTrue(source.contains("PlaybackProfileMergePolicy.selectableProfiles"));
        assertFalse(source.contains("PlaybackPerformanceSetting.applyOriginal()"));

        String catalog = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceCatalog.java"));
        assertTrue(catalog.contains("profileDescription(kernel, recommendedMerged)"));
        assertTrue(catalog.contains("自动档"));
    }

    @Test
    public void consolidatedProfilesUseShortChineseLabels() throws Exception {
        String setting = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String strings = read(sourcePath("main", "res", "values-zh-rCN", "strings.xml"));

        assertTrue(setting.contains("yield count == 0 ? \"自动\""));
        assertTrue(setting.contains("PROFILE_LIGHTWEIGHT -> \"轻量\""));
        assertTrue(strings.contains("<string name=\"player_performance_auto\">自动</string>"));
        assertTrue(strings.contains("<string name=\"player_performance_lightweight\">轻量</string>"));
    }

    @Test
    public void performanceDialogHighlightsSelectedProfile() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));
        String createView = methodBody(source, "private View createView", "private void showHelpDialog");
        String refresh = methodBody(source, "private void refresh()", "private void reset()");
        String sync = methodBody(source, "private void syncProfileTabs(TabLayout tabs)", "private boolean profileTabsMatch");
        String profileAt = methodBody(source, "private int profileAt", "private int profilePosition");

        assertContainsAll(source,
                "private TabLayout createProfileTabs()",
                "tabs.selectTab(position < 0 ? null : tabs.getTabAt(position))",
                "PlaybackProfileMergePolicy.selectableProfiles");
        assertTrue(createView.contains("profileTabs = createProfileTabs();"));
        assertTrue(refresh.contains("syncProfileTabs();"));
        assertTrue(sync.contains("profilePosition(PlaybackPerformanceSetting.getProfile())"));
        assertTrue(profileAt.contains("PlaybackPerformanceSetting.PROFILE_AUTO"));
    }

    @Test
    public void performanceDialogButtonsKeepLabelsVisible() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));
        String actionButton = methodBody(source, "private MaterialButton actionButton", "private MaterialButton closeButton");

        assertContainsAll(actionButton,
                "button.setMaxLines(1)",
                "button.setIncludeFontPadding(false)",
                "button.setPadding(dp(6), 0, dp(6), 0)",
                "TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(",
                "TypedValue.COMPLEX_UNIT_SP");
    }

    @Test
    public void exoNonEnhancedProfilesKeepMedia3DefaultLoadControl() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoUtil.java"));
        String buildPlayer = methodBody(source, "public static ExoPlayer buildPlayer", "public static MediaItem getMediaItem");

        assertTrue(buildPlayer.contains("if (PlaybackPerformanceSetting.isHighBufferEnabled()) builder.setLoadControl(buildEnhancedLoadControl());"));
        assertFalse(buildPlayer.contains("buildLoadControl()"));
    }

    @Test
    public void lateDropThresholdSwitchCanActuallyDisableDropping() throws Exception {
        // media3 defaults lateThresholdToDropDecoderInputUs to 15 ms, which is exactly the value
        // this build passes when the switch is on. If the wiring skipped the setter when the
        // switch is off, the default would keep dropping input frames and the switch would lie.
        String util = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoUtil.java"));
        String renderer = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoRuntimeAwareVideoRenderer.java"));

        assertContainsAll(util,
                "ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US = 15_000L",
                "PlaybackPerformanceSetting.isLateDropInputEnabled()",
                "C.TIME_UNSET");
        // Both renderer paths must read the one shared accessor so they cannot drift apart.
        assertContainsAll(renderer, "ExoUtil.effectiveLateDropThresholdUs()");
        assertDoesNotContainAny(renderer, "isLateDropInputEnabled()");
    }

    @Test
    public void frameSchedulingConfigurationReachesTheDiagnosticLog() throws Exception {
        // A dropped-frame report is unattributable while exported logs carry no configuration,
        // so the player build must emit one line naming what actually reached the factory.
        String util = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoUtil.java"));
        String diagnostics = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoPlaybackDiagnostics.java"));
        String settings = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoFrameSchedulingRendererSettings.java"));

        assertContainsAll(util,
                "long lateDropThresholdUs = effectiveLateDropThresholdUs();",
                "ExoPlaybackDiagnostics.logFrameScheduling(",
                "frameSchedulingSettings.codecQueueModeLabel()",
                "frameSchedulingSettings.dynamicSchedulingEnabled()");
        assertContainsAll(diagnostics, "static void logFrameScheduling(", "ExoFrameSchedulingRendererSettings.describe(");
        assertContainsAll(settings,
                "static String describe(",
                "durationToProgress=",
                "earlySchedulingUs=",
                "lateDropInputUs=",
                "\"unset\"",
                "metricScope=settings-applied-only");
    }

    @Test
    public void inputSideDropCountersReachTheDiagnosticLog() throws Exception {
        // The 「输入丢帧阈值」 switch drops decoder input frames before they are decoded. Reporting
        // only the output counters let that happen invisibly, which is the one question a
        // dropped-frame report has to answer.
        String collector = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoDiagnosticCollector.java"));

        assertContainsAll(collector,
                "droppedInputBufferCount",
                "skippedInputBufferCount",
                "droppedToKeyframeCount",
                "droppedInputBuffers are pre-decoder drops");
    }

    @Test
    public void frameLatenessHistogramDoesNotRequireExperimentEnrollment() throws Exception {
        // The per-frame lateness distribution used to be collected only for devices enrolled in
        // the frame-scheduling A/B experiment, so an ordinary report could not say how late frames
        // were. Diagnostics-on must be enough, and stop-only delivery must not be the only trigger:
        // media3 reports the offset from onStopped() alone.
        String listener = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "PlaybackAnalyticsListener.java"));
        String collector = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoDiagnosticCollector.java"));

        assertContainsAll(listener,
                "frameSchedulingExperimentActive || SpiderDebug.isEnabled()",
                "static void logFrameTimingIfDue()",
                "\"exo-frame-timing\"",
                "FRAME_TIMING_LOG_INTERVAL_MS");
        assertContainsAll(collector, "PlaybackAnalyticsListener.logFrameTimingIfDue();");
    }

    @Test
    public void telemetryNamesTheRenderedFpsScope() throws Exception {
        // 58.824 in the telemetry line is a presentation-timestamp cadence, not rendered
        // throughput; a stuttering 60 fps stream still reports it. Name the scope in the line.
        String mapper = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "PlaybackTelemetryMapper.java"));

        assertContainsAll(mapper, "renderedFpsScope=pts-cadence-estimate;not-throughput");
    }

    @Test
    public void durationProgressKeepsUpstreamDefaultUntilMigrated() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String getter = methodBody(source, "public static boolean isVideoDurationProgressEnabled()", "public static void putVideoDurationProgressEnabled");
        String recommended = methodBody(source, "private static void putRecommendedFlags()", "private static int clampProfile(int profile)");
        String init = methodBody(source, "public static void ensureInitialized()", "public static int getProfile()");

        assertTrue(getter.contains("Prefers.getBoolean(KEY_VIDEO_DURATION_PROGRESS, false)"));
        assertFalse(recommended.contains("put(KEY_VIDEO_DURATION_PROGRESS, true)"));
        assertTrue(recommended.contains("put(KEY_VIDEO_DURATION_PROGRESS, false)"));
        assertTrue(init.contains("migrateExoFrameSchedulingParity();"));
    }

    private static void assertContainsAll(String source, String... values) {
        for (String value : values) assertTrue("Missing: " + value, source.contains(value));
    }

    private static void assertDoesNotContainAny(String source, String... values) {
        for (String value : values) assertFalse("Unexpected: " + value, source.contains(value));
    }

    private static String methodBody(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String... parts) {
        Path path = Path.of("src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        return path;
    }
}
