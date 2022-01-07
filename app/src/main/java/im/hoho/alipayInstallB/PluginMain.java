package im.hoho.alipayInstallB;

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

    public PluginMain() {
        XposedBridge.log("Now Loading HOHO`` alipay plugin...");
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {


        if (lpparam.packageName.contains("com.eg.android.AlipayGphone")) {
            XposedBridge.log("Loaded App: " + lpparam.packageName);
            XposedBridge.log("Powered by HOHO`` 20220107");

            XposedHelpers.findAndHookMethod("com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult",
                    lpparam.classLoader,
                    "getExtResAttrs",
                    new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam param1MethodHookParam) throws Throwable {
                            XposedBridge.log("Now, let's install B...");
                            String diamond = "diamond";
                            Map<String, String> map = (Map) param1MethodHookParam.getResult();
                            if (map.containsKey("memberGrade")) {
                                XposedBridge.log("Original member grade: " + (String) map.get("memberGrade"));
                                XposedBridge.log("Putting " + diamond + " into dict...");
                                map.put("memberGrade", diamond);
                                XposedBridge.log("Member grade changed to: " + (String) map.get("memberGrade"));
                            } else {
                                XposedBridge.log("Can not get the member grade in return value...WTF?");
                            }
                        }
                    });

            //region modify skin

            final Class<?> ConfigUtilBiz = lpparam.classLoader.loadClass("com.alipay.mobile.onsitepaystatic.ConfigUtilBiz");
            final Class<?> OspSkinModel = lpparam.classLoader.loadClass("com.alipay.mobile.onsitepaystatic.skin.OspSkinModel");

            XposedHelpers.findAndHookMethod("com.alipay.mobile.onsitepaystatic.ConfigUtilBiz",
                    lpparam.classLoader,
                    "getFacePaySkinModel",
                    new XC_MethodHook() {


                        public void deleteFile(File file) {
                            if (file.isDirectory()) {
                                File[] files = file.listFiles();
                                for (int i = 0; i < files.length; i++) {
                                    File f = files[i];
                                    deleteFile(f);
                                }
                                file.delete();
                            } else if (file.exists()) {
                                file.delete();
                            }
                        }

                        public int copy(String fromFile, String toFile) {
//                            XposedBridge.log("DEBUG: copy: " + fromFile + " to " + toFile);
                            File[] currentFiles;
                            File root = new File(fromFile);
                            if (!root.exists()) {
                                return -1;
                            }
                            currentFiles = root.listFiles();
                            File targetDir = new File(toFile);
                            if (!targetDir.exists()) {
                                targetDir.mkdirs();
                            }
                            for (int i = 0; i < currentFiles.length; i++) {
                                if (currentFiles[i].isDirectory())//如果当前项为子目录 进行递归
                                {
                                    copy(currentFiles[i].getPath(), toFile + "/" + currentFiles[i].getName());

                                } else//如果当前项为文件则进行文件拷贝
                                {
                                    CopySdcardFile(currentFiles[i].getPath(), toFile + "/" + currentFiles[i].getName());
                                }
                            }
                            return 0;
                        }

                        public int CopySdcardFile(String fromFile, String toFile) {

                            try {
                                InputStream fosfrom = new FileInputStream(fromFile);
                                OutputStream fosto = new FileOutputStream(toFile);
                                byte bt[] = new byte[1024];
                                int c;
                                while ((c = fosfrom.read(bt)) > 0) {
                                    fosto.write(bt, 0, c);
                                }
                                fosfrom.close();
                                fosto.close();
                                return 0;

                            } catch (Exception ex) {
                                return -1;
                            }
                        }

                        public List<String> searchSkins(String Path) {
                            List<String> resultList = new ArrayList<>();
                            File[] files = new File(Path).listFiles();

                            for (int i = 0; i < files.length; i++) {
                                File f = files[i];
                                if (f.isDirectory()) {
                                    if (f.getName().equals("update") ||
                                            f.getName().equals("actived") ||
                                            f.getName().equals("delete")
                                            )
                                        continue;
                                    String filename = f.getName();
                                    resultList.add(f.getName());
                                }
                            }
                            return resultList;
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param)
                                throws Throwable {

                            String fixedPathInAliData = "/data/data/com.eg.android.AlipayGphone/files/onsitepay_skin_dir/HOHO";
                            XposedBridge.log("DEBUG: fixedPathInAliData: " + fixedPathInAliData);
                            File hohoSkinFileInAliData = new File(fixedPathInAliData);

                            String fixedPathUpdates = Environment.getExternalStorageDirectory() + "/000_HOHO_ALIPAY_SKIN";
                            XposedBridge.log("DEBUG: skin SD card path: " + fixedPathUpdates);

                            File skinActived = new File(fixedPathUpdates + "/actived");
                            File skinUpdateRequired = new File(fixedPathUpdates + "/update");
                            File skinDeleteRequired = new File(fixedPathUpdates + "/delete");

                            if (skinDeleteRequired.exists()) {
                                XposedBridge.log("deleting skin...");
                                skinDeleteRequired.delete();
                                deleteFile(hohoSkinFileInAliData);
                                XposedBridge.log("skin is deleted");
                            }

                            if (skinUpdateRequired.exists()) {
                                XposedBridge.log("copying skin...");
                                skinUpdateRequired.delete();
                                if (!hohoSkinFileInAliData.exists())
                                    hohoSkinFileInAliData.mkdirs();
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
}
