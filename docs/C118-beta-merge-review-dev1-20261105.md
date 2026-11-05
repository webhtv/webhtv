# C118 · dev1 合并 origin/beta 后的改动评审

- 日期：2026-11-05
- 分支：`dev1`
- 评审对象：合并 `origin/beta` 后工作区/未推送的全部改动
- 结论：**通过**（修复 2 处缺陷后）

## 1. 合并坐标

| 项 | 值 |
| --- | --- |
| 合并前 `dev1` | `842c03a0e13`（Merge pull request #525 from Silent1566/dev2） |
| 目标 `origin/beta` | `37bba96d24a`（Merge pull request #526 from Silent1566/dev3） |
| 合并方式 | fast-forward，无冲突 |
| 带入提交 | 26 个（插件框架 PLUGIN-01/02/03、Exo 能力位、IJK surface 重绑、弹幕本地包、内嵌全屏方向保持等） |
| 合并后 `dev1` | `37bba96d24a` |

合并前工作区存在一个未提交改动 `TmdbDetailActivity.java`，而带入提交 `a19433f7ef` 也修改同一文件，因此先 `git stash` 再合并，合并后 `stash pop` 自动合入且无冲突。

## 2. 评审范围裁剪

用户允许「先前已评审过且无合入冲突的改动可跳过」。据此核对：

- 合并前 `dev1` 相对 `origin/dev1` 领先的 8 个提交（IJK HLS AES-128 解密与密钥作用域修复等）**已全部包含在 `origin/beta` 中**（经 PR #525 合入），其任务记录见 `docs/IJK-HLS-AESDEC-04-ijk-encrypted-hls.md`，属于已交付内容，本次不重复评审。
- 唯一未被评审覆盖的改动是工作区未提交的 `TmdbDetailActivity` 芯片内边距修复（原 task-guard 会话 `CHIP-PADDING-DP` 遗留），本次评审即针对它。

## 3. 发现的问题

### P1（严重）：`applyChipFocus` 里的第二次 `applyChipPadding` 是死代码，且注释注释了一个不存在的机制

原改动在 `applyChipFocus(...)` 末尾追加：

```java
// Material 的 inset/描边路径会用当前 padding 重算背景，重建后把它写回，保持与创建时一致。
applyChipPadding(button);
```

证据（解包 `material-1.14.0.aar` 后对 `classes.jar` 做 `javap -p -c`）：

- `MaterialButtonHelper` 中唯三会写回 View padding 的方法是 `loadFromAttributes(TypedArray)`、`updateButtonShape()`、`setInsets(int,int,int,int)`（内部调用 `MaterialButton.setPaddingRelative`）。
- `updateButtonShape()` 只由 `setShapeAppearance(...)` 与 `setCornerSpringForce(...)` 触发。
- `applyChipFocus` 只调用 `setSelected`、`setStrokeWidth`、`setStrokeColor`；后两者走 `MaterialButtonHelper.setStrokeWidth/​setStrokeColor → updateStroke()`，只改 `MaterialShapeDrawable` 的描边，不触碰 View padding。

因此该调用不修复任何问题，注释所述机制在 material 1.14.0 中不存在，属于误导后来者的错误注释。

**修复**：删除该调用，并把注释改成已验证的事实（只有 `setInsets`/`updateButtonShape`/`loadFromAttributes` 会写 View padding）。

### P2（中）：常量注释的因果与事实不成立

原注释称：

> 那是像素而不是 dp，**并且会覆盖背景 selector 声明的 12dp/8dp 内边距**……取值与背景 selector（selector_video_item / 对应 shape）保持一致

核对：

- 该芯片由 `createChipButton` 用 `MaterialButton(this, null, R.attr.materialButtonOutlinedStyle)` 创建，背景是 `MaterialShapeDrawable`，并靠 `setBackgroundTintList` 上色；它从未使用 `selector_video_item`，也不存在被覆盖的「selector 声明的内边距」。
- 真实的缺陷只是**裸像素不随密度缩放**：24px 在 2x（320dpi）上等于 12dp、1.75x 上约 13.7dp、3x（480dpi）上仅 8dp，于是「按钮边框包不住文本」只在部分密度设备上复现。
- 12dp/8dp 的取值来源是同类芯片 `shape_video_item.xml`（`left/right=12dp`、`top/bottom=8dp`），属于取值对齐，而非「恢复被覆盖的内边距」。

**修复**：重写注释为该事实。

## 4. 保留的改动（评审通过部分）

- `CHIP_PADDING_H_DP = 12` / `CHIP_PADDING_V_DP = 8` 常量 + `applyChipPadding(MaterialButton)` 走 `ResUtil.dp2px`：与仓库既有约定一致（同文件 15 处、`TmdbPersonActivity`、`InlineEpisodeAdapter` 等均按 dp 换算写 padding）。
- `createChipButton` 增加 `setInsetLeft(0)` / `setInsetRight(0)`：与已存在的 `setInsetTop(0)` / `setInsetBottom(0)` 对称，且顺序在 `applyChipPadding` 之前（`MaterialButtonHelper.setInsets` 会基于当前 padding 重算，顺序错误会叠加内边距）。

## 5. 回归测试

在 `app/src/testMobile/java/.../TmdbDetailActivityLayoutTest.java` 新增
`metaChipPaddingIsDensityIndependentAndMatchesTheVideoItemShape`：

1. 两个 dp 常量存在且为正；
2. 常量值与 `shape_video_item.xml` 的 `android:left` / `android:top` 相等（防止取值漂移）；
3. `createChipButton` 必须调用 `applyChipPadding`，且不得再出现 `setPadding(24, 12, 24, 12)`；
4. `applyChipPadding` 必须经 `ResUtil.dp2px` 换算；
5. `applyChipFocus` 不得再调用 `applyChipPadding`（钉死 P1 的修复，防止死代码回潮）。

## 6. 验证

- 目标单测：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest' --no-daemon`
- 结果：BUILD SUCCESSFUL，见下方「验证记录」。

## 7. 风险与遗留

- 未在真机/多密度设备上做像素级目视回归（无可用设备）；行为变化仅为密度换算，低风险，且 2x 密度下取值与原像素表现等价（24px=12dp）。
- 合并带入的插件框架其余代码本轮不在评审范围（属 `origin/beta` 已交付内容）。

## 验证记录

| 轮次 | 命令 | 结果 |
| --- | --- | --- |
| 1（修复后） | `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest' --no-daemon` | BUILD SUCCESSFUL in 4m 21s |
| 2（变异验证） | 同上，临时把 `applyChipPadding(button);` 加回 `applyChipFocus` | `metaChipPaddingIsDensityIndependentAndMatchesTheVideoItemShape FAILED`，BUILD FAILED in 1m 51s（守卫确实会红） |
| 3（还原后复跑） | 同上 | BUILD SUCCESSFUL in 1m 4s |

即新增用例对 P1 的修复具备可失败性（非永真断言）。

## 8. 二次评审结论

修复后重新评审本轮全部改动：

- `TmdbDetailActivity.java`：+22/-1（常量 + dp 换算 helper + 对称 inset + 已修正的注释），无其它副带改动。
- `TmdbDetailActivityLayoutTest.java`：+44/-0（一条新用例 + 两个私有解析 helper + 两个 import）。
- `git diff --check` 无空白错误。
- 未改动 `app/build.gradle`、资源、其它模块。

**通过**。
