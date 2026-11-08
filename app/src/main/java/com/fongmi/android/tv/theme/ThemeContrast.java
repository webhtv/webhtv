package com.fongmi.android.tv.theme;

final class ThemeContrast {

    private ThemeContrast() {
    }

    static void require(ThemeTokens tokens) {
        require(tokens.colorOnPrimary(), tokens.colorPrimary(), 4.5, "onPrimary/primary");
        require(tokens.colorOnPrimaryContainer(), tokens.colorPrimaryContainer(), 4.5, "onPrimaryContainer/primaryContainer");
        require(tokens.colorOnSecondary(), tokens.colorSecondary(), 4.5, "onSecondary/secondary");
        require(tokens.colorOnSecondaryContainer(), tokens.colorSecondaryContainer(), 4.5, "onSecondaryContainer/secondaryContainer");
        require(tokens.colorOnTertiary(), tokens.colorTertiary(), 4.5, "onTertiary/tertiary");
        require(tokens.colorOnError(), tokens.colorError(), 4.5, "onError/error");
        require(tokens.colorOnErrorContainer(), tokens.colorErrorContainer(), 4.5, "onErrorContainer/errorContainer");
        require(tokens.colorOnSuccess(), tokens.colorSuccess(), 4.5, "onSuccess/success");
        require(tokens.colorOnSuccessContainer(), tokens.colorSuccessContainer(), 4.5, "onSuccessContainer/successContainer");
        require(tokens.colorOnWarning(), tokens.colorWarning(), 4.5, "onWarning/warning");
        require(tokens.colorOnWarningContainer(), tokens.colorWarningContainer(), 4.5, "onWarningContainer/warningContainer");
        require(tokens.colorOnSurface(), tokens.colorSurface(), 4.5, "onSurface/surface");
        require(tokens.colorOnSurfaceVariant(), tokens.colorSurface(), 4.5, "onSurfaceVariant/surface");
        require(tokens.colorPrimary(), tokens.colorSurface(), 4.5, "primary/surface");
        require(tokens.colorPrimary(), tokens.colorSurfaceContainer(), 4.5, "primary/surfaceContainer");
        require(tokens.colorPrimary(), tokens.colorSurfaceContainerHigh(), 4.5, "primary/surfaceContainerHigh");
        require(tokens.colorPrimary(), tokens.colorSurfaceContainerHighest(), 4.5, "primary/surfaceContainerHighest");
        require(tokens.colorOnSurface(), tokens.colorSurfaceContainerHigh(), 4.5, "onSurface/surfaceContainerHigh");
        require(tokens.colorOnSurface(), tokens.colorSurfaceContainerHighest(), 4.5, "onSurface/surfaceContainerHighest");
        require(tokens.colorInverseOnSurface(), tokens.colorInverseSurface(), 4.5, "inverseOnSurface/inverseSurface");
        require(tokens.colorOutline(), tokens.colorSurface(), 3.0, "outline/surface");
        require(tokens.colorFocus(), tokens.colorSurface(), 3.0, "focus/surface");
        require(tokens.colorPlayerControlActive(), composite(tokens.colorPlayerScrim(), 0xFF000000), 4.5, "playerControlActive/playerScrim");
    }

    static double ratio(int foreground, int background) {
        double light = relativeLuminance(foreground);
        double dark = relativeLuminance(background);
        return (Math.max(light, dark) + 0.05) / (Math.min(light, dark) + 0.05);
    }

    static int composite(int foreground, int background) {
        int alpha = (foreground >>> 24) & 0xFF;
        int inverse = 255 - alpha;
        int red = (((foreground >>> 16) & 0xFF) * alpha + ((background >>> 16) & 0xFF) * inverse + 127) / 255;
        int green = (((foreground >>> 8) & 0xFF) * alpha + ((background >>> 8) & 0xFF) * inverse + 127) / 255;
        int blue = ((foreground & 0xFF) * alpha + (background & 0xFF) * inverse + 127) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static void require(int foreground, int background, double minimum, String pair) {
        double ratio = ratio(foreground, background);
        if (ratio + 0.0001 < minimum) {
            throw new IllegalArgumentException(pair + " contrast " + String.format(java.util.Locale.US, "%.2f", ratio) + " < " + minimum);
        }
    }

    private static double relativeLuminance(int color) {
        double red = channel((color >>> 16) & 0xFF);
        double green = channel((color >>> 8) & 0xFF);
        double blue = channel(color & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
