package com.fongmi.android.tv.tts;

import java.util.ArrayList;
import java.util.List;

/**
 * 朗读文本切分（纯逻辑，便于单测）。
 *
 * 对齐 legado 的朗读分句策略：
 * - 系统朗读按「段落」入队，交给 TTS 引擎处理长文本韵律；
 * - 在线朗读按「分句」入队，因为每个分句是一次 HTTP/WS 请求，
 *   分句越短首句越快出声、预取也越平滑。
 *
 * 两种模式都丢弃纯标点/空白片段（legado 的 notReadAloudRegex 语义），
 * 避免朗读引擎对「……」「——」之类片段报错或产生长时间静音。
 */
public final class TtsTextSplitter {

    /** 单次在线合成的文本上限（URL 长度与首句时延的折中）。 */
    public static final int MAX_CHUNK = 160;

    private static final String SENTENCE_END = "。！？!?；;…\n";
    private static final String SOFT_BREAK = "，,、：:";

    private TtsTextSplitter() {
    }

    /** 一个朗读片段：文本 + 它所属的段落下标（0 基）。 */
    public static final class Chunk {
        public final String text;
        public final int paragraph;

        public Chunk(String text, int paragraph) {
            this.text = text;
            this.paragraph = paragraph;
        }

        @Override
        public String toString() {
            return paragraph + ":" + text;
        }
    }

    /**
     * @param paragraphs  段落文本（来自阅读器正文）
     * @param maxChars    单片段最大字数；&lt;= 0 表示按段落（系统朗读）
     */
    public static List<Chunk> split(List<String> paragraphs, int maxChars) {
        List<Chunk> out = new ArrayList<>();
        if (paragraphs == null) return out;
        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = normalize(paragraphs.get(i));
            if (paragraph.isEmpty() || isSilent(paragraph)) continue;
            if (maxChars <= 0) {
                out.add(new Chunk(paragraph, i));
                continue;
            }
            for (String piece : splitParagraph(paragraph, maxChars)) {
                if (piece.isEmpty() || isSilent(piece)) continue;
                out.add(new Chunk(piece, i));
            }
        }
        return out;
    }

    /** 规范化：去掉控制字符与多余空白，保留正文标点。 */
    public static String normalize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\t' || c == '\u00a0' || c == '\u3000') {
                sb.append(' ');
            } else if (c < 0x20 && c != '\n') {
                // 控制字符直接丢弃（朗读引擎遇到会报错）
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /** 是否没有可朗读内容（只有标点/空白/装饰符）。 */
    public static boolean isSilent(String text) {
        if (text == null) return true;
        for (int i = 0; i < text.length(); i++) {
            if (isSpeakable(text.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isSpeakable(char c) {
        // 中日韩统一表意文字在 Unicode 里就是 Letter，isLetterOrDigit 已覆盖；
        // 这里只额外保留需要的边界判断，其余一律视为标点/装饰符。
        return Character.isLetterOrDigit(c);
    }

    private static List<String> splitParagraph(String paragraph, int maxChars) {
        List<String> sentences = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            cur.append(c);
            if (SENTENCE_END.indexOf(c) >= 0) {
                sentences.add(cur.toString().trim());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) sentences.add(cur.toString().trim());
        List<String> out = new ArrayList<>();
        for (String s : sentences) {
            if (s.isEmpty()) continue;
            if (s.length() <= maxChars) {
                out.add(s);
            } else {
                out.addAll(hardSplit(s, maxChars));
            }
        }
        return out;
    }

    /** 超长句：优先在逗号等软停顿处切，实在没有就按长度硬切。 */
    private static List<String> hardSplit(String sentence, int maxChars) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < sentence.length(); i++) {
            char c = sentence.charAt(i);
            cur.append(c);
            boolean soft = SOFT_BREAK.indexOf(c) >= 0;
            if (cur.length() >= maxChars || (soft && cur.length() >= maxChars / 2)) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        List<String> trimmed = new ArrayList<>(out.size());
        for (String s : out) if (!s.isEmpty()) trimmed.add(s);
        return trimmed;
    }
}
