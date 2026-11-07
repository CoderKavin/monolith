package com.monolith.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

/** Gallery: live previews rendered from the real widget code, one-tap Add, and the two setup cards. */
public class MainActivity extends Activity {

    private static final String[] NAMES = {
            "Clock · Display", "Clock · Display centred", "Clock · Card", "Week",
            "Screen time", "Month", "Battery", "Screen time · small"
    };
    private static final String[] META = {
            "4 × 2 · no background · time and date",
            "4 × 2 · no background · time only",
            "4 × 2 · date and next alarm as chips",
            "4 × 2 · today as the filled pill",
            "4 × 2 · today, split by your top apps",
            "2 × 2 · opens calendar",
            "2 × 2 · charging, full, low, power saving",
            "2 × 2 · segmented ring by app"
    };

    private LinearLayout items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        items = findViewById(R.id.items);

        View content = findViewById(R.id.content);
        findViewById(R.id.scroll).setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            content.setPadding(content.getPaddingLeft(), bars.top + dp(36),
                    content.getPaddingRight(), bars.bottom + dp(36));
            return insets;
        });

        findViewById(R.id.usage_btn).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean usage = Data.usageAllowed(this);
        ((TextView) findViewById(R.id.usage_body)).setText(usage ? R.string.usage_on : R.string.usage_off);
        findViewById(R.id.usage_btn).setVisibility(usage ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.saver_body)).setText(
                Data.canToggleSaver(this) ? R.string.saver_on : R.string.saver_off);

        // Widgets on the home screen pick up a newly granted permission straight away.
        Refresh.refreshAll(this);
        rebuildGallery();
    }

    private void rebuildGallery() {
        items.removeAllViews();
        int screenDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        float wideW = Math.min(380f, screenDp - 80f);
        float wideH = Math.round(204f * wideW / 380f);
        for (int kind = 0; kind < NAMES.length; kind++) {
            boolean wide = Render.DEFAULT_W[kind] > 300;
            float w = wide ? wideW : 184f;
            float h = wide ? wideH : 204f;
            add(kind, w, h, wide);
        }
    }

    private void add(int kind, float wDp, float hDp, boolean wide) {
        View row = getLayoutInflater().inflate(R.layout.item_widget, items, false);
        ((TextView) row.findViewById(R.id.name)).setText(NAMES[kind]);
        ((TextView) row.findViewById(R.id.meta)).setText(META[kind]);

        FrameLayout stage = row.findViewById(R.id.stage);
        try {
            RemoteViews preview = Render.build(this, kind, wDp, hDp);
            View widget = preview.apply(this, stage);
            int w = wide ? ViewGroup.LayoutParams.MATCH_PARENT : dp(Math.round(wDp));
            stage.addView(widget, new FrameLayout.LayoutParams(w, dp(Math.round(hDp)), Gravity.CENTER));
        } catch (RuntimeException e) {
            stage.setMinimumHeight(dp(Math.round(hDp)));
        }

        final Class<?> provider = W.ALL[kind];
        row.findViewById(R.id.add).setOnClickListener(v -> pin(provider));
        items.addView(row);
    }

    private void pin(Class<?> provider) {
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        if (m.isRequestPinAppWidgetSupported()) {
            m.requestPinAppWidget(new ComponentName(this, provider), null, null);
        } else {
            Toast.makeText(this, "Long-press your home screen → Widgets → Monolith", Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
