# MPV-AUTO-DIRECT-GUARD-RESTORE-20261017

## Recovery anchor

- Objective: 让 MPV「自动」输出模式在支持零拷贝的电视设备上重新进入电视直出（`surface/mediacodec_embed`），只保留按设备判定的零拷贝保护，消除自动档被全局开关永久钉在 GPU 合成 + 拷贝导致的 4K 掉帧/音频欠载。
- Lane: `quick-fix`（单文件行为 + 两个既有测试的期望值修正 + 本文件）。
- Baseline: branch `dev2`, HEAD `dae133ddc49940dc001e4f3588315cfce6dc971d`。
- Protected dirty paths: `.wh_diag/**`（任务前已存在，未纳入 scope）。
- Status: 已实现，验证通过，待提交。
- Next action: 无；本任务完成后由用户决定是否做设备实测（见 §6）。

## 1. 用户可见能力

电视端（leanback）把 MPV 输出模式设为「自动」时，硬解 + 无 LUT/滤镜/自定义 shader 的普通视频应重新走 `mediacodec_embed` 直出，而不是统一走 GPU 合成 + 帧拷贝。预期效果是 4K 高帧率片源的掉帧与音频欠载减少；用户显式选择「GPU 渲染」或「电视直出」，以及手机版行为不变。

## 2. 现场证据（用户提供的 AV diagnostics 导出）

- 文件：`webhtv-debug-log (3) (4).txt`；故障构建 `gitRevision=fc3d3a2b4a`，`flavor=mobile`，`state=clean`。
- `player-engine: mpv output mode=自动 direct=false zeroCopyBlocked=false hwdec=mediacodec,mediacodec-copy render requested=opengl ... actual=opengl/gpu`：设备**未**命中零拷贝保护，但自动档仍落到 GPU。
- `mpv-output: auto decision ... transition=KEEP_GPU reason=not-tv`：该次为手机版，`not-tv` 属预期，不能单独证明电视端问题。
- `mpv-resource-pressure: ... input=hard reason=hard-pressure policy=thermal-severe forwardCeiling=25165824 backCeiling=0`、`playback-system-condition: thermal=severe`：设备处于严重热节流，前向缓存被压到 24MiB。
- `mpv-frame-timing: ... mistimed=0 delayed=0 avSyncMs=0`、`Audio device underrun detected.`：日志同时出现欠载，说明 GPU 拷贝路径在 4K 23.976fps 下余量不足。
- 证据边界：该导出为手机版，**不能**直接证明电视端直出被关闭；电视端结论来自源码（§3），设备实测仍待补（§6）。

## 3. 源码根因

`app/src/main/java/com/fongmi/android/tv/setting/MpvPerformanceSetting.java` 中存在全局常量：

```java
private static final boolean SURFACE_AUTO_STABILITY_GUARD_ENABLED = true;
```

它同时否决三处自动直出判定：

1. `isAutoSurfaceDirectEnabled()` → `!SURFACE_AUTO_STABILITY_GUARD_ENABLED && !isZeroCopyBlocked()`，恒为 `false`；
2. `resolveSurfaceDirect(...)` 的 `OUTPUT_AUTO` 分支 → `!SURFACE_AUTO_STABILITY_GUARD_ENABLED && leanback && autoEligible`，恒为 `false`；
3. 间接影响 `PlayerManager` 的 `shouldKeepVideoShutterClosed()`、`prepareMpvOutputForNewItem()` 粘滞路径、`evaluateMpvAutoOutput()` 的 `effectiveEligible`。

因此电视端自动档**永远**不会进入直出，只剩 GPU 合成路径。

### 3.1 该开关是遗留的临时 kill switch

- `506639c9b0`（2026-07-30）引入，提交信息为「稳定性保护期间暂停自动 Surface 直出」，当时 native Surface 拆除尚未加固。
- `22f2333317`（2026-08-01）「keep MPV TV direct output in production」明确要求：内部实验关闭、回退或风险分类变化**均不能再中止生产自动直出**；该提交移除了 `PlaybackExperimentPolicy.Action.MPV_AUTO_OUTPUT_REBUILD` 门禁，但**没有**移除这个更早的全局开关，`plans/播放性能自动档判断依据与最佳参数方案.md` 的 V-08 记录因此与实际代码状态矛盾。
- 开关要防的 native 生命周期问题已由后续工作解决：`P4-1`（JNI 关闭串行化，`907bfca982`）、`P4-3`（终态 Surface 拆除，`8250e2204f`）。

### 3.2 与鱼壳（`fish2018/webhtv` @ `Silent1566`）的差异

| 项 | 鱼壳 | 本仓库 |
|---|---|---|
| `libmpv.so`（两 ABI） | — | **md5 逐字节相同**，非差异来源 |
| 自动直出全局开关 | 无 | 有，恒 false |
| 零拷贝设备保护 | 无 | 有（保留） |
| 失败回退内核 / 15s 启动看门狗 | 无 | 有（不在本任务范围） |

这解释了「同一接口同一片源，鱼壳流畅、本仓库卡顿」中与输出路径相关的部分。

## 4. 方案比较

| 方案 | 收益 | 缺陷/风险 | 决定 |
|---|---|---|---|
| 不改 | 零风险 | 电视端自动档永久退化；V-08 结论与实际不符 | 否决 |
| 删除全局开关，保留设备保护 | 恢复已批准行为；仅影响自动档；保护仍按设备生效 | 自动档重新进入 `mediacodec_embed`，若某设备存在未发现的拆除竞态会重新暴露 | **采用** |
| 同时删除设备保护 | 更彻底 | 会重新引入 `17085ec024` 修过的 Kirin/x86 转译/模拟器问题 | 否决 |
| 改为用户可见开关 | 可控 | 扩大 UI/持久化面，超出本次范围 | 暂缓 |

## 5. 实现

`MpvPerformanceSetting.java`：

- 删除 `SURFACE_AUTO_STABILITY_GUARD_ENABLED`，保留 `ZERO_COPY_DEVICE_GUARD_ENABLED` 与 `MpvHardwarePolicy`；
- `isAutoSurfaceDirectEnabled()` 改为 `return !isZeroCopyBlocked();`；
- `resolveSurfaceDirect(...)` 的 `OUTPUT_AUTO` 分支恢复为 `leanback && autoEligible`；
- 补充注释说明删除原因与历史提交坐标。

未改动：`PlayerManager`、`MpvPlayerEngine`、`MpvPlayer`、native 资产、依赖锁、补丁、UI 文案。

## 6. 验证

1. `MpvOutputModePolicyTest`：自动档在「硬解 + 电视 + 合格」时进入直出；缺 autoEligible/非电视/软解时仍拒绝；零拷贝设备保护仍能否决自动档。
2. `MpvStabilityGuardSourceTest`：新增源码级回归断言，要求 `SURFACE_AUTO_STABILITY_GUARD_ENABLED` 不被重新引入，且 `isAutoSurfaceDirectEnabled()` 的唯一否决条件是 `isZeroCopyBlocked()`；原有两个源码断言（`PlayerManager` 仍调用该判定）保留。
3. `MpvOutputShutterSourceTest`：未改动，其断言只依赖 `PlayerManager` 仍调用 `isAutoSurfaceDirectEnabled()`，继续通过。
4. 编译与单测：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.setting.MpvOutputModePolicyTest --tests com.fongmi.android.tv.player.MpvStabilityGuardSourceTest --tests com.fongmi.android.tv.player.MpvOutputShutterSourceTest --no-daemon`。
5. `git diff --check`。

实测结果（2026-10-17 Asia/Shanghai）：

- 首次执行 `BUILD SUCCESSFUL in 5m 27s`，99 tasks；`MpvOutputModePolicyTest` 9/9、`MpvStabilityGuardSourceTest` 6/6、`MpvOutputShutterSourceTest` 4/4，失败/错误/跳过均为 0。
- **变异测试**：临时把 `SURFACE_AUTO_STABILITY_GUARD_ENABLED` 重新插回 `MpvPerformanceSetting`，`MpvStabilityGuardSourceTest` 如期 `failures="1"`（`BUILD FAILED in 1m 3s`），证明新增断言可失败而非恒真；随后从备份还原，`grep -c` 为 0，重跑三组测试 `BUILD SUCCESSFUL in 2m`，仍为 9/6/4 全绿。
- `git diff --check` 通过。

### 6.1 未完成的设备验证（不在本任务范围）

需在电视端（leanback，arm64 与 32 位各一次）同一片源下对比：

- 预期日志变化：`mpv output mode=自动 direct=true ... actual=surface/mediacodec_embed`、`mpv-output: auto decision ... transition=ENTER_SURFACE_DIRECT`（或 `KEEP_SURFACE_DIRECT`）。
- 判据：`mpv-frame-timing` 的 `mistimed/delayed` 与 `Audio device underrun` 次数下降；无新增 native 崩溃、无 Surface 拆除相关错误。
- 回退判据：出现直出相关崩溃或黑屏时，把输出模式显式切到「GPU 渲染」应恢复现状，且可用 `git revert` 回退本次提交。

## 7. 回滚

单文件行为改动 + 两个测试期望值 + 本文件，`git revert <本任务提交>` 即可完整回退；不涉及 native、依赖锁、补丁或产物。
