package com.magdi.auto;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.concurrent.*;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** A separate private ADB identity, only ever connected to loopback. Never enables tcpip:5555. */
final class InternalAdbClient extends AbsAdbConnectionManager {
    private final PrivateKey key;
    private final Certificate certificate;
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor();

    InternalAdbClient(Context context) throws Exception {
        setHostAddress("127.0.0.1"); setApi(Build.VERSION.SDK_INT);
        setTimeout(12, TimeUnit.SECONDS); setThrowOnUnauthorised(true);
        // Software RSA is required by the library's raw ADB authentication operation.
        // Store a single atomic identity only in app-private, excluded-from-backup storage.
        AtomicFile identity = new AtomicFile(new File(context.getNoBackupFilesDir(), "internal-adb.p12"));
        KeyStore store = KeyStore.getInstance("PKCS12");
        char[] password = new char[0];
        if (identity.getBaseFile().exists()) {
            try (InputStream input = identity.openRead()) { store.load(input, password); }
        } else {
            store.load(null, password);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            java.security.KeyPair pair = generator.generateKeyPair();
            Provider provider = new BouncyCastleProvider();
            X500Name subject = new X500Name("CN=MM Auto local installer");
            long now = System.currentTimeMillis();
            Certificate cert = new JcaX509CertificateConverter().setProvider(provider).getCertificate(
                    new JcaX509v3CertificateBuilder(subject, new BigInteger(128, new SecureRandom()),
                            new Date(now - 86400000), new Date(now + 10L * 365 * 86400000), subject, pair.getPublic())
                            .build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(pair.getPrivate())));
            store.setKeyEntry("local-adb", pair.getPrivate(), password, new Certificate[]{cert});
            FileOutputStream output = null;
            try { output = identity.startWrite(); store.store(output, password); identity.finishWrite(output); }
            catch (Exception failure) { if (output != null) identity.failWrite(output); throw failure; }
        }
        key = (PrivateKey) store.getKey("local-adb", password); certificate = store.getCertificate("local-adb");
        if (key == null || certificate == null) throw new IOException("LOCAL_IDENTITY_UNAVAILABLE");
    }
    @Override protected PrivateKey getPrivateKey() { return key; }
    @Override protected Certificate getCertificate() { return certificate; }
    @Override protected String getDeviceName() { return "MM-Auto"; }
    @Override public void close() throws IOException { disconnect(); }

    boolean connectLocal(int port) throws Exception { return connect("127.0.0.1", port); }
    boolean pairLocal(int port, String code) throws Exception { return pair("127.0.0.1", port, code); }

    static int discover(Context context, boolean pairing) throws Exception {
        java.util.concurrent.atomic.AtomicInteger found = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch result = new CountDownLatch(1);
        AdbMdns discovery = new AdbMdns(
                context, pairing ? AdbMdns.SERVICE_TYPE_TLS_PAIRING : AdbMdns.SERVICE_TYPE_TLS_CONNECT, (host, port) -> {
                    // Library filters hosts to the phone's own interfaces. We use only the port
                    // and still connect strictly to 127.0.0.1, never the discovered LAN address.
                    if (host != null && port > 0 && port <= 65535) { found.set(port); result.countDown(); }
                });
        try {
            discovery.start();
            if (!result.await(8, TimeUnit.SECONDS)) throw new IOException("DISCOVERY_UNAVAILABLE");
            return found.get();
        } finally { discovery.stop(); }
    }

    String install(Releases.App app, File apk) throws Exception {
        int user = android.os.Process.myUid() / 100000;
        long length = apk.length();
        Thread runner = Thread.currentThread();
        ScheduledFuture<?> deadline = TIMER.schedule(() -> {
            runner.interrupt();
            try { close(); } catch (IOException ignored) { }
        }, 120, TimeUnit.SECONDS);
        try (AdbStream stream = openStream(InternalAdbPolicy.installCommand(app, user, length));
             InputStream input = new FileInputStream(apk)) {
            // Smaller than ADB's minimum maxData; exactly one WRTE per acknowledgement.
            byte[] chunk = new byte[4096]; long sent = 0; int count;
            while ((count = input.read(chunk)) != -1) {
                if (Thread.currentThread().isInterrupted() || sent + count > length) throw new IOException("TRANSFER_INTERRUPTED");
                stream.write(chunk, 0, count); stream.flush(); sent += count;
            }
            if (sent != length) throw new IOException("APK_CHANGED");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while ((count = stream.read(chunk, 0, chunk.length)) != -1) {
                if (output.size() + count > 16384) throw new IOException("RESULT_TOO_LARGE");
                output.write(chunk, 0, count);
            }
            return output.toString("UTF-8");
        } finally { deadline.cancel(false); Thread.interrupted(); }
    }
}
