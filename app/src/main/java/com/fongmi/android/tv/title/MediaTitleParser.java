package com.fongmi.android.tv.title;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaTitleParser {

    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)(?:s(\\d{1,2})[-._\\s]*e(\\d{1,3})|第\\s*([0-9零〇一二三四五六七八九十两百]+)\\s*[集话話回期章节節]|\\b(?:EP|E|Episode)\\s*0*([0-9]{1,5})\\b)");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)(?:第\\s*([0-9零〇一二三四五六七八九十两百]+)\\s*[季部]|season\\s*([0-9]{1,2})|s([0-9]{1,2})(?:[-._\\s]*e[0-9]{1,3})?|([一二三四五六七八九十两0-9]{1,3})\\s*(?:st|nd|rd|th)?\\s*(?:季|部))");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)");
    private static final Pattern RELEASE_DATE_PATTERN = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?:[-./_](?:0?[1-9]|1[0-2])[-./_](?:0?[1-9]|[12]\\d|3[01])|(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01]))(?!\\d)");
    private static final Pattern BRACKET_PATTERN = Pattern.compile("[\\[【「『(（]([^\\]】」』)）]{1,60})[\\]】」』)）]");
    private static final Pattern NOISE_WORDS = Pattern.compile("(?i)\\b(HD|4K|8K|1080P|2160P|720P|HDR|HDR10|DV|BluRay|WEB[- ]?DL|HDTV|BDRip|Remux|HEVC|H\\.?265|H\\.?264|x265|x264|AAC|DTS|DDP|Atmos|NF|Netflix|AMZN|DSNP)\\b");
    private static final Set<String> CATEGORY_LABELS = Set.of(
            "电影", "電影", "电视剧", "電視劇", "剧集", "劇集", "动漫", "動漫", "动画", "動畫", "综艺", "綜藝",
            "纪录片", "紀錄片", "纪录", "紀錄", "短片", "微电影", "微電影", "国漫", "國漫", "日漫", "美漫",
            "美剧", "美劇", "韩剧", "韓劇", "日剧", "日劇", "港剧", "港劇", "台剧", "台劇", "泰剧", "泰劇",
            "短剧", "短劇", "连续剧", "連續劇", "戏曲", "戲曲", "音乐", "音樂", "体育", "體育", "儿童", "兒童",
            "大陆", "大陸", "内地", "內地", "中国", "中國", "国产", "國產", "港台", "香港", "台湾", "台灣",
            "澳门", "澳門", "日本", "日韩", "日韓", "韩国", "韓國", "美国", "美國", "欧美", "歐美", "英国", "英國",
            "法国", "法國", "德国", "德國", "泰国", "泰國", "印度", "新加坡", "加拿大", "澳大利亚", "澳大利亞",
            "澳洲", "俄罗斯", "俄羅斯", "西班牙", "意大利", "土耳其", "巴西", "墨西哥",
            "动作", "動作", "喜剧", "喜劇", "爱情", "愛情", "科幻", "恐怖", "惊悚", "驚悚", "悬疑", "懸疑",
            "犯罪", "剧情", "劇情", "冒险", "冒險", "奇幻", "战争", "戰爭", "历史", "歷史", "传记", "傳記",
            "歌舞", "家庭", "运动", "運動", "武侠", "武俠", "古装", "古裝", "灾难", "災難", "西部", "青春",
            "都市", "谍战", "諜戰", "真人秀", "脱口秀", "脫口秀");
    private static final Pattern SUSPICIOUS_MARKERS = Pattern.compile("[#＃]");
    private static final Pattern BOOK_TITLE_PATTERN = Pattern.compile("《([^》]{1,80})》");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern INLINE_FROM_PATTERN = Pattern.compile("\\bfrom\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASH_SEPARATOR_PATTERN = Pattern.compile("\\s+[-－—~～]\\s+");
    private static final Pattern FRAME_RATE_PATTERN = Pattern.compile("(?i)(?<!\\d)(?:24|25|30|50|60|120)\\s*(?:fps|帧)(?![\\u4e00-\\u9fffA-Za-z0-9])");
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("(?i)\\.(mkv|mp4|avi|mov|wmv|flv|rmvb|ts|m2ts)$");

    public MediaTitleResolution parse(MediaTitleRequest request) {
        MediaTitleRequest safe = request == null ? MediaTitleRequest.builder().build() : request;
        String raw = first(safe.getRawTitle(), safe.getRawRemarks(), safe.getEpisodeName());
        String combined = join(raw, safe.getRawRemarks(), safe.getEpisodeName());
        String ruleTitle = cleanTitle(raw);
        float ruleConfidence = ruleConfidence(raw, ruleTitle);
        List<String> contextCandidates = contextTitles(safe);
        String contextTitle = contextConsensusTitle(contextCandidates);
        float contextConfidence = contextConfidence(contextCandidates, ruleTitle, contextTitle);
        MediaTitleLearningExample learning = bestLearningExample(ruleTitle, combined, safe.getLearningExamples());

        MediaTitleResolution resolution = new MediaTitleResolution();
        resolution.setRawTitle(raw);
        resolution.setRuleTitle(ruleTitle);
        resolution.setRuleSource(ruleSource(raw, ruleTitle));
        resolution.setContextConfidence(contextConfidence);
        resolution.setContextGroupSize(contextCandidates.isEmpty() ? 0 : contextCandidates.size() + 1);
        resolution.setYear(firstPositive(firstYear(safe.getVodYear()), firstYear(combined)));
        resolution.setSeasonNumber(firstPositive(seasonNumber(combined), inferTrailingSeason(raw)));
        resolution.setEpisodeNumber(episodeNumber(combined));
        resolution.setMediaType(resolution.getSeasonNumber() > 0 || resolution.getEpisodeNumber() > 0 ? "tv" : "unknown");
        resolution.setEpisodeTitle(cleanTitle(safe.getEpisodeName()));

        if (learning != null) {
            resolution.setCanonicalTitle(learning.getExpectedTitle());
            resolution.setMediaType(learning.getMediaType());
            resolution.setYear(learning.getYear() > 0 ? learning.getYear() : resolution.getYear());
            resolution.setSeasonNumber(learning.getSeasonNumber() > 0 ? learning.getSeasonNumber() : resolution.getSeasonNumber());
            resolution.setConfidence(0.95f);
            resolution.setSource(MediaTitleResolution.SOURCE_MANUAL);
            resolution.addCandidate(MediaTitleCandidate.of(learning.getExpectedTitle(), MediaTitleCandidate.SOURCE_MANUAL, 0.98f));
            resolution.addAlias(learning.getRuleTitle());
        } else {
            boolean useContext = !contextTitle.isEmpty() && contextConfidence >= 0.85f
                    && (ruleTitle.isEmpty() || ruleConfidence < 0.75f);
            resolution.setCanonicalTitle(useContext ? contextTitle : ruleTitle);
            resolution.setConfidence(useContext
                    ? Math.max(ruleConfidence, Math.min(0.88f, 0.55f + contextConfidence * 0.35f))
                    : ruleConfidence);
            resolution.setSource(MediaTitleResolution.SOURCE_RULE);
            if (useContext) resolution.addCandidate(MediaTitleCandidate.of(contextTitle, MediaTitleCandidate.SOURCE_CONTEXT, contextConfidence));
        }

        resolution.addCandidate(MediaTitleCandidate.of(resolution.getCanonicalTitle(), learning != null ? MediaTitleCandidate.SOURCE_MANUAL : MediaTitleCandidate.SOURCE_RULE, resolution.getConfidence()));
        resolution.addCandidate(MediaTitleCandidate.of(ruleTitle, MediaTitleCandidate.SOURCE_RULE, Math.min(0.8f, resolution.getConfidence())));
        if (learning == null && !contextTitle.isEmpty() && contextConfidence >= 0.85f) {
            resolution.addCandidate(MediaTitleCandidate.of(contextTitle, MediaTitleCandidate.SOURCE_CONTEXT, contextConfidence));
        }
        if (!raw.equals(ruleTitle)) resolution.addCandidate(MediaTitleCandidate.of(raw, MediaTitleCandidate.SOURCE_RAW, 0.25f));
        resolution.addAlias(ruleTitle);
        resolution.setConfidenceBreakdown(String.format(Locale.ROOT, "rule=%.2f,context=%.2f,final=%.2f", ruleConfidence, contextConfidence, resolution.getConfidence()));
        return resolution;
    }

    public List<String> cleanSearchTitles(MediaTitleRequest request) {
        MediaTitleRequest safe = request == null ? MediaTitleRequest.builder().build() : request;
        List<String> result = new ArrayList<>();
        addCleanSearchTitles(result, safe.getRawTitle());
        addCleanSearchTitles(result, safe.getEpisodeName());
        if (result.isEmpty()) addCleanSearchTitles(result, safe.getRawRemarks());
        return result;
    }

    public String cleanTitle(String text) {
        if (isBlank(text)) return "";
        String raw = text.trim();
        String clean = raw;
        clean = EXTENSION_PATTERN.matcher(clean).replaceAll(" ");
        clean = removeNoiseBrackets(clean);
        clean = EPISODE_PATTERN.matcher(clean).replaceAll(" ");
        clean = SEASON_PATTERN.matcher(clean).replaceAll(" ");
        clean = YEAR_PATTERN.matcher(clean).replaceAll(" ");
        clean = NOISE_WORDS.matcher(clean).replaceAll(" ");
        clean = FRAME_RATE_PATTERN.matcher(clean).replaceAll(" ");
        clean = clean.replaceAll("(更新至|更至|连载至|全|共)\\s*[0-9零〇一二三四五六七八九十百]+\\s*[集话話回期章节節]", " ");
        clean = clean.replaceAll("(更新至|更至|连载至|連載至)\\s*$", " ");
        // 站源标题在繁体界面下会被改成繁体，噪声词表必须同时列出简体与繁体写法，
        // 否则「慶餘年（高碼）」这类标题清洗不掉尾部标记，会连标记一起拿去搜 TMDB。
        clean = clean.replaceAll("(国语版|国配版|普通话版|普通話版|粤语版|粵語版|台语版|台語版|闽南语版|閩南語版|原声版|原聲版|配音版|中字版|字幕版|台版|台湾版|台灣版|港版|大陆版|大陸版|内地版|內地版|中国版|中國版|泰版|韩版|韓版|日版|美版|英版)", " ");
        clean = clean.replaceAll("(真彩|臻彩|高码|高码率|高碼|高碼率|无水印|無水印|无台标|無台標|国语|國語|国配|國配|国粤|國粵|粤语|粵語|中字|字幕|内封|內封|简繁|簡繁|双语|雙語|官中|杜比|合集|全集|完结|完結|未删减|未刪減|加长版|加長版|修复版|修復版|防和谐版|防和諧版|防和谐|防和諧)", " ");
        clean = clean.replaceAll("[#＃]+", "");
        clean = clean.replaceAll("(?i)(^|\\s)(动漫|动画|电视剧|剧集|电影|综艺)(\\s|$)", " ");
        clean = clean.replaceAll("[._\\-+]+", " ");
        clean = clean.replaceAll("\\s+", " ").trim();
        clean = clean.replaceAll("(?i)^[a-z]\\s+(?=.*[\\u4e00-\\u9fff])", "");
        if (clean.matches("(?i).*[\\u4e00-\\u9fff].*\\s+[a-z]")) clean = clean.replaceAll("(?i)\\s+[a-z]$", "");
        clean = clean.replaceAll("([\\u4e00-\\u9fff])\\s+([\\u4e00-\\u9fff])", "$1$2");
        clean = clean.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
        clean = stripTrailingSeasonDigit(clean);
        return clean.isEmpty() ? raw : clean;
    }

    public int firstYear(String text) {
        if (isBlank(text)) return 0;
        Matcher matcher = YEAR_PATTERN.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public int seasonNumber(String text) {
        if (isBlank(text)) return -1;
        Matcher matcher = SEASON_PATTERN.matcher(text);
        while (matcher.find()) {
            int number = normalizeNumber(firstNonEmptyGroup(matcher, 1, 2, 3, 4));
            if (number > 0) return number;
        }
        return -1;
    }

    public int episodeNumber(String text) {
        if (isBlank(text)) return -1;
        Matcher matcher = EPISODE_PATTERN.matcher(text);
        while (matcher.find()) {
            int number = normalizeNumber(firstNonEmptyGroup(matcher, 2, 3, 4));
            if (number > 0) return number;
        }
        return -1;
    }

    public String normalizeSearchText(String text) {
        return cleanTitle(text).replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").toLowerCase(Locale.ROOT);
    }

    private List<String> contextTitles(MediaTitleRequest request) {
        List<String> result = new ArrayList<>();
        for (String raw : request.getContextTitles()) {
            List<String> candidates = cleanSearchTitles(MediaTitleRequest.builder().rawTitle(raw).build());
            if (!candidates.isEmpty()) result.add(candidates.get(0));
        }
        return result;
    }

    private String contextConsensusTitle(List<String> titles) {
        if (titles.isEmpty()) return "";
        String best = "";
        int bestCount = 0;
        for (String title : titles) {
            int count = 0;
            String normalized = normalizeSearchText(title);
            for (String candidate : titles) if (normalized.equals(normalizeSearchText(candidate))) count++;
            if (count > bestCount) {
                best = title;
                bestCount = count;
            }
        }
        return best;
    }

    private float contextConfidence(List<String> titles, String ruleTitle, String consensusTitle) {
        if (titles.isEmpty() || consensusTitle.isEmpty()) return 0f;
        int matches = 0;
        String consensus = normalizeSearchText(consensusTitle);
        for (String title : titles) if (consensus.equals(normalizeSearchText(title))) matches++;
        float consensusScore = matches / (float) titles.size();
        double ruleScore = similarity(normalizeSearchText(ruleTitle), consensus);
        if (titles.size() == 1 && ruleScore < 0.9d) return 0.65f;
        return (float) Math.min(1d, Math.max(consensusScore, ruleScore));
    }

    private String ruleSource(String raw, String ruleTitle) {
        if (isBlank(ruleTitle)) return "empty";
        List<String> rules = new ArrayList<>();
        if (EPISODE_PATTERN.matcher(raw == null ? "" : raw).find()) rules.add("episode");
        if (SEASON_PATTERN.matcher(raw == null ? "" : raw).find()) rules.add("season");
        if (YEAR_PATTERN.matcher(raw == null ? "" : raw).find()) rules.add("year");
        if (NOISE_WORDS.matcher(raw == null ? "" : raw).find() || FRAME_RATE_PATTERN.matcher(raw == null ? "" : raw).find()) rules.add("media-noise");
        if (BRACKET_PATTERN.matcher(raw == null ? "" : raw).find()) rules.add("bracket");
        if (SUSPICIOUS_MARKERS.matcher(raw == null ? "" : raw).find()) rules.add("obfuscation");
        return rules.isEmpty() ? "plain" : String.join("+", rules);
    }

    private void addCleanSearchTitles(List<String> result, String text) {
        if (isBlank(text)) return;
        String source = normalizePlaybackName(URL_PATTERN.matcher(text.trim()).replaceAll(" "));
        Matcher book = BOOK_TITLE_PATTERN.matcher(source);
        while (book.find()) addCleanSearchTitle(result, book.group(1));

        addCleanSearchTitle(result, mainTitleWithoutCategoryLabels(source));

        boolean bracketTitle = addCleanSearchTitle(result, bestBracketTitle(source));
        if (bracketTitle && looksLikeBracketReleaseName(source)) return;

        for (String segment : titleSegments(source)) addCleanSearchTitle(result, segment);
    }

    private boolean addCleanSearchTitle(List<String> result, String text) {
        String title = effectiveSearchTitle(text);
        if (!isUsableCleanSearchTitle(title)) return false;
        for (String item : result) if (item.equalsIgnoreCase(title)) return false;
        result.add(title);
        return true;
    }

    private String effectiveSearchTitle(String text) {
        if (isBlank(text)) return "";
        String value = normalizePlaybackName(URL_PATTERN.matcher(text.trim()).replaceAll(" "));
        value = cutInlineSourceSuffix(value);
        value = FRAME_RATE_PATTERN.matcher(value).replaceAll(" ");
        value = cleanTitle(value);
        value = FRAME_RATE_PATTERN.matcher(value).replaceAll(" ");
        value = value.replaceAll("(更新至|更至|连载至)\\s*$", " ");
        value = value.replaceAll("[\\[\\]【】「」『』()（）]", " ");
        value = value.replace('|', ' ').replace('丨', ' ');
        value = dropLeadingGroupTokens(value);
        return compactSearchTitle(value);
    }

    private List<String> titleSegments(String text) {
        List<String> segments = new ArrayList<>();
        if (isBlank(text)) return segments;
        String value = text.trim();
        String piped = value.replace('丨', '|');
        int first = piped.indexOf('|');
        if (first >= 0) {
            int second = piped.indexOf('|', first + 1);
            int last = piped.lastIndexOf('|');
            if (second > first) addSegment(segments, piped.substring(first + 1, second));
            if (last > first) addSegment(segments, piped.substring(last + 1));
            addSegment(segments, piped.substring(first + 1));
        } else {
            addSegment(segments, value);
        }
        return segments;
    }

    private void addSegment(List<String> segments, String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return;
        for (String item : segments) if (item.equalsIgnoreCase(text)) return;
        segments.add(text);
    }

    private String bestBracketTitle(String text) {
        Matcher matcher = BRACKET_PATTERN.matcher(text == null ? "" : text);
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        while (matcher.find()) {
            String title = effectiveSearchTitle(matcher.group(1));
            if (!isUsableCleanSearchTitle(title)) continue;
            int score = titleScore(title);
            if (score > bestScore) {
                best = title;
                bestScore = score;
            }
        }
        return best;
    }

    private int titleScore(String title) {
        String value = title == null ? "" : title.trim();
        int cjk = value.replaceAll("[^\\u4e00-\\u9fff]", "").length();
        int letters = value.replaceAll("[^A-Za-z]", "").length();
        return cjk * 100 + letters * 10 + value.length();
    }

    private boolean looksLikeBracketReleaseName(String text) {
        if (isBlank(text)) return false;
        String value = text.trim();
        return value.matches("^(?:[\\[【「『(（][^\\]】」』)）]{1,60}[\\]】」』)）]){2,}.*");
    }

    private String cutInlineSourceSuffix(String text) {
        String value = text == null ? "" : text;
        int cut = -1;
        Matcher from = INLINE_FROM_PATTERN.matcher(value);
        if (from.find()) cut = from.start();
        Matcher dash = DASH_SEPARATOR_PATTERN.matcher(value);
        if (dash.find()) cut = cut < 0 ? dash.start() : Math.min(cut, dash.start());
        return cut >= 0 ? value.substring(0, cut) : value;
    }

    private String normalizePlaybackName(String text) {
        if (isBlank(text)) return "";
        return text.replaceFirst("^\\s*正在播放\\s*[:：]?\\s*", "").trim();
    }

    private String dropLeadingGroupTokens(String text) {
        String value = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return "";
        String[] tokens = value.split("\\s+");
        int start = 0;
        while (start < tokens.length - 1 && looksLikeGroupToken(tokens[start], joinTokens(tokens, start + 1))) start++;
        return joinTokens(tokens, start);
    }

    private boolean looksLikeGroupToken(String token, String rest) {
        String value = normalize(token);
        String remaining = rest == null ? "" : rest;
        if (value.isEmpty() || !remaining.matches(".*[\\u4e00-\\u9fff].*")) return false;
        if (value.matches("(?i)[a-z]{1,4}")) return true;
        if (value.length() <= 8 && value.matches(".*\\d.*") && !value.matches(".*(?:季|部|集|话|話|期|岁)$")) return true;
        return value.matches("(?i)(?:tv|bd|dvd|line\\d*|source\\d*)");
    }

    private String joinTokens(String[] tokens, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = Math.max(0, start); i < tokens.length; i++) {
            if (isBlank(tokens[i])) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(tokens[i]);
        }
        return builder.toString();
    }

    private String compactSearchTitle(String text) {
        String value = text == null ? "" : text;
        value = value.replaceAll("[#＃]+", "");
        value = value.replaceAll("[._+]+", " ");
        value = value.replaceAll("\\s+", " ").trim();
        String previous;
        do {
            previous = value;
            value = value.replaceAll("([\\u4e00-\\u9fff])\\s+([\\u4e00-\\u9fff])", "$1$2");
        } while (!previous.equals(value));
        value = value.replaceAll("(?i)^[a-z]\\s+(?=.*[\\u4e00-\\u9fff])", "");
        value = value.replaceAll("^[\\s:：,，.。·|/\\\\\\-]+|[\\s:：,，.。·|/\\\\\\-]+$", "");
        return stripTrailingSeasonDigit(value);
    }

    private boolean isUsableCleanSearchTitle(String text) {
        String value = text == null ? "" : text.trim();
        if (value.length() < 2 || value.length() > 80) return false;
        if (isNoiseTag(value)) return false;
        if (!value.matches(".*[\\u4e00-\\u9fffA-Za-z].*")) return false;
        String normalized = normalize(value);
        if (normalized.length() < 2) return false;
        if (normalized.matches("(?i)(?:更新至|更至|连载至|全集|合集|完结|电影|电视剧|剧集|动漫|动画|综艺|tv)")) return false;
        if (value.matches("(?i)^(?:更新至|更至|连载至|全|共)?\\s*[0-9零〇一二三四五六七八九十两百]+\\s*[集话話回期章节節](?:完结|更新中)?$")) return false;
        return !value.matches("^第\\s*[0-9零〇一二三四五六七八九十两百]+\\s*[集话話回期章节節].*");
    }

    private MediaTitleLearningExample bestLearningExample(String ruleTitle, String combined, List<MediaTitleLearningExample> examples) {
        if (examples == null || examples.isEmpty()) return null;
        List<MediaTitleLearningExample> usable = new ArrayList<>();
        for (MediaTitleLearningExample example : examples) if (example != null && example.isUsable()) usable.add(example);
        usable.sort(Comparator
                .comparingDouble((MediaTitleLearningExample example) -> learningScore(ruleTitle, combined, example))
                .reversed()
                .thenComparing(MediaTitleLearningExample::isManual, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(MediaTitleLearningExample::getHitCount).reversed()));
        if (usable.isEmpty()) return null;
        MediaTitleLearningExample best = usable.get(0);
        return learningScore(ruleTitle, combined, best) >= 0.55 ? best : null;
    }

    private double learningScore(String ruleTitle, String combined, MediaTitleLearningExample example) {
        String rule = normalize(ruleTitle);
        String sourceRule = normalize(cleanTitle(example.getRuleTitle()));
        String raw = normalize(cleanTitle(combined));
        String sourceRaw = normalize(cleanTitle(example.getRawTitle()));
        double score = similarity(rule, sourceRule);
        if (!sourceRaw.isEmpty()) score = Math.max(score, similarity(raw, sourceRaw));
        if (!sourceRule.isEmpty() && (raw.contains(sourceRule) || sourceRule.contains(rule))) score += 0.2;
        if (example.isManual()) score += 0.15;
        return Math.min(1.0, score);
    }

    private float ruleConfidence(String raw, String ruleTitle) {
        if (isBlank(ruleTitle)) return 0f;
        if (SUSPICIOUS_MARKERS.matcher(raw == null ? "" : raw).find()) return 0.6f;
        if (looksLikeObfuscated(ruleTitle)) return 0.3f;
        if (!raw.equals(ruleTitle) && ruleTitle.length() >= 2) return 0.75f;
        return 0.8f;
    }

    private boolean looksLikeObfuscated(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("[A-Za-z]{2,8}") || value.length() <= 2 && value.matches("[A-Za-z0-9]+");
    }

    private String removeNoiseBrackets(String text) {
        Matcher matcher = BRACKET_PATTERN.matcher(text == null ? "" : text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            matcher.appendReplacement(buffer, isNoiseTag(value) ? " " : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isNoiseTag(String text) {
        String value = normalize(text);
        if (value.isEmpty()) return true;
        return NOISE_WORDS.matcher(text).find()
                || value.matches(".*(真彩|臻彩|高码|高碼|无水印|無水印|无台标|無台標|国语|國語|国配|國配|国粤|國粵|粤语|粵語|中字|字幕|内封|內封|简繁|簡繁|双语|雙語|官中|杜比|合集|全集|完结|完結|未删减|未刪減|加长版|加長版|修复版|修復版|完整版|未剪辑|未剪輯|双字|雙字|国英|國英|中英|票房).*");
    }

    /**
     * 括号里是「电影」「美国 恐怖 奇幻」这类分类标签时，真实片名在括号外。
     * 弹幕服务按整串匹配，带标签的串一律搜不到，所以这里优先把所有括号剥掉只留主干。
     * 但全剥会误伤片名本身写在括号里的标题，因此按三档依次回退：
     * 第一档｜全剥：删掉所有括号内容，仅在至少一个括号是分类标签时才参与比较，
     *   对应「[电影] 小丑回魂：德里故事 (2026) [...] [美国 恐怖 奇幻]」这类片名在括号外的标题；
     * 第二档｜只留片名括号：仅分类标签括号被删，剩下的非标签括号里只有片名该进标题，
     *   对应「[美国][恐怖][我的电影].mkv」这类片名写在括号里的标题，否则全剥会把它删成近乎空串；
     * 第三档｜两档都不可用则返回空串，保持调用方 addCleanSearchTitles 的既有语义，
     *   bestBracketTitle 候选仍在，[恐怖].mkv 这类不会因此丢候选。
     */
    private String mainTitleWithoutCategoryLabels(String text) {
        // 先摘掉扩展名：否则「[美国][恐怖][我的电影].mkv」全剥后只剩「.mkv」，
        // 经 effectiveSearchTitle 会变成可用标题「mkv」，第二档就永远轮不到。
        String source = text == null ? "" : EXTENSION_PATTERN.matcher(text).replaceAll(" ").trim();
        if (!hasCategoryLabel(source)) return "";
        String all = stripAllBrackets(source);
        if (isUsableCleanSearchTitle(effectiveSearchTitle(all))) return all;
        return lastUsableBracketTitle(source);
    }

    /**
     * 第二档不能整串返回：effectiveSearchTitle 只删括号字符，括号里的文字会带空格拼进标题，
     * 「[北美票房][美国][阿凡达]」因此会得到「北美票房 阿凡达」。所以这里在多个括号里取最靠后的
     * 可用括号；但片名后面还可能挂着发行噪声，最靠后的括号就不再是片名，因此跳过两类括号：
     * 一｜分类标签括号，判据 isCategoryLabel，如「[美国]」「[恐怖]」；
     * 二｜发行噪声括号，判据 isNoiseTag，如「[完整版]」「[未剪辑]」「[双字]」「[北美票房]」。
     */
    private String lastUsableBracketTitle(String text) {
        Matcher matcher = BRACKET_PATTERN.matcher(text == null ? "" : text);
        String best = "";
        while (matcher.find()) {
            if (isCategoryLabel(matcher.group(1))) continue;
            if (isNoiseTag(matcher.group(1))) continue;
            if (!isUsableCleanSearchTitle(effectiveSearchTitle(matcher.group(1)))) continue;
            best = matcher.group();
        }
        return best;
    }

    private boolean hasCategoryLabel(String text) {
        Matcher matcher = BRACKET_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) if (isCategoryLabel(matcher.group(1))) return true;
        return false;
    }

    /** 第一档：删掉所有括号内容，只留括号外的主干。 */
    private String stripAllBrackets(String text) {
        return BRACKET_PATTERN.matcher(text == null ? "" : text).replaceAll(" ").trim();
    }

    private boolean isCategoryLabel(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return false;
        boolean labeled = false;
        for (String token : value.split("[\\s,，、/|·]+")) {
            if (token.isEmpty()) continue;
            if (!isCategoryLabelToken(token)) return false;
            labeled = true;
        }
        // 只有分隔符（如 "[//]"）时一个 token 都没命中，不能算分类标签。
        return labeled;
    }

    /** 判的是「电影」「美国」「恐怖」这类分类词；与上面的 looksLikeGroupToken（a-z1-4、短码、tv|bd 这类发布组标记）不是一回事。 */
    private boolean isCategoryLabelToken(String token) {
        if (CATEGORY_LABELS.contains(token)) return true;
        int length = token.length();
        if (length < 2) return false;
        String head = token.substring(0, length - 1);
        char tail = token.charAt(length - 1);
        return (tail == '剧' || tail == '劇' || tail == '片' || tail == '番') && CATEGORY_LABELS.contains(head);
    }

    private int inferTrailingSeason(String raw) {
        if (isBlank(raw)) return -1;
        String value = raw.trim();
        Matcher releaseDate = RELEASE_DATE_PATTERN.matcher(value);
        if (releaseDate.find()) value = value.substring(0, releaseDate.start()).trim();
        Matcher matcher = Pattern.compile("^(.+?)([2-9])(?:\\s|\\.|_|-|$)").matcher(value);
        if (!matcher.find()) return -1;
        String prefix = matcher.group(1);
        return prefix.length() >= 2 ? normalizeNumber(matcher.group(2)) : -1;
    }

    private String stripTrailingSeasonDigit(String text) {
        if (isBlank(text)) return "";
        Matcher matcher = Pattern.compile("^(.+?)([2-9])$").matcher(text.trim());
        if (!matcher.find()) return text.trim();
        String prefix = matcher.group(1).trim();
        return prefix.length() >= 2 ? prefix : text.trim();
    }

    private String firstNonEmptyGroup(Matcher matcher, int... groups) {
        for (int group : groups) {
            String value = matcher.group(group);
            if (!isBlank(value)) return value;
        }
        return "";
    }

    private int normalizeNumber(String value) {
        if (isBlank(value)) return -1;
        value = value.trim();
        try {
            if (value.matches("\\d+")) return Integer.parseInt(value.replaceFirst("^0+(?!$)", ""));
        } catch (Exception ignored) {
            return -1;
        }
        int number = parseSmallChineseNumber(value);
        return number > 0 ? number : -1;
    }

    private int parseSmallChineseNumber(String value) {
        if (isBlank(value)) return 0;
        value = value.replace("两", "二").replace("零", "").replace("〇", "");
        if (value.matches("[一二三四五六七八九]")) return chineseDigit(value.charAt(0));
        int tenIndex = value.indexOf("十");
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : chineseDigit(value.charAt(tenIndex - 1));
            int ones = tenIndex == value.length() - 1 ? 0 : chineseDigit(value.charAt(tenIndex + 1));
            return tens * 10 + ones;
        }
        return 0;
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }

    private double similarity(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        if (left.equals(right)) return 1.0;
        if (left.contains(right) || right.contains(left)) return 0.85;
        int max = Math.max(left.length(), right.length());
        return (max - editDistance(left, right)) / (double) max;
    }

    private int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int i = 0; i <= right.length(); i++) previous[i] = i;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private int firstPositive(int first, int second) {
        return first > 0 ? first : second;
    }

    private String first(String... values) {
        for (String value : values) if (!isBlank(value)) return value.trim();
        return "";
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (isBlank(value)) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
