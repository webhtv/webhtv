package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import okhttp3.Response;
import okhttp3.ResponseBody;

public class Strm implements Source.Extractor {

    private static final long MAX_STRM_BYTES = 64 * 1024;

    @Override
    public boolean match(Uri uri) {
        return UrlUtil.path(uri).endsWith(".strm");
    }

    @Override
    public String fetch(String url) throws Exception {
        if (url.startsWith("http")) return http(url);
        if (url.startsWith("file")) url = url.substring(7);
        return Path.read(new File(url)).split("\\R", 2)[0];
    }

    private String http(String url) {
        try (Response res = OkHttp.newCall(OkHttp.noRedirect(), url).execute()) {
            if (!res.isSuccessful() || res.code() == 206) return url;
            if (res.body().contentLength() >= MAX_STRM_BYTES) return url;
            try (ResponseBody body = res.peekBody(MAX_STRM_BYTES)) {
                if (body.contentLength() >= MAX_STRM_BYTES) return url;
                return parseTarget(body.string(), url);
            }
        } catch (IOException | IllegalArgumentException e) {
            return url;
        }
    }

    private String parseTarget(String content, String original) {
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        String line = content.split("\\R", 2)[0];
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isISOControl(c) && c != '\t') return original;
        }
        line = line.trim();
        try {
            URI target = new URI(line);
            if (!target.isAbsolute() || target.getRawSchemeSpecificPart().isEmpty()) return original;
            String scheme = target.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && (target.getRawAuthority() == null || target.getRawAuthority().isEmpty())) {
                return original;
            }
            return line;
        } catch (URISyntaxException e) {
            return original;
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
