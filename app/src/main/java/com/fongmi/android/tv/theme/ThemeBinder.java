package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Controlled runtime channel for user theme overrides.
 *
 * <p>Android offers no public API to rewrite a compiled {@code ?attr/color*}
 * value, so the binder walks the already-created view tree and swaps only colors
 * that provably came from a semantic role: a view color is rewritten when it
 * exactly equals a frozen baseline role value and is not stateful. Everything
 * else - player surfaces, media, posters, brand and health colors, focus
 * selectors, unknown colors - is left untouched.
 *
 * <p>This runs on the main thread only, is a no-op when the active tokens equal
 * the baseline, and never touches a view more than once per token signature.
 */
public final class ThemeBinder {

    private static final String IGNORE_TAG = "webhtv:ignore";
    private static final String[] EXEMPT_CLASS_MARKERS = {
            "surface", "texture", "video", "player", "danmaku", "subtitle",
            "karaoke", "wall", "logo", "rating", "media",
    };

    /**
     * 唯一允许改写焦点态描边的「应用表面」selector 名字（见 {@link #isAppSurfaceFocusRing}）。
     *
     * <p>用资源名字而非 {@code R.drawable.*}：这些资源只存在于 leanback 源集，本类在 main 源集
     * 会被 mobile 变体一起编译，硬引用会让 mobile javac 失败。mobile 上解析为 0，白名单为空。
     *
     * <p>新增成员前必须**同时**满足三条，缺一不可：
     * <ol>
     *   <li>宿主画在调色板表面（不是视频画面或固定深色玻璃）；</li>
     *   <li>焦点态环色走 {@code ?attr/tvFocusRing}——<b>不能是 {@code ?attr/colorOnPrimary} 等
     *       「填充色的配对 on-色」</b>。后者是给「焦点态整块填充 {@code colorPrimary}」的控件用的，
     *       环与填充必须成对；把它们改写成本类写入的 {@code colorFocus()} 会让环与填充同色
     *       （默认色板里 focus == primary），焦点环直接消失。
     *       因此 {@code selector_config_history_item} / {@code selector_site_item} /
     *       {@code selector_group_button} 这类「primary 填充 + onPrimary 环」的**不在白名单**；</li>
     *   <li>焦点态宽度已是 {@code @dimen/webhtv_focus_ring_width}（本类无法读回旧宽度，只能按
     *       token 重写，宽度未收敛的族必须排除，否则会重新露出卡片圆角外的月牙）。</li>
     * </ol>
     */
    private static final String[] APP_SURFACE_FOCUS_RINGS = {
            "selector_item",
            "selector_item_round",
            "selector_vod",
            "selector_vod_oval",
            "selector_keyboard",
            "selector_search_hot_word",
            "selector_video_item",
            "selector_tv_search_field",
            "selector_empty_action",
    };

    /**
     * 装载 {@link #ITEM_OVERLAY_FILLS} 的宿主 selector：生产上这些 shape 都是作为它们的
     * {@code <item>} 子项被取到的。
     *
     * <p>为什么指纹必须同时收录**两条**取图路径：同一个 XML 在「直接 {@code getDrawable}」与
     * 「作为 {@code <selector>} 子项解析」两种上下文中，{@code ?attr/colorOutline} 这类属性可能解析到
     * 不同取值（实测 {@code shape_tv_search_field} 的常态子项在两条路径下栅格化结果不同，而
     * {@code shape_tv_search_field_focused} 因描边走 {@code ?attr/tvFocusRing} 恰好相同）。
     * 只收一条路径，另一条路径上的宿主就永远匹配不上、功能静默失效。
     */
    private static final String[] ITEM_OVERLAY_PARENTS = {
            "selector_item",
            "selector_item_round",
            "selector_search_hot_word",
            "selector_tv_search_field",
    };

    /**
     * 唯一允许改写**实心填充**的「条目遮罩」drawable 名字。
     *
     * <p>为什么需要一条独立的名单，而不是按颜色反查角色：这七个 drawable 的填充是
     * {@code @color/black_40}（{@code #66000000}），而这个取值在 leanback 里被 28 个 drawable
     * 共用——键盘键底（{@code shape_keyboard_normal}）、卡片信息条带（{@code shape_vod_name}）、
     * 控件底座（{@code shape_widget}）、实验区玻璃族都在用它。按颜色反查（{@link ThemeColorIndex}）
     * 会把它们一起改掉，而那几族正是「刻意固定在不可控底材上的半透明黑」，跟随主题就是可读性回归。
     * 因此这里只认资源名字，并且与焦环族一样用「签名 + 像素指纹」两级判定来确认身份。
     *
     * <p>名单只收编译期填充就是 {@code @color/black_40} 的那几个：它们接上遮罩槽后取值不变，
     * 接线本身不改变外观。常态底座（{@code shape_item_normal} 一族，填充 {@code @color/black_20}）
     * 是另一档强度，单独见 {@link #ITEM_OVERLAY_REST_FILLS}。
     * {@code shape_item_selected}（{@code black_30}）、{@code shape_keyboard_focused}
     * （{@code black_30}）仍然排除：它们是选中档/键底的固定取值，不是条目底座。
     *
     * <p>与 {@link #APP_SURFACE_FOCUS_RINGS} 一样按名字解析（{@code getIdentifier}）：这些资源只在
     * leanback 源集，本类在 main 源集会被 mobile 变体一起编译，硬引用 R.drawable.* 会让 mobile
     * javac 失败；mobile 上解析为 0，名单为空，行为与现状一致。
     */
    private static final String[] ITEM_OVERLAY_FILLS = {
            "shape_item_focused",
            "shape_item_activated",
            "shape_item_round_focused",
            "shape_item_round_activated",
            "shape_search_hot_word_focused",
            "shape_tv_search_field",
            "shape_tv_search_field_focused",
    };

    /**
     * 常态（未聚焦、未选中）的条目底座：{@code shape_item_normal} /
     * {@code shape_item_round_normal} / {@code shape_search_hot_word_normal}。
     *
     * <p>为什么要单独一张名单、单独一档强度：它们的编译期填充是 {@code @color/black_20}
     * （{@code #33000000}），恰好是遮罩底色 {@code @color/black_40}（{@code #66000000}）的
     * <b>一半 alpha</b>。把它们并进 {@link #ITEM_OVERLAY_FILLS} 后整块写入遮罩色，空档案下的
     * 默认外观就会从 20% 黑跳到 40% 黑——那是外观变化而不是主题化，违反「默认外观字节不变」。
     * 因此这一档按<b>半强度</b>跟随遮罩槽：默认 {@code 0x66 / 2 = 0x33} 与编译期取值逐位相等，
     * 用户改过槽位时又整体跟着走，「常态比聚焦态浅一半」的层次也不会丢。
     *
     * <p>与 {@link #ITEM_OVERLAY_FILLS} 分成两张表而不是一张表加权重字段：指纹集合必须按强度
     * 分开缓存，否则 {@code shape_item_normal}（{@code black_20}）与 {@code shape_item_focused}
     * （{@code black_40}）的指纹会落进同一格，判定时再无从区分强度。
     */
    private static final String[] ITEM_OVERLAY_REST_FILLS = {
            "shape_item_normal",
            "shape_item_round_normal",
            "shape_search_hot_word_normal",
    };

    /** 条目遮罩的两档强度（百分比 alpha）：全强度（聚焦/选中）与半强度（常态底座）。 */
    private static final int ITEM_OVERLAY_FULL = 100;
    private static final int ITEM_OVERLAY_REST = 50;

    /** 编译期条目遮罩填充 {@code @color/black_40}：全强度成员的身份判据。 */
    private static final int COMPILED_OVERLAY_FILL = 0x66000000;
    /** 编译期常态底座填充 {@code @color/black_20}：半强度成员的身份判据。 */
    private static final int COMPILED_REST_FILL = 0x33000000;

    private static final Map<View, Long> APPLIED = new WeakHashMap<>();
    /**
     * 视图的白名单判定缓存（命中与未命中都记）。只有「背景实例没换且环色 token 没变」时
     * 才复用结论——RecyclerView 会复用 item 视图，换 drawable 或换绑主题都必须重新判定。
     */
    private static final Map<View, RingDecision> RING_DECIDED = new WeakHashMap<>();
    /** {@link #ringColorToken} 解析不出环色时的哨兵值（不是合法 ARGB，与 0x00000000 区分）。 */
    private static final int RING_COLOR_UNSET = 0x01000000;

    /**
     * 一次白名单判定的输入与结论，用于判断缓存是否仍然适用。
     *
     * <p>背景实例用 {@link java.lang.ref.WeakReference} 引用：drawable 会把自己设为宿主的
     * callback，若值里强引用它，就等于「值 → 键」，{@link WeakHashMap} 的键永远不可达回收，
     * 长列表反复回收视图时会持续累积。
     */
    private record RingDecision(java.lang.ref.WeakReference<StateListDrawable> states, int ringColor, boolean member) {
    }

    /**
     * 每个 {@link Resources} 的白名单焦点环「签名 → 像素指纹」集合，避免每个视图都重复解析资源。
     * 指纹把描边也计入，用于排除"填充/圆角/内边距相同但描边不同"的资源（描边无公开 getter）。
     */
    private static final Map<Resources, Map<Integer, Map<String, java.util.Set<Long>>>> WHITELIST_FINGERPRINTS = new WeakHashMap<>();
    /**
     * 条目遮罩白名单的「签名 → 像素指纹」集合，按 {@code (Resources, 描边相关主题属性)} 解析一次后缓存。
     *
     * <p>缓存键与 {@link #whitelistFingerprints} 同源：白名单成员带描边，而描边色由视图主题解析
     * （{@code ?attr/tvFocusRing} / {@code ?attr/colorOutline}）。只按 {@link Resources} 做键会让结果
     * 与首次调用顺序绑定——先到的主题写入，后到的主题永远拿不到自己的指纹，功能静默失效。
     */
    private static final Map<Resources, Map<Integer, Map<Integer, Map<String, java.util.Set<Long>>>>>
            ITEM_OVERLAY_FINGERPRINTS = new WeakHashMap<>();
    /**
     * 每个 {@link GradientDrawable} 实例的白名单归属结论。
     *
     * <p>为什么要按**实例**记忆而不再每次重算：判定用像素指纹，而指纹里必然包含填充色
     * （{@code shape_item_focused} 与 {@code shape_item_selected} 的圆角、内边距、焦点环完全
     * 相同，只有填充 {@code black_40} vs {@code black_30} 不同——漏掉填充就会误改后者）。本类自己
     * 会把成员的填充改写成用户槽位的颜色，于是第二轮判定时它已经「不像自己」了。实例不变，
     * 归属就不变，所以记住第一次（尚未被本类改写时）的结论即可；只有背景实例被换掉
     * （RecyclerView 复用、重新充气）才会重新判定。
     */
    private static final Map<GradientDrawable, ItemOverlayDecision> ITEM_OVERLAY_MEMBER = new WeakHashMap<>();

    /**
     * 一次条目遮罩归属判定的输入与结论（与 {@link RingDecision} 同一用意：结论必须可失效）。
     *
     * <p>{@code weight} 是强度而不是布尔值：{@link #ITEM_OVERLAY_FULL}（填充写整块遮罩色）/
     * {@link #ITEM_OVERLAY_REST}（填充写半强度，见 {@link #ITEM_OVERLAY_REST_FILLS}）/ {@code 0}
     * （不是成员）。用强度一档表达，判定的缓存键与焦点环那条路径保持同构。
     */
    private record ItemOverlayDecision(int strokeKey, int weight) {
    }
    /** 指纹位图边长（px）：足够容纳描边差异，又足够便宜。 */
    private static final int FINGERPRINT_SIZE = 48;
    private static final Map<RecyclerView, RecyclerView.OnChildAttachStateChangeListener> WATCHED = new WeakHashMap<>();
    private static final Map<View, int[]> ROOTS = new WeakHashMap<>();
    private static final Map<ColorStateList, ColorStateList> REBUILT = new WeakHashMap<>();

    private static long cachedSignature;

    private static volatile long lastBindMillis;
    private static volatile int lastBoundViews;
    private static volatile int lastWalkedViews;

    private ThemeBinder() {
    }

    /** Elapsed time of the most recent non-trivial bind, for the performance gate. */
    public static long lastBindMillis() {
        return lastBindMillis;
    }

    /** Number of views rewritten by the most recent non-trivial bind. */
    public static int lastBoundViews() {
        return lastBoundViews;
    }

    public static void bind(View root, ThemeTokens baseline, ThemeTokens active) {
        if (root == null || baseline == null || active == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        if (baseline.equals(active)) {
            // Default profile: the static Layer 1 theme is already correct, so the
            // binder must cost nothing and change nothing.
            lastBindMillis = 0L;
            lastBoundViews = 0;
            return;
        }
        long started = SystemClock.elapsedRealtimeNanos();
        long signature = signature(active);
        if (signature != cachedSignature) {
            REBUILT.clear();
            cachedSignature = signature;
        }
        ThemeColorIndex index = ThemeColorIndex.of(baseline);
        // 选中文字色只有「真的被档案改过」才往下传：空档案与只改了别的槽位的档案都必须
        // 逐位保持编译期文字色，否则夜间表（on_item_overlay = #062E6F，而条目宿主实际文字
        // 是 on_wallpaper = #FFFFFF）会在用户仅调整无关槽位时突然变深。
        int overlayText = active.colorOnItemOverlay() == baseline.colorOnItemOverlay() ? 0 : active.colorOnItemOverlay();
        int bound = walk(root, index, active, signature, overlayText);
        watchRoot(root, index, active, signature, overlayText);
        lastBoundViews = bound;
        lastBindMillis = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L;
    }

    private static int walk(View root, ThemeColorIndex index, ThemeTokens active, long signature, int overlayText) {
        int bound = 0;
        int walked = 0;
        Deque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view == null) continue;
            walked++;
            if (isExempt(view)) continue;
            if (bindView(view, index, active, signature, overlayText)) bound++;
            if (view instanceof RecyclerView recyclerView) watchChildren(recyclerView, index, active, signature, overlayText);
            if (view instanceof ViewGroup group) {
                for (int child = 0; child < group.getChildCount(); child++) {
                    queue.add(group.getChildAt(child));
                }
            }
        }
        lastWalkedViews = walked;
        return bound;
    }

    /**
     * Fragments and async content attach after the Activity bind points. The root
     * is re-walked only when its descendant count actually changed, which keeps the
     * scroll path free of repeated traversals.
     */
    private static void watchRoot(View root, ThemeColorIndex index, ThemeTokens active, long signature, int overlayText) {
        if (ROOTS.containsKey(root)) return;
        int[] state = {countDescendants(root)};
        ROOTS.put(root, state);
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (state[0] == countDescendants(view)) return;
            state[0] = countDescendants(view);
            walk(view, index, active, signature, overlayText);
        });
    }

    private static int countDescendants(View view) {
        if (!(view instanceof ViewGroup group)) return 1;
        int count = 1;
        for (int child = 0; child < group.getChildCount(); child++) count += countDescendants(group.getChildAt(child));
        return count;
    }

    private static void watchChildren(RecyclerView recyclerView, ThemeColorIndex index, ThemeTokens active, long signature, int overlayText) {
        if (WATCHED.containsKey(recyclerView)) return;
        RecyclerView.OnChildAttachStateChangeListener listener = new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                walk(view, index, active, signature, overlayText);
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                APPLIED.remove(view);
            }
        };
        WATCHED.put(recyclerView, listener);
        recyclerView.addOnChildAttachStateChangeListener(listener);
    }

    private static boolean bindView(View view, ThemeColorIndex index, ThemeTokens active, long signature, int overlayText) {
        Long applied = APPLIED.get(view);
        if (applied != null && applied == signature) return false;
        ThemeRole explicit = ThemeRole.fromTag(view.getTag());
        boolean changed = false;
        if (view instanceof TextView textView) changed |= bindText(textView, explicit, index, active, overlayText);
        if (view instanceof ImageView imageView) changed |= bindImageTint(imageView, explicit, index, active);
        if (view instanceof MaterialCardView cardView) changed |= bindCard(cardView, explicit, index, active);
        if (view instanceof MaterialButton button) changed |= bindButton(button, explicit, index, active);
        changed |= bindBackgroundTint(view, explicit, index, active);
        changed |= bindBackground(view, explicit, index, active);
        changed |= bindForeground(view, explicit, index, active);
        if (changed) APPLIED.put(view, signature);
        return changed;
    }

    private static boolean bindText(TextView view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active, int overlayText) {
        boolean changed = false;
        ColorStateList colors = rewrite(view.getTextColors(), explicit, index, active);
        if (colors != null) {
            view.setTextColor(colors);
            changed = true;
        }
        ColorStateList hint = rewrite(view.getHintTextColors(), explicit, index, active);
        if (hint != null) {
            view.setHintTextColor(hint);
            changed = true;
        }
        // 选中文字色排在角色改写之后：两者都可能落在同一个 TextView 上，遮罩宿主的文字
        // 语义优先（它压在遮罩上，而不是压在调色板表面）。
        if (overlayText != 0 && sitsOnItemOverlay(view)) {
            ColorStateList overlayColors = rewriteOverlayText(view.getTextColors(), overlayText);
            if (overlayColors != null) {
                view.setTextColor(overlayColors);
                changed = true;
            }
            ColorStateList overlayHint = rewriteOverlayText(view.getHintTextColors(), overlayText);
            if (overlayHint != null) {
                view.setHintTextColor(overlayHint);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 判定一段文字是不是画在条目遮罩上：自身是遮罩宿主，或自身无背景而**直接**父容器是遮罩宿主。
     *
     * <p>为什么要看父容器：电视首页的功能按钮行（{@code adapter_func.xml}）、设置页条目
     * （{@code activity_setting.xml}）、搜索页范围按钮（{@code activity_search.xml}）都是
     * 「容器挂 {@code selector_item}、文字是子 {@code TextView}」的形状——只认自身背景会漏掉
     * 它们中的绝大多数。只向上看一层，不递归到任意祖先：再往上就可能走出条目、落进整页背景，
     * 那时「文字压在遮罩上」的前提已经不成立。
     *
     * <p>判定复用{@link #itemOverlayFillWeight}那条填充白名单，而**不是**按颜色反查
     * {@code @color/black_40}：{@code black_40} 被 28 个 drawable 共用，按色反查会连带把
     * 键盘键底、搜索框与玻璃面板的文字一起改掉。名单外一律返回 false。
     */
    private static boolean sitsOnItemOverlay(View view) {
        if (isItemOverlayHost(view)) return true;
        if (view.getBackground() != null) return false;
        return view.getParent() instanceof View parent && isItemOverlayHost(parent);
    }

    /** 背景是不是条目 selector，且至少一个状态子项命中遮罩白名单。 */
    private static boolean isItemOverlayHost(View view) {
        Drawable background = unwrap(view.getBackground());
        if (!(background instanceof StateListDrawable states)) return false;
        for (int i = 0; i < states.getStateCount(); i++) {
            Drawable child = unwrap(states.getStateDrawable(i));
            if (child instanceof GradientDrawable shape && itemOverlayFillWeight(view, shape) != 0) return true;
        }
        return false;
    }

    /**
     * 把条目宿主文字色里的选中/聚焦态换成用户设定的遮罩文字色，其余状态保持原值。
     *
     * <p>为什么必须在运行时做：条目宿主的文字在 XML 里直接写 {@code ?attr/webhtvColorOnWallpaper}
     * （单色、无状态），仓库里没有任何「条目宿主 + {@code ?attr/colorOnPrimary}」的颜色状态列表
     * 可改——引擎在 layout 里直接写字面属性，绕开了 {@code res/color/} 那一层。反过来硬改
     * {@code home_menu_text} / {@code site_item_text} 会把 colorPrimary 实心底上的文字换掉，
     * 那些文件的注释已说明必须用 colorOnPrimary，改了就是对比度回归。
     */
    private static ColorStateList rewriteOverlayText(ColorStateList source, int color) {
        if (source == null || color == 0) return null;
        if (!source.isStateful()) {
            return source.getDefaultColor() == color ? null : ColorStateList.valueOf(color);
        }
        int[][] states = ThemeColorIndex.stateSkeleton();
        int[] colors = new int[states.length];
        boolean changed = false;
        for (int position = 0; position < states.length; position++) {
            int original = resolvedColor(source, states[position]);
            boolean onOverlay = false;
            for (int state : states[position]) {
                if (state == android.R.attr.state_focused || state == android.R.attr.state_selected) onOverlay = true;
            }
            colors[position] = onOverlay ? color : original;
            changed |= onOverlay;
        }
        return changed ? new ColorStateList(states, colors) : null;
    }

    private static boolean bindImageTint(ImageView view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        ColorStateList tint = rewrite(view.getImageTintList(), explicit, index, active);
        if (tint == null) return false;
        view.setImageTintList(tint);
        return true;
    }

    private static boolean bindCard(MaterialCardView view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        boolean changed = false;
        ColorStateList background = rewrite(view.getCardBackgroundColor(), explicit, index, active);
        if (background != null) {
            view.setCardBackgroundColor(background);
            changed = true;
        }
        ColorStateList stroke = rewrite(ColorStateList.valueOf(view.getStrokeColor()),
                index.uniqueRoleFor(view.getStrokeColor()) == null ? ThemeRole.OUTLINE : null, index, active);
        if (stroke != null) {
            view.setStrokeColor(stroke);
            changed = true;
        }
        return changed;
    }

    /**
     * Filled and outlined Material buttons keep their fill in a background tint and
     * their border in a stroke colour rather than in a GradientDrawable, so the
     * generic drawable path never reached them and a themed button stayed on the
     * compiled palette. Both channels are still resolved from the same semantic
     * attributes and obey the identical exact-match rule.
     */
    private static boolean bindButton(MaterialButton view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        boolean changed = false;
        ColorStateList stroke = rewrite(view.getStrokeColor(), explicit, index, active);
        if (stroke != null) {
            view.setStrokeColor(stroke);
            changed = true;
        }
        ColorStateList icon = rewrite(view.getIconTint(), explicit, index, active);
        if (icon != null) {
            view.setIconTint(icon);
            changed = true;
        }
        return changed;
    }

    /**
     * Covers every view that fills itself through {@code app:backgroundTint} - the
     * Material button family, chips, FABs and text-input boxes - with the same
     * "only an exact baseline role colour" guard the rest of the binder uses.
     */
    private static boolean bindBackgroundTint(View view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        ColorStateList tint = rewrite(view.getBackgroundTintList(), explicit, index, active);
        if (tint == null) return false;
        view.setBackgroundTintList(tint);
        return true;
    }

    private static boolean bindBackground(View view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        return bindDrawable(view.getBackground(), explicit, index, active, view);
    }

    /**
     * Rewrites a view's {@code android:foreground} drawable.
     *
     * <p>电视卡片把焦点环画在 foreground 而不是 background：{@code selector_vod} /
     * {@code selector_vod_oval} / {@code selector_episode_card} 等都以
     * {@code android:foreground} 覆盖在内容之上，因此只走 background 的旧路径永远碰不到
     * 卡片族的焦点环，用户改「焦点色」时它们恒为原始颜色。
     */
    private static boolean bindForeground(View view, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        return bindDrawable(view.getForeground(), explicit, index, active, view);
    }

    /**
     * Rewrites a window-level background drawable.
     *
     * <p>{@code MaterialAlertDialogBuilder} builds the dialog panel as a
     * {@link MaterialShapeDrawable} wrapped in an {@link InsetDrawable} and hands it to
     * {@code Window.setBackgroundDrawable}. That drawable belongs to the window, not to
     * any {@code View}, so the ordinary view-tree walk can never reach it - which is why
     * a themed dialog changed its text but kept the compiled panel colour. The builder
     * exposes the very same instance through {@code getBackground()}, so it is rewritten
     * here through one shared code path instead of a second colour model.
     */
    public static boolean bindWindowBackground(Drawable drawable, ThemeTokens baseline, ThemeTokens active) {
        if (drawable == null || baseline == null || active == null) return false;
        if (Looper.myLooper() != Looper.getMainLooper()) return false;
        if (baseline.equals(active)) return false;
        // A window background belongs to no View, so there is no theme to resolve a ring
        // against; the 4-argument overload keeps the fill-only path this entry point has
        // always had.
        boolean changed = bindDrawable(drawable, null, ThemeColorIndex.of(baseline), active);
        // dialogOpacity is documented as "the dialog/BottomSheet shell only, never the text
        // alpha", so it is applied to this window background and nowhere else. Without this
        // the editor's dialog-opacity slider changed only the preview swatch and the Web
        // snapshot while real dialogs stayed fully opaque.
        changed |= applyShellOpacity(drawable, active.dialogOpacity());
        return changed;
    }

    /**
     * Scales the alpha of a window background fill, preserving whatever transparency the
     * drawable already had. A shell opacity of 1.0 is the shipped default and must stay a
     * strict no-op so the frozen palette path remains byte-identical.
     */
    private static boolean applyShellOpacity(Drawable drawable, float opacity) {
        if (drawable == null || opacity >= 1f) return false;
        if (opacity < 0f) return false;
        if (drawable instanceof InsetDrawable inset) {
            return applyShellOpacity(inset.getDrawable(), opacity);
        }
        if (drawable instanceof MaterialShapeDrawable shape) {
            ColorStateList fill = shape.getFillColor();
            if (fill == null) return false;
            int original = fill.getDefaultColor();
            int target = scaleAlpha(original, opacity);
            if (target == original) return false;
            shape.setFillColor(ColorStateList.valueOf(target));
            return true;
        }
        if (drawable instanceof GradientDrawable shape) {
            ColorStateList color = shape.getColor();
            if (color == null) return false;
            int original = color.getDefaultColor();
            int target = scaleAlpha(original, opacity);
            if (target == original) return false;
            shape.setColor(target);
            return true;
        }
        return false;
    }

    /** Package-private so the alpha arithmetic can be asserted without an Android device. */
    static int scaleAlpha(int color, float opacity) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, opacity)));
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /**
     * Rewrites the solid fill of a background drawable, unwrapping the transparent
     * wrappers Android and Material put around real panels.
     *
     * <p>Two families are covered by the same exact-match rule as everything else:
     * {@link GradientDrawable} (classic XML shapes) and
     * {@link MaterialShapeDrawable} (Chip, text-input box, bottom sheet and the
     * AlertDialog panel). Material's alert background is additionally wrapped in an
     * {@link InsetDrawable} carrying the window insets, so wrappers are unwrapped
     * recursively instead of being treated as an unknown drawable.
     */
    private static boolean bindDrawable(Drawable drawable, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        return bindDrawable(drawable, explicit, index, active, null);
    }

    /**
     * Same pass, with the owning View available so a focus-ring selector can be resolved
     * against that view's own theme.
     */
    private static boolean bindDrawable(Drawable drawable, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active, View owner) {
        if (drawable == null) return false;
        if (drawable instanceof InsetDrawable inset) {
            return bindDrawable(inset.getDrawable(), explicit, index, active, owner);
        }
        if (drawable instanceof DrawableWrapper wrapper) {
            return bindDrawable(wrapper.getDrawable(), explicit, index, active, owner);
        }
        if (owner != null && drawable instanceof StateListDrawable states) {
            return bindStateList(owner, states, explicit, active);
        }
        if (drawable instanceof GradientDrawable shape) {
            // 条目遮罩白名单必须先判：成员完全由用户槽位拥有，不允许再落到「按基线色反查角色」
            // 那条通用路径上——否则用户把遮罩设成某个角色色时，通用路径会按角色表改写它。
            if (owner != null) {
                int weight = itemOverlayFillWeight(owner, shape);
                if (weight != 0) {
                    return setItemOverlayFill(shape, overlayFillColor(active.colorItemOverlay(), weight));
                }
            }
            ColorStateList color = rewrite(shape.getColor(), explicit, index, active);
            if (color == null) return false;
            shape.setColor(color);
            return true;
        }
        if (drawable instanceof MaterialShapeDrawable shape) {
            ColorStateList color = rewrite(shape.getFillColor(), explicit, index, active);
            if (color == null) return false;
            shape.setFillColor(color);
            return true;
        }
        return false;
    }

    /**
     * 一次状态列表上的两条白名单改写（焦点环 + 条目遮罩）。
     *
     * <p>两条判定**都必须在本方法改写任何东西之前**完成，因为两者的身份判据都取自 drawable 的
     * 原始外观，而各自的改写会破坏对方的判据：
     * <ul>
     *   <li>焦点环改写 {@code setStroke} 会改掉描边色，而遮罩族的像素指纹里含描边；</li>
     *   <li>遮罩改写 {@code setColor} 会改掉填充，而环族的形状签名里含填充。</li>
     * </ul>
     * 先判后改，两边结论就都与调用顺序无关。
     */
    private static boolean bindStateList(View view, StateListDrawable states, ThemeRole explicit, ThemeTokens active) {
        int ringColor = active.colorFocus();
        boolean ringMember = ringColor != 0
                && (explicit == ThemeRole.FOCUS || isAppSurfaceFocusRing(view, states));
        java.util.Map<Integer, Integer> overlays = active.colorItemOverlay() == 0
                ? java.util.Map.of() : itemOverlayStates(view, states);
        if (!ringMember && overlays.isEmpty()) return false;
        boolean changed = false;
        if (ringMember) changed |= bindFocusRingStates(states, view, ringColor);
        changed |= applyItemOverlay(states, overlays, active.colorItemOverlay());
        return changed;
    }

    /**
     * 把 {@code states} 里每一个子项的实心填充改写成用户选定的条目遮罩色。
     *
     * <p>覆盖**全部**状态子项而不是只有 focused：{@code selector_item} 的 selected 分支是
     * {@code shape_item_activated}，它同样是「压在条目上的那层半透明深色」，用户改遮罩槽时必须
     * 一起变，否则同一行按钮在聚焦态和选中态会露出两种底色。normal 分支
     * （{@code shape_item_normal}）按<b>半强度</b>写入（见 {@link #ITEM_OVERLAY_REST_FILLS}）：
     * 它的编译期填充是 {@code black_20}，整块写入会让默认外观从 20% 黑跳到 40% 黑，而半强度下
     * 默认取值 {@code 0x33} 与编译期逐位相同。
     */
    private static boolean applyItemOverlay(StateListDrawable states, java.util.Map<Integer, Integer> overlays, int color) {
        if (overlays.isEmpty() || color == 0) return false;
        // 容器先 mutate：子 drawable 可能与其他视图共享 ConstantState，不先切断会让首个宿主的
        // 改写连带影响其它宿主（与 setRingStroke 路径同一理由）。注意 mutate 之后必须**按索引重新
        // 取子 drawable**：StateListDrawable.mutate() 会逐个 mutate 子项，而被 mutate 过的
        // GradientDrawable 不再保证还是先前那个实例，抓住旧引用会静默漏改（实测：聚焦态漏改、
        // 选中态改对了，因为只有前者先被焦点环路径 mutate 过）。
        states.mutate();
        boolean changed = false;
        for (java.util.Map.Entry<Integer, Integer> entry : overlays.entrySet()) {
            changed |= setItemOverlayFill(states.getStateDrawable(entry.getKey()),
                    overlayFillColor(color, entry.getValue()));
        }
        return changed;
    }

    /**
     * 判定 {@code states} 里哪些状态子项属于条目遮罩白名单，以及各自属于哪一档强度。
     *
     * <p>判定在 {@code mutate()} 之前进行：填充在指纹里，被本类改写之后就认不出来了。
     * 返回「子项下标 → 强度」的映射，正常态子项因此在同一个宿主里拿到与聚焦态不同的强度。
     */
    private static java.util.Map<Integer, Integer> itemOverlayStates(View view, StateListDrawable states) {
        java.util.Map<Integer, Integer> members = new java.util.LinkedHashMap<>();
        for (int i = 0; i < states.getStateCount(); i++) {
            Drawable child = unwrap(states.getStateDrawable(i));
            if (!(child instanceof GradientDrawable shape)) continue;
            int weight = itemOverlayFillWeight(view, shape);
            if (weight != 0) members.put(i, weight);
        }
        return members;
    }

    /**
     * 应用焦点环：只改 {@code state_focused} 子项的描边。
     *
     * <p><b>调用方必须先完成白名单判定</b>（见 {@link #bindStateList}），因为本方法会
     * {@code mutate()} 并改掉描边，之后像素指纹就不再匹配，任何后置判定都会失效。
     *
     * <p>环宽按唯一 token {@code @dimen/webhtv_focus_ring_width} 重写：{@code GradientDrawable}
     * 没有公开的 stroke getter，而白名单内每个 selector 的焦点态宽度本就等于该 token，
     * 重写同一 token 是无变化，真正变化的只有环色。
     */
    private static boolean bindFocusRingStates(StateListDrawable states, View view, int color) {
        if (color == 0) return false;
        boolean changed = false;
        // 容器先 mutate：StateListDrawable 的子 drawable 可能与其他视图共享 ConstantState，
        // 不先切断会让首个宿主的改写连带影响其它宿主。
        states.mutate();
        for (int i = 0; i < states.getStateCount(); i++) {
            int[] stateSet = states.getStateSet(i);
            boolean focusedState = false;
            for (int state : stateSet) if (state == android.R.attr.state_focused) focusedState = true;
            if (!focusedState) continue;
            if (setRingStroke(states.getStateDrawable(i), view, color)) changed = true;
        }
        return changed;
    }

    /**
     * Whether {@code states} is one of the whitelisted app-surface focus-ring selectors.
     *
     * <p>判定走「焦点环公开签名」比对（见 {@link #ringSignature}）。注意必须在
     * {@code mutate()} **之前**调用：{@code mutate()} 会就地改写子 drawable 的填充/描边，
     * 签名随即变化，之后再比就永远不匹配了。
     *
     * <p>白名单之外还有一道**主题前置判定**：视图自身主题解析出的 {@code ?attr/tvFocusRing}
     * 必须就是 {@code R.color.tv_item_focus_ring}。原因是白名单里的
     * {@code selector_video_item} 是**双宿主**资源——选集芯片在普通页面用它，而
     * {@code dialog_episode_list} / {@code dialog_quick_search} 两处固定深色玻璃对话框的根节点
     * 用 {@code @style/ThemeOverlay.WebHTV.GlassFocusRings} 把 {@code tvFocusRing} 重绑到了
     * {@code tv_player_focus_ring}。玻璃子树里只看内容签名会命中并改写成 {@code colorFocus()}
     * （浅色表深蓝 {@code #0B57D0} 贴在深靛玻璃上仅 1.83:1，等于不可见）。
     *
     * <p>这条判据单独使用会**恒真**（所有相关主题都把 {@code tvFocusRing} 绑到该资源），
     * 所以只能作为白名单的必要条件（AND），用来把玻璃子树挡在外面。
     *
     * <p>白名单本身为什么是**资源名字**而不是 {@code R.drawable.*}：这些 selector 只存在于
     * {@code app/src/leanback/res}，而本类位于 {@code app/src/main}（两个 flavor 共享），
     * 直接引用 {@code R.drawable.selector_item} 会让 mobile 变体的 javac 直接失败
     * （与 {@code DanmakuDialog} 的 {@code @id/loadHost} 泄漏同属一类）。因此按名字解析：
     * mobile 上解析为 0 → 白名单为空 → 一律不改写，行为与该 flavor 现状一致。
     *
     * <p>白名单之外必须排除的族，以及它们为什么不能跟随主题：
     * <ul>
     *   <li>使用 {@code ?attr/tvPlayerRing} 的固定深色玻璃族（{@code selector_glass_icon_action}、
     *       {@code selector_control_sheet_button}、{@code shape_subtitle_focused/pressed}）——
     *       浅色表 FOCUS 深蓝 {@code #0B57D0} 画在深靛玻璃上只有 1.83:1，改了等于不可见；</li>
     *   <li>焦点态宽度尚未收敛到 token 的 3dp 卡片族（{@code selector_episode_card} 等）——
     *       它们的声明圆角是按「宿主圆角 − 环宽/2」以 3dp 推导并经栅格化验算的，改宽度会重新
     *       露出「卡片圆角外的月牙」；</li>
     *   <li>本来就没有描边的焦点态（如实心填充 + layer-list 的图标按钮）——改了就凭空多出一圈环。</li>
     * </ul>
     * 白名单内每个 selector 的焦点态宽度都已经是 {@code @dimen/webhtv_focus_ring_width}
     * （由 {@code TvFocusRingContractTest} 钉住），因此读不到旧宽度也没有关系。
     */
    private static boolean isAppSurfaceFocusRing(View view, StateListDrawable states) {
        if (view == null || states == null) return false;
        // 缓存只对「同一个背景实例 + 同一个传入主题」有效：RecyclerView 会复用 item 视图，
        // 描边改写或换绑主题都会让先前的结论失效。
        Context context = view.getContext();
        if (context == null) return false;
        int ringColor = ringColorToken(context);
        if (ringColor == RING_COLOR_UNSET) return false;
        RingDecision cached = RING_DECIDED.get(view);
        if (cached != null && cached.ringColor() == ringColor && cached.states().get() == states) {
            return cached.member();
        }
        // 先做主题前置判定：玻璃子树会把 tvFocusRing 重绑到视频层环色（浅色表 #447BF5），
        // 那里必须保持原样，否则深蓝环贴固定深色玻璃不可见（实测 1.83:1）。
        boolean member = ringColor == context.getResources().getColor(R.color.tv_item_focus_ring, null)
                && matchesWhitelist(context, states);
        RING_DECIDED.put(view, new RingDecision(new java.lang.ref.WeakReference<>(states), ringColor, member));
        return member;
    }

    /** 视图主题解析出的 {@code ?attr/tvFocusRing} 颜色；解析不出时返回 {@link #RING_COLOR_UNSET}。 */
    private static int ringColorToken(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        try {
            if (!context.getTheme().resolveAttribute(R.attr.tvFocusRing, value, true)) return RING_COLOR_UNSET;
            if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) return value.data;
            if (value.resourceId != 0) return context.getResources().getColor(value.resourceId, null);
        } catch (RuntimeException ignored) {
            return RING_COLOR_UNSET;
        }
        return RING_COLOR_UNSET;
    }

    /**
     * 焦点环的公开可读签名：填充色 + 圆角 + 内边距 + 是否椭圆。
     *
     * <p>为什么用内容签名而不是 {@code ConstantState} 身份比较：身份比较依赖
     * {@code Resources} 对同一 (id, 主题) 复用同一个 ConstantState，这在真机上成立，但在
     * Robolectric 下不成立（每次 {@code getDrawable} 都可能新建 ConstantState），于是守卫测试
     * 无法验证正向路径。签名只用 {@link GradientDrawable} 的公开 getter。
     *
     * <p><b>必须包含填充色</b>：{@code shape_search_hot_word_focused} 与
     * {@code selector_glass_icon_action} / {@code shape_subtitle_*} 同为 8dp 圆角、内边距相近，
     * 只有实心色不同（{@code black_40} vs {@code white_15}）；漏掉颜色就会把固定深色玻璃族
     * 误判成应用表面族并改写成 colorFocus（浅色表在深靛玻璃上 1.83:1，等于不可见）。
     */
    private static String ringSignature(Drawable drawable) {
        if (drawable instanceof DrawableWrapper wrapper) return ringSignature(wrapper.getDrawable());
        if (!(drawable instanceof GradientDrawable shape)) return null;
        ColorStateList fill = shape.getColor();
        Rect padding = new Rect();
        shape.getPadding(padding);
        float radius = shape.getCornerRadius();
        boolean oval = shape.getShape() == GradientDrawable.OVAL;
        return (fill == null ? "null" : Integer.toHexString(fill.getDefaultColor()))
                + '|' + (oval ? "oval" : Float.toString(radius))
                + '|' + padding.left + ',' + padding.top + ',' + padding.right + ',' + padding.bottom;
    }

    /** 焦点态子项的签名；找不到焦点态时返回 null（这类 selector 本就不该被改写）。 */
    private static String focusedRingSignature(StateListDrawable states) {
        for (int i = 0; i < states.getStateCount(); i++) {
            for (int state : states.getStateSet(i)) {
                if (state != android.R.attr.state_focused) continue;
                String signature = ringSignature(states.getStateDrawable(i));
                if (signature != null) return signature;
            }
        }
        return null;
    }

    private static boolean matchesWhitelist(Context context, StateListDrawable states) {
        String probe = focusedRingSignature(states);
        if (probe == null) return false;
        // 两级判定：先用廉价签名做预筛（绝大多数视图在这里就返回 false，不做任何绘制），
        // 只有签名命中时才做一次小位图指纹确认。
        //
        // 为什么还需要第二级：描边宽度/颜色没有公开 getter，而
        // {@code selector_lab_icon_btn} 的焦点态与 {@code shape_search_hot_word_focused}
        // 填充/圆角/内边距完全相同（black_40 + 8dp + 无内边距），只有描边不同
        //（2dp {@code @color/accent} vs token {@code ?attr/tvFocusRing}）。位图指纹把描边也
        // 算进去，因此能把这类"签名相同但描边不同"的资源排除掉。
        java.util.Set<Long> expected = whitelistFingerprints(context, ringColorToken(context)).get(probe);
        if (expected == null) return false;
        Long actual = ringFingerprint(states);
        return actual != null && expected.contains(actual);
    }

    /** 焦点态栅格化后的像素指纹；拿不到可复制实例时返回 null（此时按不匹配处理）。 */
    private static Long ringFingerprint(StateListDrawable states) {
        Drawable focused = null;
        for (int i = 0; i < states.getStateCount(); i++) {
            for (int state : states.getStateSet(i)) {
                if (state == android.R.attr.state_focused) focused = states.getStateDrawable(i);
            }
            if (focused != null) break;
        }
        if (focused == null) return null;
        // 复制一份再绘制：直接给共享实例 setBounds 会污染其它宿主。
        Drawable.ConstantState state = focused.getConstantState();
        if (state == null) return null;
        Drawable copy = state.newDrawable();
        if (copy == null) return null;
        return fingerprint(copy);
    }

    private static Long fingerprint(Drawable drawable) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                FINGERPRINT_SIZE, FINGERPRINT_SIZE, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, FINGERPRINT_SIZE, FINGERPRINT_SIZE);
            drawable.draw(new android.graphics.Canvas(bitmap));
            long hash = 1125899906842597L;
            for (int y = 0; y < FINGERPRINT_SIZE; y++) {
                for (int x = 0; x < FINGERPRINT_SIZE; x++) {
                    hash = 31 * hash + bitmap.getPixel(x, y);
                }
            }
            return hash;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * 白名单每个签名对应的像素指纹集合，按 {@code (Resources, 环色 token)} 解析一次后缓存。
     *
     * <p>缓存键**必须**带上环色：{@code ContextThemeWrapper.getResources()} 返回的是同一个
     * {@link Resources} 实例，但白名单资源的描边色取决于解析主题（应用表面 {@code #0B57D0}
     * vs 玻璃子树 {@code #447BF5}）。只按 {@code Resources} 做键会让结果与首次调用顺序绑定：
     * 先到者写入，后到者永远拿不到自己的指纹，功能静默失效。
     */
    private static Map<String, java.util.Set<Long>> whitelistFingerprints(Context context, int ringColor) {
        Resources resources = context.getResources();
        Map<Integer, Map<String, java.util.Set<Long>>> byRing = WHITELIST_FINGERPRINTS.get(resources);
        if (byRing == null) {
            byRing = new java.util.HashMap<>();
            WHITELIST_FINGERPRINTS.put(resources, byRing);
        }
        Map<String, java.util.Set<Long>> cached = byRing.get(ringColor);
        if (cached != null) return cached;
        Map<String, java.util.Set<Long>> resolved = new java.util.HashMap<>();
        for (String name : APP_SURFACE_FOCUS_RINGS) {
            int id = resources.getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) continue;
            Drawable candidate;
            try {
                candidate = context.getDrawable(id);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!(candidate instanceof StateListDrawable)) continue;
            // 用原始实例取签名与指纹（指纹内部自己复制，不污染原实例）。
            String signature = focusedRingSignature((StateListDrawable) candidate);
            Long print = ringFingerprint((StateListDrawable) candidate);
            if (signature == null || print == null) continue;
            resolved.computeIfAbsent(signature, key -> new java.util.HashSet<>()).add(print);
        }
        byRing.put(ringColor, resolved);
        return resolved;
    }

    // ------------------------------------------------------------------ 条目遮罩（ITEM_OVERLAY）

    /** 取到真正持有填充的那层 drawable（selector 子项可能是 InsetDrawable/包装器）。 */
    private static Drawable unwrap(Drawable drawable) {
        if (drawable instanceof DrawableWrapper wrapper) return unwrap(wrapper.getDrawable());
        return drawable;
    }

    /**
     * Writes the user's overlay colour into one drawable's fill.
     *
     * <p>只用公开方法（{@code mutate} + {@code setColor}）：{@code GradientDrawable} 没有公开的
     * {@code getStroke*}，但填充有公开 getter，因此这里不需要重写任何读不到的量。
     */
    private static boolean setItemOverlayFill(Drawable drawable, int color) {
        if (color == 0) return false;
        if (drawable instanceof InsetDrawable inset) return setItemOverlayFill(inset.getDrawable(), color);
        if (drawable instanceof DrawableWrapper wrapper) return setItemOverlayFill(wrapper.getDrawable(), color);
        if (drawable instanceof GradientDrawable shape) {
            ColorStateList current = shape.getColor();
            if (current != null && current.getDefaultColor() == color) return false;
            shape.mutate();
            shape.setColor(color);
            return true;
        }
        if (drawable instanceof MaterialShapeDrawable shape) {
            ColorStateList current = shape.getFillColor();
            if (current != null && current.getDefaultColor() == color) return false;
            shape.setFillColor(ColorStateList.valueOf(color));
            return true;
        }
        return false;
    }

    /**
     * 判定 {@code shape} 属于条目遮罩白名单的哪一档强度，不属于时返回 {@code 0}。
     *
     * <p>返回值是强度而不是布尔值（{@link #ITEM_OVERLAY_FULL} / {@link #ITEM_OVERLAY_REST}）：
     * 同一份用户遮罩色在「聚焦/选中底座」上写整块、在「常态底座」上写半块，见
     * {@link #overlayFillColor}。
     *
     * <p>两级判定，与 {@link #isAppSurfaceFocusRing} 同构但**不共用**：环族的签名取自焦点态描边，
     * 遮罩族的签名取自填充本身，两者放进同一个集合会互相污染。
     * <ol>
     *   <li>形状签名（填充 + 圆角 + 内边距 + 是否椭圆）先做廉价预筛；</li>
     *   <li>签名命中后做一次 48×48 的像素指纹确认——{@code shape_search_hot_word_focused} 与
     *       {@code shape_tv_search_field_focused} 的填充/圆角/内边距完全相同（{@code black_40}
     *       + 28dp + 无内边距中有差异），只有描边宽度/颜色不同，而描边没有公开 getter，
     *       只能靠栅格化把它们区分开。</li>
     * </ol>
     *
     * <p>这里不像环族那样再加一道「视图主题解析出的取值」前置判定：条目遮罩是单一用户槽位，
     * 在两类宿主（页面内容区、固定深色玻璃）上语义相同，不存在 {@code selector_video_item} 那种
     * 双宿主分流问题。
     */
    private static int itemOverlayFillWeight(View view, Drawable drawable) {
        if (!(drawable instanceof GradientDrawable shape)) return 0;
        if (view == null || view.getContext() == null) return 0;
        int strokeKey = itemOverlayStrokeKey(view.getContext());
        ItemOverlayDecision cached = ITEM_OVERLAY_MEMBER.get(shape);
        if (cached != null && cached.strokeKey() == strokeKey) return cached.weight();
        int weight = matchesItemOverlayWhitelist(view, shape, strokeKey);
        ITEM_OVERLAY_MEMBER.put(shape, new ItemOverlayDecision(strokeKey, weight));
        return weight;
    }

    /**
     * 按强度把用户选的遮罩色换算成这一档的实际填充色。
     *
     * <p>只缩放 alpha、保持色相与彩度：默认 {@code 0x66} 的半强度正好是 {@code 0x33}，
     * 与编译期 {@code @color/black_20} 逐位相等——「默认外观字节不变」是靠这条换算成立的。
     * 用户设了不透明色（{@code #RRGGBB} 解析后 alpha=0xFF）时，常态底座拿到 50% 的同色，
     * 聚焦/选中态拿到整色，条目「常态比聚焦浅」的层次随之保留。
     */
    private static int overlayFillColor(int color, int weight) {
        if (color == 0 || weight >= ITEM_OVERLAY_FULL) return color;
        int alpha = (color >>> 24) * weight / ITEM_OVERLAY_FULL;
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** 条目遮罩白名单成员的描边取值指纹：两个随主题解析的描边属性 + 环宽 token。 */
    private static int itemOverlayStrokeKey(Context context) {
        return 31 * (31 * ringColorToken(context) + outlineColorToken(context))
                + focusRingWidthPx(context);
    }

    private static int matchesItemOverlayWhitelist(View view, GradientDrawable shape, int strokeKey) {
        if (view == null) return 0;
        Context context = view.getContext();
        if (context == null) return 0;
        String probe = ringSignature(shape);
        if (probe == null) return 0;
        Long actual = fingerprint(shape);
        if (actual == null) return 0;
        java.util.Map<Integer, Map<String, java.util.Set<Long>>> tiers = itemOverlayFingerprints(context, strokeKey);
        // 两档按固定顺序命中：同一张指纹不可能同时属于两档（填充分别是 black_40 / black_20），
        // 固定顺序只是让结论与 Map 的遍历顺序无关。
        for (int weight : new int[] {ITEM_OVERLAY_FULL, ITEM_OVERLAY_REST}) {
            Map<String, java.util.Set<Long>> tier = tiers.get(weight);
            if (tier == null) continue;
            java.util.Set<Long> expected = tier.get(probe);
            if (expected != null && expected.contains(actual)) return weight;
        }
        return 0;
    }

    /**
     * 白名单成员的「签名 → 指纹」集合，按 {@code (Resources, 描边相关主题属性)} 解析一次后缓存。
     *
     * <p>签名与指纹都取自**未经本类改写**的实例：{@code mutate()} 之前取，因此填充仍是
     * {@code black_40}。指纹内部自己复制一份再绘制，不会污染共享的 ConstantState。
     */
    private static Map<Integer, Map<String, java.util.Set<Long>>> itemOverlayFingerprints(Context context, int strokeKey) {
        Resources resources = context.getResources();
        Map<Integer, Map<Integer, Map<String, java.util.Set<Long>>>> byStroke = ITEM_OVERLAY_FINGERPRINTS.get(resources);
        if (byStroke == null) {
            byStroke = new java.util.HashMap<>();
            ITEM_OVERLAY_FINGERPRINTS.put(resources, byStroke);
        }
        Map<Integer, Map<String, java.util.Set<Long>>> cached = byStroke.get(strokeKey);
        if (cached != null) return cached;
        Map<Integer, Map<String, java.util.Set<Long>>> resolved = new java.util.HashMap<>();
        collectWhitelistDrawables(context, ITEM_OVERLAY_FILLS, ITEM_OVERLAY_FULL, resolved);
        collectWhitelistDrawables(context, ITEM_OVERLAY_REST_FILLS, ITEM_OVERLAY_REST, resolved);
        // 第二条路径：作为 selector 子项解析（生产上宿主就是这么拿到这些 shape 的）。
        collectSelectorChildren(context, resolved);
        byStroke.put(strokeKey, resolved);
        return resolved;
    }

    /** 按名字把一条白名单解析成「签名 → 指纹」集合并收进对应强度格；解析不到的名字跳过（mobile 变体）。 */
    private static void collectWhitelistDrawables(Context context, String[] names, int weight,
            Map<Integer, Map<String, java.util.Set<Long>>> into) {
        for (String name : names) {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) continue;
            Drawable candidate;
            try {
                candidate = context.getDrawable(id);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!(candidate instanceof GradientDrawable shape)) continue;
            String signature = ringSignature(shape);
            Long print = fingerprint(shape);
            if (signature == null || print == null) continue;
            into.computeIfAbsent(weight, key -> new java.util.HashMap<>())
                    .computeIfAbsent(signature, key -> new java.util.HashSet<>()).add(print);
        }
    }

    /**
     * 把 {@link #ITEM_OVERLAY_PARENTS} 每个 selector 的**状态子项**的签名与指纹收进 {@code into}。
     *
     * <p>这里不收名字、只按<b>编译期填充</b>归一档：{@code black_40} 归全强度、{@code black_20}
     * 归半强度，其余（{@code black_30} 的选中档等）直接跳过。之所以在 selector 子项路径上能用
     * 填充当判据，是因为这四个父 selector 的状态子项只有自己那几档形状：{@code selector_item} /
     * {@code selector_item_round} 是 focused+activated+normal，{@code selector_search_hot_word} 是
     * focused+normal，{@code selector_tv_search_field} 两态都是 {@code black_40} 成员。
     * 形状签名（填充 + 圆角 + 内边距）随后再区分具体是哪一个，因此这条判据只决定强度、不决定身份。
     */
    private static void collectSelectorChildren(Context context,
            Map<Integer, Map<String, java.util.Set<Long>>> into) {
        for (String parent : ITEM_OVERLAY_PARENTS) {
            int id = context.getResources().getIdentifier(parent, "drawable", context.getPackageName());
            if (id == 0) continue;
            Drawable loaded;
            try {
                loaded = context.getDrawable(id);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!(loaded instanceof StateListDrawable states)) continue;
            for (int i = 0; i < states.getStateCount(); i++) {
                Drawable child = unwrap(states.getStateDrawable(i));
                if (!(child instanceof GradientDrawable shape)) continue;
                // 只收白名单成员的子项：编译期填充决定强度档，形状签名随后区分具体身份。
                // black_30 的选中档（shape_item_selected / shape_keyboard_focused）在这里被排除。
                ColorStateList fill = shape.getColor();
                if (fill == null) continue;
                int weight = overlayWeightOfCompiledFill(fill.getDefaultColor());
                if (weight == 0) continue;
                String signature = ringSignature(shape);
                Long print = fingerprint(shape);
                if (signature == null || print == null) continue;
                into.computeIfAbsent(weight, key -> new java.util.HashMap<>())
                        .computeIfAbsent(signature, key -> new java.util.HashSet<>()).add(print);
            }
        }
    }

    /** 编译期填充 ⇒ 条目遮罩强度：{@code black_40} 全强度、{@code black_20} 常态半强度，其余 0。 */
    private static int overlayWeightOfCompiledFill(int fill) {
        if (fill == COMPILED_OVERLAY_FILL) return ITEM_OVERLAY_FULL;
        if (fill == COMPILED_REST_FILL) return ITEM_OVERLAY_REST;
        return 0;
    }

    /** 视图主题解析出的 {@code ?attr/colorOutline}；解析不出时返回 0（与「未设置」区分开即可）。 */
    private static int outlineColorToken(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        try {
            // colorOutline 由 Material 定义，main 源集的 R 不生成该符号；用 Material 的 R 解析。
            if (!context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, value, true)) return 0;
            if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) return value.data;
            if (value.resourceId != 0) return context.getResources().getColor(value.resourceId, null);
        } catch (RuntimeException ignored) {
            return 0;
        }
        return 0;
    }

    /**
     * Applies the themed focus ring colour to one state drawable.
     *
     * <p>{@code GradientDrawable} 没有公开的 stroke 宽度/颜色 getter（已对 android-36 的
     * {@code android.jar} 用 javap 核实：只有 {@code setStroke} 系列，没有 {@code getStroke*}），
     * 而仓库契约禁止在 binder 里用反射或隐藏 API，所以宽度只能按唯一 token 重新写入。
     *
     * <p><b>因此调用方必须先把资源收窄到{@link #APP_SURFACE_FOCUS_RINGS}白名单</b>：只有那些
     * selector 的焦点态宽度本就等于该 token，重写宽度才真是无变化。白名单之外若也调用本方法，
     * 就会把 3dp 卡片环等未收敛族强制改成 1.5dp（会重新露出卡片圆角外的月牙）。
     */
    private static boolean setRingStroke(Drawable drawable, View view, int color) {
        if (drawable instanceof DrawableWrapper wrapper) return setRingStroke(wrapper.getDrawable(), view, color);
        if (drawable instanceof GradientDrawable shape) {
            shape.mutate();
            shape.setStroke(focusRingWidthPx(view), color);
            return true;
        }
        return false;
    }

    /** The single TV ring width token, in pixels, resolved through the view's own resources. */
    private static int focusRingWidthPx(View view) {
        return Math.max(1, Math.round(view.getResources().getDimension(R.dimen.webhtv_focus_ring_width)));
    }

    private static int focusRingWidthPx(Context context) {
        return Math.max(1, Math.round(context.getResources().getDimension(R.dimen.webhtv_focus_ring_width)));
    }

    /**
     * Returns a recolored copy of the list, preserving its state behaviour, or null
     * when the colour is unknown/unchanged or the state shape cannot be rebuilt.
     */
    private static ColorStateList rewrite(ColorStateList source, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        if (source == null) return null;
        if (!source.isStateful()) {
            Integer replacement = singleColor(source.getDefaultColor(), explicit, index, active);
            return replacement == null ? null : ColorStateList.valueOf(replacement);
        }
        ColorStateList cached = REBUILT.get(source);
        if (cached != null) return cached;
        int[][] states = ThemeColorIndex.stateSkeleton();
        int[] colors = new int[states.length];
        boolean changed = false;
        // Material state lists usually vary alpha only. Resolve the role from the
        // default colour first, then keep each state's original alpha so pressed /
        // disabled / focused states stay distinguishable.
        int defaultColor = source.getDefaultColor();
        Integer defaultReplacement = singleColor(defaultColor, explicit, index, active);
        for (int position = 0; position < states.length; position++) {
            int original = resolvedColor(source, states[position]);
            Integer replacement = singleColor(original, explicit, index, active);
            if (replacement == null && defaultReplacement != null && sameRgb(original, defaultColor)) {
                replacement = withAlpha(defaultReplacement, original >>> 24);
            }
            colors[position] = replacement == null ? original : replacement;
            changed |= replacement != null;
        }
        if (!changed) return null;
        // ColorStateList exposes a public (int[][], int[]) constructor, so the list
        // can be rebuilt directly without XML inflation or hidden platform APIs.
        ColorStateList rebuilt = new ColorStateList(states, colors);
        REBUILT.put(source, rebuilt);
        return rebuilt;
    }

    private static boolean sameRgb(int first, int second) {
        return (first & 0x00FFFFFF) == (second & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int resolvedColor(ColorStateList source, int[] state) {
        int color = source.getColorForState(state, source.getDefaultColor());
        return color == 0 ? source.getDefaultColor() : color;
    }

    private static Integer singleColor(int current, ThemeRole explicit, ThemeColorIndex index, ThemeTokens active) {
        if (explicit != null) {
            Integer baseline = index.baselineOf(explicit);
            int target = explicit.colorOf(active);
            if (target == 0 || target == current) return null;
            if (baseline != null && baseline != current) return null;
            return target;
        }
        return index.replacementFor(current, active);
    }

    private static boolean isExempt(View view) {
        Object tag = view.getTag();
        if (tag instanceof String value && IGNORE_TAG.equals(value.trim().toLowerCase(Locale.ROOT))) return true;
        String name = view.getClass().getName().toLowerCase(Locale.ROOT);
        for (String marker : EXEMPT_CLASS_MARKERS) {
            if (name.contains(marker)) return true;
        }
        return false;
    }

    private static long signature(ThemeTokens tokens) {
        // ThemeTokens is a content-based record; its generated hashCode is stable
        // across the desugared Android runtime. Java-16 record reflection is
        // deliberately avoided because it does not exist on Android API < 33.
        return tokens.hashCode();
    }
}
