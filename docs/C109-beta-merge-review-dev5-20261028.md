# C109 · dev5 合并审查与「按 configKey 桥接」修复记录（2026-10-28）

## Recovery anchor

- **目标**：让「PC 端通过本机网关按 configKey 桥接安卓已保存配置」这条路径真正做到非侵入——不杀激活配置的 node、不污染激活配置的 spider 缓存、configKey 失效时明确报错而不是静默回落到激活配置、configKey 的解析与仓库既有解析器收敛成一套；并补上锁住这些契约的测试。
- **验收**：B1–B4、M1–M4 逐条有改动与证据；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 与 `:app:compileMobileArm64_v8aDebugJavaWithJavac` 双 flavour 编译通过；定向单测全绿；全量单测失败集与既有 8 条环境性失败基线一致；`git diff --check` 无输出。
- **Lane**：`quick-fix`（范围受限的缺陷修复，不含新功能、不含依赖或二进制变更）。
- **当前状态**：第三轮（B2 `?do=js`/`?do=py` 子路由的 `recent` 命名空间补修 + 真实行为测试）与第四轮的记录更正（recent 改动的真实覆盖边界如实化、`?do=js`/`?do=py` 对桥接配置仍不可用登记为已知风险、两处测试归类更正）均已完成，代码与测试改动全部落盘，双 flavour 编译通过、定向单测 55 例全绿、全量单测 `4725 tests / 8 failed (与基线逐条同名同因) / 3 skipped`、`git diff --check` 无输出；**改动未提交、未打标签**（提交与打标签由任务委派方执行）。第二轮的状态是 `4721 tests / 51 例定向`，本轮再 +4 例。

## 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并提交 | `1790e1741702102df845c4d9fe4fda8f68e04edc`「Merge remote-tracking branch 'origin/beta' into dev5」 |
| 父提交 | `b501538b77f8bd251b5d5746bd05296304e7bf0f`（dev5）+ `93b57d4f23b2200d7759baa39a9f8760ac401767`（origin/beta） |
| beta 目标 | `93b57d4f23b2200d7759baa39a9f8760ac401767` |
| 冲突 | 无。合并为标准的双父 merge，未产生冲突解决改动。 |
| 分支状态 | 工作区起点干净；`origin/beta..HEAD` 只有 3 个提交：`1790e17417`、`b501538b77`、`d4b965db96` |

origin/beta 带入的 58 个提交内容本轮**未改动**。

## 被评审的 2 个提交及其结论

| 提交 | 内容 | 独立评审结论 |
| --- | --- | --- |
| `d4b965db96` | vod-api: serve any config's sites via configKey without touching the active source（VodSiteCatalog 新增、BaseLoader 改造、VodApi、VodApiTest） | 不可通过 |
| `b501538b77` | sync: push bridged history into the target config without switching the active source（VodSiteCatalog、History、Action、HistoryScopeSyncTest） | 不可通过 |

评审给出的 4 条 BLOCKER 已逐条复验成立，8 条 MAJOR 中本轮修 4 条（M1–M4），M5 需产品批准后单独实施。两条提交**未被回滚或改写**，修正在工作区以叠加改动的方式给出。

**设计意图本身成立**：网关按 configKey 服务任意配置的站源、按 configKey 把历史写进目标配置，安卓只当运行容器。出问题的是实现细节的四处外溢，而不是方向。

## B1–B4 修复记录

### B1 · 猫源 bundle 会杀掉激活配置的 node 子进程

- **缺陷链**：`VodSiteCatalog.java:178`（原）`CatSource.isBundle(configUrl) ? CatSource.serve(configUrl)` → `CatSource.java:42-66` 里 `latch.await(START_TIMEOUT_MS)`（12 分钟）→ `NodeRuntime.java:156-159` 只要 `running` 或 `servingUrl` 非空就 `NodeService.stop` + `waitForProcessDeath` + 重启。`NodeRuntime.servingUrl` 是全局单例（`NodeRuntime.java:55`），激活配置 A 的 `/config` 也会跟着换成 B 的。调用线程最长阻塞 12 分钟，局域网内可被当作 DoS。
- **改后行为**：
  - `NodeRuntime.java:217-220` 新增只读判定 `public static boolean isServing(String url)`，内部复用既有 `private static boolean same(String url)`（`NodeRuntime.java:223-226`）的比对语义；`start()` 的换源语义一行未动。（第一轮这个判定只比 `servingUrl`，存活校验是第二轮补的，见下面的「B1 补修·第二轮」。）
  - `CatSource.java:79-82` 新增 `public static String reuse(String url)`：不在服务该 bundle 时直接抛 `IllegalStateException("该配置的猫源需要另起 node 运行时，为避免打断当前激活配置已拒绝解析")`，在服务时只返回 `NodeRuntime.configUrl()`。
  - `VodSiteCatalog.java:224-227` 的 `objectOf` 改用 `CatSource.isBundle(configUrl) ? CatSource.reuse(configUrl) : UrlUtil.convert(configUrl)`。
- **为什么这是最小修法**：桥接路径唯一需要的只是「拿到已在服务的那个 node 的 /config 端口」。新增的是一个纯只读判定 + 一个拒绝分支，`serve`/`start` 的换源与重启逻辑保持原样，因此激活配置（不带 configKey）路径的行为逐字节不变，而跨 bundle 的桥接请求变成一次快速失败而不是一次进程杀灭。
- **证据**：`VodApiTest.nonActiveBundleIsRejectedInsteadOfRestartingNode`（`app/src/test/java/com/fongmi/android/tv/server/process/VodApiTest.java:511`）实际调用 `CatSource.reuse(...)` 并断言抛 `IllegalStateException` 且信息含「已拒绝解析」，同时断言 `VodSiteCatalog` 源码含 `CatSource.reuse(`、不含 `CatSource.serve(`，`NodeRuntime` 含 `public static boolean isServing(String url)`。

#### B1 补修 · 第二轮：isServing 缺少存活校验

- **第一轮的实际缺口**：`NodeRuntime.java` 的 `isServing` 原实现是 `return !servingUrl.isEmpty() && same(url);`，只看 `servingUrl`。两种真实状态会骗过它：
  1. `NodeRuntime.timeout()`（`NodeRuntime.java:186-201`）把 `running` 置 false、`port` 置 0，但 `servingUrl` 保留；
  2. `:node` 子进程被系统杀掉时 `running` 甚至不会被改写（`start()` 里 `boolean alive = running && serviceAlive()` 的「运行时还标着 running 但进程已经没了」分支正是为此存在）。

  这两态下 `isServing` 都返回 true，于是 `CatSource.reuse`（`CatSource.java:79-82`）返回 `NodeRuntime.configUrl()` = `http://127.0.0.1:0/config` 这种无效地址——桥接解析随后必然失败，且错误信息指向网络而不是「运行时已停」。
- **本轮修法**（`NodeRuntime.java:217-220`）：
  ```java
  public static boolean isServing(String url) {
      if (servingUrl.isEmpty() || !running || port <= 0) return false;
      return serviceAlive() && same(url);
  }
  ```
  三次 volatile 读先短路上「明显不成立」的情况，成立后才复用既有的 `private static boolean serviceAlive()`（`NodeRuntime.java:103-110`，内部 `isProcessRunning(App.get(), pkg + ":node")`）与 `port()`。`start()`/`timeout()`/`same()` 一行未动，`serve`/换源语义不变；`serviceAlive()` 本身也不阻塞（只查 `ActivityManager` 的进程列表）。
- **证据**：新增 `NodeRuntimeServingTest`（`app/src/test/java/com/fongmi/android/tv/node/NodeRuntimeServingTest.java`）用反射写 `servingUrl`/`running`/`port` 三个真实字段（与既有 `NodePortSelectionTest` 反射私有方法的做法一致），断言：三条都成立时为 true；`port=0` 为 false；`running=false` 为 false；`servingUrl` 为空为 false；bundle 不同为 false。**说明**：`:node` 进程被杀这一态在纯 JVM 里造不出来（`App.get()` 为 null 时 `serviceAlive()` 按设计 fail-open 返回 true），所以「`serviceAlive()` 参与判定」这半句由源码契约在同一个测试类里锁定（`isServing` 体内必须出现 `serviceAlive()` / `port <= 0` / `!running`，且不得含 `sleep`/`await`/`wait(`）。

### B2 · 全局 spider 缓存只按 siteKey 建键，桥接解析串源

- **缺陷链**：`Site.spider()` → `BaseLoader.java:110`（原）`catSpiders.computeIfAbsent(key, k -> new CatSpider(api))`；js/py 分别在 `JsLoader.java:42-43`、`PyLoader.java:44-45` 同样按 siteKey 建键；只有 `JarLoader` 的 dex 键里带 jar md5。清空只发生在 `VodConfig.java:149`。于是 A、B 都有同一 siteKey 时，桥接先建出的 B 实例会被激活配置 A 命中。
- **隔离方案（选定：缓存键加命名空间）**：
  - `BaseLoader.java:47-49` `public static String slot(String namespace, String key)`：命名空间为空（含 null）时**原样返回 key**，否则返回 `namespace + "@@@" + key`。
  - `BaseLoader.java:52-60` `public static java.util.Set<String> baseKeys(java.util.Collection<String> slots)`：按 `@@@` 截断还原成裸 siteKey，供 `activePluginKeys()` 继续保护插件脚本。
  - `BaseLoader.java:136-144` 新增 5 参 `getSpider(namespace, key, api, ext, jar)`，猫源槽用 `catSpiders.computeIfAbsent(slot, k -> new CatSpider(api))`；4 参版本 `BaseLoader.java:125` 委托 `getSpider("", ...)`，因此激活配置路径的键与改动前逐字节相同。
  - `JsLoader.java:51-52`、`PyLoader.java:53-54` 的 `spiders.computeIfAbsent(BaseLoader.slot(namespace, key), ...)`；`JarLoader.java:225-227` 的 `String jaKey = Util.md5(jar); String spKey = jaKey + BaseLoader.slot(namespace, key);`（`loaders` 仍只按 jar md5 建键，dex 复用不受影响）。
  - `JsLoader.java:40`、`PyLoader.java:42` 的 `activeKeys()` 改为 `BaseLoader.baseKeys(spiders.keySet())`，`CacheCleanupManager` 的插件脚本保护据此仍看到裸 key。
  - 桥接入口 `BaseLoader.java:178-183` 在最后一行用 `VodSiteCatalog.namespace(configKey)` 作命名空间。
- **为什么不选「桥接路径自持一份有界缓存」**：自持缓存要为 cat/js/py/jar 四种 spider 各写一份带淘汰的容器，还要自己管 `Spider.destroy()`/DexClassLoader 生命周期与并发初始化，代码量与回归面都明显大于给现有 4 个 map 的键加一个前缀；而前缀方案天然复用既有的 `clear()`/`activeKeys()`/初始化锁语义，空命名空间退化后与现状严格等价，所以它才是更小、也更容易被测试锁住的那条路。
- **兼容性论证**：空命名空间 → `slot` 返回原 key，四个 loader 的键、`setRecent` 的 recent 语义（`BaseLoader.setRecent` 用的仍是裸 siteKey / jar md5）以及 `activeKeys()` 的返回集合都没有变化；只有带 configKey 的桥接请求会落到一个新槽位。
- **第一轮的覆盖边界（必须如实说明）**：上面这套槽位方案在第一轮只接到了 `/proxy` 路径——真正读命名空间的是 `BaseLoader.java:178-183` 的 `catalogSpider(configKey, siteKey)`，而它只被 `proxy(Map)` 调用。`/vod/api` 的两条主路径当时仍然走**空命名空间**：
  - `VodApi.java:103` `String proxyKey = site != null && !site.isEmpty() && site.getType() == 3 && supportsDirectProxy(site.spider()) ? key : "";`
  - `VodApi.java:152-154` `if (site.getType() == 3) { ... Spider spider = site.spider(); ... }`

  这两处拿到的 `site` 来自 `findSite(catalog, key)`，即由 configKey 桥接出来的站源；而第一轮的 `Site.java:484-486` `public Spider spider()` 调的是四参 `BaseLoader.get().getSpider(getKey(), getApi(), getExt(), getJar())`（= 空命名空间）。后果是：/vod/api?ac=config 生成的桥接站源（`VodApi.configJson` `type="4"` + `configKey`）在 PC 侧走标准搜索/详情/播放流程、命中 `type==3` 分支时，会把非激活配置的 spider 实例写进激活配置的同名槽位；反向也会让非激活配置拿到激活配置的实例——**双向串源**，用户可见（搜到别的配置的站点结果、播放地址错源）。第一轮把「B2 已修复」写完整属于记录不实。
- **本轮（第二轮）补的部分**：把命名空间从 `/proxy` 路径补齐到 `/vod/api` 主路径，做法是把「哪条配置」这个事实记在桥接出来的 `Site` 上，而不是在 `VodApi` 里再传一遍参数：
  - `Site.java:133-141` 新增纯运行时标记 `@Ignore private transient String namespace;`（`transient` + `@Ignore`：既不进 Room 列，也不进 Gson，也不进 Parcel）。
  - `Site.java:490-496` `getNamespace()`（空值归一为 `""`，只 `trim`）与 `setNamespace(String)`（空串归一为 `null`）。
  - `Site.java:506-510` `public Spider spider() { return BaseLoader.get().getSpider(getNamespace(), getKey(), getApi(), getExt(), getJar()); }`——四参改五参。
  - `VodSiteCatalog.java:119-129` `newDatabase()` 里 `String id = namespace(interfaceKey)` 之后调用 `catalogOf(findConfig(id), id)`，即命名空间就是该 Catalog 的 configKey。
  - `VodSiteCatalog.java:208-211` 新增 `catalogOf(Config, String namespace)`；`VodSiteCatalog.java:225-245` 新增 `catalogOf(JsonObject, String namespace)`，在**两处 `CustomCspSetting.inject` 合并之后**执行 `VodSiteCatalog.java:242-243` `if (!isBlank(namespace)) for (Site site : sites) site.setNamespace(namespace);`（合并进来的自定义 CSP 站源也必须带上命名空间）。
  - `VodSiteCatalog.java:193-195/198-199/214-216` 三个旧的公开入口保留并委托空命名空间：`sitesOf(Config)`、`catalogOf(Config)`、`catalogOf(JsonObject)`。
  - `VodApi.java` **零改动**：它的 `findSite(catalog, key)` 现在拿到的是已打标的 Site，于是 `:103` 与 `:154` 两处 `site.spider()` 自动按 configKey 落槽。
- **B2 的最终覆盖边界（如实，不再声称「双路径闭合」）**——第二轮那句「`/proxy` + `/vod/api` 双路径闭合」是过度声称，第三轮复评已推翻并在此更正。真实边界如下：
  - **已闭合（主路径）**：
    1. `/proxy?do=csp|...`：`BaseLoader.proxy(Map)` 见到 `configKey` 即走 `catalogSpider(configKey, siteKey)`，命名空间取自 `VodSiteCatalog.namespace(configKey)`（第一轮）。
    2. `/vod/api` 主路径（搜索/详情/播放，即 `type==3` 的 `site.spider()` 与 `proxyKey` 外化）：命名空间由 `VodSiteCatalog` 写在桥接出来的 `Site` 上（第二轮）。
  - **第三轮补上的**：`?do=js` / `?do=py` 两个**子路由**。这两个请求由站源脚本自己生成，生产端（`quickjs/.../method/Global.java:115-117` 的 `return Proxy.getUrl(local) + "?do=js";`、`chaquo/.../base/spider.py:114-115` 的 `?do=py`）既不传 `siteKey` 也不传 `configKey`，所以 `BaseLoader.proxy` 只能落到 `jsLoader.proxy(params)` / `pyLoader.proxy(params)`，由 loader 的 `recent` 定位实例。第二轮的 `spiders` 表键已带命名空间而 `recent` 仍是裸 siteKey，于是桥接配置的脚本请求要么查不到自己（返回 null → HTTP INVALID_RESPONSE），要么命中激活配置里同名站源的实例（B2 同类串源在该子路由复现）。第三轮把 `recent` 一并改成槽位键（见「第三轮改动」一节），使**存侧与查侧的键形状严格同形**，并为未来传入命名空间的调用方准备好正确语义。
  - **重要边界（第四轮复评推翻，如实更正）**：`recent` 的命名空间**在生产链路里恒为空**，因此上述修复在当前产品里是**休眠的**，该子路由在「哪条配置」这一维度上**并未闭合**。证据：`BaseLoader.java:170-174` 是 recent 的唯一生产写入入口，js/py 分支的 namespace 由调用方给；唯一传命名空间的生产调用方是 `Site.java:502` `BaseLoader.get().setRecent(getNamespace(), getKey(), getApi(), getJar());`，而 `Site.recent()` 的全部生产调用点（`SiteApi.java:77/127/174/302/431`、`SpiderApi.java:87`、`ActionCardHelper.java:94`、`CspWarmup.java:85`）取得的 Site 一律来自 `VodConfig.get().getSite(key)`，其 `namespace` 恒为 `""`（只有 `VodSiteCatalog.java:243` 的 `catalogOf` 才 `setNamespace`）；桥接主路径 `VodApi.java:152-154` 明确不调用 recent，且 `VodApiTest.java:446` 用 `throw new AssertionError("Catalog must not change the active loader")` 把「不切全局 recent loader」钉成契约。故 `slot("", k) == k`，`JsLoader.java:77-81`/`PyLoader.java:96-100` 的 `spiders.get(recent)` 与第三轮改动前逐字节等价。
  - **仍未闭环（不强行改设计，登记为限制）**：`js2Proxy` 子路由（`Global.java:121-123` 的 `js2Proxy` → `String.format("&from=catvod&siteType=%s&siteKey=%s&header=%s&url=%s", ...)`）。它携带 `siteKey` 但**从不携带 `configKey`**，因此 `BaseLoader.proxy` 走的是 `getSpider(params.get("siteKey"))` 分支，即激活配置 `VodConfig.get().getSite(key)`。桥接态的脚本调用 `js2Proxy` 时，服务它的是激活配置里同名（或无同名则新建）的实例；要真正闭合必须让 quickjs 侧带上 configKey，属于跨模块契约扩张，本轮按任务要求**不改设计、只登记**。
  - 激活配置（非桥接）路径行为等价，理由有三条：(a) 命名空间为空时 `BaseLoader.slot` 原样返回 key；(b) 数据库/原生 `VodConfig` 加载出来的 Site 上 `namespace` 恒为 `null`（只有 `VodSiteCatalog` 由 configKey 构造 Catalog 时才设置），且该字段 `transient`+`@Ignore` 不参与持久化；(c) 三个旧公开入口与旧 `setRecent` 重载仍显式传 `""`。
- **证据（本轮补强）**：新增 `LoaderNamespaceSlotTest`（`app/src/test/java/com/fongmi/android/tv/api/loader/LoaderNamespaceSlotTest.java`），它**真的经过 loader 槽位**而不是源码 grep：
  1. `sameSiteKeyUnderDifferentConfigKeysHitsIndependentLoaderSlots`——直接构造 `new JarLoader()`（其构造器只分配四个 `ConcurrentHashMap`，不碰 native，可在纯 JVM 里跑），按 `Util.md5(jar) + BaseLoader.slot(site.getNamespace(), site.getKey())` 预置三个实例，再用**真实的五参 `getSpider`** 取回，断言同名 siteKey 在 `cfgAlpha`/`cfgBeta` 下 `assertNotSame`、且桥接配置与激活配置也不 `assertSame`。
  2. `siteNamespaceDrivesTheLoaderSlotWhileActiveSiteStaysUnchanged`——命名空间来自真实 `Site` 对象，锁定 `slot("" ,"same")=="same"`、`slot("cfgAlpha","same")=="cfgAlpha@@@same"`、两条配置槽键不相等。
  3. `bridgePlumbingPassesTheNamespaceAllTheWayToTheLoader`——源码契约钉死 `Site.spider()` 的五参调用、`transient` 字段、`catalogOf(findConfig(id), id)`、`site.setNamespace(namespace)`，以及 `VodApi` 两处仍是 `site.spider()` 且不再自己拼空命名空间。
  仍无法做的运行时断言：`BaseLoader.get()` 会 `new PyLoader/JsLoader` 触发 Python 与 QuickJS 初始化，纯 JVM 单测构造不了它，所以「`Site.spider()` → `BaseLoader`」这一跳用源码契约锁定，而「命名空间 → loader 槽位」这一跳是真实运行时断言。

#### B2 补修 · 第三轮：`?do=js` / `?do=py` 子路由的 `recent` 仍是裸键（MAJOR-1）

前两轮把 `spiders` 表的**存侧**键命名空间化了，但**查侧**的 `recent` 没跟着改，存查两侧的键形状不一致：

- **缺陷链（逐段，均已独立核实）**：
  1. 存侧已带命名空间：`JsLoader.java:52` `return spiders.computeIfAbsent(BaseLoader.slot(namespace, key), k -> {`；`PyLoader.java:54` 同形。
  2. 查侧仍是裸键：`JsLoader.java:77-81` `public Object[] proxy(Map<String, String> params) throws Exception { if (recent == null) return null; Spider spider = spiders.get(recent); return spider != null ? spider.proxy(params) : null; }`；`PyLoader.java:96-100` 同形。
  3. `recent` 原本来自裸 key：第二轮的 `Site.java:500` `BaseLoader.get().setRecent(getKey(), getApi(), getJar());`，以及 `BaseLoader.java:154-158` 的 `jsLoader.setRecent(key)` / `pyLoader.setRecent(key)`。
  4. 生产端不带 siteKey/configKey：`quickjs/src/main/java/com/fongmi/quickjs/method/Global.java:115-117` `return Proxy.getUrl(local) + "?do=js";`；`chaquo/src/main/python/base/spider.py:114-115` 同形 `?do=py`。
  5. 消费端只能靠 `recent`：`BaseLoader.java:176-186` 在没有 `siteKey` 时 `if ("js".equals(params.get("do"))) return jsLoader.proxy(params);`（py 同理）。
  6. 后果：PC 通过 configKey 桥接一个 js/py 站源（`type==3`）时，该站源脚本生成的 `?do=js` 请求在 `spiders.get(recent)` 里查不到自己（`recent` 是别的配置留下的裸 key）→ 返回 null → HTTP INVALID_RESPONSE；若激活配置恰有同名 js/py 站源，则用激活配置的 spider 服务非激活配置的请求，即 B2 同类串源在该子路由复现。
- **改后行为（本轮）**：让 `recent` 与 `spiders` 表的键同形。
  - `JsLoader.java:34-36` `public void setRecent(String recent) { setRecent("", recent); }`（旧签名保留）；`JsLoader.java:44-46` `public void setRecent(String namespace, String key) { this.recent = BaseLoader.slot(namespace, key); }`。
  - `PyLoader.java:36-38` / `PyLoader.java:45-47` 与之完全同构。
  - `BaseLoader.java:154-157` 旧三参 `setRecent(String key, String api, String jar)` 委托 `setRecent("", key, api, jar)`；`BaseLoader.java:159-174` 新增四参 `setRecent(String namespace, String key, String api, String jar)`，把 namespace 透给 js/py；`isCsp(api)` 分支仍然只是 `jarLoader.setRecent(Util.md5(jar))`（原因见下面的 JarLoader 结论）。
  - `Site.java:499-504` `BaseLoader.get().setRecent(getNamespace(), getKey(), getApi(), getJar());`——桥接出来的 Site 自带 configKey 命名空间，于是 recent 与槽位键一致。
  - `Live.java:412` 未改动（`Live` 没有命名空间，仍走旧三参 → 等价空命名空间）。
- **为什么这是最小修法（不改生产端）**：缝隙的本质是同一张表存查两侧键形状不一致，把 recent 换成槽位键即可闭合，空命名空间下 `slot` 原样返回 key，原生路径与改动前逐字节等价。反之若让生产端带上 siteKey+configKey，就要动 quickjs 与 chaquo 两个模块的脚本可见 API，并且会被既有站源脚本的 URL 拼接方式反噬（`getProxy() + "&kw=…"` 之类），回归面远大于改一个字段赋值。
- **JarLoader 核实结论：无同类缺陷，本轮不动。** `JarLoader` 看似同形，实际不是：
  - `JarLoader.java:85-88` `public void setRecent(String recent) { this.recent = recent; SpiderDebug.log("jar-loader", "recent=%s", recent); }`，其值来自 `BaseLoader.java:174` 的 `Util.md5(jar)`；
  - `JarLoader.java:321-326` `public Object[] proxy(Map<String, String> params) throws Exception { Method method = recent != null ? methods.get(recent) : null; Object[] result = proxyInvoke(method, params); if (result != null) return result; return tryOthers(params); }`，查的是 **`methods` 表（键 = jar md5）**，不是站点实例表；
  - `JarLoader.java:328-330` 的 `tryOthers` 同样按 md5 过滤；`JarLoader.java:303-307` 的 `requireRecentLoader()` 查 `loaders.get(recent)`（键也是 md5）；
  - `JarLoader.java:225-227` `String jaKey = Util.md5(jar); String spKey = jaKey + BaseLoader.slot(namespace, key);` 的 spKey **只用于 `spiders` 实例缓存**。
  即 JarLoader 的 recent 从不代表「哪条配置的哪个站源」，而代表「哪个 jar 的 Proxy 入口方法」：两条配置引用同一个 jar 本来就应该共用同一个 Proxy 方法，真正区分站源的是 `params` 里传进去的 siteKey。若把它也命名空间化，`methods.get(recent)` 会直接查不到方法，`?do=csp` 全线失效——那是把改动做坏。

### B3 · configKey 失效时 /proxy 静默回落到激活配置

- **缺陷链**：`BaseLoader.java:145-154`（原）`catalogSpider` 内部 `catch` → `return null`，回到 `BaseLoader.java:134` 的 `spider != null ? spider : getSpider(params.get("siteKey"))`，于是请求写的是 B、响应来自 A。
- **改后行为**：`BaseLoader.java:160-170` 的 `proxy` 改为——只要 `configKey` 非空白就 `return catalogSpider(params.get("configKey"), params.get("siteKey")).proxy(params)`，**没有回落分支**；`catalogSpider`（`BaseLoader.java:178-183`）声明 `throws Exception`，内部不再 `catch`，站点缺失时抛 `IllegalArgumentException("配置 " + configKey + " 里没有站点: " + siteKey)`，siteKey 为空时抛「缺少站点 key」。未携带 `configKey` 的请求仍走原来的 `getSpider(siteKey)`，语义不变。
- **错误如何变成客户端可见的失败（调用链）**：
  1. `/proxy` 请求进入 `app/src/main/java/com/fongmi/android/tv/server/process/Proxy.java:40` 的 `doResponse`；
  2. `Proxy.java:42` `return createResponse(params, url, BaseLoader.get().proxy(params));`
  3. `BaseLoader.java:164` → `catalogSpider` → `VodSiteCatalog.findConfig` 抛 `IllegalArgumentException("未知的配置标识: …")`，沿 `catalogSpider` 的 `throws Exception` 向上传播；
  4. `Proxy.java:43-46` `catch (Throwable e)` → `return Nano.error(Objects.toString(e.getMessage(), e.toString()));`，即 HTTP 500 + `text/plain` 的异常原文；
  5. 客户端因此拿到明确的错误文本，而不是一个来自别的配置的「成功」响应。
  - 站源接口那条路的对应转换点是 `app/src/main/java/com/fongmi/android/tv/server/process/VodApi.java:108`：`IllegalArgumentException` 的 message 直接进 `json(error(...))`，客户端看到 `{"code":-1,"msg":"未知的配置标识: …"}`。
- **为什么这是最小修法**：把「解析失败」从返回值（null）改成异常，并删掉唯一一条回落分支；回落所需的 `getSpider(siteKey)` 在未带 configKey 时仍然保留，无需新增任何错误类型或响应体格式。
- **证据**：`VodApiTest.catalogProxyNeverFallsBackToActiveLoader`（`VodApiTest.java:559`）断言 `BaseLoader` 中不再存在 `spider != null ? spider :`、存在 `if (!TextUtils.isEmpty(params.get("configKey"))) return catalogSpider(`、`catalogSpider` 声明 `throws Exception` 且内部无 `catch`，并断言 `Proxy.java` 仍把异常转成 `Nano.error(...)`。端到端行为另有 `VodApiTest.unknownConfigKeyFailsWithoutFallingBackToActiveSource`（`VodApiTest.java:473` 起，改动前既有用例）断言 `code=-1`、`msg="未知的配置标识: missing"`、激活源 spider 一次都没有被调用。

### B4 · configKey → 配置的解析与仓库既有解析器不一致

- **缺陷**：新路径只用 `findByInterfaceKey(id, BaseConfig.VOD)`（精确匹配，不认 URL 哈希 / legacy key / 大小写），而进度写入走的是 `PlaybackConfigIdentity.cidForKey`（`PlaybackConfigIdentity.java:54-64`：interfaceKey + `getUrls()` 的 `keyForUrl(sha256)` + `getLegacyConfigKeys()`），见 `PlaybackProgressWriter.java:608,619`。同一个 key 在进度同步能解析、在站源/历史推送却报「未知」。
- **改后行为**：`VodSiteCatalog.java:162-171` 的 `findConfig` 先 `findByInterfaceKey` 精确命中，未命中时走 `legacyConfig(id)`（`VodSiteCatalog.java:173-177`：`PlaybackConfigIdentity.cidForKey(id)` 得到 cid 后 `Config.find(cid)`），再由 `VodSiteCatalog.java:185-191` 的 `resolve(id, exact, legacy)` 统一校验 `config.getUrl()` 非空白，否则抛 `IllegalArgumentException("未知的配置标识: " + id)`。
- **不插行保证**：兜底用的是 `Config.find(int)`（`Config.java:137`，纯 `dao.findById`），**绝不使用** `Config.find(url, type)` 的 `create(type, url)` 插入语义；`resolve` 本身是纯函数，不碰数据库，因此可以被单测直接锁住。
- **为什么这是最小修法**：不新建解析器、不改 `PlaybackConfigIdentity` 的算法，只是在精确匹配失败后补一次「既有解析器的兜底」，两套 key 立刻收敛成同一套。
- **证据**：`VodApiTest.configKeyResolutionAcceptsExactAndLegacyIdentity`（`VodApiTest.java:574`）用两个不落库的 `Config` 实例断言精确优先、legacy 兜底、空/空白url 抛 `IllegalArgumentException` 且信息为「未知的配置标识: <id>」，并断言源码含 `PlaybackConfigIdentity.cidForKey(id)`、`Config.find(cid)`，且在剔除注释后的代码里不出现 `Config.find(url`。

## M1–M4 修复记录

| 编号 | 改动位置 | 改后行为 | 为什么最小 |
| --- | --- | --- | --- |
| M1 | `VodSiteCatalog.java:140-145` + `Config.java:444-446, 454, 464, 479` | 新增 `public static void invalidate()`；`Config.insert()`/`save()`/`delete()` 在各自原有的数据库动作之后各调一次 `invalidateCatalog()` | 只追加一次清缓存调用，不改变这三个方法的参数、返回值或写入顺序 |
| M2 | `VodSiteCatalog.java:119-138` | 缓存命中判据仍是「当前时间 - entry.at < TTL」，但写入改为解析完成之后取时间：`CACHE.put(id, new Entry(catalog, System.currentTimeMillis()))` | 只把取时间点从方法开头挪到 `catalogOf` 之后，TTL 常量与命中语义不变，慢配置不再「出生即过期」 |
| M3 | `VodSiteCatalog.java:203-222` | `catalogOf(JsonObject)` 补齐原生加载的两步：`Site.findAll()` 结果按 key 建表后逐个 `site.sync(known.get(...))`，再 `CustomCspSetting.inject(sites)` | 直接镜像 `VodConfig.java:539-543` 的调用顺序，只读不写库 |
| M4 | `Action.java:429-471` + `HistoryScopeSyncTest.java:73-110` | `pushScopedHistory` 拆成「解析配置」与「按 sink 落库」两层，cid 只来自 `config.getId()`；单测注入记录器断言写入的 cid | 抽的是一个三方法接口，生产实现仍是原来的 `History.delete`/`History.syncTo`/`RefreshEvent.history()`，行为零变化 |

**M3 的副作用说明**：`CustomCspSetting.inject(List<Site>) `（`app/src/main/java/com/fongmi/android/tv/setting/CustomCspSetting.java:381-392`）在 registry 启用时会调用 `Server.get().start()`。该调用在 `Server.java:66-80` 第一行就 `if (nano != null) return;`，是幂等的；而桥接请求本身就跑在这个网关上，所以它等价于「已经在跑」。它返回的 `Result.home` 在桥接清单里没有输出通道（`VodApi.configJson` 不输出 home 字段），而原生路径的 `setHome(config, home, false)` 会**改写配置行**，因此这里刻意不采用它——安全等价物就是「按 registry 的插入位置合并站点，但不写库、不切全局 home」。

**M4 变异验证（强度证明）**：

1. 变异 A —— 把 `Action.java:440` 的 `int cid = config.getId();` 改成 `int cid = com.fongmi.android.tv.api.config.VodConfig.getCid();`（即评审担心的「改回激活配置 cid」）：`HistoryScopeSyncTest` 7 个用例中 `scopedPushWritesToTargetCid`、`scopedPushClearsTargetCidAndFollowsConfig` **双双变红**，失败原因是 `NullPointerException: ... androidx.room.Room.databaseBuilder, parameter context`（纯 JVM 里 `VodConfig.getCid()` 真去碰了 Room）。结论：任何读激活 cid 的实现都过不了这两个用例。
2. 变异 B —— 用不接触 Room 的等价变异 `int cid = config.getId() + 1;`：`7 tests completed, 2 failed`，失败信息分别是 `java.lang.AssertionError: expected:<[77]> but was:<[78]>` 与 `java.lang.AssertionError: force 必须先清空同一个 cid expected:<[77]> but was:<[78]>`——直接命中 cid 断言本身。
3. 两次变异均已改回 `int cid = config.getId();`，改回后定向单测 46 → `HistoryScopeSyncTest tests=7 failures=0`、`VodApiTest tests=39 failures=0`。

## 新增/加强的测试

**文本契约 vs 行为断言（MINOR-1 的登记）**：本文件把两类用例分开记账，避免把源码 grep 当成缺陷已被行为覆盖。
- **行为断言**（真的构造对象、真的调用被测方法、断言返回值/实例身份）：
  `LoaderNamespaceSlotTest.sameSiteKeyUnderDifferentConfigKeysHitsIndependentLoaderSlots`（真实 `JarLoader` + 真实五参 `getSpider`）、`NodeRuntimeServingTest.servingRequiresMatchingUrlAndLiveRuntime`（反射写真实字段后调真实 `isServing`）、以及本轮新增的 `LoaderRecentNamespaceTest` 全部 4 例（见下表）。
- **文本契约**（读源码字符串，只防误改、不能证明行为）：`VodApiTest` 的 5 个新用例、`LoaderNamespaceSlotTest.bridgePlumbingPassesTheNamespaceAllTheWayToTheLoader`、`NodeRuntimeServingTest.isServingProbesProcessLivenessWithoutBlocking`。（更正：`LoaderNamespaceSlotTest.siteNamespaceDrivesTheLoaderSlotWhileActiveSiteStaysUnchanged` 实为 `new Site()` + 真实 `getNamespace()/slot()` 断言，应归入行为断言；上一版把它记成文本契约是自查偏严。）
第三轮复评的正确批评是：MAJOR-1 的 `?do=js`/`?do=py` 串源在修复前**只**有文本契约覆盖，所以文本全绿而缺陷仍在。本轮把该类缺陷补成行为断言（`recent` 的存查往返 + 落空反证），文本契约保留但不再作为该缺陷的唯一覆盖。

| 用例 | 位置 | 锁住的契约 |
| --- | --- | --- |
| `nonActiveBundleIsRejectedInsteadOfRestartingNode` | `VodApiTest.java:511` | B1：非激活 bundle 被拒绝，桥接路径不得调用 `CatSource.serve` |
| `spiderSlotKeepsActiveCacheKeysByteIdentical` | `VodApiTest.java:530` | B2：空命名空间逐字节等价、四种 spider 都带命名空间、`activeKeys` 仍返回裸 key |
| `catalogProxyNeverFallsBackToActiveLoader` | `VodApiTest.java:559` | B3：无回落分支、异常不被吞、`Proxy` 把异常转成可见错误 |
| `configKeyResolutionAcceptsExactAndLegacyIdentity` | `VodApiTest.java:574` | B4：精确优先 + legacy 兜底 + 未知标识报错 + 不插行 |
| `catalogCacheAndSiteListMatchNativeLoading` | `VodApiTest.java:600` | M1/M2/M3：失效入口、解析后取时间、补齐 `Site.findAll()` 回填与 `CustomCspSetting` 合并、Config 三处挂钩 |
| `scopedPushWritesToTargetCid` | `HistoryScopeSyncTest.java:73` | M4：configKey=B 写入 B 的 cid，非 force 不清空 |
| `scopedPushClearsTargetCidAndFollowsConfig` | `HistoryScopeSyncTest.java:86` | M4：force 先清同一 cid；换配置换 cid（证明 cid 不是常量） |
| `sameSiteKeyUnderDifferentConfigKeysHitsIndependentLoaderSlots` | `LoaderNamespaceSlotTest.java:35` | B2 补修：**真实经过 JarLoader 槽位**，同名 siteKey 在两条 configKey 下 `assertNotSame`，且与激活配置也不共用 |
| `siteNamespaceDrivesTheLoaderSlotWhileActiveSiteStaysUnchanged` | `LoaderNamespaceSlotTest.java:68` | B2 补修：命名空间取自真实 `Site`，锁定 `slot("","same")=="same"` 与 `slot("cfgAlpha","same")=="cfgAlpha@@@same"` |
| `bridgePlumbingPassesTheNamespaceAllTheWayToTheLoader` | `LoaderNamespaceSlotTest.java:85` | B2 补修：`Site.spider()` 五参调用、`transient` 字段、`catalogOf(findConfig(id), id)`、`site.setNamespace(namespace)`、`VodApi` 两处仍是 `site.spider()` |
| `servingRequiresMatchingUrlAndLiveRuntime` | `NodeRuntimeServingTest.java:36` | B1 补修：`port=0`/`running=false`/空 `servingUrl`/换 bundle 四态都必须 false |
| `isServingProbesProcessLivenessWithoutBlocking` | `NodeRuntimeServingTest.java:57` | B1 补修：`isServing` 必须含 `serviceAlive()`/`port <= 0`/`!running`，且不含 `sleep`/`await`/`wait(` |
| `jsProxyResolvesRecentToTheNamespacedSlot` / `pyProxyResolvesRecentToTheNamespacedSlot` | `LoaderRecentNamespaceTest.java:43/60` | **MAJOR-1 行为断言**：真实 `setRecent(namespace,key)` + 真实 `proxy(params)`，断言取回的正是该命名空间槽位里的 fake Spider；两个不同 namespace 下的同名 key 互不干扰；空/null namespace 与改动前一致 |
| `jsProxyReturnsNullWhenRecentBelongsToAnotherNamespace` / py 同名 | `LoaderRecentNamespaceTest.java:84/99` | **MAJOR-1 反证（可变异）**：只预置激活配置的裸键实例，把 recent 指向别的命名空间，proxy 必须返回 null；若 recent 退回裸键就会命中激活配置的实例并返回非 null，即串源现场 |

原有 5 个 `HistoryScopeSyncTest` 用例与全部既有 `VodApiTest` 用例均未改动语义，只在其后追加（`catalogCacheAndSiteListMatchNativeLoading` 里那条锚点断言因本轮的 `catalogOf(findConfig(id), id)` 改成同名新文本，语义不变）。

**第二轮补强的变异验证**（证明新测试不是摆设）：

1. 变异 A —— 把 `Site.java:507` 改回四参 `getSpider(getKey(), getApi(), getExt(), getJar())`（即桥接 Site 丢掉命名空间）：`LoaderNamespaceSlotTest` 立即变红，失败原文 `java.lang.AssertionError: Site.spider() 必须把命名空间透传给五参 getSpider`（`LoaderNamespaceSlotTest.java:82`）。
2. 变异 B —— 把 `NodeRuntime.java:218` 的 `port <= 0` 去掉：`NodeRuntimeServingTest` 两个用例都变红，失败原文分别是 `java.lang.AssertionError: timeout() 之后 port 被清 0，必须判定为不可复用`（`NodeRuntimeServingTest.java:42`，走的就是 `assertFalse(NodeRuntime.isServing(URL))`）与 `java.lang.AssertionError: isServing 必须校验 port > 0`（`NodeRuntimeServingTest.java:64`）；当次汇总 `5 tests completed, 3 failed`。
3. 变异 C（额外一条，钉住槽位键本身）—— 把 `BaseLoader.java:48` 的 `slot` 改成恒返回 `key`（命名空间完全失效）：`3 tests completed, 2 failed`，失败原文 `org.junit.ComparisonFailure: 桥接 Site 必须落到带 configKey 的键 expected:<[cfgAlpha@@@]same> but was:<[]same>`（`LoaderNamespaceSlotTest.java:75`）与 `java.lang.AssertionError: 空命名空间必须仍然命中原来的键（激活配置路径逐字节不变） expected same:<...$1@1ed4ae0f> was not:<...$3@54c5a2ff>`（`LoaderNamespaceSlotTest.java:53`）。
4. 三处变异均已改回（`Site.java:507` 五参、`NodeRuntime.java:218` 含 `port <= 0`、`BaseLoader.java:48` 复原），改回后定向单测全绿：`VodApiTest tests=39`、`HistoryScopeSyncTest tests=7`、`LoaderNamespaceSlotTest tests=3`、`NodeRuntimeServingTest tests=2`。

## 第三轮（本轮）改动位置与证据

**改动清单（5 处源码 + 1 个新测试类，全部在白名单内）**

| 文件 | 位置 | 改动 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java` | `:34-36`、`:44-46` | 旧单参 `setRecent(String)` 委托空命名空间；新增 `setRecent(String namespace, String key)` 存 `BaseLoader.slot(namespace, key)` |
| `app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java` | `:36-38`、`:45-47` | 与 JsLoader 完全同构 |
| `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java` | `:154-157`、`:159-174` | 旧三参 `setRecent` 委托 `setRecent("", …)`；新增四参 `setRecent(String namespace, String key, String api, String jar)`，js/py 透传 namespace，csp 仍是 jar md5 |
| `app/src/main/java/com/fongmi/android/tv/bean/Site.java` | `:499-504` | `recent()` 改为 `BaseLoader.get().setRecent(getNamespace(), getKey(), getApi(), getJar());` |
| `app/src/test/java/com/fongmi/android/tv/api/loader/LoaderRecentNamespaceTest.java` | 新增（4 例） | `?do=js`/`?do=py` 的 recent 存查往返 + 落空反证 |

未改动：`JarLoader.java`（核实后确认无同类缺陷）、`Live.java`（无命名空间，走旧三参即等价空命名空间）、生产者 `quickjs/.../Global.java`、`chaquo/.../spider.py`（不在白名单，且改法面更大）。

**测试可构造性（为什么用 Unsafe 分配实例）**：`new JsLoader()` 会执行 `new Loader()` → `QuickJSLoader.init()`（`quickjs/src/main/java/com/fongmi/quickjs/crawler/Loader.java:9-11`），`new PyLoader()` 会 `Python.start(Platform.create())`（`chaquo/src/main/java/com/fongmi/chaquo/Loader.java:50`），纯 JVM 单测跑不了构造器。新测试因此用反射按名取 `sun.misc.Unsafe` 的 `allocateInstance` 分配空壳（不执行构造器），只反射写入 `spiders` 表；被测的 `setRecent` 与 `proxy` 是**真实方法、真实字段**，没有任何替身。断言里还加了对照组（同一张表的裸键必须仍可取），确保「返回 null」不是因为表本身空。

**变异验证（强度证明，本轮）**：把 `JsLoader.java:45` 与 `PyLoader.java:46` 同时改回裸键 `this.recent = key;` 后重跑 `--tests '*LoaderRecentNamespaceTest*'`：

- 汇总：`4 tests completed, 4 failed`（修复态为 `tests=4 failures=0`）。
- `jsProxyResolvesRecentToTheNamespacedSlot`：`org.junit.ComparisonFailure: 桥接配置的 ?do=js 必须命中自己的槽位，不能命中激活配置的同名站源 expected:<a[lpha]> but was:<a[ctive]>`（`LoaderRecentNamespaceTest.java:44`）
- `pyProxyResolvesRecentToTheNamespacedSlot`：同形，`?do=py … expected:<a[lpha]> but was:<a[ctive]>`（`:65`）
- `jsProxyReturnsNullWhenRecentBelongsToAnotherNamespace`：`java.lang.AssertionError: recent 属于另一条配置时必须落空；命中激活配置的实例即为串源 expected null, but was:<[Ljava.lang.Object;@24569dba>`（`:90`）
- `pyProxyReturnsNullWhenRecentBelongsToAnotherNamespace`：同形（`:103`）
- 两处已改回 `this.recent = BaseLoader.slot(namespace, key);`，改回后该测试类 `tests=4 failures=0`。**注意第一、二条失败原文里 `was:<a[ctive]>` 正是「非激活配置的请求被激活配置的同名站源服务」这一串源现场，不是"查不到"的另一半。**

## 验证结果

以下数字为**第三轮**的最终结果；第一/二轮的对应数字在括号内保留以说明演变。

1. **双 flavour 编译**
   `./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --offline -PchaquopyBuildPython=G:/Python310/python.exe`
   → `BUILD SUCCESSFUL in 44s`（退出码 0；无 `error:`，仅有既存的 Room/deprecation 提示）。第二轮为 `BUILD SUCCESSFUL`，第一轮为 `1m 11s`。
2. **定向单测**
   `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests '*VodApiTest*' --tests '*HistoryScopeSyncTest*' --tests '*LoaderNamespaceSlotTest*' --tests '*NodeRuntimeServingTest*' --tests '*LoaderRecentNamespaceTest*' --offline -PchaquopyBuildPython=G:/Python310/python.exe`
   → `BUILD SUCCESSFUL in 38s`；XML 汇总 `VodApiTest: tests=39 failures=0 errors=0 skipped=0`、`HistoryScopeSyncTest: tests=7 failures=0 errors=0 skipped=0`、`LoaderNamespaceSlotTest: tests=3 failures=0 errors=0 skipped=0`、`NodeRuntimeServingTest: tests=2 failures=0 errors=0 skipped=0`、`LoaderRecentNamespaceTest: tests=4 failures=0 errors=0 skipped=0`（合计 **55**，第二轮为 51）。
   另外单独跑过验收清单里的原始命令（不带 `*LoaderRecentNamespaceTest*` 过滤器）→ `BUILD SUCCESSFUL in 21s`，XML 汇总 `LoaderNamespaceSlotTest 3 + NodeRuntimeServingTest 2 + HistoryScopeSyncTest 7 + VodApiTest 39 = 51`，`failures=0 errors=0 skipped=0`；新测试类不在该过滤器的匹配范围里，因此 51 而非 55，不是回归。
3. **全量单测基线对比**
   `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --offline -PchaquopyBuildPython=G:/Python310/python.exe`
   → **`4725 tests completed, 8 failed, 3 skipped`**（Gradle 因既有 8 条失败报 `BUILD FAILED in 2m 27s`，这是基线的正常表现）。用例数 4709（改动前基线）→ 4716（第一轮 +7）→ 4721（第二轮 +5）→ **4725（本轮 +4：全部来自 `LoaderRecentNamespaceTest`）**。失败集合与既有基线**逐条同名同因**：
   - `androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile`
   - `androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation`
   - `com.fongmi.android.tv.ui.activity.NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`
   - `com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest.horizontalComicProgressUsesItsOwnPageState`
   - `com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest.readerDefinesTheMonotonicClockUsedByRestore`
   - `com.fongmi.android.tv.ui.activity.SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout`
   - `com.fongmi.android.tv.ui.activity.TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors`
   - `com.fongmi.android.tv.web.WebThemeTokenSourceTest.webHomeTokenDeclarationsStayInsideRootScope`
   逐条核对结论：这 8 条全部是 Windows `renameTo` 与 CRLF 类既有环境性失败，与 B1/B2 改动无任何调用关系（无一条涉及 `Site`/`VodSiteCatalog`/`BaseLoader`/`NodeRuntime`/`CatSource`/`VodApi`，也无一条涉及本轮改的 `JsLoader`/`PyLoader`）。**新增失败 = 0**；失败条数与名称与基线逐条相同。
4. **`git diff --check`** → 无输出（仅 stderr 有一条 `warning: in the working copy of 'app/src/main/java/com/fongmi/android/tv/api/config/VodSiteCatalog.java', LF will be replaced by CRLF the next time Git touches it`，属仓库既有换行策略提示，不是空白错误）。

## 改动面

```
 M app/src/main/java/com/fongmi/android/tv/api/CatSource.java
 M app/src/main/java/com/fongmi/android/tv/api/config/VodSiteCatalog.java
 M app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java
 M app/src/main/java/com/fongmi/android/tv/api/loader/JarLoader.java
 M app/src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java
 M app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java
 M app/src/main/java/com/fongmi/android/tv/bean/Config.java
 M app/src/main/java/com/fongmi/android/tv/bean/Site.java
 M app/src/main/java/com/fongmi/android/tv/node/NodeRuntime.java
 M app/src/main/java/com/fongmi/android/tv/server/process/Action.java
 M app/src/test/java/com/fongmi/android/tv/server/process/HistoryScopeSyncTest.java
 M app/src/test/java/com/fongmi/android/tv/server/process/VodApiTest.java
?? app/src/test/java/com/fongmi/android/tv/api/loader/LoaderNamespaceSlotTest.java
?? app/src/test/java/com/fongmi/android/tv/api/loader/LoaderRecentNamespaceTest.java
?? app/src/test/java/com/fongmi/android/tv/node/NodeRuntimeServingTest.java
?? docs/C109-beta-merge-review-dev5-20261028.md
```

即 **12 个已修改文件 + 4 个未跟踪文件（3 个新测试 + 本记录）**，全部落在任务白名单内。第一轮增量：`BaseLoader/JsLoader/PyLoader/JarLoader/VodSiteCatalog/CatSource/VodApiTest` 的命名空间化与 B1 拒绝分支；第二轮增量：`Site.java`（transient 命名空间字段 + `getNamespace/setNamespace` + `spider()` 改五参）与 `NodeRuntime.java`（`isServing` 加存活校验）；**第三轮增量：`JsLoader.java`/`PyLoader.java` 的 `setRecent(namespace,key)`、`BaseLoader.setRecent` 四参重载、`Site.recent()` 传命名空间，加新测试类 `LoaderRecentNamespaceTest`**。`VodApi.java` 三轮全程 **一行未改**。
**未触碰**：`VodConfig.java`（加载/换源语义）、`PlaybackConfigIdentity.java`（算法）、`Server`/`Nano`/`Proxy` 的鉴权面、以及 origin/beta 带入的 58 个提交。

## 已知风险（**未修**，如实登记，不得视为已修复）

### M5 · 局域网越权（未加鉴权，需用户单独批准后实施）
1. `Server.java:70` 把网关绑到全部网卡，`/vod/api` 与 `/proxy` 都没有任何鉴权：同一局域网内任意主机都能读任意已保存配置的站源、并按 `configKey` 打任意配置的接口。
2. `Manage` 可列出 `interfaceKey`（即 `/vod/api?ac=config` 与 `/proxy` 需要的全部凭据），因此上面的越权不需要额外信息即可完成。
3. `JarLoader` 允许按请求加载 jar（`parseJar` + `DexClassLoader`），配合无鉴权构成远程代码加载面。
4. **状态：本轮明确不修。** 加鉴权属于产品行为扩张（需要凭证分发、与 PC 端约定的握手方式、以及既有客户端的兼容策略），必须由用户单独批准后另开任务实施。在此之前建议只在可信网络内开启网关。

### M1 · 残余（未修）
5. `Config.java:94-100` 里两个 `static` 的 delete 重载没有挂 `invalidateCatalog()`，只有 `insert/save/delete` 的三个实例方法挂了（`VodApiTest.catalogCacheAndSiteListMatchNativeLoading` 数的正是这 3 处）。走这两个静态路径删除配置后，桥接缓存最坏仍有 60 秒旧数据。
6. `Backup.java:132/147/197/198` 有 DAO 直写（恢复备份时直接用 `getConfigDao()`），同样绕过 `invalidateCatalog()`，恢复后 60 秒内桥接可能仍读到旧站源。

### MINOR · 残余（未修）
7. `BaseLoader.baseKeys` 按**首个** `@@@` 截断：空命名空间且 siteKey 本身含 `@@@` 时会被误截（理论上会让 `activePluginKeys()` 少保护一个插件脚本）。
8. `invalidate()` 与「正在飞的解析」之间存在极窄竞态：线程 A 在 `cached` 里读到的旧 Catalog 可能被线程 B 的 `invalidate` 之后写回 `CACHE`（写回不再校验 at/版本），最坏多存活一个 TTL。
9. `namespace(String)` 只做 `trim()`，不做 `toLowerCase()`：`abc` 与 `ABC` 被视为两个不同命名空间，同一配置的两种大小写写法会各占一套槽位（不串源，但槽位翻倍）。
10. 命名空间槽位没有上限：`JsLoader.spiders`/`PyLoader.spiders`/`BaseLoader.catSpiders` 里的 `configKey@@@siteKey` 条目只在 `VodConfig.clear()` 时清空，没有 TTL/LRU；反复用不同 configKey 探测会持续增长。
11. `VodApiTest` 里仍有若干「读源码 grep 断言」的用例（如 `spiderSlotKeepsActiveCacheKeysByteIdentical`、`bridgePlumbingPassesTheNamespaceAllTheWayToTheLoader`、`NodeRuntimeServingTest.isServingProbesProcessLivenessWithoutBlocking`）：它们钉的是文本而不是行为，重命名变量或换等价写法就会误报。第二轮曾把这类文本契约当成 `?do=js`/`?do=py` 串源的唯一覆盖，结果缺陷仍在而测试全绿——第三轮已用 `LoaderRecentNamespaceTest` 的行为断言补上（详见「新增/加强的测试」里的文本契约 vs 行为断言登记），剩下的这部分属于可维护性债。另：`LoaderRecentNamespaceTest.java:146-154` 用 `Class.forName("sun.misc.Unsafe")` 反射取 unsafe（`grep 'import sun\.'` 在 app/src/test 无命中，无编译期 JDK 内部依赖），当前 JBR JDK 25.0.2 实测通过；JDK 26+ 可能需要 `--sun-misc-unsafe-memory-access=allow`，仅影响单测执行、不影响产物。