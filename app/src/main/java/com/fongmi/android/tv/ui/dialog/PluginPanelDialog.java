package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogPluginPanelBinding;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.WebViewUtil;

/**
 * 插件面板：把插件脚本返回的 html 渲染在一块侧滑面板里。
 *
 * <p><b>为什么是侧滑而不是底部弹层。</b>电视端控制条在屏幕下缘，底部弹层会盖住刚点过的 chip，
 * 关闭后焦点也没有落点；侧滑面板从右侧推进，控制条始终可见，关闭后焦点回到原 chip 是自然的。
 * 这与 {@code docs/PLUGIN-01} §8.2 的交互裁定一致。
 *
 * <p><b>只做容器，不做内容。</b>面板不解析、不注入、不拼接插件返回的 html，直接用
 * {@code loadDataWithBaseURL} 交给 WebView。任何"宿主帮忙加工 html"的逻辑都会变成
 * 第二套模板系统，而插件作者已经有完整的 html/css 能力。
 */
public final class PluginPanelDialog extends BaseSideSheetDialog {

    private static final String TAG = "plugin-panel";
    private static final String ARG_RESUME_PARENT = "resumeParent";
    /** 请求标识也要进 Bundle：重建走无参构造器，字段不会重放，重建实例会拿不到自己的 token。 */
    private static final String ARG_TOKEN = "token";
    /** 标题要进 Bundle：重建后标题栏不能退回占位文案。 */
    private static final String ARG_TITLE = "title";
    /**
     * 内容要进 Bundle，且 {@code null} 本身就是语义（还在等结果）。
     *
     * <p>真正的重建来源是进程被回收后恢复，以及 fontScale / density / locale 变化——
     * 这两类都会把 Activity 连同 Fragment 一起重建（旋屏不会：宿主页的 {@code configChanges}
     * 已声明 orientation|screenSize）。只看字段，重建实例会以为从未填过正文，面板永久转圈。
     */
    private static final String ARG_HTML = "html";
    /**
     * 正文没有进 Bundle 的标记（与 {@link #ARG_HTML} 互斥）。
     *
     * <p>重建后读到这个标记就说明"内容不可恢复"，面板只能关掉，见 {@link #initView}。
     */
    private static final String ARG_HTML_OVERSIZED = "htmlOversized";
    /**
     * 允许写进 arguments 的正文上限，单位是**字符数**。
     *
     * <p><b>为什么按字符数定，而不是字节数。</b>Bundle 走 Binder 事务时的体积估算要做一次
     * UTF-8 编码，而那正是我们要避免的开销与不确定性；`String.length()` 在写入之前就能拿到，
     * 且是最坏情况下（每字符 2 字节 UTF-16 + Parcel 长度前缀）体积的单调上界，判起来零成本。
     *
     * <p><b>为什么是 32K 这个量级。</b>整份 savedInstanceState 必须在一个 Binder 事务里过
     * （系统预算约 1MB，实测 500KB 上下就开始 {@code TransactionTooLargeException}）。32K 字符
     * 约 64KB，既远低于该阈值，也给同一份 Bundle 里的 FragmentManager 状态留了余量；再放大一个
     * 量级就会把"用户按 Home"变成崩溃点。
     *
     * <p><b>它和网络层的上限是两件事。</b>{@code PluginNetBridge.MAX_RESPONSE_BYTES} 限的是**单次
     * HTTP 响应** 512KB，而插件可以多次请求后自行拼接，动作返回值本身没有长度上限。这条常量是
     * **宿主给 arguments 体积定的上界**，与那 512KB 没有推导关系，不要互相替换。
     */
    private static final int MAX_PERSISTED_HTML_CHARS = 32_768;

    @Nullable
    private String title;
    @Nullable
    private String html;
    @Nullable
    private DialogPluginPanelBinding binding;
    @Nullable
    private WebView webView;
    @Nullable
    private Runnable onClosed;
    /** 本次请求标识：用于把异步回来的结果配到打开它的那个面板上，防止串台。 */
    @Nullable
    private String token;
    private boolean resumeParent;
    private boolean parentResumed;
    /** 超限面板只关一次：root view 与主线程两条投递可能都会跑到，靠这个标记收口。 */
    private boolean oversizedShellClosing;

    /** 无收尾回调的入口：等价于四参版本传入 {@code null}。 */
    public static void show(@Nullable FragmentActivity activity, @Nullable String title, @Nullable String html) {
        show(activity, title, html, null);
    }

    /**
     * 打开面板并先显示加载中，数据由随后的 {@link #fill} 填入。
     *
     * <p><b>为什么要先把面板打开。</b>插件一次调用最长 5 秒，豆瓣搜索+详情+短评通常 1~3 秒。
     * 原先的实现是"结果回来才弹面板"，这段空白里用户点了 chip 看不到任何变化，会以为没点上，
     * 于是连点——而那正好又叠加出多个面板。先开壳既给了即时反馈，也天然抑制了连点。
     *
     * <p>若同一页已有面板在显示，本方法复用那个实例（不新开第二个），把它切回加载态，
     * 并换发一个新 token：旧 token 从此失效，上一次未完成的结果回来时会被丢弃。
     *
     * @return 本次请求的标识，交给 {@link #fill} 配对；{@code null} 表示面板没能打开
     */
    @Nullable
    public static String showLoading(@Nullable FragmentActivity activity, @Nullable String title,
            @Nullable Runnable onClosed) {
        if (activity == null || activity.isFinishing()) return null;
        String token = Long.toString(System.nanoTime());
        PluginPanelDialog existing = find(activity);
        if (existing != null) {
            existing.token = token;
            // 复用必须把面板真正切回"等结果"：fill(title, null) 会清掉旧内容并同步进 Bundle，
            // 空结果回来时 dismissLoading 才认得出这是个壳（留着旧 html 它就永远撤不掉壳）。
            existing.fill(title, null);
            return token;
        }
        if (activity.getSupportFragmentManager().isStateSaved()) return null;
        PlaybackActivity playback = activity instanceof PlaybackActivity ? (PlaybackActivity) activity : null;
        boolean resumeParent = playback != null && playback.pauseForOverlayPlayback();
        PluginPanelDialog dialog = new PluginPanelDialog().bind(title, null, onClosed);
        dialog.token = token;
        Bundle args = new Bundle();
        args.putBoolean(ARG_RESUME_PARENT, resumeParent);
        dialog.setArguments(args);
        dialog.persistState();
        try {
            dialog.show(activity.getSupportFragmentManager(), TAG);
        } catch (RuntimeException e) {
            if (playback != null) playback.resumeAfterOverlayPlayback(resumeParent);
            return null;
        }
        return token;
    }

    /**
     * 把结果填进由 {@link #showLoading} 打开的那个面板。
     *
     * <p>token 不匹配就什么都不做：用户可能已经关掉面板、或又点了另一个 chip，
     * 这时把上一次的结果灌进来等于串台。面板已不存在时同样返回。
     */
    public static void fill(@Nullable FragmentActivity activity, @Nullable String token, @Nullable String html) {
        if (activity == null || token == null || html == null || html.isEmpty()) return;
        PluginPanelDialog existing = find(activity);
        if (existing == null || !token.equals(existing.token)) return;
        existing.fill(null, html);
    }

    /**
     * 撤销一个仍在等待结果的加载壳。
     *
     * <p>插件返回空（抓取失败、被隔离、运行时抛错）时必须走这里：留着转圈的面板比没有面板
     * 更糟，用户会一直等一个永远不会来的结果。
     */
    public static void dismissLoading(@Nullable FragmentActivity activity, @Nullable String token) {
        if (activity == null || token == null) return;
        PluginPanelDialog existing = find(activity);
        if (existing == null || !token.equals(existing.token)) return;
        // 已有内容的面板不撤：那不是"等结果"的壳，是用户正在读的页面。
        // 这条守卫能成立的前提是"切回加载态就一定清掉 html"，见 fill()。
        if (existing.html != null) return;
        try {
            existing.dismissAllowingStateLoss();
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static PluginPanelDialog find(@Nullable FragmentActivity activity) {
        if (activity == null) return null;
        try {
            Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(TAG);
            return fragment instanceof PluginPanelDialog dialog && dialog.isAdded() ? dialog : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 更新面板内容。{@code html} 为 {@code null} 表示切回加载态。
     *
     * <p><b>为什么切回加载态要清掉 {@code this.html}。</b>"还在等结果"与"已有内容"必须是两个
     * 可区分的事实：{@link #dismissLoading} 只撤前者。留着旧 html，复用面板后插件返回空时
     * 就会被当成"用户正在阅读"，加载壳永久转圈。
     *
     * <p>视图尚未建好（面板刚 show、{@code initView} 还没跑）时只更新字段，
     * {@code initView} 会读到最新值。
     */
    private void fill(@Nullable String newTitle, @Nullable String html) {
        if (newTitle != null) this.title = newTitle;
        this.html = html;
        persistState();
        DialogPluginPanelBinding current = binding;
        if (current == null) return;
        if (newTitle != null) current.pluginPanelTitle.setText(newTitle);
        boolean loading = html == null;
        current.pluginPanelLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        current.pluginPanelWeb.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        if (!loading) {
            WebView view = this.webView;
            if (view != null) view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        }
    }

    /**
     * 把 {@link #title} / {@link #html} / {@link #token} 同步进 arguments。
     *
     * <p><b>为什么在每次变更时同步，而不是覆写 {@code onSaveInstanceState}。</b>token 已经是
     * "写进 arguments"的写法，内容跟着它走只有一套语义；覆写还会和 DialogFragment 自己保存的
     * 对话框状态抢同一份 Bundle。
     *
     * <p><b>正文超限时不写正文，只写 {@link #ARG_HTML_OVERSIZED}。</b>arguments 会随
     * {@code onSaveInstanceState} 走 Binder 事务，几百 KB 的正文足以让宿主在用户退到后台时抛
     * {@code TransactionTooLargeException}。上限见 {@link #MAX_PERSISTED_HTML_CHARS}；正文与标记
     * 严格互斥——写正文就清标记，写标记就一定不写正文，否则重建实例无法判断内容到底在不在。
     *
     * <p>{@code html} 允许是 {@code null}，并对 {@code null} 做显式写空：重建后 {@code initView}
     * 要靠它区分"等结果的壳"和"有内容的页"。
     */
    private void persistState() {
        Bundle args = getArguments();
        // 两个入口都在 setArguments 之后才走到这里，恢复实例的 arguments 也由系统还原：
        // 为 null 只可能是调用方违约，此时放弃同步，不去碰可能已保存的 FragmentManager。
        if (args == null) return;
        boolean oversized = html != null && html.length() > MAX_PERSISTED_HTML_CHARS;
        args.putString(ARG_TOKEN, token);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_HTML, oversized ? null : html);
        args.putBoolean(ARG_HTML_OVERSIZED, oversized);
    }

    /**
     * 打开面板。
     *
     * <p>Activity 正在结束或 FragmentManager 状态已保存时直接返回——此时提交事务会抛
     * {@code IllegalStateException}，而插件动作是异步回来的，完全可能落在页面销毁之后。
     *
     * <p><b>打开前先暂停宿主播放。</b>面板是阅读界面：视频继续播会盖住声音、继续耗流量，
     * 而用户此刻的注意力在短评上。恢复动作不在这里做，见 {@link #onDismiss}。
     *
     * <p><b>为什么「是否要恢复」进 Bundle 而不是留字段。</b>Fragment 被系统重建时走无参构造器
     * （进程被回收后恢复，以及 fontScale / density / locale 这类会重建 Activity 的变化；旋屏不会，
     * 宿主页的 {@code configChanges} 已声明 orientation|screenSize），字段值不会被重放——重建后的
     * 实例会以为自己从未暂停过播放，关闭面板时静默跳过恢复，视频就永久停在暂停帧。
     * Bundle 活得比实例长，重建后仍在。
     * 恢复时也不持有 Activity 字段：持有一个 Activity 会让它在面板开着期间无法回收。
     *
     * @param onClosed 面板关闭后的收尾动作（用来把焦点还给入口控件）；可为 {@code null}
     */
    public static void show(@Nullable FragmentActivity activity, @Nullable String title, @Nullable String html,
            @Nullable Runnable onClosed) {
        if (activity == null || activity.isFinishing()) return;
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        PlaybackActivity playback = activity instanceof PlaybackActivity ? (PlaybackActivity) activity : null;
        boolean resumeParent = playback != null && playback.pauseForOverlayPlayback();
        PluginPanelDialog dialog = new PluginPanelDialog().bind(title, html, onClosed);
        Bundle args = new Bundle();
        args.putBoolean(ARG_RESUME_PARENT, resumeParent);
        dialog.setArguments(args);
        dialog.persistState();
        try {
            dialog.show(activity.getSupportFragmentManager(), TAG);
        } catch (RuntimeException e) {
            // 事务提交失败等于面板从未出现：已经按下的暂停必须立刻还回去，
            // 否则用户点了一下没反应，视频却停在那一帧再也不会自己动。
            if (playback != null) playback.resumeAfterOverlayPlayback(resumeParent);
        }
    }

    private PluginPanelDialog bind(@Nullable String title, @Nullable String html, @Nullable Runnable onClosed) {
        this.title = title;
        this.html = html;
        this.onClosed = onClosed;
        return this;
    }

    /**
     * 关闭后恢复播放，再执行收尾动作。
     *
     * <p><b>为什么两条都在这里而不是 {@code onDestroyView}。</b>用户按返回键、点关闭按钮、
     * 点面板外侧三条路径最终都会走到 {@code onDismiss}，而 {@code onDestroyView} 只覆盖
     * 视图被销毁的情形；把恢复播放挂在视图销毁上，任何「只销毁视图不 dismiss」的路径都会
     * 让播放永久停在暂停态。
     *
     * <p><b>为什么先恢复播放再还焦点。</b>焦点归还可能立刻打开另一个遮罩，若顺序反过来，
     * 用户会在视频仍暂停的那一瞬间看到焦点跳走。
     *
     * <p>恢复只发一次（{@code parentResumed} 守卫）；Activity 已不在或正在因配置变更销毁时
     * 跳过——那时持有播放器的旧实例正在退出，不该由它把播放拉起来。
     * 播放页从当前的 {@code getActivity()} 现取，不从字段读：重建后的实例没有那个字段，
     * 而「要不要恢复」已经由 Bundle 里的 {@code ARG_RESUME_PARENT} 带过来了。
     * 回调抛异常不让它冒出去——面板收尾失败不该比"焦点没还回去"更严重。
     */
    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!parentResumed) {
            parentResumed = true;
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isChangingConfigurations() && activity instanceof PlaybackActivity) {
                ((PlaybackActivity) activity).resumeAfterOverlayPlayback(resumeParent);
            }
        }
        Runnable closed = onClosed;
        onClosed = null;
        if (closed == null) return;
        try {
            closed.run();
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        binding = DialogPluginPanelBinding.inflate(inflater, container, false);
        return binding;
    }

    @Override
    protected int getWidth() {
        int width = Math.min(ResUtil.dp2px(480), ResUtil.getScreenWidth(requireContext()) * 2 / 3);
        return Math.max(width, ResUtil.dp2px(280));
    }

    /**
     * 建视图、回填状态。
     *
     * <p><b>正文超限时这个面板必须被关掉。</b>超过 {@link #MAX_PERSISTED_HTML_CHARS} 的正文没有进
     * Bundle（见 {@link #persistState}），重建出来的实例手上什么都没有。此处只有两个选择：留一个
     * 永远转圈的空壳，或者关掉让用户重新点一次入口。选后者——**内容过大无法忠实恢复，宁可关掉
     * 让用户重新点击入口，也不留一个永远转圈的空壳，更不冒把宿主拖崩的风险**：用户重新点击只是
     * 一次操作，而空壳意味着他永远等不到结果。
     *
     * <p>关掉的动作投递到 {@code binding.getRoot()} 上而不是当场执行：{@code initView} 跑在
     * {@code onViewCreated} 里，此刻提交事务会撞上尚未 added 的 Fragment。
     */
    @Override
    protected void initView() {
        // 重建会把视图再建一次，也正好是回读 Bundle 的时机：只有这里能保证新实例知道自己
        // 还欠一次「恢复播放」、自己的 token、以及手上那份内容是否已经到位。
        Bundle args = getArguments();
        boolean oversized = args != null && args.getBoolean(ARG_HTML_OVERSIZED, false);
        resumeParent = args != null && args.getBoolean(ARG_RESUME_PARENT, false);
        // 重建后字段不重放，只能从 Bundle 取回；否则重建实例的 token 为 null，结果回来时比不中，
        // html 也为 null，有内容的面板会退回永久转圈。
        if (args != null) {
            if (token == null) token = args.getString(ARG_TOKEN);
            title = args.getString(ARG_TITLE);
            // 只在 Bundle 真带回了正文时覆盖：超限实例的 Bundle 里没有正文，而同一实例仅重建视图
            // 的情形（字段仍然有效）不该被这个 null 抹掉。
            String restored = args.getString(ARG_HTML);
            if (restored != null) html = restored;
        }
        if (binding == null) return;
        binding.pluginPanelTitle.setText(title == null || title.isEmpty() ? getString(R.string.plugin_panel_title) : title);
        binding.pluginPanelClose.setOnClickListener(view -> dismiss());
        WebView view = binding.pluginPanelWeb;
        webView = view;
        WebViewUtil.configureBase(view, TAG);
        WebViewUtil.configureHome(view);
        // html 为 null 表示"还等着异步结果"：先把壳显示出来并转圈，等 fill() 再灌正文。
        boolean loading = html == null;
        binding.pluginPanelLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        view.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        if (!loading) view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        // 面板是独立窗口：遥控器的方向键只看得到这个窗口里的控件，找不到任何"下一个"就会卡住。
        // 显式给出关闭按钮 ↔ 正文之间的上下通道，方向键才既出得去又回得来。
        if (Util.isLeanback()) {
            binding.pluginPanelClose.setNextFocusDownId(R.id.pluginPanelWeb);
            binding.pluginPanelWeb.setNextFocusUpId(R.id.pluginPanelClose);
        }
        // 正文超限：Bundle 里没有内容可恢复，这个壳永远等不到结果，投递关闭（理由见方法注释）。
        if (oversized && html == null) beginOversizedShellClose(binding.getRoot());
    }

    /**
     * 投递"关掉这个内容不可恢复的面板"。
     *
     * <p>投递而不是当场调用：{@code initView} 跑在 {@code onViewCreated} 里，此刻提交事务会撞上
     * {@code Fragment} 尚未 added 的时机。主路径挂在 root view 上——它在面板自己的窗口里，队列
     * 与面板同生命周期；但视图此刻通常还没 attach，而一个始终没 attach 的视图会把动作永久压在
     * 队列里，那就等于没投递，所以未 attach 时再补一次主线程投递。两条路径由幂等标记收口，
     * 先到的那条生效，不会重复关闭。
     */
    private void beginOversizedShellClose(@NonNull View root) {
        root.post(this::dismissOversizedShell);
        if (!root.isAttachedToWindow()) new Handler(Looper.getMainLooper()).post(this::dismissOversizedShell);
    }

    /** 关闭一个内容无法恢复的面板；投递执行时 Fragment 可能已被移除，失败不外抛。 */
    private void dismissOversizedShell() {
        if (oversizedShellClosing) return;
        oversizedShellClosing = true;
        try {
            dismissAllowingStateLoss();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 释放 WebView。
     *
     * <p>WebView 持有原生渲染进程资源，不显式 destroy 会随面板次数累积。先切
     * {@code about:blank} 再 destroy，避免正在加载的页面在销毁瞬间回调到已解绑的视图。
     */
    @Override
    public void onDestroyView() {
        WebView view = webView;
        webView = null;
        if (view != null) {
            try {
                view.loadUrl("about:blank");
                view.destroy();
            } catch (Throwable ignored) {
            }
        }
        binding = null;
        super.onDestroyView();
    }
}
