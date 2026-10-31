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

## 4. 清单示例

见本目录 `example/manifest.json`。最小可用清单：

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

## 5. 参数配置

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

## 6. 网络请求

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

## 7. 安全边界

- 清单里每种 UI 槽位类型都必须有对应权限：`floating → ui.floating`、
  `toolbar → ui.toolbar`、`panel → ui.panel`、`page → ui.page`。
- 插件只能读取**声明过的事件**；未声明的事件不会派发，不存在"默认收全部"。
- `net.request` 权限在 SDK 1 中**已开放**，但只能在脚本里通过 `webhtv.request` 使用：
  宿主不提供域名白名单（插件与站源、字幕源同级信任），因此**每一次请求都会记入审计日志**
  （只记 host + path，丢弃 query 与 fragment）。没有该权限时调用会返回
  `{status:0, error:"missing permission net.request"}`，插件**不会**因此被禁用。
- 插件失败会被隔离并记入审计日志，可追到具体插件 id 与版本。
