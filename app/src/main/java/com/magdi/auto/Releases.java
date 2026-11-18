package com.magdi.auto;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Network;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

final class Releases {
    private static final long MAX_APK_BYTES = 100 * 1024 * 1024;
    private static final String OWNER = "moustafamagdi";
    private static final String SIGNER =
            "6d9d30d8f427a6cfc0241831d1e324e684fe2aefda72925c89ef872bf3a5ab65";
    private static final Pattern VERSION = Pattern.compile("^v?(\\d{1,8})\\.(\\d{1,8})\\.(\\d{1,8})(?:[-+].+)?$");
    private static final Set<String> DOWNLOAD_HOSTS = Set.of(
            "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com");

    enum App {
        AUTO("MM Auto", "com.magdi.auto", "MM-Auto", "MM-Auto"),
        IPTV("MM IPTV", "com.magdi.iptv", "MagdiIPTV", "(?:MM|Magdi)-IPTV"),
        TUBE("MM Tube", "com.magdi.tube", "MagdiTube", "(?:MM|Magdi)-Tube");

        final String title;
        final String packageName;
        final String repo;
        final Pattern assetName;

        App(String title, String packageName, String repo, String assetPrefix) {
            this.title = title;
            this.packageName = packageName;
            this.repo = repo;
            assetName = Pattern.compile(assetPrefix
                    + "-(\\d{1,8}\\.\\d{1,8}\\.\\d{1,8}(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?)\\.apk");
        }
    }

    static final class Release {
        final String version;
        final long versionCode;
        final URL url;
        final long size;
        final String digest;
        final boolean preview;

        Release(String version, long versionCode, URL url, long size, String digest, boolean preview) {
            this.version = version;
            this.versionCode = versionCode;
            this.url = url;
            this.size = size;
            this.digest = digest;
            this.preview = preview;
        }

        boolean isNewer(PackageInfo installed) {
            if (installed == null) return true;
            if (versionCode > 0) return versionCode > installed.getLongVersionCode();
            return compareVersions(version, installed.versionName) > 0;
        }
    }

    static PackageInfo installed(PackageManager pm, App app) {
        try {
            return pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    static Release latest(App app) throws Exception {
        return latest(app, null);
    }

    static Release latest(App app, Network network) throws Exception {
        String endpoint = "https://api.github.com/repos/" + OWNER + "/" + app.repo + "/releases";
        if (app != App.AUTO) {
            String latest = releaseResponse(endpoint + "/latest", network);
            if (latest != null) {
                Release stable = parse(app, new JSONObject(latest));
                if (stable != null) return stable;
            }
        }

        String history = releaseResponse(endpoint + "?per_page=30", network);
        return history == null ? null : selectRelease(app, new JSONArray(history));
    }

    static Release selectRelease(App app, JSONArray releases) throws Exception {
        Release selected = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject entry = releases.getJSONObject(i);
            Release candidate = parse(app, entry);
            if (candidate == null) continue;
            if (app != App.AUTO && !candidate.preview) return candidate;
            if (selected == null || (app == App.AUTO && newerRelease(candidate, selected))) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static boolean newerRelease(Release candidate, Release current) {
        if (candidate.versionCode > 0 && current.versionCode > 0) {
            return candidate.versionCode > current.versionCode;
        }
        return compareVersions(candidate.version, current.version) > 0;
    }

    private static String releaseResponse(String endpoint, Network network) throws IOException {
        HttpURLConnection connection = connect(new URL(endpoint), "application/vnd.github+json", network);
        try {
            int status = connection.getResponseCode();
            if (status == 404) return null;
            if (status != 200) throw new IOException("GitHub returned HTTP " + status + ".");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                copy(input, body, 1024 * 1024, null);
                return new String(body.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    static Release parse(App app, JSONObject release) throws Exception {
        if (release.optBoolean("draft")) return null;
        JSONArray assets = release.getJSONArray("assets");
        Release selected = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            Matcher name = app.assetName.matcher(asset.getString("name"));
            if (!name.matches() || !"uploaded".equals(asset.optString("state"))) continue;
            URL download = new URL(asset.getString("browser_download_url"));
            if (!safeUrl(download, Set.of("github.com")) ||
                    !download.getPath().startsWith("/" + OWNER + "/" + app.repo + "/releases/download/")) {
                throw new IOException("Unexpected release download address.");
            }
            long size = asset.getLong("size");
            if (size <= 0 || size > MAX_APK_BYTES) throw new IOException("Invalid release APK size.");
            String digest = asset.isNull("digest") ? "" : asset.optString("digest");
            if (!digest.isEmpty() && !digest.matches("sha256:[a-fA-F0-9]{64}")) {
                throw new IOException("Invalid release checksum.");
            }
            if (selected != null) throw new IOException("More than one official APK was found.");
            Matcher label = Pattern.compile("^versionCode=(\\d{1,10})$").matcher(asset.optString("label"));
            long versionCode = label.matches() ? Long.parseLong(label.group(1)) : 0;
            selected = new Release(name.group(1), versionCode, download, size, digest, release.optBoolean("prerelease"));
        }
        return selected;
    }

    static File download(App app, Release release, File cache, PackageManager pm) throws Exception {
        File directory = new File(cache, "releases");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Download storage is unavailable.");
        File temp = File.createTempFile(app.packageName, ".apk", directory);
        File apk = new File(directory, app.packageName + ".apk");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            HttpURLConnection connection = openDownload(release.url);
            try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(temp)) {
                if (copy(input, output, release.size, digest) != release.size) {
                    throw new IOException("Download size does not match the release.");
                }
            } finally {
                connection.disconnect();
            }
            if (!release.digest.isEmpty() && !release.digest.equalsIgnoreCase("sha256:" + hex(digest.digest()))) {
                throw new IOException("Download checksum does not match the release.");
            }
            validate(app, release, temp, pm);
            if (!temp.renameTo(apk)) throw new IOException("Could not save the verified APK.");
            return apk;
        } finally {
            temp.delete();
        }
    }

    static void validate(App app, Release release, File file, PackageManager pm) throws Exception {
        PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null || !app.packageName.equals(archive.packageName) || !officialSigner(archive)) {
            throw new IOException("APK package or signing certificate is incorrect.");
        }
        if (release.versionCode > 0 && release.versionCode != archive.getLongVersionCode()) {
            throw new IOException("APK version code does not match the release.");
        }
        PackageInfo current = installed(pm, app);
        if (current != null && !officialSigner(current)) {
            throw new IOException("The installed app uses a different signing certificate.");
        }
        if (current != null && archive.getLongVersionCode() <= current.getLongVersionCode()) {
            throw new IOException("The installed app is already current or newer.");
        }
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.getEntry("assets/initial-account.json") != null) {
                throw new IOException("The release APK contains a private account asset.");
            }
        }
    }

    private static boolean officialSigner(PackageInfo info) throws Exception {
        Signature[] signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        return signatures != null && signatures.length == 1 &&
                SIGNER.equals(hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())));
    }

    private static HttpURLConnection openDownload(URL url) throws IOException {
        for (int redirects = 0; redirects <= 4; redirects++) {
            if (!safeUrl(url, DOWNLOAD_HOSTS)) throw new IOException("Unexpected download host.");
            HttpURLConnection connection = connect(url, "application/octet-stream", null);
            try {
                int status = connection.getResponseCode();
                if (status == 200) return connection;
                if (status < 300 || status >= 400) throw new IOException("Download returned HTTP " + status + ".");
                String location = connection.getHeaderField("Location");
                if (location == null) throw new IOException("Download redirect is missing its destination.");
                url = new URL(url, location);
            } catch (IOException error) {
                connection.disconnect();
                throw error;
            }
            connection.disconnect();
        }
        throw new IOException("Too many download redirects.");
    }

    private static boolean safeUrl(URL url, Set<String> hosts) {
        return "https".equals(url.getProtocol()) && url.getUserInfo() == null &&
                (url.getPort() == -1 || url.getPort() == 443) && hosts.contains(url.getHost());
    }

    private static HttpURLConnection connect(URL url, String accept, Network network) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) (network == null
                ? url.openConnection() : network.openConnection(url));
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "MM-Auto-Android");
        return connection;
    }

    private static long copy(InputStream input, OutputStream output, long limit, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long size = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            size += read;
            if (size > limit) throw new IOException("Download exceeds the expected size.");
            output.write(buffer, 0, read);
            if (digest != null) digest.update(buffer, 0, read);
        }
        return size;
    }

    static int compareVersions(String first, String second) {
        Matcher a = VERSION.matcher(first == null ? "" : first);
        Matcher b = VERSION.matcher(second == null ? "" : second);
        if (!a.matches() || !b.matches()) return 0;
        for (int i = 1; i <= 3; i++) {
            int result = Integer.compare(Integer.parseInt(a.group(i)), Integer.parseInt(b.group(i)));
            if (result != 0) return result;
        }
        return 0;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
