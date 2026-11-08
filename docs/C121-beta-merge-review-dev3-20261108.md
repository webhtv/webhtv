# C121 · dev3 合并 origin/beta 并修复「电视首页功能区焦点环绕」

- 分支：`dev3`
- 合并前 HEAD：`f375e7019d`（Merge pull request #533 from Silent1566/dev4）
- 合并基线：`origin/beta` tip `7c64c9f5f1`（Merge pull request #535 from Silent1566/dev2）
- 合并提交：`1943086d9c`（`merge: 合并 origin/beta 最新代码（播放缓存上限接入播放预算与总容量）`，0 冲突）
- 日期：2026-11-08（Asia/Shanghai）

## 1. 合并事实

| 项 | 结果 |
| --- | --- |
| 合并前拓扑 | `f375e7019d` 是 `7c64c9f5f1` 的祖先，beta 独有 8 个提交 |
| beta 独有内容 | 16 文件 `+693/−33`：`PlaybackCacheBudget`（新增）、`CacheCenter`/`CachePolicyStore`/`CacheScheduler`、`MpvHlsProxy`、`MediaSourceFactory`、`CacheManagementDialog`、三个 `strings.xml`、2 个测试、4 份文档 |
| 冲突 | 0 处（beta 侧改动与本分支工作区改动路径零交集） |
| 合并后相对 beta 的净差异 | 合并提交后仅剩本任务未提交的两处环绕改动，即合并结果等于 beta 内容，无复活、无丢失 |
| 合并后可编译性 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL in 2m 44s`（基线，含未提交改动） |

## 2. 被评审的代码范围

评审对象是「已修改的代码」：工作区未提交的两处改动，加上本轮修复产生的增量。

| 文件 | 内容 |
| --- | --- |
| `app/src/leanback/.../ui/custom/CustomRowPresenter.java` | 新增 `wrapFocus` 开关与 `wrapToEdge`，行级接管左右边界环绕 |
| `app/src/leanback/.../ui/activity/HomeActivity.java` | 仅功能区（`FuncPresenter`）行 `.setWrapFocus(true)` |
| `app/src/leanback/.../ui/presenter/FuncPresenter.java` | 仅补注释，声明项级环绕已成为落点 |
| `app/src/testLeanback/.../ui/custom/CustomRowPresenterWrapFocusTest.java` | 新增 4 例守卫测试 |

已提交未推送的提交无需重复评审：`git merge-base --is-ancestor HEAD origin/beta` 为真，本地 dev3 的 39 个"未推送"提交全部已在 beta 历史中，其内容由 C117–C120 各轮评审覆盖且已合入 beta。

## 3. 第一轮评审（两轴独立，只读子智能体，0 修改）

两轴分别为「正确性/时序」与「规格/接线完整性」。评审前先取得一条决定性事实：

> **`BaseGridView.dispatchKeyEvent` 先调用 `mOnKeyInterceptListener.onInterceptKeyEvent(event)`（字节码 offset 0–21），返回 `true` 直接 `ireturn`，之后才调用 `RecyclerView.dispatchKeyEvent`（offset 22–31）。**
> 证据：`leanback-grid-1.0.0.aar` 的 `classes.jar`，`javap -p -c androidx.leanback.widget.BaseGridView`。

由此确定：启用 `wrapFocus` 后，行级拦截器会**先于** `FuncPresenter` 的项级 `OnKeyListener` 拿到边界左右键，因此改动不是"双重环绕"，而是**旧实现被架空**。

### 3.1 必修项与处置

| 编号 | 缺陷 | 处置 |
| --- | --- | --- |
| M-1 / M2 | `requestFocusAt` 重试分支无条件 `return true`，最终失败时按键被消费而焦点不动，与同处注释「失败则不消费按键」相反；`postOnAnimation` 在行被回收时还会静默丢失 | 已修：删除 `requestFocusAt`，改由 `grid.setSelectedPosition(target, task)` 承担滚动与聚焦 |
| M2 / M-2 | 手写「逐步 `scrollBy` + 重试」丢掉了旧实现在目标项未 attach 时 `scrollToPosition` + post 的确定性兜底，属行为回归 | 已修：同上；并由 grid 自己滚动到目标，attach 后回调换焦 |
| M1（轴 B） | 同一行并存两套环绕实现且无任何标记指出权威 | 已修：`FuncPresenter` 处补注释声明「已由行级接管，保留为未开启行级环绕时的落点」 |
| M3（轴 B） | `wrapFocus` 非 final 且 `keepFocusInRow` 同时为真时 `edgeListener` 静默不可达 | 已修：`setWrapFocus` 补 Javadoc 说明调用时序与优先级；当前无同时为真的调用点（`setWrapFocus` 全仓库唯一调用在 `HomeActivity:675`，1 参构造器 ⇒ `keepFocusInRow=false`） |
| B3（轴 B） | 注释与实现相反、`HomeActivity` 缺"为什么只有这一行"的说明 | 已修：三处注释均重写为与实现一致 |

### 3.2 记录但本轮不修

| 项 | 理由 |
| --- | --- |
| `exposeTarget` 的 RTL 对称性 | 方法已随修复删除，问题消失 |
| `FuncPresenter` 项级环绕成为死代码 | 行为已被行级覆盖，且可作未开启行级环绕时的落点；注释已标明，删除属清理而非缺陷 |
| `wrapFocus` 非 final 的形状问题 | 当前唯一调用点在行绑定前完成，无实际时序风险；改成构造器参数会与既有 `(int, boolean keepFocusInRow)` 重载产生歧义 |
| 只给功能区行开启 | `Vod`/`History` 是长列表，环绕会把滚动位置甩回另一端；注释已写明取舍 |
| 项数 ≤1 时 ACTION_UP 的消费 | 修复后项数 ≤1 在 `ACTION_DOWN` 之前就放行，行为与改动前该行一致 |
| 真机视觉/长行卡顿 | 无设备证据，不作推断性改动 |

## 4. 修复轮验证

| 项 | 命令 / 方式 | 结果 |
| --- | --- | --- |
| 双 flavour 前置基线 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL（2m 44s） |
| 新增守卫测试 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.custom.CustomRowPresenterWrapFocusTest'` | 4 例全绿 |
| 变异探针 | 把 `wrapToEdge` 的「项数 ≤1 返回 false」改成 `return true` | `aRowThatCannotWrapDoesNotSwallowTheEdgeKey` 转红（`AssertionError` at 该例 assertFalse 行），其余 3 例仍绿；还原后 `CustomRowPresenter.java` SHA-256 `C1B349CD77FDD23F06144E1531067EA7C54F4A726207FFAF9FEDED645FB8EEFF` 且 4 例复绿 |

### 4.1 守卫测试覆盖的契约

1. 末项按右 → 首项获焦（`wrapFocus` 真的换焦，不是只吞键）；
2. 首项按左 → 末项获焦；
3. 非边界按键放行（拦截器不影响普通移动）；
4. 行内项数 ≤1 时**不得吞掉边界键**（本次修复的核心契约，已由变异探针证明非空转）。

### 4.2 环境注意

构建期间出现过 `Timeout waiting to lock Build cache (G:\GradleCache\caches\build-cache-1)`，成因是并发 gradle 进程，验证统一改用 `--no-build-cache` 规避；该锁与产品代码无关。

另需注意：把 gradle 输出通过 `Select-Object -First N` 截断会让管道提前关闭并吞掉真实退出码，本轮曾因此误判一次探针结果，后续验证改为读取尾部输出并显式回显 `GRADLE_EXIT`。

## 5. 第二轮评审（针对修复增量）

评审对象：`git diff`（三个生产文件）+ 新增测试文件。三轴为「修复正确性」「测试有效性」「回归面与遗留」。

结论：**可交付**，同时提出 1 条字面残余必修与 1 条测试盲点，均在收口前修掉。

### 5.1 第二轮必修项与处置

| 编号 | 缺陷 | 处置 |
| --- | --- | --- |
| R2-1 | 目标 holder 在场但 `requestFocus()` 失败时，落到 `setSelectedPosition` 会对同一个 itemView 再请求一次焦点（该分支同步执行），必然同样失败，但方法仍 `return true` —— 第一轮「消费但焦点不动」的字面残余；且此时 `mFocusPosition` 已被改写，会让后续边界判定与真实焦点脱钩 | 已修：改为 `if (holder != null) return holder.itemView.requestFocus();`，holder 在场就无需滚动，失败即放行 |

### 5.2 第二轮测试盲点与处置

| 编号 | 问题 | 处置 |
| --- | --- | --- |
| S2-1 | 4 个用例都不断言焦点，而 `setSelectedPosition` 会同步改写 `mFocusPosition`，所以「改了选中位却没换焦」时四例仍会全绿 | 已修：首例补 `assertTrue(grid.findViewHolderForAdapterPosition(0).itemView.hasFocus())` |

该断言同时回答了第二轮标为「未确认」的问题：Robolectric 下 `requestFocus` 确实能建立焦点链，断言不是空转（见 §5.3）。

### 5.3 未能验证的一条路径（记录，不写空转用例）

生产上功能区默认溢出一屏，目标项常常尚未布局，走的是 `setSelectedPosition` 的滚动后再换焦分支。该路径试图用「20 项 + 先把选中位推到中间，使末项不在场」来构造，实测在 Robolectric 下 `setSelectedPosition` 不会真正产生滚动布局，前提断言（目标 holder 为 null）无法成立。按「不写会空转的用例」处理，删除该用例并清理随之失去引用的 `assertNull` import 与 `drainFrames` 辅助方法，留待真机验证。

变异探针也印证了这一点：把 lambda 里的 `attached.itemView.requestFocus()` 掏空后 4 例仍全绿，因为现有用例的目标项都在场，走的是 holder 早退分支。

## 6. 最终验证

| 项 | 命令 / 方式 | 结果 |
| --- | --- | --- |
| leanback Java 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 新增守卫测试 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests '…CustomRowPresenterWrapFocusTest'` | 4 例全绿 |
| 既有守卫 | 同上并含 `HomeCategoryNavigationSourceTest` | 通过 |
| 变异探针 A（项数 ≤1 放行） | `return false` → `return true` | `aRowThatCannotWrapDoesNotSwallowTheEdgeKey` 转红，其余 3 例绿 |
| 变异探针 B（lambda 换焦） | 掏空 `attached.itemView.requestFocus()` | 未转红 —— 用例目标项都在场，未覆盖滚动分支；已在 §5.3 说明 |

## 7. 回滚锚点与交付坐标

- 回滚基线：`f375e7019d`（合并前 HEAD）；回退合并提交 `1943086d9c` 与修复提交 `3b4b6d3e47` 即可恢复。
- 合并提交：`1943086d9c`
- 修复提交：`3b4b6d3e47`（5 文件，`+310/−2`）
- recovery tag：`recovery/C121-beta-merge-review-dev3/20261108113318-3b4b6d3e47`（annotated，指向 `3b4b6d3e47`）
- 推送：`origin dev3`（`a19433f7ef..3b4b6d3e47`）
- PR：<https://github.com/Silent1566/webhtv/pull/538>（base `beta`，head `dev3`）
- 拉取最新：`git pull --ff-only origin dev3` → `Already up to date.`

### 7.1 环境差异记录

`task_guard.sh` 依赖 bash，本机只有 WSL 的 `C:\WINDOWS\system32\bash.exe`，运行时报
`Bash/Service/CreateInstance/CreateVm/HCS/HCS_E_SERVICE_NOT_AVAILABLE`，脚本无法执行。本轮改为手工等价操作：
只 `git add` 本任务的 5 个路径（不触碰任何预存脏文件）、按任务 ID 打 annotated recovery tag。预存的
`%SystemDrive%/`、`.dbg.txt`、`.design-evidence/` 与约 200 个 `.tmp-*` 全部保持未跟踪状态未纳入提交。

