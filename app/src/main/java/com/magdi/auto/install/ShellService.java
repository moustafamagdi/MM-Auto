package com.magdi.auto.install;

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

    @Override public String openInstaller(String uri, String component, int userId) {
        try {
            ShellRunner.Output out = ShellRunner.run(PackageTool.openInstallerCommand(uri, component, userId), null, 30);
            return out.exit + "\n" + out.text;
        } catch (Exception failure) {
            return "-1\n" + failure;
        }
    }

}
