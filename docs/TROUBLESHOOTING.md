# Troubleshooting

## “Could not check GitHub. Try again.”

This is a general discovery error, not evidence that installed apps are broken. Check internet access first. GitHub can return HTTP 403 when the unauthenticated quota for your public IP is exhausted; shared Wi-Fi users can share that quota. Wait for the reset time or use another network. Repeated taps are not a workaround. Do not uninstall apps to resolve an API error.

The dedicated **Repair install source** operation uses the installed APK locally and needs no release download. Update checks still require GitHub access. MM Auto does not ask for a GitHub token.

## A new update is not offered

- Check the installed version in Diagnostics. IPTV and Tube prefer compatible stable releases over previews.
- Use MM Auto 0.2.3 or later for suffixes such as `regional-login`, `keyboard` and `rc.1`.
- Assets must use the supported app prefix, numeric `major.minor.patch`, an optional hyphenated suffix, and `.apk`. Draft releases are excluded.
- More than one matching APK in a release is rejected as ambiguous.
- Maintainers should set the asset label to `versionCode=<integer>`. The downloaded APK is checked again before installation.

## Classic does not record Google Play Store

Complete the installer and return to MM Auto. For the dedicated repair button, MM Auto offers Shizuku when a completed Classic repair leaves the wrong attribution. Choose **Use Shizuku**, start Shizuku, grant permission and confirm. No root was needed on the tested Samsung device; other devices are untested.

If Android offers multiple installer apps, select the system **Package installer** when testing Classic. A third-party installer is a different path.

## Shizuku opens the system installer

In 0.2.7, Shizuku uses the system package installer confirmation UI through its UserService, rather than silent `pm install`. Confirm the install and return to MM Auto. Merely opening or cancelling the installer does not count as completed installation. APK read grants and repair files are kept until a package change is observed or the 30-minute verification window expires.

For Xiaomi/MIUI/HyperOS, check the Shizuku permission and the device's **Install via USB** and **USB debugging (Security settings)** developer options. This preview has not been physically validated on Xiaomi. A launch error is reported; MM Auto does not silently switch methods or clear data.

## Diagnostics shows an installation-source mismatch

Read **Installed by** and **Requested by** together. The expected pair is Play Store as installer and either Play Store or a resolved system installer as initiator. A shell, missing or unverified third-party initiator fails the check even if Installed by says Play Store. A MATCH is diagnostic only; Android Auto visibility and playback are still marked NOT_TESTED.

## Signature or version mismatch

Use an official signed release. Do not change the pinned certificate, disable verification or uninstall merely to bypass a mismatch. Debug builds have a different certificate and `.debug` package ID. Same-version repair requires the official signer and exact installed version; split APKs and private account assets are rejected.

## Repair was cancelled

Cancelling or returning before completion is not success and does not invoke Shizuku. Retry when ready. Pending Classic repair verification expires after 30 minutes.

## Reporting a problem

Open [a bug report](https://github.com/moustafamagdi/MM-Auto/issues/new?template=bug_report.yml) with MM Auto version, device/ROM, Android version, method, target app version and exact message. Review Diagnostics before sharing and redact personal information. Never include accounts, playlists, tokens or signing keys.
