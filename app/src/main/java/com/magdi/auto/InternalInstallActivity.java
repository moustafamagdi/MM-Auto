package com.magdi.auto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.Locale;

/** Explicit preview journey; pairing credentials never saved in view state, logs or diagnostics. */
public final class InternalInstallActivity extends Activity {
    private Releases.App app;
    private LinearLayout body;
    private final Handler ui = new Handler(android.os.Looper.getMainLooper());
    private String shown = "";
    private boolean visible;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!visible || isDestroyed()) return;
            String state = InternalInstallOperation.state(InternalInstallActivity.this) + InternalInstallOperation.busy();
            if (!state.equals(shown)) { shown = state; render(); }
            ui.postDelayed(this, 400);
        }
    };
    @Override protected void attachBaseContext(Context base) {
        Configuration c = new Configuration(base.getResources().getConfiguration());
        String language = base.getSharedPreferences("ui", 0).getString("language", "system");
        if (!"system".equals(language)) { Locale l = Locale.forLanguageTag(language); c.setLocale(l); c.setLayoutDirection(l); }
        String theme = base.getSharedPreferences("ui", 0).getString("theme", "system");
        if (!"system".equals(theme)) c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | ("dark".equals(theme) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(c));
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!BuildConfig.VERSION_NAME.contains("preview")) { finish(); return; }
        try {
            app = InternalInstallOperation.busy() ? InternalInstallOperation.activeApp()
                    : InternalAdbPolicy.target(getIntent().getStringExtra("app"));
        } catch (IllegalArgumentException invalid) { finish(); return; }
        InternalInstallOperation.select(this, app);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiStyle.dark(this) ? 0xff0b1220 : 0xfff3f6fc);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiStyle.dp(this, 20), UiStyle.dp(this, 20), UiStyle.dp(this, 20), UiStyle.dp(this, 20));
        body.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        scroll.addView(body); setContentView(scroll); UiStyle.systemBars(this);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return android.view.WindowInsets.CONSUMED;
        });
        render();
        shown = InternalInstallOperation.state(this) + InternalInstallOperation.busy();
    }
    @Override protected void onResume() { super.onResume(); visible = true; ui.post(refresh); }
    @Override protected void onPause() { visible = false; ui.removeCallbacks(refresh); super.onPause(); }
    @Override public void onBackPressed() {
        if (InternalInstallOperation.busy()) Toast.makeText(this, R.string.internal_wait, Toast.LENGTH_SHORT).show();
        else super.onBackPressed();
    }
    private void render() {
        body.removeAllViews();
        text(getString(R.string.internal_adb_title), 24); text(app.title, 20);
        String state = InternalInstallOperation.state(this);
        if (InternalInstallOperation.busy()) {
            ProgressBar progress = new ProgressBar(this); body.addView(progress);
            int message = state.equals("pairing") ? R.string.internal_pairing : state.equals("connecting")
                    ? R.string.internal_connecting : state.equals("installing") ? R.string.internal_installing : R.string.internal_preparing;
            text(getString(message), 18); text(getString(R.string.internal_wait), 15);
            if (!state.equals("installing")) button(R.string.cancel, false, () -> InternalInstallOperation.cancel(this));
            return;
        }
        if (state.equals("finished")) {
            text(getString(R.string.installed_check_car), 19);
            if (!InternalInstallOperation.prefs(this).getBoolean("installer_match", false)) text(getString(R.string.internal_source_unconfirmed), 16);
            text(getString(R.string.internal_turn_off), 16);
            button(R.string.close, true, this::finish);
            button(R.string.internal_new_attempt, false, () -> change("paired"));
            settings(); details(); return;
        }
        if (state.equals("failed") || state.equals("interrupted")) {
            text(getString(state.equals("interrupted") ? R.string.internal_interrupted : R.string.internal_failed), 17);
            button(R.string.internal_retry, true, () -> change("setup")); details(); button(R.string.close, false, this::finish); return;
        }
        if (state.equals("ready")) {
            boolean installed = Releases.installed(getPackageManager(), app) != null;
            text(getString(installed ? R.string.internal_confirm_repair : R.string.internal_confirm_new, app.title), 17);
            text(getString(R.string.internal_permission_note), 15);
            Button install = UiStyle.button(this, getString(R.string.internal_install_app, app.title), true);
            install.setOnClickListener(v -> { install.setEnabled(false); InternalInstallOperation.install(this, app); shown = ""; });
            body.addView(install, UiStyle.actionParams(this));
            button(R.string.internal_connection_step, false, () -> change("paired"));
            button(R.string.close, false, this::finish); return;
        }
        boolean paired = state.equals("paired") || InternalInstallOperation.prefs(this).getBoolean("paired", false);
        if (paired && !state.equals("pair_again")) {
            text(getString(R.string.internal_connect_help), 17); settings();
            EditText port = input(R.string.internal_connection_port, false);
            button(R.string.internal_check_connection, true, () -> {
                try { InternalInstallOperation.connect(this, app, InternalAdbPolicy.optionalPort(port.getText().toString().trim())); shown = ""; }
                catch (IllegalArgumentException invalid) { port.setError(getString(R.string.internal_invalid_port)); }
            });
            button(R.string.internal_pair_again, false, () -> change("pair_again"));
        } else {
            text(getString(R.string.internal_intro), 17);
            text(getString(R.string.internal_pair_help), 16); settings();
            EditText port = input(R.string.internal_pair_port, false), code = input(R.string.internal_pair_code, true);
            button(R.string.internal_pair, true, () -> {
                int number;
                try { number = InternalAdbPolicy.optionalPort(port.getText().toString().trim()); }
                catch (IllegalArgumentException invalid) { port.setError(getString(R.string.internal_invalid_port)); return; }
                try {
                    String secret = InternalAdbPolicy.code(code.getText().toString().trim());
                    code.setText(""); InternalInstallOperation.pair(this, app, number, secret); shown = "";
                } catch (IllegalArgumentException invalid) { code.setError(getString(R.string.internal_invalid_code)); }
            });
        }
        text(getString(R.string.internal_restart_note), 14); button(R.string.close, false, this::finish);
        button(R.string.internal_licenses, false, () -> {
            try (java.io.InputStream input = getAssets().open("internal-adb-notices.txt")) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                TextView text = new TextView(this); text.setText(output.toString("UTF-8")); text.setTextIsSelectable(true);
                ScrollView scroll = new ScrollView(this); scroll.addView(text);
                new AlertDialog.Builder(this).setTitle(R.string.internal_licenses).setView(scroll).setPositiveButton(R.string.close, null).show();
            } catch (java.io.IOException unavailable) { Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show(); }
        });
    }
    private void settings() {
        button(R.string.internal_settings, false, () -> {
            try { startActivity(new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")); }
            catch (RuntimeException absent) {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
                catch (RuntimeException unavailable) { Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show(); }
            }
        });
    }
    private void details() {
        button(R.string.problem_details, false, () -> new AlertDialog.Builder(this).setTitle(R.string.problem_details)
                .setMessage(InternalInstallOperation.prefs(this).getString("detail", "") + "\n\n"
                        + com.magdi.auto.install.InstallManager.lastAttempt(this, app.packageName))
                .setPositiveButton(R.string.close, null).show());
    }
    private void change(String state) { InternalInstallOperation.prefs(this).edit().putString("state", state).commit(); shown = ""; render(); }
    private void text(String label, int size) {
        TextView view = new TextView(this); view.setText(label); view.setTextSize(size);
        view.setTextColor(UiStyle.dark(this) ? 0xffdce9ff : 0xff152b48);
        view.setPadding(0, UiStyle.dp(this, 8), 0, UiStyle.dp(this, 8)); body.addView(view);
    }
    private EditText input(int label, boolean secret) {
        text(getString(label), 15);
        EditText edit = new EditText(this); edit.setSingleLine(true); edit.setMinHeight(UiStyle.dp(this, 52));
        edit.setTextDirection(View.TEXT_DIRECTION_LTR); edit.setInputType(InputType.TYPE_CLASS_NUMBER
                | (secret ? InputType.TYPE_NUMBER_VARIATION_PASSWORD : 0));
        edit.setContentDescription(getString(label)); edit.setSaveEnabled(false);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(secret ? 6 : 5)});
        body.addView(edit); return edit;
    }
    private void button(int label, boolean primary, Runnable action) {
        Button button = UiStyle.button(this, getString(label), primary);
        button.setOnClickListener(v -> action.run()); body.addView(button, UiStyle.actionParams(this));
    }
}
