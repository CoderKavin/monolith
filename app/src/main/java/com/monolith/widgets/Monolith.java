package com.monolith.widgets;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

/**
 * Redraws the Battery widget the moment the charger, power saving or charge level changes,
 * whenever Android lets this app run. The minute tick catches anything that arrives while it can't.
 */
public class Monolith extends Application {

    private static int lastLevel = -1;
    private static int lastState = -1;

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Dark mode or wallpaper colours changed: the dock is drawn in-app, so redraw it.
        Refresh.refreshKind(this, Render.DOCK);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                Data.Battery b = Data.battery(c);
                // ACTION_BATTERY_CHANGED also fires for voltage and temperature; only redraw on visible changes.
                if (b.level == lastLevel && b.state == lastState) return;
                lastLevel = b.level;
                lastState = b.state;
                Refresh.refreshKind(c, Render.BATTERY);
            }
        }, f, Context.RECEIVER_EXPORTED);
    }
}
