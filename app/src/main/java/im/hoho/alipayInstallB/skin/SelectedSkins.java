package im.hoho.alipayInstallB.skin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * selected_skins.json 读写。
 *
 * 文件示例：
 * {
 *   "version": 1,
 *   "skins": ["皮肤A", "皮肤B"]
 * }
 */
public final class SelectedSkins {

    public static final int CURRENT_VERSION = 1;

    private SelectedSkins() {
    }

    /**
     * 读取选中列表。文件不存在/损坏/为空时返回空列表。
     * 自动过滤保留名以及空值。
     */
    public static List<String> read(File f) {
        if (f == null || !f.exists() || !f.isFile()) {
            return Collections.emptyList();
        }
        try {
            byte[] bytes = SkinIO.readAllBytes(f);
            JSONObject obj = JSON.parseObject(new String(bytes, "UTF-8"));
            if (obj == null) return Collections.emptyList();
            JSONArray arr = obj.getJSONArray("skins");
            if (arr == null) return Collections.emptyList();
            List<String> out = new ArrayList<>(arr.size());
            LinkedHashSet<String> dedupe = new LinkedHashSet<>();
            for (int i = 0; i < arr.size(); i++) {
                String s;
                try {
                    s = arr.getString(i);
                } catch (Exception e) {
                    continue;
                }
                if (s == null) continue;
                s = s.trim();
                if (s.isEmpty()) continue;
                if (SkinPaths.isReservedName(s)) continue;
                if (dedupe.add(s)) out.add(s);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 原子写：先写 .tmp 再 rename。已对保留名硬过滤兜底。
     */
    public static void write(File f, List<String> skins) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        JSONObject obj = new JSONObject();
        obj.put("version", CURRENT_VERSION);
        JSONArray arr = new JSONArray();
        if (skins != null) {
            LinkedHashSet<String> dedupe = new LinkedHashSet<>();
            for (String s : skins) {
                if (s == null) continue;
                String t = s.trim();
                if (t.isEmpty()) continue;
                if (SkinPaths.isReservedName(t)) continue;
                if (dedupe.add(t)) arr.add(t);
            }
        }
        obj.put("skins", arr);

        File tmp = new File(parent, f.getName() + ".tmp");
        FileOutputStream os = new FileOutputStream(tmp);
        try {
            os.write(obj.toJSONString().getBytes("UTF-8"));
            os.flush();
        } finally {
            try { os.close(); } catch (IOException ignored) {}
        }
        if (f.exists() && !f.delete()) {
            tmp.delete();
            throw new IOException("无法删除旧文件: " + f);
        }
        if (!tmp.renameTo(f)) {
            throw new IOException("rename 失败: " + tmp + " -> " + f);
        }
    }
}
