package com.magdi.auto;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import java.util.EnumMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class UpdateCheckJob extends JobService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private JobParameters active;
    private Future<?> check;

    @Override public boolean onStartJob(JobParameters params) {
        if (!UpdateAlerts.enabled(this) || !UpdateAlerts.canNotify(this)) return false;
        Network network = params.getNetwork();
        if (network == null) return false;

        active = params;
        long generation = ReleaseChannel.generation(this);
        boolean previews = ReleaseChannel.preview(this);
        check = worker.submit(() -> {
            EnumMap<Releases.App, Releases.Release> updates = new EnumMap<>(Releases.App.class);
            for (Releases.App app : Releases.App.values()) {
                if (Thread.currentThread().isInterrupted()) return;
                if (Releases.installed(getPackageManager(), app) == null) continue;
                try {
                    ReleaseCache.Snapshot saved = ReleaseCache.check(this, app, () -> {try (TransferControl call = new TransferControl(30_000)) {return Releases.latest(app, network, call, previews);}});
                    updates.put(app, saved.release);
                } catch (Exception unavailable) {
                    // The next scheduled check retries this app without blocking the other checks.
                }
            }
            main.post(() -> {
                if (active != params) return;
                active = null;
                check = null;
                try {
                    if (generation == ReleaseChannel.generation(this)) updates.forEach((app, release) -> UpdateAlerts.notifyIfNew(this, app, release));
                } finally {
                    jobFinished(params, false);
                }
            });
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopCheck();
        return UpdateAlerts.enabled(this);
    }

    @Override public void onDestroy() {
        stopCheck();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void stopCheck() {
        active = null;
        if (check != null) {
            check.cancel(true);
            check = null;
        }
    }
}
