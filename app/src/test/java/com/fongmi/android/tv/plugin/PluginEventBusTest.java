package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1 验收测试：证明事件总线在正确时机派发、按身份过滤、且无订阅者时零行为。
 *
 * <p>本测试不触碰 Android 类型（总线是纯 JVM），因此可在 {@code app/src/test} 直接跑。
 */
public class PluginEventBusTest {

    private PluginEventBus bus;

    @Before
    public void setUp() {
        bus = PluginEventBus.get();
        bus.clear();
    }

    @After
    public void tearDown() {
        bus.clear();
    }

    @Test
    public void emitWithoutSubscribersIsNoOp() {
        assertEquals(0, bus.emit(PluginEventBus.EVENT_ACTIVITY_CREATED, Map.of(PluginEventBus.KEY_ACTIVITY, "VideoActivity")));
        assertFalse(bus.hasSubscribers(PluginEventBus.EVENT_ACTIVITY_CREATED));
    }

    @Test
    public void activityIdentityIsCarriedInPayload() {
        List<Map<String, String>> received = Collections.synchronizedList(new ArrayList<>());
        bus.subscribe(PluginEventBus.EVENT_ACTIVITY_CREATED, (event, payload) -> received.add(payload));

        int delivered = bus.emitActivity(PluginEventBus.EVENT_ACTIVITY_CREATED, "TmdbDetailActivity");

        assertEquals(1, delivered);
        assertEquals(1, received.size());
        assertEquals("TmdbDetailActivity", received.get(0).get(PluginEventBus.KEY_ACTIVITY));
    }

    @Test
    public void subscriberOnlyReceivesItsOwnEvent() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger destroyed = new AtomicInteger();
        bus.subscribe(PluginEventBus.EVENT_ACTIVITY_CREATED, (event, payload) -> created.incrementAndGet());
        bus.subscribe(PluginEventBus.EVENT_ACTIVITY_DESTROYED, (event, payload) -> destroyed.incrementAndGet());

        bus.emitActivity(PluginEventBus.EVENT_ACTIVITY_CREATED, "VideoActivity");

        assertEquals(1, created.get());
        assertEquals(0, destroyed.get());
    }

    @Test
    public void throwingSubscriberDoesNotBreakHostOrOtherSubscribers() {
        AtomicInteger healthy = new AtomicInteger();
        bus.subscribe(PluginEventBus.EVENT_UI_CONTENT_SHOWN, (event, payload) -> {
            throw new IllegalStateException("plugin boom");
        });
        bus.subscribe(PluginEventBus.EVENT_UI_CONTENT_SHOWN, (event, payload) -> healthy.incrementAndGet());

        int delivered = bus.emitActivity(PluginEventBus.EVENT_UI_CONTENT_SHOWN, "HomeActivity");

        assertEquals(1, delivered);
        assertEquals(1, healthy.get());
    }

    @Test
    public void unsubscribeStopsDelivery() {
        AtomicInteger count = new AtomicInteger();
        PluginEventBus.Listener listener = (event, payload) -> count.incrementAndGet();
        bus.subscribe(PluginEventBus.EVENT_APP_FOREGROUND, listener);

        bus.emit(PluginEventBus.EVENT_APP_FOREGROUND, null);
        bus.unsubscribe(PluginEventBus.EVENT_APP_FOREGROUND, listener);
        bus.emit(PluginEventBus.EVENT_APP_FOREGROUND, null);

        assertEquals(1, count.get());
        assertFalse(bus.hasSubscribers(PluginEventBus.EVENT_APP_FOREGROUND));
    }

    @Test
    public void subscribeAllReceivesEveryEvent() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        bus.subscribeAll((event, payload) -> events.add(event));

        bus.emitActivity(PluginEventBus.EVENT_ACTIVITY_CREATED, "A");
        bus.emit(PluginEventBus.EVENT_APP_BACKGROUND, null);

        assertEquals(2, events.size());
        assertTrue(events.contains(PluginEventBus.EVENT_ACTIVITY_CREATED));
        assertTrue(events.contains(PluginEventBus.EVENT_APP_BACKGROUND));
    }

    @Test
    public void blankSubscriptionIsRejected() {
        PluginEventBus.Listener listener = (event, payload) -> {
        };
        assertFalse(bus.subscribe("", listener));
        assertFalse(bus.subscribe(null, listener));
        assertFalse(bus.subscribe(PluginEventBus.EVENT_APP_FOREGROUND, null));
        assertFalse(bus.subscribeAll(null));
    }
}
