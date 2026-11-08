package com.fongmi.chaquo;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Spider extends com.github.catvod.crawler.Spider {

    private static final String TAG = "python-spider";

    private final PyObject app;
    private final PyObject obj;
    private final String api;
    private final Gson gson;

    public Spider(PyObject app, PyObject obj, String api) {
        this.gson = new Gson();
        this.app = app;
        this.obj = obj;
        this.api = api;
    }

    @Override
    public void init(Context context, String extend) {
        PyObject dependence = app.callAttr("getDependence", obj);
        if (dependence != null) {
            for (PyObject item : dependence.asList()) {
                // A dependency that cannot be fetched must not abort init(): that used
                // to propagate to PyLoader, which degrades the whole site to an
                // all-empty SpiderNull, so a single unreachable .py made home,
                // category and search silently render nothing.  Log and continue; a
                // spider that really needs the module fails later with a clear
                // ImportError instead.  Throwable rather than Exception so a JVM-level
                // Error (OutOfMemoryError, StackOverflowError) raised inside the download
                // call path cannot abort init() either; a Python exception arrives as
                // Chaquopy's PyException, which is itself a RuntimeException.
                try {
                    download(item + ".py");
                } catch (Throwable e) {
                    SpiderDebug.log(TAG, "depend download failed name=%s error=%s", item, e);
                    // SpiderDebug.log() is gated on DebugLogStore.acceptsTag(), which is
                    // false whenever 调试日志 is off (the default), so the structured line
                    // alone would leave a skipped dependency invisible in Logcat.  Keep the
                    // unconditional channel too, as PyLoader does for a failed init.
                    e.printStackTrace();
                } finally {
                    close(item);
                }
            }
            close(dependence);
        }
        obj.put("siteKey", siteKey);
        app.callAttr("init", obj, extend);
    }

    @Override
    public String homeContent(boolean filter) {
        return toStr(app.callAttr("homeContent", obj, filter));
    }

    @Override
    public String homeVideoContent() {
        return toStr(app.callAttr("homeVideoContent", obj));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return toStr(app.callAttr("categoryContent", obj, tid, pg, filter, gson.toJson(extend)));
    }

    @Override
    public String detailContent(List<String> ids) {
        return toStr(app.callAttr("detailContent", obj, gson.toJson(ids)));
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return toStr(app.callAttr("searchContent", obj, key, quick));
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        return toStr(app.callAttr("searchContent", obj, key, quick, pg));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return toStr(app.callAttr("playerContent", obj, flag, id, gson.toJson(vipFlags)));
    }

    @Override
    public String liveContent(String url) {
        return toStr(app.callAttr("liveContent", obj, url));
    }

    @Override
    public boolean manualVideoCheck() {
        return toBool(app.callAttr("manualVideoCheck", obj));
    }

    @Override
    public boolean isVideoFormat(String url) {
        return toBool(app.callAttr("isVideoFormat", obj, url));
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        PyObject container = app.callAttr("localProxy", obj, gson.toJson(params));
        try {
            List<PyObject> list = container.asList();
            boolean base64 = list.size() > 4 && list.get(4).toInt() == 1;
            boolean header = list.size() > 3 && list.get(3) != null;
            Object[] result = new Object[4];
            result[0] = list.get(0).toInt();
            result[1] = list.get(1).toString();
            result[2] = getStream(list.get(2), base64);
            result[3] = header ? getHeader(list.get(3)) : null;
            for (PyObject item : list) close(item);
            return result;
        } finally {
            close(container);
        }
    }

    @Override
    public String action(String action) {
        return toStr(app.callAttr("action", obj, action));
    }

    public String subtitleInit(String config) {
        return toStr(app.callAttr("subtitle_init", obj, config == null ? "" : config));
    }

    public String subtitleSearch(String request) {
        return toStr(app.callAttr("subtitle_search", obj, request));
    }

    public String subtitleResolve(String request) {
        return toStr(app.callAttr("subtitle_resolve", obj, request));
    }

    @Override
    public void destroy() {
        try {
            app.callAttr("destroy", obj);
        } catch (Exception ignored) {
        }
    }

    private Map<String, String> getHeader(PyObject obj) {
        try {
            Map<String, String> header = new HashMap<>();
            for (Map.Entry<PyObject, PyObject> entry : obj.asMap().entrySet()) header.put(entry.getKey().toString(), entry.getValue().toString());
            return header;
        } catch (Exception e) {
            return null;
        }
    }

    private ByteArrayInputStream getStream(PyObject o, boolean base64) {
        if (o == null) return null;
        if (o.type().toString().contains("bytes")) return new ByteArrayInputStream(o.toJava(byte[].class));
        String content = o.toString();
        if (base64 && content.contains("base64,")) content = content.split("base64,")[1];
        return new ByteArrayInputStream(base64 ? Util.decode(content) : content.getBytes());
    }

    private void download(String name) {
        String path = Path.py(name).getAbsolutePath();
        String url = UriUtil.resolve(api, name);
        app.callAttr("download", path, url);
    }

    private static String toStr(PyObject o) {
        if (o == null) return null;
        try {
            return o.toString();
        } finally {
            close(o);
        }
    }

    private static boolean toBool(PyObject o) {
        try {
            return o != null && o.toBoolean();
        } finally {
            close(o);
        }
    }

    private static void close(PyObject o) {
        if (o == null) return;
        try {
            o.close();
        } catch (Throwable ignored) {
        }
    }
}
