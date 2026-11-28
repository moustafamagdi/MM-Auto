package com.magdi.auto;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class)
public class InternalInstallScreenTest {
    private InternalInstallOperation.ConnectionProbe original;
    @org.junit.Before public void mockConnection(){original=InternalInstallOperation.connectionProbe;InternalInstallOperation.connectionProbe=(c,p)->4567;}
    @org.junit.After public void restoreConnection() throws Exception {awaitProbe();InternalInstallOperation.connectionProbe=original;}
    static void awaitProbe() throws Exception {for(int i=0;i<100&&InternalInstallOperation.busy();i++)Thread.sleep(10);assertFalse(InternalInstallOperation.busy());}

    @Test public void bothLanguagesAndThemesKeepCredentialsOutOfSavedState() {
        Context c = RuntimeEnvironment.getApplication();
        for (String language : new String[]{"ar","en"}) for (String theme : new String[]{"dark","light"}) {
            InternalInstallOperation.prefs(c).edit().clear().commit();
            c.getSharedPreferences("ui",0).edit().putString("language",language).putString("theme",theme).commit();
            try (org.robolectric.android.controller.ActivityController<InternalInstallActivity> controller =
                    Robolectric.buildActivity(InternalInstallActivity.class, new Intent(c,InternalInstallActivity.class).putExtra("app","TUBE")).setup()) {
                InternalInstallActivity activity=controller.get();
                assertEquals(theme.equals("dark"),UiStyle.dark(activity));
                assertEquals(language,activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                List<EditText> inputs=new ArrayList<>(); collect(activity.findViewById(android.R.id.content),inputs);
                assertEquals(0,inputs.size());
                clickManual(activity.findViewById(android.R.id.content), activity.getString(R.string.pair_manual_show));
                collect(activity.findViewById(android.R.id.content),inputs);
                assertEquals(2,inputs.size());
                for(EditText field:inputs)assertFalse(field.isSaveEnabled());
                assertFalse(InternalInstallOperation.busy());
            }
        }
    }
    @Test public void interruptedInstallationShowsReviewWithoutExecutingAnything() {
        Context c=RuntimeEnvironment.getApplication();
        InternalInstallOperation.prefs(c).edit().clear().putString("app","TUBE").putString("state","installing").commit();
        try(org.robolectric.android.controller.ActivityController<InternalInstallActivity> controller =
                Robolectric.buildActivity(InternalInstallActivity.class,new Intent(c,InternalInstallActivity.class).putExtra("app","TUBE")).setup()) {
            assertEquals("interrupted",InternalInstallOperation.state(c));
            assertFalse(InternalInstallOperation.busy());
        }
    }
    @Test public void explicitNewRequestKeepsPairingButDoesNotReuseSuccessOrAutoInstall() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        InternalInstallOperation.prefs(c).edit().clear().putString("app","TUBE").putString("state","finished").putBoolean("paired",true).commit();
        try(org.robolectric.android.controller.ActivityController<InternalInstallActivity> controller=Robolectric.buildActivity(InternalInstallActivity.class,new Intent(c,InternalInstallActivity.class).putExtra("app","TUBE").putExtra("new_request",true)).setup()) {
            awaitProbe();assertEquals("ready",InternalInstallOperation.state(c));assertTrue(InternalInstallOperation.prefs(c).getBoolean("paired",false));assertFalse(InternalInstallOperation.busy());
            controller.recreate();awaitProbe();assertFalse(InternalInstallOperation.busy());
        }
    }
    @Test public void revokedPairingClearsReadinessButNetworkFailureDoesNotErasePairing() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        for(boolean revoked:new boolean[]{true,false}) {
            InternalInstallOperation.prefs(c).edit().clear().putString("app","TUBE").putString("state","ready").putBoolean("paired",true).putInt("connect_port",1234).commit();
            InternalInstallOperation.connectionProbe=(ctx,port)->{if(revoked)throw new io.github.muntashirakon.adb.AdbAuthenticationFailedException();throw new java.io.IOException("network unavailable");};
            InternalInstallOperation.verifyOnResume(c,Releases.App.TUBE);awaitProbe();
            assertEquals(revoked?"pair_required":"connection_unavailable",InternalInstallOperation.state(c));
            assertEquals(!revoked,InternalInstallOperation.prefs(c).getBoolean("paired",false));
            assertFalse(InternalInstallOperation.prefs(c).contains("connect_port"));
            assertFalse(InternalInstallOperation.busy());
        }
    }
    private static boolean clickManual(View view,String label) {
        if(view instanceof android.widget.Button && label.contentEquals(((android.widget.Button)view).getText())) { view.performClick(); return true; }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(clickManual(((ViewGroup)view).getChildAt(i),label))return true;
        return false;
    }
    private static void collect(View view,List<EditText> inputs) {
        if(view instanceof EditText)inputs.add((EditText)view);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collect(((ViewGroup)view).getChildAt(i),inputs);
    }
}
