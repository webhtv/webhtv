package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackProgressWriterEpisodeTest {

    @Test
    public void sameEpisodeUsesPersistedTmdbPositionWhenIncomingSourceHasOnlyTitle() {
        History local = history("源站第20集", "", 2, 20);
        PlaybackProgressInput input = input("20. 新标题", "");

        assertTrue(PlaybackProgressWriter.isSameEpisode(local, input));
    }

    @Test
    public void emptyUrlsDoNotMakeDifferentEpisodeTitlesLookIdentical() {
        History local = history("源站第20集", "", 2, 20);
        PlaybackProgressInput input = input("源站第21集", "");

        assertFalse(PlaybackProgressWriter.isSameEpisode(local, input));
    }

    @Test
    public void sourceBindingIsCopiedIntoPersistedHistory() {
        History history = new History();
        PlaybackProgressInput input = new PlaybackProgressInput();
        input.sourceBindingKey = "stable-interface-key";

        PlaybackProgressWriter.applySourceBindingKey(history, input);

        assertTrue("stable-interface-key".equals(history.getSourceBindingKey()));
        assertTrue("stable-interface-key".equals(history.copy().getSourceBindingKey()));
    }

    @Test
    public void fingerprintProvesSameEpisodeWhenPeerHasNoUrl() {
        // 只读 API 故意不输出 episodeUrl，只给指纹；对端原样回推时就是这种形态。
        // 没有季集号的内容（非 TMDB）只有指纹能证明「这是同一集」。
        History local = history("源站第1集", "https://cdn.example.com/ep01.m3u8", -1, -1);
        PlaybackProgressInput input = input("源站第1集", "");
        input.episodeId = PlaybackRecord.episodeId("https://cdn.example.com/ep01.m3u8");

        assertTrue(PlaybackProgressWriter.sameEpisodeFingerprint(local, input));
    }

    @Test
    public void differentFingerprintIsNotTheSameEpisode() {
        // 对端推来的可能是另一集：此时不能把本地地址当成新集的地址保留。
        History local = history("源站第1集", "https://cdn.example.com/ep01.m3u8", -1, -1);
        PlaybackProgressInput input = input("源站第2集", "");
        input.episodeId = PlaybackRecord.episodeId("https://cdn.example.com/ep02.m3u8");

        assertFalse(PlaybackProgressWriter.sameEpisodeFingerprint(local, input));
    }

    @Test
    public void oneSidedFingerprintIsNotEnough() {
        // 单侧有指纹无法证明同一集（对端可能是另一条线路的同名集），必须保守。
        History local = history("源站第1集", "https://cdn.example.com/ep01.m3u8", -1, -1);
        PlaybackProgressInput input = input("源站第1集", "");

        assertFalse(PlaybackProgressWriter.sameEpisodeFingerprint(local, input));
        assertFalse(PlaybackProgressWriter.sameEpisodeFingerprint(null, input));
    }

    @Test
    public void peerRoundTripWithoutUrlKeepsTheLocalPlaybackUrl() {
        // 端到端决策：对端把只读 API 读到的记录原样回推（没有 episodeUrl，只有指纹），
        // 本地已落库的播放地址必须保留——它是「从历史继续播放」定位集条目的依据。
        String url = "https://cdn.example.com/ep01.m3u8";
        History local = history("源站第1集", url, -1, -1);
        PlaybackProgressInput input = input("源站第1集", "");
        input.episodeId = PlaybackRecord.episodeId(url);

        assertEquals(url, PlaybackProgressWriter.resolveEpisodeUrl(local, input, false));
    }

    @Test
    public void peerPushingAnotherEpisodeDoesNotKeepTheStaleUrl() {
        // 反向约束：对端推的确是另一集时，不能把上一集的地址挂到新集的进度上。
        History local = history("源站第1集", "https://cdn.example.com/ep01.m3u8", -1, -1);
        PlaybackProgressInput input = input("源站第2集", "");
        input.episodeId = PlaybackRecord.episodeId("https://cdn.example.com/ep02.m3u8");

        assertEquals("", PlaybackProgressWriter.resolveEpisodeUrl(local, input, false));
    }

    @Test
    public void incomingUrlAlwaysWins() {
        // 入参带了地址就是权威（对端在播放、地址是新的），直接采用。
        History local = history("源站第1集", "https://cdn.example.com/old.m3u8", -1, -1);
        PlaybackProgressInput input = input("源站第1集", "https://cdn.example.com/new.m3u8");

        assertEquals("https://cdn.example.com/new.m3u8",
                PlaybackProgressWriter.resolveEpisodeUrl(local, input, false));
    }

    @Test
    public void sameEpisodeNameKeepsTheLocalUrl() {
        // 既有容错语义（sameEpisode=true）也必须保留本地地址，不能只认指纹。
        History local = history("源站第1集", "https://cdn.example.com/ep01.m3u8", -1, -1);
        PlaybackProgressInput input = input("源站第1集", "");

        assertEquals("https://cdn.example.com/ep01.m3u8",
                PlaybackProgressWriter.resolveEpisodeUrl(local, input, true));
    }

    @Test
    public void firstTimeWriteWithoutUrlStaysEmpty() {
        // 本地本来就没有记录：没有地址可保留，落空串而不是 null。
        PlaybackProgressInput input = input("源站第1集", "");

        assertEquals("", PlaybackProgressWriter.resolveEpisodeUrl(null, input, false));
    }

    @Test
    public void episodeWithoutTmdbIdentityKeepsTheConfirmedLocalSeason() {
        // 入参没有 TMDB 季集身份但集号可解析时，不得把本地已确认季号抹成 0。
        History local = history("源站第5集", "https://cdn.example.com/ep05.m3u8", 2, 5);
        History history = local.copy();
        PlaybackProgressInput input = input("源站第6集", "https://cdn.example.com/ep06.m3u8");
        input.episodeNumber = 6;

        PlaybackProgressWriter.applyEpisodePosition(history, local, input, false);

        assertFalse(input.hasTmdbEpisodeIdentity());
        assertEquals(2, history.getTmdbSeasonNumber());
        assertEquals(6, history.getTmdbEpisodeNumber());
    }

    @Test
    public void episodeWithoutTmdbIdentityFallsBackToTheIncomingSeasonWhenLocalHasNone() {
        History local = history("源站第5集", "https://cdn.example.com/ep05.m3u8", -1, -1);
        History history = local.copy();
        PlaybackProgressInput input = input("源站第6集", "https://cdn.example.com/ep06.m3u8");
        input.seasonNumber = 4;
        input.episodeNumber = 6;

        PlaybackProgressWriter.applyEpisodePosition(history, local, input, false);

        assertEquals(4, history.getTmdbSeasonNumber());
        assertEquals(6, history.getTmdbEpisodeNumber());
    }

    @Test
    public void episodeWithoutTmdbIdentityAndWithoutNumberStillClearsThePosition() {
        History local = history("源站第5集", "https://cdn.example.com/ep05.m3u8", 2, 5);
        History history = local.copy();
        PlaybackProgressInput input = input("下一集", "https://cdn.example.com/ep06.m3u8");

        PlaybackProgressWriter.applyEpisodePosition(history, local, input, false);

        assertEquals(0, history.getTmdbSeasonNumber());
        assertEquals(0, history.getTmdbEpisodeNumber());
    }

    @Test
    public void sameEpisodeKeepsThePersistedTmdbPositionUntouched() {
        History local = history("源站第5集", "https://cdn.example.com/ep05.m3u8", 2, 5);
        History history = local.copy();
        PlaybackProgressInput input = input("源站第5集", "");

        PlaybackProgressWriter.applyEpisodePosition(history, local, input, true);

        assertEquals(2, history.getTmdbSeasonNumber());
        assertEquals(5, history.getTmdbEpisodeNumber());
    }

    private static History history(String remarks, String url, int season, int episode) {
        History history = new History();
        history.setVodRemarks(remarks);
        history.setEpisodeUrl(url);
        history.setTmdbSeasonNumber(season);
        history.setTmdbEpisodeNumber(episode);
        return history;
    }

    private static PlaybackProgressInput input(String episodeName, String episodeUrl) {
        PlaybackProgressInput input = new PlaybackProgressInput();
        input.episodeName = episodeName;
        input.episodeUrl = episodeUrl;
        return input;
    }
}
