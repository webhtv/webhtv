# C114-beta-merge-review-dev3-20261031

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（注意远端已移除/回退的提交不得随合并带回来）；评审 `dev3` 已提交未推送的 3 个提交
  以及本次合并解冲突后改动的代码；有必修项就修复并验证，通过后再评审，循环直到通过；然后提交、推送 `dev3`、
  创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后归零（`origin/beta` 成为 HEAD 的祖先），`beta` 侧 51 个路径一个不丢、不多带，
     且回退类提交一个都不被带回。
  2. 评审覆盖 3 个未推送提交（33 个路径）与 4 个手工解冲突文件的合并结果；每条结论有可复核的 `路径:行号` 证据。
  3. 每个必修项均已修复并有会变红的守卫；修复后复评通过。
  4. 双 flavour 编译通过；主题定向单测 0 失败；全量单测失败集与 `origin/beta` 基线**逐条同名**（无新增失败）；
     `git diff --cached --check` 空。
  5. 改动经任务守卫 `finish` 落盘并打 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` + `docs` + `catvod` + `chaquo` + `scripts`）
- **任务守卫**：`C114-beta-merge-review-dev3-20261031`，base HEAD `dccfa6b79bff4f01305bc69af491139227b8189a`，branch `dev3`
- **当前状态**：合并、4 轮评审、必修项修复、复评、验证完成；提交/推送/PR 见 §8

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `134bb29dc46f754662510de425153077461505fd`（"Merge pull request #505 from Silent1566/dev5"） |
| 合并前本地 | `dev3` = `dccfa6b79bff4f01305bc69af491139227b8189a`（"Let the item overlay slots drive selected text and the rest tier"） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false** |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **19 / 3** |
| merge-base | `ba802dc4327dcb2433dfecb2f2dfd5c591621e86` |
| 冲突预检 | 未预检；直接 `git merge --no-commit --no-ff origin/beta` 得到 **4 个冲突文件** |
| 冲突文件 | `app/src/main/java/com/fongmi/android/tv/theme/{ThemeEditor,ThemePreviewView,ThemeProfile,ThemeProfileValidator}.java` |
| 冲突成因 | `dev3` 新增 `ITEM_OVERLAY_OPACITY` 等等级槽，`beta` 新增 `LIVE_LIST_SCRIM_OPACITY` 槽，两边落在同一批位置 ⇒ 只能取并集 |
| 合并动作 | 4 个文件全部手工解冲突为并集后 `git add`；`git diff --name-only --diff-filter=U` 空 |
| 合并规模 | 暂存 **51 个路径 / +4385 −70**；`.git/MERGE_HEAD` = `134bb29dc46f…` |
| 合并纯净性（正向） | `git diff --cached --name-only` 与 `git diff --name-only ba802dc43 origin/beta` 做 `Compare-Object` → **空**（没有一条路径在 `beta` 之外） |
| 合并纯净性（反向） | 两侧路径集合相等 ⇒ `beta` 侧 51 个路径一个不丢 |

`beta` 侧本次带入的 19 个提交（全部是 `beta` 已评审/已合并内容，本轮只做保真性核对，不重复评审）：

- `998172a4c8a2b3294e6300d52f7c6c5e760e6c26` fix(following): 修复追更源检查静默失败与集数进度停更
- `23f51a773643307ff3a4b9f5bb6352ced7285373` perf(leanback): cut playback-enter main-thread stalls
- `efdf8be13e295cbd692490e55cfba98740a78325` docs(following): 新增 FOLLOW-3 追更 TMDB 匹配设计
- `3639181e3e9618f15738c95b1f1603447aa289e4` merge: 合并远端 beta ba802dc432 到 dev2
- `380aad38da69281235292c09beb2c688a87cc47b` merge(C111-dev1): 合并 origin/beta ba802dc432 并修复 5 处问题
- `77b302540dc886f408c28cefa16a4f500a10ec98` Merge pull request #502 from Silent1566/dev1
- `bcdd344ec5c8a4ffe8b3a9d215cad1161c13a7b8` feat(theme): 直播列表遮盖层可配置（LIVE_LIST_SCRIM_OPACITY）
- `15cf4ee1e29f13be2fae132e09428c663bf199cb` merge+fix(following): 合并并修复追更检查静默失败
- `66b40a1338fa086ae506cb87dfc2b8c72e03d086` Merge pull request #503 from Silent1566/dev2
- `8566ea696a0b0d78e8347a4fbcacf82c223d638a` merge(C112): 合并 origin/beta 66b40a1338 到 dev5
- `d039c39f33181aeac0ae6abc609b58c75bb884b3` docs(theme): 记录 LIVE_LIST_SCRIM_OPACITY 的真机验证证据
- `f6aea3c4b01de1842d6f78f2b6e764d7c241db5a` Merge remote-tracking branch 'origin/beta' into dev4
- `8a093c73638466599f646edb85963e35077d672b` merge: 合并 origin/beta 66b40a1338f 并完成 dev4 未推送代码评审
- `248cdfe111d451ab9477b36c91faf08297d9f838` docs(C113): 回填交付坐标
- `f7f6fc3c4de1993ec66b7a32c3c3790456bebe50` Merge pull request #504 from Silent1566/dev4
- `448d33d7a412ccbb573a75fea4be1bb3b630b2a9` fix(home): 类型栏结构性写入统一走布局守卫
- `189c494ae9892dbc79910d95d5007279490cdb69` docs(C112): 回填 PR #505 与 delivery 坐标
- `5daef84b85be953b5374ff5fcf17dbef60f95c22` merge(C113): 合入 origin/beta f7f6fc3c4d
- `134bb29dc46f754662510de425153077461505fd` Merge pull request #505 from Silent1566/dev5

**「远端已移除的提交不得带回」的专项核对**（这是本次目标里写明的风险项，不靠"应该没问题"过关）：

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| `beta` 是否删除过路径 | `git diff --name-only --diff-filter=D ba802dc43 origin/beta` | **空** ⇒ 合并不可能把 `beta` 主动删掉的文件当作"新增"再带回来 |
| 区间内是否存在回退提交 | `git log --grep='revert\|回退\|撤销\|回滚' -i ba802dc43..origin/beta` | 仅命中 `448d33d7a4`（提交正文出现"撤销"二字，不是 Revert） |
| 全库 Revert 提交是否落在本区间 | `git log --oneline --all --grep='^Revert'` | 共 10 条，**没有一条**在 `ba802dc43..origin/beta` 内 |
| 合并树相对 `beta` 的删行 | `git diff --cached origin/beta` | 只删 13 行，全部是 `dev3` 有意的重写（见 §2），不存在"把 `beta` 的新代码删回去" |

## 2. 评审范围

需要新评审的是 `dev3` 相对 `origin/dev3` 的 **3 个未推送提交**（净差异 **33 个路径 / +1672 −86**），
以及本次合并解冲突后的结果：

- `538e0e7f2a24d22732941d6970ab3d7e6474deae` Make the TV focus colour follow the theme profile
- `d9ea5e497645d927f0aead0aa63b0e0477397b03` Make the TV show-item overlay and selected text themeable
- `dccfa6b79bff4f01305bc69af491139227b8189a` Let the item overlay slots drive selected text and the rest tier

交集判定：`beta` 侧 51 个路径与 `dev3` 侧 33 个路径的**交集有 10 个**，其中 5 个主题主文件与 3 个 `strings.xml`
在前一步就产生了 4 个冲突文件，另外两个是 `ThemeEditorContractTest.java` 与 `ThemeEditorPanelTest.java`
（两侧都新增了断言，git 自动合并成功但语义必须复核）。因此本轮**必须重新评审**，不能复用 C111/C113 的结论：

- `app/src/main/java/com/fongmi/android/tv/theme/{ThemeController,ThemeEditor,ThemePreviewView,ThemeProfile,ThemeProfileValidator}.java`
- `app/src/main/res/values/strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml`
- `app/src/test/java/com/fongmi/android/tv/theme/ThemeEditorContractTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/theme/ThemeEditorPanelTest.java`

合并结果相对 `origin/beta` 的 13 行删除全部核对过，都是有意的语义统一：

| 文件 | 被删内容 | 判定 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java` | 8 行旧 Javadoc | `dev3` 改写焦点色语义后的等价说明 |
| `app/src/main/java/com/fongmi/android/tv/theme/ThemeEditor.java` | `return ordinal() <= WARNING.ordinal();` | 改为显式列出不透明度槽，语义等价且更抗插入 |
| `app/src/main/java/com/fongmi/android/tv/theme/ThemePreviewView.java` | `case ON_SURFACE, ON_SURFACE_VARIANT -> "Aa";` | 改为再并入 `ON_ITEM_OVERLAY` |
| `app/src/main/java/com/fongmi/android/tv/theme/ThemeProfile.java` | 2 行 Javadoc（"19 slots"/"14 色 + 5 强度"） | 更新为 22 / 16+6 |

评审沿 Standards 与 Spec 两轴各出结论，修完复评，共 4 轮，直到两轴都 PASS。

## 3. 评审结论与必修项

共 **2 个必修项**，均已修复并复评通过。

### F1 解冲突后 `LIVE_LIST_SCRIM_OPACITY` 不再是最后一个槽，`beta` 的位置守卫直接变红

`beta` 在 `app/src/test/java/com/fongmi/android/tv/theme/ThemeEditorContractTest.java:239-257` 留了一条位置守卫：
取 `ThemeEditor.Slot.values()` 的最后一个元素，断言 `assertEquals("LIVE_LIST_SCRIM_OPACITY", last.name())`、
`assertFalse(last.isColor())`，再验证该槽的取值范围是 `0.20f..1.00f`（`0.19f`、`1.01f`、`NaN` 必须被拒）。

按并集解冲突时把 `dev3` 的 `ITEM_OVERLAY_OPACITY` 排在末尾，于是这条守卫的**位置前提**被打破：

```
ThemeEditorContractTest > theLiveListScrimSlotIsTheLastSlotAndUsesItsOwnRange FAILED
org.junit.ComparisonFailure at ThemeEditorContractTest.java:242
```

这不是"改一下测试"就能收口的事情——位置守卫本身在防止"新插入的槽被当成颜色槽读"，把它删掉等于削掉
`beta` 的防线。修法是**让两侧断言同时成立**：把枚举顺序调整为
`… SCRIM_OPACITY, DIALOG_OPACITY, OVERLAY_OPACITY, PLAYER_SCRIM_OPACITY, ITEM_OVERLAY_OPACITY, LIVE_LIST_SCRIM_OPACITY`
（`app/src/main/java/com/fongmi/android/tv/theme/ThemeEditor.java:46` 收尾在 `LIVE_LIST_SCRIM_OPACITY`），
并在 `:39-45` 用 Javadoc 写清"位置固定在末尾"是给 `beta` 守卫用的。

之所以重排是**行为中性**的：`dev3` 已经把 `isColor()` 从 `ordinal()` 比较改成显式 switch
（`ThemeEditor.java:53-57`，列出 `SCRIM_OPACITY, DIALOG_OPACITY, OVERLAY_OPACITY, PLAYER_SCRIM_OPACITY,
LIVE_LIST_SCRIM_OPACITY, ITEM_OVERLAY_OPACITY -> false`，其余为颜色），全仓 `Slot.values()` 的消费点只有
`ThemePresets.java:153`（逐个重放差异槽，与顺序无关）与两处测试遍历，都不依赖序号。
修复后定向单测 244 例 0 失败。

### F2 `ThemeWebBridge` 新增的三个只读字段完全没有断言

`dev3` 在 `app/src/main/java/com/fongmi/android/tv/theme/ThemeWebBridge.java:30-32` 给页面补了
`itemOverlay` / `onItemOverlay` / `itemOverlayOpacity` 三个只读派生字段，但
`app/src/test/java/com/fongmi/android/tv/theme/ThemeWebBridgeTokenTest.java` 只断言了原有的 13 色 + 三个强度，
**三个新字段零覆盖**：字段名拼错、取错 token（`itemOverlay` 误取 `colorOnItemOverlay`）、
`itemOverlayOpacity` 不随 profile 变化，全都会静默通过。

修法：`ThemeWebBridgeTokenTest.java:54` 新增 `itemOverlayFieldsMirrorTheResolvedTokens`（light+dark 两个
`ThemeTokens` 都核对 `#RRGGBBAA` 与两位小数强度）、`:65` 新增 `itemOverlayFieldsFollowTheProfileSlots`
（把 `profile.light.itemOverlay` 设为 `#203040`、`itemOverlayOpacity` 设为 `0.50f`，要求解析后
JSON 里出现 `"itemOverlay":"#80203040"`、强度 `0.50`），并在 `:105` 补 `hex(int)` 辅助方法。
新增的两条断言经变异验证确实会红（见 §5）。

## 4. 建议项的处理

两轴累计给出的建议级意见按主题去重，处置标准同样是"会不会改变用户可见行为或降低守卫强度"：

| 建议 | 处置 |
| --- | --- |
| `ThemeWebBridge.java:29` 原注释写"共 19 个，仍远低于『固定上限』约定的 24" | **改注释**。字段实际是 20 个（13 色 + focusScale + 3 强度 + 本轮 3 个），且全仓只有 `docs/webhtv-unified-visual-design-system-20260920.md:1328` 的 `// 只读、受控字段、固定上限` 提过"固定上限"却**没有给数字**，24 这个数无从复核。改成可核对表述"一共 20 个字段，字段集合仍由本类固定，页面只读" |
| `ThemeEditorContractTest.java:243` 注释仍写 `isColor() decides by ordinal()` | **改注释**。`isColor()` 已改为显式 switch，注释与实现不符；改为"位置仍是守卫的一部分" |
| `ThemeRole.isUserSlot()` 仍用 `ordinal() <= WARNING.ordinal()`（`ThemeRole.java:95`） | **不改**。`ITEM_OVERLAY/ON_ITEM_OVERLAY` 是用户可编辑槽但**故意**不算 baseline 重写槽，用序号表达"前 13 个"是稳定契约，且 `ThemeBinderContractTest.java:34` 断言 `assertEquals(13, userSlots)` 把它锁住了 |
| `ThemeColorIndex` 跳过整个 overlay 家族 | **不改**。`#66000000` 是键盘按键、搜索/输入框、玻璃面板共用色，重写会连带改坏一批本该固定的资源；已由 `rewritesBaseline()` 明示并写进 Javadoc |
| `docs/C113-beta-merge-review-dev4-20261030.md:136/:185/:207` 仍写"19 个槽 = 14 色 + 5 强度" | **不改 `beta` 文档**（该文件属 `beta` 侧产物，改动会污染合并纯净性）。本篇 §1/§3 已按 22 = 16 色 + 6 强度记录，并在此声明 C113 数字被本次合并取代 |
| `ThemeProfile.java:5/:79` 的 "22 user-editable semantic slots"/"16 color + 6 opacity" | **已由解冲突修正**，与枚举实际数量一致，无需再动 |
| 为 item-overlay 机制单独补一篇设计文档 | **本轮不做**（YAGNI + 范围控制）。机制说明已内嵌在 `ThemeBinder`/`ThemeResolver` 的 Javadoc 与 `ThemeBinderItemOverlayTest` 的用例注释里；记入 §7 遗留 |
| `ThemeProfileCodec` / `ThemeTransfer` 补新槽的映射 | **不需要改**。codec 是 schema 驱动（Gson + `validateJsonBounds` + `ThemeProfileValidator`），不做逐槽枚举；老 profile 缺槽时字段为 `null`，`ThemeResolver` 回落到冻结基线 `#66000000`/onPrimary ⇒ 导入导出与旧备份不受影响 |

## 5. 变异验证（证明新守卫真的会红）

| 守卫 | 变异 | 结果 |
| --- | --- | --- |
| `ThemeEditorContractTest#theLiveListScrimSlotIsTheLastSlotAndUsesItsOwnRange`（`beta` 侧） | 把枚举末尾排成 `ITEM_OVERLAY_OPACITY`（即首次解冲突的结果） | **变红**：`org.junit.ComparisonFailure at ThemeEditorContractTest.java:242`；重排回 `LIVE_LIST_SCRIM_OPACITY` 收尾后 244 例 0 失败 |
| `ThemeWebBridgeTokenTest#itemOverlayFieldsMirrorTheResolvedTokens`（新增） | `ThemeWebBridge.java:32` 的 `alpha(safe.colorItemOverlay())` → `alpha(safe.colorOnItemOverlay())` | **变红**：`java.lang.AssertionError at ThemeWebBridgeTokenTest.java:60`，`7 tests completed, 2 failed`（job `pwsh-21f7f0354b754708`，exit 1）；改回后 `BUILD SUCCESSFUL in 48s`，7 例 0 失败（job `pwsh-78d3c014045940b7`） |

## 6. 验证证据

| 检查 | 结果 |
| --- | --- |
| 双 flavour 编译 | `gradlew.bat --offline compileMobileArm64_v8aDebugJavaWithJavac compileLeanbackArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL in 5m 10s`，exit 0；修复后再次与主题单测同跑 `BUILD SUCCESSFUL in 48s`，exit 0 |
| 主题定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*'` → 33 个测试类共 **246** 例，**0 失败**（首次解冲突后为 244 例 1 失败，即 F1） |
| `beta` 交界契约测试 | `TvSearchSurfaceContractTest` + `HomeTypeRecyclerLayoutGuardTest` → `BUILD SUCCESSFUL`，exit 0 |
| 全量双 flavour | `testLeanbackArm64_v8aDebugUnitTest` **4814** 例（8 失败 3 跳过）/ `testMobileArm64_v8aDebugUnitTest` **5583** 例（6 失败 3 跳过） |
| 基线对照 | leanback 的 8 个失败类与 `origin/beta` 基线**逐一同名**：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`NativeEnhancedPlaybackStyleFocusTest`、`ReaderPlaybackRoutingSourceTest`×2、`SearchResultDownFocusTest`、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`；mobile 为同样的清单去掉两个 leanback 专属类 ⇒ **零新增失败**。这些失败全是 `core.autocrlf=true` 工作区里的 CRLF 字面量/源码扫描断言，与本次合并无关 |
| 空白/冲突 | `git diff --cached --check` 空；`git diff --name-only --diff-filter=U` 空；`git grep` 冲突标记只命中 `cacert.pem`/`freetype` 文档/`.txt` 资源等既有假阳性 |
| 未做 | 未走 CI；未做设备实测（本轮无布局数值变化，item-overlay 的视觉口径由 `dev3` 原提交负责） |

## 7. 遗留与后续项（本轮不做）

- **item-overlay 机制缺独立设计文档**：`ThemeBinder` 的两档填充权重（`ITEM_OVERLAY_FULL=100`/`ITEM_OVERLAY_REST=50`）、
  `itemOverlayStates` 白名单、`ConstantState` 共享导致"改容器后必须按索引重取子 drawable"等约束目前只写在
  代码 Javadoc 与测试注释里，没有 `docs/` 级说明。
- **`docs/C113-beta-merge-review-dev4-20261030.md` 的槽数已过期**（19 → 22）。该文件是 `beta` 侧产物，本轮不改，
  以本篇 §1/§3 为准。
- **`ThemeRole.isUserSlot()` 仍依赖序号**：只要"13 个用户色槽"这一契约不变就安全，新增用户色槽时必须
  同时移动 `WARNING` 的位置，否则会被静默判成"非用户槽"。
- **单测仍是源码字符串/枚举位置断言式**：能锁住结构与契约，真正的视觉数值仍靠设备实测。
- **全量失败集是环境基线**：`MpvFontConfigTest` 等 8 类在 `origin/beta` 上同样失败，属 CRLF 工作区既有问题，
  不在本轮范围。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `a2d4a04faa62d9ec4f5997f7527825692e87c4e4`（"merge: 合并 origin/beta 134bb29dc4 并完成 dev3 未推送代码评审, 主题槽位并集解冲突 + 2 项必修修复"），父提交 `dccfa6b79b…` + `134bb29dc4…`，相对第一父 37 个路径 / +1905 −88 |
| 恢复标签 | `recovery/C114-beta-merge-review-dev3-20261031/20261031043140-a2d4a04faa62`（本地 + 已推送） |
| 推送 | `git push origin dev3` → `5fc26a8990..a2d4a04faa  dev3 -> dev3` |
| PR | **#509** <https://github.com/Silent1566/webhtv/pull/509>（base `beta` ← head `dev3`，`changedFiles=37`、`additions=1905`、`deletions=88`，中文描述含合并内容/冲突判定/必修项/变异验证；**只创建，不合并**，状态 OPEN） |
| 拉取远端 | `git fetch --all --prune --tags`；收尾时 HEAD = `a2d4a04faa…`，`git rev-list --left-right --count origin/beta...HEAD` = **0 / 4**，工作区只剩 3 个任务前既有的未跟踪路径 |
| 本轮修正产物 | `app/src/main/java/com/fongmi/android/tv/theme/ThemeEditor.java`、`app/src/main/java/com/fongmi/android/tv/theme/ThemeWebBridge.java`、`app/src/test/java/com/fongmi/android/tv/theme/ThemeEditorContractTest.java`、`app/src/test/java/com/fongmi/android/tv/theme/ThemeWebBridgeTokenTest.java`、`docs/C114-beta-merge-review-dev3-20261031.md` |

`gh` 未设默认仓库（`origin` 是 `Silent1566/webhtv`、`upstream` 是 `fish2018/webhtv`），所有 `gh` 命令需显式带 `-R Silent1566/webhtv`。
