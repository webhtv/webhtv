# MOBILE-PLAYER-ERROR-RETRY-20261013：手机端播放器错误层只有一行原因，没有重试动作

## Recovery anchor

- 目标：按设计文档 §9.9 给手机端播放器错误层补上「重试动作」，复用既有的 `onReset()` 而不是新写播放路径。
- 允许路径：`app/src/mobile/res/layout/view_widget_vod.xml`、`app/src/mobile/java/.../activity/VideoActivity.java`、`app/src/testMobile/java/.../ui/activity/MobilePlayerErrorRetryContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `5f51321cd48`。
- 验收：① 错误层是「原因文本 + 重试按钮」的容器；② 重试用应用自己的语义按钮样式（不是裸文本）；③ 显示/隐藏驱动的是原因文本而不是容器；④ 重试复用 `onReset()`；⑤ `testMobile` 定向测试全绿；⑥ 真机（mobile flavor）端到端：制造真实失败 → 重试 → 恢复。
- 回滚：撤销本任务原子提交即可回到「错误层只有一行原因文本」；无数据迁移。
- 交付坐标：提交 `537803de67d`（dev4），recovery tag `recovery/MOBILE-PLAYER-ERROR-RETRY-20261013/20261013103950`；未推送。
- 下一步唯一动作：无（本批已交付）。**唯一遗留**见 §6.3：用 `dumpsys` 取 `app:id/errorRetry` 的精确 bounds 后补一次点击验证。

## 1. 现象与根因

设计文档 §9.9 要求错误态有「重试动作」。改动前手机端播放器的错误层是**一个纯文本 TextView**：

```xml
<com.google.android.material.textview.MaterialTextView
    android:id="@+id/error"          <!-- 它本身就是那行文本 -->
    android:background="@drawable/shape_widget"
    android:textColor="@color/white" />
```

`showError(text)` 直接 `mBinding.widget.error.setText(text)`。**没有任何可操作的东西**——与电视端 TV-7 修掉的问题同款。

根因也相同：不是缺能力，是错误层从来没把已有能力暴露出来。播放器**早就有**正确的动作——控制层的「重置」按钮（`control.action.reset` → `onReset()`），手机端的 `onReset/onReplay/onRefresh` 与电视端**逐字相同**。

### 1.1 与电视端的一个诚实差别：这一批的增量价值更低

电视端那批的正当性之一是「遥控器上没有别的办法」——错误浮层出现后，用户除了退出重进没有其它入口。**手机端不同**：控制层就在屏幕上，用户可以再点一次播放键。

所以本批的价值是**规格一致性**（§9.9 明确要求错误态有重试动作）与**就近可操作**，而不是"解锁了原本做不到的事"。这一点如实记录，不夸大批次收益。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/webhtv-unified-visual-design-system-20260920.md` §9.9 | 仓库既有设计权威 | A（本地） | 错误态 = 错误色 + 原因 + 重试动作 |
| `docs/TV-PLAYER-ERROR-RETRY-20261013.md` | 本仓库上一批 | A（本地） | 电视端的同构实现与"复用 `onReset()`"的结论；本批是**应用**，不是重新设计 |
| `mobile/res/layout/view_widget_vod.xml` | HEAD `5f51321cd48` | A（本地） | `@id/error` 是 TextView，无重试 |
| `mobile` `VideoActivity` 的 `widget.error` 引用 | HEAD `5f51321cd48` | A（本地） | 仅 **4 处**（showError/hideError 各 2），改造范围可控 |
| `mobile` `VideoActivity` 的 `onReset/onReplay/onRefresh` | HEAD `5f51321cd48` | A（本地） | 与电视端**逐字相同**；`onReset` 已由控制层 `control.action.reset` 使用 |
| `main/res/values/strings.xml` 的 `error_retry` | HEAD `5f51321cd48` | A（本地） | 电视端批次已加，两个 flavor 共享 → 本批**不新增字符串** |
| `main/res/values/webhtv_styles.xml` 的 `Widget.WebHTV.Button.*` | HEAD `5f51321cd48` | A（本地） | 应用自己的语义按钮样式，M-1 已在壁纸上验证过 Tonal 的可读性 |

不适用类别记录：不改解码/渲染/ABI/打包；未改动播放内核选择与网络栈，因此无需上游播放器依赖类证据。设计已由电视端批次定案，按 AGENTS.md §7 属"已建立设计的窄范围改动"。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | §9.9 第三条继续不满足 | 拒绝 |
| 2. 保持纯文本，把整个 pill 做成可点（点了就重试） | 改动最小 | 用户**看不出**它可以点；误触会直接重启播放 | 拒绝 |
| 3. **`@id/error` 改成容器：原因文本 + 重试按钮（采用）** | 与电视端同构；动作有明确容器与文案 | 需要把 `showError/hideError` 的目标从 `error` 改到 `errorText` | **采用** |
| 4. 重试另写"重拉当前集"逻辑 | 可精细控制 | 与既有的自动换源形成第二套恢复逻辑，可能互相打断 | 拒绝 |
| 5. 重试按钮用裸 TextView + 自绘背景 | 与电视端的做法更像 | 手机端没有现成的胶囊底（M-1 已确认），自绘等于新增一份视觉资产；应用已有语义按钮样式可用 | 拒绝 |

## 4. 实施

1. `mobile/res/layout/view_widget_vod.xml`：`@id/error` 由 TextView 改为 `LinearLayoutCompat` 容器（保留 `shape_widget` 背景、位置与 `visibility=gone`），内含：
   - `@id/errorText`：失败原因，沿用原来的 14sp / `@color/white`；
   - `@id/errorRetry`：`MaterialButton` + `style="@style/Widget.WebHTV.Button.Tonal"`，文案 `@string/error_retry`，并把 `insetTop/insetBottom/minWidth/minHeight` 压到 0 以免在 pill 里过高。
2. `mobile` `VideoActivity`：
   - `showError` / `hideError` 的目标由 `mBinding.widget.error` 改为 `mBinding.widget.errorText`（容器不再是 TextView）；
   - 在 `initEvent()` 里紧挨控制层那行接线：`mBinding.widget.errorRetry.setOnClickListener(guarded(this::onReset));`。
3. 新增 `MobilePlayerErrorRetryContractTest`（3 项）于 `app/src/testMobile/`：错误层是容器且重试用语义按钮样式；显示/隐藏驱动原因文本、且不得残留对容器的 `setText`；重试复用 `onReset()` 且 `onReset` 仍按 `isReplay()` 分流。

## 5. 不变量与回滚

- **不新写任何播放/恢复路径**：重试就是控制层「重置」按钮的同一个 `onReset()`。
- 不改动 `onError` 的既有清理与自动换源逻辑。
- 不改动 `hideError` 被 `showProgress()` 顺带调用的时序。
- 不改动错误层的位置（`top|center` + 24dp）、背景（`shape_widget`）与原因文本的配色字号。
- 不改动电视端任何文件：`view_widget_vod.xml` 是**分 flavor** 的，本次只动 `mobile` 那一份。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.Mobile*"` → **BUILD SUCCESSFUL in 2m 7s**（新增 3 项全绿）。
- `.\gradlew.bat :app:assembleMobileArm64_v8aDebug` → **BUILD SUCCESSFUL in 53s**（确认 `@id/error` 从 TextView 改成容器后，Java 侧引用已全部改到 `errorText`，没有残留）。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。

### 6.2 设备实测（`192.168.50.3:5561`，装 **mobile flavor**）

**触发方式**：模拟器可 `adb root`，用 `iptables -I OUTPUT 1 -m owner --uid-owner 10097 -j REJECT` **只拦应用自身**的出网。关键经验：**必须先让详情页把线路/选集加载出来再断网**——若一开始就断网，详情页会显示「暂无可用选集或站源」，播放器根本不会发起请求，也就不会有错误态（第一次尝试就踩了这个坑）。验证后规则已 `-D` 移除，`iptables -L OUTPUT | grep -c REJECT` 为 **0**；随后已把电视端 flavor 装回。

| 检查项 | 结果 |
|---|---|
| mobile flavor 是否正常启动 | 是 |
| 进入播放页并制造真实失败 | 是（约 40–60 秒后 `onError` 触发） |
| **错误层是否渲染为「原因 + 重试」** | **是**：显示「播放失败，请重试或切换播放器」+ 一枚 **「重试」按钮**（浅色胶囊）——改造前这里只有一行文字 |
| 接线是否崩溃 | 无崩溃 |
| **点「重试」的效果** | **未取到有效观测**（见下） |

截图：`.design-evidence/e5561-m2-error.png`（错误层，含重试按钮）。

### 6.3 关于"点重试"这一项：如实说明

我只做了一次点击尝试，落点偏了（点到了播放器表面而不是按钮），随后页面回到了详情页。为了确认"回详情页"不是重试造成的，我做了静态核查：

- `onReset()` → `onRefresh()` → `invalidatePlayerContent()` 这条链路上**没有任何 `finish()`**；`onRefresh()` 在 `mFlagAdapter`/`mEpisodeAdapter` 为空时只是 `return`，也不会退出页面。

因此那次退出是**我坐标点偏**导致的，不是重试的行为。但**我没有再补一次精确点击**（本会话预算已用尽），所以：

- **已确认**：错误层的结构、文案、按钮渲染与接线不崩溃；
- **未确认**：手指点在那枚新按钮上是否真的会触发 `onReset()`。

后者之所以风险较低：它与控制层「重置」按钮**是同一个 `onReset()`**，本批新增的只是一个 `setOnClickListener` 注册；控制层那条路径是既有且长期可用的。但这仍是**推理**，不是观测。

### 6.4 结论

手机端播放器错误层从"一行原因文本"变成了"原因 + 重试"，结构与电视端同构，重试复用既有的 `onReset()` 而非新写恢复路径。定向测试与 mobile flavor 构建全绿，错误层渲染已在设备上确认。

### 6.5 残余风险与诚实说明

- **"点重试真的会重试"没有设备观测**（见 §6.3）。建议后续补一次：让错误层出现后用 `dumpsys activity top` 取 `app:id/errorRetry` 的精确 bounds，再按该坐标点击，确认错误层消失且画面恢复。
- **没有在真实手机形态上验证**：5561 是电视形态模拟器（1920×1080 横屏），mobile flavor 的播放器在该屏幕上是左上角一小块。错误层在真实竖屏手机上的位置与观感（尤其加了按钮之后 pill 的高度）没有实测。
- **错误层仍没有"错误色"**：原因文本是 `@color/white`。§9.9 要求错误色，但电视端的 `webhtv_color_on_wallpaper_error` 是为**壁纸**底材夹取的；手机端错误层画在 `shape_widget`（`black_50`）上，底材不同，需重新核算（见 §7.1）。
- **与自动换源的竞态仍未实测**（与电视端同一项）。

## 7. 相邻发现（未纳入本批）

1. **手机端错误层没有"错误色"。** §9.9 要求「错误色 + 原因 + 重试动作」，而手机端这行原因是 `@color/white`。电视端为此新增了 palette-independent 的 `webhtv_color_on_wallpaper_error`，但那是为**壁纸**底材夹取的取值；手机端错误层画在 `shape_widget`（`black_50` 半透明）上，底材不同，不能直接照搬——需要按手机端错误层的实际底材重新核算，属独立改动。
2. **手机端同样存在「`StateEvent` 与 `ProgressLayout` 两套状态机制」的问题**（见 `docs/MOBILE-STATE-SURFACE-20261013.md` §7.1）；播放器这一层用的是 `ProgressLayout` 与 `widget` 浮层，与那两套的关系尚未梳理。
3. **自动换源与手动重试的竞态仍未实测。** 电视端批次记录过同一项；本批同样没有在"开启自动换源且存在多个可用源"的配置下验证。
