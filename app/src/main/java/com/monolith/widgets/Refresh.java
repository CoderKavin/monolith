package com.monolith.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Redraws every widget at the top of each minute. The clocks are drawn as images (so they can use
 * Google Sans Flex), which means they need this tick instead of ticking on their own.
 * The alarm is non-waking: while the screen is off nothing runs, and the first tick after the
 * screen comes on brings everything up to date.
 */
public class Refresh extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        if (Render.ACTION_TOGGLE_SAVER.equals(intent.getAction())) {
            Data.toggleSaver(c);
            // Power saving takes a moment to switch; redraw once it has.
            final PendingResult pending = goAsync();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    refreshAll(c);
                } finally {
                    pending.finish();
                }
            }, 800);
            return;
        }
        refreshAll(c);
        schedule(c);
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int kind = 0; kind < W.ALL.length; kind++) {
            for (int id : m.getAppWidgetIds(new ComponentName(c, W.ALL[kind]))) {
                Base.push(c, m, id, kind);
            }
        }
    }

    static boolean anyWidgets(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<?> cls : W.ALL) {
            if (m.getAppWidgetIds(new ComponentName(c, cls)).length > 0) return true;
        }
        return false;
    }

    /** Schedules the next tick for the start of the next minute, or cancels it when no widgets remain. */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent i = new Intent(c, Refresh.class).setAction(Render.ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        if (!anyWidgets(c)) {
            am.cancel(pi);
            return;
        }
        long now = System.currentTimeMillis();
        long next = (now / 60_000L + 1) * 60_000L + 100L;
        if (am.canScheduleExactAlarms()) {
            am.setExact(AlarmManager.RTC, next, pi);
        } else {
            // Without exact-alarm access the system may batch this; the app asks for access.
            am.setWindow(AlarmManager.RTC, next, 60_000L, pi);
        }
    }
}
