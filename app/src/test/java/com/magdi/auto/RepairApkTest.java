package com.magdi.auto;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public final class RepairApkTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void pinMatchesUnchangedDownloadValidator() throws Exception {
        Field pin = Releases.class.getDeclaredField("SIGNER");
        pin.setAccessible(true);
        assertEquals(pin.get(null), RepairApk.SIGNER);
    }

    @Test public void acceptsOnlySameVersionOfficialPackage() throws Exception {
        RepairApk.checkIdentity("com.magdi.tube", "com.magdi.tube", 23, 23, RepairApk.SIGNER);
    }

    @Test(expected = IOException.class) public void rejectsOtherPackage() throws Exception {
        RepairApk.checkIdentity("com.magdi.tube", "com.other.app", 23, 23, RepairApk.SIGNER);
    }

    @Test(expected = IOException.class) public void rejectsOtherSigner() throws Exception {
        RepairApk.checkIdentity("com.magdi.tube", "com.magdi.tube", 23, 23, "different");
    }

    @Test(expected = IOException.class) public void rejectsUpgradeDuringRepair() throws Exception {
        RepairApk.checkIdentity("com.magdi.tube", "com.magdi.tube", 23, 24, RepairApk.SIGNER);
    }

    @Test(expected = IOException.class) public void rejectsDowngradeDuringRepair() throws Exception {
        RepairApk.checkIdentity("com.magdi.tube", "com.magdi.tube", 23, 22, RepairApk.SIGNER);
    }

    @Test public void copiesInstalledBytesExactly() throws Exception {
        File source = temp.newFile();
        byte[] bytes = new byte[100003];
        new java.util.Random(42).nextBytes(bytes);
        Files.write(source.toPath(), bytes);
        File target = temp.newFile();
        RepairApk.copyVerified(source, target);
        assertArrayEquals(bytes, Files.readAllBytes(target.toPath()));
    }

    @Test(expected = IOException.class) public void rejectsEmptyApk() throws Exception {
        RepairApk.copyVerified(temp.newFile(), temp.newFile());
    }

    @Test(expected = IOException.class) public void rejectsOversizedApk() throws Exception {
        File source = temp.newFile();
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.setLength(RepairApk.MAX_BYTES + 1);
        }
        RepairApk.copyVerified(source, temp.newFile());
    }

    @Test(expected = IOException.class) public void rejectsPrivateAccount() throws Exception {
        File apk = temp.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(apk))) {
            zip.putNextEntry(new ZipEntry("assets/initial-account.json"));
            zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        RepairApk.rejectPrivateAsset(apk);
    }

    @Test public void acceptsZipWithoutPrivateAccount() throws Exception {
        File apk = temp.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(apk))) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.closeEntry();
        }
        RepairApk.rejectPrivateAsset(apk);
    }
}
