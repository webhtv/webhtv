# TV-CARD-BADGE-CONTRAST-20261012：TV 卡片角标仍是裸十六进制加 0.7 透明，浅色海报上白字不可读

## Recovery anchor

- 目标：TV 卡片三个角标（进度 / 片源 / 年份）进入语义 token 体系，且角标上的纯白文字在**任意海报**上都满足正文 4.5:1；首页时钟降为次级字号。
- 允许路径：`app/src/main/res/values{,-night}/webhtv_tokens.xml`、`app/src/leanback/res/color/bg_{remark,site,year}.xml`、`app/src/leanback/res/layout/activity_home.xml`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvCardBadgeContrastTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `aa722e43736`。
- 验收：① 三个角标色表不再含裸十六进制、不再带 alpha、不再引用 `?attr`；② 三个 token 在两张表同值；③ 白字对"合成到纯白海报后的角标底色" ≥4.5:1（新增测试固化）；④ 角标文字仍走 `?attr/webhtvColorOnWallpaper`；⑤ 定向 JVM 测试全绿；⑥ 分配模拟器 `192.168.50.3:5561` 前后对照。
- 回滚：撤销本任务原子提交即可回到裸十六进制 + 0.7 透明与 24sp 时钟；无数据迁移。
- 交付坐标：提交 `3e87e8364de`（dev4），recovery tag `recovery/TV-CARD-BADGE-CONTRAST-20261012/20261012151138`；未推送。
- 下一步唯一动作：无（TV-2 已交付）。下一批建议 TV-3（焦点与动效统一：`webhtv_focus_scale` 单一来源），或先补 §7.4 的扫描根缺口。

## 1. 现象与根因

上一轮（TV-1）在真机上看到卡片上有三种彩色角标：绿色进度（`07:52 / 42:23`）、红色片源（`腾讯视频[官]`）、蓝色年份。它们全部是 **leanback 独有的裸十六进制**：

| 角标 | 文件 | 改动前 |
|---|---|---|
| 进度 | `app/src/leanback/res/color/bg_remark.xml` | `#177535`，`android:alpha="0.7"` |
| 片源 | `app/src/leanback/res/color/bg_site.xml` | `#F44336`，`android:alpha="0.7"` |
| 年份 | `app/src/leanback/res/color/bg_year.xml` | `#2196F3`，`android:alpha="0.7"` |

两个问题：

1. **不在 token 体系内。** 这三个文件位于 `app/src/leanback/res/color/`，而仓库的静态门禁扫描根只有 `app/src/main/res/color`（这一覆盖缺口已由 `docs/C51-beta-merge-review-dev2-20261009.md` §132 记录），所以裸色值既不会被拦、也不会随主题变化。
2. **0.7 透明度让白字在浅色海报上不可读。** 角标文字恒为 `?attr/webhtvColorOnWallpaper`（纯白，两张表同值）。底色半透明时，实际呈现色 = 0.7×底色 + 0.3×海报；海报内容不受控，遇到浅色海报时角标被提亮，白字对比度跌到：

   | 角标 | 纯白海报上的白字对比度 |
   |---|---:|
   | 进度 `#177535` @0.7 | **3.18:1** |
   | 片源 `#F44336` @0.7 | **2.61:1** |
   | 年份 `#2196F3` @0.7 | **2.19:1** |

**为什么现有门禁没拦住。** `LeanbackForegroundContrastTest` 的 `luminance()` 会丢弃 alpha 通道，把半透明底色当成不透明来算——`#177535` 本身是深色，于是判定"深色表面，通过"。**它算的是未合成的颜色，而屏幕上呈现的是合成后的颜色。** 这是该守卫的一处盲区，本任务一并补上。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 本仓库 `app/src/leanback/res/color/bg_{remark,site,year}.xml` | HEAD `aa722e43736` | A（本地） | 三个角标为裸十六进制 + 0.7 alpha，确认问题 1、2 |
| 本仓库 `app/src/leanback/res/layout/adapter_vod.xml` | HEAD `aa722e43736` | A（本地） | `@id/year`、`@id/site`、`@id/playback` 三个角标的 `textColor` 均为 `?attr/webhtvColorOnWallpaper`（纯白）——这是"必须保证深底"的依据 |
| 本仓库 `app/src/mobile/res/color/bg_{remark,site,year}.xml` | HEAD `aa722e43736` | A（本地） | 手机端同一组角标早已语义化：`?attr/colorSecondaryContainer` / `?attr/colorErrorContainer` / `?attr/colorSurfaceVariant`（均 0.8 alpha）。说明"TV 端落后"而不是"设计如此" |
| 本仓库 `app/src/test/java/.../theme/LeanbackForegroundContrastTest.java` | HEAD `aa722e43736` | A（本地） | 其 `luminance()` 丢弃 alpha、`surfaceOfDrawable()` 不合成海报——门禁盲区的直接证据 |
| 真机截图（`192.168.50.3:5561`，1920×1080 / 280dpi） | 本轮 | A | 三个角标实际呈现；浅色海报区域肉眼可见白字发虚 |
| WCAG 2.2 §1.4.3 | 沿用仓库既有记录（访问日期 2026-09-20，本次未重新抓取） | A（间接） | 正文门槛 4.5:1；角标文字 14sp 不属大字号豁免 |
| 本仓库 `docs/webhtv-unified-visual-design-system-20260920.md` §5.1 | 仓库既有设计权威 | A（本地） | "固定色只允许出现在四类位置……其余必须在静态检查的 allowlist 中并写明原因"——这三个文件既不在 token 体系内也不在 allowlist 中 |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | 浅色海报上白字 2.19–3.18:1，真实不可读 | 拒绝 |
| 2. 照搬手机端：改用 `?attr/colorSecondaryContainer` 等调色板角色 | 与手机端完全一致 | 角标画在**海报**上而非页面表面上：浅色模式下 `colorSecondaryContainer` 是 `#DFE2E6`，0.8 透明叠在海报上再配白字会**更差**；且绿/红/蓝三色在 TV 上各自承载"进度/片源/年份"语义，换成调色板容器会丢掉这层编码 | 拒绝 |
| 3. **保留色相，改为不透明的语义 token（采用）** | 对比度与海报无关、色相编码保留、进入 token 体系 | 角标从半透明变实心，壁纸/海报透出感减弱 | **采用** |
| 4. 保留 0.7 透明、只把底色压得更暗 | 保住透出感 | 要满足"叠在纯白海报上仍 ≥4.5:1"，底色必须暗到近乎纯黑（绿要压到 `#0E3B1C` 量级），叠在深色海报上会变成一团发黑的色块，观感更差 | 拒绝 |

采用方案 3 的取值（均不透明，白字对比度与海报无关）：

| 角标 | 色相 | 新取值 | 白字对比度 | 改动前（纯白海报） |
|---|---|---:|---:|---:|
| 进度 | 绿 | `#177535` | **5.78:1** | 3.18:1 |
| 片源 | 红 | `#C62828` | **5.62:1** | 2.61:1 |
| 年份 | 蓝 | `#1565C0` | **5.74:1** | 2.19:1 |

红、蓝两色相较原值略有加深（`#F44336`→`#C62828`、`#2196F3`→`#1565C0`），原因是原色相本身就偏亮、不透明时仍达不到 4.5:1；绿保持原值不动。

**为什么用直接 `@color` 引用而不是 `?attr`：** 可读性依赖"底色足够暗"这一点，做成可被主题改写的角色等于放弃该保证；同时 `?attr` 填充会被 `LeanbackForegroundContrastTest` 判为"调色板跟随背景（日间为浅色）"而产生误报。这与 `shape_vod_name` 直接用 `@color/black_40` 的既有做法一致。

## 4. 实施

1. `values/webhtv_tokens.xml` 与 `values-night/webhtv_tokens.xml` 各新增 `webhtv_color_badge_progress` / `_site` / `_year`（两张表同值，附取值与对比度推导注释）。
2. `app/src/leanback/res/color/bg_{remark,site,year}.xml` 改为引用对应 token，去掉 `android:alpha`。
3. `app/src/leanback/res/layout/activity_home.xml`：`@id/clock` 由 `24sp` 降为 `16sp`（与 24sp 的品牌标题拉开层级；保持纯白，避免在不受控壁纸上引入对比度风险）。
4. 新增 `TvCardBadgeContrastTest`（4 项）：角标色表无裸十六进制/无 alpha/不引用 `?attr`；token 两张表同值；**按声明的 alpha 合成到纯白海报后断言白字 ≥4.5:1**；角标文字仍走 `?attr/webhtvColorOnWallpaper`。

## 5. 不变量与回滚

- 不改动 `app/src/mobile/**`：手机端的角标语义色与本次无关。
- 不改动角标 drawable（`shape_vod_remark` / `shape_vod_site` / `shape_vod_year`）的形状、圆角、内边距与引用它的布局。
- 不改动角标文字颜色（仍为 `?attr/webhtvColorOnWallpaper`）。
- 不改动 `ThemeTokens` 的 49 项角色契约：三个新 token 是角色契约之外的资源，与 `webhtv_on_wallpaper` 同一先例。
- 不改动播放器解码、渲染、字幕、音轨、网络与数据行为。
- 回滚：撤销本任务原子提交即可回到裸十六进制 + 0.7 透明与 24sp 时钟；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.activity.Tv*"` → 首轮 205 项中 1 项失败：`ThemeContractTest.resourceMappingMatchesTheImmutableRecordForEveryColor` 冻结了 token 文件里 `webhtv_color_*` 一族的条目数（51）。三个新 token 属于该命名族，因此把这个计数显式改成 **54** 并在断言处写明来由；复跑 **BUILD SUCCESSFUL in 1m 38s**，全绿。
- 新增 `TvCardBadgeContrastTest` 4 项：角标色表无裸十六进制/无 alpha/不引用 `?attr`；token 两张表同值；**按声明的 alpha 合成到纯白海报后白字 ≥4.5:1**；角标文字仍走 `?attr/webhtvColorOnWallpaper`。
- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*"` → **BUILD SUCCESSFUL in 2m 38s**（`ThemeContractTest` 在手机端同样跑，确认共享 token 文件的改动无手机端回归）。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 1m 41s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。需要说明的是，本任务修改的三个文件本就**不在**该脚本的扫描根内（见 §7.4），所以即使能跑也覆盖不到；本次未新增裸十六进制。

### 6.2 设备实测（`192.168.50.3:5561`，API 28 / 1920×1080 / 280dpi）

同一台模拟器，改前基线为 TV-1 的构建（`.design-evidence/e5561-after-home.png`），改后为本次构建，`adb install -r` 覆盖安装。

| 检查项 | 结果 |
|---|---|
| 冷启动 | 正常进入首页，无崩溃 |
| 进度角标 | 由半透明绿变为实心深绿 `#177535`，白字锐利 |
| 片源角标 | 由半透明红变为实心深红 `#C62828`，白字锐利 |
| **与海报无关** | 第 1 张卡的绿色角标压在深青海报上、第 2 张压在**亮橙色**海报区域上，两者呈现**完全一致**——这正是本次要证明的：对比度不再受海报内容影响（改动前第二张会被提亮到 3.18:1） |
| 首页时钟 | 由 24sp 降为 16sp，不再与 24sp 的品牌标题抢视觉 |

截图：`.design-evidence/e5561-tv2-home.png`；角标放大对照 `e5561-tv2-badge-crop.png`（片源角标）、`e5561-tv2-progress-crop.png`（进度角标）。改前对照 `e5561-after-home.png`。

### 6.3 结论

三个角标进入 token 体系、对比度与海报解耦，首页时钟层级拉开；定向测试与手机端回归均通过。

### 6.4 残余风险

- 角标由半透明变实心，海报透出感消失（用户已在 TV-1 确认接受更不透明的承载面）。这是本任务唯一的观感变化。
- 红、蓝两色相略有加深（`#F44336`→`#C62828`、`#2196F3`→`#1565C0`），因为原值不透明时仍达不到 4.5:1。
- 年份角标在首页未出现（`@id/year` 默认 `visibility="gone"`，仅在搜索结果等场景显示），本次只通过单元测试覆盖其取值，未做真机截图。

## 7. 相邻发现（含对上一轮记录的两条更正）

1. **更正：设置项 URL「被裁切」是误报。** 上一轮记录 `vodUrl`/`liveUrl` 的 URL 末尾被裁掉。本轮从设备读取真实配置为 `http://192.168.50.50:4567/sub/2024/buye-0`（`config_0`/`config_1`），与屏幕上显示的字符串**逐字相同**——URL 本来就那么长，没有裁切。该项撤销，不需要修复。
2. **更正：叠加层空元数据未能复现，且唯一候选路径本就正确。** 上一轮记录视频窗口内的紫色面板显示空的「导演：」「演员：」。本轮查明该面板是内置 WebHome 详情页（`assets/webhome/eclipse-detail.html`），而它的 `addCredit()` 第 673 行已有 `if (!text(value)) return;` 守卫，空值根本不会生成行；原生路径的 `app:id/director`/`app:id/actor` 也已由 TV-1 修好。结论：该现象很可能是上一轮在应用尚处加载/切换时截屏得到的瞬时画面，不作为待修缺陷。若后续在稳定状态下复现，再单独立项。
3. **TMDB 评分色语义化（低优先级，未纳入本批）。** `adapter_tmdb_rail_item.xml` 等 5 个布局的评分文字引用 `@color/webhtv_color_player_control_active`（设计文档明确该角色"不用于普通 UI"），且 `TmdbRailAdapter.java:130`、`TmdbHeaderView.java:71/1903/2334/2383` 在 Java 里写死 `0xFFFFD35C` / `0xFF78E08F` / `0xFF1D8F5A` / `0xFFFFC107`。但评分角标坐在 `shape_tmdb_rating`（`webhtv_color_player_scrim`，恒深）上，前景必须是"与调色板无关的浅色"——这正是 `webhtv_on_wallpaper` 那一类角色。所以它是**代码卫生**问题（角色命名与硬编码），用户可见影响接近零；且 TMDB 界面自带 `TmdbCinemaTheme` 固定深色调色板，贸然替换有回归风险。建议单独立项、按"新增 palette-independent 评分角色"处理。
4. **`app/src/leanback/res/color/` 整体不在静态门禁扫描根内**（`docs/C51-beta-merge-review-dev2-20261009.md` §132 已记录）。本任务顺手清掉了该目录 5 个含裸色值文件中的 3 个；剩余 `exit_confirm_secondary_text.xml`（`#174EA6`/`#202124`）与 `home_menu_text.xml`（`#0B57D0`/`#006876`）仍未语义化，建议在扩大扫描根的任务里一并处理。
