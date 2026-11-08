package com.monolith.widgets;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Hands the dock bar picture to the Monolith dock wallpaper app (signature permission only).
 * query() returns the picture's version (last modified) so the wallpaper reloads only on change.
 */
public class DockProvider extends ContentProvider {

    static File file(android.content.Context c) {
        return new File(c.getFilesDir(), "dock_wall.png");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(getContext());
        if (!f.exists()) throw new FileNotFoundException("No dock bar yet");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        MatrixCursor c = new MatrixCursor(new String[]{"version"});
        c.addRow(new Object[]{file(getContext()).lastModified()});
        return c;
    }

    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
