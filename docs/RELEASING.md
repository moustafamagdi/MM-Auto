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
| MM-Auto | `MM-Auto.apk` (preferred), or legacy `MM-Auto-<version>.apk` |
| MagdiIPTV | `MM-IPTV-<version>.apk` or `Magdi-IPTV-<version>.apk` |
| MagdiTube | `MM-Tube-<version>.apk` or `Magdi-Tube-<version>.apk` |

Versions use `major.minor.patch`, optionally followed by a hyphenated suffix such as `regional-login` or `rc.1`. Leave the asset label empty so GitHub displays the APK filename. Record the build code in the release body as `<!-- mm-version-code: <integer> -->`. MM Auto 0.2.17 and newer read this metadata for canonical APK names.

Create a draft, upload the signed APK, verify name/label/size/digest, then publish. Do not overwrite published APKs or move existing tags. If releasing an unmerged experimental branch, say so in the notes.

Carry forward unverified paths: Shizuku on physical phones, other ROMs, Classic-first repair, self-update/settings preservation and playback. Diagnostics is not an Android Auto compatibility certification.

Stable APK versionName and release tags must use plain major.minor.patch (no preview suffix). Experimental APKs retain a preview suffix and GitHub prerelease=true. Do not gate supported installation features on the versionName; update channels use GitHub release metadata and versionCode. Publish a new higher versionCode to rename an installed build; never overwrite an existing APK/tag.
