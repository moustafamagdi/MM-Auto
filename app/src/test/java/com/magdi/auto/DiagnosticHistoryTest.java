package com.magdi.auto;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import com.magdi.auto.install.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=android.app.Application.class)
public class DiagnosticHistoryTest {
    Context c; final String pkg="com.magdi.tube";
    @Before public void setup(){c=RuntimeEnvironment.getApplication();c.getSharedPreferences("install",0).edit().clear().commit();}
    void installed(long at){PackageInfo p=new PackageInfo();p.packageName=pkg;p.lastUpdateTime=at;
        p.applicationInfo=new ApplicationInfo();p.applicationInfo.packageName=pkg;
        Shadows.shadowOf(c.getPackageManager()).installPackage(p);
        Shadows.shadowOf(c.getPackageManager()).setInstallSourceInfo(pkg,"com.android.vending","com.android.vending");}
    @Test public void actualMethodSurvivesSettingsChangeAndContextRecreation() throws Exception {
        installed(100);InstallManager.markPending(c,pkg,null,true,InstallMode.SHIZUKU);
        InstallManager.setMode(c,InstallMode.CLASSIC);installed(200);
        InstallManager.PendingResult result=InstallManager.pollPending(c.createPackageContext(c.getPackageName(),0),pkg);
        assertEquals(InstallManager.PendingStatus.FINISHED,result.status);
        assertEquals(InstallMode.SHIZUKU,result.method);assertTrue(result.repair);
        InstallManager.verifyAndFix(c,result.method,pkg,message->{},"");
        assertTrue(InstallManager.lastAttempt(c,pkg).contains("Method: SHIZUKU"));
        assertTrue(InstallManager.lastAttempt(c,pkg).contains("Install metadata: MATCH"));
        assertFalse(InstallManager.hasPending(c));
    }
    @Test public void legacyAttemptHasUnknownTimeAndMethodRatherThanInventedCurrentValues(){
        c.getSharedPreferences("install",0).edit().putString("last_"+pkg,"Old mismatch").commit();
        String text=InstallManager.lastAttempt(c,pkg);assertTrue(text.contains("older version"));assertTrue(text.contains("Method: UNKNOWN"));assertTrue(text.contains("Old mismatch"));
    }
    @Test public void legacyPendingMethodIsNotInferredFromGlobalPreference(){
        installed(100);InstallManager.markPending(c,pkg);
        c.getSharedPreferences("install",0).edit().remove("route_"+pkg).commit();
        InstallManager.setMode(c,InstallMode.SHIZUKU);installed(200);
        assertNull(InstallManager.pollPending(c,pkg).method);
    }
    @Test public void reportSeparatesCurrentObservationFromHistoryAndDoesNotRewriteIt(){
        installed(100);InstallManager.recordAttempt(c,pkg,InstallMode.SHIZUKU,"Old attempt marker");
        String saved=InstallManager.lastAttempt(c,pkg);
        String report=InstallDiagnostics.report(c,java.util.Collections.singletonMap("MM Tube",pkg));
        assertTrue(report.contains("CURRENT STATE (read from Android now)"));
        assertTrue(report.contains("LAST RECORDED ATTEMPT (historical"));
        assertEquals(saved,InstallManager.lastAttempt(c,pkg));
    }
}
