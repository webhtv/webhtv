package com.fongmi.android.tv.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TtsTextSplitterTest {

    @Test
    public void systemModeKeepsWholeParagraph() {
        List<TtsTextSplitter.Chunk> chunks = TtsTextSplitter.split(
                Arrays.asList("第一段。第二句！", "第二段很长很长很长。"), 0);
        assertEquals(2, chunks.size());
        assertEquals("第一段。第二句！", chunks.get(0).text);
        assertEquals(0, chunks.get(0).paragraph);
        assertEquals("第二段很长很长很长。", chunks.get(1).text);
        assertEquals(1, chunks.get(1).paragraph);
    }

    @Test
    public void onlineModeSplitsSentencesAndTracksParagraphIndex() {
        List<TtsTextSplitter.Chunk> chunks = TtsTextSplitter.split(
                Arrays.asList("甲。乙！丙？", "丁。"), TtsTextSplitter.MAX_CHUNK);
        assertEquals(4, chunks.size());
        assertEquals("甲。", chunks.get(0).text);
        assertEquals("乙！", chunks.get(1).text);
        assertEquals("丙？", chunks.get(2).text);
        assertEquals("丁。", chunks.get(3).text);
        // 段落归属必须正确，否则高亮会跳到别的段落
        assertEquals(0, chunks.get(0).paragraph);
        assertEquals(0, chunks.get(2).paragraph);
        assertEquals(1, chunks.get(3).paragraph);
    }

    @Test
    public void punctuationOnlyParagraphsAreDropped() {
        List<TtsTextSplitter.Chunk> chunks = TtsTextSplitter.split(
                Arrays.asList("……", "——", "　", "正文。"), TtsTextSplitter.MAX_CHUNK);
        assertEquals(1, chunks.size());
        assertEquals("正文。", chunks.get(0).text);
        assertEquals(3, chunks.get(0).paragraph);
    }

    @Test
    public void longSentenceIsHardSplitUnderLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("汉字测试，");
        sb.append("。");
        List<TtsTextSplitter.Chunk> chunks = TtsTextSplitter.split(
                java.util.Collections.singletonList(sb.toString()), 20);
        assertTrue("长句必须被切开", chunks.size() > 1);
        for (TtsTextSplitter.Chunk chunk : chunks) {
            assertTrue("单个片段不得超过上限: " + chunk.text.length(), chunk.text.length() <= 20);
        }
    }

    @Test
    public void controlCharactersAreStripped() {
        List<TtsTextSplitter.Chunk> chunks = TtsTextSplitter.split(
                Arrays.asList("正文\u0000\u0007内容。"), 0);
        assertEquals(1, chunks.size());
        assertFalse(chunks.get(0).text.contains("\u0000"));
        assertFalse(chunks.get(0).text.contains("\u0007"));
        assertEquals("正文内容。", chunks.get(0).text);
    }

    @Test
    public void silentDetectionMatchesSpeechEngineExpectation() {
        assertTrue(TtsTextSplitter.isSilent("……"));
        assertTrue(TtsTextSplitter.isSilent("   "));
        assertTrue(TtsTextSplitter.isSilent(null));
        assertFalse(TtsTextSplitter.isSilent("a"));
        assertFalse(TtsTextSplitter.isSilent("字"));
        assertFalse(TtsTextSplitter.isSilent("，。字。"));
    }

    @Test
    public void pitchMapsToSsmlOffset() {
        assertEquals("+0Hz", TtsOptions.pitchOffsetHz(1.0f));
        assertEquals("+50Hz", TtsOptions.pitchOffsetHz(1.5f));
        assertEquals("-50Hz", TtsOptions.pitchOffsetHz(0.5f));
        // 越界值必须按面板范围夹取，避免把非法 pitch 丢给微软接口
        assertEquals("-50Hz", TtsOptions.pitchOffsetHz(0.1f));
        assertEquals("+50Hz", TtsOptions.pitchOffsetHz(9f));
    }

    @Test
    public void emptyInputProducesNoChunks() {
        assertTrue(TtsTextSplitter.split(null, 20).isEmpty());
        assertTrue(TtsTextSplitter.split(java.util.Collections.emptyList(), 20).isEmpty());
    }
}
