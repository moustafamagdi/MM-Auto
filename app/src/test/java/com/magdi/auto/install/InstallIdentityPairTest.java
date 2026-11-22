package com.magdi.auto.install;

import org.junit.Test;
import static org.junit.Assert.*;

public final class InstallIdentityPairTest {
    @Test public void acceptsPlayAsInstallerAndSystemInstallerAsInitiator() {
        assertTrue(InstallIdentity.matchesExpectedPair("com.android.vending", "com.google.android.packageinstaller", true));
    }
    @Test public void acceptsPlayAsBothSources() {
        assertTrue(InstallIdentity.matchesExpectedPair("com.android.vending", "com.android.vending", false));
    }
    @Test public void rejectsPackageInstallerAsInstallingSource() {
        assertFalse(InstallIdentity.matchesExpectedPair("com.google.android.packageinstaller", "com.google.android.packageinstaller", true));
    }
    @Test public void rejectsShellInitiatorEvenWithPlayInstaller() {
        assertFalse(InstallIdentity.matchesExpectedPair("com.android.vending", "com.android.shell", true));
    }
    @Test public void rejectsMissingOrUnverifiedInitiator() {
        assertFalse(InstallIdentity.matchesExpectedPair("com.android.vending", null, false));
        assertFalse(InstallIdentity.matchesExpectedPair("com.android.vending", "other.app", false));
    }
    @Test public void rejectsMissingInstallerEvenWithSystemInitiator() {
        assertFalse(InstallIdentity.matchesExpectedPair(null, "com.android.packageinstaller", true));
    }
}
