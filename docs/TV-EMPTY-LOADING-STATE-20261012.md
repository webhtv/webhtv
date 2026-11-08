# TV-EMPTY-LOADING-STATE-20261012：电视端空态只有一句「这里什麽都没有」，加载指示器写死白色

## Recovery anchor

- 目标：电视端空态达到设计文档 §9.9 的「标题 + 解释 + 一个主动作」，标题走 §6.2 的 Display 档；加载指示器改用语义角色；收藏页接入真实空态内容。
- 允许路径：`app/src/leanback/res/layout/view_empty.xml`、`app/src/leanback/res/layout/view_progress.xml`、`app/src/leanback/res/drawable/shape_empty_action_focused.xml`（新增）、`app/src/leanback/res/drawable/selector_empty_action.xml`（新增）、`app/src/mobile/res/layout/view_empty.xml`（仅补两个默认隐藏的槽位，见 §4.6）、`app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java`、`app/src/leanback/java/com/fongmi/android/tv/ui/activity/KeepActivity.java`、`app/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvStateSurfaceContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `7c26d331001`。
- 验收：① 空态声明标题/解释/主动作三个槽位且后两者默认 GONE；② 标题字号落在电视 Display 档；③ 三个元素走壁纸语义角色、无裸色；④ 主按钮可聚焦且焦点环取**应用表面族**；⑤ 加载指示器不再写死颜色；⑥ `ProgressLayout.setEmptyContent` 对 flavor 差异做空值守卫；⑦ 收藏页接入且按钮有真实动作；⑧ 定向测试全绿 + 5561 真机空态对照。
- 回滚：撤销本任务原子提交即可回到「单行 16sp 空态 + 写死白色指示器 + 删空后空白页」；无数据迁移。
- 交付坐标：提交 `61483311b4d`（dev4），recovery tag `recovery/TV-EMPTY-LOADING-STATE-20261012/20261012232534`；未推送。
- 下一步唯一动作：无（TV-4 已交付）。下一批建议处理 §7.1 的错误态（需先新增"壁纸上的错误前景色"角色），或按 §7.3 逐宿主接入空态解释与动作。

## 1. 现象与根因

TV-1 勘察时把 TV 端的状态层也扫了一遍。改动前：

**空态**（`app/src/leanback/res/layout/view_empty.xml`）只有一个 Lottie 动画加一行 16sp 纯白文案 `@string/error_empty`（「这里什麽都没有 👀」）。对照本仓库自己的设计文档，它同时违反三条：

| 设计文档要求 | 出处 | 改动前 |
|---|---|---|
| 空态：**标题 + 解释 + 一个主动作** | §9.9 | 只有一句文案，没有解释、没有动作 |
| 空态**不能只显示一句"暂无数据"** | §9.9 | 正是"只显示一句" |
| 空态属 **Display** 档（电视 **40–48sp**） | §6.2 字阶表 | 16sp，比正文还小 |

**加载态**（`view_progress.xml`）的 `CircularProgressIndicator` 写死 `app:indicatorColor="@color/white"`，而同屏的码率文字用的是 `?attr/webhtvColorOnWallpaper`——同一语义两份声明，且指示器完全不随主题走。§9.9 要求「加载使用统一 spinner/progress……不使用多种颜色动画」。

**根因**是状态层当初只按"能显示"实现，没有按 §9.9/§6.2 的规格实现；而且 `view_empty.xml` 是**分 flavor** 的，两个 flavor 各写一份、都没有三件套。

### 1.1 真机验证时又发现一个更硬的缺陷：删空后连空态都不出现

在 5561 上把收藏逐条删空后，屏幕是**一片空白**——既没有列表也没有空态。查代码：

```java
// 改动前 KeepActivity
public void onItemDelete(Keep item) {
    mAdapter.remove(item.delete(), () -> {
        if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);   // 只退出删除模式
    });                                                                // 从不驱动状态机
}
```

空态只在 `getKeep()`（首次加载或收到 `RefreshEvent.KEEP`）里通过 `showContent(true, size)` 进入。也就是说：**用户在这个页面上把收藏删光，永远看不到空态**。而同一个仓库的 `HistoryActivity` 本来就写对了：

```java
mAdapter.remove(item.deleteDisplayItem(), () -> {
    mBinding.progressLayout.showContent(true, mAdapter.getItemCount());   // 驱动状态机
    if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
    else restoreFocus(...);
});
```

这是"两个同类宿主、一个做对一个漏掉"的典型，属于同一批状态层问题，因此在本任务内一并修掉并对齐写法。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 本仓库 `docs/webhtv-unified-visual-design-system-20260920.md` §9.9（第 729–734 行） | 仓库既有设计权威 | A（本地） | 空态三件套、禁止单行"暂无数据"、加载态统一 spinner、错误态"错误色 + 原因 + 重试" |
| 同上 §6.2 字阶表（第 502–511 行） | 仓库既有设计权威 | A（本地） | 空态 = Display 档，电视 40–48sp；电视最小正文 16sp、关键操作 ≥16sp |
| 本仓库 `app/src/leanback/res/layout/view_empty.xml` / `view_progress.xml` | HEAD `7c26d331001` | A（本地） | 单行 16sp + 写死 `@color/white`，确认三条违规 |
| 本仓库 `app/src/main/java/.../ui/custom/ProgressLayout.java` | HEAD `7c26d331001` | A（本地） | TV 的状态容器：CONTENT/PROGRESS/EMPTY，通过 `ViewEmptyBinding`/`ViewProgressBinding` 加载；**只有 EMPTY 态没有内容注入接口** |
| 本仓库 `app/src/leanback/res/drawable/shape_chip_round_focused.xml`（第 9–15 行注释） | HEAD `7c26d331001` | A（本地） | 明确了两族焦点环的边界：`?attr/tvPlayerRing` 用于**固定深玻璃**宿主，`?attr/tvFocusRing` 用于**应用表面**。空态画在壁纸上，必须用后者 |
| 本仓库 `dialog_exit_confirm.xml` | HEAD `7c26d331001` | A（本地） | 电视端按钮的既有形态：带 `android:background` 的 TextView（`clickable`/`focusable`），不是 MaterialButton |
| 真机截图（`192.168.50.3:5561`） | 本轮 | A | 空态实际观感；加载指示器为纯白 |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | 继续违反设计文档三条 | 拒绝 |
| 2. 只把标题改到 40sp | 改动最小 | 仍是一句"暂无数据"，§9.9 的核心（解释 + 动作）没做 | 拒绝 |
| 3. **补齐三件套 + 槽位默认隐藏 + 逐宿主接入（采用）** | 满足 §9.9；未接入的宿主渲染结果与改动前一致，不强制改变观感 | 需要新增接口与一处宿主接线 | **采用** |
| 4. 一次性给全部宿主接入解释与动作 | 一步到位 | 每个列表的"解释"与"动作"都是产品文案决策（首页/历史/文件/站点…），我不该替产品编 | 拒绝，留作逐宿主跟进 |
| 5. 空态按钮用现成的 `selector_button`（→ `shape_chip_round_focused`） | 不新增文件 | 该 drawable 的环取 `?attr/tvPlayerRing`（**固定深玻璃族**），而空态在壁纸上，用错族会重现阶段 M 修过的"同一屏两种环色" | 拒绝，新建应用表面族的 selector |

采用的空态结构：

| 元素 | 角色 | 字号 | 颜色 | 默认 |
|---|---|---|---|---|
| 标题 | `@id/text` | **40sp**（§6.2 电视 Display 档） | `?attr/webhtvColorOnWallpaper` | 可见 |
| 解释 | `@id/description` | 16sp（电视正文下限） | `?attr/webhtvColorOnWallpaperVariant` | **GONE** |
| 主动作 | `@id/action` | 16sp（关键操作下限） | `?attr/webhtvColorOnWallpaper` | **GONE** |

## 4. 实施

1. `app/src/leanback/res/layout/view_empty.xml`：标题 16sp → **40sp**，新增 `@id/description` 与 `@id/action`（均默认 GONE）。
2. 新增 `shape_empty_action_focused.xml`（28dp 胶囊 + `@dimen/webhtv_focus_ring_width` 环 + `?attr/tvFocusRing`）与 `selector_empty_action.xml`（焦点/按下 → 前者，常态 → 既有 `shape_button_normal`）。
3. `ProgressLayout` 新增 `setEmptyContent(titleRes, descriptionRes, actionRes, listener)`：参数先存、视图创建时应用；**不调用时渲染与改动前完全一致**。内部用 `findViewById` + 空值守卫而不是 `ViewEmptyBinding` 字段——`view_empty.xml` 是分 flavor 的，mobile 版没有这两个控件，而本类在 `main` 源集，直接用 binding 字段会让 mobile 编译失败。
4. `KeepActivity` 接入：标题「还没有收藏」、解释「在任意详情页按『收藏』，就会出现在这里。」、动作「去点播」（回首页，沿用 LiveActivity/VideoActivity 的 flag 写法）。
5. **修掉删除路径不驱动状态机的缺陷**（§1.1）：`onItemDelete` 的回调改为与 `HistoryActivity` 一致的 `showContent(true, count)`，使删空后能进入 EMPTY 态。
5. 三语字符串 `keep_empty_title` / `keep_empty_description` / `keep_empty_action`。
6. `view_progress.xml`：`app:indicatorColor="@color/white"` → `?attr/webhtvColorOnWallpaper`。
7. 新增 `TvStateSurfaceContractTest`（7 项）：三件套存在且后两者默认 GONE；标题落在 Display 档；三个元素走壁纸角色且无裸色（**精确比较**，避免 `…Variant` 因包含 `…OnWallpaper` 子串而误过）；主按钮可聚焦且环取应用表面族；指示器不写死颜色；`ProgressLayout` 用 findViewById 兼容 flavor；收藏页接入且按钮有真实动作。

### 4.6 为什么顺手改了手机端的 `view_empty.xml`

`ProgressLayout` 在 `main` 源集，而 `view_empty.xml` 是**分 flavor** 的：手机版没有 `description`/`action` 两个 id，于是 `findViewById(R.id.description)` 在手机变体里编译不过（实测报错 `找不到符号`，位置 `ProgressLayout.java:92`）。

两条路：把查找改成按名字反射（`getIdentifier`，慢且脆），或者让两个 flavor 都声明这两个 id。选了后者，并且**只补槽位、不改观感**：两者默认 `GONE`，且目前没有任何手机端宿主调用 `setEmptyContent`，因此手机端渲染结果可证明不变。手机端的字号档位（§6.2 手机 Display 为 32–40sp）与文案接入仍留给手机批次。

## 5. 不变量与回滚

- 手机端只补了两个默认隐藏的槽位（编译必需，见 §4.6），**不改观感**：手机端的空态标题字号、加载指示器颜色与文案接入都保持原样，留给手机批次（§7.2）。
- 不改动 `ProgressLayout` 的状态机（CONTENT/PROGRESS/EMPTY 的切换语义、`hideContent()`、`addOverlayView()`）。
- 不改动 `view_empty.xml` 的 Lottie 动画资源与尺寸。
- 不改动播放器、解码、渲染、字幕、音轨、网络与数据行为。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.Tv*" --tests "com.fongmi.android.tv.theme.*"` → **BUILD SUCCESSFUL**（含新增 `TvStateSurfaceContractTest` 8 项）。
- `.\gradlew.bat :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac` → 首轮 **FAILED**（`ProgressLayout.java:92 找不到符号`），原因是手机变体没有 `R.id.description`；按 §4.6 给手机版补上两个默认隐藏的槽位后 **BUILD SUCCESSFUL in 5m 15s**。
- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*"` → **BUILD SUCCESSFUL in 44s**。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增裸十六进制。

### 6.2 设备实测（`192.168.50.3:5561`，API 28 / 1920×1080 / 280dpi）

| 检查项 | 结果 |
|---|---|
| 冷启动 | 正常进入首页，无崩溃 |
| **删除路径缺陷复现** | 在收藏页逐条删空后，屏幕是**一片空白**——既没有列表也没有空态。确认 §1.1 的缺陷真实存在 |
| 修复后空态 | 删空后正确渲染：Lottie + 标题「还没有收藏」+ 解释「在任意详情页按『收藏』，就会出现在这里。」+ 按钮「去点播」 |
| 标题字号 | 层级明显：标题远大于解释，符合 §6.2 的 Display 档 |
| 标题/解释颜色 | 解释明显比标题暗（`webhtvColorOnWallpaperVariant`），两者不再同权重 |
| 按钮焦点 | `dumpsys activity top` 显示 `app:id/action` 为 `VFED..C.. .F......`（`F` = PFLAG_FOCUSED）；空态出现时按钮**自动获得焦点**（空态内唯一可聚焦项），焦点环为近白实心，肉眼清晰 |
| 按钮动作 | 按确认键后 `mCurrentFocus` 变为 `HomeActivityCurrent`，动作真实生效（不是死按钮） |

截图：`.design-evidence/e5561-tv4-empty.png`（空态整体）、`crop-action-focus.png`（按钮焦点环放大）；缺陷现场为同目录 `e5561-tv4-empty.png` 的修复前版本（已被覆盖，过程记录见 §1.1 与本节第一行）。

**数据变更披露**：为触发空态，在本模拟器上通过应用自身的删除模式清空了收藏列表（原有 3 条测试数据：南部档案 / 歌单详情 / 水龙吟）。这是模拟器上的可重建数据；未改动其它任何应用数据。

### 6.3 结论

空态达到设计文档 §9.9 的三件套与 §6.2 的 Display 档；加载指示器改走语义角色；删除路径不再留下空白页；定向测试、手机端编译与手机端回归全绿。

### 6.4 残余风险

- 空态标题从 16sp 提到 40sp 是**明显的观感变化**（也是本次唯一较大的视觉改动），依据是 §6.2 把空态列入 Display 档；若产品认为过大，改回 Headline 档只需改一个 `textSize` 并同步契约测试的下界。
- 解释与动作目前只有收藏页接入（§7.3）；其余宿主仍是"只有标题"，因此**在那些页面上空态仍不完全满足 §9.9**。
- 未在真机上验证加载指示器的新颜色（本次没有触发加载态的机会）；该改动只是把 `@color/white` 换成 `?attr/webhtvColorOnWallpaper`，在深色壁纸下取值相同，风险极低。

## 7. 相邻发现（未纳入本批）

1. **错误态（§9.9 第三条）没有做，需要先补一个角色。** §9.9 要求「错误态：错误色 + 原因 + 重试动作」。但 TV 的错误反馈目前是 `Notify.show(...)` 吐司、播放器内嵌错误层、以及设置页把 `error_config_get` 写进普通取值 TextView 三种形态。要让设置页的内联错误用"错误色"，不能直接用 `?attr/colorError`：它是**跟随调色板**的，浅色表的深红（`#B3261E`）压在深色壁纸上不可读。需要新增一个"壁纸上的错误前景色"palette-independent 角色（与 `webhtv_on_wallpaper` 同族），并配对比度契约——这是一次独立的改动，建议单独立项。
2. **手机端有完全相同的两个问题。** `app/src/mobile/res/layout/view_empty.xml` 同样是 16sp 单行（§6.2 手机 Display 档为 32–40sp），`view_progress.xml` 同样写死 `@color/white`。按"按端分批"的约定留给手机批次。
3. **其余空态宿主还没有接入解释与动作。** `ProgressLayout.setEmptyContent` 目前只有收藏页调用；首页、历史、文件、站点等宿主仍是"只有标题"。每一处的解释文案与动作都是产品决策，建议按宿主逐个确认后接入，而不是由实现者代拟。
