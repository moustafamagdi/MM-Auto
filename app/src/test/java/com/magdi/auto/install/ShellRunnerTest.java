package com.magdi.auto.install;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public final class ShellRunnerTest {
    private List<String> command(String mode) throws Exception {
        return Arrays.asList(new File(System.getProperty("java.home"), "bin/java").getPath(),
                "-cp", new File(Child.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath(),
                Child.class.getName(), mode);
    }

    @Test public void drainsOutputWhileFeedingApk() throws Exception {
        ShellRunner.Output result = ShellRunner.run(command("duplex"),
                new ByteArrayInputStream(new byte[1024 * 1024]), 10);
        assertTrue(result.installed());
        assertTrue(result.text.length() <= 64 * 1024);
    }

    @Test public void timesOutWhenChildDoesNotReadApk() throws Exception {
        long started = System.nanoTime();
        try {
            ShellRunner.run(command("blocked"), new ByteArrayInputStream(new byte[1024 * 1024]), 1);
            fail("Expected a timeout");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("timed out"));
        }
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 5);
    }

    @Test public void retainsFailureOutputAndExitCode() throws Exception {
        ShellRunner.Output result = ShellRunner.run(command("failure"), null, 10);
        assertEquals(7, result.exit);
        assertFalse(result.installed());
        assertTrue(result.text.contains("Failure"));
    }

    public static final class Child {
        public static void main(String[] args) throws Exception {
            if ("blocked".equals(args[0])) {
                Thread.sleep(30000);
            } else if ("failure".equals(args[0])) {
                System.err.println("Failure");
                System.exit(7);
            } else {
                System.out.println("Success");
                byte[] output = new byte[1024 * 1024];
                Arrays.fill(output, (byte) 'x');
                System.out.write(output);
                System.out.flush();
                byte[] buffer = new byte[8192];
                while (System.in.read(buffer) != -1) { }
            }
        }
    }
}
