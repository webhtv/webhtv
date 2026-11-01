# C115 · TV 长按倍速释放残留修复（VideoActivity dispatchKeyEvent）

- 任务 ID: C115-tv-speed-hold-residue-20261031
- Lane: quick-fix
- 分支: dev2
- 基线 HEAD: 7174df94571585e8c65adc907affb7ff332e32b8
- 本地时间: 2026-10-31 +08:00
- 前置任务: C113（beta 合并评审）

## 1. 背景与起因

`7174df9457` "fix: keep TV hold-speed release from opening the playback menu" 修的是这样一个 TV 缺陷：长按 UP 进入倍速，松手时事件继续下落，命中键位状态机 `CustomKeyDownVod.check()` 的 `else listener.onKeyUp()` 分支，最终走到 `VideoActivity.onKeyUp()` 末尾的 `showControl(getFocus2())`，把播放菜单意外弹出来。

该提交的修法是在 `VideoActivity` 里加一个影子状态 `private boolean mSpeedBoostEngaged`：`onSpeedUp()` 置 true、`onSpeedEnd()` 置 false，`dispatchKeyEvent` 在调用 `releaseSpeed()` 前先把它捕获成局部变量，然后 `if (boostEngaged && KeyUtil.isUpKey(event)) return true;`。

对 `7174df9457` 做了 STANDARDS / SPEC 双轴独立评审（两个只读子代理，各自独立取证），结论分歧，由主代理逐行裁决。分歧与裁决记录见第 4 节。

## 2. 确认的缺陷

### D1 · 暂停态下原缺陷仍然复现（SPEC 轴发现）

`CustomKeyDownVod.check()` 的长按分支无条件置位 `changeSpeed = true`；而 `VideoActivity.onSpeedUp()` 开头是 `if (!player().isPlaying()) return;`。暂停时：

- `changeSpeed == true`（状态机认为在倍速）
- `mSpeedBoostEngaged == false`（`onSpeedUp()` 提前返回，从未置位）

释放 UP 时外层守卫命中（`isChangingSpeed() && isActionUp`），捕获到 false，执行 `releaseSpeed()` 后不 return，事件继续下落 → 控制栏隐藏时命中 `mKeyDown.onKeyDown(event)` → `check()` 中 `changeSpeed` 已经是 false → 走 `else listener.onKeyUp()` → `showControl(getFocus2())` → 弹菜单。

关键前提已核对：`onSpeedUp()` 只显示 `mBinding.widget.speed`，不显示 `mBinding.control.getRoot()`，所以倍速期间 `isGone(control.root)` 恒为真，下落路径确实可达，属实。

### D2 · 连击残留：其它按键的松开吞掉倍速状态（本次独立发现，两轴都标为 pre-existing）

boost 期间若先松开别的键（如 LEFT），其 ACTION_UP 命中原外层守卫 `isChangingSpeed() && isActionUp`：捕获影子状态、`releaseSpeed()` 清掉 `changeSpeed`，但因为是 LEFT 不是 UP，守卫不 return。随后 UP 的 ACTION_UP 到达时外层条件已不成立，直接下落 → `check()` → `changeSpeed` 已 false → `else onKeyUp()` → 弹菜单。与 D1 同源同症状。

## 3. 修复内容（已按用户裁决实施）

`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（-6 行）：

1. 删除字段 `private boolean mSpeedBoostEngaged;`（原 :423）。
2. `dispatchKeyEvent` 外层守卫收窄，并去掉影子状态捕获与条件返回：

```java
if (mKeyDown.isChangingSpeed() && KeyUtil.isActionUp(event) && KeyUtil.isUpKey(event)) {
    mKeyDown.releaseSpeed();
    if (isVisible(mBinding.lutQuick)) return dispatchLutQuickKey(event);
    return true;
}
```

3. 删除 `onSpeedUp()` 中的 `mSpeedBoostEngaged = true;` 与 `onSpeedEnd()` 中的 `mSpeedBoostEngaged = false;`。

`app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java`（+7 -4，`leanbackSpeedBoostReleaseIsGuarded`）：

- 原先断言源码里存在 `boolean boostEngaged = mSpeedBoostEngaged;` 与 `if (boostEngaged && KeyUtil.isUpKey(event)) return true;`、以及两处置位——这些断言把已被判定为缺陷的实现固化成了"正确"。
- 改为：截取守卫分支文本，断言整条条件 `if (mKeyDown.isChangingSpeed() && KeyUtil.isActionUp(event) && KeyUtil.isUpKey(event)) {`（只在 UP 释放时结束倍速）、分支以 `return true;` 结尾（不再下落到弹菜单路径），并新增 `assertFalse(leanback.contains("mSpeedBoostEngaged"))` 防止影子状态被重新引入。
- 起初只断言条件尾部 `KeyUtil.isUpKey(event)) {`，首轮再评审指出这样删掉 `KeyUtil.isActionUp(event)` 或追加 `&& false` 仍会通过，遂改为整条条件字面量匹配。
- 删除因此不再使用的 `speedUpBody` / `speedEndBody` 两个局部变量。

### 行为取舍（用户明确裁决）

- 暂停 + 控件隐藏时长按 UP 松手：不再弹菜单。短按 UP 仍然弹（`changeSpeed` 从未置位，守卫不命中）。
- 倍速期间松开非 UP 键：不再中断倍速，倍速保持到 UP 松手。

## 4. 双轴评审分歧与裁决

| 争点 | STANDARDS 轴 | SPEC 轴 | 裁决 |
| --- | --- | --- | --- |
| `mSpeedBoostEngaged` 是否冗余影子状态 | 不冗余：`onSpeedUp()` 的 `!isPlaying()` 早退使 flag 真实记录"倍速是否真的生效"，需保留 | 冗余且会失同步，建议直接 `if (KeyUtil.isUpKey(event)) return true;` | 采纳 SPEC 轴。外层条件已证明 `isChangingSpeed() == true`，flag 无信息量；"暂停时该不该弹菜单"属产品决策，已交用户裁决 |
| 暂停态长按松手是否应弹菜单 | 应保留（无 boost 生效，属原行为） | 属缺陷（R1 未满足） | 用户选定"松手也不弹菜单"，即无条件 return true |

STANDARDS 轴另提出 5 条判断性发现，其中 4 条（Divergent Change 一个概念两个所有者、8807 必须先于 8808 的隐藏时序耦合、`KeyUtil.isUpKey` 与 `CustomKeyDownVod:66` 的重复知识、`mSpeedBoostEngaged` 命名不够达意）在删除影子状态后一并消失。第 5 条"测试弱"一并按第 3 节重写。

两轴对同一事实的相反判断（flag 是否冗余）已经逐行核对裁决，未静默取舍；涉及用户可见行为的部分走 `ask_user_question` 由用户决策。

## 5. 验证

命令（`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`）：

```
.\gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
               :app:compileMobileArm64_v8aDebugJavaWithJavac \
               :app:testMobileArm64_v8aDebugUnitTest --tests "*VideoActivityLayoutTest*"
```

结果：`BUILD SUCCESSFUL in 37s`；三个 task 均真实执行（非 UP-TO-DATE）。
`app/build/test-results/testMobileArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest.xml`：`tests="157" skipped="0" failures="0" errors="0"`。

日志留存：`.codex/tmp-c115-verify.log`。

## 6. 修复后再评审

修复完成后派独立只读评审子代理复核（`f7b744a8-1635-408b-81ec-2838a7e3cba4`），STANDARDS / SPEC 双轴结论：**VERDICT: PASS**。

SPEC 轴逐条确认：

- D1 已消除：`:8805` 要求 `isUpKey`，暂停态 UP 释放仍进分支，`:8806` `releaseSpeed()` 后 `:8808` `return true`，早于 `:8826` 的 `mKeyDown.onKeyDown(event)` 下落点，故 `check()` 不再走到 `CustomKeyDownVod.java:68` 的 `else listener.onKeyUp()`。
- D2 已消除：LEFT/RIGHT/DOWN/ENTER 的释放不再命中外层守卫，`changeSpeed` 保留到 UP 松手。
- 影子状态彻底移除，全仓 grep 仅剩测试断言与本文档提及。

针对"倍速会不会卡死"的追问（Q1）结论是不会有卡死路径：倍速只能由 `:8826` → `CustomKeyDownVod.java:74-77` 启动（全屏 + 控制栏 GONE + lutQuick 隐藏）；非 UP 键释放不清 `changeSpeed`，而随后的 UP 释放仍在 `:8805` 被拦截（该守卫在 `:8817` 控制栏分支之前）；救援点 `:8709`(onWindowFocusChanged)、`:9225`(onStop)、`:9168`(cancelTvTouch)、`:9126`/`:9132`(touch up/cancel)、`:9111`/`:9116`(touch move) 均调用 `releaseSpeed()`，失焦、切后台、触摸接管都能结束倍速。

Q2 结论：无条件 `return true` 未新增误吞——只有 `isUpKey()` 且 `isChangingSpeed()` 为真时才会进入，而该状态只由长按 UP 分支产生；播放中的路径与 `7174df9457` 逐字节一致，唯一新吞掉的是暂停态释放，正是 D1 的本意。lutQuick 仍照旧在 `:8807` 分发，`onEpisodeKey` 只响应 DOWN，`isMediaRewind`/`isMediaFastForward` 不含 MEDIA_PREVIOUS，均无损失。

Q3 结论：短按 UP 从不置位 `changeSpeed`，`:8805` 不命中，仍下落 `:8826` → `CustomKeyDownVod.java:66-68` → `onKeyUp()` → `showControl(getFocus2())`，菜单照常弹出。

STANDARDS 轴结论：一个状态一个所有者，`changeSpeed` 成为唯一所有者，方法仍然短小。两条非阻塞判断：一是 `:8805` 与 `CustomKeyDownVod.java:66/`:74` 对"哪个键结束倍速"的知识重复（性质上未变）；二是 D2 的推论——倍速期间松开 LEFT/RIGHT 不再结束倍速（此前会结束），若 UP 的 key-up 在没有失焦/onStop 的情况下丢失，`changeSpeed` 会滞留到救援点，下一次 UP 按下会被用来结束倍速。范围窄、可自愈，且符合"长按 UP = 倍速"的语义。

## 7. 回滚

单提交、两文件、+9 −10，无资源/无布局/无 API 变化。`git revert <本次提交>` 即可完整回滚；回滚后恢复 `7174df9457` 的行为（含 D1、D2 两个缺陷）。

## 8. 未处理项（已路由说明）

- `CastActivity` 也 implements `CustomKeyDownVod.Listener`，其 `onSpeedUp()` 同样以 `if (!player().isPlaying()) return;` 开头，且经 `dispatchKeyEvent` → `super.dispatchKeyEvent(event)` 转发。投屏场景是否存在同源弹菜单缺陷未取证，且不在本次 TV 活动的用户报告范围内——记为后续候选，不在本次改动内。
