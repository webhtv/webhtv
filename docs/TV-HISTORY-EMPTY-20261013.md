# TV-HISTORY-EMPTY-20261013：电视端历史页的空态仍是默认的「这里什麽都没有」

## Recovery anchor

- 目标：电视端历史页的空态补齐三件套（标题 + 解释 + 主动作），关闭 TV-4 §7.3 的遗留项。
- 允许路径：`app/src/leanback/java/.../activity/HistoryActivity.java`、`app/src/testLeanback/java/.../ui/activity/TvStateSurfaceContractTest.java`、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `77d7daf44bd`。
- 验收：① 历史页接入三件套且主动作真实回首页；② 复用既有三语字符串（不新增文案）；③ `testLeanback` 定向测试全绿 + 真机看到三件套。
- 回滚：撤销本任务原子提交即可回到「默认单行文案」；无数据迁移。
- 交付坐标：提交 `ecacf87742e`（dev4），recovery tag `recovery/TV-HISTORY-EMPTY-20261013/20261013131621`；未推送。
- 下一步唯一动作：无（本批已交付）。**唯一剩下的空态缺口**是两端 `FileActivity`，需要先定主动作方向（见 §7.1）。

## 1. 现象与根因

TV-4 给空态补三件套时**只接了收藏页**，并把"其余宿主"记为遗留项（`docs/TV-EMPTY-LOADING-STATE-20261012.md` §7.3）。历史页因此一直显示布局里的默认单行文案 `@string/error_empty`（「这里什麽都没有 👀」）——既不解释为什么空，也不给出下一步，违反 §9.9 的「不能只显示一句'暂无数据'」。

上一批（`LIST-STATE-CONVERGENCE`）的真机截图正好把这一点拍了下来：列表为空时**空态确实出现了**（钩子驱动 ✓），但文案仍是那一句默认的。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/webhtv-unified-visual-design-system-20260920.md` §9.9 | 仓库既有设计权威 | A（本地） | 空态 = 标题 + 解释 + 一个主动作 |
| `docs/TV-EMPTY-LOADING-STATE-20261012.md` §7.3 | 本仓库既有交付记录 | A（本地） | 明确记录"其余宿主尚未接入"，本批执行 |
| `docs/LIST-STATE-CONVERGENCE-20261013.md` §6.2 的真机截图 | 本仓库上一批 | A（本地） | 直接证据：空态出现但文案是默认单行 |
| `docs/MOBILE-HISTORY-EMPTY-20261013.md` | 本仓库既有交付记录 | A（本地） | 已加三语 `history_empty_*`，两端共享（在 main 源集）→ **本批不新增文案** |
| 电视端 `KeepActivity` 的 `setEmptyContent` / `goHome` | HEAD `77d7daf44bd` | A（本地） | 同构做法，直接复用 |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | §9.9 继续不满足（截图已证实） | 拒绝 |
| 2. **复用收藏页的同构做法 + M-5 的三语字符串（采用）** | 零新文案；与收藏页完全一致 | 需要新增 `goHome()`（该页原本没有） | **采用** |
| 3. 顺手把电视端/手机端 `FileActivity` 也接入 | "一次做完" | 它是**本地文件浏览器**，"返回上级"（根目录时会退出）与"重新扫描"是**导航/产品决策**，不该由实现者代拟 | 拒绝，记为 §7.1 |

## 4. 实施

1. 电视端 `HistoryActivity`：`initView` 调用新增的 `setEmptyContent()`（标题/解释/动作取 `history_empty_*`，动作 → `goHome()`），并新增 `goHome()`（与收藏页/播放页的回首页 flag 写法一致）。
2. `TvStateSurfaceContractTest` 新增一项：历史页接入三件套、三个字符串在默认与简中表都存在、主动作真实回首页。

## 5. 不变量与回滚

- 不改动列表、删除、观影报告、焦点恢复与状态驱动（状态仍由 `BaseDiffAdapter` 的钩子统一驱动）。
- 不新增字符串：复用手机端批次已加的三语 `history_empty_*`。
- 不改动 `FileActivity`（见 §7.1）。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.Tv*"` → **BUILD SUCCESSFUL in 1m 31s**（新增 1 项全绿）。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 2m 51s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增字符串。

### 6.2 设备实测（`192.168.50.3:5561`，装 **leanback flavor**）

| 检查项 | 结果 |
|---|---|
| 打开电视端历史页（列表为空） | **三件套完整出现**：Lottie + Display 档标题「还没有观看记录」+ 解释「播放过的内容会自动出现在这里，方便继续观看。」+ 主动作「去点播」 |
| 与改动前对比 | 改动前同一页只有默认单行文案「这里什麽都没有 👀」（见 `docs/LIST-STATE-CONVERGENCE-20261013.md` §6.2 的截图） |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-tv9-history.png`（对照 `.design-evidence/e5561-c1-history.png`）。

**过程说明（诚实记录）**：首次尝试时应用还在闪屏（模拟器启动约需 60–90 秒），我发出的方向键落在闪屏上，随后进程被中断退出（logcat 里**没有** FATAL 异常，所以不是崩溃）。等待更久后重启并重新导航即成功。`dumpsys window | grep mCurrentFocus` 在此期间多次返回 `null`，与实际状态不符——**该命令在这台模拟器上不可靠**，应以截图为准。

### 6.3 结论

电视端历史页的空态达到 §9.9 的三件套，TV-4 §7.3 的遗留项关闭。至此除文件浏览器外，两端所有列表宿主的空态都已接入。

### 6.4 残余风险与诚实说明

- **主动作「去点播」未在真机上点击验证**：它与收藏页/播放页用的是同一套 flag 写法（`FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP | SINGLE_TOP`），而收藏页那处已在此前批次验证过；本批只确认了按钮的渲染与文案。
- **`FileActivity`（两端）仍未接入**（见 §7.1），它的主动作是导航/产品决策。
- **未在真机上验证「有记录 → 删空」这一转变**：历史数据已空，没有可再删的数据；该路径由 `LIST-STATE-CONVERGENCE` 批次的钩子统一驱动。

## 7. 相邻发现（未纳入本批）

1. **`FileActivity`（两个 flavor）是最后两处缺三件套的宿主。** 它是本地文件浏览器，空态意味着"这个目录里没有可播放的视频"，合理的主动作有两个方向：**返回上级**（`dir` 有父目录时才有意义；根目录时会退出应用）或**重新扫描**（对未变化的目录帮助有限）。这是导航/产品决策，需要先定方向。
2. **两端设置页仍是两套代码**（Activity vs Fragment），行为已对齐但实现未合并。
3. **手机端仍是三套状态机制并存**，见 `docs/MOBILE-HOME-ERROR-STATE-20261013.md` §7.1。
