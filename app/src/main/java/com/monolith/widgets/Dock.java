package com.monolith.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Dock bar: a glass pill or rounded bar holding up to five apps, for the bottom row of the home
 * screen (One UI doesn't let widgets into its own dock, so this replaces it).
 */
final class Dock {

    private Dock() {}

    static final int PILL = 0, ROUNDED = 1, CIRCLES = 2;
    static final String[] SHAPE_NAMES = {"Pill", "Rounded", "Circles"};
    static final int GLASS = 0, TONAL = 1;
    static final String[] MATERIAL_NAMES = {"Glass", "Solid"};
    static final String[] ICON_NAMES = {"App icons", "Themed"};
    static final int MAX = 5;
    private static final String PREFS = "dock";
    private static final int[] SLOTS = {R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4};

    static final class Config {
        final List<ComponentName> apps = new ArrayList<>();
        int shape = PILL, material = GLASS;
        boolean themed = false;

        String key() {
            StringBuilder b = new StringBuilder(shape + "|" + material + "|" + themed);
            for (ComponentName cn : apps) b.append('|').append(cn.flattenToShortString());
            return b.toString();
        }
    }

    // ------------------------------------------------------------------ storage

    static Config load(Context c, int id) {
        return read(prefs(c), "w" + id);
    }

    static void save(Context c, int id, Config cfg) {
        write(prefs(c).edit(), "w" + id, cfg).apply();
    }

    static void delete(Context c, int id) {
        prefs(c).edit().remove("w" + id + ".apps").remove("w" + id + ".shape").remove("w" + id + ".material")
                .remove("w" + id + ".themed").apply();
    }

    static void savePending(Context c, Config cfg) {
        write(prefs(c).edit(), "pending", cfg).putLong("pending.at", System.currentTimeMillis()).apply();
    }

    static Config takePending(Context c) {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() - p.getLong("pending.at", 0) > 5 * 60_000L) return null;
        Config cfg = read(p, "pending");
        p.edit().remove("pending.at").apply();
        return cfg;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Config read(SharedPreferences p, String k) {
        String apps = p.getString(k + ".apps", null);
        if (apps == null) return null;
        Config cfg = new Config();
        for (String s : apps.split(";")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (cn != null) cfg.apps.add(cn);
        }
        cfg.shape = p.getInt(k + ".shape", PILL);
        cfg.material = p.getInt(k + ".material", GLASS);
        cfg.themed = p.getBoolean(k + ".themed", false);
        return cfg;
    }

    private static SharedPreferences.Editor write(SharedPreferences.Editor e, String k, Config cfg) {
        StringBuilder b = new StringBuilder();
        for (ComponentName cn : cfg.apps) {
            if (b.length() > 0) b.append(';');
            b.append(cn.flattenToString());
        }
        return e.putString(k + ".apps", b.toString()).putInt(k + ".shape", cfg.shape).putInt(k + ".material", cfg.material).putBoolean(k + ".themed", cfg.themed);
    }

    /** Your default phone, messages, browser, camera and gallery apps, as a starting point. */
    static Config defaults(Context c) {
        Config cfg = new Config();
        PackageManager pm = c.getPackageManager();
        Intent[] intents = {
                new Intent(Intent.ACTION_DIAL),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        };
        for (Intent i : intents) {
            ResolveInfo ri = pm.resolveActivity(i, 0);
            if (ri == null || ri.activityInfo == null || "android".equals(ri.activityInfo.packageName)) continue;
            Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
            if (launch == null || launch.getComponent() == null) continue;
            if (!cfg.apps.contains(launch.getComponent())) cfg.apps.add(launch.getComponent());
        }
        return cfg;
    }

    // ------------------------------------------------------------------ widget

    static RemoteViews build(Context c, int id, float wDp, float hDp) {
        Config cfg = id >= 0 ? load(c, id) : null;
        return render(c, cfg, id, wDp, hDp);
    }

    static RemoteViews render(Context c, Config cfg, int id, float wDp, float hDp) {
        Config shown = cfg != null ? cfg : (id < 0 ? defaults(c) : new Config());
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.dock);
        v.setImageViewBitmap(R.id.dock_img, draw(c, shown, wDp, hDp));
        int n = Math.min(MAX, shown.apps.size());
        for (int i = 0; i < MAX; i++) {
            v.setViewVisibility(SLOTS[i], i < Math.max(1, n) ? View.VISIBLE : View.GONE);
            if (id < 0) continue;
            Intent tap;
            if (i < n) {
                tap = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(shown.apps.get(i)).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            } else {
                tap = new Intent(c, DockConfig.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            }
            v.setOnClickPendingIntent(SLOTS[i], Render.activity(c, tap, 2000 + id * 8 + i));
        }
        return v;
    }

    // ------------------------------------------------------------------ drawing

    private static final LruCache<String, Bitmap> DRAWN = new LruCache<>(6);

    /** Padding at each end of the bar, shared with the tap slots in layout/dock.xml. */
    static final float END_PAD_DP = 10f;

    static Bitmap draw(Context c, Config cfg, float wDp, float hDp) {
        boolean dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        boolean glass = cfg.material == GLASS;
        int tile = c.getColor(dark ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100);
        int glyph = c.getColor(dark ? android.R.color.system_accent1_100 : android.R.color.system_accent1_800);
        int solid = c.getColor(dark ? android.R.color.system_accent2_800 : android.R.color.system_accent2_100);
        int solidHi = c.getColor(dark ? android.R.color.system_accent2_700 : android.R.color.system_accent2_50);

        int W = Math.round(Txt.dp(c, wDp)), H = Math.round(Txt.dp(c, hDp));
        // Bar: full width, up to 78 dp tall, centred vertically (the row is usually taller).
        float barH = Math.min(H - Txt.dp(c, 8), Txt.dp(c, 78));
        float top = (H - barH) / 2f;
        String key = cfg.key() + "@" + W + "x" + H + "|" + dark + "|" + tile + "|" + solid;
        Bitmap hit = DRAWN.get(key);
        if (hit != null) return hit;

        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        out.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Canvas cv = new Canvas(out);

        int n = Math.min(MAX, cfg.apps.size());
        float pad = Txt.dp(c, END_PAD_DP);
        float slot = (W - 2 * pad) / Math.max(1, n);
        float icon = Math.min(barH * 0.70f, Txt.dp(c, 56));

        if (cfg.shape == CIRCLES) {
            // One bubble per app; the app icon sits inside, a little smaller.
            float d = Math.min(barH, slot - Txt.dp(c, 8));
            icon = d * 0.70f;
            for (int i = 0; i < Math.max(1, n); i++) {
                float cx = pad + slot * (i + 0.5f), cy = top + barH / 2f;
                RectF b = new RectF(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f);
                surface(c, cv, b, d / 2f, glass, dark, solid, solidHi);
            }
        } else {
            float r = cfg.shape == PILL ? barH / 2f : Txt.dp(c, 26);
            surface(c, cv, new RectF(0.5f, top, W - 0.5f, top + barH), r, glass, dark, solid, solidHi);
        }

        if (n == 0) {
            Drawable plus = c.getDrawable(R.drawable.ic_plus).mutate();
            plus.setTint(dark ? Color.WHITE : glyph);
            float s = Math.min(icon * 0.5f, Txt.dp(c, 26)), cx = W / 2f, cy = top + barH / 2f;
            plus.setBounds(Math.round(cx - s / 2), Math.round(cy - s / 2), Math.round(cx + s / 2), Math.round(cy + s / 2));
            plus.draw(cv);
        }
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < n; i++) {
            float cx = pad + slot * (i + 0.5f), cy = top + barH / 2f;
            ComponentName cn = cfg.apps.get(i);
            Bitmap mono = cfg.themed ? mono(c, cn, Math.round(icon * 0.5f)) : null;
            if (mono != null) {
                if (cfg.shape != CIRCLES) {
                    tp.setColor(tile);
                    cv.drawCircle(cx, cy, icon / 2f, tp);
                }
                Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                mp.setColor(cfg.shape == CIRCLES && glass && dark ? Color.WHITE : glyph);
                cv.drawBitmap(mono, cx - mono.getWidth() / 2f, cy - mono.getHeight() / 2f, mp);
            } else {
                Drawable d = icon(c, cn);
                if (d == null) continue;
                // Masked to a circle so every app sits on the same round tile.
                int px = Math.max(1, Math.round(icon));
                Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                Canvas bc = new Canvas(b);
                Path clip = new Path();
                clip.addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW);
                bc.clipPath(clip);
                int over = Math.round(px * 0.04f);   // trim the adaptive icon's edge
                d.setBounds(-over, -over, px + over, px + over);
                d.draw(bc);
                cv.drawBitmap(b, cx - px / 2f, cy - px / 2f, null);
            }
        }
        DRAWN.put(key, out);
        return out;
    }

    /** Glass (translucent fill, lit rim, top highlight) or solid tonal, in any rounded rect. */
    private static void surface(Context c, Canvas cv, RectF b, float r, boolean glass, boolean dark, int solid, int solidHi) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!glass) {
            fill.setShader(new LinearGradient(b.left, b.top, b.left + b.width() * 0.12f, b.bottom, solidHi, solid, Shader.TileMode.CLAMP));
            cv.drawRoundRect(b, r, r, fill);
            return;
        }
        fill.setShader(new LinearGradient(b.left, b.top, b.left + b.width() * 0.12f, b.bottom,
                dark ? Color.argb(66, 255, 255, 255) : Color.argb(179, 255, 255, 255),
                dark ? Color.argb(20, 255, 255, 255) : Color.argb(92, 255, 255, 255), Shader.TileMode.CLAMP));
        cv.drawRoundRect(b, r, r, fill);

        float st = Txt.dp(c, 1.1f);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(st);
        rim.setShader(new LinearGradient(b.left, b.top, b.left + Math.max(b.width() * 0.35f, b.height()), b.bottom,
                new int[]{dark ? Color.argb(158, 255, 255, 255) : Color.WHITE,
                        dark ? Color.argb(20, 255, 255, 255) : Color.argb(115, 255, 255, 255),
                        dark ? Color.argb(56, 255, 255, 255) : Color.argb(204, 255, 255, 255)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        RectF in = new RectF(b);
        in.inset(st / 2f, st / 2f);
        cv.drawRoundRect(in, r - st / 2f, r - st / 2f, rim);
    }

    // ------------------------------------------------------------------ app icons

    private static final LruCache<String, Bitmap> MONO = new LruCache<>(24);

    /** The app's monochrome (themed) icon as an alpha mask, cropped to its ink and fitted into px × px. */
    static Bitmap mono(Context c, ComponentName cn, int px) {
        String key = cn.flattenToShortString() + "@" + px;
        Bitmap hit = MONO.get(key);
        if (hit != null) return hit;
        if (android.os.Build.VERSION.SDK_INT < 33 || px <= 0) return null;
        Drawable d = icon(c, cn);
        if (!(d instanceof android.graphics.drawable.AdaptiveIconDrawable)) return null;
        Drawable m = ((android.graphics.drawable.AdaptiveIconDrawable) d).getMonochrome();
        if (m == null) return null;
        int big = 432;
        Bitmap full = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888);
        m.setBounds(0, 0, big, big);
        m.draw(new Canvas(full));
        int[] px2 = new int[big * big];
        full.getPixels(px2, 0, big, 0, 0, big, big);
        int l = big, t = big, rr = -1, bb = -1;
        for (int y = 0; y < big; y++) for (int x = 0; x < big; x++) {
            if ((px2[y * big + x] >>> 24) > 10) { if (x < l) l = x; if (x > rr) rr = x; if (y < t) t = y; if (y > bb) bb = y; }
        }
        if (rr < 0) return null;
        int iw = rr - l + 1, ih = bb - t + 1;
        float scale = (float) px / Math.max(iw, ih);
        Bitmap out = Bitmap.createScaledBitmap(Bitmap.createBitmap(full, l, t, iw, ih),
                Math.max(1, Math.round(iw * scale)), Math.max(1, Math.round(ih * scale)), true).extractAlpha();
        MONO.put(key, out);
        return out;
    }

    static Drawable icon(Context c, ComponentName cn) {
        PackageManager pm = c.getPackageManager();
        try {
            return pm.getActivityIcon(cn);
        } catch (PackageManager.NameNotFoundException e) {
            try {
                return pm.getApplicationIcon(cn.getPackageName());
            } catch (PackageManager.NameNotFoundException e2) {
                return null;
            }
        }
    }

    static boolean isInstalled(Context c, ComponentName cn) {
        try {
            c.getPackageManager().getActivityInfo(cn, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
