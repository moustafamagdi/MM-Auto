# Installation choices preview — 0.2.29-preview (34)

Fresh installation opens a two-choice screen: Normal installation (Classic), then Assisted installation (Preview). The latter enters MM Auto's existing built-in wireless ADB pairing/install flow; it does not launch Shizuku or King Installer. Device compatibility is not inferred from installer metadata.

For an installed app with no newer release, Open and Install sit beside each other. Install offers the same choices: Classic reuses the existing non-destructive repair path; assisted installation uses the current installed APK through the existing internal workflow. Ordinary updates, download-all and self-updates retain their Classic behavior. Manual APK selection and recovered pending operations retain their existing paths.

Help with a problem now offers problem details and diagnostic reporting, with installation guidance pointing to Install. The normal settings UI no longer offers a new Shizuku guide. Shizuku code and existing pending-task restoration remain temporarily for migration/rollback, pending field feedback; nothing silently converts queued work.

A deliberate new assisted request after an earlier success starts the setup/check-connection screen again, retains pairing credentials, and never automatically starts installation. Activity recreation preserves the ongoing screen.

Validation covers method selection without downloading, correct app targeting, Classic after a saved Shizuku preference, parallel Classic queue behavior, Open/Install siblings, help/report separation, and explicit retry after success. Existing Arabic/English light/dark internal setup tests remain. Real users who previously required Shizuku must validate this preview on their phones and cars.

Published only as a prerelease from preview/internal-adb. Stable v0.2.25 remains unchanged. VersionCode 34, canonical MM-Auto.apk, established signing certificate.

## 0.2.30-preview (35)

Entry labels now reflect intent: Download for a missing app, Repair beside Open for an installed current app, and Update + Repair + Open when a newer version is available. All three download/repair/update entry points offer Classic or assisted installation. Self-update retains its existing Classic route.

Repair always reuses the current installed APK, even when a newer release exists. Update explicitly requests the latest release from the selected channel in the built-in ADB flow. The update intent is stored across pairing/activity recreation, reset for a newly selected app, and rejects a release that is no longer newer instead of downgrading or silently repairing. No uninstall is performed. Unknown-source permission and Android confirmations remain.

A test covers simultaneous Update/Repair/Open visibility and distinguishes assisted update intent from assisted repair intent. Pending/manual installs and queue cancellation/resume retain their existing controls.
