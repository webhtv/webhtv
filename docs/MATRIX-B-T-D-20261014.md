# MATRIX-B-T-D-20261014：B-T-D 行验收（leanback / dark）

## Recovery anchor

- 目标：执行 §20.2 矩阵的 B-T-D 行（leanback / dark，场景 Dialog + 设置，通过标准「深色面板统一，焦点清晰」）。
- 性质：**验收**。本任务不改生产代码。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪；分支 `dev4`，HEAD `992297a9ab2`。
- 结论：**通过**。
- 下一步唯一动作：无（本行已交付）；矩阵剩余 B-A9、C-M、C-T、D-M、D-T、E-W。

---

## 1. 环境与模式（模式判据按 B-M-D 更正的教训记录）

| 项 | 值 | 证据 |
|---|---|---|
| 设备 | `127.0.0.1:5561`（= `192.168.50.3:5561`，LDPlayer 实例 3） | `adb devices` |
| 系统 | Android 9 / API 28 | `getprop ro.build.version.sdk` = 28 |
| **flavor** | **leanback** | 见 §1.1 |
| 分辨率 | 1920×1080（横屏） | `screencap` |
| **应用真实渲染模式** | **dark** | `dumpsys activity top` → `mCurrentConfig` 含裸 token `night` |
| 系统夜间位 | `ui_night_mode = 2`（MODE_NIGHT_YES） | `settings get secure ui_night_mode` |
| `theme_mode` | `-1`（跟随系统） | `run-as … cat shared_prefs/…xml` |
| `theme_color` | `-1`（不做动态色着色，走编译期 Layer 1 调色板） | 同上 |

注：leanback 的 `ThemeController.applyNightModeToApp()` 在 `theme_mode` 未显式指定时**产品默认取深色**
（`MODE_NIGHT_YES`），所以本行的深色不是"碰巧系统是深色"，而是产品默认与系统一致。

### 1.1 flavor 的确认方式（记录踩过的坑）

`dumpsys package <pkg> | grep -c SettingEnhanceActivity` 对 mobile 与 leanback 都返回 **0**，
因此**不能**用它判断 flavor。可靠的判据是：

- `cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER <pkg>`；
- 以及**界面形态**：leanback 首页是 TV 布局（右上大时钟、`点播/追更/历史/直播/搜索/收藏/推送` 导航行、
  2:3 大卡片带进度条），mobile 首页是竖屏底部导航。

本次先构建安装 leanback（`bash scripts/build_arm64_debug_install.sh --flavor leanback --abi arm64-v8a
--serial 127.0.0.1:5561`，7m25s，APK 195MB），界面随即变为 TV 布局，据此确认 flavor。

---

## 2. 采集清单

| 文件 | 内容 | 说明 |
|---|---|---|
| `B-T-D-01-home.png` | leanback 首页（TV 布局），深色 | 焦点在「点播」 |
| `B-T-D-02-settings.png` | `SettingActivity` 设置页，深色 | 内容源 / 播放与内容 / 个性化与智能 |
| `B-T-D-03-focus-down1.png` | D-pad ↓ 后 | 焦点帧 |
| `B-T-D-04-focus-right1.png` | D-pad → 后 | 焦点帧 |
| `B-T-D-05-focus-down2.png` | D-pad ↓ 后 | 焦点帧 |
| `B-T-D-06-focus-right2.png` | D-pad → 后 | 焦点帧 |
| `B-T-D-07-dialog.png` | `SiteDialog`（站点列表弹窗），深色 | **真 Dialog 窗口** |
| `B-T-D-08-exitdialog.png` | 退出确认弹窗（Alert），深色 | 焦点环在「完全退出」 |

### 2.1 两个弹窗确实是 Dialog 窗口（不是普通页面）

`dumpsys window windows` 同时存在两个 `HomeActivityCurrent` 窗口：

```
Window #3 …HomeActivityCurrent  ty=APPLICATION   fmt=TRANSLUCENT  surfaceInsets=Rect(64,64-64,64)   ← Dialog
Window #4 …HomeActivityCurrent  ty=BASE_APPLICATION                                              ← Activity
```

并在弹窗显示时按一次 BACK：`Window #3` **消失**，只剩 `BASE_APPLICATION`——确认上层窗口是 `Dialog`。

---

## 3. 面板与前景实测（深色面板是否"统一"）

采样为 PNG 精确像素计数（容差 0，除注明外）。所有实测值都等于 `values-night/webhtv_tokens.xml`
的取值，**没有一个**等于 Material 基线（对照见 `MATRIX-B-M-D-20261013.md` §2.3）。

| 元素 | 采样区 | 实测主色 | 对应 token | 对比度 |
|---|---|---|---|---|
| `SiteDialog` 面板 | (900,700) 200×200 | `#1F2428` **91.9%** | 深色 `surface_container` | — |
| `SiteDialog` 文字 | 同上区域 | `#E2E2E9` **3.8%** | 深色 `on_surface` | **12.26:1** ✓ |
| 退出弹窗面板 | (760,420) 200×160 | `#2A2F34` **87.6%** | 深色 `surface_container_high` | — |
| 退出弹窗标题 | 同上区域 | `#E2E2E9` **2.9%** | 深色 `on_surface` | **10.58:1** ✓ |
| 退出弹窗正文 | 同上区域 | `#C4C6D0` **2.3%** | 深色 `on_surface_variant` | **7.94:1** ✓ |

判定：**深色面板统一**。两个弹窗分别使用设计系统的 `surface_container` 与 `surface_container_high`
两层深色表面（这是设计系统自身的层级，不是不一致），全部前景角色取自深色表，
普通文本对比度 **7.94–12.26:1**，均高于 §20.3 的 4.5:1 门槛。

---

## 4. 焦点遍历

### 4.1 焦点确实在移动

四帧 D-pad 遍历的 sha256 两两不同：

| 帧 | 按键 | sha256（前 16） |
|---|---|---|
| `B-T-D-02-settings.png` | （初始） | `A6FF8E74DE8795BC` |
| `B-T-D-03-focus-down1.png` | ↓ | `CE6C18A4FFBF697F` |
| `B-T-D-04-focus-right1.png` | → | `3025A7BE45B43B81` |
| `B-T-D-05-focus-down2.png` | ↓ | `9507F13864454343` |
| `B-T-D-06-focus-right2.png` | → | `3AB823A8B9470078` |

### 4.2 焦点环颜色与可辨识度

深色表 `webhtv_color_focus = #F5F7FF`（浅色表为 `#0B57D0`），即 TV 焦点环的**唯一取值源**。
按精确颜色定位（容差 6）：

| 截图 | `#F5F7FF` 像素 | 包围盒 | 说明 |
|---|---|---|---|
| `B-T-D-02-settings.png` | 10590 | x=33..1845, y=39..1025 | 「点播」整行环，最密行 y=87/88/164（每行 1592 px，全宽描边） |
| `B-T-D-04-focus-right1.png` | 1465 | x=33..1845, y=39..1025 | 焦点移到 72px 宽的图标按钮（最密行 y=200/201/276，每行 72 px） |
| `B-T-D-08-exitdialog.png` | 32932 | x=557..1363, y=593..684 | 「完全退出」按钮环（最密行 y=613/664，每行 390 px） |

对比度（§20.3 要求非文本组件 ≥3:1）：

- `#F5F7FF` 对退出弹窗面板 `#2A2F34` = **12.63:1** ✓
- `#F5F7FF` 对设置页行底（压在壁纸上，实测约 `#524D60`）= **7.58:1** ✓

判定：**焦点清晰**。焦点环在整行控件、图标按钮、弹窗按钮三类目标上都可精确定位，
且对各自背景均远超 3:1。

---

## 5. 判定

**B-T-D 行：通过。**

- 深色面板统一：两个弹窗面板均取深色表表面色，无浅色孤岛（§3）；
- 焦点清晰：焦点环颜色取自唯一 token 源，三类控件上均可辨识且 ≥3:1（§4）；
- 模式判据：`mCurrentConfig` 含 `night`，非肉眼观感、非系统夜间位（§1）。

---

## 6. 局限

1. 覆盖的弹窗是 `SiteDialog` 与退出确认弹窗两个；`HomeMenuDialog` 等其余 TV 弹窗未逐一打开。
   它们共用同一套 `Theme.WebHTV` 主题与同一 overlay 解析路径（见 `MATRIX-B-M-D-20261013.md` §3），
   故结论可外推，但**各弹窗自身布局若含硬编码颜色**不在本行覆盖范围。
2. 设置页只覆盖了第一屏（内容源 / 播放与内容 / 个性化与智能），未逐项进入二级页。
3. 为到达 `SettingActivity`（该 Activity **未导出**，`am start` 报 `SecurityException`），
   本次临时把 `shared_prefs` 里的 `home_menu_key` 由 `1` 改为 `9`（菜单键直达设置页），
   **采集完成后已还原为 `1`**。这属于测试手法，不是产品行为；`home_menu_key=9` 本身是产品支持的合法配置。
4. 设备为横屏 1920×1080（`DisplayFrames r=1`）；leanback 本就是横屏场景，不影响本行。
5. 未做 D-pad **全量可达性**遍历（§20.3 要求"所有可交互元素可用 D-pad 到达"），
   本行只验证了焦点移动与焦点环可辨识度。

---

## 7. 下一步

矩阵剩余：**B-A9**、C-M、C-T、D-M、D-T、E-W。

- `C-T`、`D-T` 与本行同机同 flavor（leanback 已装在 `127.0.0.1:5561`），可直接继续。
- `B-A9`、`C-M`、`D-M`、`E-W` 需 mobile flavor；`127.0.0.1:5559` 当前是 mobile（API 28 / Android 9），
  可作 `B-A9` 的目标机。
- 后续各行采集时**必须**继续把 `dumpsys activity top` 的 `mCurrentConfig` 写进记录（见 §1）。
