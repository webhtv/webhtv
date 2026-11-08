package com.fongmi.android.tv.tts;

/**
 * 百度在线语音（翻译发音接口，免 Key）。
 *
 * 接口：{@code https://fanyi.baidu.com/gettts?lan=zh&text=…&spd=…&source=web}
 * 实测（2026-10-09，测试设备）：HTTP 200 + {@code audio/mpeg}；{@code spd} 有效区间 1..7，
 * 超出范围会返回空响应，因此 {@link HttpTtsEngine#speakSpeed(float)} 做了 1..7 夹取。
 *
 * 选择它作为内置轻量音源的原因：单句延迟低、无需注册、对中文长句稳定；
 * 音色质量不如微软 Neural，故保留 Edge 音源供用户选择。
 */
final class BaiduTtsEngine extends HttpTtsEngine {

    private static final String RULE =
            "https://fanyi.baidu.com/gettts?lan=zh&text={{java.encodeURI(speakText)}}&spd={{speakSpeed}}&source=web";

    BaiduTtsEngine() {
        super(config(), TtsEngines.SOURCE_BAIDU);
    }

    private static TtsEngineConfig config() {
        TtsEngineConfig c = new TtsEngineConfig();
        c.name = "百度在线语音";
        c.url = RULE;
        c.contentType = "audio/mpeg";
        return c;
    }

    @Override
    public String detail() {
        return "百度在线语音（免配置，依赖网络）";
    }
}
