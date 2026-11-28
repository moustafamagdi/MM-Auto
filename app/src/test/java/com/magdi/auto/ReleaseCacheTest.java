package com.magdi.auto;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class ReleaseCacheTest {
    private Context context;

    @Before public void clear() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("release_checks_v1", 0).edit().clear().commit();
    }

    @Test public void fixedNamesUseReleaseMetadataAndSurviveCacheRoundTrip() throws Exception {
        for (Releases.App app : Releases.App.values()) {
            org.json.JSONObject asset = new org.json.JSONObject()
                    .put("name", app.canonicalName).put("state", "uploaded").put("size", 123)
                    .put("label", "")
                    .put("browser_download_url", "https://github.com/moustafamagdi/" + app.repo
                            + "/releases/download/v1.2.3-rc.1/" + app.canonicalName);
            org.json.JSONObject record = new org.json.JSONObject().put("tag_name", "v1.2.3-rc.1").put("body", "<!-- mm-version-code: 501 -->")
                    .put("assets", new org.json.JSONArray().put(asset));
            ReleaseCache.check(context, app, () -> Releases.parse(app, record));
            Releases.Release restored = ReleaseCache.read(context, app).release;
            assertEquals("1.2.3-rc.1", restored.version);
            assertEquals(501, restored.versionCode);
            assertTrue(restored.url.getPath().endsWith("/" + app.canonicalName));
        }
    }

    private Releases.Release release(Releases.App app, int code) throws Exception {
        String name = app == Releases.App.AUTO ? "MM-Auto" : app == Releases.App.IPTV ? "MM-IPTV" : "MM-Tube";
        return new Releases.Release("0.2." + code, code,
                new URL("https://github.com/moustafamagdi/" + app.repo + "/releases/download/v0.2."
                        + code + "/" + name + "-0.2." + code + ".apk"),
                12345, "sha256:" + "a".repeat(64), false);
    }

    private void save(Releases.App app, int code) throws Exception {
        ReleaseCache.check(context, app, () -> release(app, code));
    }

    @Test public void successfulChecksSurviveNewContextAndKeepAllVerificationFields() throws Exception {
        for (Releases.App app : Releases.App.values()) save(app, 50);
        Context recreated = context.createPackageContext(context.getPackageName(), 0);
        for (Releases.App app : Releases.App.values()) {
            ReleaseCache.Snapshot saved = ReleaseCache.read(recreated, app);
            assertNotNull(saved);
            Releases.Release expected = release(app, 50);
            assertEquals(expected.url, saved.release.url);
            assertEquals(expected.version, saved.release.version);
            assertEquals(expected.versionCode, saved.release.versionCode);
            assertEquals(expected.digest, saved.release.digest);
            assertEquals(expected.size, saved.release.size);
            assertEquals(expected.preview, saved.release.preview);
            assertTrue(saved.checkedAt > 0);
        }
    }

    @Test public void failedCheckRetainsResultAndItsOriginalTimestamp() throws Exception {
        save(Releases.App.IPTV, 50);
        ReleaseCache.Snapshot previous = ReleaseCache.read(context, Releases.App.IPTV);
        try {
            ReleaseCache.check(context, Releases.App.IPTV, () -> { throw new java.io.IOException("offline"); });
            fail("Expected network failure");
        } catch (java.io.IOException expected) { }
        ReleaseCache.Snapshot saved = ReleaseCache.read(context, Releases.App.IPTV);
        assertEquals(previous.sequence, saved.sequence);
        assertEquals(previous.checkedAt, saved.checkedAt);
        assertEquals(50, saved.release.versionCode);
    }

    @Test public void successfulEmptyCheckReplacesOnlyItsOwnApp() throws Exception {
        save(Releases.App.IPTV, 50);
        save(Releases.App.TUBE, 50);
        ReleaseCache.check(context, Releases.App.IPTV, () -> null);
        assertNotNull(ReleaseCache.read(context, Releases.App.IPTV));
        assertNull(ReleaseCache.read(context, Releases.App.IPTV).release);
        assertEquals(50, ReleaseCache.read(context, Releases.App.TUBE).release.versionCode);
        assertNull(ReleaseCache.read(context, Releases.App.AUTO));
    }

    @Test public void lateOlderCheckCannotOverwriteNewerSuccessfulCheck() throws Exception {
        long old = ReleaseCache.begin(context), fresh = ReleaseCache.begin(context);
        ReleaseCache.success(context, Releases.App.TUBE, fresh, release(Releases.App.TUBE, 51));
        assertEquals(51, ReleaseCache.success(context, Releases.App.TUBE, old,
                release(Releases.App.TUBE, 50)).release.versionCode);
    }

    @Test public void corruptedAndWrongRepositoryRecordsAreIgnored() throws Exception {
        save(Releases.App.TUBE, 50);
        android.content.SharedPreferences prefs = context.getSharedPreferences("release_checks_v1", 0);
        String saved = prefs.getString("TUBE", "");
        org.json.JSONObject tampered = new org.json.JSONObject(saved);
        org.json.JSONObject asset = tampered.getJSONObject("release").getJSONArray("assets").getJSONObject(0);
        asset.put("browser_download_url", asset.getString("browser_download_url")
                .replace("moustafamagdi/MagdiTube", "attacker/MagdiTube"));
        prefs.edit().putString("TUBE", tampered.toString()).commit();
        assertNull(ReleaseCache.read(context, Releases.App.TUBE));
        prefs.edit().putString("TUBE", "broken").commit();
        assertNull(ReleaseCache.read(context, Releases.App.TUBE));
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    private Object row(MainActivity activity, Releases.App app) throws Exception {
        return ((Map<?, ?>) field(activity, "rows")).get(app);
    }

    private void drain(MainActivity activity) throws Exception {
        ((ExecutorService) field(activity, "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private ActivityController<MainActivity> screen() throws Exception {
        // Restore an in-progress file picker to suppress the unrelated automatic network fetch.
        Bundle restored = new Bundle(); restored.putString("picking_app", "AUTO");
        ActivityController<MainActivity> activity = Robolectric.buildActivity(MainActivity.class)
                .create(restored).start().resume().visible();
        drain(activity.get());
        return activity;
    }

    private void installed(Releases.App app, int code) {
        PackageInfo info = new PackageInfo(); info.packageName = app.packageName;
        info.versionName = "0.2." + code; info.setLongVersionCode(code);
        Shadows.shadowOf(context.getPackageManager()).installPackage(info);
    }

    @Test public void actualButtonsSurviveRecreationAndOnlyInstalledAppLosesOffer() throws Exception {
        for (Releases.App app : Releases.App.values()) { installed(app, 49); save(app, 50); }
        ActivityController<MainActivity> first = screen();
        for (Releases.App app : Releases.App.values()) {
            Button button = (Button) field(row(first.get(), app), "install");
            assertEquals(first.get().getString(R.string.update), button.getText().toString());
            assertTrue(button.isEnabled());
        }
        first.pause().stop().destroy();
        installed(Releases.App.AUTO, 50); // The process starts again after its own update.
        ActivityController<MainActivity> second = screen();
        assertEquals(View.GONE, ((View) field(row(second.get(), Releases.App.AUTO), "card")).getVisibility());
        for (Releases.App app : new Releases.App[] {Releases.App.IPTV, Releases.App.TUBE}) {
            assertEquals(second.get().getString(R.string.update),
                    ((Button) field(row(second.get(), app), "install")).getText().toString());
            assertTrue((Long)field(row(second.get(),app),"checkedAt")>0);
            assertFalse(((TextView)field(row(second.get(),app),"status")).getText().toString().contains("Last successful check:"));
        }
        second.pause(); installed(Releases.App.IPTV, 50); second.resume(); drain(second.get());
        assertEquals(second.get().getString(R.string.open),
                ((Button) field(row(second.get(), Releases.App.IPTV), "install")).getText().toString());
        assertEquals(second.get().getString(R.string.update),
                ((Button) field(row(second.get(), Releases.App.TUBE), "install")).getText().toString());
        second.pause().stop().destroy();
    }

    @Test public void backgroundResultIsLoadedOnResumeWithoutManualCheck() throws Exception {
        installed(Releases.App.TUBE, 49);
        ActivityController<MainActivity> activity = screen();
        activity.pause(); save(Releases.App.TUBE, 50); activity.resume(); drain(activity.get());
        assertEquals(activity.get().getString(R.string.update),
                ((Button) field(row(activity.get(), Releases.App.TUBE), "install")).getText().toString());
        activity.pause().stop().destroy();
    }

    @Test public void scheduledResultRefreshesAnAlreadyOpenScreen() throws Exception {
        installed(Releases.App.TUBE, 49);
        ActivityController<MainActivity> activity = screen();
        save(Releases.App.TUBE, 50);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        drain(activity.get());
        assertEquals(activity.get().getString(R.string.update),
                ((Button) field(row(activity.get(), Releases.App.TUBE), "install")).getText().toString());
        activity.pause().stop().destroy();
    }
}
