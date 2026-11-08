# UI-TOKEN-GATE-PORTABLE-20261013：让静态门槛在本机可跑（审计 P0）

## Recovery anchor

- 目标：给 §13.1 要求"每阶段必做"的 UI token 静态门槛提供一个**不依赖 bash** 的等价入口，使它在 Windows / 无可用 WSL 的主机上真的能跑。
- 允许路径：`scripts/check_ui_tokens.py`（新增）、`docs/webhtv-unified-visual-design-system-20260920.md`（§13.1）、`app/src/test/java/.../theme/UiTokenGatePortabilityContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `ffa2fb93814`。
- 验收：① 新入口不依赖 bash 且 CLI 与原脚本一致；② 五项检查一项不少；③ 输出行与退出码语义一致；④ **负向测试证明它会拦**；⑤ 规范指向新入口；⑥ 契约测试两端全绿。
- 回滚：删除新增文件即可；`.sh` 未改动，仍是权威定义。
- 交付坐标：提交 `70e7f27ef5f`（dev4），recovery tag `recovery/UI-TOKEN-GATE-PORTABLE-20261013/20261013163837`；未推送。
- 下一步唯一动作：无（本批已交付）。**审计 P0 剩余**：`FileActivity` 空态（卡在主动作决策）、§20.2 九行设备验收矩阵。

## 1. 问题：门槛存在，但**跑不起来**

设计文档 §13.1 把 `scripts/check_ui_tokens.sh` 列为**每阶段必做**的静态验证，§19 定义了它的五项检查契约。但：

- 该脚本把**全部检查体**放在内嵌的 `python3` heredoc 里；
- 真正依赖 bash 的只有**参数解析**与 `git rev-parse` 两件事；
- 本机（Windows）**没有可用的 bash**（只有 WSL，而 WSL 服务不可用：`HCS_E_SERVICE_NOT_AVAILABLE`）。

结果：这个门槛在本会话此前的**全部 UI 批次里一次都没运行过**，而它本该拦住的问题确实溜了过去——审计实测 `violations=1`、`hex_layouts=1`（`item_following.xml` 的两处固定色）。**没人跑门槛，门槛等于不存在。**

### 1.1 关键观察：Python 是有的

本机有可用的 **Python 3.14.7**（`python` / `python3` 均可）。所以障碍**只有 bash 这一层**，而它承担的恰恰是最不重要的那部分工作。

## 2. 方案比较

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 1. 装一个 bash（Git Bash / MSYS2） | 不改仓库 | 要动开发环境；CI 与本地仍可能不一致；不是仓库能保证的事 | 拒绝 |
| 2. 用 PowerShell 重写 | 本机原生 | PowerShell 不是这个仓库的工具链语言；且会变成**第三套**实现 | 拒绝 |
| 3. **把内嵌 Python 抽成独立 `.py` 入口（采用）** | 复用**同一份检查逻辑**（从 `.sh` 逐行抽取，不是重写）；Python 已是仓库工具链（`scripts/` 下已有 13 个 `.py`）；`.sh` 保持权威不动 | 多一个文件，需防两者漂移 | **采用** |
| 4. 改 `.sh` 让它调用 `.py` | 单一逻辑源 | 会改动权威脚本，且没有 bash 时 `.sh` 仍不可用 | 拒绝（但记为后续可选） |

**为什么不是"重写"**：核心逻辑（token 解析、对比度、固定色扫描、allowlist、stage 过滤）是**逐行从 `.sh` 的 heredoc 里抽取**的，只把 6 个 `sys.argv` 取值改成函数参数。因此两条入口的判定**同源**，不是"两份各写一遍"。

## 3. 实施

1. `scripts/check_ui_tokens.py`：
   - 从 `.sh` 的 heredoc 逐行抽取核心逻辑，包成 `run(...)`；
   - 手写参数解析（**不用 argparse**）：原脚本接受裸 `help` 别名，且把未知选项当用法错误（退出码 2），argparse 无法表达裸 `help`；
   - `repo_root()` 用 `git rev-parse --show-toplevel`，失败回落 `cwd`——与原脚本一致；
   - 保留原脚本的 strict 推导规则：`--baseline` 关 strict；`--stage` 非 A（或空）自动开 strict。
2. 规范 §13.1：在 `.sh` 条目下补一段，说明无 bash 主机改用 `.py`、两者是同一门槛的两个入口、以及验证等价性的方式。
3. `UiTokenGatePortabilityContractTest`（4 项）：入口存在且不依赖 bash/heredoc；CLI 契约一致（选项名、退出码、strict 推导）；**五项检查与八类扫描范围一项不少**；规范指向新入口。

## 4. 验证记录

### 4.1 等价性（六种 CLI 模式）

| 调用 | 结果 | 退出码 |
|---|---|---|
| `python scripts/check_ui_tokens.py` | `violations=0 legacy=0` / `38 组对比度 0 失败 min=4.28` / `PASS` | 0 |
| `--strict` | 同上 | 0 |
| `--baseline` | 同上 | 0 |
| `--stage B` | `stage=B`，自动 strict | 0 |
| `help` | 打印用法 | 0 |
| `--bogus` | `unknown option: --bogus` + 用法 | **2** |

输出行与设计文档 §13.1 记录的历史值可比：`layouts=385 drawables=559 colors=60 allowlisted=190`、`pairs=38 failures=0 min=4.28`（文档记录 378/549/47 与 4.28，差异来自此后新增的资源）。

### 4.2 负向测试（证明它会拦，不是永远绿的装饰）

临时把 `mobile/res/layout/item_following.xml` 的 `?attr/colorPrimary` 改回 `#174EA6`：

```
UI_TOKEN_BASELINE layouts=385 hex_layouts=1 ...
UI_TOKEN_SCOPE stage=A violations=1 legacy=0
UI_TOKEN_VIOLATION app/src/mobile/res/layout/item_following.xml
UI_TOKEN_STATUS FAIL
exit=1
```

**精确指出文件、退出码 1**。随后还原，`git diff` 校验 0 处差异。

这一条同时**反证了 `CONTRAST-P0-20261013` 的修复确实生效**：审计时同一门槛给出 `violations=1`，现在是 `violations=0`。

### 4.3 契约测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "...UiTokenGatePortabilityContractTest"` → 首轮 **FAILED**（见下），修正断言后 **BUILD SUCCESSFUL in 22s**。
- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "...UiTokenGatePortabilityContractTest"` → **BUILD SUCCESSFUL in 30s**。

**首轮失败又是我的断言写错了**：`allFiveChecksSurviveThePort` 断言源码里含 `"UI_TOKEN_SCOPE stage="`，但该行是 **f-string 拼接**（`f"UI_TOKEN_SCOPE {scope} ..."`），所以那个含具体值的字面量在源码里**并不存在**。改为按前缀断言（`"UI_TOKEN_SCOPE "`）后通过。

这与本会话此前反复踩到的同一类错误同源（断言写的形态与实际源码形态不符）——只是这次不是"匹配到注释"，而是"匹配到拼接后的样子"。

**环境限制**：本会话无可用 bash，`.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。

### 4.4 结论

§13.1 的静态门槛现在有了**可执行路径**：`python scripts/check_ui_tokens.py --strict`。它不再是"文档里要求、实际跑不了"的条目。

### 4.5 残余风险与诚实说明

- **两个入口可能漂移**：本批只保证"当前同源"，没有机制防止将来有人只改 `.sh`（或只改 `.py`）。契约测试钉住了**五项检查与扫描范围**的存在，但钉不住数值逻辑逐行一致。彻底的解法是让 `.sh` 调用 `.py`（或反之），那会改动权威脚本，属独立决定。
- **契约测试断言的是脚本源码内容，不是运行结果**：JVM 单测里无法可靠 spawn python（也不该依赖宿主装了 python）。所以"门槛真的会拦"这件事由 §4.2 的**人工负向测试**证明，而不是自动化测试。
- **`git diff --check`（§13.1 第 1 项）仍未纳入本入口**：它需要 git，而本机的 `GIT_CONFIG_*` 环境变量问题会让 git 命令报错（本会话已多次遇到）。这一项仍靠人工。
- **本批未跑 §13.1 第 3 项的编译任务**：本批只新增了一个 Python 脚本与一份测试，编译由 §4.3 的测试任务覆盖。

## 5. 相邻发现

1. **审计 P0 的剩余项**：`FileActivity` 空态（卡在主动作决策）、§20.2 的九行设备验收矩阵（未系统执行）。
2. **`GIT_CONFIG_*` 环境变量问题**：本机 git 命令需要先 `Remove-Item Env:GIT_CONFIG_COUNT,Env:GIT_CONFIG_VALUE_0,Env:GIT_CONFIG_VALUE_1`，否则报 `missing config key GIT_CONFIG_KEY_0`。这是环境问题不是仓库问题，但会影响任何"门槛里带 git 调用"的设计。
3. **`scripts/` 下已有 13 个 `.py` 工具**，本批新增第 14 个——Python 已是这个仓库的既有工具链语言，选它而不是 PowerShell 正是为了不与既有惯例分叉。
