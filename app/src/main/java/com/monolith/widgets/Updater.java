package com.monolith.widgets;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Pulls the newest build from this project's GitHub release ("latest") and installs it in place.
 * Network work runs on a background thread; callers get results on that thread.
 */
final class Updater {

    private Updater() {}

    static final String BASE = "https://github.com/CoderKavin/monolith/releases/download/latest/";
    static final String ACTION_INSTALL_STATUS = "com.monolith.widgets.INSTALL_STATUS";

    static long installedVersion(Context c) {
        return installedVersion(c, c.getPackageName());
    }

    /** Version code of any package, or 0 if it isn't installed. */
    static long installedVersion(Context c, String pkg) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(pkg, 0);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    /** Latest published version code, or -1 if it can't be reached. */
    static long latestVersion() {
        long v = fetchLatest();
        if (v < 0) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            v = fetchLatest();
        }
        return v;
    }

    private static long fetchLatest() {
        HttpURLConnection conn = null;
        try {
            conn = open(BASE + "version.txt");
            if (conn.getResponseCode() != 200) return -1;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line = r.readLine();
                return line == null ? -1 : Long.parseLong(line.trim());
            }
        } catch (IOException | NumberFormatException e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Downloads the app's own APK to the cache. */
    static File download(Context c) throws IOException {
        return download(c, "Monolith.apk");
    }

    /** Downloads any APK published with the latest build (the app or an icon pack). */
    static File download(Context c, String asset) throws IOException {
        File out = new File(c.getCacheDir(), asset);
        HttpURLConnection conn = open(BASE + asset);
        try {
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            try (InputStream in = conn.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
        } finally {
            conn.disconnect();
        }
        return out;
    }

    /** Hands the APK to Android's installer; the result comes back to the activity as a new intent. */
    static void install(Activity a, File apk) throws IOException {
        install(a, apk, a.getPackageName());
    }

    static void install(Activity a, File apk, String pkg) throws IOException {
        PackageInstaller installer = a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("monolith", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            // Report back to the screen that started the install (Monolith home or Dock bar).
            Intent status = new Intent(a, a.getClass()).setAction(ACTION_INSTALL_STATUS);
            PendingIntent pi = PendingIntent.getActivity(a, 42, status,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pi.getIntentSender());
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "Monolith-Updater");
        return conn;
    }
}
