package com.monolith.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/** Home: update chip, Dock bar, a gallery of live widget previews with one-tap Add, and the setup list. */
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
            "2 × 2 · every charge state",
            "2 × 2 · ring by app"
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

        findViewById(R.id.update_chip).setOnClickListener(v -> onUpdateTapped());
        findViewById(R.id.dock_card).setOnClickListener(v -> startActivity(new Intent(this, DockActivity.class)));

        View.OnClickListener usage = v -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        };
        findViewById(R.id.row_usage).setOnClickListener(usage);
        findViewById(R.id.usage_action).setOnClickListener(usage);

        View detail = findViewById(R.id.saver_detail);
        View.OnClickListener saver = v -> {
            if (Data.canToggleSaver(this)) return;
            detail.setVisibility(detail.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        };
        findViewById(R.id.row_saver).setOnClickListener(saver);
        findViewById(R.id.saver_action).setOnClickListener(saver);
        detail.setOnClickListener(v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("adb",
                    "adb shell pm grant com.monolith.widgets android.permission.WRITE_SECURE_SETTINGS"));
            Toast.makeText(this, "Command copied", Toast.LENGTH_SHORT).show();
        });

        View.OnClickListener battery = v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
        findViewById(R.id.row_battery).setOnClickListener(battery);
        findViewById(R.id.battery_action).setOnClickListener(battery);

        ((TextView) findViewById(R.id.footer)).setText("Monolith " + Updater.installedName(this)
                + " · Google Sans Flex · Material 3 Expressive");
        ((TextView) findViewById(R.id.widget_count)).setText(NAMES.length + " · tap + to add");
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean usage = Data.usageAllowed(this);
        ((TextView) findViewById(R.id.usage_body)).setText(usage ? "Usage access is on" : "Needs usage access. Nothing leaves your phone.");
        pill(findViewById(R.id.usage_action), usage ? "On" : "Allow", usage);
        boolean saver = Data.canToggleSaver(this);
        ((TextView) findViewById(R.id.saver_body)).setText(saver ? "Tap the Battery widget to switch it" : "One-time grant from a computer");
        pill(findViewById(R.id.saver_action), saver ? "On" : "Set up", saver);
        if (saver) findViewById(R.id.saver_detail).setVisibility(View.GONE);

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
        chip("Checking…", 0);
        new Thread(() -> {
            long l = Updater.latestVersion();
            runOnUiThread(() -> showUpdateState(l));
        }).start();
    }

    private void showUpdateState(long l) {
        latest = l;
        String name = Updater.installedName(this);
        if (l < 0) chip(name + " · Offline, tap to retry", 0);
        else if (l > Updater.installedVersion(this)) chip("Update to 2." + (l - 100), 2);
        else chip(name + " · Up to date", 1);
    }

    /** style: 0 neutral, 1 up to date (green dot), 2 update ready (accent). */
    private void chip(String text, int style) {
        TextView c = findViewById(R.id.update_chip);
        c.setText(text);
        c.setBackgroundResource(style == 2 ? R.drawable.ui_chip_accent : R.drawable.ui_chip);
        c.setTextColor(getColor(style == 2 ? R.color.ui_on_accent : R.color.ui_sub));
        int dot = style == 1 ? R.drawable.ui_dot_ok : style == 2 ? R.drawable.ui_dot_accent : R.drawable.ui_dot_faint;
        c.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, 0, 0, 0);
    }

    private void pill(TextView p, String text, boolean ok) {
        p.setText(text);
        p.setBackgroundResource(ok ? R.drawable.ui_pill_ok : R.drawable.ui_chip_accent);
        p.setTextColor(getColor(ok ? R.color.ui_ok_fg : R.color.ui_on_accent));
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
        chip("Downloading…", 2);
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
        chip("Update failed, tap to retry", 0);
        if (why != null) Toast.makeText(this, why, Toast.LENGTH_LONG).show();
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
        float screenDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        float content = screenDp - 32f;              // page padding
        float wideStage = content - 16f - 24f;       // card padding + stage padding
        float smallStage = (content - 12f) / 2f - 16f - 24f;

        LinearLayout pair = null;
        for (int kind = 0; kind < NAMES.length; kind++) {
            boolean wide = Render.DEFAULT_W[kind] > 300;
            View card = card(kind, wide ? wideStage : smallStage);
            if (wide) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(12);
                items.addView(card, lp);
                pair = null;
            } else {
                if (pair == null || pair.getChildCount() == 2) {
                    pair = new LinearLayout(this);
                    pair.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(12);
                    items.addView(pair, lp);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (pair.getChildCount() == 1) lp.leftMargin = dp(12);
                pair.addView(card, lp);
            }
        }
        // An odd 2 × 2 at the end keeps its half width.
        if (pair != null && pair.getChildCount() == 1) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
            lp.leftMargin = dp(12);
            pair.addView(new View(this), lp);
        }
    }

    /**
     * The widget is built at its real home-screen size (4 × 2 = 380 × 204 dp, 2 × 2 = 184 × 204 dp)
     * and the finished preview is scaled down as one picture, so it looks exactly like the widget.
     */
    private View card(int kind, float stageDp) {
        View card = getLayoutInflater().inflate(R.layout.item_widget, items, false);
        ((TextView) card.findViewById(R.id.name)).setText(NAMES[kind]);
        ((TextView) card.findViewById(R.id.meta)).setText(META[kind]);

        float wDp = Render.DEFAULT_W[kind], hDp = Render.DEFAULT_H;
        float scale = Math.min(1f, stageDp / wDp);
        FrameLayout stage = card.findViewById(R.id.stage);
        FrameLayout box = new FrameLayout(this);
        try {
            RemoteViews preview = Render.build(this, kind, wDp, hDp);
            View widget = preview.apply(this, box);
            widget.setPivotX(0f);
            widget.setPivotY(0f);
            widget.setScaleX(scale);
            widget.setScaleY(scale);
            box.addView(widget, new FrameLayout.LayoutParams(dp(Math.round(wDp)), dp(Math.round(hDp))));
        } catch (RuntimeException e) {
            // leave the stage empty rather than crash the gallery
        }
        stage.addView(box, new FrameLayout.LayoutParams(Math.round(dp(1) * wDp * scale), Math.round(dp(1) * hDp * scale), Gravity.CENTER));

        final Class<?> provider = W.ALL[kind];
        card.findViewById(R.id.add).setOnClickListener(v -> pin(provider));
        return card;
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
