package com.magdi.auto.install;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rikka.shizuku.Shizuku;

/** Everything that touches the Shizuku API. Works with Shizuku, Shizuku-Next and other compatible builds. */
public final class ShizukuChannel {
    public static final int REQUEST_CODE = 25;
    private static final int SERVICE_VERSION = 1;

    public interface PermissionListener {
        void onResult(boolean granted);
    }

    private interface Work<T> {
        T run(IShellService service) throws Exception;
    }

    private static Shizuku.OnRequestPermissionResultListener registered;

    private ShizukuChannel() {}

    public static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static boolean granted() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** True when the user chose "deny and don't ask again" in Shizuku. */
    public static boolean blocked() {
        try {
            return Shizuku.shouldShowRequestPermissionRationale();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static void request() {
        try {
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (RuntimeException unavailable) {
            // Shizuku stopped while asking; the screen refreshes on the next resume.
        }
    }

    public static String version() {
        try {
            return Shizuku.pingBinder() ? Integer.toString(Shizuku.getVersion()) : "not running";
        } catch (RuntimeException unavailable) {
            return "unknown";
        }
    }

    public static synchronized void listen(PermissionListener listener) {
        unlisten();
        registered = (code, result) -> {
            if (code == REQUEST_CODE) listener.onResult(result == PackageManager.PERMISSION_GRANTED);
        };
        try {
            Shizuku.addRequestPermissionResultListener(registered);
        } catch (RuntimeException unavailable) {
            registered = null;
        }
    }

    public static synchronized void unlisten() {
        if (registered == null) return;
        try {
            Shizuku.removeRequestPermissionResultListener(registered);
        } catch (RuntimeException ignored) {
            // Nothing left to remove.
        }
        registered = null;
    }

    static ShellRunner.Output install(Context context, File apk) throws Exception {
        return withService(context, service -> {
            try (ParcelFileDescriptor descriptor =
                         ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY)) {
                return PackageTool.parse(service.install(descriptor, apk.length(), InstallIdentity.PLAY_STORE));
            }
        });
    }

    static ShellRunner.Output setInstaller(Context context, String packageName) throws Exception {
        return withService(context, service ->
                PackageTool.parse(service.setInstaller(packageName, InstallIdentity.PLAY_STORE)));
    }

    /** Must be called from a background thread: it waits for the Shizuku service to start. */
    private static <T> T withService(Context context, Work<T> work) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IShellService> holder = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                holder.set(IShellService.Stub.asInterface(binder));
                connected.countDown();
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                holder.set(null);
            }
        };
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(), ShellService.class.getName()))
                .processNameSuffix("shell")
                .tag("mm-installer")
                .version(SERVICE_VERSION)
                .daemon(false)
                .debuggable(false);
        Shizuku.bindUserService(args, connection);
        try {
            if (!connected.await(10, TimeUnit.SECONDS) || holder.get() == null) {
                throw new IOException("The Shizuku service did not start.");
            }
            return work.run(holder.get());
        } finally {
            Shizuku.unbindUserService(args, connection, true);
        }
    }
}
