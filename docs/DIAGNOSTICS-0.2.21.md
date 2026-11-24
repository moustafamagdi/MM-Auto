# Diagnostic history correction — 0.2.21-preview

- Classic same-version repair now records pending/completed repair events and refreshes its saved metadata result after an observed package update.
- Pending installations persist their actual Classic/Shizuku route. Completion and metadata events use that route, not the current global preference. Old pending records without a route are reported as unknown.
- Saved attempts include a UTC timestamp and method. Old saved text remains available with an explicitly unknown timestamp/method; reading a report does not rewrite it or fabricate a new attempt.
- Problem details shows only a fresh current-state read and its check time. A separate Last recorded attempt action opens the historical snapshot. The shareable report labels current and historical sections separately and lists Android Auto visibility as unverified once per app.
- Ending tracked installation without an observed package change no longer leaves a pending description as the final saved record. No cancellation/failure distinction is invented without evidence.

Validation: Release build, Release lint and 109 Release unit tests passed, including persisted-route restoration, unknown legacy records, current/history separation, and Classic repair replacing an old Shizuku result. No new physical phone or car validation was performed. Installation commands, package identities, signatures, download filenames and release version-code detection are unchanged.
