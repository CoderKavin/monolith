package com.monolith.widgets;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/**
 * Keeps Monolith running so the battery listener in {@link Monolith} hears plug, unplug, level and
 * power-saving changes the moment they happen. One UI otherwise kills the app in the background
 * and the Battery widget only catches up on the next minute tick. The notification is silent and
 * minimised; turning off Monolith's notifications hides it and the service keeps working.
 */
public class LiveService extends Service {

    private static final String CHANNEL = "live";

    static void start(Context c) {
        if (!Refresh.anyWidgets(c)) return;
        try {
            c.startForegroundService(new Intent(c, LiveService.class));
        } catch (RuntimeException ignored) {
            // Not allowed from here right now (background start limits); the next app open or reboot starts it.
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Live widgets", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("Keeps the Battery widget updating instantly. Safe to turn off; the widgets keep working.");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("Widgets are live")
                .setContentText("Battery updates the moment it changes")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        try {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Refresh.refreshAll(this);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
