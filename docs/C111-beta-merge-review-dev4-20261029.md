# C111-beta-merge-review-dev4-20261029

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`；评审 `dev4` 已提交未推送的代码（含合并提交）；
  发现问题即修复并验证；复评直至通过；然后提交、推送 `dev4`、创建到 `beta` 的中文 PR（含具体改动内容），
  最后拉取远端最新代码。
- **验收标准**：
  1. `dev4` 合并后 `origin/beta` 成为 HEAD 祖先（落后 0），无冲突、无夹带评审外改动。
  2. 评审覆盖 `origin/dev4..HEAD` 的全部路径（本轮为 1 个功能提交 + 1 个合并提交，净差异 3 个路径 / +60 −1）。
  3. 发现的必修项均已修复并有回归断言；修复后复评通过。
  4. 双 flavour 编译通过；单测失败集与既有基线**逐条一致**（无新增失败）；`git diff --check` 空。
  5. 改动提交并打 recovery tag；推送 `dev4`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` mobile + `docs`）
- **任务守卫**：**未执行**。本机 `bash` 通道不可用（`bash -c "echo ok"` →
  `Bash/Service/CreateInstance/CreateVm/HCS/HCS_E_SERVICE_NOT_AVAILABLE`），`.codex/scripts/task_guard.sh` 无法启动。
  改用等效手工流程：只暂存任务自有路径 + 手工打 recovery tag。该限制与 C105/C108 记录一致，非本轮引入。
- **当前状态**：合并、评审、必修项修复、复评、验证完成；交付坐标见 §8

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `aab55dfcca94904a4ca6b7775b4f57bda343e35c`（"Merge pull request #498 from Silent1566/dev1"） |
| 合并前本地 | `dev4` = `7965b6fd87175a86ef5f3d0dfba3c70d5c4e0188`（"fix(mobile-live): reserve the phone status bar area for the embedded live player"） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false** |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **14 / 1** |
| merge-base | `ed85084a89ecb6d333b885e9e9b41924028598f4` |
| 冲突预检 | `git merge-tree --write-tree HEAD origin/beta` → **无冲突** |
| 合并动作 | `git merge --no-ff origin/beta` → **8 个路径 / +472 −10**，无手工干预 |
| 合并提交 | `f615e53e9afe8991e88ddec17997dd84a4d7bae6`（父提交 `7965b6fd871` + `aab55dfcca9`） |
| 祖先关系（合并后） | `git merge-base --is-ancestor origin/beta HEAD` → **true**；left-right = **0 / 2** |
| 合并纯净性（正向） | `git diff --name-only 7965b6fd871 HEAD` = beta 侧 8 个路径，与本方 3 个路径零重叠，无夹带 |
| 合并纯净性（反向） | `git diff --stat origin/beta HEAD` = 仅 `dev4` 自己的 3 个路径 / **+60 −1**，beta 侧一个都没丢 |

`beta` 本次带入 7 个非 merge 提交：

- `abff821ba31` docs(C110) 回填交付坐标
- `69d87e28c3f` / `8e6a4016a98` docs(c107) 文档回填与下一动作
- `47d7156c5f0` test(c107) 补「指标可读转不可读」撤销证据分支回归用例
- `169bc59860c` fix(C109) 快搜面板搜索按钮改玻璃胶囊底（`shape_quick_search_icon_button` → `#12FFFFFF` 填充 + `#40FFFFFF` 描边）
- `6bef9927671` fix(C108) 快搜面板线路名称改常亮前景（`adapter_quick` `#site` → `webhtvColorOnWallpaper`）
- `687ec4af3c9` fix(leanback) 厂商直通音频在无可读缓冲指标时仍能恢复

以上均为 `beta` 已评审/已合并内容（PR #496→#497→#498 链），本轮不重复评审，仅验证其在本分支可用（§5）。

## 2. 评审范围

本轮新评审的是 `dev4` 相对 `origin/dev4` 的 **2 个提交**（1 个功能提交 + 1 个合并提交），
功能净差异 **3 个路径 / +60 −1**：

| 提交 | 说明 |
| --- | --- |
| `7965b6fd871` | 为手机端嵌入式直播播放器预留状态栏高度（`activity_live.xml` 增加 `@+id/statusBar` 占位 View；`LiveActivity` 增加 inset 采集/应用与菜单重挂位置修正；新增回归用例） |
| `f615e53e9af` | 本轮 `origin/beta` 合并提交，无冲突、无本方改动 |

历史评审可用性判定：C108 的评审覆盖 `origin/dev4` 之前的 6 个提交，**不含** `7965b6fd871`
（该提交在 C108 合入后才产生，C108 §8 的下一动作为空），因此本轮必须新评审；合并提交本身只引入
已评审的 beta 内容，按「无合入冲突时跳过」处理，但仍逐路径核了纯净性（§1）。

## 3. 改动逐点复核（`7965b6fd871` + 本轮修复）

| 复核点 | 结论 |
| --- | --- |
| `activity_live.xml:11-15` 新增 `@+id/statusBar`（`match_parent` / `0dp` / `@color/black`），置于 `video` 之前 | 与既有 `activity_video.xml:24-34` 同一模式（`VideoActivity.applyStatusBarSpacer()`），占位高度由代码按 inset 计算，XML 默认 0 不占位 |
| `layout-land/activity_live.xml` 不含 `statusBar` | 正确：横屏走全屏语义，`isEmbeddedLiveUi()` 为 false，不需要预留带 |
| ViewBinding 空安全 | `layout-land` 无该 id → 生成字段为 `@Nullable`，`applyStatusBarInset()` 的 `if (mBinding.statusBar == null) return;` **必需**；双 flavour 编译通过实证 |
| `updateStatusBarInset()`（`:2343-2348`）只采纳 `top > 0` | 正确：全屏 `Util.hideSystemUI` 派发 `top = 0`，保留最后一次可见高度才能在全屏/小窗往返时恢复；高度是**派生值**，不会累积 |
| `applyStatusBarInset()`（`:2350-2357`）短路 | `if (params.height == height) return;` 避免每次 inset 派发都触发 `requestLayout` |
| listener 注册时机 | `setupWindowInsets()` 由 `initView`（`:283`）在 `BaseActivity.onCreate` 内调用，早于首帧遍历；`:403`、`:2218` 与 `onWindowFocusChanged`（`:2210`）三处补派发覆盖恢复场景 |
| `moveLiveMenuToRoot()` 插入下标 | 根布局子序为 `[statusBar, video, recycler, navigation]`；recycler 先于 `:2293` 从 video 中移除，再于 `:2297` 计算 `indexOfChild(video) + 1 = 2`，恢复原相对顺序。旧的常量 `1` 在新增占位后会插到 video 之上，故改为相对定位正确 |
| 模式切换覆盖 | embedded↔全屏（`setRotate:1928` / `enter/exitFullscreenLive:764-787`）、旋转（`onConfigurationChanged` → `updateSystemUI:2202`）、重获焦点（`:2210`）、PiP 失败回退（`:750`）均经 `updateEmbeddedUiMode()` 应用；PiP 路径由本轮修复补上（§4） |

## 4. 评审结论与必修项

### 4.1 必修项（已修）

**F1 · PiP 窗口不释放状态栏占位，video 被占位带向下挤**

`onPictureInPictureModeChanged()`（`:2118-2136`）原先只调用 `setVideoView(...)` 与 `hideUI()`，
从不调用 `updateEmbeddedUiMode()` / `applyStatusBarInset()`；而 `isEmbeddedLiveUi()`
（`:1016-1018` = `!ResUtil.isLand && !isRotate && !isInPictureInPictureMode`）在 PiP 中变为 false，
占位高度本应释放为 0。实际依赖「进入 PiP 会派发 configuration change → `onConfigurationChanged`
→ `updateSystemUI`」这条系统时序兜底；一旦未及时派发，嵌入式高度会保留，而 `setVideoView`
（`:2156-2175`）已把 video 强制为 `MATCH_PARENT`/`weight = 0`，画面就被这条占位带往下推。

修复：

```java
setVideoView(isInPictureInPictureMode);
// PiP 窗口里也要按当前模式重算状态栏占位，否则嵌入式高度会把 video 往下挤。
applyStatusBarInset();
```

让不变量落在本地（模式变化点自身），不再依赖系统是否补发 config change；进出 PiP 两条分支都经过该行，
`applyStatusBarInset()` 自带「高度未变即 return」的短路，无额外布局开销。

回归断言：`LiveActivityLayoutTest.mobilePortraitLivePlayerReservesTheStatusBarArea()` 新增
`pipBody.contains("applyStatusBarInset();")`（`pipBody` 由
`section(source, "public void onPictureInPictureModeChanged(", "private void switchToAudioBackground()")` 切片），
删除该行即测试失败。

### 4.2 复核通过、无需改动

独立评审另提 2 条 nit，均判定**不动**：

- `Math.max(1, …)` 硬化 `moveLiveMenuToRoot()` 的下标：`indexOfChild(video) == -1` 在当前代码里不可达
  （video 只由 XML 加入，唯一的重挂代码只搬移 recycler），属防御性冗余，记入 §7。
- 用例 `:574` 的断言钉住 `isEmbeddedLiveUi() ? statusBarInset : 0` 字面写法，行为等价的改写会导致失败：
  这是本仓库既有的「源码契约断言」风格（`VideoActivityLayoutTest` 等同型），且 mobile 源集未接 Robolectric
  （`app/build.gradle:211` 只给 `testLeanback` 加），无法断言运行时高度，记入 §7。

## 5. 验证证据

| 项 | 结果 |
| --- | --- |
| 双 flavour 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → **成功** |
| `testMobileArm64_v8aDebugUnitTest` | **5485 用例 / 2 失败 / 3 跳过** |
| `testLeanbackArm64_v8aDebugUnitTest` | **4703 用例 / 2 失败 / 3 跳过** |
| 定向回归 `LiveActivityLayoutTest` | **19 例 / 0 失败** |
| 失败集对照 | 两个 flavour 的失败**同为**下面两条，与 C106/C108 基线记录**逐条一致** → **零回归** |
| 用例总量自洽 | mobile `5485` = 源集 `@Test` 计数 5485；leanback `4703` = `@Test` 计数 4703，且 `testsuite` 属性合计与 `testcase` 节点数一致（733 个套件、无重复套件名），无残留旧产物 |
| 空白与冲突 | `git diff --check` 空；无冲突标记 |

**基线失败（与本轮改动无关，均为 `origin/beta` 上已存在的沙箱环境失败）**：

- `androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile`
  → `java.io.IOException: Unable to commit fontconfig configuration`（沙箱无法提交 fontconfig 文件）
- `androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation`
  → `java.lang.AssertionError`

**零回归三重证据**：

1. `git diff --name-only 7965b6fd871 HEAD -- app/src/test/java/androidx/media3/mpvplayer/` 为空（本任务与 beta 都没碰过）；
2. 这两个测试文件最近一次改动分别为 `57af4ef81b7`、`33c2a9cfd3a`，与本轮无关；
3. `docs/C106-beta-merge-review-dev3-20261026.md:126-127`、`docs/C108-beta-merge-review-dev4-20261028.md:143-146`
   已有同名记录，验收方法为「以失败用例名与干净 `origin/beta` worktree 逐条对照」。

> 说明：本轮第一次全量跑（`build-fulltest.log`）在 mobile 任务失败处即 fail-fast 结束，leanback 结果目录里
> 残留的是 C108 时期的 4697 条旧产物；上表的 4703 来自修复后单独重跑 `testLeanbackArm64_v8aDebugUnitTest`
> 的新鲜产物（`LastWriteTime` 全为本次运行时间），差值 +6 即 beta 带入的 `ExoCompressedAudioDirectPolicyTest` 等新用例。

## 6. 复评

独立只读评审先行确认 `7965b6fd871` 的 H1/H2/H3 三组假设无缺陷（空安全、inset 派发与保留、下标计算），
并提出 F1（PiP 未释放占位）为必修；修复后再次独立复评该 diff：修复点位置、调用顺序、空安全、
断言可证伪性全部复核通过，无新增缺陷。
**复评通过**。

## 7. 遗留与后续项（本轮不做）

- `moveLiveMenuToRoot()` 的下标仍隐含「`video` 必为根布局直接子视图」这一不变量；若将来出现运行时重挂
  video 的代码，应改为 `Math.max(1, root.indexOfChild(mBinding.video) + 1)` 之类带下界的写法。
- mobile 源集未配置 Robolectric，状态栏占位只能做源码契约断言 + 真机 `uiautomator dump` 坐标核对
  （见 `7965b6fd871` 提交信息中的 1080×1920 实测：`statusBar[0,0][1080,72]` / `video[0,72][1080,680]`）。
- 本机 `bash` 通道不可用导致 `.codex/scripts/task_guard.sh` 无法执行，recovery tag 由手工 `git tag -a` 补足；
  恢复 bash 后应复核守卫状态文件是否被跳过。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `f615e53e9afe8991e88ddec17997dd84a4d7bae6`（`dev4`，父提交 `7965b6fd871` + `aab55dfcca9`） |
| 功能提交 | `7965b6fd87175a86ef5f3d0dfba3c70d5c4e0188`（已被 C111 评审；F1 修复另行提交） |
| Recovery tag | `recovery/C111-beta-merge-review-dev4-20261029/20261029012500-aa8f8a06f31e` |
| 分支 | `dev4` |
| PR | https://github.com/Silent1566/webhtv/pull/499 （`dev4` → `beta`） |
| 回滚 | `git reset --hard 7965b6fd871`（回到合并前），或 `git revert -m 1 f615e53e9af`（保留历史） |
| 验证坐标 | 本文件 §5 记录的提交即被验证状态 |

### 回填（提交后补记）

- F1 修复提交：`aa8f8a06f31ec9c3f5c880eb7ed0b39f41ee45b3`（`LiveActivity.java` +2、`LiveActivityLayoutTest.java` +3、本文件 +173）
- Recovery tag：`recovery/C111-beta-merge-review-dev4-20261029/20261029012500-aa8f8a06f31e`，已随分支推送
- PR：#499（`dev4` → `beta`），标题「merge(C111): dev4 合并 beta aab55dfcca9，评审并修复直播 PiP 状态栏占位」
- 远端状态：`origin/dev4` = `aa8f8a06f31`，`git rev-list --left-right --count origin/beta...HEAD` = `0 3`（beta 已全部包含）
- 收尾 `git pull --ff-only origin dev4`：Already up to date
