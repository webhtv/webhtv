package com.fongmi.android.tv.ui.helper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TmdbRowFocusChainTest {

    private static int[] up(int[][] links) {
        int[] up = new int[links.length];
        for (int i = 0; i < links.length; i++) up[i] = links[i][0];
        return up;
    }

    private static int[] down(int[][] links) {
        int[] down = new int[links.length];
        for (int i = 0; i < links.length; i++) down[i] = links[i][1];
        return down;
    }

    @Test
    public void nullOrEmptyInputsReturnEmptyResult() {
        assertEquals(0, TmdbRowFocusChain.link(null).length);
        assertEquals(0, TmdbRowFocusChain.link(new boolean[0]).length);
    }

    @Test
    public void allRowsPresentBuildsSequentialChain() {
        boolean[] present = {true, true, true};
        int[][] links = TmdbRowFocusChain.link(present);
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, 0, 1}, up(links));
        assertArrayEquals(new int[]{1, 2, TmdbRowFocusChain.NO_NEIGHBOR}, down(links));
    }

    @Test
    public void skippedRowIsCutOutAndNeighborsJoin() {
        // 剧照 present，海报缺失，相关视频 present → 剧照直接下落相关视频
        boolean[] present = {true, false, true, true};
        int[][] links = TmdbRowFocusChain.link(present);
        // 被跳过的海报行不持有任何邻居；剧照的 up 是上方最早的有效行（此处无）。
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR, 0, 2}, up(links));
        assertArrayEquals(new int[]{2, TmdbRowFocusChain.NO_NEIGHBOR, 3, TmdbRowFocusChain.NO_NEIGHBOR}, down(links));
    }

    @Test
    public void consecutiveMissingRowsAllJoinThrough() {
        // 剧照、海报、相关视频全部缺失 → 演员的下一个有效邻居是主创
        boolean[] present = {true, false, false, false, true};
        int[][] links = TmdbRowFocusChain.link(present);
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR,
                TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR, 0}, up(links));
        assertArrayEquals(new int[]{4, TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR,
                TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR}, down(links));
    }

    @Test
    public void firstRowMissingSkipsToNextActive() {
        boolean[] present = {false, true};
        int[][] links = TmdbRowFocusChain.link(present);
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR}, up(links));
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR}, down(links));
    }

    @Test
    public void lastRowMissingSkipsToPreviousActive() {
        boolean[] present = {true, false};
        int[][] links = TmdbRowFocusChain.link(present);
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR}, up(links));
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR}, down(links));
    }

    @Test
    public void singleActiveRowHasNoNeighbors() {
        int[][] links = TmdbRowFocusChain.link(new boolean[]{false, true, false});
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR,
                TmdbRowFocusChain.NO_NEIGHBOR}, up(links));
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR,
                TmdbRowFocusChain.NO_NEIGHBOR}, down(links));
    }

    @Test
    public void allRowsMissingGivesNoNeighbors() {
        int[][] links = TmdbRowFocusChain.link(new boolean[]{false, false});
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR}, up(links));
        assertArrayEquals(new int[]{TmdbRowFocusChain.NO_NEIGHBOR, TmdbRowFocusChain.NO_NEIGHBOR}, down(links));
    }

    @Test
    public void resultRowCountMatchesInputLength() {
        int[][] links = TmdbRowFocusChain.link(new boolean[]{true, false, true, false, true});
        assertEquals(5, links.length);
    }

    @Test
    public void doesNotMutateInput() {
        boolean[] present = {true, false, true};
        boolean[] copy = present.clone();
        TmdbRowFocusChain.link(present);
        assertArrayEquals(copy, present);
    }
}
