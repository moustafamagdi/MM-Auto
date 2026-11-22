package com.magdi.auto.install;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, manifest = Config.NONE)
public final class SystemInstallerFlowTest {
    private static final Uri APK = Uri.parse("content://com.magdi.auto.downloads/com.magdi.tube.apk");
    private RecordingContext context;
    private PackageManager pm;

    private static final class RecordingContext extends ContextWrapper {
        final List<String> readers = new ArrayList<>();
        int revocations;
        RecordingContext(Context base) { super(base); }
        @Override public void grantUriPermission(String pkg, Uri uri, int flags) {
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, flags);
            readers.add(pkg);
        }
        @Override public void revokeUriPermission(Uri uri, int flags) { revocations++; }
    }

    @Before public void setup() {
        context = new RecordingContext(RuntimeEnvironment.getApplication());
        pm = context.getPackageManager();
        context.getSharedPreferences("install", 0).edit().clear().commit();
    }

    private void handler(String pkg, boolean system) {
        ResolveInfo resolve = new ResolveInfo();
        resolve.isDefault = true;
        resolve.activityInfo = new ActivityInfo();
        resolve.activityInfo.name = pkg + ".InstallStart";
        resolve.activityInfo.packageName = pkg;
        resolve.activityInfo.enabled = true;
        resolve.activityInfo.exported = true;
        resolve.activityInfo.applicationInfo = new ApplicationInfo();
        resolve.activityInfo.applicationInfo.packageName = pkg;
        resolve.activityInfo.applicationInfo.enabled = true;
        resolve.activityInfo.applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        for (Uri uri : new Uri[]{APK, Uri.parse("content://com.magdi.auto.downloads/com.magdi.auto.apk")}) {
            shadowOf(pm).addResolveInfoForIntent(new Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(uri, SystemInstaller.MIME), resolve);
        }
    }

    private void installed(long updateTime) {
        PackageInfo info = new PackageInfo();
        info.packageName = "com.magdi.tube";
        info.lastUpdateTime = updateTime;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = info.packageName;
        shadowOf(pm).installPackage(info);
    }

    @Test public void onlySystemHandlersReceiveTheVerifiedUriAndTheIntentIsExplicit() {
        handler("other.installer", false);
        handler("com.google.android.packageinstaller", true);
        SystemInstaller target = SystemInstaller.resolve(pm, APK);
        assertEquals("com.google.android.packageinstaller", target.intent.getComponent().getPackageName());
        assertFalse(target.readers.contains("other.installer"));
        assertTrue(target.readers.contains("com.android.shell"));
        assertTrue(target.intent.getBooleanExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false));
        assertEquals("com.android.vending", target.intent.getStringExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME));
        assertEquals(APK, target.intent.getClipData().getItemAt(0).getUri());
        assertTrue(SystemInstaller.isSystemInstaller(pm, "com.google.android.packageinstaller"));
        assertFalse(SystemInstaller.isSystemInstaller(pm, "other.installer"));
    }

    @Test public void openingTheInstallerIsPendingAndNeverAnInstallationSuccess() {
        handler("com.google.android.packageinstaller", true);
        installed(100);
        InstallManager.Outcome out = InstallManager.openSystemInstaller(context, APK, "com.magdi.tube", false,
                (ctx, intent) -> new ShellRunner.Output(0, "Starting: Intent {}"));
        assertTrue(out.pending);
        assertFalse(out.ok);
        assertEquals(InstallManager.PendingStatus.WAITING, InstallManager.pollPending(context, "com.magdi.tube").status);
        assertEquals(0, context.revocations);
        assertFalse(context.readers.contains("other.installer"));
    }

    @Test public void confirmationStateSurvivesARecreatedContextAndIsConsumedOnce() {
        handler("com.google.android.packageinstaller", true);
        installed(100);
        InstallManager.openSystemInstaller(context, APK, "com.magdi.tube", true,
                (ctx, intent) -> new ShellRunner.Output(0, "Starting: Intent {}"));
        RecordingContext recreated = new RecordingContext(context.getBaseContext());
        assertTrue(InstallManager.hasPending(recreated));
        installed(200);
        InstallManager.PendingResult done = InstallManager.pollPending(recreated, "com.magdi.tube");
        assertEquals(InstallManager.PendingStatus.FINISHED, done.status);
        assertTrue(done.repair);
        assertEquals(1, recreated.revocations);
        assertFalse(InstallManager.hasPending(recreated));
        assertEquals(InstallManager.PendingStatus.NONE, InstallManager.pollPending(recreated, "com.magdi.tube").status);
    }

    @Test public void cancellationWithoutPackageChangeNeverBecomesSuccess() {
        installed(100);
        InstallManager.markPending(context, "com.magdi.tube", APK, false);
        assertEquals(InstallManager.PendingStatus.WAITING, InstallManager.pollPending(context, "com.magdi.tube").status);
        assertEquals(InstallManager.PendingStatus.WAITING, InstallManager.pollPending(context, "com.magdi.tube").status);
        assertTrue(InstallManager.hasPending(context));
    }

    @Test public void failedLaunchRevokesReadAccessAndDoesNotFallBackOrLeavePending() {
        handler("com.google.android.packageinstaller", true);
        int[] calls = {0};
        InstallManager.Outcome out = InstallManager.openSystemInstaller(context, APK, "com.magdi.tube", false,
                (ctx, intent) -> { calls[0]++; return new ShellRunner.Output(0, "Starting: Intent {}\nError type 3"); });
        assertEquals(1, calls[0]);
        assertFalse(out.ok);
        assertFalse(out.pending);
        assertFalse(InstallManager.hasPending(context));
        assertTrue(context.revocations > 0);
    }

    @Test public void thirdPartyOnlyHandlerIsRejectedBeforeDispatch() {
        handler("other.installer", false);
        InstallManager.Outcome out = InstallManager.openSystemInstaller(context, APK, "com.magdi.tube", false,
                (ctx, intent) -> { throw new AssertionError("Third-party installer was launched"); });
        assertFalse(out.pending);
        assertFalse(out.ok);
        assertTrue(context.readers.isEmpty());
    }
}
