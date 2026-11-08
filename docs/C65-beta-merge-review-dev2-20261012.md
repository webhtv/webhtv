# C65 · dev2 合并 beta（PR#440）与 TV 横幅高清化改动复评

- 任务 ID：`C65-beta-merge-review-dev2-20261012`
- 分支：`dev2`；合并前 dev2 tip = `c0ba017885`（本任务复评对象，已提交未推送）
- 合并对象：`origin/beta` = `37bca76737`（Merge pull request #440 from Silent1566/dev2）
- 合并基点：`8b5f8e9026`（C64 的合并提交，见 `docs/C64-beta-merge-review-dev2-20261012.md`）

## 1. 合并范围

`git merge --no-commit --no-ff origin/beta`，**0 冲突**，MERGE_HEAD 即 beta tip `37bca76737`。

本轮与以往各轮的结构性差别：**beta 侧相对合并基点未新增任何内容**。

| 判据 | 命令 | 结果 |
| --- | --- | --- |
| beta 相对合并基点的改动 | `git diff --name-status 8b5f8e9026 origin/beta` | **空** |
| 合并后索引树 | `git write-tree` | `a186f1c817b656c213c17b87da2111b3e3c86efa` |
| dev2 tip 树 | `git rev-parse c0ba017885^{tree}` | `a186f1c817b656c213c17b87da2111b3e3c86efa` |

索引树与 dev2 tip 树**逐字节相同**，即本次合并是内容中性的：它只把 beta tip 记入 dev2 的历史（消除 `1 behind`），不改变任何一个文件。原因是上一轮 PR #440 已把 dev2（`8b5f8e9026`）并入 beta，beta 侧唯一的增量就是那个合并提交本身。

## 2. 零复活 / 零丢失（三层证据）

用户明确要求：远端已移除/回退的提交不得顺带提交上去。

| 层 | 判据 | 结果 |
| --- | --- | --- |
| 1 · 净差异 | `git diff --name-status origin/beta dev2` | 仅 `c0ba017885` 自身的 7 个路径 |
| 2 · beta 侧改动 | `git diff --name-status 8b5f8e9026 origin/beta` | **空** ⇒ beta 未删除、未修改任何内容 ⇒ 结构上不可能复活 |
| 3 · 路径状态 | `git diff --name-status origin/beta dev2 -- <7 路径>` | 7 个路径全部为 `M`（既有文件），**无 `A`** |

第 3 层的意义：被复活的典型形态是「beta 已删除的文件又被重新添加」。这里 7 个路径在 beta 树中均**已存在**，只是内容被 dev2 改写，因此不存在「把已删文件加回来」的情形。第 2 层则更强：beta 相对基点一个路径都没动，所以「beta 已移除而 dev2 仍持有」的内容集合为空集，复活无从发生。

补充核验：对 `c0ba017885` 的 7 个路径，回溯 beta 历史上触及它们的 23 个单亲提交，逐条查看 numstat，全部是前向新增/改写（其中 `255059d48b` 是 dev4 最初的横幅按密度输出改动，见 `docs/C35-beta-merge-review-dev4-20261004.md`），没有任何一条删除过 `c0ba017885` 重新引入的内容。

## 3. 复评：dev2 已提交未推送的 `c0ba017885`

复评对象：`fix(tv): TV 横幅按 320x180dp 出图，消除启动器放大导致的字标锯齿`。

改动集（7 路径）：5 档 `app/src/leanback/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_banner.png`、`app/src/test/java/com/fongmi/android/tv/setting/AppBrandingContractTest.java`、`scripts/gen_app_icon.py`。

实质：`BANNER_SIZES` 由 `160x90 / 240x135 / 320x180 / 480x270 / 640x360 px` 上调为 `320x180 / 480x270 / 640x360 / 960x540 / 1280x720 px`，即每档由 **160x90 dp 改为 320x180 dp**；测试期望数组同步、16:9 断言与两处 manifest 引用计数断言保持不变。

### 3.1 第 1 轮：逐条核查

**① dp 尺寸与同族启动器入口自洽。** `app/src/leanback/AndroidManifest.xml` 有三处横幅引用：`<application>` 与 current 别名用 `@mipmap/ic_banner`，history 别名用 `@drawable/ic_banner_history`。后者是矢量，声明为 `android:width="320dp" android:height="180dp"`（16:9）。也就是说改动前**同一个 App 的两个启动器入口，横幅 dp 尺寸并不一致**：矢量侧 320x180 dp，位图侧 160x90 dp。位图侧才是异类，本次改动把它对齐到 320x180 dp。这是来自仓库自身的独立佐证，而非仅凭外部文档推断。

**② 机理与用户症状吻合。** 位图按 160x90 dp 出图时，资源系统在**每一档**密度下交给启动器的位图都只有磁贴 dp 尺寸的一半，启动器只能统一放大 2 倍，字标边缘因此必然出现锯齿——正是用户报的「分辨率不够、锯齿很严重」。改为 320x180 dp 后每档都 1:1。

**③ 生成器确实在目标分辨率上绘制。** `render_banner(w, h)` 的布局求解全部发生在最终像素尺寸上（`_render_mark` 按目标 O 直径直接绘制、`_render_text` 先探针量墨迹再定字号），没有任何图层被放大，所以更大的档位承载的是真实细节而不是插值像素。

**④ 独立实测（本次新补，关闭一处验证缺口）。** `c0ba017885` 记录的「5 档横幅实测」只列了像素尺寸；C35 原始评审曾实测墨迹宽度占比，但**放大后的档位从未被实测**。本次用生成器自身的 `make_gradient(max(w,h)).resize((w,h), LANCZOS)` 复现背景并与已提交 PNG 逐像素求差，得到的掩膜就是真实墨迹（而非按颜色阈值猜测）：

| 档位 | 像素 | 墨迹 bbox (l,t,r,b) | 墨迹宽/宽 | 安全区宽 | 安全区高 | 全不透明 | 墨迹像素 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| mdpi | 320x180 | (20, 50, 300, 130) | 0.8750 | ✓ | ✓ | ✓ | 8123 |
| hdpi | 480x270 | (30, 74, 450, 195) | 0.8750 | ✓ | ✓ | ✓ | 18186 |
| xhdpi | 640x360 | (39, 100, 601, 260) | 0.8781 | ✓ | ✓ | ✓ | 31587 |
| xxhdpi | 960x540 | (59, 150, 901, 389) | 0.8771 | ✓ | ✓ | ✓ | 69797 |
| xxxhdpi | 1280x720 | (79, 202, 1201, 519) | 0.8766 | ✓ | ✓ | ✓ | 122759 |

5 档全部：完全不透明、含真实墨迹（无空白/豆腐块）、墨迹落在四边各 6.1% 的安全区内、宽高比精确 16:9（1.777778）。xhdpi 的 0.8781 相对安全区 0.8780 仅溢出 0.04 px，落在求解器自身的 `total <= safe_w + 0.5` 余量内，属亚像素舍入而非越界。

**⑤ 放大档位是独立渲染，不是把小图放大。** 墨迹像素数随档位按面积增长，但**始终略低于纯面积倍率**：mdpi→hdpi 18186/18277 = 0.9950、hdpi→xhdpi 31587/32331 = 0.9770、xhdpi→xxhdpi 69797/71071 = 0.9821、xxhdpi→xxxhdpi 122759/124084 = 0.9893。若某档是把小图插值放大而来，该比值应为 ≈1.0（甚至略高）。略低于 1.0 正是抗锯齿细笔画在更高分辨率下占比下降的表现。

**⑥ 字节级确定性已证实。** 新的 mdpi/hdpi/xhdpi 与旧的 xhdpi/xxhdpi/xxxhdpi 逐字节一致（9639 / 16295 / 24216 字节，完全吻合），说明生成器对同一尺寸可复现同样字节；本次上调净新增两档（xxhdpi 960x540 41983 B、xxxhdpi 1280x720 63186 B），并非对既有档位重新编码。

**⑦ 无残留引用。** 本次真正被淘汰的只有 `160x90`、`240x135` 两档；定向检索 `app/src` 与 `scripts`（`*.py`/`*.java`/`*.xml`/`*.md`/`*.kts`）对这两个常量**零命中**。（`320x180`/`480x270`/`640x360` 不属残留——它们仍作为新的 mdpi/hdpi/xhdpi 合法存在。）`ic_banner` 的引用只有 manifest 两处、测试断言与 `_purge_banner_leftovers` 的旧产物清单（该清单指向已移除的 anydpi/foreground/default 三件套，本次未改动）。

**⑧ 代价。** 5 档横幅合计由 60930 B 增至 155319 B（+94389 B ≈ 92 KiB，PNG 已压缩，APK 增量大致相当）。运行期只解码与设备密度匹配的那一档，单张可绘制对象内存上升 4 倍（以 xhdpi 为例 320x180 → 640x360，ARGB_8888 约 0.23 MB → 0.92 MB）。对启动器磁贴可接受。

**结论：第 1 轮通过，未发现缺陷，无需修改。**

### 3.2 非阻塞观察（记录不修）

- `do_preview()` 仍以 `render_banner(320, 180)` 出预览图。改动后 320x180 已是最小档（mdpi），不再是原先那个 1080p/xhdpi 的代表档。这不是缺陷（320x180 仍是合法档位，且预览最小档用于检查可读性也说得通），但预览不再代表真实电视观感，一并记录。
- 脚本注释把 `xhdpi 320x180 px` 描述为「最小尺寸（API<=25 的旧式兜底）」，属对 Android TV 图标规范的转述。本环境无法复核该页面（文档页为 JS 渲染壳、Web 检索不可用），故记为**未复核的文档性表述**，不作为缺陷；本次改动的正确性不依赖该表述——第 3.1 节①③④⑤ 的证据均来自仓库自身与实测。

## 4. 验证

| 验证项 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| 合并冲突 | `git merge --no-commit --no-ff origin/beta` | 0 冲突 |
| 合并内容中性 | 索引树 vs `c0ba017885^{tree}` | 哈希相同 `a186f1c8…` |
| 零复活 | `git diff --name-status 8b5f8e9026 origin/beta` | 空 |
| 净差异面 | `git diff --name-status origin/beta dev2` | 恰为 `c0ba017885` 的 7 路径 |
| 横幅安全区/墨迹/不透明 | 复现背景逐像素求差（`F:\temp\wh_banner_audit.py`） | 5 档全 PASS |
| 横幅 16:9 | 同上 | 5 档均 1.777778 |
| 独立渲染（非放大） | 墨迹像素面积倍率 | 0.9950 / 0.9770 / 0.9821 / 0.9893，均 < 1.0 |
| `AppBrandingContractTest` | `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.setting.AppBrandingContractTest` | **BUILD SUCCESSFUL**（7m41s）；报告 `app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.setting.AppBrandingContractTest.xml`：`tests=13 failures=0 errors=0 skipped=0`，含 `tvBannerIsProvidedPerDensityAtFullResolution` 与 `tvBannerUsesBitmapDensitiesNotAdaptiveIcon` |

## 5. 已知限制（不在本任务范围）

- 既有 `.wh_diag/` 诊断产物为任务开始前的工作区脏文件，已由任务守卫列为保护路径，未触碰、未提交。
- 全量 JVM 套件在本机存在既有 CRLF 行尾敏感失败（根因 `core.autocrlf=true`，见 `docs/C58-beta-merge-review-dev2-20261011.md`、`docs/C35-beta-merge-review-dev4-20261004.md`），与本次改动无交集，按范围规则不扩大修复面。
- 本次合并内容中性，故不需要也不存在「合并引入的回归面」。

## 6. Recovery anchor

- 目标：把 `origin/beta` 最新合入 `dev2`（不回退已移除提交）、复评 dev2 已提交未推送改动、验证通过后提交并推送、开 PR 到 beta。
- 状态：**已闭环**。合并内容中性（索引树哈希证实）；复评 1 轮通过、未发现缺陷；定向测试 13/13 通过；已提交、已推送、PR 已创建（只创建未合并）。
- 交付坐标：
  - 合并提交：`b6c36bdf1a37fa7d4334efe20c1cd20672f32450`（双父：`c0ba017885` + `37bca76737`）
  - 恢复标签（本地）：`recovery/C65-beta-merge-review-dev2-20261012/20261012160732-b6c36bdf1a37`
  - 推送：`origin/dev2` 快进 `8b5f8e9026..b6c36bdf1a`
  - PR：[#441](https://github.com/Silent1566/webhtv/pull/441)（`dev2` → `beta`，OPEN / MERGEABLE，只创建不合并）
- 下一步：无。
