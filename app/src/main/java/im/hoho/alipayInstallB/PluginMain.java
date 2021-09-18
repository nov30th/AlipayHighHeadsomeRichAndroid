package im.hoho.alipayInstallB;

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
            XposedBridge.log("Powered by HOHO`` 20210917");

            XposedHelpers.findAndHookMethod("com.alipay.mobilegw.biz.shared.processer.login.UserLoginResult", lpparam.classLoader, "getExtResAttrs", new Object[]{new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param1MethodHookParam) throws Throwable {
                    String str1;
                    XposedBridge.log("Now, let's install B...");
                    String diamond = "diamond";
                    Map<String, String> map = (Map) param1MethodHookParam.getResult();
                    if (map.containsKey("memberGrade")) {
                        XposedBridge.log("Original member grade: " + (String) map.get("memberGrade"));
                        XposedBridge.log("Putting " + diamond + " into dict...");
                        map.put("memberGrade", diamond);
                        XposedBridge.log("Member grade changed to: " + (String) map.get("memberGrade"));
                        str1 = "diamond";
                    } else {
                        XposedBridge.log("Can not get the member grade in return value...WTF?");
                    }
                }
            }
            });
        }

    }
}
