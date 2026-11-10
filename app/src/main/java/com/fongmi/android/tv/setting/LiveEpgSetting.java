package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LiveEpgSetting {

    private static final String KEY_SELECTED = "live_epg_selected";
    private static final String KEY_HISTORY = "live_epg_history";
    private static final int MAX_HISTORY = 20;
    private static final Type TYPE = new TypeToken<List<String>>() {}.getType();

    public static Set<String> getSelected() {
        try {
            List<String> items = App.gson().fromJson(Prefers.getString(KEY_SELECTED, "[]"), TYPE);
            return items == null ? new LinkedHashSet<>() : new LinkedHashSet<>(items);
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    public static void setSelected(Set<String> urls) {
        Prefers.put(KEY_SELECTED, App.gson().toJson(new ArrayList<>(urls)));
    }
 
    public static boolean isSelected(String url) {
        return getSelected().contains(normalize(url));
    }
 
    public static void toggleSelected(String url) {
        url = normalize(url);
        Set<String> selected = getSelected();
        if (selected.contains(url)) selected.remove(url);
        else selected.add(url);
        setSelected(selected);
    }
 
    public static void addSelected(String url) {
        url = normalize(url);
        if (url.isEmpty()) return;
        Set<String> selected = getSelected();
        selected.add(url);
        setSelected(selected);
    }
 
    public static void removeSelected(String url) {
        url = normalize(url);
        Set<String> selected = getSelected();
        if (selected.remove(url)) setSelected(selected);
    }
 
    public static void clearSelected() {
        Prefers.put(KEY_SELECTED, App.gson().toJson(Collections.emptyList()));
    }
 
    /** 兼容旧逻辑：取第一个选中的 URL，无则返回空 */
    public static String getUrl() {
        for (String url : getSelected()) return url;
        return "";
    }
 
    public static List<String> getHistory() {
        try {
            List<String> items = App.gson().fromJson(Prefers.getString(KEY_HISTORY, "[]"), TYPE);
            return items == null ? new ArrayList<>() : new ArrayList<>(items);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void addHistory(String url) {
        url = normalize(url);
        if (url.isEmpty()) return;
        List<String> items = getHistory();
        items.remove(url);
        items.add(0, url);
        while (items.size() > MAX_HISTORY) items.remove(items.size() - 1);
        Prefers.put(KEY_HISTORY, App.gson().toJson(items));
    }

    public static void removeHistory(String url) {
        url = normalize(url);
        if (url.isEmpty()) return;
        List<String> items = getHistory();
        if (items.remove(url)) Prefers.put(KEY_HISTORY, App.gson().toJson(items));
        removeSelected(url);
    }

    public static void replaceHistory(String oldUrl, String newUrl) {
        oldUrl = normalize(oldUrl);
        newUrl = normalize(newUrl);
        Set<String> selected = getSelected();
        if (!oldUrl.isEmpty() && selected.contains(oldUrl)) {
            selected.remove(oldUrl);
            selected.add(newUrl);
            setSelected(selected);
        }
        if (!oldUrl.isEmpty() && !oldUrl.equals(newUrl)) {
            List<String> items = getHistory();
            if (items.remove(oldUrl)) Prefers.put(KEY_HISTORY, App.gson().toJson(items));
        }
        addHistory(newUrl);
    }

    public static void clearHistory() {
        Prefers.put(KEY_HISTORY, App.gson().toJson(Collections.emptyList()));
    }

    public static void apply(Live live) {
        if (live == null || live.getGroups().isEmpty()) return;
        for (Group group : live.getGroups()) for (Channel channel : group.getChannel()) apply(live, channel);
    }

    public static void apply(Live live, Channel channel) {
        if (live == null || channel == null) return;
        channel.setDataList(Collections.emptyList());
        String template = getEffectiveUrl(live);
        if (isGlobalXmlUrl(template)) {
            channel.setEpg("");
            return;
        }
        if (!template.contains("{")) {
            channel.setEpg(template);
            return;
        }
        channel.setEpg(template.replace("{id}", channel.getTvgId()).replace("{name}", channel.getTvgName()).replace("{epg}", channel.getEpg()));
    }

    public static String getEffectiveUrl(Live live) {
        for (String url : getSelected()) if (!url.contains("{")) return url;
        String custom = getUrl();
        if (!custom.isEmpty()) return custom;
        return live != null ? live.getEpgApi() : "";
    }

    public static List<String> getXmlUrls(Live live) {
        Set<String> items = new LinkedHashSet<>();
        for (String url : getSelected()) if (isGlobalXmlUrl(url)) items.add(url);
        if (live != null) items.addAll(live.getEpgXml());
        return new ArrayList<>(items);
    }

    public static boolean isGlobalXmlUrl(String url) {
        url = normalize(url);
        return !url.isEmpty() && !url.contains("{");
    }

    public static boolean hasSelected() {
        return !getSelected().isEmpty();
    }
 
    private static String normalize(String url) {
        return url == null ? "" : url.trim();
    }
}
