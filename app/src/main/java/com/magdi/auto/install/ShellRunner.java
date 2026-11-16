package com.magdi.auto.install;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        ExecutorService streams = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "installer-stream");
            thread.setDaemon(true);
            return thread;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            Future<?> writer = streams.submit(() -> {
                try (OutputStream out = process.getOutputStream()) {
                    if (stdin != null) {
                        byte[] buffer = new byte[32 * 1024];
                        int read;
                        while ((read = stdin.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                } catch (IOException earlyExit) {
                    // The command may have stopped reading; its output explains why.
                }
                });
            Future<String> reader = streams.submit(() -> {
                ByteArrayOutputStream captured = new ByteArrayOutputStream();
                try (InputStream in = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        int keep = Math.min(read, 64 * 1024 - captured.size());
                        if (keep > 0) captured.write(buffer, 0, keep);
                    }
                }
                return captured.toString(StandardCharsets.UTF_8.name()).trim();
                });
            if (!process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new IOException("The command timed out.");
            }
            writer.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            String output = reader.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return new Output(process.exitValue(), output);
        } catch (TimeoutException timeout) {
            throw new IOException("The command timed out.", timeout);
        } catch (ExecutionException failure) {
            throw new IOException("Could not read the command output.", failure.getCause());
        } finally {
            process.destroyForcibly();
            streams.shutdownNow();
        }
    }
}
