package com.fongmi.android.tv.playback.origin;

import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import java.util.List;

public final class OriginHistoryResolver {
    public static final class Resolved {
        public final Vod vod;
        public final Flag flag;
        public final Episode episode;
        public final Result result;
        Resolved(Vod vod, Flag flag, Episode episode, Result result) {
            this.vod = vod; this.flag = flag; this.episode = episode; this.result = result;
        }
    }
    public static final class Failure extends Exception {
        public final String reason;
        public Failure(String reason, String message) { super(message == null ? reason : message); this.reason = reason; }
    }
    private OriginHistoryResolver() { }
    public static Failure failure(String reason, String message) { return new Failure(reason, message); }

    public static Resolved resolve(History history) throws Failure {
        return resolve(history, history.getPlayer());
    }
    public static Resolved resolve(History history, int playerType) throws Failure {
        Site site = VodConfig.get().getSite(history.getSiteKey());
        if (site.isEmpty()) throw failure(OriginSessionContract.SITE_MISSING, history.getSiteKey());
        if (site.getType() == 3 && site.spider() instanceof com.github.catvod.crawler.SpiderNull)
            throw failure(OriginSessionContract.ISOLATION_ERROR, "origin runtime did not initialize");
        Result detail;
        try { detail = loadDetail(site, history.getVodId()); }
        catch (Failure e) { throw e; }
        catch (Exception e) {
            if (infrastructure(e)) throw failure(OriginSessionContract.ISOLATION_ERROR, e.toString());
            if (auth(e)) throw failure(OriginSessionContract.ORIGIN_AUTH_REQUIRED, e.toString());
            throw failure(OriginSessionContract.DETAIL_FAILED, e.toString());
        }
        if (detail.getList().isEmpty()) throw failure(OriginSessionContract.DETAIL_FAILED, "empty origin detail");
        Vod vod = detail.getVod();
        String savedUrl = history.getEpisodeUrl();
        Flag preferred = null;
        for (int i = 0; i < vod.getFlags().size(); i++) {
            Flag flag = vod.getFlags().get(i);
            if (history.getSourceBindingKey().equals(Flag.stableKey(flag, i))) preferred = flag;
        }
        if (preferred != null) {
            Episode episode = unique(preferred.getEpisodes(), history, true);
            if (episode != null) return play(history.getSiteKey(), vod, preferred, episode, playerType);
        }
        Resolved match = null;
        for (Flag flag : vod.getFlags()) {
            Episode episode = unique(flag.getEpisodes(), history, true);
            if (episode == null) continue;
            if (match != null) throw failure(OriginSessionContract.EPISODE_MISSING, "ambiguous origin episode URL");
            match = new Resolved(vod, flag, episode, null);
        }
        if (match == null) {
            for (Flag flag : vod.getFlags()) {
                if (!TextUtils.isEmpty(history.getVodFlag()) && !history.getVodFlag().equals(flag.getFlag())) continue;
                Episode episode = unique(flag.getEpisodes(), history, false);
                if (episode == null) continue;
                if (match != null) throw failure(OriginSessionContract.EPISODE_MISSING, "ambiguous origin episode identity");
                match = new Resolved(vod, flag, episode, null);
            }
        }
        if (match == null) throw failure(OriginSessionContract.EPISODE_MISSING, "origin target episode missing");
        return play(history.getSiteKey(), vod, match.flag, match.episode, playerType);
    }

    public static Resolved select(String siteKey, Vod vod, String stableFlagKey, String episodeUrl, int playerType) throws Failure {
        for (int i = 0; i < vod.getFlags().size(); i++) {
            Flag flag = vod.getFlags().get(i);
            if (!stableFlagKey.equals(Flag.stableKey(flag, i))) continue;
            Episode target = null;
            for (Episode episode : flag.getEpisodes()) {
                if (!episodeUrl.equals(episode.getUrl())) continue;
                if (target != null) throw failure(OriginSessionContract.EPISODE_MISSING, "ambiguous episode request");
                target = episode;
            }
            if (target != null) return play(siteKey, vod, flag, target, playerType);
        }
        throw failure(OriginSessionContract.EPISODE_MISSING, "episode not in original detail");
    }
    private static Resolved play(String siteKey, Vod vod, Flag flag, Episode episode, int playerType) throws Failure {
        Result result;
        try { result = SiteApi.playerContent(siteKey, flag.getFlag(), episode.getUrl(), playerType); }
        catch (Exception e) { throw failure(auth(e) ? OriginSessionContract.ORIGIN_AUTH_REQUIRED : OriginSessionContract.PLAYER_FAILED, e.toString()); }
        if (result.hasMsg() || result.getRealUrl().isEmpty()) throw failure(OriginSessionContract.PLAYER_FAILED, result.getMsg());
        result.setKey(siteKey);
        return new Resolved(vod, flag, episode, result);
    }
    private static Result loadDetail(Site site, String id) throws Exception {
        if (site.getType() == 3) return SiteApi.detailContent(site.getKey(), id, true);
        androidx.collection.ArrayMap<String, String> params = new androidx.collection.ArrayMap<>();
        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
        params.put("ids", id);
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        okhttp3.Call call = site.getExt().length() <= 1000
                ? com.github.catvod.net.OkHttp.newCall(site.getApi(), site.getHeader(), params)
                : com.github.catvod.net.OkHttp.newCall(site.getApi(), site.getHeader(), com.github.catvod.net.OkHttp.toBody(params));
        try (okhttp3.Response response = call.execute()) {
            if (response.code() == 401 || response.code() == 403)
                throw failure(OriginSessionContract.ORIGIN_AUTH_REQUIRED, "origin detail authentication failed");
            if (!response.isSuccessful()) throw failure(OriginSessionContract.DETAIL_FAILED, "origin detail HTTP " + response.code());
            String raw = response.body() == null ? "" : response.body().string();
            com.fongmi.android.tv.api.TmdbSourceCredentialIngress.Ingress ingress = com.fongmi.android.tv.api.TmdbSourceCredentialIngress.extractRootAndStrip(raw);
            Result result = Result.fromType(site.getType(), ingress.getSanitizedJson());
            com.fongmi.android.tv.player.Source.get().parse(result.getVod().setFlags());
            return result;
        }
    }

    private static Episode unique(List<Episode> episodes, History history, boolean exactUrl) throws Failure {
        Episode match = null;
        for (Episode episode : episodes) {
            boolean matches = exactUrl ? !history.getEpisodeUrl().isEmpty() && history.getEpisodeUrl().equals(episode.getUrl())
                    : episode.matchesNumber(history.getEpisode()) || history.getVodRemarks().equals(episode.getName());
            if (!matches) continue;
            if (match != null) throw failure(OriginSessionContract.EPISODE_MISSING, "non-unique episode");
            match = episode;
        }
        return match;
    }
    public static boolean infrastructure(Throwable error) {
        String message = String.valueOf(error.getMessage());
        return message.contains("ISOLATION_ERROR") || message.contains("PORT_OWNERSHIP_AMBIGUOUS")
                || error instanceof IllegalStateException || error instanceof LinkageError;
    }
    public static boolean auth(Throwable error) {
        String value = String.valueOf(error.getMessage());
        return value.contains("401") || value.contains("403") || value.contains("Unauthorized") || value.contains("ORIGIN_AUTH_REQUIRED");
    }
}
