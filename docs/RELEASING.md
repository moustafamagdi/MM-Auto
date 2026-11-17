# Release checklist

1. Review intended source changes. Increment `versionCode` and `versionName` in `app/build.gradle`.
2. Run `:app:assembleRelease :app:lintRelease :app:testReleaseUnitTest` using JDK 17, API 36 and build tools 36.0.0.
3. Distinguish device evidence from assumptions. Keep experimental releases marked **prerelease**.
4. Align and sign locally with the established key. Never upload signing material to commits, issues or assets.

## Verify before publishing

Use SDK `zipalign`, `apksigner verify --print-certs` and `aapt dump badging` to confirm:

- package `com.magdi.auto`, without `.debug`;
- intended version name/code and minSdk 30;
- valid alignment and APK signature;
- certificate SHA-256 `6d9d30d8f427a6cfc0241831d1e324e684fe2aefda72925c89ef872bf3a5ab65`;
- no `assets/initial-account.json` or other private data.

Retain the final APK SHA-256. Build from the commit referenced by the release tag.

## Asset naming

Publish exactly one matching APK per app release:

| Repository | Supported names |
| --- | --- |
| MM-Auto | `MM-Auto-<version>.apk` |
| MagdiIPTV | `MM-IPTV-<version>.apk` or `Magdi-IPTV-<version>.apk` |
| MagdiTube | `MM-Tube-<version>.apk` or `Magdi-Tube-<version>.apk` |

Versions use `major.minor.patch`, optionally followed by a hyphenated suffix such as `regional-login` or `rc.1`. Set the asset **label** to `versionCode=<integer>`.

Create a draft, upload the signed APK, verify name/label/size/digest, then publish. Do not overwrite published APKs or move existing tags. If releasing an unmerged experimental branch, say so in the notes.

Carry forward unverified paths: Root, other ROMs, Classic-first repair, self-update/settings preservation and playback. Diagnostics is not an Android Auto compatibility certification.
