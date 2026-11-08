# LIVE-LIST-SCRIM-OPACITY-20261029 · TV 直播列表遮盖层可配置

> **状态：已被取代（2026-10-31）** · 本任务实现的渐变形态（`GradientDrawable` / `BASE_ALPHAS = {0.80f, 0.40f, 0.05f}` / `gradientColors()`）已被
> `docs/LIVE-LIST-SCRIM-UNIFORM-20261031-live-list-scrim.md` 取代并从代码中删除。未设置槽位时现在渲染的是整块 `black_80`（`#CC000000`），
> 不再是 `black_80` / `black_40` / `black_05` 三段。本文件以下关于三段的描述与数值表是 2026-10-29 当轮的历史记录，不代表当前行为。

## Recovery anchor

- 目标：让 TV（leanback）与 mobile 直播页的频道抽屉遮罩像「播放菜单遮盖层」那样可调，解决用户反馈的「太透、远一点看不清」（图一）。
- 验收标准：
  1. 主题层新增第 5 个不透明度槽 `LIVE_LIST_SCRIM_OPACITY`（profile 字段 `liveListScrimOpacity`），编辑器「图层」组出现滑块，区间 20%..100%。
  2. 槽位未设置时渲染结果与现状逐值一致（`shape_live_list` 的 black_80 / black_40 / black_05）。
  3. leanback 与 mobile 两个 flavor 的直播页抽屉都从同一槽位取值；mobile 的 classic / embedded 形态行为不变。
  4. 只缩放 alpha，不换色相（焦点环对比度前提见 `shape_live_focused.xml`）。
- 分支 dev4，base HEAD `ba802dc4327dcb2433dfecb2f2dfd5c591621e86`。
- guard scope：theme 包、两个 LiveActivity、三个 strings.xml、test/testLeanback 的 theme 测试、docs。

## 问题定位

用户实拍图（m00071）显示：TV 直播页打开频道抽屉后，左列分组与频道文字几乎不可读；图二（m00073）是用户自行降低遮罩后的可读效果。

遮罩不是 Java 动态生成，而是布局静态绑定的 drawable：

- `app/src/leanback/res/layout/activity_live.xml:56` — 抽屉容器 `@+id/recycler`，`android:background="@drawable/shape_live_list"`。
- `app/src/leanback/res/drawable/shape_live_list.xml` — `<gradient android:angle="0" startColor=@color/black_80 centerColor=@color/black_40 endColor=@color/black_05 type="linear"/>`，即左边缘 80% 黑 → 中间 40% → 右边缘 5% 黑。左侧 80% 黑正是「远看不清」的直接原因。
- `app/src/mobile/res/layout-land/activity_live.xml:55` 与 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:2278`（`applyLiveListStyle()` 的 overlay 分支）复用同一份 drawable。
- leanback 的 `LiveActivity` 里没有任何代码给 `mBinding.recycler` 设背景（只有 `:318-321` 的 `applyPlaybackOverlay()` 作用于控制条），所以抽屉遮罩完全由 XML 静态决定。

## 既有可配置范式（本次照它做）

`app/src/main/java/com/fongmi/android/tv/theme/PlaybackMenuScrim.java`：

- `:19` `DEFAULT_OPACITY = 0.80f`，`:22` `TOP_RATIO = 0.75f`
- `:30` `create(Context)` 读 `ThemeController.configuredPlayerScrimOpacity()`，未设置回落 `DEFAULT_OPACITY`，只缩放 alpha 不改色相
- `:40` `resolveOpacity(Float)`、`:45` `gradientColors(int rgb, float opacity)`、`:50` `withAlpha(int rgb, float opacity)`

配套槽链路：`ThemeProfile.SlotSet.playerScrimOpacity`（`:106`）→ `ThemeProfileValidator` 的 `MIN/MAX_PLAYER_SCRIM_OPACITY`（`:20-21`，0.20f..1f）→ `ThemeController.configuredPlayerScrimOpacity(boolean light)`（`:122`）→ `ThemeEditor.Slot.PLAYER_SCRIM_OPACITY`（`:29`，`isColor()` 靠 `ordinal() <= WARNING.ordinal()`，故 opacity 槽必须追加在 WARNING 之后）→ `ThemePreviewView` 的「图层」组（`:55-63`）与 `minimumOf/maximumOf/labelOf`（`:296-335`）。

约束：`PlaybackMenuScrim` 的错误用法已被 `ThemeBinderContractTest` 用源码字符串断言锁定（`applyScrimOpacity` 未设置必须 no-op、不得出现 `colorScrim()`）；新槽沿用同一约定——**未设置返回 null，不得回落到具体数值**，避免把默认值固化成配置。

## 设计决策（已与用户确认，均为推荐项）

1. **独立槽而非复用 playerScrimOpacity**：播放菜单是底部横向幕、直播抽屉是左侧纵向列表，可读性诉求不同；混在一个滑块里任一次微调都会牵连另一处。
2. **默认值 = 现状**：未设置时按 `DEFAULT_OPACITY = 0.80f` 渲染，输出与 `shape_live_list` 的 black_80/black_40/black_05 逐值一致，零观感回归；滑块可上调到 100%（三段全程更高不透明度）。
3. **TV + mobile 同槽**：槽位属主题层，只接一个 flavor 会让同名遮罩在两个 flavor 上语义分叉。mobile 只影响 overlay 形态（classic / embedded 不动）。

## 实现方案（最小面）

新增 `LiveListScrim`（包 `com.fongmi.android.tv.theme`），镜像 `PlaybackMenuScrim`：

- `DEFAULT_OPACITY = 0.80f`，`BASE_ALPHAS = {0.80f, 0.40f, 0.05f}`（对应 black_80/black_40/black_05）
- `create(Context)`：`ThemeController.configuredLiveListScrimOpacity(!ThemeController.isNight(context))` → `GradientDrawable(Orientation.LEFT_RIGHT, gradientColors(opacity))`
- `gradientColors(float)`：`scale = clamp(opacity,0,1) / DEFAULT_OPACITY`，逐项 `withAlpha(min(1f, BASE_ALPHAS[i] * scale))`，颜色恒为纯黑
- 数值基准：未设置 → `{0xCC000000, 0x66000000, 0x0D000000}`；1.00 → `{0xFF000000, 0x80000000, 0x10000000}`；0.20 → `{0x33000000, 0x1A000000, 0x03000000}`（以上三段渐变实现已于 20261031 任务删除，见上述状态横幅）

槽位链路镜像：`ThemeProfile.SlotSet.liveListScrimOpacity`（Float，默认 null）→ validator `MIN/MAX_LIVE_LIST_SCRIM_OPACITY = 0.20f/1f` → controller `configuredLiveListScrimOpacity(boolean light)` → `ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY`（最后一个常量）→ `ThemePreviewView` 图层组 + `minimumOf/maximumOf/labelOf`（`labelOf` 的 switch 无 default，漏 case 会编译失败）→ 三 locale 字符串 `theme_editor_slot_live_list_scrim_opacity`。

接入点：

- `app/src/leanback/java/.../LiveActivity.java`：`initView()` 内新增 `applyLiveListScrim()`，内容 `mBinding.recycler.setBackground(LiveListScrim.create(this));`。**故意不放进 `applyPlaybackOverlay()`**，因为 `app/src/test/java/com/fongmi/android/tv/setting/SettingPlaybackOverlayTest.java` 对该方法做源码字符串断言；且抽屉遮罩与 `Setting.isPlaybackOverlayEnabled()` 无关，必须无条件生效。
- `app/src/mobile/java/.../LiveActivity.java`：`applyLiveListStyle()` 仅把 overlay 分支的 `setBackgroundResource(R.drawable.shape_live_list)` 换成 `setBackground(LiveListScrim.create(this))`；非 overlay 分支与 `liveCurrent` 背景逻辑不动。

## 未纳入本次（记录为后续可选）

- `app/src/main/baseline-prof.txt` 里有 `PlaybackMenuScrim` 条目；补齐 `LiveListScrim` 属于性能基线维护，不在本次 guard scope 内，未做。
- `shape_live_list.xml` 仍保留为 XML 默认背景（布局静态引用不动），运行时由 Java 覆盖；这样未接入主题的入口（如有）回落到现状而不是透明。

## 验证

### 双 flavor 编译

`testLeanbackArm64_v8aDebugUnitTest` 与 `testMobileArm64_v8aDebugUnitTest` 的 `compile*JavaWithJavac` 均通过；两个 flavor 的 `LiveActivity` 都成功解析到 `com.fongmi.android.tv.theme.LiveListScrim`（若该类缺失会在此编译失败）。

### 主题相关单测（两个 flavor 各跑一次）

| 套件 | leanback | mobile |
| --- | --- | --- |
| `LiveListScrimTest`（本次新增） | 7/0/0 | 7/0/0 |
| `LiveListScrimWiringTest`（本次新增） | 7/0/0 | 7/0/0 |
| `ThemeEditorPanelTest`（testLeanback 独有） | 2/0/0 | — |
| `ThemeEditorContractTest` | 13/0/0 | 13/0/0 |
| `ThemeProfileCodecTest` | 10/0/0 | 10/0/0 |
| `ThemeProfileValidatorTest` | 11/0/0 | 11/0/0 |
| `ThemeProfileMigrationTest` | 8/0/0 | 8/0/0 |
| `ThemePresetsTest` | 9/0/0 | 9/0/0 |
| `PlaybackMenuScrimTest` | 5/0/0 | 5/0/0 |
| `ThemeBinderContractTest` | — | 26/0/0 |
| `SettingPlaybackOverlayTest` | 3/0/0 | 3/0/0 |

（格式：tests/failures/errors；mobile 侧 `com.fongmi.android.tv.theme.*` 全部 27 个套件零失败。）

### 全量 leanback 单测的既有失败（与本任务无关）

`:app:testLeanbackArm64_v8aDebugUnitTest` 全量 4751 个用例中 2 个失败，二者都在 `androidx.media3.mpvplayer`：

- `MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile` → `java.io.IOException: Unable to commit fontconfig configuration`（`MpvFontConfig.java:74`，本机沙箱无法 rename 提交 fontconfig）
- `MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation` → `AssertionError` at `MpvHlsCacheCoordinatorTest.java:208`

这两条是既有环境性失败，同一台机器在 `docs/C108-beta-merge-review-dev4-20261028.md` 就已记录（同样两条、同样的 `MpvFontConfigTest.java:53` 与 `MpvHlsCacheCoordinatorTest.java:208`），本任务未触碰 `androidx/media3/mpvplayer` 任何文件。

### 源码审查要点（协调者独立复核，非采信子代理自述）

- `LiveListScrim.gradientColors` 基准值逐条复算：0.80 → `{0xCC000000, 0x66000000, 0x0D000000}`（等于 `black_80/black_40/black_05`）；1.00 → `{0xFF000000, 0x80000000, 0x10000000}`；0.20 → `{0x33000000, 0x1A000000, 0x03000000}`；`withAlpha` 仅 `alpha << 24`，RGB 恒 0。
- `ThemeEditor.Slot` 共 19 个常量，`LIVE_LIST_SCRIM_OPACITY` 是最后一个，`isColor()` 的 `ordinal() <= WARNING.ordinal()` 因此把它判为 opacity 槽；6 个穷举 switch 全部补齐（`labelOf` 无 default 分支，漏 case 即编译失败）。
- 未设置槽位严格 no-op：`ThemeController.configuredLiveListScrimOpacity` 未设置返回 `null`（不回落到数值），`ThemeProfile` 新字段无默认值。注意 `ThemeProfileCodec` 用 `serializeNulls()`，编码 JSON 里键存在但值为 `null`——"不烘焙默认值"应断言为 `assertNull`，不能写成"键不存在"。
- 两个 `LiveActivity` 的 diff 均为最小面：leanback `+6`（import / initView 一行 / 新私有方法），mobile `+2/−1`（import / overlay 分支一行）；`applyPlaybackOverlay()` 方法体与 mobile 的 classic / embedded 分支、`liveCurrent` 逻辑一字未改。
- `git grep -n shape_live_list -- app/src` 改后仅剩 3 条：两处 layout 静态引用（保留作 XML 回落）+ `shape_live_focused.xml:7` 的注释文字，Java 侧零引用。

## 真机验证（emulator-5560, Android 9 / HD1910, 1080x1920 竖屏）

已验证的构建：设备上安装的 `com.silent.android.webhtv` base.apk = 204991032 字节，与
`app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk` 字节数一致，
安装时间 2026-10-30 00:46:30 晚于该 APK 的 00:45:36，即 HEAD `bcdd344ec5c` 的产物。

**入口**：直播页抽屉不是 DPAD_LEFT 打开，而是 `LiveActivity.onKeyCenter()`（`LiveActivity.java:1361-1364`）调 `showUI()`（`:610-616`）；`onToggle()`（`:850-855`）是另一条切换路径。

**像素量化**（截图 y=30 行，抽屉右端 bg G=133 处反解合成 alpha = 1 − G/133）：

| profile `liveListScrimOpacity` | x=0 | x=400 | x=1000 |
|---|---|---|---|
| `1.0`（设备原值） | 1.000 | 0.624 | 0.113 |
| `null`（未设置 → 回落 0.80） | 0.797 | 0.496 | 0.083 |
| `0.5` | 0.504 | 0.308 | 0.053 |

结论：
1. 三档单调可调，且 `null` 档左端实测 0.797 ≈ `shape_live_list` 的 `black_80`(0xCC=204/255=0.800)，即**默认路径与改动前逐值一致**，零观感回归。
2. 抽屉背景确实由 `LiveListScrim` 在 Java 层绘制，不再是 XML 静态绑定——`1.0` 档左端实测 1.000 只可能来自 `LiveListScrim.withAlpha(1.0)`，XML 的 `black_80` 上限是 0.80。

**编辑器滑块**：设置 → 外观与语言 → 主题色彩 → 滚到底「透明度与浮层」组，第 5 行「直播列表遮盖层不透明度」可见。设备原值显示 `100%`（profile 已存 1.0，故无「· 自动」后缀；相邻的播放菜单滑块显示 `80% · 自动`）。点按轨道后显示 `68%`，状态栏由「正在编辑已保存的配色·修改后可直接预览」变为「仅在本页预览，尚未保存」，说明滑块双向可写且脏标记生效。

**设备状态复原**：验证期间通过替换 shared_prefs 制造了 `null` / `0.5` 两档，结束时已把原始 prefs 推回；比对 `theme_profile_v2_json` 字段得 `profileEqual=True`、无 `0.68` 残留，即设备主题配置与验证前逐字节一致。本地 `.dsh-verify/` 临时目录已删除，`git status --porcelain` 为空。
### mobile flavor（emulator-5560，同一提交 `bcdd344ec5c` 的 mobile-arm64 APK）

实测结论：**embedded 分支行为不变，overlay 分支无法在本机触发**。

- 安装 `app-mobile-arm64_v8a-debug.apk`（`adb install -r`）后启动 `LiveActivity`，竖屏进入
  `isEmbeddedLiveUi()` = true 的分支（`LiveActivity.java:1023-1025`：非横屏且非旋转且非画中画）。
  截图确认左列分组是各自带圆角背景的卡片（`selector_live`），**容器上没有黑渐变遮罩**，
  与 `applyLiveListStyle()` 的 else 分支（`LiveActivity.java:2280-2282`，classic→`R.color.transparent`、
  非 classic→`shape_live_embedded_list`）一致，即 embedded 形态未被新槽位影响。
- overlay 分支（`LiveActivity.java:2278-2279` 调 `LiveListScrim.create(this)`）只在横屏生效，
  本机无法验证：`settings put system user_rotation 1` 无效（`SurfaceOrientation` 恒为 0）、
  `adb emu rotate` 连不上 5560 控制台（连接被拒）、`wm size 1920x1080` 只产生黑边 letterbox
  而不改变 `mAppBounds`（仍为 1080x1920 port）。该分支目前仅经代码审查 + 单元测试覆盖。

**设备复原**：验证后已 `adb install -r` 装回 leanback APK（`lastUpdateTime=10:48:10`）、
把 leanback 的 prefs 推回、`user_rotation` 复位 0、`wm size` 已 reset（`Physical size: 1080x1920` 无 Override）、
删除 `/sdcard/*.png` 与本地 `.dsh-verify/`。复原后 profile 中 `liveListScrimOpacity` 仍为 `1.0`，与验证前一致。

## 后续可选

- `app/src/main/baseline-prof.txt` 未补 `LiveListScrim` 条目（性能基线维护，不在本次 guard scope）。
- 若用户希望"升级即更清楚"，可把 `LiveListScrim.DEFAULT_OPACITY` 或 profile 默认值调高——本次按决策 2 保持等于现状。