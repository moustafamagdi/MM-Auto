# MM Auto 0.2.7 Shizuku preview

Shizuku installation, update and same-version repair now open the device's real system package installer. The user confirms in Android and returns to MM Auto. This follows KingInstaller's system-installer approach; MM Auto uses the Shizuku UserService / `am start` route. It does not include King's separate hidden-Binder launcher. No KingInstaller source routines are included.

Reference reviewed: [KingInstaller ShizukuUtils](https://github.com/fcaronte/KingInstaller/blob/e1b843ac6bcc97f663dfbbe18e7b4884c3962752/app/src/main/java/com/example/kinginstaller/ShizukuUtils.kt).

## Dispatch and verification

- Resolve an enabled, exported installer activity belonging to a system app. A third-party APK handler cannot receive this flow.
- Pass only a verified MM APK URI from MM Auto's private provider. Grant read-only access to system installer handlers and the shell; never expose a filesystem path or enable provider export.
- Open the explicit system installer as the current Android user, using Play Store attribution, the not-unknown-source extra, a Play Store referrer and the user-install reason.
- A successful launch is **pending**, not installation success. There is no Shizuku `pm install`, `pm set-installer`, Classic or Root fallback.
- Save pending state before launch. Require a real package update-time change before reading the final install source. State survives activity/process recreation and same-version repairs. No-change/cancellation never reports success. Expiry is 30 minutes.
- Keep the verified repair file and URI access while confirmation is outstanding. Clean them up after a detected package change, expiry or a known launch failure.
- Require **Installed by = com.android.vending** and **Requested by = Google Play or an actual system APK installer**. Shell, missing and third-party initiators fail even when the first field matches.
- Diagnostics reports both fields, the metadata result, build/security patch and the last attempt. Android Auto visibility/playback remains NOT_TESTED.

The release discovery/download, checksum, pinned signer, package/version verifier, private-account rejection and installed-APK repair verification are unchanged from 0.2.6. Sharing and developer contact are retained. Root execution has since been removed. See INSTALLATION-USABILITY.md for the current flow.

## Validation scope

Tests cover system-versus-third-party handler resolution, explicit intent flags and grants, source-pair acceptance/rejection, Android profiles, invalid URI/command inputs, launch errors, no-change/expiry handling and pending repair state across a recreated context. Build, lint and test results are published by the preview build workflow.

The new path still requires physical-device testing, including Xiaomi/HyperOS, real Shizuku IPC, self-update and Android Auto launcher/playback behavior. Earlier Samsung tests of the silent route do not validate this preview.

For an already installed target, open its menu, choose Help with a problem, Repair installation, complete Classic or choose Use Shizuku when offered, confirm Android's installer, and return to inspect Diagnostics. No uninstall or data clearing is requested.
