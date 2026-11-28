package com.magdi.auto;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import java.util.Locale;
import java.util.UUID;

/** User-started, bounded pairing only. Inline replies never launch an Activity or install an APK. */
public final class InternalPairingService extends Service {
    static final String CHANNEL = "internal_adb_pairing";
    static final int NOTIFICATION = 77132;
    static final String START = "mm.pairing.START", REPLY = "mm.pairing.REPLY", STOP = "mm.pairing.STOP";
    static final String CODE = "pairing_code";
    static final long WINDOW_MS = 150_000; // Below shortService's three-minute OS deadline.
    private final Handler main = new Handler(Looper.getMainLooper());
    private Releases.App target;
    private String session;
    private boolean pairing, finished, waiting;
    private io.github.muntashirakon.adb.android.AdbMdns discovery;
    private boolean readyAlerted;
    static final long DISCOVERY_MS = 120_000;

    static final long WAIT_MS = 10 * 60_000;
    private PendingIntent replyIntent, stopIntent;
    private static volatile Releases.App waitingApp;
    static Releases.App waitingApp() { return waitingApp; }

    @Override protected void attachBaseContext(Context base) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        String lang = base.getSharedPreferences("ui", 0).getString("language", "system");
        if (!"system".equals(lang)) {
            Locale locale = Locale.forLanguageTag(lang);
            configuration.setLocale(locale); configuration.setLayoutDirection(locale);
        }
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }
    static void channel(Context c) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, c.getString(R.string.pair_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static Intent start(Context c, Releases.App app) {
        return new Intent(c, InternalPairingService.class).setAction(START).putExtra("app", app.name());
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !BuildConfig.VERSION_NAME.contains("preview")) { stopSelf(); return START_NOT_STICKY; }
        if (START.equals(intent.getAction())) {
            if (pairing || InternalInstallOperation.busy()) { if (session == null) stopSelf(); return START_NOT_STICKY; }
            Releases.App requested;
            try { requested = InternalAdbPolicy.target(intent.getStringExtra("app")); }
            catch (IllegalArgumentException invalid) { stopSelf(); return START_NOT_STICKY; }
            if (!requested.name().equals(InternalInstallOperation.prefs(this).getString("app", ""))) { stopSelf(); return START_NOT_STICKY; }
            stopDiscovery(); cancelActions();
            target = requested; waitingApp = requested; session = UUID.randomUUID().toString(); finished = false; readyAlerted = false;
            channel(this);
            waiting = true;
            getSharedPreferences("pair_ticket",0).edit().putString("session",session).putString("app",target.name())
                    .putLong("expires",System.currentTimeMillis()+WAIT_MS).commit();
            promote(notification(R.string.pair_notification_wait, true));
            watchForPairing();
            return START_NOT_STICKY;
        }
        // A waiting notification may restart this service, but an accepted code is consumed once.
        if (session == null) {
            android.content.SharedPreferences ticket=getSharedPreferences("pair_ticket",0);
            if (ticket.getLong("expires",0)>System.currentTimeMillis()
                    && intent.getStringExtra("session") != null
                    && intent.getStringExtra("session").equals(ticket.getString("session",""))) {
                try { target=InternalAdbPolicy.target(ticket.getString("app","")); session=ticket.getString("session",null); }
                catch(IllegalArgumentException ignored) { }
            }
        }
        // No replay after acceptance, expiry or a different selected app.
        if (!validSession(intent)) { if (!pairing) stopSelf(); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) {
            stopDiscovery();
            getSharedPreferences("pair_ticket",0).edit().clear().commit();
            if (pairing) InternalInstallOperation.cancel(this);
            finished = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        } else if (REPLY.equals(intent.getAction()) && !pairing) {
            if (InternalInstallOperation.busy()) {
                stopSelf(); return START_NOT_STICKY;
            }
            stopDiscovery();
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            CharSequence input = results == null ? null : results.getCharSequence(CODE);
            String code;
            try { code = InternalAdbPolicy.code(input == null ? null : input.toString().trim()); }
            catch (IllegalArgumentException invalid) {
                waiting = true;
                promote(notification(R.string.internal_invalid_code, true));
                stopForeground(STOP_FOREGROUND_DETACH); stopSelf(); return START_NOT_STICKY;
            } finally {
                // Do not retain incoming RemoteInput extras/ClipData in the Service.
                if (results != null) results.clear();
                intent.setClipData(null);
            }
            waiting = false;
            getSharedPreferences("pair_ticket",0).edit().clear().commit();
            pairing = true;
            // Reset bounded foreground window on this explicit user interaction.
            try { promote(notification(R.string.internal_pairing, false)); }
            catch (RuntimeException unavailable) { finishResult(R.string.pair_notification_expired); return START_NOT_STICKY; }
            main.removeCallbacks(expire); main.postDelayed(expire, WINDOW_MS);
            InternalInstallOperation.pair(this, target, 0, code);
            main.post(poll);
        }
        return START_NOT_STICKY;
    }
    private void watchForPairing() {
        final String ticket = session;
        main.postDelayed(stopWatching, DISCOVERY_MS);
        try {
            discovery = new io.github.muntashirakon.adb.android.AdbMdns(this,
                    io.github.muntashirakon.adb.android.AdbMdns.SERVICE_TYPE_TLS_PAIRING,
                    (host,port)->{if(host!=null&&port>0&&port<=65535)main.post(()->pairingReady(ticket));});
            discovery.start();
        } catch(RuntimeException unavailable) { stopWatching.run(); }
    }
    void pairingReady(String ticket) {
        if (!waiting || pairing || finished || readyAlerted || session==null || !session.equals(ticket)
                || !session.equals(getSharedPreferences("pair_ticket",0).getString("session",""))
                || getSharedPreferences("pair_ticket",0).getLong("expires",0)<=System.currentTimeMillis()) return;
        readyAlerted = true;
        notifyStatus(R.string.pair_notification_ready, true);
        stopWatching.run();
    }
    private void stopDiscovery() {
        main.removeCallbacks(stopWatching);
        if(discovery!=null){try{discovery.stop();}catch(RuntimeException ignored){}discovery=null;}
    }
    private final Runnable stopWatching = () -> {
        stopDiscovery();
        if(waiting&&!pairing){stopForeground(STOP_FOREGROUND_DETACH);stopSelf();}
    };
    private boolean validSession(Intent intent) {
        return !finished && (pairing || getSharedPreferences("pair_ticket",0).getLong("expires",0)>System.currentTimeMillis()) && session != null && session.equals(intent.getStringExtra("session"))
                && target != null && target.name().equals(InternalInstallOperation.prefs(this).getString("app", ""));
    }
    private void promote(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(NOTIFICATION, notification);
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (finished || !pairing) return;
            if (InternalInstallOperation.busy()) { main.postDelayed(this, 250); return; }
            pairing = false;
            String state = InternalInstallOperation.state(InternalPairingService.this);
            finishResult("paired".equals(state) ? R.string.pair_notification_success : R.string.pair_notification_failed);
        }
    };
    private final Runnable expire = () -> {
        if (pairing) InternalInstallOperation.cancel(this);
        finishResult(R.string.pair_notification_expired);
    };
    private void finishResult(int message) {
        finished = true; pairing = false;
        main.removeCallbacksAndMessages(null);
        cancelActions();
        Notification result = notification(message, false);
        stopForeground(STOP_FOREGROUND_DETACH);
        getSystemService(NotificationManager.class).notify(NOTIFICATION, result);
        stopSelf();
    }
    private void notifyStatus(int message, boolean allowReply) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(message, allowReply));
    }
    private Notification notification(int message, boolean allowReply) {
        Intent resume = new Intent(this, InternalInstallActivity.class)
                .setData(Uri.parse("mm-auto://pairing/" + session)).putExtra("app", target.name())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(this, NOTIFICATION, resume,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = getString(R.string.pair_notification_title, target.title);
        String text = getString(message);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_auto).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setOnlyAlertOnce(message != R.string.pair_notification_ready).setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOngoing(!finished).setAutoCancel(finished).setTimeoutAfter(waiting ? WAIT_MS : WINDOW_MS)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (allowReply) {
            Intent reply = new Intent(this, InternalPairingService.class).setAction(REPLY)
                    .setData(Uri.parse("mm-auto://pairing/" + session)).putExtra("session", session);
            int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            replyIntent = PendingIntent.getForegroundService(this, NOTIFICATION, reply, PendingIntent.FLAG_UPDATE_CURRENT | mutable);
            RemoteInput input = new RemoteInput.Builder(CODE).setLabel(getString(R.string.internal_pair_code)).build();
            Notification.Action.Builder action = new Notification.Action.Builder(null, getString(R.string.pair_notification_reply), replyIntent)
                    .addRemoteInput(input).setAllowGeneratedReplies(false);
            if (Build.VERSION.SDK_INT >= 31) action.setAuthenticationRequired(true);
            builder.addAction(action.build());
        }
        if (!finished) {
            Intent stop = new Intent(this, InternalPairingService.class).setAction(STOP)
                    .setData(Uri.parse("mm-auto://pairing/" + session)).putExtra("session", session);
            stopIntent = PendingIntent.getService(this, NOTIFICATION + 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, getString(R.string.cancel), stopIntent).build());
        }
        return builder.build();
    }
    private void cancelActions() {
        if (replyIntent != null) { replyIntent.cancel(); replyIntent = null; }
        if (stopIntent != null) { stopIntent.cancel(); stopIntent = null; }
    }
    @Override public void onTimeout(int startId) { expire.run(); }
    @Override public void onTimeout(int startId, int fgsType) { expire.run(); }
    @Override public void onDestroy() {
        stopDiscovery();
        if (pairing && !finished) InternalInstallOperation.cancel(this);
        main.removeCallbacksAndMessages(null); if (!waiting) cancelActions(); waitingApp = null;
        super.onDestroy();
    }
}
