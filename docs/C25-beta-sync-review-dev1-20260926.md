# C25 合并远端 beta 并复评未推送改动（dev1，2026-09-26/27）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入本地 `dev1`，循环复评全部已修改代码（含已提交未推送部分），发现问题最小修复并验证；通过后提交、推送并创建中文 PR 到 `beta`，只创建 PR 不合并；不得把远端已移除的回退提交重新带上来。
- **验收**：合并结果包含远端 beta 最新提交；被剔除提交不在合并结果祖先中；组合后定向测试与编译通过；PR 中文描述排版清楚。
- **允许路径**：`app`、`docs/C25-beta-sync-review-dev1-20260926.md`。
- **分支/HEAD**：`dev1`；合并前 `83e0a3159b90e07a019c514e14224a905d06e230`；合并后 `c1645a374596c470fc7db3f543deaf9fab4ab701`。
- **保护面**：任务开始前仓库根目录的 18 个未跟踪验证产物（`theme_verify_*.png/xml`、`runtime_verify_*.png`、`window_dump.xml`、`window_state.txt`、`surfaceflinger_state.txt`、`transparency_verification.png`）均未纳入本任务。
- **当前状态**：合并、2 轮复评、1 处最小修复、定向验证、提交、推送、创建 PR 均已完成。
- **下一动作**：等待 PR #378 评审结果；如需改进站点弹窗聚焦行文字对比度，另起独立任务拆分 `SiteAdapter` 的 `selected` 语义（本轮已记录为不改动项）。

## 基线与提交台账

- 合并前本地 `dev1@83e0a3159b90e07a019c514e14224a905d06e230`；远端 `origin/beta@ec6730cc8881f84388980d31c18198bf91467726`；`origin/dev1@5058aaab2ef18db41dbdb18955a87b9fa79b520f`；共同祖先 `f35b8dafdd196cc96726c6eae52629553e528366`。
- 合并提交：`c1645a374596c470fc7db3f543deaf9fab4ab701`，父提交为上述本地 `dev1` 与远端 `beta`，`ort` 策略自动合并，无冲突。
- 本任务合入的远端 beta 提交（相对共同祖先）：
  - `67849819db9f4c93f8a51bcc4b6e3217c1bba041` Fix Hongguo redirect proxy responses
  - `1e278a3a7f24cfa7d4b89abef9d56d2d63577c32` Fix short proxy responses and redirect tests
  - `ec6730cc8881f84388980d31c18198bf91467726` Merge pull request #377 from Silent1566/dev4
- 被剔除/回退提交核对：`5682f2b054b577b0db5c6f7c1143f2eebb51858e`、`2faa1f37757f` 均不是本地 `HEAD` 与 `origin/beta` 的祖先，合并结果也不包含其内容。
- 复评范围内本地未推送提交（相对 `origin/dev1`）：`f35b8dafdd196cc96726c6eae52629553e528366`、`f7674024e962bcd4fe66dae16686c952153cb549`、`1bb72bd7099c85736627713fd32c1d0fddf55cbc`、`1e41863c181c9e3d3575f0840d0e1e1cd706e3ba`、`83e0a3159b90e07a019c514e14224a905d06e230`。

## 评审记录

### 第 1 轮（远端改动 + 本地未推送改动组合复评）

- 远端改动范围只有 `Proxy.java`（+23/−7）、新增 `ProxyRedirectResponseTest`（+110）和 `docs/C24-beta-sync-review-dev1-20260926.md`（远端侧 7 行状态文字），本地未推送改动范围只有 leanback 主题/站点/配置资源与 `ConfigAdapter`，两侧文件集合不相交，组合后无交叉影响。
- 复评合并后的 `Proxy.createResponse`：3xx 允许空响应体，但必须同时满足 `code ∈ [300,400)` 且存在大小写不敏感的 `Location` 头；非 3xx 仍要求 `InputStream` 体；`rs.length < 3`、非 `Integer` 状态码、空响应体缺 `Location` 仍返回 `Invalid proxy response`。`wrapStream` 已加空值保护，空体走 `ByteArrayInputStream(new byte[0])`，Range 策略仍由 `ProxyRangeResponsePolicy.resolveStart` 判定（3xx 不产生 range 头）。
- 结论：远端修复只把“原先必判错”的 302+Location 场景改为放行转发，属于窄化增强，不改变其他状态码路径；未发现越界、空指针或对本地代码的适配需求（本地未改过 `Proxy.java`）。
- **发现问题 1**：`1e41863c1` 新增的 `app/src/leanback/res/drawable/selector_config_name_item.xml`、`shape_config_name_item_normal.xml` 已被随后同一批未推送的 `83e0a3159`（改用 `MaterialButton` + `app:strokeColor` 方案）取代，全仓库已无任何引用，属该批次自引入的死资源，若不处理会随本任务进入 beta。
- **发现问题 2（仅记录，不修改）**：`f7674024e` 给 `site_item_text.xml` 增加的 `?attr/colorOnPrimary` + `state_focused` 条目在 leanback 站点弹窗里实际不生效——`SiteAdapter` 只把整行焦点转发为 `text.setSelected(...)`，`TextView` 自身从不获得 `focused` 状态；自定义主题下该选择器又被 `ThemeController.leanbackTextColors()` 运行时覆盖，因此该条目只在“内置主题”路径可命中。上一会话曾留下一个未提交改动（把 `state_selected` 直接映射到 `colorOnPrimary`），复评后判定不能采纳，理由见下节。

### 第 1 轮修复

- 删除 `app/src/leanback/res/drawable/selector_config_name_item.xml` 与 `app/src/leanback/res/drawable/shape_config_name_item_normal.xml`；删除前用全仓库字符串检索确认两文件已无被引用，删除后检索结果为空。
- 未改动 `site_item_text.xml`/`site_item_check.xml`：问题 2 的正确修法需要拆分 `SiteAdapter` 中文字视图的 `selected` 语义（当前同一状态同时表示“整行聚焦”和“当前站点”），属跨状态设计改动，需独立任务、亮/暗与自定义/内置主题的对比验证，不适合放进本次同步复评。

### 第 2 轮（修复后复评）

- 复查删除结果：`app/src` 内 `config_name_item` 相关引用为 0；`adapter_config.xml` 的接口名称按钮仍走 `?attr/materialButtonOutlinedStyle` + `dialog_outlined_button_bg/stroke/text`，与 `InterfaceEntryInteractionTest` 断言的最终方案一致，删除未触及任何生效路径。
- 复查未采纳的未提交改动：`SiteAdapter` 对文字视图的 `setSelected(hasFocus || isSelected())` 同时覆盖“聚焦行”和“当前站点行”，而“当前站点行”的背景是 `shape_site_item_selected`（`?attr/colorSurfaceVariant` + primary 描边）。把 `state_selected` 映射为 `colorOnPrimary` 会让该行变成深色文字叠深灰底（内置暗色约 1.6:1）或白字叠浅灰底（内置亮色约 1.2:1），对比度不达标；而现状 `colorOnSurface` 叠 `colorPrimary` 聚焦底约 3.47:1，18sp 大字已满足 AA。因此该改动会引入可见回退，最终未采纳并已恢复到提交状态。
- 复查合并提交：`Proxy.java` 的 `ByteArrayInputStream` 导入、`canResponseHaveEmptyBody` 与 `header()` 组合无死代码；`docs/C24-beta-sync-review-dev1-20260926.md` 的远端侧改动为状态文字更新，合并直接采用远端版本，无本地内容丢失。
- 复评未再发现必须修改的问题，进入收尾。

### 验证记录

- 通过：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ProxyRedirectResponseTest --tests com.fongmi.android.tv.server.process.ProxyRangeResponsePolicyTest --tests com.fongmi.android.tv.ui.dialog.SiteDialogThemeSourceTest --tests com.fongmi.android.tv.ui.dialog.InterfaceEntryInteractionTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac --continue`，BUILD SUCCESSFUL（41s），ProxyRedirectResponseTest 8/8、ProxyRangeResponsePolicyTest 4/4、SiteDialogThemeSourceTest 7/7、InterfaceEntryInteractionTest 10/10，无 failure/error，Leanback Java 编译通过。
- 通过：`git diff --check` 无空白错误；删除前/后引用检索确认资源无悬空引用。
- 通过：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` 的 Gradle 打包阶段 BUILD SUCCESSFUL（129 tasks，APK 生成成功）。
- **未验证边界**：安装/启动冒烟未执行——脚本在 `adb install` 阶段失败，`192.168.50.3:5555`（以及 5557/5559/5561）全部为 `offline`，本机已无 emulator 进程、无监听端口，仓库内也没有启动模拟器的脚本，按模拟器分配规则未强行连接或占用其它工作区设备。本任务实际代码改动仅为删除两个无引用资源，无运行时路径；未推送的 leanback UI 提交各自保留了当时的设备验证记录。
- 未执行多设备同步矩阵、原生/Go/Rust 工具链测试（与本任务改动无关）。

## 交付状态

- 本任务提交：`720d2bcbd3d16a8b91acb703fbee8ad22a408473`（删除两个被取代的 drawable + 本任务记录），恢复标签 `recovery/C25-beta-sync-review-dev1-20260926/20260927005542-720d2bcbd3d1`。
- 已推送 `dev1`（`5058aaab2..720d2bcbd`），并创建 base=`beta`、head=`dev1` 的中文 PR：<https://github.com/Silent1566/webhtv/pull/378>（只创建，未合并）。
- PR 内含 6 个提交：`f7674024e962`、`1bb72bd7099c`、`1e41863c181c`、`83e0a3159b90`、`c1645a374596`（合并提交）、`720d2bcbd3d1`。
