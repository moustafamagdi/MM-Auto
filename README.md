# MM Auto

Android 11+ companion for MM IPTV and MM Tube. It checks for updates to itself, MM IPTV and MM Tube at launch and when you tap **Check for updates**. The MM Auto card shows the installed version and offers **Update** when a newer compatible GitHub release is available, including previews. For MM IPTV and MM Tube, a compatible stable release takes priority. Install or update opens Android's installation confirmation after checking download size, the GitHub checksum when available, the package ID, the pinned signing certificate, and the installed version code. The app rejects APKs with a preconfigured IPTV account.

Download the signed APK from [GitHub Releases](https://github.com/moustafamagdi/MM-Auto/releases).

## Build

Use JDK 17, Android SDK API 36 and build tools 36.0.0. Set `ANDROID_HOME` or configure `sdk.dir` in `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat`.

## Release assets

Publish one signed public APK per release:

- MM-Auto: `MM-Auto-<version>.apk`
- MagdiIPTV: `MM-IPTV-<version>.apk`
- MagdiTube: `MM-Tube-<version>.apk`

Set the GitHub asset label to `versionCode=<integer>` for precise update discovery. Older official asset names are supported and use the numeric version in the APK filename for discovery. The actual APK version code is checked before installation.

Keep using the existing release signing certificate. Its SHA-256 fingerprint is pinned in the app.
