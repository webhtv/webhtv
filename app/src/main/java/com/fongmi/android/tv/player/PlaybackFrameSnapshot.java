package com.fongmi.android.tv.player;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/**
 * 从当前播放视图抓一帧，作为 WebHome 嗅探历史缺失海报时的兜底封面。
 *
 * <p>只做一次 PixelCopy/TextureView 截图，不 seek、不改播放位置；黑屏帧由调用方丢弃后重试。
 * 截图缩到最长边 {@link #TARGET_MAX_EDGE}，保存为私有目录下的 JPEG，返回 {@code file://} URI，
 * 交给现有的 {@code ImgUtil} 走本地文件加载。</p>
 */
public final class PlaybackFrameSnapshot {

    private static final int TARGET_MAX_EDGE = 640;
    private static final int JPEG_QUALITY = 82;
    private static final int MAX_SNAPSHOTS = 80;
    private static final float BLACK_LUMA = 26f;
    private static final float MOSTLY_BLACK_RATIO = 0.85f;

    private PlaybackFrameSnapshot() {
    }

    public interface Callback {
        /** 成功返回 file:// URI，失败或黑屏返回空串；回调在主线程。 */
        void onCaptured(@NonNull String uri);
    }

    public static void capture(@NonNull Activity activity, @Nullable PlayerView playerView, @NonNull Callback callback) {
        if (playerView == null) {
            callback.onCaptured("");
            return;
        }
        View surface = playerView.getVideoSurfaceView();
        if (!playerView.isAttachedToWindow() || surface == null || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            callback.onCaptured("");
            return;
        }
        int[] size = targetSize(surface.getWidth(), surface.getHeight());
        Handler handler = new Handler(Looper.getMainLooper());
        if (surface instanceof SurfaceView surfaceView) {
            Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            try {
                PixelCopy.request(surfaceView, bitmap, result -> finish(activity, bitmap, result == PixelCopy.SUCCESS, callback), handler);
            } catch (RuntimeException error) {
                bitmap.recycle();
                callback.onCaptured("");
            }
            return;
        }
        if (surface instanceof TextureView textureView) {
            Bitmap bitmap = null;
            try {
                bitmap = textureView.getBitmap(size[0], size[1]);
            } catch (RuntimeException ignored) {
            }
            finish(activity, bitmap, bitmap != null, callback);
            return;
        }
        callback.onCaptured("");
    }

    private static void finish(Activity activity, Bitmap bitmap, boolean success, Callback callback) {
        if (!success || bitmap == null || isMostlyBlack(bitmap)) {
            if (bitmap != null) bitmap.recycle();
            callback.onCaptured("");
            return;
        }
        Task.execute(() -> {
            String uri = "";
            try {
                uri = save(activity, bitmap);
            } catch (Throwable ignored) {
            } finally {
                bitmap.recycle();
            }
            final String result = uri;
            App.post(() -> callback.onCaptured(result));
        });
    }

    private static boolean isMostlyBlack(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return true;
        int stepX = Math.max(1, width / 32);
        int stepY = Math.max(1, height / 18);
        int total = 0;
        int dark = 0;
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int color = bitmap.getPixel(x, y);
                float luma = 0.2126f * ((color >> 16) & 255) + 0.7152f * ((color >> 8) & 255) + 0.0722f * (color & 255);
                if (luma < BLACK_LUMA) dark++;
                total++;
            }
        }
        return total > 0 && dark >= total * MOSTLY_BLACK_RATIO;
    }

    private static String save(Activity activity, Bitmap bitmap) throws Exception {
        File directory = new File(activity.getFilesDir(), "webhome_snapshot");
        if (!directory.exists() && !directory.mkdirs()) return "";
        File file = new File(directory, "snapshot-" + System.currentTimeMillis() + "-" + System.nanoTime() + ".jpg");
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                file.delete();
                return "";
            }
        }
        trim(directory);
        return Uri.fromFile(file).toString();
    }

    private static void trim(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith("snapshot-") && name.endsWith(".jpg"));
        if (files == null || files.length <= MAX_SNAPSHOTS) return;
        Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (int i = 0; i < files.length - MAX_SNAPSHOTS; i++) files[i].delete();
    }

    private static int[] targetSize(int width, int height) {
        if (width >= height) {
            int targetWidth = Math.min(width, TARGET_MAX_EDGE);
            return new int[]{targetWidth, Math.max(1, Math.round(height * targetWidth / (float) width))};
        }
        int targetHeight = Math.min(height, TARGET_MAX_EDGE);
        return new int[]{Math.max(1, Math.round(width * targetHeight / (float) height)), targetHeight};
    }
}
