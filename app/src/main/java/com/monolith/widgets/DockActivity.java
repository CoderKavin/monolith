package com.monolith.widgets;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Dock bar: your wallpaper with a glass or solid bar behind One UI's dock, set as the home screen wallpaper. */
public class DockActivity extends Activity {

    private static final int PICK = 1, PICK_SHOT = 2;
    private Bitmap guide;
    private boolean showGuide = true;
    private final List<SeekBar> seekbars = new ArrayList<>();
    private final List<List<TextView>> chipGroups = new ArrayList<>();
    private DockWall.Settings s;
    private Bitmap source;
    private ImageView preview;
    private TextView status;
    private int screenW, screenH;
    private float density;
    private View countRow;
    private TextView allow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Rect bounds = getWindowManager().getMaximumWindowMetrics().getBounds();
        screenW = bounds.width();
        screenH = bounds.height();
        density = getResources().getDisplayMetrics().density;
        s = DockWall.Settings.load(this);
        File f = sourceFile();
        if (f.exists()) source = BitmapFactory.decodeFile(f.getAbsolutePath());
        buildUi();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentWallpaper();
        if (setAfterInstall && Updater.installedVersion(this, DOCK_PKG) > 0) {
            setAfterInstall = false;
            showOnHomeScreen();
        }
    }

    /**
     * Uses the wallpaper you have now. Once Monolith has set one, it keeps working from the clean
     * original it saved, unless you've since changed wallpaper, so the bar never gets drawn twice.
     */
    private void loadCurrentWallpaper() {
        if (!android.os.Environment.isExternalStorageManager()) {
            allow.setVisibility(View.VISIBLE);
            return;
        }
        allow.setVisibility(View.GONE);
        WallpaperManager wm = WallpaperManager.getInstance(this);
        int now = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        int ours = getSharedPreferences("dockwall", MODE_PRIVATE).getInt("ourId", Integer.MIN_VALUE);
        if (source != null && (now == ours || ourLiveWallpaperOn())) return;
        new Thread(() -> {
            try {
                android.graphics.drawable.Drawable d = android.os.Build.VERSION.SDK_INT >= 34
                        ? wm.getDrawable(WallpaperManager.FLAG_SYSTEM) : null;
                if (d == null) d = wm.getDrawable();
                if (d == null) return;
                Bitmap bmp;
                if (d instanceof android.graphics.drawable.BitmapDrawable && ((android.graphics.drawable.BitmapDrawable) d).getBitmap() != null) {
                    bmp = ((android.graphics.drawable.BitmapDrawable) d).getBitmap().copy(Bitmap.Config.ARGB_8888, false);
                } else {
                    int w = Math.max(1, d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : screenW);
                    int h = Math.max(1, d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : screenH);
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    d.setBounds(0, 0, w, h);
                    d.draw(new android.graphics.Canvas(bmp));
                }
                try (FileOutputStream os = new FileOutputStream(sourceFile())) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                }
                final Bitmap got = bmp;
                runOnUiThread(() -> {
                    source = got;
                    status.setText("Using your current wallpaper.");
                    refresh();
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> status.setText("Couldn't read your wallpaper, choose the photo instead."));
            }
        }).start();
    }

    private File sourceFile() {
        return new File(getFilesDir(), "dock_source.jpg");
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.app_bg));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets bars = in.getInsets(android.view.WindowInsets.Type.systemBars());
            root.setPadding(dp(20), bars.top + dp(20), dp(20), bars.bottom + dp(28));
            return in;
        });

        root.addView(text("Dock bar", 34, R.font.gsf_t540, R.color.ink));
        TextView body = text("A glass or solid bar behind the apps in your dock. Android doesn't let widgets go there, so Monolith draws the bar into your current wallpaper and sets it as your home screen.",
                14, R.font.gsf_t540, R.color.ink_2);
        body.setLineSpacing(0, 1.15f);
        root.addView(body, margins(0, 8));

        // Phone-shaped preview.
        int pw = dp(220), ph = Math.round(pw * (float) screenH / screenW);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(0xFF1A1B1F);
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(28));
            }
        });
        preview.setClipToOutline(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(pw, ph);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.topMargin = dp(22);
        root.addView(preview, plp);

        allow = pill("Allow access to use your wallpaper", true);
        allow.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException e) {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        });
        allow.setVisibility(View.GONE);
        root.addView(allow, margins(0, 18));
        TextView why = text("Android only shares your real wallpaper with apps that have All files access. Monolith reads the wallpaper and nothing else.",
                12, R.font.gsf_t540, R.color.ink_3);
        why.setLineSpacing(0, 1.15f);
        root.addView(why, margins(0, 6));

        TextView choose = pill("Use a different photo", false);
        choose.setOnClickListener(v -> pick());
        LinearLayout.LayoutParams clp = margins(0, 18);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(choose, clp);

        TextView line = pill("Line it up with a screenshot", false);
        line.setOnClickListener(v -> pickShot());
        root.addView(line, margins(0, 10));
        TextView how = text("Take a screenshot of your home screen first. Monolith finds your dock icons in it, sizes the bar to them and shows them over the preview.",
                13, R.font.gsf_t540, R.color.ink_3);
        how.setLineSpacing(0, 1.15f);
        root.addView(how, margins(0, 8));

        root.addView(chips("Shape", DockWall.SHAPES, s.shape, i -> {
            s.shape = i;
            countRow.setVisibility(i == DockWall.CIRCLES ? View.VISIBLE : View.GONE);
        }));
        root.addView(chips("Look", DockWall.LOOKS, s.look, i -> s.look = i));
        countRow = chips("Apps", new String[]{"4", "5"}, s.count == 5 ? 1 : 0, i -> s.count = i == 1 ? 5 : 4);
        countRow.setVisibility(s.shape == DockWall.CIRCLES ? View.VISIBLE : View.GONE);
        root.addView(countRow);

        root.addView(chips("Overlay", new String[]{"My icons", "Off"}, 0, i -> showGuide = i == 0));
        root.addView(slider("Height from bottom", 40, 260, s.posDp, v -> s.posDp = v));
        root.addView(slider("Bar height", 52, 120, s.heightDp, v -> s.heightDp = v));
        root.addView(slider("Side margin", 0, 40, s.marginDp, v -> s.marginDp = v));

        TextView apply = pill("Set dock bar", true);
        apply.setOnClickListener(v -> apply());
        root.addView(apply, margins(0, 26));

        status = text("Tip: apply once, go home and check it lines up with your dock icons, then nudge Height from bottom and apply again.",
                13, R.font.gsf_t540, R.color.ink_3);
        status.setLineSpacing(0, 1.15f);
        root.addView(status, margins(0, 12));

        setContentView(scroll);
    }

    private interface IntPick { void on(int v); }

    private View chips(String label, String[] names, int selected, IntPick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(margins(0, 18));
        row.addView(text(label, 14, R.font.gsf_t540, R.color.ink_2), new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView c = text(names[i], 14, R.font.gsf_t620, R.color.ink);
            c.setPadding(dp(16), dp(8), dp(16), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chips.add(c);
            c.setOnClickListener(v -> {
                pick.on(idx);
                styleChips(chips, idx);
                refresh();
            });
            row.addView(c, lp);
        }
        styleChips(chips, selected);
        chipGroups.add(chips);
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
                bg.setStroke(dp(1), 0x40FFFFFF);
                chips.get(i).setTextColor(getColor(R.color.ink));
            }
            chips.get(i).setBackground(bg);
        }
    }

    private View slider(String label, int min, int max, int value, IntPick pick) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(margins(0, 18));
        TextView l = text(label + " · " + value + " dp", 14, R.font.gsf_t540, R.color.ink_2);
        box.addView(l);
        SeekBar bar = new SeekBar(this);
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(value);
        seekbars.add(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int v, boolean user) {
                l.setText(label + " · " + v + " dp");
                pick.on(v);
                refresh();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        box.addView(bar, margins(0, 4));
        return box;
    }

    private TextView pill(String label, boolean filled) {
        TextView t = text(label, 15, R.font.gsf_t620, filled ? R.color.app_bg : R.color.ink);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(22), dp(13), dp(22), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(100));
        if (filled) bg.setColor(getColor(R.color.ink));
        else bg.setStroke(dp(1), 0x59FFFFFF);
        t.setBackground(bg);
        return t;
    }

    // ------------------------------------------------------------------ preview + apply

    private void refresh() {
        if (source == null) {
            preview.setImageDrawable(null);
            return;
        }
        // Preview at a third of the screen's size; dp values scale with it.
        int w = screenW / 3, h = screenH / 3;
        Bitmap out = DockWall.compose(this, source, w, h, s, density / 3f);
        if (guide != null && showGuide) {
            // Your real home screen at half strength on top, so the dock icons show where they'll sit.
            android.graphics.Canvas cv = new android.graphics.Canvas(out);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            p.setAlpha(150);
            cv.drawBitmap(guide, null, new android.graphics.RectF(0, 0, w, h), p);
        }
        preview.setImageBitmap(out);
    }

    private void pick() {
        Intent i = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
        try {
            startActivityForResult(i, PICK);
        } catch (RuntimeException e) {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK);
        }
    }

    private void pickShot() {
        Intent i = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
        try {
            startActivityForResult(i, PICK_SHOT);
        } catch (RuntimeException e) {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK_SHOT);
        }
    }

    private void useShot(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap shot = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (dec, info, src) -> dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
                DockWall.Settings found = DockWall.detect(shot, screenW, screenH, density, s);
                Bitmap small = Bitmap.createScaledBitmap(shot, screenW / 3, screenH / 3, true);
                runOnUiThread(() -> {
                    guide = small;
                    if (found == null) {
                        status.setText("Couldn't find the dock icons in that screenshot. Line the bar up by eye with the sliders; your icons are shown over the preview.");
                    } else {
                        s.posDp = found.posDp;
                        s.heightDp = found.heightDp;
                        s.marginDp = found.marginDp;
                        s.count = found.count;
                        seekbars.get(0).setProgress(s.posDp);
                        seekbars.get(1).setProgress(s.heightDp);
                        seekbars.get(2).setProgress(s.marginDp);
                        if (chipGroups.size() > 2) styleChips(chipGroups.get(2), s.count == 5 ? 1 : 0);
                        status.setText("Found your dock: the bar is sized and placed behind those icons. Check the preview, then set it.");
                    }
                    refresh();
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Couldn't open that screenshot", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_SHOT && res == RESULT_OK && data != null && data.getData() != null) {
            useShot(data.getData());
            return;
        }
        if (req != PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            try {
                Bitmap bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (dec, info, src) -> {
                    dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int iw = info.getSize().getWidth(), ih = info.getSize().getHeight();
                    float scale = Math.max((float) screenW / iw, (float) screenH / ih);
                    if (scale < 1f) dec.setTargetSize(Math.max(1, Math.round(iw * scale)), Math.max(1, Math.round(ih * scale)));
                });
                try (FileOutputStream os = new FileOutputStream(sourceFile())) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                }
                runOnUiThread(() -> {
                    source = bmp;
                    refresh();
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Couldn't open that photo", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    static final String DOCK_PKG = "com.monolith.dock";
    private boolean setAfterInstall;

    private boolean ourLiveWallpaperOn() {
        android.app.WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
        return info != null && DOCK_PKG.equals(info.getPackageName());
    }

    private void apply() {
        if (source == null) {
            Toast.makeText(this, "Choose your wallpaper photo first", Toast.LENGTH_SHORT).show();
            return;
        }
        s.save(this);
        status.setText("Preparing…");
        new Thread(() -> {
            try {
                Bitmap full = DockWall.compose(this, source, screenW, screenH, s, density);
                try (FileOutputStream os = new FileOutputStream(DockProvider.file(this))) {
                    full.compress(Bitmap.CompressFormat.PNG, 100, os);
                }
                runOnUiThread(this::showOnHomeScreen);
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> status.setText("Couldn't prepare the wallpaper: " + e.getMessage()));
            }
        }).start();
    }

    /** Hands the picture to the Monolith dock wallpaper app, installing it the first time. */
    private void showOnHomeScreen() {
        if (Updater.installedVersion(this, DOCK_PKG) == 0) {
            installDockApp();
            return;
        }
        if (ourLiveWallpaperOn()) {
            sendBroadcast(new Intent("com.monolith.dock.CHANGED").setPackage(DOCK_PKG),
                    "com.monolith.widgets.permission.DOCK_WALL");
            status.setText("Updated. The dock bar stays still, and Monolith updates won't reset it.");
            return;
        }
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new android.content.ComponentName(DOCK_PKG, DOCK_PKG + ".DockWallpaper"));
        try {
            startActivity(i);
            status.setText("Tap Set wallpaper and choose Home screen. You only do this once.");
        } catch (RuntimeException e) {
            status.setText("Couldn't open the wallpaper picker: " + e.getMessage());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!Updater.ACTION_INSTALL_STATUS.equals(intent.getAction())) return;
        int st = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,
                android.content.pm.PackageInstaller.STATUS_FAILURE);
        if (st == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation")
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) startActivity(confirm);
        } else if (st != android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            setAfterInstall = false;
            status.setText("Install didn't finish: " + intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    }

    /** One-time: the small Monolith dock app that shows the wallpaper. It never needs updating. */
    private void installDockApp() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow Monolith to install apps, then tap Set dock bar again", Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        status.setText("Installing the Monolith dock wallpaper (one time)…");
        setAfterInstall = true;
        new Thread(() -> {
            try {
                File apk = Updater.download(this, "MonolithDock.apk");
                runOnUiThread(() -> {
                    try {
                        Updater.install(this, apk, DOCK_PKG);
                    } catch (IOException e) {
                        status.setText("Couldn't install it: " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> status.setText("Couldn't download it: " + e.getMessage()));
            }
        }).start();
    }

    // ------------------------------------------------------------------ helpers

    private TextView text(String str, float sp, int font, int color) {
        TextView t = new TextView(this);
        t.setText(str);
        t.setTextSize(sp);
        t.setTypeface(getResources().getFont(font));
        t.setTextColor(getColor(color));
        return t;
    }

    private LinearLayout.LayoutParams margins(int l, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(l);
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
