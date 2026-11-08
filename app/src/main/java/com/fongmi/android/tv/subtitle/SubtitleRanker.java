package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SubtitleRanker {

    public List<SubtitleCandidate> rank(List<SubtitleCandidate> candidates, SubtitleContext context) {
        List<SubtitleCandidate> ranked = new ArrayList<>();
        if (candidates == null) return ranked;
        for (SubtitleCandidate candidate : candidates) ranked.add(score(candidate, context));
        ranked.sort(Comparator.comparingInt(SubtitleCandidate::getScore).reversed().thenComparing(SubtitleCandidate::getDisplayName));
        return ranked;
    }

    /**
     * 从已排序候选中选出要自动应用的那一条。
     *
     * <p>过去这里在「不止一个候选且分差 &lt; 5」时直接放弃，本意是遇到真歧义时不要
     * 乱选。但同一部片子往往有几十个版本（简/繁、ASS/SRT、不同压制组），它们本来就
     * 应该被区分开而不是被当成歧义——放弃的结果是「搜得到却匹配不到」，用户必须
     * 手动进列表里再点一次。现在改为：只要标题确实对得上就取最优；只有在标题根本
     * 没出现在候选里，或最高分连阈值都够不到时才返回 null。
     */
    public SubtitleCandidate pickBest(List<SubtitleCandidate> ranked, SubtitleContext context) {
        if (ranked == null || ranked.isEmpty()) return null;
        SubtitleCandidate best = ranked.get(0);
        // 标题完全没出现在候选里说明这条字幕不是这部片子的，分数再高也不能挂
        if (titleScore(context, normalize(best.getDisplayName() + " " + best.getReleaseInfo())) <= 0) return null;
        int threshold = threshold(context, ranked.size());
        if (best.getScore() < threshold) return null;
        return best;
    }

    private SubtitleCandidate score(SubtitleCandidate candidate, SubtitleContext context) {
        int score = candidate.getScore();
        SubtitleMatchType matchType = SubtitleMatchType.METADATA_FUZZY;
        String haystack = normalize(candidate.getDisplayName() + " " + candidate.getReleaseInfo());
        int title = titleScore(context, haystack);
        score += title;
        score += yearScore(candidate, context, haystack);
        score += episodeScore(candidate, context, haystack);
        score += durationScore(candidate, context);
        score += languageScore(candidate.getLanguage(), context.getPreferredLanguage());
        score += formatScore(candidate.getFormat());
        if (!SubtitleStrings.isEmpty(context.getEpisodeTitle()) && contains(haystack, context.getEpisodeTitle())) score += 10;
        // 标题都对不上的候选不能因为其它维度凑分高而被标成严格匹配
        if (title > 0 && score >= (context.hasTmdbIdentity() ? 78 : 72)) matchType = SubtitleMatchType.METADATA_STRICT;
        return candidate.withScore(score, matchType);
    }

    /**
     * 标题分：精确 &gt; 包含（按覆盖比例）&gt; 编辑距离。
     *
     * <p>沿用 {@code TmdbMatcher.titleSimilarityScore} 的分层思路：精确匹配给满分，
     * 包含关系按「较短串占较长串的比例」给分，剩余情况用 Levenshtein 距离递减。
     * 这样同一部片子的不同版本字幕（「战狼2.1080p…」「[www.hdmayi.com]战狼2…」）
     * 都能拿到接近满分，而别的片子会被明显压下去。
     */
    private int titleScore(SubtitleContext context, String haystack) {
        int best = 0;
        for (String title : candidateTitles(context)) {
            String needle = normalize(title);
            if (needle.isEmpty()) continue;
            if (haystack.equals(needle)) return 40;
            if (haystack.contains(needle)) {
                int coverage = Math.round(36f * needle.length() / Math.max(1, haystack.length()));
                best = Math.max(best, Math.min(38, 30 + coverage));
                continue;
            }
            int distance = levenshtein(haystack, needle);
            int max = Math.max(haystack.length(), needle.length());
            int similarity = Math.max(0, Math.round(30f * (max - distance) / Math.max(1, max)));
            if (similarity >= 20) best = Math.max(best, similarity);
        }
        return best;
    }

    /** 会被拿去匹配的作品名：正名、原名、别名，去重后按长度降序（长标题更具体）。 */
    private List<String> candidateTitles(SubtitleContext context) {
        List<String> titles = new ArrayList<>();
        addTitle(titles, context.getCanonicalTitle());
        addTitle(titles, context.getOriginalTitle());
        for (String alias : context.getAliases()) addTitle(titles, alias);
        titles.sort(Comparator.comparingInt(String::length).reversed());
        return titles;
    }

    private void addTitle(List<String> titles, String value) {
        if (SubtitleStrings.isEmpty(value)) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || normalizedContains(titles, trimmed)) return;
        titles.add(trimmed);
    }

    private boolean normalizedContains(List<String> titles, String value) {
        String needle = normalize(value);
        for (String title : titles) if (normalize(title).equals(needle)) return true;
        return false;
    }

    private int yearScore(SubtitleCandidate candidate, SubtitleContext context, String haystack) {
        if (context.getYear() <= 0) return 0;
        String year = String.valueOf(context.getYear());
        if (candidate.getYear() == context.getYear()) return 14;
        if (haystack.contains(year)) return 12;
        if (candidate.getYear() > 0 && Math.abs(candidate.getYear() - context.getYear()) <= 1) return 6;
        if (candidate.getYear() > 0) return -8;
        return 0;
    }

    private int episodeScore(SubtitleCandidate candidate, SubtitleContext context, String haystack) {
        int score = 0;
        if (context.getSeasonNumber() > 0 && context.getEpisodeNumber() > 0) {
            boolean seasonMatch = candidate.getSeasonNumber() == context.getSeasonNumber() || haystack.contains(String.format(Locale.US, "s%02de%02d", context.getSeasonNumber(), context.getEpisodeNumber()));
            boolean episodeMatch = candidate.getEpisodeNumber() == context.getEpisodeNumber() || haystack.contains("第" + context.getEpisodeNumber() + "集");
            if (seasonMatch && episodeMatch) score += 30;
            else if (candidate.getSeasonNumber() > 0 || candidate.getEpisodeNumber() > 0) score -= 12;
        } else if (context.getEpisodeNumber() > 0 && candidate.getEpisodeNumber() == context.getEpisodeNumber()) {
            score += 12;
        }
        return score;
    }

    /**
     * 时长分：只在双方都有时长时比较。
     *
     * <p>这是区分「同一部片子的不同剪辑/不同集」最有力的信号——标题、年份、语言
     * 全都一样的两个候选，时长对不上的那个基本可以排除。差值按容差线性递减，
     * 超过 10 分钟视为不同的内容。
     */
    private int durationScore(SubtitleCandidate candidate, SubtitleContext context) {
        long expected = context.getDurationMs();
        long actual = candidate.getDurationMs();
        if (expected <= 0 || actual <= 0) return 0;
        long diffSeconds = Math.abs(expected - actual) / 1000L;
        if (diffSeconds <= 5) return 24;
        if (diffSeconds <= 30) return 18;
        if (diffSeconds <= 120) return 8;
        if (diffSeconds <= 600) return -6;
        return -20;
    }

    private int levenshtein(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int i = 0; i <= second.length(); i++) previous[i] = i;
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    /**
     * 自动应用的准入线。
     *
     * <p>分值与旧实现同一量纲（provider 基础分 + 标题/年份/语言/格式），标题维度最重
     * （满分 40）。阈值只需挡住「标题对不上、全靠其它维度凑分」的候选，因此维持在
     * 略高于「无标题匹配时的可得分上限」即可，不必随候选数量变化——候选多寡反映的
     * 是同一部片子有多少版本，不是匹配可信度。
     */
    private int threshold(SubtitleContext context, int count) {
        if (context == null) return 70;
        if (context.hasTmdbIdentity()) return "tv".equalsIgnoreCase(context.getMediaType()) ? 60 : 55;
        if ("tv".equalsIgnoreCase(context.getMediaType())) return context.getEpisodeNumber() > 0 ? 68 : 78;
        return 70;
    }

    private int languageScore(String language, String preferredLanguage) {
        String normalized = normalizeLanguage(language);
        String preferred = normalizeLanguageCode(preferredLanguage);
        if (SubtitleStrings.isEmpty(preferred) || "any".equals(preferred)) return 0;
        if (matchesPreferredLanguage(normalized, preferred)) return "zh".equals(preferred) ? 15 : 12;
        if (preferred.startsWith("zh") && isChineseLanguage(normalized)) return 10;
        return 0;
    }

    private boolean matchesPreferredLanguage(String language, String preferred) {
        if (language.contains(preferred)) return true;
        return switch (preferred) {
            case "zh" -> isChineseLanguage(language);
            case "zhhans" -> containsAny(language, "simplified", "simplifiedchinese", "简体", "简中", "简", "chs", "sc", "gb", "zhhans");
            case "zhhant" -> containsAny(language, "traditional", "traditionalchinese", "繁体", "繁中", "繁", "cht", "tc", "big5", "zhhant");
            case "en" -> containsAny(language, "english", "英文", "英语", "英語", "eng", "en");
            case "ja" -> containsAny(language, "japanese", "日语", "日文", "日語", "jp", "ja");
            case "ko" -> containsAny(language, "korean", "韩语", "韓語", "韩文", "韓文", "kr", "ko");
            default -> false;
        };
    }

    private boolean isChineseLanguage(String language) {
        return containsAny(language, "中文", "中字", "双语", "雙語", "简", "繁", "粵", "粤", "国语", "國語", "chi", "chs", "cht", "zh");
    }

    private String normalizeLanguage(String value) {
        return normalize(value);
    }

    /**
     * 把字幕源给出的语言描述收敛成合法的 BCP-47 标签。
     *
     * <p>字幕源常返回「简体中文」「默认」这类自然语言描述。它们进不了 Media3 的
     * 语言匹配，选轨时等同于「语言未知」，于是用户设置的简体中文偏好在最关键
     * 的一步失效。无法识别的一律返回空串，让播放器按未知语言处理，
     * 而不是拿一个非法标签去比较。
     */
    public static String normalizeLanguageTag(String language) {
        if (SubtitleStrings.isEmpty(language)) return "";
        String lower = language.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("zhhans") || lower.startsWith("zh-hans") || lower.startsWith("zh-cn")
                || lower.startsWith("zh-sg") || lower.equals("chs") || lower.equals("sc")
                || lower.contains("简")) return "zh-Hans";
        if (lower.startsWith("zhhant") || lower.startsWith("zh-hant") || lower.startsWith("zh-tw")
                || lower.startsWith("zh-hk") || lower.startsWith("zh-mo") || lower.equals("cht")
                || lower.equals("tc") || lower.contains("繁")) return "zh-Hant";
        if (lower.startsWith("zh") || lower.contains("中文") || lower.contains("中字") || lower.contains("国语")
                || lower.contains("國語") || lower.contains("双语") || lower.contains("雙語")
                || lower.equals("默认") || lower.equals("默認") || lower.equals("default")) return "zh";
        if (lower.startsWith("en") || lower.contains("英文") || lower.contains("英语") || lower.contains("英語")) return "en";
        if (lower.startsWith("ja") || lower.equals("jp") || lower.contains("日文") || lower.contains("日语")) return "ja";
        if (lower.startsWith("ko") || lower.equals("kr") || lower.contains("韩文") || lower.contains("韩语")) return "ko";
        return "";
    }

    private String normalizeLanguageCode(String value) {
        String normalized = normalize(value).replace("traditional", "hant").replace("simplified", "hans");
        if (SubtitleStrings.isEmpty(normalized)) return "zh";
        if (normalized.startsWith("zhhans") || normalized.equals("chs") || normalized.equals("sc")) return "zhhans";
        if (normalized.startsWith("zhhant") || normalized.startsWith("zhtw") || normalized.equals("cht") || normalized.equals("tc")) return "zhhant";
        if (normalized.startsWith("zh")) return "zh";
        if (normalized.startsWith("en")) return "en";
        if (normalized.startsWith("ja") || normalized.equals("jp")) return "ja";
        if (normalized.startsWith("ko") || normalized.equals("kr")) return "ko";
        if (normalized.equals("any") || normalized.equals("all")) return "any";
        return normalized;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (!SubtitleStrings.isEmpty(needle) && value.contains(needle.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private int formatScore(String format) {
        if (SubtitleStrings.isEmpty(format)) return 0;
        String lower = format.toLowerCase(Locale.ROOT);
        if (lower.contains("ssa") || lower.contains("ass")) return 10;
        if (lower.contains("srt")) return 6;
        if (lower.contains("vtt")) return 4;
        return 0;
    }

    private boolean contains(String haystack, String needle) {
        return !SubtitleStrings.isEmpty(needle) && normalize(haystack).contains(normalize(needle));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\u4e00-\u9fa5]+", "");
    }
}
