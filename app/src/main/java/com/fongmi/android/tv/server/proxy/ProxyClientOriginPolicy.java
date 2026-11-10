package com.fongmi.android.tv.server.proxy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public final class ProxyClientOriginPolicy {

    private ProxyClientOriginPolicy() {
    }

    public static boolean allows(Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey();
            String value = header.getValue();
            if (value != null) value = value.trim();
            if ("Origin".equalsIgnoreCase(name) && !allowsOrigin(value)) {
                return false;
            }
            if ("Sec-Fetch-Site".equalsIgnoreCase(name)
                    && !"none".equals(value) && !"same-origin".equals(value)) {
                return false;
            }
            if ("Sec-Fetch-Mode".equalsIgnoreCase(name)
                    && ("cors".equals(value) || "no-cors".equals(value))) {
                return false;
            }
        }
        return true;
    }

    private static boolean allowsOrigin(String value) {
        if (value == null) return false;
        try {
            URI origin = new URI(value);
            String scheme = origin.getScheme();
            String host = origin.getHost();
            String path = origin.getRawPath();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && ("127.0.0.1".equalsIgnoreCase(host) || "localhost".equalsIgnoreCase(host))
                    && origin.getRawUserInfo() == null
                    && origin.getRawQuery() == null
                    && origin.getRawFragment() == null
                    && ("".equals(path) || "/".equals(path))
                    && !origin.getRawAuthority().endsWith(":")
                    && origin.getPort() <= 65535;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
