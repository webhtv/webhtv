package com.fongmi.android.tv.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.parser.EpgParser;
import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.setting.LiveEpgSetting;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.ZoneId;

public class LiveApi {

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    /**
     * [CHANGED] 返回「是否至少绑定了一个频道的 EPG」，而不是「有没有抛异常」。
     * 原来 EpgParser.start() 一旦中途抛异常，这里就 return false，
     * 导致 LiveActivity.setEpg(false) 不执行 notifyDataSetChanged()——
     * 数据其实已经写进 Channel.dataList 了，只是没通知 UI。
     */
    public static boolean parseXml(@NonNull Live item) {
        try {
            int bind = EpgParser.start(item);
            SpiderDebug.log("EpgParser", "parseXml bind=" + bind);
            return bind > 0;
        } catch (Throwable e) {
            // [CHANGED] 用 SpiderDebug 打全栈，原来的 e.printStackTrace() 按 "EpgParser" 过滤看不到
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            SpiderDebug.log("EpgParser", "parseXml异常：" + sw);
            return false;
        }
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        for (int offset : new int[]{-1, 0, 1}) fetchEpgDay(item, zoneId, offset);
        return item.getDataList().stream().filter(epg -> epg.equal(today)).findFirst().orElseGet(Epg::new).selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = getUrl(item);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        return result;
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
        if (need) item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), zoneId));
    }

    /**
     * [NEW] 取指定偏移天数（相对今天，负数往日、正数明日）的节目单。
     * 已经在内存里的直接返回，没有的按天单独拉取；解析结果不写回 Channel，
     * 交由主线程落库，避免后台线程改 dataList 引发并发问题。
     */
    @Nullable
    public static Epg getEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        for (Epg epg : new ArrayList<>(item.getDataList())) if (epg.equal(date) && !epg.getList().isEmpty()) return epg;
        String url = item.getEpg().replace("{date}", date);
        if (!url.startsWith("http")) return null;
        try {
            Epg epg = Epg.objectFrom(OkHttp.string(url), item.getTvgId(), zoneId);
            epg.setDate(date);
            return epg.getList().isEmpty() ? null : epg;
        } catch (Throwable e) {
            SpiderDebug.log("EpgParser", "getEpgDay offset=" + offset + " 异常：" + e);
            return null;
        }
    }
}
