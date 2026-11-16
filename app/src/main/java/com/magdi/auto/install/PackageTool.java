package com.magdi.auto.install;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Builds the package manager commands that are run by the Shizuku service and by su. */
final class PackageTool {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");

    private PackageTool() {}

    /** pm install reading the APK from stdin; "-i" records the installer package. */
    static List<String> installCommand(long size, String installer) {
        if (size <= 0) throw new IllegalArgumentException("Invalid APK size.");
        List<String> command = new ArrayList<>();
        command.add("pm");
        command.add("install");
        command.add("-r");
        command.add("-S");
        command.add(Long.toString(size));
        command.add("-i");
        command.add(checked(installer));
        return command;
    }

    static List<String> setInstallerCommand(String packageName, String installer) {
        List<String> command = new ArrayList<>();
        command.add("pm");
        command.add("set-installer");
        command.add(checked(packageName));
        command.add(checked(installer));
        return command;
    }

    static List<String> asRoot(List<String> command) {
        List<String> wrapped = new ArrayList<>();
        wrapped.add("su");
        wrapped.add("-c");
        wrapped.add(String.join(" ", command));
        return wrapped;
    }

    static ShellRunner.Output parse(String raw) {
        if (raw == null) return new ShellRunner.Output(-1, "No answer from the Shizuku service.");
        int newline = raw.indexOf('\n');
        String head = newline < 0 ? raw : raw.substring(0, newline);
        try {
            return new ShellRunner.Output(Integer.parseInt(head.trim()), newline < 0 ? "" : raw.substring(newline + 1));
        } catch (NumberFormatException invalid) {
            return new ShellRunner.Output(-1, raw);
        }
    }

    private static String checked(String name) {
        if (name == null || !PACKAGE.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid package name.");
        }
        return name;
    }
}
