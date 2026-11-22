package com.magdi.auto;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import java.io.InputStream;
import java.util.Locale;
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
import com.magdi.auto.install.InstallIdentity;
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
    private int backgroundColor, surfaceColor, primaryColor, inkColor, mutedColor;
    private LinearLayout selfBanner;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean cancelPreparation;
    private Releases.App pickingApp;
    private int preparationGeneration;
    private static final int PICK_APK = 42;
    private Button settingsButton;
    private AlertDialog localConfirmation;
    private boolean resumed;

    @Override protected void attachBaseContext(Context base) {
        android.content.SharedPreferences prefs = base.getSharedPreferences("ui", MODE_PRIVATE);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        String language = prefs.getString("language", "system");
        if (!"system".equals(language)) {
            Locale locale = Locale.forLanguageTag(language);
            config.setLocale(locale);
            config.setLayoutDirection(locale);
        }
        String theme = prefs.getString("theme", "system");
        if (!"system".equals(theme)) config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | ("dark".equals(theme) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    private static final class Row {
        TextView status;
        Button install;
        Button repair;
        Releases.Release release;
        String error;
        String note;
        boolean checked;
        LinearLayout card;
        TextView version;
        Button manual;
        ProgressBar progress;
        Button cancel;
        Button more;
        String operation;
        Boolean attributionMatch;
        int percent = -1;
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        backgroundColor = dark ? 0xff0b1220 : 0xfff3f6fc;
        surfaceColor = dark ? 0xff172235 : Color.WHITE;
        primaryColor = dark ? 0xff8bb4ff : 0xff2563eb;
        inkColor = dark ? 0xffeef3fc : 0xff14233b;
        mutedColor = dark ? 0xffb4c1d6 : 0xff52627c;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor);
        LinearLayout page = column();
        page.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("MM Auto", 26, inkColor, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        settingsButton = action(getString(R.string.settings), false);
        settingsButton.setOnClickListener(v -> showSettings());
        header.addView(settingsButton, new LinearLayout.LayoutParams(-2, dp(48)));
        page.addView(header);
        TextView subtitle = text(getString(R.string.app_subtitle), 16, mutedColor, false);
        subtitle.setPadding(0, dp(6), 0, dp(24));
        page.addView(subtitle);

        // Keep the self-update row in memory, but show its compact card only when needed.
        selfBanner = column();
        page.addView(selfBanner);
        addRow(selfBanner, Releases.App.AUTO, R.drawable.ic_auto);
        TextView section = text(getString(R.string.your_apps), 18, inkColor, true);
        section.setPadding(0, 0, 0, dp(12));
        page.addView(section);
        addRow(page, Releases.App.IPTV, R.drawable.ic_iptv);
        addRow(page, Releases.App.TUBE, R.drawable.ic_tube);
        check = action(getString(R.string.check_updates), false);
        check.setOnClickListener(v -> { if (!busy) checkReleases(); });
        page.addView(check, new LinearLayout.LayoutParams(-1, dp(52)));
        Button share = action(getString(R.string.share_app), false);
        share.setOnClickListener(v -> shareApp());
        page.addView(share, new LinearLayout.LayoutParams(-1, dp(52)));
        Button contact = action(getString(R.string.contact_developer), false);
        contact.setOnClickListener(v -> safeStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/966597197475"))));
        page.addView(contact, new LinearLayout.LayoutParams(-1, dp(52)));
        setContentView(scroll);
        if (savedState != null) {
            String selected = savedState.getString("picking_app");
            if (selected != null) pickingApp = Releases.App.valueOf(selected);
        }
        ShizukuChannel.listen(granted -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            refreshMethodStatus();
            if (granted) {
                Toast.makeText(this, R.string.permission_ready, Toast.LENGTH_SHORT).show();
                resumePendingLocal();
            }
        }));
        if (pickingApp == null && pendingLocalApp() == null && !InstallManager.hasPending(this)) checkReleases();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (pickingApp != null) state.putString("picking_app", pickingApp.name());
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        UpdateAlerts.syncSchedule(this);
        refreshAlertsSwitch();
        refreshMethodStatus();
        checkPendingInstalls();
        checkRepairPending();
        if (!busy) for (Releases.App app : rows.keySet()) render(app);
        resumePendingLocal();
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
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
            Toast.makeText(this, getString(R.string.alerts_failed), Toast.LENGTH_SHORT).show();
        }
        refreshAlertsSwitch();
    }

    private void refreshAlertsSwitch() {
        if (updateAlerts == null) return;
        updateAlerts.setChecked(UpdateAlerts.enabled(this) && UpdateAlerts.canNotify(this));
    }

    @Override protected void onDestroy() {
        cancelPreparation = true;
        ui.removeCallbacksAndMessages(null);
        ShizukuChannel.unlisten();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void addRow(LinearLayout page, Releases.App app, int icon) {
        Row row = new Row();
        rows.put(app, row);
        LinearLayout card = column();
        row.card = card;
        card.setPadding(dp(18), dp(18), dp(18), dp(14));
        card.setBackground(shape(surfaceColor, 22));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = column();
        labels.setPaddingRelative(dp(12), 0, dp(4), 0);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(text(app.title, app == Releases.App.AUTO ? 18 : 21, inkColor, true));
        row.version = text("", 13, mutedColor, false);
        labels.addView(row.version);
        row.more = action("⋮", false);
        row.more.setTextSize(24);
        row.more.setContentDescription(getString(R.string.more_options, app.title));
        row.more.setOnClickListener(v -> showAppMenu(app, v));
        header.addView(row.more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(header);
        row.status = text("", 14, mutedColor, false);
        row.status.setPadding(0, dp(12), 0, dp(8));
        row.status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(row.status);
        row.progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        row.progress.setProgressTintList(ColorStateList.valueOf(primaryColor));
        row.progress.setVisibility(View.GONE);
        card.addView(row.progress, new LinearLayout.LayoutParams(-1, dp(6)));
        row.install = action("", true);
        row.install.setOnClickListener(v -> primaryAction(app));
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(-1, dp(52));
        mainParams.topMargin = dp(8);
        card.addView(row.install, mainParams);
        row.manual = action(getString(R.string.manual_download), false);
        row.manual.setOnClickListener(v -> showManual(app));
        card.addView(row.manual, new LinearLayout.LayoutParams(-1, dp(48)));
        row.cancel = action(getString(R.string.cancel_download), false);
        row.cancel.setOnClickListener(v -> {
            cancelPreparation = true;
            row.operation = getString(R.string.cancelling);
            row.cancel.setEnabled(false);
            render(app);
        });
        row.cancel.setVisibility(View.GONE);
        card.addView(row.cancel, new LinearLayout.LayoutParams(-1, dp(48)));
        // Repair stays reachable through Help, with its existing implementation unchanged.
        row.repair = action(getString(R.string.troubleshoot), false);
        row.repair.setOnClickListener(v -> showHelp(app));
        row.repair.setVisibility(View.GONE);
        card.addView(row.repair, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        page.addView(card, params);
        render(app);
    }

    private void primaryAction(Releases.App app) {
        if (busy) return;
        if (pendingLocalApp() == app) {
            installLocal(app, localApk(app));
            return;
        }
        Row row = rows.get(app);
        PackageInfo current = Releases.installed(getPackageManager(), app);
        if (row.release != null && row.release.isNewer(current)) { install(app); return; }
        if (current == null) { showManual(app); return; }
        if (app == Releases.App.AUTO) { showVersion(app); return; }
        Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launch != null) safeStart(launch);
        else Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
    }

    private void showAppMenu(Releases.App app, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.release_details).setOnMenuItemClickListener(item -> { showVersion(app); return true; });
        menu.getMenu().add(R.string.open_releases).setOnMenuItemClickListener(item -> { openReleases(app); return true; });
        menu.getMenu().add(R.string.choose_apk).setEnabled(!busy)
                .setOnMenuItemClickListener(item -> { chooseApk(app); return true; });
        menu.getMenu().add(R.string.troubleshoot).setEnabled(!busy)
                .setOnMenuItemClickListener(item -> { showHelp(app); return true; });
        menu.show();
    }

    private void showManual(Releases.App app) {
        new AlertDialog.Builder(this).setTitle(app.title).setMessage(R.string.manual_instructions)
                .setPositiveButton(R.string.open_releases, (d, w) -> openReleases(app))
                .setNeutralButton(R.string.choose_apk, (d, w) -> chooseApk(app))
                .setNegativeButton(R.string.close, null).show();
    }

    private void openReleases(Releases.App app) {
        safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/moustafamagdi/" + app.repo + "/releases")));
    }

    private void safeStart(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException error) { Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show(); }
    }

    private void showVersion(Releases.App app) {
        Row row = rows.get(app);
        PackageInfo installed = Releases.installed(getPackageManager(), app);
        String description = installed == null ? getString(R.string.not_installed)
                : getString(R.string.installed_version, installed.versionName);
        if (row.release != null) description += "\n" + getString(R.string.available_version, row.release.version);
        if (row.release != null && row.release.preview) description += " · " + getString(R.string.preview);
        new AlertDialog.Builder(this).setTitle(app.title).setMessage(description)
                .setPositiveButton(R.string.whats_new, (d, w) -> {
                    if (row.release == null) { openReleases(app); return; }
                    // The asset URL already passed the existing repository and HTTPS checks.
                    String path = row.release.url.getPath();
                    int end = path.lastIndexOf('/');
                    safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"
                            + path.substring(0, end).replace("/releases/download/", "/releases/tag/"))));
                })
                .setNeutralButton(R.string.choose_apk, (d, w) -> chooseApk(app))
                .setNegativeButton(R.string.close, null).show();
    }

    private void showHelp(Releases.App app) {
        Row row = rows.get(app);
        String description = getString(R.string.help_description);
        if (row.note != null) description += "\n\n" + row.note;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(R.string.troubleshoot)
                .setMessage(description).setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.diagnostics, (d, w) -> showDiagnostics());
        if (Releases.installed(getPackageManager(), app) != null)
            dialog.setPositiveButton(R.string.repair_action, (d, w) -> repair(app));
        dialog.show();
    }

    private void repair(Releases.App app) {
        if (busy || Releases.installed(getPackageManager(), app) == null) return;
        if (RepairState.needsShizuku(this, app.packageName)) {
            offerShizukuRepair(app);
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            rows.get(app).note = getString(R.string.repair_unknown_apps);
            render(app);
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException unavailable) {
                rows.get(app).note = getString(R.string.repair_failed, unavailable.getMessage());
                render(app);
            }
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.repair_source)
                .setMessage(getString(R.string.repair_classic_confirm, app.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.repair_source, (dialog, which) -> runClassicRepair(app)).show();
    }

    private void runClassicRepair(Releases.App app) {
        if (busy) return;
        setBusy(true);
        rows.get(app).status.setText(R.string.repair_verifying);
        worker.execute(() -> {
            File verified = null;
            try {
                verified = RepairApk.prepare(this, app);
                File directory = new File(getCacheDir(), "repairs");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Repair storage unavailable.");
                File target = new File(directory, app.packageName + ".apk");
                if (!verified.renameTo(target)) throw new java.io.IOException("Could not save verified repair APK.");
                runOnUiThread(() -> {
                    if (isDestroyed()) { target.delete(); return; }
                    setBusy(false);
                    try {
                        RepairState.mark(this, app.packageName);
                        startActivity(InstallManager.classicIntent(ApkProvider.repairUri(getPackageName(), app), app.title));
                    } catch (Exception failure) {
                        RepairState.clear(this, app.packageName);
                        target.delete();
                        rows.get(app).note = getString(R.string.repair_failed, failure.getMessage());
                        render(app);
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    rows.get(app).note = getString(R.string.repair_failed, failure.getMessage());
                    setBusy(false);
                });
            } finally {
                if (verified != null) verified.delete();
            }
        });
    }

    private void checkRepairPending() {
        for (Releases.App app : Releases.App.values()) {
            if (!RepairState.takeFinished(this, app.packageName)) continue;
            new File(new File(getCacheDir(), "repairs"), app.packageName + ".apk").delete();
            boolean ready = InstallIdentity.read(getPackageManager(), app.packageName).matchesExpectedPair(getPackageManager());
            RepairState.needsShizuku(this, app.packageName, !ready);
            rows.get(app).note = getString(ready ? R.string.repair_classic_success : R.string.repair_shizuku_needed)
                    + "\n" + InstallIdentity.read(getPackageManager(), app.packageName).describe();
            render(app);
            if (!ready) offerShizukuRepair(app);
        }
    }

    private void offerShizukuRepair(Releases.App app) {
        new AlertDialog.Builder(this).setTitle(R.string.repair_source)
                .setMessage(R.string.repair_shizuku_needed)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.repair_retry_classic, (dialog, which) -> {
                    RepairState.needsShizuku(this, app.packageName, false);
                    repair(app);
                })
                .setPositiveButton(R.string.repair_use_shizuku, (dialog, which) -> requestShizukuRepair(app)).show();
    }

    private void requestShizukuRepair(Releases.App app) {
        if (busy) return;
        Row row = rows.get(app);
        if (!ShizukuChannel.granted()) {
            if (!ShizukuChannel.running()) row.note = getString(R.string.repair_start_shizuku);
            else if (ShizukuChannel.blocked()) row.note = getString(R.string.repair_allow_shizuku);
            else ShizukuChannel.request();
            render(app);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.repair_source)
                .setMessage(getString(R.string.repair_confirm, app.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.repair_source, (dialog, which) -> runRepair(app))
                .show();
    }

    private void runRepair(Releases.App app) {
        if (busy) return;
        setBusy(true);
        rows.get(app).status.setText(R.string.repair_verifying);
        worker.execute(() -> {
            File temporary = null;
            File target = new File(new File(getCacheDir(), "repairs"), app.packageName + ".apk");
            try {
                temporary = RepairApk.prepare(this, app);
                if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
                    throw new java.io.IOException("Repair storage unavailable.");
                if (!temporary.renameTo(target)) throw new java.io.IOException("Could not save verified repair APK.");
                runOnUiThread(() -> {
                    if (isDestroyed()) { target.delete(); return; }
                    openShizukuInstaller(app, ApkProvider.repairUri(getPackageName(), app), true);
                });
            } catch (Exception failure) {
                target.delete();
                String message = getString(R.string.repair_failed, failure.getMessage());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    rows.get(app).note = message;
                    setBusy(false);
                });
            } finally {
                if (temporary != null) temporary.delete();
            }
        });
    }

    private void openShizukuInstaller(Releases.App app, Uri uri, boolean repair) {
        setBusy(true);
        preparationGeneration++;
        Row row = rows.get(app);
        row.percent = -1;
        row.operation = getString(R.string.opening_system_installer);
        row.cancel.setVisibility(View.GONE);
        render(app);
        worker.execute(() -> {
            InstallManager.Outcome outcome = InstallManager.openShizuku(this, uri, app.packageName, repair);
            if (!outcome.pending && repair)
                new File(new File(getCacheDir(), "repairs"), app.packageName + ".apk").delete();
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                row.note = outcome.pending ? getString(R.string.installer_waiting) : outcome.message;
                setBusy(false);
                if (!outcome.pending && !outcome.ok) showHelp(app);
                if (resumed) checkPendingInstalls();
            });
        });
    }

    private void checkReleases() {
        if (busy) return;
        setBusy(true);
        check.setText(R.string.checking);
        for (Row row : rows.values()) {
            row.note = null;
            row.operation = getString(R.string.checking);
        }
        for (Releases.App app : rows.keySet()) render(app);
        worker.execute(() -> {
            for (Releases.App app : Releases.App.values()) {
                Releases.Release release = null;
                String error = null;
                try {
                    release = Releases.latest(app);
                } catch (Exception failure) {
                    String reason = failure.getMessage();
                    error = getString(reason != null && (reason.contains("HTTP 403") || reason.contains("HTTP 429"))
                            ? R.string.github_limited : R.string.github_unavailable);
                }
                Releases.Release result = release;
                String message = error;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    Row row = rows.get(app);
                    row.release = result;
                    row.error = message;
                    row.checked = true;
                    row.operation = null;
                    render(app);
                });
            }
            runOnUiThread(() -> {
                if (!isDestroyed()) {
                    setBusy(false);
                    checkPendingInstalls();
                    resumePendingLocal();
                }
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
            explainInstallPermission(app);
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
        beginPreparation(app, release.size);
        worker.execute(() -> {
            File apk;
            try {
                apk = Releases.download(app, release, getCacheDir(), getPackageManager());
                if (cancelPreparation || isDestroyed()) {
                    apk.delete();
                    runOnUiThread(() -> finishCancelled(app));
                    return;
                }
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setBusy(false);
                    row.note = failure.getMessage();
                    row.error = getString(R.string.prepare_failed);
                    if (cancelPreparation) finishCancelled(app); else render(app);
                });
                return;
            }
            if (mode == InstallMode.SHIZUKU) {
                runOnUiThread(() -> {
                    if (isDestroyed()) { apk.delete(); return; }
                    if (cancelPreparation) { apk.delete(); finishCancelled(app); return; }
                    openShizukuInstaller(app, ApkProvider.uri(getPackageName(), app), false);
                });
                return;
            }
            if (mode == InstallMode.CLASSIC) {
                runOnUiThread(() -> {
                    if (isDestroyed()) { apk.delete(); return; }
                    if (cancelPreparation) { apk.delete(); finishCancelled(app); return; }
                    setBusy(false);
                    try {
                        InstallManager.markPending(this, app.packageName);
                        row.note = getString(R.string.ready_install);
                        render(app);
                        row.status.setText(R.string.ready_install);
                        startActivity(InstallManager.classicIntent(ApkProvider.uri(getPackageName(), app), app.title));
                    } catch (RuntimeException unavailable) {
                        InstallManager.clearPending(this, app.packageName);
                        row.note = getString(R.string.install_failed);
                        render(app);
                    }
                });
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed()) { apk.delete(); return; }
                if (cancelPreparation) { apk.delete(); finishCancelled(app); return; }
                preparationGeneration++;
                row.percent = -1;
                row.operation = getString(R.string.installing);
                row.cancel.setVisibility(View.GONE);
                render(app);
                worker.execute(() -> {
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
            });
        });
    }

    /** Confirmation UI launch is pending; only a real package change triggers the two-field check. */
    private void checkPendingInstalls() {
        if (busy) return;
        for (Releases.App app : Releases.App.values()) {
            InstallManager.PendingResult pending = InstallManager.pollPending(this, app.packageName);
            if (pending.status == InstallManager.PendingStatus.NONE) continue;
            Row row = rows.get(app);
            if (pending.status == InstallManager.PendingStatus.WAITING) {
                row.note = getString(R.string.installer_waiting);
            } else if (pending.status == InstallManager.PendingStatus.FINISHED) {
                InstallManager.Outcome outcome = InstallManager.verifyAndFix(
                        this, InstallMode.CLASSIC, app.packageName, message -> { }, "");
                RepairState.needsShizuku(this, app.packageName, !outcome.ok);
                row.note = outcome.message;
                row.attributionMatch = outcome.ok;
                Toast.makeText(this, getString(outcome.ok ? R.string.install_pair_match : R.string.install_pair_mismatch),
                        Toast.LENGTH_LONG).show();
            } else {
                row.note = getString(R.string.install_not_observed);
            }
            if (pending.repair && pending.status != InstallManager.PendingStatus.WAITING)
                new File(new File(getCacheDir(), "repairs"), app.packageName + ".apk").delete();
            render(app);
        }
    }

    private void addInstallMethod(LinearLayout page) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(14), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surfaceColor);
        background.setCornerRadius(dp(16));
        card.setBackground(background);
        card.addView(text(getString(R.string.install_method), 18, inkColor, true));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        addModeButton(group, InstallMode.CLASSIC, getString(R.string.classic));
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

        methodStatus = text("", 14, mutedColor, false);
        methodStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(methodStatus);

        Button diagnostics = new Button(this);
        diagnostics.setText(R.string.diagnostics);
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
        button.setTextColor(inkColor);
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
            methodStatus.setText(getString(R.string.classic_method_description) + " " + shizuku);
        } else if (mode == InstallMode.SHIZUKU) {
            methodStatus.setText(getString(R.string.shizuku_method_description) + " " + shizuku
                    + ((Build.MANUFACTURER.equalsIgnoreCase("Xiaomi") || Build.MANUFACTURER.equalsIgnoreCase("Redmi")
                    || Build.MANUFACTURER.equalsIgnoreCase("POCO")) ? "\n" + getString(R.string.xiaomi_shizuku_hint) : ""));
        } else {
            methodStatus.setText("Installs silently with su. Android or your root manager may ask you to allow it.");
        }
    }

    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "MM Auto");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_message));
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_app)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.share_app_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDiagnostics() {
        Map<String, String> apps = new LinkedHashMap<>();
        for (Releases.App app : Releases.App.values()) apps.put(app.title, app.packageName);
        String report = InstallDiagnostics.report(this, apps);
        new AlertDialog.Builder(this)
                .setTitle(R.string.diagnostics)
                .setMessage(report)
                .setPositiveButton(R.string.share, (dialog, which) -> {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, report);
                    startActivity(Intent.createChooser(send, getString(R.string.diagnostics)));
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void setBusy(boolean working) {
        busy = working;
        check.setEnabled(!working);
        settingsButton.setEnabled(!working);
        if (!working) {
            preparationGeneration++;
            check.setText(R.string.check_updates);
            for (Row row : rows.values()) {
                row.operation = null;
                row.percent = -1;
                row.cancel.setVisibility(View.GONE);
            }
        }
        for (Releases.App app : rows.keySet()) render(app);
    }

    private void render(Releases.App app) {
        Row row = rows.get(app);
        PackageInfo current = Releases.installed(getPackageManager(), app);
        Releases.Release release = row.release;
        boolean available = release != null && release.isNewer(current);
        row.version.setText(current == null ? getString(R.string.not_installed)
                : getString(R.string.installed_version, current.versionName));
        String detail = row.error;
        if (detail == null && row.checked) {
            detail = release == null ? getString(R.string.no_release)
                    : available ? getString(R.string.available_version, release.version)
                    + (release.preview ? " · " + getString(R.string.preview) : "")
                    : getString(R.string.up_to_date);
        }
        // Technical outcomes remain available inside Help with a problem.
        String sourceStatus = row.note != null && row.attributionMatch != null
                ? getString(row.attributionMatch ? R.string.install_pair_match : R.string.install_pair_mismatch) : detail;
        row.status.setText(row.operation != null ? row.operation : sourceStatus == null ? "" : sourceStatus);
        row.status.setTextColor(row.error == null ? mutedColor : primaryColor);
        row.install.setEnabled(!busy);
        row.more.setEnabled(!busy);
        boolean pendingLocal = pendingLocalApp() == app;
        row.install.setText(pendingLocal ? R.string.ready_install
                : current == null ? (available ? R.string.install : R.string.download)
                : available ? R.string.update : R.string.open);
        row.manual.setVisibility(row.error == null || (current == null && !available) ? View.GONE : View.VISIBLE);
        row.manual.setEnabled(!busy);
        row.progress.setVisibility(row.operation != null && busy ? View.VISIBLE : View.GONE);
        row.progress.setIndeterminate(row.percent < 0);
        if (row.percent >= 0) row.progress.setProgress(row.percent);
        row.repair.setVisibility(row.note == null || busy ? View.GONE : View.VISIBLE);
        row.repair.setEnabled(!busy);
        if (app == Releases.App.AUTO) {
            row.card.setVisibility(available || row.error != null || row.operation != null || row.note != null || pendingLocal
                    ? View.VISIBLE : View.GONE);
            if (!available && !pendingLocal) row.install.setVisibility(View.GONE);
            else row.install.setVisibility(View.VISIBLE);
        }
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private Button action(String title, boolean primary) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinHeight(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPaddingRelative(dp(14), 0, dp(14), 0);
        button.setTextColor(primary ? backgroundColor : primaryColor);
        android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x337b9edc), shape(primary ? primaryColor : Color.TRANSPARENT, 14),
                shape(Color.WHITE, 14));
        button.setBackground(ripple);
        return button;
    }

    private void showSettings() {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), dp(16));
        updateAlerts = new Switch(this);
        updateAlerts.setText(R.string.daily_alerts);
        updateAlerts.setTextColor(inkColor);
        updateAlerts.setMinHeight(dp(52));
        updateAlerts.setOnClickListener(v -> changeUpdateAlerts());
        content.addView(updateAlerts);
        content.addView(text(getString(R.string.daily_description), 14, mutedColor, false));
        refreshAlertsSwitch();
        Button appearance = action(getString(R.string.appearance), false);
        appearance.setOnClickListener(v -> choosePreference("theme", R.string.appearance,
                new String[]{getString(R.string.system), getString(R.string.light), getString(R.string.dark)},
                new String[]{"system", "light", "dark"}));
        content.addView(appearance, new LinearLayout.LayoutParams(-1, dp(52)));
        Button language = action(getString(R.string.language), false);
        language.setOnClickListener(v -> choosePreference("language", R.string.language,
                new String[]{getString(R.string.system), "English", "العربية"}, new String[]{"system", "en", "ar"}));
        content.addView(language, new LinearLayout.LayoutParams(-1, dp(52)));
        Button self = action("MM Auto", false);
        self.setOnClickListener(v -> showVersion(Releases.App.AUTO));
        content.addView(self, new LinearLayout.LayoutParams(-1, dp(52)));
        Button help = action(getString(R.string.troubleshoot), false);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(R.string.troubleshoot)
                .setItems(new String[]{"MM Auto", "MM IPTV", "MM Tube"}, (d, i) -> showHelp(Releases.App.values()[i])).show());
        content.addView(help, new LinearLayout.LayoutParams(-1, dp(52)));
        Button advanced = action(getString(R.string.advanced), false);
        advanced.setOnClickListener(v -> {
            LinearLayout panel = column();
            panel.setPadding(dp(12), dp(12), dp(12), 0);
            addInstallMethod(panel);
            new AlertDialog.Builder(this).setTitle(R.string.advanced).setView(panel)
                    .setNegativeButton(R.string.close, null).show();
        });
        content.addView(advanced, new LinearLayout.LayoutParams(-1, dp(52)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(R.string.settings).setView(scroll)
                .setNegativeButton(R.string.close, null).show();
    }

    private void choosePreference(String key, int title, String[] labels, String[] values) {
        String current = getSharedPreferences("ui", MODE_PRIVATE).getString(key, "system");
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected, (d, i) -> {
            getSharedPreferences("ui", MODE_PRIVATE).edit().putString(key, values[i]).apply();
            d.dismiss();
            recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void explainInstallPermission(Releases.App app) {
        new AlertDialog.Builder(this).setTitle(R.string.allow_install_title)
                .setMessage(R.string.allow_install_description)
                .setPositiveButton(R.string.continue_action, (d, w) -> safeStart(new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton(R.string.cancel, null).show();
    }

    // Observe existing download files. No release-selection, download or verification rules are changed.
    private void beginPreparation(Releases.App app, long expected) {
        cancelPreparation = false;
        Row row = rows.get(app);
        row.operation = getString(R.string.preparing);
        row.cancel.setVisibility(View.VISIBLE);
        row.cancel.setEnabled(true);
        long started = System.currentTimeMillis();
        int generation = ++preparationGeneration;
        Runnable monitor = new Runnable() {
            @Override public void run() {
                if (isDestroyed() || !busy || row.operation == null || generation != preparationGeneration) return;
                long bytes = 0;
                File[] files = new File(getCacheDir(), "releases").listFiles();
                if (files != null) for (File file : files) {
                    if (file.getName().startsWith(app.packageName)
                            && !file.getName().equals(app.packageName + ".apk") && file.lastModified() >= started - 1000)
                        bytes = Math.max(bytes, file.length());
                }
                if (!cancelPreparation && bytes > 0 && expected > 0) {
                    row.percent = (int) Math.min(100, bytes * 100 / expected);
                    row.operation = row.percent == 100 ? getString(R.string.verifying)
                            : getString(R.string.downloading, row.percent);
                }
                render(app);
                ui.postDelayed(this, 300);
            }
        };
        ui.post(monitor);
    }

    private void finishCancelled(Releases.App app) {
        if (isDestroyed()) return;
        rows.get(app).note = getString(R.string.cancelled);
        setBusy(false);
        rows.get(app).status.setText(R.string.cancelled);
    }

    private void chooseApk(Releases.App app) {
        if (busy) return;
        pickingApp = app;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { startActivityForResult(pick, PICK_APK); }
        catch (RuntimeException error) { pickingApp = null; Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_APK) return;
        Releases.App app = pickingApp;
        pickingApp = null;
        if (result != RESULT_OK || data == null || data.getData() == null || app == null || busy) return;
        Uri uri = data.getData();
        setBusy(true);
        beginPreparation(app, 0);
        worker.execute(() -> {
            File apk = null;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException(getString(R.string.file_unavailable));
                apk = ManualApk.prepare(app, input, getCacheDir(), getPackageManager(), () -> cancelPreparation || isDestroyed());
                if (cancelPreparation || isDestroyed()) {
                    apk.delete();
                    runOnUiThread(() -> finishCancelled(app));
                    return;
                }
                File verified = apk;
                runOnUiThread(() -> {
                    if (isDestroyed()) { verified.delete(); return; }
                    if (cancelPreparation) { verified.delete(); finishCancelled(app); return; }
                    setBusy(false);
                    rows.get(app).status.setText(R.string.ready_install);
                    showLocalConfirmation(app, verified);
                });
            } catch (Exception error) {
                if (apk != null) apk.delete();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (cancelPreparation) { finishCancelled(app); return; }
                    rows.get(app).note = error.getMessage();
                    rows.get(app).error = getString(R.string.prepare_failed);
                    setBusy(false);
                });
            }
        });
    }

    private void installLocal(Releases.App app, File verified) {
        if (busy) return;
        getSharedPreferences("ui", MODE_PRIVATE).edit().remove("pending_local").apply();
        InstallMode mode = InstallManager.mode(this);
        if (mode == InstallMode.CLASSIC && !getPackageManager().canRequestPackageInstalls()) {
            // Keep the verified file and allow the user to retry without selecting it again.
            new AlertDialog.Builder(this).setTitle(R.string.allow_install_title)
                    .setMessage(R.string.allow_install_description)
                    .setPositiveButton(R.string.continue_action, (d, w) -> {
                        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("pending_local", app.name()).apply();
                        safeStart(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                    }).setNegativeButton(R.string.cancel, (d, w) -> verified.delete()).show();
            return;
        }
        if (mode == InstallMode.SHIZUKU && !ShizukuChannel.granted()) {
            getSharedPreferences("ui", MODE_PRIVATE).edit().putString("pending_local", app.name()).apply();
            rows.get(app).note = getString(!ShizukuChannel.running() ? R.string.repair_start_shizuku : R.string.repair_allow_shizuku);
            if (ShizukuChannel.running() && !ShizukuChannel.blocked()) ShizukuChannel.request();
            render(app);
            Toast.makeText(this, rows.get(app).note, Toast.LENGTH_LONG).show();
            return;
        }
        if (mode == InstallMode.SHIZUKU) {
            openShizukuInstaller(app, ApkProvider.uri(getPackageName(), app), false);
            return;
        }
        if (mode == InstallMode.CLASSIC) {
            try {
                InstallManager.markPending(this, app.packageName);
                startActivity(InstallManager.classicIntent(ApkProvider.uri(getPackageName(), app), app.title));
            } catch (RuntimeException error) {
                InstallManager.clearPending(this, app.packageName);
                rows.get(app).note = getString(R.string.install_failed);
                showHelp(app);
            }
            return;
        }
        setBusy(true);
        rows.get(app).operation = getString(R.string.installing);
        render(app);
        worker.execute(() -> {
            InstallManager.Outcome outcome = InstallManager.installPrivileged(this, mode, verified, app.packageName,
                    message -> runOnUiThread(() -> { if (!isDestroyed()) rows.get(app).status.setText(message); }));
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                rows.get(app).note = outcome.message;
                setBusy(false);
                if (!outcome.ok) showHelp(app);
            });
        });
    }

    private File localApk(Releases.App app) {
        return new File(new File(getCacheDir(), "releases"), app.packageName + ".apk");
    }

    private Releases.App pendingLocalApp() {
        String name = getSharedPreferences("ui", MODE_PRIVATE).getString("pending_local", null);
        if (name == null) return null;
        try {
            Releases.App app = Releases.App.valueOf(name);
            if (localApk(app).isFile()) return app;
        } catch (IllegalArgumentException invalid) {
            // Discard stale UI state; imported files still use the existing verifier.
        }
        getSharedPreferences("ui", MODE_PRIVATE).edit().remove("pending_local").apply();
        return null;
    }

    private void resumePendingLocal() {
        if (busy || isDestroyed() || (localConfirmation != null && localConfirmation.isShowing())) return;
        Releases.App app = pendingLocalApp();
        if (app == null) return;
        InstallMode mode = InstallManager.mode(this);
        boolean ready = mode == InstallMode.CLASSIC ? getPackageManager().canRequestPackageInstalls()
                : mode != InstallMode.SHIZUKU || ShizukuChannel.granted();
        rows.get(app).note = getString(ready ? R.string.ready_install
                : mode == InstallMode.CLASSIC ? R.string.allow_install_description : R.string.repair_allow_shizuku);
        render(app);
        if (ready) showLocalConfirmation(app, localApk(app));
    }

    private void showLocalConfirmation(Releases.App app, File verified) {
        if (localConfirmation != null && localConfirmation.isShowing()) return;
        Runnable discard = () -> {
            getSharedPreferences("ui", MODE_PRIVATE).edit().remove("pending_local").apply();
            verified.delete();
            rows.get(app).note = null;
            render(app);
        };
        localConfirmation = new AlertDialog.Builder(this).setTitle(app.title).setMessage(R.string.ready_install)
                .setPositiveButton(R.string.install, (d, w) -> installLocal(app, verified))
                .setNegativeButton(R.string.cancel, (d, w) -> discard.run())
                .setOnCancelListener(d -> discard.run()).create();
        localConfirmation.setOnDismissListener(d -> localConfirmation = null);
        localConfirmation.show();
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
