# MM Auto support reports (0.2.8 preview)

Open **Settings → Support & diagnostics**. Copy the installation Support ID, send a report now after consent, or opt into automatic delivery. Automatic delivery is OFF by default; a manual send does not enable it. Each MM app has its own random installation ID. Give the developer the ID and approximate incident time.

MM Auto reports only closed diagnostic categories: release checks, APK preparation/download success/failure/cancellation, installation method, pending/finished/expired observations, Shizuku permission availability, and the three MM applications' versions and enumerated installing/initiating sources. No file names/paths, shell output, exception messages, URLs, arbitrary installed-app inventory, credentials, location or hardware identifiers are sent. Install attribution MATCH is diagnostic only, not proof of Android Auto visibility.

The developer-owned Supabase service in Frankfurt is shared with Tube/IPTV. Requests necessarily expose network IP addresses to the hosting provider; diagnostic tables do not store IPs. Uploads are bounded to 128 events, roughly every two hours subject to network/OEM scheduling. Local history is at most 256 events / 24 hours; server reports up to 30 days, possibly less under global quota pressure. Disabling stops future automatic sends. Delete stops sending, removes this installation's server records and changes its ID only after confirmation; offline deletion preserves the old credential for retry. Manual copies/files saved elsewhere are not removed.

**Share a report manually / Copy report** provide a safe fallback. **Advanced: installation details** preserves the older full installation report as a manual-only view. Neither feature uploads the old report automatically.

Owner inbox: https://supabase.com/dashboard/project/hncaagdpxhbysdcdwuoa/explorer/notebook/fea19f9e-0f9a-4975-b690-ea13452e8e3c

Backend source and quotas: https://github.com/moustafamagdi/MagdiIPTV/tree/main/support-backend

This is based on v0.2.7, not the older stable v0.2.6 checkout. It keeps v0.2.7's pending installation verification and Shizuku system-installer behavior. Package ID, pinned APK verifier, signing identity, UI branding and settings remain unchanged. It stays a preview because cross-device installer acceptance still needs physical testing.
