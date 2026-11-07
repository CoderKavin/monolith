package com.monolith.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

/** Shared provider: renders at the widget's real size and re-renders when it is resized. */
public abstract class Base extends AppWidgetProvider {

    abstract int kind();

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) push(c, m, id, kind());
        Refresh.schedule(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        push(c, m, id, kind());
    }

    @Override
    public void onDisabled(Context c) {
        Refresh.schedule(c);
    }

    static void push(Context c, AppWidgetManager m, int id, int kind) {
        Bundle o = m.getAppWidgetOptions(id);
        // Portrait size: full width from MIN_WIDTH, full height from MAX_HEIGHT.
        float w = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
        float h = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) : 0;
        if (w <= 0) w = Render.DEFAULT_W[kind];
        if (h <= 0) h = Render.DEFAULT_H;
        try {
            m.updateAppWidget(id, Render.build(c, kind, w, h));
        } catch (RuntimeException e) {
            Log.w("Monolith", "Widget " + kind + " failed to render", e);
        }
    }
}
