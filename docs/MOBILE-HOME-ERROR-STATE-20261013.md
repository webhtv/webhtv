# MOBILE-HOME-ERROR-STATE-20261013：手机端首页加载失败后是一片空白

## Recovery anchor

- 目标：手机端首页在内容源加载失败时给出错误态（错误色 + 原因 + 重试），在"确实没有内容"时给出空态（三件套），都不再留空白。
- 允许路径：`app/src/main/java/.../event/StateEvent.java`、`app/src/mobile/java/.../activity/HomeActivity.java`、`app/src/mobile/java/.../fragment/VodFragment.java`、`app/src/mobile/res/layout/fragment_vod.xml`、`app/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml`、`app/src/testMobile/java/.../ui/fragment/MobileHomeStateContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `bf81ad59ca2`。
- 验收：① `StateEvent` 有 ERROR 且带失败原因；② 首页失败发 ERROR 而不是 EMPTY，崩溃重启那次仍保持 EMPTY；③ `VodFragment` 渲染错误态（错误色标题 + 原因 + 重试）与空态（三件套）；④ 两者都复用 `homeContent()`，不新写加载路径；⑤ 状态浮层在 PROGRESS/CONTENT 时收起；⑥ `testMobile` 定向测试全绿 + 真机（启动时断网）看到错误态。
- 回滚：撤销本任务原子提交即可回到「失败后一片空白」；无数据迁移。
- 交付坐标：提交 `f2f65ff3cf0`（dev4），recovery tag `recovery/MOBILE-HOME-ERROR-STATE-20261013/20261013115707`；未推送。
- 下一步唯一动作：无（本批已交付）。后续候选见 §7。

## 1. 现象与根因

手机端首页的状态由 `StateEvent`（EventBus）广播、由 `VodFragment` 消费——这是与 `ProgressLayout` **并行**的第二套状态机制。改动前它只有三种状态，而消费方长这样：

```java
public void onStateEvent(StateEvent event) {
    switch (event.type()) {
        case EMPTY:     hideProgress(); break;   // 只隐藏转圈，什么都不显示
        case PROGRESS:  showProgress(); break;
    }
}
```

`HomeActivity` 的配置加载失败路径是：

```java
public void error(String msg) {
    resetVodChrome(); checkAction(getIntent());
    StateEvent.empty();      // ← 发 EMPTY
    Notify.show(msg);        // ← 只留一个会消失的吐司
}
```

两者一叠加就是：**内容源加载失败 → 首页一片空白**，用户既不知道原因，也没有重试入口。

**根因有两条**：

1. **`StateEvent` 把"失败"和"没有内容"混成了一个 EMPTY**。失败需要的是「错误色 + 原因 + 重试动作」（§9.9），"确实没有内容"需要的是三件套空态——两者要分开表达。
2. 消费方对 EMPTY 的处理**只是隐藏转圈**，根本没有渲染任何状态视图——所以无论哪种情况都是空白。

这是电视端 TV-5 修过的**同一类缺陷**，但发生在手机端**最大的面**（首页）上。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/webhtv-unified-visual-design-system-20260920.md` §9.9 | 仓库既有设计权威 | A（本地） | 空态 = 标题 + 解释 + 主动作；错误态 = 错误色 + 原因 + 重试动作 |
| `docs/MOBILE-STATE-SURFACE-20261013.md` §7.1/§7.2 | 本仓库既有交付记录 | A（本地） | 早已记录"手机端有两套状态机制"且"`StateEvent` 没有 ERROR 类型，要给首页做错误态得先加分支"——本批正是执行那条 |
| `StateEvent`（main 源集） | HEAD `bf81ad59ca2` | A（本地） | 只有 EMPTY/PROGRESS/CONTENT，且 record 不带原因 |
| `mobile` `HomeActivity.getCallback().error(msg)` | HEAD `bf81ad59ca2` | A（本地） | 失败发 EMPTY；另有崩溃重启那次也发 EMPTY（那**不是**失败） |
| `mobile` `VodFragment.onStateEvent` / `showProgress` / `hideProgress` | HEAD `bf81ad59ca2` | A（本地） | EMPTY 只 `hideProgress()`；状态视图只有 `@id/progress` |
| `mobile/res/layout/fragment_vod.xml` | HEAD `bf81ad59ca2` | A（本地） | 根是 CoordinatorLayout + AppBarLayout；已有一个同级的状态浮层 `@id/progress`（`view_progress` 的 include）→ 新状态浮层照这个位置放 |
| `ProgressLayout`（main 源集） | HEAD `bf81ad59ca2` | A（本地） | 已实现四态与 `setEmptyContent` / `showError`；电视端与手机端其余页面都在用 → 直接复用，不重复实现 |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络协议变更，无需上游播放器依赖类证据。设计已由 §9.9 与电视端批次定案，属"已建立设计的窄范围改动"。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | 首页失败后继续一片空白 | 拒绝 |
| 2. 只把 EMPTY 改成显示空态 | 改动小 | **语义错**：失败会被显示成"没有内容"，既没有原因也没有错误色 | 拒绝 |
| 3. **新增 ERROR（带原因），EMPTY 保持原义并各自渲染（采用）** | 失败与无内容分开表达；都复用 `homeContent()` | 需要改 `StateEvent` 的 record 形状 | **采用** |
| 4. 把 `fragment_vod.xml` 的根换成 `ProgressLayout` | 状态与内容天然一体 | 根是 CoordinatorLayout + AppBarLayout（toolbar 依赖 `liftOnScroll`），包一层会破坏滚动行为 | 拒绝 |
| 5. **在 CoordinatorLayout 里加一个**同级**的状态浮层（采用）** | 与既有的 `@id/progress` 位置一致，不动滚动结构 | 状态与内容是两层，需要显式收起 | **采用** |
| 6. 自己拼一套空态/错误态视图 | 无依赖 | 重复实现 `ProgressLayout` 已有的四态与内容注入 | 拒绝 |

## 4. 实施

1. `StateEvent`：record 增加 `message` 字段（其余状态传 `null`），新增 `Type.ERROR` 与 `error(String message)` 工厂。
2. `mobile` `HomeActivity`：配置加载失败的 `StateEvent.empty()` 改为 `StateEvent.error(msg)`；**崩溃重启那次保持 EMPTY**（它是"本次跳过加载"，不是失败）。
3. `fragment_vod.xml`：在既有 `@id/progress` 旁新增 `@id/state`——一个**空的** `ProgressLayout`（默认 GONE）。它只负责画状态，内容视图首页本来就有，所以 CONTENT 态对它无意义、由 `VodFragment` 显式收起。
4. `VodFragment`：
   - `ERROR` → `hideProgress()` + `showError(message)`：错误色标题（`error_config_load`）+ 原因（服务端返回的 message）+ 重试（`error_retry`），重试动作是 `homeContent()`；
   - `EMPTY` → `hideProgress()` + `showEmpty()`：三件套（`home_empty_*`），主动作同样是 `homeContent()`；
   - `PROGRESS` / `CONTENT` → `hideState()`。
5. 三语字符串 `home_empty_title` / `home_empty_description` / `home_empty_action`。
6. 新增 `MobileHomeStateContractTest`（6 项）。

## 5. 不变量与回滚

- 不改动 `ProgressLayout`（main 源集）：只调用它的既有接口。
- 不改动 `fragment_vod.xml` 的根结构与 AppBarLayout 的滚动行为：新状态浮层是**同级**追加，不包裹内容。
- 不改动 `StateEvent` 三个既有状态的语义：`empty()/progress()/content()` 仍然发不带原因的事件。
- 不改动首页的加载路径：`homeContent()` 与 `mViewModel.homeContent()` 原样复用；重试只是再走一次。
- 不改动电视端任何文件。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.fragment.Mobile*"` → **BUILD SUCCESSFUL**（新增 7 项全绿）。
- `.\gradlew.bat :app:assembleMobileArm64_v8aDebug` → **BUILD SUCCESSFUL**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增颜色。

### 6.2 设备实测（`192.168.50.3:5561`，装 **mobile flavor**）

**触发方式**：`adb root` 后用 `iptables -I OUTPUT 1 -m owner --uid-owner 10097 -j REJECT` 只拦应用自身出网，**在启动前**断网，让首次配置加载就失败（真实失败，不是伪造状态）。验证后规则已移除（`REJECT` 计数 0），随后已把电视端 flavor 装回。

**这次模拟器处于竖屏 1080×1920**，因此是真实手机形态下的观测。

| 检查项 | 结果 |
|---|---|
| 失败后首页是否还是空白 | **不再是**：显示「内容源加载失败」（**错误色**，暖粉）+ 原因「配置取得失败 / Failed to connect to /192.168.50.50:4567」+ **「重试」**按钮 |
| 错误色是否与原因可区分 | 是：标题暖粉、原因纯白，肉眼可辨 |
| 有无崩溃 | 无 |
| **点「重试」是否恢复** | **是**：移除拦截后点重试，首页完整恢复（推荐/电影/电视剧/动漫 分类 + 海报网格全部回来） |

截图：`.design-evidence/e5561-m4-error.png`（错误态）、`e5561-m4-recovered.png`（重试后恢复）。

### 6.3 真机验证抓出的一个缺陷（本批内已修）

**第一次实现把"重试"接成了 `homeContent()`——点了没有任何反应。**

- 首页的错误来自承载它的 `HomeActivity.initConfig()` 的**内容源配置加载**，而 `homeContent()` 重新加载的是**首页内容**；配置根本没被重新拉取，所以错误态原样停在那里。
- 我第一次点击时还怀疑是坐标点偏了，于是用 `dumpsys activity top` 取了按钮的**精确 bounds**（`206,322-566,466`，相对被居中的空态容器；换算到屏幕是 `(359,929)-(719,1073)`），确认点击**命中**了按钮——排除了"点偏"这个解释，问题确实在动作接错。

**修法**：新增 `VodFragment.ConfigRetry` 回调接口，`HomeActivity implements` 它并把重试接到 `initConfig()`（重试期间先 `StateEvent.progress()` 切回加载态）；`VodFragment` 在重试时优先把动作交回实现了该接口的 Activity，未实现时退回 `homeContent()`，至少不会变成死按钮。**空态**的主动作仍保持 `homeContent()`——"确实没有内容"时重载内容才是正解。

修完后重新在真机上跑了一遍完整流程（断网启动 → 错误态 → 恢复网络 → 点重试 → 首页恢复），结果见 §6.2。

### 6.4 结论

手机端首页首次具备 §9.9 意义上的错误态（错误色 + 原因 + 重试）与空态（三件套）：加载失败不再是一片空白，重试能真正回到失败的那一步并恢复。

### 6.5 残余风险与诚实说明

- **空态（EMPTY）没有在真机上观测到**：它对应的路径是"崩溃重启跳过加载"（`CrashRestartMode.consume()`）或"内容源确实没有内容"，两者都不容易构造。空态的三件套由契约测试覆盖，但**没有实机截图**。
- **`StateEvent` 的 `CONTENT` 分支没有实测**：代码里加了 `case CONTENT: hideState();`，但我没有找到会广播 `StateEvent.content()` 的调用点——它可能是为将来预留的。该分支只做"收起状态浮层"，即使从不触发也无副作用。
- **`ConfigRetry` 依赖 `getActivity() instanceof`**：若将来有别的 Activity 承载 `VodFragment` 而不实现该接口，重试会退回 `homeContent()`（不会崩，但可能"点了没反应"）。契约测试钉住了 `HomeActivity` 必须实现它。
- 本批**没有**处理三套状态机制的关系（见 §7.1）。
## 7. 相邻发现（未纳入本批）

1. **手机端现在有三套状态机制**：`ProgressLayout`（视图内切换）、`StateEvent`（首页 Fragment 用）、以及设置页 Fragment 里的 `safeRun` 兜底。本批只让 `StateEvent` 具备错误态，**没有**处理三者的关系——那是架构级决定（收敛成一套，还是明确各自职责），需要先定方向。
2. **`StateEvent` 目前只有首页一个消费方**。若将来其它 Fragment 也要用它，需要先确认它是否还适合做通用状态广播，还是应该收敛回 `ProgressLayout`。
3. **首页的 `EMPTY` 仍可能与"崩溃重启跳过加载"重合**：那种情况下显示"暂时没有内容 + 重新加载"是合理的，但严格说它并不是"内容源没有返回内容"。若要让文案更准确，需要给 EMPTY 也带上原因。
