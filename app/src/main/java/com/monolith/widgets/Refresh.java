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
import android.os.SystemClock;

/** Periodic refresh, reboot/update recovery, and the Battery widget's power-saving tap. */
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

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent i = new Intent(c, Refresh.class).setAction(Render.ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        boolean any = false;
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<?> cls : W.ALL) {
            if (m.getAppWidgetIds(new ComponentName(c, cls)).length > 0) { any = true; break; }
        }
        if (any) {
            // Inexact: batched with other wakeups, so the battery cost is negligible.
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                    AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi);
        } else {
            am.cancel(pi);
        }
    }
}
