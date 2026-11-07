package com.monolith.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Insets;
import android.net.Uri;
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

import java.io.File;
import java.io.IOException;

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

        findViewById(R.id.update_btn).setOnClickListener(v -> onUpdateTapped());
        findViewById(R.id.dock_btn).setOnClickListener(v -> startActivity(new Intent(this, DockActivity.class)));

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
        Refresh.schedule(this);
        rebuildGallery();
        if (!updating) checkForUpdate();
    }

    // ------------------------------------------------------------------ updates

    private long latest = -1;
    private boolean updating;

    private void checkForUpdate() {
        ((TextView) findViewById(R.id.update_body)).setText(R.string.update_checking);
        new Thread(() -> {
            long l = Updater.latestVersion();
            runOnUiThread(() -> showUpdateState(l));
        }).start();
    }

    private void showUpdateState(long l) {
        latest = l;
        TextView body = findViewById(R.id.update_body);
        TextView btn = findViewById(R.id.update_btn);
        String name = "Version " + Updater.installedName(this);
        if (l < 0) {
            body.setText(name + " · couldn't reach GitHub");
            btn.setText(R.string.update_check);
        } else if (l > Updater.installedVersion(this)) {
            body.setText("A newer version is ready to install");
            btn.setText(R.string.update_now);
        } else {
            body.setText(name + " · up to date");
            btn.setText(R.string.update_check);
        }
    }

    private void onUpdateTapped() {
        if (updating) return;
        if (latest > Updater.installedVersion(this)) startUpdate();
        else checkForUpdate();
    }

    private void startUpdate() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow Monolith to install updates, then tap Update again", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        updating = true;
        ((TextView) findViewById(R.id.update_body)).setText(R.string.update_working);
        new Thread(() -> {
            try {
                File apk = Updater.download(this);
                runOnUiThread(() -> {
                    try {
                        Updater.install(this, apk);
                    } catch (IOException e) {
                        updateFailed(e.getMessage());
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> updateFailed(e.getMessage()));
            }
        }).start();
    }

    private void updateFailed(String why) {
        updating = false;
        ((TextView) findViewById(R.id.update_body)).setText("Update failed" + (why != null ? ": " + why : ""));
        ((TextView) findViewById(R.id.update_btn)).setText(R.string.update_check);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!Updater.ACTION_INSTALL_STATUS.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android shows its own "Update this app?" confirmation.
            @SuppressWarnings("deprecation")
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) startActivity(confirm);
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            updateFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
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
