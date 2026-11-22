# Guided Shizuku setup (0.2.13)

Open an app card's **Help with a problem → Guided setup**, or use **Settings →
Advanced settings → Guided setup**. A Classic repair that still has incorrect
installation attribution offers **Use Shizuku**, which now opens this guide.

The guide shows the next action based on observed state: install the helper,
start its service, authorize MM Auto, or explicitly continue to repair. It
refreshes while visible and after returning from another app. Granting permission
or returning from Settings never starts repair automatically. Service/permission
loss is checked again before continuing. The existing installer confirmation,
APK verification, retained app data and manual launcher check are preserved.

Xiaomi, Redmi and POCO users see the separate **USB debugging (Security settings)**
requirement and a shortcut to Developer options. MM Auto cannot inspect this OEM
switch and never labels it verified or changes it. **Install via USB** is mentioned
only if requested by the installer. Pairing details and the official guide are
available separately, including the distinction between waiting and running.
This reduces navigation but cannot eliminate user-owned Android permissions and
wireless-debugging setup. Shizuku needs restarting after a phone reboot.

Sources:
- https://shizuku.rikka.app/guide/setup/
- https://shizuku.rikka.app/download/

On 2026-11-22 the owner relayed a successful Xiaomi user attempt after enabling
USB debugging (Security settings) and using Shizuku. This is user-reported
evidence, not a new instrumented Xiaomi or physical Android Auto test.

The install-source Toast is now short in English and Arabic. Full source-pair
details remain in diagnostics; successful attribution never certifies launcher
visibility. No new permissions, account data or report fields are introduced.

Validation results are recorded in the release notes. Unit tests exercise actual
dialog buttons, loss of service/permission, denial, explicit continuation,
compatible providers, localization and Xiaomi-only instructions. Synthetic tests
do not establish real OEM setting behavior or Shizuku installation success.
