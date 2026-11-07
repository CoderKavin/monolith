package com.monolith.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;

import java.util.Calendar;

/**
 * Draws the widget graphics as alpha-only bitmaps. Each layer is tinted by its ImageView in XML,
 * so colours follow the wallpaper palette and light/dark mode without redrawing.
 */
final class Gfx {

    private Gfx() {}

    private static final float TAU = (float) (Math.PI * 2);

    static Bitmap alpha(int w, int h) {
        return Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ALPHA_8);
    }

    private static Paint stroke(float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(Color.BLACK);
        return p;
    }

    private static Paint fill() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        return p;
    }

    private static float deg(float rad) {
        return (float) Math.toDegrees(rad);
    }

    /**
     * Material 3 Expressive wavy ring. Returns {track, active}.
     * Geometry matches the design: radius 82, amplitude 3.2, 11 waves, stroke 11 on a 200 box.
     */
    static Bitmap[] wavyRing(int px, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        Bitmap track = alpha(px, px), active = alpha(px, px);
        float s = px / 200f, c = px / 2f, r0 = 82f * s, amp = 3.2f * s;
        Paint paint = stroke(11f * s);

        float t0 = (float) (-Math.PI / 2), t1 = t0 + TAU * p;
        if (p > 0f) {
            int n = Math.max(8, Math.round(360 * p));
            Path path = new Path();
            for (int i = 0; i <= n; i++) {
                float a = t0 + (t1 - t0) * i / n;
                float r = r0 + amp * (float) Math.sin(11 * (a - t0));
                float x = c + r * (float) Math.cos(a), y = c + r * (float) Math.sin(a);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            new Canvas(active).drawPath(path, paint);
        }
        float gap = 22f / 82f;
        float a0 = t1 + gap, a1 = t0 + TAU - gap;
        if (p < 1f && a1 - a0 > 0.02f) {
            RectF oval = new RectF(c - r0, c - r0, c + r0, c + r0);
            new Canvas(track).drawArc(oval, deg(a0), deg(a1 - a0), false, paint);
        }
        return new Bitmap[]{track, active};
    }

    /** Ring split into four rounded segments with gaps. Returns one layer per segment. */
    static Bitmap[] segmentedRing(int px, long[] values) {
        Bitmap[] layers = new Bitmap[values.length];
        for (int i = 0; i < layers.length; i++) layers[i] = alpha(px, px);
        float s = px / 200f, c = px / 2f, r = 80f * s;
        Paint paint = stroke(16f * s);
        RectF oval = new RectF(c - r, c - r, c + r, c + r);

        long total = 0;
        for (long v : values) total += v;
        if (total <= 0) {
            new Canvas(layers[layers.length - 1]).drawArc(oval, 0, 360, false, paint);
            return layers;
        }
        float gap = deg(28f / 80f);
        float a = -90f;
        int nonZero = 0;
        for (long v : values) if (v > 0) nonZero++;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            float span = 360f * values[i] / total;
            if (nonZero == 1) {
                new Canvas(layers[i]).drawArc(oval, 0, 360, false, paint);
            } else if (span - gap > 0.5f) {
                new Canvas(layers[i]).drawArc(oval, a + gap / 2f, span - gap, false, paint);
            }
            a += span;
        }
        return layers;
    }

    /** Horizontal bar split into rounded pills with gaps. Returns one layer per segment. */
    static Bitmap[] segmentedBar(int w, int h, float gapPx, long[] values) {
        Bitmap[] layers = new Bitmap[values.length];
        for (int i = 0; i < layers.length; i++) layers[i] = alpha(w, h);
        Paint paint = fill();
        float radius = h / 2f;

        long total = 0;
        int nonZero = 0;
        for (long v : values) { total += v; if (v > 0) nonZero++; }
        if (total <= 0) {
            new Canvas(layers[layers.length - 1]).drawRoundRect(0, 0, w, h, radius, radius, paint);
            return layers;
        }
        float usable = w - gapPx * Math.max(0, nonZero - 1);
        float x = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            float segW = Math.max(h, usable * values[i] / total);
            float right = Math.min(w, x + segW);
            new Canvas(layers[i]).drawRoundRect(x, 0, right, h, radius, radius, paint);
            x = right + gapPx;
        }
        return layers;
    }

    /**
     * Month grid, Sunday first. Returns {dim, text, circle, today}:
     * weekday letters and past days, future days, today's filled circle, today's number.
     */
    static Bitmap[] month(Context c, int w, int h, Calendar now) {
        Bitmap dim = alpha(w, h), text = alpha(w, h), circle = alpha(w, h), today = alpha(w, h);
        float dp = c.getResources().getDisplayMetrics().density;

        Paint letters = textPaint(c, R.font.gsf_t620, 11);
        Paint days = textPaint(c, R.font.gsf_t540, 12);
        Paint todayText = textPaint(c, R.font.gsf_t620, 12);
        Paint dot = fill();

        Calendar first = (Calendar) now.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int count = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (offset + count + 6) / 7;
        int todayDom = now.get(Calendar.DAY_OF_MONTH);

        float colW = w / 7f;
        float letterH = 18f * dp;
        float rowH = Math.min(26f * dp, (h - letterH) / rows);
        float radius = Math.min(11.5f * dp, Math.min(colW, rowH) / 2f - 0.5f * dp);

        String[] names = {"S", "M", "T", "W", "T", "F", "S"};
        Canvas cd = new Canvas(dim), ct = new Canvas(text), cc = new Canvas(circle), cy = new Canvas(today);
        for (int i = 0; i < 7; i++) drawCentered(cd, names[i], colW * (i + 0.5f), letterH / 2f, letters);

        for (int d = 1; d <= count; d++) {
            int idx = offset + d - 1;
            float x = colW * (idx % 7 + 0.5f);
            float y = letterH + rowH * (idx / 7 + 0.5f);
            String n = String.valueOf(d);
            if (d == todayDom) {
                cc.drawCircle(x, y, radius, dot);
                drawCentered(cy, n, x, y, todayText);
            } else if (d < todayDom) {
                days.setAlpha(153);
                drawCentered(cd, n, x, y, days);
                days.setAlpha(255);
            } else {
                drawCentered(ct, n, x, y, days);
            }
        }
        return new Bitmap[]{dim, text, circle, today};
    }

    private static Paint textPaint(Context c, int fontRes, float sp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(Color.BLACK);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, c.getResources().getDisplayMetrics()));
        Typeface tf;
        try {
            tf = c.getResources().getFont(fontRes);
        } catch (RuntimeException e) {
            tf = Typeface.DEFAULT;
        }
        p.setTypeface(tf);
        p.setFontFeatureSettings("tnum");
        return p;
    }

    private static void drawCentered(Canvas canvas, String s, float cx, float cy, Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, p);
    }
}
