package com.magdi.auto;

import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;

/** Document-picker adapter. Uses the existing release verifier and the existing installer. */
final class ManualApk {
    private ManualApk() {}

    static File prepare(Releases.App app, InputStream input, File cache, PackageManager pm,
                        BooleanSupplier cancelled) throws Exception {
        File directory = new File(cache, "releases");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Download storage is unavailable.");
        File temporary = File.createTempFile(app.packageName, ".apk", directory);
        File destination = new File(directory, app.packageName + ".apk");
        try {
            long bytes = 0;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new IOException("Cancelled");
                    bytes += count;
                    if (bytes > RepairApk.MAX_BYTES) throw new IOException("APK exceeds the supported size.");
                    output.write(buffer, 0, count);
                }
            }
            if (cancelled.getAsBoolean()) throw new IOException("Cancelled");
            if (bytes == 0) throw new IOException("The selected file is empty.");
            // No online metadata is required for a local file. Package, signer, installed signer,
            // version-code monotonicity and private-asset checks are identical to a download.
            Releases.validate(app, new Releases.Release("", 0, null, bytes, "", false), temporary, pm);
            if (!temporary.renameTo(destination)) throw new IOException("Could not save the verified APK.");
            return destination;
        } finally {
            temporary.delete();
        }
    }
}
