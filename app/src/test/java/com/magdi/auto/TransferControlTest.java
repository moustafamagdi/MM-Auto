package com.magdi.auto;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransferControlTest {
    @Test public void continuousTrickleCannotExtendOverallDeadline() throws Exception {
        try (TransferControl control = new TransferControl(120)) {
            InputStream trickle = new InputStream() {
                public int read() { return 1; }
                public int read(byte[] bytes) {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) { }
                    bytes[0] = 1; return 1;
                }
            };
            long start = System.nanoTime();
            try { Releases.copy(trickle, new ByteArrayOutputStream(), 1_000_000, null, control, bytes -> {}); fail(); }
            catch (InterruptedIOException expected) { assertTrue(control.stopped()); }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);
        }
    }

    @Test public void cancelDoesNotWaitForDisconnectAndAbortsTheAttachedConnection() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), unblock = new CountDownLatch(1), attached = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (TransferControl control = new TransferControl(10_000)) {
            HttpURLConnection connection = new HttpURLConnection(new URL("https://example.invalid")) {
                public void connect() { }
                public boolean usingProxy() { return false; }
                public void disconnect() {
                    entered.countDown();
                    try { unblock.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                }
            };
            Future<Boolean> blocked = worker.submit(() -> {
                control.attach(connection); attached.countDown();
                try { Thread.sleep(10_000); return false; }
                catch (InterruptedException expected) { return true; }
            });
            assertTrue(attached.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime(); control.cancel();
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 500);
            assertTrue(blocked.get(2, TimeUnit.SECONDS));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            try { control.attach(connection); fail(); } catch (InterruptedIOException expected) { }
        } finally { unblock.countDown(); worker.shutdownNow(); }
    }

    @Test public void cancelledOldTransferCannotStopAnotherTransfer() throws Exception {
        try (TransferControl old = new TransferControl(1000); TransferControl current = new TransferControl(1000)) {
            old.cancel(); current.check(); assertFalse(current.stopped());
        }
    }
}
