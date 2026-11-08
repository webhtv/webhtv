# MOBILE-STATE-SURFACE-20261013：手机端空态仍是一句「这里什麽都没有」，加载指示器写死白色

## Recovery anchor

- 目标：手机端按设计文档 §9.9 与 §6.2 对齐空态（标题 + 解释 + 主动作，标题走手机 Display 档）、加载态（指示器改走语义角色）与错误态（错误色 + 原因 + 重试）；并修掉收藏页"删空不驱动状态机"的同款缺陷。
- 允许路径：`app/src/mobile/res/layout/view_empty.xml`、`app/src/mobile/res/layout/view_progress.xml`、`app/src/mobile/java/.../activity/KeepActivity.java`、`app/src/testMobile/java/.../ui/activity/MobileStateSurfaceContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `e939250ac16`。
- 验收：① 空态三件套存在且后两者默认 GONE；② 标题落在手机 Display 档（32–40sp）；③ 三个元素走壁纸语义角色、无裸色；④ 加载指示器不再写死颜色；⑤ 收藏页接入三件套且按钮有真实动作；⑥ 删除路径驱动状态机；⑦ 配置加载失败进入错误态且可重试；⑧ `testMobile` 定向测试全绿。
- 回滚：撤销本任务原子提交即可回到「单行 16sp 空态 + 写死白色指示器 + 删空留空白 + 失败只弹吐司」；无数据迁移。
- 交付坐标：提交 `46f0d5560da`（dev4），recovery tag `recovery/MOBILE-STATE-SURFACE-20261013/20261013101854`；未推送。
- 下一步唯一动作：无（本批已交付）。后续候选见 §7（`StateEvent` 与 `ProgressLayout` 两套状态机制的关系、其余宿主的空态接入）。

## 1. 现象与根因

手机端与电视端**同款问题**，只是晚了一批处理。设计文档 §9.9 / §6.2 的要求与 TV 批次完全相同：

| 位置 | 设计文档要求 | 改动前 |
|---|---|---|
| `mobile/res/layout/view_empty.xml` 标题 | 空态 = 标题 + 解释 + 一个主动作；**不能只显示一句"暂无数据"**（§9.9）；空态属 Display 档，手机 **32–40sp**（§6.2） | 只有一句 16sp 的 `error_empty`；解释与动作两个槽位虽在（TV-4 为编译所需补的）但默认 GONE、**没有任何宿主接入** |
| `mobile/res/layout/view_progress.xml` | 加载使用统一 spinner/progress，**不使用多种颜色动画**（§9.9） | `app:indicatorColor="@color/white"` 写死，与同屏 traffic 文字用的 `?attr/webhtvColorOnWallpaper` 是两份声明 |
| `mobile` `KeepActivity.loadConfig` 失败 | 错误态：**错误色 + 原因 + 重试动作**（§9.9） | 只弹一个会消失的吐司，页面毫无变化 |
| `mobile` `KeepActivity.onItemDelete` | 空态必须可达 | 删掉最后一条后**只退出删除模式、从不驱动状态机** → 用户看到的是一片空白，连空态都不出现（与电视端 TV-4 修掉的同款缺陷） |

**根因**：手机端从未按这两条规格实现状态层；`ProgressLayout` 提供的 `setEmptyContent` / `showError` 两个接口在手机端**一个都没被调用过**。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/webhtv-unified-visual-design-system-20260920.md` §9.9 / §6.2 | 仓库既有设计权威 | A（本地） | 三条规格与手机字阶档位 |
| 电视端批次 `docs/TV-EMPTY-LOADING-STATE-20261012.md`、`docs/TV-ERROR-STATE-20261012.md`、`docs/TV-ERROR-STATE-CLOSEOUT-20261013.md` | 本仓库既有交付记录 | A（本地） | 设计已定案：三件套结构、`ProgressLayout` 的两个接口、错误态语义。**本批是应用，不是重新设计** |
| `mobile/res/layout/view_empty.xml` / `view_progress.xml` | HEAD `e939250ac16` | A（本地） | 确认三处违规 |
| `mobile` `KeepActivity` | HEAD `e939250ac16` | A（本地） | `setEmptyContent` 从未调用；`onItemDelete` 不驱动状态机；`loadConfig` 失败只弹吐司 |
| `ProgressLayout`（main 源集） | HEAD `e939250ac16` | A（本地） | `setEmptyContent` / `showError` 已存在且已在电视端端到端验证；手机端直接复用 |
| 手机端 `KeepActivity` 的 `showContent(true, size)` 调用点 | HEAD `e939250ac16` | A（本地） | 首次加载走这条分支，删除路径照抄即可让空态可达 |

**关于对比度**：本批**不新增任何颜色**，只把已有的壁纸语义角色用到该用的地方（标题/解释/按钮/指示器）。因此不需要新的实测对比度——电视端那批测得的数字属于电视端底材，手机端壁纸不同，不能平移，本批也没有据此做任何断言。

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。设计已由电视端批次定案，按 AGENTS.md §7 属"已建立设计的窄范围改动"，不再重复最佳实践调研。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | 三条规格继续不满足 | 拒绝 |
| 2. 只把标题改到 32sp | 改动最小 | 仍是一句"暂无数据"，§9.9 的核心（解释 + 动作）没做 | 拒绝 |
| 3. **三件套 + 逐宿主接入 + 加载指示器语义化 + 错误态（采用）** | 与电视端同构；未接入的宿主渲染与改动前一致 | 需要新增测试源集契约 | **采用** |
| 4. 手机端也顺便改首页的失败路径 | "覆盖更全" | 手机端首页走的是**另一套状态机制**（`StateEvent` 事件总线），不是 `ProgressLayout`；混进来会让本批跨两套架构 | 拒绝，记为 §7.1 |
| 5. 把手机端与电视端的 `view_empty.xml` 合并成一个 | 少一份重复 | 两个 flavor 的尺寸与字阶档位本就不同（§6.2 手机 32–40sp / 电视 40–48sp），合并会牺牲规格 | 拒绝 |

## 4. 实施

1. `mobile/res/layout/view_empty.xml`：标题 16sp → **32sp**（§6.2 手机 Display 档下沿）；补注释说明两个槽位默认 GONE、由宿主按需接入。
2. `mobile/res/layout/view_progress.xml`：`app:indicatorColor="@color/white"` → `?attr/webhtvColorOnWallpaper`。
3. `mobile` `KeepActivity`：
   - `initView` 调用新增的 `setEmptyContent()`（标题「还没有收藏」/ 解释「在任意详情页按『收藏』，就会出现在这里。」/ 动作「去点播」→ `goHome()`，沿用 LiveActivity/VideoActivity 的回首页 flag 写法）；
   - `onItemDelete` 的回调补 `showContent(true, count)`，让删空后能进入 EMPTY 态；
   - `loadConfig` 的 `error(msg)` 保留吐司并进入错误态（原因取服务端 message，重试复用同一条 `loadConfig(config, item)` 路径）。
4. 三语字符串复用电视端批次已加的 `keep_empty_*` 与 `error_config_load` / `error_retry`（都在 `main/res/values*/strings.xml`，两个 flavor 共享）——**本批不新增字符串**。
5. 新增 `MobileStateSurfaceContractTest`（8 项）于 `app/src/testMobile/`，与电视端契约同构，含"删除路径必须驱动状态机"。

## 5. 不变量与回滚

- 不改动电视端任何文件：`view_empty.xml` / `view_progress.xml` 是**分 flavor** 的，本次只动 `mobile` 那一份。
- 不改动 `ProgressLayout`（main 源集）：它的两个接口在电视端已验证，本批只调用。
- 不改动手机端首页的 `StateEvent` 状态机制（见 §7.1）。
- 不改动 `view_empty.xml` 的 Lottie 资源、尺寸与 `translationY`（后者影响所有手机端空态，改动超出本批）。
- 不改动播放器、解码、渲染、字幕、音轨、网络与数据行为。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "...MobileStateSurfaceContractTest"` → **BUILD SUCCESSFUL in 3m 26s**（新增 8 项全绿）。
- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.activity.*"` → **BUILD SUCCESSFUL in 37s**（确认没有影响既有手机端测试）。
- `.\gradlew.bat :app:assembleMobileArm64_v8aDebug` → **BUILD SUCCESSFUL in 2m 11s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增裸十六进制，也未新增颜色。

### 6.2 设备实测（`192.168.50.3:5561`，API 28 / 1920×1080 / 280dpi）

**说明**：本会话没有手机形态的设备可用（分配给我的 `5561` 是电视形态模拟器，`5557` 是用户自己的真机、明确不可用）。因此采用"在 5561 上装 **mobile flavor**"的方式做**状态层渲染**验证——它能确认布局、字阶、配色与接线是否成立，但**不能**代表真实手机上的尺寸观感。验证后已把电视端 flavor 装回，设备恢复原状。

| 检查项 | 结果 |
|---|---|
| mobile flavor 是否正常启动 | 是（首页、分类、卡片网格、底部导航均正常，无崩溃） |
| 进入「我的收藏」 | 正常（右上角 ⋮ → 我的收藏） |
| **空态渲染** | Lottie + 「还没有收藏」（32sp，明显大于改动前）+ 次级色解释「在任意详情页按『收藏』，就会出现在这里。」+ 「去点播」按钮 |
| 空态接线是否崩溃 | 无崩溃——`initView` 里新增的 `setEmptyContent` 调用安全 |
| **主动作的可点性** | **首轮发现问题并已修**：改动前它是一个只有 `clickable` 的 TextView，渲染出来与解释文字**没有区别**，看不出可以点（见 §6.3）。改用 `Widget.WebHTV.Button.Tonal` 的 `MaterialButton` 后是一枚浅色胶囊，明确可点 |

截图：`.design-evidence/e5561-m1-home.png`（mobile flavor 首页）、`e5561-m1-menu.png`（⋮ 菜单）、`e5561-m1-empty.png`（修复后的空态）。

### 6.3 实机验证发现并修掉的一个缺陷（本批内）

第一次在设备上看到空态时，「去点播」是**一行普通文字**——因为 TV-4 为编译所需补的 `action` 槽位只是一个带 `clickable`/`focusable` 的 `TextView`，**没有任何背景**。在电视端这不成问题（电视端空态用的是自己那套带焦点环的 `selector_empty_action`），但手机端是触摸交互，**没有容器就没有可点的暗示**。

修法：把手机端 `view_empty.xml` 的 `action` 改为 `com.google.android.material.button.MaterialButton` + `style="@style/Widget.WebHTV.Button.Tonal"`（应用自己的语义按钮样式）。容器色与文字色由该样式成对提供，所以它**不**声明 `textColor`——契约测试里对应的断言也相应改成"必须用语义按钮样式而不是裸文本"。

### 6.4 结论

手机端空态达到 §9.9 的三件套与 §6.2 的手机 Display 档；加载指示器改走语义角色；收藏页删空不再留空白、配置加载失败可重试。定向测试、既有手机端测试与 mobile flavor 构建全绿，且在设备上确认了空态渲染与接线安全。

### 6.5 残余风险与诚实说明

- **没有在真实手机形态上验证。** 本会话无手机设备，5561 是电视形态模拟器。布局与字阶在 1920×1080 上成立，但在真实手机（例如 1080×2400）上 32sp 标题与 320dp 的解释换行宽度是否合适，**没有实测**。`description` 的 `maxWidth="320dp"` 是按手机宽度估的，建议在真机上确认。
- **删除路径与错误态没有单独实测。** 两者走的是电视端已端到端验证过的同一套 `ProgressLayout`（`showContent(true, size)` / `showError`），本批新增的只是调用点，由契约测试覆盖。触发收藏页的配置加载失败需要构造一条不可达的配置，本批没有做。
- **加载指示器的新颜色没有单独截图**：它只在加载窗口出现，本次没有抓到。改动只是把 `@color/white` 换成 `?attr/webhtvColorOnWallpaper`，在深色壁纸下取值相同。

## 7. 相邻发现（未纳入本批）

1. **手机端存在两套并行的状态机制。** `ProgressLayout`（`CONTENT/PROGRESS/EMPTY/ERROR`，视图内切换）与 `StateEvent`（EventBus 广播 `EMPTY/PROGRESS/CONTENT`，供首页与各 Fragment 消费）表达的是同一件事。手机端首页的配置失败走的是后者（`StateEvent.empty()` + 吐司），所以本批**没有**把首页接进错误态——那需要先决定这两套机制的关系（是收敛成一套，还是明确各自职责），属于架构级改动，不该塞进状态层的规格对齐批次。
2. **`StateEvent` 没有 ERROR 类型。** 即便将来要给首页做错误态，也需要先给它加一个 `ERROR` 分支并定义消费方行为。
3. **手机端其余 `ProgressLayout` 宿主（历史/文件）的空态同样只有标题。** 它们的"解释"与"动作"都是各屏的产品文案决策，建议按宿主逐个确认后接入，而不是由实现者代拟（与电视端 §7.3 的结论一致）。
