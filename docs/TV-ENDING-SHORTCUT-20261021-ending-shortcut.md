# TV-ENDING-SHORTCUT-20261021 TV 片尾区间上键快捷聚焦回归修复

## Recovery anchor

- 目标：TV（leanback）播放页在正片进入片尾可设窗口且已过中点时，隐藏控制栏短按上键必定聚焦「片尾」；片头区间、中途唤醒的「恢复上次控件」语义保持不变。
- 验收：①结尾窗口内上键 → 焦点在片尾（不受记忆焦点影响）；②片头区间存在记忆焦点时仍恢复该控件（不复现 09-29 回归）；③片中唤醒仍恢复记忆控件 / 回退下一集；④片头/片尾按钮上的 UP/DOWN 微调语义不变；⑤片头区间无记忆焦点仍聚焦片头，短片前半段仍给片头。
- 当前文件/符号：`app/src/leanback/.../VideoActivity.java` 的 `onKeyUp()` / 新增 `isEndingShortcutDue()`；`PlayerControlFocusIntegrationTest.leanbackEndingShortcutOutranksRememberedFocusNearTheEnd`。
- 下一步：跑编译与定向单测，`task_guard.sh finish` 提交并打 recovery tag。

## 用户反馈与结论

反馈：正片播放时按遥控器上键会跳到播放状态栏「片头」；设置片头成功后，正片快播完时再按上键不会跳到「片尾」。

结论：问题成立。片尾快捷聚焦入口在 2026-09-29 的 `c7fba76a46` 之后被「记忆焦点」门禁挡住，且该门禁在整场全屏播放内是粘滞的。

## 功能来源

- `VideoActivity.onKeyUp()`（leanback）来自上游 Fongmi，初始导入 `3192791d22`（2026-05-22）即存在。
- 逻辑：控制栏隐藏时短按上键唤醒控制栏，并按当前进度直接聚焦便捷按钮：
  - `canSetOpening(position, duration)` → 片头；
  - 否则 `canSetEnding(position, duration)` → 片尾；
  - 都不满足 → 记忆控件 / 回退「下一集」。
- 窗口：`Constant.getOpEdLimit(duration)`（<15 分钟 3 分钟；<30 分钟 6 分钟；其余 10 分钟），判定在 `PlayerManager.canSetOpening/canSetEnding`。

## 变更时间线

| 时间 | 提交 / 构建 | 影响 |
| --- | --- | --- |
| 2026-05-22 | `3192791d22`（初始导入） | 片头/片尾两个快捷聚焦分支都在，且无条件按进度生效 |
| 2026-09-29 12:11 +0800 | `c7fba76a46` `fix(tv): 影视原生唤醒控制栏优先恢复用户记忆焦点` | 两个分支一起被 `if (!hasRememberedFocus())` 包裹，片尾入口从此有条件生效 |
| 2026-09-28 21:52 | tag `v5.6.0-beta-202609282152` | 旧行为（片尾快捷聚焦正常） |
| 2026-09-30 16:54 | tag `v5.6.0-beta-202609301654` | 首个包含该提交的安装包，此后 beta 包开始出现本问题 |

`c7fba76a46` 的自述验证写明「设备行为未取证」，只补了源码契约测试，因此片尾分支被连带废掉没有被发现。

## 根因

1. **记忆焦点门禁过宽且粘滞**：`mFocus2` 只在进出全屏时清空（`VideoActivity.java` 的 `enterFullscreen()` / `exitFullscreen()`），控制栏隐藏后仍保留；`hasRememberedFocus()` 把「上一次落在控制栏内的任意非片头/片尾控件」都算作可用记忆焦点，包括默认回退聚焦的「下一集」、进度条、选集/字幕/弹幕等。用户本集内只要碰过一次控制栏（按过下键、点过任意按钮、进过面板），此后整场播放（跨集不恢复）的上键都不再走片头/片尾快捷聚焦；片头区间却仍会在无记忆焦点时生效，于是表现为「能跳片头、跳不了片尾」。
2. **分支顺序让短内容永远进不去片尾**：原实现先判片头。对短剧/短片（整段时长都落在两侧窗口内，例如 3 分钟一集、上限 3 分钟），`canSetOpening` 恒为真，片尾分支不可达。该缺陷早于 9-29。

## 决策

- 不整体回退 `c7fba76a46`：那会重新引入 09-29 已修的「片头区间唤醒被抢焦点」问题。
- 采用最小的非对称收敛：**片尾区间（且已过中点）的快捷聚焦优先于记忆焦点**；片头区间保留 09-29 的收敛语义。
- 加「已过中点」条件，使短片开场仍给片头；对长内容该条件恒成立（片尾窗口必定在后半程），不改变原本能命中的场景。
- 不改 `hasRememberedFocus()` / `getFocus2()` / `showControl()` / `PlayerControlFocusHelper`；不改手机 flavor 与融合播放页——融合页内联控制栏本来就没有片头/片尾唤醒快捷聚焦（`focusInlineDefaultControl()` 只回退 `playerNext`）。
- 按 AGENTS.md 第 7 条，本改动属已确立设计的窄范围修复（单方法、无新契约、无依赖/架构变化），免做外部最佳实践检索；依据是本地代码、Git 历史与既有源码契约测试。

## 实现

`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`

```java
@Override
public void onKeyUp() {
    long position = player().getPosition();
    long duration = player().getDuration();
    if (isEndingShortcutDue(position, duration)) {
        showControl(mBinding.control.action.ending);
        return;
    }
    if (!hasRememberedFocus()) {
        if (player().canSetOpening(position, duration)) { showControl(mBinding.control.action.opening); return; }
        if (player().canSetEnding(position, duration)) { showControl(mBinding.control.action.ending); return; }
    }
    showControl(getFocus2());
}

private boolean isEndingShortcutDue(long position, long duration) {
    return position > 0 && duration > 0 && position >= duration / 2 && player().canSetEnding(position, duration);
}
```

测试：`PlayerControlFocusIntegrationTest` 新增 `leanbackEndingShortcutOutranksRememberedFocusNearTheEnd`（断言片尾判定先于记忆焦点门禁、仍以 `canSetEnding` 为准、带后半程条件、落点为 ending 按钮）；既有 `leanbackControlWakeKeepsUserChosenFocusOutsideTheIntroRange` 继续锁住片头区间语义。

## 验证

- `bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.PlayerControlFocusIntegrationTest'` → `BUILD SUCCESSFUL in 3m`（110 tasks）。
- 定向单测 XML：`TEST-com.fongmi.android.tv.ui.activity.PlayerControlFocusIntegrationTest.xml` → `tests="11" skipped="0" failures="0" errors="0"`（原 10 项 + 新增 `leanbackEndingShortcutOutranksRememberedFocusNearTheEnd`）。
- 真机实测（设备 `192.168.50.3:5557`，SM-N9700，1920×1080，覆盖安装 leanback debug 包，`install -r` Success）：
  - 用例 A（片头窗口）：第 1 集 07:35/48:08，控制栏隐藏时按上键 → 控制栏唤出且焦点在「片头」（与修复前一致）。
  - 用例 B（结尾窗口 + 记忆焦点，本次回归的关键路径）：第 2 集 49:03/49:42，先把焦点移到「弹幕」等普通控件并隐藏控制栏（`mFocus2` 生效）→ 按上键 → 焦点落在「片尾」。修复前该路径走 `showControl(getFocus2())`，会回到记忆控件，不会到片尾。
  - 用例 C（片头语义保持）：第 2 集 00:15，记忆焦点为「弹幕」→ 按上键 → 焦点回到「弹幕」而非「片头」，确认 09-29 收敛未被破坏。
  - 说明：未在旧包上做对照复现（旧行为由 `c7fba76a46` 的代码路径直接判定）；测试期间未修改用户的片头/片尾设置值。

## 回滚

- `git revert <本次提交>` 恢复旧 `onKeyUp`；
- `task_guard.sh finish` 会生成 `recovery/TV-ENDING-SHORTCUT-20261021/<时间戳>` 标签。

## 残留风险 / 未验证

- 片尾已设过的剧集在结尾窗口仍会聚焦片尾（便于复核、长按清空），若误按确认会按当前位置重设片尾——与 09-29 之前的上游行为一致。
- 设备实测已完成（见「验证」）；未在旧包上做同一路径的对照复现，旧行为结论来自代码路径判定。
- 若用户实际使用融合播放页（内联控制栏），本修复不生效，该页没有片头/片尾唤醒快捷聚焦，需另立需求。
