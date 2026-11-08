# C46：dev3 合并远端 beta 最新代码并复评 T3→T4 本机网关

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（**远端已移除/回退的提交不得顺带带回**）；复评 dev3 全部已修改代码（含已提交未推送的 `c388619629` T3→T4 本机网关）；发现问题即修复并验证通过；循环评审直至通过；然后提交、推送 `dev3`、创建 `dev3 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `d8daedf86c`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev3 既有改动**零丢失**；④ 定向 JVM 测试通过；⑤ 双 flavor Java/AndroidTest 编译通过；⑥ UI token 门禁相对基线零新增违规；⑦ 净差异只含本分支自身改动；⑧ 实机（`192.168.50.3:5559`）端到端验证网关；⑨ 提交 + recovery tag；⑩ `dev3` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；4 轮评审全部通过（无必修问题，**本任务未修改任何生产代码**）；定向测试、双 flavor 编译、UI token 门禁、实机端到端验证均完成；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev3` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `c388619629291e7dc32d0cefca2202b8df9064da`（领先 `origin/dev3` 1 个提交，未推送） |
| `origin/beta` tip | `d8daedf86c`（Merge PR #418 from dev1） |
| 合并基点（merge-base） | `f85398d359`（= 前一任务 C44 的合并提交，即 `origin/dev3` tip） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard `finish` 创建合并提交） |
| 合并结果 | **0 冲突、0 冲突标记**，1 路径合入 |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `c388619629291e7dc32d0cefca2202b8df9064da` |
| 任务守卫 | `C46-beta-merge-review-dev3-20261008`（standard，scope `app/src` + `docs` + `gradle`） |

### beta 增量 ledger

`git log --oneline HEAD..origin/beta`（合并前）= 3 个提交，其中非合并提交仅 1 个：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `d8daedf86c` | Merge PR #418 from dev1 | 纳入（合并线） |
| `6e667a236f` | `fix(ci): 修复守护进程 JVM 供给失败导致流水线在测试前中断` | **纳入**（唯一实质增量） |
| `6e3037c569` | Merge PR #417 from dev3 | 纳入（合并线） |

`git diff --stat f85398d359 origin/beta` = 仅 `gradle/gradle-daemon-jvm.properties`（10 增 11 删）。该修复把守护进程 JVM 从已下架的 JetBrains JBR 21 硬钉改为「Java 21、任意厂商」，并重新生成 10 个有效下载地址。dev3 侧此前从未改过该文件（`git log f85398d359..c388619629 -- gradle/gradle-daemon-jvm.properties` 为空），因此合入无冲突。

### 三方无损校验

```text
beta 增量零丢失：f85398d359..origin/beta 的 1 个路径 → 合并树与 origin/beta 逐文件一致（SAME）
dev3 既有零丢失：c388619629 相对 f85398d359 的 5 个路径 → 合并树与 c388619629 全部 SAME
合并结果净差异：git diff --cached --name-status origin/beta = 5 个路径，全部为 dev3 自身改动
（最终推送后的净差异为 6 个：上述 5 个 + 本任务文档 docs/C46-…md）
```

净差异（`git diff --cached --stat origin/beta`，1800 行全为新增）：

| 路径 | 性质 |
| --- | --- |
| `app/src/main/assets/VodPlus/EnvFiles/T4Proxy.js` | 新增（450 行） |
| `app/src/main/java/com/fongmi/android/tv/server/Nano.java` | 修改（+7） |
| `app/src/main/java/com/fongmi/android/tv/server/process/VodApi.java` | 新增（620 行） |
| `app/src/test/java/com/fongmi/android/tv/server/process/VodApiTest.java` | 新增（591 行） |
| `docs/C45-t4-api-gateway.md` | 新增（132 行） |

## 用户核心关注点：远端已移除（回退）内容零复活

程序化逐路径比对（不依赖人工目测），并用 `git rev-parse` 固化 ref 以避免 MSYS 把 `origin/beta:path` 误做路径转换。revert 提交按**主题行（`%s`）以关键词开头**严格识别：

```text
全 refs revert 提交（主题以 Revert/revert/回退/撤销/剔除 开头） : 109
其触及唯一路径总数                                          : 725
这些路径中「不在 origin/beta 树」的                         : 601
其中「在合并索引树中存在」= 复活数                           : 0
合并前 dev3 基线 c388619629 同样扫描的复活数                 : 0
净差异 6 个路径与 revert 路径集的交集                        : 空
HEAD 独有的 Revert 提交 / beta 独有的 Revert 提交             : 0 / 0
```

**检测器反证（证明「0 复活」不是空转）**：把一条确实「在 revert 集且不在 beta 树」的路径（`ISSUE_TEMPLATE`）注入伪造的 HEAD 树后，同一解析步骤正确报出该路径；真实 HEAD 树同一步骤输出为空。另用 `git diff --cached --name-only origin/beta` 与 revert 集求交，结果为空，即本次净差异未触及任何被回退路径。

### 两次误报的排除过程（记录以便复现）

| 误报 | 原因 | 排除方式 |
| --- | --- | --- |
| `.codex/scripts/task_guard.sh`、`.github/workflows/android-release.yml`、`.gitignore` 三条「复活」 | MSYS 把 `git cat-file -e origin/beta:path` 中的 `/` 当路径分隔符，导致该形式对部分路径报「不存在」 | 改用 `git rev-parse origin/beta` 固化 ref 后用 `git ls-tree` 取树比对，复扫为 0 |
| `docs/C46-beta-merge-review-dev3-20261008.md` 一条「复活」 | **本任务自己的提交**被误判为 revert 提交：未锚定的 `git log --grep='回退\|revert'` 会匹配提交**正文**，而 5d4a7e8e18 的正文恰好写了「258 个 revert-like 提交、1144 条被触及路径」，`^revert` 因此命中该行 | 改为只匹配主题行（`%s`）并锚定开头；同时交叉复核「显式排除本任务 3 个提交」后复活数为 0，且该文件在 `origin/beta` 从未存在过 |

关键已剔除标志物（沿用 C44 记录）：`SpiderCrashBreadcrumb.java`、`SpiderCrashBreadcrumbTest.java`、`SPIDER-CRASH-DIAG-spider-crash-diagnostics.md` 在合并树中仍为 absent；`ThemeCatalog` = 0、`assets/themes` = 0。

## 评审循环记录

### 第 1 轮：净差异逐行静态审读（无必修问题）

逐行审读 5 个路径（`Nano.java` 的网关注册与资源回退、`VodApi.java` 全部 620 行、`VodApiTest.java` 31 个用例、`T4Proxy.js` 450 行、任务文档）。重点核对：

- **路由**：`route()` 以参数优先于 `ac`，`ac=detail` 同时用于分类与详情（T4 客户端实际形态）；`isRequest` 用 `PATH.equals || startsWith(PATH + "/")`，不会吞掉 `/vod/apix`。
- **站源类型分派**：type 3 直接调 `site.spider()`（不切换全局 recent loader）；type 4/1/0 复用 `SiteApi.call` 的 HTTP 层；仅对真正覆写 `Spider.proxy(Map)` 的爬虫补 `siteKey`，老式 JAR 保持 `JarLoader` 集中兼容分派。
- **字段白名单**：`vodJson`/`macCms`/`playJson` 只输出公开苹果CMS 字段，`formatContent` 显式 `remove("site")`、`remove("tmdb")`。
- **数值边界**：`total` 用 long 防溢出；缺失 `pagecount` 时非空页用 9999、空页结束分页。
- **`playFrom`/`playUrl` 回填**：T3 爬虫常只回 `flags`，按 `$$$`/`#`/`$` 规则回填线路与集。
- **`extendFrom` 优先级**：base64 `ext` → JSON `extend` → 显式字段，后者覆盖前者。

### 第 2 轮：实证与反证（无必修问题）

不依赖阅读，直接跑真实组件取证：

1. **NanoHTTPD URI 解码契约**（`siteKeyFromPath` 的注释与断言都依赖它）：用 nanohttpd 2.3.1 起真实 server，实测 `/vod/api/%E4%B8%AD%E6%96%87%2F%2B%3F%26key` → `getUri()` = `/vod/api/中文/+?&key`（已解码），`/vod/api/a+b` → `/vod/api/a b`；查询形式 `?key=a+b` → `getParms()` = `a b`。注释与断言成立。
2. **`ByteString.decodeBase64` 容错**：实测可解码带 `=` 填充、无填充、含换行/尾空格的 URL-safe base64；对 `!!!notbase64!!!` 与拼接了 `&x=1` 的脏串返回 `null`（不会静默产生错误 JSON）。
3. **`Proxy.getPort()` 语义**：`Proxy.set(i)` 在 `Server.start()` 中与 `new Nano(i)` 同端口设置，`remoteUrl`/`isSelfGateway` 用它判断本机端口成立。
4. **`getSpider` 会缓存**：`JsLoader`/`PyLoader`/`JarLoader` 均 `computeIfAbsent` 并 `setRecent`，故直接调 `site.spider()` 不切换 recent 的判断成立。

### 第 3 轮：净差异复核（无必修问题）

- 净差异仅 5 个功能路径（+ 本任务文档），无 `.tmp/.log/.png` 等临时物；唯一 `console.log` 命中来自 `T4Proxy.js` 的启动横幅（可执行脚本的正常输出，非调试残留）。
- 全仓冲突标记扫描 0 处；`git diff --check` 与 `git diff --cached --check` 退出码 0。
- UI token 门禁唯一违规 `app/src/mobile/res/layout/item_following.xml` 相对 `origin/beta` 与合并前 `c388619629` **均逐字节一致**，且不在净差异内 → 既有项，相对基线**零新增**。

### 第 4 轮：实机端到端（无必修问题）

`192.168.50.3:5559`（API 28、1920x1080、arm64、leanback）覆盖安装 193 MB APK 后（`adb install -r`，未卸载），经 `adb forward` 访问真实应用服务：

| 项 | 结果 |
| --- | --- |
| `/vod/api?ac=config` | 200，**170 个站源**，每个 `type="4"`、`api` 指回本网关 |
| Nano 资源回退 `/file/WebHTV/EnvFiles/T4Proxy.js` | 200，19357 字节，sha256 `fff1a438…2443` **与内置 asset 逐字节一致**（分支归属见下方专项验证） |
| `OPTIONS` / `PUT` / `HEAD` | 204 / 405 / 200，CORS `Access-Control-Allow-Origin: *` |
| 错误边界 | 未知站源、缺 key、POST 非 JSON 均返回 `{"code":-1,...}`，无内部堆栈 |
| 真实站源 `csp_PianDan` 首页 | `page/pagecount/limit/total/list/class/filters` 齐全，30 条 + 35 分类 + filters |
| 真实站源 `csp_BiliBili` 搜索「海」 | 161 条，`total=1020` |
| 同站详情 | 1 条，`vod_play_from=正片`、`vod_play_url` 956 字节，`site/tmdb/vodFlags` **未泄漏** |
| 播放（GET 与 `ac=play&play=` 别名） | 均返回真实 `bilivideo.com` 直链 + `header`（含 Cookie）+ `flag/parse/jx` |
| 分类 + URL-safe base64 `ext` 筛选（`area=日本`） | `pagecount=107`、`total=3100`、31 条 |
| 路径形式 `/vod/api/<key>` | 正常返回 161 条 |

**关于 `csp_PianDan` 详情返回空列表**：同一 id 经**既有未改动的** `/spider` 接口（`SpiderApi`）也返回 `data:null`，故为站源自身行为，非本网关缺陷；换 `csp_BiliBili` 后详情/播放全部正常。

### Nano 资源回退分支的专项区分验证

`Nano.java` 的唯一改动是新增「精确 URL 缺文件时回退内置 asset」分支。上述「字节一致」结果本身**不能区分**命中新增回退分支还是命中原有的 `Local` 外置文件分支——设备上 `/storage/emulated/0/WebHTV/EnvFiles/T4Proxy.js` 确实已被 `LabActivity` 同步存在（实测 sha256 与 asset 相同）。因此对同一 URL 做了移走外置文件的可证伪验证（`adb forward tcp:19978 tcp:9978`）：

| 步骤 | 外置文件状态 | HTTP | 结果 |
| --- | --- | --- | --- |
| A 基线 | 存在（sha256 `fff1a438…2443`） | 200 / 19357 B | 由 `Local` 外置分支服务，sha256 同 |
| B **移走外置文件** | 不存在 | 200 / 19357 B | **由新增回退分支服务内置 asset，sha256 `fff1a438…2443` 字节一致 → 回退分支确认生效** |
| C 写入哨兵内容 | 内容为 `USER_SENTINEL_C46` | 200 / 17 B | 返回哨兵原文，**用户脚本未被覆盖**（「不覆盖用户脚本」语义成立） |
| D 恢复外置文件 | 已还原 | 200 / 19357 B | sha256 回到 `fff1a438…2443`，设备状态复原 |

该 URL 在改动前会返回 404（`Local.getFile` 抛 `FileNotFoundException`），故 B 步骤的 200 + 内置 asset 字节一致即为新增分支的直接证据。

## 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 定向 JVM 测试 | `:app:testMobileArm64_v8aDebugUnitTest --tests …VodApiTest` | **31 tests / 0 skipped / 0 failures / 0 errors**，`BUILD SUCCESSFUL in 3m 34s` |
| 双 flavor 编译 | `compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` + 两个 `AndroidTestJavaWithJavac` | `BUILD SUCCESSFUL in 8m 31s` |
| 守护进程 JVM 契约 | `./gradlew --version` | `Compatible with Java 21, any vendor`（证明 beta 增量生效、旧 `toolchainVendor=JETBRAINS` 已移除） |
| JS 语法 | `node --check …/T4Proxy.js` | 通过 |
| JS 真实 HTTP 契约 | 本地 stub 站源 + `T4Proxy.js` 实例，17 项断言 | 全部符合文档语义（见下注） |
| UI token 门禁 | `scripts/check_ui_tokens.sh --strict` | `violations=1 legacy=0 hex_colors=0`，唯一违规为既有 `item_following.xml`，**相对基线零新增** |
| 空白门禁 | `git diff --check` / `--cached --check` | 退出码 0 |
| 冲突标记 | 全仓扫描 | 0 处 |
| 实机端到端 | `adb forward` + 真实站源 HTTP | 见第 4 轮表 |
| 构建资源清理 | `gradlew.bat --no-daemon clean` | 见「收尾」 |

> **JS 契约测试注**：首轮 3 项 FAIL 均为我的预期写错，非代码缺陷——Node 入口按设计**只改 hostname、保留 App 的媒体代理端口**（脚本注释与 `docs/C45` 均写明「Node 和 App 是两个端口，客户端仍须能访问 App 的媒体代理端口」）；且桩服务返回的 `127.0.0.1:<App端口>` 正属 loopback，被改写为客户端可达主机名是预期行为。修正预期并加显式 `Host` 头用例后全部通过。

### 既有失败集合归属

本任务**未修改任何生产代码**（净差异中 5 个功能路径全部来自既有 `c388619629`，第 6 个为本任务文档），因此不存在新增回归的可能。UI token 门禁唯一违规项已证明与 `origin/beta` 及合并前 HEAD 逐字节一致。

## 相邻但未修复（非本次引入，仅报告）

1. **多行源码文本断言的 CRLF 脆弱性**：沿用 C44 记录，本机 `core.autocrlf=true` 会导致若干既有断言失败；beta 侧已在逐项按行尾归一化修复。本任务按范围规则仅记录、不扩界。
2. **`mobile/item_following.xml`** 的 UI token 违规为既有项。
3. **`csp_PianDan` 详情返回空**：站源侧行为，`/spider` 既有接口同样返回 `data:null`。

## 回滚

revert 合并提交即回到 `c388619629291e7dc32d0cefca2202b8df9064da`；本任务无生产代码改动，无需其他回滚动作。
