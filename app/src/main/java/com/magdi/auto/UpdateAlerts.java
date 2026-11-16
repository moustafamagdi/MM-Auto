package com.magdi.auto;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.concurrent.TimeUnit;

final class UpdateAlerts {
    private static final String CHANNEL_ID = "app_updates";
    private static final String ENABLED = "enabled";
    private static final int JOB_ID = 2401;
    private static final int NOTIFICATION_ID = 1;

    static boolean enabled(Context context) {
        return preferences(context).getBoolean(ENABLED, false);
    }

    static boolean setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(ENABLED, enabled).apply();
        if (syncSchedule(context)) return true;
        preferences(context).edit().putBoolean(ENABLED, false).apply();
        return false;
    }

    static boolean syncSchedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (!enabled(context) || !canNotify(context)) {
            scheduler.cancel(JOB_ID);
            if (!enabled(context)) {
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                for (Releases.App app : Releases.App.values()) manager.cancel(app.packageName, NOTIFICATION_ID);
            }
            return true;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return true;

        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateCheckJob.class))
                .setPeriodic(TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(6))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT));
        return manager.areNotificationsEnabled()
                && manager.getNotificationChannel(CHANNEL_ID).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static void notifyIfNew(Context context, Releases.App app, Releases.Release release) {
        if (release == null || !enabled(context) || !canNotify(context)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        PackageInfo installed = Releases.installed(context.getPackageManager(), app);
        if (installed == null || !release.isNewer(installed)) {
            manager.cancel(app.packageName, NOTIFICATION_ID);
            return;
        }

        SharedPreferences settings = preferences(context);
        String key = "notified:" + app.packageName;
        String releasePath = release.url.getPath();
        if (releasePath.equals(settings.getString(key, ""))) return;

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent action = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE);
        String version = "Version " + release.version + (release.preview ? " (preview)" : "") + " is available.";
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update_notification)
                .setContentTitle(app.title + " update available")
                .setContentText(version)
                .setContentIntent(action)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        manager.notify(app.packageName, NOTIFICATION_ID, notification);
        settings.edit().putString(key, releasePath).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("update_alerts", Context.MODE_PRIVATE);
    }

    private UpdateAlerts() {}
}
