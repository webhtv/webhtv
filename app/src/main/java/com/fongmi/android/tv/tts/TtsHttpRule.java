package com.fongmi.android.tv.tts;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 在线朗读引擎 URL 规则（legado 兼容子集）。
 *
 * 支持 legado 在线朗读规则的常用形态：
 * <pre>
 * https://host/path?text={{java.encodeURI(speakText)}}&amp;spd={{speakSpeed}}
 * http://tts.baidu.com/text2audio,{
 *     "method": "POST",
 *     "body": "tex={{java.encodeURI(java.encodeURI(speakText))}}&amp;spd={{(speakSpeed + 5) / 10 + 4}}",
 *     "headers": {"User-Agent": "..."}
 * }
 * </pre>
 *
 * 支持的模板表达式：
 * - 变量：{@code speakText}、{@code speakSpeed}
 * - 函数：{@code java.encodeURI(<expr>)}（等价 JS encodeURI，整体转义后保留保留字符）
 * - 包装：{@code String(<expr>)}
 * - 算术：{@code + - * / ( )} 与数字常量
 *
 * 不支持（构造时即判定不可用，避免运行期静默出错）：{@code @js:} 规则、
 * {@code loginUrl} / {@code loginCheckJs} / {@code jsLib}。
 */
public final class TtsHttpRule {

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";

    private final String raw;
    private final String url;
    private final String method;
    private final String body;
    private final Map<String, String> headers;
    private final String contentType;
    private final String unsupported;

    private TtsHttpRule(String raw, String url, String method, String body,
                        Map<String, String> headers, String contentType, String unsupported) {
        this.raw = raw == null ? "" : raw;
        this.url = url == null ? "" : url;
        this.method = method;
        this.body = body;
        this.headers = headers;
        this.contentType = contentType;
        this.unsupported = unsupported;
    }

    /** 解析引擎规则；参数非法时构造出的对象 {@link #isUsable()} 为 false。 */
    public static TtsHttpRule parse(String rule, String contentType) {
        String text = rule == null ? "" : rule.trim();
        if (text.isEmpty()) return unusable(rule, contentType, "引擎规则为空");
        if (text.startsWith("@js:")) return unusable(rule, contentType, "暂不支持 @js: 规则引擎");
        String url = text;
        String method = METHOD_GET;
        String body = null;
        Map<String, String> headers = new LinkedHashMap<>();
        int split = indexOfOptions(text);
        if (split > 0) {
            url = text.substring(0, split).trim();
            JsonObject options = parseObject(text.substring(split + 1));
            if (options == null) return unusable(rule, contentType, "规则 JSON 解析失败");
            if (options.has("method") && !options.get("method").isJsonNull()) {
                method = options.get("method").getAsString().trim().toUpperCase(Locale.ROOT);
            }
            if (options.has("body") && !options.get("body").isJsonNull()) {
                JsonObject bodyObject = options.get("body").isJsonObject() ? options.getAsJsonObject("body") : null;
                body = bodyObject != null ? new Gson().toJson(bodyObject) : options.get("body").getAsString();
            }
            if (options.has("headers") && options.get("headers").isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : options.getAsJsonObject("headers").entrySet()) {
                    if (e.getValue() == null || e.getValue().isJsonNull()) continue;
                    headers.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
        if (url.isEmpty()) return unusable(rule, contentType, "引擎地址为空");
        if (METHOD_POST.equals(method) && (body == null || body.isEmpty())) {
            return unusable(rule, contentType, "POST 规则缺少 body");
        }
        return new TtsHttpRule(rule, url, method, body, headers, contentType, null);
    }

    /**
     * 找到 URL 与 JSON 选项的分界点（legado 的 {@code url,{...}} 形式）。
     * 只有当 URL 部分不含 {@code {}} 且逗号后紧跟 JSON 对象时才算选项分界，
     * 否则把整串当作 URL（URL 自身可能带逗号，如 gettts 的查询串）。
     */
    private static int indexOfOptions(String text) {
        // 从右往左找：URL 模板里也可能出现逗号（{java.encodeURI(...)}），
        // 只有「逗号后面的整段能解析成 JSON 对象」才是选项分界。
        int comma = text.lastIndexOf(',');
        while (comma >= 0) {
            String rest = text.substring(comma + 1).trim();
            if (rest.startsWith("{") && parseObject(rest) != null) return comma;
            comma = text.lastIndexOf(',', comma - 1);
        }
        return -1;
    }

    /** 解析 JSON 对象；失败返回 null（不抛出，交给调用方给出可读原因）。 */
    static JsonObject parseObject(String json) {
        if (json == null) return null;
        try {
            com.google.gson.JsonElement element = JsonParser.parseString(json.trim());
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static TtsHttpRule unusable(String rule, String contentType, String reason) {
        return new TtsHttpRule(rule, "", METHOD_GET, null, new LinkedHashMap<>(), contentType, reason);
    }

    public boolean isUsable() {
        return unsupported == null;
    }

    public String unsupportedReason() {
        return unsupported;
    }

    public String raw() {
        return raw;
    }

    public String contentType() {
        return contentType;
    }

    public String method() {
        return method;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String url(String speakText, int speakSpeed) {
        return render(url, speakText, speakSpeed);
    }

    public String body(String speakText, int speakSpeed) {
        return body == null ? null : render(body, speakText, speakSpeed);
    }

    /** 渲染模板：把 {{表达式}} 求值后替换。 */
    public static String render(String template, String speakText, int speakSpeed) {
        if (template == null) return null;
        StringBuilder out = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("{{", i);
            if (start < 0) {
                out.append(template, i, template.length());
                break;
            }
            int end = template.indexOf("}}", start + 2);
            if (end < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, start);
            String expr = template.substring(start + 2, end);
            out.append(evaluateExpression(expr, speakText, speakSpeed));
            i = end + 2;
        }
        return out.toString();
    }

    private static String evaluateExpression(String expr, String speakText, int speakSpeed) {
        String e = expr == null ? "" : expr.trim();
        if (e.isEmpty()) return "";
        if (e.equals("speakText")) return speakText == null ? "" : speakText;
        if (e.equals("speakSpeed")) return String.valueOf(speakSpeed);
        if (e.startsWith("String(") && e.endsWith(")")) {
            String inner = e.substring("String(".length(), e.length() - 1).trim();
            Double value = tryEvaluateNumber(inner, speakText, speakSpeed);
            return value != null ? numberToString(value) : evaluateExpression(inner, speakText, speakSpeed);
        }
        if (e.startsWith("java.encodeURI(") && e.endsWith(")")) {
            String inner = e.substring("java.encodeURI(".length(), e.length() - 1).trim();
            return encodeUri(evaluateExpression(inner, speakText, speakSpeed));
        }
        Double value = tryEvaluateNumber(e, speakText, speakSpeed);
        if (value != null) return numberToString(value);
        return "{{" + e + "}}";
    }

    /** 简单递归下降算术求值；含未知标识符时返回 null（保持原文）。 */
    private static Double tryEvaluateNumber(String expr, String speakText, int speakSpeed) {
        try {
            ArithmeticParser parser = new ArithmeticParser(expr, speakSpeed);
            double v = parser.parse();
            if (!parser.isConsumed()) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String numberToString(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        String s = String.format(Locale.US, "%.4f", value);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 等价 JS encodeURI：保留 URI 保留字与未保留字，其余按 UTF-8 转义为大写十六进制。 */
    public static String encodeUri(String value) {
        if (value == null) return "";
        // URLEncoder 把空格编成 '+'，而 JS encodeURI 是 %20：先对齐空格，
        // 否则模板拼出的查询串在按 JS 语义解析的服务端会得到字面加号。
        String encoded = urlEncode(value).replace("+", "%20");
        StringBuilder sb = new StringBuilder(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%' && i + 2 < encoded.length()) {
                sb.append('%').append(Character.toUpperCase(encoded.charAt(i + 1)))
                        .append(Character.toUpperCase(encoded.charAt(i + 2)));
                i += 2;
            } else {
                sb.append(c);
            }
        }
        // URLEncoder 把 JS 视为保留的字符也转义了，这里还原，避免百度等接口解析差异
        return sb.toString()
                .replace("%2A", "*").replace("%2D", "-").replace("%2E", ".")
                .replace("%5F", "_").replace("%21", "!").replace("%7E", "~")
                .replace("%27", "'").replace("%28", "(").replace("%29", ")")
                .replace("%3B", ";").replace("%2C", ",").replace("%2F", "/")
                .replace("%3F", "?").replace("%3A", ":").replace("%40", "@")
                .replace("%26", "&").replace("%3D", "=").replace("%2B", "+")
                .replace("%24", "$").replace("%23", "#");
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    /** 只支持 speakSpeed 变量的整数算术，够用且能挡住任意表达式注入。 */
    private static final class ArithmeticParser {
        private final String s;
        private final int speakSpeed;
        private int pos;

        ArithmeticParser(String s, int speakSpeed) {
            this.s = s;
            this.speakSpeed = speakSpeed;
        }

        boolean isConsumed() {
            skipSpace();
            return pos >= s.length();
        }

        double parse() {
            double v = expression();
            return v;
        }

        private double expression() {
            double v = term();
            while (true) {
                skipSpace();
                if (accept('+')) v += term();
                else if (accept('-')) v -= term();
                else return v;
            }
        }

        private double term() {
            double v = factor();
            while (true) {
                skipSpace();
                if (accept('*')) v *= factor();
                else if (accept('/')) v /= factor();
                else return v;
            }
        }

        private double factor() {
            skipSpace();
            if (accept('(')) {
                double v = expression();
                skipSpace();
                if (!accept(')')) throw new IllegalArgumentException("missing )");
                return v;
            }
            if (accept('-')) return -factor();
            if (accept('+')) return factor();
            if (pos < s.length() && s.startsWith("speakSpeed", pos)) {
                pos += "speakSpeed".length();
                return speakSpeed;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("unexpected token");
            return Double.parseDouble(s.substring(start, pos));
        }

        private void skipSpace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private boolean accept(char c) {
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }
    }
}
