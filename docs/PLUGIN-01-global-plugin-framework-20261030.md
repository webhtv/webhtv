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

P2c：QuickJS 适配器（**新写 JS 运行时，不复用 `quickjs.crawler.Spider`**，因为它继承爬虫语义）+ `floating` 槽位渲染 + 审计接入真实请求。这一步起必须有真机/模拟器验证。
