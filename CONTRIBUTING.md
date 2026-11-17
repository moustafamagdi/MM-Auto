# Contributing

Start with a reproducible issue or a focused pull request. Discuss changes to signing, release selection, privileged installation or data handling first.

## Development

Use JDK 17, API 36 and build tools 36.0.0. Configure `sdk.dir` in untracked `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Use `gradlew.bat` on Windows. Keep minSdk 30 and the pure-Java style. Add meaningful regression tests for security-sensitive behavior. Describe device tests separately from unit tests.

## Safeguards

- Preserve package, certificate, checksum, size, version and private-account-asset validation.
- Do not replace the pinned signer to accept another APK.
- Keep shell commands narrow, validate package names, and keep blocking operations off the main thread.
- Never commit local SDK configuration, signing keys, passwords, tokens or private app data.
- Do not copy third-party code without checking licensing and discussing it with the maintainer. No KingInstaller code is included in this project.
- Explain effects on data, pending verification and self-update behavior.

## Review checklist

- Describe the concrete problem and resulting behavior.
- List commands actually run and devices actually tested.
- State untested paths and known limitations.
- Remove personal information from screenshots.
- Run `git diff --check` and inspect staged files for secrets.

No project license has been selected yet. Resolve licensing with the maintainer before reusing or redistributing code.
