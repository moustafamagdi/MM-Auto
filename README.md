# MM Auto

**Install, update and repair install-source attribution for the MM Android Auto companion apps.**

[![Release](https://img.shields.io/github/v/release/moustafamagdi/MM-Auto?include_prereleases&label=release)](https://github.com/moustafamagdi/MM-Auto/releases)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84)
![Java](https://img.shields.io/badge/language-Java-ED8B00)
![Experimental](https://img.shields.io/badge/status-experimental-F5B942)

![MM Auto project overview: discover official releases, verify APKs, install and inspect the source.](docs/images/overview.svg)

The official source and release repository for **MM Auto**, maintained by [moustafamagdi](https://github.com/moustafamagdi). Manage **MM Auto, MM IPTV and MM Tube** on Android 11 and later using Android's installer, Shizuku or an optional root installation path.

**[Download signed APKs](https://github.com/moustafamagdi/MM-Auto/releases) · [Getting started](#getting-started) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Report a problem](https://github.com/moustafamagdi/MM-Auto/issues/new/choose)**

> **Experimental software.** Install-source attribution is one part of Android Auto compatibility. A “ready” result in Diagnostics does not guarantee visibility or playback. This independent project is not affiliated with Google, Android Auto or Shizuku.

## What it does

- **Discover updates** from the official GitHub repositories, including compatible preview filenames.
- **Verify APKs** before installation: package, pinned signing certificate, size, checksum when supplied by GitHub, and version code. Reject private initial-account assets.
- **Choose Classic, Shizuku or Root** for installation.
- **Repair existing installations** by reinstalling the exact installed version without uninstalling or clearing app data. The repair operation uses a verified local copy, without a GitHub download.
- **Inspect Diagnostics** for versions, installer attribution and Shizuku availability.
- **Enable daily update alerts** for scheduled background checks when Android allows them.

## Supported apps

| App | Package | Official source |
| --- | --- | --- |
| MM Auto | `com.magdi.auto` | [MM-Auto](https://github.com/moustafamagdi/MM-Auto) |
| MM IPTV | `com.magdi.iptv` | [MagdiIPTV](https://github.com/moustafamagdi/MagdiIPTV) |
| MM Tube | `com.magdi.tube` | [MagdiTube](https://github.com/moustafamagdi/MagdiTube) |

MM Auto is designed for these apps, not arbitrary APKs. It does not supply IPTV accounts, playlists or video content.

## Getting started

1. Open [Releases](https://github.com/moustafamagdi/MM-Auto/releases) and download `MM-Auto-<version>.apk`. Current releases are experimental/prereleases. The source ZIP is not an installable app.
2. Install on Android 11 or later. To update an official MM Auto installation, install over it rather than uninstalling it.
3. Open MM Auto, tap **Check for updates**, and choose your installation method.
4. Tap **Install** or **Update** and follow the permission prompts. After Classic installation, return to MM Auto.
5. Open **Diagnostics** to inspect attribution, then test the target app in your Android Auto setup while parked.

For MM Auto itself, discovery considers compatible previews. For IPTV and Tube, a compatible stable release takes priority. **Update** appears only when the selected release is newer than the installed version.

## Installation methods

| Method | Requirements | Behavior |
| --- | --- | --- |
| **Classic** | Allow “Install unknown apps” for MM Auto | Opens Android's installer and requests Play Store attribution. Device behavior varies. |
| **Shizuku** | [Shizuku](https://github.com/RikkaApps/Shizuku) running and permission granted | Uses a shell service with `pm install -r -i com.android.vending`. No root is required on the tested Samsung device. |
| **Root** | An existing root setup and `su` permission | Uses the root shell installation path. **Untested**; compatibility is not guaranteed. |

Normal installation flows can attempt source repair afterward. When `pm set-installer` is refused, the cached-download fallback can reinstall a matching verified APK through an available privileged channel. The dedicated repair button follows the separate Classic-first flow below.

## Repair install source

For an already installed MM app, you do not need to delete it or wait for a newer version:

1. Tap **Repair install source** on its card.
2. Allow installs from MM Auto if prompted, and complete the **Classic** installer.
3. Return to MM Auto. If Android reports Google Play Store as installer, repair finishes without Shizuku.
4. Otherwise, choose **Use Shizuku**, **Try Classic again**, or **Cancel**. Shizuku requires permission and separate confirmation; this repair flow does not invoke it automatically.

Repair verifies the installed APK's package, exact version code and pinned signer, enforces a size limit, checks SHA-256 copy integrity, and rejects private account assets. Split APKs are not supported. The repaired app closes during replacement. Cancelling the installer does not count as success; pending verification expires after 30 minutes.

## Tested behavior and limits

| Scenario | Status |
| --- | --- |
| Shizuku installation through MM Auto | Verified on Samsung SM-S948B, Android 17/API 37, Shizuku 13.5 running as adb. Tube received Play Store attribution. |
| Cached-download silent reinstall fallback | Verified on that device by starting with incorrect attribution and observing the reinstall success message and corrected source. |
| Classic installation | Correct attribution observed. Retained-data uninstall may preserve attribution, so this does not prove all Classic installs honor the installer extra. |
| Android Auto launcher visibility | Tube and IPTV appeared in a user-provided Desktop Head Unit screenshot. Playback was not verified. |
| Dedicated Classic-first Repair button | Built and unit-tested; end-to-end device validation remains outstanding. |
| Root, Xiaomi/POCO/Redmi and other ROMs | Untested. |
| Self-update/self-repair and settings preservation | Not fully verified. |
| Android Auto video playback | Not verified. |

The current source has **23 passing unit tests** covering shell I/O/timeouts, repair validation, pending repair state and APK filename matching. Unit tests do not replace device testing.

## Security and privacy

Release installation is restricted to known repositories, allowed HTTPS hosts and the official signing identity. The pinned certificate SHA-256 is:

```text
6d9d30d8f427a6cfc0241831d1e324e684fe2aefda72925c89ef872bf3a5ab65
```

GitHub API checks are unauthenticated. Do not embed a personal access token in the APK. Rate limits can temporarily stop update discovery; uninstalling apps does not resolve them. See [Troubleshooting](docs/TROUBLESHOOTING.md).

Diagnostics includes device/build information and app versions. Review it before sharing. Never post passwords, signing keys, tokens, private account assets or playlists in issues.

## Build and contribute

Use **JDK 17**, **Android SDK API 36**, **build tools 36.0.0** and **minSdk 30**. The app is written in Java.

```sh
git clone https://github.com/moustafamagdi/MM-Auto.git
cd MM-Auto
# Configure sdk.dir in your untracked local.properties first.
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

On Windows, use `gradlew.bat`. Debug builds use `com.magdi.auto.debug` and coexist with the signed release; they have a different signing identity.

See [CONTRIBUTING.md](CONTRIBUTING.md) and [the release checklist](docs/RELEASING.md). Use issue templates for reproducible reports.

## License and acknowledgments

No project license has been selected yet. Public visibility alone is not an open-source license; contact the maintainer before reusing or redistributing project code. Dependencies retain their own licenses.

MM Auto uses the [Shizuku API](https://github.com/RikkaApps/Shizuku-API). The overview illustration is an original repository asset based on MM Auto's icon, not an app screenshot.
