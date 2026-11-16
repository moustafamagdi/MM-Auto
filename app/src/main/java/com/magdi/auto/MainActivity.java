package com.magdi.auto;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

import java.util.EnumMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final EnumMap<Releases.App, Row> rows = new EnumMap<>(Releases.App.class);
    private Button check;
    private boolean busy;

    private static final class Row {
        TextView status;
        Button install;
        Releases.Release release;
        String error;
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

        addRow(page, Releases.App.IPTV, R.drawable.ic_iptv);
        addRow(page, Releases.App.TUBE, R.drawable.ic_tube);

        check = new Button(this);
        check.setText("Check for updates");
        check.setAllCaps(false);
        check.setOnClickListener(view -> checkReleases());
        page.addView(check, new LinearLayout.LayoutParams(-1, dp(52)));
        setContentView(scroll);
        checkReleases();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!busy) for (Releases.App app : rows.keySet()) render(app);
    }

    @Override protected void onDestroy() {
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
        for (Row row : rows.values()) row.status.setText("Checking GitHub…");
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
        if (!getPackageManager().canRequestPackageInstalls()) {
            row.status.setText("Allow installs from MM Auto, then tap again.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException unavailable) {
                row.status.setText("Android installation settings are unavailable.");
            }
            return;
        }

        setBusy(true);
        row.status.setText("Downloading and verifying the APK…");
        worker.execute(() -> {
            try {
                Releases.download(app, release, getCacheDir(), getPackageManager());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setBusy(false);
                    try {
                        Uri uri = ApkProvider.uri(getPackageName(), app);
                        Intent installer = new Intent(Intent.ACTION_VIEW);
                        installer.setDataAndType(uri, ApkProvider.MIME_TYPE);
                        installer.setClipData(ClipData.newRawUri(app.title, uri));
                        installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(installer);
                    } catch (RuntimeException unavailable) {
                        row.status.setText("Could not open the Android installer.");
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setBusy(false);
                    row.status.setText("Could not prepare the APK. " +
                            (failure.getMessage() == null ? "Try again." : failure.getMessage()));
                });
            }
        });
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
            detail = release == null ? "No supported APK in the latest release"
                    : available ? "Available " + release.version : "Up to date";
        }
        row.status.setText(detail == null ? installed : installed + "\n" + detail);
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
