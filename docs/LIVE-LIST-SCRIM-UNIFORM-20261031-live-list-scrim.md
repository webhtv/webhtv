# LIVE-LIST-SCRIM-UNIFORM-20261031 · TV 直播列表遮盖层改为整块统一不透明度

## Recovery anchor

- 目标：把 TV 直播页频道抽屉（列表）的黑色遮盖层从「左浓右淡的三段渐变」改为**整块统一**，滑块线性映射到这块纯黑的 alpha（用户 m00008：调节到 100% 也只有左边缘变黑，右边没变化，"这个地方不用渐变，统一调节透明度就可以"）。
- 验收标准：
  1. `LiveListScrim.create()` 返回整块单色纯黑（`ColorDrawable`），源码里不再有 `GradientDrawable` / `BASE_ALPHAS`。
  2. 滑块 100% → 整块 alpha 1.00；20% → 整块 alpha 0.20；未设置 → 整块 alpha 0.80（= 旧的左端 `black_80`）。左右边缘一致，不再是 0.80/0.40/0.05。
  3. 静态 XML 回落 `shape_live_list.xml`（leanback + mobile）同步改成一整块 `black_80`，任何绕过 Java 路径的宿主也不会再渐变。
  4. 只缩放 alpha，色相恒为纯黑 —— `shape_live_focused.xml` 的白色焦点环对比度前提不变（整块 80% 黑只会比原来的右侧 5% 更暗，对比度更好）。
  5. leanback 与 mobile overlay（横屏）两个宿主继续走 `LiveListScrim.create(this)`；mobile 的 classic / embedded 形态不变。
- 分支 dev5，base HEAD `5daef84b85be953b5374ff5fcf17dbef60f95c22`。
- guard scope：`LiveListScrim.java`、`LiveListScrimTest.java`、`LiveListScrimWiringTest.java`、`LiveListScrimRenderTest.java`、两个 `shape_live_list.xml`、`shape_live_focused.xml`、本文件。

## 问题定位

上一轮任务（`docs/LIVE-LIST-SCRIM-OPACITY-20261029-live-list-scrim.md`）只把三段渐变的**整体浓度**做成可调：

```
gradientColors(opacity): scale = clamp(opacity) / DEFAULT_OPACITY
                         colors[i] = withAlpha(min(1, BASE_ALPHAS[i] * scale))
BASE_ALPHAS = {0.80, 0.40, 0.05}
```

所以滑块到 100% 时三段变成 1.00 / 0.50 / 0.06 —— 右端从 5% 只涨到 6%，肉眼完全看不出变化，正是用户观察到的「只有左边缘变黑，右边没变化」。功能没坏，是**形态**不符合诉求：可读性由右端的低 alpha 决定，而滑块对右端几乎没有杠杆。

## 设计决策

1. **去掉渐变，改单色 `ColorDrawable`**：用户明确要求"不用渐变，统一调节透明度"。整块 alpha = 滑块值本身（线性，不再除以 0.80 再分段缩放）。
2. **默认值仍是 0.80**：未设置槽位时整块 80% 黑，等于旧渐变的**最浓**一端，观感不会比现在更透；旧渐变的中/右端本来就要被替换，因此默认观感变化是本次需求的固有部分（用户要的就是右侧也变黑）。
3. **XML 回落同步改平**：两个 `shape_live_list.xml` 从 `<gradient>` 改成 `<solid android:color="@color/black_80" />`。布局里的静态引用保留，未接入主题路径的宿主回落到整块 80% 黑而不是透明。
4. **不改槽位/范围/文案**：`LIVE_LIST_SCRIM_OPACITY` 槽、`MIN/MAX_LIVE_LIST_SCRIM_OPACITY = 0.20f/1f`、三语言字符串全部不动——本次只改渲染形态，编辑器语义不变（此前"自动"显示 80%，现在同样 80%，且 100% 现在名副其实）。
5. **焦点环结论不变**：`shape_live_focused.xml` 的注释按新实现改写（原来引用的三段渐变已不存在），白色环的对比度论证只会更成立。

## 变更清单

| 文件 | 变更 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/theme/LiveListScrim.java` | 删 `BASE_ALPHAS`；`create()` 改 `new ColorDrawable(withAlpha(opacity))`；删 `gradientColors()`；`withAlpha` / `resolveOpacity` 不变 |
| `app/src/test/java/com/fongmi/android/tv/theme/LiveListScrimTest.java` | 三处数值断言改成单值断言；源码断言从 `GradientDrawable.Orientation.LEFT_RIGHT` 改为 `new ColorDrawable(` + 反向断言 `GradientDrawable`/`BASE_ALPHAS` 不存在 |
| `app/src/test/java/com/fongmi/android/tv/theme/LiveListScrimWiringTest.java` | 新增 `theStaticDrawableFallbackIsOneFlatBlackInBothHosts`（两份 XML 必须是 `<solid>`、必须含 `@color/black_80`、不得含 `<gradient>`）；类注释同步 |
| `app/src/leanback/res/drawable/shape_live_list.xml` | `<gradient angle=0 ...>` → `<solid android:color="@color/black_80" />` |
| `app/src/mobile/res/drawable/shape_live_list.xml` | 同上 |
| `app/src/leanback/res/drawable/shape_live_focused.xml` | 只改注释（宿主描述从"固定深色渐变"改为"整块 black_80 纯黑"） |
| `app/src/testLeanback/java/com/fongmi/android/tv/theme/LiveListScrimRenderTest.java` | 新增：Robolectric @GraphicsMode(NATIVE) 把 drawable 栅格化到 400x8 Bitmap，逐列断言整块 alpha（0xCC / 0xFF / 0x33），4 个用例 |

## 验证

### 1. 单元测试（静态 + 源码断言）

| 测试文件 | 用例数 | 结果 |
| --- | --- | --- |
| `app/src/test/java/com/fongmi/android/tv/theme/LiveListScrimTest.java` | 7 | tests=7 failures=0 errors=0 |
| `app/src/test/java/com/fongmi/android/tv/theme/LiveListScrimWiringTest.java` | 8 | tests=8 failures=0 errors=0 |
| `app/src/testLeanback/java/com/fongmi/android/tv/theme/LiveListScrimRenderTest.java` | 4 | tests=4 failures=0 errors=0 |

```
gradlew --offline testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.LiveListScrim*'   # exit=0
gradlew --offline testMobileArm64_v8aDebugUnitTest  --tests 'com.fongmi.android.tv.theme.*'               # exit=0
```

### 2. 像素级行为测试（Robolectric 栅格化）

静态断言看不出"渐变 vs 整块"，因此新增 `LiveListScrimRenderTest`，用 Robolectric NATIVE 画布把一个 400×8 的 Bitmap 交给 `LiveListScrim.create()` 之后逐列读 alpha：

| 槽位值 | 左端 x=0 | 中段 x=200 | 右端 x=399 | 结论 |
| --- | --- | --- | --- | --- |
| 未设置 | 0xCC | 0xCC | 0xCC | 整块 80%，与 XML 回落逐值一致 |
| 1.00 | 0xFF | 0xFF | 0xFF | 滑块拉满时整块纯黑（本次修复的核心） |
| 0.20 | 0x33 | 0x33 | 0x33 | 低值下整块均匀变淡，色相仍为纯黑 |

同一张表在旧实现下右端分别是 0x0D / 0x10 / 0x03，正是用户"右边没变化"的量化来源。

### 3. 编译与安装

```
gradlew --offline assembleLeanbackArm64_v8aDebug        # exit=0
# app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk (195.6MB)
adb -s emulator-5562 install -r -t <apk>                # Success
```

### 4. 设备实拍：本环境不可行（如实记录）

emulator-5562（NX627J / Android 9 / x86_64 / 1920×1080）上 APK 安装成功、`LiveActivity` 能正常启动（`logcat` 有 `Displayed com.silent.android.webhtv/...LiveActivity`、Media3 `playWhenReady … USER_REQUEST`，crash buffer 为空），但该设备是全新安装，`tv.db` 为 4096 字节空库（`sqlite_master` 无任何表），prefs 里也没有任何直播源地址。频道抽屉依赖已配置的直播列表，因此主页与直播页都只有黑屏和一个 0 KB/s 缓冲圈，无法唤出抽屉做像素采样。emulator-5560（HD1910）状态相同。

结论：设备端像素实测在本工作环境缺少必要数据，不能作为证据；本次行为证据来自第 2 节的 Robolectric 逐列栅格化断言（覆盖的正是用户报障的那一列差异），以及第 3 节的编译/安装链路。

### 5. 回归面

- 槽位、范围（0.20–1.00）、编辑器文案、"自动"参考值（`ThemePreviewView` 仍返回 `DEFAULT_OPACITY`）全部未改，`ThemeProfileValidatorTest` / `ThemeProfileCodecTest` / `ThemeEditorContractTest` 无需改动。
- mobile 的 classic / embedded 形态不受影响：embedded 仍用 `shape_live_embedded_list`，classic 仍为 `R.color.transparent`，只有 overlay（横屏）分支走 `LiveListScrim.create(this)`，与 leanback 共用同一实现。
