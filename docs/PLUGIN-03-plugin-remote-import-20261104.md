# PLUGIN-03 · 插件远程地址导入（GitHub / 多插件勾选批量导入）

## Recovery anchor

- **目标**：让用户用远程地址装插件，并在一个包内含多个插件时提供勾选 + 全选的批量导入；TV 端通过局域网推送 + 二维码获得同样的能力。
- **状态**：方案待批准，**尚未改动任何代码**。
- **基线**：`dev4` @ `c481a6e3584`（PLUGIN-02 已提交，worktree 干净）。
- **回滚**：本任务全部为新增面（新类 + 新 UI + 一个新路由 case），不改动 `PluginPackageImporter.importFrom` 的既有语义，删掉新增文件并回退 `Action.java`/`PluginManagerActivity` 的调用点即可回到当前状态。

## 决策记录（本轮澄清访谈结论）

| 决策 | 结论 |
| --- | --- |
| 安装护栏 | HTTPS-only + 安装前披露每个插件的声明权限与来源域名 + 二次确认；**明确不做自动更新**（只可手动「检查更新」） |
| 多插件发现方式 | 扫 zip 内的 `manifest.json`，**不发明新索引格式** |
| TV 输入方式 | 手机优先 + **追加二维码**：TV 展示二维码，手机扫码后把地址推给 TV |

### 明确排除

- **不做自动更新**：会把「一次性粘贴」变成持久远程控制通道，风险等级跃升，且需求未提出。
- **不引入 index.json 索引格式**：清单本就是插件包必需文件，扫它不需要作者额外维护约定；现有仓库（含 `webhtv-plugin-douban`）都还没索引。
- **不做插件签名/仓库白名单**：能真正防住被骗，但需密钥管理、签名验证、分发，工程量远大于本需求；记录为后续独立立项。

## 事实基础（均已实测核对，带坐标）

1. **解压零新依赖**：`commons-compress` 是 app 直接依赖（`app/build.gradle:189`，`gradle/libs.versions.toml:93`），`lab/LabEnv.java:916` 已用 `SevenZFile` 跑通；`java.util.zip` 亦可。
2. **远程列表 + 多选批量导入已有先例**：`following/AlistSubscriptionImporter.java` 的 `fetch(baseUrl, token)` → `parse(json)` → `List<Candidate>` → `importCandidates(List)`；`ui/activity/FollowingActivity.java:307/332/352` 是「拉取 → `showServerPreview` → 批量导入」的完整闭环。
3. **安全逻辑可原样复用**：`plugin/PluginPackageImporter.importFrom(File)` 已含清单校验、`MAX_PACKAGE_BYTES`(4MB)/`MAX_FILES`(64) 上限、符号链接与 `..` 穿越拒绝、失败回滚 `rollback()`、配置登记。远程包解压成临时目录后逐个调用它，**既有防线自动生效，不需要重写任何安全判断**。
4. **codeload 实测可用**：`https://codeload.github.com/<owner>/<repo>/zip/refs/heads/main` → HTTP 200、25,668 字节、**0 重定向**；包内顶层是 `<repo>-main/` 前缀，`manifest.json` 在其下（如 `webhtv-plugin-douban-main/douban/manifest.json`）→ **必须剥离公共顶层前缀**。
5. **必须绕开 GitHub API**：`api.github.com` 未认证 60 次/小时/IP，TV 盒子常在共享 NAT 下会随机失败且难解释。codeload 不计 API 配额。分支名无法探测 → 依次试 `main`、`master`，或从 `/tree/<branch>` URL 直接取。
6. **QR 与 LAN 推送都已存在**：`utils/QRCode.java` 是现成工具，`zxing.core` 为 `implementation` 级（`app/build.gradle:204`），`zxing.android.embedded` 在 leanback 与 mobile 两个 flavor 都有（`:207`/`:209`）；`ui/dialog/RemoteTrustDialog.java` 已用它展示二维码并配合 `remote/RemoteTokens` 回传。
7. **动态路由已存在**：`server/process/Action.java:61` 匹配 `/action`，`:67` 读 `do`，`:88` 已有 `case "apk_url" -> onApkUrl(params)`，`:122` 取 `params.get("url")`。插件推送只需同形的 `case "plugin_url"`。
8. **导入器当前只收目录**：`PluginPackageImporter.importFrom:125` 对非目录直接返回「插件路径不是目录」；无 zip、无 URL 分支。这正是本任务要补的入口，而不是要改的语义。

## 方案对比

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| **A. 不改** | 维持目录导入 | 放弃：TV 上选目录体验差，用户明确要求 |
| **B. 裸 URL 直装单插件** | URL 必须是单个插件的 zip | 不足：不满足「多插件勾选 + 全选」 |
| **C. 引入 index.json 索引格式** | 仓库需提供索引，App 按索引展示 | 拒绝：要求作者维护约定，现有仓库全不符合，属发明新格式（违背 YAGNI） |
| **D. 窄适配（推荐）** | URL → 下载 zip → 扫 `manifest.json` 发现候选 → 勾选/全选 → 解压所选到临时目录 → **逐个调用既有 `importFrom(File)`** | 采纳 |

选 D 的理由：发现方式不依赖任何新约定；安全校验零重写（第 3 条事实）；与 `AlistSubscriptionImporter` 的既有范式同构；GitHub 走 codeload 可绕开 API 限流。

## 分阶段计划

### Stage 1 · 手机端闭环（独立可用）

新增：

- `plugin/PluginBundleIndex.java`（纯 JVM 可测）：入参 zip `File`，输出候选列表 `{entryPath, id, name, version, runtimeApi, permissions}`。
  - 遍历 zip 条目找 `manifest.json`；读清单文本 → 既有 `PluginManifest.parse`；
  - 剥离公共顶层目录前缀（codeload 的 `<repo>-main/`）；
  - 复用 `PluginManifest.MAX_MANIFEST_BYTES` 与 `PluginPackageImporter.MAX_FILES`/`MAX_PACKAGE_BYTES` 做上限；
  - 拒绝含 `..`/绝对路径/符号链接语义的条目名。
- `plugin/PluginRemoteSource.java`：
  - URL 归一化：GitHub 仓库页 / `/tree/<branch>/<subdir>` / 直接 `.zip` → 可下载 URL；
  - **HTTPS-only**（拒绝 `http`、`file`、`content`）；
  - 用 catvod 共享 OkHttp（已带 DNS/代理/认证头），带字节上限与超时，落临时文件。
- `PluginPackageImporter.importBundle(File zip, Set<String> selectedEntryPaths)`：逐个候选解压到临时目录后调用既有 `importFrom`，返回逐项结果（成功/失败+原因），**部分失败不影响已成功的项**。

改动：

- `ui/activity/PluginManagerActivity.java`：新增「从地址导入」入口 → URL 输入对话框（复用既有 `TextInputLayout` 参数对话框样式）→ 候选列表（CheckBox + 全选 + 每项显示 名字/版本/权限/来源域名）→ 二次确认 → 后台批量导入 → 逐项结果汇总。
- `res/layout/` 新增候选列表项布局；`res/values/strings.xml` 与 `values-zh` 补文案。

### Stage 2 · TV 输入（二维码 + 局域网推送）

- `server/process/Action.java`：新增 `case "plugin_url" -> onPluginUrl(params)`，取 `params.get("url")`，复用 Stage 1 的归一化 + 下载 + 索引 + 导入链路；沿用 `ApkUrlPolicy` 的校验风格。
- URL 输入对话框（TV 布局）展示二维码：内容为手机可访问的推送页地址（复用 `utils/QRCode.java` 与 `Server` 的本地地址）。
- 保留原有目录导入作为离线备选。

Stage 2 依赖 Stage 1 的链路，但 Stage 1 单独交付即可用。

## 验收标准

**Stage 1**
1. 输入 `https://github.com/Silent1566/webhtv-plugin-douban` 能识别出 1 个候选 `douban.comments`，显示名称/版本/权限，取消勾选则不导入。
2. 一个含 3 个插件子目录的 zip 能一次列出 3 项，全选后 3 项全部导入并出现在管理页；只勾 2 项时第 3 项不落地。
3. `http://`、`file://`、`content://` 一律拒绝并给出可读原因。
4. 超过 4MB / 64 文件的包被拒，且**盘上无残留**（复用既有回滚）。
5. 部分失败语义：一项失败不阻止其余项，结果页逐项标明成功/失败原因。
6. 确认页必须展示每个选中项声明的权限（至少把 `net.request`、`ui.panel` 这类高危项标出）与来源域名。
7. 无自动更新：导入完成后不注册任何周期性检查。
8. 纯 JVM 单测覆盖 `PluginBundleIndex`（前缀剥离、多插件、畸形清单、超限、路径穿越）与 `PluginRemoteSource`（URL 归一化、非 HTTPS 拒绝）。

**Stage 2**
9. TV 端展示二维码，手机扫码后填入地址并提交，TV 端收到后进入同一条候选勾选流程。
10. `plugin_url` 路由对非法 URL 的拒绝行为与 `apk_url` 一致。

## 风险与边界

- **插件无沙箱（本方案不解决）**：`net.request` 无域名白名单、`ui.panel` 把脚本 HTML 灌进 WebView、启动即加载。护栏只是「提醒」，无签名机制时用户仍可能被骗过确认。**这是本方案最大的残余风险，需在导入确认页文案中如实告知**，而非暗示已安全。
- **GitHub 分支名探测**：`main`/`master` 顺序试探会有一次多余往返；用户给 `/tree/<branch>` 形式可避免。
- **公共 NAT 下的下载失败**：codeload 无限流，但企业网络/代理可能拦截，需给出可读错误而非静默失败。
- **不做自动更新**：已明确排除，若将来要做，必须同时补签名机制。

## 研究记录（§7 设计研究门）

| 证据类别 | 结论 | 是否影响决策 |
| --- | --- | --- |
| 本项目源码与既有实现 | 已逐点核对（上文 8 条事实，带 file:line） | 是，决定复用 `importFrom` 与 codeload |
| 平台/项目官方文档 | `codeload.github.com` 与 GitHub 未认证限流（60/h）行为已实测确认 | 是，决定绕开 `api.github.com` |
| 上游 PR / issue / revert | 不适用：本需求不涉及上游 FFmpeg/Media3/mpv 依赖，无对应上游仓 | 不适用，理由记此 |
| 成熟项目同类实现 | 本项目内已有两处同构范式（`AlistSubscriptionImporter`、`ApkUrlPush`），无需外部参照 | 是，直接采用既有范式 |
| 论文/技术博客/基准 | 不适用：无算法或性能取舍，纯工程集成 | 不适用，理由记此 |

研究边界说明：本需求是工程集成而非算法/协议选型，能改变设计的未知点只有「GitHub 怎么下才不被限流」「包内结构长什么样」「QR/LAN 通道是否已有」三项，均已实测收敛，继续检索不再可能改变设计。

## 批准记录

- 2026-11-04：用户批准 **Stage 1（手机端闭环）** 先行交付，Stage 2 待 Stage 1 自验后再确认。
- 支持的 URL 形态限定为：GitHub 仓库页 URL、`/tree/<branch>`、直链 `.zip`。不认识的形态直接报可读错误，不猜。

## 详细设计阶段的修正（对前述事实 4 的更正）

前文说「必须剥离公共顶层前缀」。细化设计时发现**这是装饰性的，不是正确性要求**：`importFrom` 复制到的目标目录名来自清单里的 `id`（如 `douban.comments`），临时解压目录叫什么完全不影响结果。因此实现上**不剥离前缀**，按 zip 内完整路径定位与解压；仅在界面展示时把公共前缀去掉以便阅读。这样也避免了一个真实边界错误——单插件包 `plugin/manifest.json` 的公共顶层段恰好就是插件目录本身，剥离会把清单顶到根上。

## Stage 1 实施记录（已完成并通过独立复核）

### 交付物

| 文件 | 性质 | 说明 |
| --- | --- | --- |
| `plugin/PluginBundleIndex.java` | 新增 | 扫 zip 内 `manifest.json` 发现候选；复用 `PluginManifest.parse`；按 id 去重；上限 64；拒绝 `..`/绝对路径/盘符/反斜杠/`.git`/`__MACOSX__` |
| `plugin/PluginRemoteSource.java` | 新增 | URL 归一化（codeload，仓库页试 main→master）、HTTPS-only、16MB 流式上限、zip 魔数校验、失败删 partial |
| `plugin/PluginPackageImporter.java` | **+220 纯新增** | 新增 `importBundle` + `ItemResult`/`BundleResult`；`importFrom` 零改动 |
| `PluginManagerActivity.java` | **+503 纯新增** | URL 输入、候选勾选/全选、权限披露二次确认、逐项结果 |
| `dialog_plugin_candidates.xml` / `item_plugin_candidate.xml` | 新增 | 候选列表与行布局 |
| `values/strings.xml` / `values-zh-rCN/strings.xml` | 各 +36 | 键集经脚本比对完全一致 |
| `PluginBundleIndexTest.java` | 新增 | 17 用例 |
| `PluginRemoteSourceTest.java` | 新增 | 19 用例 |

### 独立复核结论（协调者亲自执行，非子代理自报）

- **编译**：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` BUILD SUCCESSFUL。
- **测试**：`:app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` → 25 套件 **269 用例 0 失败 0 错误**（新增 17+19，既有 14 通过）。
- **`importFrom` 未被改动**：`git diff --stat` = 220 insertions；删除行 grep 为空。
- **zip-slip 双重校验**：`safeRelativePath` 在索引与解压两个阶段各走一遍；写入前 `isInside` 做 canonical 前缀比较。
- **zip 炸弹防护**：上限按 `OutputStream` 实际写入字节流式累计（`writeEntry`），不信任声明的 size。
- **勾选真实生效**：`adapter.checkedCandidates()` → `selected` → `importBundle(zip, selected)`；未勾选项不进入该列表；零勾选时导入按钮 `setEnabled(false)`。
- **URL 零持久化**：全类无写入 URL 的 `Prefers`/`putString`；无 `Timer`/`WorkManager`/`AlarmManager`，无自动更新。
- **临时包清理**：`getCacheDir()/plugin-bundle.zip`，所有出口均删除（下载失败、索引失败、页面销毁、候选取消、确认取消、导入成功/失败）。
- **免责声明诚实**：中英文均明写「安装前没有对它们做过任何安全校验」「插件没有沙箱隔离」，未软化为安全承诺。
- **键集一致性**：脚本比对 `values/` 与 `values-zh-rCN/` 的 `plugin_manager_*` 键，各 55 个，双向差集为空（防非中文环境 `Resources$NotFoundException`）。

### 一处被推翻的子代理结论（记录以免重犯）

实现核心逻辑的子代理报告称发现既有缺陷：`PluginPackageImporter.isSymlink` 使用 `java.nio.file.Files.isSymbolicLink`（API 26+），而 `minSdk = 24`，会让 API 24/25 上拒绝一切插件包，并建议修改。

**核实后该结论不成立**：本项目用 `desugar_jdk_libs_nio`（`gradle/libs.versions.toml:72`）而非默认 `desugar_jdk_libs`，nio 变体包含 `java.nio.file` 脱糖，`Files.isSymbolicLink(Path)` 在官方支持表内。故未改动该安全边界代码——照建议修改反而会削弱一道真实防线。

### 未做（Stage 2，待批准）

TV 端二维码 + `plugin_url` 局域网推送路由。Stage 1 已单独可用。
