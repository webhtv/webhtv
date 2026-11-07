package com.fongmi.android.tv.player.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LangUtilTest {

    @Test
    public void preferredTextLanguages_usesConfiguredSimplifiedChinese() {
        // 用户显式选了简体中文时，选轨首选语言必须是 zh-Hans，而不是设备语言
        List<String> languages = List.of(LangUtil.getPreferredTextLanguages("zh-Hans"));
        assertEquals("zh-Hans", languages.get(0));
        assertTrue(languages.contains("zh"));
    }

    @Test
    public void preferredTextLanguages_usesConfiguredTraditionalChinese() {
        List<String> languages = List.of(LangUtil.getPreferredTextLanguages("zh-Hant"));
        assertEquals("zh-Hant", languages.get(0));
    }

    @Test
    public void preferredTextLanguages_usesConfiguredNonChineseLanguage() {
        List<String> languages = List.of(LangUtil.getPreferredTextLanguages("ja"));
        assertEquals(List.of("ja"), languages);
    }

    @Test
    public void preferredTextLanguages_fallsBackToDeviceLocaleWhenUnset() {
        // 未设置偏好时行为必须与旧实现完全一致
        assertEquals(List.of(LangUtil.getPreferredTextLanguages()), List.of(LangUtil.getPreferredTextLanguages("")));
        assertEquals(List.of(LangUtil.getPreferredTextLanguages()), List.of(LangUtil.getPreferredTextLanguages(null)));
    }

    @Test
    public void preferredTextLanguages_ignoresNoPreferenceValue() {
        // 设置项里的「不限制」不是语言标签，必须回落设备语言而不是被当成 zh
        assertEquals(List.of(LangUtil.getPreferredTextLanguages()), List.of(LangUtil.getPreferredTextLanguages("any")));
        assertEquals(List.of(LangUtil.getPreferredTextLanguages()), List.of(LangUtil.getPreferredTextLanguages("all")));
    }
}
