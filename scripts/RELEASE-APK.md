# Release APK

Build and sign using the existing release key, then run:

    ./scripts/Stage-Release.ps1 -SignedApk <signed-apk> -SdkPath <android-sdk>

The script verifies the APK package and signer and stages `MM-Auto.apk`. Pass `-DraftTag vX.Y.Z` to prepare an existing empty draft and upload the verified APK using authenticated GitHub CLI. The script writes `<!-- mm-version-code: <code> -->` to the release body using the signed APK's versionCode. The asset has an empty display label so GitHub Assets shows `MM-Auto.apk`. Review the draft before publishing.

Keep the published `v0.2.17` release and its `MM-Auto-0.2.17.apk` asset available at their existing URLs. Older updaters use that versioned APK as a bridge. MM Auto 0.2.17 and later understand the fixed APK name and release-body metadata.
