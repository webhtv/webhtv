package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.github.catvod.crawler.SpiderDebug;

/**
 * Fullscreen WebView playback helper for {@code webview://} live channels.
 * <p>Used for sites whose stream cannot be played by native players (e.g.
 * yangshipin.cn, which uses CMG encryption). The page's own player handles
 * decryption via WebAssembly inside the WebView.</p>
 * <p>Usage: call {@link #attach(Activity, ViewGroup, String)} to overlay a
 * WebView on top of the video container, and {@link #detach()} when the channel
 * changes or the activity is destroyed.</p>
 * <p>单WebView版本，onPageStarted清理JS、注入全屏/静音脚本；系统WebView，无X5</p>
 */
public class WebViewPlayer {

    private static final String TAG = "WebViewPlayer";
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    // onPageStarted FastLoading 清理脚本
    private static final String FAST_LOADING_JS = """
            function FastLoading() {
                const fullscreenBtn = document.querySelector('#player_pagefullscreen_yes_player') || document.querySelector('.videoFull');
                if (fullscreenBtn) return;
                // 清空所有图片的 src 属性，阻止图片加载
                Array.from(document.getElementsByTagName('img')).forEach(img => {
                    img.src = '';
                });
                // 清空特定的脚本 src 属性
                const scriptKeywords = ['login', 'index', 'daohang', 'grey', 'jquery'];
                Array.from(document.getElementsByTagName('script')).forEach(script => {
                    if (scriptKeywords.some(keyword => script.src.includes(keyword))) {
                        script.src = '';
                    }
                });
                // 清空具有特定 class 的 div 内容
                const classNames = ['newmap', 'newtopbz', 'newtopbzTV', 'column_wrapper'];
                classNames.forEach(className => {
                    Array.from(document.getElementsByClassName(className)).forEach(div => {
                        div.innerHTML = '';
                    });
                });
                // 递归调用 FastLoading，每 4ms 触发一次
                setTimeout(FastLoading, 4);
            }
            FastLoading();
            """;
    // 全屏、自动播放、取消静音
    private static final String UNMUTE_VIDEO_JS = """
            var videoEl = null;
            function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
            function setscale(scaletype) {
                if (!videoEl) return;
                let objectFitValue = 'contain', aspectratioValue = 'auto', widthValue = '100%', heightValue = '100%';
                switch (scaletype) {
                    case 0: objectFitValue = 'contain'; aspectratioValue = 'auto'; widthValue = '100%'; break;
                    case 1: objectFitValue = 'contain'; aspectratioValue = '16/9'; widthValue = '100%'; break;
                    case 2: aspectratioValue = '4/3'; objectFitValue = 'fill'; widthValue = 'auto'; break;
                    case 3: objectFitValue = 'fill'; aspectratioValue = 'none'; widthValue = '100%'; break;
                    case 4: objectFitValue = 'contain'; aspectratioValue = 'auto'; widthValue = '100%'; break;
                    case 5: objectFitValue = 'cover'; aspectratioValue = 'none'; widthValue = '100%'; break;
                    case 6:
                        objectFitValue = 'fill'; aspectratioValue = 'auto'; widthValue = '100%';
                        const screenWidth = window.innerWidth, screenHeight = window.innerHeight;
                        let videoHeight = screenWidth / 2.35;
                        if (videoHeight > screenHeight) videoHeight = screenHeight;
                        heightValue = (videoHeight / screenHeight) * 100 + '%';
                        break;
                }
                videoEl.style.cssText = 'width: ' + widthValue + ' !important; height: ' + heightValue + ' !important; object-fit: ' + objectFitValue + ' !important; aspect-ratio: ' + aspectratioValue + ' !important; position: absolute !important; top: 50% !important; left: 50% !important; transform: translate(-50%, -50%) !important; outline: none !important;';
            }
            function play() { if (videoEl && videoEl.paused) videoEl.play().catch(e => console.warn('play failed:', e)); }
            function pause() { if (videoEl && !videoEl.paused) videoEl.pause(); }
            function setposition(position) { if (videoEl) videoEl.currentTime = position; }
            function setspeed(speed) { if (videoEl) videoEl.playbackRate = speed; }
            async function ensureVideoVolume() {
                if (!videoEl) return;
                const maxRetries = 5;
                let retryCount = 0;
                while (retryCount < maxRetries) {
                    try {
                        videoEl.muted = false;
                        videoEl.volume = 1;
                        if (videoEl.paused) await videoEl.play().catch(() => {});
                        if (!videoEl.muted && videoEl.volume === 1) { console.log('volume set ok'); break; }
                    } catch (e) { console.warn('volume set retry:', e); }
                    retryCount++;
                    await delay(200);
                }
                if (retryCount >= maxRetries) console.error('volume set failed after retries');
            }
            (async function() {
                while (true) {
                    videoEl = document.querySelector('video');
                    if (videoEl && videoEl.readyState >= 1) break;
                    await delay(50);
                }
                document.body.style.cssText = 'width: 100vw; height: 100vh; margin: 0; min-width: 0; background: #000000; overflow: hidden;';
                document.documentElement.style.overflow = 'hidden';
                let fullscreenContainer = document.createElement('div');
                fullscreenContainer.style.cssText = 'position: fixed !important; top: 0 !important; left: 0 !important; width: 100% !important; height: 100% !important; z-index: 999999 !important; background: black !important; overflow: hidden !important;';
                document.body.appendChild(fullscreenContainer);
                if (videoEl.parentNode !== fullscreenContainer) fullscreenContainer.appendChild(videoEl);
                videoEl.controls = false;
                videoEl.removeAttribute('controls');
                // 强制 video 适配容器，防止原始尺寸溢出屏幕（含旋转后）
                function fitVideo(){
                    videoEl.style.cssText = 'width:100%!important;height:100%!important;object-fit:contain!important;max-width:100%!important;max-height:100%!important;outline:none!important;border:none!important;';
                }
                fitVideo();
                window.addEventListener('resize', fitVideo);
                window.addEventListener('orientationchange', () => setTimeout(fitVideo, 300));
                if (videoEl.readyState < 1) {
                    await new Promise(resolve => { videoEl.addEventListener('loadedmetadata', resolve, { once: true }); });
                }
                await ensureVideoVolume();
                setTimeout(async () => {
                    if (videoEl) {
                        await ensureVideoVolume();
                        if (videoEl.paused) videoEl.play().catch(e => console.warn('play failed:', e));
                    }
                }, 500);
                videoEl.addEventListener('volumechange', () => {
                    if (videoEl.muted || videoEl.volume === 0) ensureVideoVolume();
                }, { passive: true });
                videoEl.addEventListener('play', () => { ensureVideoVolume(); if (typeof WebVideoBridge !== 'undefined') WebVideoBridge.onPlay(true); }, { passive: true });
                videoEl.addEventListener('pause', () => { if (typeof WebVideoBridge !== 'undefined') WebVideoBridge.onPlay(false); }, { passive: true });
                videoEl.addEventListener('playing', () => { if (typeof WebVideoBridge !== 'undefined') WebVideoBridge.onPlay(true); }, { passive: true });
                if (typeof ku9 !== 'undefined' && ku9.getscale) setscale(ku9.getscale());
                if (typeof ku9 !== 'undefined' && ku9.setduration) {
                    if (videoEl.duration > 0) ku9.setduration(videoEl.duration);
                    else videoEl.addEventListener('loadedmetadata', () => { if (videoEl.duration > 0) ku9.setduration(videoEl.duration); });
                }
                if (typeof ku9 !== 'undefined' && ku9.setvideo) {
                    if (videoEl.videoWidth && videoEl.videoHeight) ku9.setvideo(videoEl.videoWidth, videoEl.videoHeight);
                    else videoEl.addEventListener('loadedmetadata', () => { ku9.setvideo(videoEl.videoWidth, videoEl.videoHeight); });
                }
                if (typeof ku9 !== 'undefined' && ku9.setposition) {
                    videoEl.addEventListener('timeupdate', () => { ku9.setposition(videoEl.currentTime); });
                }
                videoEl.addEventListener('resize', () => {
                    if (typeof ku9 !== 'undefined' && ku9.setvideo) ku9.setvideo(videoEl.videoWidth, videoEl.videoHeight);
                    if (typeof ku9 !== 'undefined' && ku9.getscale) setscale(ku9.getscale());
                });
            })();
            """;

    private WebView activeWebView;

    private ViewGroup container;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private Activity activity;
    private View.OnTouchListener touchListener;
    private PlaybackListener playbackListener;
    private boolean webPlaying;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 视频播放状态回调，用于把 WebView 内 video 元素的播放/暂停状态同步给原生控制栏。
     */
    public interface PlaybackListener {
        void onWebPlayStateChanged(boolean playing);
    }
 
    public void attach(Activity activity, ViewGroup container, String url) {
        attach(activity, container, url, null, null);
    }

    public void attach(Activity activity, ViewGroup container, String url, View.OnTouchListener touchListener) {
        attach(activity, container, url, touchListener, null);
    }
 
    @SuppressLint("SetJavaScriptEnabled")
    public void attach(Activity activity, ViewGroup container, String url, View.OnTouchListener touchListener, PlaybackListener playbackListener) {
        if (activeWebView == null) {
            // 首次加载，新建WebView
            detach();
            this.activity = activity;
            this.container = container;
            this.touchListener = touchListener;
            this.playbackListener = playbackListener;

            activeWebView = createWebViewInstance(activity);
            if (touchListener != null) activeWebView.setOnTouchListener(touchListener);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            container.addView(activeWebView, lp);
        } else {
            // 切台：复用当前webview直接加载新链接
            SpiderDebug.log(TAG, "switch url: %s", url);
            activeWebView.stopLoading();
            this.touchListener = touchListener;
            this.playbackListener = playbackListener;
            // 切台时同步刷新触摸监听器，避免旧监听器残留
            if (touchListener != null) activeWebView.setOnTouchListener(touchListener);
            if (customView != null && touchListener != null) customView.setOnTouchListener(touchListener);
        }
        webPlaying = false;
        activeWebView.onResume();
        activeWebView.loadUrl(url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebViewInstance(Activity ctx) {
        WebView webView = new WebView(ctx);
        webView.setBackgroundColor(0xFF000000);
        webView.setFocusable(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(DESKTOP_UA);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadsImagesAutomatically(false);
        s.setBlockNetworkImage(true);

        // 注册 JS 回调接口，把 video 元素的播放/暂停状态同步给原生
        webView.addJavascriptInterface(new WebVideoBridge(), "WebVideoBridge");
 
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                view.evaluateJavascript(FAST_LOADING_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if ("about:blank".equals(url)) return;
                view.evaluateJavascript(UNMUTE_VIDEO_JS, null);
                SpiderDebug.log(TAG, "onPageFinished %s", url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                SpiderDebug.log(TAG, "resource error main=%s code=%s desc=%s url=%s",
                        request.isForMainFrame(), error.getErrorCode(), error.getDescription(), request.getUrl());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                SpiderDebug.log(TAG, "console: %s", cm == null ? "" : cm.message());
                return super.onConsoleMessage(cm);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (activity == null || customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
                decor.addView(customView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                // 把触摸监听器挂到 customView 上，拦截所有触摸事件，避免点击画面导致 video 暂停
                if (touchListener != null) customView.setOnTouchListener(touchListener);
                if (webView != null) webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
                decor.removeView(customView);
                if (customView != null) customView.setOnTouchListener(null);
                customView = null;
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customViewCallback = null;
                if (webView != null) webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });
        return webView;
    }

    private void simulateClick(WebView webView) {
        View targetView;
        if (customView != null) {
            targetView = customView;
        } else {
            targetView = webView;
        }

        if (targetView == null || targetView.getWidth() <= 0 || targetView.getHeight() <= 0) {
            SpiderDebug.log(TAG, "simulate click skip, view size invalid");
            return;
        }

        int x = targetView.getWidth() / 2;
        int y = (int) (targetView.getHeight() * 0.75f);

        long downTime = System.currentTimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, x, y, 0);
        targetView.dispatchTouchEvent(down);
        targetView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        SpiderDebug.log(TAG, "simulate click target=%s x=%d y=%d", customView != null ? "customView" : "webView", x, y);
    }

    private void destroyWebView(WebView wv) {
        if (wv == null) return;
        try {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            wv.onPause();
            wv.removeAllViews();
            if (container != null) container.removeView(wv);
            wv.destroy();
        } catch (Throwable ignored) {}
    }

    public void onResume() {
        if (activeWebView != null) activeWebView.onResume();
    }

    public void onPause() {
        if (activeWebView != null) activeWebView.onPause();
    }

    public boolean canGoBack() {
        return activeWebView != null && activeWebView.canGoBack();
    }

    public void goBack() {
        if (activeWebView != null) activeWebView.goBack();
    }

    /** 由原生控制栏调用，让 WebView 内的 video 播放 */
    public void play() {
        if (activeWebView == null) return;
        activeWebView.evaluateJavascript("javascript:if(typeof play==='function')play();", null);
    }
 
    /** 由原生控制栏调用，让 WebView 内的 video 暂停 */
    public void pause() {
        if (activeWebView == null) return;
        activeWebView.evaluateJavascript("javascript:if(typeof pause==='function')pause();", null);
    }
 
    public boolean isWebPlaying() {
        return webPlaying;
    }
 
    public void detach() {
        mainHandler.removeCallbacksAndMessages(null);
        if (customView != null) {
            if (activity != null) {
                FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
                if (decor != null) decor.removeView(customView);
            }
            customView = null;
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        }
        destroyWebView(activeWebView);

        activeWebView = null;
        container = null;
        activity = null;
        touchListener = null;
        playbackListener = null;
    }
 
    /**
     * JS 回调桥梁：把 video 元素的 play/pause 事件转发给原生 PlaybackListener。
     * 运行在 WebView 内部线程，需切到主线程回调。
     */
    private class WebVideoBridge {
        @android.webkit.JavascriptInterface
        public void onPlay(final boolean playing) {
            if (activeWebView == null) return;
            final PlaybackListener listener = playbackListener;
            if (listener == null) return;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onWebPlayStateChanged(playing);
                }
            });
        }
    }
}
