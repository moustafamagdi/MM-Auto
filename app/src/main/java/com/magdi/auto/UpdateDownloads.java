package com.magdi.auto;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.magdi.auto.install.InstallMode;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Process-owned transfers survive activity recreation. Only verified ready APKs are persisted. */
final class UpdateDownloads {
    enum State { DOWNLOADING, READY, INSTALLING, FAILED, CANCELLED }
    static final class Job {
        final Releases.App app;
        final Releases.Release release;
        final InstallMode mode;
        final TransferControl control;
        State state;
        volatile long bytes;
        File file;
        boolean installerOpened, covered, launching, recoveredInstaller;
        int requestCode;
        Job(Releases.App app, Releases.Release release, InstallMode mode, State state) {
            this.app = app; this.release = release; this.mode = mode; this.state = state;
            control = new TransferControl(10 * 60_000);
            if (state != State.DOWNLOADING) control.close();
        }
    }
    interface Backend { File download(Job job) throws Exception; }
    private static UpdateDownloads instance;
    static synchronized UpdateDownloads get(Context context) {
        if (instance == null) instance = new UpdateDownloads(context);
        return instance;
    }
    final LinkedHashMap<Releases.App, Job> jobs = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService workers;
    private final Context context;
    private final Backend backend;
    private Job active;

    private UpdateDownloads(Context context) {
        this(context, Executors.newFixedThreadPool(3), null);
    }
    UpdateDownloads(Context context, ExecutorService workers, Backend backend) {
        this.context = context.getApplicationContext(); this.workers = workers;
        this.backend = backend == null ? job -> Releases.download(job.app, job.release,
                this.context.getCacheDir(), this.context.getPackageManager(), job.control, bytes -> job.bytes = bytes) : backend;
        restore();
    }
    void listen(Runnable listener) { listeners.add(listener); }
    void unlisten(Runnable listener) { listeners.remove(listener); }
    Job get(Releases.App app) { return jobs.get(app); }
    Job active() { return active; }
    boolean occupied(Releases.App app) {
        Job j = get(app); return j != null && j.state != State.FAILED && j.state != State.CANCELLED;
    }
    boolean hasWork() { for (Releases.App app : jobs.keySet()) if (occupied(app)) return true; return false; }

    void start(Releases.App app, Releases.Release release, InstallMode mode) {
        if (occupied(app)) return;
        Job job = new Job(app, release, mode, State.DOWNLOADING);
        jobs.put(app, job);
        SupportEvents.event("download", app.packageName, "started", mode.name());
        changed();
        Runnable monitor = new Runnable() {
            final long deadline = android.os.SystemClock.elapsedRealtime() + 10 * 60_000;
            int lastPercent = -1;
            @Override public void run() {
                if (jobs.get(app) != job || job.state != State.DOWNLOADING) return;
                if (job.control.stopped() || android.os.SystemClock.elapsedRealtime() >= deadline) {
                    job.control.cancel(); job.control.close(); job.state = State.FAILED;
                    SupportEvents.event("download", app.packageName, "failed", mode.name());
                    changed(); return;
                }
                int percent = (int)Math.min(100, job.bytes * 100 / job.release.size);
                if (percent != lastPercent) { lastPercent = percent; changed(); }
                main.postDelayed(this, 250);
            }
        };
        main.post(monitor);
        workers.execute(() -> {
            File file = null;
            try { file = backend.download(job); job.control.check(); }
            catch (Exception failed) { file = null; /* Never expose response bodies or credentials. */ }
            finally { job.control.close(); }
            File result = file;
            main.post(() -> {
                // A cancelled job cannot complete or clear a newer job for this app.
                if (jobs.get(app) != job || job.state != State.DOWNLOADING) return;
                job.file = result;
                job.state = result == null ? State.FAILED : State.READY;
                SupportEvents.event("download", app.packageName, result == null ? "failed" : "ready", mode.name());
                save(); changed();
            });
        });
    }

    void cancel(Releases.App app) {
        Job job = jobs.get(app);
        if (job == null || (job.state != State.DOWNLOADING && job.state != State.READY)) return;
        job.control.cancel(); job.control.close(); job.state = State.CANCELLED;
        if (job.recoveredInstaller) {
            com.magdi.auto.install.InstallManager.clearPending(context, app.packageName);
            job.recoveredInstaller = false;
        }
        if (job.file != null) job.file.delete();
        SupportEvents.event("download", app.packageName, "cancelled", job.mode.name());
        save(); changed();
    }

    Job reserveNext() {
        if (active != null) return null;
        // Self-update terminates our process. Let all requested companion updates finish first.
        for (Job job : jobs.values()) {
            if (job.app == Releases.App.AUTO || !occupied(job.app)) continue;
            if (job.state == State.DOWNLOADING) return null; // selection order, not download completion order
            if (job.state == State.READY) return reserve(job);
        }
        Job self = jobs.get(Releases.App.AUTO);
        return self != null && self.state == State.READY ? reserve(self) : null;
    }
    private Job reserve(Job job) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("download_queue", 0);
        job.requestCode = 1000 + (prefs.getInt("request_sequence", 0) % 63000);
        prefs.edit().putInt("request_sequence", job.requestCode - 999).commit();
        active = job; job.state = State.INSTALLING; job.launching = true;
        save(); changed(); return job;
    }
    void release(Job job) {
        if (active != job) return;
        active = null; job.state = State.READY; job.launching = false;
        job.installerOpened = false; job.covered = false; save(); changed();
    }
    void finish(Job job) {
        if (active != job) return;
        active = null; jobs.remove(job.app); save(); changed();
    }
    void launched(Job job) { if (active == job) { job.launching = false; changed(); } }
    private void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }

    private void save() {
        JSONArray data = new JSONArray();
        try {
            for (Job j : jobs.values()) {
                if (j.state != State.READY && j.state != State.INSTALLING) continue;
                JSONObject asset = new JSONObject().put("name", j.release.url.getPath().substring(j.release.url.getPath().lastIndexOf('/') + 1))
                        .put("state", "uploaded").put("browser_download_url", j.release.url.toString())
                        .put("size", j.release.size).put("digest", j.release.digest)
                        .put("label", "versionCode=" + j.release.versionCode);
                data.put(new JSONObject().put("app", j.app.name()).put("mode", j.mode.name())
                        .put("installing", j.state == State.INSTALLING)
                        .put("release", new JSONObject().put("tag_name", j.release.version).put("prerelease", j.release.preview)
                                .put("assets", new JSONArray().put(asset))));
            }
            // Small journal committed before launching an installer (including MM Auto's own update).
            context.getSharedPreferences("download_queue", 0).edit().putString("ready", data.toString()).commit();
        } catch (Exception invalid) { throw new IllegalStateException("Could not save installation queue", invalid); }
    }
    private void restore() {
        try {
            JSONArray data = new JSONArray(context.getSharedPreferences("download_queue", 0).getString("ready", "[]"));
            for (int i = 0; i < data.length(); i++) {
                try {
                    JSONObject item = data.getJSONObject(i);
                    Releases.App app = Releases.App.valueOf(item.getString("app"));
                    if ("ROOT".equals(item.optString("mode"))) {
                        com.magdi.auto.install.InstallManager.clearPending(context, app.packageName);
                        continue; // Retire, never convert queued Root work into another installation.
                    }
                    Releases.Release release = Releases.parse(app, item.getJSONObject("release"));
                    File file = new File(new File(context.getCacheDir(), "releases"), app.packageName + ".apk");
                    if (release == null || !file.isFile() || !release.isNewer(Releases.installed(context.getPackageManager(), app))) continue;
                    Job job = new Job(app, release, InstallMode.valueOf(item.getString("mode")), State.READY);
                    job.recoveredInstaller = item.optBoolean("installing");
                    job.file = file; jobs.put(app, job);
                } catch (Exception invalidEntry) { /* Ignore missing/corrupt entries; revalidate bytes before installation. */ }
            }
        } catch (Exception invalid) { /* A corrupt journal never starts an installer. */ }
    }
}
