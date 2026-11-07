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

    private static final int PICK = 1;
    private DockWall.Settings s;
    private Bitmap source;
    private ImageView preview;
    private TextView status;
    private int screenW, screenH;
    private float density;
    private View countRow;

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
        TextView body = text("A glass or solid bar behind the apps in your dock. Android doesn't let widgets go there, so Monolith draws the bar into your wallpaper and sets it as your home screen. Pick the photo you use as your wallpaper.",
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

        TextView choose = pill("Choose wallpaper photo", false);
        choose.setOnClickListener(v -> pick());
        LinearLayout.LayoutParams clp = margins(0, 18);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(choose, clp);

        root.addView(chips("Shape", DockWall.SHAPES, s.shape, i -> {
            s.shape = i;
            countRow.setVisibility(i == DockWall.CIRCLES ? View.VISIBLE : View.GONE);
        }));
        root.addView(chips("Look", DockWall.LOOKS, s.look, i -> s.look = i));
        countRow = chips("Apps", new String[]{"4", "5"}, s.count == 5 ? 1 : 0, i -> s.count = i == 1 ? 5 : 4);
        countRow.setVisibility(s.shape == DockWall.CIRCLES ? View.VISIBLE : View.GONE);
        root.addView(countRow);

        root.addView(slider("Height from bottom", 40, 260, s.posDp, v -> s.posDp = v));
        root.addView(slider("Bar height", 52, 120, s.heightDp, v -> s.heightDp = v));
        root.addView(slider("Side margin", 0, 40, s.marginDp, v -> s.marginDp = v));

        TextView apply = pill("Set as home screen wallpaper", true);
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
        preview.setImageBitmap(DockWall.compose(this, source, w, h, s, density / 3f));
    }

    private void pick() {
        Intent i = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
        try {
            startActivityForResult(i, PICK);
        } catch (RuntimeException e) {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
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

    private void apply() {
        if (source == null) {
            Toast.makeText(this, "Choose your wallpaper photo first", Toast.LENGTH_SHORT).show();
            return;
        }
        s.save(this);
        status.setText("Setting wallpaper…");
        new Thread(() -> {
            try {
                Bitmap full = DockWall.compose(this, source, screenW, screenH, s, density);
                WallpaperManager.getInstance(this).setBitmap(full, null, true, WallpaperManager.FLAG_SYSTEM);
                runOnUiThread(() -> status.setText("Done. Go home and check the bar sits behind your dock icons; adjust Height from bottom if it doesn't, then set it again."));
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> status.setText("Couldn't set the wallpaper: " + e.getMessage()));
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
