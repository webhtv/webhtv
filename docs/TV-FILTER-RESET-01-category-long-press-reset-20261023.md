# TV-FILTER-RESET-01 TV 分类按钮长按重置筛选（2026-10-23）

## Recovery anchor

- 目标：TV(leanback) 分类页顶部分类/分组按钮（`TypeAdapter` 的 `CustomTypeView`）长按 → 一次性清空该分类下全部筛选条件（地区/年份/类型/排序等）。
- 验收标准：1) 长按 DPAD_CENTER 约 550ms 触发重置且不额外触发点击切换；2) `mExtends` 清空并同步 `Value.selected=false`；3) 筛选面板可见时刷新为未选中态；4) 有实际变化才重新请求并 toast 提示；5) 短按行为不变（含原有"上方向键=刷新"冷却逻辑）。
- 状态：代码完成，验证与收尾进行中。
- 关联路径：CustomTypeView / TypeAdapter / TypeFragment / FolderFragment / VodActivity / HomeActivity / leanback 三份 strings / TvFilterResetLongPressTest。

## 需求来源

用户：需要在筛选面板加"长按重置筛选"能力；补充说明为"长按电视版的分组按钮取消下面的筛选"。即长按分类条上的分组按钮，清空该分组下的所有筛选条件。

## 落点分析

- 分类条控件：`app/src/leanback/res/layout/adapter_type.xml` 根节点是 `com.fongmi.android.tv.ui.custom.CustomTypeView`（id `text`），因此 `binding.text` 与 `binding.getRoot()` 是同一个 View。
- 短按：`CustomTypeView` 未消费 DPAD_CENTER，事件下发给 root 的 `OnClickListener` → `TypeAdapter.mListener.onItemClick(item)` → Activity 切分类/开关筛选。
- 既有长按占用：卡片长按（`VodListHolder`/`TypeFragment.onLongClick(Vod)`）= 收藏，故长按重置只能落在分类按钮或筛选 chip 上。选择分类按钮，符合用户描述。
- 筛选状态唯一数据源：`TypeFragment.mExtends`（`HashMap<String,String>`），请求走 `mViewModel.categoryContent(getKey(), typeId, page, true, mExtends)`；UI 选中态由 `Filter.Value.selected` 承载。因此重置必须同时清 `mExtends` 并把所有 `Value.selected` 置 false，不能只改视觉。

## 方案

- `CustomTypeView` 内置长按计时（`LONG_PRESS_MS = 550`，沿用 `HomeActivity.CONFIRM_LONG_PRESS_MS` 的既有手感基准）：
  - ACTION_DOWN(repeatCount==0) → 先复位 `longPressFired` 再 `App.post(longPressRunnable, LONG_PRESS_MS)`。
    复位是必须的：若上一次长按到点后配对的 ACTION_UP 没送达该 View（宿主 onPause、休眠、未 detach），
    latch 会残留，下一次短按就会既不重新计时、又被 UP 当成"已长按"吞掉。`App.post(runnable, delay)`
    自带 `removeCallbacks`，重复 arm 不会叠加；repeatCount!=0 的重复键不进该分支，故"重复键不重启计时"不变。
  - 到点 → 置 `longPressFired` 并回调 `LongPressListener.onLongPress()`；
  - ACTION_UP → 取消计时；若已触发则消费该次释放并复位（避免长按后又触发点击）；未触发则照常转发，保留短按点击；
  - 额外在 `performClick`、失焦、`onDetachedFromWindow` 中清理，避免残留回调。
- `TypeAdapter` 新增 `OnClickListener.onLongPress(Class item)`，绑定到 `CustomTypeView.setLongPressListener`。
- `TypeFragment.resetFilter()`：清空全部 `Value.selected`、`mExtends.clear()`；**仅在确有变化时**才在面板可见下
  `hideFilter()+showFilter()` 重建行、`onRefresh()` 并 `Notify.show(R.string.filter_reset)`。无变化时直接返回 `false`，
  不重建也不把面板滚回首行（`showFilter()` 内有 `postDelayed(scrollToPosition(0), 48)`）。
- `FolderFragment.resetFilter()` 转发到子 `TypeFragment`。
- `VodActivity.onLongPress` / `HomeActivity.onLongPress` 做守卫（当前分类、非 home、分类条内容可见），再调用重置。
- 新增字符串 `filter_reset`（en / zh-rCN / zh-rTW）。

## 取舍

- 未在筛选 chip 上做长按：chip 短按已是"选中/取消"，长按重置虽也可行，但用户明确指分组按钮；只改一处可把影响面压到分类条。
- 未改动 `KeyUtil` 增加全局长按工具：仓库无该工具，需求只涉及这一处控件，内联计时即可，避免跨模块扩散。
- 保留原有"上方向键=刷新"冷却逻辑不动，回归风险最小。

## 修改文件

1. `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomTypeView.java` — 长按计时 + `LongPressListener`。
2. `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/TypeAdapter.java` — 接口加 `onLongPress`，绑定监听。
3. `app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java` — `resetFilter()`。
4. `app/src/leanback/java/com/fongmi/android/tv/ui/fragment/FolderFragment.java` — `resetFilter()` 转发。
5. `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java` — `onLongPress`。
6. `app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java` — `onLongPress`。
7. `app/src/leanback/res/values{,-zh-rCN,-zh-rTW}/strings.xml` — `filter_reset`。
8. `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/TvFilterResetLongPressTest.java` — 新增契约测试。

## 验证

命令：

```
JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot" \
  "G:\Git\bin\bash.exe" ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.ui.custom.TvFilterResetLongPressTest \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac --continue
```

结果：`BUILD SUCCESSFUL`，`TvFilterResetLongPressTest` 4/4 通过（tests=4, failures=0, errors=0），`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。

覆盖面：长按到点只触发一次且吞掉随后的点击；未到阈值仍按短按点击；重复 ACTION_DOWN 不重启计时；
长按到点后 ACTION_UP 丢失时，下一次短按仍是正常点击（`aLostReleaseDoesNotSwallowTheNextClick`）。
该用例做过负向对照：把 DOWN 分支回退成旧的 `&& !longPressFired` 写法后，它会在断言处失败（4 tests, 1 failed），
证明用例确实能抓住该缺陷。

过程中修正的两点：

1. 方法名冲突：初版把取消计时的方法命名为 `cancelLongPress()`，与 `TextView.cancelLongPress()` 签名冲突导致编译失败（javac "无法覆盖 TextView 中的 cancelLongPress()"），改为 `cancelLongPressTimer()`。
2. 计时改走 `App.post/App.removeCallbacks`：`View.postDelayed` 依赖 attach，且 `CustomTypeView` 原有冷却逻辑已用 `App.post`，统一到同一 handler 后行为一致，也让 Robolectric 下可稳定断言（测试里通过 `ReflectionHelpers.setStaticField(App.class, "instance", new App())` 提供单例）。

环境备注（不属于本任务改动）：本机 CI 前需 `ANDROID_HOME=C:\Users\Maple\AppData\Local\Android\Sdk`（仓库无 local.properties）与 `-PchaquopyBuildPython=G:/Python310/python.exe`（Chaquopy 需要 Python 3.10）。

## 回滚

回滚本次提交即可（`git revert <commit>`）。改动全部限定在 leanback 分类条与筛选面板，无数据/协议/资源格式变更。
