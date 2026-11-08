# C59：Android 9 电视盒子「反复自我升级」死循环根因修复（更新判定）

## Recovery anchor

- **目标**：定位并修复用户反馈的 Android 9（CM311-1sa，API 28）电视盒子"装完即闪退、反复下载 `update.apk` 覆盖安装形成死循环"问题中的**更新判定缺陷**，并给出用户原判断（targetSdk 过高）是否成立的证据结论。
- **验收标准**：① 给出可复现、可证伪的真实崩溃链路与死循环机制；② 更旧的包不再被当作新版推荐安装；③ 正常（更新方向）升级能力不受影响；④ 聚焦测试通过；⑤ 提交 + recovery tag。
- **当前状态**：**已完成**。根因之一是 `Update.hasUpdate()` 把"不同"当成"更新"，使 stable/beta 两个渠道互相覆盖安装；已改为"仅严格更新"，并以真实渠道清单字面量做回归锁定。
- **配套缺陷**：同一分析中发现第二个独立缺陷（Room 数据完整性校验失败导致打开期崩溃、旧包覆盖新包会静默销毁数据）。它属于**另一个原子逻辑改动**，按 `AGENTS.md` §6「一次守卫会话 = 一个原子逻辑改动」在后续任务 **C60** 中实施与验证，记录见 `docs/C60-android9-box-database-integrity-recovery-20261011.md`。
- **回滚锚点**：`git revert <C59 提交>`（仅影响更新排序语义，与 C60 改动互不依赖）。
- **下一动作**：无（本任务闭环）。

## 时间与设备

- 任务开始：2026-10-11 11:40（Asia/Shanghai）；记录时 13:20。
- 基线：`dev1` HEAD = `3148f030df9784be7b14fc924364c304980ff9ab`；工作区仅 `.scratch-goal/` 未跟踪（本任务取证目录，不提交）；受保护的既有脏路径 371 条（既有构建产物/任务状态，未触碰）。
- 设备：`192.168.50.3:5555`（`ro.build.version.sdk=28`、`ro.build.version.release=9`，aosp_marlin 镜像，与盒子同一 API 级别）。
- 取证目录：`.scratch-goal/`（原始日志、渠道清单、重签 APK、数据库样本；不提交）。

## 用户反馈的原始判断与证据的偏差

用户日志（`10091755TV版闪退盒子日志.txt`）与随附分析认为：Android 9 缺少 `FragmentManager$FragmentLifecycleCallbacks`、`ValueAnimator$DurationScaleChangeListener`、`androidx.window.extensions`，属于"编译目标 SDK 太高、API 不匹配"，建议把 `compileSdk/targetSdk` 降到 28。

逐条核对后，**该判断不成立**：

| 用户判断 | 实际证据 |
| --- | --- |
| targetSdk 太高 | `gradle/libs.versions.toml`：`targetSdk = "28"`；渠道 APK 的 `aapt2 dump badging` 亦为 `targetSdkVersion:'28'`。targetSdk 本来就是 28。 |
| `FragmentManager$FragmentLifecycleCallbacks` 缺失导致崩溃 | 该探测来自 TV 版依赖 `me.jessyan.autosize`。`AutoSizeConfig.<clinit>` 会探测 `android.support.v4.app.FragmentActivity`（APK 内因兼容媒体通知确实含 support-v4 类），置 `DEPENDENCY_SUPPORT=true`，随后构造 `FragmentLifecycleCallbacksImpl` 才触发该 CNFE，属**被记录后跳过**的可选探测失败。实测：同一镜像上 leanback 包每次启动都出现 16 行该类 CNFE，同时均正常启动（`Displayed ... +8s155ms`）。盒子日志同时段也证明这点——10:32 与 10:35 出现 CNFE 后，10:35:50–10:36:36 应用**正常运行**了 SettingActivity。 |
| `ValueAnimator$DurationScaleChangeListener` 缺失导致崩溃 | 同上，日志中为 `Rejecting re-init on previously-failed class`（被捕获后跳过），与崩溃无因果。 |
| `androidx.window.extensions` | 清单中为 `android:required="false"`，PackageManager 明确 `desires unavailable shared library androidx.window.extensions; ignoring!`，属**预期忽略**。 |
| 建议降 SDK 重编 | 不需要，且会带来兼容性回归风险；真实缺陷在应用自身的更新判定与数据库打开路径。 |

## 真实根因

盒子日志的关键序列（`grep` 过滤后仍可见）是：

1. `10:36:37` 应用创建 `cache/update.apk` → `10:37:06` 拉起系统安装器 → `10:37:31` 完成覆盖安装（`Replace existing pacakge`）。
2. 之后**每次**启动都是：`10:38:34.714 E/AndroidRuntime` → `Force finishing activity ... HomeActivityCurrent` → `Process ... has died`；`10:38:52`、`10:38:54` 重复。

即"某次自我升级之后起步即崩"，而不是"从来就跑不起来"。

### 缺陷 1（本任务）：更新判定把"更旧的包"当成新版，形成跨渠道覆盖安装死循环

修复前 `Update.hasUpdate()`：

```java
return code != BuildConfig.VERSION_CODE || !AppVersion.isCurrent(name);
```

`code != VERSION_CODE` 是"不相等"而不是"更新"。而本次发布的两个渠道清单**版本号完全相同**（实测 `code=560`）：

| 渠道 | 清单 `name` | 对应提交 | DB `VERSION` | APK sha256 |
| --- | --- | --- | --- | --- |
| stable | `v5.6.0-202609221328` | `edf04f9` | 45 | `02543c12ad09b3770e52247c80a59280690d6530c38cf6add0295c1de7d53ec5` |
| beta | `v5.6.0-beta-202610091755` | `841a570` | 48 | `5a379887bcde860830efab3b6f3937e21f028d4472eb2d6e6308048746ba2156` |

于是：beta 装机时 stable 清单 `name` 与当前构建名不同 → `hasUpdate()` 为真，且 `getPreferredUpdate()` **优先选 stable** → 应用把**更旧的 stable 包**当作更新推给用户；装成 stable 后 beta 清单又名不同 → 又推 beta。两个渠道互相覆盖，正是用户看到的"反复下载覆盖安装、装完依旧闪退、死循环"。

### 缺陷 2（后续任务 C60）：两个包数据库 schema 不一致

- **静默毁数据（实测复现）**：v48 数据 + v45 包（即缺陷 1 的降级路径）→ 实测 `user_version` 回到 45、`Keep/History/Config` 全部为 **0**（80 条历史、收藏、配置被 `fallbackToDestructiveMigration(true)` 销毁）。
- **打开期崩溃且无法恢复（实测复现）**：`versionCode` 相同但 `identity_hash` 不同的库，Room 在打开期抛 `IllegalStateException: Room cannot verify the data integrity ...`，崩溃点为 `RoomConnectionManager.checkIdentity` → `supportOpenHelperCallback.onOpen` → `HomeActivity` 创建失败 → 进程死亡；该异常发生在打开数据库时，调用点无法捕获，因此表现为**永久崩溃循环**，与盒子日志中"E/AndroidRuntime + Force finishing HomeActivity + has died"每次重复的形状一致。

该缺陷的修复与实机验证见 `docs/C60-android9-box-database-integrity-recovery-20261011.md`。

## 修复（本任务）

`app/src/main/java/com/fongmi/android/tv/bean/Update.java`

```java
if (code > BuildConfig.VERSION_CODE) return true;   // 更高版本号 = 更新
if (code < BuildConfig.VERSION_CODE) return false;  // 更低版本号 = 降级，永不当作更新
return AppVersion.isNewer(name);                    // 同版本号：仅按构建时间戳判新旧
```

`app/src/main/java/com/fongmi/android/tv/utils/AppVersion.java` 新增 `buildStamp()` 与 `isNewer(current, target)`：从发布标签尾部提取 `yyyyMMddHHmm[ss]` 构建时间戳，作为同版本号下的排序依据；标签相同不更新，缺少可比时间戳时沿用历史"不同即更新"语义以免本地/非标构建失去升级能力。

效果：

- beta 装机时更旧的 stable 不再被推荐 → **死循环消失**；
- stable 装机时更新的 beta 仍可正常升级 → **不损失正常升级方向**；
- 任何情况下不再自动降级 → 缺陷 2 的静默毁数据路径不再被自动触发。

## 验证

### 聚焦单元测试（JVM，本任务确定性验证）

```bash
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.utils.AppVersionBuildStampTest' \
  --tests 'com.fongmi.android.tv.bean.UpdateOrderingTest' \
  --tests 'com.fongmi.android.tv.UpdateChannelTest' \
  --tests 'com.fongmi.android.tv.UpdaterFutureTest'
```

`BUILD SUCCESSFUL`；测试报告：**21 tests / 0 failures / 0 errors / 0 skipped**
（`AppVersionBuildStampTest` 7、`UpdateOrderingTest` 5、`UpdateChannelTest` 5、`UpdaterFutureTest` 4）。

直接锁定本缺陷的用例（使用**真实渠道清单字面量**）：

- `olderStableIsNeverAnUpdateForABetaInstall`：`v5.6.0-beta-202610091755` 视角下 `v5.6.0-202609221328` 必须**不是**更新 → 死循环被切断；
- `newerBetaIsAnUpdateForAStableInstall`：反向仍必须是更新 → 不损失升级能力；
- `olderManifestWithTheSameVersionCodeIsNotAnUpdate`：同 `versionCode` 更旧清单 `hasUpdate()` 必须为假；
- `lowerVersionCodeIsNeverAnUpdate` / `higherVersionCodeIsAlwaysAnUpdate`：版本号方向性。

### 说明

本任务的改动是纯判定逻辑，风险相称的验证是上述确定性单测。**实机（Android 9 机位）证据在 C60 中采集**，其所验证的构建同时包含本任务的改动（C59 是 C60 构建的祖先提交），因此本任务的逻辑也在真机上被实际执行且未引起回归。

## 有意保留的行为与残留风险

- **同版本号且标题相同**的清单不再被认为是更新（修复前会被反复提示）。真实发布标签含分钟级时间戳，不受影响。
- **缺少可比时间戳**的标签沿用历史语义（"不同即更新"），此时仍可能被推荐旧包；CI 发布标签始终含时间戳，实际不触发。
- **用户手动侧载**更旧的包仍会走 Room 的降级重建路径（见 C60 的残留风险记录）。修复 1 已使应用**不会自动**走到该路径。

## 与既有任务的关系

本任务与上游播放器依赖合并计划（`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`）无关，不涉及 Exo/MPV/common 任何阶段，未改动任何上游依赖、锁文件或原生产物。ID 取 C 家族下一个空闲号（`C59` 在文档与评估索引中均未被占用）。

## 复评补充（C61，2026-10-11）

合并 `origin/beta` 后对 dev1 全部已修改代码（含本任务的已提交未推送提交）复评时，在本任务新增的 `AppVersion.isNewer(current, target)` 中发现一处真实缺陷 **F1** 并已修复：

- 原文 `if (targetStamp.length() != currentStamp.length()) return true;` 把「长度不同的两个**可用**时间戳」当成不可比较，直接判定目标为更新。负对照实测：14 位秒级时间戳的装机版本会把**更早**的 12 位时间戳渠道包判为更新（`isNewer("v5.6.0-beta-20261009175522", "v5.6.0-202609221328") = true`），与 `BUILD_STAMP` 的 `\d{12,14}` 以及 `buildStamp()` javadoc 声明的 `yyyyMMddHHmm[ss]` 相矛盾，且方向是「自动降级」。
- 修复：新增 `STAMP_LENGTH = 14` 与 `padStamp()`，把分钟级时间戳补齐为该分钟的第 0 秒后比较；只有真正缺失时间戳时才回退到历史语义。锁定用例 `AppVersionBuildStampTest#stampsOfDifferentLengthsAreStillOrdered`。
- CI 发布标签固定为 12 位（`.github/workflows/android-release.yml` 的 `TAG_PATTERN` 与 `app/build.gradle` 的 `buildTimestamp` 均为 `yyyyMMddHHmm`），因此本任务原有的 12/12 位结论与实机验证结论不受影响；修复只关闭了跨长度这一条降级路径。

详见 `docs/C61-beta-merge-review-dev1-20261011.md` 的 F1 与「验证」章节。

## 改动文件

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/utils/AppVersion.java` | 新增 `buildStamp()`、`isNewer(current,target)`、`isNewer(target)` |
| `app/src/main/java/com/fongmi/android/tv/bean/Update.java` | `hasUpdate()` 改为"仅严格更新"，并补充原因注释 |
| `app/src/test/java/com/fongmi/android/tv/utils/AppVersionBuildStampTest.java` | 新增：构建时间戳提取与新旧排序回归 |
| `app/src/test/java/com/fongmi/android/tv/bean/UpdateOrderingTest.java` | 新增：`hasUpdate()` 排序语义回归（含真实渠道清单字面量） |
| `docs/C59-android9-box-update-downgrade-fix-20261011.md` | 本文件 |
