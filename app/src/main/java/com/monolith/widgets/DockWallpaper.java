package com.monolith.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The dock bar wallpaper as a live wallpaper. It draws the same picture as a normal wallpaper,
 * but ignores the zoom One UI applies when apps open and close, so the bar stays perfectly still.
 */
public class DockWallpaper extends WallpaperService {

    private static final Set<DockEngine> ENGINES = new HashSet<>();
    private static Bitmap cached;

    static File file(Context c) {
        return new File(c.getFilesDir(), "dock_wall.png");
    }

    /** Called after Monolith saves a new picture: running wallpapers redraw straight away. */
    static void changed() {
        synchronized (ENGINES) {
            cached = null;
            for (DockEngine e : ENGINES) e.draw();
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new DockEngine();
    }

    private final class DockEngine extends Engine {

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setOffsetNotificationsEnabled(false);
            synchronized (ENGINES) { ENGINES.add(this); }
        }

        @Override
        public void onDestroy() {
            synchronized (ENGINES) { ENGINES.remove(this); }
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            draw();
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            draw();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) draw();
        }

        // No zoom, no parallax: the picture never moves. (Not annotated: the zoom hook isn't in every SDK.)
        public void onZoomChanged(float zoom) {}

        @Override
        public void onOffsetsChanged(float x, float y, float xs, float ys, int xp, int yp) {}

        void draw() {
            SurfaceHolder h = getSurfaceHolder();
            Canvas cv = null;
            try {
                cv = h.lockHardwareCanvas();
                if (cv == null) return;
                Bitmap b = picture();
                cv.drawColor(Color.BLACK);
                if (b != null) {
                    float W = cv.getWidth(), H = cv.getHeight();
                    float s = Math.max(W / b.getWidth(), H / b.getHeight());
                    float dw = b.getWidth() * s, dh = b.getHeight() * s;
                    cv.drawBitmap(b, null, new RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f),
                            new Paint(Paint.FILTER_BITMAP_FLAG));
                }
            } catch (RuntimeException ignored) {
                // surface went away mid-draw; the next visibility change redraws
            } finally {
                if (cv != null) {
                    try { h.unlockCanvasAndPost(cv); } catch (RuntimeException ignored) {}
                }
            }
        }

        private Bitmap picture() {
            synchronized (ENGINES) {
                if (cached == null) {
                    File f = file(DockWallpaper.this);
                    if (f.exists()) cached = BitmapFactory.decodeFile(f.getAbsolutePath());
                }
                return cached;
            }
        }
    }
}
