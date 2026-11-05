package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private boolean resumeParent;
    private boolean parentResumed;

    /** 无收尾回调的入口：等价于四参版本传入 {@code null}。 */
    public static void show(@Nullable FragmentActivity activity, @Nullable String title, @Nullable String html) {
        show(activity, title, html, null);
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
        if (binding == null) return;
        binding.pluginPanelTitle.setText(title == null || title.isEmpty() ? getString(R.string.plugin_panel_title) : title);
        binding.pluginPanelClose.setOnClickListener(view -> dismiss());
        WebView view = binding.pluginPanelWeb;
        webView = view;
        WebViewUtil.configureBase(view, TAG);
        WebViewUtil.configureHome(view);
        view.loadDataWithBaseURL(null, html == null ? "" : html, "text/html", "utf-8", null);
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
