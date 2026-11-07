package com.monolith.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.util.TypedValue;

/**
 * Draws text in our own fonts. Launchers ignore custom fonts inside widget layouts, so every
 * piece of widget text is rendered here as an alpha-only bitmap and tinted by its ImageView.
 */
final class Txt {

    private Txt() {}

    private static final SparseArray<Typeface> FONTS = new SparseArray<>();

    static Typeface font(Context c, int res) {
        Typeface tf = FONTS.get(res);
        if (tf == null) {
            try {
                tf = c.getResources().getFont(res);
            } catch (RuntimeException e) {
                tf = Typeface.DEFAULT;
            }
            FONTS.put(res, tf);
        }
        return tf;
    }

    static float sp(Context c, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, c.getResources().getDisplayMetrics());
    }

    static float dp(Context c, float dp) {
        return dp * c.getResources().getDisplayMetrics().density;
    }

    static Paint paint(Context c, int font, float px, float letterSpacing) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(Color.BLACK);
        p.setTypeface(font(c, font));
        p.setTextSize(px);
        p.setLetterSpacing(letterSpacing);
        p.setFontFeatureSettings("tnum");
        return p;
    }

    private static Bitmap blank(Context c, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ALPHA_8);
        b.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        return b;
    }

    /** A single-line label, sized to its line box like a TextView without font padding. */
    static Bitmap label(Context c, String s, int font, float sizeSp, float letterSpacing) {
        Paint p = paint(c, font, sp(c, sizeSp), letterSpacing);
        Paint.FontMetrics fm = p.getFontMetrics();
        int w = (int) Math.ceil(p.measureText(s)) + 2;
        int h = (int) Math.ceil(fm.descent - fm.ascent);
        Bitmap b = blank(c, w, h);
        new Canvas(b).drawText(s, 1, -fm.ascent, p);
        return b;
    }

    /** Font size (px) at which the text's ink fits inside maxW × maxH, capped at maxPx. */
    static float fit(Context c, String s, int font, float letterSpacing, float maxW, float maxH, float maxPx) {
        Paint p = paint(c, font, 100f, letterSpacing);
        Rect r = new Rect();
        p.getTextBounds(s, 0, s.length(), r);
        if (r.width() <= 0 || r.height() <= 0) return maxPx;
        float k = Math.min(maxW / r.width(), maxH / r.height());
        return Math.min(maxPx, 100f * k);
    }

    /**
     * Large text cropped to its ink plus a margin, so it can be centred optically.
     * Returns {text, shadow}; both have the same size so they stack exactly.
     */
    static Bitmap[] display(Context c, String s, int font, float px, float letterSpacing,
                            float padPx, float blurPx, float shadowDyPx) {
        Paint p = paint(c, font, px, letterSpacing);
        Rect r = new Rect();
        p.getTextBounds(s, 0, s.length(), r);
        int pad = Math.round(padPx);
        int w = r.width() + 2 * pad, h = r.height() + 2 * pad;
        float x = pad - r.left, y = pad - r.top;

        Bitmap text = blank(c, w, h);
        new Canvas(text).drawText(s, x, y, p);

        Bitmap shadow = blank(c, w, h);
        if (blurPx > 0) {
            Paint sp = new Paint(p);
            sp.setMaskFilter(new BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL));
            new Canvas(shadow).drawText(s, x, y + shadowDyPx, sp);
        }
        return new Bitmap[]{text, shadow};
    }

    /**
     * Numbers at full size, units (letters and %) smaller on a second layer.
     * Returns {numbers, units}, same size and baseline, for stacking in a FrameLayout.
     */
    static Bitmap[] twoTone(Context c, String s, int font, float sizeSp, float unitScale, float letterSpacing) {
        Paint big = paint(c, font, sp(c, sizeSp), letterSpacing);
        Paint small = paint(c, font, sp(c, sizeSp) * unitScale, letterSpacing);
        Paint.FontMetrics fm = big.getFontMetrics();

        float width = 0;
        int i = 0;
        while (i < s.length()) {
            int j = i;
            boolean unit = isUnit(s.charAt(i));
            while (j < s.length() && isUnit(s.charAt(j)) == unit) j++;
            width += (unit ? small : big).measureText(s, i, j);
            i = j;
        }
        int w = (int) Math.ceil(width) + 2;
        int h = (int) Math.ceil(fm.descent - fm.ascent);
        Bitmap numbers = blank(c, w, h), units = blank(c, w, h);
        Canvas cn = new Canvas(numbers), cu = new Canvas(units);

        float x = 1, baseline = -fm.ascent;
        i = 0;
        while (i < s.length()) {
            int j = i;
            boolean unit = isUnit(s.charAt(i));
            while (j < s.length() && isUnit(s.charAt(j)) == unit) j++;
            Paint p = unit ? small : big;
            (unit ? cu : cn).drawText(s, i, j, x, baseline, p);
            x += p.measureText(s, i, j);
            i = j;
        }
        return new Bitmap[]{numbers, units};
    }

    private static boolean isUnit(char ch) {
        return Character.isLetter(ch) || ch == '%';
    }
}
