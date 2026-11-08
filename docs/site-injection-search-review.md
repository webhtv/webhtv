# 站点注入搜索评审

## 范围与验收

- 分支：`Silent1566`；评审基线：`58ebb653ff9386b292bd1e4957c0544e7582c035`。
- 被评审提交：`7357b2c37d6b73887e56e3375cfa6e30655196c7`，共 8 个文件。
- 目标：评审未推送变更，修复引入的问题，复评并完成针对性验证，然后中文提交、恢复标签与推送。
- 保护：任务开始时 `app/.cxx/` 中 35 个未跟踪文件，不纳入提交、不删除或覆盖。
- 修复仅涉及 `CustomCspDialog.java`、`CustomCspDialogTest.java` 与本记录；不改依赖、不打正式包、不操作模拟器。

## 首轮发现

1. **必须修复：空状态不同步。** `CspAdapter.remove` 删除最后一个匹配项后只刷新列表，没有重新计算 `searchEmpty` 可见性。用户看到空白而不是无匹配提示。
2. **必须修复：排序通知回归。** `moveItemToIndex` 将原有 `notifyItemMoved` / `notifyItemRangeChanged` 改为 `notifyDataSetChanged`；移动期间全量失效使 RecyclerView 无法持续跟踪行位置，同时每步重建所有可见行。筛选时已禁止移动，因而可安全恢复原局部通知。
3. **不修改：原始序号。** 倒序/筛选后保留底层顺序编号，与父提交一致，不将既有设计误报为缺陷。

已检查搜索匹配、显示/底层索引转换、过滤状态下保存完整集合、编辑/删除、倒序、排序禁用以及新增布局和三语言资源。没有发现搜索执行脚本、联网或更改持久化格式的行为。

## 验证与复评结果

使用仓库现有 leanback Robolectric 依赖，调用实际适配器与绑定布局：
- 删除最后匹配项后显示空提示，清空搜索后恢复全部未删除条目。
- 过滤及倒序时编辑/删除准确对应原集合，隐藏条目不受影响。
- 移动的正反序坐标与局部通知正确；过滤时禁止排序且原集合不变。
- 原匹配函数测试，以及实际 Item 字段匹配。

验证命令（Microsoft JDK 21，2026-10-08）：

```text
gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest
  --tests com.fongmi.android.tv.ui.dialog.CustomCspDialogTest
  --tests com.fongmi.android.tv.setting.CustomCspSettingTest
  --no-daemon --max-workers=2 --console=plain
```

- 修复前：8 个测试中 2 个失败，分别准确复现空状态缺失和全量排序通知；其余 6 个通过。
- 最小修复：移动前保存显示坐标，恢复 `notifyItemMoved` 和受影响区间重绑；删除后更新模式/空状态可见性。
- 修复后：**8/8 通过，0 跳过，0 错误**；包含 Java 与资源编译，`BUILD SUCCESSFUL in 2m 3s`。
- 报告：`app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/TEST-*.xml`；本机完整红/绿日志保留在 `F:/temp/webhtv-search-review-{red,green}.log`。
- 最终 `git diff --check` 通过。最初 LSP 三个文件检查超时；修复后主动检查两个变更 Java 文件均确认 clean。
- 复评：两项缺陷均已修复，恢复的排序通知与父提交设计一致；未引入新的过滤/倒序坐标问题，没有剩余阻塞项。
- 未改播放器/native，任务图没有 CMake/native 构建，也未生成正式 APK；未进行设备/模拟器实测，不将 JVM 测试描述为设备验证。
- 构建使用一次性 daemon；结束后检查没有残留 Java 进程。保留验证报告与复用缓存，不删除预先存在的 `app/.cxx/`。
- 既有 CXX5202、资源命名空间及弃用 API 警告不是本次改动引入，未扩大范围处理。

## Recovery anchor

- 守卫：`site-injection-search-review`，quick-fix，基准 HEAD 如上；当前 active。
- 未提交修改：`CustomCspDialog.java` 两处局部修复、新增 `app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/CustomCspDialogTest.java` 和本记录；验证全部通过。
- 已知环境：Windows 默认 JAVA_HOME 指向 Java 8；使用 Microsoft JDK 21。Git Bash 通过 `G:/Git/usr/bin/bash.exe` 启动并显式补齐 `/usr/bin:/mingw64/bin`，日志重定向至本机临时目录，可正常执行守卫。
- 回滚：若需要，撤销本次独立修复提交；不重写原功能提交。
- 下一步：通过守卫原子提交上述三个文件、立即创建恢复标签，并推送 `origin/Silent1566` 与本次新标签；不重跑已通过的检查。
