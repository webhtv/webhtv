package com.fongmi.android.tv.player;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.catvod.crawler.diagnostics.DiagnosticEvent;

import org.junit.Test;

/**
 * {@code surface.rebind} 的事件名与字段名必须登记在 catvod 的诊断白名单里。
 *
 * <p>{@code PlaybackDiagnosticCollector.emit} 把白名单外名字抛出的 {@link IllegalArgumentException}
 * 吞成 {@code collectorFailure}：现场既拿不到这条证据，采集器还会被记成故障。这里直接构造生产代码
 * 写入的同一批事实，把白名单契约钉住。
 */
public class SurfaceRebindDiagnosticTest {

    @Test
    public void surfaceRebindFactsAreRegisteredInTheSchema() {
        DiagnosticEvent event = SurfaceDiagnosticCollector.rebindFacts(
                new DiagnosticEvent(SurfaceDiagnosticCollector.EVENT_REBIND, "trace", "instance", 1, 1),
                "state=2", false, null);

        String json = event.json();
        assertTrue("the event name must survive the schema allowlist", json.contains("\"event\":\"surface.rebind\""));
        assertTrue(json.contains("\"detachedFirst\""));
        assertTrue(json.contains("\"surfaceViewPresent\""));
        assertTrue(json.contains("\"reason\""));
        assertTrue(json.contains("\"physicalDisplay\""));
        assertTrue(json.contains("\"surfaceId\""));
        assertTrue(json.contains("\"valid\""));
    }

    @Test
    public void theAllowlistGuardIsNotVacuous() {
        try {
            new DiagnosticEvent(SurfaceDiagnosticCollector.EVENT_REBIND + ".typo", "t", "i", 1, 1);
            fail("unknown diagnostic events must be rejected");
        } catch (IllegalArgumentException expected) {
            // 白名单外的名字确实会被拒，上面的用例才有意义。
        }
        try {
            SurfaceDiagnosticCollector.rebindFacts(
                            new DiagnosticEvent(SurfaceDiagnosticCollector.EVENT_REBIND, "t", "i", 1, 1),
                            "x", false, null)
                    .observed("unregisteredField", true);
            fail("unknown diagnostic fields must be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }
}
