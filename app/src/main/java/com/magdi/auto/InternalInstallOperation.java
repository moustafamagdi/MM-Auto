package com.magdi.auto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import com.magdi.auto.install.InstallIdentity;
import com.magdi.auto.install.InstallManager;
import com.magdi.auto.install.InstallMode;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

/** Activity-independent single flight. Persistent progress is informational, never an automatic retry. */
final class InternalInstallOperation {
    private static volatile boolean running;
    private static volatile Releases.App active;
    private static volatile boolean cancelled;
    private static volatile TransferControl transfer;
    private static volatile Thread runner;
    private static final java.util.concurrent.ExecutorService WORK = Executors.newSingleThreadExecutor();
    static boolean busy() { return running; }
    static Releases.App activeApp() { return active; }
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("internal_install", 0); }
    static String state(Context context) { return prefs(context).getString("state", "setup"); }
    static void select(Context context, Releases.App app) {
        if (running) return;
        SharedPreferences p = prefs(context);
        String old = state(context);
        if (!app.name().equals(p.getString("app", "")))
            p.edit().putString("app", app.name()).putString("state", "setup").remove("detail").remove("update_requested").commit();
        else if (old.equals("pairing") || old.equals("connecting") || old.equals("preparing") || old.equals("installing"))
            p.edit().putString("state", "interrupted").commit();
    }
    interface Task { void run(Context context) throws Exception; }
    private static synchronized void start(Context context, Releases.App app, String phase, Task task) {
        if (running) return;
        Context c = context.getApplicationContext();
        if (!prefs(c).edit().putString("app", app.name()).putString("state", phase).remove("detail").commit()) return;
        running = true; active = app;
        cancelled = false;
        WORK.execute(() -> {
            runner = Thread.currentThread();
            try { checkCancelled(); task.run(c); }
            catch (Exception | LinkageError failure) {
                // No ports, pairing codes, paths, key material, shell output or stack traces in diagnostics.
                String detail = failure instanceof InstallFailure ? failure.getMessage() : failure.getClass().getSimpleName();
                if (!phase.equals("preparing")) SupportEvents.event("permission", app.packageName, "unavailable", "adb");
                String failedState = cancelled ? "interrupted" : (phase.equals("connecting") || failure instanceof ConnectionFailure) ? connectionFailureState(failure) : "failed";
                SharedPreferences.Editor edit=prefs(c).edit().putString("state",failedState).putString("detail",phase+": "+detail);
                if (phase.equals("connecting") || failure instanceof ConnectionFailure) edit.remove("connect_port");
                if (failedState.equals("pair_required")) edit.putBoolean("paired",false);
                edit.commit();
                if (phase.equals("preparing")) {
                    InstallManager.recordAttempt(c, app.packageName, InstallMode.INTERNAL_ADB, "Installation not confirmed: " + detail);
                    SupportEvents.event("install", app.packageName, "failed", "adb");
                }
            } finally { transfer = null; runner = null; Thread.interrupted(); running = false; }
        });
    }
    static synchronized void cancel(Context context) {
        if (!running || state(context).equals("installing")) return;
        cancelled = true;
        TransferControl current = transfer; if (current != null) current.cancel();
        Thread thread = runner; if (thread != null) thread.interrupt();
    }
    private static void checkCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("CANCELLED");
    }
    private static synchronized void beginInstall(Context c, long before, long expected) throws IOException {
        checkCancelled();
        if (!prefs(c).edit().putString("state", "installing").putLong("before", before)
                .putLong("expected_version", expected).commit()) throw new IOException("STATE_NOT_SAVED");
    }
    static void pair(Context context, Releases.App app, int port, String code) {
        InternalAdbPolicy.code(code); if (port != 0) InternalAdbPolicy.port(Integer.toString(port));
        start(context, app, "pairing", c -> {
            try (InternalAdbClient client = new InternalAdbClient(c)) {
                int actualPort = port == 0 ? InternalAdbClient.discover(c, true) : port;
                if (!client.pairLocal(actualPort, code)) throw new IOException();
                checkCancelled();
                SupportEvents.event("permission", app.packageName, "ready", "adb");
                prefs(c).edit().putBoolean("paired", true).putString("state", "paired").commit();
            }
        });
    }
    interface ConnectionProbe { int verify(Context context, int port) throws Exception; }
    static ConnectionProbe connectionProbe = InternalInstallOperation::verifyConnection;
    private static int verifyConnection(Context context, int port) throws Exception {
        try (InternalAdbClient client = new InternalAdbClient(context)) {
            int actual = port == 0 ? InternalAdbClient.discover(context, false) : port;
            if (!client.connectLocal(actual)) throw new IOException("LOCAL_CONNECTION_FAILED");
            return actual;
        }
    }
    static String connectionFailureState(Throwable failure) {
        for (int depth=0; failure!=null && depth<8; depth++,failure=failure.getCause())
            if (failure instanceof io.github.muntashirakon.adb.AdbPairingRequiredException
                    || failure instanceof io.github.muntashirakon.adb.AdbAuthenticationFailedException)
                return "pair_required";
        return "connection_unavailable";
    }
    static void verifyOnResume(Context context, Releases.App app) {
        String phase=state(context);
        if (!busy() && prefs(context).getBoolean("paired",false)
                && (phase.equals("setup") || phase.equals("paired") || phase.equals("ready"))) connect(context,app,0);
    }
    static void connect(Context context, Releases.App app, int port) {
        if (port != 0) InternalAdbPolicy.port(Integer.toString(port));
        start(context, app, "connecting", c -> {
            int actualPort = connectionProbe.verify(c, port);
            checkCancelled();
            prefs(c).edit().putBoolean("paired",true).putInt("connect_port", actualPort).putString("state", "ready").commit();
        });
    }
    static void install(Context context, Releases.App app) {
        InternalAdbPolicy.target(app.name());

        boolean preview = ReleaseChannel.preview(context);
        boolean updateRequested = prefs(context).getBoolean("update_requested", false);
        start(context, app, "preparing", c -> {
            File apk = null;
            boolean repair = !updateRequested && Releases.installed(c.getPackageManager(), app) != null;
            try (InternalAdbClient client = new InternalAdbClient(c)) {
                // Fail before downloading if the helper has stopped / its dynamic port changed.
                int port = connectForInstall(client, c);
                if (repair) apk = RepairApk.prepare(c, app);
                else {
                    SupportEvents.event("download", app.packageName, "started", "adb");
                    try (TransferControl control = new TransferControl(10 * 60000)) {
                        transfer = control; checkCancelled();
                        Releases.Release release = Releases.latest(app, null, control, preview);
                        if (release == null || (updateRequested && !release.isNewer(Releases.installed(c.getPackageManager(), app)))) throw new IOException("NO_NEWER_RELEASE");
                        apk = Releases.download(app, release, c.getCacheDir(), c.getPackageManager(), control, bytes -> {});
                        Releases.validate(app, release, apk, c.getPackageManager());
                    }
                    transfer = null;
                    SupportEvents.event("download", app.packageName, "ready", "adb");
                }
                // Reconnect after preparation; no background persistent ADB service is needed.
                client.disconnect();
                port = connectForInstall(client, c);
                PackageInfo archive = c.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (archive == null) throw new IOException();
                long expectedVersion = archive.getLongVersionCode();
                long before = updateTime(c, app);
                beginInstall(c, before, expectedVersion);
                InstallManager.recordAttempt(c, app.packageName, InstallMode.INTERNAL_ADB, "Installation requested; awaiting Android result.");
                SupportEvents.event("install", app.packageName, "started", "adb");
                String output = client.install(app, apk);
                if (!InternalAdbPolicy.success(output)) throw new InstallFailure(InternalAdbPolicy.failureCode(output));
                PackageInfo installed = Releases.installed(c.getPackageManager(), app);
                if (installed == null || installed.getLongVersionCode() != expectedVersion || updateTime(c, app) == before)
                    throw new InstallFailure("RESULT_NOT_YET_OBSERVED");
                InstallIdentity identity = InstallIdentity.read(c.getPackageManager(), app.packageName);
                InstallManager.recordAttempt(c, app.packageName, InstallMode.INTERNAL_ADB,
                        "Android package manager returned Success; installed version and update time confirmed.\n"
                        + "Installer target: " + (identity.viaPlayStore() ? "MATCH" : "MISMATCH") + ".\n"
                        + identity.describe() + "\nAndroid Auto visibility/playback: NOT_TESTED.");
                SupportEvents.event("install", app.packageName, "finished", "adb");
                SupportEvents.event("install-check", app.packageName, InstallManager.matchesRecordedRoute(c, app.packageName, identity) ? "match" : "mismatch", "adb");
                prefs(c).edit().putString("state", "finished").putBoolean("installer_match", identity.viaPlayStore()).commit();
            } finally { if (repair && apk != null) apk.delete(); }
        });
    }
    private static long updateTime(Context c, Releases.App app) {
        PackageInfo info = Releases.installed(c.getPackageManager(), app); return info == null ? 0 : info.lastUpdateTime;
    }
    private static int connectForInstall(InternalAdbClient client, Context context) throws ConnectionFailure {
        try {int port=InternalAdbClient.discover(context,false);if(!client.connectLocal(port))throw new IOException("LOCAL_CONNECTION_FAILED");return port;}
        catch(Exception failure){throw new ConnectionFailure(failure);}
    }
    private static final class ConnectionFailure extends IOException { ConnectionFailure(Exception cause){super(cause);} }
    private static final class InstallFailure extends IOException { InstallFailure(String code) { super(code); } }
    private InternalInstallOperation() {}
}
