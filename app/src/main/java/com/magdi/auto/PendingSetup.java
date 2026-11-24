package com.magdi.auto;

import android.content.Context;
import android.content.SharedPreferences;

/** A user-requested continuation, never authorization to install on resume. */
final class PendingSetup {
    enum Kind { INSTALL, LOCAL, REPAIR, QUEUE }
    final Releases.App app;
    final Kind kind;
    PendingSetup(Releases.App app, Kind kind) { this.app = app; this.kind = kind; }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("setup_task", 0); }
    static void save(Context c, Releases.App app, Kind kind) {
        if (!prefs(c).edit().putString("app", app.name()).putString("kind", kind.name())
                .putLong("at", System.currentTimeMillis()).commit())
            throw new IllegalStateException("Could not save setup task");
    }
    static PendingSetup read(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("app")) return null;
        try {
            long age = System.currentTimeMillis() - p.getLong("at", 0);
            if (age < 0 || age > 24 * 60 * 60_000L) throw new IllegalArgumentException();
            return new PendingSetup(Releases.App.valueOf(p.getString("app", "")),
                    Kind.valueOf(p.getString("kind", "")));
        } catch (IllegalArgumentException invalid) { clear(c); return null; }
    }
    static void clear(Context c) { prefs(c).edit().clear().commit(); }
}
