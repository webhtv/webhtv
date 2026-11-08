package com.fongmi.android.tv.ui.helper;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生增强播放页 TMDB 区块的纵向焦点链计算。
 *
 * 只把「可见且非空」的行纳入链条，其余行被完全跳过：某行没有数据（或标签与行一起被隐藏）时，
 * 它的上下邻居直接相连——剧照下面没有海报就落到相关视频，相关视频也没有就落到主创团队。
 * 这样焦点永远交给一个真实存在的行，而不是先跳到不可见的 View 再退回几何搜索。
 */
public final class TmdbRowFocusChain {

    /** 表示该方向没有邻居；调用方按上下文回退到列表首/末项或详情按钮。 */
    public static final int NO_NEIGHBOR = -1;

    private TmdbRowFocusChain() {
    }

    /**
     * 计算每个候选行的纵向邻居。
     *
     * @param present 每个候选行的「可见且非空」标记，数组下标顺序即屏幕上的视觉顺序
     * @return 与 {@code present} 等长的数组，第 i 项为 {@code {upIndex, downIndex}}；
     * 不参与链条的行返回 {@code {NO_NEIGHBOR, NO_NEIGHBOR}}
     */
    public static int[][] link(boolean[] present) {
        if (present == null) return new int[0][];
        int[][] result = new int[present.length][2];
        List<Integer> active = new ArrayList<>();
        for (int index = 0; index < present.length; index++) {
            // Java 的 int 默认值是 0，必须先铺上 NO_NEIGHBOR，
            // 否则被跳过的行会误报邻居为第 0 行。
            result[index][0] = NO_NEIGHBOR;
            result[index][1] = NO_NEIGHBOR;
            if (present[index]) active.add(index);
        }
        for (int position = 0; position < active.size(); position++) {
            int row = active.get(position);
            result[row][0] = position == 0 ? NO_NEIGHBOR : active.get(position - 1);
            result[row][1] = position == active.size() - 1 ? NO_NEIGHBOR : active.get(position + 1);
        }
        return result;
    }
}
