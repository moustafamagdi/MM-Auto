# Update checks and download queue — 0.2.15

Version code: 20. Package and original signing certificate unchanged.

## Behavior

- Foreground release checks run independently, with a 30-second overall UI deadline. Errors retain the last successful cached offers. Request identity guards reject late callbacks.
- A transfer deadline spans redirects and reads (30 seconds for release lookups; 10 minutes for an APK). Cancel marks the operation immediately, interrupts its worker and disconnects its current connection off the UI thread. Stream copying checks cancellation before and after each read.
- Three app downloads can run concurrently. Each card has independent progress and cancellation; “Download all updates” starts all offered updates. Manual file import and installation-source repairs remain exclusive to avoid sharing an APK path with an active download.
- Only verified APKs enter the installation queue. Companion apps install in selection order; MM Auto installs last, since a self-update terminates the process. Only one installer is active. Cancelling or failing an installer releases that slot; it never counts as installation success.
- Classic uses an activity result and a unique persisted request code per installer. Returning from the installer is also handled for Shizuku and installers that omit the result. A late result cannot release another installer's slot. Root remains its existing explicitly selected path.
- Downloads survive activity recreation within the process. Verified ready files are journaled before opening an installer and checked again before installation after recovery. If the process was interrupted during an installer, the recovered queue waits for an explicit Continue. Unfinished network downloads are not resumed after process termination; the user can retry them. No background foreground-service permission is added.
- Version, package, pinned signer, size and release checksum verification remain in the download path. No account data is embedded or added to diagnostics.

## Validation

- JVM/Robolectric: 95 passing tests, including the existing regression suite plus transfer deadlines under continuously arriving data, nonblocking cancellation while disconnect waits, independent cancellation, concurrent downloads, ordered serial installation, self-update ordering, journal recovery and cancellation persistence. Actual activity-button tests cover per-card enablement, Download all, a stalled check deadline, retained cache, late network callbacks, installer cancellation and stale installer results.
- Android API 36 emulator, signed release APK: installed over 0.2.14 successfully; downloaded real public Tube/IPTV release APKs from GitHub; confirmed IPTV's installer appeared first, cancellation advanced to Tube, and Tube installed as version code 49. Confirmed cancelling an APK download re-enabled Install and displayed cancellation. Force-stopped MM Auto while the first installer was open, restored both ready files, pressed Continue, and installed IPTV then Tube in sequence. Failed network checks retained cached offers and recovered after network restoration. No AndroidRuntime crash was observed.
- Emulator Classic installation-source metadata was reported as mismatched; installation success does not imply Android Auto acceptance. Root/Shizuku installation on physical devices, Xiaomi-specific permissions and physical Android Auto were not tested in this update.

## Device smoke test

1. Upgrade MM Auto without uninstalling it. Check that settings and update offers remain.
2. Check for updates with a slow/unavailable connection. The check should finish with a retry message and retain previous offers.
3. Download two offered apps (or use Download all updates). Cancel one; the other should continue. Retry the cancelled one.
4. Confirm or cancel each Android installer. The next one should open only after returning from the previous one. Repeat with your chosen Shizuku method if applicable.
