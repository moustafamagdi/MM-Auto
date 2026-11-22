package com.magdi.auto.install;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class SystemInstallerCommandTest {
    private static final String APK = "content://com.magdi.auto.downloads/com.magdi.tube.apk";
    @Test public void launchesConfirmationForTheCallingProfileWithoutPmInstall() {
        List<String> command = PackageTool.openInstallerCommand(APK, "com.google.android.packageinstaller/.InstallStart", 10);
        assertEquals("am", command.get(0));
        assertEquals("10", command.get(command.indexOf("--user") + 1));
        assertEquals("com.google.android.packageinstaller/.InstallStart", command.get(command.indexOf("-n") + 1));
        assertEquals(APK, command.get(command.indexOf("-d") + 1));
        assertFalse(command.contains("pm"));
        assertTrue(command.contains("android.intent.extra.NOT_UNKNOWN_SOURCE"));
        assertTrue(command.contains("com.android.vending"));
        assertTrue(command.contains("--grant-read-uri-permission"));
    }
    @Test public void repairAndDebugUrisAreAccepted() {
        PackageTool.checkApkUri("content://com.magdi.auto.downloads/repair/com.magdi.iptv.apk");
        PackageTool.checkApkUri("content://com.magdi.auto.debug.downloads/com.magdi.auto.apk");
    }
    @Test public void rejectsExternalAndAmbiguousUris() {
        String[] bad = {"file:///data/local/tmp/test.apk", "content://other.provider/com.magdi.tube.apk",
                APK + "?x=1", APK + "#x", "content://com.magdi.auto.downloads/../com.magdi.tube.apk",
                "content://com.magdi.auto.downloads/com%2emagdi.tube.apk",
                "content://com.magdi.auto.downloads/other.app.apk"};
        for (String uri : bad) {
            try { PackageTool.checkApkUri(uri); fail(uri); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void rejectsInjectedComponentsAndInvalidProfiles() {
        try { PackageTool.openInstallerCommand(APK, "pkg/.Activity;id", 0); fail(); } catch (IllegalArgumentException expected) { }
        try { PackageTool.openInstallerCommand(APK, "com.android.packageinstaller/.InstallStart", -1); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void amErrorWithZeroExitIsNotReportedAsOpened() {
        assertFalse(new ShellRunner.Output(0, "Starting: Intent {}\nError type 3: Activity does not exist").activityStarted());
        assertFalse(new ShellRunner.Output(0, "Permission Denial").activityStarted());
        assertFalse(new ShellRunner.Output(0, "Success").activityStarted());
        assertFalse(new ShellRunner.Output(1, "Starting: Intent {}").activityStarted());
        assertTrue(new ShellRunner.Output(0, "Starting: Intent { act=android.intent.action.INSTALL_PACKAGE }").activityStarted());
    }
    @Test public void noChangeCancellationAndEarlyResumeRemainPending() {
        assertEquals(InstallManager.PendingStatus.WAITING, InstallManager.pendingStatus(100, 100, 1000));
        assertEquals(InstallManager.PendingStatus.WAITING, InstallManager.pendingStatus(0, 0, 1000));
        assertEquals(InstallManager.PendingStatus.WAITING, InstallManager.pendingStatus(100, 0, 1000));
    }
    @Test public void sameVersionReinstallStillCompletesAndExpiryNeverReportsSuccess() {
        assertEquals(InstallManager.PendingStatus.FINISHED, InstallManager.pendingStatus(100, 200, 1000));
        assertEquals(InstallManager.PendingStatus.EXPIRED, InstallManager.pendingStatus(100, 200, -1));
        assertEquals(InstallManager.PendingStatus.EXPIRED, InstallManager.pendingStatus(100, 200, 1800001));
    }
}
