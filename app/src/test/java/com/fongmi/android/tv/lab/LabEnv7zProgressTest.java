package com.fongmi.android.tv.lab;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * 7zz 的 -bsp1 进度解析契约：只认行首的 1~3 位 "NN%"，单调递增，且跨 read 边界不丢状态。
 */
public final class LabEnv7zProgressTest {

    private static int feed(LabEnv.PercentScanner scanner, String text) {
        byte[] buf = text.getBytes(StandardCharsets.UTF_8);
        return scanner.accept(buf, buf.length);
    }

    @Test
    public void readsCarriageReturnRefreshedPercent() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(3, feed(scanner, "\r  3%"));
        assertEquals(45, feed(scanner, "\r 45%"));
        assertEquals(100, feed(scanner, "\r100%"));
    }

    @Test
    public void keepsStateAcrossBufferBoundary() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(-1, feed(scanner, "\r  1"));
        assertEquals(12, feed(scanner, "2%"));
    }

    @Test
    public void ignoresDigitsInsideFileNames() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(-1, feed(scanner, "Extracting  node-v20.1.0-linux-arm64/50%\r"));
    }

    @Test
    public void rejectsPercentOutsideOneToThreeDigits() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(-1, feed(scanner, "\r1234%"));
        assertEquals(-1, feed(scanner, "\r%"));
    }

    @Test
    public void rejectsValuesAboveHundred() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(-1, feed(scanner, "\r999%"));
    }

    @Test
    public void rejectsNonMonotonicProgress() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(60, feed(scanner, "\r 60%"));
        assertEquals(-1, feed(scanner, "\r 20%"));
        assertEquals(-1, feed(scanner, "\r 60%"));
        assertEquals(61, feed(scanner, "\r 61%"));
    }

    @Test
    public void backspaceReturningToLineStartIsAccepted() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(7, feed(scanner, "\b7%"));
    }

    @Test
    public void tabIndentIsStillLineStart() {
        LabEnv.PercentScanner scanner = new LabEnv.PercentScanner();
        assertEquals(9, feed(scanner, "\t9%"));
    }

    @Test
    public void nativeBytesLeavesHundredPercentForRealCompletion() {
        // 7zz 的百分比是"当前文件"进度，同包下一个文件会回到 0%，所以封顶 99%，
        // 100% 只由解压真正结束时的补发给出。
        assertEquals(0L, LabEnv.nativeBytes(0, 1000));
        assertEquals(500L, LabEnv.nativeBytes(50, 1000));
        assertEquals(990L, LabEnv.nativeBytes(99, 1000));
        assertEquals(990L, LabEnv.nativeBytes(100, 1000));
    }

    @Test
    public void nativeBytesWithoutTotalReportsNothing() {
        // total<=0（量不出总字节）时不上报字节数，UI 停在不确定进度而不是伪造进度。
        assertEquals(0L, LabEnv.nativeBytes(50, 0));
        assertEquals(0L, LabEnv.nativeBytes(50, -1));
    }
}
