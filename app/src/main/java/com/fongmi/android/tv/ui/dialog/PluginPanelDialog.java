package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
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
    /** 请求标识也要进 Bundle：旋屏重建走无参构造器，字段不会重放，重建实例会拿不到自己的 token。 */
    private static final String ARG_TOKEN = "token";

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
        args.putString(ARG_TOKEN, token);
        dialog.setArguments(args);
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
     * <p>视图尚未建好（面板刚 show、#{@code initView} 还没跑）时只更新字段，
     * {@code initView} 会读到最新值。
     */
    private void fill(@Nullable String newTitle, @Nullable String html) {
        if (newTitle != null) this.title = newTitle;
        if (html != null) this.html = html;
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
     * 打开面板。
     *
     * <p>Activity 正在结束或 FragmentManager 状态已保存时直接返回——此时提交事务会抛
     * {@code IllegalStateException}，而插件动作是异步回来的，完全可能落在页面销毁之后。
     *
     * <p><b>打开前先暂停宿主播放。</b>面板是阅读界面：视频继续播会盖住声音、继续耗流量，
     * 而用户此刻的注意力在短评上。恢复动作不在这里做，见 {@link #onDismiss}。
     *
     * <p><b>为什么「是否要恢复」进 Bundle 而不是留字段。</b>旋屏时 FragmentManager 用无参构造器
     * 重建这个 Fragment，字段值不会被重放——重建后的实例会以为自己从未暂停过播放，关闭面板时
     * 静默跳过恢复，视频就永久停在暂停帧。Bundle 活得比实例长，旋屏后仍在。
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
     * 播放页从当前的 {@code getActivity()} 现取，不从字段读：旋屏重建后的实例没有那个字段，
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

    @Override
    protected void initView() {
        // 旋屏重建会把视图再建一次，也正好是回读 Bundle 的时机：只有这里能保证
        // 新实例知道自己还欠一次「恢复播放」。
        Bundle args = getArguments();
        resumeParent = args != null && args.getBoolean(ARG_RESUME_PARENT, false);
        // 旋屏重建后字段不重放，token 只能从 Bundle 取回；否则重建实例的 token 为 null，
        // 结果回来时比不中，面板会永远停在加载态。
        if (token == null && args != null) token = args.getString(ARG_TOKEN);
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
