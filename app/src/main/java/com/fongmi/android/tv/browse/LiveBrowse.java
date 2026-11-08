package com.fongmi.android.tv.browse;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;

import com.fongmi.android.tv.api.LiveApi;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.db.AppDatabase;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

class LiveBrowse {

    static final String LIVE_GROUP = "LG:";
    static final String LIVE_CH = "LC:";

    private static final Map<String, String> liveNavMap = new ConcurrentHashMap<>();
    private static final Map<String, Integer> liveCountMap = new ConcurrentHashMap<>();
    private static final Map<String, LiveEntry> livePosMap = new ConcurrentHashMap<>();

    static void clear() {
        livePosMap.clear();
        liveNavMap.clear();
        liveCountMap.clear();
    }

    @NonNull
    static ImmutableList<MediaItem> getGroups() {
        // id 必须用与界面语言无关的身份名（Live.keep / 上次频道写的是同一套名字，且要能长期持久化），
        // 标题才用界面语言下的展示名；两者混用会让繁体界面下的分组名不再翻译（Group.trans() 已只写 displayName）。
        return liveHome().getGroups().stream().map(group -> BrowseTree.folder(LIVE_GROUP + group.getName(), group.getDisplayName())).collect(ImmutableList.toImmutableList());
    }

    @NonNull
    static ImmutableList<MediaItem> getChannels(@NonNull String parentId) {
        // 浏览树 id 来自可持久化的身份名（Live.keep 会写同一套名字），但也兼容旧版本写入的译文名。
        String groupName = parentId.substring(LIVE_GROUP.length());
        Group group = liveHome().getGroups().stream().filter(item -> item.matchesName(groupName)).findFirst().orElse(null);
        if (group == null) {
            liveCountMap.put(groupName, 0);
            return ImmutableList.of();
        }
        liveNavMap.keySet().removeIf(key -> key.startsWith(groupName + '|'));
        livePosMap.values().removeIf(entry -> entry.groupName.equals(groupName));
        List<Channel> channels = group.getChannel();
        liveCountMap.put(groupName, channels.size());
        return IntStream.range(0, channels.size()).mapToObj(i -> indexChannel(groupName, channels.get(i), i)).collect(ImmutableList.toImmutableList());
    }

    private static MediaItem indexChannel(@NonNull String groupName, @NonNull Channel channel, int index) {
        String key = liveNavKey(groupName, index);
        String id = LIVE_CH + key;
        liveNavMap.put(key, id);
        livePosMap.put(id, new LiveEntry(channel, groupName, index));
        return BrowseTree.playable(id, channel.getNumber() + " " + channel.getShow(), null, channel.getLogo());
    }

    @Nullable
    static MediaItem resolve(@NonNull String mediaId) throws Exception {
        LiveEntry entry = livePosMap.get(mediaId);
        return resolveChannel(entry != null ? entry.channel() : null, mediaId);
    }

    @Nullable
    static MediaItem navigate(@NonNull String mediaId, int delta) throws Exception {
        if (mediaId.startsWith(LIVE_CH)) return navigateChannel(mediaId, delta);
        String liveId = ensureLoaded();
        if (liveId != null) return navigateChannel(liveId, delta);
        return null;
    }

    @Nullable
    private static MediaItem navigateChannel(@NonNull String mediaId, int delta) throws Exception {
        LiveEntry current = livePosMap.get(mediaId);
        if (current == null) return null;
        Integer count = liveCountMap.get(current.groupName);
        if (count == null || count == 0) return null;
        int target = BrowseTree.wrapIndex(current.index, delta, count);
        String nextId = liveNavMap.get(liveNavKey(current.groupName, target));
        if (nextId == null) return null;
        LiveEntry next = livePosMap.get(nextId);
        return resolveChannel(next != null ? next.channel() : null, nextId);
    }

    @Nullable
    private static String ensureLoaded() {
        String keep = liveHome().getKeep();
        if (TextUtils.isEmpty(keep)) return null;
        String[] splits = keep.split(AppDatabase.SYMBOL);
        if (splits.length < 2) return null;
        String groupName = splits[0];
        String channelName = splits[1];
        getChannels(LIVE_GROUP + groupName);
        Integer count = liveCountMap.get(groupName);
        if (count == null || count == 0) return null;
        return livePosMap.entrySet().stream().filter(e -> e.getValue().groupName().equals(groupName) && e.getValue().channel().getName().equals(channelName)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    @Nullable
    private static MediaItem resolveChannel(@Nullable Channel channel, @NonNull String mediaId) throws Exception {
        if (channel == null || TextUtils.isEmpty(channel.getCurrent())) return null;
        Result result = LiveApi.getUrl(channel);
        if (TextUtils.isEmpty(result.getRealUrl())) return null;
        BrowseTree.putBrowseResult(mediaId, result);
        return BrowseTree.stream(mediaId, result.getRealUrl(), channel.getShow(), null, channel.getLogo());
    }

    private static Live liveHome() {
        LiveConfig.get().ensureLoaded();
        return LiveConfig.get().getHome();
    }

    @NonNull
    private static String liveNavKey(@NonNull String groupName, int index) {
        return groupName + '|' + index;
    }

    private record LiveEntry(Channel channel, String groupName, int index) {
    }
}
