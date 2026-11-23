package com.magdi.auto;

import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One deadline across DNS, redirects and all reads. Cancellation never waits on the UI thread. */
final class TransferControl implements AutoCloseable {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "transfer-deadlines"); t.setDaemon(true); return t;
    });
    private static final java.util.concurrent.ExecutorService ABORT = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "transfer-abort"); t.setDaemon(true); return t;
    });
    private final ScheduledFuture<?> alarm;
    private boolean stopped, closed;
    private HttpURLConnection connection;
    private Thread runner;
    private String reason = "Download cancelled.";

    TransferControl(long timeoutMs) {
        alarm = TIMER.schedule(() -> stop("The request timed out."), timeoutMs, TimeUnit.MILLISECONDS);
    }

    synchronized void attach(HttpURLConnection value) throws InterruptedIOException {
        check(); connection = value; runner = Thread.currentThread();
    }

    synchronized void detach(HttpURLConnection value) {
        if (connection == value) connection = null;
    }

    synchronized void check() throws InterruptedIOException {
        if (stopped || Thread.currentThread().isInterrupted()) throw new InterruptedIOException(reason);
    }

    synchronized boolean stopped() { return stopped; }

    void cancel() { stop("Download cancelled."); }

    private void stop(String message) {
        HttpURLConnection abort;
        synchronized (this) {
            if (stopped || closed) return;
            stopped = true; reason = message; abort = connection;
            if (runner != null) runner.interrupt();
        }
        // disconnect() itself can wait for a network lock on some Android versions.
        if (abort != null) ABORT.execute(abort::disconnect);
    }

    @Override public synchronized void close() { closed = true; alarm.cancel(false); connection = null; runner = null; }
}
