# Installation usability — 0.2.20-preview

Local implementation; not published. Based on main d157359 (0.2.19).

## User journey

- Install and Download all use Classic, with the existing Android confirmation intent and APK verification.
- Help offers contextual Shizuku setup for the selected app. For an installed app, repair copies and verifies its current APK instead of asking the user to download it. A fresh install uses the release downloader. No external King Installer is needed.
- The guide shows the next prerequisite and a named Continue action. Pairing details and Xiaomi instructions live behind optional help. Android pairing and authorization remain user actions.
- The selected app and operation are committed to private preferences, restored after recreation/process restart, and expire after 24 hours. Restoration and permission grants do not themselves start installation. Closing/back cancels the setup continuation. Download queue entries remain separately verified and serialized; MM Auto updates run last.
- Root enum, installer execution and shell command builders are removed. Old Root settings migrate to Classic; pending imported-file UI state is cleared. Root queue entries are discarded, never converted into another installation; cached APKs are not deleted by migration.
- Normal messages describe download, confirmation and observed installation separately. Installer metadata remains in Problem details and the shareable installation report. Car visibility/playback is not inferred from metadata.
- Support retains the existing Ko-fi URL, with a vector coffee icon, cream/coffee colors, dark styling and relative drawable positioning for RTL.

## Video and implementation comparison

Reviewed sampled frames spanning the local 5m19s demonstration, including GitHub downloads, Developer options, wireless pairing, starting Shizuku, King selection of each APK, Android confirmation, King's compatibility warning and MM Auto's metadata screen. No audio transcription was performed. Download/file picking and King are eliminated from the normal guided journey; wireless pairing cannot be automated by MM Auto.

[KingInstaller ShizukuUtils](https://github.com/fcaronte/KingInstaller/blob/master/app/src/main/java/com/example/kinginstaller/ShizukuUtils.kt) tries reflected `IActivityTaskManager.startActivityAsUser` through `ShizukuBinderWrapper`, then UserService `am start`, then a standard activity launch. Its reflected argument construction relies on hidden Android signatures. A launch alone does not establish attribution or car visibility.

[Official Shizuku API documentation](https://github.com/RikkaApps/Shizuku-API) supports both remote Binder calls and UserService; the latter runs with the helper's identity and avoids app-process hidden-API restrictions. MM Auto retains its bounded, explicit-system-component UserService route, validated APK URI, read grants and current Android user. No speculative Binder or silent Classic fallback was added. This change improves orchestration, not an unverified promise of better OEM attribution.

## Validation boundaries

Focused tests cover helper states, explicit continuation, repeated permission taps, contextual restoration, Root settings/queue retirement, Classic default, cancellation, queue ordering, release metadata and existing verification behavior. See the local build reports for final counts.

Validation on 2026-11-24: 104 tests passed in each of Debug and Release, Release lint/build passed (existing warnings remain). On a disposable read-only API 36 emulator, a signed MM Auto update installed over the existing app; its language/theme settings were retained. MM Auto downloaded and updated MM Tube from 0.1.48 to 0.1.50 through Classic and reported installation without claiming car visibility. English/dark and Arabic/RTL/light layouts were visually inspected. A pending MM Tube helper setup returned after force-stop/relaunch without launching installation. No physical phone was connected. The Debug APK cannot coexist with the production app because of the existing shared signature-permission declaration; emulator checks used the correctly signed production-package preview.

Physical Xiaomi/Samsung Shizuku installation, OEM attribution, Android Auto launcher visibility/playback, and actual phone self-update still require device testing. No release was published.
