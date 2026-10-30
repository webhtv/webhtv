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

## 5. 安全边界

- 清单里每种 UI 槽位类型都必须有对应权限：`floating → ui.floating`、
  `toolbar → ui.toolbar`、`panel → ui.panel`、`page → ui.page`。
- 插件只能读取**声明过的事件**；未声明的事件不会派发，不存在"默认收全部"。
- `net.request` 权限在 SDK 1 中**尚未开放**。等 P2c 之后的分发切片提供受控网络能力时再启用。
- 插件失败会被隔离并记入审计日志，可追到具体插件 id 与版本。
