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
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final EnumMap<Releases.App, Row> rows = new EnumMap<>(Releases.App.class);
    private Button check, downloadAll;
    private UpdateDownloads downloads;
    private final ExecutorService checks = Executors.newFixedThreadPool(3);
    private final EnumMap<Releases.App, TransferControl> checkCalls = new EnumMap<>(Releases.App.class);
    private final Runnable downloadsChanged = () -> {
        if (isDestroyed()) return;
        for (Releases.App app : rows.keySet()) render(app);
        refreshActions();
        ui.post(this::resumeInstallQueue);
    };
    private Switch updateAlerts;
    private boolean busy;
    private int backgroundColor, surfaceColor, primaryColor, inkColor, mutedColor;
    private LinearLayout selfBanner;
    private volatile boolean cancelPreparation;
    private Releases.App pickingApp;
    private int preparationGeneration;
    private static final int PICK_APK = 42;
    private Button settingsButton;
    private AlertDialog localConfirmation;
    private boolean resumed;
    private ShizukuGuide shizukuGuide;
    private String guideTarget;
    private final Runnable guideRefresh = new Runnable() {
        @Override public void run() {
            if (!resumed || shizukuGuide == null || !shizukuGuide.dialog.isShowing()) return;
            shizukuGuide.refresh();
            ui.postDelayed(this, 1000);
        }
    };
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener releaseChanges = (prefs, key) -> {
        if (!isDestroyed() && key != null && !"sequence".equals(key)) loadReleaseCache(false);
    };

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
        long checkedAt;
        long checkSequence;
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
        InstallManager.mode(this); // Migrate retired settings before restoring work.
        downloads = UpdateDownloads.get(this);
        PendingSetup restoredSetup = PendingSetup.read(this);
        if (restoredSetup != null && restoredSetup.kind == PendingSetup.Kind.QUEUE) queuePaused = true;
        for (UpdateDownloads.Job job : downloads.jobs.values()) if (job.recoveredInstaller) queuePaused = true;
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
        title.setGravity(Gravity.START);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        settingsButton = action(getString(R.string.settings), false);
        settingsButton.setOnClickListener(v -> showSettings());
        UiStyle.icon(settingsButton, R.drawable.ic_settings_action);
        header.addView(settingsButton, new LinearLayout.LayoutParams(-2, -2));
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
        UiStyle.icon(check, R.drawable.ic_refresh_action);
        page.addView(check, UiStyle.actionParams(this));
        downloadAll = action(getString(R.string.download_all_updates), true);
        downloadAll.setOnClickListener(v -> {
            if (!getPackageManager().canRequestPackageInstalls()) {
                explainInstallPermission(Releases.App.AUTO); return;
            }
            for (Releases.App app : new Releases.App[] { Releases.App.IPTV, Releases.App.TUBE, Releases.App.AUTO }) {
                Row row = rows.get(app);
                if (row.release != null && row.release.isNewer(Releases.installed(getPackageManager(), app))) install(app);
            }
        });
        page.addView(downloadAll, UiStyle.actionParams(this));
        Button share = action(getString(R.string.share_app), false);
        share.setOnClickListener(v -> shareApp());
        UiStyle.icon(share, R.drawable.ic_share_action);
        page.addView(share, UiStyle.actionParams(this));
        Button support = action(getString(R.string.support_development), false);
        support.setOnClickListener(v -> safeStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://ko-fi.com/mostafaabdellatif")).addCategory(Intent.CATEGORY_BROWSABLE)));
        UiStyle.coffee(support);
        page.addView(support, UiStyle.actionParams(this));
        Button contact = action(getString(R.string.contact_developer), false);
        contact.setOnClickListener(v -> safeStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/966597197475"))));
        UiStyle.icon(contact, R.drawable.ic_contact_action);
        page.addView(contact, UiStyle.actionParams(this));
        setContentView(scroll);
        UiStyle.systemBars(this);
        if (savedState != null) {
            String selected = savedState.getString("picking_app");
            if (selected != null) pickingApp = Releases.App.valueOf(selected);
        }
        ShizukuChannel.listen(granted -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (shizukuGuide != null) shizukuGuide.refresh();
            if (granted) {
                Toast.makeText(this, R.string.permission_ready, Toast.LENGTH_SHORT).show();
                // Continue is an explicit user action in the setup guide.
            }
        }));
        ReleaseCache.preferences(this).registerOnSharedPreferenceChangeListener(releaseChanges);
        downloads.listen(downloadsChanged);
        loadReleaseCache(true);
        if (PendingSetup.read(this) != null) {
            showShizukuGuide(PendingSetup.read(this).app);
        } else if (savedState != null && savedState.containsKey("shizuku_guide")) {
            String target = savedState.getString("shizuku_guide");
            showShizukuGuide("setup".equals(target) ? null : Releases.App.valueOf(target));
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (pickingApp != null) state.putString("picking_app", pickingApp.name());
        if (shizukuGuide != null && shizukuGuide.dialog.isShowing()) state.putString("shizuku_guide", guideTarget);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        ui.removeCallbacks(guideRefresh);
        ui.post(guideRefresh);
        UpdateAlerts.syncSchedule(this);
        refreshAlertsSwitch();
        checkPendingInstalls();
        checkRepairPending();
        if (!busy) for (Releases.App app : rows.keySet()) render(app);
        resumePendingLocal();
        loadReleaseCache(false);
        ui.postDelayed(this::resumeInstallQueue, 700);
        ui.postDelayed(this::finishStandaloneReturn, 700);
    }

    @Override protected void onPause() {
        resumed = false;
        if (downloads.active() != null) downloads.active().covered = true;
        android.content.SharedPreferences pending = getSharedPreferences("ui", MODE_PRIVATE);
        if (pending.contains("standalone_app")) pending.edit().putBoolean("standalone_covered", true).commit();
        ui.removeCallbacks(guideRefresh);
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
        ReleaseCache.preferences(this).unregisterOnSharedPreferenceChangeListener(releaseChanges);
        cancelPreparation = true;
        downloads.unlisten(downloadsChanged);
        for (TransferControl call : checkCalls.values()) { call.cancel(); call.close(); }
        checkCalls.clear(); checks.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        if (shizukuGuide != null) shizukuGuide.dialog.dismiss();
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
        card.setBackground(UiStyle.surface(this, surfaceColor, 22, UiStyle.border(this)));
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
        row.more.setPadding(0, 0, 0, 0);
        row.more.setMinHeight(dp(48));
        row.more.setMinimumHeight(dp(48));
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
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(-1, -2);
        mainParams.topMargin = dp(8);
        card.addView(row.install, mainParams);
        row.manual = action(getString(R.string.manual_download), false);
        row.manual.setOnClickListener(v -> showManual(app));
        card.addView(row.manual, UiStyle.actionParams(this));
        row.cancel = action(getString(R.string.cancel_download), false);
        row.cancel.setOnClickListener(v -> {
            if (downloads.occupied(app)) { row.error = null; downloads.cancel(app); return; }
            cancelPreparation = true;
            row.operation = getString(R.string.cancelling);
            row.cancel.setEnabled(false);
            render(app);
        });
        row.cancel.setVisibility(View.GONE);
        card.addView(row.cancel, UiStyle.actionParams(this));
        // Repair stays reachable through Help, with its existing implementation unchanged.
        row.repair = action(getString(R.string.troubleshoot), false);
        row.repair.setOnClickListener(v -> showHelp(app));
        row.repair.setVisibility(View.GONE);
        card.addView(row.repair, UiStyle.actionParams(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        page.addView(card, params);
        render(app);
    }

    private void primaryAction(Releases.App app) {
        if (busy) return;
        if (downloads.occupied(app)) {
            if (queuePaused) {
                UpdateDownloads.Job waiting = downloads.get(app);
                if (waiting != null && waiting.mode == InstallMode.SHIZUKU && !ShizukuChannel.granted()) {
                    beginSetup(app, PendingSetup.Kind.QUEUE); return;
                }
                if (waiting != null && waiting.mode == InstallMode.CLASSIC && !getPackageManager().canRequestPackageInstalls()) {
                    explainInstallPermission(app); return;
                }
                for (UpdateDownloads.Job job : downloads.jobs.values()) if (job.recoveredInstaller) {
                    InstallManager.clearPending(this, job.app.packageName); job.recoveredInstaller = false;
                }
                queuePaused = false; rows.get(app).error = null; drainInstallQueue();
            }
            return;
        }
        if (pendingLocalApp() == app) {
            installLocal(app, localApk(app));
            return;
        }
        Row row = rows.get(app);
        PackageInfo current = Releases.installed(getPackageManager(), app);
        if (row.release != null && row.release.isNewer(current)) { install(app); return; }
        if (current == null) { checkReleases(); return; }
        if (app == Releases.App.AUTO) { showVersion(app); return; }
        Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launch != null) safeStart(launch);
        else Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
    }

    private void showAppMenu(Releases.App app, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.release_details).setOnMenuItemClickListener(item -> { showVersion(app); return true; });
        menu.getMenu().add(R.string.open_releases).setOnMenuItemClickListener(item -> { openReleases(app); return true; });
        menu.getMenu().add(R.string.choose_apk).setEnabled(!busy && !downloads.hasWork())
                .setOnMenuItemClickListener(item -> { chooseApk(app); return true; });
        menu.getMenu().add(R.string.troubleshoot).setEnabled(!busy && !downloads.hasWork())
                .setOnMenuItemClickListener(item -> { showHelp(app); return true; });
        if (Releases.installed(getPackageManager(), app) != null)
            menu.getMenu().add(R.string.repair_retry_classic).setEnabled(!busy && !downloads.hasWork())
                    .setOnMenuItemClickListener(item -> { repair(app); return true; });
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
        String description = getString(R.string.help_description);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(R.string.troubleshoot)
                .setMessage(description).setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.problem_details, (d, w) -> showProblemDetails(app));
        if (Releases.installed(getPackageManager(), app) != null)
            dialog.setPositiveButton(R.string.complete_setup, (d, w) -> beginSetup(app, PendingSetup.Kind.REPAIR));
        else dialog.setPositiveButton(R.string.try_shizuku, (d,w) -> beginSetup(app, PendingSetup.Kind.INSTALL));
        dialog.show();
    }

    private void repair(Releases.App app) {
        if (busy || Releases.installed(getPackageManager(), app) == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            rows.get(app).note = getString(R.string.repair_unknown_apps);
            render(app);
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException unavailable) {
                InstallManager.recordFailure(this, app.packageName, unavailable.toString());
                rows.get(app).note = getString(R.string.install_failed);
                render(app);
            }
            return;
        }
        runClassicRepair(app);
    }

    private void runClassicRepair(Releases.App app) {
        if (busy || downloads.hasWork()) return;
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
                        markStandalone(app);
                        startActivity(InstallManager.classicIntent(ApkProvider.repairUri(getPackageName(), app), app.title));
                    } catch (Exception failure) {
                        RepairState.clear(this, app.packageName);
                        clearStandalone();
                        target.delete();
                        InstallManager.recordFailure(this, app.packageName, failure.toString());
                        rows.get(app).note = getString(R.string.install_failed);
                        render(app);
                    }
                });
            } catch (Exception failure) {
                SupportEvents.event("repair",app.packageName,"failed",InstallManager.mode(this).name());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    InstallManager.recordFailure(this, app.packageName, failure.toString());
                    rows.get(app).note = getString(R.string.install_failed);
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
            rows.get(app).note = getString(R.string.installed_check_car);
            rows.get(app).attributionMatch = ready;
            render(app);

        }
    }

    private void showProblemDetails(Releases.App app) {
        new AlertDialog.Builder(this).setTitle(R.string.problem_details)
                .setMessage(InstallManager.lastAttempt(this, app.packageName) + "\n\n"
                        + InstallIdentity.read(getPackageManager(), app.packageName).describe())
                .setPositiveButton(R.string.diagnostics, (d,w) -> showDiagnostics())
                .setNegativeButton(R.string.close, null).show();
    }

    private void beginSetup(Releases.App app, PendingSetup.Kind kind) {
        if (busy || (downloads.hasWork() && kind != PendingSetup.Kind.QUEUE)) return;
        PendingSetup.save(this, app, kind);
        showShizukuGuide(app);
    }

    private void continueSetup() {
        PendingSetup task = PendingSetup.read(this);
        if (task == null || busy) return;
        if (downloads.hasWork() && task.kind != PendingSetup.Kind.QUEUE) return;
        if (!ShizukuChannel.granted()) { showShizukuGuide(task.app); return; }
        if (task.kind == PendingSetup.Kind.INSTALL && rows.get(task.app).release == null) {
            rows.get(task.app).error = getString(R.string.setup_check_update);
            checkReleases(); showShizukuGuide(task.app); return;
        }
        PendingSetup.clear(this);
        switch (task.kind) {
            case REPAIR: runRepair(task.app); break;
            case LOCAL:
                try {
                    File apk = localApk(task.app);
                    Releases.validate(task.app, new Releases.Release("",0,null,apk.length(),"",false),apk,getPackageManager());
                    installLocal(task.app, apk, InstallMode.SHIZUKU);
                } catch (Exception invalid) {
                    InstallManager.recordFailure(this, task.app.packageName, invalid.toString());
                    rows.get(task.app).error = getString(R.string.prepare_failed); render(task.app);
                }
                break;
            case QUEUE:
                UpdateDownloads.Job job = downloads.get(task.app);
                if (job != null && job.recoveredInstaller) {
                    InstallManager.clearPending(this, task.app.packageName); job.recoveredInstaller = false;
                }
                rows.get(task.app).error = null;
                queuePaused = false; drainInstallQueue(); break;
            case INSTALL: install(task.app, InstallMode.SHIZUKU); break;
        }
    }

    private void showShizukuGuide(Releases.App app) {
        if (isDestroyed() || busy) return;
        if (shizukuGuide != null) shizukuGuide.dialog.dismiss();
        guideTarget = app == null ? "setup" : app.name();
        PendingSetup task = PendingSetup.read(this);
        if (app != null && task == null) {
            PendingSetup.save(this, app, Releases.installed(getPackageManager(), app) == null
                    ? PendingSetup.Kind.INSTALL : PendingSetup.Kind.REPAIR);
            task = PendingSetup.read(this);
        }
        ShizukuGuide shown = new ShizukuGuide(this, app == null ? null : app.title,
                task == null ? null : this::continueSetup);
        shizukuGuide = shown;
        shown.dialog.setOnCancelListener(d -> PendingSetup.clear(this));
        shown.dialog.setOnDismissListener(d -> {
            if (shizukuGuide != shown) return;
            shizukuGuide = null; guideTarget = null; ui.removeCallbacks(guideRefresh);
        });
        shown.show();
        shown.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            PendingSetup.clear(this); shown.dialog.dismiss();
        });
        ui.removeCallbacks(guideRefresh); ui.post(guideRefresh);
    }

    private void runRepair(Releases.App app) {
        if (busy || downloads.hasWork()) return;
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
                InstallManager.recordFailure(this, app.packageName, failure.toString());
                String message = getString(R.string.install_failed);
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
        markStandalone(app);
        preparationGeneration++;
        Row row = rows.get(app);
        row.attributionMatch = null;
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
                row.note = getString(outcome.pending ? R.string.installer_waiting : R.string.install_failed);
                if (!outcome.pending) clearStandalone();
                setBusy(false);
                if (!outcome.pending && !outcome.ok) showHelp(app);
                if (resumed) checkPendingInstalls();
                if (resumed) ui.postDelayed(this::finishStandaloneReturn, 700);
            });
        });
    }

    private void loadReleaseCache(boolean initial) {
        worker.execute(() -> {
            EnumMap<Releases.App, ReleaseCache.Snapshot> saved = new EnumMap<>(Releases.App.class);
            for (Releases.App app : Releases.App.values()) saved.put(app, ReleaseCache.read(this, app));
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                for (Releases.App app : Releases.App.values()) {
                    applyReleaseSnapshot(app, saved.get(app));
                    render(app);
                }
                refreshActions();
                if (initial && pickingApp == null && pendingLocalApp() == null
                        && !InstallManager.hasPending(this)) checkReleases();
            });
        });
    }

    private void applyReleaseSnapshot(Releases.App app, ReleaseCache.Snapshot saved) {
        Row row = rows.get(app);
        if (saved == null || saved.sequence < row.checkSequence) return;
        if (saved.sequence > row.checkSequence) row.error = null;
        row.release = saved.release;
        row.checked = true;
        row.checkedAt = saved.checkedAt;
        row.checkSequence = saved.sequence;
    }

    private void checkReleases() {
        if (busy || !checkCalls.isEmpty()) return;
        // Independent requests share a 30-second UI deadline, not a serial three-app wait.
        for (Releases.App app : Releases.App.values()) {
            TransferControl call = new TransferControl(30_000);
            checkCalls.put(app, call);
            rows.get(app).error = null;
            ui.postDelayed(() -> completeCheck(app, call, null, getString(R.string.check_timeout)), 30_000);
            checks.execute(() -> {
                ReleaseCache.Snapshot snapshot = null;
                String error = null;
                try {
                    SupportEvents.event("release-check", app.packageName, "started", "unknown");
                    snapshot = ReleaseCache.check(this, app, () -> {
                        Releases.Release release = releaseLookup.fetch(app, call);
                        call.check(); return release;
                    });
                } catch (Exception failure) {
                    String reason = failure.getMessage();
                    error = getString(call.stopped() ? R.string.check_timeout
                            : reason != null && (reason.contains("HTTP 403") || reason.contains("HTTP 429"))
                            ? R.string.github_limited : R.string.github_unavailable);
                } finally { call.close(); }
                ReleaseCache.Snapshot result = snapshot;
                String message = error;
                runOnUiThread(() -> completeCheck(app, call, result, message));
            });
        }
        refreshActions();
        for (Releases.App app : rows.keySet()) render(app);
    }

    interface ReleaseLookup { Releases.Release fetch(Releases.App app, TransferControl call) throws Exception; }
    ReleaseLookup releaseLookup = (app, call) -> Releases.latest(app, null, call);

    private void completeCheck(Releases.App app, TransferControl call, ReleaseCache.Snapshot result, String error) {
        if (isDestroyed() || checkCalls.get(app) != call) return;
        checkCalls.remove(app);
        call.cancel(); call.close();
        applyReleaseSnapshot(app, result);
        rows.get(app).error = error;
        SupportEvents.event("release-check", app.packageName, error == null ? "ready" : "failed", "unknown");
        render(app); refreshActions();
    }

    private void refreshActions() {
        if (check == null) return;
        check.setEnabled(!busy && checkCalls.isEmpty());
        check.setText(checkCalls.isEmpty() ? R.string.check_updates : R.string.checking);
        if (settingsButton != null) settingsButton.setEnabled(!busy);
        if (downloadAll != null) {
            int offers = 0;
            for (Releases.App app : rows.keySet()) {
                Releases.Release release = rows.get(app).release;
                if (!downloads.occupied(app) && release != null && release.isNewer(Releases.installed(getPackageManager(), app))) offers++;
            }
            downloadAll.setVisibility(offers > 1 ? View.VISIBLE : View.GONE);
            downloadAll.setEnabled(!busy);
        }
    }

    private void install(Releases.App app) { install(app, InstallMode.CLASSIC); }

    private void install(Releases.App app, InstallMode mode) {
        Row row = rows.get(app);
        Releases.Release release = row.release;
        if (busy || release == null || !release.isNewer(Releases.installed(getPackageManager(), app))) return;
        row.note = null;
        if (mode == InstallMode.CLASSIC && !getPackageManager().canRequestPackageInstalls()) {
            explainInstallPermission(app);
            return;
        }
        if (mode == InstallMode.SHIZUKU && !ShizukuChannel.granted()) {
            beginSetup(app, PendingSetup.Kind.INSTALL); return;
        }

        if (downloads.occupied(app)) return;
        row.error = null;
        downloads.start(app, release, mode);
    }

    private void drainInstallQueue() {
        if (PendingSetup.read(this) != null || queuePaused || !resumed || isDestroyed() || busy || pickingApp != null || pendingLocalApp() != null
                || (localConfirmation != null && localConfirmation.isShowing()) || InstallManager.hasPending(this)) return;
        UpdateDownloads.Job job = downloads.reserveNext();
        if (job == null) return;
        // Verify again after restore/waiting; never install an unverified or now-older APK.
        worker.execute(() -> {
            boolean valid = false;
            try { Releases.validate(job.app, job.release, job.file, getPackageManager()); valid = true; }
            catch (Exception invalid) { }
            boolean verified = valid;
            runOnUiThread(() -> {
                if (downloads.active() != job) return;
                if (!verified) {
                    rows.get(job.app).error = getString(R.string.queue_verify_failed);
                    downloads.finish(job); return;
                }
                if (isDestroyed() || !resumed) { downloads.release(job); return; }
                launchQueuedInstaller(job);
            });
        });
    }

    @SuppressWarnings("deprecation")
    private void launchQueuedInstaller(UpdateDownloads.Job job) {
        Row row = rows.get(job.app);
        if ((job.mode == InstallMode.CLASSIC && !getPackageManager().canRequestPackageInstalls())
                || (job.mode == InstallMode.SHIZUKU && !ShizukuChannel.granted())) {
            row.error = getString(R.string.queue_permission);
            // Keep the ready file, but wait for an explicit retry after permission is restored.
            queuePaused = true; downloads.release(job);
            if (job.mode == InstallMode.SHIZUKU) beginSetup(job.app, PendingSetup.Kind.QUEUE);
            else explainInstallPermission(job.app);
            return;
        }
        job.installerOpened = true;
        row.attributionMatch = null;
        if (job.mode == InstallMode.CLASSIC) {
            job.launching = false;
            try {
                InstallManager.markPending(this, job.app.packageName);
                Intent intent = InstallManager.classicIntent(ApkProvider.uri(getPackageName(), job.app), job.app.title);
                intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                startActivityForResult(intent, job.requestCode);
            } catch (RuntimeException failed) {
                InstallManager.clearPending(this, job.app.packageName);
                row.error = getString(R.string.install_failed); downloads.finish(job);
            }
        } else {
            // Shizuku opens the normal confirmation UI, one operation at a time.
            worker.execute(() -> {
                InstallManager.Outcome outcome = InstallManager.openShizuku(this, ApkProvider.uri(getPackageName(), job.app), job.app.packageName, false);
                runOnUiThread(() -> {
                    downloads.launched(job);
                    if (downloads.active() != job) return;
                    row.note = getString(outcome.pending ? R.string.installer_waiting : R.string.install_failed);
                    if (!outcome.pending) {
                        if (!outcome.ok) row.error = getString(R.string.install_failed);
                        downloads.finish(job);
                    } else if (resumed && job.covered) ui.postDelayed(this::resumeInstallQueue, 700);
                });
            });
        }
    }

    private boolean queuePaused;

    private void markStandalone(Releases.App app) {
        rows.get(app).attributionMatch = null;
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("standalone_app", app.name())
                .putBoolean("standalone_covered", false).commit();
    }

    private void clearStandalone() {
        getSharedPreferences("ui", MODE_PRIVATE).edit().remove("standalone_app")
                .remove("standalone_covered").commit();
    }

    private void finishStandaloneReturn() {
        if (!resumed || busy || isDestroyed()) return;
        android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        if (!p.getBoolean("standalone_covered", false)) return;
        String name = p.getString("standalone_app", "");
        p.edit().remove("standalone_app").remove("standalone_covered").commit();
        try {
            Releases.App app = Releases.App.valueOf(name);
            checkPendingInstalls(); checkRepairPending();
            if (rows.get(app).attributionMatch == null) rows.get(app).note = getString(R.string.install_incomplete);
            InstallManager.clearPending(this, app.packageName);
            RepairState.clear(this, app.packageName);
            render(app);
        } catch (IllegalArgumentException invalid) { /* Discard stale activity state. */ }
    }

    private void resumeInstallQueue() {
        if (!resumed || isDestroyed()) return;
        UpdateDownloads.Job job = downloads.active();
        if (job != null && job.installerOpened && job.covered && !job.launching) {
            checkPendingInstalls();
            // Resume cannot distinguish cancellation from failure without an Android result.
            if (rows.get(job.app).attributionMatch == null)
                rows.get(job.app).note = getString(R.string.install_incomplete);
            InstallManager.clearPending(this, job.app.packageName);
            downloads.finish(job);
        }
        if (!queuePaused) drainInstallQueue();
    }

    /** Confirmation UI launch is pending; only a real package change triggers the two-field check. */
    private void checkPendingInstalls() {
        if (busy) return;
        for (Releases.App app : Releases.App.values()) {
            InstallManager.PendingResult pending = InstallManager.pollPending(this, app.packageName);
            if (pending.status == InstallManager.PendingStatus.NONE) continue;
            Row row = rows.get(app);
            if (pending.status == InstallManager.PendingStatus.WAITING) {
                row.attributionMatch = null;
                row.note = getString(R.string.installer_waiting);
            } else if (pending.status == InstallManager.PendingStatus.FINISHED) {
                InstallManager.Outcome outcome = InstallManager.verifyAndFix(
                        this, InstallMode.CLASSIC, app.packageName, message -> { }, "");
                RepairState.needsShizuku(this, app.packageName, !outcome.ok);
                row.note = getString(R.string.installed_check_car);
                row.attributionMatch = outcome.ok;
                Toast.makeText(this, getString(R.string.installed_check_car),
                        Toast.LENGTH_SHORT).show();
            } else {
                row.attributionMatch = null;
                row.note = getString(R.string.install_not_observed);
            }
            if (pending.repair && pending.status != InstallManager.PendingStatus.WAITING)
                new File(new File(getCacheDir(), "repairs"), app.packageName + ".apk").delete();
            render(app);
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

    private void showDiagnostics() { startActivity(new Intent(this,SupportActivity.class)); }

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
        refreshActions();
    }

    private void render(Releases.App app) {
        Row row = rows.get(app);
        PackageInfo current = Releases.installed(getPackageManager(), app);
        Releases.Release release = row.release;
        boolean available = release != null && release.isNewer(current);
        row.version.setText(current == null ? getString(R.string.not_installed)
                : getString(R.string.installed_version, android.text.BidiFormatter.getInstance(getResources().getConfiguration().getLocales().get(0))
                        .unicodeWrap(current.versionName, android.text.TextDirectionHeuristics.LTR)));
        String detail = null;
        if (row.checked) {
            detail = release == null ? getString(R.string.no_release)
                    : available ? getString(R.string.available_version, release.version)
                    + (release.preview ? " · " + getString(R.string.preview) : "")
                    : getString(R.string.up_to_date);
        }
        if (row.checkedAt > 0) {
            String when = java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
                    getResources().getConfiguration().getLocales().get(0))
                    .format(new java.util.Date(row.checkedAt));
            detail += "\n" + getString(R.string.last_successful_check, when);
        }
        if (row.error != null) detail = detail == null ? row.error : detail + "\n" + row.error;
        // Technical outcomes remain available inside Help with a problem.
        String sourceStatus = row.note != null ? row.note : detail;
        String status = sourceStatus == null ? "" : sourceStatus;
        row.status.setText(row.operation == null ? status
                : getString(R.string.checking).equals(row.operation) && row.checked
                    ? status + "\n" + row.operation : row.operation);
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
        row.repair.setVisibility(busy ? View.GONE : View.VISIBLE);
        row.repair.setEnabled(!busy);
        UpdateDownloads.Job job = downloads.get(app);
        boolean queued = downloads.occupied(app);
        if (!busy && checkCalls.containsKey(app) && !queued) {
            row.status.setText(status + "\n" + getString(R.string.checking));
            row.progress.setVisibility(View.VISIBLE); row.progress.setIndeterminate(true);
        }
        if (job != null && !busy) {
            int message = job.state == UpdateDownloads.State.DOWNLOADING ? R.string.preparing
                    : job.state == UpdateDownloads.State.READY ? R.string.queued_install
                    : job.state == UpdateDownloads.State.INSTALLING ? R.string.installer_waiting
                    : job.state == UpdateDownloads.State.CANCELLED ? R.string.cancelled : R.string.download_failed_retry;
            row.status.setText(row.error != null ? row.error : getString(message));
            boolean downloading = job.state == UpdateDownloads.State.DOWNLOADING;
            row.progress.setVisibility(downloading ? View.VISIBLE : View.GONE);
            row.progress.setIndeterminate(job.bytes == 0);
            if (downloading && job.bytes > 0) {
                int percent = (int)Math.min(100, job.bytes * 100 / job.release.size);
                row.progress.setProgress(percent);
                row.status.setText(percent == 100 ? getString(R.string.verifying) : getString(R.string.downloading, percent));
            }
            boolean cancellable = downloading || job.state == UpdateDownloads.State.READY;
            row.cancel.setVisibility(cancellable ? View.VISIBLE : View.GONE);
            row.cancel.setEnabled(cancellable);
            row.install.setEnabled(!queued || (queuePaused && job.state == UpdateDownloads.State.READY));
            if (queuePaused && job.state == UpdateDownloads.State.READY) row.install.setText(R.string.continue_action);
        }
        row.more.setEnabled(!busy && !downloads.hasWork());
        row.manual.setEnabled(!busy && !downloads.hasWork());
        if (app == Releases.App.AUTO) {
            row.card.setVisibility(queued || available || row.error != null || row.operation != null || row.note != null || pendingLocal
                    ? View.VISIBLE : View.GONE);
            if (!queued && !available && !pendingLocal) row.install.setVisibility(View.GONE);
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
        return UiStyle.button(this, title, primary);
    }

    private void showSettings() {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), dp(16));
        Button support = action(getString(R.string.diagnostics), false);
        support.setOnClickListener(v -> showDiagnostics());
        content.addView(support, UiStyle.actionParams(this));
        updateAlerts = new Switch(this);
        updateAlerts.setText(R.string.daily_alerts);
        updateAlerts.setTextColor(inkColor);
        updateAlerts.setMinHeight(dp(52));
        updateAlerts.setOnClickListener(v -> changeUpdateAlerts());
        LinearLayout alerts = column();
        alerts.setPadding(dp(12), dp(4), dp(12), dp(12));
        alerts.setBackground(UiStyle.surface(this, surfaceColor, 14, UiStyle.border(this)));
        alerts.addView(updateAlerts);
        alerts.addView(text(getString(R.string.daily_description), 14, mutedColor, false));
        content.addView(alerts, UiStyle.actionParams(this));
        refreshAlertsSwitch();
        Button appearance = action(getString(R.string.appearance), false);
        appearance.setOnClickListener(v -> choosePreference("theme", R.string.appearance,
                new String[]{getString(R.string.system), getString(R.string.light), getString(R.string.dark)},
                new String[]{"system", "light", "dark"}));
        content.addView(appearance, UiStyle.actionParams(this));
        Button language = action(getString(R.string.language), false);
        language.setOnClickListener(v -> choosePreference("language", R.string.language,
                new String[]{getString(R.string.system), "English", "العربية"}, new String[]{"system", "en", "ar"}));
        content.addView(language, UiStyle.actionParams(this));
        Button self = action("MM Auto", false);
        self.setOnClickListener(v -> showVersion(Releases.App.AUTO));

        Button help = action(getString(R.string.troubleshoot), false);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(R.string.troubleshoot)
                .setItems(new String[]{"MM Auto", "MM IPTV", "MM Tube"}, (d, i) -> showHelp(Releases.App.values()[i])).show());

        Button advanced = action(getString(R.string.advanced), false);
        advanced.setOnClickListener(v -> {
            LinearLayout panel = column();
            panel.setPadding(dp(12), dp(12), dp(12), 0);
            if(self.getParent()!=null)((android.view.ViewGroup)self.getParent()).removeView(self);
            if(help.getParent()!=null)((android.view.ViewGroup)help.getParent()).removeView(help);
            panel.addView(self, UiStyle.actionParams(this));
            panel.addView(help, UiStyle.actionParams(this));
            Button guide = action(getString(R.string.guide_title), false);
            guide.setOnClickListener(clicked -> showShizukuGuide(null));
            panel.addView(guide, UiStyle.actionParams(this));
            ScrollView advancedScroll=new ScrollView(this);advancedScroll.addView(panel);
            new AlertDialog.Builder(this).setTitle(R.string.advanced).setView(advancedScroll)
                    .setNegativeButton(R.string.close, null).show();
        });
        content.addView(advanced, UiStyle.actionParams(this));
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
        SupportEvents.event("download",app.packageName,"cancelled",InstallManager.mode(this).name());
        if (isDestroyed()) return;
        rows.get(app).note = getString(R.string.cancelled);
        setBusy(false);
        rows.get(app).status.setText(R.string.cancelled);
    }

    private void chooseApk(Releases.App app) {
        if (busy || downloads.hasWork()) return;
        pickingApp = app;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { startActivityForResult(pick, PICK_APK); }
        catch (RuntimeException error) { pickingApp = null; Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request >= 1000 && request < 64000) {
            UpdateDownloads.Job job = downloads.active();
            if (job != null && job.mode == InstallMode.CLASSIC && job.requestCode == request) {
                checkPendingInstalls();
                if (rows.get(job.app).attributionMatch == null)
                    rows.get(job.app).note = getString(result == RESULT_CANCELED
                            ? R.string.install_cancelled : R.string.install_incomplete);
                InstallManager.clearPending(this, job.app.packageName);
                downloads.finish(job);
            }
            return;
        }
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
                SupportEvents.event("download",app.packageName,"started",InstallManager.mode(this).name());
                apk = ManualApk.prepare(app, input, getCacheDir(), getPackageManager(), () -> cancelPreparation || isDestroyed());
                SupportEvents.event("download",app.packageName,"ready",InstallManager.mode(this).name());
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
                SupportEvents.event("download",app.packageName,"failed",InstallManager.mode(this).name());
                if (apk != null) apk.delete();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (cancelPreparation) { finishCancelled(app); return; }
                    InstallManager.recordFailure(this, app.packageName, error.toString());
                    rows.get(app).note = getString(R.string.prepare_failed);
                    rows.get(app).error = getString(R.string.prepare_failed);
                    setBusy(false);
                });
            }
        });
    }

    private void installLocal(Releases.App app, File verified) { installLocal(app, verified, InstallMode.CLASSIC); }

    private void installLocal(Releases.App app, File verified, InstallMode mode) {
        if (busy || downloads.hasWork()) return;
        getSharedPreferences("ui", MODE_PRIVATE).edit().remove("pending_local").apply();
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
            beginSetup(app, PendingSetup.Kind.LOCAL); return;
        }
        if (mode == InstallMode.SHIZUKU) {
            openShizukuInstaller(app, ApkProvider.uri(getPackageName(), app), false);
            return;
        }
        if (mode == InstallMode.CLASSIC) {
            try {
                InstallManager.markPending(this, app.packageName);
                markStandalone(app);
                startActivity(InstallManager.classicIntent(ApkProvider.uri(getPackageName(), app), app.title));
            } catch (RuntimeException error) {
                InstallManager.clearPending(this, app.packageName);
                clearStandalone();
                rows.get(app).note = getString(R.string.install_failed);
                showHelp(app);
            }
            return;
        }
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
        if (PendingSetup.read(this) != null || busy || downloads.hasWork() || isDestroyed() || (localConfirmation != null && localConfirmation.isShowing())) return;
        Releases.App app = pendingLocalApp();
        if (app == null) return;
        boolean ready = getPackageManager().canRequestPackageInstalls();
        rows.get(app).note = getString(ready ? R.string.ready_install
                : R.string.allow_install_description);
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
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){UiStyle.systemBars(this);SupportNotice.show(this,null);}}
}
