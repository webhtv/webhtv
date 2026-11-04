# IJK-HLS-AESDEC-04 — IJK 加密 HLS 解密下沉到代理

## Recovery anchor

- **目标**：让 IJK 内核能播放 AES-128 加密的 HLS（修复前必失败并自动回退 MPV）。
- **验收**：加密源切 IJK 能出画面；未加密源与 MPV 路径行为不变。
- **状态**：**已实施，三项验收已在模拟器 emulator-5554 实测通过**。
- **回滚**：单一提交，`git revert` 即可；无二进制、无配置迁移、无数据迁移。

## 问题与证据

`webhtv-debug-log (34).txt` + 模拟器 emulator-5554 实机复现（APK 192.9 MB，dev2@5eb7872c7b）。

失败源：AES-128 加密单层媒体 playlist，99 分片。

```
mpv-proxy: playlist dump session=1 extinf=99
  tags={#EXTM3U=1, #EXT-X-VERSION=1, #EXT-X-TARGETDURATION=1,
        #EXT-X-MEDIA-SEQUENCE=1, #EXT-X-KEY=1, #EXT-X-ENDLIST=1}
  keys=[METHOD=AES-128]            ← 结构完整，无标签缺失
ijk: native event=2 http=200       ← playlist 取到 200
ijk: native event=2 http=200       ← 第二个 200
ijk: error what=-10000 extra=-1094995529 prepared=false stage=http-opened
playback-error errorCode=ERROR_CODE_PARSING_CONTAINER_MALFORMED action=FATAL
```

`extra=-1094995529` 即 FFmpeg `AVERROR_INVALIDDATA`。全程代理侧**零个** `MEDIA_SEGMENT` 请求：
IJK 拿到 playlist 后未请求任何分片即判定失败。

### 已排除的假设

| 假设 | 反证 | 结论 |
|---|---|---|
| 上游返回坏分片 | 失败窗口内无分片请求 | 排除 |
| 代理转发密钥导致失败 | `key direct` 已生效（密钥直连源站），仍同错误 | 排除 |
| playlist 被剪坏/缺标签 | dump 显示 6 个标签齐全、extinf=99、segments=99 | 排除 |
| 代理 URL 格式有问题 | 同一代理 session MPV 播放正常 | 排除 |
| IJK 不支持 HLS | `libijkffmpeg.so` 含 `hls,applehttp`/`EXT-X-KEY`/`AES-128`/`aes-128-cbc` 字符串 | 排除 |

**结论**：这份打包 `libijkffmpeg.so` 的 AES-128 HLS 解密路径在运行时不可用（编译带字符串，
执行失败）。依据 IJK 自身在 `http-opened` 阶段、未取分片即抛 `AVERROR_INVALIDDATA`。

## 方案比较

| 方案 | 结论 |
|---|---|
| 不改 | 加密源永远无法用 IJK，用户侧表现为"切核失败并自动跳回 MPV" |
| 改 native 库（重编 ijkffmpeg 开 AES） | 需重编 native、涉 ABI 与二进制定源，成本与风险最高 |
| **代理侧解密（选定）** | Java 层完成 AES-128-CBC，对 IJK 输出明文 TS |

选定理由：绕开 IJK 自身能力依赖，改动限于 Java、可逆、不触碰二进制。

## 设计

对 `kernel == IJK` 且 playlist 含 `METHOD=AES-128` 时：

1. 重写阶段：**剥离** `#EXT-X-KEY` 行（IJK 不会再被要求解密）。
2. 分片阶段：代理取到密文分片后，用 `AES/CBC/NoPadding` 解密，PKCS7 去填充，返回明文。
3. IV：`#EXT-X-KEY` 未带 `IV` 时按规范用**媒体序列号**（`EXT-X-MEDIA-SEQUENCE` +
   分片序号）作为 16 字节大端 IV。
4. 密钥缓存：同一 key URI 只取一次。

约束：
- 仅 `kernel == IJK` 生效，MPV/Exo 路径零改动。
- 仅处理 `METHOD=AES-128`；`SAMPLE-AES` 等直接放弃（保持现状失败）。
- 非加密源、已剥离的源行为完全不变。

## 验收标准与结果

| 场景 | 结果 | 关键证据（模拟器 emulator-5554） |
|---|---|---|
| 加密源 + IJK | **通过** | `aes128 decrypt session=1 iv=sequence`；`aes128 segment bytes=2966464->2966452 seq=0..38`；`decoderName=MediaCodec, OMX.qcom.video.decoder.avc`；`videoSelected=true`；`phase=READY`；position 450620→564860 递增；`ijk: error` 计数 0 |
| 加密源 + MPV | **通过（无回归）** | `kernel=mpv`；`aes128` 计数 0；密钥仍走代理 `role=OTHER code=200`；`phase=READY`；0 错误 |
| 未加密源 + IJK | **通过（无回归）** | `keys=[]`、`key=false`；`aes128` 计数 0；`phase=READY`；position 814867；0 错误 |
| 单元测试 | **通过** | `HlsSegmentDecryptorTest` 8/8、`HlsPlaylistRewriterTest` 12/12、`MpvHlsAdblockTest` 6/6 |

### 实施中修正的两个自身缺陷

1. **顺序缺陷**：`sessionKeys` 最初在 `rewrite` 之后填充，而分片 target 是在 `rewrite`
   **期间**注册的，导致 `Target.key` 恒为 null、解密分支永不触发。改为在重写前用
   `HlsPlaylistRewriter.findAes128Key` 解析密钥。
2. **MPV 回归**：中途把 `#EXT-X-KEY` 单独拦截后又未加 kernel 判断，会让 MPV 的密钥 URI
   从"代理转发"变成"直连"。最终移除 `UriRole.ENCRYPTION_KEY` 与相关 mapper 分支，
   使 MPV 的密钥处理逐字节回到原状（已由 `role=OTHER` 走代理实测确认）。

### 仍存在的已知基线失败（与本任务无关）

`MpvFontConfigTest` 与 `MpvHlsCacheCoordinatorTest` 各有 1 例失败。已用 `git stash`
在**不含本改动**的 HEAD 上复跑确认：同样两个用例、同样数量失败，属预先存在的基线问题。

## 回滚

单一提交，`git revert` 即可；无二进制、无配置迁移。
