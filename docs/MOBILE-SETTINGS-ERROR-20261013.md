# MOBILE-SETTINGS-ERROR-20261013：手机端设置页的错误与正常取值看起来完全一样

## Recovery anchor

- 目标：手机端设置页的内联错误与正常取值可区分（错误色 + 实心深底），且成功刷新时颜色与底材都完全复位。
- 允许路径：`app/src/main/res/drawable/shape_error_value.xml`（由 leanback 迁入 main 供两个 flavor 共用）、`app/src/mobile/java/.../ui/fragment/SettingEnhanceFragment.java`、`SettingAdFragment.java`、`app/src/testMobile/java/.../ui/fragment/MobileSettingsErrorContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `72e817d1ac5`。
- 验收：① 两个 Fragment 的 `setError` 同时换错误色与实心深底、且不复用跟随调色板的 `colorError`；② 成功路径经 `setSummary` 复位颜色与底材；③ 没有绕过复位的直接 `setText`；④ 取值 TextView 在布局里原本无背景（复位安全的前提）；⑤ 底材是实心色且在 main 源集共用；⑥ `testMobile` 定向测试全绿；⑦ 电视端不受影响。
- 回滚：撤销本任务原子提交即可回到「错误与正常取值同色」；无数据迁移。
- 交付坐标：提交 `615de468aca`（dev4，git 识别为 drawable 的 rename），recovery tag `recovery/MOBILE-SETTINGS-ERROR-20261013/20261013111634`；未推送。
- 下一步唯一动作：无（本批已交付）。后续候选见 §7。

## 1. 现象与根因

设计文档 §9.9 要求错误态有「错误色」。手机端两个设置页（`SettingEnhanceFragment`、`SettingAdFragment`）的 `setError` 与电视端此前**逐字相同**：

```java
private void setError(TextView view) {
    if (view != null) view.setText(R.string.error_config_get);   // 只改文案
}
```

于是"配置取得失败"这行字与正常取值**用的是同一个颜色**——用户看不出这一行是失败的。

**根因**：与电视端同款。而且这两个 Fragment 的 `safeSet` / `safeRun` / `setError` 三段结构、以及"取值摘要复用同一个 TextView"的形态，与电视端 `SettingEnhanceActivity` / `SettingAdActivity` **完全一致**——所以电视端 TV-6 的修法可以直接移植，不需要重新设计。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/webhtv-unified-visual-design-system-20260920.md` §9.9 | 仓库既有设计权威 | A（本地） | 错误态 = 错误色 + 原因 + 重试动作 |
| `docs/TV-ERROR-STATE-CLOSEOUT-20261013.md`（TV-6） | 本仓库既有交付记录 | A（本地） | 电视端的同构修法：错误色 + 实心深底 + 成功复位。本批是**应用** |
| `mobile` `SettingEnhanceFragment` / `SettingAdFragment` | HEAD `72e817d1ac5` | A（本地） | `setError` 只改文案；`safeSet` 与电视端逐字相同 |
| `mobile/res/layout/fragment_setting_enhance.xml` / `fragment_setting_ad.xml` | HEAD `72e817d1ac5` | A（本地） | 逐个核对：取值 TextView 共 **23 + 5 = 28 个，全部没有背景** → `setBackground(null)` 复位安全 |
| `main/res/values/webhtv_tokens.xml` 的 `webhtv_color_on_wallpaper_error` / `webhtv_color_error_value_bg` | HEAD `72e817d1ac5` | A（本地） | 两个角色都在 **main**，两个 flavor 共享 → 手机端无需新增颜色 |
| `leanback/res/drawable/shape_error_value.xml` | HEAD `72e817d1ac5` | A（本地） | 底材 drawable 原先只在 leanback；本批迁到 main 供两端共用 |

### 2.1 为什么不需要重新实测对比度

电视端 TV-6 的结论是：错误前景色直接画在行色带（中亮度，L≈0.13–0.15）上只有 **4.05–4.50:1**，略低于正文门槛；换上一块**实心深底** `#4A3438` 后为 **8.84:1**。

关键在于那块底是**实心色**而不是半透明——对比度只由"错误前景色 + 实心底"这两个确定值决定，**与下层底材、与 flavor 都无关**。所以：

- 手机端设置页的行色带即使与电视端亮度不同，也不影响 8.84:1；
- 不需要为手机端重新取样，也不需要新增颜色；
- 两端可以共用同一份 drawable 与同一个角色。

这也是把 drawable 从 leanback 迁到 main 的理由：**不是重构，是为了让"同一个实心底材"只有一份**。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | §9.9 继续不满足，错误不可见 | 拒绝 |
| 2. 只换错误色、不加底材 | 改动最小 | 错误色直接画在行色带上只有 4.05–4.50:1，**正文门槛不达标** | 拒绝 |
| 3. **换错误色 + 实心深底 + 成功复位（采用）** | 8.84:1 稳达标；与电视端一致 | 错误时该行取值区域会出现一块深色块（观感变化） | **采用** |
| 4. 在 mobile 再复制一份 drawable | 不动 leanback | 两份内容相同的文件，将来必然漂移 | 拒绝 |
| 5. 顺便把播放器错误层的白字也改成错误色 | "更彻底" | 播放器错误层画在**任意视频画面**上（`shape_widget` 是 50% 黑叠视频），底材不可确定，纯白才是最稳的选择；改成 `#FFDAD6` 只会降低对比度且给不出保证 | 拒绝，记为 §7.1 |

## 4. 实施

1. `shape_error_value.xml` 由 `leanback/res/drawable/` **迁到** `main/res/drawable/`：两个 flavor 共用同一份实心底材；注释里补上"因为是实心色所以与 flavor 无关"的理由。
2. `SettingEnhanceFragment` / `SettingAdFragment`：
   - `setError`：改用 `?attr/webhtvColorOnWallpaperError`，并 `setBackgroundResource(R.drawable.shape_error_value)`；
   - 新增 `setSummary(TextView, CharSequence)`：写文本 + 复位到 `?attr/webhtvColorOnWallpaper` + `setBackground(null)` 撤掉底材；
   - `safeSet` 改为经 `setSummary` 走成功路径；
   - 把两个文件里**全部直接**的取值 `setText` 调用（Enhance 7 处、Ad 1 处）也改走 `setSummary`——否则这些行失败一次后会一直保持红字加深底；
   - 新增 `themeColor(int)`（`requireContext().getTheme().resolveAttribute`）。
3. 新增 `MobileSettingsErrorContractTest`（5 项）：错误色 + 底材且不复用 `colorError`；成功路径经 `setSummary` 且颜色与底材都复位；**没有绕过复位的直接 `setText`**；28 个取值 TextView 在布局里原本无背景；底材是实心色且只在 main 一份。

## 5. 不变量与回滚

- 不改动错误文案本身（仍是 `error_config_get`）与 `safeRun` 的异常兜底语义。
- 不改动电视端 Java：本次只把一份 drawable 从 leanback 移到 main，leanback 的引用（`@drawable/shape_error_value`）照旧解析得到。
- 不改动播放器错误层（见 §3 方案 5 与 §7.1）。
- 不改动手机端首页的 `StateEvent` 状态机制（见 `docs/MOBILE-STATE-SURFACE-20261013.md` §7.1）。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "...MobileSettingsErrorContractTest"` → **BUILD SUCCESSFUL in 41s**（新增 5 项全绿）。
- **电视端回归**（因为把一份 drawable 从 leanback 迁到了 main）：`.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.Tv*" --tests "com.fongmi.android.tv.theme.*"` → **BUILD SUCCESSFUL in 8m 48s**。迁移未影响电视端。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 2m 37s**；`.\gradlew.bat :app:assembleMobileArm64_v8aDebug` → **BUILD SUCCESSFUL in 6m 21s**。两个 flavor 都能解析到 main 里的那份底材。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增颜色。

### 6.2 设备实测（`192.168.50.3:5561`，装 **mobile flavor**）

| 检查项 | 结果 |
|---|---|
| mobile flavor 是否正常启动 | 是 |
| **「增强功能」页渲染** | 全部取值正常显示为**常规次级色**（运行环境·工具·网络 / 启用·0/0 可用 / 账号 0 / 未绑定 / 选择装置 / 已选 0·待确认 0 / 禁用·0 条规则 / 浏览器页面），**没有一行卡在错误色**；行标题白色、取值次级色，层级完好 |
| 接线是否崩溃 | 无崩溃 |

这一项是**决定性检查**：`setSummary` 会显式调用 `themeColor(...)`（Fragment 里走 `requireContext().getTheme().resolveAttribute(...)`）。若解析出的是资源 id 而不是颜色值，这些取值会立刻显示成错误的颜色。实测全部正常，说明解析正确、且"成功路径复位"没有副作用。

截图：`.design-evidence/e5561-m3-enhance.png`。验证后已把电视端 flavor 装回。

### 6.3 结论

手机端设置页的内联错误从"与正常取值同色"变成"错误色 + 实心深底"，成功刷新时颜色与底材都会复位；底材是实心色，因此电视端夹取的 8.84:1 对手机端同样成立，两端共用同一份 drawable 与同一个角色。定向测试、电视端回归与两端构建全绿。

### 6.4 残余风险与诚实说明

- **错误底材的观感仍未经真机确认**：要触发设置页内联错误必须让某个设置项读取抛异常，我同样没有构造出这样的场景（与电视端 TV-6 同一限制）。8.84:1 是确定算出来的（实心色 + 固定前景），但**看起来是否合适**没有实测；若观感不佳，替代方案是给整行换底。
- **`requireContext()` 在 Fragment 里的失败模式**：若 Fragment 已 detach 而刷新仍在跑，`requireContext()` 会抛 `IllegalStateException`。走 `safeRun` 的刷新路径会被它的 `catch (Throwable)` 兜住；而我在点击处理器里新接入的 `setSummary` 不在 `safeRun` 内——那些都是可见 Fragment 上的用户操作，正常不会 detach。这一点是**推理**，没有专门构造 detach 场景验证。
- 手机端与电视端的设置页仍是**两套代码**（Fragment vs Activity），本批只对齐了行为，没有合并实现（见 §7.3）。

## 7. 相邻发现（未纳入本批）

1. **播放器错误层仍然没有"错误色"，而且我认为它不该有。** §9.9 要求「错误色 + 原因 + 重试动作」，但播放器的错误层画在 `shape_widget`（`black_50` 叠在**任意视频画面**）上——底材不可确定，任何带色相的前景色都无法给出对比度保证，纯白才是最稳的选择（现有实现也是 `@color/white`）。要真正满足 §9.9，需要给错误层一块**不透明的实心底**（像设置页那样），那会改变播放页的观感，属独立决策。
2. **手机端同样存在「`StateEvent` 与 `ProgressLayout` 两套状态机制」**（见 `docs/MOBILE-STATE-SURFACE-20261013.md` §7.1），设置页用的是第三套（Fragment 内的 `safeRun` 兜底），三者关系尚未梳理。
3. **电视端与手机端的设置页仍是两套代码**（Activity vs Fragment），本批只是把它们的**行为**对齐，没有合并实现。真正的收敛需要先决定设置页的统一形态。
