package com.magdi.auto;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

/** Settings owns scrolling/highlighting. OEMs may ignore these optional preference hints. */
final class WirelessDebuggingSettings {
    static final String KEY = ":settings:fragment_args_key";
    static final String WIRELESS = "toggle_adb_wireless";
    static Intent developerOptions() {
        Bundle args = new Bundle(); args.putString(KEY, WIRELESS);
        return new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(KEY, WIRELESS).putExtra(":settings:show_fragment_args", args);
    }
    static boolean open(Activity activity) {
        // Some OEMs export this direct action; it is not universally available.
        try {
            activity.startActivity(new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
                    .putExtra(KEY, WIRELESS));
            return true;
        } catch (RuntimeException unsupported) {
            try { activity.startActivity(developerOptions()); return true; }
            catch (RuntimeException unavailable) { return false; }
        }
    }
    private WirelessDebuggingSettings() {}
}
