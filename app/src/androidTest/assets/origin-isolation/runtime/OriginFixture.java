package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OriginFixture extends Spider {
    private static JSONObject options;
    private static int initialized;
    @Override public void init(Context context, String ext) throws Exception {
        options = new JSONObject(ext);
        initialized++;
        context.getSharedPreferences("origin-fixture-private", 0).edit().putString("account", options.getString("account")).commit();
    }
    @Override public String homeContent(boolean filter) throws Exception {
        return "{\"list\":[{\"vod_id\":\"origin-vod\",\"vod_name\":\"" + options.getString("account") + ":jar:" + initialized + "\"}]}";
    }
    private String fetch(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(options.getString("base") + path + "&account=" + options.getString("account") + "&runtime=jar").openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(5000);
        JSONObject h = options.getJSONObject("headers");
        java.util.Iterator<String> keys = h.keys();
        while (keys.hasNext()) { String key = keys.next(); c.setRequestProperty(key, h.getString(key)); }
        try { if(c.getResponseCode()!=200) throw new IllegalStateException("fixture HTTP " + c.getResponseCode());
            try(java.io.InputStream in=c.getInputStream(); java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
                byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)out.write(b,0,n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally { c.disconnect(); }
    }
    @Override public String detailContent(List<String> ids) throws Exception { return fetch("/detail?id=" + ids.get(0)); }
    @Override public String searchContent(String key, boolean quick) throws Exception { return fetch("/search?wd=" + URLEncoder.encode(key,"UTF-8")); }
    @Override public String playerContent(String flag, String id, List<String> vipFlags) throws Exception { return fetch("/player?episode=" + URLEncoder.encode(id,"UTF-8")); }
}
