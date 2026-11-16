package com.magdi.auto.install;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a command, optionally feeding it a stream on stdin, and captures stdout and stderr together. */
final class ShellRunner {
    static final class Output {
        final int exit;
        final String text;

        Output(int exit, String text) {
            this.exit = exit;
            this.text = text;
        }

        /** True when the command exited normally. */
        boolean ok() {
            return exit == 0;
        }

        /** True when "pm install" reported success. */
        boolean installed() {
            return exit == 0 && text.contains("Success");
        }
    }

    private ShellRunner() {}

    static Output run(List<String> command, InputStream stdin, long timeoutSeconds)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            try (OutputStream out = process.getOutputStream()) {
                if (stdin != null) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = stdin.read(buffer)) != -1) out.write(buffer, 0, read);
                }
            } catch (IOException earlyExit) {
                // The command may have stopped reading; its output explains why.
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("The command timed out.");
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1 && captured.size() < 64 * 1024) {
                    captured.write(buffer, 0, read);
                }
            }
            return new Output(process.exitValue(), captured.toString(StandardCharsets.UTF_8.name()).trim());
        } finally {
            process.destroy();
        }
    }
}
