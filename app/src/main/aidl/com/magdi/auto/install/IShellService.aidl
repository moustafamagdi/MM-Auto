package com.magdi.auto.install;

import android.os.ParcelFileDescriptor;

// Runs inside the Shizuku "shell" process (uid 2000). Every method returns "<exit code>\n<output>".
interface IShellService {
    void destroy() = 16777114; // transaction code reserved by Shizuku
    int uid() = 1;
    String install(in ParcelFileDescriptor apk, long size, String installer) = 2;
    String setInstaller(String packageName, String installer) = 3;
}
