package androidx.media3.mpvplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HlsPlaylistRewriter {

    private static final Pattern URI_ATTRIBUTE = Pattern.compile("URI=\"([^\"]+)\"");

    private HlsPlaylistRewriter() {
    }

    static Result rewrite(String text, Variant inheritedVariant, UriMapper mapper) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 256);
        List<Segment> segments = new ArrayList<>();
        List<MediaUnit> mediaUnits = new ArrayList<>();
        List<VariantEntry> variants = new ArrayList<>();
        Variant pendingVariant = null;
        boolean pendingSegment = false;
        boolean pendingByteRange = false;
        double pendingDuration = 0;
        double elapsed = 0;
        double partialGroupStart = -1;
        for (int i = 0; i < lines.length; i++) {
            String raw = trimCr(lines[i]);
            String line = raw.trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                pendingVariant = parseVariant(line, VariantKind.STREAM);
                out.append(raw);
            } else if (line.startsWith("#") && line.contains("URI=\"")) {
                UriContext context = uriAttributeContext(
                        line, inheritedVariant, elapsed);
                out.append(rewriteUriAttributes(
                        raw, context, mapper, variants, mediaUnits));
                if (context.role() == UriRole.MEDIA_SEGMENT
                        && context.durationSeconds() > 0) {
                    if (partialGroupStart < 0) partialGroupStart = elapsed;
                    elapsed += context.durationSeconds();
                }
            } else if (line.startsWith("#EXTINF:")) {
                pendingSegment = true;
                pendingDuration = parseExtInfDuration(line);
                out.append(raw);
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                pendingByteRange = true;
                out.append(raw);
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                UriRole role = pendingVariant != null ? UriRole.VARIANT_PLAYLIST : pendingSegment ? UriRole.MEDIA_SEGMENT : UriRole.OTHER;
                Variant variant = pendingVariant != null ? pendingVariant : inheritedVariant;
                boolean cacheable = pendingSegment && pendingDuration > 0 && !pendingByteRange;
                double itemStart = pendingSegment && partialGroupStart >= 0
                        ? partialGroupStart : elapsed;
                UriContext context = new UriContext(
                        role, variant, pendingDuration, itemStart,
                        pendingByteRange);
                MappedUri mapped = safeMap(mapper, line, cacheable, context);
                out.append(mapped.rewrittenUri());
                if (role == UriRole.VARIANT_PLAYLIST) variants.add(new VariantEntry(mapped.sourceUri(), variant));
                if (pendingSegment && pendingDuration > 0) {
                    segments.add(new Segment(mapped.sourceUri(),
                            pendingDuration, itemStart, pendingByteRange));
                    mediaUnits.add(new MediaUnit(
                            mapped.sourceUri(), pendingDuration, itemStart,
                            pendingByteRange, false));
                    elapsed = Math.max(elapsed, itemStart + pendingDuration);
                    partialGroupStart = -1;
                }
                pendingVariant = null;
                pendingSegment = false;
                pendingDuration = 0;
                pendingByteRange = false;
            } else {
                out.append(raw);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return new Result(out.toString(), List.copyOf(segments),
                List.copyOf(variants), List.copyOf(mediaUnits));
    }

    private static String rewriteUriAttributes(
            String line,
            UriContext context,
            UriMapper mapper,
            List<VariantEntry> variants,
            List<MediaUnit> mediaUnits) {
        Matcher matcher = URI_ATTRIBUTE.matcher(line);
        StringBuffer buffer = new StringBuffer();
        boolean mediaUnitAdded = false;
        while (matcher.find()) {
            String value = matcher.group(1);
            String replacement = value;
            if (!value.isEmpty() && !value.startsWith("data:")) {
                MappedUri mapped = safeMap(mapper, value, isCacheableUriAttribute(line), context);
                replacement = mapped.rewrittenUri();
                if (context.role() == UriRole.VARIANT_PLAYLIST) variants.add(new VariantEntry(mapped.sourceUri(), context.variant()));
                if (!mediaUnitAdded
                        && context.role() == UriRole.MEDIA_SEGMENT
                        && context.durationSeconds() > 0) {
                    mediaUnits.add(new MediaUnit(
                            mapped.sourceUri(), context.durationSeconds(),
                            context.startSeconds(), context.byteRange(), true));
                    mediaUnitAdded = true;
                }
            }
            matcher.appendReplacement(buffer, "URI=\"" + Matcher.quoteReplacement(replacement) + "\"");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static UriContext uriAttributeContext(
            String line,
            Variant inheritedVariant,
            double startSeconds) {
        String upper = line.toUpperCase(Locale.US);
        if (upper.startsWith("#EXT-X-I-FRAME-STREAM-INF")) {
            return new UriContext(UriRole.VARIANT_PLAYLIST,
                    parseVariant(line, VariantKind.I_FRAME), 0,
                    startSeconds, false);
        }
        if (upper.startsWith("#EXT-X-IMAGE-STREAM-INF")) {
            return new UriContext(UriRole.VARIANT_PLAYLIST,
                    parseVariant(line, VariantKind.IMAGE), 0,
                    startSeconds, false);
        }
        if (upper.startsWith("#EXT-X-KEY")) {
            return new UriContext(UriRole.ENCRYPTION_KEY, inheritedVariant, 0,
                    startSeconds, false);
        }
        if (upper.startsWith("#EXT-X-PART") || (upper.startsWith("#EXT-X-PRELOAD-HINT") && upper.contains("TYPE=PART"))) {
            return new UriContext(
                    UriRole.MEDIA_SEGMENT,
                    inheritedVariant,
                    upper.startsWith("#EXT-X-PART:")
                            ? parseDoubleAttribute(line, "DURATION") : 0,
                    startSeconds,
                    upper.contains("BYTERANGE="));
        }
        return new UriContext(UriRole.OTHER, inheritedVariant, 0,
                startSeconds, false);
    }

    private static boolean isCacheableUriAttribute(String line) {
        String upper = line.toUpperCase(Locale.US);
        return upper.startsWith("#EXT-X-MAP") && !upper.contains("BYTERANGE=");
    }

    private static MappedUri safeMap(UriMapper mapper, String uri, boolean cacheable, UriContext context) {
        MappedUri mapped = mapper.map(uri, cacheable, context);
        if (mapped == null) return new MappedUri(uri, uri);
        String source = mapped.sourceUri() == null || mapped.sourceUri().isEmpty() ? uri : mapped.sourceUri();
        String rewritten = mapped.rewrittenUri() == null || mapped.rewrittenUri().isEmpty() ? uri : mapped.rewrittenUri();
        return new MappedUri(source, rewritten);
    }

    private static Variant parseVariant(String line, VariantKind kind) {
        long bandwidth = parseLongAttribute(line, "BANDWIDTH");
        long averageBandwidth = parseLongAttribute(line, "AVERAGE-BANDWIDTH");
        int[] resolution = parseResolution(attributeValue(line, "RESOLUTION"));
        return new Variant(bandwidth, averageBandwidth, resolution[0], resolution[1], kind);
    }

    private static int[] parseResolution(String value) {
        if (value == null || value.isEmpty()) return new int[]{0, 0};
        String[] parts = value.toLowerCase(Locale.US).split("x", 2);
        if (parts.length < 2) return new int[]{0, 0};
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException ignored) {
            return new int[]{0, 0};
        }
    }

    private static long parseLongAttribute(String line, String name) {
        String value = attributeValue(line, name);
        if (value == null || value.isEmpty()) return 0;
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double parseDoubleAttribute(String line, String name) {
        String value = attributeValue(line, name);
        if (value == null || value.isEmpty()) return 0;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? Math.max(0, parsed) : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String attributeValue(String line, String name) {
        int colon = line.indexOf(':');
        if (colon < 0 || colon >= line.length() - 1) return null;
        for (String part : line.substring(colon + 1).split(",")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String key = part.substring(0, equals).trim();
            if (!name.equalsIgnoreCase(key)) continue;
            String value = part.substring(equals + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            return value;
        }
        return null;
    }

    private static double parseExtInfDuration(String line) {
        try {
            int colon = line.indexOf(':');
            int comma = line.indexOf(',', colon + 1);
            String value = comma >= 0 ? line.substring(colon + 1, comma) : line.substring(colon + 1);
            return Math.max(0, Double.parseDouble(value.trim()));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String trimCr(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }

    interface UriMapper {
        MappedUri map(String uri, boolean cacheable, UriContext context);
    }

    enum UriRole {
        VARIANT_PLAYLIST,
        MEDIA_SEGMENT,
        ENCRYPTION_KEY,
        OTHER
    }

    enum VariantKind {
        STREAM,
        I_FRAME,
        IMAGE
    }

    record UriContext(
            UriRole role,
            Variant variant,
            double durationSeconds,
            double startSeconds,
            boolean byteRange) {

        UriContext {
            role = role == null ? UriRole.OTHER : role;
            durationSeconds = finiteNonNegative(durationSeconds);
            startSeconds = finiteNonNegative(startSeconds);
        }
    }

    record MappedUri(String sourceUri, String rewrittenUri) {
    }

    record Variant(
            long bandwidth,
            long averageBandwidth,
            int width,
            int height,
            VariantKind kind) {

        Variant(long bandwidth, long averageBandwidth, int width, int height) {
            this(bandwidth, averageBandwidth, width, height, VariantKind.STREAM);
        }

        Variant {
            kind = kind == null ? VariantKind.STREAM : kind;
        }
    }

    record VariantEntry(String uri, Variant variant) {
    }

    record Segment(String uri, double durationSeconds, double startSeconds, boolean byteRange) {
        double endSeconds() {
            return startSeconds + durationSeconds;
        }
    }

    record MediaUnit(
            String uri,
            double durationSeconds,
            double startSeconds,
            boolean byteRange,
            boolean partial) {

        MediaUnit {
            uri = uri == null ? "" : uri;
            durationSeconds = finiteNonNegative(durationSeconds);
            startSeconds = finiteNonNegative(startSeconds);
        }

        double endSeconds() {
            return startSeconds + durationSeconds;
        }
    }

    record Result(
            String text,
            List<Segment> segments,
            List<VariantEntry> variants,
            List<MediaUnit> mediaUnits) {
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
