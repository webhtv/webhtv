# C26 beta 合并与 dev2 选源高亮复审

## 任务信息

- 任务 ID：beta-merge-dev2-review-20260927
- 分支：dev2
- 合并目标：origin/beta
- 合并前 HEAD：e65243842bc5753703a850ec401aa143665374ce
- beta 目标：e55f03f57f51998210bbba95c7886edf1836f23b
- 合并提交：1a27828360c4c79be3e728e2e47111cbba545e83
- 当前日期：2026-09-27（Asia/Shanghai）

## 完成内容

- 将 origin/beta 最新 11 个提交合并进 dev2，合并无冲突。
- 保留本任务已有的 TV 选源高亮改动，没有重新带入远端已移除/回退的主题或回退提交。
- 对 `origin/beta..HEAD` 净差异进行了两轮评审，确认只包含本地 TV 选源高亮相关改动。
- 检查了 `QuickAdapter` 的 activated 状态、`selector_video_item.xml` 的 `state_activated` 分支、`QuickSearchDialog` 的 `currentSiteKey` 注入链路，以及 beta 新增的配置/站源主题和代理重定向改动，未发现需要继续修改的问题。

## 验证证据

执行：

```powershell
.\\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.adapter.QuickAdapterSelectionTest --tests com.fongmi.android.tv.ui.adapter.SiteAdapterSelectionTest
```

结果：BUILD SUCCESSFUL，两个定向测试通过，相关 Leanback arm64 Debug 源码与测试源码编译通过。

补充静态检查：

```powershell
git diff --check origin/beta..HEAD
```

结果：通过，无空白错误。

## PR 边界

相对 `origin/beta`，`dev2` 净差异为 7 个文件、160 行新增、5 行删除：

- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/QuickAdapter.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/QuickSearchDialog.java`
- `app/src/leanback/res/drawable/shape_site_item_focused.xml`
- `app/src/leanback/res/drawable/shape_site_item_selected.xml`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/adapter/QuickAdapterSelectionTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/adapter/SiteAdapterSelectionTest.java`

## 回滚锚点

- 合并前：`e65243842bc5753703a850ec401aa143665374ce`
- 合并后未提交记录前：`1a27828360c4c79be3e728e2e47111cbba545e83`
- 远端 beta：`e55f03f57f51998210bbba95c7886edf1836f23b`

## 状态与下一步

- 合并和复审完成，定向验证通过。
- 下一步：提交任务文档、推送 `dev2`，创建 `dev2 -> beta` 中文 PR；只创建 PR，不执行合并。
