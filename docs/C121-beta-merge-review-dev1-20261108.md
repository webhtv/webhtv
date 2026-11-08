# C121 · dev1 合并 origin/beta 后的改动评审

- 日期：2026-11-08
- 分支：`dev1`
- 评审对象：合并 `origin/beta` 后工作区的全部未提交改动
- 结论：**通过**（两轮评审；第一轮发现 1 个 P2 缺陷已修复并复评）

## 1. 合并坐标

| 项 | 值 |
| --- | --- |
| 合并前 `dev1` | `13050a007ba`（Merge pull request #531 from Silent1566/dev2） |
| 目标 `origin/beta` | `7c64c9f5f11`（Merge pull request #535 from Silent1566/dev2） |
| 合并方式 | **fast-forward**，无冲突、无合并提交 |
| 带入提交 | 13 个，37 文件 / 2947 insertions |
| 合并后 `dev1` | `7c64c9f5f11` |

`dev1` 的 HEAD 是 `origin/beta` 的祖先（本分支历史已随 PR #531 进入 beta），因此合并没有产生分叉。
合并前工作区已有 3 个未提交改动，其 diff base blob 正好等于 `origin/beta` HEAD 中对应 blob，
所以 `git stash` → `git merge` → `git stash pop` 全程零冲突，3 个改动逐字节保留。
6 个历史 stash 全部原样保留（`stash@{0}` 由 `pop` 正常消费，非人为 `drop`）。

## 2. 评审范围裁剪

用户允许「先前已评审过且无合入冲突的改动可跳过」。据此核对：

- 带入的 13 个提交均在 `dev2`/`dev4` 分支上经过评审后进入 beta（记录见对应任务文档与
  `recovery/C120-*` 标签），本次为无冲突带入，不重复评审。
- `dev1` 相对 `origin/dev1` 领先的 21 个提交，其历史内容已全部包含在 `origin/beta` 中
  （经 PR #531 合入），本次不重复评审。
- **唯一未被任何历史评审覆盖的是工作区的 3 个未提交改动**，本任务即针对它们。

## 3. 被评审的改动

内联加载圈（手机端影视原生详情页）修复，3 个文件：

| 文件 | 改动 |
| --- | --- |
| `TmdbDetailActivity.java` | 加载圈网速改用 `sampleSpeedTextNow()`；加载中隐藏中心播放键；`tintTextTree` 跳过 `playerLoading`；删除未使用的 `Traffic` import |
| `PlayerOsdController.java` | 新增 `sampleSpeedTextNow()`（带 500ms 节流）；删除已无调用者的 `sampleSpeedText()` |
| `activity_tmdb_detail.xml` | `playerLoading` 从 `detailControlHost` 之前移到之后（压在控制栏上层） |

## 4. 第一轮评审（全量）

### 结论：通过（无 P1、无阻塞 P2），提出 1 个 P2 + 4 个 P3

先核实为「正确」的关键判据（均附代码级证据）：

- **`tintTextTree` 的剪枝不是死代码**：`TmdbDetailActivity.java:2483-2494` 确实递归遍历
  `ViewGroup` 子 View（2489-2493），新 return 位于染色（2489）与递归（2492）之前，
  因此整棵 `playerLoading` 子树被剪掉，`playerLoadingTraffic` 不再被 `colors.primary` 染色。
- **注释里的颜色数值属实**：`webhtv_color_player_control` 在 `values/` 与 `values-night/`
  都是 `#FFFFFF`，但真正覆盖它的是 `textView.setTextColor(colors.primary)`；
  `ThemeColors.light()` 的 primary 为 `0xFF12202D`，与注释一致。
- **`updateMobileInlinePlayVisibility` 无 NPE、无状态冲突**：全仓仅 `TmdbDetailActivity.java`
  4 处引用 `R.id.play`，改动前**没有任何代码**设置该 View 的可见性，因此
  `setVisibility(VISIBLE)` 恢复的就是 XML 默认态，不会显示本不该显示的按钮；
  `INVISIBLE` 而非 `GONE` 保留了 `center` 的 wrap_content 占位，且
  `ViewGroup.dispatchTouchEvent` 会跳过非 VISIBLE 子 View，不拦截触摸。
- **布局层级移动无副作用**：父子关系未变（仍在同一 `MaterialCardView` 内），
  仍 `match_parent`，整条链不可点击不可聚焦，新 z 序下仍低于 `lutQuick`。
- **import 清理正确**：删除后全文件无 `Traffic` 符号引用。

### P2-1（真实缺陷，已修复）

`sampleSpeedTextNow()` 调用的是有状态的 `updateSpeed(...)`，而 `PlaybackSpeedMeter.observe`
**每次调用都无条件推进** `lastTimeStamp` 与两路计数器基线。该方法又挂在
`updateInlineLoadingSpeed()` 上，后者经 `updateInlineLoading()` → `updateInlineDisplayPanel()`
在本文件有 20+ 调用点。控制栏隐藏且仍在加载时，OSD 1s 循环、加载圈 1s 定时器、
每次 UI 刷新三路共用同一 meter，采样窗口被切到毫秒级，零增量分支随即把读数归零，
表现为 OSD 底部网速抖动或偶发闪 0。

### P3（未做，见第 7 节）

P3-1 `sampleSpeedText()` 已成死代码（已随 P2-1 一并清理）；
P3-2 加载中只隐藏 play、未隐藏 prev/next；
P3-3 play 可见性由隐式副作用维护、未纳入 `updateMobileInlineButtons` 可见性总表；
P3-4 手机端默认焦点可能落到 INVISIBLE 的 play。

## 5. 修复

仅动 `PlayerOsdController.java` 一个文件：

```java
/** 强制采样间隔下限：该 meter 还被 OSD 1s 循环与加载圈 1s 定时器共用。 */
private static final long SPEED_SAMPLE_THROTTLE_MS = 500L;
private long lastSpeedSampleAt;

public String sampleSpeedTextNow() {
    long now = SystemClock.elapsedRealtime();
    if (now - lastSpeedSampleAt >= SPEED_SAMPLE_THROTTLE_MS) {
        lastSpeedSampleAt = now;
        updateSpeed(source.getPlayer());
    }
    return lastSpeedText == null ? "" : lastSpeedText;
}
```

同时删除已无调用者的 `sampleSpeedText()`，并移除其 javadoc 中指向自身方法的 `@link`
（改为指向现存常量），不留坏链。

## 6. 验证

| 项 | 结果 |
| --- | --- |
| 编译 | `.\gradlew.bat :app:compileMobileArm64_v8aDebugJavaWithJavac --offline` → **BUILD SUCCESSFUL** |
| 合并冲突 | 无（fast-forward），`git diff --diff-filter=U` 为空，无 `MERGE_HEAD` |
| 改动范围 | 3 文件，`11+2` / `23+2` / `28+26`，无新增未跟踪文件 |
| 死代码清理 | 全仓 `sampleSpeedText(` 0 匹配，仅剩 `sampleSpeedTextNow(` 的定义与调用 |

注意：`:app:compileDebugJavaWithJavac` 在该工程不存在（`app` 有 `flavorDimensions = ["mode","abi"]`），
必须使用带变体的任务名。

## 7. 第二轮评审（增量，针对修复）

### 结论：通过，可提交

逐条核实结果：

- 节流方向正确（`>=`）、`lastSpeedSampleAt` 只在采样分支内推进、方法只有一条 `return`，
  不存在「节流命中却返回空」的路径；`SystemClock.elapsedRealtime()` 为单调时钟，
  不受系统校时影响，选型正确。
- `lastSpeedSampleAt` 初值 0，首次调用必真采样，加载圈第一次即有读数。
- 非 null 保障是双保险：`updateSpeed` 的两条分支（`player == null` → `resetSpeed()` 写 `""`；
  正常路径 → `speedMeter.getText()` 只返回 `""` 或格式化串）都不会泄漏 null。
- 无残留调用点、无坏 `@link`、无反射字符串引用。
- `import android.os.SystemClock` 仅一处，位于 `android.os.Build` 与 `android.text.TextUtils`
  之间，符合该文件 import 分组风格。
- 未越界：`TmdbDetailActivity.java` 与 XML 的 numstat 与第一轮完全一致，mtime 早 48 分钟。

**P2-1 消除情况（量化）**：

- 移动端：`inlineOsd.setSuppressed(Util.isMobile())`，`render()` 在 suppressed 分支提前返回，
  渲染路径完全不推进 meter，只剩节流后的单路 → 采样窗口最小 500ms，毫秒级窗口问题彻底消失。
- TV 端（`suppressed=false`）：强制路径 ≤2 次/秒 + 渲染路径 1 次/秒，最坏合计 3 次/秒。
  因两条路径的节流基线不共享，理论上强制采样可紧贴渲染采样发生（窗口 ≈1ms），
  触发零增量归零还需该窗口内两路计数器都没涨字节（相位概率约 0.2%/次强制采样）。
  命中后果为**单帧显示 0 KB/s、≤500ms 内自愈、纯显示层**，无崩溃、无系统性漂移。

## 8. 已知未做项（用户已确认不处理）

| 编号 | 内容 | 不做的理由 |
| --- | --- | --- |
| P3-1（二轮） | `updateSpeed` 内同步 `lastSpeedSampleAt`，让两路共享节流基线，彻底消除 TV 端 1ms 窗口 | 残留风险为单帧、自愈、纯显示层；加固会改变「每个 1s tick 必真采样」的现有性质，收益不抵行为变更风险 |
| P3-2（一轮） | 加载中连 `prev`/`next` 一起隐藏 | 属行为扩展，超出本次「加载圈压住播放键」的修复边界 |
| P3-3（一轮） | 把 play 可见性并入 `updateMobileInlineButtons` 可见性总表 | 契约重构，非缺陷；当前 20+ 路径可自恢复 |
| P3-4（一轮） | 手机端默认焦点回退前增加 `isVisibleInHierarchy` 判断 | 手机以触控为主，影响有限 |
| 二轮 P3-2 | 常量注释只列了 2 路，javadoc 写了 3 路 | 两者不矛盾（常量注释未声称「仅」两路），只是详略不同 |

## 9. 交付坐标

- 修复提交、recovery tag、PR 编号见本文件提交后的回填记录。
