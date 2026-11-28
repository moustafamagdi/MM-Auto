package com.magdi.auto;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35, application=Application.class)
public class InternalPairingServiceTest {
    static class Managed implements AutoCloseable {
        final ServiceController<InternalPairingService> controller=Robolectric.buildService(InternalPairingService.class).create();
        InternalPairingService get(){return controller.get();}
        public void close(){controller.destroy();}
    }
    Context c;
    @Before public void setup() {
        c=RuntimeEnvironment.getApplication();
        InternalInstallOperation.prefs(c).edit().clear().putString("app","TUBE").putString("state","setup").commit();
    }
    Notification notification() { return shadowOf(c.getSystemService(NotificationManager.class)).getNotification(InternalPairingService.NOTIFICATION); }
    @Test public void replyIsExplicitServiceAndInvalidCodeDoesNotLeaveSettingsOrPersistInput() {
        try(Managed controller=new Managed()) {
            InternalPairingService service=controller.get();
            service.onStartCommand(InternalPairingService.start(c,Releases.App.TUBE),0,1);
            Notification.Action action=notification().actions[0];
            assertEquals(1,action.getRemoteInputs().length);
            Intent reply=new Intent(shadowOf(action.actionIntent).getSavedIntent());
            assertEquals(InternalPairingService.class.getName(),reply.getComponent().getClassName());
            Bundle values=new Bundle(); values.putCharSequence(InternalPairingService.CODE,"bad-code");
            RemoteInput.addResultsToIntent(action.getRemoteInputs(),reply,values);
            service.onStartCommand(reply,0,2);
            assertFalse(InternalInstallOperation.busy());
            assertEquals("setup",InternalInstallOperation.state(c));
            assertNull(reply.getClipData());
            assertNull(shadowOf((Application)c).getNextStartedActivity());
            assertFalse(InternalInstallOperation.prefs(c).getAll().toString().contains("bad-code"));
            assertEquals(c.getString(R.string.internal_invalid_code),notification().extras.getString(Notification.EXTRA_TEXT));
        }
    }
    @Test public void expiredNotificationCannotStartPairing() {
        try(Managed controller=new Managed()) {
            InternalPairingService service=controller.get();
            service.onStartCommand(InternalPairingService.start(c,Releases.App.TUBE),0,1);
            Intent oldReply=new Intent(shadowOf(notification().actions[0].actionIntent).getSavedIntent());
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(InternalPairingService.WINDOW_MS+1));
            assertEquals(c.getString(R.string.pair_notification_expired),notification().extras.getString(Notification.EXTRA_TEXT));
            assertTrue(notification().actions==null || notification().actions.length==0);
            service.onStartCommand(oldReply,0,2);
            assertFalse(InternalInstallOperation.busy());
        }
    }
    @Test public void wrongTargetCannotStartAndOldReplyCannotReplayAfterServiceDeath() {
        Intent oldReply;
        try(Managed controller=new Managed()) {
            controller.get().onStartCommand(InternalPairingService.start(c,Releases.App.TUBE),0,1);
            oldReply=new Intent(shadowOf(notification().actions[0].actionIntent).getSavedIntent());
        }
        try(Managed controller=new Managed()) {
            controller.get().onStartCommand(oldReply,0,1);
            controller.get().onStartCommand(InternalPairingService.start(c,Releases.App.IPTV),0,2);
            assertFalse(InternalInstallOperation.busy());
            assertEquals("TUBE",InternalInstallOperation.prefs(c).getString("app",""));
        }
    }
    @Test public void developerSettingsRequestsWirelessHighlightWithoutClearingTask() {
        Intent intent=WirelessDebuggingSettings.developerOptions();
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,intent.getAction());
        assertEquals("toggle_adb_wireless",intent.getStringExtra(WirelessDebuggingSettings.KEY));
        assertEquals("toggle_adb_wireless",intent.getBundleExtra(":settings:show_fragment_args").getString(WirelessDebuggingSettings.KEY));
        assertEquals(0,intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
