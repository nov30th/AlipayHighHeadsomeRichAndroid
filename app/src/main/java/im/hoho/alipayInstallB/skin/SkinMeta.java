package im.hoho.alipayInstallB.skin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.File;

/**
 * 皮肤 meta.json 读取。仅读取展示所需字段。
 */
public final class SkinMeta {

    private SkinMeta() {
    }

    /**
     * 读取 description；为空或读取失败返回 null。
     */
    public static String readDescription(File skinDir) {
        File f = new File(skinDir, "meta.json");
        if (!f.exists() || !f.isFile()) return null;
        try {
            byte[] bytes = SkinIO.readAllBytes(f);
            JSONObject obj = JSON.parseObject(new String(bytes, "UTF-8"));
            if (obj == null) return null;
            String d = obj.getString("description");
            if (d == null) return null;
            d = d.trim();
            return d.isEmpty() ? null : d;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 显示名：优先 description，其次目录名，最后 "未知皮肤"。
     */
    public static String displayName(File skinDir) {
        String d = readDescription(skinDir);
        if (d != null) return d;
        String n = skinDir.getName();
        if (n != null && !n.isEmpty()) return n;
        return "未知皮肤";
    }

    /**
     * 仅做最低限度校验：meta.json 存在 + 可解析为 JSON 对象。
     * 资源文件路径校验因 meta 中 path 有时不带扩展名，不做严格存在性校验。
     */
    public static boolean isValid(File skinDir) {
        File f = new File(skinDir, "meta.json");
        if (!f.exists() || !f.isFile()) return false;
        try {
            byte[] bytes = SkinIO.readAllBytes(f);
            JSONObject obj = JSON.parseObject(new String(bytes, "UTF-8"));
            return obj != null;
        } catch (Exception e) {
            return false;
        }
    }
}
