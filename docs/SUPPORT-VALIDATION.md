# 0.2.8 support diagnostics validation

Based on published preview v0.2.7 (4121c6c); existing stable main/v0.2.6 was not used as a rollback baseline. This release stays a preview and does not promote the new installer route to stable.

Passed: 53 Java/JUnit/Robolectric tests, debug/release APK assembly, Android release Lint. The six new support tests cover actual UI consent/send/disable/delete behavior, bounded retention, strict diagnostic fields, fixed three-app context and a read-only report provider. Existing installer routing/pending verification tests pass.

Shared backend: six Node validator tests, live HTTPS upload/deduplication/unknown-field rejection/wrong-token rejection/deletion for Auto, IPTV and Tube. Migration only extends the allowed application discriminator. Existing table permissions, token verification, storage budgets and retention remain intact. Supabase advisors show only the three expected informational RLS-with-no-public-policy entries.

Native Android 36 / WebView 133 emulator: opened Settings > Support & diagnostics, confirmed default-off, accepted manual-send consent, observed delivery success, and retrieved the report by its exact support ID through the developer database connection. It included only the fixed MM app context plus safe release-check events. This used the .debug application; the production MM Auto package was absent in that fixture, correctly reported as not-installed. Synthetic test records are removed after verification.

Not tested physically: Xiaomi/Samsung, actual Shizuku/Root installation on the user's device, Android Auto visibility, or long-running OEM background scheduling. This diagnostic integration is not a compatibility guarantee. Existing installation algorithms are unchanged; only typed observations were added.

Package: com.magdi.auto; versionName 0.2.8; versionCode 13. Release certificate SHA256: 6d9d30d8f427a6cfc0241831d1e324e684fe2aefda72925c89ef872bf3a5ab65.
