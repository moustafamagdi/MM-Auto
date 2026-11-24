package com.magdi.auto.install;

import java.util.ArrayList;
import java.util.List;
import java.net.URI;

/** Builds the package manager commands that are run by the Shizuku service. */
final class PackageTool {

    private PackageTool() {}

    /** Launches Android's confirmation UI as shell. No pm install or silent fallback is used. */
    static List<String> openInstallerCommand(String uri, String component, int userId) {
        checkApkUri(uri);
        if (component == null || !component.matches("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z_.][A-Za-z0-9_.$]*"))
            throw new IllegalArgumentException("Invalid installer component.");
        if (userId < 0 || userId > 21474) throw new IllegalArgumentException("Invalid Android user.");
        List<String> command = new ArrayList<>();
        java.util.Collections.addAll(command, "am", "start", "--user", Integer.toString(userId),
                "-n", component, "-a", "android.intent.action.INSTALL_PACKAGE",
                "--grant-read-uri-permission", "-d", uri, "-t", "application/vnd.android.package-archive",
                "--es", "android.intent.extra.INSTALLER_PACKAGE_NAME", InstallIdentity.PLAY_STORE,
                "--ez", "android.intent.extra.NOT_UNKNOWN_SOURCE", "true",
                "--es", "android.intent.extra.REFERRER_NAME", "android-app://" + InstallIdentity.PLAY_STORE,
                "--ei", "android.content.pm.extra.INSTALL_REASON", "4");
        return command;
    }

    static void checkApkUri(String uri) {
        try {
            URI parsed = URI.create(uri);
            String authority = parsed.getAuthority();
            String path = parsed.getPath();
            if (!"content".equals(parsed.getScheme())
                    || !("com.magdi.auto.downloads".equals(authority) || "com.magdi.auto.debug.downloads".equals(authority))
                    || parsed.getRawQuery() != null || parsed.getRawFragment() != null
                    || path == null || !path.matches("/(?:repair/)?com\\.magdi\\.(?:auto|iptv|tube)\\.apk")
                    || !parsed.getRawPath().equals(path)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Only MM Auto's verified APK URIs are supported.");
        }
    }

    static ShellRunner.Output parse(String raw) {
        if (raw == null) return new ShellRunner.Output(-1, "No answer from the Shizuku service.");
        int newline = raw.indexOf('\n');
        String head = newline < 0 ? raw : raw.substring(0, newline);
        try {
            return new ShellRunner.Output(Integer.parseInt(head.trim()), newline < 0 ? "" : raw.substring(newline + 1));
        } catch (NumberFormatException invalid) {
            return new ShellRunner.Output(-1, raw);
        }
    }

}
