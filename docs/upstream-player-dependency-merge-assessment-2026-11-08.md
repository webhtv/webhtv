# FongMi/TV 新提交合入评估（2026-11-08）

## Recovery anchor

- 当前目标：评估记录基线之后全部 TV 增量已评估完毕；用户进一步批准其中 6 个源提交对应的 5 个窄适配单元，已在独立工作树实现、完成联合验证，并已合入本分支。不升级依赖/native、不改锁/二进制。
- 本地：`Silent1566@47454687f504955775b398905ba09d73aeb9f20c`；原有 `app/.cxx/` 由 guard 保护（35 个脏路径）。
- 上游冻结：`https://github.com/FongMi/TV`，`fongmi@4512b4c9f82534ff66acfde06ccdd68f35b074f0`，访问日期 2026-11-08。
- 原记录基线：`1a19fee278fa2234da725d61a53bf59b69fe9127`；共同基点：`4573249cd6679c6becca9957f6a011bf6c4a5afd`。不是祖孙；旧侧 20 条、当前侧 154 条，不能把全部 154 条称为新功能。
- 当前状态：下方台账为 154 条源码级评估；用户只授权首批 6 个源提交的选定不变量，其余候选/全部上游 hunk 未实施。首批代码、测试与任务文档已合入，单元与整批验证结果见文末归档。
- 回滚锚点：上述本地 HEAD；实际实施应分别形成可回滚原子变更，不能整支 merge。当前评估仅新增本文及 `.git/` 内阅读/证据缓存。
- 下一步 / Next action：优先按本文高价值窄适配簇补齐唯一任务文档、必需设计证据和风险比例验证，再实施；本次不启动任何实施。

## 1. 范围、方法与历史关系

延续 [2026-08-20 任务索引](upstream-player-dependency-merge-assessment-2026-08-20.md)，它在当前分支是恢复副本。历史完整索引保存在 `9fcab83f9084446566240a8e8f5233d87d0274cc` 的同名路径。新波次由本文索引，既有 E/P/C ID 不重新编号；未启动候选暂只登记本文行为簇 ID，未来开始时只能使用一个唯一任务文件，不创建重复 plan/fix 文档。

用户确认了完整记录基线范围；只追踪候选必需的关联仓库，不重做所有媒体依赖的全量升级审计。完整提交编号及处置以本文第 4 节的持久化台账为准；early 45、player 60、common 49。`.git/tv-assessment-commits.json` 及三组审查 JSON 是本机辅助证据，不随 Git 提交分发；其他克隆应按台账编号和上游完整 hash 恢复，不依赖这些缓存。不同 git log 排序不能重新分组。

重写历史实证：当前侧第 3–20 条与旧侧对应条目的 `diff-tree --raw` 路径和完整 blob 差量一致；第 1/2 条不同，是音视频效果向 Media3 API 下沉的迁移。不能据文件删除断言功能消失，也不能因标题相同断言当前 WebHTV 已覆盖。逐条处置以本地最终行为为基线。

证据分级：A = 精确上游/本地源码与官方 API；B = 维护者提交说明；未做实机或性能测量的资源/流畅度收益仅为推断。巨型架构提交按具体行为簇检查，未验证所有关联媒体二进制和全量行为。未取得的 patch 或关联实现必须明确列为 needs-evidence/暂缓，而非伪装完成。

## 2. 总体建议与优先级

**不建议整体合并当前 fongmi 分支。** 先提取小型正确性/安全不变量，保留现有 WebHome、管理页、远程托管、同步墓碑、缓存总量、日志、手动视频解码、ASS/HDR/双字幕、AV3A/AVS3、DV/FEL、MPV native 与 linker 命名隔离。

| 优先级 | 行为簇/任务 | 建议 | 实际价值与边界 |
| --- | --- | --- | --- |
| 高 | C-TV-FILE-SAFETY | 实施候选，窄适配 | ZIP/本地服务路径约束、根删除保护、有限解压与失败反馈；保留 Range/download/size/多选/目录。404可作为同簇小子项 |
| 高 | C-TV-SEEK-OWNERSHIP | 实施候选，窄适配 | 合并遥控连续 seek、可取消延迟提交；切集/切源/退出不让旧回调作用新媒体 |
| 高 | C-TV-STRM | 实施候选，窄适配 | 无 Content-Disposition 的远程 STRM 也能解析；有界读取、BOM/空白/URL校验、失败保留原 URL |
| 高 | C-TV-PYTHON-CLEANUP / C-TV-LOADER-OWNERSHIP | 分阶段实施候选 | init失败回收且不永久缓存失败；再处理clear与新owner/迟到发布竞态，不能全局清旧JS模块误伤新owner |
| 中高 | C-TV-REDIRECT-PROXY / C-TV-LIVE-AUTH | 设计后窄适配 | 跨重定向选路与代理凭据隔离、直播分片/密钥的来源作用域；须保留本地代理通配符/localhost/日志，不能把源码风险描述成已实测泄漏 |
| 中高 | C-TV-PROXY-RESPONSE | 实施候选，窄适配 | QuickJS proxy2保留第4响应头、null/bytes规范；保留旧3/4/5项base64协议，不直接换新stream-token |
| 中 | E-TV-CONTENT-TYPE | 有条件候选 | 无扩展名播放源在prepare前确定类型；先证明Media3源码/产物API可用，保留代理、DRM与本地提取器 |
| 中 | C-TV-ASYNC-RESULT-OWNERSHIP | 实施候选，窄适配 | 旧弹幕/尺寸更新不污染切集后的新播放；目前cancel网络不能撤销已入主线程队列回调 |
| 中 | E4-LIBASS外挂配置子项 | 沿既有任务补充 | null/空字幕过滤、稳定选轨身份；上游UUID不能直接替换本地ASS index解析契约，不重做libass |
| 低/待需求 | 通知封面、SAF ABI、直播回看、目录筛选、网页工作台 | 有条件或暂缓 | 各自补确切失败样本/交互合同/安全边界，不以框架更整齐为合入理由 |
| 暂缓 | C-TV-MOTION-INTERPOLATION | 独立 Exo→MPV 阶段 | 新光流插帧不同于本地已有MPV通用平滑运动；需要新renderer/native/API/同锁双ABI制品和GPU/画质/功耗验证，不能只合设置页 |
| 暂缓 | C-TV-RUNTIME-MODULES | 另立刚需再评估 | 外置模块/WASM/原生Python/配对QuickJS SDK-JNI，不应绑进播放器修复；签名、安装重启、双ABI与包体待证 |
| 忽略 | 已有libass/双字幕/手动视频解码/诊断TXT；Gson R8保留；版本/网站维护 | 不重复实现 | 当前已有等价/更强机制；R8随Gson2.14.0规则覆盖，不是证实缺陷；部分能力仍有原任务实机边界，不改写为新任务 |

最小执行顺序：纯App安全与正确性小簇可独立；涉及播放器依赖严格 Exo→MPV→common。先验证输入与取消/所有权不变量，再讨论新模块或插帧；不同时叠加大重构和制品升级。

## 3. 候选决策包与本地适配

以下每包同时记录不改、原样上游、窄适配的取舍。完整设计/实机/性能证据不足的项明确暂缓；“实施”表示值得下一步做窄适配，不表示所有实施门槛已经通过。

### C-TV-FILE-SAFETY — 通用：文件输入/原子写/本地服务安全不变量（优先适配）
- 所属审查分类：early（实际Exo/MPV/common按适配所有权分阶段）
- 建议：建议窄适配实施研究，禁止原样合；当前无实施授权。安全功能收益清晰，但官方Android资料未取得，最佳实践 gate仍待补证。
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`b802f538cb25bab7252bc170279101b6e409cec4`](https://github.com/FongMi/TV/commit/b802f538cb25bab7252bc170279101b6e409cec4)
  - FongMi/TV [`179cd159266121932ab570b5188b1f9fccf65461`](https://github.com/FongMi/TV/commit/179cd159266121932ab570b5188b1f9fccf65461)
  - FongMi/TV [`5a905f87404ec803239edd3c3d6abce6a554f6a8`](https://github.com/FongMi/TV/commit/5a905f87404ec803239edd3c3d6abce6a554f6a8)
  - FongMi/TV [`2ae956ad57b8f60246192e64e2e2af9b0d389b48`](https://github.com/FongMi/TV/commit/2ae956ad57b8f60246192e64e2e2af9b0d389b48)
  - FongMi/TV [`c33e010a36a6e72c759abb427dd6d1c3cbe5b707`](https://github.com/FongMi/TV/commit/c33e010a36a6e72c759abb427dd6d1c3cbe5b707)
- 当前项目已有实现 / 具体本地证据：FileUtil.java:69–80未canonical校验；Path.root(String):93–98无隐含边界；Local.java:64–85上传/删除直接root，local():129–132可读绝对路径。；FileChooser.java:58–111保留多选/目录并同步解析、URLDecoder；Download.java:61–111有HTTP/完整长度/详细进度，StorageSpace:115–135与溢出size合同必须保持。；来源1只加canonical；2加条目/字节；27加atomic temp/fsync/Os.rename；30委托FileUtil，不是新的ZipInputStream实现。
- 要实现的实际能力 / 收益：防ZipSlip/目录穿越/根误删除/半写文件与SAF大文件阻塞，错误可反馈；收益为代码推断，无实测性能数据。
- 缺点与风险 / 与现有功能的关系：上游ZIP默认Integer.MAX_VALUE/Long.MAX_VALUE，越界entry静默continue且可能返回success，解压失败可能留下部分文件；必须有限配额+明确拒绝/回滚，而非裸移植。；27删除本地storage统计并用Path.size普通相加；原样合会破坏缓存容量/未知空间表达。；30未带本地download/Content-Disposition/size，读loopback例外须保留仅内部用途和网络认证边界，不能变外网任意读。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：不改现有API消费者：新增显式可失败同目录原子API；逐一替换必要导入/写入；跨文件系统移动不采用先copy后删除吞错。；逐个入口定义允许根/单名/拒绝root删除/不跟symlink；Zip有限条目、总字节、单文件、取消与错误传播；保持本地Range/download/授权。；SAF按URI和ContentResolver后台materialize，保留多选/目录/lifecycle并限制配额；不沿上游缩回单URI接口。；no-change保留已证实缺口，原样上游会丢合同；选窄适配。回滚为一个安全API及受影响入口的原子提交，不动播放器产物。
- 预计需要的验证 / 未完成门槛：未运行：../、绝对路径、symlink、同名前缀兄弟目录、根删除、ZIP bomb/越界entry/取消/存储满/半写保留旧文件。；未运行：Range suffix/open/full/空文件/416、download header/size字段、本地loopback播放器读与远端共享权限。；未运行：content provider/display name穿越/中文+%路径/多选/目录/页面离开/超限；缓存StorageSpace与总量回归。；资料：https://developer.android.com/privacy-and-security/risks/zip-path-traversal 及 https://developer.android.com/training/data-storage/shared/documents-files 读取失败（Firecrawl403，历史代理127.0.0.1:7897拒连）；不得宣称完整最佳实践评审。

### E4-LIBASS（外挂配置子项） — Exo优先：外挂字幕配置可靠性与有限直下；保留E4-LIBASS
- 所属审查分类：early（实际Exo/MPV/common按适配所有权分阶段）
- 建议：22+35优先窄适配；31按最小下载接线增补而非全量搜索重构；36/37渲染体系忽略，外部字体UI暂缓E4内增补。
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`753b66fc0e390b27e7a49c7553d4dda5e3831054`](https://github.com/FongMi/TV/commit/753b66fc0e390b27e7a49c7553d4dda5e3831054)
  - FongMi/TV [`94acc63a83ee836e219375ef392fadf004e08f11`](https://github.com/FongMi/TV/commit/94acc63a83ee836e219375ef392fadf004e08f11)
  - FongMi/TV [`125468f0ec3f448c93626b4a7ef27a03cad21790`](https://github.com/FongMi/TV/commit/125468f0ec3f448c93626b4a7ef27a03cad21790)
  - FongMi/TV [`773cae11bf3b89ef26f7353ce8a534c775984df2`](https://github.com/FongMi/TV/commit/773cae11bf3b89ef26f7353ce8a534c775984df2)
  - FongMi/TV [`0addfec68454b588c5c77cfbdaaf230714cbece8`](https://github.com/FongMi/TV/commit/0addfec68454b588c5c77cfbdaaf230714cbece8)
- 当前项目已有实现 / 具体本地证据：ExoUtil.java:923–934未过滤null/空Sub，ASS使用AssInput.EXTERNAL_ID_PREFIX+index；AssInput.java:12–28读取数值身份。；PlayerManager.java:878未预检；SubtitleDialog.java:80–148支持既有字号位置；ExoPlayerEngine.java:165/253创建ExoAssSession，双轨ExoSubtitleSession存在。；docs/E4-LIBASS-exo-ass-rendering.md:17–50/73–90已有字体附件、独立SDR HDR层、双字幕和当前JNI产物；不可误判缺libass。；Download.java:61–111已有HTTP/完整长度/取消/详细进度；本地无上游SubtitleApi/SubtitleArchive/ASSRT搜索体系。
- 要实现的实际能力 / 收益：避免无效Sub异常；稳定外挂身份并补MIME推断，降低列表重排/切集选轨错配；有限下载可支持直接srt/ass，未来外部字体选择增加可读性。
- 缺点与风险 / 与现有功能的关系：上游external:UUID直接替换本地ASS index将破坏解析与流绑定，是明确适配缺陷，门槛为同步映射/测试。；新上游LibassPlaybackSession/SecondaryTextTrackSelector与本地TextRenderer observer+独立JNI不同，整升级可能回退HDR/DV/双轨/attachment/软解/性能；不升级。；31上游取消后不删file且进度字段更少，本地已有完整性检查更强，必须保存现有行为。；全ASSRT搜索需要接口配置、鉴权、供应商/取消/归档安全研究；尚未完备，暂缓门槛明确。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：先过滤null/空url，并保留valid entries原flag和默认/forced策略。；使用稳定URL digest独立映射，保留webhtv-ass-external:index可解析契约，或同一原子单元更新AssInput/流绑定；不改视频输出。；直下通过既有Download/setSub加入有限maxBytes/格式判定/空结果错误；zip复用安全候选限额。；外部font选择如有产品需求沿现有E4任务接线；不创建第二套渲染器，不改变弹幕宿主。；no-change无效配置缺口保留；原样上游破坏ASS。选择窄适配；回滚Java字幕入口及其映射/测试，不动现有JNI/Media3/MPV产物。
- 预计需要的验证 / 未完成门槛：未运行：null/空/重复/重排Sub，缺MIME srt/ass/vtt，forced/default flags，内嵌+外挂双轨切源/seek/附件字体/HDR。；未运行：同URL不同参数、取消/HTTP错误/空/截断/超限下载，原progress speed/elapsed消费者兼容。；36/37新Media3 API的完整跨仓库fullhash链尚未冻结；这是拒绝整升级的门槛，不是本地E4能力缺失。

### C-TV-ARTWORK — 通用：通知/媒体浏览封面Headers与data URI边界
- 所属审查分类：early（实际Exo/MPV/common按适配所有权分阶段）
- 建议：有条件候选，适配持久性及访问边界后再实施；不原样使用会逐出的LAN内存URL作为History。
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`1eba5a1aecf6c0a80b7d512a4251ba375bd72380`](https://github.com/FongMi/TV/commit/1eba5a1aecf6c0a80b7d512a4251ba375bd72380)
- 当前项目已有实现 / 具体本地证据：ImgUtil.java:109–119只Glide模型处理data及@Headers/@Cookie/@Referer/@User-Agent；PlaybackService.java:117默认MediaLibrarySession无setBitmapLoader。；leanback VideoActivity.java:414预载封面，History.java:39/41持久vodPic/wallPic；上游32把data换Server /image/hash，16MiB memory cache和8MiB输入限制。
- 要实现的实际能力 / 收益：让需要Cookie/Referer的封面在MediaSession通知和浏览中可加载，避免大data直接进入Intent造成binder超限；实际样本尚未运行。
- 缺点与风险 / 与现有功能的关系：内存cache重启或逐出使保存的/image/hash失效，错误跨播放/历史恢复；须改身份生命周期或持久受限文件。；/image共享LAN可见性、敏感封面、URL/Headers隐私以及有界解码内存要有门槛；上游传输16MiB限额不是完整decoded bitmap预算。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：先只新增MediaSession BitmapLoader复用安全headers且有限字节/尺寸/取消；data使用进程内/私有受限存储，传Intent短ID但History保留稳定来源。；不替换本地wallPic、ImgUtil本地协议、服务权限和错误封面回退。；no-change通知可能加载失败；原样上游短期LAN cache不能持久化。选择最小loader+明确data生命周期；回滚独立Java封面适配。
- 预计需要的验证 / 未完成门槛：未运行：带Headers/data/普通https/content封面、通知与媒体浏览、进程重启、16MiB逐出、超大压缩图、取消、历史返回和墙纸模式。；必要官方Media3 BitmapLoader/notification资料及安全边界补证未完成，实施前完成，不声称该候选ready。

### C-TV-PLAYBACK-OWNERSHIP — 通用：迟到service/result与历史预载身份（暂缓整框架）
- 所属审查分类：early（实际Exo/MPV/common按适配所有权分阶段）
- 建议：优先验证12的本地late-binding窗口；3/8/33/34仅不变量参考，暂缓controller整迁移；无可量化性能收益主张。
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`8146efc17c91c4f75b1b8db36fbc11ceaf93b1a7`](https://github.com/FongMi/TV/commit/8146efc17c91c4f75b1b8db36fbc11ceaf93b1a7)
  - FongMi/TV [`f803adce0bfffebd05a2115a126aa3334ae99a11`](https://github.com/FongMi/TV/commit/f803adce0bfffebd05a2115a126aa3334ae99a11)
  - FongMi/TV [`9c61c1b4562a96b21f069074b36e4ed6e5b6ae49`](https://github.com/FongMi/TV/commit/9c61c1b4562a96b21f069074b36e4ed6e5b6ae49)
  - FongMi/TV [`a4ebdd65560ca207ef78f908168ceca9bfbb0220`](https://github.com/FongMi/TV/commit/a4ebdd65560ca207ef78f908168ceca9bfbb0220)
  - FongMi/TV [`2112cb48cf6570818ac578df011925a0a3494369`](https://github.com/FongMi/TV/commit/2112cb48cf6570818ac578df011925a0a3494369)
- 当前项目已有实现 / 具体本地证据：PlaybackActivity.java:854–869立即绑定/foreground/回调；880–913本地audio后台/PiP/ASS策略。上游12有STARTED/canDispatch/owner和一次init。；History.java:338–418现有push禁止同名合并、nearEnd与墓碑删除；PlaybackProgressWriter.java:58–77,130–149有cid和墓碑同步锁策略。；本地无上游VodPlaybackController/LivePlaybackController/VideoViewModel，Activity/LiveViewModel调用仍独立；MpvPreloadController/MpvHlsVariantController/缓存总量已本地改进。；3–20每对新旧完整raw delta相同，重放不是未实现证明，也不是covered证明。
- 要实现的实际能力 / 收益：避免快速切页/晚连service抢回Surface和错误历史/预载结果污染；需定向复现后确认收益，不为“架构更干净”整体升级。
- 缺点与风险 / 与现有功能的关系：上游停播/observeForever/claim binding条件不等价于本地音频后台/PiP/音频专辑/disc/Surface代次；裸合可能停错或泄露。；History cid键格式及同名merge更改与远端同步/墓碑/Push语义冲突；不能套History replacement。；整体移植跨本地大型Activity、自动性能、MPV/Exo预载和诊断路径，风险超过已证收益，暂停门槛。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：先复现late service connect已onStop/切另播放页场景，再给现有binding加owner/generation与生命周期闸门。；历史只审snapshot/实际cid删除/异步有序写，并保留所有墓碑和导入同步路径；预载只加request+media identity过期拒绝。；不创建整套Host/Controller/State/DataSource替换；分别原子回滚。no-change若复现成立不可接受；原样重构不适配本地合同。
- 预计需要的验证 / 未完成门槛：未运行：晚连/再连、A→B→A、后台音频/PiP、销毁、双Surface/ASS代次、同页重复activate不重复prepare。；未运行：切源切集、反序、DRM/parse禁预载、迟到result、历史清空/同步墓碑/跨cid删除、重复写读恢复。；没有实机及Gradle/native测试；若无法复现具体缺口，保留maintenance暂缓而不新增任务。

### C-TV-PYTHON-ABI — 通用：自定义Chaquopy平台ABI状态同步
- 所属审查分类：early（实际Exo/MPV/common按适配所有权分阶段）
- 建议：独立候选：先核对17.0 AndroidPlatform.ABI实际消费/可见性，再单行适配；不绑定25的APK解包重签。
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`5c8b9a82de2fe3f8ea912ed42324a31c616c621e`](https://github.com/FongMi/TV/commit/5c8b9a82de2fe3f8ea912ed42324a31c616c621e)
- 当前项目已有实现 / 具体本地证据：chaquo/src/main/java/com/fongmi/chaquo/Platform.java:43–66从stdlib资产选择本地ABI，未导入AndroidPlatform也未赋其静态ABI。；gradle/libs.versions.toml:3 python17.0.0；当前mode+abi flavor仍隔离，不能用设备SUPPORTED_ABIS替代实际进程ABI。；官方https://chaquo.com/chaquopy/doc/current/android.html 成功读取（2026-11-08，17.2文档）；ABI原生requirements、Python3.11及以下兼容v7、仅单模块插件和AndroidPlatform标准启动说明。
- 要实现的实际能力 / 收益：可能修复自定义Platform下原生Python扩展资产定位；一行且无需媒体重建，低实施面。未实测崩溃，不将潜在问题说成已验证bug。
- 缺点与风险 / 与现有功能的关系：AndroidPlatform.ABI为实现细节，本地17.0与文档17.2不能混同；并发初始化和实际进程/资产ABI需核验。；上游25split资产可能掩盖问题，本地flavor已有隔离；如果没有真实consumer/失败证据，不做无用补丁。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：核查本地依赖字节码/源中字段与Python importer消费；确认同步既有asset选择结果而非新猜测设备ABI。；只改Platform赋值及定向测试，不升级Chaquopy、Python、Gradle或native，不改APK签名裁剪。；no-change待确认风险；原样赋值在17.0未核验不可ready。适配通过后可原子回滚单文件。
- 预计需要的验证 / 未完成门槛：未运行：arm64、v7在arm64设备进程、纯Python与至少一个原生extension import、重启/升级资产恢复、并发start。；未运行：字段可见性/consumer静态核验，双ABI Java编译；官方17.0确切源码commit未取，实施门槛。

### C-TV-SEEK-OWNERSHIP — 1. 电视按键seek合并与过期取消（common）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施（仅建议，须另行批准）
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`7bf115759881e27df819bb01a357f4d7094d295c`](https://github.com/FongMi/TV/commit/7bf115759881e27df819bb01a357f4d7094d295c)
  - FongMi/TV [`51813cb611386ab3fc3fe388480cfb12d2670f67`](https://github.com/FongMi/TV/commit/51813cb611386ab3fc3fe388480cfb12d2670f67)
- 当前项目已有实现 / 具体本地证据：CustomKeyDownVod.java:51-55 独立匿名250ms post，无法removeCallbacks；PlayerManager.java:1269-1287 seekTo是真正执行入口；上游SeekInput使用稳定Runnable、keyCode/deviceId/downTime归属；131在控制/pause/play/menu/back取消
- 要实现的实际能力 / 收益：减少连点重复seek及切片/控制接管后的旧seek；性能收益只推断。纯Java不需native，包体增量预计很小未测。
- 缺点与风险 / 与现有功能的关系：上下键/长按加速/直播换线路行为不可改变；快速换源/退出后250ms回调不得seek新片；不同遥控deviceId和cancelled keyUp须隔离
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：不复制上游Activity；在当前CustomKeyDownVod/Live输入保存稳定Runnable+source/session token、提供cancelSeek，现有Cast/Live/Video各在换源/换集/控制接管/onStop/onDestroy调用；250ms保持当前值。回滚为一个纯Java输入单元。
- 预计需要的验证 / 未完成门槛：确定性假时钟测试：重复左右键只提交一次；设备混杂、取消UP、pause/menu、换集、onStop后零旧seek；TV Cast/Live/Vod三入口验证，直播不可seek仍换线路。

### C-TV-STRM — 2. HTTP STRM无文件名首行识别（common）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施（仅建议）
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`7d70ebdfe56997ae9bccca7539a413a6999a09e3`](https://github.com/FongMi/TV/commit/7d70ebdfe56997ae9bccca7539a413a6999a09e3)
- 当前项目已有实现 / 具体本地证据：Strm.java:29-33 只读Content-Disposition命中.strm/.txt才body.string；没有probe上限；上游peekBody(64*1024)、res.isSuccessful、BOM/trim、合法目标解析
- 要实现的实际能力 / 收益：普通HTTP STRM响应也可解析，读量有界；无需Media3/native升级，包体预计极小未测。
- 缺点与风险 / 与现有功能的关系：不能把媒体二进制或HTML误识别成URL；认证/重定向/SSRF范围不应无意扩大；超限和错误响应须退回原URL，不能卡起播
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：只改当前Strm.http，64KiB peek+首行合法scheme校验+空/BOM处理；保持noRedirect、原URLfallback及现有Source链。明确content://支持另任务，不趁机改URI权限。独立提交回滚Strm即可。
- 预计需要的验证 / 未完成门槛：mock HTTP：无Content-Disposition、BOM、CRLF、空白、64KiB边界、HTML/MP4、非2xx、重定向、认证、超时取消；验证原媒体URL不误解析。

### C-TV-LIVE-AUTH — 3. 来源限定的HLS认证上下文（Exo→MPV适配）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施设计/先Exo窄适配，MPV并行证明后分阶段启用
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`16b1f3e10c3e9ca0877461b26c21308626fac77c`](https://github.com/FongMi/TV/commit/16b1f3e10c3e9ca0877461b26c21308626fac77c)
- 当前项目已有实现 / 具体本地证据：AuthInterceptor.java:18 host-only全局ConcurrentHashMap；31-41用原request而非challenge response.request生成401重试，48-52保存userInfo；LiveApi.java:45-49 Channel.result→Source.fetch；MediaSourceFactory.java:385-390 OkHttp.play+headers；local Auth.Context、Channel.applyAuth、PlaySpec.auth均缺；上游Context capture sameOrigin(scheme/host/effectivePort)，拒绝已有Authorization/userinfo/auth query的覆盖
- 要实现的实际能力 / 收益：降低跨source/scheme/port凭据复用的源码风险，同源清单/分片/key能共享认证。没有实测泄漏结论；额外映射开销/包体未测。
- 缺点与风险 / 与现有功能的关系：跨origin重定向禁止承袭凭据，显式Authorization优先；HLS key/子清单与token/digest不能简单复用challenge值；MPV直curl、App代理/stream_cb为不同网络边界；认证凭据不得进入cache key或诊断明文
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：使用当前Result/engine.PlaySpec增不可变auth context；限定origin和source session，Exo DataSourceFactory按每源传tag；challenge用response.request。保留当前缓存/代理/Range/去广。MPV必须分别审App代理及curl可传方式，未证实时不启用。可按auth新模式feature flag回滚，不删除现有认证fallback前先迁移。
- 预计需要的验证 / 未完成门槛：同host不同port/source/HTTP→HTTPS、跨origin302、Basic/Digest/query token、HLS master/media/key、显式Authorization优先、切台/停止/预缓存取消、脱敏日志；各Exo/MPV路径单独结果。

### C-TV-REDIRECT-PROXY — 4. 重定向代理重新选路（common/网络）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施窄适配；81仅新Net体系引入时伴随
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`2d84dc637908a0ca8e7b86a55da631b143c9cf6b`](https://github.com/FongMi/TV/commit/2d84dc637908a0ca8e7b86a55da631b143c9cf6b)
  - FongMi/TV [`0a4e0f93641d82b205ab5a5dd40a18e249a666a9`](https://github.com/FongMi/TV/commit/0a4e0f93641d82b205ab5a5dd40a18e249a666a9)
- 当前项目已有实现 / 具体本地证据：local OkProxySelector.java:63-75保护host==null/local-target，当前没有Policy/ProxyRedirectInterceptor；gradle/libs.versions.toml OkHttp=5.4.0；父节点官方5.4.0 Interceptor源码复核有withProxySelector/getProxy/getProxySelector（本子节点未独立HTTP读取）
- 要实现的实际能力 / 收益：重定向到新域名可按规则换代理；减少错误选路/认证失败为推断未量化，无native制品变化。
- 缺点与风险 / 与现有功能的关系：代理规则继承边界、loop次数、POST重放/SSL redirect、Proxy-Authorization脱敏；124针对新Policy的URI nullhost，不是当前已有同类崩溃；现有debug/fallback/localhost行为不可退化
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：当前OkProxySelector上窄补有界RedirectInterceptor，采用最终HttpUrl语义而非48原Policy；保留现日志、系统fallback、动态规则更新与认证。若不引入NetOptions则不要复制81接口；单独网络unit回滚。
- 预计需要的验证 / 未完成门槛：借鉴上游OkProxySelectorTest：跨host/scheme/port、有/无规则、DIRECT/proxy认证、规则generation变更、loop上限、Unicode host/nullhost、redirect=false、POST307/308与302策略、HLS/Range。

### E-TV-CONTENT-TYPE — 5. 未知URL类型预解析（Exo）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：暂缓，先完成确切Media3类/制品可达性gate
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`60e901aa0aeaaac41c3586b7f6c5989db5e4d5a4`](https://github.com/FongMi/TV/commit/60e901aa0aeaaac41c3586b7f6c5989db5e4d5a4)
- 当前项目已有实现 / 具体本地证据：localMediaSourceFactory.java:323-325直接DefaultMediaSourceFactory，getDataSourceFactory:367-388带缓存/字节观测/优先级/EOF恢复；local代码与patch未找到ContentTypeResolvingMediaSource；未在可定位的本机AAR缓存确认类；third_party/media-lock.json local media commit e3e922d5c01bc0b564849940fe589daf37360d15
- 要实现的实际能力 / 收益：缺扩展/错误扩展HLS/DASH起播类型更可靠；额外HTTP sniff/延迟和包体未测。
- 缺点与风险 / 与现有功能的关系：上游自定义ContentTypeResolvingMediaSource不等于官方默认类，确切源commit未知；必须取消及时、避免双请求、保留auth/Range/key/cache/去广/ASS/DV提取；无匹配类不能仅以media3版本字符串认定可编译
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：冻结关联FongMi media类全hash并确认已打包API后，在当前工厂末端包装，禁止替换整个工厂。对已知mime不probe，对未知源有界探测/回退；flag关闭恢复当前路径。
- 预计需要的验证 / 未完成门槛：纯单测/fixture：无后缀HLS/DASH/MP4、错误Content-Type、认证、redirect、Range、204/404/HTML、取消seek/换源、预缓存、字幕字体与DV/APE；起播请求次数/延迟A/B才可称收益。

### C-TV-ASYNC-RESULT-OWNERSHIP — 6. 异步弹幕结果当前播放归属（common）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施窄适配（先确定性竞态fixture）
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`fa5a39261ba053174b3a2b100c4a1073e2bd39c0`](https://github.com/FongMi/TV/commit/fa5a39261ba053174b3a2b100c4a1073e2bd39c0)
- 当前项目已有实现 / 具体本地证据：VideoActivity.java:1236 DanmakuApi.search(...,player()::setDanmaku)无Result/session谓词；DanmakuApi.java:83-98只HTTP cancel，无generation；上游isCurrentResult要求pendingRequest==null、当前quality==result且host未finishing；旧binding不得取消新binding查询
- 要实现的实际能力 / 收益：旧集弹幕不落到新片，增强换源/页面重绑正确性；包体小、性能推断不作提升声称。
- 缺点与风险 / 与现有功能的关系：HTTP cancel不足以撤销已post callback但尚无实机复现；当前DanmakuApi也可能被手动字幕/弹幕查找共用，不能一律全局cancel；需保护当前网络直播弹幕与批处理背压
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：在现有查询调用捕获immutable播放session/source/result token，UI apply前校验同片+binding/native owner；按会话撤销，而非迁移VodPlaybackController。一个Java查询所有权单元可独立回滚。
- 预计需要的验证 / 未完成门槛：控制callback先后顺序：A→B、A activity销毁B重绑、pending换源、后台/Native↔Web、手动选择与自动查找；只B结果可apply且旧owner不cancel B请求。

### E-SP3续评（预载） / C-TV-LIVE-TIMESHIFT（两个独立暂缓项） — 7. 下一集预载seek隔离与直播timeshift（分开的暂缓能力）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：分别暂缓，不建立合一控制器任务
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`6a5d4b2e6176df819e8fa9b03b81d9ea23daa75f`](https://github.com/FongMi/TV/commit/6a5d4b2e6176df819e8fa9b03b81d9ea23daa75f)
  - FongMi/TV [`c2e3ca0e89ccdc2b2c288c5f89aa16ba804c6678`](https://github.com/FongMi/TV/commit/c2e3ca0e89ccdc2b2c288c5f89aa16ba804c6678)
  - FongMi/TV [`23fbfdfc01e8c003eca396f60f6916e354803307`](https://github.com/FongMi/TV/commit/23fbfdfc01e8c003eca396f60f6916e354803307)
  - FongMi/TV [`43bf0b0f1d4673a81c815d72df50e547bb4bc4e6`](https://github.com/FongMi/TV/commit/43bf0b0f1d4673a81c815d72df50e547bb4bc4e6)
  - FongMi/TV [`939747b3f174aef9f9b91e6b7b1a71117104eed2`](https://github.com/FongMi/TV/commit/939747b3f174aef9f9b91e6b7b1a71117104eed2)
- 当前项目已有实现 / 具体本地证据：local无VodPreloader/TimeshiftPlayback；PreCache:51/310 generation+当前media seek取消现有磁盘缓存；LiveApi:53-57已有catchup与rtsp_range，不等价新滑动窗口timeshift；上游99只onPlaybackSeek保持下一集preloader，94 pending==future忽略过期response
- 要实现的实际能力 / 收益：下一集首播更快与直播拖回均是潜在新能力；网络竞争/额外license流量/包体/CPU未测。
- 缺点与风险 / 与现有功能的关系：当前媒体range预缓存与下一集预载不可混淆，不能让现缓存取消机制失效；DRM预载需会话/许可配额/过期/cancel设计；timeshift需服务端契约、鉴权、窗口移动、原时间轴去广、切台取消
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：下一集独立缓存键(source/flag/episode/request)与生命周期，不重启当前PreCache、不改变现seek保障；直播另建最小Timeshift adapter保留catchup、pending-future身份与return-live。先协议fixture及产品批准再设各自task/flag，分别回滚。
- 预计需要的验证 / 未完成门槛：next preload中seek/换源/换集/cancel/DRM失败与license过期；timeshift窗口边界/直播端点/401/过期回调/切台/seek合并；主播放网络/内存优先级与缓存字节计数A/B。

### C-TV-MOTION-INTERPOLATION — 8. 新运动插帧（Exo→MPV）
- 所属审查分类：player（实际Exo/MPV/common按适配所有权分阶段）
- 建议：明确暂缓两播放器新链（不是已有通用MPV interpolation）
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`04ab460fa5ae78f480b610b8ef6f91d77e65be6d`](https://github.com/FongMi/TV/commit/04ab460fa5ae78f480b610b8ef6f91d77e65be6d)
- 当前项目已有实现 / 具体本地证据：MpvPlayerEngine.java:1014已有interpolation yes/no，MpvPerformanceSetting.java:157-163；非setMotionInterpolationFps/光流；上游ExoUtil把RenderersFactory继承改FfmpegMotionRenderersFactory，motionFps默认60与480处理参数；本地缺Motion类证据；local ASS/DV/FEL/手动解码/ExoAudio策略不得整factory覆写；local media e3e922d5c01bc0b564849940fe589daf37360d15，MPV cca559b41ceb0bb7731cf6ef2e1f33276cd30c42
- 要实现的实际能力 / 收益：低帧率运动更平滑为预期，不保证画质/性能；CPU/GPU/内存/功耗/包体均未测。
- 缺点与风险 / 与现有功能的关系：Exo FFmpegMotion链可能强制软件解码/缩放，损失画质和吞吐；MPV新motion需对应native API与GPU-next/Vulkan，不是旧通用插值等效；HDR/DV/FEL/libass双字幕/LUT/直通音频及A/V sync不可退化；关联media/nextlib/mpv/libplacebo新motion准确全hash与两ABI制品缺证据
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：不改本轮locks或二进制。先冻结源→fork→patch→AAR/so全hash/许可，明确新motion禁用默认及设备能力白名单；Exo/MPV分别可回滚构建链，保留当前通用插值选项及所有renderer保障。
- 预计需要的验证 / 未完成门槛：两ARM API/ABI/ELF/符号与APK可达性；24/25/30fps SDR/HDR/DV/FEL、硬/软、字幕/LUT、seek/切轨/退出，重复测帧、A/V sync、画质伪影、CPU/GPU/内存/发热及包体；无数据绝不声称性能改善。

### C-TV-PYTHON-CLEANUP — 失败初始化回收与可重试错误（优先）
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`123cc086159b0f13bf54bdf64bacbc259ca47c88`](https://github.com/FongMi/TV/commit/123cc086159b0f13bf54bdf64bacbc259ca47c88)
- 当前项目已有实现 / 具体本地证据：PyLoader.java:38-49；JsLoader.java:39-51；chaquo/Spider.java:107-112；chaquo/app.py:7-12,105-106
- 要实现的实际能力 / 收益：避免失败实例和JS executor滞留、失败被SpiderNull永久缓存；保留真实缺依赖原因。
- 缺点与风险 / 与现有功能的关系：旧桥无net/streams/runtime成员；强改异常类型会影响调用者。现有Python标准流tee必须保留。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：先只在已创建spider init失败时destroy并把cleanup错误suppressed到原异常；失败不放成功缓存，使用现有Notify/调用者错误合同。Python模块释放须独立按siteKey+source+generation命名并仅清自己的sys.modules，不重启全局Python。兼容/性能/包体：纯Java/Python窄补，无ABI/制品升级；资源改善为推断。回滚同一App/bridge补丁。
- 预计需要的验证 / 未完成门槛：失败factory、失败init、destroy二次失败、重试成功、A/B同名模块互不污染、标准流日志保留；不先构建整个runtime。

### C-TV-LOADER-OWNERSHIP — 共享loader owner世代、取消和清理隔离（优先）
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`8b499e0972b93f6173eac0e0414485c6b894294a`](https://github.com/FongMi/TV/commit/8b499e0972b93f6173eac0e0414485c6b894294a)
- 当前项目已有实现 / 具体本地证据：BaseLoader.java:24-26,50-55；JarLoader.java:45-52；JsLoader.java:24-28,39-51；PyLoader.java:24-28,38-49；Module.java:11-32
- 要实现的实际能力 / 收益：旧clear不清掉新配置实例；不同key初始化不持全局metadata锁；迟到结果不再复活。
- 缺点与风险 / 与现有功能的关系：只交换owner而旧JsLoader.clear还全局Module.get().clear会逐出新缓存；任务cancel不保证脚本立即终止；Python初始化是进程级单例。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：保留ConcurrentHashMap已有single-key保障，按最终SpiderLoader不变量加按key共享pending、短元数据锁、retire/世代发布、独立失败回收和aggregate cleanup；交换所有旧owner再异步清理，旧缓存snapshot/session仅由旧owner释放。保留activePluginKeys、ext normalize、Jar签名/下载及日志。兼容/包体无新增runtime；并行/内存收益仅推断，验证不再死锁后才选默认。回滚App loader小组。
- 预计需要的验证 / 未完成门槛：同key并发只初始化一次；异key互不阻塞；clear与init各交错、取消后迟到发布、一个destroy抛错仍清全部、代理recent及siteKey正确、新owner缓存不被旧clear逐出。

### C-TV-PROXY-RESPONSE — QuickJS/Python响应桥窄规范化
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`f1aa53e2526307fa67930dd1d0efb6fc3e4732a9`](https://github.com/FongMi/TV/commit/f1aa53e2526307fa67930dd1d0efb6fc3e4732a9)
- 当前项目已有实现 / 具体本地证据：quickjs/Spider.java:208-244；Res.java:48-61；chaquo/Spider.java:89-98,114-129
- 要实现的实际能力 / 收益：补QuickJS catvod分支遗漏响应头，避免null body崩溃；统一错误输入而不破坏旧站源。
- 缺点与风险 / 与现有功能的关系：本地第五项是base64，直接采用新协议会破坏兼容；JSON.stringify不等于保真原始TypedArray；$tvStream属于新runtime不能伪造可用。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：先保留3/4/5项旧协议与data:base64、Python bytes，显式规范code/MIME/null/body和安全headers，proxy2返回第4项；对不支持body类型拒绝而非toString损坏。raw TypedArray/stream-token暂缓，等待当前SDK能力及字节fixture证据。兼容/性能/包体：无native变化，小范围分配差异未实测；回滚桥方法。
- 预计需要的验证 / 未完成门槛：null/空体、字节0/127/128/255、非UTF8、base64/data URI、3/4/5项、畸形status/header、Content-Type/Location/Range响应头、失效body只报受控错误；正式Release同fixture。

### C-TV-FILE-SAFETY（404子项） — 不存在本地源返回404
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：实施
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1`](https://github.com/FongMi/TV/commit/0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1)
- 当前项目已有实现 / 具体本地证据：Local.java:47-55；Nano.java:68-73
- 要实现的实际能力 / 收益：区分源缺失与服务器错误，帮助缺依赖处理及客户端正确报错。
- 缺点与风险 / 与现有功能的关系：不能把权限/I/O异常都隐藏为404；不得改变现有路径授权和Range。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：仅file既非目录也非文件的分支返回Nano.error(Status.NOT_FOUND,"File not found")；其余异常保持，HTTP语义小补，无ABI、性能或包体实质影响（未实测）；可单方法回滚。
- 预计需要的验证 / 未完成门槛：/file存在文本/二进制、缺文件404、目录、权限/I/O失败、Range/ETag/Content-Disposition不变。

### C-TV-WEB-TOOLS — e3f5d38网页遥控/爬虫工具/断点上传行为簇
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：暂缓
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`e3f5d38d2aa9373a17d0842e34d0a7748b56a3e9`](https://github.com/FongMi/TV/commit/e3f5d38d2aa9373a17d0842e34d0a7748b56a3e9)
  - FongMi/TV [`a906c788eb2ed9aacbb430ae123a5dad73b06a36`](https://github.com/FongMi/TV/commit/a906c788eb2ed9aacbb430ae123a5dad73b06a36)
  - FongMi/TV [`91f8f16a8bbd5e32f2e2bd31467035b834f45eba`](https://github.com/FongMi/TV/commit/91f8f16a8bbd5e32f2e2bd31467035b834f45eba)
  - FongMi/TV [`e5ec0c60f53c215acda2d0d66bb1bc78209e8dcb`](https://github.com/FongMi/TV/commit/e5ec0c60f53c215acda2d0d66bb1bc78209e8dcb)
- 当前项目已有实现 / 具体本地证据：Nano.java:42-58；DebugLogs.java:77-98；docs/AV-DIAG-01-playback-diagnostics.md:807-839,853-871；spider.html/SpiderTest.java无本地对应。
- 要实现的实际能力 / 收益：新站源测试/源码版本保存、上传续传和网页遥控有开发便利；TXT诊断已本地覆盖。
- 缺点与风险 / 与现有功能的关系：最终SpiderTest.requireOrigin允许无Origin；会话令牌不是App用户授权，远程加载脚本/保存文件扩大攻击面；新前端/编辑格式器WASM包体、现代浏览器兼容和性能未测。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：不整体合入；诊断/TXT忽略覆盖项，遥控/Browser/上传/工作台各成独立可回滚需求。工作台实施前须WebHTV显式授权、严格本机Host+必需Origin、路径canonical边界、会话/任务/请求大小限额，保留脱敏和现有管理/远程托管合同。缺门禁即暂缓，不原样上线。
- 预计需要的验证 / 未完成门槛：若获批：无Origin/伪Host/跨站/未授权拒绝；路径逃逸、8会话/2MiB/超时/取消、重复上传/断网/错sha、保存冲突、旧日志导出/移动TV页面和包内资产差分。

### C-TV-RUNTIME-MODULES — Python native依赖、WASM和模块APK外置链
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：暂缓
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`99be430d54d848a9ad996648f57bbb4459edb682`](https://github.com/FongMi/TV/commit/99be430d54d848a9ad996648f57bbb4459edb682)
  - FongMi/TV [`2e2196de0efc8a54ab91ef22389daa6a0c024ba4`](https://github.com/FongMi/TV/commit/2e2196de0efc8a54ab91ef22389daa6a0c024ba4)
  - FongMi/TV [`d73a3f44a1e772bde2024cdd8bc88d7193fd18e7`](https://github.com/FongMi/TV/commit/d73a3f44a1e772bde2024cdd8bc88d7193fd18e7)
  - FongMi/TV [`dbb0f5b30aa134c12a506470f2816da1a79d40a6`](https://github.com/FongMi/TV/commit/dbb0f5b30aa134c12a506470f2816da1a79d40a6)
  - FongMi/TV [`c62c10385ea969754c89bd40a8c679b5c92bd40d`](https://github.com/FongMi/TV/commit/c62c10385ea969754c89bd40a8c679b5c92bd40d)
  - FongMi/TV [`18fba7b66b1082fd135f9881ae7aa8bedbaa1e67`](https://github.com/FongMi/TV/commit/18fba7b66b1082fd135f9881ae7aa8bedbaa1e67)
- 当前项目已有实现 / 具体本地证据：app/build.gradle:163-164；chaquo/build.gradle:14-32；chaquo/requirements.txt:1-7；quickjs/build.gradle:25-26；libs.versions.toml:3,51；无chaquo/runtime及RuntimeInstaller。关联精确生产提交：python-android@4cabf1d1bce37d1fc517d939c54b8087af8f9cd3；quickjs-wrapper@f7d4266f3fd8020508e41dc2eb00606c9af49e3f及b0cca8be5cd90a1c6baddf18eda3b0ee7fe4ba20，最后锁QuickJS@71da48ec49e6163d130f7f9001258959e3a319c2、Chicory@6bc7cf8bb6c1a8284d8346c32ea32a15c526d096。
- 要实现的实际能力 / 收益：特定Python cryptography/pywasm站源能力、原生制品可追溯校验、可选独立更新；不是所有站源都需要。
- 缺点与风险 / 与现有功能的关系：Python/SDK/JNI/Chicory成套API与模块类加载/签名/进程重启、libc++所有权、双ABI；pywasm1.0.8增加NumPy/OpenBLAS/Fortran体积；上游148host126/127且设备未验证。上游二进制/CI宣称不是本机验证。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：保持现有3.2.3与Python3.10；先取明确失败站源判断最小依赖，不整支升级。真有刚需才锁生产hash+manifest SHA256，独立双ABI制品、许可证/ELF/DT_NEEDED与同C++校验。ARM64需16KiB、ARM32按4KiB，不强套64位页要求。新模块必须包名/签名/版本/ABI/用户确认及重启原子边界；安装流不强塞现有Updater。QuickJS SDK/JNI保持成对。包体无法由AAR差量等同APK，性能推断不宣称。
- 预计需要的验证 / 未完成门槛：获批后针对实际缺依赖样本；双ABI python桥/ssl/cryptography/NumPy/WASM import、16KiB设备加载、JNI导出与SDK配对、模块安装取消/重建/重启/签名替换/降级拒绝，最终APK容量与CPU/内存对照；本轮不构建。

### C-TV-FOLDER-FILTER — 目录导航后的分类筛选
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：暂缓
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`9c845a913337b2d994343b7885159c0a84681b93`](https://github.com/FongMi/TV/commit/9c845a913337b2d994343b7885159c0a84681b93)
- 当前项目已有实现 / 具体本地证据：leanback FolderFragment.java:70-81；mobile FolderFragment.java:74-96；真实patch筛选前popBackStackImmediate全清。
- 要实现的实际能力 / 收益：使分类级筛选回到根类别而不施加到错误子目录。
- 缺点与风险 / 与现有功能的关系：强制全清目录位置是可见交互变更，不等于无条件修复；TV焦点/手机返回栈可能回归。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：仅明确分类级筛选时root-rebase，目录内筛选保留当前child；保持排序/默认筛选/焦点，需产品合同确认后小补。无依赖或包体变化；滚动性能未测。
- 预计需要的验证 / 未完成门槛：进入多级目录、开/关筛选、设置分类/目录筛选、返回、刷新、TV焦点、手机旋转恢复及自定义网页分类。

### 忽略：无需新任务 — QuickJS Res shrinking显式keep
- 所属审查分类：common（实际Exo/MPV/common按适配所有权分阶段）
- 建议：忽略
- 涉及仓库及完整 commit ID：
  - FongMi/TV [`93ba4b7d36b2021b6cd4db4319a9dc46988a70c4`](https://github.com/FongMi/TV/commit/93ba4b7d36b2021b6cd4db4319a9dc46988a70c4)
- 当前项目已有实现 / 具体本地证据：Res.java:19-29；libs.versions.toml:32 Gson2.14.0；官方gson-parent-2.14.0/gson/src/main/resources/META-INF/proguard/gson.pro已keep SerializedName字段+无参构造；无现有Release mapping。
- 要实现的实际能力 / 收益：仅额外保险，不是已证实缺陷；当前随库规则已覆盖目标。
- 缺点与风险 / 与现有功能的关系：盲keep整个quickjs包会抑制优化并把新版缺陷误当本地缺陷。
- 最小实施步骤 / 适配 / 兼容性能包体 / 回滚：不加整包keep；未来正式Release fixture若证明merged rules未入或字段仍移除，才给Res精确规则或TypeAdapter，保留当前SDK。无本轮变更。
- 预计需要的验证 / 未完成门槛：未来正式R8 Release的merged configuration/mapping及Res code/content/buffer/headers fixture；fastRelease不算shrinking验证。

## 4. 全部154条当前侧提交处置台账

处置术语：partial = 部分覆盖、按所列差异窄适配；covered = 本地已覆盖、忽略重复实现；candidate = 候选、仍需设计与实施授权；maintenance = 维护项、无独立功能收益；not-applicable = 当前架构不适用。其余中文处置同样是评估建议，不代表功能已实施。

candidate/partial只表示候选或部分覆盖；covered不表示所有设备情形已实测；needs-evidence不作为可直接合入。混合提交中的维护/已覆盖部分仍保留处置。

| # | 完整TV提交ID | 主题 | 处置 | 本地/上游证据 | 理由 |
| ---: | --- | --- | --- | --- | --- |
| 1 | [`b802f538cb25bab7252bc170279101b6e409cec4`](https://github.com/FongMi/TV/commit/b802f538cb25bab7252bc170279101b6e409cec4) | Redesign playback settings and player effects | partial | app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:731–768,792–816; player/engine/ExoPlayerEngine.java:165; utils/FileUtil.java:69–76; 新旧raw差21项；https://github.com/FongMi/TV/commit/b802f538cb25bab7252bc170279101b6e409cec4 | 重写旧e25b4ff87990fd595aed1228835d8b5e96f7d00a；音画effects下沉Media3 AudioEffectParameters等API，不是仅删功能；本地已有LUT/原生ASS/双字幕，但未等价覆盖完整EQ设置重构。巨型163文件不整支合，唯Zip canonical安全不变量拆入安全候选，其余暂缓依赖链评审。 |
| 2 | [`179cd159266121932ab570b5188b1f9fccf65461`](https://github.com/FongMi/TV/commit/179cd159266121932ab570b5188b1f9fccf65461) | Refine subtitle, danmaku, and playback integration | partial | app/src/main/java/com/fongmi/android/tv/utils/FileUtil.java:69–80; ui/dialog/SubtitleDialog.java:80–148; 上游FileUtil实际patch新增归档条目/总字节限制；https://github.com/FongMi/TV/commit/179cd159266121932ab570b5188b1f9fccf65461 | 重写旧cf1096325a1e362551a9c6b18c284d7ad88edd71，raw差10项；字幕搜索/归档与effects委托不能当全新简单补丁。安全边界可窄移植；本地缺上游SubtitleApi/SubtitleSearchDialog，勿称搜索已覆盖；完整搜索暂缓产品及接口门槛。 |
| 3 | [`8146efc17c91c4f75b1b8db36fbc11ceaf93b1a7`](https://github.com/FongMi/TV/commit/8146efc17c91c4f75b1b8db36fbc11ceaf93b1a7) | Serialize and restore playback history | partial | app/src/main/java/com/fongmi/android/tv/bean/History.java:338–418; playback/PlaybackProgressWriter.java:58–77,130–149; 上游History真实patch；https://github.com/FongMi/TV/commit/8146efc17c91c4f75b1b8db36fbc11ceaf93b1a7 | 与旧2841ddfc9d58293987b935ffb7b8e98f9255b5b1的完整raw delta相同，是重放但本地非全面覆盖。上游串行存储/历史snapshot/按cid删除可参考；现有同步墓碑和跨源历史合并更复杂，不能替换History/Task。进入运行状态候选的适配研究，不改现有键格式。 |
| 4 | [`d9a30c51c3a3ed117d0f5c6d8ab119df75a0ac9d`](https://github.com/FongMi/TV/commit/d9a30c51c3a3ed117d0f5c6d8ab119df75a0ac9d) | Adjust Gradle parallel configuration | maintenance | gradle.properties:1–3 当前Xmx4g和org.gradle.parallel=true；patch改2048m及org.gradle.tooling.parallel=true；https://github.com/FongMi/TV/commit/d9a30c51c3a3ed117d0f5c6d8ab119df75a0ac9d | 与旧29def13025870164416ff7e374850095bd2bba9a raw相同，重放；不是用户播放收益；内存降低及非原parallel键不盲跟，本地保持，忽略。 |
| 5 | [`97afc7891181d1799fadcd19635b3edd893c4e50`](https://github.com/FongMi/TV/commit/97afc7891181d1799fadcd19635b3edd893c4e50) | Fix Leanback QR server address | covered | app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java:105; DanmakuApiDialog.java:60; UaDialog.java:63；https://github.com/FongMi/TV/commit/97afc7891181d1799fadcd19635b3edd893c4e50 | 与旧b8eed44d26458bb0ae1dc2722f833d81750aa356 raw相同；本地二维码均使用getAddress(4)，且本地MPV配置UI已改名，旧MpvConfDialog不存在。不重建旧UI，忽略。 |
| 6 | [`1575af876a0a4d05fce105d75c3cf357500cc844`](https://github.com/FongMi/TV/commit/1575af876a0a4d05fce105d75c3cf357500cc844) | Update playback debug integration | covered | docs/AV-DIAG-01-playback-diagnostics.md:13–23; app/src/main/java/com/fongmi/android/tv/player/exo/ExoDiagnosticCollector.java:181–184；https://github.com/FongMi/TV/commit/1575af876a0a4d05fce105d75c3cf357500cc844 | 与旧188babeca47d72df4ef21e48c13f5cf89eca2bba raw相同；上游只是DebugTextViewHelper混淆及删hideDebugView，本地完整结构化诊断链且不靠该helper，非新能力，忽略而非套keep规则。 |
| 7 | [`9faef693ab308c5b0b4bdde3af8c053a2e726e2a`](https://github.com/FongMi/TV/commit/9faef693ab308c5b0b4bdde3af8c053a2e726e2a) | Add cookie parsing for live playlists | covered | app/src/main/java/com/fongmi/android/tv/api/parser/LiveParser.java:199,281；https://github.com/FongMi/TV/commit/9faef693ab308c5b0b4bdde3af8c053a2e726e2a | 与旧28f504c491f902fbed1cff31d76d289b6e80de26 raw相同；本地同样识别#EXTVLCOPT:http-cookie并写Cookie，真实等价，忽略。 |
| 8 | [`f803adce0bfffebd05a2115a126aa3334ae99a11`](https://github.com/FongMi/TV/commit/f803adce0bfffebd05a2115a126aa3334ae99a11) | Refactor live and VOD playback coordination | partial | app/src/main/java/com/fongmi/android/tv/model/LiveViewModel.java:90–95; ui/activity/PlaybackActivity.java:854–869; app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1205–1236；https://github.com/FongMi/TV/commit/f803adce0bfffebd05a2115a126aa3334ae99a11 | 与旧6bbc6103c9ac9fea8cd029ca6932d6fac2827e4e raw相同；本地无上游VodPlaybackController/LivePlaybackController体系，仍本地Activity/模型分工。请求身份/状态机可作运行状态候选研究，不以47文件重構整支替换本地disc、性能、同步策略。 |
| 9 | [`1f37b800b0ca53111185379149779a6b310aff77`](https://github.com/FongMi/TV/commit/1f37b800b0ca53111185379149779a6b310aff77) | Defer DNS-over-HTTPS initialization | covered | app/src/main/java/com/fongmi/android/tv/Startup.java:34; catvod/src/main/java/com/github/catvod/net/OkDns.java:24–39,58–66；https://github.com/FongMi/TV/commit/1f37b800b0ca53111185379149779a6b310aff77 | 与旧5ca4d2ba5ed0f2069f1524583e4261a051c40a2c raw相同；本地Supplier惰性init及synchronized/volatile均等价存在，忽略。 |
| 10 | [`46002df7f743348e4487dc18d47086e49e2a8166`](https://github.com/FongMi/TV/commit/46002df7f743348e4487dc18d47086e49e2a8166) | Update Android build dependencies and app version | partial | gradle/libs.versions.toml:1–49; app/proguard-rules-media.pro:59–80; third_party/media-lock.json; docs/AV-DIAG-01-playback-diagnostics.md:30–32；https://github.com/FongMi/TV/commit/46002df7f743348e4487dc18d47086e49e2a8166 | 与旧d234010b13365a57a9a0cc8c2fe8198bc5e0dac1 raw相同；本地保留AGP9.2.1/Glide5.0.7/NewPipe0.26.3但自有Media3/nextlib和MPV源码/产物。版本及不同FFmpeg构造keep签名不可套合；通用依赖升级作为maintenance暂缓，播放器混淆按当前真实AAR适配。 |
| 11 | [`dfcf83c71726cc75cce5c71bd7c3111d77734702`](https://github.com/FongMi/TV/commit/dfcf83c71726cc75cce5c71bd7c3111d77734702) | Fix playback end-state handling | covered | app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java:324–327; leanback/ui/activity/LiveActivity.java:570–580; mobile/ui/activity/VideoActivity.java:5800–5815；https://github.com/FongMi/TV/commit/dfcf83c71726cc75cce5c71bd7c3111d77734702 | 与旧42b3824caf1cd9271aad03aa180398a0cbb41d2a raw相同；seek至片尾不再play，结束按钮状态已有，手机PiP另有本地audioMode政策。忽略重放，保留本地差异。 |
| 12 | [`9c61c1b4562a96b21f069074b36e4ed6e5b6ae49`](https://github.com/FongMi/TV/commit/9c61c1b4562a96b21f069074b36e4ed6e5b6ae49) | Preserve playback dispatch across lifecycle | candidate | app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java:854–869,880–913; 上游canActivate/canDispatch/ServiceReadyObserver补丁；https://github.com/FongMi/TV/commit/9c61c1b4562a96b21f069074b36e4ed6e5b6ae49 | 与旧bcb11b230586e7c509199c75f94cd100053b84e0 raw相同但本地未采用等价dispatch门；本地service连接立即claim且callback，无STARTED检查。适配候选：先用service延迟连接/切页复现并引入本地owner+generation闸门；不得原样移植observeForever和停播条件，保持音频后台、PiP、ASS生命周期。 |
| 13 | [`311bae2fc6739aedd63eaf930b4ebca9ad7a4bdc`](https://github.com/FongMi/TV/commit/311bae2fc6739aedd63eaf930b4ebca9ad7a4bdc) | Retry disguised HLS streams in mpv | covered | app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java:882–906; PlayerManager.java:7710；https://github.com/FongMi/TV/commit/311bae2fc6739aedd63eaf930b4ebca9ad7a4bdc | 与旧954299e20fad2f1a131ac4410770155a5b118b75 raw相同；本地已有一次retryFormat为M3U8并保留position，对terminal错误有更窄排除；不能恢复上游所有IO_UNSPECIFIED的广泛重试或删除本地HLS连接策略，忽略。 |
| 14 | [`095f12f3a545114d4d83bf200651464371e19fc5`](https://github.com/FongMi/TV/commit/095f12f3a545114d4d83bf200651464371e19fc5) | Update Chrome user agent | covered | catvod/src/main/java/com/github/catvod/utils/Util.java:28；https://github.com/FongMi/TV/commit/095f12f3a545114d4d83bf200651464371e19fc5 | 与旧dd696dadcfa90a39f2e627e6f7048e51891768d8 raw相同；本地CHROME151.0.0.0完全一致，忽略。 |
| 15 | [`99726597a31725b17da12eec7507591fd89a1615`](https://github.com/FongMi/TV/commit/99726597a31725b17da12eec7507591fd89a1615) | Apply mpv GPU settings explicitly | covered | app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java:960–976; is/xyz/mpv/MPVLib.java:150；https://github.com/FongMi/TV/commit/99726597a31725b17da12eec7507591fd89a1615 | 与旧ae4cf04616a1d8480ee719e0f95862e104c13e01 raw相同；本地显式计算requestVulkan/nativeVulkan/deviceVulkan/useVulkan并维持direct/backend策略。不是缺少true/false应用能力，忽略。 |
| 16 | [`403c67730145a5fb66d279636bd7499c67438836`](https://github.com/FongMi/TV/commit/403c67730145a5fb66d279636bd7499c67438836) | Bump app version to 5.5.9 | maintenance | app/build.gradle；patch仅versionCode558→559和5.5.8→5.5.9；https://github.com/FongMi/TV/commit/403c67730145a5fb66d279636bd7499c67438836 | 与旧0256f71795c0dc39943d82f2a97ac81aa79b81c6 raw相同；上游发行号不适用于本地产品发布，忽略。 |
| 17 | [`46866be1752d78c40702e37a52d4627cc875bc50`](https://github.com/FongMi/TV/commit/46866be1752d78c40702e37a52d4627cc875bc50) | Handle task backgrounding on legacy TV firmware | covered | app/src/main/java/com/fongmi/android/tv/utils/Util.java:44–51；https://github.com/FongMi/TV/commit/46866be1752d78c40702e37a52d4627cc875bc50 | 与旧b04c63ce61f7ca40226b14ecc944ddd3ad63215f raw相同；本地moveToBackground已有moveTaskToBack及旧固件NPE回HOME，忽略。 |
| 18 | [`c44309d7cc321896c5745bd5180fbd436b06778f`](https://github.com/FongMi/TV/commit/c44309d7cc321896c5745bd5180fbd436b06778f) | Handle invalid DNS-over-HTTPS URLs | covered | catvod/src/main/java/com/github/catvod/net/OkDns.java:32–35；https://github.com/FongMi/TV/commit/c44309d7cc321896c5745bd5180fbd436b06778f | 与旧a7bd92541f287c5ba12a1c36d31e8b6229941e32 raw相同；本地HttpUrl.parse及url null退系统DNS清supplier完全等价，忽略。 |
| 19 | [`610174d05d81bc108710ab99b2de8dc29fe6179b`](https://github.com/FongMi/TV/commit/610174d05d81bc108710ab99b2de8dc29fe6179b) | Update build.gradle | maintenance | app/build.gradle；patch仅559→560/5.5.9→5.6.0；https://github.com/FongMi/TV/commit/610174d05d81bc108710ab99b2de8dc29fe6179b | 与旧21624010da8a9de2fbb064bb28b34ed274fe926b raw相同；发行版本号不导入，忽略。 |
| 20 | [`b431323bcf1f4ea08bdddf54495dcef9dbdeb3ac`](https://github.com/FongMi/TV/commit/b431323bcf1f4ea08bdddf54495dcef9dbdeb3ac) | Clean code | not-applicable | 上游bean/AssrtResponse.java真实patch仅删errorFrom；本地该class与SubtitleApi均不存在；https://github.com/FongMi/TV/commit/b431323bcf1f4ea08bdddf54495dcef9dbdeb3ac | 与旧1a19fee278fa2234da725d61a53bf59b69fe9127 raw相同；不存在对应consumer，无新增能力，忽略。 |
| 21 | [`a767cb510e73f4263a4f8248c3e82e6bb6b652f6`](https://github.com/FongMi/TV/commit/a767cb510e73f4263a4f8248c3e82e6bb6b652f6) | Guard Vulkan settings on unsupported devices | covered | app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java:960–976; ui/dialog/PlaybackPerformanceDialog.java:958–959; is/xyz/mpv/MPVLib.java:150；https://github.com/FongMi/TV/commit/a767cb510e73f4263a4f8248c3e82e6bb6b652f6 | 本地已具device Vulkan1.3与bundled native能力交集和UI可用性，不应回退上游简单1.2 PackageManager单门。保留本地direct/stable/legacy/generic路径，忽略。 |
| 22 | [`753b66fc0e390b27e7a49c7553d4dda5e3831054`](https://github.com/FongMi/TV/commit/753b66fc0e390b27e7a49c7553d4dda5e3831054) | Ignore invalid external subtitle entries | candidate | app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java:923–934; PlayerManager.java:878–881; bean/Sub.java:46–60；https://github.com/FongMi/TV/commit/753b66fc0e390b27e7a49c7553d4dda5e3831054 | 上游null/空Sub预过滤有本地真实缺口：本地列表直接buildSubConfig且setSub无预检，可能异常或无效URI。窄适配并保存原始flag，不能套其SubtitleFlags全量政策。与35合为外挂字幕配置候选。 |
| 23 | [`11654b066e196c0242d6601c0b289455d3da4428`](https://github.com/FongMi/TV/commit/11654b066e196c0242d6601c0b289455d3da4428) | Bump app version to 5.6.1 | maintenance | app/build.gradle；patch仅560→561/5.6.0→5.6.1；https://github.com/FongMi/TV/commit/11654b066e196c0242d6601c0b289455d3da4428 | 发行号维护无播放收益，忽略。 |
| 24 | [`a585e34e0fe40e2aec3a5792a33493ce5cc9e729`](https://github.com/FongMi/TV/commit/a585e34e0fe40e2aec3a5792a33493ce5cc9e729) | Update Android build dependencies | maintenance | gradle/libs.versions.toml:1–49; gradle/wrapper/gradle-wrapper.properties；patch更新AppCompat/Guava/OkHttp等并新增CommonsCompress；https://github.com/FongMi/TV/commit/a585e34e0fe40e2aec3a5792a33493ce5cc9e729 | 不等于播放器能力；本地依赖和插件/ABI链自有，暂缓批量升级。CommonsCompress只在决定接受25的新打包插件时需要，不为日志新版本添加依赖。 |
| 25 | [`19ce83912cacf43bfe5d144c44721418ce3280e8`](https://github.com/FongMi/TV/commit/19ce83912cacf43bfe5d144c44721418ce3280e8) | Package release APKs by ABI | not-applicable | app/build.gradle:63–92,151–154; chaquo/build.gradle；上游buildSrc/FinalizeApksTask.groovy真实patch；https://github.com/FongMi/TV/commit/19ce83912cacf43bfe5d144c44721418ce3280e8 | 本地已有mode+abi产品flavor隔离与独立ASS JNI，整改为device+split并解包重签APK改变任务名、资产裁剪和签名供应链。现有需求无必要，不接受整包；44可单独评审，不绑定此重构。若重启研究必须双ABI资产、16KiB对齐、签名及更新目标门槛。 |
| 26 | [`4c7d02c14ea3da44c79b7d60a87784bc23390263`](https://github.com/FongMi/TV/commit/4c7d02c14ea3da44c79b7d60a87784bc23390263) | Centralize cryptographic utilities | maintenance | catvod/src/main/java/com/github/catvod/utils/Util.java:61–81; quickjs/src/main/java/com/fongmi/quickjs/method/Global.java:191–208; 上游Crypto.java真实patch；https://github.com/FongMi/TV/commit/4c7d02c14ea3da44c79b7d60a87784bc23390263 | 上游集中MD5/AES/DES/RSA及padding到catvod Crypto，主要维护重构；本地quickjs已有Crypto，网络认证/Jar校验保持现行协议。无确认功能缺陷，不一并重写；若未来需要仅移植测试锁定的功能，MD5不能提升为安全来源验证。 |
| 27 | [`5a905f87404ec803239edd3c3d6abce6a554f6a8`](https://github.com/FongMi/TV/commit/5a905f87404ec803239edd3c3d6abce6a554f6a8) | Harden filesystem operations | candidate | app/src/main/java/com/fongmi/android/tv/utils/FileUtil.java:47–80,97–135; catvod/src/main/java/com/github/catvod/utils/Path.java:189–209; 上游FileUtil/Path真实patch；https://github.com/FongMi/TV/commit/5a905f87404ec803239edd3c3d6abce6a554f6a8 | 原子复制temp/fsync/rename、显式异常是缺口；但上游同时删除本地storage统计入口并以无溢出保护Path.size替换，不能原样合。安全候选只加原子API，保留本地StorageSpace/溢出饱和/缓存总量政策；Zip边界源于1/2而非本提交首次实现。 |
| 28 | [`d930258e06c85722369442bfb4f3053ad554b159`](https://github.com/FongMi/TV/commit/d930258e06c85722369442bfb4f3053ad554b159) | Separate and migrate database backups | partial | app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java:50–98; utils/AppBackup.java:53–60,96–136,199–237；https://github.com/FongMi/TV/commit/d930258e06c85722369442bfb4f3053ad554b159 | 本地已有AppBackup完整ZIP备份/manifest/共享配置/登录资料和旧tv-bk.gz兼容，行为不等价但更广。上游7份保留和backup目录迁移可研究，不导入新.tv格式或删除本地备份；当前暂缓维护收益，不另造迁移任务。 |
| 29 | [`2ae956ad57b8f60246192e64e2e2af9b0d389b48`](https://github.com/FongMi/TV/commit/2ae956ad57b8f60246192e64e2e2af9b0d389b48) | Harden local file imports | candidate | app/src/main/java/com/fongmi/android/tv/utils/FileChooser.java:58–111; app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java:240; catvod/utils/Path.java:129–132；https://github.com/FongMi/TV/commit/2ae956ad57b8f60246192e64e2e2af9b0d389b48 | content URI后台materialize/文件名去目录/异常与生命周期保护值得补。本地同步getPathFromUri及URLDecoder解码，且有多文件/目录选择，上游新单URI接口不能覆写。纳安全候选窄适配，导入大小/取消/权限必须设门槛。 |
| 30 | [`c33e010a36a6e72c759abb427dd6d1c3cbe5b707`](https://github.com/FongMi/TV/commit/c33e010a36a6e72c759abb427dd6d1c3cbe5b707) | Harden local file server | candidate | app/src/main/java/com/fongmi/android/tv/server/process/Local.java:34–85,98,107–133; catvod/src/main/java/com/github/catvod/utils/Path.java:37,93–98,129–132；https://github.com/FongMi/TV/commit/c33e010a36a6e72c759abb427dd6d1c3cbe5b707 | Local路径穿越/根删除/静默错误及suffix Range缺口；上游canonical confinement、文件名检查、根删除禁用、Range正确性值得适配。保留本地download参数/Content-Disposition/size及授权体系；上传ZIP仍委托FileUtil，默认未给有限配额，需在适配中补足，不原样合。 |
| 31 | [`94acc63a83ee836e219375ef392fadf004e08f11`](https://github.com/FongMi/TV/commit/94acc63a83ee836e219375ef392fadf004e08f11) | Support direct subtitle downloads | candidate | app/src/main/java/com/fongmi/android/tv/utils/Download.java:61–111,129–138; player/exo/ExoUtil.java:923–934; 本地无SubtitleApi/SubtitleArchive；https://github.com/FongMi/TV/commit/94acc63a83ee836e219375ef392fadf004e08f11 | 上游直下srt/ass而非仅zip，以及大小/空下载/cancel回调抑制有价值。当前Download已有HTTP/长度完整性/详细进度，不能覆盖成上游较少字段或取消后遗留文件。附属字幕候选先接既有setSub与有限下载，不自动建整个ASSRT搜索产品。 |
| 32 | [`1eba5a1aecf6c0a80b7d512a4251ba375bd72380`](https://github.com/FongMi/TV/commit/1eba5a1aecf6c0a80b7d512a4251ba375bd72380) | Preserve playback artwork | candidate | app/src/main/java/com/fongmi/android/tv/utils/ImgUtil.java:109–119; service/PlaybackService.java:117; leanback/ui/activity/VideoActivity.java:414–415; 上游ArtworkBitmapLoader/Image/ImgUtil真实patch；https://github.com/FongMi/TV/commit/1eba5a1aecf6c0a80b7d512a4251ba375bd72380 | 本地Glide可读data和@Headers但MediaSession未配自定义bitmap loader；大data经Intent可能binder超限。候选补带header通知封面和受限data存储。上游内存16MiB缓存URL会随进程/逐出失效，不能把该URL当永久History封面；需适配持久/短期身份与隐私访问门槛。 |
| 33 | [`a4ebdd65560ca207ef78f908168ceca9bfbb0220`](https://github.com/FongMi/TV/commit/a4ebdd65560ca207ef78f908168ceca9bfbb0220) | Decouple live playback data loading | maintenance | app/src/main/java/com/fongmi/android/tv/model/LiveViewModel.java:90–95; 上游LiveDataSource/Controller/Host真实patch；https://github.com/FongMi/TV/commit/a4ebdd65560ca207ef78f908168ceca9bfbb0220 | 仅数据源接口下沉，非新直播能力；本地没有上游controller体系。可用作运行状态候选的界面参考，不为6行接口迁移整层，不单独实施。 |
| 34 | [`2112cb48cf6570818ac578df011925a0a3494369`](https://github.com/FongMi/TV/commit/2112cb48cf6570818ac578df011925a0a3494369) | Extract VOD data loading and preloading | partial | app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1205–1236; app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:182–184; player/mpv/MpvPreloadController.java；上游VodPreloader真实patch；https://github.com/FongMi/TV/commit/2112cb48cf6570818ac578df011925a0a3494369 | 上游抽VodDataSource/VodPreloader并规范请求身份/反序下一集/DRM和parse禁预载；本地有自有Exo/MPV缓存预载，不能退化。暂缓整框架，运行状态候选只验证迟到预载拒绝和媒体身份；不同Player不能互证。 |
| 35 | [`125468f0ec3f448c93626b4a7ef27a03cad21790`](https://github.com/FongMi/TV/commit/125468f0ec3f448c93626b4a7ef27a03cad21790) | Stabilize external subtitle configurations | candidate | app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java:923–934; player/exo/ass/AssInput.java:12–28；https://github.com/FongMi/TV/commit/125468f0ec3f448c93626b4a7ef27a03cad21790 | 上游稳定external:UUID及MIME推断有真实缺口，本地ASS id仅index且其他字幕无id。必须保留AssInput可解析前缀/index与原flag，再新增独立stable identity映射，或同步适配解析器；原样换UUID将破坏现有ASS，禁止裸合。 |
| 36 | [`773cae11bf3b89ef26f7353ce8a534c775984df2`](https://github.com/FongMi/TV/commit/773cae11bf3b89ef26f7353ce8a534c775984df2) | Integrate libass and advanced subtitle controls | partial | app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java:165,253; player/exo/subtitle/ExoSubtitleSession.java; docs/E4-LIBASS-exo-ass-rendering.md:7,17–50,73–90; ui/dialog/SubtitleDialog.java:80–148；https://github.com/FongMi/TV/commit/773cae11bf3b89ef26f7353ce8a534c775984df2 | 原生ASS/HDR独立SDR层/字体附件/双字幕本地已完整，不重做E4。上游LibassPlaybackSession/SecondaryTextTrackSelector是不同Media3 API和二进制链，不能整升级。只有可选外部字体选择和副字幕模式UI可能增补，需在现有E4任务里审查最小差异，非立即合并。 |
| 37 | [`0addfec68454b588c5c77cfbdaaf230714cbece8`](https://github.com/FongMi/TV/commit/0addfec68454b588c5c77cfbdaaf230714cbece8) | Bind subtitle and danmaku overlays to PlayerView | partial | app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java:854–865,879–913,922–924; setting/DanmakuSetting.java:394; player/exo/subtitle/ExoSubtitleSession.java；https://github.com/FongMi/TV/commit/0addfec68454b588c5c77cfbdaaf230714cbece8 | 本地已有前后台弹幕门、ASS Surface/双轨宿主生命周期；上游DanmakuPlayerViewController不是必须新能力。可选弹幕外部字体缺口未全覆盖；沿字幕设置候选只增字体，不替换PlayerView绑定或drop本地LiveDanmaku/诊断实现。 |
| 38 | [`117058ed15fe3addc8e2bbca3038d94c91eb44d7`](https://github.com/FongMi/TV/commit/117058ed15fe3addc8e2bbca3038d94c91eb44d7) | Clarify mpv.conf option precedence | maintenance | app/src/main/java/com/fongmi/android/tv/ui/dialog/MpvConfigEditorDialog.java；本地旧MpvConfDialog不存在；上游MpvConfigFile解析default/profile/conflict真实patch；https://github.com/FongMi/TV/commit/117058ed15fe3addc8e2bbca3038d94c91eb44d7 | 原始界面优先提示/保存导入失败反馈为维护价值，本地已多配置编辑/创建/历史/重命名体系，未证明等价conflict检测。暂缓，将来必须由本地真实option precedence提供managed名单，不引入不匹配的Media3 MpvAndroidOptions API。 |
| 39 | [`055450b06050caa5aeb67d979e49b65dfe7841fb`](https://github.com/FongMi/TV/commit/055450b06050caa5aeb67d979e49b65dfe7841fb) | Move VOD lists to top before leaving | not-applicable | app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java:186–189; 上游FolderFragment/TypeFragment.moveToTop真实patch；https://github.com/FongMi/TV/commit/055450b06050caa5aeb67d979e49b65dfe7841fb | 改变遥控返回为先回顶部再退出，属产品手势而非缺陷修复。当前本地返回先过滤/目录回退，是已存在交互合同；无明确需求不改，忽略。 |
| 40 | [`50710170d42aeedeb6d4cbc0a14f9d779871006e`](https://github.com/FongMi/TV/commit/50710170d42aeedeb6d4cbc0a14f9d779871006e) | Refine playback message placement | not-applicable | app/src/leanback/res/layout/view_widget_live.xml:56,98; view_widget_vod.xml:56,84；上游新shape_widget_message.xml与top margin真实patch；https://github.com/FongMi/TV/commit/50710170d42aeedeb6d4cbc0a14f9d779871006e | 把提示移顶部40dp/16sp纯视觉偏好，可能与OSD/弹幕冲突。无真实播放修复收益，不改变本地布局，忽略。 |
| 41 | [`26f9965e3cb7b6154b589e5b0e73bf6ad6f44178`](https://github.com/FongMi/TV/commit/26f9965e3cb7b6154b589e5b0e73bf6ad6f44178) | Refine playback speed presets | not-applicable | app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:155,1213–1216；https://github.com/FongMi/TV/commit/26f9965e3cb7b6154b589e5b0e73bf6ad6f44178 | 上游删除0.1/2.5/3.5/4倍预设及电视对话宽度；本地已有0.75/1.25/1.75/2.5等更广预设。非能力提升，不能减现有可用选项，忽略。 |
| 42 | [`3f986a90c28ad61c18526be41899a3118490cdab`](https://github.com/FongMi/TV/commit/3f986a90c28ad61c18526be41899a3118490cdab) | Update build.gradle | maintenance | app/build.gradle；patch仅561→562/5.6.1→5.6.2；https://github.com/FongMi/TV/commit/3f986a90c28ad61c18526be41899a3118490cdab | 发行版本号不合入本地，忽略。 |
| 43 | [`441109b9ae1b6bcd2ada1b4c54ded0c991a3e3fe`](https://github.com/FongMi/TV/commit/441109b9ae1b6bcd2ada1b4c54ded0c991a3e3fe) | Refine mpv.conf editor actions | maintenance | app/src/main/java/com/fongmi/android/tv/ui/dialog/MpvConfigEditorDialog.java及MpvConfigCreateDialog/MpvConfigHistoryDialog/MpvConfigRenameDialog；上游真实patch；https://github.com/FongMi/TV/commit/441109b9ae1b6bcd2ada1b4c54ded0c991a3e3fe | 手机导入按钮位置及文本简化；电视移除38刚加conflict hint，是follow-up非正确性修复。不拿本地多配置UI换回旧单文件编辑器，忽略样式；保存可靠性只沿安全API改进。 |
| 44 | [`5c8b9a82de2fe3f8ea912ed42324a31c616c621e`](https://github.com/FongMi/TV/commit/5c8b9a82de2fe3f8ea912ed42324a31c616c621e) | Fix Chaquopy native package ABI selection | candidate | chaquo/src/main/java/com/fongmi/chaquo/Platform.java:43–66; gradle/libs.versions.toml:3; 上游一行AndroidPlatform.ABI=ABI；https://github.com/FongMi/TV/commit/5c8b9a82de2fe3f8ea912ed42324a31c616c621e | 自定义Python.Platform选择ABI但没同步AndroidPlatform.ABI，是原生Python包定位的潜在缺口。推荐独立窄修复研究，不依赖25打包重构；本地17.0内部字段公开性/读取方及v7运行arm64硬件必须先验证，现阶段不称已证实崩溃或可裸合。 |
| 45 | [`809422d36b2a7d68510e7a532130393c2b2677db`](https://github.com/FongMi/TV/commit/809422d36b2a7d68510e7a532130393c2b2677db) | Update build.gradle | maintenance | app/build.gradle；patch仅562→563/5.6.2→5.6.3；https://github.com/FongMi/TV/commit/809422d36b2a7d68510e7a532130393c2b2677db | 发行号维护，忽略。 |
| 46 | [`9ebac08ef3188e2753f1deb0cfceb388d59f5b2f`](https://github.com/FongMi/TV/commit/9ebac08ef3188e2753f1deb0cfceb388d59f5b2f) | Hide font controls for image subtitles | 忽略/需局部UI证据 | SubtitleSettingPanel.updateAppearance:406 隐藏位图字幕字体控件；local无该Panel，不是新字幕解码能力。当前libass与双字幕合同不能覆写。；https://github.com/FongMi/TV/commit/9ebac08ef3188e2753f1deb0cfceb388d59f5b2f | 新上游面板配套UI，不引入整套设置架构。 |
| 47 | [`dbd6d887c1aa6bd4c6a8dc6ec5a7fb5aa05720cf`](https://github.com/FongMi/TV/commit/dbd6d887c1aa6bd4c6a8dc6ec5a7fb5aa05720cf) | Add documentation website and streamline README | 忽略 | 真实patch新增website/GitHub Pages；路径命中来自playback-commands文档。；https://github.com/FongMi/TV/commit/dbd6d887c1aa6bd4c6a8dc6ec5a7fb5aa05720cf | 文档发布不是当前播放器新能力。 |
| 48 | [`2d84dc637908a0ca8e7b86a55da631b143c9cf6b`](https://github.com/FongMi/TV/commit/2d84dc637908a0ca8e7b86a55da631b143c9cf6b) | Fix proxy selection across redirects | 实施候选/窄适配 | 新增ProxyRedirectInterceptor与OkProxySelector.Policy、OkAuthenticator重试；local OkProxySelector.java:63-75已有null-host/localhost保护，无Policy/RedirectInterceptor；OkHttp 5.4.0。；https://github.com/FongMi/TV/commit/2d84dc637908a0ca8e7b86a55da631b143c9cf6b | 跨重定向重新选择代理有价值，但需保留当前fallback/debug/代理认证并与124修复一起适配。 |
| 49 | [`d4948fa8a04586f6c3d0d3cf035dc7f2bdf8c98a`](https://github.com/FongMi/TV/commit/d4948fa8a04586f6c3d0d3cf035dc7f2bdf8c98a) | Document source-provided VOD skip intervals | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/d4948fa8a04586f6c3d0d3cf035dc7f2bdf8c98a.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 50 | [`4ee722a1a4b1376f711674ade21dca7ac4909dd8`](https://github.com/FongMi/TV/commit/4ee722a1a4b1376f711674ade21dca7ac4909dd8) | Document Exo playback command controls | 忽略 | 真实patch只website播放命令说明。；https://github.com/FongMi/TV/commit/4ee722a1a4b1376f711674ade21dca7ac4909dd8 | 文档而非播放器实现，不能当新能力。 |
| 51 | [`966c46fcbecb1d8ec5be07e461db0036a100dd0b`](https://github.com/FongMi/TV/commit/966c46fcbecb1d8ec5be07e461db0036a100dd0b) | Document shared spider APIs | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/966c46fcbecb1d8ec5be07e461db0036a100dd0b.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 52 | [`9e060750f04fa72211017d338c895909abb5f3e5`](https://github.com/FongMi/TV/commit/9e060750f04fa72211017d338c895909abb5f3e5) | Use the build base path for website preview | 忽略/工具不适用 | website=False；真实patch将preview.mjs读site-config改为读.next/routes-manifest.json。；真实patch：https://github.com/FongMi/TV/commit/9e060750f04fa72211017d338c895909abb5f3e5.patch | 修正上游网站预览basePath；当前无该构建/预览链，不合入。 |
| 53 | [`f47af4ae20c69c90351947ba4e76a0310d2779cd`](https://github.com/FongMi/TV/commit/f47af4ae20c69c90351947ba4e76a0310d2779cd) | Correct embedded browser navigation documentation | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/f47af4ae20c69c90351947ba4e76a0310d2779cd.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 54 | [`348dfad46a2a9c0357602e1978ec09c2d2f98175`](https://github.com/FongMi/TV/commit/348dfad46a2a9c0357602e1978ec09c2d2f98175) | Document persistent WebSocket support | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/348dfad46a2a9c0357602e1978ec09c2d2f98175.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 55 | [`56e6b4282bf9d6bf375a417d66943200ccf9f753`](https://github.com/FongMi/TV/commit/56e6b4282bf9d6bf375a417d66943200ccf9f753) | Document web remote controls and diagnostics sharing | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/56e6b4282bf9d6bf375a417d66943200ccf9f753.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 56 | [`e0fb273092da5d6da61231cfef2a528c74cd4f65`](https://github.com/FongMi/TV/commit/e0fb273092da5d6da61231cfef2a528c74cd4f65) | Document web playback seeking | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/e0fb273092da5d6da61231cfef2a528c74cd4f65.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 57 | [`3e7d358c4e6f7105b99cfeec6fbe2e27f1581c12`](https://github.com/FongMi/TV/commit/3e7d358c4e6f7105b99cfeec6fbe2e27f1581c12) | Simplify website remote and spider documentation | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/3e7d358c4e6f7105b99cfeec6fbe2e27f1581c12.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 58 | [`651aa7cb5b485fd2dcbf1cff42fd0c5f01a3da87`](https://github.com/FongMi/TV/commit/651aa7cb5b485fd2dcbf1cff42fd0c5f01a3da87) | Update website guides and navigation | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/651aa7cb5b485fd2dcbf1cff42fd0c5f01a3da87.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 59 | [`5b927238c8c5d2929c7903fc9ea2abfae3da8047`](https://github.com/FongMi/TV/commit/5b927238c8c5d2929c7903fc9ea2abfae3da8047) | Keep local tests and tooling out of published sources | 忽略 | 真实patch扩大.gitignore与本地工具排除。；https://github.com/FongMi/TV/commit/5b927238c8c5d2929c7903fc9ea2abfae3da8047 | 无播放收益，禁止移植忽略docs/AGENTS等仓库治理规则。 |
| 60 | [`970862c65bfe3eda1179759cfa7bc65aa584772a`](https://github.com/FongMi/TV/commit/970862c65bfe3eda1179759cfa7bc65aa584772a) | Add modular crawler runtimes and shared network APIs | 暂缓/架构gate | RuntimeModule新增外部APK/插件加载、prepare/entry/ABI/compatibility，167路径；local无RuntimeModule、无nodejs模块。；https://github.com/FongMi/TV/commit/970862c65bfe3eda1179759cfa7bc65aa584772a | 整套模块运行时不属窄播放器修复；需授权插件兼容/安全/ABI/制品链专项，不能顺带替换现有Chaquo/QuickJS。 |
| 61 | [`6a5d4b2e6176df819e8fa9b03b81d9ea23daa75f`](https://github.com/FongMi/TV/commit/6a5d4b2e6176df819e8fa9b03b81d9ea23daa75f) | Refine playback controllers, tracks and TV/mobile controls | 暂缓整包/拆行为 | 355路径；VodPreloader以playedMs、request对象身份、consume/clear/cancelPreloadContent驱动；local无playback/vod与player/media；已有PreCache:51/310、ASS/DV/FEL/Exo音频保护。；https://github.com/FongMi/TV/commit/6a5d4b2e6176df819e8fa9b03b81d9ea23daa75f | 拆控制器所有权、轨道持久化/生命周期、字幕面板、下一集预载、手势分别评估；当前架构不可整文件覆写。用户后续批准时先Exo窄功能而非355文件重放。 |
| 62 | [`805f7a0784be83af6a56fb3fa7c0a6b956383232`](https://github.com/FongMi/TV/commit/805f7a0784be83af6a56fb3fa7c0a6b956383232) | Integrate Android Auto browsing and DLNA playback | 暂缓 | 真实patch DLNAAvTransportImpl新增generation、pendingState、cachedSpeed、setDlnaActive；Android Auto browse/service新体系。；https://github.com/FongMi/TV/commit/805f7a0784be83af6a56fb3fa7c0a6b956383232 | 现有PlaybackService/DLNA是不同所有权设计，需车载产品需求、导航/前后台/DLNA竞态门禁。 |
| 63 | [`e3f5d38d2aa9373a17d0842e34d0a7748b56a3e9`](https://github.com/FongMi/TV/commit/e3f5d38d2aa9373a17d0842e34d0a7748b56a3e9) | Expand web remote controls, diagnostics and spider tools | 暂缓/按最终行为簇拆分 | 本地Nano.java:42-58挂载DebugLogs/Manage/WebResourceGateway等；DebugLogs.java:77-98已有TXT、nosniff、完整性和严格同源POST限频；app/src/main/assets/spider.html及SpiderTest.java不存在。上游最终SpiderTest.java:86-104、128-178有8会话/15min/2MiB/会话令牌/共享根路径约束，但requireOrigin:160允许缺Origin。；真实patch：https://github.com/FongMi/TV/commit/e3f5d38d2aa9373a17d0842e34d0a7748b56a3e9.patch | 巨型e3f5d38按遥控/诊断/Browser/爬虫工作台/断点上传/编辑格式器簇核对，非一次整体迁移。诊断和管理已有部分覆盖；新的远程代码执行及写文件面必须采用WebHTV授权/严格Host-Origin/限额，不原样推荐。格式器WASM和大前端资产无实测包体收益。 |
| 64 | [`5181b4782a3ae5e68bede4d9aaf0c5b1722357b6`](https://github.com/FongMi/TV/commit/5181b4782a3ae5e68bede4d9aaf0c5b1722357b6) | Integrate module management, app updates and shared UI | 暂缓 | 真实patch模块管理/安装/更新及资源；local无RuntimeInstaller/RuntimeModule新模块体系。；https://github.com/FongMi/TV/commit/5181b4782a3ae5e68bede4d9aaf0c5b1722357b6 | 新插件体系配套而非缺失播放器行为，包体/ABI/安全/安装生命周期先定方案。 |
| 65 | [`08d0bd40448abe02f6256b2a7a08cb38f143642d`](https://github.com/FongMi/TV/commit/08d0bd40448abe02f6256b2a7a08cb38f143642d) | Refresh project and crawler network documentation | 忽略/项目说明不适用 | README.md保留WebHome SDK/管理页/远程托管/诊断/实际工具链及TV基线；真实patch精简FongMi README与CatVodSpider入口。；真实patch：https://github.com/FongMi/TV/commit/08d0bd40448abe02f6256b2a7a08cb38f143642d.patch | 直接替换会丢失WebHTV实际产品及构建说明；仅作上游背景来源。 |
| 66 | [`9b534d53a45c3981f2836ad9c7e8889f3d41b5f2`](https://github.com/FongMi/TV/commit/9b534d53a45c3981f2836ad9c7e8889f3d41b5f2) | Improve playback log capture and Markdown reports | 忽略/已有更强诊断 | 真实patch PlaybackLog/ExoPlaybackLog错误与报告；local PlaybackDiagnosticCollector/Session/AudioPlaybackDiagnostics及AV-DIAG-01已独立实现。；https://github.com/FongMi/TV/commit/9b534d53a45c3981f2836ad9c7e8889f3d41b5f2 | 不迁移重复日志底座；具体遗漏字段可后续按诊断任务采集，不能覆盖现有脱敏/限频。 |
| 67 | [`4de3f2d53554d51fd4afca71c21d5397b072f579`](https://github.com/FongMi/TV/commit/4de3f2d53554d51fd4afca71c21d5397b072f579) | Bump app version to 5.7.8 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/4de3f2d53554d51fd4afca71c21d5397b072f579.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 68 | [`a906c788eb2ed9aacbb430ae123a5dad73b06a36`](https://github.com/FongMi/TV/commit/a906c788eb2ed9aacbb430ae123a5dad73b06a36) | Export playback logs as plain text | 忽略/当前已覆盖 | DebugLogs.java:77-83 DiagnosticReport.text生成text/plain UTF-8、.txt、nosniff和完整性标记；docs/AV-DIAG-01-playback-diagnostics.md:807、829沿用脱敏轮转/TXT/ZIP。；真实patch：https://github.com/FongMi/TV/commit/a906c788eb2ed9aacbb430ae123a5dad73b06a36.patch | 真实patch由Markdown改TXT；本地已是更强的受保护导出，不退回上游含完整播放URL的原始报告。 |
| 69 | [`3f6cf4366c1f782afff5d0e50057c53fed1b3032`](https://github.com/FongMi/TV/commit/3f6cf4366c1f782afff5d0e50057c53fed1b3032) | Support video aspect ratio playback commands | 暂缓/低优先级 | 真实patch ExoCommand新增video-aspect-override(比例/冒号/no)，local无ExoCommand；当前缩放/比例属于PlayerManager/UI已有能力。；https://github.com/FongMi/TV/commit/3f6cf4366c1f782afff5d0e50057c53fed1b3032 | 命令入口未覆盖不是比例渲染全无；需按现有按钮/远程鉴权接口适配并验证PlayerView扩展API，不建第二命令体系。 |
| 70 | [`5b893c36c8216d46c51871236e20961963411d4f`](https://github.com/FongMi/TV/commit/5b893c36c8216d46c51871236e20961963411d4f) | Allow mobile pinch scaling down to 50% with 100% stops | 暂缓/独立交互需求 | PinchScale.java在当前git ls-files中无此类；mobile VideoActivity.java:3749、6270为既有onScale入口。真实patch是按轴MIN_SCALE=0.5及跨100%停靠。；真实patch：https://github.com/FongMi/TV/commit/5b893c36c8216d46c51871236e20961963411d4f.patch | 并非当前爬虫/网络缺口；需单独确认允许缩到50%的产品交互及双指单轴/双轴回归，不能复制一个当前不存在的辅助类作为修复。 |
| 71 | [`0a7469bdce4de1c1edabd763eb8b7b3eb382d25c`](https://github.com/FongMi/TV/commit/0a7469bdce4de1c1edabd763eb8b7b3eb382d25c) | Simplify log export documentation wording | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/0a7469bdce4de1c1edabd763eb8b7b3eb382d25c.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 72 | [`3aaab4fa20590beb3ba99474a0ebc347917c80c4`](https://github.com/FongMi/TV/commit/3aaab4fa20590beb3ba99474a0ebc347917c80c4) | Remove txt keywords from log documentation search | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/3aaab4fa20590beb3ba99474a0ebc347917c80c4.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 73 | [`16b1f3e10c3e9ca0877461b26c21308626fac77c`](https://github.com/FongMi/TV/commit/16b1f3e10c3e9ca0877461b26c21308626fac77c) | Share source-scoped live authentication across Exo and mpv | 实施候选/认证隔离 | Auth.Context.capture+sameOrigin(scheme/host/effectivePort)、Channel.live/applyAuth→Result→PlaySpec/MediaItemFactory→Exo/Mpv；local Auth无Context，AuthInterceptor:18/31-41/48-52为host-only userMap。；https://github.com/FongMi/TV/commit/16b1f3e10c3e9ca0877461b26c21308626fac77c | 可确认作用域少source/scheme/port的源码风险，未实测泄漏；同源HLS清单/分片/key及mpv代理需显式传上下文，禁止凭据跨源扩散。 |
| 74 | [`c20f052bfec9f3569cfbb2bf6471956937d1dbd0`](https://github.com/FongMi/TV/commit/c20f052bfec9f3569cfbb2bf6471956937d1dbd0) | Stabilize playback cards and separate media headings | 暂缓/低价值UI | 真实patch media metadata title/subtitle分离、清空图片/卡片状态；local无MediaItemFactory/LivePlaybackController。；https://github.com/FongMi/TV/commit/c20f052bfec9f3569cfbb2bf6471956937d1dbd0 | 保留现有歌词/封面/诊断显示；若现用户有标题残留复现再按UI局部修，不引入新browse架构。 |
| 75 | [`8cbd71d453c58dbf0e6359d88d52186400b1e3ec`](https://github.com/FongMi/TV/commit/8cbd71d453c58dbf0e6359d88d52186400b1e3ec) | Persist refreshed device discovery results | 暂缓/设备发现适配 | DeviceDao.java:13-20只有全量find/delete及按uuid delete，无replaceAppDevices；本地无ScanTask.java；Device.java:168已有save。；真实patch：https://github.com/FongMi/TV/commit/8cbd71d453c58dbf0e6359d88d52186400b1e3ec.patch | 真实patch事务替换type0/1发现缓存并uuid去重；原样删除全部缓存可能清掉用户手工配对/离线设备，需先界定自动发现与手工信任记录的所有权，当前不合入。 |
| 76 | [`46ff3eeb127e7a01ce74b1a9670fcb5e68caac9b`](https://github.com/FongMi/TV/commit/46ff3eeb127e7a01ce74b1a9670fcb5e68caac9b) | Remove resolution display from TV playback overlays | 忽略 | 真实patch删除TV overlay分辨率显示。；https://github.com/FongMi/TV/commit/46ff3eeb127e7a01ce74b1a9670fcb5e68caac9b | 移除现有可见信息无播放器收益，保留当前诊断能力。 |
| 77 | [`91f8f16a8bbd5e32f2e2bd31467035b834f45eba`](https://github.com/FongMi/TV/commit/91f8f16a8bbd5e32f2e2bd31467035b834f45eba) | Fix web refresh, return navigation and playback details | 暂缓/依赖遥控前端新簇 | 本地app/src/main/assets/js/remote.js、navigation.js和spider.html无对应新版链；DebugLogs.java生成本项目日志页。；真实patch：https://github.com/FongMi/TV/commit/91f8f16a8bbd5e32f2e2bd31467035b834f45eba.patch | 真实patch是Navigation API返回、render blocking和遥控卡片loading/artwork淡入；需附着63的获批UI小簇，不能替换本地管理/日志体系。现代Navigation API/:has兼容与首屏等待尚未测。 |
| 78 | [`60e901aa0aeaaac41c3586b7f6c5989db5e4d5a4`](https://github.com/FongMi/TV/commit/60e901aa0aeaaac41c3586b7f6c5989db5e4d5a4) | Resolve untyped sources before ExoPlayer preparation | 暂缓/高价值gate | 真实patch以ContentTypeResolvingMediaSource.Factory包装DefaultMediaSourceFactory；local MediaSourceFactory:323-325/367-388无该包装，media-lock e3e922d5c01bc0b564849940fe589daf37360d15。；https://github.com/FongMi/TV/commit/60e901aa0aeaaac41c3586b7f6c5989db5e4d5a4 | 未知扩展/伪路径HLS/DASH识别值得补；该自定义Media3类确切源码与已打包可达性未验证，先冻结全hash并保证鉴权/cache/Range/ASS/DV不丢。 |
| 79 | [`e5ec0c60f53c215acda2d0d66bb1bc78209e8dcb`](https://github.com/FongMi/TV/commit/e5ec0c60f53c215acda2d0d66bb1bc78209e8dcb) | Smooth playback page refresh transitions | 暂缓/依赖遥控前端新簇 | 本地app/src/main/assets/js/remote.js、navigation.js和spider.html无对应新版链；DebugLogs.java生成本项目日志页。；真实patch：https://github.com/FongMi/TV/commit/e5ec0c60f53c215acda2d0d66bb1bc78209e8dcb.patch | 真实patch是Navigation API返回、render blocking和遥控卡片loading/artwork淡入；需附着63的获批UI小簇，不能替换本地管理/日志体系。现代Navigation API/:has兼容与首屏等待尚未测。 |
| 80 | [`7448d205ab0d306e40ddb3c8533bddd8a9ad2e38`](https://github.com/FongMi/TV/commit/7448d205ab0d306e40ddb3c8533bddd8a9ad2e38) | Bump app version to 5.7.9 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/7448d205ab0d306e40ddb3c8533bddd8a9ad2e38.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 81 | [`d00701d238dfceb69da408405d09e084b3faa986`](https://github.com/FongMi/TV/commit/d00701d238dfceb69da408405d09e084b3faa986) | Honor redirect option in shared crawler requests | 暂缓/60伴随 | Net.newCall给Request.tag(NetOptions)；RedirectInterceptor遵守isRedirect false。local无Net/NetOptions/RedirectInterceptor。；https://github.com/FongMi/TV/commit/d00701d238dfceb69da408405d09e084b3faa986 | 不是当前同名bug；若实施48且接新Net才需兼容，不可独立复制新接口。 |
| 82 | [`c05d9b2e5dd8a47a70bcf55ca23c86560f0c25cf`](https://github.com/FongMi/TV/commit/c05d9b2e5dd8a47a70bcf55ca23c86560f0c25cf) | Fix stale artwork when switching VOD playback | 忽略/本地等价且更强 | mobile VideoActivity.java:4253-4288用requestUrl+requestOwner+Activity存活守卫；leanback VideoActivity.java:3544-3568有owner守卫及空图清除。；真实patch：https://github.com/FongMi/TV/commit/c05d9b2e5dd8a47a70bcf55ca23c86560f0c25cf.patch | 真实patch复用单个Glide CustomTarget及onLoadCleared；本地已有过期封面抑制并接入音频封面色彩，替换可能丢本地语义。 |
| 83 | [`66471762c28d964ae13c610d01f5081c611ea028`](https://github.com/FongMi/TV/commit/66471762c28d964ae13c610d01f5081c611ea028) | Allow subtitle scaling up to 400 percent | 暂缓/产品UI | 真实patchSubtitleSetting.MAX_SCALE由2改4；local无SubtitleSetting，当前mpv sub-scale与字幕样式为独立体系。；https://github.com/FongMi/TV/commit/66471762c28d964ae13c610d01f5081c611ea028 | 400%只是上限策略，不是libass缺失；需主副/ASS/位图布局及性能一致性需求后窄改。 |
| 84 | [`bd1e887aad7646cc83c445e8057dacea1ea4907c`](https://github.com/FongMi/TV/commit/bd1e887aad7646cc83c445e8057dacea1ea4907c) | Avoid copying selected files before playback | 暂缓/需要URI gate | 真实patch FileChooser直接Uri/ExternalPlayback+STRM content来源避免复制；local FileChooser有路径解析，Strm仅http/file。；https://github.com/FongMi/TV/commit/bd1e887aad7646cc83c445e8057dacea1ea4907c | 减少复制与空间占用可推断；必须授权content://可重复读取权限、FD释放、直播转本地URL、字体仍需可读文件的专项设计，不能一律取消复制。 |
| 85 | [`56bf1b3bc84bda45c0e7bc023945eb6dc8de50d5`](https://github.com/FongMi/TV/commit/56bf1b3bc84bda45c0e7bc023945eb6dc8de50d5) | Remember notified runtime module versions | 忽略/当前模块未外置 | Updater.java:129-146、169-219仅WebHTV稳定/beta App更新；ModuleViewModel/RuntimeInstaller/RuntimeModule模块安装体系不存在。；真实patch：https://github.com/FongMi/TV/commit/56bf1b3bc84bda45c0e7bc023945eb6dc8de50d5.patch | 真实patch按模块版本记一次通知；当前JS/Python均App内置，无该模块通知需求。 |
| 86 | [`87dd18c3bc12e8c13218e80e5a034ff70a3afbe7`](https://github.com/FongMi/TV/commit/87dd18c3bc12e8c13218e80e5a034ff70a3afbe7) | Bump app version to 5.8.0 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/87dd18c3bc12e8c13218e80e5a034ff70a3afbe7.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 87 | [`9e366710d03527030e92ce2a409feb4ec96bc5e6`](https://github.com/FongMi/TV/commit/9e366710d03527030e92ce2a409feb4ec96bc5e6) | Place CSP selector beside jar input | 忽略/当前工具页不存在 | Test-Path app/src/main/assets/spider.html=False；真实patch仅CSP按钮挪到jar输入右侧。；真实patch：https://github.com/FongMi/TV/commit/9e366710d03527030e92ce2a409feb4ec96bc5e6.patch | 附属63工作台UX，当前无独立收益。 |
| 88 | [`486eeb389f43925b22ef0e68123ddb1c59c2c371`](https://github.com/FongMi/TV/commit/486eeb389f43925b22ef0e68123ddb1c59c2c371) | Restrict video scaling gestures to fullscreen playback | 忽略/未覆盖入口无当前价值 | 真实patchTouchInput/PiP/fullscreen缩放开关；local无TouchInput新输入体系。；https://github.com/FongMi/TV/commit/486eeb389f43925b22ef0e68123ddb1c59c2c371 | 新手势配套约束，若以后移植缩放再一并用，当前不引入整套手机输入层。 |
| 89 | [`86ae0983cbbaf0b087da9d99c55be8e7dcf59d83`](https://github.com/FongMi/TV/commit/86ae0983cbbaf0b087da9d99c55be8e7dcf59d83) | Restore per-station live channel history | 暂缓/local已有扩展 | 真实patch移除LiveConfig currentChannel历史方式，改findKeepPosition；local LiveConfig仍有当前站点扩展与Keep。；https://github.com/FongMi/TV/commit/86ae0983cbbaf0b087da9d99c55be8e7dcf59d83 | 可能恢复旧能力而非新增；不能删当前跨站/历史策略，需复现后比较精确身份合同。 |
| 90 | [`f98a376caa7f91618b03b494ab1326e83ffe832b`](https://github.com/FongMi/TV/commit/f98a376caa7f91618b03b494ab1326e83ffe832b) | Extend crawler networking and live danmaku | 暂缓/partial | 真实patchSiteApi.danmaku(spider://key/id)返回AutoCloseable、网络API；local已有player/danmaku WebSocketSession/Retry/Buffer/Batcher，但无spider subscription入口。；https://github.com/FongMi/TV/commit/f98a376caa7f91618b03b494ab1326e83ffe832b | 已有直播网络弹幕不等于新爬虫订阅；新入口需60 runtime协议/关闭所有权/背压/线程安全门禁。 |
| 91 | [`93ba4b7d36b2021b6cd4db4319a9dc46988a70c4`](https://github.com/FongMi/TV/commit/93ba4b7d36b2021b6cd4db4319a9dc46988a70c4) | Preserve QuickJS proxy response fields during shrinking | 忽略/随库规则已覆盖；Release证据未采集 | gradle/libs.versions.toml:32 Gson=2.14.0；Res.java:19-26全字段有SerializedName、28-29 Gson反射；官方gson-parent-2.14.0/gson/src/main/resources/META-INF/proguard/gson.pro自动keep带注解字段及无参构造；app/build/outputs/mapping不存在。；真实patch：https://github.com/FongMi/TV/commit/93ba4b7d36b2021b6cd4db4319a9dc46988a70c4.patch | 上游真实patch仅显式keep Res。不能由quickjs/consumer-rules.pro不存在推断当前字段丢失；保留现有Gson规则，若后续正式Release回归暴露问题再加精确keep，不升级runtime。 |
| 92 | [`8de89a1070c6ce7e45f845756b00be6f1bb1754c`](https://github.com/FongMi/TV/commit/8de89a1070c6ce7e45f845756b00be6f1bb1754c) | Add shared crawler mapping and isolated JavaScript evaluation | 暂缓/60伴随 | 真实patchSpider.js、JarLoader close与NetMap、隔离JS求值。local无Net/NetMap新架构。；https://github.com/FongMi/TV/commit/8de89a1070c6ce7e45f845756b00be6f1bb1754c | 网络/脚本生命周期升级，不以player正则误当播放性能优化；专项授权。 |
| 93 | [`43bf0b0f1d4673a81c815d72df50e547bb4bc4e6`](https://github.com/FongMi/TV/commit/43bf0b0f1d4673a81c815d72df50e547bb4bc4e6) | Support live timeshift in playback controls | 暂缓/高价值live gate | 新增Timeshift bean/TimeshiftPlayback、PlayerManager seek未来请求；local无TimeshiftPlayback，但LiveApi:53-57已有EPG catchup/rtsp_range。；https://github.com/FongMi/TV/commit/43bf0b0f1d4673a81c815d72df50e547bb4bc4e6 | 已有节目回看不等于直播滑动窗口拖回；需服务端时移协议/认证/时间轴/切台取消/返回直播适配，不能替换现有catchup。 |
| 94 | [`939747b3f174aef9f9b91e6b7b1a71117104eed2`](https://github.com/FongMi/TV/commit/939747b3f174aef9f9b91e6b7b1a71117104eed2) | Simplify live timeshift request tracking | 暂缓/93必带 | 真实patch TimeshiftPlayback generation改pending==future判等，取消后过期callback不apply。；https://github.com/FongMi/TV/commit/939747b3f174aef9f9b91e6b7b1a71117104eed2 | 93不存在时不可单独合入；时移实施必须保留该取消/请求身份不变量。 |
| 95 | [`e434458fe824eb968760a4ad5c9777926151b136`](https://github.com/FongMi/TV/commit/e434458fe824eb968760a4ad5c9777926151b136) | Refine crawler mapping and script execution helpers | 暂缓/92伴随 | NetMap.createWorker正确completion，Scripts globals校验与buildScript。；https://github.com/FongMi/TV/commit/e434458fe824eb968760a4ad5c9777926151b136 | 新NetMap/isolated JS细化，无当前播放器入口收益。 |
| 96 | [`07b6d5545fa5fa293bbe14631d0e9411d85d3687`](https://github.com/FongMi/TV/commit/07b6d5545fa5fa293bbe14631d0e9411d85d3687) | Align crawler documentation and navigation | 忽略/上游文档不适用 | Test-Path website=False；README.md 的本项目文档入口为 webhome-devkit/docs/应用完整开发文档.md；本地不发布FongMi六页Next.js网站。；真实patch：https://github.com/FongMi/TV/commit/07b6d5545fa5fa293bbe14631d0e9411d85d3687.patch | 真实patch是website字段说明/索引/导航/文字维护，不产生当前App能力；不可把尚无的shared net/connect/skips等文档写为本地支持。必要协议文档应跟随获批功能单独适配。 |
| 97 | [`9c845a913337b2d994343b7885159c0a84681b93`](https://github.com/FongMi/TV/commit/9c845a913337b2d994343b7885159c0a84681b93) | Fix category filters after folder navigation | 暂缓/窄适配前需确认导航合同 | leanback FolderFragment.java:70-81和mobile:74-96进入子目录后直接操作当前child，没有popBackStackImmediate。真实patch在打开分类筛选或setFilter前清空全目录返回栈。；真实patch：https://github.com/FongMi/TV/commit/9c845a913337b2d994343b7885159c0a84681b93.patch | 可解决分类级筛选落在子目录，但原样会强制丢用户目录位置；应明确分类筛选才返回根、目录级筛选不重置，暂不原样采用。 |
| 98 | [`412e7d6b32ca545fbffc216b12b854a85a305ed1`](https://github.com/FongMi/TV/commit/412e7d6b32ca545fbffc216b12b854a85a305ed1) | Bump app version to 5.8.1 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/412e7d6b32ca545fbffc216b12b854a85a305ed1.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 99 | [`c2e3ca0e89ccdc2b2c288c5f89aa16ba804c6678`](https://github.com/FongMi/TV/commit/c2e3ca0e89ccdc2b2c288c5f89aa16ba804c6678) | Preserve next-episode preloads across seeks | 暂缓/下一集功能不等价 | 真实patchVodPlaybackController.onPlaybackSeek由preloader.update改onPlayingChanged；local无VodPreloader，PreCache:310为当前媒体磁盘预缓存取消seek。；https://github.com/FongMi/TV/commit/c2e3ca0e89ccdc2b2c288c5f89aa16ba804c6678 | 不可用该patch让当前预缓存跨seek存活；下一集身份/解析预载/媒体预载须独立于当前媒体缓存生命周期。 |
| 100 | [`ef58e5551f9a9d8679821b38422c06373185a04e`](https://github.com/FongMi/TV/commit/ef58e5551f9a9d8679821b38422c06373185a04e) | Apply VOD details from playback results | 暂缓 | 真实patchResult.getVod().setFlags/Flag rev保持当前Episode、控制器应用Result中的详情。local无VodPlaybackController。；https://github.com/FongMi/TV/commit/ef58e5551f9a9d8679821b38422c06373185a04e | 可扩展爬虫详情，但必须处理pending request/换源/历史merge，不能重放整控制器。 |
| 101 | [`baf0fd2988327afe1bd7e81c8e3cf0d5a45b7812`](https://github.com/FongMi/TV/commit/baf0fd2988327afe1bd7e81c8e3cf0d5a45b7812) | Bump app version to 5.8.2 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/baf0fd2988327afe1bd7e81c8e3cf0d5a45b7812.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 102 | [`999c282e96a91987346f53d744c692b1f7c382d5`](https://github.com/FongMi/TV/commit/999c282e96a91987346f53d744c692b1f7c382d5) | Validate MPV C++ runtime before first native load | 忽略/当前包内方案不适用 | RuntimeModule.prepareInstalled中首次MPV native load前verifyHostLibrary。localMPV包内独立namespace已有锁/制品校验。；https://github.com/FongMi/TV/commit/999c282e96a91987346f53d744c692b1f7c382d5 | 外部模块C++兼容保护不是当前新增MPV加载能力，若切外挂模块再专项审查。 |
| 103 | [`c956dbc980c65cba6135c13a521aa123d5c2db28`](https://github.com/FongMi/TV/commit/c956dbc980c65cba6135c13a521aa123d5c2db28) | Add shared dialogs for Java, Python and QuickJS spiders | 暂缓/60伴随 | Runtime.setDialog/Spider.dialog.close/CompletableFuture跨Java/Python/QuickJS。；https://github.com/FongMi/TV/commit/c956dbc980c65cba6135c13a521aa123d5c2db28 | 交互爬虫协议变化，需主线程/Activity销毁/取消/鉴权门禁而非播放小修。 |
| 104 | [`867581dc198fc39d6523acf1921a5b827be849cc`](https://github.com/FongMi/TV/commit/867581dc198fc39d6523acf1921a5b827be849cc) | Report native mpv audio effect application results | 暂缓/能力链缺失 | MpvPlayerEffect加入setAudioFilterListener与失败结果回报；local无MpvPlayerEffect类。；https://github.com/FongMi/TV/commit/867581dc198fc39d6523acf1921a5b827be849cc | 不能断言本地音频特效全覆盖；需对应MpvAudioFilter JNI/API全hash+读回/失败回滚，保留直通独占策略。 |
| 105 | [`ff40eddb972d312a65a7ae9d0893d1086e5329f0`](https://github.com/FongMi/TV/commit/ff40eddb972d312a65a7ae9d0893d1086e5329f0) | Support raw binary HTTP request bodies | 暂缓/60伴随 | NetOptions body改JsonElement，NetRequest支持raw binary body。local无NetOptions/NetRequest。；https://github.com/FongMi/TV/commit/ff40eddb972d312a65a7ae9d0893d1086e5329f0 | 爬虫网络能力非播放器收益，保留现有nativeSdk网络/日志脱敏兼容后专项。 |
| 106 | [`aa30eb047c749fe5a96dd47b94947cda2421354f`](https://github.com/FongMi/TV/commit/aa30eb047c749fe5a96dd47b94947cda2421354f) | Fix filter scroll | 忽略/本地已有 | app/src/mobile/res/layout/dialog_filter.xml:2已经androidx.recyclerview.widget.RecyclerView，1-13无nestedScrollingEnabled=false。；真实patch：https://github.com/FongMi/TV/commit/aa30eb047c749fe5a96dd47b94947cda2421354f.patch | 真实patch换掉CustomRecyclerView及移除nestedScrollingEnabled=false；本地已覆盖，玻璃面板/间距必须保留。 |
| 107 | [`3808c58d956a2128d2a4510ae99a08ba3eec351f`](https://github.com/FongMi/TV/commit/3808c58d956a2128d2a4510ae99a08ba3eec351f) | Allow local ClearKey next-episode preloading | 忽略/已被110替代 | 真实patchVodPreloader允许LocalClearKeyLicense；110删除该限定。；https://github.com/FongMi/TV/commit/3808c58d956a2128d2a4510ae99a08ba3eec351f | 只作为历史过渡记录，不推荐过渡patch或凭标题称新解密能力。 |
| 108 | [`1177e4cc8e06cedf1b885f30828b4e39abd73e83`](https://github.com/FongMi/TV/commit/1177e4cc8e06cedf1b885f30828b4e39abd73e83) | Bump app version to 5.8.3 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/1177e4cc8e06cedf1b885f30828b4e39abd73e83.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 109 | [`bda5e1d67613574ec3276a28d4f6956b3e6e7d48`](https://github.com/FongMi/TV/commit/bda5e1d67613574ec3276a28d4f6956b3e6e7d48) | Fix shared dialog styling and D-pad scrolling | 暂缓/当前共用表单不存在 | Test-Path app/src/main/java/com/fongmi/android/tv/ui/dialog/SpiderDialog.java=False；上游patch是Material3 exposed dropdown、multiselect图标和ScrollView.arrowScroll优先。；真实patch：https://github.com/FongMi/TV/commit/bda5e1d67613574ec3276a28d4f6956b3e6e7d48.patch | 属于新Spider表单API的TV焦点适配，不是当前可触达页面缺陷；若引入表单后再一起验证长表单/编辑光标/下拉/按钮。 |
| 110 | [`23fbfdfc01e8c003eca396f60f6916e354803307`](https://github.com/FongMi/TV/commit/23fbfdfc01e8c003eca396f60f6916e354803307) | Allow DRM next-episode preloading | 暂缓/下一集DRM gate | 真实patchVodPreloader删除所有DRM预载排除；local无下一集VodPreloader。；https://github.com/FongMi/TV/commit/23fbfdfc01e8c003eca396f60f6916e354803307 | 可能提前触发license/并发DRM会话，禁直接放开；需许可有效期/配额/释放隔离/取消门禁。 |
| 111 | [`0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1`](https://github.com/FongMi/TV/commit/0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1) | Return 404 for missing local crawler sources | 实施候选/窄HTTP正确性适配 | Local.java:47-55缺文件throw FileNotFoundException后Nano.error(e.getMessage())；Nano.java:68-73默认500且已有error(Status,text)重载。；真实patch：https://github.com/FongMi/TV/commit/0234d3dcee079fb4d5406e7f42f06c73cd8d4fb1.patch | 真实patch缺文件直接NOT_FOUND，当前可触达/file路径确有500≠404缺口；仅缺文件分支改404，其它I/O和权限异常保留，不全文件移植。 |
| 112 | [`99be430d54d848a9ad996648f57bbb4459edb682`](https://github.com/FongMi/TV/commit/99be430d54d848a9ad996648f57bbb4459edb682) | Add cryptography using pinned Python Android artifacts | 暂缓/原生与包体门禁 | chaquo/build.gradle:14-18 Python3.10、pip requirements；:24-32双ARM；chaquo/requirements.txt:1-7现有requests/pycryptodome/lxml等，无cryptography/pywasm/numpy；chaquo/runtime目录不存在。上游135锁python-android@4cabf1d1bce37d1fc517d939c54b8087af8f9cd3，真实patchARM64 16KiB/ARM32 4KiB、版本化.so、wheel摘要与同C++校验。；真实patch：https://github.com/FongMi/TV/commit/99be430d54d848a9ad996648f57bbb4459edb682.patch | 112的全native替换已被135生产链/ABI修正覆盖；cryptography不是现有pycryptodome等价替换，pywasm1.0.8引入NumPy/OpenBLAS/libgfortran。无爬虫样本证明刚需，不整支升级/外置runtime；只在获批依赖缺口时做同ABI可复现小制品包与许可证/加载/包体门禁。 |
| 113 | [`123cc086159b0f13bf54bdf64bacbc259ca47c88`](https://github.com/FongMi/TV/commit/123cc086159b0f13bf54bdf64bacbc259ca47c88) | Fix Python loading errors and resource cleanup | 实施候选/失败清理与可诊断错误 | PyLoader.java:38-49创建spider再init，失败返回并缓存SpiderNull但不销毁已创建实例；JsLoader.java:39-51相同；chaquo/Spider.java:107-112只调用ru.destroy并吞异常；app.py:7-12用SourceFileLoader.load_module且无命名空间close。；真实patch：https://github.com/FongMi/TV/commit/123cc086159b0f13bf54bdf64bacbc259ca47c88.patch | 真实patch补Python资源close/模块release、缺依赖转ModuleNotFoundError和SpiderLoadException；本地旧桥没有js/net/streams对象，不可原样移植。先补已创建实例的失败destroy、失败不缓存、原错误+cleanup suppressed与UI错误，模块隔离单独适配。 |
| 114 | [`b1bab5bfdf883fb938addc2a95ca5f13d3b72abb`](https://github.com/FongMi/TV/commit/b1bab5bfdf883fb938addc2a95ca5f13d3b72abb) | Bump app version to 5.8.4 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/b1bab5bfdf883fb938addc2a95ca5f13d3b72abb.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 115 | [`14885254ae4545de9389206b19a7f163062293f5`](https://github.com/FongMi/TV/commit/14885254ae4545de9389206b19a7f163062293f5) | Remember decoder modes and disable automatic decoder retries | 忽略/partial合同保留 | 补读真实patch删除Exo/mpv自动decoder retry与DECODE错误分支；localPlayerManager:766-767手动模式、E4-LIBASS/E11/C-AVS3/P2-4文档视频仅手动，音频有独立回退。；https://github.com/FongMi/TV/commit/14885254ae4545de9389206b19a7f163062293f5 | 上游禁所有重试不能覆盖本地音频/资源恢复；手动视频合同已实现，记忆模式差异尚需需求，不整合删本地错误恢复。 |
| 116 | [`2645429fc06b238760c9aab5c8053f476d26c5c4`](https://github.com/FongMi/TV/commit/2645429fc06b238760c9aab5c8053f476d26c5c4) | Restore local subtitle discovery and VTT upload support | 暂缓/partial | 真实patchSiteApi本地STRM解析后scan subtitles、VTT上传白名单；local有ASS字体/外挂字幕，缺LocalSubtitleScanner新类。；https://github.com/FongMi/TV/commit/2645429fc06b238760c9aab5c8053f476d26c5c4 | 不能宣称字幕缺失；路径/content://生命周期和上传格式需窄核查，按E4-LIBASS保留HDR/DV/双字幕。 |
| 117 | [`a09d2315d3d95d4e353386878f0ab42e1727ef07`](https://github.com/FongMi/TV/commit/a09d2315d3d95d4e353386878f0ab42e1727ef07) | Support Exo audio commands and organize playback documentation | 暂缓/具体未覆盖 | 真实patchExoCommand aid/audio通过TrackSelectionUtil cycle/select/reset/disable轨道；local无ExoCommand，已有TrackUtil/ExoAudioOutputState。；https://github.com/FongMi/TV/commit/a09d2315d3d95d4e353386878f0ab42e1727ef07 | 缺命令语法而非音频解码，音轨变更必须保护直通/PCM/软音频回退、轨道稳定ID及跨source复位，不复制新TrackManager整体系。 |
| 118 | [`23639b7eea70c1dfcd2aa3f9673aa48407f0c38f`](https://github.com/FongMi/TV/commit/23639b7eea70c1dfcd2aa3f9673aa48407f0c38f) | Select module ABI from packaged native libraries | 忽略/当前包内方案不适用 | PackageAbi从packaged native libraries选择外挂模块ABI；local两ARM成套包内资产。；https://github.com/FongMi/TV/commit/23639b7eea70c1dfcd2aa3f9673aa48407f0c38f | 仅外部模块架构配套，不以CPU ABI取代当前APK ABI配置。 |
| 119 | [`ef96a482667c73389a7c9c09c02ff7d5986d238b`](https://github.com/FongMi/TV/commit/ef96a482667c73389a7c9c09c02ff7d5986d238b) | Hide MPV playback engine when its module is not installed | 忽略/当前包内方案不适用 | PlayerSetting.getEngineCount以RuntimeModule.MPV.isInstalled判断。；https://github.com/FongMi/TV/commit/ef96a482667c73389a7c9c09c02ff7d5986d238b | localMPV当前包内，不能错误隐藏已有MPV。 |
| 120 | [`01d2d56e335f3d751d02446a799e2fa120231189`](https://github.com/FongMi/TV/commit/01d2d56e335f3d751d02446a799e2fa120231189) | Fix API 24 compatibility and share failed future creation | 暂缓/60伴随 | Task.failedFuture替换API24不可用直接方法、Net关闭状态future。；https://github.com/FongMi/TV/commit/01d2d56e335f3d751d02446a799e2fa120231189 | 新插件网络/API的兼容修复，不说明当前有同一故障；引入新runtime时必带API24测试。 |
| 121 | [`8c296c0d2ee459ae15cc121f11621809060710ab`](https://github.com/FongMi/TV/commit/8c296c0d2ee459ae15cc121f11621809060710ab) | Align crawler APIs and simplify Python and QuickJS integration | 暂缓/大runtime gate | 64路径crawler APIs/Python+QuickJS重组、二进制与lock。；https://github.com/FongMi/TV/commit/8c296c0d2ee459ae15cc121f11621809060710ab | 需冻结插件API/ABI/制品来源与旧Spider兼容，不能以player组归属绕过native安全门禁。 |
| 122 | [`ade54d6f557c4d11afdfeeda61bb778c497654ba`](https://github.com/FongMi/TV/commit/ade54d6f557c4d11afdfeeda61bb778c497654ba) | Simplify QuickJS proxy URL bridge | 忽略/新桥当前不适用 | 本地quickjs仍wrapper3.2.3（libs.versions.toml:51,133-134），无net.js共享host桥；Spider.java:118-119当前proxy1/proxy2。；真实patch：https://github.com/FongMi/TV/commit/ade54d6f557c4d11afdfeeda61bb778c497654ba.patch | 真实patch将net.proxy/net.proxyLocal直达QuickJS.Json桥参数生成URL；不是旧版Global API签名的直接修复，需随获批共享网络API设计，当前不移植。 |
| 123 | [`f1aa53e2526307fa67930dd1d0efb6fc3e4732a9`](https://github.com/FongMi/TV/commit/f1aa53e2526307fa67930dd1d0efb6fc3e4732a9) | Align Python and QuickJS proxy response handling | 实施候选/保留旧响应协议的窄规范化 | chaquo/Spider.java:89-98已容纳第5项base64，:124-129已有bytes；quickjs/Spider.java:208-219同样保留base64，:223-234 proxy2仅返回3项丢Res响应头，:237-244空body会o.toString空指针；Res.java:48-61可取header/base64流。；真实patch：https://github.com/FongMi/TV/commit/f1aa53e2526307fa67930dd1d0efb6fc3e4732a9.patch | 真实patch不再拒绝>4项、允许$tvStream附加字段。不能引入新token桥或删旧base64标志；适配状态/MIME/headers/body类型与null，先补proxy2第四项和现有bytes/base64兼容；typed-array/raw-token须另证旧SDK可表达再实施。 |
| 124 | [`0a4e0f93641d82b205ab5a5dd40a18e249a666a9`](https://github.com/FongMi/TV/commit/0a4e0f93641d82b205ab5a5dd40a18e249a666a9) | Fix proxy policy crash when URI host is null | 实施48伴随/当前独立忽略 | 真实patch Policy改用HttpUrl避免URI.getHost null小写崩溃；localOkProxySelector:65已有host==null fallback，无Policy。；https://github.com/FongMi/TV/commit/0a4e0f93641d82b205ab5a5dd40a18e249a666a9 | 当前不存在该Policy崩溃，不能宣称修复当前；若采用48必须用最终HttpUrl防护版本。 |
| 125 | [`7bf115759881e27df819bb01a357f4d7094d295c`](https://github.com/FongMi/TV/commit/7bf115759881e27df819bb01a357f4d7094d295c) | Coalesce TV seek keys and cancel stale seek sessions | 实施候选 | 真实patchSeekInput stable seekCommit Runnable、deviceId/downTime归属、cancelPendingSeek与取消监听；localCustomKeyDownVod:55匿名App.post(...,250)。；https://github.com/FongMi/TV/commit/7bf115759881e27df819bb01a357f4d7094d295c | 本地不可撤销延迟seek是具体差异；移植不变量到现有输入层，禁止连带替换PlaybackActivity/355文件架构。 |
| 126 | [`7d70ebdfe56997ae9bccca7539a413a6999a09e3`](https://github.com/FongMi/TV/commit/7d70ebdfe56997ae9bccca7539a413a6999a09e3) | Resolve HTTP STRM targets without filename headers | 实施候选 | 真实patchStrm.http res.isSuccessful+peekBody(64KiB)+BOM/trim+合法URL；localStrm:29-33依赖Content-Disposition且body.string无上限。；https://github.com/FongMi/TV/commit/7d70ebdfe56997ae9bccca7539a413a6999a09e3 | 无文件名响应不解析/可能无界读取，窄补首行probe；保留源提取器取消/协议白名单。 |
| 127 | [`c3345b892f58f32f35d73034505907bff2f270bd`](https://github.com/FongMi/TV/commit/c3345b892f58f32f35d73034505907bff2f270bd) | Remove redundant crawler runtime checks | 忽略/删除防御非收益 | 本地无net.js流/会话host桥及ru.runtime成员；app.py:105-106仅ru.destroy。；真实patch：https://github.com/FongMi/TV/commit/c3345b892f58f32f35d73034505907bff2f270bd.patch | 真实patch删除桥返回类型/ID/chunk大小/可选destroy校验，前提是新版桥内部已保证契约；当前不删防御，更不能盲目ru.runtime.close。 |
| 128 | [`6f64521a652c646b18ef83e8f6a3cedae3334b75`](https://github.com/FongMi/TV/commit/6f64521a652c646b18ef83e8f6a3cedae3334b75) | Keep playback speed and danmaku offset temporary | 暂缓/产品语义 | 真实patchSpeedSetting与DanmakuSetting移除全局临时offset，PlayerManager.resetPlaybackSettings新媒体复位。；https://github.com/FongMi/TV/commit/6f64521a652c646b18ef83e8f6a3cedae3334b75 | 更改用户记忆策略，不是必然bug；需先明确全局/按媒体/临时策略及现有历史同步字段保护。 |
| 129 | [`5cbfc564f89792600e2cdf4f678e5aab256a2ad3`](https://github.com/FongMi/TV/commit/5cbfc564f89792600e2cdf4f678e5aab256a2ad3) | Update crawler runtime compatibility profiles | 暂缓/60伴随 | RuntimeModule/AbiApkPackaging/runtime_catalog变python-v3和QuickJS-v12 compatibilityId。；https://github.com/FongMi/TV/commit/5cbfc564f89792600e2cdf4f678e5aab256a2ad3 | 插件ABI与版本耦合，单改字符串会拒绝或误接二进制，当前播放器无直接收益。 |
| 130 | [`9febe402bb951c92856e26b95deae36c34248071`](https://github.com/FongMi/TV/commit/9febe402bb951c92856e26b95deae36c34248071) | Bump app version to 5.8.5 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/9febe402bb951c92856e26b95deae36c34248071.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 131 | [`51813cb611386ab3fc3fe388480cfb12d2670f67`](https://github.com/FongMi/TV/commit/51813cb611386ab3fc3fe388480cfb12d2670f67) | Cancel pending TV key seeks when controls take over | 实施125伴随 | 真实patchshowControl/pause/play/menu/back取消pending seek，SeekInput close key归属。；https://github.com/FongMi/TV/commit/51813cb611386ab3fc3fe388480cfb12d2670f67 | 只做125不处理控制接管仍有过期seek风险；共一可回滚输入修复单元。 |
| 132 | [`8b499e0972b93f6173eac0e0414485c6b894294a`](https://github.com/FongMi/TV/commit/8b499e0972b93f6173eac0e0414485c6b894294a) | Unify crawler loader lifecycle and cancellation | 实施候选/本地owner世代与single-flight适配 | BaseLoader.java:24-26 final owners、:50-55仅异步逐个clear；JarLoader.java:45-52及JsLoader:24-28用forEach，任一destroy失败可能跳过后续clear；PyLoader:38-49/JsLoader:39-51在computeIfAbsent内执行init。上游最终Js/PyLoader继承SpiderLoader，真实132按key FutureTask、closed、交换owner和拒绝迟到发布。；真实patch：https://github.com/FongMi/TV/commit/8b499e0972b93f6173eac0e0414485c6b894294a.patch | 本地已ConcurrentHashMap并非无并发保护；缺的是clear/init竞争、清理失败隔离与失败可重试。借用最终不变量而非新RuntimeModule/NodeLoader整类；保留activePluginKeys及Python ext []→{}、日志tee与Jar下载策略。 |
| 133 | [`de6fe8a0b4d5634eb2853066797f39690cd66971`](https://github.com/FongMi/TV/commit/de6fe8a0b4d5634eb2853066797f39690cd66971) | Fix crawler runtime isolation and error propagation | 暂缓/60伴随 | ResponseInterceptor deflate/close，Crypto与Python/JS/Node错误隔离。；https://github.com/FongMi/TV/commit/de6fe8a0b4d5634eb2853066797f39690cd66971 | 新runtime架构修复，流解压可单独评估但尚未证明当前同路径缺陷；不偷渡包体/二进制升级。 |
| 134 | [`1837e5afd0da5a3bd8b1bd1d63116320c7de9e15`](https://github.com/FongMi/TV/commit/1837e5afd0da5a3bd8b1bd1d63116320c7de9e15) | Handle null cache refresh rejections | 忽略/当前缓存桥未有 | Test-Path quickjs/src/main/assets/js/lib/net.js=False；Module.java:22-32仅LRU fetch/clear，无cached/refresh后台TTL loader。；真实patch：https://github.com/FongMi/TV/commit/1837e5afd0da5a3bd8b1bd1d63116320c7de9e15.patch | 真实patcherror?.message ?? String(error)修复reject(null/undefined)；当前路径不可达，若未来引入TTL缓存随契约测补，不制造独立任务。 |
| 135 | [`2e2196de0efc8a54ab91ef22389daa6a0c024ba4`](https://github.com/FongMi/TV/commit/2e2196de0efc8a54ab91ef22389daa6a0c024ba4) | Use official pywasm wheels and validate Python native artifacts | 暂缓/原生与包体门禁 | chaquo/build.gradle:14-18 Python3.10、pip requirements；:24-32双ARM；chaquo/requirements.txt:1-7现有requests/pycryptodome/lxml等，无cryptography/pywasm/numpy；chaquo/runtime目录不存在。上游135锁python-android@4cabf1d1bce37d1fc517d939c54b8087af8f9cd3，真实patchARM64 16KiB/ARM32 4KiB、版本化.so、wheel摘要与同C++校验。；真实patch：https://github.com/FongMi/TV/commit/2e2196de0efc8a54ab91ef22389daa6a0c024ba4.patch | 112的全native替换已被135生产链/ABI修正覆盖；cryptography不是现有pycryptodome等价替换，pywasm1.0.8引入NumPy/OpenBLAS/libgfortran。无爬虫样本证明刚需，不整支升级/外置runtime；只在获批依赖缺口时做同ABI可复现小制品包与许可证/加载/包体门禁。 |
| 136 | [`b4b8a9e2cd6003149153fd42730fab65f9813b87`](https://github.com/FongMi/TV/commit/b4b8a9e2cd6003149153fd42730fab65f9813b87) | Fix folder filtering and preserve navigation state | 忽略本轮/非播放 | 真实patchFolderFragment筛选复制、Cache.contains保导航上下文。；https://github.com/FongMi/TV/commit/b4b8a9e2cd6003149153fd42730fab65f9813b87 | 仅VodFragment正则命中，分类导航不是新播放器行为；需要单独UI缺陷任务。 |
| 137 | [`2120cd82a0e5585e192dbb15d7e6904b0d43bec0`](https://github.com/FongMi/TV/commit/2120cd82a0e5585e192dbb15d7e6904b0d43bec0) | Add WASM support to Java and QuickJS crawlers | 暂缓/二进制gate | 真实patchNetCache invalidate(expected)防过期覆盖、新wasm绑定及QuickJS AAR/JAR。；https://github.com/FongMi/TV/commit/2120cd82a0e5585e192dbb15d7e6904b0d43bec0 | 无当前播放收益证据；WASM执行安全/ABI/双制品来源与包体先审，不可引入未明二进制。 |
| 138 | [`4b5692363f48c507924dda69aea0b9b06e385d22`](https://github.com/FongMi/TV/commit/4b5692363f48c507924dda69aea0b9b06e385d22) | Remove unused QuickJS getPort binding and exports | 暂缓/137伴随 | 真实patch移除getPort JS绑定/export。；https://github.com/FongMi/TV/commit/4b5692363f48c507924dda69aea0b9b06e385d22 | 新QuickJS库接口清理，不能删当前爬虫依赖的旧API以套用新lib。 |
| 139 | [`caed82aea5147161529eaad8ebd749c87201430b`](https://github.com/FongMi/TV/commit/caed82aea5147161529eaad8ebd749c87201430b) | Bump app version to 5.8.6 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/caed82aea5147161529eaad8ebd749c87201430b.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 140 | [`d73a3f44a1e772bde2024cdd8bc88d7193fd18e7`](https://github.com/FongMi/TV/commit/d73a3f44a1e772bde2024cdd8bc88d7193fd18e7) | Use system APK installer for runtime modules | 暂缓/当前没有模块安装架构 | ModuleViewModel/RuntimeInstaller不存在；app/build.gradle:163-164静态依赖chaquo/quickjs；Updater.java:491-525已App APK包名/版本/签名校验。上游最终RuntimeInstaller:215-219核下载摘要和module.validate，141补版本化路径/回前台确认/资源retention。；真实patch：https://github.com/FongMi/TV/commit/d73a3f44a1e772bde2024cdd8bc88d7193fd18e7.patch | 系统APK安装流程不是JS/Python可分发模块架构的充分理由。若获批外置模块必须包名/签名/版本/ABI及用户确认/进程重启合同成套，不能套到App现有更新流；无该需求暂缓。 |
| 141 | [`dbb0f5b30aa134c12a506470f2816da1a79d40a6`](https://github.com/FongMi/TV/commit/dbb0f5b30aa134c12a506470f2816da1a79d40a6) | Refine runtime installation flow and error reporting | 暂缓/当前没有模块安装架构 | ModuleViewModel/RuntimeInstaller不存在；app/build.gradle:163-164静态依赖chaquo/quickjs；Updater.java:491-525已App APK包名/版本/签名校验。上游最终RuntimeInstaller:215-219核下载摘要和module.validate，141补版本化路径/回前台确认/资源retention。；真实patch：https://github.com/FongMi/TV/commit/dbb0f5b30aa134c12a506470f2816da1a79d40a6.patch | 系统APK安装流程不是JS/Python可分发模块架构的充分理由。若获批外置模块必须包名/签名/版本/ABI及用户确认/进程重启合同成套，不能套到App现有更新流；无该需求暂缓。 |
| 142 | [`c1c470b84e8111b0660316e842a9e4cb18fd83af`](https://github.com/FongMi/TV/commit/c1c470b84e8111b0660316e842a9e4cb18fd83af) | Unify crawler lifecycle and defer destruction until app exit | 暂缓/60伴随 | VodConfig clear(false)/destroy及BaseLoader最后退出释放，HomeActivity配置变更不destroy。；https://github.com/FongMi/TV/commit/c1c470b84e8111b0660316e842a9e4cb18fd83af | 新runtime生命周期非当前全部等效；需config reload/播放service存活/退出/Spider长连接生命周期专项。 |
| 143 | [`47a41c8ba6220f2274cfb792fa8a99990068b957`](https://github.com/FongMi/TV/commit/47a41c8ba6220f2274cfb792fa8a99990068b957) | Fix source resolution and native BT playback lifecycle | 暂缓/源解析专项 | SiteApi.resolvePlayerContent、SourceUrl.original/TorrentUrl、BT request lifecycle；local有Source/Thunder/Youtube但无SourceUrl/TorrentUrl。；https://github.com/FongMi/TV/commit/47a41c8ba6220f2274cfb792fa8a99990068b957 | 需保留当前迅雷/ISO/MPD/去广/预缓存适配，解析预载与BT会话所有权不可整体移植。 |
| 144 | [`8626650ebcbc0d028e16d859e826fcf439720f3a`](https://github.com/FongMi/TV/commit/8626650ebcbc0d028e16d859e826fcf439720f3a) | Bump app version to 5.8.7 | 忽略/上游发布维护 | app/build.gradle:41-42 当前versionCode=560/versionName=5.6.0；仅记录该提交对应的上游版本递增，不作为本地版本升级建议。；真实patch：https://github.com/FongMi/TV/commit/8626650ebcbc0d028e16d859e826fcf439720f3a.patch | 不产生能力；版本号不能借合入候选代替WebHTV发布节奏。 |
| 145 | [`305dff233cde512e2c83f71529b7c3ecfd61fefc`](https://github.com/FongMi/TV/commit/305dff233cde512e2c83f71529b7c3ecfd61fefc) | Restore legacy spider initialization | 忽略/142伴随兼容提醒 | 真实patch恢复Spider.init(Context)并init(Context,String)委托；local旧Spider init接口仍在旧runtime。；https://github.com/FongMi/TV/commit/305dff233cde512e2c83f71529b7c3ecfd61fefc | 不是新能力；若升级142须带旧Jar反射兼容测试，当前不要移除旧接口。 |
| 146 | [`c62c10385ea969754c89bd40a8c679b5c92bd40d`](https://github.com/FongMi/TV/commit/c62c10385ea969754c89bd40a8c679b5c92bd40d) | Refresh paired QuickJS WASM artifacts | 暂缓/成对SDK与运行时制品 | 本地quickjs/build.gradle:25-26使用Maven wrapper-android/java3.2.3；quickjs/libs/runtime-release.aar不存在。146生产quickjs-wrapper@f7d4266f3fd8020508e41dc2eb00606c9af49e3f；148@b0cca8be5cd90a1c6baddf18eda3b0ee7fe4ba20，后者真实patch锁QuickJS@71da48ec49e6163d130f7f9001258959e3a319c2和Chicory@6bc7cf8bb6c1a8284d8346c32ea32a15c526d096。；真实patch：https://github.com/FongMi/TV/commit/c62c10385ea969754c89bd40a8c679b5c92bd40d.patch | 146已被148配对更新覆盖；不能只换Java JAR或JNI AAR，WASM/Chicory不是当前3.2.3的drop-in。上游148写35 JNI导出/ABI/16KiB与126/127host用例，但设备未验、外部SFHD失败；收益未实测，当前无刚需先暂缓。 |
| 147 | [`bce7b8b29d43bb0b00184fc93fef26fa7e525f0c`](https://github.com/FongMi/TV/commit/bce7b8b29d43bb0b00184fc93fef26fa7e525f0c) | Remove axis-specific mobile pinch scaling | 忽略 | 真实patch删mobile axis-specific PinchScale及反馈字符串。；https://github.com/FongMi/TV/commit/bce7b8b29d43bb0b00184fc93fef26fa7e525f0c | 新TouchInput手势产品取舍，不是当前缺失播放能力，不删既有缩放功能。 |
| 148 | [`18fba7b66b1082fd135f9881ae7aa8bedbaa1e67`](https://github.com/FongMi/TV/commit/18fba7b66b1082fd135f9881ae7aa8bedbaa1e67) | Refresh paired QuickJS runtime artifacts | 暂缓/成对SDK与运行时制品 | 本地quickjs/build.gradle:25-26使用Maven wrapper-android/java3.2.3；quickjs/libs/runtime-release.aar不存在。146生产quickjs-wrapper@f7d4266f3fd8020508e41dc2eb00606c9af49e3f；148@b0cca8be5cd90a1c6baddf18eda3b0ee7fe4ba20，后者真实patch锁QuickJS@71da48ec49e6163d130f7f9001258959e3a319c2和Chicory@6bc7cf8bb6c1a8284d8346c32ea32a15c526d096。；真实patch：https://github.com/FongMi/TV/commit/18fba7b66b1082fd135f9881ae7aa8bedbaa1e67.patch | 146已被148配对更新覆盖；不能只换Java JAR或JNI AAR，WASM/Chicory不是当前3.2.3的drop-in。上游148写35 JNI导出/ABI/16KiB与126/127host用例，但设备未验、外部SFHD失败；收益未实测，当前无刚需先暂缓。 |
| 149 | [`04ab460fa5ae78f480b610b8ef6f91d77e65be6d`](https://github.com/FongMi/TV/commit/04ab460fa5ae78f480b610b8ef6f91d77e65be6d) | Add frame interpolation settings for mpv and ExoPlayer | 暂缓/partial新运动链 | 真实patchExo FfmpegMotionRenderersFactory(motionFps默认60,480,enabled)、新mpv motion设置；localMpvPlayerEngine:1014只是通用interpolation=yes/no，非光流/60-120fps；没有MotionRenderers新类证据。；https://github.com/FongMi/TV/commit/04ab460fa5ae78f480b610b8ef6f91d77e65be6d | 两侧新运动插帧暂缓源码→补丁→制品→ABI→设备性能/质量 gate；不得把通用GPU平滑算新motion已覆盖，更不可替换本地DV/FEL/libass音频renderer。 |
| 150 | [`a51861220ec80290e8f9628e9fe0d7e958f58ebf`](https://github.com/FongMi/TV/commit/a51861220ec80290e8f9628e9fe0d7e958f58ebf) | Restore app casting playback progress | 暂缓/partial | 真实patchcast传history copy/pic缓存、VodHistoryPolicy优先cast进度；local有PlaybackProgressWriter与历史同步，但无VodHistoryPolicy。；https://github.com/FongMi/TV/commit/a51861220ec80290e8f9628e9fe0d7e958f58ebf | 投屏进度不是本地历史写入全覆盖；需实际跨设备cast入口复现、单位/身份/explicit episode优先级门禁。 |
| 151 | [`6e9dd1bb3065d205dfe4774add9578ea2e52ea2f`](https://github.com/FongMi/TV/commit/6e9dd1bb3065d205dfe4774add9578ea2e52ea2f) | Fix playback state and ownership restoration | 暂缓/61所有权伴随 | 真实patchisNativePlaybackCurrent、PlayerManager track state归属恢复/当前player video尺寸；local独立PlaybackService/Activity所有权。；https://github.com/FongMi/TV/commit/6e9dd1bb3065d205dfe4774add9578ea2e52ea2f | 避免直接替换track manager与字段；切源/页面重绑/后台状态需用现有generation局部适配。 |
| 152 | [`fa5a39261ba053174b3a2b100c4a1073e2bd39c0`](https://github.com/FongMi/TV/commit/fa5a39261ba053174b3a2b100c4a1073e2bd39c0) | Keep video sizing and danmaku updates with current playback | 实施候选/需竞态复现 | 真实patchsearchDanmaku引入isCurrentResult Result身份guard、旧binding取消条件；localVideoActivity:1236直接player()::setDanmaku，DanmakuApi:83-98有HTTP cancel但无generation。；https://github.com/FongMi/TV/commit/fa5a39261ba053174b3a2b100c4a1073e2bd39c0 | HTTP cancel不撤销已经post的旧callback；加source/session/Result token，确认当前前台/native拥有者后apply，不整控制器移植。 |
| 153 | [`dea4faae30b807dc460f063780c985961b5749aa`](https://github.com/FongMi/TV/commit/dea4faae30b807dc460f063780c985961b5749aa) | Restore browser client tracking when playback service rebinds | 暂缓/局部服务gate | 真实patchPlaybackService.onRebind恢复clients.bind，onUnbind返回browser可重绑；localService:175/183有bind/unbind，无onRebind与MediaClients新类。；https://github.com/FongMi/TV/commit/dea4faae30b807dc460f063780c985961b5749aa | 新browse client计数bug可能不在当前路径；先证实MediaSession/Android Auto浏览重绑影响再窄补，不能宣称当前必然退出。 |
| 154 | [`4512b4c9f82534ff66acfde06ccdd68f35b074f0`](https://github.com/FongMi/TV/commit/4512b4c9f82534ff66acfde06ccdd68f35b074f0) | Support explicit episode selection from crawler lists | 暂缓/explicit episode gate | 真实patchVod playId/playName贯穿创建Intent与VodHistoryPolicy优先级；local有Flag/Episode与历史进度但无VodHistoryPolicy。；https://github.com/FongMi/TV/commit/4512b4c9f82534ff66acfde06ccdd68f35b074f0 | 爬虫列表指定集可有价值，但必须保留已有WebHome/外部推送/历史优先级，确证playId生产者后再小范围适配。 |

## 5. 已发现风险及处置；证据来源与限制

- 代理API兼容疑点：本地OkHttp5.4.0和官方同版Interceptor.Chain已核对，有withProxySelector/getProxy/getProxySelector；改为适配本地语义门槛，不误报必须升级。
- R8疑点：已回派核实，Gson2.14.0随库规则保护Res的SerializedName字段和无参构造；忽略重复keep，不声称已有Release实测。
- ZIP/路径风险：源码缺口独立核实；路由为有限配额、显式失败、原子写与根约束适配，不能照抄默认不限额或静默跳过。
- 字幕身份/历史/封面风险：UUID与ASS index、短期内存URL与持久History不能直接混用；分别路由窄映射或暂缓。
- 新旧插值混淆：本地interpolation已有，不等同新motion-interpolation/FFmpegMotionRenderersFactory；两侧新能力均进入依赖/性能门禁，暂缓。
- loader/异步风险：失败回收、owner世代、旧全局Module.clear、迟到UI回调分别在适配计划中，不以网络cancel代替世代验证。
- 网页执行/模块/WASM/原生供应链风险：暂缓拆簇，必须显式本地授权、Host/Origin、路径/会话/大小限额和成对SDK/JNI验证。
- 无整支升级、无生产修复、无Gradle/native/设备测试、无性能/包体实测；不把源代码或上游测试说明冒充本机验收。

官方OkHttp来源（已读200）：https://raw.githubusercontent.com/square/okhttp/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Interceptor.kt
官方Gson来源（子审查已读）：https://raw.githubusercontent.com/google/gson/gson-parent-2.14.0/gson/src/main/resources/META-INF/proguard/gson.pro
Chaquopy官方当前文档（已读，版本差异要复核）：https://chaquo.com/chaquopy/doc/current/android.html
Android ZIP/SAF/16KiB官方页读取受阻，当前无完整最佳实践通过结论；实施前补证，不换不安全网络/不执行下载脚本。

### early审查限制
- 评估基线Silent1566 47454687f504955775b398905ba09d73aeb9f20c；冻结FongMi/TV4512b4c9f82534ff66acfde06ccdd68f35b074f0；共同基点4573249cd6679c6becca9957f6a011bf6c4a5afd；旧侧TV1a19fee278fa2234da725d61a53bf59b69fe9127。
- early 审查顺序以本文第 4 节编号 1–45 为准；本机辅助清单为 .git/tv-assessment-commits.json。第 46 条属于 player 审查组，不在 early 组范围内。
- 前3–20以diff-tree --raw完整blob/path identity证明新旧patch身份相同，未称每棵tree相同；1/2真实effects迁移Media3 API，需依赖链不能按deleted路径称退化。
- 未实施、未Gradle/native/实机/性能测试；仅授权.git/tv-assessment-early.json证据输出，app/.cxx未读取或改动。
- 官方Android安全/SAF资料无法获得，Firecrawl403且已有记录代理127.0.0.1:7897拒连；Chaquopy官方17.2页面可得，17.0确切源码消费尚未验证；所有此类风险已路由适配或暂停门槛。
- 巨型1/2/8/36按行为簇+代表契约及当前本地审查，不代表所有媒体跨仓库产物/API均做完最佳实践gate；禁止整支升级。
- 首轮promisor git show stderr出现auto packing；随后所有上游读取加-c gc.auto=0 -c maintenance.auto=false，无显式改config/ref/branch；按需blob本身是授权缓存读取。
- 已发现风险均在候选risks/adaptation/verification中路由；尚无候选被宣称可原样合入。

### player审查限制
- 评估而非实施：未改生产代码、branch/ref、lock、制品；仅写父节点授权.git/tv-assessment-player.json，保护app/.cxx/。
- 逐条git show真实patch读取，巨型60/61/64/121仅架构与关键行为hunk/路径审查，未完成355文件逐行深审，已转暂缓gate；所有60有明确处置，不等于全部行为已验证。
- 115首次promisor blob SSL失败；第二次限定路径成功读到Exo/mpv decoder retry移除，不宣称9文件全部深审。
- 未运行Gradle/native大构建、设备测试、性能A/B或制品重建。收益/包体大小只推断，不能作实测结论。
- 仅本地源码/真实上游patch/docs/Skill；没有泛搜。外部URL为每commit https://github.com/FongMi/TV/commit/<fullhash>，git promisor源https://github.com/FongMi/TV.git；父节点官方OkHttp证据https://raw.githubusercontent.com/square/okhttp/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Interceptor.kt 是父节点读取，本子节点未HTTP复读。
- 关联新motion/ContentTypeResolving/TrackSelectionUtil native/media准确source全hash仍缺：暂缓而非推断已打包；现有media hash e3e922d5c01bc0b564849940fe589daf37360d15来自local lock。
- 命令主要git rev-parse/status（HEAD匹配，?? app/.cxx/）、Get-Content JSON（player60）、git --git-dir=.git/upstream-tv-assessment.git show 全60、Select-String/rg local符号；本机gradle缓存未定位AAR是无证据，不是类不存在证明。
- 只形成最多8决策包，候选7包含两种必须拆开的暂缓能力，不应当成同一实施task；各候选仍需归入既有E/P/C编号、唯一docs与单独用户批准。

### common审查限制
- 执行run=1；只新增父节点授权的.git/tv-assessment-common.json，生产源码/文档/引用/分支/artifact未修改，app/.cxx/保持保护。
- 基线只读核实Silent1566@47454687f504955775b398905ba09d73aeb9f20c；只按JSON group=common49条，redirect/nullhost/content不复制/STRM及大970862c属于父节点其他组，未重复研究。
- 真实patch通过https://github.com/FongMi/TV/commit/<full40>.patch内存读取；裸缓存缺blob且关闭lazy fetch，不写对象；127.0.0.1:7897拒绝后改系统默认直连；HTTP读取无需执行下载内容。
- 已读canonical Skill及integration-workflow/evidence-and-research/webhtv-player-gates、README和活动索引相关段落；旧侧20提交log核对。重放判断以当前可触达树为主，无声称patch-id全匹配；父节点负责全154覆盖。
- 关联只追精确hash；python-android4cab与quickjs-wrapper b0真实patch已读。GitHub API403限流后换.git patch，未扫描关联全历史；148与d432948cd72f51693aa2bfa0333674dcf6783138同树是上游提交说明，未独立treehash验证。
- Gson2.14.0官方随库规则成功读取；Chaquopy官方Gradle/API/ABI/包体文档成功读取，https://chaquo.com/chaquopy/doc/current/android.html；Android16KiB官方https://developer.android.com/guide/practices/page-sizes抓取失败，不冒充已完成外部best-practice门禁。
- 未做构建、测试、设备/性能实测、APK/二进制SHA256或ELF检查；上游patch内测试数据只记来源，不冒充本机测试。性能/资源/包体收益均推断或未定，候选实施前需唯一任务文档和完整风险比例研究+用户批准。
- 所有已发现风险均路由到窄适配或暂缓：失败清理/owner世代/响应规范/404为适配，远程执行/模块原生/WASM/目录导航为暂缓，Gson已覆盖不制造缺陷。

## Checkpoint 1: 最终源码级评估与下一步

- 完成：154条唯一完整hash/处置覆盖，三组证据及独立关键源码/API复核。
- 文件：仅本文新增；证据在.git/tv-assessment-{commits,early,player,common}.json；上游阅读缓存.git/upstream-tv-assessment.git。
- 保护：Silent1566/HEAD不变，原有app/.cxx/由guard校验；无lock/native/App运行行为修改。
- 验证：本次复审已核对 154 条编号、完整 hash、标题及三组处置，冻结历史范围为旧侧 20 条、当前侧 154 条；最终文档检查使用 checkpoint 脚本，提交时由 task guard finish 校验保护路径、范围并创建恢复标签，结果在交付摘要报告。
- 未做：候选功能实施、构建、设备/性能/ABI测量。本次仅授权评审修订后的文档提交、恢复标签及推送；assessment guard 在文档验证通过后通过 finish 关闭，不能把文档交付解释为任何功能已验收。
- 下一步：选择一个高价值窄适配阶段，补齐唯一任务文档及必需设计/验证，不能直接整支merge。

## 首批实施授权（2026-11-08）

用户已在确认五个窄适配单元后明确说“开始吧”。仅实施下列六个源提交，不包含文件安全、认证大改、模块/WASM、插帧或依赖/native升级。原工作区未提交评估及app/.cxx保持原样，代码在独立工作树实现。

| 唯一任务ID | 唯一任务文件 | 已批准边界 |
| --- | --- | --- |
| C-TV-STRM | [HTTP STRM](C-TV-STRM-http-pointer.md) | 有界首行解析、合法目标和HTTP失败原URL回退；不改Source/file/content语义 |
| C-TV-PYTHON-CLEANUP | [初始化失败回收](C-TV-PYTHON-CLEANUP-failed-init.md) | 一次失败实例回收、失败不缓存、非nullfallback；不改整loader owner/runtime |
| C-TV-PROXY-RESPONSE | [代理响应兼容](C-TV-PROXY-RESPONSE-compatibility.md) | 响应头/null/bytes规范；保留3/4/5项base64，不升级SDK/JNI |
| C-TV-SEEK-OWNERSHIP | [TV seek所有权](C-TV-SEEK-OWNERSHIP-tv-keys.md) | 两条seek提交合为一单元、250ms不变、取消完整TV入口，不改变换台/音频/PiP |
| C-TV-ASYNC-RESULT-OWNERSHIP | [弹幕查询所有权](C-TV-ASYNC-RESULT-OWNERSHIP-danmaku.md) | 双端当前会话校验，拒绝迟到回调，不迁controller/不取消新owner |

本文为五任务索引；各任务自己的设计、实现、验收和回滚只写入上表唯一文件。进度与具体提交在最终集成后统一归档，不以源提交直接cherry-pick作为实施。

## 最终集成归档 — 2026-11-08

| 单元 | 原始代码/修复完整提交 | 单元测试结果 |
| --- | --- | --- |
| [C-TV-STRM](C-TV-STRM-http-pointer.md) | `38d2670e10eb69fa8c00c3bbd8df48b07d98f1db` | 16项定向测试通过；HTTP/Source公共链，不等于decoder实播 |
| [C-TV-PYTHON-CLEANUP](C-TV-PYTHON-CLEANUP-failed-init.md) | `d3a7298d5f3a6f76f5f2a256d4fd6f02ff7cecef` → `c861fac058e89136a6a2be233262b532fb4ceee6` | 24项定向测试通过；F1诊断后备已修复且独立复审通过；原生资源归零未设备测 |
| [C-TV-PROXY-RESPONSE](C-TV-PROXY-RESPONSE-compatibility.md) | `e81ae22a1cdc4d026d6d5ecbbb11b96ddc3e9103` → `93d2217d00156a672f47d1405bb97dac5471ba16` | 18项定向测试通过；测试source set修正后Mobile单测编译通过；QuickJS/Python完整native链未测 |
| [C-TV-SEEK-OWNERSHIP](C-TV-SEEK-OWNERSHIP-tv-keys.md) | `dc5f1e60c4f96f88f5183cbf11b50bf97f2c6b65` | 21项真实输入/消费者测试和双端Java编译通过；完整设备生命周期未测 |
| [C-TV-ASYNC-RESULT-OWNERSHIP](C-TV-ASYNC-RESULT-OWNERSHIP-danmaku.md) | `1425d0118975ed9412cb85a688a8de9f0f6b3c10` → `3e39a08eb900b92579e90a68782e5ce18d519b9d` → `4a4907c859f09468e7624a1803e9e68c4e446323` | 23项单元测试通过；F2/F3/F4复审ADDRESSED；Mobile真消费者/ARM设备未测；R1为已知未修风险 |

- 采用私有分支no-commit集成merge保留各原始code commit和recovery tag，不复制/重写历史；联合验证通过后才原子提交集成状态。
- 联合定向测试最终一次（代码末态 4a4907c8）：9类102项0失败/0错误/0跳过，Leanback/Mobile Debug Java及Mobile UnitTestJavaCompile通过；日志 tree reports/TV-SMALL-FIXES-FINAL-4a4907c8.log。整批判定为 Spec PASS / Quality approved（F2/F3/F4均ADDRESSED），R1列入已知未修风险。
- 原工作区Silent1566及其app/.cxx和未提交旧评估报告保持原样；所有提交/tag仅本地，未推送/未安装候选。
- 独立审查已路由并修复：失败诊断打印可逃逸(F1)；Robo测试错误放common导致Mobile单测编译回归。其它STRM保基线协议观察未扩大scope。
