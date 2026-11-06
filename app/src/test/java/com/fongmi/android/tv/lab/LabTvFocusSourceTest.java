package com.fongmi.android.tv.lab;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实验室详情页必须能用遥控器操作焦点。
 *
 * <p>缺陷实测（leanback arm64 debug，{@code 192.168.50.3:5561}，{@code mInTouchMode=false}）：
 * {@code LabDetailActivity} 的 2 秒轮询调用 {@code updateButtons()} → {@code commandAdapter
 * .notifyDataSetChanged()}，RecyclerView 回收并重新挂载全部子 View，焦点所在的「运行」按钮被移除，
 * 框架把焦点丢回工具栏返回键。实测按下方向键聚焦命令行后 1.2 秒内焦点即回到工具栏，
 * 遥控器按不到「运行」——用户只能靠 lab 配置里的 {@code auto_execute} 启动命令。
 *
 * <p>本测试锁住两条不变量：命令列表只做状态差异刷新；实验室自己的可聚焦控件有聚焦态。
 * 设备上的焦点保持由任务文档记录的遥控实测覆盖，这里只锁会导致回归的源码/资源形态。
 */
public class LabTvFocusSourceTest {

    private static final String ACTIVITY = "src/main/java/com/fongmi/android/tv/lab/LabDetailActivity.java";
    private static final String ADAPTER = "src/main/java/com/fongmi/android/tv/lab/LabCommandAdapter.java";
    private static final String DETAIL_LAYOUT = "src/main/res/layout/activity_lab_detail.xml";

    /** 实验室自己绘制的可聚焦控件：缺 state_focused 就等于遥控看不到焦点。 */
    private static final String[] FOCUS_VISIBLE_DRAWABLES = {
            "src/main/res/drawable/shape_lab_card.xml",
            "src/main/res/drawable/shape_lab_run_btn.xml",
            "src/main/res/drawable/shape_lab_stop_btn.xml",
            "src/main/res/drawable/shape_lab_click_btn.xml",
            "src/main/res/drawable/selector_lab_group_tab.xml",
            "src/main/res/drawable/selector_lab_icon_btn.xml"
    };

    @Test
    public void commandEditorBuildsTheFocusChain() throws Exception {
        String editor = stripComments(read("src/main/java/com/fongmi/android/tv/lab/LabCommandEditDialog.java"));
        String show = methodBody(editor, "dialog.setOnShowListener");
        assertTrue("the dialog must build an explicit focus chain", show.contains("collectFocusChain(root, chain);"));
        assertTrue("dialog buttons are the tail of that chain",
                show.contains("addToChain(chain, dialog.getButton(AlertDialog.BUTTON_POSITIVE));"));
        assertTrue("the chain must take over the vertical keys", show.contains("releaseVerticalKeys(chain);"));
        assertTrue("the first chain entry owns the initial focus", show.contains("chain.get(0).requestFocus();"));
        // 「新建命令」不会 setNeutralButton，getButton 返回 null；直接 add 会让 null 占一个链槽，
        // 上下键停在那里被静默吞掉，取消/添加按钮按不到。
        assertFalse("a missing dialog button must not occupy a chain slot",
                show.contains("chain.add(dialog.getButton("));
    }

    @Test
    public void commandEditorReleasesVerticalDpadKeys() throws Exception {
        String editor = stripComments(read("src/main/java/com/fongmi/android/tv/lab/LabCommandEditDialog.java"));
        String navigate = methodBody(editor, "private static void releaseVerticalKeys");
        assertTrue("DOWN must move the chain forward", navigate.contains("KeyEvent.KEYCODE_DPAD_DOWN"));
        assertTrue("UP must move the chain backward", navigate.contains("KeyEvent.KEYCODE_DPAD_UP"));
        assertTrue("the chain index decides the next target", navigate.contains("requestFocus();"));
        assertTrue("autocomplete popup must keep native arrow selection", navigate.contains("isPopupShowing()"));
        // 触摸模式下文本控件同样吃掉上下键，把触摸模式当跳过条件会让焦点在弹窗里出不去。
        assertFalse("touch mode must not disable the vertical-key escape", navigate.contains("isInTouchMode()"));
        // 链式索引移动取代几何搜索：focusSearch 在可滚动弹窗里取决于当前滚动位置。
        assertFalse("geometric focus search must not drive the editor chain", navigate.contains("focusSearch("));

        String collect = methodBody(editor, "private static void collectFocusChain");
        assertTrue("an unfocusable field must not occupy a chain slot", collect.contains("view.isFocusable()"));
        assertTrue("a hidden row must not occupy a chain slot", collect.contains("getVisibility() != View.VISIBLE"));
        // 参数行是 TextInputEditText / MaterialAutoCompleteTextView / MaterialSwitch，
        // 按具体类型白名单收集会漏掉其中几类。
        assertFalse("the chain must not filter by concrete control type", collect.contains("instanceof EditText"));
        assertFalse("a switch row must stay reachable through the chain", collect.contains("instanceof MaterialSwitch"));
    }

    /**
     * 编辑弹窗的焦点顺序只由 {@code LabCommandEditDialog} 的焦点链决定。
     *
     * <p>布局上的 nextFocus* 会被链里的上下键拦截完全覆盖，两套机制并存时改错一处也不会有测试察觉。
     */
    @Test
    public void commandEditorDoesNotDeclareNextFocus() throws Exception {
        String layout = stripComments(read("src/main/res/layout/dialog_add_command.xml"));
        assertTrue("the editor layout must still be the one under review",
                layout.contains("android:id=\"@+id/inputCommand\""));
        assertFalse("the editor focus order is owned by the chain, not by nextFocus*",
                layout.contains("android:nextFocus"));
    }

    /**
     * 周期刷新不能再整表重建列表。
     *
     * <p>{@code updateButtons()} 由 2 秒轮询驱动，任何整表刷新都会周期性抢走遥控焦点。
     */
    @Test
    public void periodicRefreshUsesDiffUpdatesOnly() throws Exception {
        String activity = stripComments(read(ACTIVITY));
        assertTrue("updateButtons() must refresh the command list through the diff path",
                activity.contains("commandAdapter.refreshStates();"));
        assertFalse("a full notifyDataSetChanged() on the 2s poll steals the remote focus",
                activity.contains("commandAdapter.notifyDataSetChanged()"));
    }

    /**
     * 差异刷新必须按行状态判断，并且只做局部通知。
     *
     * <p>{@code notifyItemChanged} 复用同一个 ViewHolder；整表刷新会回收焦点所在的视图。
     */
    @Test
    public void diffRefreshSkipsUnchangedRowsAndNotifiesSingleItems() throws Exception {
        String refresh = methodBody(stripComments(read(ADAPTER)), "public void refreshStates()");
        assertTrue("refreshStates() must compare per-row state before notifying",
                refresh.contains("stateOf(rows.get(i))"));
        assertTrue("unchanged rows must be skipped entirely",
                refresh.contains("continue;"));
        assertTrue("changed rows are refreshed in place",
                refresh.contains("notifyItemChanged(i)"));
        assertFalse("refreshStates() must never rebuild the whole list",
                refresh.contains("notifyDataSetChanged"));
    }

    /** 行状态签名必须覆盖 onBindViewHolder 会改变的全部可视差异。 */
    @Test
    public void rowStateSignatureCoversRunningAndLabel() throws Exception {
        String state = methodBody(stripComments(read(ADAPTER)), "private String stateOf(");
        assertTrue("the running state drives the dot, tag and button",
                state.contains("LabRunner.isRunning("));
        assertTrue("the label is the only other visible difference",
                state.contains("command.name == null ? command.description : command.name"));
    }

    /**
     * 实验室自绘的可聚焦控件必须都有聚焦态。
     *
     * <p>设备实测：命令行的「运行」按钮聚焦前后像素完全一致（仅有按下态），
     * 遥控用户看不到焦点落在哪一行/哪个按钮上。
     */
    @Test
    public void labFocusableControlsExposeAFocusedState() throws Exception {
        for (String file : FOCUS_VISIBLE_DRAWABLES) {
            assertTrue(file + " must render a focused state for remote navigation",
                    stripComments(read(file)).contains("android:state_focused=\"true\""));
        }
    }

    /**
     * 命令弹窗的初始焦点必须落在按钮上。
     *
     * <p>BottomSheetDialog 默认聚焦第一个可聚焦控件（命令预览 EditText）。实测遥控方向键在单行输入框里
     * 出不去（DOWN / RIGHT / UP 都不移动焦点，只有 TAB 能到按钮），电视遥控器没有 TAB，
     * 用户打开了弹窗却按不到「运行」。
     */
    @Test
    public void commandSheetAnchorsFocusOnAButton() throws Exception {
        String sheet = stripComments(read("src/main/java/com/fongmi/android/tv/lab/LabCommandSheet.java"));
        assertTrue("the sheet must move the initial focus onto a button",
                sheet.contains("requestSheetFocus();"));
        assertTrue("the run button wins when it is usable, otherwise the edit button",
                sheet.contains("sheetBtnRun.getVisibility() == View.VISIBLE && sheetBtnRun.isEnabled() ? sheetBtnRun : sheetBtnEdit"));

        // 锚点必须是轮询本体（mStateRunnable 匿名类），不能用 "public void run()"：
        // 那个签名在本文件里第一处命中的是轮询的 run()（约 60 行），第二处才是
        // LabCommandSheet 自己的 run()（约 319 行），靠 indexOf 的先后顺序太脆。
        String poll = methodBody(sheet, "private final Runnable mStateRunnable");
        assertFalse("the 1s state poll must not steal focus back while the user types",
                poll.contains("requestSheetFocus"));
    }

    /**
     * 「导入包」弹窗必须能用遥控器操作。
     *
     * <p>实测（leanback arm64 debug，1920x1080）：弹窗可聚焦节点只有粘贴框、`选择离线包文件`、取消、导入；
     * 初始焦点落在多行粘贴框，遥控 DOWN / UP / RIGHT 都不移动焦点（被当光标键吃掉），只有 TAB 能跳出去。
     * 电视遥控器没有 TAB，用户打开弹窗后什么都按不动。
     */
    @Test
    public void importDialogIsReachableWithTheRemote() throws Exception {
        String activity = stripComments(read("src/main/java/com/fongmi/android/tv/lab/LabActivity.java"));
        assertTrue("the paste field must release the vertical D-pad keys to focus search",
                activity.contains("class LabPasteEditText extends AppCompatEditText"));
        assertTrue("only the vertical keys are released, horizontal cursor movement stays",
                activity.contains("keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN"));
        assertTrue("the file-picker link must be focusable",
                activity.contains("select.setFocusable(true);"));
        assertTrue("the link must render a focused state",
                activity.contains("select.setBackgroundResource(R.drawable.selector_lab_link);"));
        assertTrue("the dialog must anchor the initial focus on the file-picker link",
                activity.contains("select.requestFocus();"));
        assertTrue("the link must hand UP back to the paste field (geometry alone does not work)",
                activity.contains("select.setNextFocusUpId(edit.getId());"));
        assertTrue("the link drawable must define a focused state",
                stripComments(read("src/main/res/drawable/selector_lab_link.xml"))
                        .contains("android:state_focused=\"true\""));
        // 环色必须与链接文字色同源（LabActivity 用 R.color.webhtv_color_primary）。用固定的
        // @color/accent 会在浅色弹窗面板 #E7E8EF 上掉到 2.56:1，低于仓库既有的「焦点环 ≥3:1」门。
        String link = stripComments(read("src/main/res/drawable/selector_lab_link.xml"));
        assertTrue("the link focus ring must use the theme token (5.23:1 light / 7.86:1 night)",
                link.contains("@color/webhtv_color_primary"));
        assertFalse("the fixed lab accent is only 2.56:1 on the light dialog panel",
                link.contains("@color/accent"));
    }

    /**
     * 「实验室配置源」弹窗必须能用遥控器操作。
     *
     * <p>实测（leanback arm64 debug，1920x1080）：打开后焦点落在「配置源」下拉上，遥控 DOWN / UP 全都不移动
     * （被 AutoCompleteTextView / 可编辑文本当光标键吃掉），到不了文件夹图标、URL、根目录、开关和取消/确定；
     * 下拉展开后 DOWN 能移动高亮，但按 OK 只关闭列表、不回调选中项（点选同一行才生效）。
     */
    @Test
    public void settingsDialogReleasesVerticalKeysAndAppliesTheDropdownPick() throws Exception {
        String activity = stripComments(read("src/main/java/com/fongmi/android/tv/lab/LabActivity.java"));
        assertTrue("the settings dialog must install the vertical-key escape",
                activity.contains("escapeVerticalKeys(cycle, dropdown, input, rootInput, proxyPort, proxyNoProxy);"));
        String escape = methodBody(activity, "private void escapeVerticalKeys(");
        assertTrue("vertical keys move focus",
                escape.contains("view.focusSearch("));
        assertTrue("the open dropdown list is dismissed before moving focus",
                escape.contains("dropdown.dismissDropDown();"));
        assertTrue("the confirm key cycles the dropdown instead of using the unusable popup",
                escape.contains("cycle.run();"));
        assertTrue("the cycle advances to the next option",
                activity.contains("+ 1) % items.length"));
        assertTrue("touch picking and the remote cycle share one code path",
                activity.contains("dropdown.setOnItemClickListener((parent, view, position, id) -> pick.accept(position));"));
    }

    /**
     * 配置源下拉必须能直接 DOWN 到「选本地配置」的文件夹按钮，且两种配置源模式都要走对。
     *
     * <p>实测（leanback arm64 debug，1920x1080）：焦点搜索按加权距离比较，文件夹在右侧、
     * 根目录在正下方，于是 DOWN 直接从下拉跳到根目录，跳过了用户想点的文件按钮。
     *
     * <p>{@code nextFocus*} 会顺着「目标控件自己的连线」继续走、并跳过不可见目标，
     * 所以把链条写成 下拉 → 文件夹 → URL 输入框 → 根目录：本地配置模式下 URL 输入框是 GONE，
     * 链条自然落到根目录；网络 URL 模式下文件夹是 GONE，链条落到 URL 输入框。
     */
    @Test
    public void settingsDialogWiresTheDropdownToTheFileButton() throws Exception {
        String layout = stripComments(read("src/main/res/layout/dialog_lab_settings.xml"));
        assertTrue("the dropdown must hand focus down to the file button",
                layout.contains("android:nextFocusDown=\"@+id/folder\""));
        assertTrue("the file button must lead back up to the dropdown",
                layout.contains("android:nextFocusUp=\"@+id/sourceDropdown\""));
        assertTrue("the file button must chain through the URL field",
                layout.contains("android:nextFocusDown=\"@+id/input\""));
        assertTrue("the URL field must chain down to the root input",
                layout.contains("android:nextFocusDown=\"@+id/rootInput\""));
    }

    /**
     * 配置源弹窗的取消/确定必须按「就近」回到上方控件，不能硬钉到某个固定控件。
     *
     * <p>实测（leanback arm64 debug，1920x1080）：两个按钮在 ScrollView 之外，焦点搜索按绝对坐标比较，
     * 内容滚到底时按 UP 落到「不走代理的域名」——这正是用户视觉上方的控件，属于正确行为。
     * 曾一度把它硬钉到「根目录」，反而与所见不符（用户已指出），故锁住「不得钉死」。
     */
    @Test
    public void settingsDialogButtonsKeepGeometricUpFocus() throws Exception {
        String activity = stripComments(read("src/main/java/com/fongmi/android/tv/lab/LabActivity.java"));
        assertFalse("no button-focus pinning helper should remain",
                activity.contains("wireButtonFocus("));
        assertFalse("the dialog buttons must not be pinned to a fixed target",
                activity.contains("getButton(AlertDialog.BUTTON_NEGATIVE).setNextFocusUpId")
                        || activity.contains("getButton(AlertDialog.BUTTON_POSITIVE).setNextFocusUpId"));
    }

    /** 分组标签的聚焦态必须由 selector 通过 state_focused 接上（聚焦图形本身是纯 shape）。 */
    @Test
    public void groupTabSelectorWiresTheFocusedShape() throws Exception {
        String selector = stripComments(read("src/main/res/drawable/selector_lab_group_tab.xml"));
        assertTrue("the focused branch must come first so it wins over activated",
                selector.indexOf("state_focused") < selector.indexOf("state_activated"));
        assertTrue("the focused branch must point at the focused shape",
                selector.contains("android:drawable=\"@drawable/shape_lab_group_tab_focused\""));
        assertTrue("the focused shape must keep the tab padding so the label does not jump",
                read("src/main/res/drawable/shape_lab_group_tab_focused.xml").contains("android:left=\"12dp\""));
    }

    /**
     * 详情页图标按钮必须显式声明焦点邻居。
     *
     * <p>实测（leanback arm64 debug，小 UI 缩放）：从「下载安装」按 DOWN 会直接跳到第一条命令，
     * 跳过终端/新增/刷新三个图标 —— 焦点搜索按加权距离（13×主轴² + 次轴²）比较，
     * 图标在右侧且行高只有 32px 时，正下方的命令行更「近」。用 nextFocus* 固定顺序即可，
     * 目标控件不可见时框架会自动回退到几何搜索。
     */
    @Test
    public void detailHeaderIconsDeclareFocusNeighbours() throws Exception {
        String layout = stripComments(read(DETAIL_LAYOUT));
        assertTrue("the download button must hand focus down to the icon row",
                layout.contains("android:nextFocusDown=\"@+id/btnTerminal\""));
        assertTrue("the terminal icon must lead back up to the download button",
                layout.contains("android:nextFocusUp=\"@+id/btnDownload\""));
        assertTrue("the three icons must chain left to right",
                layout.contains("android:nextFocusRight=\"@+id/btnAddCommand\"")
                        && layout.contains("android:nextFocusRight=\"@+id/btnRefreshCommand\"")
                        && layout.contains("android:nextFocusLeft=\"@+id/btnAddCommand\""));
    }

    /** 详情页顶部图标按钮必须用带聚焦态的 selector，而不是只有按下态的 borderless ripple。 */
    @Test
    public void detailHeaderIconButtonsUseTheFocusSelector() throws Exception {
        String layout = stripComments(read(DETAIL_LAYOUT));
        Matcher matcher = Pattern.compile("android:background=\"([^\"]+)\"").matcher(layout);
        int focusSelectors = 0;
        while (matcher.find()) {
            if ("@drawable/selector_lab_icon_btn".equals(matcher.group(1))) focusSelectors++;
        }
        assertTrue("terminal / add / refresh icons must use selector_lab_icon_btn, found " + focusSelectors,
                focusSelectors == 3);
        assertFalse("a borderless ripple has no focused state",
                layout.contains("?attr/selectableItemBackgroundBorderless"));
    }

    // ------------------------------------------------------------------ helpers

    /** 取出方法体，避免断言被同文件其它方法的文本意外满足。 */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature + " is missing", start >= 0);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError(signature + " has no closing brace");
    }

    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?sm)^\\s*/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
