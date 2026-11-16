package com.magdi.auto;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

import com.magdi.auto.install.InstallDiagnostics;
import com.magdi.auto.install.InstallManager;
import com.magdi.auto.install.InstallMode;
import com.magdi.auto.install.ShizukuChannel;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_UPDATE_ALERTS = 24;
    private static final String ASKED_FOR_ALERTS = "asked_for_alerts";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final EnumMap<Releases.App, Row> rows = new EnumMap<>(Releases.App.class);
    private Button check;
    private Switch updateAlerts;
    private TextView methodStatus;
    private boolean busy;

    private static final class Row {
        TextView status;
        Button install;
        Releases.Release release;
        String error;
        String note;
        boolean checked;
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff0b1324);
        LinearLayout page = column();
        page.setPadding(dp(22), dp(18), dp(22), dp(24));
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });

        page.addView(text("MM Auto", 34, Color.WHITE, true));
        TextView subtitle = text("Manage your apps", 16, 0xffaec2e2, false);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        page.addView(subtitle);

        addRow(page, Releases.App.AUTO, R.drawable.ic_auto);
        addRow(page, Releases.App.IPTV, R.drawable.ic_iptv);
        addRow(page, Releases.App.TUBE, R.drawable.ic_tube);

        addInstallMethod(page);

        check = new Button(this);
        check.setText("Check for updates");
        check.setAllCaps(false);
        check.setOnClickListener(view -> checkReleases());
        page.addView(check, new LinearLayout.LayoutParams(-1, dp(52)));
        updateAlerts = new Switch(this);
        updateAlerts.setText("Daily update alerts");
        updateAlerts.setTextColor(Color.WHITE);
        updateAlerts.setMinHeight(dp(48));
        updateAlerts.setOnClickListener(view -> changeUpdateAlerts());
        page.addView(updateAlerts, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
        ShizukuChannel.listen(granted -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            refreshMethodStatus();
            if (granted) Toast.makeText(this, "Shizuku allowed. Tap Install again.", Toast.LENGTH_SHORT).show();
        }));
        checkReleases();
    }

    @Override protected void onResume() {
        super.onResume();
        UpdateAlerts.syncSchedule(this);
        refreshAlertsSwitch();
        refreshMethodStatus();
        checkPendingInstalls();
        if (!busy) for (Releases.App app : rows.keySet()) render(app);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!busy) checkReleases();
    }

    private void changeUpdateAlerts() {
        if (!updateAlerts.isChecked() || UpdateAlerts.canNotify(this)) {
            setUpdateAlerts(updateAlerts.isChecked());
            return;
        }
        refreshAlertsSwitch();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
                && (!getPreferences(MODE_PRIVATE).getBoolean(ASKED_FOR_ALERTS, false)
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            getPreferences(MODE_PRIVATE).edit().putBoolean(ASKED_FOR_ALERTS, true).apply();
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_UPDATE_ALERTS);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_UPDATE_ALERTS && UpdateAlerts.canNotify(this)) setUpdateAlerts(true);
    }

    private void setUpdateAlerts(boolean enabled) {
        if (!UpdateAlerts.setEnabled(this, enabled)) {
            Toast.makeText(this, "Could not schedule update alerts. Try again.", Toast.LENGTH_SHORT).show();
        }
        refreshAlertsSwitch();
    }

    private void refreshAlertsSwitch() {
        updateAlerts.setChecked(UpdateAlerts.enabled(this) && UpdateAlerts.canNotify(this));
    }

    @Override protected void onDestroy() {
        ShizukuChannel.unlisten();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void addRow(LinearLayout page, Releases.App app, int icon) {
        Row row = new Row();
        rows.put(app, row);
        LinearLayout card = column();
        card.setPadding(dp(14), dp(14), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff172742);
        background.setCornerRadius(dp(16));
        card.setBackground(background);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        header.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = column();
        labels.setPaddingRelative(dp(12), 0, 0, 0);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(text(app.title, 21, Color.WHITE, true));
        row.status = text("", 14, 0xffb9c9e2, false);
        row.status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        labels.addView(row.status);
        card.addView(header);

        row.install = new Button(this);
        row.install.setAllCaps(false);
        row.install.setOnClickListener(view -> install(app));
        card.addView(row.install, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        page.addView(card, cardParams);
        render(app);
    }

    private void checkReleases() {
        setBusy(true);
        for (Row row : rows.values()) {
            row.note = null;
            row.status.setText("Checking GitHub…");
        }
        worker.execute(() -> {
            for (Releases.App app : Releases.App.values()) {
                Releases.Release release = null;
                String error = null;
                try {
                    release = Releases.latest(app);
                } catch (Exception failure) {
                    error = "Could not check GitHub. Try again.";
                }
                Releases.Release result = release;
                String message = error;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    Row row = rows.get(app);
                    row.release = result;
                    row.error = message;
                    row.checked = true;
                    render(app);
                });
            }
            runOnUiThread(() -> {
                if (!isDestroyed()) setBusy(false);
            });
        });
    }

    private void install(Releases.App app) {
        Row row = rows.get(app);
        Releases.Release release = row.release;
        if (busy || release == null || !release.isNewer(Releases.installed(getPackageManager(), app))) return;
        InstallMode mode = InstallManager.mode(this);
        row.note = null;
        if (mode == InstallMode.CLASSIC && !getPackageManager().canRequestPackageInstalls()) {
            row.status.setText("Allow installs from MM Auto, then tap again.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException unavailable) {
                row.status.setText("Android installation settings are unavailable.");
            }
            return;
        }
        if (mode == InstallMode.SHIZUKU && !ShizukuChannel.granted()) {
            if (!ShizukuChannel.running()) {
                row.status.setText("Start Shizuku first, then tap again.");
            } else if (ShizukuChannel.blocked()) {
                row.status.setText("Shizuku permission was denied. Allow MM Auto inside Shizuku.");
            } else {
                ShizukuChannel.request();
            }
            return;
        }

        setBusy(true);
        row.status.setText("Downloading and verifying the APK…");
        worker.execute(() -> {
            File apk;
            try {
                apk = Releases.download(app, release, getCacheDir(), getPackageManager());
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setBusy(false);
                    row.status.setText("Could not prepare the APK. " +
                            (failure.getMessage() == null ? "Try again." : failure.getMessage()));
                });
                return;
            }
            if (mode == InstallMode.CLASSIC) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setBusy(false);
                    try {
                        InstallManager.markPending(this, app.packageName);
                        startActivity(InstallManager.classicIntent(ApkProvider.uri(getPackageName(), app), app.title));
                    } catch (RuntimeException unavailable) {
                        InstallManager.clearPending(this, app.packageName);
                        row.status.setText("Could not open the Android installer.");
                    }
                });
                return;
            }
            InstallManager.Outcome outcome = InstallManager.installPrivileged(this, mode, apk, app.packageName,
                    message -> runOnUiThread(() -> {
                        if (!isDestroyed()) row.status.setText(message);
                    }));
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                row.note = outcome.message;
                setBusy(false);
            });
        });
    }

    /** After a Classic install, check the install source and repair it if Shizuku or Root can. */
    private void checkPendingInstalls() {
        InstallMode mode = InstallManager.mode(this);
        for (Releases.App app : Releases.App.values()) {
            if (!InstallManager.takeFinishedPending(this, app.packageName)) continue;
            Row row = rows.get(app);
            worker.execute(() -> {
                InstallManager.Outcome outcome = InstallManager.verifyAndFix(
                        this, mode, app.packageName, message -> { }, "");
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    row.note = outcome.message;
                    render(app);
                });
            });
        }
    }

    private void addInstallMethod(LinearLayout page) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(14), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff172742);
        background.setCornerRadius(dp(16));
        card.setBackground(background);
        card.addView(text("Install method", 18, Color.WHITE, true));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        addModeButton(group, InstallMode.CLASSIC, "Classic (try this first)");
        addModeButton(group, InstallMode.SHIZUKU, "Shizuku");
        addModeButton(group, InstallMode.ROOT, "Root");
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton button = (RadioButton) group.getChildAt(i);
            if (button.getTag() == InstallManager.mode(this)) group.check(button.getId());
        }
        group.setOnCheckedChangeListener((view, id) -> {
            View chosen = view.findViewById(id);
            if (chosen == null) return;
            InstallManager.setMode(this, (InstallMode) chosen.getTag());
            refreshMethodStatus();
        });
        card.addView(group);

        methodStatus = text("", 14, 0xffb9c9e2, false);
        methodStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(methodStatus);

        Button diagnostics = new Button(this);
        diagnostics.setText("Diagnostics");
        diagnostics.setAllCaps(false);
        diagnostics.setOnClickListener(view -> showDiagnostics());
        card.addView(diagnostics, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        page.addView(card, params);
        refreshMethodStatus();
    }

    private void addModeButton(RadioGroup group, InstallMode mode, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setTag(mode);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setMinHeight(dp(48));
        group.addView(button);
    }

    private void refreshMethodStatus() {
        if (methodStatus == null) return;
        InstallMode mode = InstallManager.mode(this);
        String shizuku = !ShizukuChannel.running() ? "Shizuku is not running."
                : ShizukuChannel.granted() ? "Shizuku is ready."
                : "Shizuku needs permission. It is requested when you tap Install.";
        if (mode == InstallMode.CLASSIC) {
            methodStatus.setText("Opens Android's installer, then repairs the install source if possible. " + shizuku);
        } else if (mode == InstallMode.SHIZUKU) {
            methodStatus.setText("Installs silently with the Play Store as installer. " + shizuku);
        } else {
            methodStatus.setText("Installs silently with su. Android or your root manager may ask you to allow it.");
        }
    }

    private void showDiagnostics() {
        Map<String, String> apps = new LinkedHashMap<>();
        for (Releases.App app : Releases.App.values()) apps.put(app.title, app.packageName);
        String report = InstallDiagnostics.report(this, apps);
        new AlertDialog.Builder(this)
                .setTitle("Diagnostics")
                .setMessage(report)
                .setPositiveButton("Share", (dialog, which) -> {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, report);
                    startActivity(Intent.createChooser(send, "Share diagnostics"));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void setBusy(boolean working) {
        busy = working;
        check.setEnabled(!working);
        for (Releases.App app : rows.keySet()) render(app);
    }

    private void render(Releases.App app) {
        Row row = rows.get(app);
        PackageInfo current = Releases.installed(getPackageManager(), app);
        Releases.Release release = row.release;
        boolean available = release != null && release.isNewer(current);
        String installed = current == null ? "Not installed" : "Installed " + current.versionName;
        String detail = row.error;
        if (detail == null && row.checked) {
            detail = release == null ? "No compatible APK in recent GitHub releases"
                    : available ? "Available " + release.version + (release.preview ? " (preview)" : "") : "Up to date";
        }
        String line = detail == null ? installed : installed + "\n" + detail;
        row.status.setText(row.note == null ? line : line + "\n" + row.note);
        row.install.setVisibility(available ? View.VISIBLE : View.GONE);
        row.install.setEnabled(available && !busy);
        row.install.setText(current == null ? "Install" : "Update");
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String label, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }
}
