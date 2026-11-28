# Pairing verification preview — 0.2.31-preview (36)

A saved pairing flag is only history, not proof that Android still trusts the app identity. On entering/resuming setup with a saved pairing and setup/paired/ready state, MM Auto makes a bounded fresh local ADB connection check before displaying readiness. The check never installs an APK. Existing checks before APK preparation and immediately before installation remain, now sharing connection-failure classification.

Explicit pairing/authentication rejection clears cached readiness, the cached port and the historical paired flag, and offers pairing again. Network/discovery/general I/O failure clears readiness and the cached port but does not assert that pairing was revoked or delete the identity. It offers retry, phone settings and pairing again with an accurate explanation. No keys, codes, socket output or credentials enter diagnostics. Failed checks do not loop automatically within the screen.

Pairing and update intent survive app recreation. New requests can recover an old generic connecting failure by performing the check again. Existing successful installation history remains separate from current connection readiness.

Validation covers authorization rejection vs transient network failure, retained pairing on network failure, removed cached ports, lifecycle checks without automatic installation, and existing tests. Physical device verification is recorded separately; a generic connection failure cannot prove whether Android removed a pairing or the transport is unavailable.

Device check: Samsung SM-S948B, installed official-signed 0.2.31-preview over 0.2.30 without clearing data. Wireless debugging was enabled. Entering assisted repair automatically displayed Checking the connection, then connection_unavailable with Pair again. Android returned a generic connection failure, so this does not establish explicit authorization rejection. No target APK was downloaded or installed by the probe. Tests: 137 passed; release build and lint passed.
