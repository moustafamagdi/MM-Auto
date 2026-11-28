package com.magdi.auto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class InternalIdentityTest {
    @Test public void privatePairingIdentitySurvivesRecreationAndUsesLoopback() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        try (InternalAdbClient first = new InternalAdbClient(c); InternalAdbClient second = new InternalAdbClient(c)) {
            assertArrayEquals(first.getCertificate().getEncoded(), second.getCertificate().getEncoded());
            assertEquals("127.0.0.1", first.getHostAddress());
            assertTrue(new java.io.File(c.getNoBackupFilesDir(),"internal-adb.p12").isFile());
            assertFalse(new java.io.File(c.getCacheDir(),"internal-adb.p12").exists());
        }
    }
}
