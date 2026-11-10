package com.fongmi.android.tv.player.extractor;
 
import android.net.Uri;

import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.UrlUtil;

/**
 * Resolves {@code webview://<url>} links for channels that must be played inside
 * a WebView (e.g. yangshipin.cn, whose HLS stream is CMG-encrypted and cannot be
 * decrypted by native players without a closed-source native library).
 *
  * <p>This extractor only strips the {@code webview://} prefix and returns the real
 * page URL. The actual playback is handled by the LiveActivity, which detects the
 * {@code webview://} scheme on the channel and shows a fullscreen WebView overlay
 * instead of starting the native player.</p>
 */
public class WebViewResolver implements Source.Extractor {
 
    private static final String SCHEME = "webview";
    private static final String PREFIX = SCHEME + "://";
 
    @Override
    public boolean match(Uri uri) {
        return SCHEME.equals(UrlUtil.scheme(uri));
    }
 
    @Override
    public String fetch(String url) {
        if (url == null || !url.startsWith(PREFIX)) return url;
        String target = url.substring(PREFIX.length());
        return target.isEmpty() ? url : target;
    }
 
    @Override
    public void stop() {
    }
 
    @Override
    public void exit() {
    }
}
