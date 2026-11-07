package com.monolith.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a dock bar into a wallpaper, behind where One UI puts its dock icons. Widgets can't go in
 * the dock, but the wallpaper sits under it, and with the real pixels the glass gets a true blur.
 */
final class DockWall {

    private DockWall() {}

    static final int PILL = 0, ROUNDED = 1, CIRCLES = 2;
    static final String[] SHAPES = {"Pill", "Rounded", "Circles"};
    static final int GLASS = 0, SOLID = 1;
    static final String[] LOOKS = {"Glass", "Solid"};

    static final class Settings {
        int shape = PILL, look = GLASS, count = 4;
        int posDp = 104;     // bar centre, measured up from the bottom of the screen
        int heightDp = 84;
        int marginDp = 14;

        static Settings load(Context c) {
            SharedPreferences p = c.getSharedPreferences("dockwall", Context.MODE_PRIVATE);
            Settings s = new Settings();
            s.shape = p.getInt("shape", PILL);
            s.look = p.getInt("look", GLASS);
            s.count = p.getInt("count", 4);
            s.posDp = p.getInt("pos", 104);
            s.heightDp = p.getInt("height", 84);
            s.marginDp = p.getInt("margin", 14);
            return s;
        }

        void save(Context c) {
            c.getSharedPreferences("dockwall", Context.MODE_PRIVATE).edit()
                    .putInt("shape", shape).putInt("look", look).putInt("count", count)
                    .putInt("pos", posDp).putInt("height", heightDp).putInt("margin", marginDp).apply();
        }
    }

    /**
     * @param density px per dp at the output size (device density × preview scale).
     */
    static Bitmap compose(Context c, Bitmap src, int W, int H, Settings s, float density) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        drawCover(cv, src, W, H);

        float h = s.heightDp * density, cy = H - s.posDp * density, m = s.marginDp * density;
        List<RectF> rects = new ArrayList<>();
        float r;
        if (s.shape == CIRCLES) {
            float usable = W - 2 * m;
            for (int i = 0; i < s.count; i++) {
                float cx = m + usable * (i + 0.5f) / s.count;
                rects.add(new RectF(cx - h / 2f, cy - h / 2f, cx + h / 2f, cy + h / 2f));
            }
            r = h / 2f;
        } else {
            rects.add(new RectF(m, cy - h / 2f, W - m, cy + h / 2f));
            r = s.shape == PILL ? h / 2f : Math.min(h / 2f, 28 * density);
        }

        Path shape = new Path();
        for (RectF b : rects) shape.addRoundRect(b, r, r, Path.Direction.CW);
        RectF all = new RectF();
        shape.computeBounds(all, true);
        boolean darkBehind = luminance(out, all) < 0.5f;

        if (s.look == GLASS) {
            // Frost: the wallpaper under the bar, blurred, then a soft white veil and a lit rim.
            Bitmap frost = blur(out, Math.max(8, Math.round(W / 90f)));
            cv.save();
            cv.clipPath(shape);
            Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG);
            cv.drawBitmap(frost, null, new RectF(0, 0, W, H), fp);
            for (RectF b : rects) {
                Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
                veil.setShader(new LinearGradient(b.left, b.top, b.left + b.width() * 0.1f, b.bottom,
                        darkBehind ? Color.argb(56, 255, 255, 255) : Color.argb(110, 255, 255, 255),
                        darkBehind ? Color.argb(18, 255, 255, 255) : Color.argb(60, 255, 255, 255), Shader.TileMode.CLAMP));
                cv.drawRoundRect(b, r, r, veil);
            }
            cv.restore();
            float st = Math.max(1f, 1.1f * density);
            for (RectF b : rects) {
                Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
                rim.setStyle(Paint.Style.STROKE);
                rim.setStrokeWidth(st);
                rim.setShader(new LinearGradient(b.left, b.top, b.left + Math.max(b.width() * 0.35f, b.height()), b.bottom,
                        new int[]{Color.argb(darkBehind ? 170 : 235, 255, 255, 255), Color.argb(darkBehind ? 22 : 90, 255, 255, 255),
                                Color.argb(darkBehind ? 60 : 170, 255, 255, 255)},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                RectF in = new RectF(b);
                in.inset(st / 2f, st / 2f);
                cv.drawRoundRect(in, r - st / 2f, r - st / 2f, rim);
            }
        } else {
            // Solid: the wallpaper palette's container colour, like the widgets.
            int base = c.getColor(darkBehind ? android.R.color.system_accent2_800 : android.R.color.system_accent2_100);
            int hi = c.getColor(darkBehind ? android.R.color.system_accent2_700 : android.R.color.system_accent2_50);
            for (RectF b : rects) {
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setShader(new LinearGradient(b.left, b.top, b.left + b.width() * 0.1f, b.bottom, hi, base, Shader.TileMode.CLAMP));
                cv.drawRoundRect(b, r, r, fill);
            }
        }
        return out;
    }


    /**
     * Finds the dock icons in a home screen screenshot: the lowest band of strong detail near the
     * bottom that is about one icon tall, then the icons inside it. Returns null if nothing fits.
     */
    static Settings detect(Bitmap shot, int screenW, int screenH, float density, Settings base) {
        int w = 360, h = Math.max(1, Math.round(shot.getHeight() * 360f / shot.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(shot, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        float[] g = new float[w * h];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            g[i] = 0.2126f * ((p >> 16) & 255) + 0.7152f * ((p >> 8) & 255) + 0.0722f * (p & 255);
        }
        int y0 = Math.round(h * 0.60f), y1 = Math.round(h * 0.985f);
        float[] e = new float[h];
        for (int y = Math.max(1, y0); y < y1; y++) {
            float sum = 0;
            for (int x = 1; x < w; x++) {
                sum += Math.abs(g[y * w + x] - g[y * w + x - 1]) + Math.abs(g[y * w + x] - g[(y - 1) * w + x]);
            }
            e[y] = sum / w;
        }
        float[] sorted = java.util.Arrays.copyOfRange(e, y0, y1);
        java.util.Arrays.sort(sorted);
        float med = sorted[sorted.length / 2], max = sorted[sorted.length - 1];
        float t = med + 0.35f * (max - med);

        // Bands of rows above the threshold, bottom-most first; an icon is roughly 11-24% of the width tall.
        int bestTop = -1, bestBottom = -1;
        int y = y1 - 1;
        while (y >= y0) {
            if (e[y] > t) {
                int bottom = y;
                int gap = 0;
                while (y >= y0 && (e[y] > t || gap < 3)) {
                    if (e[y] > t) gap = 0; else gap++;
                    y--;
                }
                int top = y + 1 + gap;
                int bh = bottom - top + 1;
                if (bh >= w * 0.11f && bh <= w * 0.24f) {
                    bestTop = top;
                    bestBottom = bottom;
                    break;
                }
            }
            y--;
        }
        if (bestTop < 0) return null;

        // Icons across the band: columns with detail, merged into runs about one icon wide.
        float[] col = new float[w];
        for (int x = 1; x < w; x++) {
            float sum = 0;
            for (int yy = bestTop; yy <= bestBottom; yy++) sum += Math.abs(g[yy * w + x] - g[yy * w + x - 1]);
            col[x] = sum / (bestBottom - bestTop + 1);
        }
        float[] cs = col.clone();
        java.util.Arrays.sort(cs);
        float ct = cs[w / 2] + 0.25f * (cs[w - 1] - cs[w / 2]);
        int ih = bestBottom - bestTop + 1;
        List<float[]> runs = new ArrayList<>();
        int x = 1;
        while (x < w) {
            if (col[x] > ct) {
                int a = x, gap = 0;
                while (x < w && (col[x] > ct || gap < ih * 0.25f)) {
                    if (col[x] > ct) gap = 0; else gap++;
                    x++;
                }
                int b = x - 1 - gap;
                if (b - a + 1 >= ih * 0.5f && b - a + 1 <= ih * 1.6f) runs.add(new float[]{a, b});
            }
            x++;
        }

        float sx = screenW / (float) w, sy = screenH / (float) h;
        Settings s = new Settings();
        s.shape = base.shape;
        s.look = base.look;
        float iconPx = ih * sy;
        float cyPx = (bestTop + bestBottom) / 2f * sy;
        s.posDp = Math.round((screenH - cyPx) / density);
        s.heightDp = Math.round(iconPx / density + 22);
        s.count = runs.size() == 5 ? 5 : 4;
        if (runs.size() >= 2) {
            float first = (runs.get(0)[0] + runs.get(0)[1]) / 2f * sx;
            float last = (runs.get(runs.size() - 1)[0] + runs.get(runs.size() - 1)[1]) / 2f * sx;
            float slot = (last - first) / (runs.size() - 1);
            s.marginDp = Math.max(0, Math.min(40, Math.round((first - slot / 2f) / density)));
        } else {
            s.marginDp = base.marginDp;
        }
        return s;
    }

    /** Scale-to-fill, centred, like a wallpaper. */
    private static void drawCover(Canvas cv, Bitmap src, int W, int H) {
        float scale = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
        float dw = src.getWidth() * scale, dh = src.getHeight() * scale;
        RectF dst = new RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f);
        cv.drawBitmap(src, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
    }

    /** Cheap, smooth blur: shrink, three box-blur passes, stretched back up by the caller. */
    static Bitmap blur(Bitmap src, int factor) {
        int w = Math.max(1, src.getWidth() / factor), h = Math.max(1, src.getHeight() / factor);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
        int[] px = new int[w * h], tmp = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            box(px, tmp, w, h, 3, true);
            box(tmp, px, w, h, 3, false);
        }
        small.setPixels(px, 0, w, 0, 0, w, h);
        return small;
    }

    private static void box(int[] in, int[] out, int w, int h, int rad, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        for (int line = 0; line < lines; line++) {
            int r = 0, g = 0, b = 0;
            for (int k = -rad; k <= rad; k++) {
                int p = in[idx(line, clamp(k, len), w, horizontal)];
                r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
            }
            int n = 2 * rad + 1;
            for (int i = 0; i < len; i++) {
                out[idx(line, i, w, horizontal)] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
                int add = in[idx(line, clamp(i + rad + 1, len), w, horizontal)];
                int sub = in[idx(line, clamp(i - rad, len), w, horizontal)];
                r += ((add >> 16) & 255) - ((sub >> 16) & 255);
                g += ((add >> 8) & 255) - ((sub >> 8) & 255);
                b += (add & 255) - (sub & 255);
            }
        }
    }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    private static int clamp(int v, int len) {
        return v < 0 ? 0 : (v >= len ? len - 1 : v);
    }

    private static float luminance(Bitmap b, RectF area) {
        int x0 = Math.max(0, (int) area.left), y0 = Math.max(0, (int) area.top);
        int x1 = Math.min(b.getWidth() - 1, (int) area.right), y1 = Math.min(b.getHeight() - 1, (int) area.bottom);
        double sum = 0;
        int n = 0;
        for (int y = y0; y <= y1; y += 6) {
            for (int x = x0; x <= x1; x += 6) {
                int p = b.getPixel(x, y);
                sum += (0.2126 * ((p >> 16) & 255) + 0.7152 * ((p >> 8) & 255) + 0.0722 * (p & 255)) / 255.0;
                n++;
            }
        }
        return n == 0 ? 0f : (float) (sum / n);
    }
}
