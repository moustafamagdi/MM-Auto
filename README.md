# MM Auto

Android 11+ companion for MM IPTV and MM Tube. It checks their public GitHub Releases at launch and on demand. Install or update opens Android's installation confirmation after checking download size, the GitHub checksum when available, the package ID, the pinned signing certificate, and the installed version code. The app rejects APKs with a preconfigured IPTV account.

Download the signed APK from [GitHub Releases](https://github.com/moustafamagdi/MM-Auto/releases).

## Build

Use JDK 17, Android SDK API 36 and build tools 36.0.0. Set `ANDROID_HOME` or configure `sdk.dir` in `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat`.

## Release assets

Publish one signed public APK in the stable release of each existing repository:

- MagdiIPTV: `MM-IPTV-<version>.apk`
- MagdiTube: `MM-Tube-<version>.apk`

Set the GitHub asset label to `versionCode=<integer>` for precise update discovery. Older official asset names are supported and use the numeric version in the APK filename for discovery. The actual APK version code is checked before installation.

Keep using the existing release signing certificate. Its SHA-256 fingerprint is pinned in the app.
