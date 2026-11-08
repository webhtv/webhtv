package com.fongmi.android.tv.player.track;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LangUtil {

    private static final int SCORE_EXACT = 400;
    private static final int SCORE_SUBTAG = 300;
    private static final int SCORE_PRIMARY = 200;
    private static final int SCORE_RELATED = 100;
    private static final String LANGUAGE_CHINESE = "zh";
    private static final String SCRIPT_HANS = "Hans";
    private static final String SCRIPT_HANT = "Hant";
    private static final String TAG_HANS = "zh-Hans";
    private static final String TAG_HANT = "zh-Hant";

    public static String[] getPreferredTextLanguages() {
        return getPreferredTextLanguages(null);
    }

    /**
     * 显式语言设置优先于设备语言；空值回落到设备语言。
     * 默认值 zh 表示「中文优先 + 设备字形」，zh-Hans / zh-Hant 表示明确指定字形。
     */
    public static String[] getPreferredTextLanguages(String preferredLanguage) {
        String setting = normalize(preferredLanguage);
        if (!setting.isEmpty() && !"any".equals(setting) && !"all".equals(setting)) {
            String script = chineseScriptTag(setting);
            String primary = primarySubtag(setting);
            if (script != null) return unique(script, primary);
            if (LANGUAGE_CHINESE.equals(primary) && primary.equals(setting)) return unique(getChineseScript(Locale.getDefault()), primary);
            return unique(primary);
        }
        Locale locale = Locale.getDefault();
        String tag = locale.toLanguageTag();
        String language = locale.getLanguage();
        if (!isChinese(locale)) return tag.equals(language) ? new String[]{language} : unique(tag, language);
        return tag.equals(language) ? unique(getChineseScript(locale), language) : unique(tag, getChineseScript(locale), language);
    }

    /** 返回设置值对应的中文字形标签，非中文返回 null。 */
    private static String chineseScriptTag(String normalized) {
        if (normalized.startsWith("zhhans") || normalized.startsWith("zh-hans")
                || normalized.equals("chs") || normalized.equals("sc")) return TAG_HANS;
        if (normalized.startsWith("zhhant") || normalized.startsWith("zh-hant")
                || normalized.startsWith("zhtw") || normalized.startsWith("zh-tw")
                || normalized.equals("cht") || normalized.equals("tc")) return TAG_HANT;
        return null;
    }

    private static String primarySubtag(String normalized) {
        int separator = normalized.indexOf('-');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    public static int getPreferredTextLanguageScore(String languageTag) {
        Locale locale = Locale.getDefault();
        String preferred = normalize(locale.toLanguageTag());
        String language = normalize(languageTag);
        if (language.isEmpty()) return 0;
        if (language.equals(preferred)) return SCORE_EXACT;
        if (isChinese(locale)) return getChineseScore(locale, language);
        return getLanguageScore(preferred, locale.getLanguage(), language);
    }

    private static boolean isChinese(Locale locale) {
        return LANGUAGE_CHINESE.equals(locale.getLanguage());
    }

    private static boolean isTraditionalChinese(Locale locale) {
        String script = locale.getScript();
        if (SCRIPT_HANT.equalsIgnoreCase(script)) return true;
        if (SCRIPT_HANS.equalsIgnoreCase(script)) return false;
        String country = locale.getCountry();
        return "TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country);
    }

    private static String getChineseScript(Locale locale) {
        return isTraditionalChinese(locale) ? TAG_HANT : TAG_HANS;
    }

    private static int getChineseScore(Locale locale, String language) {
        Locale trackLocale = Locale.forLanguageTag(language);
        if (!LANGUAGE_CHINESE.equals(trackLocale.getLanguage())) return 0;
        if (language.equals(LANGUAGE_CHINESE)) return SCORE_PRIMARY;
        return isTraditionalChinese(locale) == isTraditionalChinese(trackLocale) ? SCORE_SUBTAG : SCORE_RELATED;
    }

    private static int getLanguageScore(String preferred, String preferredLanguage, String language) {
        String trackLanguage = Locale.forLanguageTag(language).getLanguage();
        if (!preferredLanguage.equals(trackLanguage)) return 0;
        return isTagPrefix(preferred, language) || isTagPrefix(language, preferred) ? SCORE_SUBTAG : SCORE_PRIMARY;
    }

    private static boolean isTagPrefix(String tag, String prefix) {
        return tag.equals(prefix) || tag.startsWith(prefix + "-");
    }

    private static String normalize(String language) {
        return language == null ? "" : language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static String[] unique(String... languages) {
        List<String> result = new ArrayList<>();
        for (String language : languages) if (!result.contains(language)) result.add(language);
        return result.toArray(new String[0]);
    }
}
