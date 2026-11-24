package com.magdi.auto;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import com.magdi.auto.install.InstallMode;
import com.magdi.auto.install.InstallManager;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class UpdateScreenTest {
    Context context;
    ExecutorService downloadsWorker;
    CountDownLatch releaseDownloads;
    UpdateDownloads queue;
    ActivityController<MainActivity> screen;
    MainActivity activity;
    static Object field(Object object, String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method m=object.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(object,args);
    }
    Object row(Releases.App app) throws Exception { return ((Map<?,?>)field(activity,"rows")).get(app); }
    Button button(Releases.App app, String name) throws Exception { return (Button)field(row(app),name); }
    void drain() throws Exception {
        ((ExecutorService)field(activity,"worker")).submit(() -> {}).get(5,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Before public void setup() throws Exception {
        context=RuntimeEnvironment.getApplication();
        PendingSetup.clear(context);
        context.getSharedPreferences("download_queue",0).edit().clear().commit();
        context.getSharedPreferences("install",0).edit().clear().commit();
        context.getSharedPreferences("release_checks_v1",0).edit().clear().commit();
        Shadows.shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(true);
        downloadsWorker=Executors.newFixedThreadPool(3); releaseDownloads=new CountDownLatch(1);
        queue=new UpdateDownloads(context,downloadsWorker,job -> {
            releaseDownloads.await(10,TimeUnit.SECONDS); job.control.check(); return null;
        });
        Field singleton=UpdateDownloads.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null,queue);
        for(Releases.App app: Releases.App.values()) ReleaseCache.check(context,app,()->UpdateDownloadsTest.release(app));
        Bundle state=new Bundle(); state.putString("picking_app","AUTO"); // suppress automatic fetch, keep fixture offline
        screen=Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible();
        activity=screen.get(); drain();
    }
    @After public void close() {
        if(screen!=null)screen.pause().stop().destroy();
        releaseDownloads.countDown(); downloadsWorker.shutdownNow();
    }
    @Test public void mainInstallIgnoresOldShizukuPreferenceAndKeepsClassic() throws Exception {
        InstallManager.setMode(context,InstallMode.SHIZUKU);button(Releases.App.TUBE,"install").performClick();
        assertEquals(InstallMode.CLASSIC,queue.get(Releases.App.TUBE).mode);
    }
    @Test public void returningFromCancelledStandaloneInstallDoesNotBlockTheQueue() throws Exception {
        InstallManager.markPending(context,Releases.App.TUBE.packageName);
        invoke(activity,"markStandalone",new Class<?>[]{Releases.App.class},Releases.App.TUBE);
        context.getSharedPreferences("ui",0).edit().putBoolean("standalone_covered",true).commit();
        invoke(activity,"finishStandaloneReturn",new Class<?>[]{});
        assertFalse(InstallManager.hasPending(context));
        assertEquals(activity.getString(R.string.install_incomplete),field(row(Releases.App.TUBE),"note"));
        assertNull(field(row(Releases.App.TUBE),"attributionMatch"));
    }
    @Test public void freshInstallGuideRestoresTargetAfterRecreationWithoutAutoInstall() throws Exception {
        invoke(activity,"beginSetup",new Class<?>[]{Releases.App.class,PendingSetup.Kind.class},Releases.App.TUBE,PendingSetup.Kind.INSTALL);
        assertEquals(Releases.App.TUBE,PendingSetup.read(context).app);
        screen.recreate();activity=screen.get();drain();
        assertEquals(PendingSetup.Kind.INSTALL,PendingSetup.read(context).kind);
        assertNotNull(field(activity,"shizukuGuide"));assertFalse(queue.hasWork());
        ShizukuGuide guide=(ShizukuGuide)field(activity,"shizukuGuide");
        guide.dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();assertNull(PendingSetup.read(context));
    }
    @Test public void actualButtonsAllowParallelDownloadsAndImmediateIndependentCancel() throws Exception {
        assertTrue(button(Releases.App.IPTV,"install").performClick());
        assertFalse(button(Releases.App.IPTV,"install").isEnabled());
        assertTrue(button(Releases.App.TUBE,"install").isEnabled());
        button(Releases.App.TUBE,"install").performClick();
        assertTrue(queue.occupied(Releases.App.IPTV)); assertTrue(queue.occupied(Releases.App.TUBE));
        button(Releases.App.IPTV,"cancel").performClick();
        assertTrue(button(Releases.App.IPTV,"install").isEnabled());
        assertFalse(button(Releases.App.TUBE,"install").isEnabled());
        assertEquals(activity.getString(R.string.cancelled), ((TextView)field(row(Releases.App.IPTV),"status")).getText().toString());
        assertTrue(((Button)field(activity,"check")).isEnabled());
    }
    @Test public void downloadAllStartsEveryOfferWithoutGloballyLockingThePage() throws Exception {
        ((Button)field(activity,"downloadAll")).performClick();
        for(Releases.App app:Releases.App.values()) assertTrue(queue.occupied(app));
        assertTrue(((Button)field(activity,"check")).isEnabled());
        assertTrue(((Button)field(activity,"settingsButton")).isEnabled());
    }
    @Test public void deadlineUnlocksCheckKeepsCachedOffersAndRejectsLateCallbacks() throws Exception {
        CountDownLatch blocked=new CountDownLatch(1), entered=new CountDownLatch(3);
        activity.releaseLookup=(app,call)->{
            entered.countDown();
            while(!blocked.await(20,TimeUnit.MILLISECONDS)) { /* deliberately ignore cancellation until test releases */ }
            return UpdateDownloadsTest.release(app);
        };
        ((Button)field(activity,"check")).performClick();
        assertTrue(entered.await(3,TimeUnit.SECONDS));
        assertFalse(((Button)field(activity,"check")).isEnabled());
        assertTrue(button(Releases.App.IPTV,"install").isEnabled());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertTrue(((Button)field(activity,"check")).isEnabled());
        assertNotNull(ReleaseCache.read(context,Releases.App.IPTV).release);
        String before=((TextView)field(row(Releases.App.IPTV),"status")).getText().toString();
        assertTrue(before.contains(activity.getString(R.string.check_timeout)));
        blocked.countDown();
        ExecutorService checks=(ExecutorService)field(activity,"checks");
        checks.shutdown(); assertTrue(checks.awaitTermination(3,TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(before,((TextView)field(row(Releases.App.IPTV),"status")).getText().toString());
    }
    @Test public void installerCancellationReleasesOnlyItsSlotAndDoesNotReportSuccess() throws Exception {
        queue.start(Releases.App.IPTV,UpdateDownloadsTest.release(Releases.App.IPTV),InstallMode.CLASSIC);
        queue.start(Releases.App.TUBE,UpdateDownloadsTest.release(Releases.App.TUBE),InstallMode.CLASSIC);
        // Isolate the Android installer handoff from byte verification (covered by verifier tests).
        queue.get(Releases.App.IPTV).state=UpdateDownloads.State.READY;
        queue.get(Releases.App.TUBE).state=UpdateDownloads.State.READY;
        UpdateDownloads.Job first=queue.reserveNext();
        invoke(activity,"launchQueuedInstaller",new Class<?>[]{UpdateDownloads.Job.class},first);
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        assertNull(queue.reserveNext());
        activity.onActivityResult(first.requestCode,MainActivity.RESULT_CANCELED,null);
        assertFalse(InstallManager.hasPending(context));
        assertNull(queue.get(Releases.App.IPTV));
        UpdateDownloads.Job second=queue.reserveNext();
        assertEquals(Releases.App.TUBE,second.app);
        activity.onActivityResult(first.requestCode,MainActivity.RESULT_CANCELED,null);
        assertSame(second,queue.active()); // late callback from previous installer cannot clear this slot
        assertNotEquals(Boolean.TRUE,field(row(Releases.App.IPTV),"attributionMatch"));
    }
}
