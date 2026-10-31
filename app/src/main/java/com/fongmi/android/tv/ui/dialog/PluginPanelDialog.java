package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogPluginPanelBinding;
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
     * @param onClosed 面板关闭后的收尾动作（用来把焦点还给入口控件）；可为 {@code null}
     */
    public static void show(@Nullable FragmentActivity activity, @Nullable String title, @Nullable String html,
            @Nullable Runnable onClosed) {
        if (activity == null || activity.isFinishing()) return;
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        new PluginPanelDialog().bind(title, html, onClosed).show(activity.getSupportFragmentManager(), TAG);
    }

    private PluginPanelDialog bind(@Nullable String title, @Nullable String html, @Nullable Runnable onClosed) {
        this.title = title;
        this.html = html;
        this.onClosed = onClosed;
        return this;
    }

    /**
     * 关闭后执行收尾动作。
     *
     * <p>放在 {@code onDismiss} 而不是 {@code onDestroyView}：用户按返回键、点关闭按钮、
     * 点面板外侧三条路径最终都会走到这里，而 {@code onDestroyView} 只覆盖视图被销毁的情形。
     * 回调抛异常不让它冒出去——面板收尾失败不该比"焦点没还回去"更严重。
     */
    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
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
