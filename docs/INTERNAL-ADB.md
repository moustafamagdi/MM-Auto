# Built-in installer preview: 0.2.26-preview (31)

## Evidence and scope

The supplied DriveDeck 1.3.2 APK (com.fareskch.drivedeck, code 9, SHA-256
8bb82676a7e25fd5817e71a19a9a408d24d7894049bc9e2f3dc6894276f8a434) was inspected
statically, not installed or executed. Its post describes wireless debugging
pairing. LocalAdbManager uses LibADB, SPAKE2 and Conscrypt, then launches its
PrivilegedDisplayServer through app_process. Its inspected updater opens an
ordinary system APK installer. No Play installer attribution operation was
found in that path. This demonstrates embedded ADB, not an embedded Shizuku
manager or proof of an Android Auto installation fix.

MM Auto uses a separate implementation against the openly licensed upstream
library. No DriveDeck code, binaries, assets, display service, accessibility
service or screen-capture functionality is included.

## User journey

Preview builds only: MM Tube / MM IPTV three-dot menu → Built-in installation.

1. Connect the phone to Wi-Fi; enable Developer options and Wireless debugging.
   Open Settings and MM Auto in split screen.
2. Choose Android's Pair device with pairing code and keep its dialog open.
   Enter the six-digit code in MM Auto. Leave the optional port blank for
   automatic discovery; manual pairing port entry is also available.
3. Return to the main Wireless debugging page and check the connection. If
   discovery fails, enter that page's connection port, not the pairing port.
4. Explicitly press Install. Existing apps use their same verified version,
   preserving data. Missing apps download the selected stable/preview release
   using existing signature, package, digest and version checks.
5. Check the result, then the actual car screen. Wireless debugging can be
   turned off afterwards. Android may require enabling it after a restart or
   network change. Revoked pairing requires pairing again.

The Install button authorizes this ADB installation; Android may not show
another confirmation, as explained on screen. No silent pairing is promised.
Classic remains the normal Install button. Shizuku and MM Auto self-updates
retain their existing paths. No automatic downgrade, uninstall, data deletion,
root selection or persistent daemon is added.

## Correctness and diagnostics

- A private RSA identity is stored atomically in app-private no-backup storage.
  Pairing codes never enter saved view state, autofill, preferences or reports.
- Discovery filters to this phone's interfaces; connections always target
  127.0.0.1. No arbitrary host or shell-command input. The app never enables
  TCP port 5555 or changes ADB network mode.
- Verified bytes use ADB exec with cmd package install -r --user CURRENT_USER
  -i com.android.vending -S VERIFIED_SIZE. No downgrade or uninstall flags.
- Connection, pairing, stream open and installation waits are bounded.
  Preparation can be cancelled. Dispatched installations wait for a result
  instead of claiming cancellation while Android may still commit them.
- Single-flight work survives activity recreation. In-flight persisted state
  after process death becomes interrupted, never an automatic retry.
- Success requires Android's exact Success result and observed version/update
  time. Installer source is checked separately. Only a recorded internal ADB
  attempt allows shell as initiator with Play as installer; existing Classic
  and Shizuku pair checks remain unchanged.
- Neither installation nor source metadata verifies car visibility/playback.
- Safe events use method=adb. Dashboard ingest v8 accepts this value and labels
  it separately; pairing codes and addresses remain rejected.

## Upstream sources and licenses

- [LibADB Android 3.1.1](https://github.com/MuntashirAkon/libadb-android/tree/3.1.1),
  commit c849886ebc6d48e7b46d967e78a6bb65c90c3b74. Vendored in local-adb using
  Apache-2.0 plus file-specific BSD/MIT notices. Local changes: bounded socket,
  handshake and stream-open waits; early-OKAY race; drain peer output before
  EOF; failed-pair cleanup; no global TLS identity cache; stop delayed discovery.
  The EOF problem is also described in [upstream PR35](https://github.com/MuntashirAkon/libadb-android/pull/35).
- [SPAKE2 Android 2.2.1](https://github.com/MuntashirAkon/spake2-java/tree/2.2.1),
  LGPL-3.0, commit 7615ddd680b990e14513ebb66eac4cb0dbf82464 and native submodule
  0d15933e5ba3e662cb01245a7ac0dc9fca3eac31. Complete corresponding source,
  including native submodule, is in third-party/spake2-2.2.1-source.zip.
- [Conscrypt 2.10.0](https://github.com/google/conscrypt/tree/2.10.0), Apache-2.0
  and bundled BoringSSL notices.
- [Bouncy Castle 1.81](https://github.com/bcgit/bc-java/tree/r1rv81), MIT-style.
- [Android wireless debugging documentation](https://developer.android.com/tools/adb#wireless).

Notices ship in app/src/main/assets/internal-adb-notices.txt and are accessible
from the setup screen. To replace/relink SPAKE2: extract the supplied source,
build its Android module using its wrapper and compatible SDK/NDK/CMake, then
replace its Maven dependency in local-adb/build.gradle with the rebuilt AAR.
Rebuild MM Auto using JDK17, Gradle8.11.1 and SDK/Build Tools36. Debug signing or
your own key works for rebuilding; the developer signing key is not needed or
provided. Library modification, replacement and reverse engineering to debug
those modifications are permitted under LGPL terms. Builds are not minified.

## Validation boundaries

Tests cover prior installation behavior, explicit-only route, interrupted work,
target restrictions, persistent private identity, both languages/themes,
telemetry allowlists and stream EOF races.

A headless API36 AOSP emulator completed a same-version MM Tube reinstall
through the actual in-app ADB transport. It used the emulator's existing test
ADB connection, not a user pairing-code flow. The image has no Play Store:
Android reported a null installer and the app correctly showed source not
confirmed. No real phone pairing, OEM source retention, car launcher visibility
or playback validation is claimed.

Final validation: 126 app tests + 4 library tests, lint (0 errors), release build;
20 dashboard tests and production build passed. All bundled native ELF LOAD
segments and APK ZIP entries meet 16 KiB alignment. A repeated emulator reinstall
preserved a dedicated private app-data fixture. Pairing and car limits above
remain unchanged.
