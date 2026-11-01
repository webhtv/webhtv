# C116-lab-7z-native-progress-dev3-20261101

## Recovery anchor

- **目标**：合并 `origin/beta` 最新代码后，评审 `dev3` 上「已提交未推送」与「工作区未提交」的代码；有必修项则修复并验证，通过后再次评审，循环直到通过；然后提交、推送 `dev3`、创建到 `beta` 的中文 PR，最后拉取远端最新代码。
- **验收标准**：
  1. `origin/beta` 是 HEAD 的祖先（`git merge-base --is-ancestor origin/beta HEAD` → true），落后计数为 0。
  2. 工作区改动全部评审过，每条必修项要么已修复并有「会变红」的守卫，要么有明确的不修理由。
  3. 双 flavour 编译通过（leanback + mobile，arm64_v8a）；`com.fongmi.android.tv.lab.*` 定向单测 0 失败。
  4. 改动经任务守卫 `finish` 落盘并打 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR；随后 `git fetch` 拉取远端最新。
  5. 结束时除预先存在的脏项（`%SystemDrive%/`、`.dbg.txt`、`.design-evidence/`）外工作区无残留改动。
- **任务坐标**：branch `dev3`，base HEAD `e8c78ae8db241a98dd1da5d799692b7eb8db164d`（"Merge pull request #511 from Silent1566/dev3"），`origin/beta` = 同一提交。
- **当前状态**：评审通过，修复已落地并验证；交付坐标见 §5。

---

## 1. 合并远端 beta

| 项 | 值 |
| --- | --- |
| 合并前 HEAD | `9c695e9982` |
| `origin/beta`（fetch 后） | `e8c78ae8db241a98dd1da5d799692b7eb8db164d` |
| `git merge-base --is-ancestor origin/beta HEAD`（合并前） | exit 1（HEAD 是 origin/beta 的祖先） |
| 合并方式 | `git merge --ff-only`，零冲突纯快进 |
| 合并后 `git rev-list --left-right --count origin/beta...HEAD` | `0  1`（唯一的 1 就是本次合并提交本身） |

本次带入的 5 个提交只碰 3 个文件（`VideoActivity.java`、`SpeedAdjustKeyContractTest.java`、`docs/C115-...md`），
内容已由前一任务评审覆盖，本轮不重复评审。

## 2. 待评审改动

```
 M app/src/main/java/com/fongmi/android/tv/lab/LabEnv.java      (+99 −5)
?? app/src/test/java/com/fongmi/android/tv/lab/LabEnv7zProgressTest.java （本任务新增）
```

`git log --all -S'-bsp1'` / `-S'extract7zNative'` 只命中 `79bbb3e01d`「新增：实验室功能与猫源支持」，
即这套进度改动从未提交过，属首次评审，不能跳过。

### 2.1 改动意图

`LabEnv.install()` 把 `ExtractProgress.onExtract(done, total)` 直接喂给 `LabActions` 的进度条，
契约是**字节**（`done`=已解压字节，`total`=解压后总字节，`total<=0` 表示未知），
`LabActions.java:149-164` 用 `done*100/total` + `formatSize()` 渲染。
随包 7zz（`app/src/*/assets/lab/7zz`）支持 BCJ2 等多流 coder，是 commons-compress 解不了时唯一的兜底，
但它的 `-bsp1` 输出只有「当前文件百分比」。本轮改动就是让 7zz 这条路径也能驱动进度条。

## 3. 评审结论与必修项

| # | 问题（改前） | 判定 | 修复 |
| --- | --- | --- | --- |
| 1 | 7zz 百分比被直接当字节数上报给上层，`formatSize()` 会显示荒谬数值，进度条与文字自相矛盾 | 必修 | 新增 `static long nativeBytes(int percent, long total)`（`LabEnv.java:995-998`）折算成字节 |
| 2 | 7zz 报的是**当前文件**百分比，同包下一个文件回到 0%，进度条会来回抖 | 必修 | `nativeBytes` 封顶 99%，100% 只留给解压真正结束时补发（`LabEnv.java:983`） |
| 3 | `total<=0`（量不出总字节）时若仍上报，等于伪造进度 | 必修 | `nativeBytes` 在 `total<=0` 时返回 0，上层 `LabActions` 的 `total>0` 分支不成立 → 退化为不确定进度 |
| 4 | 读 stdout 的循环在 `cb==null`/无进度时提前 `break` 之外的分支没有排空管道，7zz 会因管道写满卡死 | 必修 | 循环体改成 `if (cb == null \|\| total <= 0) continue;`，始终排空（`LabEnv.java:975`） |
| 5 | 百分比解析没有单元测试，属于「无法证明正确」的解析器 | 必修 | 新增 `LabEnv7zProgressTest`，10 个用例覆盖 CR 刷新 / 跨 buffer / 文件名数字 / 位数 / >100 / 单调 / 退格 / 制表符 / 封顶 / 无 total |
| 6 | `nativeTried` 前，`cb == null \|\| total <= 0` 时 7zz 失败后仍会再调一次 `extract7zNative`，同一批文件白解一遍 | 必修 | 提为局部变量 `boolean nativeTried`（`LabEnv.java:895`），第二次回退改为 `if (!nativeTried && ...)` |

不修项（已记录理由）：7zz 不可用时 `extract7zNative` 返回 false 走 compat 路径，属于既有兜底语义，不改。

## 4. 验证

- 编译 + 单测命令（必须显式指定 JDK 21，PATH 上的 Oracle JDK 不兼容）：
  `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'; .\gradlew.bat --console=plain :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.lab.*'`
- 结果：`EXIT=0`；测试 XML 全部 `f=0`：
  `LabEnv7zProgressTest t=10`、`LabEnvTest t=6`、`LabProcessIdentityTest t=3`、
  `LabRunnerProcessCompatTest t=2`、`LabSettingsDialogThemeTest t=8`、`LabTvFocusSourceTest t=12`。
- **守卫非空转（变异验证）**：临时去掉 `nativeBytes` 里的 `Math.min(percent, 99)` 后重跑，
  `LabEnv7zProgressTest t=10 f=1`，失败信息
  `nativeBytesLeavesHundredPercentForRealCompletion :: java.lang.AssertionError: expected:<990> but was:<1000>`；
  随后已还原 clamp 并重跑全绿（即本条 10:24:06 的那一轮）。
- 7zz 开关能力已实证：两个 ABI 的 `7zz` 里存在帮助串 `-bs{0|1|2}`、`set output stream`、`Unsupported switch postfix -bb`，
  说明 `-bsp1`/`-bso0` 被该二进制支持（7-Zip 的帮助串是模板，不存在字面量 `-bsp1`，故不能用 `Contains('-bsp1')` 判断）。
- 未做真机往返：本机无 7z 可执行文件，无法用随包 7zz（arm64 ELF）在 Windows 上跑真实解压往返，只能靠单测 + 变异验证。

## 5. 交付坐标

| 项 | 值 |
| --- | --- |
| 分支 | `dev3` |
| 提交 | 本文件所在提交 `fix(lab): 7z 原生解压进度按字节上报` |
| recovery tag | `recovery/C116-lab-7z-native-progress-dev3-20261101/20261101102600` |
| 评审记录 | `docs/C116-lab-7z-native-progress-dev3-20261101.md`（本文件） |

## 6. 任务守卫说明

`.codex/scripts/task_guard.sh` 在本机不可用（需要 WSL/HCS，HCS 服务未安装，报 `HCS_E_SERVICE_NOT_AVAILABLE`），
因此改用等价的普通 `git commit` + annotated local `git tag` 完成「提交即完成」的要求，
并且只 stage 本任务拥有的两个文件，不触碰预存脏项。
