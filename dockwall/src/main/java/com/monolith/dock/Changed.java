package com.monolith.dock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Monolith saved a new dock bar picture: redraw now. */
public class Changed extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        DockWallpaper.changed();
    }
}
