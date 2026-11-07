package com.monolith.widgets;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dock bar setup: shape, material, icon style and up to five apps (tap order = dock order). */
public class DockConfig extends Activity {

    static final String EXTRA_PIN = "pin";

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean pinMode;
    private Dock.Config cfg;
    private FrameLayout preview;
    private final List<App> all = new ArrayList<>(), shown = new ArrayList<>();
    private AppAdapter adapter;
    private final Map<String, Drawable> icons = new HashMap<>();

    private static final class App {
        ComponentName cn;
        String label;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        pinMode = getIntent().getBooleanExtra(EXTRA_PIN, false);

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            cfg = Dock.load(this, widgetId);
            if (cfg == null) {
                // Added from Monolith a moment ago: the launcher opened setup for it, use what was picked.
                Dock.Config pending = Dock.takePending(this);
                if (pending != null) {
                    finishWith(pending);
                    return;
                }
            }
        }
        if (cfg == null) cfg = Dock.defaults(this);
        buildUi();
        loadApps();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.app_bg));
        root.setPadding(dp(20), dp(20), dp(20), 0);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets bars = in.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(dp(20), bars.top + dp(16), dp(20), bars.bottom + dp(12));
            return in;
        });

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Dock bar", 30, R.font.gsf_t540, R.color.ink);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView done = text(pinMode ? "Add" : "Done", 15, R.font.gsf_t620, R.color.app_bg);
        done.setBackgroundResource(R.drawable.pill);
        done.setPadding(dp(22), dp(10), dp(22), dp(10));
        done.setOnClickListener(v -> onDone());
        head.addView(done);
        root.addView(head);

        preview = new FrameLayout(this);
        preview.setBackgroundResource(R.drawable.stage);
        preview.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(124));
        plp.topMargin = dp(16);
        root.addView(preview, plp);

        root.addView(chips("Shape", Dock.SHAPE_NAMES, cfg.shape, i -> cfg.shape = i));
        root.addView(chips("Look", Dock.MATERIAL_NAMES, cfg.material, i -> cfg.material = i));
        root.addView(chips("Icons", Dock.ICON_NAMES, cfg.themed ? 1 : 0, i -> cfg.themed = i == 1));

        TextView hint = text("Apps · tap up to five, in the order you want them", 13, R.font.gsf_t540, R.color.ink_2);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(18);
        root.addView(hint, hlp);

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextColor(getColor(R.color.ink));
        search.setHintTextColor(getColor(R.color.ink_3));
        search.setTypeface(getResources().getFont(R.font.gsf_t540));
        search.setTextSize(15);
        search.setBackgroundResource(R.drawable.panel);
        search.setPadding(dp(16), dp(12), dp(16), dp(12));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(10);
        root.addView(search, slp);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> toggle(shown.get(pos)));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        llp.topMargin = dp(8);
        root.addView(list, llp);

        setContentView(root);
        refreshPreview();
    }

    private interface Pick { void on(int i); }

    private View chips(String label, String[] names, int selected, Pick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(14);
        row.setLayoutParams(rlp);
        TextView l = text(label, 14, R.font.gsf_t540, R.color.ink_2);
        row.addView(l, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView c = text(names[i], 14, R.font.gsf_t620, R.color.ink);
            c.setPadding(dp(16), dp(8), dp(16), dp(8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = dp(8);
            chips.add(c);
            c.setOnClickListener(v -> {
                pick.on(idx);
                styleChips(chips, idx);
                refreshPreview();
            });
            row.addView(c, clp);
        }
        styleChips(chips, selected);
        return row;
    }

    private void styleChips(List<TextView> chips, int sel) {
        for (int i = 0; i < chips.size(); i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(100));
            if (i == sel) {
                bg.setColor(getColor(R.color.ink));
                chips.get(i).setTextColor(getColor(R.color.app_bg));
            } else {
                bg.setColor(0x00000000);
                bg.setStroke(dp(1), 0x40FFFFFF);
                chips.get(i).setTextColor(getColor(R.color.ink));
            }
            chips.get(i).setBackground(bg);
        }
    }

    private void refreshPreview() {
        preview.removeAllViews();
        float wDp = (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 40 - 28;
        View v = Dock.render(this, cfg, -1, wDp, 96).apply(this, preview);
        preview.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ResolveInfo> ris = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            List<App> apps = new ArrayList<>();
            for (ResolveInfo ri : ris) {
                if (getPackageName().equals(ri.activityInfo.packageName)) continue;
                App a = new App();
                a.cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                a.label = String.valueOf(ri.loadLabel(pm));
                apps.add(a);
            }
            Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(apps);
                filter("");
            });
        }).start();
    }

    private void filter(String q) {
        shown.clear();
        String needle = q.trim().toLowerCase(Locale.ROOT);
        for (App a : all) if (needle.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(needle)) shown.add(a);
        adapter.notifyDataSetChanged();
    }

    private void toggle(App a) {
        if (cfg.apps.remove(a.cn)) {
            // removed
        } else if (cfg.apps.size() >= Dock.MAX) {
            Toast.makeText(this, "The dock holds five apps. Untick one first.", Toast.LENGTH_SHORT).show();
            return;
        } else {
            cfg.apps.add(a.cn);
        }
        adapter.notifyDataSetChanged();
        refreshPreview();
    }

    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int p) { return shown.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row;
            if (convert instanceof LinearLayout) row = (LinearLayout) convert;
            else {
                row = new LinearLayout(DockConfig.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(9), dp(4), dp(9));
                ImageView iv = new ImageView(DockConfig.this);
                row.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
                TextView tv = text("", 15, R.font.gsf_t540, R.color.ink);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                tlp.leftMargin = dp(14);
                row.addView(tv, tlp);
                TextView badge = text("", 13, R.font.gsf_t620, R.color.app_bg);
                badge.setGravity(Gravity.CENTER);
                row.addView(badge, new LinearLayout.LayoutParams(dp(26), dp(26)));
            }
            App a = shown.get(pos);
            ImageView iv = (ImageView) row.getChildAt(0);
            String k = a.cn.flattenToShortString();
            Drawable d = icons.get(k);
            if (d == null) {
                d = Dock.icon(DockConfig.this, a.cn);
                if (d != null) icons.put(k, d);
            }
            iv.setImageDrawable(d);
            ((TextView) row.getChildAt(1)).setText(a.label);
            TextView badge = (TextView) row.getChildAt(2);
            int order = cfg.apps.indexOf(a.cn);
            if (order >= 0) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(getColor(R.color.ink));
                badge.setBackground(bg);
                badge.setText(String.valueOf(order + 1));
            } else {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setStroke(dp(1), 0x40FFFFFF);
                badge.setBackground(bg);
                badge.setText("");
            }
            return row;
        }
    }

    // ------------------------------------------------------------------ finish

    private void onDone() {
        if (cfg.apps.isEmpty()) {
            Toast.makeText(this, "Pick at least one app", Toast.LENGTH_SHORT).show();
            return;
        }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            finishWith(cfg);
            return;
        }
        // Started from Monolith: remember the choice, ask the launcher to place the widget.
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        if (!m.isRequestPinAppWidgetSupported()) {
            Toast.makeText(this, "Long-press your home screen → Widgets → Monolith → Dock bar", Toast.LENGTH_LONG).show();
            return;
        }
        Dock.savePending(this, cfg);
        Intent cb = new Intent(this, Refresh.class).setAction(Refresh.ACTION_DOCK_PINNED);
        PendingIntent pi = PendingIntent.getBroadcast(this, 77, cb,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        m.requestPinAppWidget(new ComponentName(this, W.Dock.class), null, pi);
        finish();
    }

    private void finishWith(Dock.Config c) {
        Dock.save(this, widgetId, c);
        Base.push(this, AppWidgetManager.getInstance(this), widgetId, Render.DOCK);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }

    // ------------------------------------------------------------------ helpers

    private TextView text(String s, float sp, int font, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        Typeface tf = getResources().getFont(font);
        t.setTypeface(tf);
        t.setTextColor(getColor(color));
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
