# C101-beta-merge-review-dev4-20261023

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`（本轮实测落后 3 个提交，已产生真实合并提交），评审 `dev4` 已提交未推送
  的代码（含 `beta` 新合入的「播放菜单遮盖层主题槽」与上一轮的 E-SP12 掉帧口径修复），修复必修项并验证，
  修复后再次评审直至通过，然后提交、推送 `dev4`、更新到 `beta` 的中文 PR（只创建/更新，不合并），最后拉取远端最新代码。
- **验收标准**：
  1. `dev4` 相对 `origin/beta` 落后 0 个提交（`origin/beta` 已是 HEAD 祖先），合并没有丢失 `beta` 侧任何路径、
     也没有夹带评审外的改动。
  2. 评审覆盖 `origin/beta...HEAD` 的净差异（9 个路径 / +655 −15）以及合并带入的 20 个路径 / +224 −12，
     每个结论都有可复核证据；上一轮已评审且本轮合并未触碰的 E-SP12 路径按用户约定跳过，并写明跳过依据。
  3. 每个必修项均已修复；修复有回归用例或负向对照证明其真实有效；修复后复评通过。
  4. 定向单测全绿（`com.fongmi.android.tv.theme.*` + `SettingPlaybackOverlayTest` + E-SP12 两个测试类，
     合计 29 个测试类 / 209 个用例 / 0 失败 0 错误）、`:app:compileMobileArm64_v8aDebugJavaWithJavac` 通过、
     `scripts/check_ui_tokens.py` PASS、`git diff --check` 为空。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev4`；更新到 `beta` 的中文 PR（含具体改动内容）；
     随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src`、`docs`、`scripts`（本轮实际写入仅新增本评审文档）
- **任务守卫**：`C101-BETA-MERGE-DEV4-20261023`，base HEAD `e377db6cf20493d5b8b607ca4f2eed74ed3c4468`，branch `dev4`，
  受保护既有脏路径 0 条（工作树在会话开始时干净）
- **当前状态**：合并核对、评审、复核、验证均完成；提交/推送/PR/拉取见 §8
- **下一动作**：无；等待 PR 评审/合并（本轮只更新 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `db4a96cec32c51ad68138ed7c8b4c106e71fa529`（"Merge pull request #483 from Silent1566/dev2"，2026-10-23 14:5x `git fetch origin --prune --tags` 取得） |
| 合并前本地 | `dev4` = `36c6ed5324c2da61fbdef90748376bc95d7fa521`（上一轮 E-SP12 复评修复，已推送） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false**（落后 3 个提交） |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **3 / 4** |
| `beta` 独有提交 | 3 个：`29d357d882d`（feat(theme): 播放菜单遮盖层强度接入主题色彩，第 17 槽 `playerScrimOpacity`）、`dcf9828cba6`（merge: 拉取远端 beta 最新代码）、`db4a96cec32`（PR #483 合并） |
| 冲突预检 | `git merge-tree --write-tree HEAD origin/beta` 仅返回一个 tree `26266d0ccb3…`，无冲突段 |
| 合并动作 | `git merge --no-ff origin/beta` → **`e377db6cf20493d5b8b607ca4f2eed74ed3c4468`**（"merge: 合并远端 beta（db4a96cec32，PR #483 播放菜单遮盖层主题槽）到 dev4"，父提交 `36c6ed5324c` + `db4a96cec32`，**20 个路径 / +224 −12，0 冲突**） |
| 祖先关系（合并后） | `git merge-base --is-ancestor origin/beta HEAD` → **true** |
| 落后 / 领先（合并后） | **0 / 5**（5 = 2 个合并提交 + 上一轮 3 个 E-SP12 提交） |
| 合并纯净性 | `git diff --name-only ea678c0fd44 db4a96cec32`（`beta` 侧贡献）与 `git diff --name-only 36c6ed5324c e377db6cf20`（合并带入）**逐条相等，均 20 条** → 合并只带入 `beta` 内容，无夹带、无遗漏 |
| 合并是否触碰 E-SP12 路径 | **否**。对 `app/src/main/java/com/fongmi/android/tv/player`、`.../ui/custom`、`app/src/test/java/com/fongmi/android/tv/player`、两份 E-SP12 文档做 pathpec 过滤 → 结果为空 |

需评审的未推送提交（5 个）：

| 提交 | 说明 | 是否本轮新评审 |
| --- | --- | --- |
| `29d357d882d` | feat(theme) 播放菜单遮盖层强度接入主题色彩（PR #483 实质内容） | **是**（`dev4` 线此前未含此代码） |
| `dcf9828cba6` / `db4a96cec32` | `beta` 侧合并提交，无独立代码 | 内容已随上面一处覆盖 |
| `fd5bcf109ee` | fix(E-SP12) 掉帧按播放时长归一 | 否（上一轮已完整评审，见 `docs/E-SP12-exo-pause-settled-dropped-frames.md` §4/§5 与本轮 §2 跳过依据） |
| `82a2731f4ef` | docs(E-SP12) 补记模拟器实测证据 | 否（同上，仅文档） |
| `36c6ed5324c` | review(E-SP12) 修 `PlayerOsdController` javadoc 错位 + 复核记录 | 否（上一轮评审即产出该修复，本轮 §5 复核其未回退） |

结论：本轮「合并远端 beta 最新代码」产生了真实合并提交 `e377db6cf20`，`beta` 侧 3 个提交全部进入 `dev4`；
唯一需要新评审的是 `29d357d882d` 带来的 20 个路径。

## 2. 评审范围与跳过依据

**本轮全量评审对象**：`29d357d882d`（`beta` → `dev4` 新合入内容），按 `origin/beta...HEAD` 净差异 + 合并带入双口径核对：
**净差异 9 个路径 / +655 −15**，其中 20 个路径的 `beta` 新增内容按合并带入单独评审。

| 路径 | +/- | 评审结论 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/theme/PlaybackMenuScrim.java`（新增） | +54 | 全文件读完：默认 0.80、顶部 0.75×、顶部圆角 8dp、`null` 上下文返回 `null`、`resolveOpacity` 与 `gradientColors` 双重 clamp、`withAlpha` 四舍五入 —— 见 §4-1 |
| `.../theme/ThemeController.java` | +17 −2 | `configuredPlayerScrimOpacity` 与既有 `configuredScrimOpacity` 同构；`isNight(context)` 读 Activity 配置而非系统位 —— 见 §4-2 |
| `.../theme/ThemeEditor.java` | +9 −1 | 枚举新增 `PLAYER_SCRIM_OPACITY`，`valueOf`/`setOpacity`/`clamp` 三处 switch 均补齐，`setColor` 走 `default -> throw` 保持互斥 —— 见 §4-3 |
| `.../theme/ThemePreviewView.java` | +7 −1 | 透明度行数组、自动值、最小/最大、文案四处齐全 —— 见 §4-4 |
| `.../theme/ThemeProfile.java` | +10 −1 | 新增 `Float playerScrimOpacity` 并进入 `copy()`；`SlotSet` 无位置构造器，加字段不破二进制/源码 —— 见 §4-5 |
| `.../theme/ThemeProfileValidator.java` | +3 | `MIN_PLAYER_SCRIM_OPACITY=0.20`/`MAX=1.00` 并接入 `validateSlots` —— 见 §4-6 |
| `app/src/{leanback,mobile}/.../ui/activity/{LiveActivity,VideoActivity}.java`（4 个） | 各 +4 −2 | `applyPlaybackOverlay()` 由 `setBackgroundResource` 改 `setBackground(PlaybackMenuScrim.create(this))`；mobile 保留 `control.getRoot()` 透明不变 —— 见 §4-7 |
| `app/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml` | 各 +1 | 三套 locale 文案齐全，key 唯一 —— 见 §4-8 |
| `app/src/test/**`（6 个测试文件） | +102 −6 | `PlaybackMenuScrimTest` 5 例、`ThemeProfileCodecTest`+`ThemeProfileValidatorTest`+`ThemeEditorContractTest` 范围与往返、`ThemeEditorPanelTest` 滑块初值 75、`SettingPlaybackOverlayTest` 源码契约 —— 见 §4-9 |
| `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` | +15 −1 | 第 17 槽行、3→4 个透明度槽、校验规则 5、新增 §9 —— 见 §4-10 |

**净差异中属上一轮已评审的路径（按用户约定跳过重评）**：`ExoPlayerEngine.java`(+4−2)、`DroppedFrameAccounting.java`(+108 新)、
`ExoDecoderRuntimeSession.java`(+34−2)、`PlaybackAnalyticsListener.java`(+51−8)、`PlayerOsdController.java`(+42−2)、
`DroppedFrameAccountingTest.java`(+103 新)、`ExoDecoderRuntimeSessionTest.java`(+62−1)、`docs/E-SP12-*.md`(+250 新)、
`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`(+1)。

**跳过依据（可复核）**：`git diff --name-only 36c6ed5324c e377db6cf20` 对上述 E-SP12 路径的过滤结果为空 —— 本轮合并没有
触碰它们一个字节，因此上一轮评审结论仍然覆盖当前全部代码；且上一轮评审已完成「发现问题 → 修复 → 复评通过」闭环
（`36c6ed5324c`，含 `PlayerOsdController` javadoc 错位修复与 `docs/E-SP12-*.md` §5 复核记录）。符合用户「已评审过且
没有合入冲突可以跳过」的前提。

## 3. 评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| F1 | 信息（原判「中」，复核后降级） | `PlaybackMenuScrim.java:19` `DEFAULT_OPACITY = 0.80f` 与四个 Activity 的 `applyPlaybackOverlay()` | 旧观感是 `shape_controller_scrim`（顶部 20% → 底部 50% 黑，`#33000000`→`#80000000`），新默认是顶部 60% → 底部 80% 黑（0x99→0xCC）；而 `Setting.isPlaybackOverlayEnabled()` 默认 `true`（`Setting.java:1464-1466` `Prefers.getBoolean("playback_overlay_enabled", true)`），即所有用户在不改主题的情况下都会看到更重的黑幕。 | **不修改，记录为有意变更**。依据三条：(1) 该默认值是 `beta` 侧 `29d357d882d` 的明确设计，其文档 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` §9 已写"默认 80% → 顶部 60% / 底部 80%"；(2) `ThemeController` javadoc 只声明老 profile 的回落是"确定的"，并未承诺像素与旧 drawable 相同，声明与实现一致；(3) 在评审任务里替另一个任务改视觉默认属于未授权的 UX 变更（`AGENTS.md` §2 scope 封闭）。此处按"把前后值写进记录"的方式交付，避免后续被误判为回归。 |
| F2 | 低（复核后驳回） | `ThemeController.java:122-130` `configuredPlayerScrimOpacity` | 读路径 clamp 到 `0..1`，而校验器范围是 `0.20..1.00`，理论上读端会接受写端拒绝的 0.00–0.19。 | **无缺陷，不改**。同级既有 `configuredScrimOpacity`（`ThemeController.java:105-113`）同样是"读端 0..1、校验端 `MIN_SCRIM_OPACITY=0`/`MAX=0.85`"的写法，这是本仓库既有模式；且 `ThemeProfileCodec.parse()` 先 `validate` 后返回 profile，越界值根本进不了内存，读端宽 clamp 只是兜底。 |
| F3 | 信息（不改） | `PlaybackMenuScrim.java:33` `ThemeController.current().colorPlayerScrim()` | 运行期重新读 token，若调用前 `ThemeController` 未刷新，色相可能不是最新主题色。 | **不改**。token 的 RGB 在明暗两张表里都是 `000000`（`values/webhtv_tokens.xml:60` `#7A000000`、`values-night/webhtv_tokens.xml:78` `#8F000000`），"色相固定黑"的契约与是否刷新无关；且该路径与既有 `shape_controller_scrim` 一样只在 `onCreate` 期取一次，属既有模式。 |
| F4 | 信息（不改） | 4 个 Activity 的新增 import | `import com.fongmi.android.tv.theme.PlaybackMenuScrim;` 插在 `setting.*` 块中间（如 leanback `LiveActivity.java:55` 位于 `Setting` 与 `PlayerButtonSetting` 之间），未按字母序。 | **不改**。仓库无 import 顺序门禁（`check_ui_tokens.py` 与 `gradle` 均不校验），且属于他人提交的纯排版，改动只会制造与 `beta` 的无意义分歧（`AGENTS.md` §2 "no formatting sweep"）。记录备查。 |

**本轮必修项：0 个。** 全量评审未发现正确性、生命周期、兼容性、资源覆盖或测试契约层面的缺陷；
上一轮唯一的必修项（`PlayerOsdController` javadoc 错位）已在 `36c6ed5324c` 修复，本轮复核未回退（§5）。

## 4. 复核未发现问题的项（正面证据，非「没看」）

1. **`PlaybackMenuScrim.java`（54 行全文读完）**：`create(Context)` 对 `null` 上下文返回 `null`；`rgb` 先 `& 0x00FFFFFF` 去 alpha 再交给 `gradientColors`；`resolveOpacity` 对 `null` 回落 `DEFAULT_OPACITY` 后再次 clamp，因此即便调用方传入 `Float` 异常值也不会产生 alpha 溢出；`withAlpha` 用 `Math.round(opacity * 255)` 保证 0.80→0xCC、1.0→0xFF；`setCornerRadii(new float[]{r,r,r,r,0,0,0,0})` 与旧 drawable 的"仅顶部两角 8dp"逐值一致（`CORNER_DP = 8f` × density）。
2. **`ThemeController` 语义与既有约定一致**：`configuredPlayerScrimOpacity(boolean light)` 的入参 `light` 由调用方 `!ThemeController.isNight(context)` 提供，与 `isNight` javadoc（第 75-81 行"Activity configuration 已反映强制的 theme_mode，普通 UI 必须问本方法而不是裸系统夜间位"）自洽；`applyNightModeToApp()`（第 52-64 行）在 leanback 默认深色，因此 TV 上取到的是 `dark` 槽，行为可预测。
3. **`ThemeEditor` 穷尽性**：`Slot` 枚举 18 项；`valueOf`（第 140-163 行）`switch` 表达式无 `default` 覆盖全部 18 项；`setOpacity`（第 185-193 行）与 `clamp`（第 196-205 行）都显式处理 `PLAYER_SCRIM_OPACITY` 并保留 `default -> throw`；`isColor()` 用 `ordinal() <= WARNING.ordinal()`，新槽排在 `OVERLAY_OPACITY` 之后，仍是透明度槽 —— 三者互不矛盾，未来新增槽会编译失败而不是静默落空。
4. **`ThemePreviewView`**：透明度行数组（第 56-58 行）含新槽；自动值 `case PLAYER_SCRIM_OPACITY -> PlaybackMenuScrim.DEFAULT_OPACITY;`（第 243 行）与 min/max（第 300/309 行取校验器常量）自洽；文案 key `theme_editor_slot_player_scrim_opacity` 指向已新增字符串。
5. **`ThemeProfile` 向后兼容**：`SlotSet` 只有无参构造（`ThemePresets.java:91`、`ThemeProfile.java:25/26/52/55/108`、`ThemeProfileCodec.java:51/52`、`ThemeProfileValidator.java:49/50` 全部 `new ThemeProfile.SlotSet()`），新增字段不破坏任何位置构造或序列化顺序；`copy()` 已复制该字段（否则编辑器改动会在 Apply 时丢失，这是本槽最容易漏的一处，实测已复制）。
6. **`ThemeProfileValidator` 范围**：`MIN_PLAYER_SCRIM_OPACITY = 0.20f` / `MAX_PLAYER_SCRIM_OPACITY = 1f` 并接入 `validateSlots`；`opacity(...)` 对非有限值与非范围值都追加错误并返回原值，最终 `validate()` 在有错误时返回 `profile=null`，因此 `ThemeEditor`/`ThemeProfileCodec` 两条写入路径都会被拒。
7. **四个 Activity 的接线**：leanback `LiveActivity.java:319`、`VideoActivity.java:1584` 与 mobile `LiveActivity.java:316`、`VideoActivity.java:11469` 形状一致；`if/else` 取代三元表达式后 `setBackgroundResource(R.color.transparent)` 分支保留，关闭遮盖层时行为与改动前逐字等价；mobile 侧 `control.getRoot()` 的透明设置保持原位未动，避免把根容器也刷成黑幕。`SettingPlaybackOverlayTest.java:67/73` 已把两条源码契约改成 `setBackground(PlaybackMenuScrim.create(this))`，测试与实现同步。
8. **资源覆盖**：`app/src/main/res` 仅 `values`/`values-night`/`values-zh-rCN`/`values-zh-rTW` 四类（`git ls-files` 实测），新字符串在三套 `strings.xml` 齐全，无遗漏 locale；`values-night` 只放颜色，不需要文案。
9. **测试契约真实有效**：`PlaybackMenuScrimTest` 断言 `DEFAULT_OPACITY` → `0x99000000`/`0xCC000000`、`1f` → `0xBF000000`/`0xFF000000`、`0f` → `0x00000000`，这组数字与 §4-1 的手算一致（0.80×0.75=0.60→153=0x99；0.80→204=0xCC；1×0.75→191=0xBF），因此用例不是"跟着实现抄的常量"；`ThemeEditorPanelTest` 断言滑块 progress 75（=(0.80−0.20)/0.80），与本轮实测 2 例全绿一致。
10. **文档一致性**：`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 1154/1156 行给出入口、默认值与验证文件清单，与代码逐条对得上（`ThemeController.configuredPlayerScrimOpacity`、`PlaybackMenuScrim`、6 个测试类名）；`shape_controller_scrim` 未被删除，仍被 `activity_tmdb_detail.xml:1344` 与 `TmdbDetailActivityLayoutTest.java:774` 使用，因此 `PlaybackMenuScrimTest` 里"源码不含 `shape_controller_scrim`"的断言只约束新类，不与旧资源冲突。
11. **`Setting.isPlaybackOverlayEnabled()` 关闭路径**：`else` 分支恢复 `R.color.transparent`，与改动前的 `Setting.isPlaybackOverlayEnabled() ? R.drawable.shape_controller_scrim : R.color.transparent` 语义等价 —— 关闭遮盖层的用户观感未变。
12. **`check_ui_tokens.py` 门禁**：`UI_TOKEN_STATUS PASS`（`layouts=385 hex_layouts=0 drawables=563 hex_drawables=1 colors=64 hex_colors=0 allowlisted=190`、`SCOPE stage=A violations=1 legacy=0`、`CONTRAST pairs=38 failures=0 min=4.28`），新代码以 `colorPlayerScrim` 取色、未引入裸 hex，符合该门禁的 token 化要求。

## 5. 复核（本轮无代码修复，改为一致性与未回退核对）

- **上一轮必修项未回退**：`git show HEAD:app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` 复核 `:503-506` 是
  `getStartupText` 的 javadoc 且 `:507` 紧跟其方法体，`:514-519`/`:527-531` 分别是 `droppedFrameText`/`excessiveDroppedFrames` 的
  javadoc —— 即 `36c6ed5324c` 的修复仍在 HEAD 内，未被本轮合并覆盖（该路径也不在合并路径集中，见 §1）。
- **一致性对照（替代负向对照）**：本轮没有新增修复，因此没有"回退修复看测试是否失败"的负向对照；改用更强的等价对照：
  `beta` 侧贡献路径集（`ea678c0fd44..db4a96cec32`，20 条）与合并带入路径集（`36c6ed5324c..e377db6cf20`，20 条）
  **逐条相等**，证明合并既没漏一条也没多带一条；E-SP12 路径集过滤为空，证明上一轮评审对象未被合并动过。
- **集成面复核**：`PlaybackMenuScrim` 的唯一生产调用点是 4 个 Activity 的 `applyPlaybackOverlay()`（`grep` 全仓仅这 4 处 + 测试 2 处 +
  `ThemePreviewView` 引用常量），没有其他引擎/宿主遗漏接线；`playerScrimOpacity` 不进 `ThemeTokens`，因此 `ThemeResolver`、
  `ThemePresets.withPaletteStyle`（遍历 `Slot.values()` 比较前后值）无需改动，用户手调该槽后切换调色风格不会被重置。
- **结论：通过。本轮无必修项、无代码改动。**

## 6. 验证（2026-10-23 15:0x +08:00）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 主题 + E-SP12 定向单测 + mobile Java 编译 | `gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.setting.SettingPlaybackOverlayTest" --tests "com.fongmi.android.tv.player.exo.DroppedFrameAccountingTest" --tests "com.fongmi.android.tv.player.exo.ExoDecoderRuntimeSessionTest" :app:compileMobileArm64_v8aDebugJavaWithJavac --console=plain` | **BUILD SUCCESSFUL in 4m 24s**（110 tasks，8 executed / 102 up-to-date），exit 0 |
| 测试明细 | 读 `app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/*.xml` | **29 个测试类 / 209 个用例 / 0 失败 0 错误**；关键类：`PlaybackMenuScrimTest` 5、`SettingPlaybackOverlayTest` 3、`ThemeEditorPanelTest` 2、`ThemeEditorContractTest` 12、`ThemeProfileCodecTest` 8、`ThemeProfileValidatorTest` 9、`DroppedFrameAccountingTest` 8、`ExoDecoderRuntimeSessionTest` 16 |
| leanback 编译 | 同上任务内 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | 通过（4 个 Activity 的接线改动编入 leanback 变体） |
| UI token 门禁 | `G:\Python310\python.exe scripts/check_ui_tokens.py` | `UI_TOKEN_STATUS PASS` |
| 差异卫生 | `git diff --check` | 空 |

环境备注（不属于本任务改动）：本机执行前需 `ANDROID_HOME=C:\Users\Maple\AppData\Local\Android\Sdk`（仓库无 `local.properties`）、
`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`、`GRADLE_USER_HOME=G:\GradleCache`；
Git-for-Windows bash 无法写 Gradle 锁文件，gradle 改由 Node `execSync` 调用完成。

## 7. 回滚

- **本轮评审记录**：删除 `docs/C101-beta-merge-review-dev4-20261023.md` 即可（无生产代码改动）。
- **合并本身**：`git revert -m 1 e377db6cf20` 可整体撤回 `beta` 合入（会同时撤回播放菜单遮盖层主题槽）；
  若只回退视觉改动，可单独 `git revert 29d357d882d`（四个 Activity 回到 `shape_controller_scrim`）。
- **上一轮 E-SP12 修复**：`git revert 36c6ed5324c`。
- 无偏好写入、无数据库/协议/二进制/schema/依赖变更；`shape_controller_scrim` 仍保留，回滚不需要恢复资源。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `e377db6cf20493d5b8b607ca4f2eed74ed3c4468`（"merge: 合并远端 beta（db4a96cec32，PR #483 播放菜单遮盖层主题槽）到 dev4"，20 files changed, 224 insertions(+), 12 deletions(-)）；因守卫 `scope_check` 要求 HEAD 不变，合并提交无法在守卫会话内创建，由独立 `git merge` 产生后开启会话 |
| 评审提交 | 由任务守卫 `C101-BETA-MERGE-DEV4-20261023` 的 `finish` 创建，只含本评审文档 |
| Recovery tag | `recovery/C101-BETA-MERGE-DEV4-20261023/<stamp>-<commit12>`（`finish` 创建） |
| 推送 | `git push origin dev4` |
| PR | [#484 merge+review: 合并 origin/beta 至 dev4 并复评 E-SP12 + 播放菜单遮盖层主题槽](https://github.com/Silent1566/webhtv/pull/484)，base `beta` ← head `dev4`，中文描述含具体改动；**更新**既有开放 PR（同 head/base 无法再建第二个），不合并 |
| 拉取 | `git fetch origin --prune` + `git pull --ff-only origin dev4` |
| 交付时工作树 | 跟踪文件干净；未跟踪 `Python/`、`.design-evidence/` 通过 `.git/info/exclude` 排除，保持不动、不入库 |
