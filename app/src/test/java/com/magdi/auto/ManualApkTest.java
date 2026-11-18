package com.magdi.auto;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.Assert.*;

public final class ManualApkTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void emptyImportIsRejectedAndTemporaryIsDeleted() throws Exception {
        rejects(new ByteArrayInputStream(new byte[0]), false, "empty");
    }

    @Test public void cancelledImportIsDeletedBeforeValidation() throws Exception {
        rejects(new ByteArrayInputStream(new byte[10]), true, "Cancelled");
    }

    @Test public void oversizedImportIsRejectedBeforePackageParsing() throws Exception {
        InputStream endless = new InputStream() {
            @Override public int read() { return 1; }
            @Override public int read(byte[] b) { return b.length; }
        };
        rejects(endless, false, "size");
    }

    @Test public void readFailureDoesNotReplacePreviouslyVerifiedApk() throws Exception {
        File dir = new File(temp.getRoot(), "releases");
        assertTrue(dir.mkdirs());
        File previous = new File(dir, Releases.App.TUBE.packageName + ".apk");
        java.nio.file.Files.write(previous.toPath(), new byte[]{1, 2, 3});
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failed"); }
        };
        try { ManualApk.prepare(Releases.App.TUBE, broken, temp.getRoot(), null, () -> false); fail(); }
        catch (IOException expected) { assertEquals("read failed", expected.getMessage()); }
        assertArrayEquals(new byte[]{1,2,3}, java.nio.file.Files.readAllBytes(previous.toPath()));
        assertEquals(1, dir.listFiles().length);
    }

    private void rejects(InputStream input, boolean cancel, String message) throws Exception {
        try { ManualApk.prepare(Releases.App.TUBE, input, temp.getRoot(), null, () -> cancel); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
        assertEquals(0, new File(temp.getRoot(), "releases").listFiles().length);
    }
}
