# Alipay Theme Logic Notes

This note compares the theme feature from:

- Current repo: `E:\GITHUB\AlipayHighHeadsomeRichAndroid`
- LiYiCha repo: `E:\GITHUB\LiYiCha-AlipayHighHeadsomeRichAndroid`
- Decompiled latest Alipay APK source: `D:\__alipay_apk_decomp`

## 1. Old Payment-Screen Skin Logic

The original module only changes the payment-code skin. It hooks:

```text
com.alipay.mobile.onsitepaystatic.ConfigUtilBiz.getFacePaySkinModel()
```

That path uses Alipay's onsite-pay skin directory:

```text
/data/data/com.eg.android.AlipayGphone/files/onsitepay_skin_dir
```

The module copies external custom files into:

```text
/data/data/com.eg.android.AlipayGphone/files/onsitepay_skin_dir/HOHO
```

Then it returns a custom `OspSkinModel`, for example:

```json
{
  "md5": "HOHO_MD5",
  "minWalletVersion": "10.2.23.0000",
  "outDirName": "HOHO/<selectedSkin>",
  "skinId": "HOHO_CUSTOMIZED",
  "skinStyleId": "Sesame Skin",
  "userId": "HOHO"
}
```

This only affects the payment screen. It does not drive Alipay's app-wide theme system.

## 2. LiYiCha Theme Feature

LiYiCha adds a new `theme` package:

```text
E:\GITHUB\LiYiCha-AlipayHighHeadsomeRichAndroid\app\src\main\java\im\hoho\alipayInstallB\theme
```

Important files:

```text
ThemeManager.kt
ThemeHookV2.kt
ThemeRepository.kt
ThemeCacheInfo.kt
ThemeModels.kt
ThemeConstants.kt
```

External module theme storage:

```text
/storage/emulated/0/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER
```

Imported themes are stored under:

```text
/storage/emulated/0/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER/themes/<themeId>
```

The selected theme is stored in:

```text
/storage/emulated/0/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER/selected_theme
```

Operation request folders:

```text
/storage/emulated/0/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER/export
/storage/emulated/0/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER/delete
/storage/emulated/0/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER/update
```

LiYiCha's intended update flow:

1. UI imports a theme ZIP or folder containing `meta.json`.
2. `ThemeRepository` generates `theme_info.json` if missing.
3. User selects a theme; the selected theme ID is written to `selected_theme`.
4. Update request is created.
5. In Alipay process, `ThemeManager.handleThemeOperations()` copies the selected theme into Alipay internal storage.
6. It updates Alipay skin-center cache prefs.
7. It clears in-memory cache.
8. It calls `AntSkinRenderManager.notifySkinChanged()`.

LiYiCha copies selected themes to:

```text
/data/data/com.eg.android.AlipayGphone/files/skin_center_dir/<userId>/theme/<themeId>
```

It writes this SharedPreferences file:

```text
prefs_skincenter_file
```

With key:

```text
cached_skin_info_v2#<userId>
```

However, based on the latest decompiled APK, that `#` is probably wrong for this APK. See section 4.

## 3. Latest Decompiled APK Source Findings

Checked source root:

```text
D:\__alipay_apk_decomp
```

Relevant APK source files:

```text
D:\__alipay_apk_decomp\sources\com\alipay\mobile\skincenter\manage\SCInnerManager.java
D:\__alipay_apk_decomp\sources\com\alipay\mobile\skincenter\model\SCCacheInfoModel.java
D:\__alipay_apk_decomp\sources\com\alipay\mobile\skincenter\model\SCMetaModel.java
D:\__alipay_apk_decomp\sources\com\alipay\mobile\skincenter\util\SCCommonUtil.java
D:\__alipay_apk_decomp\sources\com\alipay\mobile\skincenter\util\SCConfigUtil.java
D:\__alipay_apk_decomp\sources\com\alipay\mobile\skincenter\manage\AntSkinRenderManager.java
D:\__alipay_apk_decomp\sources\com\alipay\mobile\skincenter\service\SCSkinCenterService.java
```

The latest APK still uses the `skincenter` package:

```text
com.alipay.mobile.skincenter
```

App-wide theme files are loaded from:

```text
/data/data/com.eg.android.AlipayGphone/files/skin_center_dir/<userId>/theme/<userSkinId>
```

`SCInnerManager.i()` returns the root:

```text
LauncherApplicationAgent.getInstance().getFilesDir()/skin_center_dir
```

`SCInnerManager.a(String userId)` returns:

```text
skin_center_dir/<userId>
```

For theme scene paths, private method `SCInnerManager.a(File file, String scene, String userSkinId)` returns:

```text
<file>/theme/<userSkinId>
```

## 4. Latest APK Cache Logic

`SCCommonUtil` defines:

```text
SC_PREFS_FILE = "prefs_skincenter_file"
```

`SCCommonUtil.putString(key, value)` stores:

```text
key + getCurrentUserId()
```

`SCCommonUtil.putString(key, userId, value)` stores:

```text
key + userId
```

So in this APK, the key is:

```text
cached_skin_info_v2<userId>
```

not:

```text
cached_skin_info_v2#<userId>
```

`SCInnerManager.g()` is the local cache reader. It reads:

```text
SCCommonUtil.getString("cached_skin_info_v2", currentUserId)
```

Then parses it as:

```text
Map<String, SCCacheInfoModel>
```

The map entry for app-wide theme is:

```text
"theme" -> SCCacheInfoModel
```

Required `SCCacheInfoModel` fields seen in APK:

```text
usageScene
skinId
userSkinId
userId
md5
appSquareMd5
cacheTime
expireDate
versionLimit
isDiySkin
name
skinType
materialId/outMetaId where applicable
diyExpiredTime
```

Important: this APK also checks:

```text
cached_skin_theme<userId>
```

`SCInnerManager.hasEnableSkin("theme")` calls an internal helper that reads:

```text
SCCommonUtil.getString("cached_skin_theme", userId)
```

If it is empty or `"DEFAULT"`, theme is treated as disabled.

Therefore, a correct prefs-only update must write both:

```text
cached_skin_info_v2<userId> = {"theme": {...}}
cached_skin_theme<userId> = <themeId>
```

## 5. Mismatches in LiYiCha ThemeHookV2

`ThemeHookV2.kt` is directionally useful, but does not match this APK version.

Observed latest APK names:

```text
SCInnerManager singleton: b()
SCInnerManager readSkinInfoFromLocalCache: g()
SCInnerManager cache map field: b
SCInnerManager cache-loaded AtomicBoolean field: g
SCInnerManager meta cache field: j
SCInnerManager theme id cache field: h
SCInnerManager hasEnableSkin: a(String, Map)
SCInnerManager theme path helper: private a(File, String, String)
```

LiYiCha assumes:

```text
SCInnerManager.K()
SCInnerManager.m()
SCInnerManager.g as cache Map
SCInnerManager.y(String, Map)
SCInnerManager.q(File, String, String)
```

Those assumptions are wrong for this APK.

Also, LiYiCha assumes:

```text
SCConfigUtil.m(String, Long) is MD5 validation
SCConfigUtil.l() is timestamp/cache expiry check
```

In this APK:

```text
SCConfigUtil.m() returns subscribe fatigue config
SCConfigUtil.l() checks skin_center_preview_show_nft
SCConfigUtil.a(String, long) checks skin_center_force_update
```

So the MD5/time hooks in `ThemeHookV2` are not safe for this APK.

## 6. Which Steps Are Necessary

For this APK, the minimal reliable flow should be:

1. Get current user ID using:

```text
com.alipay.mobile.skincenter.util.SCCommonUtil.getCurrentUserId()
```

2. Copy selected theme to:

```text
/data/data/com.eg.android.AlipayGphone/files/skin_center_dir/<userId>/theme/<themeId>
```

3. Ensure the copied theme contains valid:

```text
meta.json
```

4. Generate or load `ThemeCacheInfo` / `SCCacheInfoModel` equivalent.

5. Write:

```text
prefs_skincenter_file: cached_skin_info_v2<userId>
```

as JSON:

```json
{
  "theme": {
    "usageScene": "theme",
    "skinId": "<skinId from meta.json or themeId>",
    "userSkinId": "<themeId>",
    "userId": "<userId>",
    "md5": "<real md5>",
    "appSquareMd5": "<real md5>",
    "cacheTime": 1770000000,
    "expireDate": "2126-01-01",
    "versionLimit": "10.8.20.0000",
    "isDiySkin": false,
    "name": "<theme name>",
    "skinType": "INST_UNLIMITED",
    "diyExpiredTime": 0
  }
}
```

6. Write:

```text
prefs_skincenter_file: cached_skin_theme<userId> = <themeId>
```

7. Clear memory cache:

```text
SCInnerManager theme-id cache field h
SCInnerManager meta cache field j["theme"]
SCInnerManager cache map field b["theme"]
SCInnerManager loaded flag field g = false, or call cache reload after updates
```

8. Reload cache:

```text
SCInnerManager.g()
```

9. Refresh UI:

```text
AntSkinRenderManager.notifySkinChanged()
```

## 7. Unnecessary or Risky Steps

Avoid these as primary logic:

- Hooking `SCConfigUtil.m()` for MD5. It is not MD5 validation in this APK.
- Hooking `SCConfigUtil.l()` for expiry. It is not expiry logic in this APK.
- Hooking `SCInnerManager.g` as a map. It is an `AtomicBoolean` in this APK.
- Relying on fixed obfuscated method names like `K`, `m`, `y`, `q`.
- Polling only from `getFacePaySkinModel()`. That method belongs to payment-screen skin and may not run when only app theme is needed.

MD5 bypass should be a fallback only. Prefer generating a real md5 for the copied theme folder and using a fresh `cacheTime`.

## 8. Best Version-Compatible Integration

Use public/stable wrapper classes first:

```text
com.alipay.mobile.skincenter.service.SCSkinCenterService
com.alipay.mobile.skincenter.util.SCCommonUtil
com.alipay.mobile.skincenter.manage.AntSkinRenderManager
com.alipay.mobile.skincenter.model.SCCacheInfoModel
```

`SCSkinCenterService` exposes stable method names:

```text
hasEnableSkin(String)
hasEnableSkin(String, Map)
getSkinId(String)
getSkinId(String, Map)
loadMetaInfo(String)
loadMetaInfo(String, String)
loadResSync(...)
loadResSyncForFilePath(...)
initSkinScene(String)
isSkinCenterThemeEnable()
```

Recommended strategy:

1. Primary path: prefs + file copy + refresh.
2. Resolve current user ID through `SCCommonUtil.getCurrentUserId()`.
3. Resolve `SCInnerManager` fields by type, not by name:
   - `Map<String, SCCacheInfoModel>` = skin cache map
   - `Map<String, SCMetaModel>` = meta cache
   - `ConcurrentHashMap<String, String>` = theme id cache
   - `AtomicBoolean` = cache loaded flag
4. Resolve singleton method by return type:
   - static no-arg method returning `SCInnerManager`
5. Resolve reload method by behavior/signature if names differ:
   - no-arg instance method that reads `cached_skin_info_v2`
   - in this APK it is `g()`
6. Use fallback hooks only when prefs mode fails:
   - hook `SCSkinCenterService.hasEnableSkin("theme")` and return `true`
   - hook `SCSkinCenterService.getSkinId("theme")` and return selected theme
   - hook resource loading only if Alipay cannot resolve files from `skin_center_dir`

This should survive more APK versions than directly hooking obfuscated `SCInnerManager` methods.

## 9. Practical Fix Needed If Porting LiYiCha

If integrating LiYiCha into this repo, update `ThemeManager.updateSharedPreferences()` for this APK behavior:

```text
Use key cached_skin_info_v2 + userId, not cached_skin_info_v2# + userId.
Also write cached_skin_theme + userId = selectedThemeId.
```

Also wire the theme feature into the active Xposed entrypoint. In the checked LiYiCha repo:

```text
app\src\main\assets\xposed_init
```

still points to:

```text
im.hoho.alipayInstallB.PluginMain
```

and `PluginMain.java` still mainly contains the old Java payment-screen hook. `ThemeHookV2` exists but is not clearly wired into the active Xposed entrypoint.
