# C72 在 dev3 上合并 `origin/beta` 最新代码并复评未推送的电视端控制栏进度条 UP 返焦改动

## Recovery anchor

- 目标（完成句）：把 `origin/beta` 最新代码合入 `dev3`，逐轮评审本轮改动（含已提交未推送的 `80816a15f0`、`9116639e3e`）直到无必修问题，验证通过后原子提交、推送 `dev3`、创建中文 PR 到 `beta`（只创建不合并）。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelperSeekReturnTest.java`、`docs/TV-PLAYER-SEEK-UP-FOCUS-20261016.md`、`docs/C72-beta-merge-review-dev3-20261016.md`（本文）、`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`。
- 验收标准：合并提交第二父等于提交前最新 `origin/beta` tip；净差异只含批准的本地改动与任务文档；远端已移除/回退内容零复活；定向测试与双 flavor 编译通过；PR base=`beta`、head=`dev3`、OPEN、未合并。
- 当前状态：**合并完成、复评完成（1 项实现 + 4 项文档修正）、验证通过、待提交推送与创建 PR**。
- 下一步唯一动作：`task_guard.sh finish` 提交并打恢复标签，然后推送 `dev3` 并创建 PR（见 §7）。

## 1. 合并对象与树对象证据

| 项 | 值 |
| --- | --- |
| 分支 / 起始 HEAD | `dev3` / `9116639e3e0b244618eabfe03a3e9ce5b5906b55` |
| `origin/beta` tip（合并前） | `1125b6c9c0727d572ad14a7c38937433afa51d71` |
| 合并前 `origin/dev3` | `51bad1e0e49bf1e01e97b295b06812b6371c879e` |
| 共同祖先 | `51bad1e0e49bf1e01e97b295b06812b6371c879e` |
| `origin/beta` 相对共同祖先的提交 | 仅 1 个：`1125b6c9c0 Merge pull request #449 from Silent1566/dev3` |
| 共同祖先树 / `origin/beta` 树 | `8064bc564ab764173087d5234531599d6232c08a`（**相同**） |
| `dev3` 本地未推送提交 | `80816a15f0`、`9116639e3e` |

**零内容合并证据**：`git diff --stat 51bad1e0e4 origin/beta` 输出为空，且两者树对象同为 `8064bc564ab764173087d5234531599d6232c08a`。即 `origin/beta` 本轮相对共同祖先**没有引入任何内容变化**——`1125b6c9c0` 正是「把 dev3（`51bad1e0e4`）合进 beta」的合并提交。因此 `git merge --no-commit --no-ff origin/beta` 自动合并成功、0 冲突，合并后索引树与 `HEAD` 树逐字节相同（`373809946a7821b6073c13d4049205a14ba33523`）。

## 2. 零复活 / 零丢失证据（可证伪）

`origin/beta` 本轮不含内容变化，所以「复活」只能来自本地分支相对 beta 的**新增路径**。按当前 `origin/beta` 树为唯一权威口径：

| 层 | 口径 | 结果 |
| --- | --- | --- |
| A 文件级新增 | `comm -13 <beta 树> <合并树>` | 2 条：`PlayerControlFocusHelperSeekReturnTest.java`、`TV-PLAYER-SEEK-UP-FOCUS-20261016.md`（均为本任务自身新增文件） |
| B 文件级丢失 | `comm -23 <beta 树> <合并树>` | **0**（beta 5067 个文件全部保留） |
| C 文件级复活 | 新增路径 ∩ revert 提交触及路径 | **0**（revert 提交 111 个、触及路径 731 条） |
| D 行级复活 | 净差异新增行 ∩ revert 提交删除的实质行 | 31 条命中，**逐条分类后全部为样板/导入/注解，实质行 0** |

- revert 提交识别只匹配**主题行**并锚定开头：`git log --all --format='%H%x09%s' | awk -F'\t' '$2 ~ /^(Revert|revert|回退|撤销|剔除)/ {print $1}'` → 111 个（避免匹配到本类任务合并提交正文里的「被触及路径」等字样而误判）。
- D 层 31 条命中的分类明细（按分类器实测）：**15 条 `import`、3 条 `@` 注解、11 条脚手架**（`/**`、`*/`、`}`、```` ``` ````、```` ```text ````、`## Recovery anchor`、`| --- | --- |`、`return view;`）、**2 条实质行**：`public void setUp() {` 与 `| --- | --- | --- |`。
- 两条实质行逐条核实：`| --- | --- | --- |` 是**三列**的 Markdown 表格分隔行（分类器的脚手架集只列了二列形式，故未归入），本质仍为样板；真正代码样式的实质行只剩 `public void setUp() {` 一条。
- 该唯一实质行的溯源：它由 revert 提交 `c9cc147fb92778730f59251d894bb5eb1435a524`（`Revert "Playlist API: add Playlist and PlaylistTest"`）删除。本任务新增的测试类恰好也有一个同名 JUnit 生命周期方法——这是**通用测试样板行**的巧合，不是「复活了被回退的 Playlist 功能」：净差异不含 `Playlist`/`PlaylistTest` 的任何路径或标识符（见 §3，净差异仅 3 路径）。
- **检测器反空转自检**：把确实「在 revert 集且不在 beta 树」的探针路径 `ISSUE_TEMPLATE` 注入伪造新增列表，检测器如期报出（`fake-intersection count: 1`，`SELF-CHECK PASS`）。若不自检，无法排除检测器空转导致「零复活」恒真。

## 3. 净差异（合并树 vs `origin/beta`）

```text
app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java          |  49 +
app/src/testLeanback/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelperSeekReturnTest.java | 262 +
docs/TV-PLAYER-SEEK-UP-FOCUS-20261016.md                                                 | 136 +
3 files changed, 447 insertions(+)
```

`git diff --check` 无空白错误；全仓无 `<<<<<<<`/`|||||||`/`>>>>>>>` 冲突标记。净差异只含本任务自身路径，未触碰 beta 的任何既有内容。

## 4. 复评：本轮改动（含已提交未推送）

复评对象是 `80816a15f0`（`PlayerControlFocusHelper` 的进度条 UP 返焦实现 + 9 条测试）与 `9116639e3e`（设备复核文档）。复评方法：先读实现与调用方，再用 Robolectric 真实 `View` 语义**实测**每个假设，而不是只读代码下结论。

### 4.1 修正 1（实现）：`seekReturnTarget` 未拒绝进度条的**容器**

- 事实：`CustomSeekView` 构造函数里 `setFocusable(true)`（`CustomSeekView.java:82`），并把 `requestFocus` 转发给 `timeBar`（`:331`）。原判定只拒绝「焦点在 seek **子树内**」（`isDescendant(seek, focused)`），没有拒绝「seek 本身是焦点」的**祖先方向**。
- 后果：若容器成为 `findFocus()` 的结果，`timeBar.setNextFocusUpId(容器 id)` 会被写入；焦点被转发回进度条，表现为「按 UP 没反应」。
- 修正：判定补上 `isDescendant(focused, seek)`，两个方向都拒绝。
- **可达性实测（重要，避免夸大）**：用探针实测 `CustomSeekView` 的焦点语义——`timeBar` 可聚焦时 `requestFocus` 委托给它、`findFocus()` 返回 `timeBar`；`timeBar` 不可聚焦（手机版布局形状）时委托分支**直接返回 `false`、不会回退到 `super.requestFocus()`**，容器同样拿不到焦点。因此**当前代码里容器持有焦点不可达**，本次修正属**契约加固**（不依赖上游 `requestFocus` 委托细节），不是可达缺陷修复。Javadoc 已按此实测口径改写，并新增 `containerNeverHoldsFocusBecauseRequestFocusDelegatesToTheSeekBar` 锁住该前提：一旦上游改成「失败就回退」，该测试转红，提示祖先方向从加固变为必需。

### 4.2 修正 2（测试）：修正前的「容器」用例是**恒真**的

- 事实：原测试夹具用裸 `FrameLayout` 当容器且**未设 `setFocusable(true)`**，而真实 `CustomSeekView` 是可聚焦的。`seekReturnTarget` 里有 `!focused.isFocusable()` 提前返回，所以「焦点落在进度条容器上时不得覆盖记忆」这条断言在夹具上**因为「不可聚焦」而恒真**，即使删掉任何容器相关逻辑也照样通过。
- 证据：把夹具容器改为可聚焦后，`seekItselfNeverOverwritesTheRememberedTarget` 与 `syncKeepsTheMemoryWhileTheSeekItselfIsFocused` **由绿转红**（`11 tests completed, 3 failed`），证明修正前这两条对容器方向**零保护**。
- 修正：夹具容器补 `setFocusable(true)`，并加**反空转前置断言** `assertTrue(seek.isFocusable())`，防止后续再退化回恒真。

### 4.3 修正 3（文档）：`TmdbDetailActivity` 的 `handleKey` 行号错误

文档 §2 写「`TmdbDetailActivity:10905`」，实测该行是 `private boolean canInlineKeySeek(KeyEvent event) {`；真正的 `PlayerControlFocusHelper.handleKey` 入口在 **`10887`**（`80816a15f0` 与当前 HEAD 一致）。已更正为 `10887`。

### 4.4 修正 4（文档）：`detail_open_mode` 的控制器归属与 `showInlinePlayer` 结论错误

文档 §4.3 原文称「`0`/`1`/`2` 都会由 `PlayerDetailController`/`FusionDetailController` 复用同一套 `playerControls`，`detail_open_mode=2` 因 `showInlinePlayer()=false` 需先点击播放」。

实测（`TmdbDetailActivity:1010` 与各控制器）：

| `detail_open_mode` | 路由判定 | 控制器 | `showInlinePlayer()` |
| --- | --- | --- | --- |
| `0` FUSION | `rawFusionMode()` = true | `FusionDetailController` | `true` |
| `1` ENHANCED | 两者皆 false | `EnhancedDetailController` | `false` |
| `2` DIRECT | 两者皆 false | `EnhancedDetailController` | `false` |
| `4` PLAYER | `rawPlayerMode()` = true | `PlayerDetailController` | `false` |

即 `detail_open_mode=1` 走的是 `EnhancedDetailController` 而非 `FusionDetailController`，且 `detail_open_mode=2` 也**不是** `PlayerDetailController`（那是 mode `4`）。已按实测表格改写为「mode `0` 由 `FusionDetailController` 驱动、`showInlinePlayer()=true` 故默认展示内嵌播放器」，并修正 §4.4 的对应措辞。

### 4.5 修正 5（文档）：手机端「no-op」结论错误

原文称手机端 `syncSeekReturnTarget` 是 no-op。实测：手机版 `view_control_seek.xml` 里 `timeBar` **仍存在于布局中**（只是不可聚焦），`root.findViewById(R.id.timeBar)` 依然取得到，所以该函数在手机版树上**照常执行并写入**目标；真正让手机端无观感变化的是「焦点到不了 `timeBar`，该目标永不被咨询」。已改写为准确口径，并新增 `mobileShapedTreeIsNotANoop` 固定「函数照常执行」这一事实。

### 4.6 复核通过、未改动的部分

- `handleKey` 在 `containsFocus` 之后、`focusSearch` 之前调用 `syncSeekReturnTarget` 的时序正确：同步发生在框架做焦点搜索之前，且该 helper 是电视端控制栏唯一遥控入口（`VideoActivity:8154`、`TmdbDetailActivity:10887`）。
- 文档「目标之后被隐藏时 FocusFinder 会跳过它并回退到几何搜索，因此不需要清理失效记忆」这一承重结论**实测成立**：探针显示 `nextFocusUpId` 指向可见目标时 `focusSearch(UP)` 返回该目标，目标置 `GONE` 后返回 `null`（框架跳过失效目标）。
- 手机端 `handleKey` 路径（`mobile/VideoActivity:5594`）与本改动无交互；`AudioActivity` 的 `CustomSeekView` 不使用该 helper（其 `nextFocusUp` 由布局静态指定），不受影响。
- 控制栏布局内无重复 id；`timeBar` 的可见性从不被代码改写（恒 `VISIBLE`），不存在「记忆目标被隐藏」的额外清理需求。

## 5. 验证（本轮新生成产物）

### 5.1 定向单测（leanback，13 条）

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.PlayerControlFocusHelperSeekReturnTest"
→ BUILD SUCCESSFUL in 23s
app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.ui.helper.PlayerControlFocusHelperSeekReturnTest.xml
  tests="13" skipped="0" failures="0" errors="0"   (mtime 10:53:58，属本轮)
```

### 5.2 合并验证（定向单测 + 双 flavor Java 编译）

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "...PlayerControlFocusHelperSeekReturnTest" \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac
→ BUILD SUCCESSFUL in 3m 43s
  tests="13" skipped="0" failures="0" errors="0"   (mtime 11:01:36，属本轮)
```

### 5.3 手机端集成测试（读取本 helper 源码文本的既有回归锁）

```text
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.PlayerControlFocusIntegrationTest"
→ BUILD SUCCESSFUL in 1m 29s
  tests="10" skipped="0" failures="0" errors="0"
```

### 5.4 变异测试（证明新增/加强的断言可失败）

| 变异 | 期望 | 实测 |
| --- | --- | --- |
| M1：删掉 `isDescendant(focused, seek)` 祖先方向（还原为修正前实现） | 转红 | `11 tests completed, 3 failed`（`seekItselfNeverOverwritesTheRememberedTarget`、`syncKeepsTheMemoryWhileTheSeekItselfIsFocused`、`focusableSeekContainerIsNeverWrittenAsItsOwnUpTarget`）✅ |
| M2：把真实 `CustomSeekView.setFocusable(true)` 改成 `false` | 转红 | `11 tests completed, 1 failed`，且恰好命中 `realCustomSeekViewIsNotWrittenAsItsOwnReturnTarget` ✅ |

变异还原后逐字节校验：`PlayerControlFocusHelper.java` 与 `CustomSeekView.java` 均与还原前 hash 相同（后者 `a538cbdc14a21a9c2426eabd7d86c0266870370a` 等于 `HEAD` 版本，`git diff --stat` 无输出）。

### 5.5 卫生检查

`git diff --check` 无空白错误；无冲突标记（`.so` 二进制命中为 grep 假阳性，`--binary-files=without-match` 复扫为空）；测试文件与评估索引行尾已按仓库约定（`core.autocrlf=true`）归一为 CRLF，消除 `LF will be replaced by CRLF` 警告。

### 5.6 索引 union 冲突口径核对

评估索引是各分支各自追加行的 union 冲突高发区，故按**行 ID 集合**而非「无冲突标记」校验：`origin/beta` 侧 12 个 `C*` 行 ID 全部保留（缺失 0），合并侧 13 个，新增恰为 `C72`。

### 5.7 提交前 beta 复核

`git fetch origin` 后 `origin/beta` 仍为 `1125b6c9c0727d572ad14a7c38937433afa51d71`，等于 `MERGE_HEAD`，无需重新合并。

## 6. 设备证据边界

本轮为**复评轮**，未新增设备走位：`80816a15f0` 与 `9116639e3e` 已分别记录影视原生控制栏 A/B（§4.2）与沉浸融合内嵌控制栏（§4.3）设备实测。本轮改动为「拒绝容器作为回来目标」的判定加固 + 测试/文档修正，**不改变任何已实测路径的按键结果**（容器持有焦点当前不可达，见 §4.1），故不重复设备取样。手机端未回归，理由见 §4.5。

## 7. 交付坐标

- 任务：`C72-beta-merge-review-dev3-20261016`（lane：upstream）
- 合并提交：`task_guard.sh finish` 生成（双父：`9116639e3e` + `1125b6c9c0`）
- 恢复标签：`recovery/C72-beta-merge-review-dev3-20261016/<timestamp>`（由 `finish` 创建）
- 推送：`git push origin dev3` 与标签
- PR：`base=beta`、`head=dev3`，中文描述，**只创建不合并**

## 8. 回滚

- 整体回滚：`git revert -m 1 <合并提交>`（回到 `9116639e3e`，即本任务前的本地状态）。
- 仅回滚本轮复评修正：还原 `PlayerControlFocusHelper.java` 的祖先方向判定、测试夹具的 `setFocusable(true)` 与新增用例、以及 `TV-PLAYER-SEEK-UP-FOCUS-20261016.md` 的 4 处文档更正。
