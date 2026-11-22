package com.magdi.auto.install;

import android.content.ClipData;
import com.magdi.auto.SupportEvents;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;

/** APKs must pass the existing package, pinned-certificate and version verifier before dispatch. */
public final class InstallManager {
    private static final String PREFS = "install";
    private static final String MODE = "mode";
    private static final String PENDING = "pending_";
    private static final long PENDING_TTL_MS = 30 * 60 * 1000L;
    private static final String APK_MIME = "application/vnd.android.package-archive";

    public interface Status { void update(String message); }

    public static final class Outcome {
        public final boolean ok;
        public final boolean pending;
        public final String message;
        Outcome(boolean ok, String message) { this(ok, false, message); }
        Outcome(boolean ok, boolean pending, String message) {
            this.ok = ok; this.pending = pending; this.message = message;
        }
    }

    public enum PendingStatus { NONE, WAITING, FINISHED, EXPIRED }

    public static final class PendingResult {
        public final PendingStatus status;
        public final boolean repair;
        PendingResult(PendingStatus status, boolean repair) { this.status = status; this.repair = repair; }
    }

    interface InstallerLauncher { ShellRunner.Output open(Context context, Intent intent) throws Exception; }

    private InstallManager() {}

    public static InstallMode mode(Context context) {
        try { return InstallMode.valueOf(prefs(context).getString(MODE, InstallMode.CLASSIC.name())); }
        catch (IllegalArgumentException unknown) { return InstallMode.CLASSIC; }
    }

    public static void setMode(Context context, InstallMode mode) {
        prefs(context).edit().putString(MODE, mode.name()).apply();
    }

    /** Classic remains the existing activity-owned installation dialog. */
    @SuppressWarnings("deprecation")
    public static Intent classicIntent(Uri apk, String label) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setDataAndType(apk, APK_MIME);
        intent.setClipData(ClipData.newRawUri(label, apk));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, InstallIdentity.PLAY_STORE);
        return intent;
    }

    /** King-style Shizuku path: shell opens the real system installer; the user confirms installation. */
    public static Outcome openShizuku(Context context, Uri apk, String packageName, boolean repair) {
        SupportEvents.event("install",packageName,"started","SHIZUKU");
        if (!ShizukuChannel.granted()) {SupportEvents.event("permission",packageName,"unavailable","SHIZUKU");return fail("Start Shizuku and allow MM Auto, then try again.");}
        return openSystemInstaller(context, apk, packageName, repair, ShizukuChannel::openInstaller);
    }

    static Outcome openSystemInstaller(Context context, Uri apk, String packageName, boolean repair,
                                       InstallerLauncher launcher) {
        try {
            PackageTool.checkApkUri(apk.toString());
            if (!(packageName + ".apk").equals(apk.getLastPathSegment())
                    || (apk.getPathSegments().size() == 2) != repair)
                return fail("The verified APK URI does not match this installation request.");
            SystemInstaller target = SystemInstaller.resolve(context.getPackageManager(), apk);
            target.grant(context, apk);
            markPending(context, packageName, apk, repair);
            ShellRunner.Output out = launcher.open(context, target.intent);
            if (!out.activityStarted()) {
                SupportEvents.event("install",packageName,"failed","SHIZUKU");
                clearPending(context, packageName);
                return fail("Could not open the system installer through Shizuku. " + explain(out.text));
            }
            String message = "Android installer opened through Shizuku. Confirm installation, then return to MM Auto. "
                    + "Installed by and Requested by have not been verified yet.";
            prefs(context).edit().putString("last_" + packageName,
                    "Shizuku UserService -> " + target.intent.getComponent().flattenToShortString()
                    + "; awaiting package change").apply();
            SupportEvents.event("install",packageName,"pending","SHIZUKU");
            return new Outcome(false, true, message);
        } catch (Exception failure) {
            SupportEvents.event("install",packageName,"failed","SHIZUKU");
            clearPending(context, packageName);
            // Also revoke if a failure occurred before markPending could save the URI.
            try { context.revokeUriPermission(apk, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (RuntimeException ignored) { }
            return fail("Could not open the system installer through Shizuku. " + detail(failure));
        }
    }

    /** The legacy silent route is now Root-only. Shizuku can never fall back to pm install. */
    public static Outcome installPrivileged(Context context, InstallMode mode, File apk,
                                            String packageName, Status status) {
        SupportEvents.event("install",packageName,"started",mode.name());
        if (mode != InstallMode.ROOT) return fail("Use the Android installer for Classic or Shizuku.");
        try (InputStream input = new FileInputStream(apk)) {
            status.update("Installing as root…");
            ShellRunner.Output out = ShellRunner.run(PackageTool.asRoot(
                    PackageTool.installCommand(apk.length(), InstallIdentity.PLAY_STORE)), input, 180);
            if (!out.installed()) {SupportEvents.event("install",packageName,"failed",mode.name());return fail(explain(out.text));}
            return verifyAndFix(context, mode, packageName, status, "Installed. ");
        } catch (Exception failure) { SupportEvents.event("install",packageName,"failed",mode.name());return fail("Install failed. " + detail(failure)); }
    }

    /** Read-only for Classic/Shizuku. Root retains its explicit privileged repair behavior. */
    public static Outcome verifyAndFix(Context context, InstallMode mode, String packageName,
                                       Status status, String prefix) {
        PackageManager pm = context.getPackageManager();
        InstallIdentity identity = InstallIdentity.read(pm, packageName);
        if (!identity.installed) return fail(prefix + "The app is not installed.");
        if (identity.matchesExpectedPair(pm)) return checked(context, packageName, identity, prefix);
        if (mode == InstallMode.ROOT) {
            try {
                status.update("Checking the Root install source…");
                ShellRunner.run(PackageTool.asRoot(PackageTool.setInstallerCommand(
                        packageName, InstallIdentity.PLAY_STORE)), null, 30);
                identity = InstallIdentity.read(pm, packageName);
                if (!identity.matchesExpectedPair(pm)) {
                    status.update("Reinstalling the verified cached APK as Root…");
                    reinstallRoot(context, packageName);
                    identity = InstallIdentity.read(pm, packageName);
                }
            } catch (Exception ignored) {
                // Report the actual pair, never turn command success into an attribution match.
            }
        }
        return checked(context, packageName, identity, prefix);
    }

    private static Outcome checked(Context context, String packageName, InstallIdentity identity, String prefix) {
        boolean match = identity.matchesExpectedPair(context.getPackageManager());
        SupportEvents.event("install-check",packageName,match?"match":"mismatch",mode(context).name());
        String message = prefix + "Install metadata: " + (match ? "MATCH" : "MISMATCH") + ".\n"
                + identity.describe() + "\nAndroid Auto visibility/playback: NOT_TESTED.";
        prefs(context).edit().putString("last_" + packageName, message).apply();
        return new Outcome(match, message);
    }

    private static void reinstallRoot(Context context, String packageName) throws Exception {
        File apk = new File(new File(context.getCacheDir(), "releases"), packageName + ".apk");
        if (!apk.isFile()) return;
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo current = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null || !packageName.equals(archive.packageName)
                || archive.getLongVersionCode() != current.getLongVersionCode() || !sameSigners(archive, current)) return;
        try (InputStream input = new FileInputStream(apk)) {
            ShellRunner.run(PackageTool.asRoot(PackageTool.installCommand(
                    apk.length(), InstallIdentity.PLAY_STORE)), input, 180);
        }
    }

    private static boolean sameSigners(PackageInfo first, PackageInfo second) {
        if (first.signingInfo == null || second.signingInfo == null) return false;
        Signature[] a = first.signingInfo.getApkContentsSigners();
        Signature[] b = second.signingInfo.getApkContentsSigners();
        return a != null && b != null && a.length > 0
                && new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)));
    }

    public static void markPending(Context context, String packageName) {
        markPending(context, packageName, null, false);
    }

    public static void markPending(Context context, String packageName, Uri uri, boolean repair) {
        SupportEvents.event(repair?"repair":"install",packageName,"pending",mode(context).name());
        long before = lastUpdate(context.getPackageManager(), packageName);
        SharedPreferences.Editor edit = prefs(context).edit()
                .putString(PENDING + packageName, before + "|" + System.currentTimeMillis())
                .putBoolean("repair_" + packageName, repair);
        if (uri == null) edit.remove("uri_" + packageName); else edit.putString("uri_" + packageName, uri.toString());
        if (!edit.commit()) throw new IllegalStateException("Could not save the pending installation.");
    }

    public static void clearPending(Context context, String packageName) {
        String uri = prefs(context).getString("uri_" + packageName, null);
        prefs(context).edit().remove(PENDING + packageName).remove("uri_" + packageName)
                .remove("repair_" + packageName).apply();
        if (uri != null) {
            try { context.revokeUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (RuntimeException ignored) { }
        }
    }

    /** Resume alone is not success: require a real install/update time change, including same-version repair. */
    public static PendingResult pollPending(Context context, String packageName) {
        SharedPreferences prefs = prefs(context);
        String value = prefs.getString(PENDING + packageName, null);
        boolean repair = prefs.getBoolean("repair_" + packageName, false);
        if (value == null) return new PendingResult(PendingStatus.NONE, false);
        PendingStatus result;
        try {
            String[] parts = value.split("\\|");
            long before = Long.parseLong(parts[0]);
            long started = Long.parseLong(parts[1]);
            result = pendingStatus(before, lastUpdate(context.getPackageManager(), packageName),
                    System.currentTimeMillis() - started);
        } catch (RuntimeException invalid) { result = PendingStatus.EXPIRED; }
        if (result == PendingStatus.FINISHED || result == PendingStatus.EXPIRED) {SupportEvents.event("install",packageName,result==PendingStatus.FINISHED?"finished":"expired",mode(context).name());clearPending(context, packageName);}
        return new PendingResult(result, repair);
    }

    public static boolean hasPending(Context context) {
        for (String key : prefs(context).getAll().keySet()) if (key.startsWith(PENDING)) return true;
        return false;
    }

    static PendingStatus pendingStatus(long before, long after, long elapsed) {
        if (elapsed < 0 || elapsed > PENDING_TTL_MS) return PendingStatus.EXPIRED;
        return after > 0 && after != before ? PendingStatus.FINISHED : PendingStatus.WAITING;
    }

    public static String lastAttempt(Context context, String packageName) {
        return prefs(context).getString("last_" + packageName, "No verified result recorded.");
    }

    private static long lastUpdate(PackageManager pm, String packageName) {
        try { return pm.getPackageInfo(packageName, 0).lastUpdateTime; }
        catch (PackageManager.NameNotFoundException missing) { return 0; }
    }

    private static String explain(String text) {
        if (text.contains("SecurityException") || text.contains("Permission Denial"))
            return "Android refused this operation. Check Shizuku permission and the device installation settings.";
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }

    private static String detail(Exception failure) {
        return failure.getMessage() == null ? "Try again." : explain(failure.getMessage());
    }

    private static Outcome fail(String message) { return new Outcome(false, message); }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
