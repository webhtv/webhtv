# 光影剧幕浅色白色中间层 + 线路/选集 chip 底色修复

- 任务：`CINEMA-LIGHT-CHIP-FILL-20261006`
- 状态：**已实施，自动化验证通过**；设备级验证受阻（见第 5 节）
- 时间：2026-10-06（Asia/Shanghai）
- 基线：`dev1@cb82755ae8fc671f19e26a7771939c7bce0b0580`，起始工作树干净
- 用户报告（附实机截图 `/tmp/orca-paste-1791204895261-a4bb6857-f7c8-40ee-8bd1-2845e544ef45.png`）：
  1. 电视版「线路」和「选集」没有底色，压在亮色剧照上看不清；
  2. 「光影剧幕」浅色模式要改回早之前有白色中间层的版本，和深色模式差不多。

## Recovery anchor

- 目标：让电视详情页的线路/选集 chip 有可见底色；让浅色光影剧幕恢复白色中间层。
- 验收：`TmdbDetailActivityLayoutTest`、`TmdbDetailChipFillTest`、`TmdbCinemaLightReadabilityTest` 全绿；
  `:app:testMobileArm64_v8aDebugUnitTest` + `:app:testLeanbackArm64_v8aDebugUnitTest` 无回归。
- 当前状态：上述自动化全部通过；APK 已构建，但 dev1 机位 `192.168.50.3:5555` 全程离线，未取得设备级像素证据。
- 回滚锚点：revert 本提交即恢复原行为（chip 走 `setBackgroundColor`、浅色剧幕中间层透明、浅色剧幕强制白字）。

## 1. 问题一：chip 没有底色（根因）

`TmdbDetailActivity#setChipState` 用 `setBackgroundColor` 上色：

```java
button.setBackgroundColor(selected ? colors.chipActive : colors.chip);
```

`MaterialButton#setBackgroundColor` 的实现（material 1.14.0，`javap -c` 核对）是：

```text
public void setBackgroundColor(int);
  isUsingOriginalBackground() ? materialButtonHelper.setBackgroundColor(color)
                              : super.setBackgroundColor(color);
```

而 `MaterialButtonHelper#setBackgroundColor` 只做一件事——**给当前那个 `MaterialShapeDrawable` 实例 setTint**：

```text
void setBackgroundColor(int);
  getMaterialShapeDrawable()?.setTint(color);
```

它**不更新** helper 的 `backgroundTint` 字段。而 Material 会在 inset / 圆角 / 尺寸变化时用那个字段重建背景
（`MaterialButtonHelper#updateBackground()` → `createBackground()` → `setTintList(backgroundTint)`）。
这些 chip 由 `createChipButton` 新建，`backgroundTint` 字段从未被赋值，于是**重建后的填充是全透明**，
只剩描边 —— 正是截图里线路/选集的样子。

同一页的「第 N 季」按钮走的是另一条通道（`applyEpisodeTitleButtonFocus` 用 `setBackgroundTintList`），
写的就是那个持久字段，所以它一直有可见底色。这一点在同一张截图里构成天然 A/B 对照：

| 控件 | 通道 | 截图实测内部像素 |
| --- | --- | --- |
| 线路 chip | `setBackgroundColor` | `(45, 2, 6)` = 剧照原图，无底色 |
| 选集「第 1 季」 | `setBackgroundTintList` | `(222, 237, 255)` = 白色底板 |

修复：`setChipState` 改走 `setBackgroundTintList`。

### 1.1 复现证据（Robolectric 真实渲染，`GraphicsMode.NATIVE`）

临时探针直接测两条通道，然后触发 Material 的背景重建（`setInsetTop(0)/setInsetBottom(0)`）：

```text
PROBE A0 after setBackgroundColor      fill=#FFEAF0F5
PROBE A1 after setCornerRadius(45)     fill=#FFEAF0F5
PROBE B1 after setInsetTop/Bottom(0)   fill=#00000000   <- 填充被丢掉
PROBE C1 after setPadding (insets->bg) fill=#FFEAF0F5
PROBE D1 after addView + layout pass   fill=#FFEAF0F5
```

该差异已固化为永久契约测试 `TmdbDetailChipFillTest`：

- `persistentTintChannelKeepsChipFillThroughMaterialBackgroundRebuild`：tint 通道重建后仍有填充；
- `legacySetBackgroundColorLosesItsFillWhenMaterialRebuildsTheBackground`：旧的 `setBackgroundColor`
  写法在重建后填充退化为 `0x00000000`。

## 2. 问题二：浅色光影剧幕缺少白色中间层（根因）

`cinemaBackdropShade()` 是全屏渐变中间层，夹在剧照（`backdrop`）与内容（`scroll`）之间：

```xml
<ImageView android:id="@+id/backdrop" .../>
<View      android:id="@+id/backdropShade" .../>
...
<NestedScrollView android:id="@+id/scroll" ...>
```

深色剧幕一直是「黑幕 + 浅字」（`0xEC090B0F…`），浅色剧幕的那一层被改成全透明：

```java
if (lightTheme) return TmdbDetailLayoutUtils.colorDrawable(Color.TRANSPARENT);
```

于是内容直接浮在剧照原图上，没有稳定底板。历史版本 `cinemaLightBackdropShade()`（`7e42054de^`）
就是「白色幕布」，其 alpha 曾被 `893a6d495` 主动调柔到当前这组数值。

修复：恢复 `cinemaLightBackdropShade()`，并把 `cinemaBackdropShade()` 的浅色分支接回去；
同时删除为绕开该缺失而引入的 `applyLightCinemaCopyPlate()` / `LightCinemaCopyPlateDrawable`
局部羽化底板（它是白色中间层的替代品，中间层恢复后即为多余）。

### 2.1 连带修复：浅色剧幕不得强制白字

`tintTmdbSectionTitles()` 原先**不分深浅**一律强制白字（`0xFFFFFFFF`）并加暗投影。
在暗幕上成立，但白色中间层恢复后白字压浅色底板会看不见。改为只对深色剧幕强制白字，
浅色剧幕使用调色板 `primary`（`0xFF12202D`）；`personalAiReason` 同样处理。

## 3. 验收数值（WCAG 对比度，`TmdbCinemaLightReadabilityTest`）

该测试**从生产源码里读出真正发布的中间层 alpha**，再按 sRGB 相对亮度计算对比度，
因此不是会漂移的副本。取样点为文字所在左侧、叠加水平+垂直两层后的底色，
底色取最不利的饱和亮剧照 `(200,0,0)`：

| 前景 | 对比度 | 阈值 |
| --- | --- | --- |
| 小标题 `palette.primary` `#12202D` | ≥ 4.5:1 ✅ | WCAG AA 正文 |
| 正文 `palette.body` | ≥ 4.5:1 ✅ | WCAG AA 正文 |
| 对照：强制白字 | < 4.5:1（实测 2.34:1）❌ | 证明必须去掉强制白字 |

**变异检验（证明测试有牙齿）**：把非紧凑分支水平首 alpha 由 `0x99` 改为 `0x1A` 后，
小标题对比度降到 **2.95:1**，3 条断言失败；把中间层改回全透明则由
`TmdbDetailActivityLayoutTest#profileBackdropKeepsHistoricalTransparencyOverPosterArt` 捕获。

## 4. 改动文件

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/.../ui/activity/TmdbDetailActivity.java` | `setChipState` 改走 `setBackgroundTintList`；恢复 `cinemaLightBackdropShade()` 并接回浅色分支；删除 `applyLightCinemaCopyPlate()` / `LightCinemaCopyPlateDrawable` 及 7 个随之无用的 graphics import；浅色剧幕改用调色板深色文字 |
| `app/src/testMobile/.../TmdbDetailActivityLayoutTest.java` | 白色中间层契约改写；新增 chip 底色通道契约 |
| `app/src/testLeanback/.../TmdbDetailChipFillTest.java` | 新增：真实渲染证明两条通道在背景重建后的差异 |
| `app/src/testMobile/.../TmdbCinemaLightReadabilityTest.java` | 新增：从源码读 alpha 的可读性/变异契约 |

## 5. 验证结果

- `:app:testMobileArm64_v8aDebugUnitTest` → **5196 tests, 0 failures, 0 errors**
- `:app:testLeanbackArm64_v8aDebugUnitTest` → **4348 tests, 0 failures, 0 errors**
- `bash scripts/build_arm64_debug_install.sh --flavor leanback` → **BUILD SUCCESSFUL**，APK 196M
- APK 内容核对：`cinemaLightBackdropShade` 存在于 `classes16.dex`；`LightCinemaCopyPlateDrawable` 已消失；
  `setChipState` 反汇编显示调用 `MaterialButton.setBackgroundTintList`（offset 0023）且无 `setBackgroundColor`。

### 5.1 未完成：设备级验证（如实记录，不冒充通过）

dev1 专属机位 `192.168.50.3:5555` 在本次任务全程离线：

- 端口扫描：5555 关闭；5557/5559/5561 开放但分属 dev2/dev3/dev4，按项目规则不得占用；
- 多次重试（跨约 10 分钟、`adb kill-server`/重连）均 `Connection timed out`；
- 该机位不在本机：无 `emulator` 可执行文件、无 AVD、`192.168.50.3` 无 SSH(22)/Docker(2375/2376) 入口，
  本机无 `virsh`/本地 qemu，Orca `host list` 只有 `local`。因此无法从本机重启该模拟器。

**结论**：本次只有自动化证据（含真实渲染像素与变异检验），缺设备级截图确认。
需用户恢复 `192.168.50.3:5555` 后再补一次覆盖安装验证：
浅色光影剧幕下确认出现白色中间层、线路/选集 chip 有底色、文字可读。

## 6. 经验教训

1. **`MaterialButton#setBackgroundColor` 不是持久上色**。它只 tint 当前 `MaterialShapeDrawable` 实例，
   不更新 `backgroundTint` 字段；Material 之后重建背景时会读该字段，填充因此丢失。
   凡是要在 Material 按钮上设填充，应统一走 `setBackgroundTintList`。
   （本次同一页两种通道并存，是问题长期存在却只在部分控件上暴露的原因。）
2. **"改回旧版本"要先定位那一层到底是什么**。用户说的"白色中间层"就是 `backdropShade` 全屏渐变，
   历史实现是 `cinemaLightBackdropShade()`；恢复它比新增局部底板更贴合"和深色模式差不多"的要求。
3. **强制前景色必须与底板一同裁定**。恢复白色中间层后，原先在暗幕上合理的强制白字立即变成缺陷；
   底色与前景色是一对契约。
4. **测试要能失败**。可读性断言若只写死副本数值就没有意义；从生产源码读取 alpha 并做变异检验，
   才能证明断言真的守着这条行为。

## 7. 回滚

- 代码：revert 本提交。
- 影响面：仅 TMDB 详情页 chip 填充通道与光影剧幕浅色外观；不涉及数据格式、偏好键、profile 或资源替换。
- 用户已保存的主题 profile 不受影响。
