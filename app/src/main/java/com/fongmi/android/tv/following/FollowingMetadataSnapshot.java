package com.fongmi.android.tv.following;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class FollowingMetadataSnapshot {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String RETURNING = "RETURNING";
    public static final String PLANNED = "PLANNED";
    public static final String ENDED = "ENDED";
    public static final String CANCELED = "CANCELED";

    public String source = "";
    public String status = UNKNOWN;
    public int latestReleasedSeason;
    public int latestReleasedEpisode;
    public int seasonTotalEpisodes;
    public int seasonReleasedEpisodes;
    public int seriesTotalEpisodes;
    public int nextAirSeason;
    public int nextAirEpisode;
    public long nextAirAt;
    public int nextAirWeekday;
    public long fetchedAt;

    public static String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (status.contains("RETURNING") || status.contains("PRODUCTION") || status.contains("PILOT")) return RETURNING;
        if (status.contains("PLANNED")) return PLANNED;
        if (status.contains("ENDED")) return ENDED;
        if (status.contains("CANCEL")) return CANCELED;
        return UNKNOWN;
    }

    public FollowingMetadataSnapshot copy() {
        FollowingMetadataSnapshot item = new FollowingMetadataSnapshot();
        item.source = source;
        item.status = status;
        item.latestReleasedSeason = latestReleasedSeason;
        item.latestReleasedEpisode = latestReleasedEpisode;
        item.seasonTotalEpisodes = seasonTotalEpisodes;
        item.seasonReleasedEpisodes = seasonReleasedEpisodes;
        item.seriesTotalEpisodes = seriesTotalEpisodes;
        item.nextAirSeason = nextAirSeason;
        item.nextAirEpisode = nextAirEpisode;
        item.nextAirAt = nextAirAt;
        item.nextAirWeekday = nextAirWeekday;
        item.fetchedAt = fetchedAt;
        return item;
    }

    public static int weekday(String utcDate) {
        if (utcDate == null || utcDate.length() < 10) return 0;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        calendar.setLenient(false);
        try {
            calendar.set(Integer.parseInt(utcDate.substring(0, 4)),
                    Integer.parseInt(utcDate.substring(5, 7)) - 1,
                    Integer.parseInt(utcDate.substring(8, 10)), 0, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.get(Calendar.DAY_OF_WEEK);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
