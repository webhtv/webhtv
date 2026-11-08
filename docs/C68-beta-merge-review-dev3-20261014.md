# C68 · dev3 合并 beta（无新提交）与「电视版实验室遥控焦点」7 提交复评

- 任务 ID：`C68-beta-merge-review-dev3-20261014`
- 分支：`dev3`；本任务开始前 tip = `b2bb4a249b9b2c9385e322d0652086facdf60aa1`
- 合并对象：`origin/beta` = `f398ced4dd355b68547dfeea440aa6516df4f62e`
- 复评对象：dev3 已提交未推送的 7 个「实验室遥控焦点」提交（`978bfa1e26`、`9fe00309e9`、`64228db4d0`、`c3c3504d5f`、`2812ae02e1`、`4fab3b0360`、`b2bb4a249b`）
- 访问日期：2026-10-14（Asia/Shanghai）

## Recovery anchor

- 目标：合并 `origin/beta` 最新代码；对 dev3 已提交未推送的实验室遥控焦点改动做 Standards + Spec 双轴复评，修正问题并验证通过；然后提交、推送 `dev3`、创建到 `beta` 的 PR（只创建不合并）。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/lab/**`、实验室相关 `app/src/main/res/drawable/{selector_lab_*,shape_lab_*,shape_lab_group_tab_focused}.xml`、`app/src/main/res/layout/{activity_lab_detail,dialog_lab_settings}.xml`、`app/src/test/java/com/fongmi/android/tv/lab/**`、`docs/LAB-TV-REMOTE-FOCUS-20261013.md`、本文、`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`。
- 保护既有脏文件：0 个（任务开始时工作区干净）。
- 验收标准：合并后净差异不含被 beta 回退的提交内容（零复活、零丢失）；复评发现的阻塞/重要问题全部修正并跑通定向单测；`dev3` 推送成功且 PR 已创建（不合并）。
- 当前状态：**已实施、已验证、已交付**（合并为空操作；双轴复评得到 1 项重要 + 3 项次要问题并全部修正；31 例 lab 单测 0 失败）。
- 下一步唯一动作：无，见文末「实施记录」。

## 1. 合并范围：beta 没有新提交，合并为空操作

`origin/beta` 的 tip `f398ced4dd355b68547dfeea440aa6516df4f62e` **已经是 dev3 的祖先**：

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 远端 beta tip | `git ls-remote origin refs/heads/beta` | `f398ced4dd355b68547dfeea440aa6516df4f62e` |
| 远端 dev3 tip | `git ls-remote origin refs/heads/dev3` | `fc790db7090e40b596bc427c76d49c0e33f53018` |
| 是否祖先 | `git merge-base --is-ancestor origin/beta HEAD` | exit 0（是） |
| merge-base | `git merge-base HEAD origin/beta` | `f398ced4dd…`（等于 beta tip） |
| 合并预演 | `git merge --no-ff --no-commit origin/beta` | `Already up to date.`（exit 0，无 `MERGE_HEAD`，工作区保持干净） |
| beta 领先 dev3 | `git rev-list --count HEAD..origin/beta` | `0` |
| dev3 领先 beta | `git rev-list --count origin/beta..HEAD` | `7`（即本轮复评对象） |
| dev3 领先 origin/dev3 | `git rev-list --count origin/dev3..HEAD` | `16`（7 个实验室提交 + 9 个 beta 已含的合并/文档提交） |

即本轮没有需要带入的远端提交，也没有冲突。

## 2. 零复活 / 零丢失（四层证据）

用户明确要求：远端已移除/回退的提交不得顺带提交上去。

| 层 | 判据 | 结果 |
| --- | --- | --- |
| 1 · 净差异 | `git diff --name-status origin/beta HEAD` | 16 个路径：11 个修改（4 个 Java + 6 个 drawable + 1 个 layout）+ 5 个新增（3 个 drawable、1 个测试、1 个文档）；无第 17 个路径被夹带 |
| 2 · 零丢失（blob 级） | beta tip **5057** 个文件逐一对 HEAD 树比对 blob | 缺失 **0**、逐字节一致 **5046**，差异 11 个且全部为本轮意图内的实验室文件 |
| 3 · 零复活（行级） | beta 历史 **26 个单亲 revert 提交**的全部删除行（去重后 **1336** 条 `(文件,行)` 记录）∩ HEAD 的 **782** 条新增记录 | 原始交集 **0**（无一条命中，因此无需再逐条判定样板行） |
| 4 · 零复活（文件级） | beta 历史删除过的 **622** 个文件路径 ∩ 本 diff 的 16 个改动路径 | 交集 **0**（没有把 beta 删过的文件重新加回来） |

26 个 revert 提交的主题全部是 Exo / MPV / 主题色 / 壁纸 / 移动端布局，与本轮的实验室焦点改动无交集；第 3、4 层的严格交集均为空，因此不存在「顺带把回退提交再提交上去」的情况。

## 3. 复评：dev3 已提交未推送的实验室遥控焦点改动

评审按 `code-review-zh` 的 **Standards / Spec 双轴**分别进行（两个轴各自独立成篇，不合并结论）。
本轮把两轴委派给独立子代理共 4 次（每轴 2 次），**4 次都没有返回结论**（2 次中途失败、2 次长时间无返回后中止），因此改由主会话按同一套标准与坏味道基线直接执行两轴，证据全部来自仓库内可复现路径。

### 3.1 第 1 轮：发现 4 项问题（1 重要 + 3 次要）

| 编号 | 轴 | 严重度 | 问题 | 事实链 |
| --- | --- | --- | --- | --- |
| S1 | Standards | **重要** | `selector_lab_link.xml` 的聚焦描边用**固定** `@color/accent`（`#2196F3`），在浅色系统下只有 **2.56:1**，低于仓库既有的「焦点环 ≥3:1」对比度门；同时与该链接自身的文字色、所属弹窗主题、以及仓库「焦点环只允许一个取值源」的约定都不一致 | ① 该链接由 `LabActivity.showImportDialog()` 创建，弹窗主题是 `Theme.App.Lab.DayNight.Dialog`（`LabActivity.java:274`）；② 该主题的 `colorSurface` = `lab_surface` = `@color/webhtv_color_surface_container_high`，浅色表 `#E7E8EF`（`values/webhtv_tokens.xml:31`）；③ `@color/accent` = `@color/blue_500` = 固定 `#2196F3`（`values/lab_colors.xml:2-3`），不随深浅色变；④ 实测对比度 `#2196F3` on `#E7E8EF` = **2.56:1**（脚本见 §3.3）；⑤ 同一 TextView 的文字色却是语义 token `getColor(R.color.webhtv_color_primary)`（`LabActivity.java:264`），弹窗主题的 `colorPrimary` 也是 `@color/webhtv_color_primary`（`lab_styles.xml:23`），而 `lab_styles.xml:10-12` 明确要求这个弹窗用语义 token；⑥ 仓库既有约定：`colors.xml:38-49`「这三项是唯一的取值来源，新增可选控件必须引用它们，不要再写死十六进制」，`ThemeController.focusRingColor` javadoc「TV 的焦点环只允许有一个取值源：`tv_item_focus_ring`」，并记录了上一次「两种焦点环色」回归 |
| S2 | Standards | 次要 | `LabActivity.java` 的 `escapeVerticalKeys` 方法上挂了**两段连续的 Javadoc**，第一段（原 `503–512`）与实现**自相矛盾**：它说「下拉列表展开时上下键仍交给列表导航」，而实现是 `dismissDropDown()` 后直接移动焦点 | `LabActivity.java` 原 `503-512`（第一段）与 `513-524`（第二段）；第二段才描述 `cycle`/确认键切换。属 Duplicated Code（重复的文档）与过时注释 |
| S3 | Standards | 次要 | `LabTvFocusSourceTest.java:210` 把 Javadoc 与 `@Test` 挤在同一行 | `... */    @Test` |
| S4 | Standards | 次要 | `LabTvFocusSourceTest.java:113` 用 `methodBody(sheet, "public void run()")` 取**第一处**匹配。该签名在本文件里第一处是 `mStateRunnable` 匿名类的 `run()`（第 60 行），第二处才是 `LabCommandSheet` 自己的 `public void run()`（第 319 行）——断言语义上恰好命中了想要的轮询，但依赖「匿名类排在前面」这一偶然事实 | `LabCommandSheet.java:58-70`（轮询）与 `:319`（执行命令） |

Spec 轴同轮结论：

| 编号 | 严重度 | 问题 | 事实链 |
| --- | --- | --- | --- |
| P1 | **重要** | `docs/LAB-TV-REMOTE-FOCUS-20261013.md` 的 Recovery anchor「允许路径」清单是会话 1 写的，**漏了会话 2–5 追加的 4 个路径**，与 §2 决策表「六项一起修，最小面」及实际 diff 不符 | 清单原文只含 12 个路径；实际 diff 的 16 个路径里，`LabCommandSheet.java`（会话 2）、`LabActivity.java`（会话 3–5）、`selector_lab_link.xml`（会话 3）、`dialog_lab_settings.xml`（会话 4）不在清单内 |
| P2 | 通过 | §3 实施记录逐条声称的改动都在 diff 里；§1.7 描述的焦点链与 `dialog_lab_settings.xml` 的实际写法一致（`sourceDropdown.nextFocusDown=folder` → `folder.nextFocusDown=input` → `input.nextFocusDown=rootInput`，利用「跳过不可见目标并顺链继续」同时覆盖本地配置与网络 URL 两种模式） | `git diff origin/beta...HEAD -- app/src/main/res/layout/dialog_lab_settings.xml` |
| P3 | 通过 | §1.8「撤销钉死上行焦点」已撤销干净 | `git diff 2812ae02e1 HEAD -- .../LabActivity.java` 为空；两处 blob 同为 `10c72515d84cfcd0ab0e376073ab465ea1498594`；`git diff 4fab3b0360 HEAD --stat` 只剩该提交被撤销的部分 |
| P4 | 通过 | 验收标准每条都有代码支撑，且 lab 包 31 例单测 0 失败（§6） | 见 §6 两次运行记录 |

### 3.2 修复

| 编号 | 修改 |
| --- | --- |
| S1 | `selector_lab_link.xml` 聚焦描边 `@color/accent` → `@color/webhtv_color_primary`：浅色 **5.23:1**、深色 **7.86:1**，均 ≥3:1，并与链接文字色/弹窗主题同源；文件头注释写明为什么不能用固定 accent |
| S2 | 删除 `escapeVerticalKeys` 上过时的第一段 Javadoc，只留描述实际行为的那一段 |
| S3 | Javadoc 与 `@Test` 拆成两行 |
| S4 | 断言锚点由 `"public void run()"` 改为 `"private final Runnable mStateRunnable"`，并加注释说明原因；断言现在明确锁住「1 秒轮询不得抢焦点」 |
| P1 | Recovery anchor「允许路径」补齐为 16 个路径，逐条标注是哪一轮会话追加的 |

### 3.3 关键证据的可复现命令

对比度（WCAG 相对亮度，脚本化复算，非估算）：

```powershell
function Lum([string]$hex) { $h=$hex.TrimStart('#'); $lin=@(); foreach($i in 0..2){
    $v=[Convert]::ToInt32($h.Substring($i*2,2),16)/255.0
    $lin += if($v -le 0.03928){$v/12.92}else{[Math]::Pow(($v+0.055)/1.055,2.4)} }
  0.2126*$lin[0]+0.7152*$lin[1]+0.0722*$lin[2] }
function Ratio($a,$b){ $la=Lum $a; $lb=Lum $b; ([Math]::Max($la,$lb)+0.05)/([Math]::Min($la,$lb)+0.05) }
```

| 环色 | 底板 | 对比度 | 是否过 ≥3:1 门 |
| --- | --- | --- | --- |
| `@color/accent` `#2196F3`（**修复前**） | 浅色 lab 弹窗面板 `#E7E8EF` | **2.56:1** | **否** |
| `@color/webhtv_color_primary` `#0B57D0`（修复后） | 浅色 lab 弹窗面板 `#E7E8EF` | 5.23:1 | 是 |
| `@color/accent` `#2196F3` | 深色 lab 弹窗面板 `#2A2F34` | 4.32:1 | 是 |
| `@color/webhtv_color_primary` `#A8C7FA`（修复后） | 深色 lab 弹窗面板 `#2A2F34` | 7.86:1 | 是 |
| `@color/accent` `#2196F3` | lab 活动底板 `#111318` | 5.95:1 | 是 |
| `@color/white` | BottomSheet 底板 `#1B1B1F` | 17.17:1 | 是 |

### 3.4 第 2 轮：修复后复审

对**本轮改动**（5 个文件：1 个 drawable、1 个 Java、1 个测试、2 个文档）重新过两轴：

- **Standards**：S1 修好后，`selector_lab_link.xml` 的环色与链接文字色、弹窗主题、仓库焦点环约定三者一致，对比度 5.23:1 / 7.86:1 过门。S2 修好后 `escapeVerticalKeys` 只剩一段与实现一致的 Javadoc（无 Duplicated Code、无过时描述）。S3/S4 修好后测试文件的格式与断言目标都无歧义。
- **Spec**：P1 修好后 Recovery anchor 的允许路径与 `git diff --name-only origin/beta HEAD` 的 16 个路径**逐项一致**；§3 实施记录新增一行说明 S1 的修正与「其余 4 个聚焦态为何留在 `accent`」。P2/P3/P4 三项仍成立（未被本轮改动影响）。
- **无新增阻塞或重要问题。**

第 2 轮经核实**不成立**的两条（记录取舍，不改）：

| 候选 | 结论 | 证据 |
| --- | --- | --- |
| 其余 4 个新增聚焦态（`shape_lab_card`、`selector_lab_group_tab`、`selector_lab_icon_btn`、`shape_lab_run_btn`/`shape_lab_stop_btn`）也该改用语义 token | **不成立**：它们只出现在固定深色的 `Theme.App.Lab` 活动（该主题的 `colorPrimary` 本身就是 `accent`，`lab_styles.xml:53`）或固定深色的 BottomSheet（`Theme.App.Lab.Dialog`，`#1B1B1F`）里，对各自底板分别是 5.95:1 / 5.95:1 / 5.95:1 / 17.17:1，均过门；且 `?attr/tvFocusRing` 在 lab 弹窗主题里**解析不到**（`webhtv_styles.xml:60-64` 已记录「Alert 对话框不继承基底主题，属性解析不到会静默退化」），所以「模块内统一用 `accent`」是自洽且更安全的选择 | `lab_styles.xml:35-55`、`webhtv_styles.xml:60-67`、§3.3 对比度表 |
| `LabCommandAdapter.stateOf` 用字符串拼状态签名（可读作 Primitive Obsession） | **不成立**：私有 3 行助手，`onBindViewHolder` 的全部可视差异确实只由 `running` + 标签决定（圆点、运行标签、按钮文字、按钮底色），且已被 `rowStateSignatureCoversRunningAndLabel` 锁定；为它引入小类型收益不足 | `LabCommandAdapter.java:80-101`、`LabTvFocusSourceTest.java:74-82` |

## 4. best-practice 依据

`AGENTS.md` §7 的设计研究门对本轮**不触发**：本轮是「合并 + 复评既有改动」，不是新的上游候选或新的用户可见能力；改动面是 1 个颜色 token、1 段注释、1 个测试锚点与 1 份文档清单，设计已由 `LAB-TV-REMOTE-FOCUS-20261013.md` §2 决策表确定。

S1 的修法依据全部是**仓库内一手来源**：

| 编号 | 来源（可复现路径 / 行号） | 证据等级 | 支持的结论 | 对 WebHTV 的适用性 | 局限 |
| --- | --- | --- | --- | --- | --- |
| E1 | `app/src/main/res/values/colors.xml:38-49` | 一手（仓库既有约定） | 「焦点/当前/常态」三项是**唯一取值来源**，新增可选控件必须引用它们，不要再写死十六进制；焦点环必须能被主题设置覆写（记录了上一次写死 `#FFD166` 的回归） | 直接适用：S1 的固定 `accent` 与弹窗内语义 token 并存正是同类问题 | 该注释的语境是原生增强播放页，需要 S1 的「同一视图内文字色已是 token」作为适用性桥梁 |
| E2 | `app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java:365-400` | 一手（仓库既有实现与文档） | 「TV 的焦点环只允许有一个取值源：`tv_item_focus_ring`」，并给出浅色底板上环不可见的具体数值（`#FFD166` 只有 1.14–1.35:1），Resolver 另有 ≥3:1 的对比度门 | 直接适用：2.56:1 落在同一条门之下 | 该取值源经 `?attr/tvFocusRing` 提供，而 lab 弹窗主题不绑定该属性（见 E4），所以本轮**不**改用它，只统一到语义 token |
| E3 | `app/src/main/res/values/lab_styles.xml:10-12,23` | 一手（lab 模块自己的约定） | lab 的 DayNight 弹窗「`colorPrimary` 必须用语义 token（而不是固定的 `@color/accent=#2196F3`），否则…主题色永远作用不到这个设置弹窗」 | 直接适用：同一弹窗里的新环色应当服从同一条要求 | 该注释针对 `colorPrimary` 属性，本轮把它外推到同一弹窗内的 drawable 环色 |
| E4 | `app/src/main/res/values/webhtv_styles.xml:60-67`、`attrs.xml:8` | 一手 | TV 焦点语义属性必须在基底主题上绑定；Alert 对话框主题（`Theme.Material3.DayNight.Dialog.Alert`）不继承基底主题，属性解析不到会**静默退化** | 直接适用：解释了为什么 lab 弹窗不能用 `?attr/tvFocusRing`，支撑 3.4 的「不成立」判定 | 未做真机验证「解析不到」的具体退化表现 |
| E5 | `app/src/main/res/values/webhtv_tokens.xml:3,31`、`values-night/webhtv_tokens.xml:3,31`、`values/lab_colors.xml:2-3` | 一手（仓库资源） | 浅色 primary `#0B57D0` / 面板 `#E7E8EF`；深色 primary `#A8C7FA` / 面板 `#2A2F34`；`accent` 固定 `#2196F3` | 直接适用：§3.3 对比度表的输入 | 只覆盖默认主题；用户自定义主题色由 `ThemeBinder` 改写，本轮未在真机上枚举 |

方案比较（针对 S1）：

| 方案 | 内容 | 正确性 | 兼容性/性能 | 维护与可验证性 | 结论 |
| --- | --- | --- | --- | --- | --- |
| no change | 保留固定 `@color/accent` | 浅色弹窗 2.56:1，低于仓库既有 ≥3:1 门；与同视图文字色不同源 | 无变化 | 没有回归锁，容易再被写成固定色 | 否 |
| 改用 `?attr/tvFocusRing`（仓库「唯一取值源」） | 直接引用 TV 焦点环属性 | 语义上最正统，但在 lab 弹窗主题里该属性**解析不到**（E4），会静默退化，反而可能让环消失 | 有可见性回归风险 | 需要同时改 lab 弹窗主题绑定，超出本轮最小面 | 否（记录为后续可选） |
| **改用 `@color/webhtv_color_primary`（采纳）** | 与链接文字色、弹窗主题 `colorPrimary` 同源 | 浅 5.23:1 / 深 7.86:1，过 ≥3:1 门 | 纯颜色资源替换，无布局/尺寸变化；环色在浅色下变深、深色下变浅，可见性只增不减 | 一个 token，且新增 2 条断言锁死「不得回到固定 accent」 | **采纳** |

## 5. 回归锁

`LabTvFocusSourceTest` 仍为 12 个测试方法（本轮未增删方法，而是**加固**了 2 个）：

- `importDialogIsReachableWithTheRemote` 新增 2 条断言（S1 的锁）：`selector_lab_link.xml` 必须含 `@color/webhtv_color_primary`，且**不得**含 `@color/accent`。
- `commandSheetAnchorsFocusOnAButton` 的轮询断言锚点改为 `private final Runnable mStateRunnable`（S4），现在明确锁住「1 秒轮询不得调用 `requestSheetFocus`」。
- 其余 10 个方法不变，继续锁住：周期刷新不得整表重建、差异刷新只做局部通知、行状态签名覆盖全部可视差异、可聚焦控件必须有聚焦态、图标按钮必须声明焦点邻居、命令弹窗初始焦点必须锚在按钮上、配置源弹窗必须放行上下键并由确认键切换、配置源必须能 DOWN 到文件夹按钮、弹窗按钮不得钉死上行焦点、分组标签 selector 必须接上聚焦图形。

## 6. 验证

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 修复前基线 | `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.lab.*" --console=plain` | `BUILD SUCCESSFUL in 5m 50s`，exit 0；lab 包 **31 例 / 0 失败**（含新增的 `LabTvFocusSourceTest` 12 例） |
| 修复后复验（含资源编译） | 同上 | `BUILD SUCCESSFUL in 1m 55s`，exit 0；`processLeanbackArm64_v8aDebugResources` 与 `compileLeanbackArm64_v8aDebugJavaWithJavac` 均执行；lab 包 **31 例 / 0 失败** |
| 交付面 | `git diff --name-status origin/beta` + `git ls-files --others --exclude-standard` | **18** 个路径 = 16 个实验室焦点路径（与 `LAB-TV-REMOTE-FOCUS-20261013.md` 补齐后的「允许路径」清单**逐项一致**）+ 本文 + 评估索引 1 行；无第 19 个路径 |
| 空白卫生 | `git diff --check origin/beta` | exit 0（无行尾空白/空白错误）；工作区行尾与仓库 `core.autocrlf=true` 约定一致，未引入整文件换行噪声 |

修复后逐类明细（`app/build/test-results/testLeanbackArm64_v8aDebugUnitTest`）：

- `LabTvFocusSourceTest` 12/12（本轮加固 2 例）、`LabSettingsDialogThemeTest` 8/8、`LabEnvTest` 6/6、`LabProcessIdentityTest` 3/3、`LabRunnerProcessCompatTest` 2/2。

即：本轮只改 1 个颜色 token、1 段注释、1 个测试锚点与 2 份文档，实验室遥控焦点行为、lab 设置弹窗主题契约、进程身份与 Runner 兼容性均未被破坏。

## 7. 残留未知与已知限制

- **未做设备实测**：本轮是「合并 + 复评 + 源码/资源级修正」，没有重跑 `LAB-TV-REMOTE-FOCUS-20261013.md` §4.1 的设备矩阵。S1 是纯颜色资源替换，其对比度用 WCAG 相对亮度脚本复算（§3.3），但「浅色系统下导入弹窗里环真的看得见」这条仍属设备侧结论，本轮未在真机复现。
- **`?attr/tvFocusRing` 未采纳**：见 §4 方案比较。若后续要让实验室也接入仓库唯一的 TV 焦点环取值源，需要先在 `Theme.App.Lab.DayNight.Dialog` 上绑定 `tvFocusRing`（现在不绑定，属性解析不到会静默退化），这属于跨模块的主题改动，不在本轮最小面内。
- **`LAB-TV-REMOTE-FOCUS-20261013.md` §4.1 表格措辞偏松**：表里把 lab 活动内 `accent` 描边也写成「主题色描边」。lab 活动的 `colorPrimary` 本身就是 `accent`（`lab_styles.xml:53`），所以该说法在「模块主题色」意义上成立；已在 §3 实施记录加行说明，未逐格改写历史实测记录（保持会话日志原貌）。
- **本轮未采纳的评审建议（记录取舍）**：见 §3.4 的两条「不成立」——其余 4 个聚焦态留在 `accent`、`stateOf` 不引入小类型。
- **子代理评审未返回**：本轮委派给独立子代理的双轴评审 4 次尝试全部没有返回结论（2 次中途失败、2 次长时间无返回后中止），两轴由主会话直接执行；证据均可在仓库内复现（§3.3 与 §4 的命令/路径）。

## 8. 回滚

- 代码回滚锚点：本任务开始前 `dev3` tip `b2bb4a249b9b2c9385e322d0652086facdf60aa1`；`git revert` 本任务提交即可恢复复评前状态（1 个 drawable 颜色、1 段注释、1 个测试锚点、2 份文档）。
- 文档回滚：删除本文，撤销对 `docs/LAB-TV-REMOTE-FOCUS-20261013.md` 的允许路径与 C68 修正行，以及评估索引的 C68 行。
- 不涉及 Media3/nextlib AAR、FFmpeg、MPV native、lock、native 二进制或音频路由。

## 9. 交付坐标

| 项 | 值 |
| --- | --- |
| 修正提交 | `5a5ce47394d7f3037dd93a48915037b14b954fdf`（单亲，父 = `b2bb4a249b9b2c9385e322d0652086facdf60aa1`） |
| recovery tag | `recovery/C68-beta-merge-review-dev3-20261014/20261014032823-5a5ce47394d7` |
| 推送 | `git push origin dev3` → `fc790db709..5a5ce47394  dev3 -> dev3`；`git rev-parse origin/dev3` == `git rev-parse dev3` == `5a5ce47394d7f3037dd93a48915037b14b954fdf` |
| PR | [#444](https://github.com/Silent1566/webhtv/pull/444)（`dev3` → `beta`，**只创建，未合并**） |

## 闭环记录

| 复核项 | 结果 |
| --- | --- |
| `git rev-list --parents -n1 5a5ce47394` | 单亲（父 `b2bb4a249b`）：本轮 beta 已是祖先，无合并父，与 `C66` 同形 |
| `git merge-base --is-ancestor origin/beta HEAD`（收尾） | **真** |
| `MERGE_HEAD` | 不存在（合并预演返回 `Already up to date.`，从未进入合并态） |
| `git diff --diff-filter=D --name-status origin/beta HEAD` | 空（没有删除 beta 侧文件） |
| `git diff --name-status origin/beta dev3` | 18 路径（16 个实验室焦点路径 + 本文 + 评估索引 1 行） |
| 工作区 | `git status --porcelain` 为空（除本次纯文档坐标提交） |
| PR 状态 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=18`、`baseRefName=beta`、`headRefName=dev3` |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/444/files --jq '.[].filename'` 与 `git diff --name-only origin/beta dev3` **逐项一致**（18 项，`Compare-Object` 无输出） |
| 守卫会话 | 主会话 `C68-beta-merge-review-dev3-20261014`（upstream）与坐标会话 `C68-beta-merge-review-dev3-20261014-coords`（quick-fix，scope `docs`） |
| 临时资源 | `%TEMP%\c68-pr-body.md`（PR 正文）按任务范围清理 |

- 本次交付只创建 PR，未执行任何合并操作。
- **本任务闭环完成。** 三项用户要求均已满足：① 合并远端 beta 最新代码且远端已移除/回退的内容**零复活**（行级与文件级交集均为 0）；② 复评全部已修改代码（含已提交未推送的 7 个实验室遥控焦点提交）并修正 1 项重要 + 3 项次要问题，2 轮评审通过、31 例单测全绿；③ 提交（修正提交 + recovery tag）、推送 `dev3`、创建 PR（中文描述、只创建未合并）。

## 实施记录

- 2026-10-14 10:55 (+08:00)：确认工作区干净；`git fetch --all --prune` 后 `origin/beta` = `f398ced4dd`，`git merge --no-ff --no-commit origin/beta` 返回 `Already up to date.`，本轮无远端新提交、无冲突。
- 2026-10-14：guard `C68-beta-merge-review-dev3-20261014`（upstream）启动，基线 `b2bb4a249b`，保护面 0 个既有脏文件。
- 2026-10-14：零复活/零丢失四层证据完成（净差异 16 路径、缺失 0、revert 行级交集 0、revert 文件级交集 0）。
- 2026-10-14：修复前基线单测 31/31 通过（5m50s）。
- 2026-10-14：Standards / Spec 双轴复评（主会话直接执行，子代理两次失败未返回）得到 S1–S4 + P1；完成 5 处修复。
- 2026-10-14：修复后复验 31/31 通过（1m55s，含资源编译）。
- 2026-10-14：第 2 轮复审无新增阻塞/重要问题，2 条候选经核实不成立并记录取舍。
- 2026-10-14：交付面与空白卫生校验（18 路径、`git diff --check` 干净），guard `finish` → 修正提交 `5a5ce47394d7f3037dd93a48915037b14b954fdf` + recovery tag `recovery/C68-beta-merge-review-dev3-20261014/20261014032823-5a5ce47394d7`。
- 2026-10-14：`git push origin dev3`（`fc790db709..5a5ce47394`）；创建 PR [#444](https://github.com/Silent1566/webhtv/pull/444)（base `beta`，18 个文件，只创建不合并）。
- 2026-10-14：坐标会话（`-coords`，quick-fix，scope `docs`）把交付坐标与闭环记录写回本文。
- 环境注记：本机 `bash` 指向不可用的 WSL，guard 用 `G:\Git\bin\bash.exe` 执行；Gradle 用 `gradlew.bat`（`JAVA_HOME` = JDK 21）。本机环境变量 `GIT_CONFIG_COUNT=2` 缺 `GIT_CONFIG_KEY_*`，所有 git 命令前需清空该变量。
