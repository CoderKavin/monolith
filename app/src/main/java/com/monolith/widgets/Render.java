package com.monolith.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Builds the RemoteViews for each widget at its current size (in dp). */
final class Render {

    private Render() {}

    static final int DISPLAY = 0, CENTRED = 1, CARD = 2, WEEK = 3, SCREEN = 4, MONTH = 5, BATTERY = 6, SCREEN_SMALL = 7;

    /** Design sizes: 4 × 2 is 380 × 204 dp, tall 2 × 2 is 184 × 204 dp. */
    static final float[] DEFAULT_W = {380, 380, 380, 380, 380, 184, 184, 184};
    static final float DEFAULT_H = 204;

    static final String ACTION_REFRESH = "com.monolith.widgets.REFRESH";
    static final String ACTION_TOGGLE_SAVER = "com.monolith.widgets.TOGGLE_SAVER";

    static RemoteViews build(Context c, int kind, float wDp, float hDp) {
        switch (kind) {
            case DISPLAY: return clock(c, R.layout.g_display);
            case CENTRED: return clock(c, R.layout.g_centred);
            case CARD: return card(c);
            case WEEK: return week(c);
            case SCREEN: return screen(c, wDp);
            case MONTH: return month(c, wDp, hDp);
            case BATTERY: return battery(c, wDp, hDp);
            case SCREEN_SMALL: return screenSmall(c, wDp, hDp);
            default: throw new IllegalArgumentException("kind " + kind);
        }
    }

    // ------------------------------------------------------------------ clocks

    private static RemoteViews clock(Context c, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        v.setOnClickPendingIntent(android.R.id.background, openAlarms(c));
        return v;
    }

    private static RemoteViews card(Context c) {
        RemoteViews v = clock(c, R.layout.g_card);
        String alarm = Data.nextAlarm(c);
        if (alarm == null) {
            v.setViewVisibility(R.id.alarm_chip, View.GONE);
        } else {
            v.setViewVisibility(R.id.alarm_chip, View.VISIBLE);
            v.setTextViewText(R.id.alarm_time, alarm);
        }
        return v;
    }

    // ------------------------------------------------------------------ calendar

    private static final int[] PILLS = {R.id.p0, R.id.p1, R.id.p2, R.id.p3, R.id.p4, R.id.p5, R.id.p6};
    private static final int[] LETTERS = {R.id.l0, R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6};
    private static final int[] NUMBERS = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5, R.id.n6};

    private static RemoteViews week(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_week);
        Calendar now = Calendar.getInstance();
        v.setTextViewText(R.id.wk_month, Data.monthName(now));
        v.setTextViewText(R.id.wk_no, "Week " + Data.isoWeek());

        Calendar day = (Calendar) now.clone();
        day.add(Calendar.DAY_OF_MONTH, -(now.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY));
        SimpleDateFormat letter = new SimpleDateFormat("EEEEE", Locale.getDefault());
        int onPrimary = c.getColor(R.color.g_on_primary);
        int onSurface = c.getColor(R.color.g_on_surface);
        for (int i = 0; i < 7; i++) {
            boolean isToday = day.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                    && day.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
            v.setTextViewText(LETTERS[i], letter.format(day.getTime()));
            v.setTextViewText(NUMBERS[i], String.valueOf(day.get(Calendar.DAY_OF_MONTH)));
            v.setInt(PILLS[i], "setBackgroundResource", isToday ? R.drawable.g_pill_today : R.drawable.g_pill);
            v.setTextColor(LETTERS[i], isToday ? onPrimary : onSurface);
            v.setTextColor(NUMBERS[i], isToday ? onPrimary : onSurface);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        v.setOnClickPendingIntent(android.R.id.background, openCalendar(c));
        return v;
    }

    private static RemoteViews month(Context c, float wDp, float hDp) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_month);
        Calendar now = Calendar.getInstance();
        v.setTextViewText(R.id.mo_name, Data.monthName(now));

        float dp = density(c);
        int w = Math.round((wDp - 24f) * dp);
        int h = Math.round((hDp - 16f - 14f - 20f - 6f) * dp);
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
        int layout, percentColor;
        String label = null;
        int icon = 0;
        switch (b.state) {
            case Data.B_LOW:
                layout = R.layout.g_battery_low;
                percentColor = R.color.g_on_surface_var;
                break;
            case Data.B_CHARGING:
                layout = R.layout.g_battery_charge;
                percentColor = R.color.g_on_primary_c;
                break;
            case Data.B_FULL:
                layout = R.layout.g_battery_charge;
                percentColor = R.color.g_on_primary_c;
                label = "Full";
                icon = R.drawable.ic_check;
                break;
            case Data.B_SAVER:
                layout = R.layout.g_battery_saver;
                percentColor = R.color.g_on_tertiary_c;
                break;
            default:
                layout = R.layout.g_battery;
                percentColor = R.color.g_on_surface_var;
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        if (label != null) v.setTextViewText(R.id.b_label, label);
        if (icon != 0) v.setImageViewResource(R.id.b_icon, icon);

        float side = Math.max(48f, Math.min(wDp - 32f, hDp - 32f - 20f - 10f));
        Bitmap[] ring = Gfx.wavyRing(Math.round(side * density(c)), b.level / 100f);
        v.setImageViewBitmap(R.id.b_track, ring[0]);
        v.setImageViewBitmap(R.id.b_act, ring[1]);

        SpannableString level = new SpannableString(b.level + "%");
        int pct = level.length() - 1;
        level.setSpan(new RelativeSizeSpan(0.4f), pct, pct + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        level.setSpan(new ForegroundColorSpan(c.getColor(percentColor)), pct, pct + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setTextViewText(R.id.b_level, level);

        PendingIntent tap = Data.canToggleSaver(c)
                ? broadcast(c, ACTION_TOGGLE_SAVER, 10)
                : activity(c, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), 11);
        v.setOnClickPendingIntent(android.R.id.background, tap);
        return v;
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
        v.setTextViewText(R.id.st_total, duration(c, s.today, 0.41f));

        long diff = s.today - s.yesterdaySameTime;
        if (s.yesterdaySameTime <= 0) {
            v.setViewVisibility(R.id.st_chip, View.GONE);
        } else {
            v.setViewVisibility(R.id.st_chip, View.VISIBLE);
            if (Math.abs(diff) < 60_000L) {
                v.setViewVisibility(R.id.st_arrow, View.GONE);
                v.setTextViewText(R.id.st_delta, "Same as yesterday");
            } else {
                v.setViewVisibility(R.id.st_arrow, View.VISIBLE);
                v.setImageViewResource(R.id.st_arrow, diff < 0 ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up);
                v.setTextViewText(R.id.st_delta, Data.fmt(Math.abs(diff)) + " vs yesterday");
            }
        }

        float dp = density(c);
        Bitmap[] bar = Gfx.segmentedBar(Math.round((wDp - 40f) * dp), Math.round(12f * dp), 4f * dp, s.segments);
        for (int i = 0; i < SEGS.length; i++) v.setImageViewBitmap(SEGS[i], bar[i]);

        for (int i = 0; i < 3; i++) {
            if (s.names[i] == null) {
                v.setViewVisibility(LEGEND[i], View.GONE);
            } else {
                v.setViewVisibility(LEGEND[i], View.VISIBLE);
                v.setTextViewText(LEGEND_NAME[i], s.names[i]);
                v.setTextViewText(LEGEND_TIME[i], Data.fmt(s.times[i]));
            }
        }
        v.setOnClickPendingIntent(android.R.id.background, openApp(c));
        return v;
    }

    private static RemoteViews screenSmall(Context c, float wDp, float hDp) {
        Data.Screen s = Data.screen(c);
        if (!s.allowed) return permission(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_screen_small);
        v.setTextViewText(R.id.ss_total, duration(c, s.today, 0.5f));

        long diff = s.today - s.yesterdaySameTime;
        String delta;
        if (s.yesterdaySameTime <= 0) delta = "Today so far";
        else if (Math.abs(diff) < 60_000L) delta = "Same as yesterday";
        else delta = Data.fmt(Math.abs(diff)) + (diff < 0 ? " less than yesterday" : " more than yesterday");
        v.setTextViewText(R.id.ss_delta, delta);

        float side = Math.max(48f, Math.min(wDp - 32f, hDp - 30f - 20f - 10f - 26f));
        Bitmap[] ring = Gfx.segmentedRing(Math.round(side * density(c)), s.segments);
        for (int i = 0; i < RING.length; i++) v.setImageViewBitmap(RING[i], ring[i]);
        v.setOnClickPendingIntent(android.R.id.background, openApp(c));
        return v;
    }

    private static RemoteViews permission(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.g_perm);
        v.setOnClickPendingIntent(android.R.id.background,
                activity(c, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 12));
        return v;
    }

    /** "1h 38m" with the h and m set smaller and quieter, as in the design. */
    private static CharSequence duration(Context c, long ms, float unitScale) {
        SpannableString s = new SpannableString(Data.fmt(ms));
        int quiet = c.getColor(R.color.g_on_surface_var);
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                s.setSpan(new RelativeSizeSpan(unitScale), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new ForegroundColorSpan(quiet), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ intents

    private static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

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

    private static PendingIntent openAlarms(Context c) {
        return activity(c, new Intent(AlarmClock.ACTION_SHOW_ALARMS), 1);
    }

    private static PendingIntent openCalendar(Context c) {
        return activity(c, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), 2);
    }

    private static PendingIntent openApp(Context c) {
        return activity(c, new Intent(c, MainActivity.class), 3);
    }
}
