package com.magdi.auto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/** Separate from normal installs so Classic repair never silently invokes a privileged fallback. */
final class RepairState {
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("repair_flow", Context.MODE_PRIVATE);
    }

    static void mark(Context context, String pkg) throws Exception {
        long before = context.getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime;
        if (!prefs(context).edit().putLong(pkg + ".before", before)
                .putLong(pkg + ".started", System.currentTimeMillis()).commit()) {
            throw new java.io.IOException("Could not save pending repair.");
        }
        com.magdi.auto.install.InstallManager.recordAttempt(context, pkg, com.magdi.auto.install.InstallMode.CLASSIC, "Classic repair pending; no completion observed yet.");
        SupportEvents.event("repair",pkg,"pending","CLASSIC");
    }

    static void clear(Context context, String pkg) {
        prefs(context).edit().remove(pkg + ".before").remove(pkg + ".started").apply();
    }

    static boolean takeFinished(Context context, String pkg) {
        SharedPreferences prefs = prefs(context);
        long started = prefs.getLong(pkg + ".started", 0);
        if (started == 0) return false;
        long after = 0;
        try { after = context.getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime; }
        catch (PackageManager.NameNotFoundException missing) { }
        long elapsed = System.currentTimeMillis() - started;
        boolean done = finished(prefs.getLong(pkg + ".before", 0), after, elapsed);
        if (done || elapsed < 0 || elapsed > 30 * 60 * 1000L) clear(context, pkg);
        return done;
    }

    static boolean finished(long before, long after, long elapsed) {
        return after > 0 && after != before && elapsed >= 0 && elapsed <= 30 * 60 * 1000L;
    }

    static boolean needsShizuku(Context context, String pkg) {
        return prefs(context).getBoolean(pkg + ".shizuku", false);
    }

    static void needsShizuku(Context context, String pkg, boolean needed) {
        prefs(context).edit().putBoolean(pkg + ".shizuku", needed).apply();
    }
}
