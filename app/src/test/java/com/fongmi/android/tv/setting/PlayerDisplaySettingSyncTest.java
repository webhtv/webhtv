package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerDisplaySettingSyncTest {

    @Test
    public void settingPagesUsePlaybackDisplayPreferences() throws Exception {
        assertSettingPageUsesDisplayPreferences(read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java"))));
        assertSettingPageUsesDisplayPreferences(read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java"))));
    }

    @Test
    public void playerOsdArrayMatchesPlaybackDisplayOptions() throws Exception {
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values", "strings.xml"))));
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values-zh-rCN", "strings.xml"))));
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values-zh-rTW", "strings.xml"))));
    }

    @Test
    public void tvScreenDisplayDialogRowsUseTheThemeAwareContrastWiring() throws Exception {
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String layout = read(sourcePath(flavor, "res").resolve(Path.of("layout", "adapter_player_osd.xml")));
            String dialog = read(sourcePath(flavor, "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "PlayerOsdDialog.java")));
            assertTrue(flavor + " 屏显行必须使用主题化行背景", layout.contains("@drawable/selector_player_settings_row"));
            assertTrue(flavor + " 屏显眼睛按钮必须使用主题化按钮背景", layout.contains("@drawable/selector_player_settings_switch"));
            assertTrue(flavor + " 屏显眼睛图标必须使用主题化前景", layout.contains("@color/player_settings_icon_tint"));
            assertFalse(flavor + " 屏显行不得回退到固定浅色卡片", layout.contains("selector_git_cloud_card"));
            assertTrue(flavor + " 屏显对话框必须应用活动主题行颜色", dialog.contains("PlayerSettingsRowTheme.apply(holder.binding.root, holder.binding.name"));
            assertTrue(flavor + " 屏显对话框必须应用活动主题图标颜色", dialog.contains("PlayerSettingsRowTheme.applySwitch(holder.binding.visible"));
        }
    }

    @Test
    public void playerOsdControllerHonorsResolutionDisplayToggle() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setTopLeft(PlayerManager player)");
        assertTrue("PlayerOsdController is missing setTopLeft", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);
        assertTrue("Player OSD should honor the shared resolution display switch", body.contains("PlayerSetting.isOsdResolution()"));
        assertTrue("Player OSD should allow title and resolution to be toggled independently", body.contains("showTitle") && body.contains("showResolution"));
    }

    @Test
    public void playerOsdControllerForcesPersistentTextWhite() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setTextSize(float sp)");
        assertTrue("PlayerOsdController is missing setTextSize", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);

        assertTrue("top-left OSD text must stay white over themed TMDB surfaces", body.contains("topLeft.setTextColor(0xFFFFFFFF)"));
        assertTrue("top-right OSD text must stay white over themed TMDB surfaces", body.contains("topRight.setTextColor(0xFFFFFFFF)"));
        assertTrue("bottom-left OSD text must stay white over themed TMDB surfaces", body.contains("bottomLeft.setTextColor(0xFFFFFFFF)"));
        assertTrue("bottom-right OSD text must stay white over themed TMDB surfaces", body.contains("bottomRight.setTextColor(0xFFFFFFFF)"));
    }

    @Test
    public void playerOsdControllerCanSuppressPersistentCornerLabels() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("public void setPersistentSuppressed(boolean persistentSuppressed)");
        int render = source.indexOf("private boolean render()");

        assertTrue("PlayerOsdController must expose persistent display suppression for embedded mobile players", method >= 0);
        assertTrue("persistent suppression should hide title/time/progress/traffic/mini labels while keeping diagnostics available",
                render >= 0
                        && source.indexOf("if (persistentSuppressed)", render) > render
                        && source.indexOf("hidePersistent();", render) > render
                        && source.indexOf("setDiagnosticsPanel(player);", render) > render);
    }

    /**
     * 诊断面板的重缓冲计数必须按「有没有 Exo analytics 快照」分派，而不是按是否 MPV。
     *
     * <p>非 Exo 时 {@code snapshot} 是 {@code Snapshot.empty()}，重缓冲恒为 0；若只对 MPV 分支，
     * IJK 会落到快照分支而永远读到 0，排查时会被误导。
     */
    @Test
    public void playerOsdDiagnosticsReadRebufferCountPerEngine() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));

        int count = source.indexOf("int rebufferCount =");
        int total = source.indexOf("long rebufferTotalMs =");
        assertTrue("the diagnostics must compute a re-buffer count and total", count >= 0 && total > count);
        String countExpr = source.substring(count, source.indexOf(';', count));
        String totalExpr = source.substring(total, source.indexOf(';', total));

        assertTrue("re-buffer count must read the engine-agnostic tracker for every non-Exo engine, not only MPV",
                countExpr.contains("player.isExo()")
                        && countExpr.contains("snapshot.rebufferCount()")
                        && countExpr.contains("player.getRebufferCount()"));
        assertTrue("re-buffer total must read the engine-agnostic tracker for every non-Exo engine, not only MPV",
                totalExpr.contains("player.isExo()")
                        && totalExpr.contains("snapshot.rebufferTotalMs()")
                        && totalExpr.contains("player.getRebufferTotalMs()"));
    }

    /**
     * 媒体信息徽标的唯一开关是「屏显设置 → 媒体信息」（默认关闭）：
     * 关闭时控制栏唤醒也不再渲染徽标（非全屏唤醒的控制栏本就拥挤），
     * 打开时控制栏隐藏同样作为常驻项展示，与标题 / 时间 / 进度 / 码率同级。
     */
    @Test
    public void mediaBadgesAreGatedByTheMediaInfoSwitchOnEveryPath() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setMediaBadges(PlayerManager player)");
        assertTrue("PlayerOsdController is missing setMediaBadges", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);

        assertTrue("media badges must stay off every non-fullscreen surface",
                body.contains("!source.isFullscreen()"));
        assertTrue("the media info master switch must gate every path, including a woken control bar",
                body.contains("!PlayerSetting.isDisplayMediaBadges()"));
        assertTrue("the persistent flag may only gate the control-bar-hidden path",
                body.contains("!controlsVisible && !PlayerSetting.isDisplayMediaBadgesPersistent()"));

        int only = source.indexOf("private boolean renderMediaBadgesOnly()");
        assertTrue("PlayerOsdController is missing renderMediaBadgesOnly", only >= 0);
        String onlyBody = source.substring(only, source.indexOf("\n    }", only));
        assertTrue("the suppressed mobile control-bar path must honor fullscreen and the master switch",
                onlyBody.contains("if (!source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges())"));

        String persistent = source.substring(source.indexOf("// 控制栏隐藏时，按用户设置显示常驻屏显"));
        String renderEnd = "private boolean renderMediaBadgesOnly()";
        int end = persistent.indexOf(renderEnd);
        assertTrue("the persistent render branch must exist before renderMediaBadgesOnly", end > 0);
        String persistentBody = persistent.substring(0, end);
        assertTrue("persistent OSD must render media badges like title/time/progress/traffic",
                persistentBody.contains("setMediaBadges(player);"));
        assertTrue("the persistent path must not force the badge strip hidden",
                !persistentBody.contains("mediaBadges.setVisibility(View.GONE)"));
        assertTrue("badges must clear the real control bar height, never a hardcoded one",
                source.contains("bottomMargin = controlsVisible ? barTop : (stacked ? clear : edgeBottom);")
                        && source.contains("private int controlBarTopInset(View bar, int edgeGap)"));
        assertTrue("the control bar height must be re-measured once its layout settles",
                source.contains("private void observeControlBar(View bar)")
                        && source.contains("addOnLayoutChangeListener")
                        && source.contains("root.post(this::applyMediaBadgePosition)"));
        assertTrue("badges must hug the screen edge when nothing else occupies their corner",
                source.contains("private View persistentNeighbour(int position)")
                        && source.contains("Math.max(0, gap - (badgeParent == null ? 0 : badgeParent.getPaddingBottom()))"));
        assertTrue("one 16dp breathing gap must serve phone and TV, control bar visible or not",
                source.contains("private int badgeGap() {")
                        && source.contains("return dp(16);")
                        && source.contains("int barTop = controlBarTopInset(bar, edgeBottom);")
                        && source.contains("bottomMargin = controlsVisible ? barTop : (stacked ? clear : edgeBottom);")
                        && !source.contains("edgeInset"));
    }

    /**
     * 「媒体信息位置」单选新增关闭项，且位置键始终保留用户最近一次选的四角：
     * 关闭只关 {@code player_osd_media_badges}，不改位置；再次开启回到那个角，
     * 从未设置过（默认关闭）时则回到默认的右下角（index 3）。
     */
    @Test
    public void mediaBadgePositionChoiceKeepsTheLastCornerAcrossOff() throws Exception {
        String setting = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "PlayerSetting.java")));
        assertTrue("the Off choice must be the 5th item of the position list",
                setting.contains("MEDIA_BADGE_POSITION_OFF = 4"));

        int choice = setting.indexOf("public static int getMediaBadgeChoice()");
        assertTrue("PlayerSetting is missing getMediaBadgeChoice", choice >= 0);
        String choiceExpr = setting.substring(choice, setting.indexOf(';', choice));
        assertTrue("the selected choice must be Off while media info is disabled",
                choiceExpr.contains("isDisplayMediaBadges()") && choiceExpr.contains("MEDIA_BADGE_POSITION_OFF"));
        assertTrue("the selected choice must be the stored corner while media info is enabled",
                choiceExpr.contains("getMediaBadgePosition()"));
        assertTrue("an unset position key must still fall back to the bottom-right corner", setting.contains("KEY_MEDIA_BADGE_POSITION, 3"));

        int apply = setting.indexOf("public static void putMediaBadgeChoice(int which)");
        assertTrue("PlayerSetting is missing putMediaBadgeChoice", apply >= 0);
        String applyBody = setting.substring(apply, setting.indexOf("\n    }", apply));
        assertTrue("choosing Off must only switch media info off and keep the remembered corner",
                applyBody.contains("putDisplayMediaBadges(false)")
                        && !applyBody.contains("putMediaBadgePosition(MEDIA_BADGE_POSITION_OFF)"));
        assertTrue("choosing a corner must turn the media info master switch on so the woken control bar shows badges",
                applyBody.contains("putMediaBadgePosition(which)") && applyBody.contains("putDisplayMediaBadges(true)"));

        Path mobileSettingPage = Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java");
        Path leanbackSettingPage = Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java");
        String[] flavors = {"mobile", "leanback"};
        Path[] settingPages = {mobileSettingPage, leanbackSettingPage};
        for (int i = 0; i < flavors.length; i++) {
            String flavor = flavors[i];
            String page = read(sourcePath(flavor, "java").resolve(settingPages[i]));
            assertTrue(flavor + " 媒体信息位置单选必须显示当前选项（含关闭）",
                    page.contains("PlayerSetting.getMediaBadgeChoice()"));
            assertTrue(flavor + " 媒体信息位置单选必须写回开启/关闭",
                    page.contains("PlayerSetting.putMediaBadgeChoice(which);"));
            assertFalse(flavor + " 媒体信息位置单选不得再用只改角位的旧入口",
                    page.contains("PlayerSetting.putMediaBadgePosition(which);"));
        }

        for (String locale : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String strings = locale.equals("values")
                    ? read(mainResPath().resolve(Path.of("values", "strings.xml")))
                    : read(mainResPath().resolve(Path.of(locale, "strings.xml")));
            int arrayStart = strings.indexOf("<string-array name=\"select_media_badge_position\">");
            assertTrue(locale + " must keep the media info position list", arrayStart >= 0);
            String array = strings.substring(arrayStart, strings.indexOf("</string-array>", arrayStart));
            int items = array.split("<item>", -1).length - 1;
            assertTrue(locale + " media info position list must offer four corners plus Off, was " + items, items == 5);
        }
    }

    /**
     * 需求：「只有全屏播放才显示媒体信息」。徽标开关与全屏判定是两条独立约束：
     * Source 需要暴露 isFullscreen()，四个宿主分别接上（两个 VideoActivity 读自己的 fullscreen 字段、
     * 详情页内嵌小窗读 inlineFullscreen），未覆盖的宿主默认 false。
     */
    @Test
    public void mediaBadgesOnlyShowInFullscreenPlayback() throws Exception {
        String controller = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int sourceIface = controller.indexOf("public interface Source");
        String sourceBody = controller.substring(sourceIface, controller.indexOf("private static volatile", sourceIface));
        assertTrue("the OSD source must expose the fullscreen state", sourceBody.contains("default boolean isFullscreen()"));

        String host = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiHost.java")));
        assertTrue("the shared player host must forward the fullscreen state", host.contains("default boolean isFullscreen()"));

        String ui = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java")));
        assertTrue("the OSD controller must be fed the host fullscreen state",
                ui.contains("public boolean isFullscreen()") && ui.contains("return host.isFullscreen();"));

        Path videoActivity = Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        for (String flavor : new String[]{"mobile", "leanback"}) {
            String page = read(sourcePath(flavor, "java").resolve(videoActivity));
            assertTrue(flavor + " 全屏状态必须传给 OSD", page.contains("return VideoActivity.this.isFullscreen();"));
        }

        String tmdb = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        assertTrue("详情页内嵌小窗只有真正全屏后才显示媒体信息", tmdb.contains("return inlineFullscreen;"));
    }

    /**
     * 「媒体信息位置」是总开关，「屏显设置 / 播放控件芯片」只控制控制栏隐藏时是否常驻。
     * 两者必须分开存：关掉屏显常驻时，全屏唤醒控制栏仍应能看到媒体信息。
     */
    @Test
    public void mediaInfoMasterSwitchIsIndependentFromThePersistentToggle() throws Exception {
        String setting = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "PlayerSetting.java")));
        assertTrue("the persistent toggle needs its own key",
                setting.contains("player_osd_media_badges_persist"));
        assertTrue("the persistent toggle must have its own accessors",
                setting.contains("isDisplayMediaBadgesPersistent()") && setting.contains("putDisplayMediaBadgesPersistent("));

        int checked = setting.indexOf("public static boolean[] getDisplayChecked()");
        String checkedExpr = setting.substring(checked, setting.indexOf(';', checked));
        int masterSwitch = checkedExpr.indexOf("isDisplayMediaBadges()");
        int persistentToggle = checkedExpr.indexOf("isDisplayMediaBadgesPersistent()");
        assertTrue("屏显设置第 8 位必须是「媒体信息总开关 AND 常驻开关」：两者以 && 相连且总开关在前，"
                        + "实际 masterSwitch=" + masterSwitch + "、persistentToggle=" + persistentToggle,
                masterSwitch >= 0 && persistentToggle >= 0 && masterSwitch < persistentToggle
                        && checkedExpr.contains("isDisplayMediaBadges() && isDisplayMediaBadgesPersistent()"));

        int putChecked = setting.indexOf("public static void putDisplayChecked(boolean[] checked)");
        String putBody = setting.substring(putChecked, setting.indexOf("\n    }", putChecked));
        assertTrue("ticking the display item must also turn the master switch on",
                putBody.contains("if (persistent) putDisplayMediaBadges(true)"));

        String dialog = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "DisplayDialog.java")));
        assertTrue("the chip must write the persistent toggle", dialog.contains("putDisplayMediaBadgesPersistent(selected)"));
        assertTrue("the chip must also enable the master switch when ticked", dialog.contains("if (selected) PlayerSetting.putDisplayMediaBadges(true)"));

        String backup = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "Backup.java")));
        assertTrue("the persistent toggle must be part of the backup set", backup.contains("player_osd_media_badges_persist"));
    }

    /**
     * 拆分前 {@code player_osd_media_badges} 就是「常驻显示」的唯一键：老用户升级后新键缺失，
     * 必须在读到新键之前把旧键值抄一次，否则常驻会静默降级成「只在控制栏唤醒时出现」。
     * 同时锁住控制栏布局监听必须在 release() 时注销，以及徽标方法对空视图的判空。
     */
    @Test
    public void legacyBadgeUpgradeKeepsPersistentDisplayAndReleasesTheBarListener() throws Exception {
        String setting = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "PlayerSetting.java")));
        int persistent = setting.indexOf("public static boolean isDisplayMediaBadgesPersistent()");
        assertTrue("PlayerSetting 缺少 isDisplayMediaBadgesPersistent()", persistent >= 0);
        String persistentBody = setting.substring(persistent, setting.indexOf("\n    }", persistent));
        int call = persistentBody.indexOf("migrateMediaBadgePersist();");
        String callStatement = call < 0 ? "" : persistentBody.substring(persistentBody.lastIndexOf('\n', call) + 1, persistentBody.indexOf('\n', call)).trim();
        assertTrue("常驻开关的方法体必须真的执行迁移语句，被注释掉或无人调用的迁移都算退回原样",
                "migrateMediaBadgePersist();".equals(callStatement));

        int migrate = setting.indexOf("private static void migrateMediaBadgePersist()");
        assertTrue("PlayerSetting 缺少 migrateMediaBadgePersist()", migrate >= 0);
        String migrateBody = setting.substring(migrate, setting.indexOf("\n    }", migrate));
        int containsKey = migrateBody.indexOf("Prefers.contains(KEY_DISPLAY_MEDIA_BADGES_PERSIST)");
        int putKey = migrateBody.indexOf("Prefers.put(KEY_DISPLAY_MEDIA_BADGES_PERSIST");
        assertTrue("迁移必须先用 contains 判空再写入，反过来会让每次读取都覆盖用户值",
                containsKey > 0 && putKey > containsKey);
        assertTrue("迁移必须把旧键值抄到新键",
                migrateBody.contains("Prefers.put(KEY_DISPLAY_MEDIA_BADGES_PERSIST, Prefers.getBoolean(KEY_DISPLAY_MEDIA_BADGES))"));

        Path prefers = catvodJavaPath().resolve(Path.of("com", "github", "catvod", "utils", "Prefers.java"));
        assertTrue("catvod Prefers 源码路径不存在：" + prefers.toAbsolutePath(), Files.exists(prefers));
        String prefs = read(prefers);
        assertTrue("迁移需要「键是否存在」的只读判定", prefs.contains("public static boolean contains(String key)"));

        String controller = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        assertTrue("控制栏布局监听必须可注销", controller.contains("removeOnLayoutChangeListener"));
        int release = controller.indexOf("public void release()");
        assertTrue("PlayerOsdController 缺少 release()", release >= 0);
        String releaseBody = controller.substring(release, controller.indexOf("\n    }", release));
        assertTrue("release() 必须注销控制栏布局监听", releaseBody.contains("stopObservingControlBar()"));
        int stopObserving = controller.indexOf("private void stopObservingControlBar()");
        assertTrue("PlayerOsdController 缺少 stopObservingControlBar", stopObserving >= 0);
        String stopObservingBody = controller.substring(stopObserving, controller.indexOf("\n    }", stopObserving));
        assertTrue("stopObservingControlBar() 必须真正调用 removeOnLayoutChangeListener",
                stopObservingBody.contains("removeOnLayoutChangeListener"));
        assertTrue("stopObservingControlBar() 必须把两个持有引用清空，否则 OSD 释放后仍被控制栏布局回调持有",
                stopObservingBody.contains("observedControlBar = null;")
                        && stopObservingBody.contains("observedControlBarListener = null;"));

        int firstVisible = controller.indexOf("private static View firstVisible(View... candidates)");
        assertTrue("PlayerOsdController 缺少 firstVisible", firstVisible >= 0);
        String firstVisibleBody = controller.substring(firstVisible, controller.indexOf("\n    }", firstVisible));
        int visibleLoop = firstVisibleBody.indexOf("for (");
        int visiblePick = firstVisibleBody.indexOf("View.VISIBLE");
        int fallbackLoop = visibleLoop < 0 ? -1 : firstVisibleBody.indexOf("for (", visibleLoop + 1);
        assertTrue("firstVisible 必须先挑可见项再退回第一个非 null，否则会选到 GONE 的容器而错误回退 dp(80)",
                visibleLoop > 0 && visiblePick > visibleLoop && fallbackLoop > visiblePick);

        int controlBar = controller.indexOf("private View controlBar()");
        assertTrue("PlayerOsdController 缺少 controlBar()", controlBar >= 0);
        String controlBarBody = controller.substring(controlBar, controller.indexOf("\n    }", controlBar));
        int barControl = controlBarBody.indexOf("R.id.control");
        int barDetail = controlBarBody.indexOf("R.id.detailControlHost");
        int barTv = controlBarBody.indexOf("R.id.playerControls");
        assertTrue("详情页内嵌播放器没有 @+id/control，控制栏候选顺序必须是 control -> detailControlHost -> playerControls",
                barControl > 0 && barDetail > barControl && barTv > barDetail);
        assertTrue("详情页内嵌播放器的底部条是 @+id/playerBottom，量高度必须二级回退",
                controller.contains("findViewById(R.id.bottom)")
                        && controller.contains("findViewById(R.id.playerBottom)"));

        int apply = controller.indexOf("private void applyMediaBadgePosition()");
        assertTrue("PlayerOsdController 缺少 applyMediaBadgePosition", apply >= 0);
        assertTrue("徽标容器缺失时必须先判空再取 LayoutParams",
                controller.substring(apply, controller.indexOf("\n    }", apply)).contains("if (mediaBadges == null) return;"));
    }

    /**
     * 手机端竖屏内嵌直播窗格不是全屏：OSD 的 Source 必须覆写 isFullscreen()，
     * 否则继承默认 true，媒体徽标会跟着出现在窗格上。电视端直播恒全屏，不需要覆写。
     */
    @Test
    public void mobileLiveOsdSourceGatesMediaBadgesOnTheEmbeddedShelf() throws Exception {
        Path liveActivity = Path.of("com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java");

        String mobileLive = read(sourcePath("mobile", "java").resolve(liveActivity));
        assertTrue("手机直播的 OSD Source 必须覆写 isFullscreen()", mobileLive.contains("public boolean isFullscreen()"));
        assertTrue("手机直播的内嵌窗格与 PiP 小窗都必须被判为非全屏",
                mobileLive.contains("return !isEmbeddedLiveUi() && !isInPictureInPictureMode();"));
        int overrideAt = mobileLive.indexOf("public boolean isFullscreen()");
        String overrideBody = mobileLive.substring(overrideAt, mobileLive.indexOf("}", overrideAt));
        assertTrue("手机直播的 isFullscreen() 必须排除 PiP：PiP 小窗不算全屏",
                overrideBody.contains("!isInPictureInPictureMode()"));
        assertTrue("isFullscreen() 覆写必须落在 PlayerOsdController.Source 匿名类体内",
                mobileLive.indexOf("public boolean isFullscreen()") > mobileLive.indexOf("new PlayerOsdController.Source()"));

        String leanbackLive = read(sourcePath("leanback", "java").resolve(liveActivity));
        assertFalse("电视直播恒全屏，不应覆写 isFullscreen()", leanbackLive.contains("public boolean isFullscreen()"));
    }

    /** catvod 是仓库内的兄弟模块，测试工作目录可能是 app 模块根，也可能是仓库根。 */
    private static Path catvodJavaPath() {
        Path moduleSibling = Path.of("..", "catvod", "src", "main", "java");
        if (Files.exists(moduleSibling)) return moduleSibling;
        Path repoRelative = Path.of("catvod", "src", "main", "java");
        assertTrue("catvod 源码目录定位失败：既不在 " + moduleSibling.toAbsolutePath() + " 也不在 " + repoRelative.toAbsolutePath(),
                Files.exists(repoRelative));
        return repoRelative;
    }

    @Test
    public void backupIncludesPlaybackDisplayPreferences() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "Backup.java")));
        assertTrue(source.contains("\"display_time\""));
        assertTrue(source.contains("\"display_traffic\""));
        assertTrue(source.contains("\"display_size\""));
        assertTrue(source.contains("\"display_progress\""));
        assertTrue(source.contains("\"display_mini\""));
        assertTrue(source.contains("\"display_title\""));
    }

    private static void assertSettingPageUsesDisplayPreferences(String source) {
        assertTrue(source.contains("PlayerSetting.getDisplayChecked()"));
        assertTrue(source.contains("PlayerSetting.putDisplayChecked(checked)"));
        assertFalse(source.contains("PlayerSetting.isOsdTitle()"));
        assertFalse(source.contains("PlayerSetting.putOsdTitle("));
    }

    private static void assertPlayerOsdArrayIncludesAllDisplayOptions(String source) {
        String array = array(source, "select_player_osd");
        assertOrdered(array, "@string/display_time", "@string/display_traffic", "@string/display_size", "@string/display_progress", "@string/display_mini", "@string/display_title");
    }

    private static void assertOrdered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int index = source.indexOf(token);
            assertTrue(token + " is missing", index >= 0);
            assertTrue(token + " is out of order", index > previous);
            previous = index;
        }
    }

    private static String array(String source, String name) {
        String startTag = "<string-array name=\"" + name + "\">";
        int start = source.indexOf(startTag);
        assertTrue(name + " array is missing", start >= 0);
        int end = source.indexOf("</string-array>", start);
        assertTrue(name + " array is not closed", end > start);
        return source.substring(start, end);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJavaPath() {
        return sourcePath("main", "java");
    }

    private static Path mainResPath() {
        return sourcePath("main", "res");
    }

    private static Path sourcePath(String sourceSet, String kind) {
        Path moduleRelative = Path.of("src", sourceSet, kind);
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", sourceSet, kind);
    }
}
