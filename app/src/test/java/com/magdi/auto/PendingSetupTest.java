package com.magdi.auto;

import android.content.Context;
import com.magdi.auto.install.InstallManager;
import com.magdi.auto.install.InstallMode;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=android.app.Application.class)
public class PendingSetupTest {
    Context c;
    @Before public void setup(){c=RuntimeEnvironment.getApplication();PendingSetup.clear(c);
        c.getSharedPreferences("install",0).edit().clear().commit();}
    @Test public void restoresExactTargetAndOperationWithoutStartingAnything() throws Exception {
        for(PendingSetup.Kind kind:PendingSetup.Kind.values()){
            PendingSetup.save(c,Releases.App.TUBE,kind);
            PendingSetup restored=PendingSetup.read(c.createPackageContext(c.getPackageName(),0));
            assertEquals(Releases.App.TUBE,restored.app);assertEquals(kind,restored.kind);
            assertFalse(InstallManager.hasPending(c));
        }
    }
    @Test public void cancelledExpiredAndInvalidTasksCannotResume(){
        PendingSetup.save(c,Releases.App.IPTV,PendingSetup.Kind.REPAIR);PendingSetup.clear(c);assertNull(PendingSetup.read(c));
        PendingSetup.save(c,Releases.App.IPTV,PendingSetup.Kind.REPAIR);
        c.getSharedPreferences("setup_task",0).edit().putLong("at",1).commit();assertNull(PendingSetup.read(c));
        PendingSetup.save(c,Releases.App.IPTV,PendingSetup.Kind.REPAIR);
        c.getSharedPreferences("setup_task",0).edit().putString("kind","ROOT").commit();assertNull(PendingSetup.read(c));
    }
    @Test public void oldRootChoiceIsPersistentlyMigratedWithoutResumingImportedApk(){
        c.getSharedPreferences("install",0).edit().putString("mode","ROOT").commit();
        c.getSharedPreferences("ui",0).edit().putString("pending_local","TUBE").commit();
        assertEquals(InstallMode.CLASSIC,InstallManager.mode(c));
        assertEquals("CLASSIC",c.getSharedPreferences("install",0).getString("mode",""));
        assertFalse(c.getSharedPreferences("ui",0).contains("pending_local"));
        assertEquals(2,InstallMode.values().length);
    }
}
