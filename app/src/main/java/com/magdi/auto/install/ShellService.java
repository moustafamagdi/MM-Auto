package com.magdi.auto.install;

import android.os.ParcelFileDescriptor;

import java.io.InputStream;

/**
 * Started by Shizuku in its own process with shell privileges. It must not use a Context.
 * Package validation happens in PackageTool, so only well-formed names reach the shell.
 */
public final class ShellService extends IShellService.Stub {
    public ShellService() {}

    @Override public void destroy() {
        System.exit(0);
    }

    @Override public int uid() {
        return android.os.Process.myUid();
    }

    @Override public String install(ParcelFileDescriptor apk, long size, String installer) {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(apk)) {
            ShellRunner.Output out = ShellRunner.run(PackageTool.installCommand(size, installer), input, 180);
            return out.exit + "\n" + out.text;
        } catch (Exception failure) {
            return "-1\n" + failure;
        }
    }

    @Override public String setInstaller(String packageName, String installer) {
        try {
            ShellRunner.Output out = ShellRunner.run(PackageTool.setInstallerCommand(packageName, installer), null, 30);
            return out.exit + "\n" + out.text;
        } catch (Exception failure) {
            return "-1\n" + failure;
        }
    }
}
