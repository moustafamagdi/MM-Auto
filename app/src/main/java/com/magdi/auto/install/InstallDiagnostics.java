package com.magdi.auto.install;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Map;

/** A text report of how this device sees the MM apps, for troubleshooting and sharing. */
public final class InstallDiagnostics {
    private InstallDiagnostics() {}

    /** @param apps display title to package name */
    public static String report(Context context, Map<String, String> apps) {
        PackageManager pm = context.getPackageManager();
        StringBuilder text = new StringBuilder("MM Auto diagnostics\n");
        text.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        text.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
                .append(")\n");
        text.append("Build: ").append(Build.DISPLAY).append('\n');
        text.append("Security patch: ").append(Build.VERSION.SECURITY_PATCH).append('\n');
        try {
            text.append("MM Auto: ").append(pm.getPackageInfo(context.getPackageName(), 0).versionName).append('\n');
            text.append("Android Auto: ").append(pm.getPackageInfo("com.google.android.projection.gearhead", 0).versionName).append('\n');
        } catch (PackageManager.NameNotFoundException missing) { }
        text.append("Install method: ").append(InstallManager.mode(context)).append('\n');
        text.append("Shizuku: ").append(ShizukuChannel.running() ? "running" : "not running")
                .append(", API ").append(ShizukuChannel.version())
                .append(", permission ").append(ShizukuChannel.granted() ? "granted" : "not granted").append('\n');
        text.append("Can request installs: ").append(pm.canRequestPackageInstalls()).append("\n\n");
        for (Map.Entry<String, String> app : apps.entrySet()) {
            text.append(app.getKey()).append(" (").append(app.getValue()).append(")\n");
            try {
                PackageInfo info = pm.getPackageInfo(app.getValue(), 0);
                text.append("  version ").append(info.versionName).append(" (").append(info.getLongVersionCode())
                        .append(")\n");
            } catch (PackageManager.NameNotFoundException missing) {
                text.append("  not installed\n");
                continue;
            }
            InstallIdentity identity = InstallIdentity.read(pm, app.getValue());
            text.append("  ").append(identity.describe()).append('\n');
            text.append("  Install metadata pair: ").append(identity.matchesExpectedPair(pm) ? "MATCH" : "MISMATCH").append('\n');
            text.append("  Last attempt: ").append(InstallManager.lastAttempt(context, app.getValue())).append('\n');
            text.append("  Android Auto visibility/playback: NOT_TESTED\n");
        }
        return text.toString();
    }
}
