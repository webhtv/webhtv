# TV-FOCUS-RING-MAIN-SOURCE-20261013：两处契约盲区（审计 P0）

## Recovery anchor

- 目标：关闭审计发现的两处契约扫描盲区——① `ThemeBinderContractTest` 漏掉**全限定写法**的 `MaterialAlertDialogBuilder`；② `TvFocusRingContractTest` 只扫 `leanback/res/drawable`，漏掉 `main` 里的电视焦点环——并修掉 ① 暴露出的全部违规。
- 允许路径：`app/src/leanback/java/.../activity/VideoActivity.java`、`app/src/mobile/java/.../activity/VideoActivity.java`、`app/src/main/java/com/fongmi/android/tv/Updater.java`、`app/src/test/java/.../theme/ThemeBinderContractTest.java`、`app/src/testLeanback/java/.../activity/TvFocusRingContractTest.java`、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `585b0bcb256`。
- 验收：① 6 处弹窗改走 `WebHtvAlertDialogBuilder`；② `ThemeBinderContractTest` 的正则覆盖全限定写法且仍全绿；③ `TvFocusRingContractTest` 的扫描覆盖 `main` 里的电视焦点环，并把 4 个未收敛文件**显式**列为已知项（名单是活的：修好即失败提醒移除）；④ 两端测试全绿。
- 回滚：撤销本任务原子提交即可；无数据迁移。
- 交付坐标：提交 `f6eede67dc1`（dev4），recovery tag `recovery/TV-FOCUS-RING-MAIN-SOURCE-20261013/20261013150501`；未推送。
- 下一步唯一动作：无（本批已交付）。**建议的下一批**：`selector_mpv_tab_focus.xml` 的硬编码调色板（见 §5.2，它比那 4 个焦点环更严重——整块写死颜色，深色主题下是亮蓝孤岛）。

## 1. 盲区一：`ThemeBinderContractTest` 漏掉全限定写法

### 1.1 现象与根因

该测试的意图是"所有 Material alert dialog 都必须经过 `WebHtvAlertDialogBuilder`，否则用户自定义主题对它静默失效"。它有两条断言：

```java
// 覆盖了三种写法，没问题
Pattern forbidden = Pattern.compile("new\\s+(?:[A-Za-z_][\\w.]*\\.)?AlertDialog\\.Builder\\(");
...
// 但这一条用的是字面量 contains
if (text.contains("new MaterialAlertDialogBuilder(")) { ... }
```

第二条**只匹配不带包名的写法**。而仓库里那 6 处**全部**写了全限定名：

```
new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
```

于是它们**全部通过了测试**，而 `ThemeController.bindDialog` 从未被调用——**用户自定义主题对这 6 个弹窗不生效**。

### 1.2 修法

- 把第二条也改成与第一条同形的正则：`new\s+(?:[A-Za-z_][\w.]*\.)?MaterialAlertDialogBuilder\(`；
- 6 处调用点全部改用 `WebHtvAlertDialogBuilder`：
  - `leanback/VideoActivity.java`：开场跳过确认、画质单选；
  - `mobile/VideoActivity.java`：开场跳过确认、画质单选、播放内核选择；
  - `Updater.java`：备份确认弹窗（**显式传 `ThemeOverlay_WebHTV_Dialog`**，由 `WebHtvAlertDialogBuilder(Context, int)` 覆盖，行为不变）。

### 1.3 为什么 `Updater` 那处可以安全替换

它用了 `alert.setOnShowListener(...)`，而测试注释说"`MaterialAlertDialogBuilder` 拥有 dialog 的 OnShowListener"。核对 `ThemeController.bindDialog` 实现：

```java
public static void bindDialog(Dialog dialog) {
    if (dialog == null || dialog.getWindow() == null) return;
    bindTheme(dialog.getWindow().getDecorView());
}
```

绑定走的是 **decorView 遍历**，**不使用** `setOnShowListener`——所以替换后 `Updater` 自己的 listener 不会被覆盖。

## 2. 盲区二：`TvFocusRingContractTest` 只扫 leanback

### 2.1 现象与根因

`noLeanbackFocusRingHardcodesItsWidth` 只列 `app/src/leanback/res/drawable`。而 `main` 源集里也有电视焦点环——它们被 leanback 的 presenter/layout 消费，却不在扫描范围内。

**不能简单地"把 main 也扫一遍"**：`main/res/drawable` 里有 33 个含 `state_focused` 的文件，其中绝大多数与电视焦点环无关（输入框/开关的 1.5dp 描边、手机端自己的 2dp 卡片描边等）。机械扩大扫描会产生大量误报。

### 2.2 收口特征

用**可判定特征**识别真正的电视焦点环：**引用了 `?attr/tvFocusRing` 或 `?attr/tvCurrentRing`** 的文件。这样：

- `leanback/res/drawable`：全部扫（原有行为不变）；
- `main/res/drawable`：只扫引用这两个属性的文件。

### 2.3 暴露出的 4 个文件与它们的真实问题

| 文件 | 现状 | 问题 |
|---|---|---|
| `selector_episode_card.xml` | 焦点 `3dp` + 圆角 `6dp` | 宽度是字面量；圆角与自己的注释公式（`8 − 1.5 = 6.5dp`）不符 |
| `selector_tmdb_cast_focus.xml` | 焦点 `3dp` + 圆角 `9.5dp` | 同上（注释写 `12 − 1.5 = 10.5dp`，文件是 9.5dp） |
| `selector_tmdb_media_focus.xml` | 焦点 `3dp` + 圆角 `6dp` | 同上（注释写 `6.5dp`） |
| `shape_episode_photo_focused.xml` | `3dp` + 圆角 `6dp` | 同上 |

**关键事实**：`webhtv_focus_ring_width` 的取值是 **1.5dp**，因为 3dp 曾在 2026-10-10 被用户在首页卡片上判为"太粗"。这 4 个文件仍是 3dp——即**那次收敛只改了 leanback，漏了 main**，而扫描范围正好漏掉 main，所以一直没被发现。

### 2.4 为什么本批**不**直接改这 4 个文件

改宽度必须**同步重算圆角并重做像素级月牙复核**。参考实现 `shape_vod_focused.xml` 记录了完整方法：

> 取值 7dp = 8dp − w/2 − 像素取整余量：理论值 7.25dp，但描边宽度按整数像素向上取整（1.5dp @ density 3 = 4.5px → 5px，实际 w/2 = 2.5px），7.25dp 时外边界会超出宿主弧 0.25–0.5px……7dp 在 density 1/1.75/2/2.353/3/4 下实测均为 0 溢出（Robolectric 栅格化扫描）。

这 4 个文件的宿主圆角各不相同（8dp / 12dp），且被 `TvAppSurfaceFocusRingDeviceTest`、`TmdbDetailActivityLayoutTest`、`VideoActivityLayoutTest` 等多处引用。**宽度 + 圆角 + 像素复核 + 多密度验证**是一个独立批次，混进本批会让"关闭盲区"这件小事变成一个高风险的几何改动。

### 2.5 本批的处理：显式已知项 + 活名单

把这 4 个文件列进 `knownUnconverged` 名单，并加一条断言：

```java
assertEquals("以下文件已收敛到 token，请从 knownUnconverged 名单里删掉：" + stillUnconverged,
        knownUnconverged, stillUnconverged);
```

即**名单是活的**：一旦有人修好其中任一文件，测试立刻失败并要求把它从名单里删掉。这样既让扫描覆盖到它们（不再静默），又不会让名单腐烂成第二套豁免（这正是 `selector_video_item.xml` 那条过期豁免的教训——它现在已完全用 token，却还挂在豁免名单里）。

## 3. 不变量与回滚

- 不改动任何弹窗的**文案、按钮、行为**：只把构造点从原始 builder 换成子类。
- 不改动 `WebHtvAlertDialogBuilder` 本身。
- 不改动那 4 个焦点环 drawable 的**任何字节**——本批只让测试能看见它们。
- 不改动 `leanback/res/drawable` 的扫描行为（仍是全扫）。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 4. 验证记录

### 4.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "...TvFocusRingContractTest" --tests "com.fongmi.android.tv.theme.*"` → **BUILD SUCCESSFUL in 3m 23s**（收紧后的焦点环扫描 + theme 契约全绿）。
- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.dialog.*"` → **BUILD SUCCESSFUL in 1m 56s**。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 1m 22s**；`.\gradlew.bat :app:assembleMobileArm64_v8aDebug` → **BUILD SUCCESSFUL in 1m 13s**。

**关键**：收紧后的 `ThemeBinderContractTest` 若没有先修那 6 处调用点，会**直接失败**——这正是盲区被关闭的证明。同理 `TvFocusRingContractTest` 的 `knownUnconverged` 活名单断言会盯住那 4 个文件。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。

### 4.2 设备实测（`192.168.50.3:5561`，装 **leanback flavor**）

| 检查项 | 结果 |
|---|---|
| 应用启动 | 正常 |
| 首页 | 正常（功能按钮行与内容卡片行焦点环正常） |
| 详情页 | 正常（该页消费 `selector_episode_card` 那族焦点环） |
| 播放 | 正常（起播、`1920x1080`、码率显示） |
| 选集面板 | 正常（**该面板正是 `selector_episode_card` 的宿主**，说明未收敛文件的渲染无回归） |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-m6-home.png`、`e5561-m6-detail.png`、`e5561-m6-player.png`、`e5561-m6-menu.png`。

### 4.3 结论

两处盲区都已关闭：6 处绕过主题绑定的弹窗改走 `WebHtvAlertDialogBuilder`（用户自定义主题现在对它们生效），`ThemeBinderContractTest` 的正则覆盖全限定写法；`TvFocusRingContractTest` 的扫描扩展到 `main` 里的电视焦点环，并用**活名单**显式记录 4 个未收敛文件。

### 4.4 残余风险与诚实说明

- **「画质」单选弹窗未在真机上触发**：它需要多线路源（`result.getUrl().isMulti()`），而当前源只有一条线路，走不到那个分支。该处的改动是**纯类名替换**（`new WebHtvAlertDialogBuilder(this)` 与 `new com.google...MaterialAlertDialogBuilder(this)` 的链式调用完全同形），编译通过 + 同类替换已在其它 4 处生效，风险很低，但**未逐一实机触发**。
- **「开场跳过确认」弹窗未触发**：需要片头片尾检测命中。
- **`Updater` 的备份确认弹窗未触发**：需要更新流程。
- **那 4 个焦点环文件的实际像素表现未复核**：本批**没有改它们的任何字节**，所以不存在回归；但它们的 3dp 宽度与注释公式不符这一事实**仍然存在**，属 §5.1 的后续批次。
- **`selector_mpv_tab_focus.xml` 不在本批收口范围内**（它不引用 `?attr/tvFocusRing`），见 §5.2。

## 5. 相邻发现（未纳入本批）

1. **那 4 个文件的收敛是独立批次**：需要"改宽度 → 按 `宿主圆角 − w/2 − 像素取整余量` 重算圆角 → Robolectric 多密度栅格化复核 0 溢出 → 同步受影响的多处测试"。参考实现 `shape_vod_focused.xml` 已写明方法与实测数据。
2. **`selector_mpv_tab_focus.xml` 是另一类问题，且更严重**：它**整块写死了调色板**（`#E8F0FE` 填充 / `#1A73E8` 描边 / `#D2E3FC` 按下），不引用任何主题属性——在深色主题下是**亮蓝孤岛**，且用户改主题时纹丝不动。它被 `AdBlockStatsDialog`（TV）、`PlaybackPerformanceDialog`、`UpdateSettingsDialog`、`dialog_mpv_config_manager.xml` 消费。它不含 `?attr/tvFocusRing`，所以本批的收口特征**不会**覆盖它——需要单独处理（应改用 `?attr/tvFocusRing` 一族 + token 宽度）。
3. **审计 P0 的其余项未动**：静态门槛可执行化、`item_following.xml` 的固定色、空态描述文字对比度、`FileActivity` 空态、§20.2 矩阵验收。
