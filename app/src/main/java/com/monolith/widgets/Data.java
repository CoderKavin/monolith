package com.monolith.widgets;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Everything the widgets display that isn't the clock. */
final class Data {

    private Data() {}

    // ------------------------------------------------------------------ battery

    static final int B_NORMAL = 0, B_LOW = 1, B_CHARGING = 2, B_FULL = 3, B_SAVER = 4;

    static final class Battery {
        int level;
        int state;
    }

    static Battery battery(Context c) {
        Context app = c.getApplicationContext();
        Battery b = new Battery();
        Intent i = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1, scale = 100, status = -1, plugged = 0;
        if (i != null) {
            level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }
        if (level < 0) {
            BatteryManager bm = app.getSystemService(BatteryManager.class);
            level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : 0;
            scale = 100;
        }
        b.level = Math.max(0, Math.min(100, Math.round(level * 100f / Math.max(1, scale))));

        PowerManager pm = app.getSystemService(PowerManager.class);
        boolean saver = pm != null && pm.isPowerSaveMode();
        boolean isPlugged = plugged != 0;

        if (isPlugged && (status == BatteryManager.BATTERY_STATUS_FULL || b.level >= 100)) b.state = B_FULL;
        else if (status == BatteryManager.BATTERY_STATUS_CHARGING) b.state = B_CHARGING;
        else if (saver) b.state = B_SAVER;
        else if (b.level <= 20) b.state = B_LOW;
        else b.state = B_NORMAL;
        return b;
    }

    /** True once the user has run the one-time adb grant. */
    static boolean canToggleSaver(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static void toggleSaver(Context c) {
        if (!canToggleSaver(c)) return;
        PowerManager pm = c.getSystemService(PowerManager.class);
        boolean on = pm != null && pm.isPowerSaveMode();
        try {
            Settings.Global.putInt(c.getContentResolver(), "low_power", on ? 0 : 1);
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the write.
        }
    }

    // ------------------------------------------------------------------ calendar

    static String nextAlarm(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo info = am != null ? am.getNextAlarmClock() : null;
        if (info == null) return null;
        String pattern = DateFormat.is24HourFormat(c) ? "H:mm" : "h:mm";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(info.getTriggerTime()));
    }

    /** 2:05 in 12-hour mode, 14:05 in 24-hour mode, following the phone's setting. */
    static String time(Context c) {
        String pattern = DateFormat.is24HourFormat(c) ? "H:mm" : "h:mm";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
    }

    /** Sunday, 8 November */
    static String longDate() {
        return new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date());
    }

    static int isoWeek() {
        Calendar iso = Calendar.getInstance();
        iso.setFirstDayOfWeek(Calendar.MONDAY);
        iso.setMinimalDaysInFirstWeek(4);
        iso.setTimeInMillis(System.currentTimeMillis());
        return iso.get(Calendar.WEEK_OF_YEAR);
    }

    static String monthName(Calendar cal) {
        return new SimpleDateFormat("LLLL", Locale.getDefault()).format(cal.getTime());
    }

    // ------------------------------------------------------------------ screen time

    static final class Screen {
        boolean allowed;
        long today;
        long yesterdaySameTime;
        final String[] names = new String[3];
        final long[] times = new long[3];
        /** Top three apps, then everything else. */
        final long[] segments = new long[4];
    }

    private static Screen cache;
    private static long cacheAt;

    static boolean usageAllowed(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return c.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static synchronized Screen screen(Context c) {
        long now = System.currentTimeMillis();
        if (cache != null && now - cacheAt < 120_000L) return cache;

        Screen s = new Screen();
        s.allowed = usageAllowed(c);
        if (!s.allowed) return s;

        UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
        if (usm == null) return s;
        Set<String> ignore = ignored(c);

        long todayStart = startOfDay(now, 0);
        Map<String, Long> today = usage(usm, todayStart, now, ignore);
        s.today = sum(today);

        long yStart = startOfDay(now, -1);
        s.yesterdaySameTime = sum(usage(usm, yStart, yStart + (now - todayStart), ignore));

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(today.entrySet());
        Collections.sort(ranked, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        long top = 0;
        for (int i = 0; i < 3 && i < ranked.size(); i++) {
            long t = ranked.get(i).getValue();
            if (t < 60_000L) break;
            s.names[i] = label(c, ranked.get(i).getKey());
            s.times[i] = t;
            s.segments[i] = t;
            top += t;
        }
        s.segments[3] = Math.max(0, s.today - top);

        cache = s;
        cacheAt = now;
        return s;
    }

    /** Foreground time per package between start and end, from the system's activity events. */
    private static Map<String, Long> usage(UsageStatsManager usm, long start, long end, Set<String> ignore) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, Long> open = new HashMap<>();
        Set<String> seen = new HashSet<>();
        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            String pkg = e.getPackageName();
            if (pkg == null) continue;
            String key = pkg + "/" + e.getClassName();
            long ts = e.getTimeStamp();
            int type = e.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (!open.containsKey(key)) open.put(key, ts);
                seen.add(key);
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                Long began = open.remove(key);
                if (began != null) {
                    add(totals, pkg, ts - began);
                } else if (!seen.contains(key) && type == UsageEvents.Event.ACTIVITY_PAUSED) {
                    // Was already open when the window started.
                    add(totals, pkg, ts - start);
                }
                seen.add(key);
            } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                for (Map.Entry<String, Long> o : open.entrySet()) {
                    String k = o.getKey();
                    add(totals, k.substring(0, k.indexOf('/')), ts - o.getValue());
                }
                open.clear();
            }
        }
        for (Map.Entry<String, Long> o : open.entrySet()) {
            String k = o.getKey();
            add(totals, k.substring(0, k.indexOf('/')), end - o.getValue());
        }
        for (String p : ignore) totals.remove(p);
        return totals;
    }

    private static void add(Map<String, Long> m, String pkg, long ms) {
        if (ms <= 0) return;
        Long cur = m.get(pkg);
        m.put(pkg, (cur == null ? 0L : cur) + ms);
    }

    private static long sum(Map<String, Long> m) {
        long t = 0;
        for (long v : m.values()) t += v;
        return t;
    }

    /** Launchers, the system UI and this app don't count as screen time. */
    private static Set<String> ignored(Context c) {
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("com.android.systemui");
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(home, 0)) {
            if (r.activityInfo != null) s.add(r.activityInfo.packageName);
        }
        return s;
    }

    private static String label(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            int dot = pkg.lastIndexOf('.');
            return dot >= 0 ? pkg.substring(dot + 1) : pkg;
        }
    }

    static long startOfDay(long now, int offsetDays) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.add(Calendar.DAY_OF_YEAR, offsetDays);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 1h 38m, or 38m under an hour. */
    static String fmt(long ms) {
        long m = Math.max(0, ms) / 60_000L;
        long h = m / 60;
        return h > 0 ? h + "h " + (m % 60) + "m" : m + "m";
    }
}
