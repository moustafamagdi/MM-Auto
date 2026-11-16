package com.magdi.auto.install;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * One install path for every MM app: Classic (Android installer), Shizuku, or Root, each aiming for
 * "installed by Google Play Store" because Android Auto only lists apps with that install source.
 * Callers must verify the APK (package, signer, checksum) before handing it to this class.
 */
public final class InstallManager {
    private static final String PREFS = "install";
    private static final String MODE = "mode";
    private static final String PENDING = "pending_";
    private static final long PENDING_TTL_MS = 30 * 60 * 1000L;
    private static final String APK_MIME = "application/vnd.android.package-archive";

    public interface Status {
        void update(String message);
    }

    public static final class Outcome {
        public final boolean ok;
        public final String message;

        Outcome(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private InstallManager() {}

    public static InstallMode mode(Context context) {
        try {
            return InstallMode.valueOf(prefs(context).getString(MODE, InstallMode.CLASSIC.name()));
        } catch (IllegalArgumentException unknown) {
            return InstallMode.CLASSIC;
        }
    }

    public static void setMode(Context context, InstallMode mode) {
        prefs(context).edit().putString(MODE, mode.name()).apply();
    }

    /** Classic method: Android's installer dialog, asked to record the Play Store as installer. */
    @SuppressWarnings("deprecation")
    public static Intent classicIntent(Uri apk, String label) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setDataAndType(apk, APK_MIME);
        intent.setClipData(ClipData.newRawUri(label, apk));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, InstallIdentity.PLAY_STORE);
        return intent;
    }

    /** Silent install for the Shizuku and Root modes. Blocks, so call it from a background thread. */
    public static Outcome installPrivileged(Context context, InstallMode mode, File apk,
                                            String packageName, Status status) {
        try {
            ShellRunner.Output out;
            if (mode == InstallMode.SHIZUKU) {
                if (!ShizukuChannel.granted()) {
                    return fail("Shizuku is not running or MM Auto does not have its permission.");
                }
                status.update("Installing through Shizuku…");
                out = ShizukuChannel.install(context, apk);
            } else if (mode == InstallMode.ROOT) {
                status.update("Installing as root…");
                try (InputStream input = new FileInputStream(apk)) {
                    out = ShellRunner.run(PackageTool.asRoot(
                            PackageTool.installCommand(apk.length(), InstallIdentity.PLAY_STORE)), input, 180);
                }
            } else {
                return fail("Classic installs use the Android installer.");
            }
            if (!out.installed()) return fail(explain(out.text));
            return verifyAndFix(context, mode, packageName, status, "Installed. ");
        } catch (Exception failure) {
            return fail("Install failed. " + (failure.getMessage() == null ? "Try again." : failure.getMessage()));
        }
    }

    /**
     * Checks who Android says installed the package and, if it is not the Play Store, repairs it when a
     * privileged channel is available. Blocks, so call it from a background thread.
     */
    public static Outcome verifyAndFix(Context context, InstallMode mode, String packageName,
                                       Status status, String prefix) {
        PackageManager pm = context.getPackageManager();
        InstallIdentity identity = InstallIdentity.read(pm, packageName);
        if (!identity.installed) return fail(prefix + "The app is not installed.");
        if (identity.viaPlayStore()) return new Outcome(true, prefix + "Install source: Google Play Store.");

        status.update("Fixing the install source…");
        String problem = setInstaller(context, mode, packageName);
        identity = InstallIdentity.read(pm, packageName);
        if (identity.viaPlayStore()) return new Outcome(true, prefix + "Install source fixed to Google Play Store.");
        String shown = identity.installing == null ? "unknown" : identity.installing;
        return fail(prefix + "Install source is " + shown + ". "
                + (problem == null ? "Android Auto may not list this app." : problem));
    }

    /** Returns null on success, otherwise a short reason. */
    private static String setInstaller(Context context, InstallMode mode, String packageName) {
        try {
            ShellRunner.Output out;
            if (mode == InstallMode.ROOT) {
                out = ShellRunner.run(PackageTool.asRoot(
                        PackageTool.setInstallerCommand(packageName, InstallIdentity.PLAY_STORE)), null, 30);
            } else if (ShizukuChannel.granted()) {
                out = ShizukuChannel.setInstaller(context, packageName);
            } else {
                return "Start Shizuku or choose Root so MM Auto can repair the source.";
            }
            return out.ok() ? null : explain(out.text);
        } catch (Exception failure) {
            return failure.getMessage() == null ? "Could not repair the install source." : failure.getMessage();
        }
    }

    // --- Classic installs finish in Android's UI, so the check happens when MM Auto is shown again. ---

    public static void markPending(Context context, String packageName) {
        long before = lastUpdate(context.getPackageManager(), packageName);
        prefs(context).edit().putString(PENDING + packageName, before + "|" + System.currentTimeMillis()).apply();
    }

    /** True once, when a package marked pending has been installed or updated since it was marked. */
    public static boolean takeFinishedPending(Context context, String packageName) {
        SharedPreferences prefs = prefs(context);
        String value = prefs.getString(PENDING + packageName, null);
        if (value == null) return false;
        String[] parts = value.split("\\|");
        try {
            long before = Long.parseLong(parts[0]);
            long started = Long.parseLong(parts[1]);
            boolean expired = System.currentTimeMillis() - started > PENDING_TTL_MS;
            boolean changed = lastUpdate(context.getPackageManager(), packageName) != before;
            if (changed || expired) prefs.edit().remove(PENDING + packageName).apply();
            return changed && !expired;
        } catch (RuntimeException invalid) {
            prefs.edit().remove(PENDING + packageName).apply();
            return false;
        }
    }

    private static long lastUpdate(PackageManager pm, String packageName) {
        try {
            return pm.getPackageInfo(packageName, 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException missing) {
            return 0;
        }
    }

    private static String explain(String text) {
        if (text.contains("SecurityException") || text.contains("same cert")) {
            return "This device does not let a non-root shell set the Play Store as installer. Choose Root.";
        }
        String line = text.isEmpty() ? "No details were returned." : text;
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }

    private static Outcome fail(String message) {
        return new Outcome(false, message);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
