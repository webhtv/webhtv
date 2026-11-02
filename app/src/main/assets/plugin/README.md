# WebHTV 插件脚本契约（SDK 1）

本目录包含内置示例插件。插件是外部 `js` / `py` / `html` 文件，形态与站源、字幕源一致。

## 1. 脚本约束

- **处理器必须是同步函数。** QuickJS wrapper 没有内建 Promise 事件循环，返回 Promise
  会被宿主当作"不支持"处理（引导脚本会把它替换成 `__PROMISE_UNSUPPORTED__` 标记串）。
  需要异步数据请用同步方式获取，或等待后续切片提供受控的异步能力。
- **脚本异常不会崩溃应用。** 抛错会让该插件被隔离（禁用），并在审计日志留下记录。
- **脚本体积上限 512 KiB。** 超过则拒绝加载。

## 2. 宿主对象

插件脚本执行时，全局已有 `webhtv` 对象：

```js
webhtv.version          // 宿主 SDK 版本，当前 1
webhtv.on(event, fn)    // 订阅事件
webhtv.action(name, fn) // 注册 UI 槽位点击动作
webhtv.log(...)         // 输出到诊断日志
webhtv.config           // 本插件的参数对象（清单 params 经用户填写的值替换后）
webhtv.request(...)     // 受控 HTTP 请求（需 net.request 权限）
```

`fn(payload)` 的 `payload` 是 JSON 字符串，需要自行 `JSON.parse`。

## 3. 可用事件（需要清单声明 + 对应权限）

| 事件 | payload | 所需权限 |
| --- | --- | --- |
| `app.foreground` | `{}` | `plugin.events` |
| `app.background` | `{}` | `plugin.events` |
| `activity.created` | `{"activity":"VideoActivity"}` | `plugin.events` |
| `activity.destroyed` | `{"activity":"VideoActivity"}` | `plugin.events` |
| `ui.contentShown` | `{"activity":"TmdbDetailActivity"}` | `plugin.events` 或 `ui.floating` / `ui.toolbar` / `plugin.data.episode` |

> `ui.contentShown` 被多个页面共用（首页、文件、历史、收藏、详情、播放），
> **必须按 payload 里的 `activity` 自行过滤**，不能假设它来自详情页。

五个事件都是纯生命周期信号，payload 里只有 `activity` 或为空。业务数据（片名、集号、海报等）**不通过事件下发**，只在 `action` 被调用时随 payload 的 `context` 字段提供——见 §5.1。

## 4. 清单示例

本目录下有两个示例，分别演示不同的能力组合：

- `example/manifest.json` + `example/hello.js`：**最小可用清单**。一个 floating 槽位、一个事件订阅，用来确认装载、事件派发与故障隔离这三条链路是通的。
- `assets/plugin/douban/manifest.json` + `assets/plugin/douban/douban.js`：**完整示例**。演示 `panel` 渲染 + `plugin.data.episode` 上下文 + `net.request` 联网三件事凑在一起：点「豆瓣」chip，脚本用片名搜豆瓣条目、取第一条匹配的 subject id、再拉短评列表，最后把结果拼成一段自包含 HTML 交给宿主面板。**读它的价值在于看错误路径怎么处理**——每一步失败都返回一句可读的提示 HTML，而不是抛异常（抛异常会让整个插件被隔离）。

最小可用清单：

```json
{
  "protocol": "webhtv-plugin",
  "version": 1,
  "id": "example.hello",
  "name": "示例插件",
  "sdk": 1,
  "runtime": { "api": "js", "entry": "assets://plugin/example/hello.js" },
  "events": ["ui.contentShown"],
  "ui": [
    { "slot": "floating", "label": "示例", "action": "hello.show", "event": "ui.contentShown" }
  ],
  "permissions": ["plugin.events", "ui.floating"]
}
```

## 5. 槽位与面板渲染

清单的 `ui[]` 决定插件在界面上以什么形态出现。SDK 1 渲染三种：

| 槽位 | 权限 | 宿主的落点 |
| --- | --- | --- |
| `floating` | `ui.floating` | 手机端悬浮按钮，浮在内容之上 |
| `toolbar` | `ui.toolbar` | 播放页控制条末尾追加一枚 chip，电视端的主要入口 |
| `panel` | `ui.panel` | 点 chip 后由宿主打开的侧滑 WebView 面板 |
| `page` | `ui.page` | 整页插件，SDK 1 未渲染 |

每个 `ui[]` 项还可以声明 **`surfaces`**：这个槽位只在哪些页面上出现。

| `surfaces` | 含义 |
| --- | --- |
| 不写这个键 | 空集 = **所有页面都显示**（早期清单的默认行为，不会因为升级而消失） |
| `home` / `detail` / `playback` / `other` | 只在对应页面显示，可多选 |
| 宿主不认识的值（含拼错） | 永不显示——不报错，也不拒绝整份清单 |

至多 8 个元素、单元素至多 16 字符；比对前统一转小写，写 `"Detail"` 与写 `"detail"` 等价。

```json
"ui": [
  {
    "slot": "panel",
    "label": "豆瓣",
    "action": "douban.comments",
    "event": "ui.contentShown",
    "surfaces": ["detail", "playback"]
  }
]
```

**`panel` 的完整链路**：用户点 chip → 宿主调用插件的 `action` → **脚本返回的字符串被宿主当作 HTML** → 宿主用侧滑 WebView 面板渲染它。标题栏与关闭按钮由宿主提供，脚本不需要也不应该自己画；电视端方向键可以直接滚动面板内容。由此有四条约束：

- `action` 处理器必须**同步返回字符串**。返回 `undefined` / `null` / 非字符串时面板是空的，且不会报错。
- 返回的 HTML 必须自包含：不保证能加载外部资源，样式请内联在 `<style>` 里，图片不要指望 `<img src="http...">`。
- 面板内容是插件自己拼的，**但里面的第三方文本不可信**：用户名、评论正文这类东西必须 HTML 转义后再拼进去，否则一段 `<script>` 就能在宿主面板里执行。
- 抓取慢不会卡住控制条：`action` 在宿主的后台线程执行，面板先打开再等 HTML；但超过 `runtime.timeout` 后宿主放弃这次调用。

### 5.1 action payload

`action` 处理器收到的 `payload` 是 JSON 字符串（要自己 `JSON.parse`）：

```json
{
  "activity": "TmdbDetailActivity",
  "context": {
    "title": "片名", "subtitle": "副标题", "mediaType": "movie",
    "year": "2024", "tmdbId": "693134", "tmdbRating": "8.1", "doubanRating": "7.9",
    "originalLanguage": "en", "originCountry": "US", "posterUrl": "https://...",
    "episodeName": "第 1 集", "episodeNumber": "1"
  }
}
```

`activity` 恒有，是触发时宿主 Activity 的简名。`context` **只有清单声明了 `plugin.data.episode` 权限才出现**，且只在一个真实的条目详情页或播放页上才有内容——从首页点出来的 chip 拿不到片名。**字段缺失时对应的键直接不出现**（不是空串），所以脚本要按「可能没有」来写，先判空再取。

## 6. 参数配置

清单可以用 `params` 声明参数模板，值形如 `${KEY}` 的整串会被替换成用户在「插件管理 → 参数」
里填写的同名键；未填写的键替换成空串（**不会**把 `${KEY}` 字面量当真实值发出去）。
半截占位符（如 `"Bearer ${TOKEN}"`）不替换，原样保留——这样漏填是看得见的。

```json
"params": {
  "token": "${DOUBAN_TOKEN}",
  "limit": 20,
  "headers": { "Referer": "https://movie.douban.com/" }
}
```

脚本里读取 `webhtv.config`（已完成替换的对象）：

```js
webhtv.action('comments.show', function () {
  var token = (webhtv.config && webhtv.config.token) || '';
  var limit = (webhtv.config && webhtv.config.limit) || 20;
  return token ? 'ok' : 'missing token';
});
```

参数只在 `runtime.init` 阶段注入一次；用户在管理页改了参数，宿主会重新装载插件。

## 7. 网络请求

```js
var res = webhtv.request('GET', 'https://m.douban.com/rexxar/api/v2/movie/1/comments', {
  'Referer': 'https://movie.douban.com/'
});
// res = { status: 200, body: '...' } 或 { status: 0, error: '...' }
```

- 方法仅限 GET / POST / PUT / PATCH / DELETE / HEAD。
- 响应体截断到 512 KiB，请求体上限 256 KiB。
- 超时取清单 `runtime.timeout`（1..60 秒）。
- **返回对象而不是抛异常**：失败也是 `{status:0, error:'...'}`，脚本无需 try/catch。

## 8. 安全边界

- 清单里每种 UI 槽位类型都必须有对应权限：`floating → ui.floating`、
  `toolbar → ui.toolbar`、`panel → ui.panel`、`page → ui.page`。
- 插件只能读取**声明过的事件**；未声明的事件不会派发，不存在"默认收全部"。
- `net.request` 权限在 SDK 1 中**已开放**，但只能在脚本里通过 `webhtv.request` 使用：
  宿主不提供域名白名单（插件与站源、字幕源同级信任），因此**每一次请求都会记入审计日志**
  （只记 host + path，丢弃 query 与 fragment）。没有该权限时调用会返回
  `{status:0, error:"missing permission net.request"}`，插件**不会**因此被禁用。
- 插件失败会被隔离并记入审计日志，可追到具体插件 id 与版本。
