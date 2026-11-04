# PLUGIN-02 · 豆瓣插件打分比例 + 移出内置并独立分发

## Recovery anchor

- **目标**：(A) 豆瓣短评插件面板显示 1–5 星打分比例（横向条形 + 百分比）；(B) 豆瓣插件不再内置进 App，改由独立 GitHub 仓库 `Silent1566/webhtv-plugin-douban` 分发，用户自行下载导入。
- **分支 / BASE**：`dev4` @ `ca32efcad6abbaf4ade3a1525306bf7226af4bdb`（worktree 干净，受保护脏路径 0 条）。
- **验收标准**：见下文「验收」。独立复核方式是审 diff + `:app:testDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` + `:app:compileDebugAndroidTestJavaWithJavac` + node 冒烟。
- **回滚**：`git revert` 本次提交即可；新仓库独立于 App，删除仓库不影响 App 构建。

## 决策记录（本轮澄清访谈结论）

| 决策 | 结论 | 理由 |
| --- | --- | --- |
| 新仓库归属与命名 | `Silent1566/webhtv-plugin-douban`，public，由本会话 `gh` 代建并推送 | 与 origin `Silent1566/webhtv` 同前缀，用户从 App 仓库跳转不会认错；后续插件可按 `webhtv-plugin-*` 成系列 |
| 分发形态 | 仓库内直接放目录形态 `douban/manifest.json` + `douban/douban.js`，README 说明「下载 → 解压 → 选 `douban` 目录导入」 | App 导入器 `PluginPackageImporter.importFrom` 只接受目录，拒绝 .zip；本次不给 App 加 zip 能力（范围外） |
| 比例展示形态 | 横向条形 + 百分比文字，5 星在上一路排到 1 星 | 面板无 JS 通道，纯 div/CSS 成本极低；条形比纯文字直观 |

### 已排除的选项

- **不在本次给 App 增加 .zip 导入**：需要改 `PluginPackageImporter` + 导入 UI + 单测，范围明显扩大，且用户只多一步解压。
- **不保留 seed 机制**：`SAMPLES` 里唯一一项就是 `plugin/douban`（`example/` 从不内置），拿掉后整套 `PluginSampleInstaller` 零调用者。保留一个永远种不下东西的机制违背 YAGNI；整个删除后「不再内置」成为结构性事实而非约定，回滚只需一次 revert。

## 关键技术事实（均已实测，非推断）

### 打分分布数据源

`GET https://m.douban.com/rexxar/api/v2/movie/<id>/rating`

必需请求头：`User-Agent` = iPhone Safari UA，`Referer: https://m.douban.com/movie/subject/<id>/`。

实测响应（subject 1292052 肖申克的救赎）：

```json
{"doing_count":95,"done_count":5781523,"following":null,
 "stats":[0.00113795459631795,0.000997429657602436,0.0136350084054732,0.130321266776559,0.853908340564048],
 "type_ranks":[{"rank":0.99,"type":"剧情片"}],"wish_count":522070,"wish_friends":{...}}
```

- `stats` 是**长度 5、和为 1 的小数比例数组，下标 0→1 星 … 下标 4→5 星**（升序）。
- 归一化口径实测：`sum(stats[i] * rating.count) == rating.count` 且 `sum(stats[i] * done_count) == done_count`（两者都精确相等）。因此百分比与分母无关，但 `done_count`（看过）与 `rating.count`（评价）是**两个不同人群**，不能把 `done_count` 当成这些百分比的分母。
- 剧集无需分支：`/movie/<tvId>/rating` 会 302 到 `/tv/<tvId>/rating`，`curl -L` 实测 `REDIRECTS=1 FINAL_STATUS=200`（tvId 35588177 漫长的季节）。宿主 `catvod/src/main/java/com/github/catvod/net/OkHttp.java:239` 的 `getBuilder()` 未关 `followRedirects`，`client(timeout)` 走 `client().newBuilder()`，故跟随重定向成立。
- **详情接口不带分布**：`/movie/<id>` 的 `rating` 只有 `{count,max,star_count,value}`（`star_count` 是 `5` 之类的数字，不是分布），顶层 `stats` / `rate_info` 均为空串。新增一次 HTTP 请求不可省。
- 无评分条目可能返回 404，一律按「无分布数据」处理。

### 时间预算（脚本硬约束）

`app/src/main/assets/plugin/douban/douban.js` 现有常量：`LATEST_REQUEST_AT_MS = 2600`（相对 action 开始，还允许**发起**请求的最后时刻）、`REQUEST_TIMEOUT_MS = 2000`（单请求上限，与清单 `runtime.timeout` 对齐）。宿主 `QuickJsPluginRuntime.CALL_TIMEOUT_MS` = 5000ms 为单次调用硬上限，**超时不是本次失败而是插件被禁用**。

现有一次 action 已发 1+1+(1..3) 个请求。新增 `/rating` 请求必须保留余量给至少一页短评，不能把 2600+2000=4600 挤爆。

### 内置 seed 的耦合面

- `app/src/main/java/com/fongmi/android/tv/plugin/PluginSampleInstaller.java`：`SAMPLES = List.of("plugin/douban")`（第 71 行）、`SEED_REVISION = 4`、`KEY_SEEDED = "plugin_sample_seed_revision"`。
- 唯一调用点 `app/src/main/java/com/fongmi/android/tv/plugin/PluginBootstrap.java:76`（`installAndReloadQuietly()`）。
- 依赖它的测试：`app/src/test/java/com/fongmi/android/tv/plugin/PluginSampleInstallerTest.java`、`app/src/androidTest/java/com/fongmi/android/tv/plugin/PluginSampleInstallerDeviceTest.java`。

### 导入器约束（决定仓库形态）

- `app/src/main/java/com/fongmi/android/tv/plugin/PluginPackageImporter.java:125`：`if (!sourceDir.isDirectory()) return new Result(null, "插件路径不是目录");` —— 无 zip、无 URL 导入。
- `resolveEntry`（同文件 253 行起）拒绝 `://` 前缀、绝对路径、`..` 与 `.` 段、符号链接、逃出源目录的路径 → 仓库版清单 `runtime.entry` 必须是 `"douban.js"`。
- `copyInto` 只复制 `manifest.json` + 入口脚本同目录普通文件；空脚本被拒。

### 清单版本键

`app/src/main/java/com/fongmi/android/tv/plugin/PluginManifest.java:117-120`：`version` 是协议版本（整数，必须为 1），插件自身版本读 `pluginVersion`，缺省时回落成协议版本字符串。仓库版清单补 `"pluginVersion": "1.1.0"`，否则插件管理页版本列恒显示 `1`，用户无法区分新旧拷贝。

## 改动清单

### App 侧（移除内置）

- 删除 `app/src/main/assets/plugin/douban/manifest.json`、`app/src/main/assets/plugin/douban/douban.js`。
- 删除 `app/src/main/java/com/fongmi/android/tv/plugin/PluginSampleInstaller.java`。
- `app/src/main/java/com/fongmi/android/tv/plugin/PluginBootstrap.java`：去掉 `seedOnce()` 调用与相关 javadoc 段落，保留 `installAndReloadQuietly()` 的「失败不抛」语义与 `reloadAll()`。
- 删除 `PluginSampleInstallerTest.java`、`PluginSampleInstallerDeviceTest.java`。
- `app/src/main/assets/plugin/README.md` 第 4 节：去掉指向 `assets/plugin/douban` 的死路径，指向新仓库；`example/` 保留为仓内最小示例。
- `PluginUiPresenterDeviceTest.java:173-180`、`PluginUiPresenterDragAndSurfaceDeviceTest.java:48` 与 `:262`：**仅注释文字**，把「内置的 douban.comments 就是这样」改成不再成立的说法。
- 不动：`app/src/main/assets/plugin/example/`、`ExamplePluginManifestTest.java`、`docs/` 既有文档，以及所有把 `douban.comments` 当任意 fixture id 的单测（`PluginManifestTest`、`PluginConfigTest`、`PluginUiPresenterTest`、`PluginAuditLogTest`、`CachePluginHttpModuleTest`）。

### 插件侧（新仓库 `Silent1566/webhtv-plugin-douban`）

- `douban/douban.js`：新增 `/rating` 请求（带为短评页预留的余量门控）、`STYLE` 内条形样式、5 行条形渲染（5 星 → 1 星，一位小数百分比）、失败/形状异常时**完全退回原渲染**（无空条、无占位）。
- `douban/manifest.json`：`runtime.entry` → `"douban.js"`；新增 `"pluginVersion": "1.1.0"`；其余字段逐字节保持。
- `README.md`：用途、展示内容、目录结构、**目录导入步骤（明确 .zip 不能直接导入）**、权限与用途、依赖的豆瓣接口及其非公开性质、卸载方式、GPL-3.0 声明。

### 明确不动

`shape_douban_rating_chip_light.xml`、`dialog_ai_recommendation_info.xml`、`TmdbItem.java`、`PersonalRecommendationService.java`、`AiRecommendationService.java`、`PluginEpisodePayload.java` 里的 douban rating 逻辑属于 AI 推荐功能，与插件无关。

## 验收

1. `app/src/main/assets/plugin/douban/` 不存在，`app/src/main/assets/plugin/example/` 两文件仍在。
2. `git grep -n "PluginSampleInstaller" -- app/src` 零命中。
3. `PluginBootstrap.installAndReloadQuietly()` 仍在、仍吞异常、不再引用被删类。
4. `plugin/README.md` 不再出现 `assets/plugin/douban`，含新仓库 URL，且仍覆盖全部权限 token 与事件 token（`ExamplePluginManifestTest` 两条断言）。
5. 两个 `PluginUiPresenter*DeviceTest` 相对 HEAD 只有注释差异。
6. `:app:testDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` 通过；`:app:compileDebugAndroidTestJavaWithJavac` 通过。
7. 插件脚本：5 行条形、5 星在上、百分比一位小数、与 `stats` 一致；无分布数据时输出与改动前一致；任何失败路径都返回字符串且不抛；全文无 ES6 语法。
8. 新请求被门控，短评页仍有余量，门控常量在注释里写明算式。
9. 新仓库 README 的安装步骤与宿主「只接受目录」的规则一致。

## 独立复核结论（协调者亲自执行，非子代理自报）

### 插件侧

- `node --check` 退出码 0。
- **渲染级回归**：用 HEAD 版（改动前）与新脚本在同一组 fixture 下对比输出，剥掉 `<style>` 后**逐字节相同**（均为 1355 字符）。即豆瓣拿不到分布时面板与 1.0.0 无任何可见差异；`.dist` 只是样式定义，不产生可见元素。
  - 说明：原始 HTML 整体长度不同（2981 → 3430），差异全部落在 `<style>` 块内，属预期。
- node 冒烟 17 项断言全通过：
  - 5 行分布，顺序 `5星 85.4% | 4星 13.0% | 3星 1.4% | 2星 0.1% | 1星 0.1%`（与 `stats` 一致，一位小数）；
  - `/rating` 404 → 0 条 `dist-row`，短评卡片与 `.summary` 均完好；
  - `stats` 形状异常（200 但 `{}`）→ 0 条 `dist-row`；
  - 全接口失败 → 不抛异常，返回非空字符串。
- ES5 纯净性：`=>` / `const ` / `let ` / 反引号 / `async ` / `await ` 计数均为 0；唯一的 `class ` 出现在注释「含 class off」中，非 `class` 声明。
- 门控：`if (canRequest() && Date.now() < actionDeadline - RATING_RESERVE_MS)`，复用 `API_URL` 与 `detailHeaders`，使用 `ttl` 档位。
- `manifest.json`：JSON 合法，`runtime.entry` = `douban.js`，`pluginVersion` = `1.1.0`，其余字段未变。

### App 侧

- `git grep` 零命中：`PluginSampleInstaller`、`assets/plugin/douban`。
- `app/src/main/assets/plugin/douban/` 已不存在；`example/` 两文件长度未变（712 / 484）。
- `PluginBootstrap.installAndReloadQuietly()` 只剩 `reloadAll()`，签名与「失败不抛」语义保留。
- `plugin/README.md` 仅第 4 节变化（+3/−2），保留「每一步失败都返回一句可读的提示 HTML」原句，指向新仓库。
- 两个 `PluginUiPresenter*DeviceTest` 经 `git diff -U0` 确认为纯注释改动。
- 未触碰的 fixture 文件（`PluginManifestTest`、`PluginConfigTest`、`PluginUiPresenterTest`、`PluginAuditLogTest`、`CachePluginHttpModuleTest`、`ExamplePluginManifestTest`、`example/`）与 HEAD 逐字节相同。

### Gradle（注意：本项目有 flavor 维度，通用 task 名不存在）

`app/build.gradle:63` 为 `flavorDimensions = ["mode", "abi"]`（leanback/mobile × arm64_v8a/armeabi_v7a），因此**没有** `:app:testDebugUnitTest` / `:app:compileDebugAndroidTestJavaWithJavac`，须用带 flavor 的 task 名。

- `:app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` → BUILD SUCCESSFUL；23 个插件测试类共 **233 用例、0 失败、0 错误**。
- `ExamplePluginManifestTest` 4 条全通过（该测试固定要求 README 覆盖全部权限 token 与事件 token，故 README 改写未丢 token）。
- `:app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` → BUILD SUCCESSFUL（证明删除的设备测试类无残留引用）。
- 子代理曾报告一个瑕疵：最后一次 javadoc 措辞微调发生在其 gradle 运行之后，未重新编译验证。上述两次运行是在最终字节上完成的，已覆盖该点。

### 新仓库

- `https://github.com/Silent1566/webhtv-plugin-douban`，**PUBLIC**，默认分支 `main`。
- 远程树：`.gitattributes`(19) / `LICENSE.md`(35149) / `README.md`(3259) / `douban/douban.js`(24157) / `douban/manifest.json`(542)。
- 已加 `.gitattributes` 统一 `eol=lf`，避免插件脚本在 Windows 检出时被 CRLF 污染。
- raw 下载实测：manifest HTTP 200 且 `entry` 为相对路径 `douban.js`；脚本 HTTP 200、24157 字节。

## 执行状态

- [x] 勘察与接口实测
- [x] 澄清访谈（5 个决策点收敛）
- [x] 插件脚本（子代理 A 两次静默失败后由协调者补齐：辅助函数与门控注释沿用它已落的成果，它在断链处停止——`summary` 收了 `stats` 却未用、action 无请求、`render` 未接入、manifest 与 README 未动）
- [x] 移除内置（子代理 B 一次通过）
- [x] 独立复核（渲染级逐字节回归 + 17 项冒烟 + 2 个 Gradle task）
- [x] 建仓推送
- [x] 提交 + recovery tag

## 遗留风险（已路由，非静默接受）

1. **`docs/PLUGIN-01-global-plugin-framework-20261030.md` 仍提到 `assets/plugin/douban`**：历史记录文档，改它等于篡改当时的决策凭据，故意不动。
2. **设备测试只编译未执行**：`connectedAndroidTest` 需要真机/模拟器，超出本次窄验证范围；两个受影响文件仅注释变动，风险低。
3. **豆瓣 rexxar 接口是非公开接口**：已在插件 README 里明确告知随时可能失效；脚本本就以「安静降级」应对。

## Next action

已交付。后续若要给 App 增加 `.zip` 导入能力，需另开任务（涉及 `PluginPackageImporter` + 导入 UI + 单测）。
