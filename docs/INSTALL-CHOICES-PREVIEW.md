# Installation choices preview — 0.2.29-preview (34)

Fresh installation opens a two-choice screen: Normal installation (Classic), then Assisted installation (Preview). The latter enters MM Auto's existing built-in wireless ADB pairing/install flow; it does not launch Shizuku or King Installer. Device compatibility is not inferred from installer metadata.

For an installed app with no newer release, Open and Install sit beside each other. Install offers the same choices: Classic reuses the existing non-destructive repair path; assisted installation uses the current installed APK through the existing internal workflow. Ordinary updates, download-all and self-updates retain their Classic behavior. Manual APK selection and recovered pending operations retain their existing paths.

Help with a problem now offers problem details and diagnostic reporting, with installation guidance pointing to Install. The normal settings UI no longer offers a new Shizuku guide. Shizuku code and existing pending-task restoration remain temporarily for migration/rollback, pending field feedback; nothing silently converts queued work.

A deliberate new assisted request after an earlier success starts the setup/check-connection screen again, retains pairing credentials, and never automatically starts installation. Activity recreation preserves the ongoing screen.

Validation covers method selection without downloading, correct app targeting, Classic after a saved Shizuku preference, parallel Classic queue behavior, Open/Install siblings, help/report separation, and explicit retry after success. Existing Arabic/English light/dark internal setup tests remain. Real users who previously required Shizuku must validate this preview on their phones and cars.

Published only as a prerelease from preview/internal-adb. Stable v0.2.25 remains unchanged. VersionCode 34, canonical MM-Auto.apk, established signing certificate.
