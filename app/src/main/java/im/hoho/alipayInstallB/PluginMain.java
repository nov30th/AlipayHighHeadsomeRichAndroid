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


            //region modify skin
//            final Class<?> ConfigUtilBiz = lpparam.classLoader.loadClass("com.alipay.mobile.onsitepaystatic.ConfigUtilBiz");
            final Class<?> OspSkinModel = lpparam.classLoader.loadClass("com.alipay.mobile.onsitepaystatic.skin.OspSkinModel");

            XposedHelpers.findAndHookMethod("com.alipay.mobile.onsitepaystatic.ConfigUtilBiz", lpparam.classLoader, "getFacePaySkinModel", new XC_MethodHook() {


                @SuppressWarnings("ResultOfMethodCallIgnored")
                public void deleteFile(File file) {
                    if (file.isDirectory()) {
                        File[] files = file.listFiles();
                        for (File f : files) {
                            deleteFile(f);
                        }
                        file.delete();
                    } else if (file.exists()) {
                        file.delete();
                    }
                }

                @SuppressWarnings("ResultOfMethodCallIgnored")
                public void copy(String fromFile, String toFile) {
//                            XposedBridge.log("DEBUG: copy: " + fromFile + " to " + toFile);
                    File[] currentFiles;
                    File root = new File(fromFile);
                    if (!root.exists()) {
                        return;
                    }
                    currentFiles = root.listFiles();
                    File targetDir = new File(toFile);
                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                    }
                    for (File currentFile : currentFiles) {
                        if (currentFile.isDirectory())//如果当前项为子目录 进行递归
                        {
                            copy(currentFile.getPath(), toFile + "/" + currentFile.getName());

                        } else//如果当前项为文件则进行文件拷贝
                        {
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
                    File[] files = new File(Path).listFiles();

                    for (int i = 0; i < files.length; i++) {
                        File f = files[i];
                        if (f.isDirectory()) {
                            if (f.getName().equals("update") || f.getName().equals("actived") || f.getName().equals("delete") || f.getName().startsWith("level_")) {
                                continue;
                            }
                            String filename = f.getName();
                            resultList.add(f.getName());
                        }
                    }
                    return resultList;
                }

                @SuppressWarnings("ResultOfMethodCallIgnored")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    String fixedPathInAliData = "/data/data/" + packageName + "/files/onsitepay_skin_dir/HOHO";
                    String alipaySkinsRoot = "/data/data/" + packageName + "/files/onsitepay_skin_dir";
//                    XposedBridge.log("DEBUG: fixedPathInAliData: " + fixedPathInAliData);
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
                            //checks alipaySkinsRoot
                            File alipaySkinsRootFile = new File(alipaySkinsRoot);
                            if (!alipaySkinsRootFile.exists()) {
                                //ignore export as no skins found
                                XposedBridge.log("no skins found, ignore export");
                            } else {
                                //checks fixedPathUpdates is exists
                                File fixedPathUpdatesFile = new File(fixedPathUpdates);
                                if (!fixedPathUpdatesFile.exists()) {
                                    //create fixedPathUpdates
                                    fixedPathUpdatesFile.mkdirs();
                                }
                                //copies all skins to fixedPathUpdates except HOHO dir
                                File[] alipaySkinsRootFileList = alipaySkinsRootFile.listFiles();
                                for (File alipaySkinsRootFileListItem : alipaySkinsRootFileList) {
                                    if (alipaySkinsRootFileListItem.isDirectory()) {
                                        if (alipaySkinsRootFileListItem.getName().equals("HOHO")) {
                                            continue;
                                        }
                                        XposedBridge.log("exporting skin: " + alipaySkinsRootFileListItem.getName());
                                        copy(alipaySkinsRootFileListItem.getPath(), fixedPathUpdates + "/" + alipaySkinsRootFileListItem.getName());
                                    }
                                }
                                //removes the export sign
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
                            //random config
//                                    XposedBridge.log("DEBUG: randomConf size: " + randomConf.size());
                            int pos = (int) (Math.random() * 100) % randomConf.size();
//                                    CopySdcardFile(randomConf.get(pos), fixedPathInAliData + "/meta.json");
                            subFolder = randomConf.get(pos);
//                                    XposedBridge.log("DEBUG: random, " + subFolder + " as current folder.");
                        }
                        String hohoSkinModel_Xiaomi12Pro = "{\"md5\":\"HOHO_MD5\",\"minWalletVersion\":\"10.2.23.0000\",\"outDirName\":\"HOHO/" + subFolder + "\",\"skinId\":\"HOHO_CUSTOMIZED\",\"skinStyleId\":\"2022 New Year Happy!\",\"userId\":\"HOHO\"}";
                        Object skinModel = JSON.parseObject(hohoSkinModel_Xiaomi12Pro, OspSkinModel);
                        param.setResult(skinModel);
                        XposedBridge.log("skin updated..");
                    } else {
                        XposedBridge.log("skin is not active.");
                    }
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
