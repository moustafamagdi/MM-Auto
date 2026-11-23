package com.magdi.auto;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import com.magdi.auto.install.InstallMode;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class UpdateDownloadsTest {
    Context context;
    ExecutorService workers;
    UpdateDownloads queue;
    EnumMap<Releases.App, CountDownLatch> gates;
    CountDownLatch started;

    static Releases.Release release(Releases.App app) throws Exception {
        String name = app == Releases.App.AUTO ? "MM-Auto" : app == Releases.App.TUBE ? "MM-Tube" : "MM-IPTV";
        return new Releases.Release("9.0.0", 900, new URL("https://github.com/moustafamagdi/" + app.repo
                + "/releases/download/v9.0.0/" + name + "-9.0.0.apk"), 4, "", false);
    }
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("download_queue", 0).edit().clear().commit();
        context.getSharedPreferences("install", 0).edit().clear().commit();
        workers = Executors.newFixedThreadPool(3);
        gates = new EnumMap<>(Releases.App.class);
        for (Releases.App app : Releases.App.values()) gates.put(app, new CountDownLatch(1));
        started = new CountDownLatch(3);
        queue = new UpdateDownloads(context, workers, job -> {
            started.countDown(); gates.get(job.app).await(5, TimeUnit.SECONDS);
            job.control.check();
            File dir = new File(context.getCacheDir(), "releases"); dir.mkdirs();
            File apk = new File(dir, job.app.packageName + ".apk");
            Files.write(apk.toPath(), new byte[] {1, 2, 3, 4}); return apk;
        });
    }
    @After public void close() { gates.values().forEach(CountDownLatch::countDown); workers.shutdownNow(); }
    void start(Releases.App app) throws Exception { queue.start(app, release(app), InstallMode.CLASSIC); }
    void awaitState(Releases.App app, UpdateDownloads.State state) throws Exception {
        for (int i=0; i<100; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (queue.get(app).state == state) return;
            Thread.sleep(10);
        }
        assertEquals(state, queue.get(app).state);
    }
    @Test public void downloadsOverlapButInstallationIsSerialAndAutoIsLast() throws Exception {
        start(Releases.App.AUTO); start(Releases.App.IPTV); start(Releases.App.TUBE);
        assertTrue(started.await(2, TimeUnit.SECONDS)); // all three workers entered before any completed
        gates.get(Releases.App.AUTO).countDown(); gates.get(Releases.App.TUBE).countDown();
        awaitState(Releases.App.AUTO, UpdateDownloads.State.READY);
        awaitState(Releases.App.TUBE, UpdateDownloads.State.READY);
        assertNull(queue.reserveNext()); // IPTV was selected first among companion apps
        gates.get(Releases.App.IPTV).countDown(); awaitState(Releases.App.IPTV, UpdateDownloads.State.READY);
        UpdateDownloads.Job one = queue.reserveNext(); assertEquals(Releases.App.IPTV, one.app);
        assertNull(queue.reserveNext()); queue.finish(one);
        UpdateDownloads.Job two = queue.reserveNext(); assertEquals(Releases.App.TUBE, two.app);
        queue.finish(two); assertEquals(Releases.App.AUTO, queue.reserveNext().app);
    }
    @Test public void cancelIsImmediateAndLateCompletionCannotReplaceRetry() throws Exception {
        start(Releases.App.IPTV); UpdateDownloads.Job old = queue.get(Releases.App.IPTV);
        queue.cancel(Releases.App.IPTV); assertEquals(UpdateDownloads.State.CANCELLED, old.state);
        start(Releases.App.IPTV); UpdateDownloads.Job retry = queue.get(Releases.App.IPTV);
        gates.get(Releases.App.IPTV).countDown(); awaitState(Releases.App.IPTV, UpdateDownloads.State.READY);
        assertNotSame(old, retry); assertSame(retry, queue.reserveNext());
        assertTrue(old.control.stopped());
    }
    @Test public void failureAndCancellationDoNotBlockOtherReadyApps() throws Exception {
        start(Releases.App.IPTV); start(Releases.App.TUBE);
        queue.cancel(Releases.App.IPTV);
        gates.get(Releases.App.TUBE).countDown(); awaitState(Releases.App.TUBE, UpdateDownloads.State.READY);
        assertEquals(Releases.App.TUBE, queue.reserveNext().app);
    }
    @Test public void readyAndActiveEntriesSurviveRestartAndMissingFilesAreIgnored() throws Exception {
        start(Releases.App.IPTV); start(Releases.App.TUBE);
        gates.values().forEach(CountDownLatch::countDown);
        awaitState(Releases.App.IPTV, UpdateDownloads.State.READY); awaitState(Releases.App.TUBE, UpdateDownloads.State.READY);
        queue.reserveNext(); // process terminated during installer
        com.magdi.auto.install.InstallManager.markPending(context, Releases.App.IPTV.packageName);
        UpdateDownloads restored = new UpdateDownloads(context, workers, job -> { throw new AssertionError("Must not download again"); });
        assertEquals(UpdateDownloads.State.READY, restored.get(Releases.App.IPTV).state);
        assertEquals(UpdateDownloads.State.READY, restored.get(Releases.App.TUBE).state);
        assertNull(restored.active());
        restored.cancel(Releases.App.IPTV);
        assertFalse(com.magdi.auto.install.InstallManager.hasPending(context));
        UpdateDownloads again = new UpdateDownloads(context, workers, job -> null);
        assertNull(again.get(Releases.App.IPTV)); assertNotNull(again.get(Releases.App.TUBE));
        again.get(Releases.App.TUBE).file.delete();
        assertFalse(new UpdateDownloads(context, workers, job -> null).hasWork());
    }
}
