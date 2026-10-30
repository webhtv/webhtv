package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

/**
 * 能力门禁测试。核心断言是：**没有"默认允许"**。
 *
 * <p>这是 D2 决策（net.request 不做域名白名单）的补偿约束：既然不限制目标域名，
 * 那么"谁被允许发起请求"这道门就必须是严的，两者不能同时放松。
 */
public class PluginCapabilityRegistryTest {

    @Test
    public void unknownPermissionIsRejected() {
        assertFalse(PluginCapabilityRegistry.isKnownPermission("net.unlimited"));
        assertFalse(PluginCapabilityRegistry.isKnownPermission(null));
        assertFalse(PluginCapabilityRegistry.allows(Set.of("net.unlimited"), "net.unlimited"));
    }

    @Test
    public void nullAndEmptyPermissionsNeverAllow() {
        assertFalse(PluginCapabilityRegistry.allows(null, PluginCapabilityRegistry.PERMISSION_NET_REQUEST));
        assertFalse(PluginCapabilityRegistry.allows(Set.of(), PluginCapabilityRegistry.PERMISSION_NET_REQUEST));
        assertFalse(PluginCapabilityRegistry.allowsEvent(null, PluginEventBus.EVENT_UI_CONTENT_SHOWN));
        assertFalse(PluginCapabilityRegistry.allowsEvent(Set.of(), PluginEventBus.EVENT_UI_CONTENT_SHOWN));
        assertFalse(PluginCapabilityRegistry.allowsEvent(Set.of(PluginCapabilityRegistry.PERMISSION_EVENTS), null));
    }

    @Test
    public void netRequestIsGatedByPermission() {
        assertFalse(PluginCapabilityRegistry.allows(
                Set.of(PluginCapabilityRegistry.PERMISSION_EVENTS),
                PluginCapabilityRegistry.PERMISSION_NET_REQUEST));
        assertTrue(PluginCapabilityRegistry.allows(
                Set.of(PluginCapabilityRegistry.PERMISSION_NET_REQUEST),
                PluginCapabilityRegistry.PERMISSION_NET_REQUEST));
    }

    @Test
    public void uiSlotsAreIndependentlyGated() {
        Set<String> floatingOnly = Set.of(PluginCapabilityRegistry.PERMISSION_UI_FLOATING);
        assertTrue(PluginCapabilityRegistry.allows(floatingOnly, PluginCapabilityRegistry.PERMISSION_UI_FLOATING));
        assertFalse(PluginCapabilityRegistry.allows(floatingOnly, PluginCapabilityRegistry.PERMISSION_UI_TOOLBAR));
        assertFalse(PluginCapabilityRegistry.allows(floatingOnly, PluginCapabilityRegistry.PERMISSION_UI_PAGE));
    }

    @Test
    public void filterPermissionsDropsUnknownAndKeepsOrder() {
        Set<String> filtered = PluginCapabilityRegistry.filterPermissions(
                Set.of("bogus", PluginCapabilityRegistry.PERMISSION_NET_REQUEST, PluginCapabilityRegistry.PERMISSION_UI_PANEL));
        assertEquals(2, filtered.size());
        assertTrue(filtered.contains(PluginCapabilityRegistry.PERMISSION_NET_REQUEST));
        assertTrue(filtered.contains(PluginCapabilityRegistry.PERMISSION_UI_PANEL));
    }

    @Test
    public void eventsAreBoundToPermissions() {
        Set<String> permissions = Set.of(PluginCapabilityRegistry.PERMISSION_EVENTS);
        assertTrue(PluginCapabilityRegistry.allowsEvent(permissions, PluginEventBus.EVENT_ACTIVITY_CREATED));
        assertTrue(PluginCapabilityRegistry.allowsEvent(permissions, PluginEventBus.EVENT_APP_FOREGROUND));
        assertFalse(PluginCapabilityRegistry.allowsEvent(permissions, "ui.neverDeclared"));

        // 只有 episode 数据权限时，只能收 ui.contentShown
        Set<String> dataOnly = Set.of(PluginCapabilityRegistry.PERMISSION_EPISODE_DATA);
        assertTrue(PluginCapabilityRegistry.allowsEvent(dataOnly, PluginEventBus.EVENT_UI_CONTENT_SHOWN));
        assertFalse(PluginCapabilityRegistry.allowsEvent(dataOnly, PluginEventBus.EVENT_ACTIVITY_CREATED));
    }

    @Test
    public void permissionWithoutEventsAllowsNoEvent() {
        Set<String> netOnly = Set.of(PluginCapabilityRegistry.PERMISSION_NET_REQUEST);
        assertTrue(PluginCapabilityRegistry.eventsFor(PluginCapabilityRegistry.PERMISSION_NET_REQUEST).isEmpty());
        for (String event : new String[]{PluginEventBus.EVENT_APP_FOREGROUND, PluginEventBus.EVENT_ACTIVITY_CREATED,
                PluginEventBus.EVENT_UI_CONTENT_SHOWN}) {
            assertFalse(PluginCapabilityRegistry.allowsEvent(netOnly, event));
        }
    }
}
