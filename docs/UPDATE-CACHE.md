# Persistent update checks — 0.2.10

Previously each Activity kept release results only in its rows. Updating MM Auto
restarted the process, losing the other apps' offers. A failed network request
also replaced a previously known release with an empty result.

Each successful per-app check now commits a small private snapshot, including
its completion time, before exposing it to the UI. Foreground and scheduled
checks share this store. Failed or interrupted checks retain the previous
snapshot. A successful check with no compatible release explicitly replaces it.
Sequence numbers prevent an older overlapping check from overwriting a newer
successful result. Reads reuse the normal release parser; APK signing, package,
size, version and checksum verification remain unchanged.

The UI restores saved results before starting its usual automatic check and
shows the last successful check time in English or Arabic. Foreground cache
notifications and resume refreshes pick up scheduled checks. Available versions
are compared with the installed package on rendering, so an actual installation
clears only that app's offer. Starting or cancelling installation does not clear
the other offers. Storage reads and committed writes run on workers.

Old releases did not save these snapshots: one successful check on 0.2.10 is
needed to establish the cache. No uninstall or data reset is required. Support
IDs, sharing choices, scheduling and privacy policy are unchanged.

## Validation

- 65 JVM/Robolectric tests passed, including eight new regressions covering
  persistence, failed/empty checks, overlapping results, invalid repository
  records, Activity recreation, installed-version comparisons and background
  updates. Tests assert the actual Update/Open buttons and self-update card.
- Release build and Android lint completed (0 errors; 24 existing/style warnings).
- API 36 emulator: upgraded signed 0.2.9 to 0.2.10 with a synthetic saved cache
  and network disabled. Both other app offers and their check times remained
  after the failed automatic check and a process restart. The shared support
  identity file hash stayed unchanged. Synthetic records were removed and the
  original connectivity/display restored. These were simulated release offers,
  not live downloadable future releases.
- APK package ID and existing signing certificate preserved. Physical Android
  Auto, other ROMs and root installer paths were not retested for this UI fix.
