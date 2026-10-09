package com.fongmi.android.tv.player.karaoke;

import android.text.Html;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.CookieManager;

import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KaraokeTrackRepository {

    static final long MAX_REMOTE_BYTES = 512L * 1024L;
    private static final long MAX_SIDECAR_BYTES = 512L * 1024L;
    private static final long MAX_MIDI_BYTES = 2L * 1024L * 1024L;
    private static final long SEARCH_CACHE_MS = TimeUnit.MINUTES.toMillis(10);
    private static final Pattern USDB_ID = Pattern.compile("(?i)(?:usdb\\.animux\\.de[\\s\\S]*?[?&]id=|\\busdb\\s*[:#]?\\s*|^)(\\d{3,6})(?:\\D|$)");
    private static final Pattern USDB_FIELD = Pattern.compile("(?is)<tr\\s+class=\"list_tr[12]\"\\s*>\\s*<td>\\s*%s\\s*</td>\\s*<td>(.*?)</td>");
    private static final Pattern USDB_NOTE = Pattern.compile("giveinfo0\\('([^']*)','(-?\\d+)','(-?\\d+)','(-?\\d+)','([^']*)','([^']*)'\\)");
    private static final Pattern GITHUB_BLOB = Pattern.compile("(?i)^https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$");
    private static final Map<String, SearchCache> SEARCH_CACHE = new HashMap<>();
    private static final KaraokeTrackProvider[] PROVIDERS = new KaraokeTrackProvider[]{
            new KaraokeGithubTrackProvider(),
            new KaraokeUltraStarEsProvider(),
            new KaraokeUsdbProvider()
    };
    private static final OkHttpClient CLIENT = OkHttp.client()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public void load(PlayerManager player, Consumer<KaraokeTrack> callback) {
        String url = player == null ? null : player.getUrl();
        String title = getTitle(player);
        String artist = getArtist(player);
        String signature = signatureOf(player);
        String keySignature = fallbackKeySignature(player, signature);
        String legacySignature = legacySignatureOf(player);
        Task.execute(() -> {
            KaraokeTrack track = load(url, title, artist, signature, keySignature, legacySignature);
            if (callback != null) App.post(() -> callback.accept(track));
        });
    }

    public KaraokeTrack load(String url, String title) {
        return load(url, title, null, null, null, null);
    }

    public KaraokeTrack load(String url, String title, String signature) {
        return load(url, title, null, signature, null, null);
    }

    private KaraokeTrack load(String url, String title, String artist, String signature, String keySignature, String legacySignature) {
        for (String candidate : signatureCandidates(signature, keySignature, legacySignature)) {
            KaraokeTrack bound = readTrack(boundFile(candidate));
            if (bound != null && bound.hasScoredNotes()) return bound;
        }
        File audio = resolveLocalFile(url);
        if (audio != null && audio.isFile()) {
            for (File candidate : candidates(audio, title)) {
                KaraokeTrack track = readTrack(candidate);
                if (track != null && track.hasScoredNotes()) return track;
            }
        }
        for (String candidate : signatureCandidates(signature, keySignature, legacySignature)) {
            KaraokeTrack generatedPitch = readTrack(generatedPitchFile(candidate));
            if (generatedPitch != null && generatedPitch.hasScoredNotes()) return generatedPitch;
        }
        for (String candidate : signatureCandidates(signature, keySignature, legacySignature)) {
            KaraokeTrack generated = readTrack(generatedFile(candidate));
            if (generated != null && generated.hasScoredNotes()) return generated;
        }
        KaraokeTrack migrated = readMatchingTrack(title, artist, signature);
        if (migrated != null && migrated.hasScoredNotes()) return migrated;
        return null;
    }

    public static MediaInput snapshot(PlayerManager player) {
        if (player == null || player.isEmpty()) return MediaInput.empty();
        String key = player.getKey();
        String url = player.getUrl();
        long duration = Math.max(0, player.getDuration());
        String title = getTitle(player);
        String artist = getArtist(player);
        Map<String, String> headers = player.getHeaders() == null ? new HashMap<>() : new HashMap<>(player.getHeaders());
        return new MediaInput(key, url, title, artist, duration, headers);
    }

    public static ImportResult importText(PlayerManager player, String name, String text) {
        if (player == null || player.isEmpty()) return ImportResult.fail("empty player");
        return importText(signatureOf(player), name, text);
    }

    public static ImportResult importGenerated(PlayerManager player, List<LyricsLine> lines) {
        if (player == null || player.isEmpty()) return ImportResult.fail("empty player");
        try {
            String signature = signatureOf(player);
            String text = KaraokeGeneratedTrackBuilder.build(defaultKeyword(player), getArtist(player), lines, player.getDuration());
            deleteGeneratedBoundIfAny(signature, fallbackKeySignature(player, signature), legacySignatureOf(player));
            return importText(generatedFile(signature), "Generated rhythm scoring track", text);
        } catch (Exception e) {
            android.util.Log.e("karaoke-generate", "rhythm generation failed url=" + player.getUrl() + " duration=" + player.getDuration() + " lines=" + (lines == null ? 0 : lines.size()), e);
            return ImportResult.fail(e.getMessage());
        }
    }

    public static ImportResult importGeneratedPitch(PlayerManager player, List<LyricsLine> lines) {
        return importGeneratedPitch(snapshot(player), lines);
    }

    public static ImportResult importGeneratedPitch(MediaInput input, List<LyricsLine> lines) {
        return importGeneratedPitch(input, lines, null);
    }

    public static ImportResult importGeneratedPitch(MediaInput input, List<LyricsLine> lines, KaraokePitchTrackGenerator.Progress progress) {
        if (input == null || input.isEmpty()) return ImportResult.fail("empty player");
        try {
            String signature = input.getSignature();
            String text = PlayerSetting.isKaraokeBasicPitchTflite()
                    ? BasicPitchTfliteGenerator.build(input, lines, progress)
                    : KaraokePitchTrackGenerator.build(input, lines, progress);
            deleteGeneratedBoundIfAny(signature, input.getLegacySignature());
            delete(generatedFile(signature));
            delete(generatedFile(input.getLegacySignature()));
            return importText(generatedPitchFile(signature), "Generated pitch scoring track", text);
        } catch (Exception e) {
            android.util.Log.e("karaoke-generate", "pitch generation failed basicPitch=" + PlayerSetting.isKaraokeBasicPitchTflite() + " url=" + input.getUrl() + " duration=" + input.getDuration() + " headers=" + input.getHeaders().size() + " lines=" + (lines == null ? 0 : lines.size()), e);
            return ImportResult.fail(e.getMessage());
        }
    }

    public static ImportResult importAutoGenerated(PlayerManager player, List<LyricsLine> lines) {
        if (player == null || player.isEmpty()) return ImportResult.fail("empty player");
        try {
            String signature = signatureOf(player);
            String text = KaraokeGeneratedTrackBuilder.build(defaultKeyword(player), getArtist(player), lines, player.getDuration());
            return importText(generatedFile(signature), "Auto generated rhythm scoring track", text);
        } catch (Exception e) {
            return ImportResult.fail(e.getMessage());
        }
    }

    public static boolean canGenerate(List<LyricsLine> lines) {
        return KaraokeGeneratedTrackBuilder.canGenerate(lines);
    }

    public static boolean canGeneratePitch(PlayerManager player, List<LyricsLine> lines) {
        return canGeneratePitch(snapshot(player), lines);
    }

    public static boolean canGeneratePitch(MediaInput input, List<LyricsLine> lines) {
        return KaraokePitchTrackGenerator.canGenerate(input, lines);
    }

    public static boolean isUnsupportedPitchSourceError(String error) {
        return KaraokeAudioExtractor.isUnsupportedError(error);
    }

    public static ImportResult importFile(PlayerManager player, File file) {
        if (player == null || player.isEmpty()) return ImportResult.fail("empty player");
        if (file == null || !file.isFile()) return ImportResult.fail("empty file");
        try {
            byte[] bytes = readBytes(file, MAX_MIDI_BYTES);
            String text = MidiKaraokeParser.looksLikeMidi(bytes)
                    ? MidiKaraokeParser.toUltraStar(file.getName(), bytes)
                    : readText(file);
            return importText(signatureOf(player), file.getName(), text);
        } catch (Exception e) {
            return ImportResult.fail(e.getMessage());
        }
    }

    public static ImportResult importText(String signature, String name, String text) {
        return importText(boundFile(signature), name, text);
    }

    private static ImportResult importText(File target, String name, String text) {
        try {
            if (target == null) return ImportResult.fail("empty signature");
            if (TextUtils.isEmpty(text)) return ImportResult.fail("empty track");
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_SIDECAR_BYTES) return ImportResult.fail("track too large");
            if (!UltraStarParser.looksLikeUltraStar(text)) return ImportResult.fail("not ultrastar");
            KaraokeTrack track = UltraStarParser.parse(text);
            if (track == null || !track.hasScoredNotes()) return ImportResult.fail("no scored notes");
            Path.write(target, normalizeText(text).getBytes(StandardCharsets.UTF_8));
            return ImportResult.success(track, name);
        } catch (Exception e) {
            return ImportResult.fail(e.getMessage());
        }
    }

    public static void importUrl(PlayerManager player, String url, Consumer<ImportResult> callback) {
        String signature = signatureOf(player);
        Task.execute(() -> {
            ImportResult result;
            try {
                result = importText(signature, url, getTrackText(url));
            } catch (Throwable e) {
                result = ImportResult.fail(e.getMessage());
            }
            ImportResult finalResult = result;
            if (callback != null) App.post(() -> callback.accept(finalResult));
        });
    }

    public static void search(PlayerManager player, String keyword, Consumer<List<SearchResult>> callback) {
        String query = TextUtils.isEmpty(keyword) ? defaultKeyword(player) : keyword.trim();
        String cacheKey = cacheKey(query);
        List<SearchResult> cached = getSearchCache(cacheKey);
        if (cached != null) {
            if (callback != null) App.post(() -> callback.accept(cached));
            return;
        }
        Task.execute(() -> {
            List<SearchResult> results = new ArrayList<>();
            for (KaraokeTrackProvider provider : PROVIDERS) {
                try {
                    addUnique(results, provider.search(query));
                } catch (Exception ignored) {
                }
            }
            putSearchCache(cacheKey, results);
            if (callback != null) App.post(() -> callback.accept(results));
        });
    }

    public static void clearSearchCache() {
        synchronized (SEARCH_CACHE) {
            SEARCH_CACHE.clear();
        }
    }

    public static boolean clearCache() {
        File dir = trackDir();
        File[] files = dir.listFiles();
        if (files == null) return false;
        boolean success = true;
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".generated.txt") && !name.endsWith(".generated-pitch.txt")) continue;
            if (!file.delete()) success = false;
        }
        clearSearchCache();
        return success;
    }

    public static String defaultKeyword(PlayerManager player) {
        String title = getTitle(player);
        if (!TextUtils.isEmpty(title)) return stripExtension(title);
        if (player == null) return "";
        return stripExtension(player.getKey());
    }

    public static String identityOf(PlayerManager player) {
        return signatureOf(player);
    }

    public static boolean hasBinding(PlayerManager player) {
        String signature = signatureOf(player);
        String keySignature = fallbackKeySignature(player, signature);
        String legacySignature = legacySignatureOf(player);
        for (String candidate : signatureCandidates(signature, keySignature, legacySignature)) {
            if (hasFile(boundFile(candidate)) || hasFile(generatedPitchFile(candidate)) || hasFile(generatedFile(candidate))) return true;
        }
        return findMatchingTrackFile(getTitle(player), getArtist(player)) != null;
    }

    public static boolean clearBinding(PlayerManager player) {
        String signature = signatureOf(player);
        String keySignature = fallbackKeySignature(player, signature);
        String legacySignature = legacySignatureOf(player);
        boolean deleted = false;
        for (String candidate : signatureCandidates(signature, keySignature, legacySignature)) {
            deleted |= delete(boundFile(candidate)) | delete(generatedPitchFile(candidate)) | delete(generatedFile(candidate));
        }
        File file = findMatchingTrackFile(getTitle(player), getArtist(player));
        if (file != null) deleted |= delete(file);
        return deleted;
    }

    private static KaraokeTrack readTrack(File file) {
        try {
            if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_SIDECAR_BYTES) return null;
            String text = readText(file);
            if (!UltraStarParser.looksLikeUltraStar(text)) return null;
            KaraokeTrack track = UltraStarParser.parse(text);
            return track.hasScoredNotes() ? track : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static KaraokeTrack readMatchingTrack(String title, String artist, String signature) {
        Match match = findMatchingTrack(title, artist);
        if (match == null || TextUtils.isEmpty(signature)) return match == null ? null : match.track;
        File target = migratedFile(signature, match.file.getName());
        if (target != null && !sameFile(match.file, target)) {
            try {
                Path.write(target, normalizeText(match.text).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        return match.track;
    }

    private static File findMatchingTrackFile(String title, String artist) {
        Match match = findMatchingTrack(title, artist);
        return match == null ? null : match.file;
    }

    private static Match findMatchingTrack(String title, String artist) {
        File[] files = trackDir().listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) return null;
        Match best = null;
        for (File file : files) {
            try {
                if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_SIDECAR_BYTES) continue;
                String text = readText(file);
                if (!UltraStarParser.looksLikeUltraStar(text)) continue;
                KaraokeTrack track = UltraStarParser.parse(text);
                if (track == null || !track.hasScoredNotes()) continue;
                int score = trackMatchScore(title, artist, track);
                if (score < 80) continue;
                if (best == null || score > best.score || score == best.score && file.lastModified() > best.file.lastModified()) best = new Match(file, text, track, score);
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    private static int trackMatchScore(String title, String artist, KaraokeTrack track) {
        String wantedTitle = normalizeSearch(stripExtension(title));
        String wantedArtist = normalizeSearch(artist);
        String trackTitle = normalizeSearch(stripExtension(track.getTitle()));
        String trackArtist = normalizeSearch(track.getArtist());
        int score = 0;
        if (relatedText(wantedTitle, trackTitle)) score += 60;
        if (relatedText(wantedArtist, trackArtist)) score += 45;
        if (relatedText(wantedTitle, trackArtist)) score += 40;
        if (relatedText(wantedArtist, trackTitle)) score += 25;
        if (relatedText(wantedArtist + wantedTitle, trackArtist + trackTitle)) score += 30;
        return score;
    }

    private static boolean relatedText(String first, String second) {
        if (TextUtils.isEmpty(first) || TextUtils.isEmpty(second)) return false;
        return first.equals(second) || first.contains(second) || second.contains(first);
    }

    private static File migratedFile(String signature, String name) {
        if (TextUtils.isEmpty(signature)) return null;
        if (name != null && name.endsWith(".generated-pitch.txt")) return generatedPitchFile(signature);
        if (name != null && name.endsWith(".generated.txt")) return generatedFile(signature);
        return boundFile(signature);
    }

    private static String getTrackText(String url) throws Exception {
        RemoteUrl remote = RemoteUrl.of(url);
        String usdbId = parseUsdbId(remote.url);
        if (!TextUtils.isEmpty(usdbId)) return getUsdbTrackText(usdbId, remote.cookie);
        return getRemoteText(remote.url, remote.cookie);
    }

    static String getRemoteText(String url, String cookie) throws Exception {
        if (!isHttpUrl(url)) throw new IllegalArgumentException("invalid url");
        Request.Builder builder = new Request.Builder().url(url.trim()).header("User-Agent", userAgent());
        String actualCookie = !TextUtils.isEmpty(cookie) ? cookie : webCookie(url);
        if (!TextUtils.isEmpty(actualCookie)) builder.header("Cookie", actualCookie);
        return readResponseText(builder.build());
    }

    static String postRemoteText(String url, Map<String, String> form, String cookie) throws Exception {
        if (!isHttpUrl(url)) throw new IllegalArgumentException("invalid url");
        FormBody.Builder body = new FormBody.Builder();
        if (form != null) for (Map.Entry<String, String> entry : form.entrySet()) body.add(entry.getKey(), entry.getValue());
        Request.Builder builder = new Request.Builder().url(url.trim()).header("User-Agent", userAgent()).post(body.build());
        String actualCookie = !TextUtils.isEmpty(cookie) ? cookie : webCookie(url);
        if (!TextUtils.isEmpty(actualCookie)) builder.header("Cookie", actualCookie);
        return readResponseText(builder.build());
    }

    private static String readResponseText(Request request) throws Exception {
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
            long length = response.body().contentLength();
            if (length > MAX_REMOTE_BYTES) throw new IllegalStateException("track too large");
            String type = response.header("Content-Type", "");
            String text = response.body().string();
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_REMOTE_BYTES) throw new IllegalStateException("track too large");
            String lowerType = type.toLowerCase(Locale.ROOT);
            boolean textual = lowerType.contains("text") || lowerType.contains("html") || lowerType.contains("xml") || lowerType.contains("json");
            if (!TextUtils.isEmpty(type) && !textual && !UltraStarParser.looksLikeUltraStar(text)) throw new IllegalStateException("not text");
            return text;
        }
    }

    private static String getUsdbTrackText(String id, String cookie) throws Exception {
        String detail = getRemoteText("https://usdb.animux.de/?link=detail&id=" + id, cookie);
        String bpm = parseDetailField(detail, "BPM");
        String gap = parseDetailField(detail, "GAP");
        if (TextUtils.isEmpty(bpm) || TextUtils.isEmpty(gap)) throw new IllegalStateException("missing USDB timing");
        String title = parseTitle(detail);
        String notes = getRemoteText("https://usdb.animux.de/view.php?id=" + id + "&database1=deluxe_songs", cookie);
        Matcher matcher = USDB_NOTE.matcher(notes);
        StringBuilder builder = new StringBuilder();
        builder.append("#TITLE:").append(parseSong(title)).append('\n');
        builder.append("#ARTIST:").append(parseArtist(title)).append('\n');
        builder.append("#BPM:").append(bpm).append('\n');
        builder.append("#GAP:").append(gap).append('\n');
        int count = 0;
        while (matcher.find()) {
            String type = matcher.group(1);
            String start = matcher.group(2);
            String length = matcher.group(3);
            String pitch = matcher.group(4);
            String lyric = html(matcher.group(6)).replace('\n', ' ').trim();
            builder.append(usdbPrefix(type)).append(' ')
                    .append(start).append(' ')
                    .append(length).append(' ')
                    .append(pitch).append(' ')
                    .append(lyric).append('\n');
            count++;
        }
        if (count == 0) throw new IllegalStateException("no USDB notes");
        builder.append('E').append('\n');
        return builder.toString();
    }

    static String parseUsdbId(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher matcher = USDB_ID.matcher(text.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    static String parseDetailField(String html, String field) {
        Matcher matcher = Pattern.compile(String.format(Locale.US, USDB_FIELD.pattern(), Pattern.quote(field))).matcher(html);
        return matcher.find() ? html(matcher.group(1)).trim() : "";
    }

    static String parseTitle(String html) {
        String title = find(Pattern.compile("(?is)<title>\\s*USDB\\s*-\\s*(.*?)\\s*</title>"), html, 1);
        if (!TextUtils.isEmpty(title)) return html(title);
        return html(find(Pattern.compile("(?is)<th[^>]*>\\s*<span[^>]*>\\s*<b>(.*?)</b>"), html, 1));
    }

    static String parseArtist(String value) {
        String text = value == null ? "" : value.trim();
        int index = text.indexOf(" - ");
        return index > 0 ? text.substring(0, index).trim() : "";
    }

    static String parseSong(String value) {
        String text = value == null ? "" : value.trim();
        int index = text.indexOf(" - ");
        return index > 0 && index + 3 < text.length() ? text.substring(index + 3).trim() : text;
    }

    private static char usdbPrefix(String type) {
        if ("golden".equalsIgnoreCase(type)) return '*';
        if ("freestyle".equalsIgnoreCase(type)) return 'F';
        if ("rap".equalsIgnoreCase(type)) return 'R';
        return ':';
    }

    private static List<File> candidates(File audio, String title) {
        List<File> files = new ArrayList<>();
        File dir = audio.getParentFile();
        if (dir == null || !dir.isDirectory()) return files;
        Set<String> bases = new LinkedHashSet<>();
        addBase(bases, stripExtension(audio.getName()));
        addBase(bases, stripExtension(title));
        for (String base : bases) {
            files.add(new File(dir, base + ".ultrastar.txt"));
            files.add(new File(dir, base + ".karaoke.txt"));
            files.add(new File(dir, base + ".usdx.txt"));
            files.add(new File(dir, base + ".txt"));
        }
        return files;
    }

    private static void addBase(Set<String> bases, String value) {
        String base = value == null ? "" : value.trim();
        if (!base.isEmpty()) bases.add(base);
    }

    private static File resolveLocalFile(String url) {
        if (TextUtils.isEmpty(url)) return null;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (TextUtils.isEmpty(scheme)) return new File(url);
            if ("file".equalsIgnoreCase(scheme)) {
                String path = Uri.decode(uri.getPath());
                return TextUtils.isEmpty(path) ? null : new File(path);
            }
            return null;
        } catch (Exception e) {
            return new File(url);
        }
    }

    private static String getTitle(PlayerManager player) {
        if (player == null) return "";
        MediaMetadata metadata = player.getMetadata();
        if (metadata != null && metadata.title != null) return metadata.title.toString();
        return "";
    }

    private static String getArtist(PlayerManager player) {
        if (player == null) return "";
        MediaMetadata metadata = player.getMetadata();
        if (metadata != null && metadata.artist != null) return metadata.artist.toString();
        return "";
    }

    private static String signatureOf(PlayerManager player) {
        if (player == null) return "";
        String signature = metadataSignatureOf(getTitle(player), getArtist(player));
        if (!TextUtils.isEmpty(signature)) return signature;
        signature = keySignatureOf(player.getKey());
        return !TextUtils.isEmpty(signature) ? signature : legacySignatureOf(player);
    }

    private static String fallbackKeySignature(PlayerManager player, String signature) {
        String keySignature = keySignatureOf(player == null ? null : player.getKey());
        return TextUtils.equals(signature, keySignature) ? keySignature : "";
    }

    private static String legacySignatureOf(PlayerManager player) {
        if (player == null) return "";
        return legacySignatureOf(player.getKey(), player.getUrl(), player.getDuration());
    }

    private static String legacySignatureOf(String key, String url, long duration) {
        return Util.md5(safe(key) + "|" + safe(url) + "|" + duration);
    }

    private static String keySignatureOf(String key) {
        String mediaKey = safe(key).trim();
        return TextUtils.isEmpty(mediaKey) ? "" : Util.md5("karaoke-v2|key|" + mediaKey);
    }

    private static String metadataSignatureOf(String title, String artist) {
        String name = normalizeSearch(stripExtension(title));
        String singer = normalizeSearch(artist);
        if (TextUtils.isEmpty(name) && TextUtils.isEmpty(singer)) return "";
        return Util.md5("karaoke-v2|meta|" + name + "|" + singer);
    }

    private static List<String> signatureCandidates(String... signatures) {
        ArrayList<String> values = new ArrayList<>();
        if (signatures != null) for (String signature : signatures) addSignature(values, signature);
        return values;
    }

    private static void addSignature(List<String> values, String signature) {
        if (TextUtils.isEmpty(signature) || values.contains(signature)) return;
        values.add(signature);
    }

    private static File boundFile(String signature) {
        if (TextUtils.isEmpty(signature)) return null;
        return new File(trackDir(), signature + ".txt");
    }

    private static File generatedFile(String signature) {
        if (TextUtils.isEmpty(signature)) return null;
        return new File(trackDir(), signature + ".generated.txt");
    }

    private static File generatedPitchFile(String signature) {
        if (TextUtils.isEmpty(signature)) return null;
        return new File(trackDir(), signature + ".generated-pitch.txt");
    }

    private static File trackDir() {
        File dir = Path.cache("karaoke_tracks");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static void deleteGeneratedBoundIfAny(String... signatures) {
        if (signatures == null) return;
        for (String signature : signatures) {
            File file = boundFile(signature);
            try {
                if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_SIDECAR_BYTES) continue;
                String text = readText(file);
                if (text.contains("Generated rhythm scoring track") || text.contains("Generated pitch scoring track") || text.contains("Generated experimental pitch scoring track")) delete(file);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean hasFile(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }

    private static boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }

    private static boolean sameFile(File first, File second) {
        if (first == null || second == null) return false;
        return first.getAbsolutePath().equals(second.getAbsolutePath());
    }

    private static boolean isHttpUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String url = value.trim().toLowerCase();
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private static String userAgent() {
        return "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    }

    private static String webCookie(String url) {
        try {
            return CookieManager.getInstance().getCookie(url);
        } catch (Exception e) {
            return "";
        }
    }

    static String encode(String text) throws Exception {
        return URLEncoder.encode(text, "UTF-8");
    }

    static String absoluteUrl(String base, String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (path.startsWith("//")) return "https:" + path;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    static String find(Pattern pattern, String text, int group) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group) : "";
    }

    static String findLast(Pattern pattern, String text, int group) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher matcher = pattern.matcher(text);
        String value = "";
        while (matcher.find()) value = matcher.group(group);
        return value;
    }

    static String html(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00A0', ' ').replace('\u3000', ' ').trim();
    }

    static String normalizeSearch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static String cacheKey(String query) {
        String normalized = normalizeSearch(query);
        return TextUtils.isEmpty(normalized) ? safe(query).trim().toLowerCase(Locale.ROOT) : normalized;
    }

    private static List<SearchResult> getSearchCache(String key) {
        if (TextUtils.isEmpty(key)) return null;
        synchronized (SEARCH_CACHE) {
            SearchCache cache = SEARCH_CACHE.get(key);
            if (cache == null) return null;
            if (System.currentTimeMillis() - cache.timestampMs > SEARCH_CACHE_MS) {
                SEARCH_CACHE.remove(key);
                return null;
            }
            return new ArrayList<>(cache.results);
        }
    }

    private static void putSearchCache(String key, List<SearchResult> results) {
        if (TextUtils.isEmpty(key) || results == null || results.isEmpty()) return;
        synchronized (SEARCH_CACHE) {
            SEARCH_CACHE.put(key, new SearchCache(results));
        }
    }

    private static void addUnique(List<SearchResult> target, List<SearchResult> source) {
        if (source == null || source.isEmpty()) return;
        Set<String> exists = new LinkedHashSet<>();
        for (SearchResult result : target) exists.add(result.getUrl());
        for (SearchResult result : source) {
            if (result == null || TextUtils.isEmpty(result.getUrl()) || exists.contains(result.getUrl())) continue;
            target.add(result);
            exists.add(result.getUrl());
        }
    }

    static String emptyDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim() + "\n";
    }

    private static String stripExtension(String name) {
        String value = name == null ? "" : name.trim();
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int dot = value.lastIndexOf('.');
        if (dot > 0 && value.length() - dot <= 16) value = value.substring(0, dot);
        return value.trim();
    }

    private static String readText(File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private static byte[] readBytes(File file, long maxBytes) throws Exception {
        if (file.length() > maxBytes) throw new IllegalStateException("track too large");
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return bytes;
        }
    }

    private static class Match {

        private final File file;
        private final String text;
        private final KaraokeTrack track;
        private final int score;

        private Match(File file, String text, KaraokeTrack track, int score) {
            this.file = file;
            this.text = text;
            this.track = track;
            this.score = score;
        }
    }

    private static class SearchCache {

        private final long timestampMs;
        private final List<SearchResult> results;

        private SearchCache(List<SearchResult> results) {
            this.timestampMs = System.currentTimeMillis();
            this.results = new ArrayList<>(results);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static class RemoteUrl {

        private final String url;
        private final String cookie;

        private RemoteUrl(String url, String cookie) {
            this.url = normalize(url == null ? "" : url.trim());
            this.cookie = cookie == null ? "" : cookie.trim();
        }

        private static RemoteUrl of(String value) {
            String text = value == null ? "" : value.trim();
            int index = text.indexOf("@Cookie=");
            if (index < 0) return new RemoteUrl(text, "");
            return new RemoteUrl(text.substring(0, index), text.substring(index + 8));
        }

        private static String normalize(String url) {
            Matcher github = GITHUB_BLOB.matcher(url);
            if (github.find()) return "https://raw.githubusercontent.com/" + github.group(1) + "/" + github.group(2) + "/" + github.group(3) + "/" + github.group(4);
            return url.replace("/-/blob/", "/-/raw/");
        }
    }

    public static class MediaInput {

        private final String key;
        private final String url;
        private final String title;
        private final String artist;
        private final long duration;
        private final Map<String, String> headers;
        private final String signature;
        private final String legacySignature;

        private MediaInput(String key, String url, String title, String artist, long duration, Map<String, String> headers) {
            this.key = safe(key);
            this.url = safe(url);
            this.title = safe(title);
            this.artist = safe(artist);
            this.duration = Math.max(0, duration);
            this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
            this.legacySignature = legacySignatureOf(this.key, this.url, this.duration);
            String stableSignature = metadataSignatureOf(this.title, this.artist);
            if (TextUtils.isEmpty(stableSignature)) stableSignature = keySignatureOf(this.key);
            this.signature = !TextUtils.isEmpty(stableSignature) ? stableSignature : this.legacySignature;
        }

        private static MediaInput empty() {
            return new MediaInput("", "", "", "", 0, null);
        }

        public boolean isEmpty() {
            return TextUtils.isEmpty(url);
        }

        public String getKey() {
            return key;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getKeyword() {
            return !TextUtils.isEmpty(title) ? stripExtension(title) : stripExtension(key);
        }

        public String getArtist() {
            return artist;
        }

        public long getDuration() {
            return duration;
        }

        public Map<String, String> getHeaders() {
            return new HashMap<>(headers);
        }

        public String getSignature() {
            return signature;
        }

        public String getLegacySignature() {
            return legacySignature;
        }
    }

    public static class SearchResult {

        private final String source;
        private final String title;
        private final String artist;
        private final String note;
        private final String url;
        private final boolean loginRequired;

        SearchResult(String source, String title, String artist, String note, String url, boolean loginRequired) {
            this.source = source == null ? "" : source;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.note = note == null ? "" : note;
            this.url = url == null ? "" : url;
            this.loginRequired = loginRequired;
        }

        public String getSource() {
            return source;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
        }

        public String getNote() {
            return note;
        }

        public String getUrl() {
            return url;
        }

        public boolean isLoginRequired() {
            return loginRequired;
        }
    }

    public static class ImportResult {

        private final boolean success;
        private final KaraokeTrack track;
        private final String source;
        private final String error;

        private ImportResult(boolean success, KaraokeTrack track, String source, String error) {
            this.success = success;
            this.track = track;
            this.source = source == null ? "" : source;
            this.error = error == null ? "" : error;
        }

        public static ImportResult success(KaraokeTrack track, String source) {
            return new ImportResult(true, track, source, "");
        }

        public static ImportResult fail(String error) {
            return new ImportResult(false, null, "", error);
        }

        public boolean isSuccess() {
            return success;
        }

        public KaraokeTrack getTrack() {
            return track;
        }

        public String getSource() {
            return source;
        }

        public String getError() {
            return error;
        }
    }
}
