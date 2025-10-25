package im.hoho.alipayInstallB;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;

import com.alibaba.fastjson.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

            // helper: try multiple class name candidates (avoid Java 8 lambda for compatibility)
            final String[][] classCandidates = new String[][]{
                    // candidates for MergeMemberGradeEnum
                    {
                            "com.alipay.mobile.onsitepay9.utils.MergeMemberGradeEnum",
                            "com.alipay.mobile.onsitepay10.utils.MergeMemberGradeEnum",
                            "com.alipay.mobile.onsitepay.utils.MergeMemberGradeEnum",
                            "com.alipay.mobile.foundation.utils.MergeMemberGradeEnum",
                            "MergeMemberGradeEnum"
                    }
            };

            // find class with candidate names
            Class<?> memberGradeEnumClass = findClassWithCandidates(new String[]{
                    "com.alipay.mobile.onsitepay9.utils.MergeMemberGradeEnum",
                    "com.alipay.mobile.onsitepay10.utils.MergeMemberGradeEnum",
                    "com.alipay.mobile.onsitepay.utils.MergeMemberGradeEnum",
                    "com.alipay.mobile.foundation.utils.MergeMemberGradeEnum",
                    "MergeMemberGradeEnum"
            }, lpparam.classLoader);

            // 添加对MergeMemberGrade的hook
            try {
                if (memberGradeEnumClass != null) {
                    try {
                        XposedHelpers.findAndHookMethod(memberGradeEnumClass, "convertMemberGrade", String.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                String newGrade = getCurrentMemberGrade();
                                XposedBridge.log("Member grade changing to: " + newGrade);
                                if (!newGrade.equals("原有")) {
                                    try {
                                        switch (newGrade) {
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
                                    } catch (Throwable t) {
                                        XposedBridge.log("Error setting member grade enum: " + t.getMessage());
                                    }
                                }
                            }
                        });
                        XposedBridge.log("convertMemberGrade hooked.");
                    } catch (NoSuchMethodError e) {
                        XposedBridge.log("convertMemberGrade method not found: " + e.getMessage());
                    }
                } else {
                    XposedBridge.log("MergeMemberGradeEnum class not found among candidates.");
                }
            } catch (Throwable e) {
                XposedBridge.log("Error hooking MergeMemberGradeEnum: " + e.getMessage());
            }

            // Hook login ext res attrs (原 logic)
            try {
                Class<?> userLoginResultClass = findClassWithCandidates(new String[]{
                        "com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult",
                        "com.alipay.mobilegw.biz.shared.processor.login.UserLoginResult",
                        "com.alipay.mobilegw.biz.shared.login.UserLoginResult",
                        "com.alipay.mobilegw.biz.login.UserLoginResult",
                        "com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult" // keep original as fallback
                }, lpparam.classLoader);

                if (userLoginResultClass != null) {
                    XposedHelpers.findAndHookMethod(userLoginResultClass, "getExtResAttrs", new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam param1MethodHookParam) throws Throwable {
                            XposedBridge.log("Now, let's install B...");
                            Map<String, String> map = (Map) param1MethodHookParam.getResult();
                            if (map != null && map.containsKey("memberGrade")) {
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
                                XposedBridge.log("Can not get the member grade in return value...");
                            }
                        }
                    });
                } else {
                    XposedBridge.log("UserLoginResult class not found among candidates.");
                }
            } catch (Throwable e) {
                XposedBridge.log("UserLoginResult hook error: " + e.getMessage());
            }

            // Hook Activity.onCreate for DB update (keep original behavior; safe checks)
            try {
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
                        if (dbFile != null && dbFile.exists()) {
                            XposedBridge.log("GET DATABASE: " + context.getDatabasePath("alipayclient.db").getParentFile());
                            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                                // 将本来的普通会员直接替换
                                db.execSQL("UPDATE 'main'.'userinfo' SET 'memberGrade' = '" + newGrade + "'");
                                XposedBridge.log("Database update successful!");
                            } catch (Exception e) {
                                XposedBridge.log("Database update error: " + e);
                            }
                        } else {
                            XposedBridge.log("CAN NOT GET DATABASE: maybe not exist or permission denied, Ignore!");
                        }
                        XposedBridge.log("--------------DATABASE_UPDATER--------------");
                        isDbUpdated[0] = true;
                    }
                });
            } catch (Throwable e) {
                XposedBridge.log("Activity.onCreate hook error: " + e.getMessage());
            }

            //region modify skin - try multiple ConfigUtilBiz candidates
            try {
                String[] configUtilCandidates = new String[]{
                        "com.alipay.mobile.onsitepaystatic.ConfigUtilBiz",
                        "com.alipay.mobile.onsitepay.ConfigUtilBiz",
                        "com.alipay.mobile.onsitepaystatic.utils.ConfigUtilBiz",
                        "com.alipay.mobile.onsitepaystatic.biz.ConfigUtilBiz",
                        "ConfigUtilBiz"
                };
                Class<?> configClass = findClassWithCandidates(configUtilCandidates, lpparam.classLoader);
                final Class<?> ospSkinModelClass = findClassWithCandidates(new String[]{
                        "com.alipay.mobile.onsitepaystatic.skin.OspSkinModel",
                        "com.alipay.mobile.onsitepay.skin.OspSkinModel",
                        "com.alipay.mobile.onsitepay.OspSkinModel",
                        "OspSkinModel"
                }, lpparam.classLoader);

                if (configClass != null) {
                    XposedHelpers.findAndHookMethod(configClass, "getFacePaySkinModel", new XC_MethodHook() {

                        @SuppressWarnings("ResultOfMethodCallIgnored")
                        public void deleteFile(File file) {
                            if (file == null) return;
                            if (file.isDirectory()) {
                                File[] files = file.listFiles();
                                if (files != null) {
                                    for (File f : files) {
                                        deleteFile(f);
                                    }
                                }
                            }
                            try { file.delete(); } catch (Exception ignored) {}
                        }

                        @SuppressWarnings("ResultOfMethodCallIgnored")
                        public void copy(String fromFile, String toFile) {
                            File root = new File(fromFile);
                            if (!root.exists()) {
                                return;
                            }
                            File[] currentFiles = root.listFiles();
                            if (currentFiles == null) return;
                            File targetDir = new File(toFile);
                            if (!targetDir.exists()) {
                                targetDir.mkdirs();
                            }
                            for (File currentFile : currentFiles) {
                                if (currentFile.isDirectory()) {
                                    copy(currentFile.getPath(), toFile + "/" + currentFile.getName());
                                } else {
                                    CopySdcardFile(currentFile.getPath(), toFile + "/" + currentFile.getName());
                                }
                            }
                        }

                        public void CopySdcardFile(String fromFile, String toFile) {
                            try {
                                InputStream fosfrom = new FileInputStream(fromFile);
                                OutputStream fosto = new FileOutputStream(toFile);
                                byte[] bt = new byte[1024];
                                int c;
                                while ((c = fosfrom.read(bt)) > 0) {
                                    fosto.write(bt, 0, c);
                                }
                                fosfrom.close();
                                fosto.close();
                            } catch (Exception ex) {
                                XposedBridge.log("ERROR: CopySdcardFile: " + ex.getMessage());
                            }
                        }

                        public List<String> searchSkins(String Path) {
                            List<String> resultList = new ArrayList<>();
                            try {
                                File root = new File(Path);
                                if (!root.exists()) return resultList;
                                File[] files = root.listFiles();
                                if (files == null) return resultList;
                                for (File f : files) {
                                    if (f.isDirectory()) {
                                        if (f.getName().equals("update") || f.getName().equals("actived") || f.getName().equals("delete") || f.getName().startsWith("level_")) {
                                            continue;
                                        }
                                        resultList.add(f.getName());
                                    }
                                }
                            } catch (Exception ignored) {}
                            return resultList;
                        }

                        @SuppressWarnings("ResultOfMethodCallIgnored")
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                            String fixedPathInAliData = "/data/data/" + packageName + "/files/onsitepay_skin_dir/HOHO";
                            String alipaySkinsRoot = "/data/data/" + packageName + "/files/onsitepay_skin_dir";
                            File hohoSkinFileInAliData = new File(fixedPathInAliData);

                            String basePathUpdates = Environment.getExternalStorageDirectory() + "/Android/media/" + packageName;
                            if (!new File(basePathUpdates).exists()) {
                                XposedBridge.log("DEBUG: creating skin SD card path: " + basePathUpdates);
                                // create dir
                                new File(basePathUpdates).mkdirs();
                            }
                            String fixedPathUpdates = basePathUpdates + "/000_HOHO_ALIPAY_SKIN";

                            File skinActived = new File(fixedPathUpdates + "/actived");
                            File skinUpdateRequired = new File(fixedPathUpdates + "/update");
                            File skinDeleteRequired = new File(fixedPathUpdates + "/delete");
                            File exportSkinSign = new File(fixedPathUpdates + "/export");

                            if (exportSkinSign.exists()) {
                                try {
                                    //export skin
                                    XposedBridge.log("exporting skin...");
                                    File alipaySkinsRootFile = new File(alipaySkinsRoot);
                                    if (!alipaySkinsRootFile.exists()) {
                                        XposedBridge.log("no skins found, ignore export");
                                    } else {
                                        File fixedPathUpdatesFile = new File(fixedPathUpdates);
                                        if (!fixedPathUpdatesFile.exists()) {
                                            fixedPathUpdatesFile.mkdirs();
                                        }
                                        File[] alipaySkinsRootFileList = alipaySkinsRootFile.listFiles();
                                        if (alipaySkinsRootFileList != null) {
                                            for (File alipaySkinsRootFileListItem : alipaySkinsRootFileList) {
                                                if (alipaySkinsRootFileListItem.isDirectory()) {
                                                    if (alipaySkinsRootFileListItem.getName().equals("HOHO")) {
                                                        continue;
                                                    }
                                                    XposedBridge.log("exporting skin: " + alipaySkinsRootFileListItem.getName());
                                                    copy(alipaySkinsRootFileListItem.getPath(), fixedPathUpdates + "/" + alipaySkinsRootFileListItem.getName());
                                                }
                                            }
                                        }
                                        exportSkinSign.delete();
                                    }
                                } catch (Exception e) {
                                    XposedBridge.log("ERROR: export skin: " + e.getMessage());
                                }
                            }

                            if (skinDeleteRequired.exists()) {
                                XposedBridge.log("deleting skin...");
                                skinDeleteRequired.delete();
                                deleteFile(hohoSkinFileInAliData);
                                XposedBridge.log("skin is deleted");
                            }

                            if (skinUpdateRequired.exists()) {
                                XposedBridge.log("copying skin...");
                                skinUpdateRequired.delete();
                                if (!hohoSkinFileInAliData.exists()) hohoSkinFileInAliData.mkdirs();
                                copy(fixedPathUpdates, fixedPathInAliData);
                                XposedBridge.log("copied files..");
                            }

                            if (hohoSkinFileInAliData.exists() && skinActived.exists()) {
                                XposedBridge.log("updating skins..");
                                List<String> randomConf = searchSkins(fixedPathInAliData);
                                String subFolder = "";
                                if (randomConf.size() > 0) {
                                    int pos = (int) (Math.random() * 100) % randomConf.size();
                                    subFolder = randomConf.get(pos);
                                }
                                // update minWalletVersion to support newer Alipay versions
                                String hohoSkinModel_Xiaomi12Pro = "{\"md5\":\"HOHO_MD5\",\"minWalletVersion\":\"10.7.90.8100\",\"outDirName\":\"HOHO/" + subFolder + "\",\"skinId\":\"HOHO_CUSTOMIZED\",\"skinStyleId\":\"2022 New Year Happy!\",\"userId\":\"HOHO\"}";
                                Object skinModel;
                                try {
                                    if (ospSkinModelClass != null) {
                                        skinModel = JSON.parseObject(hohoSkinModel_Xiaomi12Pro, ospSkinModelClass);
                                    } else {
                                        skinModel = JSON.parseObject(hohoSkinModel_Xiaomi12Pro, Object.class);
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log("JSON parse to OspSkinModel failed, fallback to Object: " + t.getMessage());
                                    skinModel = JSON.parseObject(hohoSkinModel_Xiaomi12Pro, Object.class);
                                }
                                param.setResult(skinModel);
                                XposedBridge.log("skin updated..");
                            } else {
                                XposedBridge.log("skin is not active.");
                            }
                        }
                    });
                } else {
                    XposedBridge.log("ConfigUtilBiz class not found among candidates.");
                }
            } catch (Throwable e) {
                XposedBridge.log("ConfigUtilBiz hook error: " + e.getMessage());
            }
            //endregion
        }

    }

    /**
     * Try several candidate fully-qualified class names and return first found, or null.
     */
    private static Class<?> findClassWithCandidates(String[] candidates, ClassLoader cl) {
        if (candidates == null || cl == null) return null;
        for (String name : candidates) {
            try {
                Class<?> c = XposedHelpers.findClass(name, cl);
                XposedBridge.log("Found class candidate: " + name);
                return c;
            } catch (XposedHelpers.ClassNotFoundError e) {
                // ignore, try next
            } catch (Throwable t) {
                XposedBridge.log("Error while trying to load class " + name + ": " + t.getMessage());
            }
        }
        return null;
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
