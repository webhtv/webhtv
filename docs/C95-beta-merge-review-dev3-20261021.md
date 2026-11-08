# C95-beta-merge-review-dev3-20261021

## Recovery anchor

- **目标**：把 `origin/beta@590c424568`（PR #473 合入后）合并进 `dev3`（零冲突、零丢失），评审 `dev3` 已提交未推送的代码（含上一轮尚未评审的崩溃日志地址功能 `30324abe36`），修复必修项并验证，然后复评通过、提交、推送 `dev3`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 带入内容与 `dev3` 自有改动的语义交集为 0（仅 `strings.xml` 相邻新增行）。
  2. 评审覆盖：崩溃功能 18 个路径全量评审；C92（`231bbea99b` 已评审）与 C93/C94（dev2/dev4 已评审且本次合并 0 冲突、路径无交集）按“已评审 + 无合入冲突”跳过并留证。
  3. 每个必修项均已修复且修复有可复核证据；修复后第二轮复评通过。
  4. 双 flavor arm64 Debug 定向单测全绿（mobile 26 例 / leanback 19 例）；`assembleMobileArm64_v8aDebug` 成功；模拟器实测覆盖横屏与极小屏滚动可达性、临时崩溃会话页面与下载内容。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR（含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`upstream`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C95-beta-merge-review-dev3-20261021`，base HEAD `0c2d6d9ac6d7d030d2d54237f3bf1808448102e6`（合并提交），branch `dev3`，受保护脏路径 0
- **当前状态**：合并、首轮评审、修复、验证、第二轮复评、提交、推送、PR、拉取均完成；工作树干净
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `590c424568`（PR #473 合入后，2026-10-21 23:0x `git fetch origin beta dev3 --prune` 取得） |
| 本地 | `dev3` = `30324abe36`（`feat(crash)` 崩溃日志地址功能提交） |
| 合并提交 | `0c2d6d9ac6d7d030d2d54237f3bf1808448102e6`（双亲 `30324abe36` / `590c424568`） |
| 冲突 | 0（自动合并） |
| 带入路径 | 25 个：leanback/mobile `LiveActivity`（播放器按钮设置接入、平板全屏方向）、`SettingActivity`/`SettingFragment`（缓存行手势反转）、缓存清理引擎/弹窗、`LiveActivityLayoutTest`、C93 文档等 |
| 丢失面 | 0（`beta` 树相对合并树无删除） |
| 复活面 | 0（无旧实现被重新引入） |
| 路径交集 | 仅 `app/src/main/res/values*/strings.xml`：`dev3` 新增崩溃会话字符串、`beta` 新增 `cache_cleanup_busy`，相邻新增行无覆盖 |

## 2. 评审范围与跳过依据

- **全量评审**：`30324abe36` 的 18 个路径（崩溃报告落盘、崩溃会话拼装、崩溃界面、调试日志页临时会话、内存持久化、布局/字符串/测试/文档）。
- **跳过**：C92 两提交（`08618b49ec`、`231bbea99b`）在 2026-10-21 16:16 已完成复评；C93/C94（`8f7acb519c` → `590c424568` 带入的 dev1/dev2/dev4 改动）已分别在 `docs/C93-beta-merge-review-dev1-20261021.md`、`docs/C93-beta-merge-review-dev2-20261021.md` 与 C94 提交中评审。本轮合并 0 冲突、路径交集只有 `strings.xml` 相邻新增行，满足“已评审且无合入冲突”的跳过条件。

## 3. 首轮评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| F1 | 高 | `app/src/mobile/res/layout/activity_crash.xml`、`app/src/leanback/res/layout/activity_crash.xml` | 崩溃界面新增地址/提示/两个按钮后仍是无滚动、垂直居中的 `LinearLayout`。横屏或小屏（如 240dp 高）时底部「重新启动 / 静默重启 / 错误信息」被裁掉，而返回键被刻意屏蔽，用户可能无法重启；违反“既有崩溃界面行为不回归”。 | 两份布局改为 `ScrollView(fillViewport)` 包裹居中内容；实测 800×480/320dpi 下可滚动到全部三个原按钮。 |
| F2 | 中 | `server/process/DebugLogs.java` | 崩溃会话页初始标题为「崩溃会话 · 自动开启」，但 `/debug/stream` 轮询 1.5 秒后用 `j.enabled` 把标题改写为「记录中」，与临时会话横幅矛盾。 | 轮询脚本注入 `crashSession` 标记，临时会话固定保留崩溃会话标题，只刷新字节数。 |
| F3 | 低 | `utils/CrashLogSession.java` | `logcat` 尾巴为空（权限/过滤后为空/命令无输出）时仍向页面声明 `logcat=own-uid-tail`，正文却写“系统日志不可用”。 | 覆盖率判定改为 `logcat != null && !logcat.isEmpty()`，与正文一致。 |
| F4 | 低 | `utils/CrashReport.java` | `readRecent` 读到第一份过期报告即 `break`，而文件名基于墙钟；系统时间回拨后较新的报告可能排在后面被漏读。 | 改为遍历最多 3 份报告并取第一份新鲜报告。 |
| F5 | 提示 | `utils/CrashReport.java` | 崩溃写入器必须位于 CAOC 处理器外层；该顺序依赖 `CaocInitProvider` 的 `initOrder="101"`（已验证合并清单中先于 `InitializationProvider`）。原代码无说明，后续若移动安装点会静默失效。 | 在 `install()` 文档注释中固化顺序约束与失败表现，不改行为。 |

## 4. 修复后第二轮复评

- 逐项复核 F1–F5：布局根为 `ScrollView` 且 `fillViewport="true"`，绑定 ID 与 BaseActivity 取根逻辑不变；轮询 JS 三元表达式语法与初始标题一致；覆盖率判定与 `composeSession` 正文分支一致；报告遍历最多 3 个文件、无额外 IO 放大；安装顺序说明与合并清单事实一致。
- 复核修复是否引入新回归：崩溃界面仍保留复制/打开/错误信息/重启/静默重启；`CrashLogWiringTest` 新增 3 条源契约断言覆盖上述修复；无路径越界、无未评审代码。结论：**通过**。

## 5. 验证

### 5.1 自动化（2026-10-21 23:4x–23:5x +08:00）

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.utils.CrashLogTextTest' \
  --tests 'com.fongmi.android.tv.utils.CrashLogWiringTest' \
  --tests 'com.fongmi.android.tv.ui.activity.CrashActivityDetailsTest' \
  --tests 'com.fongmi.android.tv.server.process.DebugLogsPageTest' --no-daemon
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.utils.CrashLogTextTest' \
  --tests 'com.fongmi.android.tv.utils.CrashLogWiringTest' \
  --tests 'com.fongmi.android.tv.server.process.DebugLogsPageTest' --no-daemon
bash ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon
```

结果：mobile 26 例（`CrashLogTextTest` 10、`CrashLogWiringTest` 7、`CrashActivityDetailsTest` 7、`DebugLogsPageTest` 2）0 failure/0 skipped；leanback 19 例（10 + 7 + 2）0 failure/0 skipped；`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL。

### 5.2 设备实测（emulator-5554：Android 9 / sdk 28，mobileArm64_v8aDebug 覆盖安装）

- 横屏 1920×1080 崩溃界面：日志地址、复制、在浏览器打开、重新启动、静默重启、错误信息全部可见。
- `wm size 800x480; wm density 320`（约 400×240dp）：滚动前仅图片与首段文字，向上滑动后可依次到达「复制地址 → 重新启动 → 静默重启 → 错误信息」，确认 F1 修复生效；随后 `wm size reset; wm density reset`。
- 关闭 `debug_log` 后重启并崩溃：页面标题「崩溃会话 · 自动开启 · 73 KB」、页面内 `crashSession=true`、临时会话横幅与「清空/采集开关隐藏」说明均出现；`/debug/logs.txt` 151312 字节，含 `# crash-session v1`、`coverage: report=fresh logcat=own-uid-tail debugLog=off`、`# webhtv-crash-report v1`（`javaClass=android.app.RemoteServiceException`）与同 UID logcat 尾部。
- 崩溃与重启后 `debug_log` 仍为 `false`，证明临时会话未写偏好；验证结束已把设备 `debug_log` 恢复为 `true` 并清理临时截图。

### 5.3 未覆盖

- native 崩溃/ANR 不经过 CAOC，仍不在本功能范围；真机品牌 ROM 若限制读取自身 logcat，下载内容会标记 `logcat=unavailable`。
- leanback 布局仅做源契约与编译验证，未上 TV 真机复测焦点滚动。

## 6. 回滚

单一提交回滚：布局恢复为 `LinearLayout`，撤销 `DebugLogs` 轮询标记、`CrashLogSession` 覆盖率判定与 `CrashReport` 遍历/注释改动；无偏好、数据库或二进制变更。

## 7. 交付坐标

- 评审/修复提交：`c5ef7ec829b75ad3291b6ae5b11d26378d68090a`（在合并提交 `0c2d6d9ac6` 之上）。
- Recovery tag：`recovery/C95-beta-merge-review-dev3-20261021/20261021161216-c5ef7ec829b7`。
- 推送：`origin/dev3` `8a58112b53..c5ef7ec829`（随后回填本节的文档提交再次推送）。
- PR：[#475](https://github.com/Silent1566/webhtv/pull/475)（`dev3` → `beta`，中文描述，只创建不合并）。
- 拉取：`git fetch origin --prune` + `git pull --ff-only origin dev3`，`dev3` 与 `origin/dev3` 同步；`origin/beta` 仍为 `590c424568`。
- 工作树：干净；本节回填为文档单提交。
