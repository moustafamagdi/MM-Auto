package com.magdi.auto.install;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves the device's real system installer, never a third-party APK handler. */
final class SystemInstaller {
    static final String MIME = "application/vnd.android.package-archive";
    static final String SHELL = "com.android.shell";

    final Intent intent;
    final Set<String> readers;

    private SystemInstaller(Intent intent, Set<String> readers) {
        this.intent = intent;
        this.readers = readers;
    }

    @SuppressWarnings("deprecation")
    static SystemInstaller resolve(PackageManager pm, Uri uri) {
        PackageTool.checkApkUri(uri.toString());
        Intent probe = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, MIME);
        List<ResolveInfo> handlers = pm.queryIntentActivities(probe, 0);
        Set<String> readers = new LinkedHashSet<>();
        ActivityInfo selected = null;
        ResolveInfo preferred = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo handler : handlers) {
            ActivityInfo info = handler.activityInfo;
            if (!trusted(info)) continue;
            readers.add(info.packageName);
            if (selected == null || sameActivity(info, preferred == null ? null : preferred.activityInfo))
                selected = info;
        }
        if (selected == null) throw new IllegalStateException("No system APK installer is available.");
        readers.add(SHELL);
        Intent intent = InstallManager.classicIntent(uri, "MM APK");
        intent.setComponent(new ComponentName(selected.packageName, selected.name));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        intent.putExtra(Intent.EXTRA_REFERRER_NAME, "android-app://" + InstallIdentity.PLAY_STORE);
        intent.putExtra("android.content.pm.extra.INSTALL_REASON", PackageManager.INSTALL_REASON_USER);
        return new SystemInstaller(intent, readers);
    }

    static boolean isSystemInstaller(PackageManager pm, String packageName) {
        if (packageName == null || SHELL.equals(packageName)) return false;
        Uri probe = Uri.parse("content://com.magdi.auto.downloads/com.magdi.auto.apk");
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(probe, MIME);
        try {
            for (ResolveInfo handler : pm.queryIntentActivities(intent, 0)) {
                if (trusted(handler.activityInfo) && packageName.equals(handler.activityInfo.packageName)) return true;
            }
        } catch (RuntimeException unavailable) {
            // An unverified initiator must not be reported as a match.
        }
        return false;
    }

    private static boolean trusted(ActivityInfo info) {
        return info != null && info.enabled && info.exported && info.applicationInfo != null
                && info.applicationInfo.enabled && !SHELL.equals(info.packageName)
                && (info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    private static boolean sameActivity(ActivityInfo first, ActivityInfo second) {
        return second != null && first.packageName.equals(second.packageName) && first.name.equals(second.name);
    }

    void grant(Context context, Uri uri) {
        try {
            for (String reader : readers) context.grantUriPermission(reader, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException failure) {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            throw failure;
        }
    }
}
