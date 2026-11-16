package com.magdi.auto;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Repairs use an exact copy of the installed APK, not an unverified cache or network download. */
final class RepairApk {
    // Kept equal to Releases.SIGNER by a regression test; never trust an arbitrary installed signer.
    static final String SIGNER = "6d9d30d8f427a6cfc0241831d1e324e684fe2aefda72925c89ef872bf3a5ab65";
    static final long MAX_BYTES = 100 * 1024 * 1024;

    private RepairApk() {}

    static File prepare(Context context, Releases.App app) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo installed = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        requireIdentity(app.packageName, installed, installed.getLongVersionCode());
        if (installed.applicationInfo == null || (installed.applicationInfo.splitSourceDirs != null
                && installed.applicationInfo.splitSourceDirs.length != 0)) {
            throw new IOException("Repair does not support split APK installations.");
        }
        File source = new File(installed.applicationInfo.sourceDir);
        File copy = File.createTempFile("repair-", ".apk", context.getCacheDir());
        try {
            copyVerified(source, copy);
            rejectPrivateAsset(copy);
            PackageInfo archive = pm.getPackageArchiveInfo(copy.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            requireIdentity(app.packageName, archive, installed.getLongVersionCode());
            PackageInfo current = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            requireIdentity(app.packageName, current, installed.getLongVersionCode());
            if (current.lastUpdateTime != installed.lastUpdateTime
                    || !current.applicationInfo.sourceDir.equals(source.getAbsolutePath())) {
                throw new IOException("The app changed during verification. Try repair again.");
            }
            return copy;
        } catch (Exception failure) {
            copy.delete();
            throw failure;
        }
    }

    private static void requireIdentity(String expected, PackageInfo info, long version) throws Exception {
        if (info == null) throw new IOException("Could not read the installed APK.");
        Signature[] signers = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        String fingerprint = signers != null && signers.length == 1
                ? hex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())) : "";
        checkIdentity(expected, info.packageName, version, info.getLongVersionCode(), fingerprint);
    }

    static void checkIdentity(String expected, String actual, long expectedVersion, long actualVersion,
                              String fingerprint) throws IOException {
        if (!expected.equals(actual) || !SIGNER.equals(fingerprint)) {
            throw new IOException("APK package or pinned signing certificate is incorrect.");
        }
        if (expectedVersion != actualVersion) throw new IOException("Repair requires the same installed version.");
    }

    static void copyVerified(File source, File target) throws Exception {
        long expected = source.length();
        if (expected <= 0 || expected > MAX_BYTES) throw new IOException("Invalid installed APK size.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                size += read;
                if (size > expected) throw new IOException("Installed APK changed while copying.");
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        if (size != expected || target.length() != expected
                || !Arrays.equals(digest.digest(), checksum(target))) {
            throw new IOException("Copied APK size or checksum does not match the installed APK.");
        }
    }

    private static byte[] checksum(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    static void rejectPrivateAsset(File apk) throws IOException {
        try (ZipFile zip = new ZipFile(apk)) {
            if (zip.getEntry("assets/initial-account.json") != null) {
                throw new IOException("The APK contains a private account asset.");
            }
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
