# SUB-AUTOLOAD 自动匹配字幕必须真正成为选中轨道

## Recovery anchor

- 目标：开启「自动匹配字幕（优先简体中文）」后，匹配成功的字幕要真正被选中并渲染，不能只在列表里出现等用户手点；未生效时提示必须如实。
- 验收：
  1. 自动匹配命中后，字幕直接显示，无需进字幕列表手动选择。
  2. 同一场景下若确实没选上，提示不得再报「已自动匹配字幕」。
  3. 用户设置的优先语言（简体中文）参与轨道选择，不再只影响候选排序。
- 当前状态：原功能及 C122 生命周期、字幕身份、语言兼容补修已交付；修复提交 `88ea7f4242219abd7c647a69c525f10443651d8d` 与恢复标签已推送，[PR #540](https://github.com/Silent1566/webhtv/pull/540) 到 beta。双变体定向验证和审查已闭合，设备行为未做完整冒烟。
- 下一步：等待 PR 审核；设备冒烟按独立验收需求进行。交付坐标与验证见 `docs/C122-beta-merge-review-dev4-20261108.md`，不要重新实施已完成逻辑。

## 1. 用户反馈与复现路径

> 开启自动匹配字幕，优先简体中文。在观看视频的时候提示已经匹配到字幕。但是并不会自动加载已经匹配到的字幕，还需要到字幕列表里手动选择。

现象成立。根因不是「匹配失败」，而是**「应用」这一步没有任何校验**。

## 2. 现状核查（皆为已读代码事实）

### 2.1 自动路径与手动路径不是同一条链路

| 环节 | 自动路径 | 手动路径 |
| --- | --- | --- |
| 入口 | `SubtitlePlaybackSession.onSubtitleResult` | `TrackDialog.onItemClick` |
| 落地 | `PlayerManager.setSub(Sub)`（`PlayerManager.java:1278`） | `player.setTrack(...)`（`TrackDialog.java:816`） |
| 选轨方式 | `PlaySpec.setSub()` 打 `SELECTION_FLAG_DEFAULT`，其余降为 `AUTOSELECT`（`PlaySpec.java:183-199`），**靠播放器自动选轨** | 显式 override：Exo `TrackUtil.setTrackSelection`（`TrackUtil.java:154-182`）；MPV `player.setTrackSelection(type, id)`（`MpvPlayerEngine.java:223-242`） |
| 副作用 | 重启当前 item（`PlayerManager.java:1292` → `:6231`） | 不重启 |

所以「手动能选上」证明轨道存在且可选；问题定位在「重启 + DEFAULT flag 自动选轨」这一路。

### 2.2 提示恒真

```java
// SubtitlePlaybackSession.java:320-331
public boolean apply(Host host, Sub sub) {
    if (host == null || sub == null) return false;
    PlayerManager player = host.getSubtitlePlayer();
    if (player == null || player.isReleased()) return false;
    player.setSub(sub);   // void 方法，未校验任何选中状态
    return true;
}
```

`setSub` 是 `void`，且开头 `if (sub == null || spec == null) return;` 会静默返回（`PlayerManager.java:1279`）。因此 `applied=true` 只说明「调用过」，随后 `:340-343` 打印 `subtitle_auto_match_hit`（`已自动匹配字幕：%1$s`）。这是提示说谎的直接原因。

### 2.3 优先语言只作用于候选排序，不作用于选轨

- 用户设置：`Setting.getSubtitlePreferredLanguage()`（`Setting.java:1471`）→ `SubtitleRequestFactory.java:33` → `SubtitleContext.preferredLanguage` → `SubtitleRanker.languageScore`（`:73-97`）。
- 播放器选轨：`ExoUtil.buildTrackSelector` 用 `builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages())`（`ExoUtil.java:474`），而 `LangUtil.getPreferredTextLanguages()` **只读 `Locale.getDefault()`**（`LangUtil.java:19-25`）。

即：用户选「简体中文」这件事，在真正决定选哪条字幕轨的那一步完全没参与。

### 2.4 provider 给出的 lang 常常不是合法语言标签

- `XunleiSubtitleProvider.normalizeLanguage`（`:213-218`）：`"默认"` / `"default"` → `"zh"`，其余中文词（`中文`/`中字`/`简`/`繁`…）**原样返回**。
- `ShooterSubtitleProvider.normalizeLanguage`（`:174-177`）：非 eng 一律 `"zh"`。
- `AssrtSubtitleProvider`：`lang` 直接取接口 `lang`/`desc`/`m_lang` 字段。

`"简体中文"` 这类值会被 Media3 当作无效标签，自动选轨时等于「语言未知」。这也是候选 1 的机制。

## 3. 决策

三处一起改，因为只改任一处都留残余：

| 编号 | 改动 | 解决 |
| --- | --- | --- |
| F1 | 自动匹配应用后**复核真实选中状态**，未选中则返回 false | 提示不再说谎 |
| F2 | 自动路径改为**显式选轨**，与手动路径同链路 | 字幕真正被选中 |
| F3 | 选轨首选语言接入用户设置；`lang` 规范化为 BCP-47 | 简体中文优先级真正生效 |

### 3.1 F1 的实现取舍

复核点必须在新轨道已可用之后。`setSub` 内部会重启，重启是异步的，`setSub` 返回时 `getCurrentTracks()` 仍是旧轨道。因此不能在 `apply()` 里同步复核。

方案：`PlayerManager` 增加一次性「自动匹配待校验」登记，在 `onTracksChanged`（`PlayerManager.java:8732`）复核并把结果回调给会话。这是既有的轨道变更收口，且 `initTrack` 恢复逻辑也在这里，时序正确。

未通过时：提示走 `subtitle_auto_match_hit_not_applied`（资源已存在，`values-zh-rCN/strings.xml:1525`），不新增文案。

### 3.2 F2 的实现取舍

不复用 `TrackDialog.onItemClick`（那是 UI 层）。改为在 `PlayerManager` 提供一个按 `Sub` 显式选轨的方法：重启后在新轨道里按 `format.label == sub.getName()` 兼 mime/language 定位 `Track`，走已有的 `TrackUtil.findOverride` / `MpvPlayerEngine.setTrack` 同一套 override 通道。

注意约束：Exo 的 override 必须在轨道列表就绪后设置，所以 F1 与 F2 共用同一个 `onTracksChanged` 时机，一次处理、一次复核。

### 3.3 F3 的实现取舍

`LangUtil` 增加一个「优先语言可注入」的重载，默认仍走 `Locale.getDefault()`（保持既有行为，不改变未开启自动匹配的用户），`ExoUtil` 调用时传入 `Setting.getSubtitlePreferredLanguage()`。

`lang` 规范化放在 `SubtitleRanker` 已有的 `normalizeLanguageCode`（`:103-108`）同源逻辑上抽成公共方法，由 `SubtitleInjector.toSub` 写入 `Sub.lang` 前规范化，保证 `SubtitleConfiguration.setLanguage()` 拿到合法标签。

## 4. 风险与回滚

| 风险 | 缓解 |
| --- | --- |
| `onTracksChanged` 是高频回调，登记后重复执行 | 登记一次性，取出即清，与 `pendingRestoreSub`（`PlayerManager.java:1302`）同一写法 |
| Exo override 误选其它字幕 | 定位条件与既有 `findSubtitleSub`（`:1119-1131`）一致，匹配不到则不做 override，退回 flag 自动选轨 |
| `LangUtil` 行为变化影响未开启自动匹配的用户 | 默认重载保持 `Locale` 原逻辑，新行为只在显式传参时生效 |
| MPV 路径 | MPV 已有 `setInitialSubtitleTrackId` / `setTrackSelection` 显式通道（`MpvPlayerEngine.java:223-242`），F2 复用，不需要新机制 |

回滚：还原本任务单一提交。

## 5. 验证

1. 单测：`SubtitlePlaybackSession` 真实生效语义；`LangUtil` 优先语言注入；`SubtitleRanker`/`SubtitleInjector` 的 lang 规范化。
2. 反向插桩确认每个新测试能变红。
3. `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` + `:app:testLeanbackArm64_v8aDebugUnitTest`（限定新增/相关类）。
4. 设备冒烟（有设备时）：开自动匹配 + 简体中文，命中后字幕直接显示；提示与实际一致。

## 6. 实施记录

### 6.1 相对设计的偏差

1. **语言规范化没放在 `SubtitleInjector`。** 设计原本想在那里收口（所有外挂字幕路径都过它），但该文件不在本任务声明的 scope 内，且扩大 scope 需要重开守卫会话。改为在 `SubtitleRanker` 上提供静态 `normalizeLanguageTag`，由 `SubtitlePlaybackSession.withNormalizedLanguage` 在注入前转换。行为等价：自动匹配这条路径同样在构造 `Sub` 之前完成规范化。
2. **复核改为两阶段，不是一次性。** 显式选轨在 Media3 是异步生效的，下发后立刻读 `getCurrentTracks()` 拿到的还是旧状态。所以 `resolvePendingSubtitleApply` 首次只下发改写，把结果交给下一次 `onTracksChanged` 复核。若期间没有新的轨道回调，`TIMEOUT_PLAY`（15s）后按实际选中状态兜底判定，不假设失败。
3. **复核逻辑移出 `initTrack` 守卫。** 显式选轨本身会再触发一次 `onTracksChanged`，而那时 `initTrack` 已置位。留在守卫内会让「已下发」永远等不到复核。
4. **刻意不写 Track 表。** 外挂字幕的轨道 id 是运行时 `sub-add` 产生的，持久化后下次起播会指向不存在的轨道——`SUB-EXT-HISTORY` 已论证过这一点。跨集字幕记忆由宿主的 `SubtitleSource` 负责，本任务不碰。

### 6.2 改动清单

| 文件 | 改动 |
| --- | --- |
| `SubtitlePlaybackSession.java` | `ResultApplier.applyExpected`（默认退化到 `apply`）；自动路径改走它并据回执提示；`withNormalizedLanguage` 在注入前规范化语言 |
| `PlayerManager.java` | `setSub(Sub, boolean expectSelected)` + `setSubtitleApplyListener`；`resolvePendingSubtitleApply` 在轨道就绪后显式选轨并两阶段复核；超时按实际状态兜底 |
| `ExoUtil.java` | 选轨首选语言改为读 `Setting.getSubtitlePreferredLanguage()` |
| `LangUtil.java` | 新增 `getPreferredTextLanguages(String)` 重载；旧重载委托它传 `null`，未设置偏好时行为不变；`any`/`all` 视为未设置 |
| `SubtitleRanker.java` | 新增 `normalizeLanguageTag`（「简体中文」→ `zh-Hans` 等） |

### 6.3 验证记录

- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：通过。
- 新增测试：`LangUtilTest` 5 个、`SubtitleRankerTest` 新增 2 个、`SubtitlePlaybackSessionTest` 新增 3 个。
- 反向插桩（逐个确认能变红，随后全部还原）：
  - 把 `normalizeLanguageTag` 的 `zh-Hans` 改成 `zh` + 把 `LangUtil` 的新分支短路为 `if (false)` → 4 个测试失败。
  - 把 `withNormalizedLanguage` 的调用换回原始 `asset.getLanguage()` → `normalizesProviderLanguageBeforeInjection` 失败。
  - 把自动路径换回旧的 `notify(..., resultApplier.apply(...))` 语义 → `reportsNotAppliedWhenSelectionIsNotConfirmed` 失败。
- `:app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.subtitle.*" --tests "com.fongmi.android.tv.player.track.*"`：通过。
- 未执行：真机冒烟（需要设备）——开自动匹配 + 简体中文，确认命中后字幕直接显示、提示与实际一致。这是唯一未验证的环节。

### 6.4 已知残留

- `PlayerManager` 的两阶段复核依赖播放器在选轨后再次回调 `onTracksChanged`。MPV 与 Exo 都会被 `setTrack` 触发轨道刷新；若某内核不回调，15s 后按实际状态判定，提示可能延迟但不能说谎。
- 手动搜索路径（`resolveManual`）仍走原来的同步 `apply`，未纳入两阶段复核。它不在用户本次反馈的范围内，改动会扩大风险面。

## 7. 设备复测：显式选轨没有生效（本次修复）

### 7.1 症状

首次修复后设备复测：自动匹配确实命中（字幕出现在字幕列表里，即 `spec.setSub` 生效了），但选中项仍是「关闭字幕」，画面没有字幕——即 6.1 节设计的显式选轨实际没生效。

### 7.2 根因

`findTrackForSub` 构造选轨用的 `Track` 时漏了 `selected` 标志：

```java
return new Track(C.TRACK_TYPE_TEXT, name, PlayerHelper.describeFormat(match)).playerId(match.id);
//                                        ↑ Track.selected 默认 false
```

而 `TrackUtil.setTrackSelection` 只对 `isSelected()` 为真的轨道写选中索引：

```java
if (track.isSelected()) selectedIndexMapByType.put(type, info.trackIndex);
...
Integer selectedIndex = selectedIndexMapByType.get(type);
List<Integer> indices = selectedIndex != null ? List.of(selectedIndex) : List.of();
builder.setOverrideForType(new TrackSelectionOverride(mediaGroup, indices));
```

`selected=false` 于是 `indices = List.of()` —— 空索引 override，等价于把整个字幕组清空。这与截图完全一致：`TrackDialog.addDisableTrack` 判定「没有轨道被选中」后高亮「关闭字幕」。

手动选轨不受影响，因为 `TrackAdapter` 走 `item.toggle()` 会置 `selected=true`。这就是「手动正常、自动不行」的确切分界。

同一处还有连带影响：`setTrack` 里 `mpvExplicitSubtitlePreference = hasRequestedSubtitle(tracks)` 同样依赖 `isSelected()`，MPV 的输出模式判定也跟着错。

### 7.3 修复

抽出可测的静态工厂，显式置位：

```java
static Track selectionTrack(Format format, Sub sub) {
    Track track = new Track(C.TRACK_TYPE_TEXT, describeSubtitleName(format, sub),
            PlayerHelper.describeFormat(format)).playerId(format.id);
    track.setSelected(true);
    return track;
}
```

`findTrackForSub` 改为调用它。顺带清掉了一处重复的 javadoc 块。

### 7.4 验证

- 新增 `PlayerManagerTest` 两个用例：`selectionTrack_isMarkedSelectedSoTheOverrideSelectsIt`、`selectionTrack_fallsBackToSubtitleNameWhenFormatHasNoLabel`。
- 反向插桩：删掉 `track.setSelected(true)` → 两个用例立即失败，随后还原。
- `PlayerManagerTest` + `subtitle.*` + `TrackUtilTest` 在 leanback 与 mobile 两个变体均全绿。

### 7.5 教训

6.3 节的单测全部是替身测试，只覆盖到 `SubtitlePlaybackSession` 的语义，没有覆盖 `PlayerManager` 真正下发给引擎的 `Track` 对象。缺陷正好落在两者之间的缝隙里。凡是「构造命令对象」的地方，都应把对象的必要字段做成可断言的值，而不是留给设备去发现。