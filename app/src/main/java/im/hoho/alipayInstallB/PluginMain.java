package im.hoho.alipayInstallB;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;

import com.alibaba.fastjson.JSON;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import im.hoho.alipayInstallB.skin.SelectedSkins;
import im.hoho.alipayInstallB.skin.SkinIO;
import im.hoho.alipayInstallB.skin.SkinPaths;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by qzj_ on 2016/5/9.
 */
public class PluginMain implements IXposedHookLoadPackage {
    private static final String EXTERNAL_STORAGE_PATH = Environment.getExternalStorageDirectory() + "/Android/media/com.eg.android.AlipayGphone/000_HOHO_ALIPAY_SKIN";
    private static final String packageName = "com.eg.android.AlipayGphone";
    public static volatile boolean isModuleLoaded = false;

    public PluginMain() {
        XposedBridge.log("Now Loading HOHO`` alipay plugin...");
    }


    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals(packageName)) {
            XposedBridge.log("Loaded App: " + lpparam.packageName);
            XposedBridge.log("Powered by HOHO`` 20230927 杭州亚运会版 sd source changed 20231129");
            final boolean[] isDbUpdated = {false};

            // 添加对MergeMemberGrade的hook
            try {
                Class<?> memberGradeEnumClass = XposedHelpers.findClass("com.alipay.mobile.onsitepay9.utils.MergeMemberGradeEnum", lpparam.classLoader);
                if (memberGradeEnumClass != null) {
                    XposedHelpers.findAndHookMethod("com.alipay.mobile.onsitepay9.utils.MergeMemberGradeEnum",
                            lpparam.classLoader,
                            "convertMemberGrade",
                            String.class,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    String newGrade = getCurrentMemberGrade();
                                    XposedBridge.log("Member grade changing to: " + newGrade);
                                    if (!newGrade.equals("原有")) {
                                        switch(newGrade) {
                                            case "primary":
                                                param.setResult(XposedHelpers.getStaticObjectField(memberGradeEnumClass, "PRIMARY"));
                                                break;
                                            case "golden":
                                                param.setResult(XposedHelpers.getStaticObjectField(memberGradeEnumClass, "GOLDEN"));
                                                break;
                                            case "platinum":
                                                param.setResult(XposedHelpers.getStaticObjectField(memberGradeEnumClass, "PLATINUM"));
                                                break;
                                            case "diamond":
                                                param.setResult(XposedHelpers.getStaticObjectField(memberGradeEnumClass, "DIAMOND"));
                                                break;
                                            default:
                                                param.setResult(XposedHelpers.getStaticObjectField(memberGradeEnumClass, "NULL"));
                                                break;
                                        }
                                        XposedBridge.log("Member grade changed to: " + newGrade);
                                    }
                                }
                            });
                    XposedBridge.log("convertMemberGrade hooked.");
                } else {
                    XposedBridge.log("MergeMemberGradeEnum class not found.");
                }
            } catch (XposedHelpers.ClassNotFoundError e) {
                XposedBridge.log("MergeMemberGradeEnum class not found: " + e.getMessage());
            } catch (NoSuchMethodError e) {
                XposedBridge.log("convertMemberGrade method not found: " + e.getMessage());
            } catch (Exception e) {
                XposedBridge.log("Error while hooking convertMemberGrade: " + e.getMessage());
            }

            try {
                Class<?> UserLoginResultClass = XposedHelpers.findClass("com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult", lpparam.classLoader);
                if (UserLoginResultClass != null) {
                    XposedHelpers.findAndHookMethod("com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult", lpparam.classLoader, "getExtResAttrs", new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam param1MethodHookParam) throws Throwable {
                            XposedBridge.log("Now, let's install B...");
                            Map<String, String> map = (Map) param1MethodHookParam.getResult();
                            if (map.containsKey("memberGrade")) {
                                XposedBridge.log("Original member grade: " + map.get("memberGrade"));

                                String newGrade = getCurrentMemberGrade();
                                if (!newGrade.equals("原有")) {
                                    XposedBridge.log("Putting " + newGrade + " into dict...");
                                    map.put("memberGrade", newGrade);
                                    XposedBridge.log("Member grade changed to: " + map.get("memberGrade"));
                                } else {
                                    XposedBridge.log("Member grade not modified.");
                                }
                            } else {
                                XposedBridge.log("Can not get the member grade in return value...WTF?");
                            }
                        }
                    });
                } else {
                    XposedBridge.log("UserLoginResult class not found.");
                }
            } catch (Exception e) {
                XposedBridge.log("UserLoginResult class not found.");
            }

            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String newGrade = getCurrentMemberGrade();

                    if (isDbUpdated[0] || newGrade.equals("原有")) {
                        return;
                    }
                    Context context = (Context) param.thisObject; // 获取到Activity作为Context
                    XposedBridge.log("--------------DATABASE_UPDATER--------------");
                    File dbFile = context.getDatabasePath("alipayclient.db");
                    if (dbFile.exists()) {
                        XposedBridge.log("GET DATABASE: " + context.getDatabasePath("alipayclient.db").getParentFile());
                        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                            // 将本来的普通会员直接替换
                            db.execSQL("UPDATE 'main'.'userinfo' SET 'memberGrade' = '" + newGrade + "'");
                            XposedBridge.log("Database update successful!");
                        } catch (Exception e) {
                            XposedBridge.log("Database update error: " + e);
                        }
                    } else {
                        XposedBridge.log("CAN NOT GET DATABASE: " + context.getDatabasePath("alipayclient.db").getParentFile() + ", Ignore!");
                    }
                    XposedBridge.log("--------------DATABASE_UPDATER--------------");
                    isDbUpdated[0] = true;
                }
            });


            //region modify skin (v2)
            final Class<?> OspSkinModel = lpparam.classLoader.loadClass("com.alipay.mobile.onsitepaystatic.skin.OspSkinModel");

            XposedHelpers.findAndHookMethod("com.alipay.mobile.onsitepaystatic.ConfigUtilBiz", lpparam.classLoader, "getFacePaySkinModel", new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    File root = SkinPaths.root();
                    if (!root.exists()) {
                        XposedBridge.log("[v2] skin root missing, skip");
                        return;
                    }

                    File hohoCache = SkinPaths.alipayHohoCache();
                    File actived = SkinPaths.activedFlag();
                    File updateFlag = SkinPaths.updateFlag();
                    File deleteFlag = SkinPaths.deleteFlag();
                    File exportFlag = SkinPaths.exportFlag();
                    File alipayPrivateRoot = SkinPaths.alipayPrivateRoot();
                    File skinsDir = SkinPaths.skinsDir();

                    // 1) export: 把支付宝内置皮肤复制到 skins/<原目录名>/
                    if (exportFlag.exists()) {
                        try {
                            XposedBridge.log("[v2] exporting alipay built-in skins...");
                            if (alipayPrivateRoot.exists()) {
                                if (!skinsDir.exists()) skinsDir.mkdirs();
                                File[] privateChildren = alipayPrivateRoot.listFiles();
                                if (privateChildren != null) {
                                    for (File p : privateChildren) {
                                        if (p == null || !p.isDirectory()) continue;
                                        if ("HOHO".equals(p.getName())) continue;
                                        if (SkinPaths.isReservedName(p.getName())) continue;
                                        File dst = new File(skinsDir, p.getName());
                                        if (dst.exists()) SkinIO.deleteRecursive(dst);
                                        try {
                                            SkinIO.copyDir(p, dst);
                                            XposedBridge.log("[v2] exported skin: " + p.getName());
                                        } catch (Exception e) {
                                            XposedBridge.log("[v2] export skin failed: "
                                                    + p.getName() + " -> " + e.getMessage());
                                        }
                                    }
                                }
                            }
                            SkinIO.deleteRecursive(exportFlag);
                        } catch (Exception e) {
                            XposedBridge.log("[v2] export error: " + e.getMessage());
                        }
                    }

                    // 2) delete: 清空 HOHO 缓存
                    if (deleteFlag.exists()) {
                        try {
                            SkinIO.deleteRecursive(deleteFlag);
                            if (hohoCache.exists()) SkinIO.deleteRecursive(hohoCache);
                            XposedBridge.log("[v2] HOHO cache cleared");
                        } catch (Exception e) {
                            XposedBridge.log("[v2] delete error: " + e.getMessage());
                        }
                    }

                    // 3) update: 清空 HOHO 缓存 + 按 selected_skins.json 复制
                    if (updateFlag.exists()) {
                        try {
                            SkinIO.deleteRecursive(updateFlag);
                            if (hohoCache.exists()) SkinIO.deleteRecursive(hohoCache);
                            if (!hohoCache.mkdirs()) {
                                XposedBridge.log("[v2] mkdirs HOHO failed: " + hohoCache);
                            }
                            List<String> selected = SelectedSkins.read(SkinPaths.selectedSkinsJson());
                            int copied = 0;
                            for (String name : selected) {
                                if (name == null || SkinPaths.isReservedName(name)) continue;
                                File src = new File(skinsDir, name);
                                if (!src.exists() || !src.isDirectory()) continue;
                                try {
                                    SkinIO.copyDir(src, new File(hohoCache, name));
                                    copied++;
                                } catch (Exception e) {
                                    XposedBridge.log("[v2] copy skin failed: "
                                            + name + " -> " + e.getMessage());
                                }
                            }
                            XposedBridge.log("[v2] HOHO cache rebuilt, " + copied + " skins copied");
                        } catch (Exception e) {
                            XposedBridge.log("[v2] update error: " + e.getMessage());
                        }
                    }

                    // 4) 应用皮肤
                    if (!actived.exists()) {
                        XposedBridge.log("[v2] not actived, skip");
                        return;
                    }
                    if (!hohoCache.exists() || !hohoCache.isDirectory()) {
                        XposedBridge.log("[v2] HOHO cache missing, skip");
                        return;
                    }
                    File[] cached = hohoCache.listFiles();
                    if (cached == null) {
                        XposedBridge.log("[v2] HOHO cache unreadable, skip");
                        return;
                    }
                    List<String> available = new ArrayList<>();
                    for (File c : cached) {
                        if (c == null || !c.isDirectory()) continue;
                        if (SkinPaths.isReservedName(c.getName())) continue;
                        available.add(c.getName());
                    }
                    if (available.isEmpty()) {
                        XposedBridge.log("[v2] HOHO cache empty, skip");
                        return;
                    }
                    String pick = available.get((int) (Math.random() * available.size()));
                    String json = "{\"md5\":\"HOHO_MD5\",\"minWalletVersion\":\"10.2.23.0000\""
                            + ",\"outDirName\":\"HOHO/" + pick + "\""
                            + ",\"skinId\":\"HOHO_CUSTOMIZED\""
                            + ",\"skinStyleId\":\"HOHO_SKIN\""
                            + ",\"userId\":\"HOHO\"}";
                    Object skinModel = JSON.parseObject(json, OspSkinModel);
                    param.setResult(skinModel);
                    XposedBridge.log("[v2] skin applied: " + pick);
                }
            });
            //endregion
        }

    }

    private String getCurrentMemberGrade() {
        String[] grades = {"primary", "golden", "platinum", "diamond", "unknown"};
        for (String grade : grades) {
            File folder = new File(EXTERNAL_STORAGE_PATH, "level_" + grade);
            if (folder.exists()) {
                return grade;
            }
        }
        return "原有";
    }
}
