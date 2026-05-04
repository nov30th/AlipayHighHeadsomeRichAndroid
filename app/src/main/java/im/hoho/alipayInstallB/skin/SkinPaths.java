package im.hoho.alipayInstallB.skin;

import android.os.Environment;

import java.io.File;

/**
 * v2 皮肤管理路径与保留名集中定义。
 * App 与 Xposed Hook 共用本类。
 */
public final class SkinPaths {

    public static final String ALIPAY_PKG = "com.eg.android.AlipayGphone";
    public static final String ROOT_REL =
            "Android/media/" + ALIPAY_PKG + "/000_HOHO_ALIPAY_SKIN";

    public static final String REMOTE_MANIFEST_URL =
            "https://github.com/nov30th/AlipayHighHeadsomeRichAndroid/raw/master/remote_skins/manifest.json";

    public static final String REMOTE_THEME_MANIFEST_URL =
            "https://github.com/nov30th/AlipayHighHeadsomeRichAndroid/raw/master/remote_themes/manifest.json";

    private static final String[] RESERVED = {
            "actived", "update", "delete", "export", "theme_export",
            "theme_actived", "theme_update",
            "skins", "exports", "imports_tmp", "themes",
            "selected_skins.json", "selected_theme", "migration_v2_done"
    };

    private SkinPaths() {
    }

    public static File root() {
        return new File(Environment.getExternalStorageDirectory(), ROOT_REL);
    }

    public static File skinsDir() {
        return new File(root(), "skins");
    }

    public static File exportsDir() {
        return new File(root(), "exports");
    }

    public static File themesDir() {
        return new File(root(), "themes");
    }

    public static File importsTmpDir() {
        return new File(root(), "imports_tmp");
    }

    public static File selectedSkinsJson() {
        return new File(root(), "selected_skins.json");
    }

    public static File migrationDoneFile() {
        return new File(root(), "migration_v2_done");
    }

    public static File activedFlag() {
        return new File(root(), "actived");
    }

    public static File updateFlag() {
        return new File(root(), "update");
    }

    public static File deleteFlag() {
        return new File(root(), "delete");
    }

    public static File exportFlag() {
        return new File(root(), "export");
    }

    public static File themeExportFlag() {
        return new File(root(), "theme_export");
    }

    public static File themeUpdateFlag() {
        return new File(root(), "theme_update");
    }

    public static File selectedThemeFile() {
        return new File(root(), "selected_theme");
    }

    public static File alipayPrivateRoot() {
        return new File("/data/data/" + ALIPAY_PKG + "/files/onsitepay_skin_dir");
    }

    public static File alipayThemeRoot() {
        return new File("/data/data/" + ALIPAY_PKG + "/files/skin_center_dir");
    }

    public static File alipayHohoCache() {
        return new File(alipayPrivateRoot(), "HOHO");
    }

    public static boolean isReservedName(String name) {
        if (name == null || name.isEmpty()) return true;
        if (name.startsWith("level_")) return true;
        for (String r : RESERVED) {
            if (r.equals(name)) return true;
        }
        return false;
    }
}
