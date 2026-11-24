# MM Auto UI refresh

## 0.2.14 — Visible actions and consistent menus

Settings, Check for updates, Share app and Contact developer now use bordered, rounded buttons with icons and press/disabled feedback. Cards and overflow actions have visible boundaries. Settings dialogs, support actions, installation details and guided setup use the same light/dark styling. Buttons use wrapping content height and at least 48 dp touch targets; installation-version strings preserve their LTR order within Arabic labels.

Version code: 19. Package and signing identity are unchanged; no preference or diagnostic storage migration is introduced. Release discovery, cached results, installation methods, consent and upload behavior remain unchanged.

Validation: 84 existing JVM/Robolectric tests pass; release/debug builds pass; lint reports 0 errors and 23 existing warnings. Native phone UI was inspected in an API 36 emulator: English/Arabic, light/dark, Settings, Advanced settings and Support, including a 320 dp wide display at 130% font size. The signed release APK was installed and opened in the emulator. Physical phone and Android Auto compatibility were not retested for this visual-only update.

## 0.2.4 baseline

## User experience

- Home focuses on MM IPTV and MM Tube, each with one primary action: Install, Update, Open, or the manual download guide when online metadata is unavailable.
- MM Auto appears as a compact update card when an update is available. Its version and manual import are also available in Settings.
- Each app's overflow menu contains version details, the official releases page, APK selection and troubleshooting. What’s new opens the matching release page.
- Settings contains daily notifications, appearance, language and help. The current installation flow uses Classic by default and contextual Shizuku setup; Root was removed.
- Arabic/RTL, English, device language, device appearance and explicit light/dark choices are supported.
- APK preparation shows progress, verification and cancellation state. Cancellation suppresses installation and deletes the prepared file. The existing network transfer can finish before cleanup; the UI explains this. Cancellation is unavailable after installation begins.
- Outcome troubleshooting is attached to each card. Manual imports remain available to retry after permission prompts, including activity recreation; granting permission restores the install confirmation.
- HTTP 403/429 and other check failures have a manual-download route. This presentation does not change GitHub retry, rate-limit, caching or release-selection behavior.

## Core behavior preserved

The entire `install/` package, `RepairApk`, `RepairState`, `UpdateAlerts`, `UpdateCheckJob` and `ApkProvider` are unchanged. All 13 files were compared byte for byte against `ac948fe1f889065ab261ac7f3274c56a1cd23414`.

`Releases` has one access-only change: the existing `validate` method is package-visible so the document-picker adapter can call it. Its body, release discovery, host restrictions, size limit, download checksum, signer pin, version comparison and private-account rejection rules remain unchanged. The existing Activity repair and post-install verification paths remain in use.

`ManualApk` copies the selected document into private cache with a size limit and cleanup on cancellation/failure, then delegates to that verifier. A manually supplied file has no GitHub checksum to compare; the official signing-certificate, package and version checks still apply. The verified APK is passed to the existing installation methods.

## Validation and release

The normal Gradle `assembleRelease`, `testReleaseUnitTest` and `lintRelease` checks pass with API 36, build tools 36.0.0 and JDK 17. Existing unit tests plus four import-boundary/cleanup tests pass: 27 tests total. A separate SDK compilation and test run also passed. Lint reports no errors; advisory warnings remain.

The SDK fallback build includes the existing Shizuku provider permission and metadata from its library manifest. Do not omit AAR manifest merging in alternative build tools.

A distributable update must be signed with the original MM key. The original key was recovered from the owner's private backup and its certificate matched the signed 0.2.3 reference. No signing material is included in this branch. `scripts/Sign-Release.ps1` aligns an unsigned build, asks for the keystore password locally, signs it, checks the pinned certificate, and writes a SHA-256 file. The unsigned build is not installable.

Physical phone installation, notifications, Root/Shizuku execution, self-update and Android Auto playback still require device verification. UI changes do not certify Android Auto compatibility.
