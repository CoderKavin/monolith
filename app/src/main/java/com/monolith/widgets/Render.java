package com.monolith.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Builds each widget at its real size (dp). Launchers ignore custom fonts in widget layouts,
 * so every piece of text is drawn by {@link Txt} in Google Sans Flex and shown as a tinted image.
 */
final class Render {

    private Render() {}

    static final int DISPLAY = 0, CENTRED = 1, CARD = 2, WEEK = 3, SCREEN = 4, MONTH = 5, BATTERY = 6, SCREEN_SMALL = 7;

    /** Design sizes: 4 × 2 is 380 × 204 dp, tall 2 × 2 is 184 × 204 dp. */
    static final float[] DEFAULT_W = {380, 380, 380, 380, 380, 184, 184, 184};
    static final float DEFAULT_H = 204;

    static final String ACTION_REFRESH = "com.monolith.widgets.REFRESH";
    static final String ACTION_TOGGLE_SAVER = "com.monolith.widgets.TOGGLE_SAVER";

    // Fonts: rounded numerals and plain text weights of Google Sans Flex.
    private static final int R540 = R.font.gsf_r540, R580 = R.font.gsf_r580, R620 = R.font.gsf_r620, R700 = R.font.gsf_r700,
            T540 = R.font.gsf_t540, T620 = R.font.gsf_t620;

    static RemoteViews build(Context c, int kind, float wDp, float hDp) {
        switch (kind) {
            case DISPLAY: return display(c, wDp, hDp);
            case CENTRED: return centred(c, wDp, hDp);
            case CARD: return card(c, wDp, hDp);
            case WEEK: return week(c);
            case SCREEN: return screen(c, wDp);
            case MONTH: return month(c, wDp, hDp);
            case BATTERY: return battery(c, wDp, hDp);
            case SCREEN_SMALL: return screenSmall(c, wDp, hDp);
            default: throw new IllegalArgumentException("kind " + kind);
        }
    }

    // ------------------------------------------------------------------ clocks

    /** Margin around wallpaper text, cancelled by the layouts' negative margins. */
    private static final float SHADOW_PAD_DP = 12f;

    private static RemoteViews display(Context c, float wDp, float hDp) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_display);
        String time = Data.time(c);
        float availW = Txt.dp(c, wDp - 12f);
        float availH = Txt.dp(c, hDp - 18f - 24f - 20f);
        float px = Txt.fit(c, time, R580, -0.04f, availW, availH, Txt.sp(c, 136f));
        onWallpaper(c, v, R.id.t, time, R580, px, -0.04f);
        onWallpaper(c, v, R.id.d, Data.longDate(), T540, Txt.sp(c, 18f), 0f);
        v.setOnClickPendingIntent(android.R.id.background, openAlarms(c));
        return v;
    }

    private static RemoteViews centred(Context c, float wDp, float hDp) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_centred);
        String time = Data.time(c);
        // No shadow any more, so only a hair of margin: the digits run almost edge to edge.
        float pad = Txt.dp(c, 2f);
        float availW = Txt.dp(c, wDp) - 2 * pad;
        float availH = Txt.dp(c, hDp) - 2 * pad;
        float px = Txt.fit(c, time, R700, -0.05f, availW, availH, Txt.sp(c, 420f));
        v.setImageViewBitmap(R.id.t, Txt.display(c, time, R700, px, -0.05f, pad, 0f, 0f)[0]);
        v.setOnClickPendingIntent(android.R.id.background, openAlarms(c));
        return v;
    }

    /** Text straight on the wallpaper, no shadow. The 12dp margin around the ink matches the layouts. */
    private static void onWallpaper(Context c, RemoteViews v, int textId,
                                    String s, int font, float px, float letterSpacing) {
        Bitmap[] b = Txt.display(c, s, font, px, letterSpacing, Txt.dp(c, SHADOW_PAD_DP), 0f, 0f);
        v.setImageViewBitmap(textId, b[0]);
    }

    private static RemoteViews card(Context c, float wDp, float hDp) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_card);
        String time = Data.time(c);
        float px = Txt.fit(c, time, R580, -0.035f, Txt.dp(c, wDp - 46f), Txt.dp(c, hDp - 38f - 42f), Txt.sp(c, 104f));
        v.setImageViewBitmap(R.id.t, Txt.display(c, time, R580, px, -0.035f, Txt.dp(c, 2f), 0f, 0f)[0]);
        v.setImageViewBitmap(R.id.d, Txt.label(c, Data.longDate(), T540, 14f, 0f));

        String alarm = Data.nextAlarm(c);
        if (alarm == null) {
            v.setViewVisibility(R.id.alarm_chip, View.GONE);
        } else {
            v.setViewVisibility(R.id.alarm_chip, View.VISIBLE);
            v.setImageViewBitmap(R.id.a, Txt.label(c, alarm, T540, 14f, 0f));
        }
        v.setOnClickPendingIntent(android.R.id.background, openAlarms(c));
        return v;
    }

    // ------------------------------------------------------------------ calendar

    private static final int[] PILLS = {R.id.p0, R.id.p1, R.id.p2, R.id.p3, R.id.p4, R.id.p5, R.id.p6};
    private static final int[] LETTERS = {R.id.l0, R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6};
    private static final int[] NUMBERS = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5, R.id.n6};

    private static RemoteViews week(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_week);
        Calendar now = Calendar.getInstance();
        v.setImageViewBitmap(R.id.wk_month, Txt.label(c, Data.monthName(now), T620, 16f, 0f));
        v.setImageViewBitmap(R.id.wk_no, Txt.label(c, "Week " + Data.isoWeek(), T540, 13f, 0f));

        Calendar day = (Calendar) now.clone();
        day.add(Calendar.DAY_OF_MONTH, -(now.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY));
        SimpleDateFormat letter = new SimpleDateFormat("EEEEE", Locale.getDefault());
        int onPrimary = c.getColor(R.color.g_on_primary);
        int onSurface = c.getColor(R.color.g_on_surface);
        for (int i = 0; i < 7; i++) {
            boolean isToday = day.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                    && day.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
            int fg = isToday ? onPrimary : onSurface;
            v.setImageViewBitmap(LETTERS[i], Txt.label(c, letter.format(day.getTime()), T540, 12f, 0f));
            v.setImageViewBitmap(NUMBERS[i], Txt.label(c, String.valueOf(day.get(Calendar.DAY_OF_MONTH)), R620, 24f, 0f));
            v.setInt(LETTERS[i], "setColorFilter", fg);
            v.setInt(NUMBERS[i], "setColorFilter", fg);
            v.setInt(PILLS[i], "setBackgroundResource", isToday ? R.drawable.g_pill_today : R.drawable.g_pill);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        v.setOnClickPendingIntent(android.R.id.background, openCalendar(c));
        return v;
    }

    private static RemoteViews month(Context c, float wDp, float hDp) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_month);
        Calendar now = Calendar.getInstance();
        v.setImageViewBitmap(R.id.mo_name, Txt.label(c, Data.monthName(now), T620, 15f, 0f));

        int w = Math.round(Txt.dp(c, wDp - 24f));
        int h = Math.round(Txt.dp(c, hDp - 16f - 14f - 20f - 6f));
        Bitmap[] layers = Gfx.month(c, w, h, now);
        v.setImageViewBitmap(R.id.mo_dim, layers[0]);
        v.setImageViewBitmap(R.id.mo_text, layers[1]);
        v.setImageViewBitmap(R.id.mo_circle, layers[2]);
        v.setImageViewBitmap(R.id.mo_today, layers[3]);
        v.setOnClickPendingIntent(android.R.id.background, openCalendar(c));
        return v;
    }

    // ------------------------------------------------------------------ battery

    private static RemoteViews battery(Context c, float wDp, float hDp) {
        Data.Battery b = Data.battery(c);
        int layout;
        int icon = 0;
        switch (b.state) {
            case Data.B_LOW: layout = R.layout.g_battery_low; break;
            case Data.B_CHARGING: layout = chargingLayout(c, b.level); break;
            case Data.B_FULL: layout = R.layout.g_battery_charge; icon = R.drawable.ic_check; break;
            case Data.B_SAVER: layout = R.layout.g_battery_saver; break;
            default: layout = R.layout.g_battery;
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        if (icon != 0) v.setImageViewResource(R.id.b_icon, icon);
        boolean wave = b.state == Data.B_CHARGING && layout != R.layout.g_battery_charge;

        float side = Math.max(48f, Math.min(wDp - 28f, hDp - 28f));
        Bitmap[] ring = Gfx.wavyRing(Math.round(Txt.dp(c, side)), b.level / 100f);
        v.setImageViewBitmap(R.id.b_track, ring[0]);
        v.setImageViewBitmap(R.id.b_act, ring[1]);
        if (wave) {
            // The wave replaces the still arc; sized to the ring.
            v.setViewVisibility(R.id.b_act, View.INVISIBLE);
            v.setViewLayoutWidth(R.id.b_wave, side, android.util.TypedValue.COMPLEX_UNIT_DIP);
            v.setViewLayoutHeight(R.id.b_wave, side, android.util.TypedValue.COMPLEX_UNIT_DIP);
        }
        Bitmap[] level = Txt.twoTone(c, b.level + "%", R620, 44f, 0.4f, -0.02f);
        v.setImageViewBitmap(R.id.b_num, level[0]);
        v.setImageViewBitmap(R.id.b_unit, level[1]);

        PendingIntent tap = Data.canToggleSaver(c)
                ? broadcast(c, ACTION_TOGGLE_SAVER, 10)
                : activity(c, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), 11);
        v.setOnClickPendingIntent(android.R.id.background, tap);
        return v;
    }

    private static Boolean waveOk;

    /**
     * Charging layout with the flowing wave for this level (4% steps). It's test-inflated once in
     * our own process first; if that fails the widget keeps the still ring and the app shows why.
     */
    private static int chargingLayout(Context c, int level) {
        int bucket = Math.max(4, Math.min(100, Math.round(level / 4f) * 4));
        int id = c.getResources().getIdentifier("g_battery_charge_" + bucket, "layout", c.getPackageName());
        if (id == 0) return R.layout.g_battery_charge;
        if (waveOk == null) {
            try {
                android.view.LayoutInflater.from(c).inflate(id, new android.widget.FrameLayout(c), false);
                waveOk = true;
                c.getSharedPreferences("diag", Context.MODE_PRIVATE).edit().remove("wave").apply();
            } catch (Throwable t) {
                waveOk = false;
                c.getSharedPreferences("diag", Context.MODE_PRIVATE).edit()
                        .putString("wave", t.getClass().getSimpleName() + ": " + t.getMessage()).apply();
            }
        }
        return waveOk ? id : R.layout.g_battery_charge;
    }

    // ------------------------------------------------------------------ screen time

    private static final int[] SEGS = {R.id.seg0, R.id.seg1, R.id.seg2, R.id.seg3};
    private static final int[] LEGEND = {R.id.lg0, R.id.lg1, R.id.lg2};
    private static final int[] LEGEND_NAME = {R.id.lg_n0, R.id.lg_n1, R.id.lg_n2};
    private static final int[] LEGEND_TIME = {R.id.lg_t0, R.id.lg_t1, R.id.lg_t2};
    private static final int[] RING = {R.id.ss0, R.id.ss1, R.id.ss2, R.id.ss3};

    private static RemoteViews screen(Context c, float wDp) {
        Data.Screen s = Data.screen(c);
        if (!s.allowed) return permission(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_screen);
        v.setImageViewBitmap(R.id.st_label, Txt.label(c, "Screen time", T540, 14f, 0f));

        Bitmap[] total = Txt.twoTone(c, Data.fmt(s.today), R620, 54f, 0.41f, -0.02f);
        v.setImageViewBitmap(R.id.st_num, total[0]);
        v.setImageViewBitmap(R.id.st_unit, total[1]);

        long diff = s.today - s.yesterdaySameTime;
        if (s.yesterdaySameTime <= 0) {
            v.setViewVisibility(R.id.st_chip, View.GONE);
        } else {
            v.setViewVisibility(R.id.st_chip, View.VISIBLE);
            String text;
            if (Math.abs(diff) < 60_000L) {
                v.setViewVisibility(R.id.st_arrow, View.GONE);
                text = "Same as yesterday";
            } else {
                v.setViewVisibility(R.id.st_arrow, View.VISIBLE);
                v.setImageViewResource(R.id.st_arrow, diff < 0 ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
                text = Data.fmt(Math.abs(diff)) + " vs yesterday";
            }
            v.setImageViewBitmap(R.id.st_delta, Txt.label(c, text, T540, 12f, 0f));
        }

        Bitmap[] bar = Gfx.segmentedBar(Math.round(Txt.dp(c, wDp - 40f)), Math.round(Txt.dp(c, 12f)), Txt.dp(c, 4f), s.segments);
        for (int i = 0; i < SEGS.length; i++) v.setImageViewBitmap(SEGS[i], bar[i]);

        for (int i = 0; i < 3; i++) {
            if (s.names[i] == null) {
                v.setViewVisibility(LEGEND[i], View.GONE);
            } else {
                v.setViewVisibility(LEGEND[i], View.VISIBLE);
                v.setImageViewBitmap(LEGEND_NAME[i], Txt.label(c, shorten(s.names[i], 11), T620, 12f, 0f));
                v.setImageViewBitmap(LEGEND_TIME[i], Txt.label(c, Data.fmt(s.times[i]), T540, 12f, 0f));
            }
        }
        v.setOnClickPendingIntent(android.R.id.background, openApp(c));
        return v;
    }

    private static RemoteViews screenSmall(Context c, float wDp, float hDp) {
        Data.Screen s = Data.screen(c);
        if (!s.allowed) return permission(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_screen_small);

        Bitmap[] total = Txt.twoTone(c, Data.fmt(s.today), R620, 30f, 0.5f, -0.02f);
        v.setImageViewBitmap(R.id.ss_num, total[0]);
        v.setImageViewBitmap(R.id.ss_unit, total[1]);

        // Change since the same time yesterday, as "↑ 24m" / "↓ 18m" / "Same".
        long diff = s.today - s.yesterdaySameTime;
        if (s.yesterdaySameTime <= 0) {
            v.setViewVisibility(R.id.ss_chip, View.GONE);
        } else {
            v.setViewVisibility(R.id.ss_chip, View.VISIBLE);
            String text;
            if (Math.abs(diff) < 60_000L) {
                v.setViewVisibility(R.id.ss_arrow, View.GONE);
                text = "Same";
            } else {
                v.setViewVisibility(R.id.ss_arrow, View.VISIBLE);
                v.setImageViewResource(R.id.ss_arrow, diff < 0 ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
                text = Data.fmt(Math.abs(diff));
            }
            v.setImageViewBitmap(R.id.ss_delta, Txt.label(c, text, T540, 12f, 0f));
        }

        float side = Math.max(48f, Math.min(wDp - 28f, hDp - 28f - 10f - 26f));
        Bitmap[] ring = Gfx.segmentedRing(Math.round(Txt.dp(c, side)), s.segments);
        for (int i = 0; i < RING.length; i++) v.setImageViewBitmap(RING[i], ring[i]);
        v.setOnClickPendingIntent(android.R.id.background, openApp(c));
        return v;
    }

    private static RemoteViews permission(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_perm);
        v.setImageViewBitmap(R.id.p_label, Txt.label(c, "Screen time", T540, 14f, 0f));
        v.setImageViewBitmap(R.id.p_title, Txt.label(c, "Tap to allow usage access", T620, 17f, 0f));
        v.setImageViewBitmap(R.id.p_body, Txt.label(c, "Counts app time on this phone only", T540, 12f, 0f));
        v.setOnClickPendingIntent(android.R.id.background,
                activity(c, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 12));
        return v;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    // ------------------------------------------------------------------ intents

    static PendingIntent activity(Context c, Intent intent, int requestCode) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent broadcast(Context c, String action, int requestCode) {
        Intent i = new Intent(c, Refresh.class).setAction(action);
        return PendingIntent.getBroadcast(c, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Opens the clock app itself (its home screen), not straight into the alarm list. */
    private static PendingIntent openAlarms(Context c) {
        android.content.pm.PackageManager pm = c.getPackageManager();
        Intent launch = null;
        // The app set to handle alarms, unless Android would show a chooser.
        android.content.pm.ResolveInfo ri = pm.resolveActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS), 0);
        if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
            launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
        }
        for (String pkg : new String[]{"com.google.android.deskclock", "com.sec.android.app.clockpackage"}) {
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        }
        if (launch == null) launch = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return activity(c, launch, 1);
    }

    private static PendingIntent openCalendar(Context c) {
        return activity(c, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), 2);
    }

    private static PendingIntent openApp(Context c) {
        return activity(c, new Intent(c, MainActivity.class), 3);
    }
}
