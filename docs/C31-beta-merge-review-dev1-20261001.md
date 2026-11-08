# C31 dev1 合并 origin/beta 最新代码（PR #391）并复评猫源启动超时修复

- 任务：`C31-beta-merge-review-dev1-20261001`
- 日期：2026-10-02
- 仓库：Silent1566/webhtv，分支 `dev1`
- 锚点：合并前 `dev1 @ 65d709a477a28e2c12db9ff09e1d7d2554bbd266`（领先 `origin/dev1` 2 个提交）
- 远端基线：`origin/beta @ 45d340418`（Merge PR #392 from dev2，含 PR #391 与 PR #392）

## 目标

1. 合并 `origin/beta` 最新代码（PR #391：首页菜单新增「追更页面 / 站点注入」动作；PR #392：dev2 站点批量动作焦点修复），不带回任何远端已回退/剔除的内容。
2. 评审 dev1 已修改的代码，含已提交未推送的 `65d709a47`（猫源启动超时修复）。
3. 发现问题修改后验证，循环复评直到通过。
4. 提交当前任务改动、推送、创建 PR 到 beta（只创建不合并）。

## 合并记录

- 合并目标：`origin/beta @ 45d340418`（Merge PR #392 from dev2），采用 `git merge origin/beta --no-commit --no-ff` 后由任务守卫 finish 创建合并提交（与 C29 同模式）。
- 带入改动（相对合并前 dev1）：
  - PR #391（6 文件 + C30 文档）：`HomeActivity.java`、`HomeMenuDialog.java`、`Setting.java`、`dialog_home_menu.xml`、三语言 `strings.xml`、`HomeMenuDialogSourceTest.java`、`docs/C30-beta-merge-review-dev2-20261001.md`。
  - PR #392（3 文件 + C31 dev2 文档）：`SiteDialog.java`、`SiteDialogActionFocusTest.java`（新增）、`docs/C31-beta-merge-review-dev2-20261001.md`。

### 被剔除提交核对（三层全部保持通过）

| 核对项 | 结果 |
| --- | --- |
| `5682f2b05`（剔除 PR #353 主题改动）非 HEAD 祖先 | ✅ NOT ancestor |
| `59f438285`（dev3 旧合并）非 HEAD 祖先 | ✅ NOT ancestor |
| `c900dac20`（dev3 主题系统旧合并）非 HEAD 祖先 | ✅ NOT ancestor |
| `origin/beta` 自身不含 `5682f2b05`（45d340418 复核） | ✅ NOT ancestor |

净差异 `origin/beta..HEAD` 仅 7 文件，全部是猫源超时修复（CatSource / NodeBundle / NodeLib / NodeRangeZip / NodeRuntime / NodeService / NodePortSelectionTest），无任何回退内容被顺带提交。

## 评审记录

### 第 1 轮评审

**A. PR #391 带入的菜单改动（beta 侧新代码）**

- `select_home_menu_key` 数组三语言（values / zh-rCN / zh-rTW）均扩为 12 项，逐语言核对项数与文案一致。
- `Setting.getHomeMenuKey()` 上界 9 → 11，与数组下标匹配；旧存量值 10/11 此前会被钳回 0，现在可正确直达新动作。
- `HomeActivity.onHomeMenuItem`：`case 1..11` 全覆盖；`case 10 -> FollowingActivity.start(this, null)` 与实际签名 `start(Activity, String)` 匹配；`case 11 -> openCustomCsp()` 方法已存在（969 行既有调用同款）。
- `HomeMenuDialog`：`items.remove(0)` 后 position+1 映射保持正确；`spanCount=3` 下 11 项共 4 行仍在 `maxHeight=352dp`（4×40dp+padding）一屏内；`adapter_home_menu.xml` 有 `autoSizeTextType` 兜底英文长文案。
- `HomeMenuKeyDialog` / `SettingPersonalActivity` 直接读取同一数组，扩容无需适配，核对无越界。
- 结论：**通过，无问题**。

**B. 已提交未推送的 `65d709a47`（猫源启动超时修复）**

- 预算常量集中到 `NodeRuntime`（`START_TIMEOUT_MS=12min`、`TRANSFER=3min`、`LIB_TRANSFER=5min`、`METADATA=8s`、`READY=3min`、`READY_PROBE=2s`），`CatSource.serve` 的 latch 改用同一常量，主子进程等待不再互相矛盾。✅
- `NodeService.waitReady`：固定 225 轮改为 deadline 轮询；每轮 `/config` 探测用独立 2s 短超时 client，不再拖 30s 默认超时。✅
- `NodeBundle.remoteMd5Pair`：两个 md5 并行获取，latch 限时（`METADATA_TIMEOUT_MS+500ms`）后取结果，超时线程逃逸为有界后台线程且结果写 AtomicReference，无泄漏风险。`remoteMd5` 自建 client 补齐 `callTimeout`，防止 DNS 慢时挂起。✅
- 12 分钟 latch 阻塞确认运行在 `Task` 线程池（fixed-5 后台线程），不卡主线程。✅
- 新增契约测试 3 个用例（预算集中定义 / 就绪探测 deadline+单次超时 / md5 并行+传输预算），断言与实现逐条对齐。✅
- **发现 1 个问题（非功能性）**：`NodeService.java` import 顺序被打乱——`okhttp3.OkHttpClient` 插在 androidx 组中间、`android.os.SystemClock`/`Process` 与同组顺序错乱；方法体内 4 处 `java.util.concurrent.TimeUnit.MILLISECONDS` 全限定名冗余（与同包文件风格不一致）。

### 修复

- `NodeService.java`：import 按 android → androidx → com → java → okhttp3 分组排序；新增 `java.util.concurrent.TimeUnit` import，4 处全限定名简化为 `TimeUnit.MILLISECONDS`。无行为变化。

### 第 2 轮评审（修复后终态复评 + PR #392 新增内容）

- NodeService 修复仅 import 整理与限定名简化，语义零变化；`git diff --check` 无空白错误。
- **PR #392 带入的站点批量动作焦点修复（beta 侧新代码）**：
  - `setActionEnabled` 中 `select`/`cancel` 改为 `setEnabled(enabled)`，enabled 只反映加载态；模式资格由 `setType()` 既有 `setClickable(type > 0)` 控制，两个正交语义解耦。
  - 焦点链核对：`dialog_site.xml` 显式 `nextFocusUp/Down` 链 config→search→change→select→cancel→mode，type=0 下按钮留在 D-pad 链上但不可点，`View.performClick()` 的 `isClickable()` 前置挡板确认误触安全。
  - `SiteDialogActionFocusTest` 3 个 Robolectric 用例（焦点链可达 / 点击资格模式限定 / 加载态全禁用）与实现逐条对齐，反射注入 `binding` 合法（`setField` 私有字段），不触库不触网。
  - 视觉核对：`site_action_icon.xml` / `selector_site_action.xml` 无 `state_enabled` 条目，无灰显误导。
  - 结论：**通过，无问题**（dev2 侧 C31 已做四轮评审 + 单测验证，本侧独立核对一致）。
- 对修复后完整形态重新执行编译与双 flavor 单测（非沿用旧结果）。
- 交付边界核对：待交付净差异（`origin/beta..HEAD`）为猫源超时修复 7 文件 + `NodeService` import 整理；无 `app/build/**` 产物、无临时文件。
- **第 2 轮结论**：通过，可交付。

### 第 3 轮评审：beta 增量 PR #393（dev4 播放器按钮修复）合并与复评

- 守卫首会话闭合（交付提交 1fb2b558e + tag）后，创建 PR 时发现远端 beta 又前进了（PR #393 dev4 已合并，`8e4dac760`），需增量合并才能构成「beta 有 dev1 缺失提交 → PR 可建」的状态。
- 新增守卫会话 `C31-beta-merge-review-dev1-20261001-b`，`merge --no-commit --no-ff origin/beta` 无冲突，带入 5 文件：
  - `PlayerButtonSetting.java`：`Item` 记录新增 `visible` 构造参数（双构造器兼容旧调用）；`MULTI_THREAD_PROXY` 默认标记不显示；`getHidden()` 首次运行一次性种子注入默认隐藏项（`HIDDEN_SEEDED` 标记防重复注入，用户后续选择优先）；`reset()` 同步清除种子标记。
  - `VideoActivity.java`（mobile +2 行 / leanback 已有）：底部控制栏 `multiThreadProxy`/`codecCapability` 按钮接线（mobile 绑 `onCodecCapabilityPanel`、leanback 绑 `onCodecCapability`，两 flavor 方法名各自匹配，核对无错绑）。
  - `MultiThreadProxyPlayerUiSourceTest.java`：+2 用例（按钮必须真绑定点击监听器；多线程按钮默认隐藏但用户可配置）。
  - `docs/C31-beta-merge-review-dev4-20261001.md` / `docs/player-button-wiring.md`：dev4 任务文档与设备验证记录。
- 增量核对：`5682f2b05` 仍非新 `origin/beta`（45d340418 与 8e4dac760）祖先；净差异仍仅猫源链路 7 文件。
- 合并树定向单测复跑：Leanback 73 项 0 失败（含 `MultiThreadProxyPlayerUiSourceTest` 5 项完整通过、PR#393 新增 2 用例在内）。
- **第 3 轮结论**：增量合并通过，无问题。

## 验证记录

- **双 flavor Java 编译**：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` → BUILD SUCCESSFUL（合并前形态；合并后形态在最终提交后以单测复跑覆盖，见下）。
- **Leanback 定向单测（含 PR#392 新测试，最终合并树复跑）**：`HomeMenuDialogSourceTest` + `SiteDialogActionFocusTest` + `com.fongmi.android.tv.node.*` + `CatSpiderResolveTest` + `CatSourceConfigTest` + `CatActionTest` → BUILD SUCCESSFUL，结果 XML 汇总 **98 项，0 failure / 0 error**（其中 HomeMenuDialogSourceTest 5 项、SiteDialogActionFocusTest 3 项均完整通过；pi-lens 运行器对这两个类的独立提示不适用，以 Gradle 结果为准）。
- **Mobile 定向单测**：`com.fongmi.android.tv.node.*` → BUILD SUCCESSFUL，**61 项，0 failure / 0 error**。
- **打包与覆盖安装**：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` → BUILD SUCCESSFUL，`adb install -r` 覆盖安装 Success（未卸载既有包）。
- **设备冒烟（192.168.50.3:5555）**：force-stop 后冷启动 `HomeActivity` 正常驻留（`mResumedActivity=HomeActivity`）；首页分类与内容正常渲染；logcat 全程扫描 `FATAL EXCEPTION` / `Fatal signal` / `ANR in com.silent` **零命中**。
- **明确未验证的边界**：菜单键弹窗在分类栏可见时走 `updateFilter` 而非弹窗是既有设计（`dispatchKeyEvent` 中 `isCategoryVisible()` 分支优先），本次未改动该路径；猫源 12 分钟超时的端到端长等待场景未在设备复现（原提交 `65d709a47` 已在其任务内做过 ghfast/catpaw 两源设备验证）。

## PR 边界

相对 `origin/beta`（`8ba23ac14`），`dev1` 净差异：

| 状态 | 文件 | 内容 |
| --- | --- | --- |
| M | `app/src/main/java/com/fongmi/android/tv/api/CatSource.java` | 启动等待改用集中预算 `NodeRuntime.START_TIMEOUT_MS` |
| M | `app/src/main/java/com/fongmi/android/tv/node/NodeBundle.java` | md5 双请求并行 + `callTimeout` 完整覆盖 + 传输预算 |
| M | `app/src/main/java/com/fongmi/android/tv/node/NodeLib.java` | 运行时下载改用 `LIB_TRANSFER_TIMEOUT_MS` |
| M | `app/src/main/java/com/fongmi/android/tv/node/NodeRangeZip.java` | Range 下载/HEAD 改用 `LIB_TRANSFER_TIMEOUT_MS` |
| M | `app/src/main/java/com/fongmi/android/tv/node/NodeRuntime.java` | 预算常量集中定义（12min/3min/5min/8s/2s/200ms） |
| M | `app/src/main/java/com/fongmi/android/tv/node/NodeService.java` | 就绪探测 deadline 化 + 独立 2s 单次超时 + import 整理 |
| M | `app/src/test/java/com/fongmi/android/tv/node/NodePortSelectionTest.java` | 新增 3 个契约用例锁定上述行为 |

交付提交为合并提交（父 2 = `origin/beta` 45d340418），`dev1` 是 `origin/beta` 的直接后继，合并进 beta 时可快进无冲突面。

PR 只做创建，不执行合并。

## 回滚锚点

- 合并前锚点：`dev1 @ 65d709a47`（`origin/dev1 @ 890934423`）。
- 回滚：`git revert <本任务交付提交>`；改动为超时预算与探测方式调整，无偏好键或数据格式变更。
- 被剔除提交保持非 `origin/beta` 祖先：本任务未把 `5682f2b05`、`59f438285`、`c900dac20` 或任何历史 revert/reset 提交作为独立改动带入。

## Recovery anchor

- 目标：合并 origin/beta 最新（PR #391 + PR #392 + PR #393 增量）→ 复评 → 修复 → 验证 → 提交推送 → 创建 beta PR。
- 状态：首会话已闭合（合并提交 1fb2b558e + tag 已推送）；增量会话 -b 合并 origin/beta 8e4dac760（PR #393 dev4 播放器按钮）无冲突，第 3 轮评审通过，合并树 Leanback 单测 73 项 0 失败（含 MultiThreadProxyPlayerUiSourceTest 5 项）。
- 下一步：task_guard finish 提交增量合并并打 tag，推送 dev1，创建 PR 到 beta（只创建不合并）。
