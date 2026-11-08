package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubtitleRanker {

    private static final Pattern SEASON_EPISODE = Pattern.compile("(?i)s(\\d{1,2})\\s*e(\\d{1,3})");
    private static final Pattern RELEASE_METADATA = Pattern.compile(
            "^(?:(?:19\\d{2}|20\\d{2})(?=[^\\p{L}\\p{N}]|(?:720|1080|2160)p|$)"
                    + "|(?:720|1080|2160)p?|[48]k|s\\d{1,2}e\\d{1,3}"
                    + "|ass|ssa|srt|vtt|sup|sub|idx|bluray|blu|bdrip|brrip|web|webrip|webdl"
                    + "|hdr|sdr|hevc|avc|h264|h265|x264|x265|dts|aac|truehd|ddp|proper|repack"
                    + "|extended|remastered|director|uncut|eng|english|chinese|chs|cht|zh|en"
                    + "|简中|繁中|简体|繁体|简繁|中文|中英|中字|双语|国语|粤语|英语|字幕|导演剪辑版|加长版|未删减|特效)"
                    + "(?=[^\\p{L}\\p{N}]|$)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

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
        int threshold = threshold(context, ranked.size());
        for (SubtitleCandidate candidate : ranked) {
            // 身份优先：先跳过不是同一部作品的候选，再谈分数。否则一条续集/别的片子的
            // 高分会把真正对得上的那条低分字幕挡住，用户看到的就是「匹配错了」。
            if (!identifiesWork(candidate, context)) continue;
            if (candidate.getScore() < threshold) continue;
            return candidate;
        }
        return null;
    }

    /**
     * 这条候选是否就是 context 所描述的那个作品。
     *
     * <p>相似度不是身份：编辑距离相近可以是另一部片子，包含关系可以是续集
     * （《流浪地球》与《流浪地球2》）。所以这里要求两件事同时成立：
     * 候选里确实出现了某个已知作品名，且紧随其后的不是「作品本身之外」的续集序号
     * ——「战狼2.2017.1080p」这种年份/版本后缀、原名与别名都属于同一部作品。
     *
     * <p>季集则单独判断：候选明确标了别的季/集就是别的条目；未知季集不算冲突。
     */
    private boolean identifiesWork(SubtitleCandidate candidate, SubtitleContext context) {
        if (!identifiesTitle(candidate, context)) return false;
        return !conflictsWithEpisode(candidate, context);
    }

    private boolean identifiesTitle(SubtitleCandidate candidate, SubtitleContext context) {
        String text = candidate.getDisplayName() + " " + candidate.getReleaseInfo();
        for (String title : candidateTitles(context)) {
            Matcher matcher = titlePattern(title).matcher(text);
            while (matcher.find()) {
                String rest = text.substring(matcher.end()).replaceFirst("^[^\\p{L}\\p{N}]+", "");
                if (rest.isEmpty() || isReleaseMetadata(rest)) return true;
                for (String alias : candidateTitles(context)) {
                    Matcher alternate = titlePattern(alias).matcher(rest);
                    if (alternate.lookingAt()) {
                        String afterAlias = rest.substring(alternate.end()).replaceFirst("^[^\\p{L}\\p{N}]+", "");
                        if (afterAlias.isEmpty() || isReleaseMetadata(afterAlias)) return true;
                    }
                }
            }
        }
        return false;
    }

    private Pattern titlePattern(String title) {
        String value = normalize(title);
        StringBuilder regex = new StringBuilder("(?<![\\p{L}\\p{N}])");
        for (int i = 0; i < value.length(); i++) {
            if (i > 0) regex.append("[^\\p{L}\\p{N}]*");
            regex.append(Pattern.quote(value.substring(i, i + 1)));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private boolean isReleaseMetadata(String value) {
        return RELEASE_METADATA.matcher(value).find();
    }

    /** 候选明确标出的季/集与 context 明确标出的不一致时，拒绝自动应用。 */
    private boolean conflictsWithEpisode(SubtitleCandidate candidate, SubtitleContext context) {
        int season = context.getSeasonNumber();
        int episode = context.getEpisodeNumber();
        if (season > 0 && candidate.getSeasonNumber() > 0 && candidate.getSeasonNumber() != season) return true;
        if (episode > 0 && candidate.getEpisodeNumber() > 0 && candidate.getEpisodeNumber() != episode) return true;
        if (season <= 0 && episode <= 0) return false;
        String haystack = candidate.getDisplayName() + " " + candidate.getReleaseInfo();
        Matcher matcher = SEASON_EPISODE.matcher(haystack);
        while (matcher.find()) {
            int candidateSeason = Integer.parseInt(matcher.group(1));
            int candidateEpisode = Integer.parseInt(matcher.group(2));
            if (season > 0 && candidateSeason != season) return true;
            if (episode > 0 && candidateEpisode != episode) return true;
        }
        return false;
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
        if (trimmed.isEmpty() || normalize(trimmed).isEmpty() || normalizedContains(titles, trimmed)) return;
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
