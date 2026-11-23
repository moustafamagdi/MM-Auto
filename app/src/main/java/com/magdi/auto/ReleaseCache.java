package com.magdi.auto;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Last successful checks, shared by the foreground UI and scheduled checker.
 * Call from a worker: commits finish before a self-update can terminate this process. */
final class ReleaseCache {
    private static final String PREFS = "release_checks_v1";

    interface Lookup { Releases.Release fetch() throws Exception; }

    static Snapshot check(Context context, Releases.App app, Lookup lookup) throws Exception {
        long sequence = begin(context);
        Releases.Release release = lookup.fetch();
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        // Failed and interrupted checks never write a successful result or erase the old one.
        return success(context, app, sequence, release);
    }

    static final class Snapshot {
        final Releases.Release release;
        final long checkedAt;
        final long sequence;

        Snapshot(Releases.Release release, long checkedAt, long sequence) {
            this.release = release;
            this.checkedAt = checkedAt;
            this.sequence = sequence;
        }
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized long begin(Context context) {
        SharedPreferences prefs = preferences(context);
        long sequence = Math.max(System.currentTimeMillis(), prefs.getLong("sequence", 0) + 1);
        prefs.edit().putLong("sequence", sequence).commit();
        return sequence;
    }

    static synchronized Snapshot read(Context context, Releases.App app) {
        try {
            String saved = preferences(context).getString(app.name(), null);
            if (saved == null || saved.length() > 16384) return null;
            JSONObject data = new JSONObject(saved);
            long checkedAt = data.getLong("checkedAt"), sequence = data.getLong("sequence");
            if (checkedAt <= 0 || sequence <= 0) return null;
            Releases.Release release = null;
            if (!data.isNull("release")) {
                // Reuse the normal repository/host/asset/size/checksum validation on every read.
                release = Releases.parse(app, data.getJSONObject("release"));
                if (release == null) return null;
            }
            return new Snapshot(release, checkedAt, sequence);
        } catch (Exception invalidCache) {
            return null;
        }
    }

    static synchronized Snapshot success(Context context, Releases.App app, long sequence,
                                         Releases.Release release) throws Exception {
        Snapshot previous = read(context, app);
        // An older foreground/background request must not replace a newer successful check.
        if (previous != null && previous.sequence > sequence) return previous;
        JSONObject data = new JSONObject().put("sequence", sequence)
                .put("checkedAt", System.currentTimeMillis()).put("release", JSONObject.NULL);
        if (release != null) {
            String path = release.url.getPath();
            JSONObject asset = new JSONObject().put("name", path.substring(path.lastIndexOf('/') + 1))
                    .put("state", "uploaded").put("browser_download_url", release.url.toString())
                    .put("size", release.size).put("digest", release.digest)
                    .put("label", "versionCode=" + release.versionCode);
            JSONObject record = new JSONObject().put("tag_name", release.version).put("prerelease", release.preview)
                    .put("assets", new JSONArray().put(asset));
            Releases.Release validated = Releases.parse(app, record);
            if (validated == null || !validated.version.equals(release.version))
                throw new java.io.IOException("Invalid cached release.");
            data.put("release", record);
        }
        if (!preferences(context).edit().putString(app.name(), data.toString()).commit())
            throw new java.io.IOException("Could not save release check.");
        return read(context, app);
    }

    private ReleaseCache() {}
}
