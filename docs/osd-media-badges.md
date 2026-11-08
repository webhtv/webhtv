# OSD 媒体信息徽标（osd-media-badges）

**Recovery anchor**

- 目标：播放器显示当前媒体的徽标（分辨率 / 动态范围 / 画面来源与画质 / 视频编码 / 音频编码 / 声道 / 帧率 / 码率 / 容器 / 特别标签 / IMAX / 流媒体平台），**同一行横排**，每条为「现成徽标牌位图 + 可选文本 + 类别色胶囊」；没有对应牌的事实（帧率 / 码率 / 容器）只显示文本，不再用语义不符的自绘图标。展示语义（**第十轮，现行**）：
  - **只在全屏播放出现**：半屏 / 详情页内嵌小窗（`inlineFullscreen=false`）里无论屏显开关如何都**不显示**；
  - **总开关管显示、常驻开关管隐藏后**：`player_osd_media_badges` 是总开关（「媒体信息位置」单选：选角位=开、选关闭=关），`player_osd_media_badges_persist` 是屏显里的常驻开关（控制栏隐藏时是否继续留在画面上）。全屏 + 总开关开 ⇒ 唤醒控制栏显示；控制栏隐藏时还要常驻开关开。
  - **位置与总开关解耦**：位置单选（左上角 / 右上角 / 左下角 / 右下角 / 关闭，默认关闭）选角位只记角位，不碰屏显常驻开关；选「关闭」只清总开关、**角位键原样保留**，之后重新选角位就回到那个角。
  - **贴边优先**：控制栏隐藏时，只有同角**确实还有别的常驻文本**（标题 / 时间 / 进度 / 网速）才把徽标抬起躲开，否则直接贴到画面边缘；控制栏可见时下两角按**实测控制栏高度** + 呼吸位避让（手机量 `@+id/bottom`、电视量控制栏根高）。两种情形的呼吸位**统一为 `badgeGap() = dp(16)`**，并用 `Math.max(0, gap - badgeParent.getPaddingXxx())` 折算掉 `app/src/main/res/layout/view_player_osd.xml` 外层的 `android:padding="14dp"`，所以肉眼间距在「控制栏可见」与「控制栏隐藏且同角无邻居」两种情况完全相同。**只有量不到控制栏高度时才回退写死的 `dp(80)` + 呼吸位，仅作兜底。**
- 验收（第十轮）：非全屏唤醒控制栏**不显示**徽标；全屏 + 总开关开 ⇒ 唤醒控制栏显示、控制栏隐藏时看常驻开关；总开关关（位置=关闭）⇒ 任何情况都不显示；位置单选选角位**不改变**屏显常驻开关；电视控制栏可见时徽标整行位于控制栏顶边之上、不与按钮行重叠；同角无邻居时徽标贴边、有邻居时抬高一行；Exo/MPV/IJK 三引擎均不崩溃。（第三轮起按用户明确授权打包现成徽标位图，第四轮扩到参考 JSON 全量 **100 张**，第五轮并入扩充包再补 **27 张**、共 **127 张**，其中含 Dolby / DTS / IMAX / 各家流媒体平台的商标与字标，属于**用户已知并接受**的取舍。）
- Lane/scope：最后一轮实现为 task_guard `osd-badges-gap`（mode=standard），base HEAD `c5b0900c9396c89131f9d2b75083bf31b3d4a0f3`，产出提交 `382df25e267375d7f3c6e7792430d3d25233d9d7`，branch `dev3`；其后的评审修正走 task_guard `C111-beta-merge-review-dev3-20261029`（mode=upstream），base HEAD `382df25e267375d7f3c6e7792430d3d25233d9d7`。历史轮次见 §9。
- 回滚：单一逻辑提交，回滚该提交即可完整撤销；本轮共三个偏好键 `player_osd_media_badges`（总开关）/ `player_osd_media_badges_persist`（常驻开关）/ `player_osd_media_badge_position`（角位），未被引用时都是惰性偏好项，不会影响旧版本行为。其中 `player_osd_media_badges_persist` 由 `PlayerSetting.migrateMediaBadgePersist()`（`app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java:1032-1039`，在 `isDisplayMediaBadgesPersistent()` 首行 `:592` 调用）做**一次性迁移**：只有该键**缺失时**才把旧键 `player_osd_media_badges` 的值抄**一次**，键已存在就直接返回，所以「位置与常驻解耦」不会被旧值反向覆盖。
- 已完成轮次（按时序排列，base HEAD 均为 HEAD 的祖先，逐轮改动与验证见 §9）：`osd-badge-icons`（base HEAD `49e5b2c4bc05a5e1e05f2bc18327d1c02079b9f5`）把徽标从「常驻屏显」改为「控制栏可见时展示」并加图标；`osd-badge-plates`（base HEAD `7d4bee6ce1`）改为横排 + 类别色 + 现成徽标牌；`osd-badge-plates-all`（base HEAD `0dc165a9a2`）把参考 JSON 的 **100 条规则 + 100 张牌图全量入库**，并删掉语义不符的自绘兜底图标；`osd-badges-persist`（base HEAD `39e1b4a0d4`）把徽标从「只在控制栏可见时渲染」改为「控制栏唤醒无条件显示 + 开关打开后常驻显示」；`osd-badge-expansion`（base HEAD `192b55c2fc`）并入参考扩充包的媒体事实规则、把牌图扩到 127 张，并统一同行高度；`osd-badges-off`（base HEAD `3220d20d6c3d4a9f90b192340dae06d10e512c58`）新增「关闭」选项并让开关成为唯一总闸、关闭时保留最近一次角位；`osd-badges-fullscreen`（base HEAD `572af99c33`）改为只在全屏显示、位置与开关解耦、无重叠时贴边；`osd-badges-gate`（base HEAD `e8ccde109e21`）修掉控制栏唤醒时绕过开关的门控；`osd-badges-bar`（base HEAD `6feba34d3285`）拆开总开关与常驻开关、并让电视控制栏可见时徽标按实测栏高避让；`osd-badges-gap`（base HEAD `c5b0900c9396c89131f9d2b75083bf31b3d4a0f3`）把徽标与障碍物的呼吸位统一成 `dp(16)` 并把容器 14dp 内边距折算掉、控制栏布局定稿后重测高度。

## 1. 需求与取舍

用户参考实现（截图）：播放器片上叠加 "4K ULTRA HD / WEB-DL NO RE-ENCODE / Dolby Vision / E-AC-3 5.1 48kHz / MKV / CHS 简中" 一套徽标，并要求"匹配媒体徽标""媒体徽标来自哪里""看是放到屏显中还是独立设置"。

结论分三档（用户已确认"接受只显示可证事实""允许共存"）：

| 档位 | 内容 | 本项目处置 |
| --- | --- | --- |
| 运行时可证 | 分辨率、HDR/DV、视频编码、帧率、码率、音频编码/声道/采样率、容器 | 默认显示 |
| 文件名可证 | 画面来源（REMUX / BLU-RAY / WEB）、画质（WEB-DL / WebRip / HDTV …）、特别标签（PROPER / REPACK / HYBRID / CRITERION …）、IMAX、流媒体平台（NF / CR / DSNP …） | 第四轮起**按文件名匹配**：素材只认播放器已拿到的 URL 文件名与标题，匹配不到就不显示（宁缺毋滥） |
| 半可证 | HLS/DASH/网盘等流类型 | 不显示（避免误导，且 manifest 无容器语义） |
| 不可证 | 字幕语言、声道以外的音轨语言标签 | **放弃** |

第三轮时的处置是「放弃发布标签」，理由：本项目片源来自爬虫直链 / HLS / 网盘，`containerMimeType` 常为 `application/x-mpegURL`，**仅凭编码/分辨率硬猜** WEB-DL/REMUX 是编造事实。第四轮用户要求「参考 JSON 全部入库、全部实现」，因此改为**从文件名/标题里做正则匹配**：参考 JSON 的 100 条 `pattern` 本来就是为文件名设计的（例如 REMUX 牌要求结尾带已知发布组名），文件名字符串本身就是证据，未命中就不产牌。字幕语言要从 `Sub` 取且与"媒体信息"语义不同，仍另议。

## 2. 数据来源：为什么不能来自 TMDB

核查 `TmdbService.java`：TMDB `append_to_response` 只提供 images / credits / translations / release_dates / videos 等元数据，**没有任何码率、编码器、声道、采样率、容器字段**。因此：

- 徽标必须来自播放器运行时事实（`PlayerManager.getVideoFormat()` / `getVideoPlaybackDetails()` / `getAudioPlaybackDiagnostics()` / `getUrl()`）。
- 截图那套图标其实是社区 media-info 图标集（Kodi / Emby / Jellyfin / Kometa overlay 同源），混杂了"发布标签"和"流事实"两类信息，不能整体照搬。

## 3. 图标与商标

- **（第一轮决策，第三轮经用户授权推翻，第四轮完成全量）** 第一轮原定不引入任何第三方图片素材：社区图标集（含 "4K ULTRA HD"、"Dolby Vision" 字标）属于各项目自有或商标方所有，直接打包有许可与商标风险。用户在第三轮明确表示**忽略许可证顾虑**、要求「用主流现成图标」，第四轮进一步要求「参考 JSON 的牌图全部入库」。因此最终打包参考徽标库的 **100 张**，并在第五轮并入扩充包的 **27 张**，共 **127 张 `.webp`**（`app/src/main/res/drawable-nodpi/badge_<group>_<slug>.webp`，合计约 646 KB），运行时零网络请求、零新依赖。
- 牌图来源与清洗：参考 JSON 的每条 `filter` 都带 `imageURL`（`https://cdn.xperience-app.com/badges/kingsize/<group>/<name>.webp`）。入库时用 PIL 按 alpha `getbbox()` 裁掉透明边，再等比缩放到**统一高度 72px**（原图内容高度 44–2158px 不等，不统一就会让同一行高低参差）。
- DOLBY、DTS、IMAX、各家流媒体平台字标等是注册商标 / 认证字标。本实现只在**播放当前媒体确实具备该属性时**显示对应牌（属性来自运行时轨道参数或文件名正则），属对播放内容的客观说明，不做官方认证暗示。
- 视觉分两层：
  - **有对应牌的事实** → 直接显示现成徽标位图（分辨率 / 动态范围 / 画面来源 / 画质 / 视频编码 / 音频编码 / 声道 / 特别标签 / IMAX / 流媒体平台），牌已写明的事实在文本里去重（去重逻辑在 `MediaBadgeResolver` 的 `audioBadge(...)` / `channelsBadge(...)`：认出对应牌时文本只留牌没写的部分——音频只剩采样率与码率，声道文本留空）。
  - **没有对应牌的事实**（帧率 / 码率 / 容器）→ **只显示 10sp 白色大写文本**，不再使用自绘矢量图标。第四轮删除了 7 个 `ic_badge_*.xml`，因为「显示器 / 齿轮 / 秒表 / 信号条 / 录像带」这类通用图形与具体事实语义不搭边（用户明确指出的问题）。
- chip 胶囊底色：`PlayerOsdController.categoryBackground(Group)` 运行时生成 `GradientDrawable(LEFT_RIGHT, {withAlpha(categoryColor, 0x8A), 0xD9040A12})` + `setCornerRadius(dp(4))` + 1dp 描边 `withAlpha(categoryColor, 0xE6)`；类别色见 `mediaBadgeColor(Group)`（分辨率 `#5A8DEE` / 动态范围 `#7B61FF` / 视频技术 `#22D3EE` / 画面来源 `#0EA5E9` / 画质 `#A855F7` / 版别 `#8B5CF6` / 视频编码 `#14B8A6` / 音频 `#EF4444` / 声道 `#F9731A` / 帧率 `#10B981` / 码率 `#F59E0B` / 特别标签 `#EC4899` / IMAX `#2563EB` / 流媒体 `#DC2626` / 容器 `#64748B`）。参考 JSON 里 10 组的配色其实全同（`tagColor #FF000000` / `borderColor #FFFFFFFF` / `textColor #FFFFFFFF` / `tagStyle bordered`），所以**炫彩类别色是本项目自定的**，不是参考数据给的。
## 4. 三引擎事实覆盖度

| 事实 | ExoPlayer | MPV | IJK |
| --- | --- | --- | --- |
| width/height、frameRate、codecs、bitrate、ColorInfo | ✅ `Format` | ✅ `TrackInfo.toFormat()`（codecs/mime/宽高/帧率/ColorInfo/采样率/声道/平均码率） | ⚠️ `IjkSimplePlayer.buildFormat()` 字段较少 |
| Dolby Vision profile | ✅ `getVideoPlaybackDetails()` | ✅ | ➖ |
| 音频 codec/声道/采样率/码率 | ✅ `getAudioPlaybackDiagnostics().activeTrack()` | ✅（`MpvPlayer`） | ✅ `getAudioPlaybackDiagnostics()` |
| `containerMimeType` | ✅ | ❌ 不设置 | ❌ 不设置 |

因此容器徽标的策略是：**先看 `containerMimeType`，为空或未知时再退回 URL 扩展名白名单**（mkv/mp4/m4v/webm/mov/avi/flv/ts/m2ts/wmv/mpg/mpeg/3gp/ogv）；`m3u8`/`mpd` 不在白名单内，所以 HLS/DASH 永远不会冒出容器徽标，宁缺毋滥。

## 5. 方案对比

1. **不改**：屏显已有时间/网速/分辨率/进度/迷你/标题/诊断，缺"编码-码率-音轨"这一层信息；用户明确要求补，且这是排查"为什么糊/为什么没声音"的第一手线索。
2. **照搬社区徽标方案的整体做法**（打包整套图标集 + 直接用它们的 `pattern` 在**运行时**对媒体流做判定）：可证事实部分可行，但社区方案里绝大部分牌依赖**发布文件名**（REMUX / BLU-RAY / WEB-DL / 压制组 / 平台前缀），运行时轨道参数里根本没有这些字段；硬套会输出编造事实。
3. **窄适配（选中）**：**运行时事实**（分辨率/动态范围/编码/音频/声道/帧率/码率/容器）由播放器轨道参数决定；**文件名类牌**（画面来源/画质/特别标签/IMAX/流媒体平台）只对「播放器已拿到的 URL 文件名 + 标题」做正则匹配，未命中即不产牌；牌图用参考库全量 100 张 + 扩充包 27 张。可证、可测（纯 JVM 单测）、零新依赖、可回滚。

## 6. 实现落点

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/player/MediaBadgeCatalog.java` | 新增。参考 JSON 的 **100 条规则原样内嵌**，第五轮再并入扩充包 **27 条**（共 127 条）：`enum Group {RESOLUTION, DYNAMIC_RANGE, VIDEO_TECH, VIDEO_CODEC, AUDIO, CHANNELS, FRAME_RATE, BITRATE, CONTAINER, MEDIA_SOURCE, QUALITY, EDITION, SPECIAL_TAG, IMAX, STREAMING}`、`record Rule(Group group, String plate, String name, String pattern, boolean runtime)`、`rules()` 返回全部规则、`patterns()` 懒编译并双检缓存 `Pattern[]`。已验证全部 pattern **都能被 Java `Pattern.compile` 接受**（含带零宽字符的 `gq_hc_hdrip`），无需手写等价规则。 |
| `app/src/main/java/com/fongmi/android/tv/player/MediaBadgeResolver.java` | 纯逻辑。`record MediaBadge(String text, Group group, String plate)`；`resolve(Format video, PlayerEngine.VideoPlaybackDetails videoDetails, AudioPlaybackDiagnostics.Track audio, String url, String title)`；运行时组（RESOLUTION / DYNAMIC_RANGE / VIDEO_CODEC / AUDIO / CHANNELS）由轨道参数产出，其余两组来源见下；`displayOrder(Group)` 固定输出顺序；非运行时组**每组最多一枚**，避免把 OSD 塞满。 |
| `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` | `setMediaBadges(player)` 调用点补第 5 参 `source.getTitle()`；签名串改用 `badge.group()`；`createMediaBadge(...)` 只显示牌 + 文本（**删除 `mediaBadgeIcon(...)` 自绘图标分支**，该方法现已不存在）；`categoryBackground(Group)` / `mediaBadgeColor(Group)` 改为 13 个分组（第六轮扩到 15 个）；`plateResource(String)` 改为查 `Map<String,Integer> PLATES`，`plates()` 里 127 行显式 `R.drawable.badge_*` 常量引用（防 release `shrinkResources` 误删）；`createMediaBadge` 的胶囊改为**固定高度 `dp(20)`**，让有牌与纯文本徽标同行等高。 |
| `app/src/test/java/com/fongmi/android/tv/player/MediaBadgeResolverTest.java` | 367 行 / 27 用例：分辨率十档（8K/4K/1440P/1080P/720P/576P/480P/360P/240P/144P）、竖屏按长边分档、动态范围各档（含 12bit）、编码映射（含 VVC）、帧率、码率、音频与声道拆分（含 LPCM）、容器 mime 与扩展名、文件名类牌（REMUX / BluRay / 特别标签 / 流媒体平台 / IMAX / 版别 / HDR Vivid / 3D / 高码率 / ISO）、运行时事实压过文件名、纯 URL 不产牌、`catalogIsConsistent`（127 条、plate 唯一、runtime 标记与分组一致）。 |
| `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java` | 三个键：`player_osd_media_badges`（总开关，默认 false）、`player_osd_media_badges_persist`（常驻开关：控制栏隐藏时是否继续留在画面上）、`player_osd_media_badge_position`（角位，默认 3=右下）；`getDisplayChecked()` 8 元素、`putDisplayChecked()` 索引 7；`isOsdEnabled()` 计入徽标；`isDisplayMediaBadgesPersistent()`（`:591-598`，首行 `:592` 调 `migrateMediaBadgePersist()`）在 `player_osd_media_badges_persist` 缺失时把旧键 `player_osd_media_badges` 的值一次性抄过来（迁移实现 `:1032-1039`）。 |
| `app/src/main/java/com/fongmi/android/tv/bean/Backup.java` | 三个新键（`player_osd_media_badges` / `player_osd_media_badge_position` / `player_osd_media_badges_persist`）加入 `APP_PREFS`（`:46`）。 |
| `app/src/main/res/layout/view_player_osd.xml` | `FlexboxLayout @+id/osdMediaBadges`：`flexDirection=row` + `flexWrap=wrap` + `justifyContent=flex_end` + `alignItems=center` → 同一行横排、放不下才换行。 |
| `app/src/main/res/drawable-nodpi/badge_<group>_<slug>.webp` | **127 张**现成徽标牌（合计约 646 KB）。命名规则：`badge_` + 参考 JSON 的 `groupId` + `_` + `imageURL` 的目录与文件名（`-`/`.` 转 `_`），例如 `gr` + `resolution/4k.webp` → `badge_gr_4k.webp`；`gms` + `media-source/blu-ray-3.webp` → `badge_gms_blu_ray_3.webp`。放在 `-nodpi` 保证不随屏幕密度缩放。 |
| `app/src/main/res/drawable/ic_badge_*.xml` | **第四轮删除全部 7 个**（分辨率 / 动态范围 / 视频编码 / 帧率 / 码率 / 音频 / 容器）。原因：通用图形与具体事实语义不搭边，用户点名要求去掉。 |
| 三语 `strings.xml` | `display_media_badges`、`osd_media_badge_position`、`select_media_badge_position` 四角 + 「关闭」共 5 项；`select_player_osd` **末尾**追加第 8 项。 |
| 两设置页布局 + `SettingPlayerActivity` / `SettingPlayerFragment` | `@+id/osdMediaBadgePosition` 行 + `ChoiceDialog.showSingle` 四角 + 「关闭」单选（5 项）。 |
| `app/src/main/res/layout/dialog_display.xml` | `@+id/display_media_badges` 芯片（`@style/DisplayControl`，默认 gone，仅 `Mode.PLAYER_OSD` 显示）。 |
| 两 flavor `app/src/{leanback,mobile}/res/layout/dialog_control.xml` | OSD 芯片行追加 `@+id/displayMediaBadges`（`@style/ControlSheetButton`）。 |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/DisplayDialog.java` | `enum Display.MEDIA_BADGES`、`setDisplay` switch 写 `putDisplayMediaBadges`、`initPlayerOsdView()`/`initPlayerOsdEvent()` 接线。 |
| 两 flavor `app/src/{leanback,mobile}/java/.../ui/dialog/ControlDialog.java` | `displays` 列表末尾追加 `binding.displayMediaBadges`；leanback 另加入 `bindRemoteFocus()` 焦点链。 |

### 6.1 运行时组的具体判定

- **分辨率**：按**长边**分档（竖屏素材不会被短边压成低清档）≥7000 `gr_8k` / ≥3840 `gr_4k` / ≥2560 `gr_1440p` / ≥1920 `gr_1080p` / ≥1280 `gr_720p` / ≥1024 `gr_576p` / ≥854 `gr_480p` / ≥640 `gr_360p` / ≥426 `gr_240p` / ≥256 `gr_144p`；都不满足时输出 `长边X短边` 文本、无牌。
- **动态范围**：Dolby Vision → `gv_dolby_vision` 牌 + `P{profile}` 文本；`COLOR_TRANSFER_ST2084` → `gv_hdr10`；`COLOR_TRANSFER_HLG` → `gv_hlg`；其余 HDR → `gv_hdr`；位深 ≥12 → `gv_12bit`、≥10 → `gv_10bit`（仅在 SDR 路径上补位深牌）；SDR 且 8bit 不产牌。
- **视频编码**：`dvhe`/`dvh1` → `ge_hevc`、`dav1` → `ge_av1`、`dvav`/`dva1` → `ge_avc`、`hev1`/`hvc1`/`hevc`/`h265` → `ge_hevc`、`avc`/`h264` → `ge_avc`、`av01`/`av1` → `ge_av1`；`vvc`/`h266`/`h.266`/`x266` → `ge_vvc`；VP8/VP9/MPEG-4 无对应牌，只输出文本。
- **音频 / 声道**：拆成两枚。音频牌由 `AudioPlaybackDiagnostics.codecLabel(...)` 归一后映射（AAC / FLAC / Opus / DD / DD+ / TrueHD / Atmos 系列 / DTS 系列），牌为空时把编码名拼在文本前（新增 `LPCM` 牌）；声道牌由 `channelLabel(int)` → 2.0 / 5.1 / 6.1 / 7.1，牌已写明则文本只留采样率与码率。**5.1.2 / 5.1.4 / 7.1.4 只能靠文件名兜底命中**：`AudioPlaybackDiagnostics.Track` 只携带声道**个数**，`channelLabel(int)` 无法表达高度声道，而 CHANNELS 又属运行时组，故这三张牌实际仅在 URL/标题里出现该字样时才有机会产出（且运行时组已被占用时不会触发）。
- **帧率 / 码率 / 容器**：保留原逻辑（整数帧率 `60FPS`、小数去尾零 `23.976FPS`；`max(averageBitrate, bitrate)` ≥1e6 → 一位小数 `MBPS` 否则 `KBPS`；容器先 `containerMimeType` 再 URL 扩展名白名单，`m3u8`/`mpd` 不在白名单）。**三类都无牌，只显示文本。**

### 6.2 文件名组的具体判定

候选串 = `path(url)`（去掉 query/fragment 的最后一个路径段）+ `source.getTitle()`（去重）。对候选串依次跑 127 条 `Pattern.find()`：

- 非运行时组（MEDIA_SOURCE / QUALITY / EDITION / SPECIAL_TAG / IMAX / STREAMING / **VIDEO_TECH** / FRAME_RATE）**每组只取第一条命中**，顺序按参考 JSON 的原始顺序，避免同一行堆满同类牌。
- 运行时组（分辨率 / 动态范围 / 视频编码 / 音频 / 声道）如果轨道参数**没有**产出，再用文件名兜底补一枚；已经产出的一律不被覆盖（`runtimeFactsWinOverTheFileName` 用例守住这条）。
- 第五轮新增的两条非运行时组：**VIDEO_TECH**（HDR Vivid / 3D）与 **EDITION**（CC标准收藏 / 港台日美英德法欧韩版 / 箭影 / 碟影 / 全区版），都只认文件名；同组多命中时只保留排在前面的那条（例如 `HDR.Vivid.3D` 只出 HDR Vivid）。
- 已知行为：`gms_remux_1/2/3`、`gms_blu_ray_*`、`gms_web_*` 的 pattern 要求文件名结尾带已知发布组名（如 `-FraMeSToR`），没有发布组的 `...REMUX...mkv` 只会命中 `gq_remux`。这是参考实现的原样行为，未做放宽。

## 7. 开关入口（全部等价）

| 入口 | 载体 | 文案 |
| --- | --- | --- |
| 设置 → 屏显设置（多选） | `select_player_osd` 第 8 项 → 两 flavor 的 `PlayerOsdDialog`（`app/src/mobile/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java` / `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java`，`DialogFragment` + `RecyclerView`），条目右端是 `app/src/{mobile,leanback}/res/layout/adapter_player_osd.xml:30` 的 `@+id/visible` 眼睛按钮（`ic_player_button_visible` / `ic_player_button_hidden`），行样式由 `PlayerSettingsRowTheme.apply(...)` / `applySwitch(...)` 应用 | 「媒体信息」（zh-rCN）/「媒體資訊」（zh-rTW）/「Media info」 |
| 播放控件 → 屏显芯片弹窗（TV/手机） | 两 flavor `ControlDialog` 第 8 个芯片 `displayMediaBadges` | 同上 |
| TMDB 详情页内联控件 → 屏显芯片弹窗 | `DisplayDialog`（`Mode.PLAYER_OSD`）`display_media_badges` | 同上 |
| 设置 → 播放设置 → 媒体信息位置（单选） | `ChoiceDialog.showSingle`，5 项：左上角 / 右上角 / 左下角 / 右下角 / 关闭 | 「媒体信息位置」 |
| 播放控件 → 屏显芯片弹窗的「媒体信息」芯片 | 两 flavor `ControlDialog` / `DisplayDialog`：写的是**常驻开关** `player_osd_media_badges_persist`（控制栏隐藏时是否留在画面上）；勾上时顺带把总开关 `player_osd_media_badges` 打开（勾选即视为要显示），取消勾选只取消常驻、不动总开关 | 同上 |

上表各入口共读写三个键（`player_osd_media_badges` 总开关 + `player_osd_media_badges_persist` 常驻开关 + `player_osd_media_badge_position` 角位）；`ControlDialog.toggleDisplaySetting(index)` 与 `DisplayDialog.setDisplay(...)` 都汇入 `PlayerSetting.putDisplayChecked(...)` / `putDisplayMediaBadges(...)`，随后调用 `onDisplayChanged()` / `inlineControlDialogDisplayChanged()`，由 `PlayerOsdController.render()` 下一次轮询生效。

## 8. 已知缺口（有意为之）

- 徽标的展示路径（第十轮，现行）：门控是 `player == null || !source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges() || (!controlsVisible && !PlayerSetting.isDisplayMediaBadgesPersistent())`——**全屏 + 总开关必须同时成立**，控制栏隐藏时还要常驻开关成立。suppressed 分支的 `renderMediaBadgesOnly()` 只在控制栏唤醒时进入，查 `!source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges()`。
- 角位内缩（第十一轮改写，现行）：控制栏可见时上两角 `topMargin=dp(64)`（**仍是固定 64dp，属已知未实测项**）、下两角按 `private int controlBarTopInset(View bar, int edgeGap)` 量**实测控制栏占位高度 + 呼吸位**。控制栏隐藏时用 `persistentNeighbour(position)` 判断同角还有没有别的常驻文本：
  - 没有邻居 → 贴边，`edgeTop/edgeBottom/edgeStart/edgeEnd = Math.max(0, dp(16) - 对应 padding)`（把容器 14dp 内边距折算掉，不再是 `dp(2)`）；
  - 有邻居 → `clear = Math.max(neighbour.getHeight(), dp(18)) + dp(4)` 抬高躲开。
  - 下两角 `bottomMargin = controlsVisible ? barTop : (stacked ? clear : edgeBottom)`；上两角 `topMargin = controlsVisible ? dp(64) : (stacked ? clear : edgeTop)`。
  - `controlBarTopInset(View, int)` 内部：`bar` 为 null 或不可见 → 回退 `dp(80) + edgeGap`；否则依次量 `@+id/bottom` → `@+id/playerBottom` → `bar.getHeight()`，量到的值 `<= 0` 或 `>= root.getHeight()` 时同样回退 `dp(80) + edgeGap`（**该 dp(80) 仅作量不到时的兜底**）。
  - `controlBar()` 的查找顺序是 `@+id/control` → `@+id/detailControlHost` → `@+id/playerControls`，按可见性取第一个（避免拿到 GONE 容器而错误回退写死值）。
  - `applyMediaBadgePosition()` 首行有 `if (mediaBadges == null) return;` 判空；`observeControlBar(bar)` 登记的布局监听由 `stopObservingControlBar()` 在 `release()` 里注销。
- mobile 播放器在 `showControl()` 里先 `setOsdSuppressed(true)` 隐藏整个 OSD 根，因此徽标走 `renderMediaBadgesOnly()` 这条独立放行路径。
- 徽标与标题/分辨率/时间**允许同角共存**（用户确认）：徽标容器是独立 `FlexboxLayout`，同角重叠时由 `layout_gravity` 决定层级，二者可能相互覆盖——有意的「零侵入」取舍。
- 文件名类牌依赖**播放器拿到的文件名/标题**：网盘直链常是随机串（如 `/d/xxxxx`），此时这类牌一律不出现；这不是缺陷，是「不编造」的边界。
- `gms_*` 来源牌与部分 `gq_*` 画质牌要求发布组名，缺组名的文件名只能命中更粗的画质牌。
- 音频**语言**与字幕语言不产牌：`Sub` 的语言标签与「媒体信息」语义不同，另议。
- MPV/IJK 引擎的 `Format` 不设 `containerMimeType`，其容器徽标只能来自 URL 扩展名白名单，网盘直链可能因此缺容器徽标。
- 同一行徽标**等高靠容器固定高 `dp(20)` 实现**（第五轮）：牌位图按 72px 高统一缩放进 `dp(16)` 高度，纯文本胶囊原先只有一行 10sp 文本，两者会差 4px；现在外层 `LinearLayout` 直接给定高，牌与文本都居中，摆脱「牌撑高、文本塌陷」的参差。
- 高度声道（5.1.2 / 5.1.4 / 7.1.4）**没有运行时来源**：`AudioPlaybackDiagnostics.Track` 只带声道个数，`channelLabel(int)` 的映射上限是 7.1；三张高度声道牌只在文件名/标题里出现该字样时才有机会产出，且刊物组 CHANNELS 若已被运行时轨道占用则不补。要真正识别需扩展 `Track` 或解析 codec 串，属另一任务。
- 本轮的扩充包 27 条里，`gv_8bit` / `gv_12bit` 只在**SDR 路径**上作为位深牌出现（HDR 已有各自的动态范围牌），`gvf_3d` / `gvf_hdrvivid` / 13 张版别牌只认文件名；这些都属于「有证据才显示」，不会凭空产生。

## 9. 验证

- `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "*MediaBadgeResolverTest*" --tests "*PlayerDisplaySettingSyncTest*" --tests "*ControlDialogOsdSettingsTest*"`
- `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "*PlayerDisplaySettingSyncTest*" --tests "*MediaBadgeResolverTest*"`
- `./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:assembleMobileArm64_v8aDebug`

### 设备实测（emulator-5554，x86_64 + ARM 转译，1920×1080，sdk28，zh-CN）

- 第 8 个芯片：播放控件弹窗内 `resource-id=com.silent.android.webhtv:id/displayMediaBadges`、`text=媒体信息`；点击后 `selected` false→true，prefs 落盘 `<boolean name="player_osd_media_badges" value="true" />`。
- 屏显设置多选摘要：勾选后 `@id/osdText` 显示「媒体信息」，证明 8 项 `select_player_osd` 与 8 元素 `getDisplayChecked()` 长度同步、无索引错位。
- 位置四角（`player_osd_media_badge_position` → `@id/osdMediaBadges` bounds）：`0` 左上 / `1` 右上 / `2` 左下 / `3` 右下（默认），控制栏可见时四角均内缩避开控制栏。
- 控制栏自动隐藏后再 dump，只剩 `@id/osd`，**没有 `@id/osdMediaBadges` 节点** → 这条是**改动前**（开关关闭）的观测；`osd-badges-persist` 之后改为：开关关闭时依旧如此，开关打开时常驻路径同样渲染徽标（见 §9 本轮实测）。
- 全屏下位置 `1`（右上）徽标 `[1644,519][1895,735]`，与进度条不重叠。

### 第八轮：只在全屏显示 + 位置与开关解耦 + 贴边（task_guard `osd-badges-fullscreen`，base HEAD `572af99c339c455c94eebc7079ecd2ddef8811af`）

*（本节记录的是第八轮当时实现，已被后续轮次取代：第十轮拆出常驻开关并按实测控制栏高度避让，第十一轮统一 `dp(16)` 呼吸位并改用带参 `controlBarTopInset(View, int)`，第八轮再修订又把 `putMediaBadgeChoice(int)` 改回「选角位即开总开关」；现行门控见 §8，现行入口见 §7。）*

用户原话（四条）：「1. 只有全屏播放才显示媒体信息无论是否开启对应的屏显 2. 关闭屏显时不修改媒体信息位置，只有开启屏显时媒体信息位置是关闭的才恢复成最近的一个开启值 3. 开启媒体信息位置不自动开启对应的屏显，因为用户想要的只是唤醒控制台时能看到而不是一致能看到 4. 当用户开启屏显但又没和其他屏显重叠时应该处于最靠边缘的位置，比如右下角同时有媒体信息屏显和网速屏显时才处于截图中的位置」。

第 1 条的歧义（全屏内开关还管不管）与第 3 条的摘要文案，已用提问确认，用户选：**A = 全屏唤醒控制栏就显示、开关只决定「控制栏隐藏时是否常驻」；非全屏任何情况都不显示**；**A = 摘要仍显示「关闭」，但记住刚选的角位**。

#### 改动

1. `PlayerOsdController.Source` 新增 `default boolean isFullscreen() { return true; }`；`PlayerOsdController.setMediaBadges` 门控改为 `player == null || !source.isFullscreen() || (!controlsVisible && !PlayerSetting.isDisplayMediaBadges())`；`renderMediaBadgesOnly()` 的首个判断由「开关」改为 `!source.isFullscreen()`。（该门控此后又被第十轮、第十二轮继续修订，现行写法见 §8。）
2. `PlayerSetting.putMediaBadgeChoice(int)` 当时**删掉**了 `putDisplayMediaBadges(true)`：选角位只 `putMediaBadgePosition(which)`，屏显开关状态完全不动（需求 3）；选「关闭」仍是只 `putDisplayMediaBadges(false)`、角位键原样保留（需求 2）。**该点已被「总开关 / 常驻开关」拆分（第八轮再修订，并入 `osd-badges-bar`）取代**：现行 `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java:633-640` 的 `putMediaBadgeChoice(int)` 选角位是 `putMediaBadgePosition(which)` 后**再** `putDisplayMediaBadges(true)`（选角位=开总开关），选「关闭」仍只 `putDisplayMediaBadges(false)` 并保留角位键。
3. 全屏态接线：`VodPlayerUiHost` 新增 `default boolean isFullscreen() { return false; }`；`VodPlayerUiController` 构造 OSD 时补 `isFullscreen() { return host.isFullscreen(); }`；mobile / leanback `VideoActivity` 各自在匿名 host 里返回 `VideoActivity.this.isFullscreen()`；`TmdbDetailActivity` 返回 `inlineFullscreen`。
4. `applyMediaBadgePosition()` 重写：新增 `persistentNeighbour(int position)`（同角的 `topLeft/topRight/bottomLeft/bottomRight` 谁可见就返回谁）与 `isShown(View)`；控制栏隐藏时 `stacked = neighbour != null`，旧的贴边常量（第十一轮已改为 `Math.max(0, dp(16) - 对应 padding)` 的 `edgeTop/edgeBottom/edgeStart/edgeEnd`），`clear = max(neighbour.getHeight(), dp(18)) + dp(4)`；无邻居时四角直接贴边，有邻居时按邻居高度抬起（需求 4）。同时把 `setMarginStart/setMarginEnd` 纳入「无变化则不 relayout」的比较。
5. `PlayerDisplaySettingSyncTest`：改写 `mediaBadgesAreGatedByTheMediaInfoSwitchOnEveryPath`（断言含 `!source.isFullscreen()` 与 `!controlsVisible && !PlayerSetting.isDisplayMediaBadges()`；`renderMediaBadgesOnly` 断言 `if (!source.isFullscreen())`；inset 断言当时指向贴边常量与 `clear` 与 `persistentNeighbour`；第十一轮已把该断言换成 `edgeBottom` 与禁止 `edgeInset`）；改写「选角位」断言为**不得**含 `putDisplayMediaBadges(true)`；新增 `mediaBadgesOnlyShowInFullscreenPlayback`（断言 Source 接口、host 接口、controller 转发、两 flavor VideoActivity `return VideoActivity.this.isFullscreen();`、详情页 `return inlineFullscreen;`）。

#### 验证（emulator-5554，1920×1080，sdk28，zh-CN，mobileArm64-v8a debug）

- 单测：`PlayerDisplaySettingSyncTest` leanback 11 例 0 失败、mobile 11 例 0 失败；`ControlDialogOsdSettingsTest` mobile 9 例 0 失败；整任务里 8 例既存环境失败（MpvFontConfigTest / MpvHlsCacheCoordinatorTest / NativeEnhancedPlaybackStyleFocusTest / ReaderPlaybackRoutingSourceTest / SearchResultDownFocusTest / TmdbSourceOnlyInteractionTest / WebThemeTokenSourceTest），读取的文件都不在本轮 diff 内。
- 构建安装：`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL，`adb install -r -t` 到 emulator-5554 与 127.0.0.1:5559 均 Success。
- **非全屏不显示**：详情页内嵌小窗（`TmdbDetailActivity`，`inlineFullscreen=false`）播放中，prefs `player_osd_media_badges=true`，控制栏唤醒后 dump 中无 `osdMediaBadges` 节点；画面只有内嵌控件的标题/分辨率。
- **全屏唤醒显示**：同一 prefs 下进入 `VideoActivity` 全屏，唤醒控制栏后 `osdMediaBadges` 出现；`player_osd_diagnostics` 关闭时诊断面板不出现。
- **选角位不改开关**：设置页「媒体信息位置」默认摘要仍是「关闭」（`getMediaBadgeChoice()` 按开关走），选左上角后 prefs 只写 `player_osd_media_badge_position=0`，`player_osd_media_badges` 保持原值。
- 截图（`s22`/`s24` 非全屏无徽标、`s26` 全屏出现徽标）按 task guard scope 约束不进入仓库，暂存于同机 `%TEMP%`。

### 第十轮：徽标抬到真实控制栏之上 + 总开关/常驻开关拆分（task_guard `osd-badges-bar`，base HEAD `6feba34d3285c5e70ea2c9a77e809e1843f4eb76`）

*（本节记录的是第十轮当时实现，已被第十一轮的 `dp(16)` 呼吸位与带参 `controlBarTopInset(View, int)` 取代，现行行为见 §1 与第十一轮。）*

用户反馈（截图：电视版底部控制栏按钮行「下集/上集/选集/搜索/EXO/硬解/播放参数/链路诊断/硬解能力/1x/原始/LUT/字幕/音轨」正上方紧贴着一行 FHD/AVC/AAC 44.1KHZ/2.0/TS）：「电视版的媒体信息怎么在控制栏选择框里面？应该是独立的才对，是重叠了还是放进去了？」

**根因**：不是被放进控制栏，而是同一父容器下 `@+id/osd`（`include view_player_osd`）与 `@+id/control`（`include view_control_vod`）是两个兄弟层，OSD 后绘制所以盖在上层；避让值写死 `bottomMargin = controlsVisible ? dp(80) : ...`，那是按手机控制栏高度算的，电视控制栏（`app/src/leanback/res/layout/view_control_vod.xml`：vertical、paddingTop 24dp、paddingBottom 16dp、含 parse + action + seek 三行）高得多，80dp 正好落在按钮行。

**修正**：新增一个无参的控制栏内缩方法——从 `root.getParent()` 找兄弟节点 `R.id.control`；手机端控制栏根是 `match_parent`，真正占位的是内部 `@+id/bottom`，故优先取 `bottom` 高度，否则 `bar.getHeight()`；`height <= 0 || height >= root.getHeight()` 时回退 `dp(80)`；返回 `height + dp(8)`。`applyMediaBadgePosition()` 里的 `barTop` 直接调用这个无参版本，下两角 `bottomMargin = controlsVisible ? barTop : (stacked ? clear : 贴边常量);`，上两角仍 `dp(64)`。

同一提交还包含「总开关 / 常驻开关」拆分（原计划单开的 `osd-badges-split` 会话，实际并入本会话，见下一节）。

#### 验证

- 单测：`PlayerDisplaySettingSyncTest` leanback 12/12、mobile 12/12；`ControlDialogOsdSettingsTest` mobile 9/9；断言新增当时的 `bottomMargin = controlsVisible ? barTop : (stacked ? clear : 当时的贴边常量);` 与无参控制栏内缩方法签名，明确禁止写死高度。（**该断言已被第十一轮改写**，现行断言见下方「现行断言」。）
- **现行断言**（第十一轮后，`app/src/test/java/com/fongmi/android/tv/setting/PlayerDisplaySettingSyncTest.java`）：`bottomMargin = controlsVisible ? barTop : (stacked ? clear : edgeBottom);`、`private int controlBarTopInset(View bar, int edgeGap)`、`int barTop = controlBarTopInset(bar, edgeBottom);`、`private int badgeGap()` / `return dp(16);`、`Math.max(0, gap - (badgeParent == null ? 0 : badgeParent.getPaddingBottom()))`、`private void observeControlBar(View bar)` + `addOnLayoutChangeListener` + `root.post(this::applyMediaBadgePosition)`，并以 `!source.contains("edgeInset")` 作为禁止项。
- 构建：`:app:assembleLeanbackArm64_v8aDebug :app:assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL。
- 设备（emulator-5554，1920×1080，sdk28）：leanback 全屏播放 `probe.mp4`，`input tap` 唤醒控制栏后同一帧里 `control` bounds `[0,854][1920,1080]`（按钮行 `action` `[48,902][1872,972]`、进度条 `seek` `[48,996][1872,1048]`），`osdMediaBadges` bounds `[889,766][1888,810]` —— 位于控制栏顶边 854 之上，**不再压住按钮行**；控制栏自动隐藏后回到 `[889,1004][1888,1048]` 贴右下角。截图 `s3.png` 同帧可见。
- 详情页内嵌小窗（`inlineFullscreen=false`）与退出全屏后的详情页 dump 中均无 `osd` / `osdMediaBadges` 节点 → 非全屏不显示仍然成立。

### 第八轮再修订：拆开「总开关」与「常驻开关」（并入 task_guard `osd-badges-bar`，base HEAD `6feba34d3285c5e70ea2c9a77e809e1843f4eb76`）

用户反馈：「现在屏显设置关闭时无法设置媒体信息位置了，导致全屏唤醒控制栏时无法看到媒体信息」。

**根因**：第八轮修订把同一个 `player_osd_media_badges` 既当「媒体信息总开关」又当「屏显里的常驻项」。屏显一关，总开关被连带清掉，于是「媒体信息位置」那一行被迫显示「关闭」、无法选角位，全屏唤醒控制栏也就不再有徽标。

**修正**：拆成两个独立偏好。

| 偏好键 | 含义 | 唯一写入口 |
| --- | --- | --- |
| `player_osd_media_badges` | **总开关**：是否允许显示媒体信息（全屏 + 唤醒即显示） | 设置页「媒体信息位置」单选（选角位=开 / 选关闭=关） |
| `player_osd_media_badges_persist` | **常驻开关**：控制栏隐藏时是否继续留在画面上 | 屏显设置里的「媒体信息」项 / 播放控件里的「媒体信息」芯片 |

- `setMediaBadges` 门控：`player == null || !source.isFullscreen() || !isDisplayMediaBadges() || (!controlsVisible && !isDisplayMediaBadgesPersistent())`。
- `renderMediaBadgesOnly()`：仍只查 `!source.isFullscreen() || !isDisplayMediaBadges()`（该路径只在控制栏唤醒时进入）。
- `getDisplayChecked()/putDisplayChecked()` 第 8 位改为读写「总开关 AND 常驻开关」，与 DisplayDialog 芯片、isOsdEnabled 口径一致；勾上时额外 `putDisplayMediaBadges(true)`（勾选即视为要显示），取消勾选只取消常驻。
- `DisplayDialog` 的 `MEDIA_BADGES` 芯片同上。
- `isOsdEnabled()` 在原有条件之后**追加** `|| (isDisplayMediaBadges() && isDisplayMediaBadgesPersistent())`（追加，不是把原判断替换掉），避免总开关关闭时 OSD 循环空转；`PlayerSetting.java:1010-1013`。
- `Backup.APP_PREFS` 追加 `player_osd_media_badges_persist`。

**最终行为矩阵**：

| 位置=关闭 | 常驻开关 | 全屏唤醒控制栏 | 控制栏隐藏 |
| --- | --- | --- | --- |
| 是 | 任意 | 不显示 | 不显示 |
| 否 | 关 | **显示** | 不显示 |
| 否 | 开 | 显示 | **常驻显示** |

非全屏（详情页内嵌小窗）在任何组合下都不显示。

#### 验证

- 单测：新增 `mediaInfoMasterSwitchIsIndependentFromThePersistentToggle`（断言两个键/访问器、`getDisplayChecked` 第 8 位读常驻开关、勾选时联动打开总开关、`DisplayDialog` 写常驻开关、备份含新键）；`PlayerDisplaySettingSyncTest` mobile/leanback 各 12 例全绿；`ControlDialogOsdSettingsTest` 9/9。（该位在第十二轮改为「总开关 AND 常驻开关」，见 §第十二轮。）
- 构建：`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL。
- 设备（emulator-5554）：位置=左上角、常驻开关关闭、屏显其它项全关 → 全屏唤醒控制栏，`osdMediaBadges` 出现于左上角；再选「关闭」→ 同一操作不出现。
### 第十一轮：16dp 呼吸位统一 + 控制栏高度按布局定稿重测（task_guard `osd-badges-gap`，base HEAD `c5b0900c9396c89131f9d2b75083bf31b3d4a0f3`）

用户要求：「调成 + dp(16) ，手机和电视版都注意，做好事能和无网速屏显是媒体信息屏显距离底部的距离一致」——即徽标到正下方障碍物的**肉眼间距**在「控制栏可见」与「控制栏隐藏且同角无邻居」两种情况下必须完全相同，手机与电视共用同一个值。

**两个问题分开修**：

1. **内边距折算**。徽标挂在 `app/src/main/res/layout/view_player_osd.xml` 内层那个 `android:padding="14dp"` 的 FrameLayout 里，`bottomMargin` 是从 padding **内沿**开始量的。旧代码是栏高 `+ dp(8)`，实际肉眼缝隙 14+8=22dp；贴边那一路的旧常量 `dp(2)` 是 14+2=16dp，两者本来就不相等。新增 `private int badgeGap() { return dp(16); }`，并新增 `edgeTop/edgeBottom/edgeStart/edgeEnd`：`Math.max(0, gap - badgeParent.getPaddingXxx())`，把内边距折算掉；下限取 0 是因为负 margin 会顶进系统栏把胶囊裁切。
2. **控制栏高度测量时机**。+16dp 改完后设备实测仍然压栏：控制栏顶边 y=854（高 226px），而徽标底边 y=892。反推代码量到的高度是 160px——控制栏唤醒那一帧 `action` 行已布局、`seek` 行尚未布局，之后没有任何重算。于是把 `controlBarTopInset` 改成接收已解析的 `View bar`（现签名 `controlBarTopInset(View bar, int edgeGap)`），新增 `controlBar()` 查找与 `observeControlBar(View bar)`：登记一次 `addOnLayoutChangeListener`，控制栏每次布局定稿后 `root.post(this::applyMediaBadgePosition)` 重算，保证等到的永远是最终高度。

**行为**：控制栏可见时是「徽标底边 → 控制栏顶边」= 16dp；控制栏隐藏且同角无邻居时是「徽标底边 → 画面底边」= 16dp；同角有网速/标题/时间/进度时按邻居实测高度 + 4dp 抬起。

#### 验证

- 单测：`PlayerDisplaySettingSyncTest` leanback 12 例 / mobile 12 例（断言 `private int badgeGap()`、`return dp(16);`、`private int controlBarTopInset(View bar, int edgeGap)`、`observeControlBar` + `addOnLayoutChangeListener` + `root.post(this::applyMediaBadgePosition)`，且禁止任何 `edgeInset`）；`ControlDialogOsdSettingsTest` 9 例。
- 构建：`:app:assembleLeanbackArm64_v8aDebug` BUILD SUCCESSFUL，`adb install -r -t` 到 emulator-5554 Success。
- 设备（emulator-5554，1920×1080，sdk28，density 280，leanback flavor，全屏播放 `cold_m1.mp4`/29.8s）：
  - 控制栏可见：`control` bounds `[0,854][1920,1080]`、`action` `[48,902][1872,972]`、`seek` `[48,996][1872,1048]`，`osdMediaBadges` `[1323,778][1888,822]` → 徽标底边 822 与栏顶 854 相差 **32px = 16dp@2.0**，整行位于按钮行之上。
  - 控制栏隐藏且同角无邻居（屏显只开标题/媒体信息）：`osdMediaBadges` `[1323,1004][1888,1048]` → 底边 1048 与画面底 1080 相差 **32px = 16dp**，与上一场景严格相等。
  - 修复前对照：同一操作下 `osdMediaBadges` 底边 892 / `[1327,848][1892,892]`，与栏顶 854 重叠 38px（这就是「看起来被塞进控制栏」的直接原因）。
- 截图按 task guard scope 规则不进仓库，本轮取证文件为 `i1.png`（可见）与 `k1.png`（隐藏），暂存工作区根目录。

### 第八轮修订：关闭屏显后控制栏唤醒仍出现徽标（task_guard `osd-badges-gate`，base HEAD `e8ccde109e21b674fc82e6db8d44f586f39eda2f`）

用户反馈（截图：设置页「屏显设置」摘要只剩「时间 / 分辨率 / 进度 / 迷你 / 标题」、「媒体信息位置 = 关闭」，而全屏唤醒控制栏后右下角仍出现 `FHD / AVC / AAC 44.1KHZ / 2.0 / TS`）：「媒体信息关闭了，屏显也没开启唤醒控制栏怎么还有？」

**根因**：第八轮把门控写成 `!controlsVisible && !PlayerSetting.isDisplayMediaBadges()`——控制栏唤醒时 `controlsVisible=true`，第二项恒为 false，于是开关被完全绕过，只要全屏就显示。`renderMediaBadgesOnly()` 当时也只查全屏。这是把「全屏内唤醒总是显示」当成需求落实的结果，与用户真实意图相反。

**修正**：两处都改成「全屏 + 开关」双条件：
- `setMediaBadges`：`if (player == null || !source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges())`；
- `renderMediaBadgesOnly`：`if (!source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges())`。

语义最终形态（与设置页一致）：**「媒体信息位置 = 关闭」或屏显开关关闭 ⇒ 任何情况下都不显示，包括全屏唤醒控制栏**；打开后全屏唤醒显示、控制栏隐藏时按同一开关常驻。

#### 验证

- 单测：`PlayerDisplaySettingSyncTest` leanback 11/11、mobile 11/11；`ControlDialogOsdSettingsTest` mobile 9/9；断言改为「开关必须在每条路径上生效，且不再存在 `!controlsVisible && !isDisplayMediaBadges()` 这种绕过写法」。
- 构建：`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL。
- 设备（emulator-5554）：`player_osd_media_badges=false` 时进全屏并唤醒控制栏，dump 中无 `osdMediaBadges`；把开关打开后同样操作才出现。
### 第七轮：关闭项 + 开关成为唯一总闸（task_guard `osd-badges-off`，base HEAD `3220d20d6c3d4a9f90b192340dae06d10e512c58`）

用户原话（两段需求）：「新增一个关闭选项，默认关闭不显示包括控制栏唤醒后，关闭状态时用户开启媒体信息屏显时自动改成右下角（也就是现在的默认值），如果用户有历史设置则设置为最近一次的设置，比如用户之前设置在左上角后关闭屏显再开启屏显还是自动左上角」；「非全屏状态下唤醒控制栏不再显示媒体信息……除非是用户自己开启了屏显，没开启的时候非全屏唤醒控制栏不显示」。

#### 改动

1. `PlayerSetting.java`：新增 `MEDIA_BADGE_POSITION_OFF = 4`、`getMediaBadgeChoice()`（返回 `isDisplayMediaBadges() ? getMediaBadgePosition() : MEDIA_BADGE_POSITION_OFF`）、`putMediaBadgeChoice(int which)`（`which == OFF` 只 `putDisplayMediaBadges(false)` 并 return，**角位键原样保留**；否则 `putMediaBadgePosition(which)` + `putDisplayMediaBadges(true)`）。「历史角位」因此天然成立：关闭只动开关不动位置。
2. `PlayerOsdController.java`：`setMediaBadges(player)` 的门控由「控制栏可见就显示」改为只看 `PlayerSetting.isDisplayMediaBadges()`；`renderMediaBadgesOnly()`（mobile 隐藏 OSD 根后的独立放行路径）开头先查同一开关，关闭时把四角 / 诊断 / 迷你进度 / 徽标全部 `GONE`、清空 `mediaBadgeSignature`、`root` 置 `GONE` 并返回 false，防止这条旁路把「控制栏唤醒」当成显示许可。
3. 三语 `select_media_badge_position` 数组各追加第 5 项：`Off` / `关闭` / `關閉`（默认选中项由 `getMediaBadgeChoice()` 决定，从未开过就落在「关闭」）。
4. 两 flavor 设置页（`SettingPlayerActivity.java` leanback / `SettingPlayerFragment.java` mobile）的 `onBadgePosition` / `updateBadgePositionText` 改用新入口；摘要文本用 `badgePosition.length > which` 兜底，越界时显示 `setting_off`。
5. `PlayerDisplaySettingSyncTest.java`：旧用例 `mediaBadgesFollowTheMediaInfoSwitchOnlyWhenTheControlBarIsHidden` 改写为 `mediaBadgesAreGatedByTheMediaInfoSwitchOnEveryPath`（断言 `setMediaBadges` 体内含 `!PlayerSetting.isDisplayMediaBadges()`、不再含旧组合；`renderMediaBadgesOnly` 含开关判断；保留常驻分支与 inset 断言），新增 `mediaBadgePositionChoiceKeepsTheLastCornerAcrossOff`（断言 `OFF = 4`、`getMediaBadgeChoice()` 两种返回、默认 3、`putMediaBadgeChoice` 分支、两 flavor 设置页改用新入口、三语数组恰好 5 项）。

#### 验证（emulator-5554，1920×1080，sdk28，zh-CN，mobileArm64-v8a debug）

- 单测：`PlayerDisplaySettingSyncTest` leanback 10 例 0 失败、mobile 10 例 0 失败，`ControlDialogOsdSettingsTest` mobile 9 例 0 失败；整任务里另有 8 例既存环境失败（MpvFontConfigTest / MpvHlsCacheCoordinatorTest / NativeEnhancedPlaybackStyleFocusTest / ReaderPlaybackRoutingSourceTest / SearchResultDownFocusTest / TmdbSourceOnlyInteractionTest / WebThemeTokenSourceTest），它们读取的文件都不在本轮 diff 内。
- 构建安装：`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL，`app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`（209,274,248 字节）`adb install -r -t` 成功。
- 实测截图（`off-live.png` 关闭态控制栏唤醒无徽标、`on-badges.png` 开启后回到左上角、`off-dialog.png` 五项对话框、`on-panel.png` 播放控件芯片、`state.png`）按 task guard 的 scope 约束**不进入仓库**，本轮暂存于 `.design-evidence` 之外的同机目录 `%TEMP%\osd-badges-off-evidence\`；仓库内只保留文字证据。
- **关闭（默认）时控制栏唤醒不显示**：`off-live.png` 中控制栏已唤醒（短显 / 搜索 / 投屏 / 设置 与 线路 / 选集 全部在屏），视频小窗左上角**没有任何徽标**；同帧 `uiautomator` dump 中 `webhtv:id/osd*` 节点数 = **0**。
- **开关打开后按历史角位显示**：设置页把「媒体信息位置」先选成**左上角**（prefs `player_osd_media_badges=true` + `player_osd_media_badge_position=0`），再选**关闭**（prefs 变 `false`，但 `position` 仍为 `0`）；随后在播放控件里把「媒体信息」芯片拨到开，徽标出现在**视频小窗左上角**（`on-badges.png`：HD / AVC / AAC 44.1KHZ 64KBPS / 2.0 顶到小窗顶边），即「关闭再开启仍回到最近一次设置」。
- **关闭立即生效**：同一会话里再把芯片拨回关（prefs `false`），徽标立刻从画面消失，无需退出播放。
- **默认值即右下角**：全新安装时 `player_osd_media_badge_position` 默认 3，因此从未设过角位就直接打开开关会落在右下角；`off-dialog.png` 里「关闭」为选中态，说明默认选择就是关闭。
- 设置页「媒体信息位置」对话框实测 5 项：左上角 / 右上角 / 左下角 / 右下角 / **关闭**（`off-dialog.png`，`关闭` 高亮为当前值，与历史截图里 4 项旧版对比）。

### 第五轮：常驻显示 + 控制栏唤醒无条件显示（task_guard `osd-badges-persist`，base HEAD `39e1b4a0d4`）

用户原话：「媒体信息在屏显设置中开启时非控制栏唤醒的时候也显示和其他屏显一样，但是默认关闭，控制栏唤醒的时候不关是否开启了这个屏显都显示」。据此语义拆成两条：开关打开后控制栏隐藏时常驻显示；控制栏唤醒时无条件显示。

- 设备实测（emulator-5554，同一部样片，prefs 直接改写 `player_osd_media_badges` 与 `player_osd_title` 后 `run-as ... cp` 回写）：
  - 开关**打开**、控制栏**隐藏**（全屏常驻路径）：`@id/osd` 与 `@id/osdMediaBadges` 同时在树中，`@id/osdMediaBadges` bounds `[163,444][939,524]`，子节点文本 `44.1KHZ 64KBPS` / `29.94FPS` / `3.1MBPS` / `MP4`（另有 5 枚纯牌位图节点无文本）→ 徽标与标题同属常驻屏显。截图目视 9 枚横排徽标（HD / Dolby VISION·HDR10 / WebDL / AVC / AAC 44.1KHZ 64KBPS / 2.0 / 29.94FPS / 3.1MBPS / MP4）。
  - 开关**关闭**、控制栏**隐藏**：`id/osd` 0 处、`osdMediaBadges` 0 处 → 与其它屏显项一致全部不显示（默认行为不变）。
  - 开关**关闭**、控制栏**唤醒**：`id/osd` 1 处、`osdMediaBadges` 1 处，bounds `[101,183][703,299]`，文本 `44.1KHZ 64KBPS` / `29.94FPS` / `3.1MBPS` → **无需打开开关即显示**，两条需求同时成立。
- 注意 `input tap 960 540` 在 TMDB 详情小窗上会落到右侧信息区的按钮（曾误入「搜索」底部弹窗），唤醒控制栏应点小窗内的视频区域（本次用 `input tap 500 330`）。

### 第六轮：扩充包规则 + 牌图扩到 127 张 + 同行等高（task_guard `osd-badge-expansion`，base HEAD `192b55c2fc`）

用户原话：「扩展包可以加，还要这些徽标高度需要统一一下，建议采用大的」。据此做两件事：并入扩充包的媒体事实规则、把同行徽标统一到原「有牌」那档高度。

- **规则扩充**：`MediaBadgeCatalog` 由 100 条增至 **127 条**，新增 15 组中的 `VIDEO_TECH` 与 `EDITION` 两组；新增条目：`gr_8k`、`ge_vvc`、`ga_lpcm`、`ga_lossless`、`gv_8bit`、`gv_12bit`、`gvf_hdrvivid`、`gvf_3d`、`gfr_hfr`、`gms_iso`、`gms_high_bitrate`、`gc_7_1_4`、`gc_5_1_4`、`gc_5_1_2`、13 张版别牌（`ged_*`）。明确**不并入**扩充包里 506 条番号 / 431 条番号代号 / 400 条发布组 / 各类厂牌与演员类规则（本项目片源给不出这类证据），也不并入字幕语言与配音语言规则（语义另议）。
- **牌图**：`drawable-nodpi/badge_*.webp` 由 100 张增至 **127 张**（实测 661706 字节，约 646 KB）。版别牌没有可用图形资产（源 SVG 只是单个 `<text>` 元素），改用 `C:\Windows\Fonts\msyhbd.ttc` 72px 白字透明底自绘，命名统一按 `Group` 前缀（`ged_` = EDITION、`gvf_` = VIDEO_TECH、`gv_` = DYNAMIC_RANGE）。
- **高度统一**：`createMediaBadge` 的胶囊 `FlexboxLayout.LayoutParams` 由 `WRAP_CONTENT` 高改为固定 `dp(20)`。
- 单测：新增 `expansionRuntimeFactsAreRecognised`、`expansionFileNameFactsAreRecognised`、`expansionAudioFactsComeFromTheFileName` 三例；`catalogIsConsistent` 的总数断言 100 → 127。首跑 1 例失败（`HDR.Vivid.3D` 文件名里 VIDEO_TECH 只出一枚，断言 `gvf_3d` 存在不成立），改为断言 `gvf_3d` 不存在并另用 `3D` 文件名单独验证后全绿。
- 设备实测（emulator-5554）：样片改名为 `Movie.2024.港版.HDR.Vivid.3D.8K.LPCM.7.1.4.WEB-DL.mkv`，唤醒控制栏后 `@id/osdMediaBadges` 下 **11 个胶囊 / 15 张牌位图**，实测 9 个胶囊高度**全部 35px**（改动前纯文本胶囊 30px、有牌 34px）；截图中第一行 `HD` / `HDR` / `HDR Vivid` / `WebDL` / `港版`，第二行 `AVC` / `AAC 44.1KHZ 64KBPS` / `2.0` / `29.94FPS` / `3.1MBPS` / `MP4`。新增的 `港版`（紫色 EDITION）与 `HDR Vivid`（青色 VIDEO_TECH）均正确渲染。`8K` / `LPCM` / `7.1.4` / `3D` 未出现属预期：运行时轨道参数（656×1280 → 720p、AVC、AAC 2.0、29.94FPS、3.1MBPS）优先于文件名。
- 构建：`MediaBadgeResolverTest` 27 例 + `PlayerDisplaySettingSyncTest` + `ControlDialogOsdSettingsTest` 全绿，`compileLeanbackArm64_v8aDebugJavaWithJavac` 通过，`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL（APK 204,566,448 字节）。

### 第三轮：现成徽标牌 + 横排炫彩（task_guard `osd-badge-plates`，base HEAD `7d4bee6ce1`）

用户要求：徽标改「同一行横排 + 炫彩配色 + 用主流现成图标」（并明确授权忽略许可证问题）。

- 布局：`osdMediaBadges` 从竖排一列改回 `row` + `wrap` + `justifyContent=flex_end`。
- 视觉：新增类别色渐变胶囊 + 同类色描边。
- 牌图：当时只打包 18 张。
- 设备实测：`@id/osdMediaBadges` bounds `[119,383][939,423]`（宽 820 高 40，一行），子节点 `656X1280` / AVC 牌 / `29.94FPS` / `3.1MBPS` / `AAC 2.0 44.1KHZ 64KBPS` / `MP4`。

### 第四轮：全量入库 + 消除语义不符图标（task_guard `osd-badge-plates-all`，base HEAD `0dc165a9a2`）

用户原话：「为什么不都入库补都实现，还要你现在的图标和内容都不搭边吧」。据此做三件事：

1. **牌图全量入库**：参考 JSON 100 条 filter 的 100 张 `imageURL` 全部下载、PIL 裁 bbox、统一高 72px，落 `drawable-nodpi/badge_<group>_<slug>.webp`（0 缺失 / 0 重复 / 约 497 KB）；旧 18 张扬弃牌删除。
2. **规则全量实现**：`MediaBadgeCatalog` 内嵌 100 条原始 `pattern`（实测 Java `Pattern.compile` 100/100 通过），`MediaBadgeResolver` 按「运行时事实优先、文件名兜底」合并；非运行时组每组最多一枚。
3. **消除语义不符图标**：删除当时存在的 `mediaBadgeIcon(Icon)`（现已不存在）与 7 个 `ic_badge_*.xml`，只保留牌 + 文本两种形态；`Icon` 枚举整体替换为 `MediaBadgeCatalog.Group`。

验证：
- `MediaBadgeResolverTest` 20 用例全绿（含 `catalogIsConsistent`、`runtimeFactsWinOverTheFileName`、`streamingPlatformIsRecognisedFromTheReleaseName`、`portraitVideoIsGradedByItsLongSide`）。
- 一次真实崩溃被修：`PLATES = plates()` 里误写成 `PLATES.put(...)`（静态初始化期间 `PLATES` 还是 null）→ `ExceptionInInitializerError`，`PlayerOsdController.<clinit>` 抛 NPE，App 一进 TMDB 详情就崩。改为 `map.put(...)` 后正常。**静态字段初始化方法里绝不能给该字段自身赋值。**
- 设备实测（emulator-5554，播放文件名 `Movie.2024.1080p.WEB-DL.DV.HDR10.DDP5.1.Atmos.HEVC-FraMeSToR.mp4`，小窗内联控件）：`@id/osdMediaBadges` bounds `[163,343][939,423]`，子节点依次 `HD` / `Dolby VISION·HDR10` / `WebDL` / `AVC` / `AAC 44.1KHZ 64KBPS` / `2.0` / `29.94FPS` / `3.1MBPS` / `MP4` —— 9 枚，全部是**参考库的真实牌图**，文件名类（HD / WebDL）与运行时类（AVC / AAC / 2.0 / 帧率 / 码率 / 容器）同时在列；`HD` 对应 `gr_720p`（样片实际 656×1280，按长边判 720p 档）。
- 构建：`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL，`adb install -r -t` 成功。

- 既有回归守卫（必须保持通过）：`PlayerDisplaySettingSyncTest`（数组顺序、设置页偏好、四角白色文本、persistentSuppressed 顺序、Backup 键）、`ControlDialogOsdSettingsTest`（两 flavor 芯片 id 与 `getDisplayChecked()`/`putDisplayChecked(checked)`、live 5 项、`onDisplayChanged()` 重启）。

### 第十二轮：C111 合并 beta 后的评审修正（task_guard `C111-beta-merge-review-dev3-20261029`，base HEAD `382df25e267375d7f3c6e7792430d3d25233d9d7`）

本轮没有新需求，做的是两件事：把 `origin/beta` 合并进来（C111），再对合并结果做 Standards / Spec 双评审并按结论修正。

#### 合并

- 合并来源 `origin/beta` = `aab55dfcca94904a4ca6b7775b4f57bda343e35c`，合并基 `3220d20d6c3d4a9f90b192340dae06d10e512c58`，**0 冲突**。
- 评审两条口径各自出结论：Standards（是否守住本仓库的路径边界、提交纪律、单测与文档一致性）与 Spec（是否真的实现「只在全屏显示 + 位置与常驻解耦 + 不压控制栏」这套需求）。下面四条修正都是这两份评审点出来的。

#### 修正

1. **存量用户升级迁移**。拆分前只有一个 `player_osd_media_badges` 键，拆分后它只表示「总开关」，`player_osd_media_badges_persist` 才是「常驻」。旧用户升级后常驻语义会静默降级成「仅控制栏唤醒时显示」。故 `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java` 新增 `migrateMediaBadgePersist()`：`player_osd_media_badges_persist` **缺失时**把旧键 `player_osd_media_badges` 的值抄**一次**，配合 `catvod/src/main/java/com/github/catvod/utils/Prefers.java` 新增的 `contains(String)` 判断键是否存在；键已存在就直接返回、不再迁移，保证「位置与常驻解耦」不会被旧值反向覆盖。`isDisplayMediaBadgesPersistent()` 先走迁移再读值。
2. **控制栏布局监听泄漏**。`observeControlBar(View bar)` 每找到一根控制栏就 `addOnLayoutChangeListener`，但 `release()` 从未注销：播放器重建（进出详情页、横竖屏切换）会累积回调，回调还持有已销毁的 root。新增字段 `private View.OnLayoutChangeListener observedControlBarListener;` 与 `stopObservingControlBar()`，在 `release()` 里调用它反注册并置空。
3. **`controlBar()` 兜底 + 量高度三级回退**。详情页内嵌播放器按端有**两套**控制栏容器：`app/src/main/res/layout/activity_tmdb_detail.xml:1292` 的 `@+id/playerControls`（电视端，内含 `:1340 @+id/playerBottom`）与同文件 `:1546` 的 `@+id/detailControlHost`（手机端，运行时装入 `app/src/main/res/layout/view_control_vod_tmdb.xml`，其 `:223` 有 `@+id/bottom`）；两者互斥，`TmdbDetailActivity.java:1346`（电视端）把 `detailControlHost` 设 GONE、`:1350`（手机端）把 `playerControls` 设 GONE。所以 `controlBar()` 依次尝试 `R.id.control` → `R.id.detailControlHost` → `R.id.playerControls`，并**优先返回可见的那一个**，避免拿到 GONE 容器而回退写死值。`controlBarTopInset(View bar, int edgeGap)` 的高度测量同样三级回退：`R.id.bottom`（手机主播放器 / 手机内联）→ `R.id.playerBottom`（电视详情页）→ `bar.getHeight()`；仍异常（`<= 0` 或 `>= root.getHeight()`）才回退 `dp(80) + edgeGap`。这样 `inlineFullscreen=true` 时也能按实测高度避让，不再恒回退写死值。
4. **`applyMediaBadgePosition()` 判空**。首行补 `if (mediaBadges == null) return;`——OSD 布局尚未 include 或被替换时 `findViewById(R.id.osdMediaBadges)` 为 null，原实现会在第一处 `getLayoutParams()` 上 NPE。
5. **屏显第 8 位补回「与总开关的与关系」**。合并后 `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java:643` 的 `getDisplayChecked()` 第 8 项只剩 `isDisplayMediaBadgesPersistent()`，丢了与 `isDisplayMediaBadges()` 的与关系；`origin/beta` 该位原本是单一开关 `isDisplayMediaBadges()`，拆两键时被整体替换，属拆分引入的回归。复现：`PlayerSetting.java:633-640` 的 `putMediaBadgeChoice(MEDIA_BADGE_POSITION_OFF)` 只调 `putDisplayMediaBadges(false)` 就 `return`，**不动** persist 键 ⇒ 总开关已关、persist 仍 true ⇒ 第 8 位仍读到 true；于是两 flavor 的 `ControlDialog`（`app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ControlDialog.java:272-275`、`app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ControlDialog.java:376-379`）照 `displays.get(i).setSelected(i < checked.length && checked[i])` 把芯片显示成「已勾选」，屏显摘要 `getOsdText`（`app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java:258-267`、`app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingPlayerFragment.java:273-282`）仍输出「媒体信息」，而徽标实际全程不显示（`app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` 的总开关门控）⇒ UI 与真实显示状态 desync，与本文件 :211-217 的行为矩阵矛盾。修法：第 8 项改为 `isDisplayMediaBadges() && isDisplayMediaBadgesPersistent()`，与同语义另两处齐平 —— `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java:1010-1013` 的 `isOsdEnabled()`（AND 追加在 `:1012`）与 `app/src/main/java/com/fongmi/android/tv/ui/dialog/DisplayDialog.java:77`。写侧 `putDisplayChecked()`（`PlayerSetting.java:654-657`）勾上时 `putDisplayMediaBadges(true)` + 写常驻、取消时只关常驻，故四种 (总开关, 常驻) 组合点击均能正确翻转，不存在「点一下没反应」或「关不掉」死角。配套把单测 `app/src/test/java/com/fongmi/android/tv/setting/PlayerDisplaySettingSyncTest.java:256-263` 从「只 contains 常驻开关」收紧为「总开关在前 + 常驻开关在后 + 必须含 `isDisplayMediaBadges() && isDisplayMediaBadgesPersistent()`」，并做了变异验证（去掉 AND / 调换顺序 / 把 `&&` 换成 `||` 三种变异都会让该用例变红）。
6. **直播端媒体徽标补全屏门控**。`app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:295-310` 的 OSD `Source` 原来只覆写 `getPlayer()` / `getTitle()`（`:296-304`），`isFullscreen()` 继承 `PlayerOsdController.Source` 的默认 `true`，于是手机竖屏内嵌直播窗格（`isEmbeddedLiveUi()` 为真，见 `:1020-1022` 的 `return !ResUtil.isLand(this) && !isRotate() && !isInPictureInPictureMode();`）也会显示媒体徽标，违反「只在全屏显示」。现补 `public boolean isFullscreen() { return !isEmbeddedLiveUi() && !isInPictureInPictureMode(); }`（`:306-310`），`PlayerOsdController.java:309` 与 `:469-470` 两处门控即对直播端生效。电视端不覆写：其 `app/src/leanback/res/layout/activity_live.xml:10-12` 的 `@+id/video` 是 `match_parent`，直播恒全屏，没有内嵌窗格。手机直播进画中画时 `isInPictureInPictureMode()` 为真，故 PiP 小窗也判为非全屏、不出媒体徽标；这与点播端 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:5723-5725` 的 `canShowPiP` 只在非全屏时允许进 PiP 的口径一致。新增单测 `mobileLiveOsdSourceGatesMediaBadgesOnTheEmbeddedShelf`（`PlayerDisplaySettingSyncTest.java:351-372`）锁住这五点（`:360` 覆写存在、`:361-362` 整条返回值精确匹配 `return !isEmbeddedLiveUi() && !isInPictureInPictureMode();`、`:363-366` 匿名类体内含 `!isInPictureInPictureMode()`、`:367-368` 覆写落在 `new PlayerOsdController.Source()` 之后、`:371` 电视端不得出现该覆写）。

#### 验证

- 本轮验证（本地执行；本轮改动尚未提交，未走 CI，也未做设备实测）：双口味编译通过 —— `./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` 得到 `BUILD SUCCESSFUL`；定向单测通过 —— `PlayerDisplaySettingSyncTest` mobile / leanback 各 14 例、0 失败，`ControlDialogOsdSettingsTest` 9 例、0 失败。
- 单测 `PlayerDisplaySettingSyncTest` 的自验还包含**变异验证**（分别去掉 AND、调换顺序、把 `&&` 换成 `||`，三种变异都会让 `mediaInfoMasterSwitchIsIndependentFromThePersistentToggle` 变红；该次变异验证在新增直播用例之前执行，当时双口味各 13 例 0 失败，加用例后为各 14 例）。

#### 未改动（遗留）

- **上两角仍是固定 `dp(64)`**：控制栏可见时上两角的 `topMargin` 没走实测栏高，属已知未实测项。手机端暂未观察到压角，但电视端控制栏更高，若后续复现需按第十一轮同一套 `controlBarTopInset` 收口。
- **单测仍是字符串断言式**（读源码文本判断实现），未迁到 Robolectric 之类的行为断言。修泄漏、补判空这类「存在性」改动能覆盖，真正的布局数值仍靠设备实测。
- **直播端全屏门控只做了源码与单测验证**：手机竖屏内嵌窗格（`!ResUtil.isLand(this) && !isRotate() && !isInPictureInPictureMode()`）不出徽标这条口径没有设备实测。