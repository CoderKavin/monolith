package com.monolith.icons;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Opened when a launcher "applies" the pack: hand over to Monolith, which explains Theme Park. */
public class Pack extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent open = getPackageManager().getLaunchIntentForPackage("com.monolith.widgets");
        if (open != null) startActivity(open);
        finish();
    }
}
