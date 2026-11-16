# MM Auto

Android 11+ companion for MM IPTV and MM Tube. It checks for updates to itself, MM IPTV and MM Tube at launch and when you tap **Check for updates**. The MM Auto card shows the installed version and offers **Update** when a newer compatible GitHub release is available, including previews. For MM IPTV and MM Tube, a compatible stable release takes priority. Install or update opens Android's installation confirmation after checking download size, the GitHub checksum when available, the package ID, the pinned signing certificate, and the installed version code. The app rejects APKs with a preconfigured IPTV account.

Download the signed APK from [GitHub Releases](https://github.com/moustafamagdi/MM-Auto/releases).

## Install methods

MM Auto installs MM IPTV, MM Tube and itself the same way, aiming for **Installed by: Google Play Store** so Android Auto lists them. Pick a method under **Install method**:

1. **Classic** (try first): opens Android's installer and asks for the Play Store as installer. When you come back, MM Auto checks the install source and repairs it if Shizuku is ready.
2. **Shizuku**: installs silently through a Shizuku shell service (Shizuku, Shizuku-Next and compatible builds). Start Shizuku, choose this method and allow MM Auto when asked.
3. **Root**: installs silently through `su`. Needed on devices that block a non-root shell from setting the Play Store as installer, such as many Xiaomi, POCO and Redmi phones.

Every method runs only after the download passes the size, checksum, package ID, signing certificate and version checks above. **Diagnostics** shows the install source Android reports for each MM app and can share the report.

## Background update alerts

Turn on **Daily update alerts** and allow notifications to get an alert when an installed app has a newer release. MM Auto checks itself, MM IPTV and MM Tube. It checks roughly once a day with an internet connection and when the battery is not low. Android may delay the check to save power. The job persists across reboots and sends one alert per release for each app. Tapping an alert opens MM Auto to review the update.

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
