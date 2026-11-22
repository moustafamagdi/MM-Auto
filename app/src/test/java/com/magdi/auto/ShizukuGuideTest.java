package com.magdi.auto;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=android.app.Application.class)
public class ShizukuGuideTest {
    static final class Fake implements ShizukuGuide.Probe {
        boolean installed,running,granted,blocked;int requests;
        public boolean installed(){return installed;} public boolean running(){return running;}
        public boolean granted(){return granted;} public boolean blocked(){return blocked;}
        public void request(){requests++;}
    }
    Activity activity;Fake fake;AtomicInteger repairs;ShizukuGuide guide;
    @Before public void setup(){activity=Robolectric.buildActivity(Activity.class).setup().get();fake=new Fake();repairs=new AtomicInteger();}
    @After public void cleanup(){if(guide!=null)guide.dialog.dismiss();activity.finish();}
    private void show(boolean xiaomi){guide=new ShizukuGuide(activity,"MM IPTV",repairs::incrementAndGet,fake,xiaomi);guide.show();}
    private String allText(View v){String s=v instanceof TextView?((TextView)v).getText().toString()+"\n":"";
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s+=allText(((ViewGroup)v).getChildAt(i));return s;}
    @Test public void missingHelperButtonOpensOfficialDownloadWithoutRepair(){show(false);guide.primary.performClick();
        Intent i=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals("https://shizuku.rikka.app/download/",i.getDataString());assertEquals(0,repairs.get());}
    @Test public void stoppedServiceDoesNotTreatOldGrantAsReady(){fake.installed=true;fake.granted=true;show(false);
        assertEquals(activity.getString(R.string.guide_open),guide.primary.getText().toString());assertEquals(ShizukuGuide.Stage.START,ShizukuGuide.stage(fake));}
    @Test public void liveCompatibleForkIsDetectedWithoutPackage(){fake.running=true;show(false);guide.primary.performClick();assertEquals(1,fake.requests);assertEquals(0,repairs.get());}
    @Test public void grantingPermissionOnlyRefreshesUiAndNeedsExplicitRepairTap(){fake.running=true;show(false);guide.primary.performClick();
        fake.granted=true;guide.refresh();assertEquals(0,repairs.get());assertEquals(activity.getString(R.string.guide_repair),guide.primary.getText().toString());
        guide.primary.performClick();assertEquals(1,repairs.get());assertFalse(guide.dialog.isShowing());}
    @Test public void serviceLossBeforeTapCannotStartRepair(){fake.installed=true;fake.running=true;fake.granted=true;show(false);
        fake.running=false;guide.primary.performClick();assertEquals(0,repairs.get());assertTrue(guide.dialog.isShowing());assertEquals(activity.getString(R.string.guide_open),guide.primary.getText().toString());}
    @Test public void revokedPermissionBeforeTapCannotStartRepair(){fake.running=true;fake.granted=true;show(false);
        fake.granted=false;guide.primary.performClick();assertEquals(0,repairs.get());assertEquals(0,fake.requests);assertEquals(activity.getString(R.string.guide_allow),guide.primary.getText().toString());}
    @Test public void blockedPermissionOpensManagerInsteadOfRepeatingRequest(){fake.running=true;fake.blocked=true;show(false);
        guide.primary.performClick();assertEquals(0,fake.requests);assertEquals(activity.getString(R.string.guide_open),guide.primary.getText().toString());
        assertTrue(guide.description.getText().toString().contains("Authorized applications"));}
    @Test public void xiaomiManualSettingIsNotReportedAsVerified(){fake.running=true;fake.granted=true;show(true);
        String text=allText(guide.dialog.getWindow().getDecorView());assertTrue(text.contains("USB debugging (Security settings)"));assertTrue(text.contains("cannot check"));}
    @Test public void otherBrandsDoNotGetXiaomiRequirement(){show(false);assertFalse(allText(guide.dialog.getWindow().getDecorView()).contains("USB debugging (Security settings)"));
        assertFalse(ShizukuGuide.isXiaomi("Samsung","samsung"));assertTrue(ShizukuGuide.isXiaomi("Xiaomi","POCO"));assertTrue(ShizukuGuide.isXiaomi("unknown","Redmi"));}
    @Test public void arabicScreenKeepsSettingNamesAndTranslatesActions(){Configuration c=new Configuration(activity.getResources().getConfiguration());c.setLocale(Locale.forLanguageTag("ar"));
        activity.getResources().updateConfiguration(c,activity.getResources().getDisplayMetrics());show(true);
        assertEquals("تنزيل Shizuku",guide.primary.getText().toString());assertTrue(allText(guide.dialog.getWindow().getDecorView()).contains("USB debugging (Security settings)"));}
    @Test public void closingGuideDoesNotRepair(){fake.running=true;fake.granted=true;show(false);guide.dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();assertEquals(0,repairs.get());}
    @Test public void refreshTracksRestartWithoutAutoGrantOrRepair(){fake.installed=true;show(false);fake.running=true;guide.refresh();
        assertEquals(activity.getString(R.string.guide_allow),guide.primary.getText().toString());fake.running=false;guide.refresh();assertEquals(activity.getString(R.string.guide_open),guide.primary.getText().toString());assertEquals(0,fake.requests);assertEquals(0,repairs.get());}
}
