# C45 移除音频指纹去广告与语音广告识别

状态：已实现并完成定向验证（待用户实机确认）。

## 恢复锚点

- 目标：删除“音频指纹去广告”和“语音（语言）广告识别”两个实用性相关的功能和对应代码，向 `https://github.com/webhtv/webhtv/tree/Silent1566` 靠齐。
- 验收标准：
  1. `app/src/main/java/com/fongmi/android/tv/ad/audio` 全部代码与其测试被删除；
  2. 播放链路（`PlayerManager`）、播放页提示（`AdSkipPromptPresenter`）、设置页音频识别分组、字符串、备份/恢复钩子、Tink 依赖全部移除，无悬空引用；
  3. 保留与两个功能无关的能力：接口/HLS/AI 规则去广告、广告统计、片头片尾区间跳过、实时字幕；
  4. 两个 flavor 编译通过、全量单元测试通过、APK 覆盖安装后启动与“去广告”设置页正常。
- 分支/基线：`dev1`，任务起点 `d8daedf86c57d58c18823343d8fc6510319289b9`。
- 保护 dirty：任务开始时工作区干净，protected 为 0。
- 回滚锚点：本任务提交及其 `recovery/C45-*` 标签。

## 上游对照

`upstream/Silent1566`（`05469a967ad32818b6e76e080100bfae1787ab76`）中完全不存在以下内容，是本任务“靠齐上游”的判定依据：

- `app/src/main/java/com/fongmi/android/tv/ad/`（0 个文件）；
- `AdSkipPromptPresenter`、`SpeechRecognitionFactory`、`RealtimeSubtitleSpeechRecognitionFactory`；
- `ad_audio_*`、`speech_ad_*` 偏好键与字符串；
- `libs.tink.android` 依赖（`app/build.gradle`、`gradle/libs.versions.toml` 均无）。

上游保留且本项目必须保留的是 `Setting.isAdblock()`、`player_adblock`（上游称“智能去广”）、HLS/接口规则与广告统计。本项目在这些之上额外保留了 `ai_ad_detection`、`adRuleManage`、`adBlockStats`、`autoSkipIntroOutro`/`introSkipKinds` 和实时字幕，它们不属于本任务的删除范围。

## 本地实现复核（删除前）

两个功能共用一个自建的 `com.fongmi.android.tv.ad.audio` 包（45 个类、约 9848 行），并由本地上游提交引入：

- `a4ea3d068` 可扩展音频指纹去广告（建立 `ad/audio` 包、`PlaybackMediaSignalHub` 会话/管线租约、`AdSkipPromptPresenter`）；
- `4cf2f76e2`/`2feb7dc84`/`9355ea530` 语音关键词广告通道；
- `3f75aa4bd` 社区音频指纹规则接入 + 去广告设置独立成页；
- `1e5642857` 语音识别执行隔离（向 `SpeechRecognitionFactory` 引入 `AD_AUDIO` profile）；
- `66a0a82ce` 语音规则 V2 设置与备份；
- `3905d37b7` Tink Ed25519 签名规则包校验；
- `c3397ba04`/`5bd95ba0c` 语音降级门与验证注释。

播放侧接线点：`PlayerManager`（`AdAudioRuntimeController`、`AdAudioPlaybackPort`、`refreshAdAudioRuntime`、`MAX_AD_AUDIO_PIPELINE_REBUILDS`、`speechAdPlaybackHealth`）、`PlaybackActivity`（`bindAdAudioPrompt`/`unbindAdAudioPrompt`）、`PlaybackActivity.onPlaybackStateChanged` 中的绑定注释、`RealtimeSubtitleRecognizer` 的 `AD_AUDIO` profile、`PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO`、`Backup` 的 `SpeechAdSetting.sanitizePreferences` 通道。

删除后经复核，唯一真实消费者是实时字幕（`RealtimeSubtitleController`），因此 PCM Hub、`PlaybackMediaAudioPipeline`、`PlaybackMediaClock`、`PlaybackMediaSessionController`、`rebuildAudioPipeline()` 全部保留，只去掉 `AD_AUDIO` 这一个 consumer 种类。

## 实施记录

### 代码删除

- `app/src/main/java/com/fongmi/android/tv/ad/audio`（45 个类）与其测试 `app/src/test/java/com/fongmi/android/tv/ad/audio`（37 个类）；
- `AdSkipPromptPresenter`、`SpeechRecognitionFactory`、`RealtimeSubtitleSpeechRecognitionFactory` 及其测试；
- `SpeechAdSettingSourceTest`；
- `app/src/main/res/raw/speech_ad_rules_v1.txt`、`app/src/test/resources/probe-rules-v1-community.json`；
- 11 个只服务于这两个功能的文档（`docs/AD-AUDIO-RULES-V2-*.md`、`docs/ad-audio-fingerprint-sdk-evaluation.md`、`docs/audio-fingerprint-phase0-implementation.md`、`docs/superpowers/{plans,specs}/2026-08-1[78]-*ad-audio|speech-ad*`）。

### 接线清理

- `PlayerManager`：删除 `ad/audio` 导入、`adAudioRuntime`/`speechAdPlaybackHealth` 字段、`MAX_AD_AUDIO_PIPELINE_REBUILDS`、`adAudioPipelineRebuilds`、`AdAudioPlaybackPort` 内部类、`bindAdAudioUi`/`unbindAdAudioUi`/`reloadAdAudioSettings`/`setAdAudioAutoSkipEnabled`/`adAudioDiagnostics`/`configureAdAudioRuntime`/`refreshAdAudioRuntime`；`seekTo` 的 `PlaybackAnalyticsListener.onUserSeekRequested` 恢复为 `isExo()` 单条件；`buildEngine` 保留 `mediaSignals.detachPipeline()`（实时字幕仍需要），去掉 `adAudioRuntime.suspend()` 与重建预算；`start`/`parse` 去掉 `adAudioRuntime.suspend()`；遥测 tick、`onPlaybackStateChanged`、`onPositionDiscontinuity` 去掉 `refreshAdAudioRuntime()` 调用。
- `PlaybackActivity`：删除 `AdSkipPromptPresenter` 字段、导入与 `bindAdAudioPrompt`/`unbindAdAudioPrompt`，以及对应调用点和过期注释。
- `PlaybackMediaSignalHub`：`ConsumerKind` 收敛为 `{ REALTIME_SUBTITLE, TEST }`；`PlaybackMediaSignalHubTest` 中引用 `AD_AUDIO` 的租约用例改用 `TEST`（用例语义不变）。
- `RealtimeSubtitleRecognizer`：删除 `ExecutionProfile` 字段与参数、`AD_AUDIO` 线程优先级分支、`threadCount(profile)` 重载；`RealtimeSubtitleRecognizerTest` 删除对应断言。实时字幕保持原有 `threadCount()` 预算。
- `Backup`：删除 `SpeechAdSetting` 导入与三处 `sanitizePreferences` 包裹，直接备份/恢复/过滤 `Prefers`；`APP_PREFS` 去掉 `speech_ad_rules_v1`、`speech_ad_rules_source`、`speech_ad_builtin_enabled`；`BackupPreferenceFilterTest` 删除 `speechAdRulePreferencesFollowSettingsOption`。
- `SettingAdActivity`（leanback）/`SettingAdFragment`（mobile）：删除音频识别分组全部逻辑（指纹开关、自动跳过、社区规则源、刷新、语音开关/关键词/规则/内置/时长/模式、`notifyAdAudioRuntime`、文件选择器、快照字段）；保留总开关、AI 智能去广、规则管理、广告统计、片头片尾与片段类型。
- 布局：`activity_setting_ad.xml`、`fragment_setting_ad.xml` 删除 `setting_ad_group_audio` 整组（含 `adAudioFingerprint`、`adAudioAutoSkip`、`probeRuleSource`、`probeRuleRefresh`、`speechAdEnabled`、`speechAdKeywords`、`speechAdRules`、`speechAdBuiltin`、`speechAdSkipSeconds`、`speechAdSkipMode`）。
- 字符串：三套 `strings.xml` 各删除 66 条音频指纹/语音广告键，并把 `setting_ad_summary` 从“总开关、规则、音频识别、片头片尾”改为“总开关、规则、片头片尾”。
- 构建：移除 `libs.tink.android` 依赖与 `tink` 版本/别名（唯一使用者是签名规则包校验）。
- `scripts/pull-ad-audio-log.sh`：本次 C45 遗漏，C46 复评删除（详见 `docs/C46-beta-merge-review-dev1-20261008.md`）。
- `SettingPlaybackDefaultsTest`：`adSettingsLiveUnderTheDedicatedAdPage` 断言列表去掉音频识别相关 id，保留分组归属校验。
- `docs/settings-classification-review.md`：删除 `adAudioFingerprint` 归类行与“音频指纹规则”描述。
- `智能去广-设计文档.md`：删除 3.3 语音广告检测验收、4.2 语音广告检测通道全节、原决策 E（复用 PCM + Sherpa-ONNX 语音去广）、原决策 G（签名规则包信封）与第 21 节音频指纹 Phase 1 落地状态（其中第 4、6 项与决策 G 属被删功能）。
  - 原决策 F（管线所有权与业务启停分离）描述的 `PlaybackMediaSignalHub` / `PipelineLease` / generation 门控在删除后**仍被实时字幕使用**，属于保留能力，不能随功能一起删除。C46 复评已将其以“决策 E”恢复（内容改写为以实时字幕为现存唯一 consumer）。
  - “当前明确不承诺”的 4 条全部指向已删功能（音频指纹匹配、指纹库、第三方 H5/SDK 协议），随功能一并删除是正确的，C46 复评未恢复。

## 验证

1. `bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` → BUILD SUCCESSFUL（EXIT=0）。
2. `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest` → BUILD SUCCESSFUL；leanback 4039 tests / mobile 4885 tests，failures+errors 均为 0，skipped 各 2（与删除前既有跳过一致）。
3. 定向回归：`--tests 'com.fongmi.android.tv.bean.*' 'com.fongmi.android.tv.setting.*' 'com.fongmi.android.tv.player.audio.*' 'com.fongmi.android.tv.subtitle.*'` → EXIT=0；`BackupPreferenceFilterTest` 12 项、`SettingPlaybackDefaultsTest` 12 项、`PlaybackMediaSignalHubTest` 5 项、`RealtimeSubtitleRecognizerTest` 4 项全部通过。
4. `bash scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5555` → BUILD SUCCESSFUL，APK 191 MB，覆盖安装 Success，包存在校验通过。
5. 设备（192.168.50.3:5555，mobile）：force-stop + 冷启动后无 crash buffer 记录、无 `FATAL EXCEPTION`/`ClassNotFoundException`/`NoClassDefFoundError`/`InflateException`，进程存活；`uiautomator` 确认首页正常，设置页“去广告”摘要显示为“总开关、规则、片头片尾”。
6. 设备进入“去广告”页：仅剩“基础（去广告总开关 / AI 智能去广）”“规则与统计（广告规则管理 / 广告拦截统计）”“片段跳过（自动跳过片头片尾 / 跳过的片段类型）”，无任何音频识别或语音广告项。
7. 设备偏好文件 `shared_prefs/com.silent.android.webhtv_preferences.xml`：`ad_audio*`、`speech_ad*` 键计数为 0；`adblock`、`ai_ad_detection`、`auto_skip_intro_outro` 仍存在，既有设置未被破坏。
8. 残留引用扫描：`app/src` 下 `AdAudio|SpeechAd|AudioFingerprint|ProbeRule|AdSkipCoordinator|AdSkipPolicy|SpeechRecognitionFactory` 在 Java/XML/资源中均为 0；`docs/` 下无指向已删除文档的引用；`git diff --check` 通过。
9. 与上游对照：`app/src/main/java/com/fongmi/android/tv/ad`、`AdSkipPromptPresenter`、`SpeechRecognitionFactory`、`ad_audio`、`speech_ad` 在上游与本地的文件计数同为 0；Tink 依赖两边均无。

## 未验证/风险

- 未做电视端（leanback）实机 UI 验证，只有 leanback 单元测试与 Java 编译证据；leanback 的 `SettingAdActivity` 改动结构与 mobile 同构，风险低。
- 未做真实点播播放的广告跳过行为回归；但接口/HLS/AI 规则路径与广告统计代码未改动，且相关测试全绿。
- 用户既有偏好中残留的 `ad_audio*`/`speech_ad*` 键不再被任何代码读取，也不会再参与备份；未主动清理，以免扩大变更面。

## 下一动作

无。等待用户实机确认；如确认无问题，本任务已由 guard 原子提交并打恢复标签。
