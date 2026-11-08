package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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
 * 锁定「已追更」判定的用户可见行为（C37 复评修复点）。
 * <p>
 * 背景：`FollowingStore.resolveTmdb` 对墓碑目标会**故意返回墓碑行而不是 null**（C9 防复活守卫）。
 * 若 `TmdbDetailActivity` 用 `item != null` 判定「已追更」，则用户取消追更后按钮仍显示「已追更」，
 * 内联播放中再点只会重复写墓碑、永远无法重新追更。
 * <p>
 * 本用例在真实设备上对同一个 TMDB 身份验证按钮文字：活跃行 → 「已追更」；写入墓碑后 → 「加入追更」。
 * 该用例在修复前会失败（实测 expected:&lt;加入追更&gt; but was:&lt;已追更&gt;），因此不是空断言。
 */
@RunWith(AndroidJUnit4.class)
public class TmdbDetailFollowingTombstoneDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private static final int TMDB_ID = 993377;
    private static final String IDENTITY = "tmdb:tv:" + TMDB_ID + ":s0";
    private static final String SHOW_NAME = "墓碑判定设备用例";
    private static final long POLL_TIMEOUT_MS = 15_000L;
    private static final long POLL_INTERVAL_MS = 100L;
    /** 按钮文字必须连续稳定这么久才算落定（避开 updateFollowingState 的乐观中间态）。 */
    private static final long STABLE_WINDOW_MS = 1_500L;
    /** 至少等这么久再开始判断稳定，给 Room 异步查询 + 主线程回调留足时间。 */
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
    public void tombstonedIdentityShowsAddFollowingInsteadOfAlreadyFollowed() throws Exception {
        try (ActivityScenario<TmdbDetailActivity> scenario = ActivityScenario.launch(detailIntent())) {
            // 1) 活跃行：按钮必须是「已追更」。
            saveActive();
            refreshFollowingState(scenario);
            assertEquals("活跃行必须显示已追更", "已追更", awaitSettledButtonText(scenario));

            // 2) 取消追更（写墓碑，与 cancelFollowing 的实际落库效果一致）。
            FollowingStore.delete(IDENTITY);
            assertNotNull(FollowingStore.findAny(IDENTITY));
            assertTrue("删除必须写墓碑而不是物理删除", FollowingStore.findAny(IDENTITY).isDeleted());

            // 3) 墓碑必须被判为未追更：按钮回到「加入追更」。
            refreshFollowingState(scenario);
            assertEquals("墓碑行必须视为未追更，否则用户永远无法重新追更",
                    "加入追更", awaitSettledButtonText(scenario));
        }
    }

    private Intent detailIntent() {
        Context context = ApplicationProvider.getApplicationContext();
        return new Intent(context, TmdbDetailActivity.class)
                .putExtra("detail_mode", Setting.DETAIL_OPEN_PLAYER)
                .putExtra("fusion", false)
                .putExtra("auto_play", false)
                .putExtra("key", "instrumented")
                .putExtra("id", "instrumented-following")
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
        item.vodId = "instrumented-following";
        item.vodName = SHOW_NAME;
        item.trackedSeason = 0;
        item.nextCheckAt = Long.MAX_VALUE;
        FollowingStore.saveNew(item, null);
        assertNotNull("活跃行必须可被 find() 查到", FollowingStore.find(IDENTITY));
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
     * 再由 Room 异步回调覆盖为最终态。若提前返回就会读到中间态，让用例变成空断言
     * （实测：对未修复代码也会误判通过）。因此要求文字连续 {@link #STABLE_WINDOW_MS} 不变，
     * 且总等待不少于 {@link #MIN_SETTLE_MS}，才认为已落定。
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
