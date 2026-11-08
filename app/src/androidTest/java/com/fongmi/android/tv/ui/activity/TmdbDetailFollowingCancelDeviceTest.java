package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.following.Following;
import com.fongmi.android.tv.following.FollowingDeviceDataRule;
import com.fongmi.android.tv.following.FollowingSettings;
import com.fongmi.android.tv.following.FollowingStore;
import com.fongmi.android.tv.setting.Setting;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

/**
 * 锁定详情页「已追更」按钮的用户可见行为（FOLLOW-DETAIL-CANCEL）。
 * <p>
 * 背景：播放页已改为就地取消（写墓碑 + 立即刷新按钮，不跳页），但详情页此前仍然是
 * 「已追更 → 跳转追更页」。用户要求详情页补齐同一语义：点击「已追更」必须**立即生效**，
 * 且**不离开当前页**。
 * <p>
 * 本用例在真实设备上真正点击详情页的追更按钮，然后断言：
 * <ol>
 *   <li>没有启动 {@code FollowingActivity}（没有跳页）；</li>
 *   <li>该身份已写入墓碑（取消立即落库）；</li>
 *   <li>按钮文字立即回到「加入追更」（状态立即刷新，不需要退出重进）。</li>
 * </ol>
 */
@RunWith(AndroidJUnit4.class)
public class TmdbDetailFollowingCancelDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private static final int TMDB_ID = 993378;
    private static final String IDENTITY = "tmdb:tv:" + TMDB_ID + ":s0";
    private static final String SHOW_NAME = "详情页就地取消设备用例";
    private static final long POLL_TIMEOUT_MS = 15_000L;
    private static final long POLL_INTERVAL_MS = 100L;
    /** 按钮文字必须连续稳定这么久才算落定（避开 updateFollowingState 的乐观中间态）。 */
    private static final long STABLE_WINDOW_MS = 1_500L;
    /** 至少等这么久再开始判断稳定，给 Room 异步写 + 回主线程刷新留足时间。 */
    private static final long MIN_SETTLE_MS = 2_000L;

    private boolean previousEnabled;

    @Before
    public void setUp() {
        previousEnabled = FollowingSettings.isEnabled();
        FollowingSettings.setEnabled(true);
        FollowingStore.purge(IDENTITY);
    }

    @After
    public void tearDown() {
        FollowingStore.purge(IDENTITY);
        FollowingSettings.setEnabled(previousEnabled);
    }

    @Test
    public void tappingAlreadyFollowedCancelsInPlaceWithoutLeavingThePage() throws Exception {
        // 真实监测 FollowingActivity 是否被启动：命中 0 次才是“没有跳页”。
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor followingMonitor =
                instrumentation.addMonitor(FollowingActivity.class.getName(), null, false);
        try (ActivityScenario<TmdbDetailActivity> scenario = ActivityScenario.launch(detailIntent())) {
            // 0) 前置：当前身份必须可追更，否则按钮是 GONE，用例会变成空断言。
            saveActive();
            refreshFollowingState(scenario);
            assertEquals("活跃行必须显示已追更", "已追更", awaitSettledButtonText(scenario));

            // 1) 真正点击详情页的追更按钮（与用户手势同一条路径）。
            clickFollowing(scenario);

            // 2) 取消必须立即生效：写入墓碑行，而不是物理删除。
            Following tombstone = awaitTombstone();
            assertNotNull("点击后必须写入墓碑行（取消立即落库）", tombstone);
            assertTrue("取消必须是写墓碑而不是物理删除", tombstone.isDeleted());

            // 3) 按钮必须立即回到「加入追更」，全程停留在当前详情页。
            assertEquals("详情页取消追更必须立即生效并就地刷新按钮",
                    "加入追更", awaitSettledButtonText(scenario));

            // 4) 绝不跳转追更页：监测器命中数必须为 0，且当前页仍可交互（未被 finish）。
            assertEquals("详情页取消追更不得启动 FollowingActivity", 0, followingMonitor.getHits());
            scenario.onActivity(activity -> {
                assertFalse("取消追更不得结束当前详情页", activity.isFinishing());
                assertEquals("取消追更后仍必须停留在 TmdbDetailActivity",
                        TmdbDetailActivity.class, activity.getClass());
            });
        } finally {
            instrumentation.removeMonitor(followingMonitor);
        }
    }

    private Intent detailIntent() {
        Context context = ApplicationProvider.getApplicationContext();
        return new Intent(context, TmdbDetailActivity.class)
                .putExtra("detail_mode", Setting.DETAIL_OPEN_PLAYER)
                .putExtra("fusion", false)
                .putExtra("auto_play", false)
                .putExtra("key", "instrumented")
                .putExtra("id", "instrumented-following-cancel")
                .putExtra("name", SHOW_NAME)
                .putExtra("pic", "")
                .putExtra("mark", "")
                .putExtra("tmdb_id", TMDB_ID)
                .putExtra("tmdb_media_type", "tv")
                .putExtra("tmdb_title", SHOW_NAME);
    }

    private void saveActive() {
        Following item = FollowingDeviceDataRule.newFollowing(IDENTITY);
        item.seriesKey = "tmdb:tv:" + TMDB_ID;
        item.tmdbId = TMDB_ID;
        item.mediaType = "tv";
        item.siteKey = "instrumented";
        item.vodId = "instrumented-following-cancel";
        item.vodName = SHOW_NAME;
        item.trackedSeason = 0;
        item.nextCheckAt = Long.MAX_VALUE;
        FollowingStore.saveNew(item, null);
        assertNotNull("活跃行必须可被 find() 查到", FollowingStore.find(IDENTITY));
    }

    private void clickFollowing(ActivityScenario<TmdbDetailActivity> scenario) {
        scenario.onActivity(activity -> {
            TextView button = activity.findViewById(R.id.following);
            assertNotNull("详情页必须存在追更按钮", button);
            assertEquals("追更按钮必须可见（该身份可追更）", View.VISIBLE, button.getVisibility());
            assertTrue("详情页追更按钮必须可点击", button.isEnabled());
            button.performClick();
        });
    }

    /** 等墓碑行落库；取消是异步写库，但必须是「立即」发生而不是等用户返回列表。 */
    private Following awaitTombstone() throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < POLL_TIMEOUT_MS) {
            Following raw = FollowingStore.findAny(IDENTITY);
            if (raw != null && raw.isDeleted()) return raw;
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return FollowingStore.findAny(IDENTITY);
    }

    /** 触发一次追更状态刷新（与真实进入详情页走的同一条判定路径）。 */
    private void refreshFollowingState(ActivityScenario<TmdbDetailActivity> scenario) {
        scenario.onActivity(activity -> {
            try {
                Method refresh = TmdbDetailActivity.class.getDeclaredMethod("updateFollowingState");
                refresh.setAccessible(true);
                refresh.invoke(activity);
            } catch (Exception e) {
                throw new AssertionError("updateFollowingState 调用失败: " + e, e);
            }
        });
    }

    /**
     * 等按钮文字落定后返回它。
     * <p>
     * 不能"一读到期望值就返回"：`updateFollowingState()` 先乐观地写成"加入追更"，
     * 再由 Room 异步回调覆盖为最终态。若提前返回就会读到中间态，让用例变成空断言。
     */
    private String awaitSettledButtonText(ActivityScenario<TmdbDetailActivity> scenario) throws Exception {
        long start = System.currentTimeMillis();
        String current = readButtonText(scenario);
        long stableSince = start;
        while (System.currentTimeMillis() - start < POLL_TIMEOUT_MS) {
            Thread.sleep(POLL_INTERVAL_MS);
            String next = readButtonText(scenario);
            long now = System.currentTimeMillis();
            if (!next.equals(current)) {
                current = next;
                stableSince = now;
                continue;
            }
            if (now - start >= MIN_SETTLE_MS && now - stableSince >= STABLE_WINDOW_MS) {
                return current;
            }
        }
        return current;
    }

    private String readButtonText(ActivityScenario<TmdbDetailActivity> scenario) {
        String[] captured = new String[1];
        scenario.onActivity(activity -> {
            TextView button = activity.findViewById(R.id.following);
            assertNotNull("详情页必须存在追更按钮", button);
            assertEquals("追更按钮必须可见（该身份可追更）", View.VISIBLE, button.getVisibility());
            captured[0] = button.getText().toString();
        });
        return captured[0];
    }
}
