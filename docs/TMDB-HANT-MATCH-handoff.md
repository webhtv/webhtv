# TMDB 繁体匹配失败修复交接（TMDB-HANT-MATCH）

> 交接说明：本文档由上一会话的诊断结论直接转写，目标是让接手者**无需重做排查**即可动手修。
> 所有结论都已落到具体文件与行号；改动前请按第 6 节顺序读代码确认行号未漂移（文档基于 `dev3` / HEAD `17bd235c24`）。

## 1. 用户报告与目标

用户原话（m01566）：

> 发现一个问题，繁体语言的场景下会导致tmdb匹配失败

一句话目标：**把 App 界面语言设为繁体中文（`Setting.LANGUAGE_TRADITIONAL`）时，TMDB 自动匹配的成功率应当与简体中文一致；不能因为标题被简繁转换过而匹配不到。**

## 2. 结论：根因已定位（静态可证）

根因是**两套字符串在比较前没有归一到同一字符集**：

- 站源侧标题在繁体界面下已被 `Trans.s2t` 逐字改成繁体；
- TMDB 侧返回的标题始终按 `TmdbConfig.language`（用户设置项，默认 `zh-CN`）请求，是简体；
- 匹配器的 strict / contained / smart 三档判定与相似度打分**全部是字面比较**，中间没有任何简繁归一化。

⇒ 只要标题里含有 `Trans` 表的 2528 组映射字，繁体下「站源标题 ≠ TMDB 标题」，`chooseStrictMatch` 与 `chooseSmartMatch` 必然落空，`titleSimilarityScore` 掉到 Levenshtein 档，`containedYear` 也多数不成立，最终匹配失败。纯英文/数字标题不受影响 —— 与用户「部分片子匹配不上」的观感一致。

### 证据链（逐处可复核）

**1. 界面语言会改写站源标题**

- `app/src/main/java/com/fongmi/android/tv/bean/Vod.java:336-346`：

```java
public Vod trans() {
    if (Trans.pass()) return this;
    this.vodName = Trans.s2t(vodName);
    this.vodArea = Trans.s2t(vodArea);
    this.typeName = Trans.s2t(typeName);
    ...
}
```

- 调用点在解析入口：`app/src/main/java/com/fongmi/android/tv/bean/Vod.java:118-121`（`objectFrom` → `vod.trans().setFlags()`）。
- 转换表：`catvod/src/main/java/com/github/catvod/utils/Trans.java:9-10`（UTF8S/UTF8T 各 2528 字符）；`trans()` 走 `Trans.pass()`，而 `pass()` 由 `Setting.applyLanguage()` → `Trans.setTraditional(true)` 控制（`app/src/main/java/com/fongmi/android/tv/setting/Setting.java:446-450`）。
- **重要陷阱**：`Trans.s2t(String)` 在简体态直接短路返回原文（`Trans.java:42-44` → `s2t(pass(), text)`）。因此任何「归一化」都必须显式写 `Trans.s2t(false, text)` 强制转换，否则简体态等于没归一。这个坑上一会话已踩过一次。

**2. TMDB 请求语言与界面语言无关**

- `app/src/main/java/com/fongmi/android/tv/utils/TmdbLanguagePolicy.java:35-48`：`requestLanguage(config)` = `normalize(config.getLanguage())`，空 → `zh-CN`。
- `app/src/main/java/com/fongmi/android/tv/bean/TmdbConfig.java:60-61,161`：`language` 是用户在 TMDB 设置里填的字符串（`app/src/main/java/com/fongmi/android/tv/ui/dialog/TmdbSourceDialog.java:129` 可编辑），`DEFAULT_LANGUAGE = zh-CN`（`TmdbConfig.java:27`）。
- ⇒ 用户界面切繁体**不会**让 TMDB 请求变 `zh-TW`。所以「繁体界面 + 默认 zh-CN 配置」这一最常见组合，天然是「繁体站源标题 vs 简体 TMDB 标题」。

**3. 匹配器是纯字面比较**

- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbMatcher.java:140-152` `chooseStrictMatch`：`normalize(item.getTitle()).equals(normalize(keyword))`。
- 同文件 `:170-195` `chooseSmartMatch`：同样要求标题 normalize 后相等。
- 同文件 `:201-228` `sortSearchResults` → `titleSimilarityScore`：完全相等 1000 / 包含 800+ / 否则 Levenshtein。
- 同文件 `:127-138` `chooseTmdbMatch` 三档顺序：strict → containedYear → smartMatch（smartMatch 还受 `Setting.isTmdbSmartMatch()` 开关限制）。
- 全仓核实：`Trans.` 在 `app/src/main/java/com/fongmi/android/tv/title/`、`app/src/main/java/com/fongmi/android/tv/ui/helper/`、`app/src/main/java/com/fongmi/android/tv/service/` 三处 **0 命中** ⇒ 这三层完全不碰简繁，问题只能在这里补。

**4. 标题清洗规则表也全是简体词**（次要但真实的加固面）

- `app/src/main/java/com/fongmi/android/tv/title/MediaTitleParser.java:99-102`：`更新至|更至|连载至`、`国语版|国配版|粤语版|台语版|中字版|字幕版|台版|台湾版|港版…`、`真彩|臻彩|高码|无水印|无台标|国语|国配|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减…`。
- 同文件 `:405` `isNoiseTag` 复用同一简体词表；`:12-23` 的集/季/年/括号/噪声正则同样只有简体。
- ⇒ 繁体标题里出现的「國語版/連載至/全集/完結/官方中字」等**清洗不掉**，会混进搜索关键词，进一步降低命中率。
- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbMatchPolicy.java:39-46` 的分季判定比对字面 **「分季」**，繁体写作「分季」不变，此处无风险。

**5. 二级放大效应：匹配缓存键含标题**

- `app/src/main/java/com/fongmi/android/tv/bean/TmdbMatchCache.java:201-211`：`sourceKey(sourceTitle) = normalize(sourceTitle)`（只去标点并小写，**不归一简繁**），缓存键 = `siteKey@@@vodId@@@sourceKey`（`:155-166`）。
- 读写点：`app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java:1822-1827`（`getCachedMatch`）与 `:1851-1860`（`saveMatch`），都用 `vod.getName()`。
- ⇒ 繁体下写入的缓存条目，切回简体后**读不到**（反之亦然），等于每次切语言都要重新匹配一遍，放大了「匹配失败」的体感。
- 另有 `TmdbMatchCache.java:293-296` `toDisplayItem(targetLanguage)`：语言不匹配时把 title/subtitle/overview 清空返回 —— 这是**故意**的展示层保护，不要当成 bug 改掉。

## 3. 修复方向（推荐方案）

**核心原则：匹配前把两侧标题归一到同一字符集，匹配后再按界面语言展示。判等/打分一律用归一化后的字符串，绝不用已翻译的显示串。**

**归一化方向统一到繁体，且只用单向 `s2t`**（2026-10-24 用真实 TMDB 数据实测得出，以下结论已推翻本文档初稿的「压成简体」方案）：

```java
// 语义：把任意简/繁标题压成繁体统一形。必须显式传 pass=false，
// 否则简体界面下 Trans.s2t(text) 会短路成 no-op（转不转取决于全局语言状态）。
private static String canonicalTitle(String text) {
    return Trans.s2t(false, text);
}
```

> **为什么不能压成简体（`t2s`）**：`T2S` 表在 10 组「多简对一繁」的字上是**多对一坍缩**（`餘<=余,馀`、`謔<=嚯,谑`、`壟<=垄,垅`、`墊<=垫,埝`、`強<=强,犟`、`滾<=滚,磙`、`濫<=滥,漤`、`線<=线,缐`、`鯰<=鲇,鲶`、`諮<=咨,谘`），所以反向 `t2s` 只能折到「表里第一个出现的简体变体」：`t2s(餘)=馀`（不是余）、`t2s(強)=犟`（不是强）、`t2s(線)=缐`（不是线）。
> **因此 `t2s(s2t(x))` 会污染简体**：`余→餘→馀`、`强→強→犟`、`线→線→缐`、`咨→諮→谘` 等 11 个常用简体字原文会被改写成异体字（余罪→馀罪、强势→犟势、线上→缐上）。初稿把这个当推荐方案是错的，**禁止使用**。
> **而单向 `s2t` 是安全的**：对 2528 组输入只有 1 个非幂等（`鏇`），已是繁体的输入稳定不变（`慶餘年→慶餘年`），含英文/数字的标题零副作用。实测单次全串转换约 23 µs/63 字符，一次匹配几十次比较完全可忽略。
> **另有一条硬约束**：归一化结果**只能用于比较，绝不能拿去当查询词**。实测 `庆馀年`（t2s 产物）+ zh-CN 搜索 → `total=0`；`庆余年 更新至第30集` → `total=0`。查询词必须保持站源原文（或 `cleanVideoName` 清洗后的原文字形）。

### 3.1 必须改的最小面（按优先级）

| 优先级 | 文件 | 改动 |
| --- | --- | --- |
| P0 | `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbMatcher.java` | `chooseStrictMatch`(:140-152)、`chooseSmartMatch`(:170-195)、`titleSimilarityScore`(:201-228 内评分入口) 的比较与打分统一走 `canonicalTitle(...)`＝`Trans.s2t(false, text)`。**只改这里就能让匹配恢复到简体态水平。** |
| P0 | 同上 | 把 `canonicalTitle` 做成 `TmdbMatcher` 内私有静态方法；若后续 `MediaTitleParser` 也要用，再抽到 `app/src/main/java/com/fongmi/android/tv/title/` 下的工具类。 |
| P1 | `app/src/main/java/com/fongmi/android/tv/bean/TmdbMatchCache.java:201-211` | `sourceKey` 的 `normalize` 里加简繁归一，让缓存键语言无关。注意：**改 `sourceKey` 会让已有缓存条目一次性失配**（可接受，但要写进提交说明，或做新旧双键兜底）。 |
| P1 | `app/src/main/java/com/fongmi/android/tv/title/MediaTitleParser.java:99-102,405` | 噪声词表补繁体等价词：`國語版/國配版/粵語版/台語版/中字版/字幕版/台版/台灣版/港版/大陸版/內地版/中國版/泰版/韓版/日版/美版/英版`、`真彩/臻彩/高碼/高碼率/無水印/無台標/國語/國配/國粵/粵語/中字/字幕/內封/簡繁/雙語/官中/杜比/合集/全集/完結/未刪減/加長版/修復版`、`更新至/更至/連載至`。**`EPISODE_PATTERN`/`SEASON_PATTERN` 已含 `话話`、`章节節` 双写，不必重复。** |
| P2（可选） | `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java` | 若希望搜索关键词本身就是简体，可在 `buildTitleRequest`(:853-864) 归一化 `rawTitle`。**风险**：`app/src/testMobile/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterTest.java:365,378` 用 `source.indexOf("tmdbMatcher.searchAndMatch(title, vod)", ...)` 做源码文本位置断言，改这两行调用形式会破测试。 |

### 3.2 明确**不要**做的事

- **不要**改 `Vod.trans()` 让标题不再翻译 —— 那是全 App 的显示层契约，动它会波及首页/详情/历史/搜索的所有显示，回归面远大于收益。
- **不要**把界面语言自动同步成 `TmdbConfig.language` —— 用户可能故意「繁体界面 + 简体 TMDB 数据」，这是合法配置。
- **不要**动 `TmdbMatchCache.toDisplayItem`(:293-296) 的语言拒绝逻辑。
- 上一会话已修的 `Live` 身份解耦（`app/src/main/java/com/fongmi/android/tv/bean/Live.java` 的 `@Ignore transient displayName`）是**不同问题**，不要顺带改动。

## 4. 建议新增的测试（先写测试，再改代码）

放 `app/src/testMobile/java/com/fongmi/android/tv/ui/helper/`（与既有 `TmdbMatcherTest.java` 同源集，那里已有 `new TmdbMatcher(null, new TmdbConfig())` 的构造范式，见 `:21,29,88`）。

建议 4 个用例，每个都必须能在**未改代码时失败**：

1. `strictMatch_acceptsTraditionalSourceTitleAgainstSimplifiedTmdbTitle` —— TMDB 返回 `庆余年`（简体），站源标题 `慶餘年`；期望命中。
2. `strictMatch_rejectsGenuinelyDifferentTitle`（防回归）—— `慶餘年` vs `赘婿`；期望不命中，避免归一化把长尾都放过。
3. `smartMatch_acceptsTraditionalSourceTitle`（需 `Setting` 侧开关可注入时才好写；否则改为直接测 `titleSimilarityScore` 的可见入口）。
4. `matchCacheKey_isLanguageIndependent` —— 同一条目分别用 `庆余年` 与 `慶餘年` 作 `sourceTitle`；期望 `sourceKey(...)` 相等。

**测试写法注意**：`app/src/test` 是纯 JVM 源集（`app/build.gradle:87-89` 只有 `unitTests.includeAndroidResources = true`，**没有 `returnDefaultValues`**），但仓库内有手写桩 `app/src/test/java/android/text/TextUtils.java`（`isEmpty`/`equals`）。Robolectric 只在 `testLeanback`（`app/build.gradle:211`）。若要测 `TmdbMatcher` 真实逻辑（它依赖 `TextUtils`），放 `testMobile` 或 `testLeanback` 并确认该源集能解析 `TextUtils`。

另需**保住**以下既有断言，改代码后必须仍然绿：

- `app/src/testMobile/java/com/fongmi/android/tv/ui/helper/TmdbMatcherTest.java:35-85`（4 个既有用例，全在简体/英文下）；
- `app/src/test/java/com/fongmi/android/tv/utils/TmdbDetailCacheTest.java:82,90`；
- `app/src/test/java/com/fongmi/android/tv/bean/TmdbSourcePayloadTest.java:57,68-77`；
- `app/src/test/java/com/fongmi/android/tv/bean/TmdbConfigEffectiveTest.java:48`。

## 5. 已知风险与边界

1. **`Trans.s2t` 不是单射**（已实测 10 组多简对一繁：`馀←[余,馀]`、`谑←[嚯,谑]`、`垄←[垄,垅]`、`垫←[垫,埝]`、`强←[强,犟]`、`滚←[滚,磙]`、`滥←[滥,漤]`、`线←[线,缐]`、`鲇←[鲇,鲶]`、`咨←[咨,谘]`）。⇒ 归一化后可能把两个**本来不同**的标题判等。实际片名撞这 10 组字的概率极低，但**不要把归一化加到严格身份判定上**（例如 `Vod.equals` 用 id 优先，已安全，不要去动）。**反向的 `t2s` 更危险**：它是这 10 组的反向多对一，会把 11 个常用简体字改写成异体字，因此归一化只许单向 `s2t`（见第 3 节）。
2. **`Trans` 是全局可变状态**（`catvod/src/main/java/com/github/catvod/utils/Trans.java:30-32` `setTraditional(Boolean)`）。归一化函数必须**显式传 `false`**，不能依赖当前 App 语言，否则简体态下归一化变成 no-op，测试会假绿。
3. **归一化有额外 CPU**：`Trans.s2t/t2s(false, x)` 是逐字符 `HashMap.getOrDefault`，O(n)。一次匹配最多几次比较，量级可忽略；但**不要**放进排序比较器的高频路径反复调用（`sortSearchResults` 里应先算出 `canonicalTitle` 再比较，避免每次比较都转）。
4. **`MediaTitleParser` 词表补繁体**属于「加法」，要小心正则里 `|` 的顺序与重复，且必须同步 `isNoiseTag`(:405)。
5. **缓存键变更的兼容性**：`TmdbMatchCache.sourceKey` 改归一化 → 老缓存条目全部 miss。若希望平滑，可在读取时同时尝试「新键」与「旧键」（参考上一会话在 `LiveConfig` 里做的 legacy 兜底写法）。

## 6. 开工步骤（照做即可，含上一会话踩过的坑）

### 6.1 环境硬约束（**这两条不照做会浪费大量时间**）

- **不要用 `ops_bash` 跑 gradle 或写仓库子目录**。DSH 的 `ops_bash` 是 **Cygwin bash**，对本仓库写 `.codex`/`app`/`app/src` 一律 `Permission denied`，且启动 gradle 会因创建目录对象失败直接崩（exit 127）。`icacls /inheritance:e` 修过，无效，别重试。
- **正确姿势**：
  - 写文件/跑 task_guard：用 `tools.pwsh` 调 Git for Windows 的真 POSIX bash（路径 `G:/Git/bin/bash.exe`，uid=197609），命令形如 `& 'G:/Git/bin/bash.exe' -lc '<cmd>'`；脚本内单引号需替换成两个单引号。
  - 跑 gradle：用 `tools.pwsh` 直接 `& ./gradlew.bat <task> --console=plain`，并且**必须 `run_in_background: true`**，再用 `job_output(wait:true, timeout_ms:115000)` 收结果；前台一次调用会撞 run_code 的 120s 死线。无法重定向到 `.design-evidence/`（denied），也别用 `%TEMP%`。
- 已知可用：`JAVA_HOME=C:/Program Files/Microsoft/jdk-21.0.12.8-hotspot`、`git=G:/Git/cmd/git.exe`。

### 6.2 开工前必须做

1. `git status --porcelain` —— 仓库有 **2 个预存在未跟踪脏路径**：`%SystemDrive%/`、`.design-evidence/`。**不得纳入 scope，也不得提交**。
2. 确认分支/HEAD：上一会话结束在 `dev3`，最新提交 `adcf52019b`（recovery tag `recovery/LIVE-HOME-LEGACY-MATCH/20261024113907-adcf52019ba3`）。若 HEAD 已前进，先重读第 2 节引用的行号。
3. 启动 task guard（**`--id` 只在 start 用，finish 不接受 `--id`**）：

```bash
bash .codex/scripts/task_guard.sh start --id TMDB-HANT-MATCH --mode quick-fix \
  --scope app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbMatcher.java \
  --scope app/src/main/java/com/fongmi/android/tv/bean/TmdbMatchCache.java \
  --scope app/src/main/java/com/fongmi/android/tv/title/MediaTitleParser.java \
  --scope app/src/testMobile/java/com/fongmi/android/tv/ui/helper/TmdbMatcherHantTest.java
```

（scope 按实际改动裁剪；若最终不改 `MediaTitleParser`/`TmdbMatchCache` 就别把它们写进 scope。）

### 6.3 实施顺序

1. 写测试（第 4 节 4 个用例），先跑一次**确认失败**（这是证明根因最硬的证据）：

```
gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.helper.TmdbMatcherHantTest --console=plain
```

2. 改 `TmdbMatcher`（P0），再跑同一测试 → 绿。
3. 补 `TmdbMatchCache.sourceKey` 归一化（P1）+ 对应测试 → 绿。
4. 补 `MediaTitleParser` 繁体词表（P1）+ 用例 → 绿。
5. 回归：

```
gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.*" --console=plain
gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.bean.*" --console=plain
gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --console=plain
```

6. 收尾：`bash .codex/scripts/task_guard.sh finish --verified '<实测证据>' --commit-message 'fix(tmdb): 匹配前先做简繁归一，修复繁体界面下 TMDB 匹配失败'`，随后立即打本地 recovery tag（`git tag -a recovery/TMDB-HANT-MATCH/<YYYYMMDDHHmmss>-<short-sha> -m '<摘要>'`，禁用签名）。

### 6.4 设备侧复验（可选，但推荐做一次端到端）

- 可用模拟器：`127.0.0.1:5559`（= `emulator-5558`，**同一实例的两个 transport**，`android_id`/`boot_id`/`ro.serialno` 三项逐字相同），Android 9 / x86_64 / mobile flavor / 1920x1080 / 280dpi。注意它是 mobile flavor —— `SettingAppearanceActivity`（leanback）**不存在**，语言入口在设置页 → 外观与语言 → 语言。
- 语言切换 UI 坐标（1920x1080 mobile）：首页底部「设置」`(1400,1030)` → 上滑 → 「外观与语言」`(500,620)` → 语言行 `(900,768)` → 繁体 `(503,633)`。
- 读 prefs：`adb -s 127.0.0.1:5559 shell run-as com.silent.android.webhtv cat shared_prefs/com.silent.android.webhtv_preferences.xml`（直接 `cat /data/data/...` 会 Permission denied）。
- `uiautomator dump` 落 `/sdcard/w.xml` 后用 `adb exec-out cat /sdcard/w.xml` 取回（Windows 下需 `MSYS_NO_PATHCONV=1`，且**不要写 `/tmp` 或 `$HOME`**，会被拒）。
- **重要预期**：上一会话三次尝试（改 prefs 冷启动 / UI 热切换 / 热切换后立即采集）**都没有**复现「內容源載入失敗 + 闪退」，且 `logcat -b crash` 为空。本次复验只验「繁体下 TMDB 能否匹配成功」，不要期待复现原崩溃。

## 7. 与原始问题（m00002）的关系

用户最初报的是「切到台湾语言（繁体中文）提示加载失败闪退」。上一会话结论：**繁体不是崩溃原因，只是触发时机** —— 切语言触发 `recreate()`，首页 `onDestroy` 无条件 `VodConfig.clear()/LiveConfig.clear()/OkHttp.clear()/Server.stop()`（`app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java:531-548`、`app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java:1492-1513`，`isChangingConfigurations` 只传给 `AutoBackupPolicy`，没用于跳过 clear），重建时 `Decoder.getJson`（`app/src/main/java/com/fongmi/android/tv/api/Decoder.java:24-31`，**无缓存、每次真出网**）若失败就走 `BaseConfig.loadConfig` catch → `VodConfig.onConfigFailure` → `callback.error` → `progressLayout.showError(R.string.error_config_load)`「內容源載入失敗」。对简体/繁体完全对称。

**本次 TMDB 问题与上述崩溃是两个独立问题**，不要试图用同一个改动解决两者。

## 8. 相关待办（用户此前认可、尚未开工，勿混入本次改动）

- **D2**：站点名能力标签（`[采][广][导][盘][官]`）在繁体界面下会被一起转成 `[采][廣]` / `[盤]` / `[導]` / `[聽]`。这些标签不是装饰：
  `GroupRuleConfig.BUILTIN_BRACKET`（`app/src/main/java/com/fongmi/android/tv/setting/GroupRuleConfig.java:29`，正则匹配方括号内容）会把 `[采]` 抽成站点分组名，
  而分组名同时是分组排序的持久化键（`SiteGroupOrderStore`）；音频源（`AudioConfig.DEFAULT_ENABLED_RULES` = `[音]` / `[听]`）、短剧、小说、漫画与 TMDB 的站点规则也直接拿站名做子串比对。
  标签一变，已保存的分组顺序与规则勾选态就静默失效。**已于 2026-10-25 修复，见第 10 节。**
- **D3**：`Trans.s2t` 非单射（10 组坍缩，见第 5 节）。
- **D4**：`History.vodName`（`app/src/main/java/com/fongmi/android/tv/bean/History.java:64-65`）按当时语言落库，历史标题保留旧语言写法（主键 key 语言无关，不串数据）。
- **D5**：`app/src/main/java/com/fongmi/android/tv/App.java:213-226` `getResources()` 无 `synchronized`、无 `try/catch`；`ResUtil` 全走它（main 171 处 + leanback 409 处调用点，含 Task 线程池）。**已于 2026-10-25 修复，见第 10 节。**
- **S2**：`keep` 复合键里的组名也会漂移 —— `app/src/main/java/com/fongmi/android/tv/bean/Group.java:199-203` `trans()` 改写 `name`（`Channel.trans()` 只改 `show`，是对的）。 **已于 2026-10-24 修复（提交 `2b405b4b7b`）**：`Group` 新增 `@Ignore private transient String displayName`，`trans()` 只写显示名，`getDisplayName()` 供 UI，keep 键与树 id 改用与语言无关的归一名匹配。

## 10. 实施进展

| 阶段 | 状态 | 落地内容 | 验证 |
| --- | --- | --- | --- |
| P0 `TmdbMatcher` 比较侧归一 | **已完成** | 新增 `canonicalTitle()`＝`Trans.s2t(false, text)` 与 `compareKey()`＝`normalize(canonicalTitle())`；`chooseStrictMatch` / `chooseContainedYearMatch` / `chooseSmartMatch` / `titleSimilarityScore` 的比较两侧全部改用 `compareKey`；`sortSearchResults` 改为先在比较器外算一次相似度（`IdentityHashMap`），避免在排序中反复做简繁转换 | 新增 `app/src/testMobile/java/com/fongmi/android/tv/ui/helper/TmdbMatcherHantTest.java`：**改前 3/5 失败**（`strictMatch_acceptsTraditionalSourceTitleAgainstSimplifiedTmdbTitle`、`strictMatch_matchesWithTraditionalInterfaceLanguageEnabled`、`containedYearMatch_acceptsTraditionalSourceTitle`），改后 5/5 通过；`--tests "com.fongmi.android.tv.ui.helper.*"` 361 例全绿；leanback + mobile `compile*JavaWithJavac` 均 BUILD SUCCESSFUL |
| P1 `TmdbMatchCache.sourceKey` 语言无关 | **已完成** | `normalize()` 改为 `legacyNormalize(canonicalTitle(text))`，新增 `canonicalTitle()`＝`Trans.s2t(false, text)`；写入一律走新格式键；`findEntry` 增加旧格式键兜底（`legacyKey` / `legacyTitleKey`），`findManualEntry` 的别名比对同时认新旧两种归一，避免升级后历史匹配结果一次性失效 | 新增 `app/src/test/java/com/fongmi/android/tv/bean/TmdbMatchCacheHantTest.java`（简写繁读、繁写简读、手动选择跨字形仍可读、旧格式键仍可读、不同片名不粘连） |
| D2 站点名标签冻结 | **已完成** | `app/src/main/java/com/fongmi/android/tv/bean/Site.java`：`trans()` 不再整串 `Trans.s2t(name)`，改为调用新增的 `transName(String)`；后者逐字符扫描，遇到 `TAG_OPEN`（半角 `[` 与全角 `「` `【` `〔` `［`）就找 `TAG_CLOSE` 的配对字符，把整个标签段**原样** append，只有标签之外的文案才交给 `Trans.s2t(...)`；标签未闭合时剩余部分整体照常翻译。`trans()` 的唯一调用点是 `Site.java` 的 `objectFrom(...)` 末尾，其后不再读 `name`，因此下游（`SiteNameStore` 分组抽取、5 个 `*Config` 的规则匹配、5 个 `*SourceDialog` 的勾选态、`SiteGroupOrderStore` 持久化键）全部自动一致，无需改动任何下游文件 |新增 `app/src/test/java/com/fongmi/android/tv/bean/SiteTransBracketTest.java`：9 例（`荐片[采][广]` 转出 `薦片[采][广]`、`木偶「盘」夸克` 转出 `木偶「盘」誇克`、全角 `【漫】漫画站` 转出 `【漫】漫畫站`、无标签的 `电影天堂` 仍整体转繁、简繁同形标签 `[官]`/`[采]`/`[音]` 不变、标签外文案仍翻译、未闭合标签回落、简体态原样、`[广]` 不会被转成 `[廣]`）；`testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.bean.SiteTransBracketTest` **9 tests / 0 failures**，相邻回归（`SiteNameRulesTest` / `SiteGroupOrderStoreTest` / `TmdbConfigSiteRuleTest` / `SiteNameIntegrationSourceTest` / `GroupRuleTest` / `AudioPlaybackStyleRoutingTest`）与 `compileLeanbackArm64_v8aDebugJavaWithJavac` 一并通过 |
| D5 语言资源入口加锁与容错 | **已完成** | `app/src/main/java/com/fongmi/android/tv/App.java`：`resources` / `resourcesLanguage` 改为 `volatile` 并新增 `private final Object resourcesLock`；`getResources()` 先做一次无锁的 volatile 读快路径，未命中再进 `synchronized (resourcesLock)` 双重检查，**`Resources` 仍由 `super.getResources()` 取得并原地 `updateConfiguration`**（保持 Android 9 上 WebView 资源包所在的 AssetManager 不被替换）；`Setting.wrapLanguage(...).getResources().getConfiguration()` 与 `updateConfiguration` 包进 `try/catch (Throwable)`，失败时 `SpiderDebug.log("resources", ...)` 记录并继续返回框架 `Resources`，让界面降级显示而不是让异常穿透 main 171 处 + leanback 409 处取资源调用点；`invalidateResources()` 取同一把锁，避免与更新路径交错 | `app/src/test/java/com/fongmi/android/tv/AppResourcesWebViewCompatibilityTest.java` 原有「AssetManager 兼容」用例保持通过，并新增 `applicationResourcesUpdateIsSerializedAndFailSafe` 断言锁、容错与 `invalidateResources` 同锁；`testMobileArm64_v8aDebugUnitTest` 2/2 通过 |
| P1 清洗词表繁体等价词 | **已完成** | `app/src/main/java/com/fongmi/android/tv/title/MediaTitleParser.java` 的 `cleanTitle` 三处正则与 `isNoiseTag`，以及 `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbMatcher.java` 的 `cleanVideoName` 与 `isNoiseTag`，均补上高碼/無水印/無台標/國語/國配/國粵/粵語/內封/簡繁/雙語/合集/完結/未刪減/加長版/修復版/連載至/台灣版/大陸版/內地版/中國版/韓版/美國版/英國版 等繁体写法 | 新增 `app/src/test/java/com/fongmi/android/tv/title/MediaTitleParserHantTest.java`（`慶餘年（高碼）`→`慶餘年`、`與鳳行 國語中字`→`與鳳行`、`贅婿 連載至第30集`→`贅婿` 等 6 例） |

| P0 详情页匹配归一（本轮） | **已完成** | `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`：详情页自动匹配是**独立于 TmdbMatcher 的另一套实现**，此前完全没有简繁归一（日志里的「候选过滤：标题不一致，关键词=喜劇之王，候选=喜剧之王」×7 就是它）。本轮把 `normalize()` 改为 `plainNormalize(canonicalTitle(text))`，新增 `canonicalTitle()`＝`Trans.s2t(false, text)` 与 `plainNormalize()`；**27 处调用点（含 `scoreTmdbTitle`、`scoreSmartTmdbCandidates`、`scoreVod`、`isCachedTmdbMatchCompatible`、`shouldSkipRawTmdbQuery`）全部受益**，未逐个改动。同时把「只为比较服务、不能吃简繁归一」的两处显式改回 `plainNormalize`：`scoreTmdbMediaType`（类型关键词与噪声词表）与 `isTmdbNoiseTag`——它们的词表是简体字形，一旦归一就会整体失配 | 新增 `app/src/test/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivityHantMatchTest.java`（5 例源码契约） |
| P1 分季缓存键语言无关（本轮） | **已完成** | `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonMatchCache.java`：`sourceKey` 的 `normalize()` 改为 `legacyNormalize(Trans.s2t(false, text))`，分季键与界面语言解耦；旧条目按「写入时字形」存放、且 `T2S` 在 10 组「多简对一繁」上是多对一坍缩（`餘 <= 余,馀`），**不能**用 `t2s` 反推键，因此 `legacyKeyMatch` 改为遍历缓存、两侧都做同一次单向 `s2t` 后按内容比较（条目数十条量级，成本可忽略），命中仍需通过 `tmdbId`/`mediaType` 校验 | 新增 `app/src/test/java/com/fongmi/android/tv/bean/TmdbSeasonMatchCacheHantTest.java`（5 例：简写繁读、繁写简读、旧格式键仍可读、线路级键同样语言无关、不同作品不粘连）。**首次运行时 `legacyKeyWrittenBeforeNormalizationIsStillReadable` 失败**，暴露的正是「不能用 t2s 反推」这条约束 |
| 第二层：TMDB 请求语言跟随界面语言 | **待办（需先做显式配置标记）** | `TmdbLanguagePolicy.requestLanguage()` 读的是 `TmdbConfig.language`（用户可编辑，默认 `zh-CN`）。若改成跟随界面语言：①会**覆盖用户显式选择**，而配置里无法区分「用户填了 zh-CN」与「默认值」；②`TmdbMatchCache.Entry.language` 与 `TmdbSourcePayload.language` 都是缓存分键，语言一变会让**已缓存的全部分季/详情载荷一次性失配**（`toDisplayItem` 还会清空标题）。而第一层已让匹配与字形无关，第二层只改善「繁体界面下 TMDB 数据仍是简体」的观感，收益不足以抵这两项风险 | — |

### 10.1 设备实测（2026-10-26，决定性对照）

设备 `emulator-5558`（Android 9 / mobile flavor / 1920x1080 / `language=2` 繁体 / TMDB `language=zh-CN`）——正是用户报告的那个组合。同一台设备、同一份应用日志 `http://<device>:9978/debug/logs.txt`，可直接前后对照：

**修复前（旧包，10-25 09:15）** —— 站源繁体、TMDB 简体，三档全部落空：

```
tmdb-match: 候选过滤：标题不一致，关键词=蘭香如故，候选=兰香如故，候选年份=2026
tmdb-match: 自动匹配结束：没有严格标题/年份一致的候选，交给手动选择
tmdb-match: 详情消歧结束：没有可消歧候选
tmdb-match: 智能匹配结束：没有可用候选
```

**修复后（本轮包，10-26 13:38）** —— 同一站点，站源标题「第二次初戀」：

```
tmdb-match: TMDB 搜索：搜索词=第二次初戀，结果数=3
tmdb-match: 候选保留：标题=第二次初恋，媒体=tv，年份=2026，标题分=300，总分=300
tmdb-match: 候选过滤：标题不一致，关键词=第二次初戀，候选=面包，巴士和第二次初恋，候选年份=2018
tmdb-match: 候选过滤：标题不一致，关键词=第二次初戀，候选=HIGH POSITION ～1986年，第二次青春～，候选年份=2020
tmdb-match: 自动匹配成功：只有一个严格候选，标题=第二次初恋，年份=2026，评分=300
```

**关键点**：繁体「第二次初戀」与简体「第二次初恋」被判为完全一致（标题分 300），而真正不同的作品（《面包，巴士和第二次初恋》《第二次青春》）仍被正确拒绝 —— 归一化没有把长尾放过。详情页据此渲染出 TMDB 简介、`China` 产地、`第 1 季 · 更新至 24 集`，TMDB 按钮进入可用态。

| 指标（同设备日志） | 修复前 10-25 | 修复后 10-26 |
| --- | --- | --- |
| `候选过滤：标题不一致` | **34 次** | 4 次（均为真实不同作品） |
| `自动匹配成功` | **0 次** | **2 次** |

**同机顺带确认**：界面为繁体（`搜尋 / 最近觀看 / 點播 / 設定 / 線路 / 選集`），站点名显示为 `木偶[盘]`（标签 `[盘]` 冻结在原文，是 D2 修复的预期效果）。

**一个必须区分的既有问题**：日志中 `115#木偶`（站点自身首页占位条目）搜索 0 条属于**混淆名清洗**问题，与简繁无关，不要混入本条根因。

**实施中确认的两个补充事实**：

- `Trans.s2t` 是 1:1 字符映射，**不改变字符串长度** ⇒ 已有的长度阈值（如 `chooseContainedYearMatch` 的 `normalized.length() < 4`）语义不变。
- `chooseContainedYearMatch` 要求标题长度 ≥ 4，**3 字的片名走不到这一档**（`與鳳行` 属此类，只能靠 strict 档命中）；写测试时不要拿短标题去验包含式匹配。

## 9. 一句话交接

**去 `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbMatcher.java` 里给 `chooseStrictMatch` / `chooseSmartMatch` / `titleSimilarityScore` 的比较两侧都套上 `Trans.s2t(false, text)`（单向转繁，不是 t2s(s2t())），先写一个「繁体站源标题 vs 简体 TMDB 标题」的失败测试证明它，再让测试变绿。** 其余（缓存键、清洗词表）是同一根因的第二层，按 P1 顺序跟上即可。
