# AGENTS.md

## Repositories

- Main source repo: https://github.com/nov30th/AlipayHighHeadsomeRichAndroid
- LSPosed module release repo: https://github.com/Xposed-Modules-Repo/im.hoho.alipayinstallb
  - Tag convention: `<versionCode>-<versionName>` (e.g. `113-2.6.2`)

## Build Info

- JDK: 17 (Temurin) — see `.github/workflows/android.yml` path: $HOME\.jdks\ms-17.0.19
- Android `compileSdkVersion`: 33
- Android `minSdkVersion`: 23
- Android `targetSdkVersion`: 33
- Build tools: 34.0.0
- Gradle plugin: `com.android.application`

## Release Artifact

- Signed APK location (local, produced by Android Studio signed build):
  `E:\下载\release\app-release.apk`
- Build metadata: `E:\下载\release\output-metadata.json`
- Upload command pattern:
  ```
  gh release upload <tag> "E:/下载/release/app-release.apk" --clobber
  ```
  Apply to both repos (main `v<versionName>` tag and Xposed repo `<versionCode>-<versionName>` tag).
  Also copy the latest README.md file to xposed repo.