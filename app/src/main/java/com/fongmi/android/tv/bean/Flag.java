package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class Flag implements Parcelable, Diffable<Flag> {

    @Attribute(name = "flag", required = false)
    @SerializedName("flag")
    private String flag;
    private String show;

    @Text
    private String urls;

    @SerializedName("episodes")
    private List<Episode> episodes;

    private boolean selected;
    private int position;

    public Flag() {
        this.position = -1;
        this.episodes = new ArrayList<>();
    }

    public Flag(String flag) {
        this.flag = flag;
        this.position = -1;
        this.episodes = new ArrayList<>();
    }

    protected Flag(Parcel in) {
        this.flag = in.readString();
        this.show = in.readString();
        this.urls = in.readString();
        this.episodes = in.createTypedArrayList(Episode.CREATOR);
        this.selected = in.readByte() != 0;
        this.position = in.readInt();
    }

    public static Flag create(String flag) {
        return new Flag(flag).trans();
    }

    public static Flag create(String flag, String url) {
        Flag item = create(flag);
        item.setEpisodes(url);
        return item;
    }

    public static String stableKey(Flag flag, int index) {
        String value = flag == null || TextUtils.isEmpty(flag.getFlag()) ? "flag" : flag.getFlag().trim();
        return value + "#" + Math.max(0, index);
    }

    public String getShow() {
        return TextUtils.isEmpty(show) ? getFlag() : show;
    }

    public String getFlag() {
        return TextUtils.isEmpty(flag) ? "" : flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getUrls() {
        return TextUtils.isEmpty(urls) ? "" : urls;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public void setEpisodes(String url) {
        String[] urls = splitEpisodes(url);
        for (int i = 0; i < urls.length; i++) {
            String[] split = urls[i].split("\\$", 2);
            String number = String.format(Locale.getDefault(), "%02d", i + 1);
            Episode episode = split.length > 1 ? Episode.create(split[0].isEmpty() ? number : split[0].trim(), split[1]) : Episode.create(number, urls[i]);
            if (!getEpisodes().contains(episode)) getEpisodes().add(episode);
        }
    }

    private String[] splitEpisodes(String url) {
        if (!url.contains("#")) return new String[]{url};
        List<String> items = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (isOpenBracket(c)) depth++;
            else if (isCloseBracket(c) && depth > 0) depth--;
            else if (c == '#' && depth == 0) {
                items.add(url.substring(start, i));
                start = i + 1;
            }
        }
        if (start < url.length()) items.add(url.substring(start));
        return items.toArray(new String[0]);
    }

    private boolean isOpenBracket(char c) {
        return c == '[' || c == '(' || c == '（' || c == '【' || c == '《';
    }

    private boolean isCloseBracket(char c) {
        return c == ']' || c == ')' || c == '）' || c == '】' || c == '》';
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(Flag item) {
        this.selected = item.equals(this);
        if (selected) item.episodes = episodes;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    private void setSelected(Episode episode) {
        setPosition(indexOf(episode));
        for (int i = 0; i < getEpisodes().size(); i++) getEpisodes().get(i).setSelected(i == getPosition());
    }

    private int indexOf(Episode episode) {
        if (episode == null) return -1;
        for (int i = 0; i < getEpisodes().size(); i++) if (getEpisodes().get(i) == episode) return i;
        if (!TextUtils.isEmpty(episode.getUrl())) {
            for (int i = 0; i < getEpisodes().size(); i++) if (episode.getUrl().equals(getEpisodes().get(i).getUrl())) return i;
        }
        int index = getEpisodes().indexOf(episode);
        if (index != -1) return index;
        if (TextUtils.isEmpty(episode.getUrl())) {
            for (int i = 0; i < getEpisodes().size(); i++) if (getEpisodes().get(i).matchesName(episode)) return i;
        }
        return -1;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void toggle(boolean selected, Episode episode) {
        if (selected) setSelected(episode);
        else getEpisodes().forEach(Episode::deselect);
    }

    public Episode find(String remarks, boolean strict) {
        if (getEpisodes().isEmpty()) return null;
        if (getEpisodes().size() == 1) return getEpisodes().get(0);
        int number = Util.getEpisodeNumber(remarks);
        return getEpisodes().stream()
                .map(episode -> new Episode.Rule(episode, episode.getScore(remarks, number)))
                .filter(Episode.Rule::find).max(Comparator.comparingInt(Episode.Rule::score)).map(Episode.Rule::episode)
                .orElseGet(() -> {
                    if (isPositionValid()) return getEpisodes().get(getPosition());
                    if (strict || getEpisodes().isEmpty()) return null;
                    return getEpisodes().get(0);
                });
    }

    private boolean isPositionValid() {
        return getPosition() >= 0 && getPosition() < getEpisodes().size();
    }

    /**
     * 对侧（历史）URL 是否仍能定位到本线路的某个条目。
     * <p>
     * 这是“同线路内同集多版本消歧”的启用判据：命中说明历史与当前列表同属一条线（多版本并存），
     * 播放恢复判定应启用版本消歧；未命中说明是换线路/换源或源站刷新（旧 URL 已不在列表），
     * 必须保留集号容错，否则跨线路续播会丢失进度。带 TMDB 季集位置的条目同样要求位置不冲突，
     * 与 {@link #find(Episode, boolean)} 的 URL 优先定位口径一致。
     */
    public boolean containsEpisodeUrl(Episode target) {
        if (target == null || TextUtils.isEmpty(target.getUrl()) || getEpisodes().isEmpty()) return false;
        for (Episode episode : getEpisodes()) {
            if (!TextUtils.equals(target.getUrl(), episode.getUrl())) continue;
            if (hasTmdbEpisodeNumber(episode) && hasTmdbEpisodeNumber(target) && !episode.matchesNumber(target)) continue;
            return true;
        }
        return false;
    }

    public Episode find(Episode target, boolean strict) {
        if (getEpisodes().isEmpty()) return null;
        // 同一 TMDB 集可能在同一线路内存在多个版本（同名不同 URL）。定位请求带 URL 时必须先按 URL
        // 锁定版本，否则下面“TMDB 集号相同即返回第一个”会把第二版本解析成第一版本。
        // 但同 URL 也可能跨季/跨集复用（源站播放地址不唯一），此时按 URL 命中会定位到另一季的条目，
        // 故 TMDB 位置冲突的条目一律不按 URL 命中，交回下面的季集号定位。
        if (target != null && !TextUtils.isEmpty(target.getUrl())) {
            for (Episode episode : getEpisodes()) {
                if (!TextUtils.equals(target.getUrl(), episode.getUrl())) continue;
                if (hasTmdbEpisodeNumber(episode) && hasTmdbEpisodeNumber(target) && !episode.matchesNumber(target)) continue;
                return episode;
            }
        }
        if (getEpisodes().size() == 1) {
            Episode episode = getEpisodes().get(0);
            if (hasTmdbEpisodeNumber(target) && hasTmdbEpisodeNumber(episode) && !episode.matchesNumber(target)) return null;
            return episode;
        }
        if (hasTmdbEpisodeNumber(target)) {
            for (Episode episode : getEpisodes()) {
                if (hasTmdbEpisodeNumber(episode) && episode.matchesNumber(target)) return episode;
            }
            int index = indexOf(target);
            if (index != -1) {
                Episode episode = getEpisodes().get(index);
                if (!hasTmdbEpisodeNumber(episode)) return episode;
            }
            for (Episode episode : getEpisodes()) {
                if (!hasTmdbEpisodeNumber(episode) && episode.matchesNumber(target)) return episode;
            }
            for (Episode episode : getEpisodes()) {
                if (!hasTmdbEpisodeNumber(episode) && episode.matchesName(target)) return episode;
            }
            return null;
        }
        int index = indexOf(target);
        if (index != -1) return getEpisodes().get(index);
        return find(target == null ? "" : target.getName(), strict);
    }

    private static boolean hasTmdbEpisodeNumber(Episode episode) {
        return episode != null && episode.getTmdbEpisode() != null && episode.getTmdbEpisode().getNumber() > 0;
    }

    public void mergeEpisodes(List<Episode> items, boolean rev) {
        if (items == null || items.isEmpty()) return;
        if (items == getEpisodes()) return;
        IdentityHashMap<Episode, Episode> byIdentity = new IdentityHashMap<>();
        Map<String, Episode> byUrl = new HashMap<>();
        Map<Episode, Episode> byValue = new HashMap<>();
        Map<String, Episode> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Episode episode : getEpisodes()) {
            byIdentity.put(episode, episode);
            byValue.putIfAbsent(episode, episode);
            if (episode == null) continue;
            if (!TextUtils.isEmpty(episode.getUrl())) byUrl.putIfAbsent(episode.getUrl(), episode);
            byName.putIfAbsent(episode.getName(), episode);
        }
        List<Episode> toAdd = new ArrayList<>();
        for (Episode item : items) {
            Episode target = byIdentity.get(item);
            if (target == null && item != null && !TextUtils.isEmpty(item.getUrl())) target = byUrl.get(item.getUrl());
            if (target == null) target = byValue.get(item);
            if (target == null && item != null && TextUtils.isEmpty(item.getUrl())) target = byName.get(item.getName());
            if (target != null) {
                mergeEpisode(target, item);
                continue;
            }
            toAdd.add(item);
        }
        if (!toAdd.isEmpty()) {
            if (rev) {
                Collections.reverse(toAdd);
                getEpisodes().addAll(0, toAdd);
            } else {
                getEpisodes().addAll(toAdd);
            }
        }
    }

    private void mergeEpisode(Episode target, Episode source) {
        if (target == null || source == null) return;
        if (source.getTmdbEpisode() != null) {
            if (source.isTmdbEpisodeMapped()) target.setMappedTmdbEpisode(source.getTmdbEpisode());
            else target.setTmdbEpisode(source.getTmdbEpisode());
        }
        if (!TextUtils.equals(source.getDisplayName(), source.getRawDisplayName())) target.setDisplayName(source.getDisplayName());
    }

    public Flag trans() {
        if (Trans.pass()) return this;
        this.show = Trans.s2t(flag);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Flag it)) return false;
        return Objects.equals(getFlag(), it.getFlag());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFlag());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.flag);
        dest.writeString(this.show);
        dest.writeString(this.urls);
        dest.writeTypedList(this.episodes);
        dest.writeByte(this.selected ? (byte) 1 : (byte) 0);
        dest.writeInt(this.position);
    }

    @Override
    public boolean isSameItem(Flag other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Flag other) {
        return equals(other);
    }

    public static final Creator<Flag> CREATOR = new Creator<>() {
        @Override
        public Flag createFromParcel(Parcel source) {
            return new Flag(source);
        }

        @Override
        public Flag[] newArray(int size) {
            return new Flag[size];
        }
    };
}
