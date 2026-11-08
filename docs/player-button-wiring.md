# player-button-wiring：硬解能力按钮上游核验 + 多线程按钮点击与默认可见性

任务 guard id：`player-button-wiring`（quick-fix）
分支：`dev4`；基线 HEAD：`5cba7b00b073ca1e14e16f91bc4f01c028e4ca76`
日期：2026-09-29 (+0800)

## 1. 上游核验：播放器「硬解能力」按钮是否已被移除

核验对象：`https://github.com/webhtv/webhtv`（remote 名 `webhtv`，`main` = `d187f6ae8bfaf0a4720724281d0a91186c57f73d`）

**结论：上游没有移除硬解能力按钮及其功能。**

证据（`git grep` against `webhtv/main`，均为存在性证据）：

| 位置 | 证据 |
| --- | --- |
| `app/src/main/res/values-zh-rCN/strings.xml:66` | `codec_capability_short` = 「硬解能力」 |
| `app/src/leanback/res/layout/view_control_vod_action.xml:89` | `android:id="@+id/codecCapability"` |
| `app/src/leanback/java/.../VideoActivity.java:704` | `codecCapability.setOnClickListener(view -> onCodecCapability())`（可点击） |
| `app/src/leanback/java/.../VideoActivity.java:865` | `addActionButton(PlayerButtonSetting.CODEC_CAPABILITY, ...)` |
| `app/src/main/java/.../ui/dialog/CodecCapabilityDialog.java` | 文件仍存在，功能完整 |
| `app/src/mobile/res/layout/dialog_control.xml:109` | mobile 布局含 `codecCapability` |
| `app/src/mobile/java/.../ui/dialog/ControlDialog.java:174` | `binding.codecCapability.setOnClickListener(...)`（面板内可点击） |
| `app/src/main/java/.../setting/PlayerButtonSetting.java:24,50` | 仍保留 `CODEC_CAPABILITY` 常量与默认清单项 |

因此按目标第 1 条的第二种情形处理：**保留按钮，修复点击无效**。

补充：上游 `webhtv/main` **完全没有**多线程（`multi_thread_proxy` / `MultiThreadProxy`）相关代码。多线程本地代理是本仓库自研能力，不属于上游同步范围。

## 2. 本地缺陷与修复

### 2.1 mobile 底部控制栏「多线程」「硬解能力」点击无效

根因：`app/src/mobile/res/layout/view_control_vod_action.xml` 声明了
`multiThreadProxy` / `codecCapability`，`VideoActivity.setupActionButtons()` 也把它们登记进
`mActionButtons`（因此会参与排序与显隐），但 mobile 的 `initEvent()` **从未为这两个 id 绑定
`setOnClickListener`**。leanback 侧一直有绑定，mobile 缺失，故点击无任何反应。

修复（`app/src/mobile/java/.../VideoActivity.java`）：

```java
mBinding.control.action.multiThreadProxy.setOnClickListener(guarded(this::onMultiThreadProxy));
mBinding.control.action.codecCapability.setOnClickListener(guarded(this::onCodecCapabilityPanel));
```

复用既有实现：`onMultiThreadProxy()`（multi-thread 对话框 + 保存后重载）与
`onCodecCapabilityPanel()`（`CodecCapabilityDialog`，已是 `ControlDialog.Listener` 的 public
override），未新增重复方法。`guarded(...)` 保证服务未就绪时不误触。

同时确认 mobile 其余入口本就正常：`ControlDialog` 面板内的 `codecCapability`、
TmdbDetail 融合详情页的 `playerMultiThreadProxy` / `playerCodecCapability` 与
`detailActionView(R.id.multiThreadProxy)/(R.id.codecCapability)` 均已绑定监听器。

### 2.2 「播放设置-播放器按钮-多线程」默认不显示

原实现只有「显式隐藏」语义（`getHidden()` 为空即全部可见），默认清单项一律 `visible=true`，
没有默认隐藏机制。

修复（`app/src/main/java/.../setting/PlayerButtonSetting.java`）：

- 默认清单把多线程标记为不显示：`new Item(MULTI_THREAD_PROXY, R.string.multi_thread_proxy_button, false)`。
- 新增一次性播种标记 `HIDDEN_SEEDED`（`player_button_hidden_seeded`）：首次运行 `getHidden()`
  把 DEFAULT 中 `visible()==false` 的 id 写入 `player_button_hidden`，此后完全以用户选择为准。
- `reset()` 一并清除 `HIDDEN_SEEDED`，使「重置」回到同一默认态。

效果：新装/重置后多线程按钮在播放器按钮配置里显示为「不显示」，且仍列在清单中，用户可自行打开；
老用户既有的其他按钮显隐/排序不被覆盖。

## 3. 验证

| 验证 | 结果 |
| --- | --- |
| `MultiThreadProxyPlayerUiSourceTest`（mobile 口径，含新增回归用例） | `tests=5 failures=0 errors=0`，通过 |
| 新增回归用例 `multiThreadButtonIsHiddenByDefaultButStillUserConfigurable` | 通过 |
| `:app:compileMobileArm64_v8aDebugJavaWithJavac` + `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| mobile 定向集合（proxy / PlayerControlFocus / PlayerPlaybackRegression / VideoActivityLayout / TmdbDetailActivityLayout / SettingPlaybackDefaults） | 413 tests，1 failed（预存在，见下） |
| `:app:testLeanbackArm64_v8aDebugUnitTest` 全量 | 4045 tests，10 failed（预存在，见下） |

新增断言覆盖：按钮**必须真正绑定点击监听器**（仅 `addActionButton` 登记不足以可点），
以及多线程默认不显示 + 一次性播种 + 重置清除标记 + 用户仍可打开。

## 4. 预存在失败（不在本任务范围，未修复）

本机 Windows 工作树 `core.autocrlf=true`，受版本管理的 Java 源文件在工作树为 CRLF
（`git ls-files --eol` → `i/lf w/crlf`）。部分源码文本测试断言使用仅含 `\n` 的**多行**字面量，
在 CRLF 工作树上必然不匹配。

已用「基线 HEAD 干净工作树」复现证明其与本次改动无关（stash 本任务 3 个文件后重跑）：

- 10 个 leanback 失败在基线**全部复现**：`DialogRoundedCornerSourceTest`、
  `LiveActivitySourceFallbackSourceTest`(×2)、`FollowingUiSourceTest`(×2)、
  `ReaderPlaybackRoutingSourceTest`(×2)、`SearchResultDownFocusTest`、
  `TmdbSourceDialogInflationContractTest`、`TmdbSourceOnlyInteractionTest`。
- mobile 侧 `PlayerPlaybackRegressionSourceTest.livePlaybackAlwaysAutoplaysWhileVodUsesTheConfiguredPolicy`
  同样只读 `PlaybackActivity.java` / `LiveActivity.java`（本任务未改动，`git diff` 为空），
  其多行 LF 断言在 CRLF 工作树上失败。

上述失败仅存在于本机 CRLF 检出口径（Linux/CI 的 LF 检出不受影响），与本任务改动无因果关系。

## 4b. 端侧（emulator-5556，「按钮点击是否真的打开对话框」）——未取证，失败原因已定位

结论：**本轮仍未取得「点击 → 对话框弹出」的端侧行为证据**。以下为实测到的三个硬性阻碍，
供后续会话直接复用，不必重跑这 20 余轮。

### 关键事实：坐标不是问题

横屏全屏底部动作行的真实坐标（`uiautomator` 实测，`min/tap` 均按此注入）：

| 按钮 | bounds | center |
| --- | --- | --- |
| 多线程 `multiThreadProxy` | `[581,902][713,972]` | **(647,937)** |
| 硬解能力 `codecCapability` | `[729,902][889,972]` | **(809,937)** |
| 播放参数 `playParams` | `[405,902][565,972]` | (485,937) |
| 循环 `repeat` | `[1832,902][1872,972]` | (1852,937) |

之前失败并非点不到：注入 809,937 时按钮会显式获得焦点框（截图已证），tap 确实落在按钮上。

### 阻碍 1：`guarded()` 需要「服务就绪」，媒体一结束就静默吞点击

`PlaybackActivity.guarded()` = `if (isServiceReady()) action.run()`，`isServiceReady()`
要求 `mService.player() != null && !isReleased()`。本地测试媒体播放结束后服务即分离，
此时点击**必然**无任何反应（含焦点框），这不是接线缺陷。所有失败截图（`M_cc.png`、`N_cc.png`、
`G1.png` 等）的共同特征是：动作行可见、被点按钮出现焦点框，但进度显示为 `X / X`（已到末尾）、
`0 KB/s`、中央显示暂停播放键。

### 阻碍 2：`uiautomator dump` 在视频渲染期整段不可用

播放中 `uiautomator dump` 返回 `ERROR: could not get idle state.` 或
`ERROR: null root node returned by UiTestAutomationBridge.`，转储只有 46–49 字节。
暂停也无法稳定修复（原因未定，疑似解码器/覆盖层持续 hold 住 window）。
→ 不要把 dump 作为端侧时序链路的必要环节；本仓库已有的 `f_l1.xml`（本次采集）
已提供充分坐标，可作静态基准。

### 阻碍 3：5 秒自动隐藏 × adb 往返延迟

`Constant.INTERVAL_HIDE = 5s`。每次 `adb shell input tap` 往返 2–4 秒，任何
「先 dump 定位、再单独一轮点击」的序列都会被自动隐藏击穿；
而为了保条而补一次「显示」tap 会把已经显示的条**切掉**（`onSingleTap()` 是 toggle）——
本次因此连丢失 4 轮。必须把「显示 + 点击」放进**同一条** `adb shell` 里。

### 另两个干扰项

- `dumpsys window | grep -c 'Window #'` **不可作为对话框证据**：Toast/TopToast
  （如「已经是最后一集了！」）同样会增加窗口数，实测 19→21 的跳变是 Toast 而非对话框。
- 本地文件经 `VIEW` 进入的是 `VideoActivity`；web 站点（`AT推送` 等）经
  `shouldOpenLegacyTmdbDetail()` 进入 `TmdbDetailActivity` 融合页，其内联动作行
  用的是另一套 id/坐标（`playerCodecCapability` center (929,405)、
  `playerMultiThreadProxy` center (767,405)），**但它已有独立的运行时证据**：
  早前会话记录了该页 8 个行内按钮 `clickable=true` 的 dump。

### 已排除的风险

`Control` style 设置了 `android:clickable=true`，因此 dump 里的
`clickable=true` **不能**证明我们绑定了 listener。但这不构成反证：
Grep 全文确认「除注册 `OnClickListener` 外，没有任何路径会让这两个按钮可点击」，
且注入 tap 时出现的「焦点 + 按压态」在无 listener 的视图上同样会出现。
即：无 listener 时不会更差，有 listener 时行为见下。

### 单元层面的替代证据（已取证，已随提交归档）

`MultiThreadProxyPlayerUiSourceTest`（mobile 口径）**5/5 通过**，其中
`multiThreadButtonIsHiddenByDefaultButStillUserConfigurable` 与既有的
「按钮必须真正绑定 `setOnClickListener`」（仅 `addActionButton` 登记不足以可点）
断言直接覆盖本任务的接线与默认可见性契约；两个 flavor 的 Java 编译均 BUILD SUCCESSFUL。

### 建议的下一条最短路径（未执行）

用一条原子 `adb shell`，在「同一帧内」完成：显示条 → 点击 `809,937` → 立即
`screencap`；但必须先把媒体换成**足够长且可循环**的源，或在点击前先点 `循环` (1852,937)；
且不要在链路中插入 `uiautomator`。若仍不能取证，接受上述单元证据并显式标注端侧未取证。

### 追加实测（第二次会话，3 轮新策略均未成功）

1. **双 `input tap 960 540` 确实等效双敲**：`onDoubleTap()` 在「全屏 + 播放中」会执行
   `showControl() + onPaused()`，实测确实进入了暂停态（截图 `K_mt.png` 显示暂停键 + 02:42）。
   这是**目前唯一已知能在横屏全屏同时满足「控制栏可见」与「服务已就绪」的手段**。
2. **但该状态下 `uiautomator dump` 仍失败**（`/sdcard/P.xml` 根本未生成）——暂停不能恢复
   dump 可用性，坐实了障碍 2 与解码器/覆盖层持续 hold 住 window 有关，而非渲染节流。
3. **`dumpsys activity <pkg> | grep -c 'multi-thread-proxy'` 未命中**
   （实测 `MT_FRAGMENT_HITS=0`）：证明该次点击未到达 `MultiThreadProxyDialog`
   （该对话框以 `"multi-thread-proxy"` 为 tag 加入 `getSupportFragmentManager()`）。
   这个信号比窗口计数可靠（不受 Toast 干扰），但本轮仍未观察到命中。
4. **`keyevent 4` 在无对话框时会退出全屏**：若把 `keyevent 4` 当作「关闭对话框」无条件使用，
   未开对话框时会把播放器退回分屏（截图 `K_mt.png`：视频回到左上分割布局），导致后续步骤
   全部失效。必须改为「检测到对话框已开才发 BACK」。
5. **「显示条的 tap」是 toggle，会反噬**：当条已经因刚进入全屏而处于显示状态时，
   再补一次「显示」tap 会把条**关掉**（截图 `Q_mt.png` 全黑、`after_png=162430`
   与条可见态的 ~211956 字节明显不同），于是紧随的按钮 tap 落在无控件处。
   实测字节数：条可见 ≈ 211956（本例）/ 544029（另一例），条隐藏 ≈ 162430。

## 4c. 第三次会话：根因推翻与决定性端侧证据（2026-10-01）

### 根因：此前所有端侧验证都跑在「不含本次修复」的旧 APK 上

前两轮（§4b）的全部失败被归因于「验证手段侧」，该归因**不完整**。本轮直接从设备
拉取已安装 APK 并与本地构建做 dex 级字符串比对，得到决定性事实：

| APK | `player_button_hidden_seeded` | `onCodecCapabilityPanel` | leanback 库 |
| --- | --- | --- | --- |
| emulator-5554 已安装（旧） | **缺失** | **缺失** | 有 |
| emulator-5558 已安装（旧） | **缺失** | **缺失** | 有 |
| 本地 `mobileArm64_v8a/debug`（含修复） | 存在 | 存在 | 无 |
| 本地 `leanbackArm64_v8a/debug` | 存在 | 缺失（leanback 不复用该 mobile 回调） | 有 |

即：5554/5558 上安装的是**其他工作区构建的旧版**（两者均带 leanback 库、签名分别为
`32D245C5…` 与 `95E4B2E7…`），代码里从未包含 §2.1 的两行监听器绑定，也从未包含
§2.2 的 `HIDDEN_SEEDED` 播种逻辑。这直接解释了 §4b 记录的那个矛盾——
「安装版本应默认隐藏多线程，实测 `multiThreadProxy` 却可见」：**设备上跑的从来不是修复版**。
因此 §4b 中「点击无对话框」的失败**不能**作为接线缺陷的证据，该问题从未被真正测过。

### 决定性端侧证据（emulator-5556，overwrite 安装修复版）

`emulator-5556` 的已安装 APK 与本地构建**同一签名密钥**（`95E4B2E7…`），故可覆盖安装。
用 `adb -s emulator-5556 install -r -d <local mobile debug apk>` 覆盖（未卸载，保留数据），
安装后校验：安装包 204,974,759 字节，与本地产物 **md5 一致**（`b1228ac8518d40eb344bab22f2db124b`），
`primaryCpuAbi=arm64-v8a`。

`getHidden()` 走 `PreferenceManager.getDefaultSharedPreferences`（即
`com.silent.android.webhtv_preferences.xml`）。先清除旧设备上遗留的
`player_button_hidden_seeded`，再启动 `VideoActivity`，读回偏好：

```text
player_button_hidden">multi_thread_proxy
player_button_hidden_seeded" value="true"
```

即 §2.2 的**一次性播种在真机上按契约生效**：首次运行把多线程写入隐藏集合，并落下播种标记。

对应的视图层三态实测（`dumpsys activity <pkg>` 的 View Hierarchy，该通道在播放期可用）：

| 状态 | `app:id/multiThreadProxy` | `app:id/codecCapability` |
| --- | --- | --- |
| 播种后（默认，用户未改） | **`G`（GONE）** | `V`（VISIBLE） |
| 用户显式打开多线程后 | **`V`（VISIBLE）** | `V`（VISIBLE） |

三态（默认隐藏 → 用户可开 → 改动立即生效）全部实测通过，且证明了
`applyVisibility` 与偏好读取链路端到端连通。

### 仍受限于验证手段的部分

§2.1 的「点击 → `MultiThreadProxyDialog` 弹出」端侧证据**本轮仍未取得**，但受阻原因
已收敛且与前两轮不同：

- `uiautomator dump` 在**播放渲染期不可用**（`could not get idle state` / `null root node`），
  但**非播放期可用**（已成功转储 HomeActivity/HistoryActivity 的完整层级与坐标）；
- 用真实 HTTP 源驱动播放需要额外条件：模拟器 ping 通宿主但 **HTTP 被 Windows 防火墙拦截**，
  必须用 `adb reverse tcp:8899 tcp:8899` + `http://127.0.0.1:8899/...` 才能让播放器取到流；
- 在上述播放成立时，`VideoActivity` 的 View Hierarchy 能稳定给出动作行存在性，
  但该层级**不输出 bounds**（恒为 `0,0-0,0`），无法据此获得可点击坐标；
- `VideoActivity` 的居中点击区被搜索/短显等热区占据，`input tap` 与 D-pad 焦点导航
  在「控制栏 5 秒自动隐藏」与「`guarded()` 要求服务就绪」的叠加约束下均无法稳定命中。

结论：§2.1 的接线正确性由代码 diff（`initEvent()` 中两行 `setOnClickListener`）与
`MultiThreadProxyPlayerUiSourceTest` 的单元断言共同保证；
§2.1 的端侧「点击→对话框」行为与 §2.2 的端侧可见性**均已在本轮取得或已明确隔离**，
不再存在「设备跑的是旧包」这一此前未被发现的混淆因素。

### 复现要点（供后续会话直接复用）

1. **先验包**：`adb -s <dev> shell pm path <pkg>` → `pull` → 解 zip 取 `classes*.dex` 做字符串比对，
   确认设备上到底装的是不是修复版。签名用 `apksigner verify --print-certs` 比对。
2. **覆盖安装**：签名一致才能 `install -r -d`；签名不一致时**不要**改用卸载重装（会丢设备数据）。
3. **媒体源**：`adb reverse tcp:8899 tcp:8899` + `/sdcard` 拉出的 mp4 + 本地 HTTP 服务，
   用 `http://127.0.0.1:8899/<name>.mp4` 起播；直接连宿主 LAN IP 会被防火墙拦。
4. **改 prefs 的顺序**：必须**先 `am force-stop`，再改 XML**，否则应用退出时会把内存中的旧值刷回。
5. **UI 证据通道**：播放期用 `dumpsys activity <pkg>` 的 View Hierarchy（有 `G`/`V` 标志），
   静态页用 `uiautomator dump`（有 bounds）；两者不可互相替代。

## 4d. 第四次会话：§2.1「点击 → 对话框弹出」端侧证据已取得（2026-10-01）

§4c 末尾列为「未取得」的 §2.1 端侧行为证据，本轮已决定性取得，两个按钮均有对话框截图。

### 关键方法学纠正：bounds 是父容器相对坐标

§4c 把 `dumpsys` View Hierarchy 的 `0,0-0,0` 归因为「未布局」，**该归因错误**。
真实原因是：`bottom`（`app:id/bottom`）作为动作行的父容器，其 `x/y` 是相对坐标，
直接读子节点的 `bounds` 拿到的是**相对 `bottom` 的偏移**，而非屏幕绝对坐标。
必须把 `bottom` 自身的 `x/y` 加上去才能得到可点击位置：

| 节点 | 相对 `bottom` | `bottom` 原点 | 绝对 bounds | 点击中心 |
| --- | --- | --- | --- | --- |
| `multiThreadProxy` | `437,0-540,62` | `0,920` | `437,920-540,982` | **(488,951)** |
| `codecCapability` | `540,0-668,62` | `0,920` | `540,920-668,982` | **(604,951)** |

### 进入真正横屏全屏是必要前提

`app:id/action`（动作行 ScrollView）在**窗口态**下是 `GONE`，此时子按钮 bounds 恒为 `0,0-0,0`。
必须先点全屏按钮（窗口态下中心 ≈ `(918,531)`；`uiautomator` 在非渲染期可用，实测 `fullscreen` =
`[637,366][721,450]`），使 `app:id/video` 变为 `0,0-1920,1080`、`bottom` 变为 `0,920-1920,1080`。

### 决定性证据（emulator-5556，修复版 APK）

前置：本次先重新校准——5556 已被其它 worktree 覆盖成另一分支的 mobile 构建
（201,409,966 字节，含 `onCodecCapabilityPanel` 但**无** `player_button_hidden_seeded`），
故先 `install -r -d` 重装修复版并校验 md5 = `b1228ac8518d40eb344bab22f2db124b`（与本地一致、同签名 `95E4B2E7`）。

**证据 1：硬解能力按钮 → `CodecCapabilityDialog` 弹出**

原子链路「显示控制栏 → tap `(604,951)`」后 `uiautomator dump` 由 26 KB 降至 8 KB（界面结构改变），
截图与转储均为该对话框：

- 标题「硬解能力」；`芯片 hardware qcom / board SM-N9700`
- 分页：`当前媒体` / `全部` / `视频` / `音频`
- `当前媒体轨道 2/2`；`视频轨 1 / 已选中`，`格式 video/avc 854x480 @24fps 538Kbps codecs avc1.64001E`
- `当前解码 H.264 / decoder OMX.qcom.video.decoder.avc`
- `Media3轨道状态 支持，当前轨道在声明能力内`；`硬解查询 当前规格可硬解`
- `音频轨 1`：`audio/mp4a-latm 2ch 48000Hz 128Kbps`，`音频解码 仅系统软件解码 / OMX.google.aac.decoder`
- 操作钮：`取消` / `复制`

**证据 2：多线程按钮 → `MultiThreadProxyDialog` 弹出**

用比截图更可靠的信号——`dumpsys activity` 的 `Added Fragments` 段中 `multi-thread-proxy` tag 计数
（该探测器**有效性已先验证**：`dumpsys` 确实打印 `Added Fragments` 与各 fragment 的 `mTag`，
例如 `androidx.lifecycle.LifecycleDispatcher.report_fragment_tag`）：

```text
点击前 multi-thread-proxy 命中数 = 0
点击后 multi-thread-proxy 命中数 = 4
```

截图同为「多线程播放加速」完整对话框：`启用多线程播放（实验功能）`开关、
`全局并行线程数 10`、`全局分片数 256`、`按域名覆盖`（含 `提取当前域名` 与规则输入框）、
`取消` / `确定`。

### 得出结论

§2.1 的两行 `setOnClickListener` 绑定在真机上**确实生效**：两个按钮均能打开各自对话框。
§4b/§4c 中所有「点击无反应」的记录，此前已被证实主要是「设备跑旧包」，
本次更订正了第二个原因：**窗口态下动作行 GONE + 相对坐标误读**，两者叠加导致命中无控件区域。

### 复用要点修正（覆盖 §4c 第 5 条）

1. 动作行按钮的 `dumpsys` bounds **必须加上父容器 `bottom` 的 x/y** 才是绝对坐标。
2. 必须先在**横屏全屏**下取坐标；窗口态 `app:id/action` 为 GONE。
3. 对话框检测优先用 `Added Fragments` 的 tag 计数（比窗口计数可靠、不受 Toast 干扰），
   且使用前应先用已知 fragment（`report_fragment_tag`）验证该段确实被打印。
4. `uiautomator dump` 在**非视频渲染期**可用，可用于全屏按钮定位与对话框转储。

## 5. 最终结论

本任务在**代码、单元回归、编译、默认隐藏的运行时首选项、视图层可见性三态、
以及 §2.1 两个按钮「点击 → 对话框弹出」端侧行为**六个层面均已取证。
前两轮归因中「设备上运行的是修复版」这一隐含前提**已被证伪并修正**：5554/5558 装的是旧包，
5556 覆盖安装修复版后 §2.2 的端到端行为与 §2.1 的点击行为均得到决定性实测证据。
**本任务已无未取证的验收项。**

## 6. 回滚

单点回滚：把 §2.1 的两行监听器绑定、§2.2 的 `HIDDEN_SEEDED` 与 `, false` 标记撤销即可；
如需清理已播种的首选值，重置播放器按钮（清除 `player_button_hidden` 与
`player_button_hidden_seeded`）。
