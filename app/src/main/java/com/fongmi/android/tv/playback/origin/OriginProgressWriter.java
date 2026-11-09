package com.fongmi.android.tv.playback.origin;

import com.fongmi.android.tv.bean.History;

public final class OriginProgressWriter {
    private OriginProgressWriter() { }
    public static boolean write(History origin, long positionMs, long durationMs) {
        if (origin == null || origin.getKey() == null || positionMs < 0) return false;
        History current = History.find(origin.getCid(), origin.getKey());
        if (current == null || current.getTmdbId() != origin.getTmdbId()) return false;
        origin.saveOriginProgress(positionMs, durationMs);
        android.util.Log.i("OriginProgress", "cid=" + origin.getCid() + " key=" + origin.getKey() + " season=" + origin.getTmdbSeasonNumber()
                + " episode=" + origin.getTmdbEpisodeNumber() + " position=" + positionMs + " duration=" + durationMs);
        return true;
    }
}
