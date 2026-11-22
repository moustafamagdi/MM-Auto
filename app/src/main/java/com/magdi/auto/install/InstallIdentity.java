package com.magdi.auto.install;

import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;

/** Android's two install-source fields. A metadata match is not an Android Auto compatibility test. */
public final class InstallIdentity {
    public static final String PLAY_STORE = "com.android.vending";

    public final boolean installed;
    public final String installing;
    public final String initiating;
    public final String originating;

    private InstallIdentity(boolean installed, String installing, String initiating, String originating) {
        this.installed = installed;
        this.installing = installing;
        this.initiating = initiating;
        this.originating = originating;
    }

    public static InstallIdentity read(PackageManager pm, String packageName) {
        try {
            InstallSourceInfo info = pm.getInstallSourceInfo(packageName);
            return new InstallIdentity(true, info.getInstallingPackageName(),
                    info.getInitiatingPackageName(), info.getOriginatingPackageName());
        } catch (PackageManager.NameNotFoundException missing) {
            return new InstallIdentity(false, null, null, null);
        }
    }

    public boolean viaPlayStore() {
        return PLAY_STORE.equals(installing);
    }

    public boolean matchesExpectedPair(PackageManager pm) {
        return installed && matchesExpectedPair(installing, initiating,
                SystemInstaller.isSystemInstaller(pm, initiating));
    }

    static boolean matchesExpectedPair(String installing, String initiating, boolean systemInstaller) {
        return PLAY_STORE.equals(installing) && initiating != null
                && !SystemInstaller.SHELL.equals(initiating)
                && (PLAY_STORE.equals(initiating) || systemInstaller);
    }

    public String describe() {
        if (!installed) return "not installed";
        return "Installed by: " + show(installing) + "\nRequested by: " + show(initiating)
                + "\nOriginating: " + show(originating);
    }

    private static String show(String value) {
        return value == null ? "none" : value;
    }
}
