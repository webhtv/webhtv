# PLUGIN-01 · 全局插件框架设计评估

- 状态：**评估完成 + D1–D4 已裁定；P1–P10 已实施并合入 `dev4`**（本文件起初是只读评估稿，§10 起在同一文件里滚动追加各阶段实施记录；不要按 §0–§9 的「未进入实施」阅读）
- 日期：2026-10-30（初次评估）；实施与救订记录截至 2026-11-03
- 分支/HEAD：`dev4` @ `379fbcdd5aa`（已合入 `origin/beta`）
- 作者：Dispatch Agent（§0–§9 为只读评估；§10 起为各阶段实施与评审记录）
- 关联文档：`docs/universal-webhome-theme-design.md`、`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md`、`docs/webtheme-compatibility-matrix.md`、`docs/C117-beta-merge-review-dev4-20261103.md`

---

## 0. 一句话结论

**值得做，但不能建在 WebView 皮肤页上。**

现有 `WebHome` 皮肤系统的**形状**（清单 + 能力注册表 + 生命周期事件 + 权限白名单）可以直接复用，但它的**宿主**只覆盖 `HOME`/`DETAIL` 两个 WebView 页面；需求里的「详情页」「播放页」「电视版插入按钮」绝大多数落在**原生 Activity** 上，那里目前**不存在任何插件机制**（全仓 `ServiceLoader` / `PluginRegistry` / `ExtensionPoint` 零命中）。

因此正确做法是：**新建一个与皮肤系统平级的原生插件宿主层**，复用字幕源/爬虫源的脚本运行时形状（js/python 外部文件），并从皮肤系统借走清单格式、能力白名单和事件命名习惯。

---

## 1. 需求澄清（本次新增的关键约束）

用户澄清：**插件是 js / python / html 这类外部文件，类似现有的爬虫源和字幕源**。

这把设计重心从"给 WebView 加钩子"改成了"建一个脚本宿主"。三个既有先例分别对应三种插件类型：

| 插件类型 | 既有先例 | 运行时 | 证据 |
| --- | --- | --- | --- |
| **js 外部脚本** | 爬虫源 `api=js`、站点扩展脚本 | QuickJS | `quickjs/src/main/java/com/fongmi/quickjs/crawler/Spider.java`；`JsLoader.java` |
| **python 外部脚本** | 字幕源 `api=py`、爬虫源 `api=py` | Chaquopy | `subtitle/source/PythonSubtitleScriptRuntime.java`；`PyLoader.java` |
| **html 页面** | 全局皮肤页 `eclipse.html` / `eclipse-detail.html` | WebView | `assets/webhome/theme.json`、`WebThemePage` |

**字幕源是最贴近的架构样板**——它证明本仓库已经有一条"非 spider 的非 Java 脚本宿主"路径，且是完整闭环：

```
assets/subtitle_sources/builtin/manifest.json   ← 声明式清单
  → SubtitleSourceConfig                        ← 单条源的类型化配置（type/api/ext/sha256/priority/timeout/headers/params）
  → SubtitleScriptRuntimeFactory.create(api)    ← 按 api 分发到具体运行时
  → SubtitleScriptRuntime                       ← 统一接口：init / search / resolve / destroy
  → SubtitleSourceManager.install(...)          ← 订阅安装、注册、失败回滚、destroy
  → SubtitleProviderRegistry                    ← 注册表
```

这五层结构**逐层都能平移到插件框架**，只是把 `search/resolve` 换成 `onEvent/contributeUi`。

---

## 2. 现有资产盘点

### 2.1 皮肤系统（WebView 侧）——可借形状，不可借宿主

| 资产 | 事实 | 证据 |
| --- | --- | --- |
| 页面枚举只有两页 | `HOME` / `DETAIL` | `web/WebThemePage.java:3-6` |
| 清单模型完整 | `schemaVersion:2` / `minHostApi:3` / `permissions` / `pages{entry,contract,fallback}` / `player.chrome` | `assets/webhome/theme.json`；`WebThemeManifest.java:18-19` |
| 能力白名单是单一事实源 | 每个 method 绑定 permission + 允许页面 | `web/WebThemeCapabilityRegistry.java:12-36` |
| 已有 JS 生命周期事件总线 | `fmsdk` / `fmresume` / `fmpause` / `fmviewport` / `fmdetailchange` / `fmextload` / `fmexterror` / `fmurlchange` | `HomeWebController.java:1868,1992,2153,502,1594`；`WebHomeViewport.java:136`；`WebHomeExtension.java:168-172` |
| 已有类油猴扩展脚本 | `runAt=document-start/end/idle`、`depends` 版本约束、GM_addStyle/GM_getValue/GM_xmlhttpRequest shim | `web/ext/WebHomeExtension.java:26-28,154-177` |
| **扩展只注入 SITE 模式** | `injectsSiteExtensions()` 仅当 `mode == SITE`，**内置 Eclipse 与远程全局皮肤都拿不到** | `web/WebHomeTarget.java:118-120` |

### 2.2 原生页面（真正需要插件的地方）——目前零插件机制

| 事实 | 证据 |
| --- | --- |
| `App` 已实现 `ActivityLifecycleCallbacks` 并注册，但 `onActivityCreated`/`onActivityDestroyed` 是**空实现** | `App.java:45,142,355,359` |
| `ProgressLayout.addOverlayView()` 存在，语义正是"不随 CONTENT/PROGRESS/EMPTY 切换而隐藏的子视图" | `ui/custom/ProgressLayout.java:223-226` |
| 已有真实用例（leanback 音频舞台） | `leanback/.../VideoActivity.java:9526` |
| `ProgressLayout.showContent()` 是 `VideoActivity` 两端唯一的"内容就绪"统一点 | `ProgressLayout.java:236`；mobile `VideoActivity.java:5622`；leanback 2450/2948/3036/3061/5673/6046 |
| `TmdbDetailActivity` 真正 reveal 在 `loadTmdbMediaBlocks` | `:3094`，由 `:3000` 的 `binding.getRoot().post` 触发 |
| **`TmdbDetailModeController.onContentLoaded()` 是死钩子** | 声明 `ui/detail/TmdbDetailModeController.java:40`，空实现 `BaseTmdbDetailModeController.java:29`，全仓**零调用点** |
| `PlaybackActivity` 有完整状态回调 | `onServiceConnected:364/1312`、`onPlaybackStateChanged:1194`、`onIsPlayingChanged:1174`、`onPositionDiscontinuity:1188`、`onResume:1346`、`onPause:1364` |
| **切集不在 `PlaybackActivity`**，在子类 | `VideoActivity.onItemClick(Episode)` mobile:3315 / leanback:4009 |
| 可插入容器 | `getExoView():208`；两端都 include 的 `mBinding.control.getRoot()`（leanback `applyPlaybackOverlay:1636` 已是先例） |

### 2.3 双端分支方式（决定插件 UI 怎么落）

**不是同 Activity 内分支，是 Gradle productFlavors + 独立源集。**

- `app/build.gradle:63-80`：`flavorDimensions = ["mode","abi"]`，`leanback` / `mobile` 各自一份
- `src/mobile`、`src/leanback` 各写一份 `VideoActivity` / `BaseActivity` / `HomeActivity`，布局也各一份
- 共享代码在 `src/main`

推论：
- **悬浮按钮** → 放 `src/main`，挂 `App` 的 Activity 生命周期回调，**零布局改动，两端通吃**
- **插入现有 UI** → 复用两端都有的 `control.getRoot()`（控制条）或 `ProgressLayout.addOverlayView`，**也可以零布局改动**
- **进正文区** → 必须 mobile/leanback 各改一份 `activity_video.xml`，成本最高，应后置

### 2.4 主题系统（插件 UI 的样式来源）

`ThemeController.applyFromPreferences:344` / `bindTheme:372` / `bindDialog:377` 全部在 `src/main`，两端 `BaseActivity` 已接线；`ThemeBinder.bind:133` 遍历视图树（`ViewGroup` 递归 `:168`）。

**结论：插件 UI 只要进视图树或用 `?attr/color*`，就自动跟随主题。** 这是零成本的样式一致性来源。

### 2.5 结构风险（既有文档已记录）

`HomeWebController`（107KB）与 `WebHomeThemeBridge`（30KB）职责已过载，文档明确写：

> 「后续不应继续把搜索、历史列表和设置接口直接堆入这两个类。」

—— `docs/universal-webhome-theme-design.md:1542`

**插件框架绝不能塞进这两个类。**

---

## 3. 「现有 html 和全局皮肤逻辑是否有帮助」——明确判定

### 3.1 有帮助的部分（值得借用）

| 借什么 | 为什么 |
| --- | --- |
| **清单格式范式** | `{schemaVersion, id, name, version, minHostApi, pages, permissions}` 已经过两版迭代，字段语义清晰，插件清单照抄即可，无需发明 |
| **能力注册表范式** | `WebThemeCapabilityRegistry` 证明"method → permission → allowedPages"的集中白名单在本项目可维护，且已有兼容矩阵生成器（`WebThemeCompatibilityMatrix`）与漂移测试（`WebThemeCompatibilityMatrixTest`） |
| **生命周期事件命名** | `fm*` 前缀 + `CustomEvent` 的 JS 侧习惯可直接延续到插件 SDK，降低插件作者学习成本 |
| **类油猴脚本 DSL** | `runAt`、`depends`、GM_* shim 已实现且被 `WebHomeExtensionDialog` 完整编辑/调试过，是 js 插件的现成书写体验 |
| **html 页面插件形态** | 若插件要贡献**整页**（如豆瓣评论全屏页），直接复用 WebTheme 的 manifest page 机制 + `WebThemeDetailActivity` 的 `onWebLoading/onWebReady/onWebError` 回调模型 |
| **主题 token 链** | 插件 UI 免费获得跟随主题的能力 |

### 3.2 没帮助的部分（不能依赖）

| 不能依赖 | 原因 |
| --- | --- |
| **把插件注入到皮肤页来解决原生页需求** | 皮肤只管 `HOME`/`DETAIL` 两个 WebView 页面，原生详情页 `TmdbDetailActivity` / `VideoActivity`、播放页 `PlaybackActivity` 根本不经过 WebView |
| **`WebHomeExtension` 现有扩展路径** | `injectsSiteExtensions()` 仅 `Mode.SITE`（`WebHomeTarget.java:118-120`），内置 Eclipse 与全局远程皮肤都被排除 |
| **`HomeWebController` 作为插件宿主** | 107KB 已过载，文档明确禁止继续堆接口 |
| **`WebHomeChrome` / `WebHomeViewport`** | 只服务内置 Web 页，**未进入原生 Activity 视图树**（`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md:15` 明确 WebHome 皮肤保持独立） |
| **`TmdbDetailModeController.onContentLoaded`** | 死钩子，零调用点，不能当"详情页就绪"用 |

### 3.3 判定

> **皮肤系统贡献的是「协议形状」与「html 页面插件」两类价值；它贡献不了「原生页挂载点」。**
> 插件框架 = 新宿主层（原生），复用旧协议形状（清单/白名单/事件）。

---

## 4. 设计

### 4.1 分层

```
┌─────────────────────────────────────────────────────────┐
│ 插件包（外部文件，用户可安装）                            │
│   manifest.json + plugin.js / plugin.py / page.html      │
└─────────────────────────────────────────────────────────┘
              ↓ 声明
┌─────────────────────────────────────────────────────────┐
│ PluginManifest / PluginConfig    （借 WebThemeManifest）  │
│   id/name/version/minHostApi/events[]/ui[]/permissions[]  │
└─────────────────────────────────────────────────────────┘
              ↓ 分发
┌─────────────────────────────────────────────────────────┐
│ PluginRuntimeFactory.create(api)  （借 SubtitleScriptRuntimeFactory）│
│   js → QuickJS   py → Chaquopy   html → WebView          │
└─────────────────────────────────────────────────────────┘
              ↓ 统一接口
┌─────────────────────────────────────────────────────────┐
│ PluginRuntime            （借 SubtitleScriptRuntime 形状）│
│   init(config) / onEvent(name, payload) / destroy()      │
└─────────────────────────────────────────────────────────┘
              ↑ 事件源                 ↓ UI 贡献
┌──────────────────────────┐  ┌──────────────────────────┐
│ PluginEventBus           │  │ PluginUiHost             │
│  宿主在各 Activity 埋点   │  │  FLOATING / TOOLBAR /    │
│  统一派发给订阅者         │  │  PANEL / PAGE            │
└──────────────────────────┘  └──────────────────────────┘
              ↑ 能力
┌─────────────────────────────────────────────────────────┐
│ PluginCapabilityRegistry  （借 WebThemeCapabilityRegistry）│
│   method → permission → allowedEvents                    │
└─────────────────────────────────────────────────────────┘
```

### 4.2 插件清单（草案）

```json
{
  "protocol": "webhtv-plugin",
  "version": 1,
  "id": "douban.comments",
  "name": "豆瓣评论",
  "sdk": 1,
  "targets": ["mobile", "leanback"],
  "runtime": { "api": "js", "entry": "douban.js", "timeout": 15 },
  "events": ["detail.ready", "playback.ready", "episode.changed"],
  "ui": [
    {
      "slot": "floating",
      "event": "detail.ready",
      "anchor": "bottom-end",
      "leanback": { "slot": "toolbar", "anchor": "before:episode-list" },
      "label": "豆瓣",
      "action": "douban.comments.show"
    }
  ],
  "permissions": ["plugin.data.episode", "net.request", "ui.floating", "ui.panel"]
}
```

要点：
- **`ui[].slot`** 同时覆盖"动态新增悬浮按钮"（`floating`）与"插到现有界面里"（`toolbar`），满足电视版需求
- **`ui[].leanback` 覆盖** 让同一插件在两端走不同槽位，无需改布局
- **`events[]`** 声明式订阅，宿主只对声明过的事件派发，天然限流
- **`permissions[]`** 与 `WebThemeCapabilityRegistry` 同构，可复用其兼容矩阵工具链

### 4.3 生命周期契约（宿主埋点位置）

| 事件 | 埋点位置 | 备注 |
| --- | --- | --- |
| `app.foreground` / `app.background` | `App.onActivityResumed:345` / `onActivityPaused:350` | 已有，零改动 |
| `activity.created` | `App.onActivityCreated:355`（**当前空实现**） | 补一行即可 |
| `activity.destroyed` | `App.onActivityDestroyed:359`（**当前空实现**） | 补一行即可 |
| `detail.ready` | `ProgressLayout.showContent():236` 单点 + `TmdbDetailActivity:3094` | **`showContent` 是唯一能覆盖两端的统一点** |
| `playback.ready` | `PlaybackActivity.onServiceConnected(ComponentName,IBinder):1312` | |
| `playback.playing` | `onIsPlayingChanged:1174` | |
| `playback.state` | `onPlaybackStateChanged:1194` | |
| `playback.pause` / `resume` | `onPause:1364` / `onResume:1346` | |
| `episode.changed` | `VideoActivity.onItemClick(Episode)` mobile:3315 / leanback:4009 | 必须双方各埋一次 |

**关键取舍**：`detail.ready` 若走 `ProgressLayout.showContent()`，能在不改两端 `VideoActivity` 的前提下覆盖全部原生详情场景；代价是事件粒度较粗（只知道"内容显示了"，不知道"TMDB 数据填充完毕"）。若要细粒度，再在 `TmdbDetailActivity:3094` 单独补一条 `detail.tmdb.ready`。

### 4.4 UI 插槽

| 槽位 | 实现 | 双端成本 | 适用 |
| --- | --- | --- | --- |
| `floating` | `ProgressLayout.addOverlayView()`（`:223`，已证明不随状态切换隐藏）或 Activity 根 FrameLayout 的 `addView` | **零布局改动** | 手机悬浮球；TV 也可用 |
| `toolbar` | `mBinding.control.getRoot()` 插入（两端都有 `include`） | **零布局改动** | 电视版"插到现有界面里" |
| `panel` | `BaseBottomSheetDialog` / `BaseAlertDialog`（现有基类） | 零布局改动（自带布局文件） | 评论列表 |
| `page` | 复用 WebTheme 的 manifest page + `WebThemeDetailActivity` 模式 | 中（需要新 manifest 字段） | 插件贡献整页 |

**所有槽位的子视图都会自动被 `ThemeBinder.bind` 遍历到，因此免费跟随主题。**

### 4.5 运行时接口（草案）

对齐 `SubtitleScriptRuntime` 的四方法形状：

```java
public interface PluginRuntime {
    String init(String pluginId, String entry, String config) throws Exception;
    String onEvent(String pluginId, String eventName, String payload) throws Exception;
    String action(String pluginId, String actionId, String payload) throws Exception;
    void destroy(String pluginId);
}
```

`PluginRuntimeFactory.create(api)` 按 `js|py|html` 分发：
- `js` → `quickjs.crawler.Loader`（复用 `JsLoader` 的线程/销毁语义）
- `py` → `chaquo.Loader`（**注意：`chaquo.Spider` 已有 `subtitleInit/Search/Resolve`，但 quickjs `Spider` 没有对应方法**——py 插件需要新增一个 hook 方法，或复用 `action()`）
- `html` → WebView 页面（复用 WebTheme 页面机制）

### 4.6 首个验证用例：豆瓣评论插件

**能力判定（子代理已核实）**：

| 需要 | 现状 | 结论 |
| --- | --- | --- |
| 剧名 | 有（TMDB `TmdbService.java:107`；豆瓣 `DoubanSubject:1739`） | 可直读 |
| 年份 | 有（藏在 `TmdbItem.subtitle`，`TmdbService.java:114`） | 可直读 |
| 导演/演员/角色名 | **只有 TMDB**（`TmdbSourceAdapter.java:137-178`、`TmdbPerson.java:7-12`） | 可直读 |
| 豆瓣评分 | 有（`loadDoubanRating:154-168` → `TmdbItem.doubanRating:26`） | 可直读 |
| 简介 | 只有 TMDB（豆瓣条目 `overview` 为空，`DoubanSubject:1761`） | 可直读 |
| **豆瓣短评/评论** | **完全不存在**（全仓只有 suggest/search/abstract/related_items） | **需要新建抓取** |

结论：**"读取剧名/演员等字段"已经零成本**；缺的只是短评数据源本身。

**可复用的两段现成代码**：

1. **豆瓣反爬限速**：`PersonalRecommendationService` 已有串行化 + 750ms 间隔（`:908-982`）+ 418/429/403 冷却（`:959-965`）+ 7 天磁盘缓存（`:60`）。短评抓取必须复用这套节奏，否则必被封。
2. **评论 UI**：`utils/AudioCommentUtil.java:38`（网易云评论 API）+ `ui/dialog/AudioCommentDialog.java`（底部弹层、列表、分页、加载态、空态）——**结构可直接照搬，只换数据源**。

**网络入口**：`catvod/src/main/java/com/github/catvod/net/OkHttp.java:40`，用 `client()`（`:101`）。**不要用 `GithubProxy`**（只服务 GitHub 下载）。

**配置缺口**：当前**没有**通用"外部 API 请求"开关，只有按功能开关（`Setting.java:1050/991/1141`）。插件框架需要新增一个插件级总开关 + 每插件开关。

---

## 5. 安全边界（必须从第一版就有）

1. **权限白名单是硬门禁**：照抄 `WebThemeCapabilityRegistry` 的模式，未在 `permissions[]` 声明且未在清单中登记的 method 一律拒绝。这条在 `docs/universal-webhome-theme-design.md:142` 已有明文约束：「不在 V2 第一阶段开放任意插件代码或无边界的 `net.request`」。
2. **`net.request` 走审计日志而非域名白名单**（D2 裁定）：不限制目标 host，但每次外发必须落审计记录（字段与落地见 §8.1）。因缺少白名单这道硬约束，**审计日志是实现该决策的必需件，不是可选装饰**。
3. **不暴露文件/凭据/Cookie/站点扩展代码**（同上文档 `:133`）。
4. **jar 插件不纳入第一版**。`G:\下载\custom_spider.jar` 的取证结果说明问题：该 jar 是 **COOS V6 加壳站源**（`AES/GCM/NoPadding` + `AndroidKeyStore` + 远程 payload 下发 + ELF `v7.bin`/`v8.bin`），dex 内**零豆瓣/评论字符串**。它既不能作为实现参考，也证明 jar 层天然不透明、不可审计。**第一版只支持 js/py/html 明文脚本。**
5. **插件失败必须软着陆**：任何插件异常只记录 + 禁用它自己，不得影响宿主页面（参照 `WebHomeExtension.script()` 的 `try/catch` + `fmexterror` 事件模式，`WebHomeExtension.java:167-173`）。

---

## 6. 分期实施（建议，未获批准前不实施）

| 切片 | 内容 | 可独立回滚 | 验收 |
| --- | --- | --- | --- |
| **P1** | 只读补洞：把 `App.onActivityCreated/onActivityDestroyed` 接上事件总线 + `ProgressLayout.showContent` 派发 `detail.ready`。**不引入任何插件加载能力。** | 是 | 单测：事件按预期触发；无插件时零副作用 |
| **P2** | 插件清单解析 + 能力注册表 + `PluginRuntimeFactory`（`js`）+ `floating` 槽位 + **审计日志**。 | 是 | 示例 js 插件在 `TmdbDetailActivity` 与 `VideoActivity`(mobile/leanback) 显示悬浮按钮；审计日志可见该插件的每次外发请求 |
| **P3** | **电视入口**（D3 推荐方案）：`toolbar` 槽位 —— 播放页控制条 chip + 详情页 `headerBar` 按钮；`panel` 槽位（`BaseSideSheetDialog`）+ `py` 运行时。 | 是 | leanback 控制条末尾出现「豆瓣」chip，方向键可达，打开侧滑面板后焦点可返回原 chip |
| **P4** | 豆瓣评论插件本体：短评抓取（复用 750ms 限速/冷却/缓存）+ `AudioCommentDialog` 式列表 + `pauseForOverlayPlayback` 接线。 | 是 | 详情页/播放页触发 → 列出当前剧集豆瓣短评；面板打开期间播放暂停、关闭后恢复 |
| **P5** | `page` 槽位（**D1 已允许** html 整页插件），与 WebTheme manifest page 机制合并。 | 是 | 插件可贡献独立整页，且继承主题与返回栈 |
| **P6** | **分发双通道**（D4）：`PluginSourceManager` 照 `SubtitleSourceManager` 支持订阅带入 + `WebHomeExtensionSourceStore` 式手动添加。 | 是 | 订阅 JSON 含 `plugins[]` 可整批安装/回滚；用户可手动贴代码或填 URL |

**推荐从 P1 开始**：它零风险、零新概念、可独立验证，且是后续所有切片的地基。

**顺序调整说明**：D1 允许 html 整页后，`page` 槽位不再需要压在最后作为"高风险项"，但它仍排在 P5 —— 因为它依赖 P2 的运行时与 P3 的宿主稳定；D4 的双通道放在 P6 是因为它同时依赖订阅解析与手动编辑器两块，属于"能力齐备后的入口收口"，早做会两次返工。

---

## 7. 风险与回滚

| 风险 | 缓解 |
| --- | --- |
| 插件在 TV 上抢焦点，破坏遥控导航 | `floating`/tvmode 默认 `focusable=false`；`toolbar` 槽位必须实现 `nextFocusUp/Down` 链，且插入点限定在已知焦点环内 |
| 事件粒度粗导致插件拿不到数据 | `detail.ready` 先粗后细，按需追加 `detail.tmdb.ready` 等细粒度事件，不改已有契约 |
| 豆瓣抓取被封 | 强制复用既有 750ms 串行 + 冷却 + 7 天缓存；插件层再叠一层 `timeout`（清单已留字段） |
| 插件宿主又变成第二个 `HomeWebController` | 强制按 5 层拆分（manifest / registry / factory / runtime / bus+host），任一层超 500 行即拒绝合并 |
| 破坏现有页面 | 所有宿主埋点必须是**空订阅即零行为**：事件总线无订阅者时直接 return，不改任何既有可见性/焦点逻辑 |

**回滚**：每切片独立提交 + 独立 recovery tag；P1 可单点 revert（两处事件派发 + 一行注册）。

---

## 8. 已确认决策（2026-10-30 用户裁定）

| # | 决策点 | 结论 | 对设计的影响 |
| --- | --- | --- | --- |
| D1 | 是否允许插件贡献**整页 html** | **允许** | `page` 槽位进入第一版范围（原 P5 提前）；复用 WebTheme 的 manifest page 机制而非新造契约 |
| D2 | `net.request` 是否要域名白名单 | **仅审计日志** | 不设 host 白/黑名单；但**必须**完整记录每次外发请求（host/path/状态码/耗时/插件 id/触发事件）。审计日志因此从"可选"升为**验收项**，见 §8.1 |
| D3 | 电视版插入点 | **采纳 §8.2 的推荐方案** | 见 §8.2 |
| D4 | 插件分发方式 | **两者都要**：用户手动 App 内添加 **且** 点播订阅可带插件 | 对齐字幕源的 `SubtitleSourceManager.install(subscriptionId, json, baseUrl)` 语义；见 §8.3 |

### 8.1 审计日志（D2 的落地要求）

因为不做域名限制，审计日志就是唯一的安全观测面，**不可裁剪为调试开关**：

- 记录字段：`pluginId` / `pluginVersion` / 触发事件 / method / 目标 host+path（**不含 query，避免 token 泄漏**）/ 请求体大小 / 响应码 / 耗时 / 结果（ok|error|timeout）
- 复用 `WebThemeRuntimeDiagnostics.safeUrl()` 的脱敏规则，保持与主题侧一致
- 落盘位置复用 `CacheModuleRegistry`（已在列 `js`/`py`/`jar`，追加 `plugin` 模块），使 `CacheCleanupManager` 的清理与保护规则自动覆盖插件目录
- 用户可在插件设置页查看/清空；**任一插件被禁用时其日志一并标记来源，便于追责到具体插件**

### 8.2 电视版遥控交互推荐（D3）

#### 结论

> **推荐「控制条 chip」作为电视版主入口，而非屏幕悬浮球。**
> 详情页用**顶部按钮条**（`headerBar` 同级位），播放页用**控制条 chip**（`view_control_vod_action` 末端位）。

#### 证据

| 事实 | 证据 |
| --- | --- |
| 电视控制条本身就是**一列 chip**，且用统一样式 | `leanback/res/layout/view_control_vod_action.xml` 里全部 `style="@style/Control"`（`next`/`prev`/`episodes`/`discMenu`/`search`/`fullscreen`/`player`/`decode`/`speed`/`scale`/`lut`… 20+ 个） |
| `Control` 样式已定义好焦点语义 | `leanback/res/values/styles.xml:80-89`：`selector_chip` 背景、`focusable=true`、`nextFocusDown=@id/timeBar` |
| 控制条容器已可编程访问，且已有改其背景的先例 | `mBinding.control.getRoot()`，`leanback VideoActivity.java:1637-1638`（`applyPlaybackOverlay`） |
| 控制条是 `HorizontalScrollView` + `LinearLayout`，追加 chip 天然可行 | `view_control_vod_action.xml:2`、`:10`（`@+id/container`） |
| 电视焦点已有统一助手，不必各写 | `ui/helper/PlayerControlFocusHelper.ensureFocus/handleKey`（`:19`/`:29`） |
| 焦点环色已有单一来源 | `ThemeController.focusRingColor()`（`:423`/`:440`），被 6+ 处复用 |
| 详情页顶部已有**同构按钮条**可插入 | `activity_tmdb_detail.xml:47-129`（`headerBar`，内含 `themeModeTop`/`rematchTop`/`keepTop`/`followingTop`，均 `MaterialButton` + `minHeight=40dp` + `?attr/materialButtonOutlinedStyle`） |
| 详情页还有融合操作区可插 | `activity_tmdb_detail.xml:153-162`（`fusionActions`，`FlexboxLayout` 自动换行） |

#### 为什么不选屏幕悬浮球

- 电视靠**焦点环**表达"我在哪"，悬浮球不在任何焦点链里，遥控器**够不到**；若强行 `focusable`，则会在原有导航路径上凭空多出一个停靠点，破坏 `PlayerControlFocusHelper` 的既有方向推断
- `TvFocusScale` 的类注释记录了一个反证：详情页密集行放大后被裁切，所以电视端的强调机制是**焦点环 + 抬升**，不是"飘在角上"
- 用户在电视上看评论时，手已经停在遥控器方向键上，**控制条里多一个 chip 是最短路径**

#### 具体落法（三处，全部零布局改动）

```
播放页  → 向 mBinding.control.action.container 追加 chip（style=@style/Control）
          nextFocusUp 指向原最后一个 chip，nextFocusDown 保持 @id/timeBar
详情页  → 向 headerBar 追加 MaterialButton（照抄 themeModeTop 的属性组）
         或向 fusionActions 追加（Flexbox 自动换行，窄屏更稳）
手机端  → floating 槽位：ProgressLayout.addOverlayView()，默认 focusable=false（触摸可达）
```

#### 交互细节（决定"好看且好用"的部分）

| 项 | 做法 | 理由 |
| --- | --- | --- |
| chip 文案 | 与既有 chip 一致用**短中文**（如「豆瓣」），不写「豆瓣评论」 | 控制条已有 20+ chip，长文案会挤压 `HorizontalScrollView` |
| chip 顺序 | 追加在 `container` **末尾**，不插中间 | 不改变既有方向键路径的肌肉记忆 |
| 打开面板 | 复用 `BaseSideSheetDialog`（`ui/dialog/`），不是 BottomSheet | 电视端侧滑面板不遮挡画面中心，且是大屏既有模式 |
| 播放态处理 | 打开面板前调 `pauseForOverlayPlayback()`，关闭后 `resumeAfterOverlayPlayback()` | **已有现成 API**（`PlaybackActivity.java:255`/`:269`），语义正是"覆盖层播放期间暂停"，不要自己写暂停逻辑 |
| 焦点返回 | 面板关闭后 `requestFocus()` 回原 chip | 仓库里**没有** `FocusRestorer`（本文早期版本的误记）。实际由 `PluginUiPresenter.focusLastClicked()` 自己实现，`PluginPanelDialog.onDismiss` 触发——见 §19 |
| 空态 | 复用 `ProgressLayout` 的空态或 `AudioCommentDialog` 的空态文案 | 两者都已存在，不新造 |
| 加载态 | 面板内骨架/进度，**不阻塞**控制条 | 抓取慢（豆瓣 750ms 限速）时不能卡住遥控 |

### 8.3 插件分发（D4）

对齐字幕源的双通道，`PluginSourceManager` 照 `SubtitleSourceManager` 写：

| 通道 | 既有样板 | 行为 |
| --- | --- | --- |
| **订阅带入** | `SubtitleSourceManager.install(subscriptionId, json, baseUrl)` → `SubtitleSubscription.parse` → 逐条 `runtimeFactory.create(api)` → 注册/回滚 | 点播订阅 JSON 里可含 `plugins[]`，随订阅激活而安装；订阅更新时整批替换；任一条失败则整批回滚（照抄现有 `destroy(staged)` 语义） |
| **手动添加** | `WebHomeExtensionSourceStore` 的 `file`/`link`/`code` 三态 + `WebHomeExtensionDialog` 编辑器 | 用户可贴代码、填 URL、选本地文件；三种形态共用一套编辑器 |
| 命名空间 | `BaseLoader.slot(namespace, key)`；字幕源用 `namespace + ":" + key` | 插件同样按 `订阅id:插件id` 建键，避免不同订阅的同名插件互相污染 |

**必须保留的字幕源语义**（`SubtitleSourceManager` 已证明有效，直接沿用）：
- `reservedNames` 预留名保护，插件注册不得覆盖内置
- `staged` 暂存 + 失败整批 `destroy` 回滚
- `registry.unregister` 先解注册再销毁，避免悬空引用

---

## 9. 剩余待确认项

无阻塞项。D1–D4 已裁定。

---

## 10. P1 实施记录（2026-10-30）

### 10.1 实施中发现并纠正的设计缺陷

**原 §4.3 方案是错的，已在实施前推翻。**

原计划让 `ProgressLayout.showContent()` 直接派发 `detail.ready`。开工前核查发现该类被广泛复用：

| 复用方 | 处数 | 证据 |
| --- | --- | --- |
| `HomeActivity` | ≥7 | `HomeActivity.java:730,789,1037,1535` 等 |
| `FileActivity` | 1 | `FileActivity.java:82` |
| `HistoryActivity` | 1 | `HistoryActivity.java:99` |
| `KeepActivity` | 1 | `KeepActivity.java:73` |
| `TypeFragment` | 1 | `TypeFragment.java:204` |
| `VideoActivity` | ≥6 | `VideoActivity.java:2450,2948,3036,3061,5673,6046` |

若照原方案，**打开首页就会误报"详情页就绪"**。

**修正**：底层只广播带宿主身份的 `ui.contentShown(activity)`，由订阅者按 Activity 类型自行过滤。好处是既不必改 mobile/leanback 两份 `VideoActivity`，也不会产生语义错误的公共事件。

### 10.2 实际交付

| 文件 | 类型 | 内容 |
| --- | --- | --- |
| `plugin/PluginEventBus.java` | 新建 | 纯 JVM 事件总线；5 个事件常量；`subscribe`/`subscribeAll`/`emit`/`emitActivity`/`hasSubscribers`；订阅方异常被吞掉 |
| `plugin/PluginHostEvents.java` | 新建 | Android 侧翻译层；`activityCreated`/`activityDestroyed`/`appForeground`/`appBackground`/`uiContentShown`；循环解包 `ContextWrapper`（上限 16 层） |
| `App.java` | 改 4 处 | 原来全空的 `onActivityCreated`/`onActivityDestroyed` 接上；`onActivityResumed`/`onActivityPaused` 加前台/后台广播 |
| `ui/custom/ProgressLayout.java` | 改 1 处 | `switchState` 的 `CONTENT` 分支加一行埋点 |
| `test/.../plugin/PluginEventBusTest.java` | 新建 | 7 个用例 |

### 10.3 关键设计决定

1. **总线保持纯 JVM**：不 import 任何 `android.*`，因此单元测试可直接构造，无需 Robolectric。
2. **`hasSubscribers` 前置门**：`emitActivity` 在无订阅者时**不构造 payload**（不调 `Map.of`），这是"空订阅零行为"的机械保证，不是靠约定。
3. **解析失败仍广播**：`uiContentShown` 解不出 Activity 时仍派发，只是不带身份键。宁可让订阅者看到"某处内容显示了"，也不因一次解包失败静默丢事件——后者会让插件行为变得不可解释。
4. **订阅异常单向吞掉**：`dispatch` 捕获 `Throwable` 并计为未投递。插件永远不能反向打断宿主生命周期或视图切换。

### 10.4 验证

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 单元测试（7 用例） | `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.PluginEventBusTest"` | **BUILD SUCCESSFUL**（1m52s） |
| leanback 编译（共享代码两端） | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL**（18s） |

测试覆盖：无订阅零行为 / 身份键传递 / 只收自己的事件 / 抛异常不伤宿主与其他订阅者 / 退订生效 / `subscribeAll` 收全量 / 空参数被拒。

### 10.5 未做（P1 边界内明确排除）

- 无插件加载、无脚本运行时、无 UI 槽位、无审计日志——这些全部属于 P2+
- `WebView` 侧 `fm*` 事件未与 `PluginEventBus` 打通（属 P2/P5）
- `detail.ready` 语义事件未实现：P1 只提供通用的 `ui.contentShown`，细粒度事件按需在 P2 追加

### 10.6 下一步

P2 拆为 P2a / P2b 两个可独立回滚的切片（见 §11）。

---

## 11. P2a 实施记录：策略层（2026-10-30）

**为什么先拆。** 原 P2 同时包含「清单解析 + 注册表 + 审计日志」和「js 运行时 + floating 槽位」。前者零脚本执行、纯策略判定、可完全单测；后者第一次引入真实代码执行。两者风险性质不同，混在一个提交里会让回滚粒度失真——一旦运行时出事，已经验证过的策略层会被迫一起回退。故先交付策略层。

### 11.1 交付

| 文件 | 内容 |
| --- | --- |
| `plugin/PluginCapabilityRegistry.java` | 7 个能力常量；能力→权限→事件映射；`filterPermissions`/`allows`/`allowsEvent`/`eventsFor` |
| `plugin/PluginManifest.java` | 清单解析：`protocol`/`version`/`id`/`sdk`/`runtime`/`events[]`/`ui[]`/`permissions[]`；失败整体返回 `null` |
| `plugin/PluginAuditLog.java` | 环形缓冲 500 条；`recordRequest`/`recordFailure`/`entriesFor`；URL 脱敏 |
| 测试 ×3 | `PluginManifestTest`(15) + `PluginCapabilityRegistryTest`(7) + `PluginAuditLogTest`(8) |

### 11.2 测试抓到的真实缺陷（重要）

首轮 37 个测试，1 个失败：

```
PluginCapabilityRegistryTest > unknownPermissionIsRejected FAILED
java.lang.AssertionError at PluginCapabilityRegistryTest.java:23
```

原实现：

```java
return grantedPermissions != null && permission != null && grantedPermissions.contains(permission);
```

**只查集合包含，不查注册表。** 后果是任意自造能力名只要写进清单 `permissions[]` 就能通过门禁——例如 `"net.unlimited"`，一个本宿主根本不认识的能力。这不是理论问题：D2 已放弃域名白名单，若能力门禁本身也能被自造名绕过，则两层防线同时失守。

**修复**：`allows()` 与 `allowsEvent()` 都以 `isKnownPermission` 为硬前置，注册表之外恒为拒绝。测试未改动——失败的是实现，不是断言。

这件事验证了 P2a 拆分的价值：纯策略层能在不接运行时的情况下，用一行断言暴露一个会随运行时上线而扩散的漏洞。

### 11.3 关键设计决定

1. **清单失败即整体拒绝**：任一字段越界返回 `null`，不做"能读多少读多少"。半加载的插件产生无法解释的行为，比不加载更糟。
2. **未知权限静默丢弃，但全未知则拒绝**：写了一个将来才有的权限不影响安装（向前兼容）；写进去的全是本宿主不认识的，说明清单不兼容，拒绝而非降级成"无权限插件"。
3. **事件按权限反查过滤**：清单声明了事件但缺对应权限时，丢弃该事件而非拒绝整个清单——允许插件在清单里预留"将来想要"的事件名。
4. **UI 槽位独立门禁**：`floating`/`toolbar`/`panel`/`page` 四种槽位分别需要 `ui.floating`/`ui.toolbar`/`ui.panel`/`ui.page`。一个槽位缺权限只丢该槽位，不影响其他。
5. **审计 URL 不用 `java.net.URI`**：畸形 URL 会抛异常，而审计路径恰恰最不能因为一条脏 URL 就丢记录。改为手工切分，永不抛异常，且**丢弃 query 与 fragment**（query 常带 token/签名）。
6. **审计只做内存，不做 IO**：P2a 不引入文件系统副作用，落盘是后续切片的事。

### 11.4 验证

| 验证 | 结果 |
| --- | --- |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **BUILD SUCCESSFUL**，37/37（修复后 16s） |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |

### 11.5 P2a 边界内明确未做

- 无 `PluginRuntimeFactory`、无 js/py 执行、无 UI 槽位渲染
- 审计日志不落盘、无设置页入口
- 清单不含 `leanback` 覆盖块（已在 §4.2 草案中，但 P2a 只解析扁平 `ui[]`；双端覆盖留到 P2b 与实际渲染一起做）

### 11.6 下一步

P2b：运行时抽象 + 宿主路由（见 §12）。

---

## 12. P2b 实施记录：运行时抽象与宿主路由（2026-10-30）

### 12.1 再次拆分的原因

原 P2b = `PluginRuntimeFactory`(js → QuickJS) + floating 渲染 + 审计接入。动手前核实 QuickJS 后发现两个硬约束：

| 约束 | 证据 |
| --- | --- |
| `quickjs.crawler.Loader` 构造即调 `QuickJSLoader.init()` 加载原生 .so | `quickjs/crawler/Loader.java:9-11` |
| `quickjs.crawler.Spider` 继承 `com.github.catvod.crawler.Spider`，方法签名全是爬虫语义（`homeContent`/`categoryContent`/`detailContent`…），**没有事件/动作概念** | `quickjs/crawler/Spider.java:34,66-123` |
| 脚本调用依赖 `Async.run(jsObject, func, args)` + 完整 `spider.js`/`http.js` 运行时 | `quickjs/crawler/Spider.java:56,164-194` |

推论：**QuickJS 适配器既不能在普通 JVM 单测里跑，也不能复用现有 Spider（语义不匹配，需新写一层 JS 运行时）**。若把它和宿主路由混在一个切片，路由逻辑就会变成"只能靠仪器化测试验证"。

故拆为：
- **P2b（本节）**：纯 JVM 的运行时抽象 + 宿主路由 + 故障隔离，可用假运行时完整覆盖
- **P2c**：QuickJS 适配器（新写 JS 运行时，不复用 Spider）+ floating 渲染

### 12.2 交付

| 文件 | 内容 |
| --- | --- |
| `plugin/PluginRuntime.java` | 4 方法接口（init/onEvent/action/destroy），纯 JVM，形状照 `SubtitleScriptRuntime` |
| `plugin/PluginRuntimeFactory.java` | 按 api 分发；可注册；未注册返回 `UnsupportedRuntime`（**不返回 null**） |
| `plugin/PluginHost.java` | 装载/卸载、事件路由、权限门禁、故障隔离、UI 槽位收集、诊断 |
| 测试 ×2 | `PluginHostTest`(15) + `PluginRuntimeFactoryTest`(7) |

### 12.3 三条边界的落地位置

| 边界 | 落地方法 | 测试 |
| --- | --- | --- |
| **权限白名单** | `PluginHost.onEvent` 里 `allowsEvent` + `subscribesTo` 双检 | `eventNotDeclaredIsNeverDelivered`、`eventDeclaredButUnauthorizedIsNeverDelivered` |
| **故障隔离** | `quarantine()`：标记 failed + 记审计 + 立即 destroy，不再重试 | `throwingPluginIsQuarantinedNotPropagated`、`quarantinedPluginStopsReceivingEvents` |
| **可审计** | 所有失败路径都走 `audit.recordFailure`，带 pluginId + version | `failedInitIsNotLoadedAndIsAudited`、`unsupportedRuntimeFailsClosedAndIsAudited` |

### 12.4 关键设计决定

1. **未实现引擎返回会抛错的占位对象，不返回 `null`，也不返回空实现**。若返回空实现，用户会看到"插件装了但没反应"，无法区分是插件写错了还是宿主没实现。现在 `UnsupportedRuntime.init` 直接抛 `UnsupportedOperationException`，走隔离路径并在审计里留下"引擎未实现"，故障归因明确。测试 `unsupportedRuntimeMessageDistinguishesUnknownFromUnimplemented` 钉住这条：已知引擎说 `not implemented`，未知引擎说 `unknown runtime`。

2. **一个插件失败不影响其他插件**。`oneBadPluginDoesNotAffectAnother` 用两个插件验证：bad 被隔离、good 仍正常收事件。

3. **失败即隔离而非重试**。插件是第三方代码，失败通常是确定性的（清单与实现不符、语法错、依赖缺失）。重试只会在主线程制造抖动。隔离到下次显式重载为止。

4. **装载批次语义与字幕源不同**。`SubtitleSourceManager.install` 失败是**整批回滚**（因为字幕源共享初始化流程）；插件之间互相独立，故 `PluginHost.load` 是**逐条装载、单条失败只丢该条**。这个差异是刻意的，已在代码注释里写明。

5. **零插件零订阅**。`attach()` 只在有插件被装载上时订阅总线，`unloadAll` 立即退订。配合 P1 的 `hasSubscribers` 前置门，形成"没装插件 = 零开销"的完整链。

6. **payload 手工拼 JSON，不引入 Gson**。payload 只有 `activity` 一个受控键，而 `ui.contentShown` 是热路径（每次页面切换触发）。JSON 序列化器会在每个事件上分配对象树。同时 `escape` 处理引号/反斜杠/换行/控制字符，防止 Activity 名注入破坏 JSON 结构。

### 12.5 验证

| 验证 | 结果 |
| --- | --- |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **BUILD SUCCESSFUL**，6 suites / **60 tests** / 0 failures |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |

累计测试分布：EventBus 7 + Manifest 15 + CapabilityRegistry 7 + AuditLog 8 + RuntimeFactory 7 + Host 15 = **59**（另有 1 个跨 suite 统计差，以 XML 汇总为准：60）。

### 12.6 P2b 边界内明确未做

- **无任何真实脚本执行**：`js`/`py`/`html` 三个引擎在 `PluginRuntimeFactory` 里都未注册，`describe()` 会如实报告 `not implemented`
- 无 floating/toolbar/panel/page 的**渲染**（`uiSlots()` 只收集声明，不画控件）
- 审计日志不落盘
- 无设置页入口、无订阅解析

### 12.7 下一步

P2c：QuickJS 适配器 + 槽位渲染（见 §13）。

---

## 13. P2c 实施记录：QuickJS 适配器（2026-10-30）

### 13.1 实施中撞到的两个真实约束

**约束一：quickjs 模块不传递 wrapper 依赖。**
`quickjs/build.gradle:25-26` 用 `implementation` 引入 `libs.quickjs.android`/`libs.quickjs.java`，而 `app` 只 `implementation project(':quickjs')`。于是 `app` 编译期看不到 `com.whl.quickjs.*`，首次编译直接报 7 个"程序包不存在"。

三条候选路线：

| 方案 | 代价 |
| --- | --- |
| 适配器放进 quickjs 模块 | `PluginRuntime` 在 app，形成循环依赖 |
| quickjs 改 `api` | 扩大该模块公开面，影响所有未来消费者 |
| **app 显式声明 wrapper**（采纳） | 两行依赖 + 注释说明原因 |

采纳第三种：改动最小、局部、可解释。

**约束二：`QuickJSLoader.init()` 必须显式调用。**
`QuickJSContext.create()` 之前未加载 .so 会 `UnsatisfiedLinkError`。既有 `quickjs.crawler.Loader` 把它放在构造函数（`Loader.java:9-11`）；本实现改为**懒加载 + 幂等**（`ensureNativeLoaded`），让"装了插件功能但从不使用"的用户不白付一次 .so 加载成本。

### 13.2 API 核实（避免凭印象写）

用 `javap` 核对 `quickjs 3.2.3` 真实签名后才落笔，修正了初稿的四处错误：

| 项 | 初稿（错） | 实际 |
| --- | --- | --- |
| 取子对象 | `getJSObject(name)` 猜存在 | **确实存在**，返回 `JSObject` |
| `getGlobalObject` | 以为返回 `Object` | 返回 `JSObject` |
| Promise 处理 | 以为 wrapper 有 then 轮询 | **没有事件循环**，只能同步 |
| Console | 猜四个方法 | `log/info/warn/error(String)`，确认一致 |

另外确认 `wrapper-android-3.2.3.aar` 的 `jni/` 含 **arm64-v8a / armeabi-v7a / x86 / x86_64**，即模拟器可跑。

### 13.3 交付

| 文件 | 内容 |
| --- | --- |
| `plugin/QuickJsPluginRuntime.java` | 每插件一上下文；单线程执行器绑定上下文；5s 调用超时；超时即作废会话 |
| `plugin/PluginScriptSource.java` | 入口解析（`assets://` / `file://` / `/` 开头）；512 KiB 上限；**拒绝远程 URL** |
| `assets/plugin/README.md` | 插件作者契约：同步处理器约束、事件表、清单示例、安全边界 |
| `assets/plugin/example/{manifest.json,hello.js}` | 最小可用示例 |
| `app/build.gradle` | 显式声明 quickjs wrapper（含原因注释） |
| 测试 ×2 | `PluginScriptSourceTest`(10) + `ExamplePluginManifestTest`(4) |

### 13.4 测试抓到的真实缺陷

**缺陷：`Path.read(File)` 对不存在的文件返回 `""`，不是 `null`。**

`catvod/utils/Path.java:135-141` 的实现是 `catch (Exception e) { return ""; }`。而 `PluginScriptSource.read` 初稿只判 `text == null`，于是"文件不存在"被当成"脚本为空"继续往下走，最终在 `init` 阶段才报错——**错误归因会指向插件脚本，而不是入口路径写错了**。

修复：同时拒绝 null 与空串，并在注释里点名 `Path.java:135-141` 这个坑，防止后人"优化"回只判 null。

测试 `rejectsNonExistentFile` + `neverThrowsOnGarbageInput` 钉住了这一点。

### 13.5 两次测试侧纠错（产品代码未改）

| 失败 | 性质 | 处理 |
| --- | --- | --- |
| `readsAssetScript` | `Asset.read` 走 `AssetManager`，JVM 单测无法工作 | 改为临时文件覆盖同一段读逻辑；示例内容由 `ExamplePluginManifestTest` 从文件系统校验 |
| `readsAbsoluteFileScript` | 相同——我用 `C:\...` 盘符路径测"绝对路径"，而 Android 上不存在盘符 | 改为断言**盘符路径必须被拒绝**，因为放宽会削弱反路径穿越 |

第二条值得记录：这不是"为了过测试而改测试"，而是测试写错了平台契约。裸路径只认 `/` 开头是 Android 语义，放宽到"任意非空路径"会让 `../escape.js` 蒙混过关。

### 13.6 诚实的能力边界

**QuickJS wrapper 没有 Promise 事件循环。** 因此引导脚本把返回 Promise 的处理器替换成 `__PROMISE_UNSUPPORTED__` 标记串，README 里明确要求处理器必须同步。

这是能力边界，不是疏漏。异步网络能力（`net.request`）本来就没在 SDK 1 开放，两者一致。等后续切片引入受控异步时，需要自己实现微任务泵，届时再放开 Promise。

### 13.7 验证

| 验证 | 结果 |
| --- | --- |
| `:app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **BUILD SUCCESSFUL**，8 suites / **74 tests** / 0 failures |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| 模拟器可用性 | 5 台 x86_64 在线（`emulator-5554/5556/5558/5560/5562`），含 native bridge，装好的 arm64 包能启动、无崩溃 |

**未做的运行验证（明确声明）**：QuickJS 适配器**尚未在设备上真跑过**。它通过了编译与单元测试，但 `QuickJSLoader.init()` + 真实脚本求值这条链路需要仪器化测试或手动验证才能确认。这是 P2c 的已知缺口，不应被"测试全绿"掩盖。

### 13.8 P2c 边界内明确未做

- **槽位渲染未做**：`uiSlots()` 仍只收集声明，不画任何控件（原计划在本切片，因依赖链修复与 API 核实耗时，留至 P2d）
- `assets://` 路径在 JVM 单测不可覆盖（需仪器化）
- 审计日志未接真实请求（因为没有真实网络能力）
- 无设置页入口、无订阅解析

### 13.9 下一步

P2d：槽位渲染 + 装配层（见 §14）。

---

## 14. P2d 实施记录：槽位渲染与装配层（2026-10-30）

### 14.1 QuickJS 真机验证（关闭 P2c 缺口）

P2c 交付时明确声明「QuickJS 未在设备上真跑过」。本切片先补这一项，**在 emulator-5560（x86_64 / SDK 28）上 `OK (7 tests)`**：

| 测试 | 验证的真实行为 |
| --- | --- |
| `loadsAndDispatchesFromAssets` | `QuickJSLoader.init()` → `QuickJSContext.create()` → 从 assets 读脚本 → 事件派发 |
| `directRuntimeApiWorks` | init/onEvent/action 三方法真实求值 + 上下文释放归零 |
| `actionRoundTripsThroughQuickJs` | 脚本返回值 `'ok'` 真实回传 |
| `brokenScriptIsQuarantinedNotCrashed` | 坏脚本被拒绝、记账、不崩应用 |
| `missingHandlerReturnsNullNotError` | 未注册处理器返回 null 而非故障 |
| `unknownPluginIdIsRejected` / `destroyIsIdempotent` | 边界与重复释放安全 |

### 14.2 交付

| 文件 | 内容 |
| --- | --- |
| `plugin/PluginUiPresenter.java` | `floating` 槽位渲染；挂 decorView；detach 精确移除 |
| `plugin/PluginController.java` | 装配层：工厂 + 宿主 + 渲染器；跟随 Activity 切换；进程级单例 |
| `App.java` | `onActivityResumed`/`onActivityDestroyed` 各加一行 |
| 测试 ×4 | `PluginControllerTest`(8) + `PluginUiPresenterTest`(4) + 两个仪器化测试 |

### 14.3 关键设计决定

1. **`floating` 挂 decorView 而非 `ProgressLayout`。** `ProgressLayout.addOverlayView`（`:224`）语义更贴切，但它只存在于部分页面；插件要覆盖任意页面只能挂 decorView。代价是必须自己处理页面切换清理，由 `detach()` 承担。
2. **电视端 floating 默认不可聚焦。** 悬浮控件不在焦点链里，强行 `focusable` 会在原有导航路径上凭空多一个停靠点，破坏 `PlayerControlFocusHelper` 的方向推断。测试 `leanbackFloatingIsNotFocusable` 钉住这条。电视正式入口留给 `toolbar`（§8.2）。
3. **描边色走 `ThemeController.focusRingColor`，不用 XML drawable。** 描边色来自运行时主题，静态资源拿不到；这与 `docs/THEME-CUSTOMIZATION-LAYERS` 的结论一致——跟随主题的控件必须走代码路径。
4. **装配层独立于 `App`。** `App` 已 30KB+，插件装配（谁订总线、何时渲染、何时清理）是独立关注点；`App` 只保留三行调用。
5. **默认不加载任何插件。** P2d 没有插件来源（订阅解析在 P6），`reload()` 必须由外部显式触发。测试 `emptyReloadMeansNoPluginsAndNoSubscription` 钉住「没有来源 = 完全静默」。

### 14.4 测试抓到的真实缺陷

| 缺陷 | 性质 | 修复 |
| --- | --- | --- |
| `safeOnResumed`/`safeOnDestroyed` 声明为包私有，而 `App` 在父包 | **产品缺陷**，编译失败 | 改为 `public`，注释说明调用方在父包 |
| `setTextColor(cond ? WHITE : WHITE)` 两个分支相同 | **产品缺陷**，无意义代码 | 删掉，改为 `ringBackground()` 提供主题描边 |
| `rootOf` 对非 ViewGroup 父节点强转 | **产品缺陷**，会 `ClassCastException` | 改为 `findViewById(android.R.id.content)` 兜底 |

### 14.5 测试侧纠错

| 失败 | 性质 |
| --- | --- |
| `String.formatted()` → `NoSuchMethodError` | API 30+ 方法，模拟器 SDK 28 没有；改 `String.format` |
| `getDecorView()` 赋给 `ViewGroup` 编译失败 | 返回值是 `View`，需显式强转 |
| `FollowingActivity` 的 `onActivity` 全部 NPE | 该 Activity 缺 Intent 参数时在 `onCreate` 直接 `finish()`（`FollowingActivity.java:114`），改宿主为 `AppBrandingActivity` |

### 14.6 环境障碍与绕过（重要工程记录）

Gradle 的 `connectedAndroidTest` 在本机**不可用**，三条独立原因：

1. **构建缓存锁冲突**：多个 Gradle daemon 并行，`G:\GradleCache\caches\build-cache-1` 被占用 → 用 `--no-build-cache` 绕过。
2. **设备 serial 带尾随空格**：`cmd /c set ANDROID_SERIAL=...` 引入空格，Gradle 报 `Connected device with serial 'emulator-5560 ' not found!`。
3. **多设备编排串扰**：`-Pandroid.injected.deviceSerial` 未生效，测试被派发到全部 5 台模拟器，且结果文件名含空格（`TEST-emulator-5558 - 9-_*.xml`）导致解析失败。

**最终采用的验证路径**：`assembleMobileArm64_v8aDebugAndroidTest` 构建 APK → `adb install` 两个包 → `adb shell am instrument -w -r -e class <FQN> <pkg>/<runner>`。这条路绕开 Gradle 的多设备编排，结果直接可读，且可精确指定单台设备。

### 14.7 验证

| 验证 | 结果 |
| --- | --- |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **BUILD SUCCESSFUL**，10 suites / **86 tests** / 0 failures |
| 仪器化 `QuickJsRuntimeDeviceTest`（emulator-5560） | **OK (7 tests)** |
| 仪器化 `PluginUiPresenterDeviceTest`（emulator-5560） | **OK (4 tests)** |
| `:app:assembleMobileArm64_v8aDebugAndroidTest` | **BUILD SUCCESSFUL** |

### 14.8 P2d 边界内明确未做

- **`toolbar` 槽位未实现**：需要插进 mobile/leanback 两份不同布局（§8.2 已给方案，但工作量大）
- **点击无动作**：`floating` 控件可点击，但点击尚未路由到 `PluginHost.action()`——接线属 P2e
- `assets://` 路径在 JVM 单测仍不可覆盖（需仪器化）
- 审计日志不落盘；无设置页入口；无订阅解析

### 14.9 下一步

**P2e**：`floating` 点击 → `PluginHost.action()` 接线 + `toolbar` 槽位（电视入口）+ 设置页插件开关。


## 15. P2e 实施记录：点击接线与 toolbar 槽位（2026-10-30）

### 15.1 目标

P2d 结束时插件按钮看得见、点得着，但**点击是个死胡同**：`PluginUiPresenter` 的 onClick 只更新内部计数，从不触达运行时。P2e 打通「槽位点击 → `PluginHost.action()` → 脚本」，并把槽位从仅 `floating` 扩到 `toolbar`——后者才是电视遥控够得到的入口。

### 15.2 一个必须先解决的类型缺口

宿主的动作 API 是 `PluginHost.action(String pluginId, String action, String payload)`（`PluginHost.java:135`）——它要靠 `pluginId` 找到对应运行时。但 P2d 的 `uiSlots()` 返回 `List<PluginManifest.UiSlot>`，**槽位本身不知道自己属于哪个插件**。点击时宿主手里只有 `action` 字符串，无法反查。

修复：`PluginHost.uiSlots()` 返回类型改为 `List<PluginHost.PluginSlot>`（`:217-222`），新增

```java
public record PluginSlot(String pluginId, PluginManifest.UiSlot slot) {}   // PluginHost.java:314
```

渲染层随之改为携带 `PluginSlot`，tag 编码为 `webhtv-plugin-host:<pluginId>:<action>`，点击时两个字段一起回传。

### 15.3 挂载点：为什么靠 findViewById + 主题包装

三份 `@style/Control` 的内容**并不相同**，这是本阶段最大的坑：

| 源集 | 行号 | 内容差异 |
| --- | --- | --- |
| `app/src/main/res/values/styles.xml` | :117 | 只有 clickable / focusable / textColor，**无背景、无尺寸** |
| `app/src/leanback/res/values/styles.xml` | :80 | wrap_content、`background=@drawable/selector_chip`、`nextFocusDown=@id/timeBar`、textSize 14sp |
| `app/src/mobile/res/values/styles.xml` | :32 | wrap_content、`background=?attr/selectableItemBackgroundBorderless`、padding 8dp |

而 `@drawable/selector_chip` **只存在于 leanback 源集**。

第一次实现直接在代码里写 `chip.setBackgroundResource(R.drawable.selector_chip)`（PluginUiPresenter.java:177），编译即失败：

```
错误: 找不到符号
  符号:   变量 selector_chip
  位置: 类 drawable
```

**根因不是漏 import，而是把「两端同构」当成了事实。** 修复为用主题包装让各 flavor 取自己那份：

```java
TextView chip = toolbar
        ? new TextView(new ContextThemeWrapper(context, R.style.Control))
        : new TextView(context);
```

这样 leanback 自动拿到 `selector_chip` 焦点背景与 `nextFocusDown`，mobile 拿到 `selectableItemBackgroundBorderless`，不需要在代码里点名任何 drawable。挂载路径 `R.id.control` → `R.id.action` → `R.id.container` 在两份 `activity_video.xml` 与 `view_control_vod_action.xml` 中都成立（mobile :66-67 / leanback :560-561），因此**零布局改动**即可在两端落位。

### 15.4 点击必须离开主线程

插件动作走 JS 运行时，`QuickJsPluginRuntime.CALL_TIMEOUT_MS = 5000` 是**阻塞**调用。跑在主线程最长冻界面 5 秒，与「插件不能拖累宿主」的前提直接冲突。

`PluginController` 因此持有单线程执行器（线程名 `plugin-action`，daemon）：

```java
private void onSlotAction(String pluginId, String action) {   // PluginController.java:67
    Activity activity = current;
    String payload = "{\"activity\":\"" + ... + "\"}";
    actions.execute(() -> { ... host.action(pluginId, action, payload) ... showPreview(...); });
}
```

结果以 Toast 预览（超 160 字符截断）：P4 之前没有豆瓣面板可开，若点击毫无可见反馈，这条链路就无法手工验证。Toast 零布局零资源，是最小代价的接线证据。

mainHandler() 惰性创建并 try/catch 返回 null——JVM 单测下 `Looper.getMainLooper()` 是 android.jar 桩，直接调会抛 `Stub!`。

### 15.5 测试又抓到一个真实缺陷

首轮 90 tests / 1 failed：

```
PluginControllerTest > slotActionForUnknownPluginIsAuditedNotThrown FAILED
    java.lang.AssertionError at PluginControllerTest.java:207
```

断言是「点击一个不存在的插件必须留审计痕迹」。当时 `PluginHost.action()` 对 `plugin == null` 直接 `return null`（原 `:137`），**悄无声息**。

这不是测试苛刻：按钮在界面上还亮着（例如插件刚被卸载），用户点了没反应，日志里也查不到——这是整条链路里唯一完全不可见的失败路径。修复为：

```java
if (plugin == null) {
    audit.recordFailure(pluginId, null, null, action, "blocked", "unknown plugin");
    return null;
}
if (plugin.failed) return null;   // 隔离时已记过审计，不重复刷
```

已隔离的插件不在此列，避免每次点击重复刷同一条审计。

### 15.6 验证

| 验证 | 结果 |
| --- | --- |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **BUILD SUCCESSFUL**，10 suites / **90 tests** / 0 failures |
| 仪器化 `PluginUiPresenterDeviceTest`（emulator-5560） | **OK (7 tests)** |
| 仪器化 `QuickJsRuntimeDeviceTest`（emulator-5560，回归） | **OK (7 tests)** |
| `:app:assembleMobileArm64_v8aDebug` + `assembleMobileArm64_v8aDebugAndroidTest` | **BUILD SUCCESSFUL** |

设备链路仍走 P2d 定下的路径：`assemble → adb install 两包 → am instrument -w -r -e class <FQN>`；Gradle 的 `connectedAndroidTest` 在本机不可用（原因见 §14.6）。

### 15.7 P2e 边界内明确未做

- **设置页插件开关未开始**：`SubtitleSettingsDialog.java` 是字幕源设置页的可对照样板（`input.setText(Setting.getSubtitleSourceEnvironment(...))`），但插件清单的增删改查 + 开关 UI 是一个独立单元，未纳入本阶段提交。
- **插件无来源**：仍然 `reload()` 由外部显式触发，进程启动零自动装载；订阅解析属 P6。
- **审计不落盘**：`PluginAuditLog` 仍是 500 条内存环形。
- **panel / page 槽位未渲染**：`countRenderable()` 只计 floating 与 toolbar。
- **动作返回值只是 Toast**：真正的豆瓣面板要等 P4。

### 15.8 下一步

**P4**：豆瓣评论本体（面板 + 数据源）。在此之前若要先补「能装上第一个插件」的闭环，应先做设置页开关 + 清单导入，即 P2e 的遗留项。


## 16. P2f：插件管理页（清单导入 + 开关）

### 16.1 范围裁定

用户对两个开放点的裁定（原话「Q1=A, Q2=A」）：

- **Q1 = A 骨架**：列表 / 开关 / 整包导入 / 删除 / 重新加载。不含详情页、事件订阅可视化、槽位预览、审计页。理由：审计页与槽位预览此刻没有真实用户数据可看，先落骨架才能当场证实最大的未知——「插件装上来能不能跑」。
- **Q2 = A 导入后默认启用**：插件来源是用户自己手动选的本地文件，再加一道开关的收益低于摩擦成本；风险已由 5 秒调用超时 + quarantine 隔离 + 审计兜底。**注意**：若将来「订阅带入」复用同一导入器，陌生源自动生效应改为默认停用，否则等于让外部源静默获得执行权。

另有两个前置裁定：导入粒度为**整包导入**（选目录 → 复制进私有目录 → 改写 entry 为复制后的绝对路径），管理页形态为**独立整页**（新增 Activity，两 flavor 各一处入口）。

### 16.2 交付物

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginSourceConfig.java` | 324 | 插件来源持久化（Prefers 单键 JSON，仿 `NovelSourceConfig`） |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginPackageImporter.java` | 446 | 整包导入：校验 → 限流 → 复制 → 改写 → 回滚 |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginBootstrap.java` | 67 | 唯一启动接线点：注册运行时 + 按配置装载，永不抛 |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/PluginManagerActivity.java` | 356 | 管理页：列表 / 开关 / 导入 / 删除 / 重载 |
| `app/src/main/res/layout/activity_plugin_manager.xml` | 85 | 页面骨架 |
| `app/src/main/res/layout/adapter_plugin_entry.xml` | 62 | 单条插件行 |
| `app/src/main/java/com/fongmi/android/tv/App.java` | +3 | `Task.execute(PluginBootstrap::installAndReloadQuietly)` |

外加两端设置入口（`SettingEnhanceFragment` / `SettingEnhanceActivity` 各 +7/-1、两处布局各 +26/+28、两处 `AndroidManifest.xml` 各 +5）与 `strings.xml` 三份各 +15。

### 16.3 为什么重载必须离开主线程

`PluginBootstrap.reloadAll()` 会同步执行脚本初始化，`QuickJsPluginRuntime.CALL_TIMEOUT_MS = 5000`，在主线程最长冻界面 5 秒。管理页因此用一条名为 `plugin-manager` 的单线程执行器串行化「重装 / 导入 / 删除」，并用 `synchronized` + `mReloadQueued` 把连点开关合并成一次重装——每次重装都要把所有启用插件的脚本重跑一遍，合并掉的是实打实的重复代价。`App.onCreate` 侧同理，插在 `warmMediaBadgePatterns()` 之后走 `Task.execute`。

### 16.4 三个被修掉的真实缺陷

**（1）`PluginManifest` 用同一个 JSON 键既判协议版本又当插件版本**

`PluginManifest.java:103` 用 `integer(root,"version",-1) != SCHEMA_VERSION` 做协议判定，`:109` 又用 `string(root,"version",...)` 取插件版本，导致 `getVersion()` 恒等于 `"1"`，管理页「版本」列对所有插件显示同一个值。首次单测唯一失败 `reimportSameIdReplacesDirectory` 断言 `version=="v2"` 即此根因。

修法：`version` 只做协议版本判定（原样保留）；新增可选键 `pluginVersion` 承载插件自身版本；`getVersion()` 有合法 `pluginVersion` 用它，否则回退协议版本字符串。注释写明两键不得复用。`assets/plugin/example/manifest.json` **故意不加** `pluginVersion`，留作「旧清单回退」的真实样本。

**（2）`InputStream.readNBytes` 在 Android 13 以下直接崩（本轮最重要）**

`PluginPackageImporter.readText` 原用 `input.readNBytes(MAX_MANIFEST_BYTES + 1)`。真机（emulator-5560，Android 9 / SDK 28）一按导入就崩：

```text
java.lang.NoSuchMethodError: No virtual method readNBytes(I)[B in class Ljava/io/InputStream;
    at ...PluginPackageImporter.readText(PluginPackageImporter.java:376)
    at ...PluginPackageImporter.importFrom(PluginPackageImporter.java:136)
```

`readNBytes(int)` 是 Java 9 API，Android 上要到 **API 33** 才有；`coreLibraryDesugaringEnabled`（`app/build.gradle:137`）补的是 `java.time` / `java.nio.file` 那一批，**不补 `java.io`**。本项目 `minSdk = 24`，所以这不是模拟器怪癖，而是任何 API < 33 的真机都会崩。

**为什么四轮单测全绿都没抓到**：JVM 的 `InputStream` 理所当然有这个办法，JVM 单测永远不会走到这条分支。这是「分段全绿不等于整体绿」的一次具体兑现。

修法：`ByteArrayOutputStream` + `read(byte[8192])` 手写计数循环。**保留超限语义**——读超 `MAX_MANIFEST_BYTES` 立即 `return null`（判为超限失败），而不是留下已读部分静默截断：截断的清单只会变成一个难查的 JSON 解析错误。

**（3）删插件时硬编码根目录名**

`removePackageDir` 原用 `new File(Path.files(), "plugins")` 前缀比对，「插件根目录名」在导入器与管理页各有一份字面量，改名后管理页的越界判断会静默失效（表现为删不掉，而不是删错——但不能靠这个侥幸）。修法：导入器暴露 `public static File pluginsRoot()` 与 `public static boolean isOwnedDirectory(@Nullable String directory)`（`PluginPackageImporter.java:83/:97`，canonical 前缀比较），管理页改为 `if (!PluginPackageImporter.isOwnedDirectory(directory)) return false;`。根目录名此后只有一处定义。

### 16.5 明确保留不动的两个风险

- **设置页新行落在列表最末**（`order` 数组末尾追加），不是紧邻 lab。末尾是「新功能默认落点」，属审美偏好；挪位置会扰动既有用户的肌肉记忆。
- **开关切换仍在主线程写 SharedPreferences**。单键小 JSON 写入是毫秒级；为它加线程切换会把「点开关 → 立刻重装」的顺序关系搞复杂，换不来可感知收益。

### 16.6 端到端验收（本阶段新增的装置）

P2f 之前所有证据都是**分段**的：导入器单测用临时根目录接缝、配置单测用假 Store、QuickJS 仪器化测试直插 factory/host 绕过导入与配置。分段全绿不能证明「用户选一个目录 → 装上来 → 脚本真跑起来」这条链条通——缺陷（2）正是这样活到现在的。

因此新增 `app/src/androidTest/java/com/fongmi/android/tv/plugin/PluginEndToEndDeviceTest.java`（142 行，2 用例），串起全部四段真实链路，不 mock 任何一层：

| 用例 | 覆盖 |
| --- | --- |
| `importedPluginRunsRealScriptEndToEnd` | 真实 `Path.files()` → 真实 `importFrom` → 真实 SharedPreferences 落盘 → `PluginBootstrap.reloadAll()` → 真实 QuickJS → `host.action()` 返回 `pong:{"x":1}`；并断言 `version == "e2e-1"`，把缺陷（1）钉成回归 |
| `disablingPluginDropsItFromTheNextReload` | 关闭开关 → 重装 → `isLoaded` 转 false 且动作返回 `null` |

它同时是 `List.copyOf` / `Files.isSymbolicLink` 走 desugar 通道这一判断的**唯一真机证据**——这两个调用在 SDK 28 上不崩，说明 desugar 覆盖成立。

### 16.7 验证

| 验证 | 结果 |
| --- | --- |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **BUILD SUCCESSFUL**，14 suites / **126 tests** / 0 failures / 0 errors |
| `:app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| 仪器化 `PluginEndToEndDeviceTest`（emulator-5560） | **OK (2 tests)** |
| 仪器化 `QuickJsRuntimeDeviceTest`（回归） | **OK (7 tests)** |
| 仪器化 `PluginUiPresenterDeviceTest`（回归） | **OK (7 tests)** |

126 tests 分布：ExamplePluginManifestTest 4 / PluginAuditLogTest 8 / PluginBootstrapTest 4 / PluginCapabilityRegistryTest 7 / PluginControllerTest 10 / PluginEventBusTest 7 / PluginHostTest 16 / PluginManifestTest 15 / PluginManifestVersionAndRootTest 8 / PluginPackageImporterTest 14 / PluginRuntimeFactoryTest 7 / PluginScriptSourceTest 10 / PluginSourceConfigTest 10 / PluginUiPresenterTest 6。

### 16.8 P2f 边界内明确未做

- **py / html 运行时**：`PluginBootstrap` 只注册 `js`。装 py/html 包会在导入阶段被 `isSupportedApi` 拦下并给出「运行时不支持」的明确文案，不崩。
- **订阅带入通道（D4 的另一半）**：未开始，`PluginPackageImporter` 已可被复用，但默认启用策略需按 §16.1 的提醒改。
- **管理页详情视图**：只有列表行，事件订阅 / 槽位 / 最近审计均未展示。
- **审计不落盘**：`PluginAuditLog` 仍是 500 条内存环形。

### 16.9 下一步

**P4** 原定是豆瓣评论本体（面板 + 数据源）；P2f 之后「能装上第一个插件」的闭环已经闭合，小说与字幕那种「外部文件 → App 内管理」的路径在插件侧首次成立。

> 后续调整：用户裁定 Q1=A，把 P4 切成两片——**P4（本篇 §17）= 插件参数配置 + net.request 地基**，豆瓣本体顺延为下一片。原因是「插件能带自己的密钥发请求」是豆瓣插件的前置条件，先做本体会在宿主侧卡住。

## 17. P4：插件参数配置 + net.request 网络桥

### 17.1 范围裁定

用户在 P2f 收尾后给了一个二选一（Q1/Q2），裁定是 **Q1=A、Q2=A**：

- **Q1=A**：本切片只补「插件参数配置 + net.request」两块**地基**；豆瓣插件本体（episode 注入 + panel 渲染 + douban.js）留到下一片。
- **Q2=A**：通用 JSON 参数透传，宿主**不预设任何第三方字段**；账号 / Token / Cookie 都只是用户自己写的键，宿主只负责替换占位符后注入脚本。

理由：豆瓣评论要跑起来，先得有「插件能带自己的密钥去发请求」这件事；否则写 douban.js 的第一行就卡住，而卡住的地方在宿主而不在豆瓣。反过来先做地基，豆瓣本体就退化成「写一个 js 文件」。

### 17.2 交付物

| 文件 | 职责 |
| --- | --- |
| `plugin/PluginEnvironment.java` | 占位符替换：把 `params` 里的 ${KEY} 换成用户填的值 |
| `plugin/PluginEnvironmentStore.java` | 用户填写的参数文本按插件 id 存取（可注入 Store，便于 JVM 单测） |
| `plugin/PluginConfig.java` | 配置信封：把 {id, params, timeout, permissions} 打包成 init 的 config 字符串 |
| `plugin/PluginNetBridge.java` | 受控 HTTP：`OkHttp.client(timeout)` 手工 execute，拿 status，每次请求落审计 |
| `plugin/PluginManifest.java` | 新增 `params` 字段（`getParams()` 返回深拷贝） |
| `plugin/PluginHost.java` | 新增 `load(manifests, config, resolver)` 三参重载 + `ConfigResolver` 接口 |
| `plugin/PluginController.java` | 新增 `reload(manifests, config, resolver)` 三参重载 |
| `plugin/PluginBootstrap.java` | `reloadAll()` 改为逐插件拼 config（`configOf`） |
| `plugin/QuickJsPluginRuntime.java` | 注入 `webhtv.config` 与 `webhtv.request`（JSCallFunction） |
| `ui/activity/PluginManagerActivity.java` | 列表行新增「参数」按钮 + JSON 编辑对话框 |
| `res/layout/adapter_plugin_entry.xml` | 追加 `@+id/entryParams` 图标按钮 |
| `res/values*/strings.xml` | 参数编辑文案（en / zh-rCN / zh-rTW 各 5 条） |
| `assets/plugin/README.md` | 2718 → 4603 B：新增 §5 参数配置、§6 网络请求，重写 net.request 安全边界条目 |
| `assets/plugin/example/manifest.json` / `hello.js` | 示例带上 params 并读 `webhtv.config` |

新增单测 4 个文件 / 35 用例，真机装置 1 个文件 / 4 用例。

### 17.3 三个被修掉的真实缺陷

**（1）删插件不删参数 → 同名重装会静默继承旧 token。**
`deletePlugin` 原来只做 `config.remove + save + removePackageDir`。用户删掉插件后重装一个同名 id 的插件，`PluginEnvironmentStore` 里那份旧 JSON 还在，新插件第一次 `init` 就拿到了别人的密钥。这不会报错、不会崩，只会在某个请求里带着错凭据——是最难发现的一类。修复：在同一个 try 块里 `config.save()` 之后补 `PluginEnvironmentStore.get().remove(entry.id());`，并在注释里写清「为什么必须跟着删」。

**（2）把「缺权限」当成「运行时故障」会让整个插件下线。**
`PluginNetBridge` 第一版只有一个私有 `failure(...)`，所有失败（含 `net.request` 权限缺失）走同一条路。但回调一旦抛异常，`PluginHost.dispatch` 会把它判为运行时故障并 `quarantine` 整个插件——「插件用了没申请的能力」就这样升级成「插件被禁用」。修复：拆成 `public static String blocked(...)`（记 `blocked` 审计后返回 `{status:0,error:"missing permission net.request"}`）与私有 `error(...)`（只拼 JSON）。能力缺失必须以「这次调用失败」出现，不是「这个插件坏了」。

**（3）真机断言用 `contains` 会在 config 为空串时假绿。**
第一版装置断言 `config.contains("abc123")` 与 `!config.contains("TOKEN")`。如果 `inject` 整段没执行、`config` 是空串，两个断言**都成立**——这正是要抓的那个 bug，却恰好能过。改成 `JsonParser` 逐字段断言（`assertEquals("abc123", parsed.get("token").getAsString())`、`assertEquals(20, parsed.get("limit").getAsInt())`）之后，空串会让 `get("token")` 返回 null 直接崩。**否定式断言在取值可能为空时没有诊断力，必须配一条肯定式断言。**

### 17.4 为什么参数只能整串替换

`SubtitleSourceEnvironment`（`app/src/main/java/com/fongmi/android/tv/subtitle/source/SubtitleSourceEnvironment.java`）只替换**顶层**字符串值，且只在整串就是 ${KEY} 时替换。插件侧 `PluginEnvironment` 保留「整串才替换」这条，但把遍历改成递归进对象与数组——因为插件参数天然是嵌套的：

```json
"params": { "headers": { "Referer": "https://movie.douban.com/" }, "token": "${DOUBAN_TOKEN}" }
```

「整串才替换」这条不能松。若允许半截替换，`"Bearer ${TOKEN}"` 在 TOKEN 漏填时会变成 `"Bearer "` —— 一个语法合法、语义为空的头，请求照发，返回 403，然后人要跑去豆瓣那边找一个不存在的问题。保持原样则漏填的占位符会在日志里明晃晃地露出来，**漏填是看得见的**。

漏填的键替换成空串而不是删掉键：脚本里 `webhtv.config.token` 恒存在（可能是空串），不用写 `in` 判断。

### 17.5 参数存哪里，为什么不放 Setting

字幕源参数放在 `Setting`（`Setting.java:1501-1546`，键 `subtitle_source_environment`）。插件这边**刻意没有沿用**，理由是 `Setting.putSubtitleSourceEnvironment(String sourceKey, String environment)` 这一行：

```java
root.add(sourceKey, JsonParser.parseString(value).getAsJsonObject());
```

它没有判 `isJsonObject()`。用户在字幕源设置里手打一个 `[1,2]` 或半截 JSON，异常会从 `Setting` 里抛出来，一路穿到设置页。字幕源那边能接受（入口是我们自己给的固定 JSON），插件这边不能接受（用户手工编辑，且插件是第三方写的）。

`PluginEnvironmentStore` 因此自己判：非法 JSON / 非对象 / 超 `MAX_ENVIRONMENT_BYTES = 32 * 1024` 一律返回 `false` 且**保留旧值**，编辑框把 `false` 翻成 `plugin_manager_params_too_large` 的错误提示，不打字也崩不了。它同时沿用 `PluginSourceConfig` 的 `interface Store { read; write; }` 可注入形态——单测里没有 `Context`，`Prefers` 不可用，这条形态是这批类能被 JVM 直接测的原因。

### 17.6 为什么 config 是信封而不是裸参数

`PluginRuntime.init(pluginId, entry, config)` 只有一个字符串入口（P2b 定的，接口未改）。但运行时同时需要三样东西：参数、权限集、超时。所以 `PluginConfig.create` 产出：

```json
{ "id": "...", "params": { }, "timeout": 15, "permissions": ["plugin.events", "net.request"] }
```

脚本侧只看得到 `webhtv.config = params`，权限与超时由宿主消费——**脚本无法通过读 config 把自己的权限改大**。

`PluginHost.load(manifests, config, resolver)` 用三参重载而不是改签名：两参签名有 27 处测试调用点（`PluginHostTest` 17 处 / `PluginControllerTest` 10 处），改签名只会换来一堆 `null` 字面量，而语义上「整批共用一份 config」仍然是合法用法（测试就是这么用的）。`resolver` 优先于 `config`，两者都可为 null。

### 17.7 webhtv.request 的三条硬约束

1. **方法白名单**：`GET / POST / PUT / PATCH / DELETE / HEAD`。`TRACE` 之类返回 `{status:0,error:...}` 并记 `blocked` 审计。
2. **必须用 `execute()` 而不是 `OkHttp.string()`**。`OkHttp.string(url, headers)`（`catvod/src/main/java/com/github/catvod/net/OkHttp.java:130-158`）拿不到状态码，而审计的价值就在状态码；它还在失败时 `printStackTrace` 后返回 `""`，把「请求失败」和「响应体是空」压成同一个值。`PluginNetBridge` 用 `OkHttp.client(timeout).newCall(request).execute()` 拿到 `Response`。
3. **永不抛异常**。成功 `{status:<code>, body:"..."}`，失败 `{status:0, error:"..."}`。桥是 `JSCallFunction`，异常会穿过 QuickJS 边界变成脚本侧的 `Error`，而脚本作者写的第一行不该是 `try/catch`。

上限：响应体截断到 512 KiB，请求体 256 KiB，头部最多 32 条 / 每条 1024 字符，超时取清单 `runtime.timeout`（1..60 秒，缺省 15）。审计走 `PluginAuditLog.recordRequest`，只记 host + path —— `target(url)` 本来就把 query 与 fragment 丢掉，因为 query 常带 token 与签名。

### 17.8 验证

| 验证 | 结果 |
| --- | --- |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **BUILD SUCCESSFUL**，18 suites / **165 tests** / 0 failures / 0 errors |
| `:app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| 仪器化 `PluginParamsNetDeviceTest`（emulator-5560 / SDK 28） | **OK (4 tests)** |
| 仪器化 `QuickJsRuntimeDeviceTest`（回归） | **OK (7 tests)** |
| 仪器化 `PluginUiPresenterDeviceTest`（回归） | **OK (7 tests)** |
| 仪器化 `PluginEndToEndDeviceTest`（回归） | **OK (2 tests)** |

165 tests 分布（P2f 为 126，本切片 +39）：ExamplePluginManifestTest 4 / PluginAuditLogTest 8 / PluginBootstrapTest 4 / PluginCapabilityRegistryTest 7 / **PluginConfigTest 6** / PluginControllerTest 10 / **PluginEnvironmentStoreTest 10** / **PluginEnvironmentTest 8** / PluginPackageImporterTest 14 / PluginEventBusTest 7 / PluginHostTest 16 / PluginManifestVersionAndRootTest 8 / PluginRuntimeFactoryTest 7 / PluginScriptSourceTest 10 / **PluginManifestTest 19**（原 15） / **PluginNetBridgeTest 11** / PluginSourceConfigTest 10 / PluginUiPresenterTest 6。

`PluginNetBridgeTest` 用真实 `MockWebServer`（`mockwebserver3` 包，照 `UpstreamRangeClientFetchTest` 的写法）而不是 mock `OkHttp`：要验的正是「拦截器链 + DNS + 超时」这一整条在 JVM 上跑得通，mock 掉就等于不验。

真机装置 `PluginParamsNetDeviceTest` 不引新依赖：用裸 `ServerSocket(0, 127.0.0.1)` 手工拼一次 `HTTP/1.1 200` 应答。四条用例覆盖：占位符被真实替换进脚本、漏填落成空串而非字面量、`webhtv.request` 打到真实 socket、缺 `net.request` 权限时返回错误对象且插件不被禁用。

### 17.9 P4 边界内明确未做

- **豆瓣插件本体**：`episode` 数据注入与 `panel` 槽位渲染都还是空的——`PluginUiPresenter.countRenderable` 仍只计 floating + toolbar，panel/page 拿到手也不渲染。下一片做。
- **`py` / `html` 运行时**：`PluginBootstrap` 只注册 `js`。
- **审计不落盘**：`PluginAuditLog` 仍是 500 条内存环形。D2 裁定「审计是 net.request 唯一的安全观测面」，但它现在还活不过进程重启。
- **参数编辑没有逐键表单**：整块 JSON 文本框，用户得自己写 `{"token":"..."}`。给定 Q2=A 的通用透传裁定，逐键表单要宿主知道字段语义，与「宿主不预设第三方字段」冲突，故不做。
- **管理页不回显替换结果**：用户填完看不到「实际注入到脚本里的 params 长什么样」。

### 17.10 下一步

**豆瓣评论本体**：episode 数据注入（`TmdbItem` → payload）+ `panel` 槽位渲染（`BaseSideSheetDialog`）+ 电视端控制条 chip + 手机端 floating + `douban.js`。地基已经在位：脚本能读配置、能发请求、请求有审计。

## 18. P5：豆瓣评论本体（episode 注入 + panel 渲染）

### 18.1 范围裁定

用户在 P4 收尾后给了三项裁定（Q5/Q6/Q7），全部选 A：

- **D5（panel）= A**：插件返回 HTML，宿主用 `BaseSideSheetDialog` + 原生 WebView 套壳，标题栏与关闭按钮由宿主提供。理由：插件完全掌握呈现，最贴合「插件是外部文件」的原始需求。已知代价：电视端 WebView 里的滚动与焦点需要宿主转发上下键。
- **D6（doubanId）= A**：插件自己搜。宿主只注入 `title` / `year` / `mediaType` 等**通用**字段，`douban.js` 用 `webhtv.request` 打豆瓣搜索接口拿到 subject id 再拉短评。宿主零豆瓣知识，豆瓣改版只影响插件文件——与 §17.1 的 Q2=A「宿主不预设任何第三方字段」是同一条原则。
- **D7（episodeScope）= A**：详情页与播放页都注入。

### 18.2 交付物

| 文件 | 角色 |
| --- | --- |
| `plugin/PluginEpisodeContext.java`（新增，85 行） | 剧集上下文的**单槽**通道：`publish(activityName, context)` / `clear(activityName)` / `current(activityName)` |
| `plugin/PluginEpisodePayload.java`（新增，83 行） | `TmdbItem` + `Vod` + `Episode` → 只读 JSON 的归一化，8 KB 硬上限，零值省略 |
| `ui/dialog/PluginPanelDialog.java`（新增，104 行） | panel 槽位的宿主外壳，继承 `BaseSideSheetDialog` |
| `res/layout/dialog_plugin_panel.xml`（新增，46 行） | 标题 + 关闭 + WebView |
| `plugin/PluginController.java` | `buildActionPayload`（activity + 条件性 context）、`routeSlotResult`（按槽位分流）、`showPanel`、`renderCurrent`/`renderNow` 线程守卫 |
| `plugin/PluginHost.java` | 新增 `grants(pluginId, permission)` 与 `slotOfAction(pluginId, action)` |
| `plugin/PluginUiPresenter.java` | panel 槽位也挂入口控件（与 floating 同形），`countRenderable` 计入 panel |
| `ui/activity/TmdbDetailActivity.java` | `applyTmdbBundle` 末尾发布、`onResume` 回归发布、`resetDetailState` 撤回 |
| `mobile/…/VideoActivity.java`、`leanback/…/VideoActivity.java` | 起播唯一收口发布、`onResume`/`onStart` 回归发布 |
| `assets/plugin/douban/`（新增：manifest.json 19 行 / douban.js 185 行） | 示例插件本体，ES5 写法，自带豆瓣搜索与短评抓取 |
| `assets/plugin/README.md`（153 行） | 新增 §5「槽位与面板渲染」含 5.1 action payload 契约 |
| `res/values*/strings.xml` | `plugin_panel_title`（en / zh-rCN / zh-rTW 三份） |
| 单测 | `PluginEpisodeContextTest`（178 行 / 15 用例）、`PluginEpisodePayloadTest`（184 行 / 13 用例）、`PluginUiPresenterTest` 改断言 |
| 真机装置 | `PluginPanelAndContextDeviceTest`（385 行 / 3 用例） |

### 18.3 关键设计决定

**（1）payload 是"每次点击现算"，不是"事件下发"。**

`PluginEpisodeContext` 只存一份 {`activityName`, json}，`PluginController.buildActionPayload` 在**点击那一刻**用当前 `activity` 的简名去取，名字不匹配就返回 null。这样页面切换不需要任何派发、不需要清缓存、也不可能把 A 页的数据给到 B 页的 chip。

**（2）上下文与槽位权限是两件事。**

`ui.panel` 给的是"能在这一页开面板"，`plugin.data.episode` 给的是"能读剧集数据"。`buildActionPayload` 里 `context` 的附加条件是 `context != null && host.grants(pluginId, PERMISSION_EPISODE_DATA)`，两个条件都必须成立——一个有入口的插件不自动拥有数据。真机用例 `actionPayloadOmitsEpisodeContextWithoutPermission` 钉的就是这条。

**（3）结果怎么呈现由槽位声明决定，不由返回值猜。**

插件只返回一个字符串。`routeSlotResult` 查 `host.slotOfAction(pluginId, action)`：`panel` → `showPanel`，其余 → `showPreview`。同一段 HTML 在 floating 上是提示、在 panel 上是面板，这是**可配置行为**，不是运行时猜测。

**（4）面板只开在"发起点击的那一页"。**

`showPanel` 投递回主线程后重新校验 `origin == current && !origin.isFinishing() && origin instanceof FragmentActivity`。JS 回来最长已过 5 秒，用户完全可能已翻页；把上一页的评论盖到新页面上，正是本项目反复要避免的串台。

**（5）注入点选"起播唯一收口"，不逐点击点。**

全仓 `mViewModel.playerContent(` 的生产调用只有 3 处——`mobile/…/VideoActivity.java`（`getPlayer` 内）、`leanback/…/VideoActivity.java`（`getPlayer` 内）、`leanback/…/VideoActivity.java`（`startPendingFastTmdbPlayback` 内，快播路径**不**经过 `getPlayer`）。在这 3 处之前各发一次，初次进入、点集、自动连播、切线路全部覆盖。

### 18.4 四个被修掉的真实缺陷

**（1）全局单槽 + 返回不重发 = 永久空窗（不是竞态）。**

`PluginEpisodeContext` 是进程级单槽（一个 volatile `Snapshot`，`publish` 整体替换）。详情页发布 → 点集进播放页覆盖 → 按返回回详情页。详情页拉起播放页时**不 finish**（`VideoActivity.startDirect(...)` 三处调用只有 `startDirectResolved` 那条后面紧跟 finish），所以返回时详情页从未销毁、`onCreate`/`applyTmdbBundle` 都不会再跑，而 `current("TmdbDetailActivity")` 因名字不匹配恒为 null。

修复：三个页面各自补"回归发布"——`TmdbDetailActivity.onResume`、`mobile VideoActivity.onResume`、`leanback VideoActivity.onStart`（该类**确实没有 onResume**，全类只有 onStart/onStop）。每处都带"没有数据就返回、不发布"的守卫：`publish(name, null)` 的语义是**清除**，一次无数据的回归发布会擦掉原本正确的快照，比不发布更糟。

**（2）`episode.context` 是死事件。**

`PluginEventBus` 定义了这个常量、`PluginCapabilityRegistry` 把它挂进 `plugin.data.episode` 的事件集、README 写着"宿主切到新剧集条目时派发"——但全仓没有任何 `emit` 调用，插件订阅它永远收不到。

为什么不补接线：真接上会在每次页面切换时**同步阻塞主线程**执行插件 JS（`PluginHost.onEvent` → `QuickJsPluginRuntime` → `session.withLock` → `future.get(5000)`）。本切片不该新增主线程派发点。处置 = 删掉死事件 + 修正文档（现在 README 明确写"业务数据不通过事件下发，只在 `action` 被调用时随 payload 的 `context` 字段提供"）。删干净后 `PluginEventBus.java` 与 `PluginCapabilityRegistry.java` 相对 HEAD byte-identical，不进 commit。

**（3）panel 槽位在后台线程 `addView`（本切片自己引入的回归）。**

HEAD 上 `PluginUiPresenter.attach` 的分支是 `else if (isToolbar || "floating".equalsIgnoreCase(type))` —— panel 被**静默忽略，不碰任何视图**。本切片把 panel 加进这条分支后，它开始走 `addFloating` → `decorView.addView`。而 `PluginController.reload` → `renderCurrent` → `attach` 整段跑在**调用方线程**，生产调用点全在后台线程：`App.java:152` 的 `Task.execute(PluginBootstrap::installAndReloadQuietly)`、`PluginManagerActivity.java:151/:254/:295` 的 `mWorker`。豆瓣插件恰好只声明 panel 一个槽位，于是"用户启用豆瓣"就会在 worker 线程 `addView`，抛出的 `CalledFromWrongThreadException` 被 `reload` 的 `catch (Throwable)` 吞成一条**假的 reload 失败**审计。

**（4）detach 也在后台线程（第 3 项修完之后才现形）。**

第 3 项的第一版只把 `attach` 投递回主线程，`host.size() == 0` 那条 `presenter.detach()` 早退分支仍留在守卫之外。这条分支同样真实可达：用户在插件管理页关掉**最后一个**插件 → `setEnabled` → `scheduleReload` → `mWorker` → `reload` 返回空清单 → `renderCurrent` 里 `current` 非空、`host.size()==0` → `detach()` → `parent.removeView(view)` 在 worker 线程抛异常 → 同样被吞成假 reload 失败。用户看到的是"关掉插件后 chip 还在、开关像没生效"。

修复：守卫提到方法最外层，`attach` 与 `detach` 一起收进只在主线程执行的 `renderNow()`；`renderNow` 内部**重读** `current` 与 `host.size()`（投递到执行之间用户可能已翻页、也可能又把插件启用了）。主线程仍然同步执行——`onActivityResumed` 与真机测试的 `runOnMainSync` 都依赖"调用返回时控件已在"。

**两个被记录但未处理的边界**（都已核实为当前不可达，不是已知缺陷在放行）：

- `onActivityDestroyed` 里的 `presenter.detach()` 同样在调用方线程触碰视图。全仓 `safeOnDestroyed` 只有一个调用方 `App.java:370`，而 `Application.ActivityLifecycleCallbacks` 的回调本身就在主线程，所以当前安全。若将来有人从后台线程调它，会以和（4）完全相同的方式炸在 `removeView` 上。
- `PluginControllerTest` 的 12 处 `reload(...)` 全部在 `current == null` 下跑，从未触达 `attach` 的视图分支——这正是 193 个单测没能提前抓到（3）（4）的原因。补这条覆盖需要真机装置，已由 `PluginPanelAndContextDeviceTest` 承担。

### 18.5 验证

| 验证 | 结果 |
| --- | --- |
| `:app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*"` | **20 suites / 193 tests / 0 failures / 0 errors** |
| 仪器化（emulator-5560 / SDK 28）全量 plugin 包 | **23 / 23 通过**（0 skipped / 0 failed） |

165 → 193 的差额正好是新增的 `PluginEpisodeContextTest` 15 + `PluginEpisodePayloadTest` 13。真机 20 → 23 的差额是新增的 `PluginPanelAndContextDeviceTest` 3 条。

真机 23 条逐类：`PluginEndToEndDeviceTest` 2 / `PluginPanelAndContextDeviceTest` 3 / `PluginParamsNetDeviceTest` 4 / `PluginUiPresenterDeviceTest` 7 / `QuickJsRuntimeDeviceTest` 7。

`PluginPanelAndContextDeviceTest` 的三条**不**断言 `PluginController` 的返回值（动作结果在内部被路由掉了，测试拿不到），而是用 `evaluateJavascript` 读 WebView 的 DOM：`panelSlotOpensSheetWithPluginHtml` 断言 `findFragmentByTag("plugin-panel")` 非空 + 标题等于槽位标签 + DOM 里的 `probe-title` 等于脚本写入的 `PANELMARK`；`actionPayloadCarriesEpisodeContextWhenPermissionGranted` 断言 `probe-title`/`probe-year`/`probe-activity` 三值；`actionPayloadOmitsEpisodeContextWithoutPermission` **先**验 `probe-activity` 等于宿主页名（证明脚本确实跑通、payload 确实到了），**再**验 `probe-title` 为 `NO_CONTEXT`。

装置侧有两条撞出来的真约束，值得记：

1. **`reload` 必须回主线程。** `PluginBootstrap.reloadAll()` 从测试线程调，会走 `attach` → `addView` 被 `catch (Throwable)` 吞成"reload 返回空"。解法是 `runOnMainSync`。既有 `PluginEndToEndDeviceTest` 从未撞到，因为它不声明 `ui` 槽位，`attach` 在触碰视图前就返回了——这恰好是（3）那条缺陷的另一面。
2. **`payload` 到脚本手里是 JSON 字符串，不是对象。** `QuickJsPluginRuntime.dispatch` 以序列化字符串调 `fn.call(key, payload)`，解析是插件自己的责任（与 README §5.1、`example/hello.js` 的既有约定一致）。示例插件开头一律 `try { payload = JSON.parse(raw || '{}'); } catch (e) {}`。

### 18.6 P5 边界内明确未做

- **`py` / `html` 运行时**：`PluginBootstrap` 仍只注册 `js`。
- **审计不落盘**：`PluginAuditLog` 仍是 500 条内存环形，活不过进程重启。而 D2 裁定「审计是 `net.request` 唯一的安全观测面」——这个缺口在设计上成立、在时间上不成立，**已作为交付风险路由给用户**，未在本切片擅自扩容。
- **`net.request` 仍无域名白名单**：插件拿到即能请求任意 host。这是 D2 的既有裁定（"不做白名单，靠审计"），本切片没有改变。
- **电视端 WebView 的上下键转发**：`WebViewUtil.configureHome` 已把 WebView 设为 `focusable`，但面板内的滚动/焦点是否顺手，未在真机上验过遥控器。
- **`mediaType` 不归一化**：`PluginEpisodePayload.mediaType()` 原样透传 `item.getMediaType()`，大写 `"TV"` 或 `"anime"` 之类的非规范值不纠正；缺失时按约定兜底 `"movie"`（`TmdbItem.isTv()` 的定义是 `"tv".equals(mediaType)`，那个值已在上一行按"已声明"返回，所以兜底只可能是 movie）。
- **`page` 槽位仍然静默忽略**：`PluginUiPresenter.attach` 至今没有 page 分支。
- **`onActivityDestroyed` 的 `detach` 未加线程守卫**：理由见 18.4 末尾。

### 18.7 下一步

**插件生态可用性**：`PluginAuditLog` 落盘（D2 的观测面前提）、`net.request` 域名白名单的重新评估、`py`/`html` 运行时、`page` 槽位、电视端面板的遥控器可达性。这些都不是豆瓣专属，属于"插件框架作为长期能力"的欠账。

---

## 19. P6 · 电视端 panel 面板的遥控器可达性

### 19.1 起因：一个只在电视端致命、在手机端永远正确的缺陷

`PluginUiPresenter.attach` 原本把 `panel` 与 `floating` 送进同一条 `addFloating`，而那条路径写的是 `button.setFocusable(!leanback)`。于是：

- 手机端：panel 入口可点、面板可开，一切正常——**所以手机回归永远发现不了**。
- 电视端：入口不在任何焦点链里，遥控器够不到，插件等于不存在。

P5 的真机测试也没抓到，因为它们都走 `PluginController.dispatchSlotAction(...)` 直接注入点击，**绕过了 chip 本身**。「chip 能否被选中」此前无任何测试覆盖。

用户裁定（`leanback-panel-anchor`）：**A —— 有控制条就进 chip，没有控制条才挂可聚焦浮标。** 播放页沿用 `style/Control` 既有焦点语义；详情页 `headerBar` 在电视端是 `View.GONE`（`TmdbDetailActivity.java:901`），没有控制条，只能退到可聚焦浮标——那是唯一够得到的落法。

### 19.2 实现（3 文件、新建 0）

| 文件 | 变更 | 要点 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginUiPresenter.java` | +110/−9 | attach 三分支；新增 `addPanelEntry`(:201-224)/`addFocusableFloating`(:226-244)；`focusLastClicked()`(:131-150)；`ringBackground(Context,boolean)`(:304-317) |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/PluginPanelDialog.java` | +41/−3 | 四参 `show(...,Runnable onClosed)`（两参重载保留）；`onDismiss` 执行收尾(:70-87)；leanback 上 close↔web 显式 `nextFocus`(:113-119) |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginController.java` | +2/−1 | `showPanel` 传 `() -> presenter().focusLastClicked()` |

三态落位（`addPanelEntry`，按「能不能被选中」排序）：

1. `leanback && toolbar != null` → `addToolbarChip`：进控制条，`style/Control` 自带 `focusable=true`、焦点环与 `nextFocusDown` 通路，**不在原有方向键路径上新增停靠点**。
2. `!leanback` → `addFloating(...,false)`：手机端与改前逐字节一致。
3. 其余（leanback 且无控制条）→ `addFocusableFloating`：`setFocusable(true)` + `setFocusableInTouchMode(true)` + `OnFocusChangeListener` 切换焦点环。这是**唯一一处我们主动往电视焦点链里加停靠点**，理由写在方法 javadoc：电视没有触摸，焦点环是「我在哪」的唯一表达，不可聚焦等于入口不存在。

焦点归还链路：`dispatch` 记 `lastClicked`(:279/:290) → `detach` 清空(:127-128) → `focusLastClicked()` → `PluginPanelDialog.onDismiss`。选 `onDismiss` 而非 `onDestroyView`：返回键、关闭按钮、点外侧三条路径最终都走它。`focusLastClicked` 在控件为 null / `getParent()==null` / `!isShown()` 时返回 false 而非抛异常——收尾动作不该制造崩溃。

`ringBackground` 未聚焦分支参数与旧实现完全相同（`0xCC000000` 底 + 1dp 描边 + 20dp 圆角），**手机端观感逐像素不变**；聚焦态加深底色到 `0xF0000000` 并加粗到 2dp。

`floating` 在 leanback 仍不可聚焦的既有行为**一字未动**——真机用例里专门留了一条对照断言守着它。

### 19.3 验证（全部本切片实跑真值）

| 项 | 结果 |
| --- | --- |
| `compileMobileArm64_v8aDebugJavaWithJavac` + `compileLeanbackArm64_v8aDebugJavaWithJavac` | EXIT=0 |
| 两变体 `compile*DebugAndroidTestJavaWithJavac` | EXIT=0 |
| plugin JVM 单测 | **SUITES=20 TESTS=193 FAILURES=0 ERRORS=0**（与 P5 一致，无回归） |
| leanback 真机（emulator-5556） | **26 tests / 0 failed / 0 skipped**；新增 `PluginPanelLeanbackFocusDeviceTest` **3 条全部真跑** |
| mobile 真机（emulator-5560） | 26 tests / 0 failed / **3 skipped**——正好是那 3 条电视专属用例被 `Assume` 跳过，证明门控真实生效 |

真机逐类（leanback）：PluginEndToEnd 2 / PluginPanelAndContext 3 / **PluginPanelLeanbackFocus 3** / PluginParamsNet 4 / PluginUiPresenter 7 / QuickJsRuntime 7 = 26。P5 基线 23 + 新增 3 自洽。

新增用例 `app/src/androidTest/java/com/fongmi/android/tv/plugin/PluginPanelLeanbackFocusDeviceTest.java`（423 行）三条：
1. `panelEntryIsFocusableOnLeanback`(:133)——panel 可聚焦 + `requestFocus` 成功 + 持焦；**并对照断言 floating 在 leanback 仍不可聚焦**。
2. `panelSlotGoesIntoControlBarOnLeanback`(:164)——断言落在**父节点身份**（`parent == R.id.container`）而非「它可聚焦」：控制条里的 chip 与浮标都不可聚焦不行、都可聚焦也不行，差别只在会不会凭空多一个停靠点。
3. `closingPanelReturnsFocusToEntryOnLeanback`(:192)——走 `chip.performClick()` 真实入口（只有真实点击才记 `lastClicked`），等面板出现、宿主窗口失焦、关闭、断言焦点回到 chip。

真实控制条 id 链已核对：`activity_video.xml:560 @+id/control` → `view_control_vod.xml:25 include @+id/action` → `view_control_vod_action.xml:10 @+id/container`（`LinearLayout`），与 `toolbarContainer()` 的 `instanceof LinearLayout` 判定吻合。

### 19.4 本切片修掉的测试侧缺陷（都出在测试自身，未动生产代码）

- `getDecorView()` 静态类型是 `View`，直接调 `addView` 编译失败；先取 `View` 再 `instanceof ViewGroup`。
- 两处时序竞态：对话框事务提交与窗口真正拿到/失去焦点是两步。改 `awaitWindowFocusLost` / `awaitFocused` 有上限轮询（3s / 50ms）。
- `tearDown` 的 `presenter.detach()` 挪到 `runOnMainSync`——`detach` 内部是 `removeView`，与 P5 记录的「reload 必须回主线程」同一类根因。
- 刻意不断言「面板弹出时 `chip.isFocused()==false`」：窗口失焦时焦点视图内部的 focused 标志不清除，那条断言会去证明一件不成立的事。改断言 `activity.hasWindowFocus()==false`。

### 19.5 文档更正

§8.2 交互细节表原写「焦点返回：沿用 `FocusRestorer`（`ui/helper/`）既有模式」——**该文件在仓库中不存在**（`glob app/src/**/ui/helper/*.java` 无此项）。已改为指向本切片的 `PluginUiPresenter.focusLastClicked()` + `PluginPanelDialog.onDismiss`。

### 19.6 P6 边界内明确未做

- **播放态处理未接线。** §8.2:378 承诺「打开面板前 `pauseForOverlayPlayback()`，关闭后 `resumeAfterOverlayPlayback()`」，`PlaybackActivity.java:255` / `:269` 两个 API 确实存在（注意 `resume` 收一个 `boolean` 参数，§8.2 原文写成 `()` 也不准确），但目前**只有 `TmdbVideoPlayerDialog` 在用**（`:91` / `:97` / `:393`），`PluginPanelDialog` 一句都没调。→ 播放中打开豆瓣面板，视频会在面板背后继续播。**已作为范围问题路由给用户决定是否并入下一片**，不在本切片擅自扩容。
- **真电视盒子未验**：只在 arm64/x86_64 模拟器上跑过；焦点环在真实 TV 上的可辨识度无法用断言表达，属人工验收范围。
- **真实播放页控制条里的落位未端到端验**：用例 2 用同构假控制条（`AppBrandingActivity` 没有控制条，而 `FollowingActivity` 缺 Intent 参数会在 `onCreate` 直接 finish）。id 链已静态核对吻合，但不是真宿主。
- **`py`/`html` 运行时、`page` 槽位、审计不落盘**：同 §18.6，本切片未触及。

### 19.7 下一步

1. **（用户已裁定优先）** `PluginAuditLog` 落盘——D2「审计是 `net.request` 唯一安全观测面」的前提。
2. 面板播放态接线（`pauseForOverlayPlayback` / `resumeAfterOverlayPlayback`），待用户裁定是否并入。
3. `net.request` 域名白名单重新评估、`py`/`html` 运行时、`page` 槽位。

## 20. P7：审计落盘与面板播放态接线

本切片兑现 §19.7 的两条：`PluginAuditLog` 落盘（D2「审计是 `net.request` 唯一安全观测面」的前提），以及 §19.6 第一条挂起的「播放态处理未接线」。两件事由两路子代理并行交付，我逐行复核全部 diff 并独立重跑全部验证。

### 20.1 交付物

| 文件 | 行数 | 作用 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginAuditStore.java` | 210（新建） | 审计文件后端：`Sink` 接口 + 文件实现 + 512 KiB / 1000 行轮转 |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginAuditLog.java` | 542（+394/−4） | 懒装载、按行恢复、非主线程写盘（版本号顺序保证） |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheModuleId.java` | `PLUGIN_AUDIT("plugin.audit")`（:15） | 新增缓存模块 |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheModuleRegistry.java` | +18/−2 | 注册模块 + 两处排除前缀 |
| `app/src/main/java/com/fongmi/android/tv/cache/CachePolicyEngine.java` | +6（:44） | 自动清理永不含审计 |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupManager.java` | +18（:374 / :450-464） | 手动清理走 `PluginAuditLog.clear()` |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java` | +1（:702） | 模块名称映射 |
| 三份 `strings.xml` | 各 +1 | `cache_module_plugin_audit`：Plugin audit log / 插件审计日志 / 外掛審計日誌 |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/PluginPanelDialog.java` | 188（+50/−4） | 面板播放态接线 |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginAuditStoreTest.java` | 111（新建，6 用例） | store 的轮转/裁剪/坏行 |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginAuditLogTest.java` | 461（+344，15 用例） | 落盘恢复 + 异步写盘顺序 |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginPanelPlaybackWiringTest.java` | 124（新建，5 用例） | 源码级接线断言 |
| 缓存侧测试三件 | +4 / +26 / +23−1 | 模块数量、家族边界、自动清理排除 |

### 20.2 审计落盘：谁拥有数据

`PluginAuditStore` 只做一件事——把一段文本安全地读回来、写下去，并保证文件不无限长：

- `FILE_NAME = "webhtv-plugin-audit.log"`（:32），`MAX_FILE_BYTES = 512 * 1024`（:40），`MAX_FILE_LINES = PluginAuditLog.MAX_ENTRIES * 2`（:48，即 1000）。
- `interface Sink { String read(); void write(String text); }`（:51-55）——测试注入点，生产实现 `FILE_SINK`（:57-100）。
- 读要**合并两段**：`.1`（轮转出的较旧一段）在前、主文件（较新一段）在后，再按 `tail()` 裁到 `MAX_ENTRIES` 条。
- `write()` 三条路径：空串 → 删两个文件；未超限 → **先删 `.1` 再写主文件**（:89 注释：残留的 `.1` 会在下次读取时把已经裁掉的旧记录复活）；超限 → 先轮转再写主文件。
- `tail(text, maxBytes, maxLines)`（:149-171）**按整行裁**，:146 javadoc 说明理由：从中间截断会把那段 JSON 变成坏行。

`PluginAuditLog` 侧的关键决定是「装载只发生一次，之后内存就是权威副本」：`loadOnce()`（:159-175）先把 `loaded = true` 再读，坏行 `continue`，超限 `removeFirst`。:156 注释写明为什么先置位：读失败时不再重试，否则一条坏文件会让每次 `entries()` / `size()` 都重读一遍磁盘。

落盘后端本身是 `private static volatile PluginAuditStore store`（:75）**惰性**获取（`store()` :404），不在静态初始化里直取 —— :72-73 注明理由：JVM 单测没有 `Init.context()`，`static` 初始化抛异常会变成 `ExceptionInInitializerError` 把整个测试类打死。

### 20.3 主线程不许碰文件（首轮交付被我打回）

首轮实现是「每次 `append` 都在调用线程同步重写整份文件」。审计写入点全集 5 处：`PluginHost.java:105` quarantine、`:164` 未知插件、`:170` 缺槽位权限、`:201` dispatch catch，`PluginController.java:204` reload catch。其中 `PluginHost.onEvent`（:131-142）由 `PluginEventBus` 驱动，而事件派发全在主线程 —— 也就是说**主线程会做一次 ≤512 KiB 的文件同步重写**，正是 StrictMode 磁盘违规与掉帧的典型形态。

返工后的形状（`PluginAuditLog.java`）：

- `persist()`（:232-245）：序列化 + 取版本号，然后 `if (isMainThread()) enqueue(version, text); else writeIfNewer(version, text);`。序列化是纯内存操作，写文件才是磁盘违规，两者拆开后主线程那条路立刻返回。
- 版本号（`nextVersion` :90）**必须与内容在同一个 `lock` 临界区里取**（:236 注释）——否则两个线程可能拿到同一个号，全序就断了。
- 主线程只把**最终快照**放进单槽位（`enqueue` :259-274：`if (version <= writtenVersion) return;` / `if (queuedText != null && queuedVersion > version) return;`）。槽位是覆盖式而不是队列（:262-263）：审计要的是「收敛到内存最新快照」，中间态没有保存价值，排队反而在频繁事件时积压成写放大。
- 写入器是 `Executors.newSingleThreadExecutor` 的守护线程 `plugin-audit-writer`（:358-370，`setDaemon(true)`，进程退出不被一条待写记录拖住），`drain()`（:292-316）一直写到手头没有待写内容才放开 `drainScheduled`。
- **唯一的落盘入口** `writeIfNewer(version, text)`（:318-339）把「版本比对」与 `store().write(text)` 包进同一个 `ioLock` 临界区（:96）——比对通过到写完之间没有别的线程能插进来写一个更旧的版本。加锁顺序固定 `ioLock → writeLock`，无反向嵌套。
- 主线程判定 `isMainThread()`（:341-356）整体 `try/catch(Throwable)` 返回 `false`：JVM 单测里 `android.os.Looper` 是桩，抛 `RuntimeException("Stub!")` 或 `NoClassDefFoundError` 必须被当作「非主线程（同步写）」，而不是被误判成主线程把落盘丢掉。

为什么必须有版本号（:83-86）：主线程把快照交给写入器就返回，网络线程（`PluginNetBridge` 的两次 `recordRequest`，:93 成功 / :102 异常）却仍同步写盘；两者并发时「谁先发起」和「谁先写完」不再同序，没有版本号就可能让一条更旧的快照最后落盘，磁盘上永久留下少了最后几条的日志 —— 而那几条往往正是刚发生的失败。`clear()` 同样拿到更大的版本号，所以「清空之后旧快照被写回来」也被堵住。

新增的测试入口全是包内可见（不扩大对外 API）：`forceAsyncWriterForTest(Boolean)`（:372-380，绕过真实主线程判定，JVM 单测里 `isMainThread()` 恒为假、没有它就测不到异步分支）、`flushForTest()`（:387-402，阻塞到待写内容真正落盘）、以及既有的 `storeForTest` / `reloadForTest`。

### 20.4 缓存集成：可手动清理，绝不自动清

审计文件是 `.log` 后缀，会被 `CacheModuleRegistry.java:34` 的 `TEMP_SUFFIXES = Set.of(".apk", ".zip", ".tmp", ".log")` 当成临时文件删掉 —— 所以它必须**同时**出现在两个排除集合里：`UNCLASSIFIED_EXCLUDED_PREFIXES`（:63）与 `DIAGNOSTIC_LOG_NAMES`（:80）。:73-76 注释写明了这个理由。

模块本体（:115-119）用 `CacheRoot.prefixedFiles(cache, Set.of(PluginAuditStore.FILE_NAME), Set.of())` 做前缀匹配 —— `CacheRoot.java:29-35` 的 javadoc 说明该 API 正是为 `webhtv-debug-log.txt.1` 这类轮转名而设，于是主文件与 `.1` 一起被覆盖。`Cleanability = SAFE_NOW`、`allowAutomaticCleanup = false`、`allowManualCleanup = true`，:115-116 注释给出裁定理由：丢掉审计会静默抹掉 D2 用域名白名单换来的证据链。

`CacheCleanupManager.clearPluginAudit()`（:450-464）调 `PluginAuditLog.get().clear()` 而不是删文件，:453-455 注释说明：只删文件不够，内存副本会被下一次 `append` 写回，用户刚清掉的记录会重新出现。既有 `case PLUGIN_SCRIPTS` 那行一个字未改，`CacheFullCleanupTest.java:206` 的源码断言仍然成立。

### 20.5 面板播放态接线（兑现 §19.6 第一条）

`PluginPanelDialog.java` 现有 188 行，改动后行为：打开面板前 `pauseForOverlayPlayback()`，关闭后 `resumeAfterOverlayPlayback(resumeParent)`，与 `TmdbVideoPlayerDialog.java:91` / `:97` / `:393` 的既有范式一致。

首轮实现把 `PlaybackActivity playback` 与 `resumeParent` 都做成字段，被我打回：`DialogFragment` 在配置变更（旋屏）时由 `FragmentManager` 用无参构造器重建，字段不会重放，新实例此时该做的是用重建后的宿主恢复播放**而不是** `null` 判空跳过；`onDismiss` 里那行 `!activity.isChangingConfigurations()` 本身就是在为旋屏写的，说明这条路径是被认真考虑过的。附带问题：字段持有 Activity 是泄漏。

第二轮的形状：

- `private static final String ARG_RESUME_PARENT = "resumeParent";`（:35）。
- `show()`（:71-88）：`isFinishing()` / `isStateSaved()` 早退（:73-74）→ 局部变量取 `PlaybackActivity playback`（:75，**只给 catch 用**）→ `boolean resumeParent = playback != null && playback.pauseForOverlayPlayback();`（:76）→ `Bundle args` 写入 `ARG_RESUME_PARENT` 后 `setArguments`（:78-80）→ `try { dialog.show(fm, TAG) } catch (RuntimeException e) { if (playback != null) playback.resumeAfterOverlayPlayback(resumeParent); }`（:83-87）。事务提交失败等于面板从未出现，已按下的暂停必须还回去。
- `initView()` 首段回读：`Bundle args = getArguments(); resumeParent = args != null && args.getBoolean(ARG_RESUME_PARENT, false);`（:150-151）。
- `onDismiss()`（:114-123）：`if (!parentResumed) { parentResumed = true; FragmentActivity activity = getActivity(); if (activity != null && !activity.isChangingConfigurations() && activity instanceof PlaybackActivity) { ((PlaybackActivity) activity).resumeAfterOverlayPlayback(resumeParent); } }` —— **用 `getActivity()` 现取**，不在对象上留 Activity 引用（:111 javadoc）。

`PluginPanelPlaybackWiringTest.java`（124 行 5 用例）是源码级断言：读文件、按花括号配平切出方法体，断言 `show`/`initView`/`onDismiss` 三个方法体各自含必须的调用；第 5 条 `resumeIntentSurvivesFragmentRecreation` 直接断言文件里**不再出现** `private PlaybackActivity playback`，并保留 `ARG_RESUME_PARENT` / `setArguments(` / `getArguments()` / `getBoolean(ARG_RESUME_PARENT, false)` / `getActivity()`。旋屏后 `resumeParent` 仍为 true 这件事只能靠代码推理 —— 真机旋屏重建属 androidTest 范围，本切片未做，明确记录在此。

### 20.6 验证

| 项 | 命令/装置 | 结果 |
| --- | --- | --- |
| 两变体编译 | `compileMobileArm64_v8aDebugJavaWithJavac` + `compileLeanbackArm64_v8aDebugJavaWithJavac` | EXITCODE=0 |
| JVM 单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.plugin.*' --tests 'com.fongmi.android.tv.cache.*'` | **36 suites / 285 tests / 0 failures / 0 errors / 1 skipped** |
| mobile 真机 | `connectedMobileArm64_v8aDebugAndroidTest`（emulator-5560） | **26 tests / 0 failed / 3 skipped**，BUILD SUCCESSFUL 1m31s |
| leanback 真机 | `connectedLeanbackArm64_v8aDebugAndroidTest`（emulator-5556） | **26 tests / 0 failed / 0 skipped**，BUILD SUCCESSFUL 1m42s |

逐类真机分布（两端一致）：`PluginEndToEndDeviceTest` 2 / `PluginPanelAndContextDeviceTest` 3 / `PluginPanelLeanbackFocusDeviceTest` 3 / `PluginParamsNetDeviceTest` 4 / `PluginUiPresenterDeviceTest` 7 / `QuickJsRuntimeDeviceTest` 7。mobile 那 3 条 skip 全是 `PluginPanelLeanbackFocusDeviceTest` 的 `Assume.assumeTrue(Util.isLeanback())` 门控。唯一 JVM skip 是既有 `CachePathSafetyTest.finalComponentSymlinkIsStillDetected`（Windows 无符号链接权限）。

与 P6 基线的差额自洽：单测从 193 增至 285（`PluginAuditLogTest` 12→15、新建 `PluginAuditStoreTest` 6、新建 `PluginPanelPlaybackWiringTest` 5、缓存侧 `CachePolicyEngineTest` 12 / `CacheModuleRegistryTest` 8 / `CacheAutoPlanAndTempFamilyTest` 6 各扩容）；真机两端口径不变 —— 本切片未新增 device 测试。

### 20.7 本切片修掉的测试侧缺陷

- **`PluginPanelAndContextDeviceTest.actionPayloadCarriesEpisodeContextWhenPermissionGranted` 偶发超时一次**（首跑 115.4s，诊断串 `TIMEOUT html=null audit=[] url=about:blank`；随后单跑该类 3/3 绿、全量 26 条又全绿）。定性为**测试自身的轮询过密**：`awaitWebText` 每 50ms 灌一次 `evaluateJavascript`，20 秒窗口最多 400 次 eval，页面还在 `loadDataWithBaseURL` 加载时足以压住渲染进程让回调不再回来。加固三处（只改测试文件，+76/−6）：等 `document.readyState == "complete"` 再读（`READY_WAIT_MS = 20_000L`）、DOM 轮询单独降到 `TEXT_POLL_MS = 250L`（查视图的 `sleep()` 仍 50ms，它不碰 WebView）、超时先 `retryDispatch()`（必须**先 dismiss 再重开**：`PluginPanelDialog.show` 用固定 TAG 提交事务，旧实例还在时再 add 同 tag 会撞在 FragmentManager 上）再读一次。断言一条没放宽。
- `retryDispatch()` 的重开路径还顺带给了这条链路一个高保真复现：用户遇到同样情形就是关掉面板再点一次。

### 20.8 明确未做 / 已知风险

- **进程被杀时队列里最后一版可能来不及落盘**（主线程那一路是「记账返回 ≠ 已落盘」）。非主线程（网络请求、JS 执行器）仍同步写，不丢。写入器是 daemon，不会拖住进程退出。
- **512 KiB 轮转未在真机文件系统上验证**，只在 JVM 单测用 `Sink` 验证了裁剪/轮转/坏行恢复。真进程重启后的恢复只走了 `reloadForTest()` 的等价路径。
- **面板不检查 FragmentManager 里是否已有 `TAG = "plugin-panel"` 实例**（`TmdbVideoPlayerDialog.show` 有 `findFragmentByTag` 复用分支）→ chip 连点两次会叠两块面板。播放态不受影响（第二次 `pauseForOverlayPlayback()` 因已暂停返回 `false`，其 `resumeParent = false`）。这是 P5 起就存在的既有缺陷，非本切片引入。
- **面板不订阅宿主生命周期**：`PlaybackActivity` 以非配置变更方式销毁时恢复会被跳过，依赖播放服务自身策略。
- 未做：`py`/`html` 运行时、`page` 槽位（仍静默忽略）、`net.request` 域名白名单（D2 既有裁定）、真电视盒子焦点环人工验收、面板内上下键的实际滚动验收。

### 20.9 下一步

1. `net.request` 域名白名单重新评估 —— 审计已落盘，D2 当初的取舍前提（「没有观测面」）已经改变，值得重新算一次账。
2. `py` / `html` 运行时（`PluginRuntimeFactory` 目前只造 JS）与 `page` 槽位。
3. 面板 chip 连点叠窗口的复用一个（`findFragmentByTag` 分支）。
4. 电视端面板遥控器可达性的**人工**验收：焦点环可辨识度、WebView 内滚动。
## 21. P8：内置样本自动登记

§17.9 起一直挂着一条没有人问出口的缺口：仓库里有两个示例插件（`assets/plugin/example`、`assets/plugin/douban`），但它们**从未出现在任何设备的插件列表里**。用户装上 APK、进详情页，看不到「豆瓣」入口，只能自己去插件管理页手动导入一个目录。本切片的完成句是：全新安装后不做任何操作，详情页就能看到「豆瓣」入口并且能用。

### 21.1 为什么必须新写一个类，而不是让导入器接受 `assets://`

最省事的想法是放宽 `PluginPackageImporter`，让它认识资产路径。取证否掉了这条路：

- `PluginPackageImporter.resolveEntry`（`app/src/main/java/com/fongmi/android/tv/plugin/PluginPackageImporter.java:252-268`）里 `:257` 是 `if (entry.contains("://")) throw new IllegalArgumentException("入口不支持协议前缀：" + rawEntry);`。这不是疏漏而是边界：导入器把用户选的目录**复制进** `files/plugins/<id>`，清单里的入口必须是包内相对路径，写成 `assets://` 的设备上永远读不到。两个示例清单的 `runtime.entry` 恰恰都是 `assets://plugin/douban/douban.js` 这种形态（`app/src/main/assets/plugin/douban/manifest.json:7`）。
- 换个方向（内置登记只写配置、不复制文件）也不行：`PluginSourceConfig.toManifests()`（`:201-216`）是 `Path.read(new File(entry.directory(), MANIFEST_NAME))`，直接把 `assets://` 当目录拼路径。
- 于是唯一自洽的形状是：**复制一次 + 改写入口**，与导入器落盘后的结果保持同一形态。这正是 `PluginSampleInstaller` 做的事（250 行），而导入器一行未改。

### 21.2 交付物

| 文件 | 行数 | 作用 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginSampleInstaller.java` | 250（新建） | 首次启动把内置样本复制进 `files/plugins/<id>`、改写入口、登记 |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginBootstrap.java` | +9/−1 | `installAndReloadQuietly()` 首行接 `seedOnce()` |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginController.java` | +34/−2 | `detachOnMainThread()`：`reset()` / `onActivityDestroyed()` 的视图摘除回主线程（§21.5） |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginSampleInstallerTest.java` | 227（新建，6 用例） | 首次登记 / 二次跳过 / 用户删除不复活 / 已登记不覆盖 / 空资产 / 坏清单 |
| `app/src/androidTest/java/com/fongmi/android/tv/plugin/PluginSampleInstallerDeviceTest.java` | 127（新建，2 用例） | 真机：真资产能读、能改写成绝对路径、能被装载成 host 里的槽位 |
| `app/src/androidTest/java/com/fongmi/android/tv/plugin/PluginUiPresenterDeviceTest.java` | +25/−3 | `tearDown` 的 `detach` 回主线程（§21.5） |

### 21.3 记账存修订号，不存布尔值

`KEY_SEEDED = "plugin_sample_seed_revision"`（`PluginSampleInstaller.java:42`），`SEED_REVISION = 1`（:51）。存 `int` 而不是 `boolean` 的理由写在同一行 javadoc：将来换样本／加样本时把常量 +1，老设备会**再走一次**登记；布尔值一旦写死，已经装过的设备永远拿不到新样本，而这类设备无法通过「重装」修复。

`seedOnce()`（:116-142）的形状是「全成或全不成」：`revision >= SEED_REVISION` 直接返 0；否则逐条 `seed()`，只要有一条返回 −1 就把 `settled` 置假，**不记账**。代价是下次启动会把成功的那些再算一遍——这没有害处，因为 `seed()` 的第二道闸是 `config.find(id) != null` 就返回 0，重复执行天然幂等。

### 21.4 两条失败语义的分界（首跑 1 红，裁定是改测试不改实现）

`seed()`（:149-190）返回三态：`1` 本次新登记 / `0` 无需处理 / `−1` 失败。分界线不是「有没有出错」，而是**下次启动重试有没有意义**：

- 清单读成空串 → `0`。`Asset.read`（`catvod/src/main/java/com/github/catvod/utils/Asset.java:17-23`）对缺失文件返回空串，资产内容又随版本固定，重试永远是同一个空串。这里记 `0` 而不是 `−1`，否则样本一旦被裁掉，每次启动都要白读一遍资产并永远不记账。
- 清单非法、id 不安全、脚本空或超过 `PluginScriptSource.MAX_SCRIPT_BYTES`（512 KiB）、`mkdirs` / 写文件 / 改写失败 → `−1`，不记账，下次还有机会。整体再套 `catch (Throwable)` 返 `−1`（:187-189）。
- 已登记（`config.find(id) != null`）→ `0`，**一行都不覆盖**（:162）。这一条覆盖两种真实情形：用户自己导入过同 id 的插件，或者上一条样本刚种下。

首跑真的红过一次，而且红得有信息量：`missingAssetKeepsTheRevisionUnadvanced` 断言 `assertEquals(0, store.revision())` 但拿到 `1`。**裁定是改测试、不改实现**——把用例改名为 `missingAssetIsSettledInsteadOfRetriedForever`、断言改成 `assertEquals(PluginSampleInstaller.SEED_REVISION, store.revision())`，并用同一口径重写 :152-154 的注释。理由是上面那条：资产读空分不出「样本不在本包里（永久）」与「读失败（瞬时）」，而前者是唯一会发生的形态。

### 21.5 一个被样本照出来的既有缺陷：`reset()` 在后台线程摘视图

本切片没有改任何渲染代码，但 leanback 真机全量跑出了一个**稳定崩溃**，把它连带修了。故障形态很迷惑：`PluginPanelLeanbackFocusDeviceTest#closingPanelReturnsFocusToEntryOnLeanback` 单跑绿、整类跑必红、单选这一个方法又绿；失败节点是空的（进程被 instrumentation 中断），`logcat -b crash` 里只有两条与插件无关的 `DecorView` NPE（`ViewParent.getLayoutDirection()` on null / `View.getVisibility()` on null）。真凶藏在测试 XML 的 failure 里：

```
android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
  at android.view.ViewGroup.removeView(ViewGroup.java:5294)
  at com.fongmi.android.tv.plugin.PluginUiPresenter.detach(PluginUiPresenter.java:123)
  at com.fongmi.android.tv.plugin.PluginController.reset(PluginController.java:326)
  at com.fongmi.android.tv.plugin.PluginPanelLeanbackFocusDeviceTest.tearDown(PluginPanelLeanbackFocusDeviceTest.java:121)
```

崩在 `tearDown`，所以只跑完 7 条就中断；单跑那个方法绿，是因为前面用例没留下已 attach 的 chip 时 `detach` 里的 `removeView` 一次都没真执行。两条 `DecorView` NPE 是它的**真机表现**而不是原因：错误线程 `removeView` 先改了 `ViewGroup.mChildren`，随后在 `requestLayout` 被 `ViewRootImpl.checkThread` 拦下，半成品留在树里，主线程下一次 measure/layout 就踩到 `null`。

「attach 与 detach 都必须回主线程」这条不变量本来就写在 `PluginController.java` 的 javadoc 里，`renderCurrent()` 也守住了，但 `reset()` 与 `onActivityDestroyed()` 两处漏了。修法是新增 `detachOnMainThread()`（`PluginController.java:268-298`）：主线程直接调，否则 `handler.post(target::detach)`；**取不到 Handler 时宁可放弃**（控件留在将销毁的页面上）也不退化到错误线程（把半成品写进存活树）。投递捕获的是 `final` 局部 `target` 而不是字段或 `this`，因为 `reset()` 随后就把单例置空了。

这也是纯 JVM 单测抓不到它的原因：单测里 `Looper` 是桩、`mainHandler()` 必返 `null`，走的正是那条「放弃」分支。同类缺陷在测试侧还有一处——`PluginUiPresenterDeviceTest.tearDown` 也直接调 `presenter.detach()`，本轮一并改成 `runOnMainSync`（此前能过只是时机凑巧，是竞态不是安全）。

### 21.6 验证（全部本切片实跑真值）

| 项 | 命令/装置 | 结果 |
| --- | --- | --- |
| 两变体编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac` + `compileLeanbackArm64_v8aDebugJavaWithJavac` | EXIT=0 |
| JVM 单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.plugin.*'` | 23 suites / **217 tests** / 0 failures / 0 errors / 0 skipped（含本切片新增 `PluginSampleInstallerTest` n=6） |
| 真机 leanback | emulator-5556，`:app:connectedLeanbackArm64_v8aDebugAndroidTest`，package 过滤 | **28 tests / 0 failures / 0 errors / 0 skipped** |
| 真机 mobile | emulator-5560，同命令 mobile 变体 | **28 tests / 0 failures / 0 errors / 3 skipped**（3 个 skip 全是 `PluginPanelLeanbackFocusDeviceTest` 的 `Assume.assumeTrue(Util.isLeanback())` 门控） |
| 真机端到端 | `adb install` 后首启，`run-as` 查看落盘 | `files/plugins/douban.comments/{manifest.json, douban.js}`；`shared_prefs` 里 `plugin_sample_seed_revision = 1`，`plugin_source_config` 含 `douban.comments`、`enabled=true`；首页右下出现「豆瓣」浮标 |

真机落盘的清单实际内容（`run-as cat` 原文，入口已被改写成绝对路径）：

```json
{"protocol":"webhtv-plugin","version":1,"id":"douban.comments","name":"豆瓣短评","sdk":1,
"runtime":{"api":"js","entry":"/data/user/0/com.silent.android.webhtv/files/plugins/douban.comments/douban.js","timeout":20},
"params":{"count":20},"events":["ui.contentShown"],
"ui":[{"slot":"panel","label":"豆瓣","action":"douban.comments","event":"ui.contentShown"}],
"permissions":["plugin.events","plugin.data.episode","net.request","ui.panel"]}
```

逐类分布（两端一致）：`PluginEndToEndDeviceTest` 2 / `PluginPanelAndContextDeviceTest` 3 / `PluginPanelLeanbackFocusDeviceTest` 3（mobile 上 skip 3）/ `PluginParamsNetDeviceTest` 4 / `PluginSampleInstallerDeviceTest` 2 / `PluginUiPresenterDeviceTest` 7 / `QuickJsRuntimeDeviceTest` 7。

### 21.7 三处后端抽象都为「JVM 单测能不能跑」而存在

`Store`（:61-66）与 `Source`（:74-77）都是包内可见接口，真机实现分别是 `Prefers.getInt/put`（:79-99，读写各套 `catch (Throwable)`）与 `Asset::read`（:109）。理由与 P7 的 `PluginAuditStore.Sink` 同源：`Prefers` 与 `Asset` 都依赖 `Init.context()`，JVM 单测里没有 `Context`。第三处是既有的 `PluginPackageImporter.overrideRootForTest`，测试把 `pluginsRoot` 指到 tempdir。`useBackendsForTest(Store, Source)`（:246-249）传 `null` 恢复真机实现，不扩大公开面。

### 21.8 接线点为什么在 `PluginBootstrap` 而不在 `App`

`PluginBootstrap.installAndReloadQuietly()` 有四条调用路径：`App.java:152`（`Task.execute`，后台线程）与 `PluginManagerActivity.java:151/:254/:295`（`mWorker` 单线程执行器）。放在 `App` 里会漏掉后三条——用户在管理页点「重新加载」时样本不会被补种。放进 `installAndReloadQuietly` 首行则四处一致，多出的代价只是两次调用之间一次 `Prefers.getInt`。

### 21.9 已知边界

- **升级不覆盖用户改动**：`config.find(id) != null` 就返回 0，包括用户改过的样本。想让老设备换新样本，把 `SEED_REVISION` 加一并接受「已存在就不动」这个更强的前置条件。
- **只登记 `douban`，不登记 `example`**（`SAMPLES` :53）。`example` 是 `floating` 槽的最小样本，会给每个用户多挂一个没有用途的浮标。
- **样本落在 `files/plugins/<id>`，与手动导入同位置**：用户手动删除目录并移除登记后不会被复活（`sampleDeletedByTheUserIsNotResurrected` 用例锁住了这条），但**只有删目录不删登记**时，下次启动会因为 `find(id) != null` 而跳过，插件表现为「列表里有、加载不出」。
- **`panel` 槽位在所有页面都渲染**是既有规则（P8 未改），所以「豆瓣」浮标在首页也出现，而不只在详情页/播放页。这一点在手机端是显眼的，见 §21.10 第 1 条。
- 真机端到端只做了「装得上、落得盘、挂得出控件」，**没有**在真机上点开豆瓣面板验内容（面板内容的真机验证在 §18.5 已做过，用的是探针插件）。

### 21.10 下一步

1. `panel` 槽位的显示条件：目前凡声明 `panel` 的插件在每个页面都渲染入口，而真正有意义的只有详情页与播放页（`plugin.data.episode` 的上下文也只在这两处存在）。要么按页面类型过滤，要么让清单声明适用页面。
2. §20.9 遗留四条仍然有效：`net.request` 域名白名单重新评估、`py`/`html` 运行时与 `page` 槽位、面板 chip 连点叠窗口的复用（`findFragmentByTag` 分支）、电视端面板遥控器可达性人工验收。

## 22. P9：槽位按页面绑定 + chip 拖动记忆

### 22.1 需求与三条裁定

用户原话（m05093）：

> 能否支持拖动未知，还有插件的显示最好是能绑定页面比如这个豆瓣插件只有详情页和播放页生效那么默认应该就只在详情页和播放页才显示首页等地方不应该出来

这是两件事：**(A)** chip 可以被拖动；**(B)** 槽位能声明它只在哪些页面生效。三个决策点按 G3 提问、用户全部选了推荐项：

| 编号 | 决策 | 裁定 | 理由与已知代价 |
| --- | --- | --- | --- |
| R1 | surfaces 怎么声明 | **清单新增 `surfaces` 字段，不写＝全页显示** | 值域 `home` / `detail` / `playback` / `other`，由宿主按 Activity 类型推导；douban 显式写 `["detail","playback"]`。代价：插件作者多一个可选字段要理解 |
| R2 | 拖动做到什么程度 | **手机端可拖 + 松手贴边吸附 + 按插件记忆** | 位移超过 touchSlop 才算拖动，否则仍是一次点击；落点存 `Prefers`，跨页面、跨重启保持。**电视端不拖**——遥控器没有拖动语义，硬加会破坏 P6 建立的焦点环契约 |
| R3 | 已种下的旧样本怎么办 | **自动刷新宿主自己种的，用户手动导入的包不碰** | `SEED_REVISION` 1→2，让存量设备跟上清单新增的 `surfaces`。代价：用户手工改过内置样本的脚本会被覆盖 |

### 22.2 交付物表

| 文件 | 行数 | 变化 | 内容 |
| --- | --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginSurface.java` | 88 | 新建 | 页面类型推导 `of(Activity)` + 声明比对 `matches(Set,String)` |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginUiPresenter.java` | 655 | +308/−1 | 按页面过滤 + 拖动/吸附/记忆 |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginManifest.java` | 379 | +49/−2 | `UiSlot` 新增 `surfaces` 分量 + 5 参便捷构造器 |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginSampleInstaller.java` | 304 | +64/−10 | `SEED_REVISION`=2、刷新分支、不复活判定 |
| `app/src/main/assets/plugin/douban/manifest.json` | 20 | +2/−1 | `surfaces:["detail","playback"]` |
| `app/src/main/assets/plugin/README.md` | 175 | +22 | §5 新增 surfaces 三行表 + 上限说明 + 示例 |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginUiPresenterTest.java` | 124 | +49 | 6 条页面判定用例 |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginManifestTest.java` | 249 | +50 | 4 条 surfaces 解析用例 |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginSampleInstallerTest.java` | 340 | +116/−3 | 6 条升级/不复活用例 |
| `app/src/androidTest/java/com/fongmi/android/tv/plugin/PluginUiPresenterDragAndSurfaceDeviceTest.java` | 400 | 新建 | 4 条真机用例（3 条页面判定 + 1 条拖动记忆） |

### 22.3 页面绑定：为什么由宿主推导页面类型

`PluginSurface.of(Activity)` 只读类的形状，不碰 `Context`，判定顺序**不能换**：

```
name.contains("Detail")           -> detail    // 必须先判
activity instanceof PlaybackActivity -> playback // 后判
name.startsWith("Home")           -> home
其余                                -> other
```

顺序就是正确性：`TmdbDetailActivity extends PlaybackActivity`（`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java:284`），先判 `instanceof PlaybackActivity` 会把所有详情页吞成 playback，豆瓣在详情页的声明就永久失配。类名判据用的是 `getSimpleName()` 含 `Detail`，因此 `TmdbDetailActivity` 与 `WebThemeDetailActivity`（`extends BaseActivity`）都归 detail。

让插件自己上报页面名会被拒绝：插件看不到宿主页面栈，上报值必然靠猜，猜错的表现是「插件在某些页面突然消失」，比多一个可选字段难查得多。

`matches` 的两条兜底是这一层的命门：

- `surfaces == null || surfaces.isEmpty()` → `true`。不写字段的旧插件必须继续全页显示，否则 P8 之前导入的所有包会集体消失。
- 匹配过程抛异常 → **也返回 true**。宁可多一个入口，也不因为一次异常让插件静默消失。

过滤点在 `attach` 里、`decorOf` 与 `toolbarContainer` 之前：被过滤掉的槽位连控件都不建，因此 `attachedCount()` 天然不计入（真机用例 `declaredSurfaceSlotIsSkippedOnTheCurrentPage` 正是这么断言的）。`countRenderable` 一个字未改——它数的是「这个槽位类型能不能渲染」，和「当前页面该不该显示」是两件事。

清单侧不做白名单校验：宿主不认识的值（如插件自己发明的 `"player"`）天然永不匹配，未来宿主扩展值域时也不必回头改校验表。上限 `MAX_SURFACES=8`、`MAX_SURFACE_LENGTH=16`，解析时统一 `toLowerCase(Locale.ROOT)` 去重。

### 22.4 拖动：为什么落在 PluginUiPresenter 而不是造新控件

拖动状态挂在控件自己的 `OnTouchListener` 上，用一次性 `DragState` 承载一次手势的中间量，不进类字段——`detach()` 之后没有任何残留状态需要清理。

三个容易写错的地方，都有注释钉住：

1. **`setOnClickListener` 不能置 null**。拖动实现里重设监听而不是清空监听，因为清空之后 chip 拖过一次就再也打不开面板。改成 `setOnClickListener(view -> { if (drag.consumeClick()) return; dispatch(entry, view); })`，`consumeClick()` 读完即复位，所以「拖完立刻点」仍然能开面板。
2. **`setLayoutParams` 不会同步刷新 `view.getLeft()`**。`applyDrag` 末尾必须自己把 `drag.lastLeft`/`drag.lastTop` 记成新 margin，否则抬手时 `snap` 读到的是上一次绘制位置，吸附方向会算反。
3. **父容器会抢触摸**。越过 touchSlop 后立刻 `requestDisallowInterceptTouchEvent(true)`，抬手或取消时再放回去，包 try/catch。

吸附只看横向半边：`boolean atEnd = (drag.lastLeft + chipWidth / 2) * 2 >= parentWidth;`——中心过了中线就贴右，否则贴左。纵向保留比例、clamp 到 `[VERTICAL_INSET_DP, parentHeight - inset]`。写入格式 `("start"|"end") + ":" + String.format(Locale.US, "%.4f", center / parentHeight)`，键是 `plugin_chip_pos:<pluginId>:<action>`——同一插件不同槽位各自记位置，不互相覆盖。

`readPosition`/`writePosition` 都包 `catch(Throwable)`：`Prefers` 只 catch `Exception`，而纯 JVM 单测里没有 `Init.context()`，静态初始化抛的是 `Error`。这是 P8 已经踩过的坑，这里直接按 Throwable 兜。

电视端（`leanback == true`）整段拖动代码不执行：`if (!leanback) { restorePosition(...); ... }`，`addFocusableFloating` 保持 P6 的焦点环与 `requestFocus` 行为不变。

### 22.5 样本升级到修订 2，以及被打破的 P8 契约

P9 改了内置样本的清单，存量设备上的 `files/plugins/douban.comments` 仍是没有 `surfaces` 的旧副本，必须能自愈。`SEED_REVISION` 从 1 提到 2，但**只刷新宿主自己种的副本**：

```
existing != null && !isOwnedCopy(existing, id)  -> 0  // 用户手动导入的同 id 包，一行都不碰
existing != null && !目录存在                  -> 0  // 僵尸条目不重建目录
existing == null && !firstRun                  -> 0  // 种过之后删掉了，不许复活
```

第三条是这一轮最重要的修正。P8 已经把「用户删掉内置样本后不复活」写进契约（用例 `sampleDeletedByTheUserIsNotResurrected`），而修订号 1→2 时 `config.find(id) == null` 会走全新建分支，把用户删掉的样本重新种回来——**升级路径打破了 P8 的承诺**。修法是把「首次登记」与「升级刷新」当成两件事，唯一的事实是记账修订号是不是 0：

```
boolean firstRun = revision == 0;   // 从没种过，才允许新建
```

`isOwnedCopy` 用双端 `getCanonicalFile().equals(...)` 比较 `PluginPackageImporter.pluginsRoot()+id` 与条目里记的 `directory()`，因此路径写法不同（`//`、无尾斜杠）也认得出来。刷新时保留 `enabled` 与 `importedAt`——用户手动停用过的插件不能因为升级被重新打开。

### 22.6 验证

| 项 | 结果 |
| --- | --- |
| 四变体编译（mobile/leanback × main/androidTest） | EXIT=0 |
| plugin JVM 单测 | **23 suites / 233 tests / 0 failures / 0 errors / 0 skipped** |
| 真机 mobile（emulator-5560） | tests=32 failures=0 errors=0 **skipped=3** |
| 真机 leanback（emulator-5560） | tests=32 failures=0 errors=0 **skipped=1** |

跳过全部来自 `Assume` 门控，不是失败：mobile 变体上 `PluginPanelLeanbackFocusDeviceTest` 3 条（电视专属）与 `dragMovesTheChipSnapsToASideAndIsRestoredOnNextAttach` 真的跑了；leanback 变体上 `PluginPanelLeanbackFocusDeviceTest` 3 条真跑，只有拖动那条按 R2 裁定跳过。

单测从 P8 的 217 涨到 233（+16）：`PluginUiPresenterTest` +6、`PluginManifestTest` +4、`PluginSampleInstallerTest` +6。

真机端到端取证（emulator-5560，mobile 变体，`adb root` 后 `am start` 直达非 exported 页面）：

1. 装包后 `run-as com.silent.android.webhtv cat files/plugins/douban.comments/manifest.json` 的 `ui[0]` 已含 `"surfaces":["detail","playback"]`，prefs 里 `plugin_sample_seed_revision = 2` → 升级刷新生效。
2. 首页 uiautomator dump：**没有「豆瓣」节点**（clickable 7 个，全是宿主自己的按钮）→ 页面过滤生效。
3. 详情页 dump：`<node text="豆瓣" clickable="true" bounds="[1800,847][1892,912]" />`（屏幕 1920x1080）→ 声明为 detail 的槽位在该页出现。
4. `input swipe 1846 879 320 500 800` 后节点变 `bounds="[28,478][120,543]"`，prefs 出现 `plugin_chip_pos:douban.comments:douban.comments = start:0.4722` → 拖动改写落点并落盘。
5. BACK 回首页（仍无「豆瓣」）→ 再次进入详情页 → 节点仍是 `[28,478][120,543]` → 记忆跨页面、跨进程重启都成立。

### 22.7 已知边界

1. `restorePosition` 与拖动用的是两份父容器高度：复原时 `decor.getHeight()` 常在首次布局前还是 0，回落到屏幕高度；拖动时拿的是真实高度。同一次会话内不漂移（写入用当次真实值），但在「窗口高度与屏幕高度不一致」的设备上首次复原可能与最终落点差几个像素。
2. 手机端 panel 槽位复用 `addFloating`，因此**也继承拖动与记忆**。这是 R2「手机端所有浮标可拖」规格的必然结果，不是遗留缺陷；电视端 panel 走 `addToolbarChip` / `addFocusableFloating`，不受影响。
3. `PluginSurface.of` 靠类名与继承关系判断，插件无法声明「只在某个具体 Activity 上出现」这种更细的粒度。当前值域四个够用，真需要更细时再加一层 `anchor`（该字段至今全仓零消费方）。
4. 电视端 chip 位置仍固定（`END|BOTTOM` 或控制条内），焦点环可辨识度仍需人工在真电视盒子上验收（P6 遗留）。

### 22.8 下一步

1. `net.request` 域名白名单重新评估：审计已落盘（P7），D2 当时的取舍前提已变。
2. py/html 运行时与 `page` 槽位——`page` 至今在 `PluginUiPresenter` 里被静默忽略。
3. 面板 chip 连点叠窗口：`PluginPanelDialog.show` 缺 `findFragmentByTag` 复用分支（`TmdbVideoPlayerDialog.show` 有），属 HEAD 上既有缺陷。
4. 电视端面板遥控器可达性人工验收。


## 23. P10：豆瓣短评的总评分、滑动分页与宿主网络缓存

### 23.1 需求来源与三条裁定

用户原话（m05721）：「按你的建议来，豆瓣评论有缓冲吧？没有的话请加上，最好是加上总评分，再就是能显示全部评论嘛是否现有登录才能获取全部？」追问两问后裁定（m05783）：「Q1插件应该可以调用APP的缓冲功能吧？和TMDB的一样的方式实现吧，缓冲时间的话也差不多在更新的1天，完结的三十天之类的。」「Q2，滑动分页获取，比如进来20条滑倒后面再加载后续的（具体比例你来定看看接口怎么处理比较稳妥，也更好的配合缓冲）」

| 编号 | 裁定 | 落地形态 |
| --- | --- | --- |
| R1 | 缓存走宿主持久层，对齐 TMDB 的文件加 TTL 方案，不放在插件内存 | 新增 `PluginHttpCache`，插件通过 `webhtv.request` 的第 5 个参数（ttlSeconds）声明缓存意愿 |
| R2 | TTL 按连载/完结分档，连更 1 天、完结 30 天 | 脚本 `ttlOf(detail)` 决策，宿主只钳制上限 2592000 秒 |
| R3 | 列表滑动分页，首屏 20 条，滚到底再继续 | 一次 action 取满 3 页共 150 条，前端只切换显隐分批揭示 |

---

### 23.2 交付物

| 文件 | 变化 | 说明 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginHttpCache.java` | 新建 218 行 | GET/HEAD 200 响应的磁盘缓存：键含 pluginId、方法、URL、请求体哈希与排序后请求头哈希 |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginNetBridge.java` | +35/-4 | 七参重载，ttl 钳制、命中审计 outcome=cache |
| `app/src/main/java/com/fongmi/android/tv/plugin/QuickJsPluginRuntime.java` | +5/-3 | 脚本侧 `request` 接受第 5 参并转成 Java 的 ttlSeconds |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheModuleId.java` | +1 | 新增 `PLUGIN_HTTP_CACHE("plugin.http")` |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheModuleRegistry.java` | +13/-1 | 登记模块与托管根目录，避开 `TEMP_FILES` 的 .tmp/.log 清扫 |
| `app/src/main/java/com/fongmi/android/tv/cache/CachePolicyEngine.java` | +5 | 把该模块排除出深度清理计划 |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupManager.java` | +2 | 手动清理分支 |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java` | +1 | 模块名映射 |
| 三份 `strings.xml` | 各 +1 | 「插件网络缓存」「外掛網路快取」 |
| `app/src/main/assets/plugin/douban/douban.js` | 185 到 423 行 | 总评分卡、展示全部评论、分批揭示、TTL 分档、搜索接口替换 |
| `app/src/main/assets/plugin/douban/manifest.json` | 1 行 | `runtime.timeout` 20 收紧到 2 |
| `app/src/main/assets/plugin/README.md` | +26/-2 | §4 与 §7.1：一次 action 只有 5 秒、面板 JS 回不到插件、ttlSeconds 的实际语义 |
| `app/src/main/java/com/fongmi/android/tv/plugin/PluginSampleInstaller.java` | +9/-2 | `SEED_REVISION` 3 提到 4，让已种样本跟上新脚本 |
| `app/src/test/java/com/fongmi/android/tv/plugin/PluginHttpCacheTest.java` | 新建 254 行 10 用例 | 键稳定性、TTL 边界、超大响应、空体、损坏目录 |
| `app/src/test/java/com/fongmi/android/tv/cache/CachePluginHttpModuleTest.java` | 新建 77 行 2 用例 | 模块只统计自己的家族 |

---

### 23.3 为什么缓存必须是宿主的，插件做不到

插件脚本能用的宿主对象只有 `webhtv.version/on/action/log/config/request`（`QuickJsPluginRuntime.java:56-86`），没有文件、没有 KV、没有定时器。`PluginNetBridge` 用的是裸 `OkHttp.client(...).newCall(...).execute()`（:89），没有 OkHttp Cache；`catvod/net` 下也没有任何 CacheControl 用法。所以「像 TMDB 一样」这条要求，只能由宿主新加一层，脚本侧只负责声明 TTL。

关键的三条设计取舍：

1. **只缓存 GET/HEAD 且只缓存 200**。`CACHEABLE_METHODS = Set.of("GET", "HEAD")`（:50）、`CACHE_STATUS = 200`（:48）。写请求缓存会让一次失败的 POST 被重放，代价远大于收益。
2. **过期判据用文件 mtime，不另存元数据**。`long age = System.currentTimeMillis() - file.lastModified(); if (age < 0 || age > ttlSeconds * 1000L) return null;`（:100-116）。省掉一份需要自己维护一致性的索引文件，也让「清理器按时间淘汰」直接可用。
3. **缓存文件名不带后缀**。`TEMP_SUFFIXES = Set.of(".apk", ".zip", ".tmp", ".log")`（`CacheModuleRegistry.java:34`）会把带这些后缀的文件当垃圾删掉，所以落盘名用裸的 64 位 sha256；写入时先用 `.part` 临时名再 `ATOMIC_MOVE`（失败回落普通 move），避免读到半截文件。

TTL 的钳制只有一处：`MAX_TTL_SECONDS = 30 * 24 * 60 * 60`（2592000），`ttlSeconds()` 把非数字、负数、NaN 统一转成 0（等于不碰缓存），越界钳到上限，正数里小于 1 的抬到 1。

---

### 23.4 缓存命中也进审计

用户和开发者都需要能分辨「这条短评是网络来的还是缓存来的」。命中分支写 `recordRequest(..., PluginHttpCache.CACHE_STATUS, 0, "cache")`（`PluginNetBridge.java:100-107`），真实请求的 outcome 保持 ok/error/timeout。真机取证（emulator-5560，同一个详情页第二次打开面板）的审计尾部：

```
GET m.douban.com /rexxar/api/v2/search/subjects          200    0ms cache
GET m.douban.com /rexxar/api/v2/movie/35517044            200    0ms cache
GET m.douban.com /rexxar/api/v2/movie/35517044/interests  200    0ms cache   (x3 页)
```

第一次打开时对应的是 `status 200 / outcome ok / duration 493ms` 与 332ms 这类真实耗时。

---

### 23.5 分页为什么是「一次取全、前端分显」

面板是 `PluginPanelDialog` 用 `loadDataWithBaseURL` 灌进去的一段静态 HTML，**没有 `addJavascriptInterface`**，面板里的 JS 回不到插件，也没有第二次 action 的通道。可选的两条路：

- 给面板加一个 JS 桥，让滚动回调触发第二次 action。代价是要新增一个跨进程边界的通信协议，且必须在主线程之外处理，安全面明显变大。
- 一次 action 内串行取满若干页，HTML 里全部渲染，滚动只切换 `display`。

选后者。实测匿名分页的硬上限决定页数：`count` 单次最多 50（传 100/200 也只回 50），`start <= 100` 才有数据（start=101 起 HTTP 200 但 `interests` 为空数组），所以 3 页 × 50 = 150 条正好填满匿名可见区间。脚本里 `MAX_PAGES = 3`、`PAGE_SIZE = 50`；首屏 `DEFAULT_FIRST = 20`，可由清单 `params.count` 调到最多 150。

时间预算才是真正的约束：`QuickJsPluginRuntime.CALL_TIMEOUT_MS = 5_000L`（:44），超时不是这次调用失败，而是 `PluginHost` 的 catch(Throwable) 把整个插件 quarantine 掉。实测三段 interests 各 1029ms / 356ms / 1613ms，合计已经接近 3 秒，加上搜索 371 到 885ms 与详情请求，必须留余量。因此脚本用两道闸：

- `LATEST_REQUEST_AT_MS = 2600`：`canRequest()` 在发起下一次取数前检查是否还剩时间，不够就提前收尾，页脚照样显示已拿到的部分。
- 清单 `runtime.timeout` 从 20 收紧到 2：这个字段是**单个 HTTP 请求**的上限（`PluginManifest.java:131-132` 钳在 1 到 60），不是一次 action 的总预算。2 秒足以覆盖正常 400 到 1000ms，超时也不会把整体拖过 5 秒。

页脚文案如实说明边界：「本页 N 条，豆瓣共 X 条 / 匿名可看前 150 条（豆瓣限制），更多需登录豆瓣」。继续翻页需要豆瓣登录态，宿主当前没有 cookie 注入点，也不该替用户保存第三方账号，因此不做。

---

### 23.6 搜索接口在本次任务中被替换（外部接口变更）

首轮真机验证时面板只显示「在豆瓣没有搜到「霸王别姬」的条目」。独立复现后确认：`https://movie.douban.com/j/subject_suggest?q=...` 现在对任何查询都返回 HTTP 200 加 2 字节的 `[]`，换 UA、换 Referer、加 `X-Requested-With` 与 `Accept` 全部一样。旧链路的 `subjectId(url)` 正则 `/subject\/(\d+)/` 因此永远取不到 id。

替代接口（实测可用）：`GET https://m.douban.com/rexxar/api/v2/search/subjects?q=<encodeURIComponent 片名>`，必须同时带 `User-Agent` 与 `Referer: https://m.douban.com/`，**不带 Referer 直接 400**。响应里 `subjects` 是对象而不是数组，条目 id 在 `subjects.items[i].target.id`（纯数字字符串），类型在 `target_type`（域恰是 movie/tv，与载荷里的 `context.mediaType` 同一套词）。脚本据此新增 `subjectItems(payload)`（逐层判空，任何一层不对就返回空数组）与改写后的 `pickSubject(items, mediaType)`（先按类型匹配，再回第一个 id 合法的条目）。

失败文案拆成三档，避免把「接口不可用」误报成「没有这部片」：搜索请求失败、返回的不是 JSON 对象、确实没搜到。

---

### 23.7 样本升级：SEED_REVISION 提到 4

内置样本只在记账修订号落后时才刷新，因此资产里的 `douban.js` 改了而修订号不变，已经装过 App 的设备会一直跑旧脚本。本次连提两次：3 对应脚本重写（总评分、分页、TTL 第 5 参）与清单 timeout 收紧，4 对应搜索接口替换。判定逻辑一个字没改，P9 定下的三条「不复活」规则仍然生效：修订号非 0 时只刷新不新建、非宿主自己的目录不碰、目录已被用户删掉不重造。

---

### 23.8 验证

| 项 | 结果 |
| --- | --- |
| 两变体 Java 编译 + plugin/cache 单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.plugin.*' --tests 'com.fongmi.android.tv.cache.*'` 全部通过，SUITES=39 TESTS=319 FAILURES=0 ERRORS=0 SKIPPED=1 |
| 两变体 APK | `assembleMobileArm64_v8aDebug assembleLeanbackArm64_v8aDebug` 通过 |
| 脚本 ES5 合规 | `node --check` 退出码 0；箭头函数、let/const、模板字符串各 0 处 |
| 真机总评分 | 详情页「低智商犯罪」打开豆瓣面板，首屏显示 8.2 分 + 五颗星 + 「24.9万 人评价 · 9.4万 条短评」 |
| 真机分页 | 同一面板连续滚动到底，页脚出现「本页 150 条，豆瓣共 9.4 万条 / 匿名可看前 150 条（豆瓣限制），更多需登录豆瓣」 |
| 真机缓存 | 第二次打开同一面板，审计里搜索、详情、三页 interests 全部 outcome=cache、durationMs=0 |
| 缓存落盘 | `/data/data/com.silent.android.webhtv/cache/plugin-http-cache/douban.comments/` 下出现无后缀的 64 位十六进制文件名 |
| 样本刷新 | 重装后设备上的 `douban.js` 含新的 `SEARCH_URL` 常量，prefs 的 `plugin_sample_seed_revision` 为 4 |
| 两变体真机 | emulator-5560 上 mobile 32 条（3 条 Assume 跳过）、leanback 32 条（1 条跳过），失败 0 错误 0；顺带修正 `PluginSampleInstallerDeviceTest` 里钉住旧站点 `movie.douban.com` 的过期断言，改为钉 `webhtv.action('douban.comments'` 与 `rexxar/api/v2` |

---

### 23.9 已知边界

1. **匿名最多 150 条**，不是全部。`total` 显示的是豆瓣自己报的总数（例如 9.4 万），不是本面板能翻到的数量。要更多必须登录豆瓣，宿主不保存第三方账号。
2. **缓存键包含请求头哈希**，因此同一个 URL 换了 UA 或 Referer 就是另一份缓存。这是刻意的：响应确实可能随请求头变化。代价是键空间比「只看 URL」更大。
3. **缓存没有总量上限**，只有 TTL 与手动清理入口。长期使用会按插件与条目数线性增长，直到用户清理或 TTL 全部过期后被下一次清理回收。
4. **TTL 分档依赖脚本自己的启发式**（非剧集、或剧集标注了「全」即视为完结），豆瓣详情里没有可靠的连载标志：`is_released` 与 `is_show` 实测恒为 True/False，`episodes_info` 恒为空字符串。判断不出时按连载档（1 天）走，宁可多取一次网络。
5. **一次 action 最多 150 条**是 5 秒硬超时下的取舍。若某天豆瓣把单页上限从 50 提高，或响应变慢，`canRequest()` 会提前收尾，用户看到的是不足 150 条而不是插件被禁用。

---

### 23.10 下一步

1. 给面板加可选的 JS 桥，让滚动真正触发增量 action，从「一次取 150 条」变成「按需取」；需要同时定义桥的权限与线程模型。
2. 缓存总量上限与按容量淘汰（当前只有 TTL 与手动清理）。
3. 若将来引入账号能力，再评估登录后的全量短评（属于产品决策，不是技术缺口）。
4. 沿用 §20.9 与 §22.8 的遗留项：net.request 域名白名单重新评估、py/html 运行时与 page 槽位、面板 chip 连点叠窗口复用、电视端焦点环人工验收。

---

## Recovery anchor · 恢复锚点（2026-11-03）

- **目标**：插件框架 P1–P10 全部落地在 `dev4`，并保持 `docs/C117-beta-merge-review-dev4-20261103.md` 记录的可提交状态。2026-11-03 收口后 `dev4` 已推送并开出 PR #519（→ `beta`），beta 随后前进 4 个提交，已再次合并（`42c7f865655`）并复验通过。
- **验收标准**：双 flavour 编译通过 + `--tests 'com.fongmi.android.tv.plugin.*' --tests 'com.fongmi.android.tv.cache.*'` 全绿（基线 SUITES=39 TESTS=319 FAILURES=0 ERRORS=0 SKIPPED=1）+ 插件包内每个文件 ≤500 行（§7:310 门禁）。
- **当前文件/符号**：`app/src/main/java/com/fongmi/android/tv/plugin/` 下 29 个类。2026-11-03 为满足 500 行门禁与评审结论完成拆分与补线：新增 `PluginAuditCodec`（落盘 JSON 编解码）、`PluginAuditWriter`（落盘调度与版本仲裁）、`PluginChipFactory`（chip 构造与配色）、`PluginFloatingDock`（悬浮入口挂载与拖动）；`PluginAuditLog` 542→279 行、`PluginUiPresenter` 655→382 行。
- **已完成动作与结果**：见 `docs/C117-beta-merge-review-dev4-20261103.md` 的「修复清单」与「验证」——`QuickJsPluginRuntime` 超时释放、审计版本透传与 `bodySize` 字段、六条生命周期事件接线、chip nextFocus 链、五处重复消除。
- **未验证的工作区改动**：无。评审修复的 16 个已跟踪改动 + 4 个新增文件已随 `c026bae6e6a` 提交（验证：双 flavour 编译 + plugin/cache 单测 321 条全绿），已推送 `origin/dev4` 并附 recovery tag；beta 前进后再次合并为 `42c7f865655`，合并后重跑编译（BUILD SUCCESSFUL 2m 14s）、plugin/cache 单测（SUITES=39 TESTS=321 FAILURES=0 ERRORS=0 SKIPPED=1）、beta 自带 3 个用例（BUILD SUCCESSFUL 1m 58s）三线全绿。工作区仅剩本节文字更新本身。
- **未解决风险**：见 §23.10 与 §22.7 的遗留项——page 整页槽位、py/html 运行时、订阅通道 `plugins[]`、清单 `targets[]` 与 `ui[].leanback` 覆盖块、`reservedNames`/`staged` 保护、插件设置页的审计查看/清理入口、电视端焦点环人工验收。
- **回滚锚点**：`379fbcdd5aa`（合入 beta 后的基线）。本轮修复若已提交为一或数个提交，用 `git revert <sha>` 逐个撤回；未提交时直接 `git checkout -- <path>` 还原即可——本锚点只保证"回到合入 beta 时的状态"这件事有唯一确定的 commit。
- **下一个动作**：按 §23.10 顺序取下一个尚未实施的项；开工前先确认 `docs/C117-beta-merge-review-dev4-20261103.md` 的遗留台账没有把它标成「已覆盖」。
