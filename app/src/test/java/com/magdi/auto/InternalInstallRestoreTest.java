package com.magdi.auto;
import android.content.Context;
import com.magdi.auto.install.InstallManager;
import com.magdi.auto.install.InstallMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class InternalInstallRestoreTest {
    @Test public void shellSourceIsAcceptedOnlyForARecordedInternalRoute() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        java.lang.reflect.Constructor<com.magdi.auto.install.InstallIdentity> constructor =
                com.magdi.auto.install.InstallIdentity.class.getDeclaredConstructor(boolean.class,String.class,String.class,String.class);
        constructor.setAccessible(true);
        com.magdi.auto.install.InstallIdentity source = constructor.newInstance(true,"com.android.vending","com.android.shell",null);
        InstallManager.recordAttempt(c,"com.magdi.tube",InstallMode.CLASSIC,"old result");
        assertFalse(InstallManager.matchesRecordedRoute(c,"com.magdi.tube",source));
        InstallManager.recordAttempt(c,"com.magdi.tube",InstallMode.INTERNAL_ADB,"observed result");
        assertTrue(InstallManager.matchesRecordedRoute(c,"com.magdi.tube",source));
        assertFalse(InstallManager.matchesRecordedRoute(c,"com.magdi.tube",
                constructor.newInstance(true,null,"com.android.shell",null)));
    }
    @Test public void processDeathNeverReplaysAnInstallation() {
        Context c = RuntimeEnvironment.getApplication();
        for (String phase : new String[]{"pairing","connecting","preparing","installing"}) {
            InternalInstallOperation.prefs(c).edit().clear().putString("app","TUBE").putString("state",phase).commit();
            InternalInstallOperation.select(c, Releases.App.TUBE);
            assertEquals("interrupted", InternalInstallOperation.state(c));
            assertFalse(InternalInstallOperation.busy());
        }
    }
    @Test public void resultAndTargetSurviveRecreationWithoutExecuting() {
        Context c = RuntimeEnvironment.getApplication();
        InternalInstallOperation.prefs(c).edit().clear().putString("app","IPTV").putString("state","finished").commit();
        InternalInstallOperation.select(c, Releases.App.IPTV);
        assertEquals("finished", InternalInstallOperation.state(c));
        InternalInstallOperation.select(c, Releases.App.TUBE);
        assertEquals("setup", InternalInstallOperation.state(c));
        assertEquals("TUBE", InternalInstallOperation.prefs(c).getString("app",""));
        assertFalse(InternalInstallOperation.busy());
    }
    @Test public void internalRouteCannotBecomeNormalInstallDefault() {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("install",0).edit().putString("mode","INTERNAL_ADB").commit();
        assertEquals(InstallMode.CLASSIC, InstallManager.mode(c));
    }
}
