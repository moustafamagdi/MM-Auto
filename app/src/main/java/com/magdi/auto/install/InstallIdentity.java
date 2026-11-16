package com.magdi.auto.install;

import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;

/** What Android reports about who installed a package. Android Auto cares about "installed by". */
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

    public String describe() {
        if (!installed) return "not installed";
        return "installed by " + show(installing) + ", requested by " + show(initiating)
                + ", originating " + show(originating);
    }

    private static String show(String value) {
        return value == null ? "none" : value;
    }
}
