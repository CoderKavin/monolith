package com.monolith.dock;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * The dock bar wallpaper. Draws the picture Monolith made and ignores the zoom One UI applies
 * when apps open and close, so the bar stays still. Lives in its own app so Monolith updates
 * don't reset it.
 */
public class DockWallpaper extends WallpaperService {

    static final Uri PICTURE = Uri.parse("content://com.monolith.widgets.dock/wall");
    private static final Set<DockEngine> ENGINES = new HashSet<>();
    private static Bitmap cached;
    private static long cachedVersion = -1;

    static void changed() {
        synchronized (ENGINES) {
            cachedVersion = -1;
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

        // No zoom, no parallax: the picture never moves.
        public void onZoomChanged(float zoom) {}

        @Override
        public void onOffsetsChanged(float x, float y, float xs, float ys, int xp, int yp) {}

        void draw() {
            Bitmap b = picture();
            SurfaceHolder h = getSurfaceHolder();
            Canvas cv = null;
            try {
                cv = h.lockHardwareCanvas();
                if (cv == null) return;
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

        /** The picture from Monolith, reloaded only when its version changes. */
        private Bitmap picture() {
            synchronized (ENGINES) {
                long v = version();
                if (cached == null || v != cachedVersion) {
                    try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(PICTURE, "r")) {
                        if (fd != null) {
                            Bitmap b = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
                            if (b != null) {
                                cached = b;
                                cachedVersion = v;
                                keepCopy(b);
                            }
                        }
                    } catch (Exception ignored) {
                        // Monolith mid-update or not set up yet: keep the last picture we had
                    }
                }
                if (cached == null) {
                    java.io.File own = new java.io.File(getFilesDir(), "wall.png");
                    if (own.exists()) cached = BitmapFactory.decodeFile(own.getAbsolutePath());
                }
                return cached;
            }
        }

        /** Our own copy, so the bar survives even while Monolith is being updated. */
        private void keepCopy(Bitmap b) {
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "wall.png"))) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            } catch (Exception ignored) {
            }
        }

        private long version() {
            try (Cursor c = getContentResolver().query(PICTURE, null, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getLong(0);
            } catch (Exception ignored) {
            }
            return cachedVersion;
        }
    }
}
