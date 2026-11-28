# Pairing-ready notification preview — 0.2.32-preview (37)

Opening phone setup starts user-visible foreground discovery for the phone's local _adb-tls-pairing._tcp advertisement, bounded to 120 seconds (below Android shortService's deadline). On the first valid advertisement for the current ticket, the existing inline-reply notification changes to Pairing is ready and allows a new alert. Discovery then stops, the foreground service detaches, and the notification remains available within its original ten-minute reply ticket.

No code is read from Settings, persisted, or logged. Readiness means a pairing endpoint was discovered, not that a code was accepted. Duplicate/stale callbacks do not alert again. Reply/cancel destroys discovery. If discovery is unavailable or times out, the existing notification still allows code entry; reopening phone setup starts a fresh discovery window. Android/channel settings decide whether the update appears as a heads-up banner.

Tests verify current-ticket re-alert, duplicate/stale suppression, retained RemoteInput, no Activity launch or installation, bounded discovery, expiry, invalid codes and service recreation. Release build and lint pass.

On Samsung SM-S948B, the installed official-signed build posted the waiting notification. Opening Pair device with pairing code caused its text to update to Pairing is ready; the monitoring foreground service was no longer running. No pairing code was read or submitted. Actual heads-up presentation remains controlled by device notification settings.

This is a prerelease. The official latest remains the promoted v0.2.31-preview (code 36); that existing APK/tag is unchanged.
