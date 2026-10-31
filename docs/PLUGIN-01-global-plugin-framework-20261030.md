# PLUGIN-01 · 全局插件框架设计评估

- 状态：**评估完成 + D1–D4 已裁定，未进入实施**
- 日期：2026-10-30
- 分支/HEAD：`dev4` @ `248cdfe111d`
- 作者：Dispatch Agent（只读评估 + 文档；本次未改动任何生产代码）
- 关联文档：`docs/universal-webhome-theme-design.md`、`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md`、`docs/webtheme-compatibility-matrix.md`

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
| 焦点返回 | 面板关闭后 `requestFocus()` 回原 chip | 沿用 `FocusRestorer`（`ui/helper/`）既有模式 |
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
