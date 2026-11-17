package com.magdi.auto;

import org.junit.Test;
import java.util.regex.Matcher;
import static org.junit.Assert.*;

public final class ReleaseAssetNameTest {
    @Test public void acceptsStableAndExistingPreviewNames() {
        assertVersion(Releases.App.AUTO, "MM-Auto-0.2.3.apk", "0.2.3");
        assertVersion(Releases.App.TUBE, "Magdi-Tube-0.1.22-audio-icon.apk", "0.1.22-audio-icon");
        assertVersion(Releases.App.IPTV, "MM-IPTV-0.1.7-keyboard.apk", "0.1.7-keyboard");
    }

    @Test public void acceptsNewSuffixesWithoutLosingVersion() {
        assertVersion(Releases.App.TUBE, "Magdi-Tube-0.1.23-regional-login.apk", "0.1.23-regional-login");
        assertVersion(Releases.App.TUBE, "MM-Tube-0.1.24-rc.1.apk", "0.1.24-rc.1");
        assertVersion(Releases.App.IPTV, "Magdi-IPTV-1.2.3-preview-2.apk", "1.2.3-preview-2");
    }

    @Test public void preservesAppSpecificPrefixes() {
        assertFalse(Releases.App.TUBE.assetName.matcher("MM-IPTV-0.1.23-regional-login.apk").matches());
        assertFalse(Releases.App.AUTO.assetName.matcher("Magdi-Auto-0.2.3.apk").matches());
        assertFalse(Releases.App.TUBE.assetName.matcher("Other-Tube-0.1.23.apk").matches());
    }

    @Test public void rejectsMalformedOrNonApkAssets() {
        String[] invalid = {"Magdi-Tube-0.1.apk", "Magdi-Tube-0.1.23-.apk",
                "Magdi-Tube-0.1.23-rc..1.apk", "Magdi-Tube-0.1.23-regional/login.apk",
                "Magdi-Tube-0.1.23-preview apk.apk", "Magdi-Tube-0.1.23.apk.sha256",
                "../Magdi-Tube-0.1.23.apk", "Magdi-Tube-123456789.1.23.apk"};
        for (String name : invalid) assertFalse(name, Releases.App.TUBE.assetName.matcher(name).matches());
    }

    @Test public void newerNumericVersionStillWinsAcrossSuffixes() {
        assertTrue(Releases.compareVersions("0.1.23-regional-login", "0.1.22-audio-icon") > 0);
        assertEquals(0, Releases.compareVersions("0.1.23-rc.1", "0.1.23-regional-login"));
    }

    private void assertVersion(Releases.App app, String name, String expected) {
        Matcher match = app.assetName.matcher(name);
        assertTrue(name, match.matches());
        assertEquals(expected, match.group(1));
    }
}
