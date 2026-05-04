package im.hoho.alipayInstallB.skin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 皮肤库 App 侧业务接口：列表、勾选、导入、导出、删除、缓存同步标记。
 *
 * 这里只放纯逻辑，不依赖 Android UI。
 */
public final class SkinLibrary {

    private SkinLibrary() {
    }

    // -------------------- 列表 --------------------

    /**
     * 列出 skins/ 下的有效皮肤，并标记其是否已被选中。
     * 已自动忽略保留名和非目录项。
     * 返回结果按显示名排序。
     */
    public static List<SkinEntry> listSkins() {
        List<SkinEntry> out = new ArrayList<>();
        File skinsDir = SkinPaths.skinsDir();
        if (!skinsDir.exists() || !skinsDir.isDirectory()) return out;

        Set<String> selected = new HashSet<>(
                SelectedSkins.read(SkinPaths.selectedSkinsJson()));

        File[] children = skinsDir.listFiles();
        if (children == null) return out;
        for (File c : children) {
            if (c == null || !c.isDirectory()) continue;
            if (SkinPaths.isReservedName(c.getName())) continue;
            String name = c.getName();
            String display = SkinMeta.displayName(c);
            out.add(new SkinEntry(name, display, selected.contains(name)));
        }
        Collections.sort(out, new Comparator<SkinEntry>() {
            @Override
            public int compare(SkinEntry a, SkinEntry b) {
                return a.displayName.compareTo(b.displayName);
            }
        });
        return out;
    }

    /**
     * 启动时静默清理 selected_skins.json 中已不存在的皮肤项。
     * 返回被清理的数量。
     */
    public static int cleanupMissingSelections() {
        List<String> selected = SelectedSkins.read(SkinPaths.selectedSkinsJson());
        if (selected.isEmpty()) return 0;
        File skinsDir = SkinPaths.skinsDir();
        List<String> kept = new ArrayList<>(selected.size());
        int removed = 0;
        for (String name : selected) {
            File d = new File(skinsDir, name);
            if (d.exists() && d.isDirectory() && !SkinPaths.isReservedName(name)) {
                kept.add(name);
            } else {
                removed++;
            }
        }
        if (removed > 0) {
            try {
                SelectedSkins.write(SkinPaths.selectedSkinsJson(), kept);
            } catch (IOException ignored) {
            }
        }
        return removed;
    }

    // -------------------- 选中 / 缓存同步 --------------------

    /**
     * 写入新的选中列表。会过滤保留名与不存在的皮肤目录。
     */
    public static void saveSelected(List<String> dirNames) throws IOException {
        File skinsDir = SkinPaths.skinsDir();
        List<String> safe = new ArrayList<>();
        if (dirNames != null) {
            for (String n : dirNames) {
                if (n == null) continue;
                if (SkinPaths.isReservedName(n)) continue;
                File d = new File(skinsDir, n);
                if (d.exists() && d.isDirectory()) safe.add(n);
            }
        }
        SelectedSkins.write(SkinPaths.selectedSkinsJson(), safe);
    }

    /**
     * 创建 update 标记。Hook 在下次进入付款码时消费。
     * 调用前应当先 saveSelected。
     */
    public static void requestCacheUpdate() {
        ensureRoot();
        File f = SkinPaths.updateFlag();
        if (!f.exists()) f.mkdirs();
    }

    /**
     * 创建 delete 标记。Hook 在下次进入付款码时清空 HOHO 缓存。
     */
    public static void requestCacheDelete() {
        ensureRoot();
        File f = SkinPaths.deleteFlag();
        if (!f.exists()) f.mkdirs();
    }

    /**
     * 创建 export 标记。Hook 在下次进入付款码时把支付宝内置皮肤复制到 skins/。
     */
    public static void requestBuiltinExport() {
        ensureRoot();
        File f = SkinPaths.exportFlag();
        if (!f.exists()) f.mkdirs();
    }

    public static boolean isActived() {
        return SkinPaths.activedFlag().exists();
    }

    public static void setActived(boolean on) {
        ensureRoot();
        File f = SkinPaths.activedFlag();
        if (on) {
            if (!f.exists()) f.mkdirs();
        } else {
            if (f.exists()) SkinIO.deleteRecursive(f);
        }
    }

    // -------------------- 删除 --------------------

    /**
     * 删除某个皮肤：
     * - 移除 skins/<name>/
     * - 从 selected_skins.json 中移除
     * - 如果 exports/<name>.zip 存在则一并删除
     * 不创建 update 标记，由调用方决定何时点击"更新缓存"。
     */
    public static boolean deleteSkin(String dirName) {
        if (dirName == null || SkinPaths.isReservedName(dirName)) return false;

        File d = new File(SkinPaths.skinsDir(), dirName);
        boolean ok = !d.exists() || SkinIO.deleteRecursive(d);

        // 同步移除选中
        List<String> selected = new ArrayList<>(
                SelectedSkins.read(SkinPaths.selectedSkinsJson()));
        if (selected.remove(dirName)) {
            try {
                SelectedSkins.write(SkinPaths.selectedSkinsJson(), selected);
            } catch (IOException ignored) {
            }
        }

        // 级联删除导出 zip
        File z = new File(SkinPaths.exportsDir(), dirName + ".zip");
        if (z.exists()) z.delete();
        File zSan = new File(SkinPaths.exportsDir(),
                SkinIO.sanitizeFilename(dirName) + ".zip");
        if (zSan.exists()) zSan.delete();

        return ok;
    }

    // -------------------- 导入 --------------------

    public static final class ImportResult {
        public final boolean success;
        public final String dirName;
        public final String message;

        private ImportResult(boolean s, String n, String m) {
            success = s;
            dirName = n;
            message = m;
        }

        public static ImportResult ok(String name) {
            return new ImportResult(true, name, "导入成功");
        }

        public static ImportResult fail(String msg) {
            return new ImportResult(false, null, msg);
        }
    }

    /**
     * 导入 zip：
     *   解压到 imports_tmp/<本次>/ -> 校验 -> 移动到 skins/<name>/，同名直接覆盖。
     */
    public static ImportResult importZip(File zip) {
        if (zip == null || !zip.exists() || !zip.isFile()) {
            return ImportResult.fail("zip 文件不存在");
        }
        ensureRoot();
        File tmpBase = new File(SkinPaths.importsTmpDir(),
                "imp_" + System.currentTimeMillis());

        try {
            SkinIO.safeExtract(zip, tmpBase);
        } catch (IOException e) {
            return ImportResult.fail("解压失败: " + e.getMessage());
        }

        File skinDir = locateSkinDir(tmpBase);
        if (skinDir == null) {
            SkinIO.deleteRecursive(tmpBase);
            return ImportResult.fail("zip 内未找到包含 meta.json 的皮肤目录");
        }
        if (!SkinMeta.isValid(skinDir)) {
            // 保留 tmp 供排查
            return ImportResult.fail("meta.json 校验失败");
        }
        String name = skinDir.getName();
        if (SkinPaths.isReservedName(name)) {
            SkinIO.deleteRecursive(tmpBase);
            return ImportResult.fail("皮肤名为保留名: " + name);
        }

        File skinsDir = SkinPaths.skinsDir();
        if (!skinsDir.exists() && !skinsDir.mkdirs()) {
            return ImportResult.fail("无法创建 skins 目录");
        }
        File target = new File(skinsDir, name);
        if (target.exists()) {
            if (!SkinIO.deleteRecursive(target)) {
                return ImportResult.fail("无法覆盖旧皮肤: " + name);
            }
        }
        if (!skinDir.renameTo(target)) {
            try {
                SkinIO.copyDir(skinDir, target);
                SkinIO.deleteRecursive(skinDir);
            } catch (IOException e) {
                return ImportResult.fail("移动失败: " + e.getMessage());
            }
        }
        SkinIO.deleteRecursive(tmpBase);
        return ImportResult.ok(name);
    }

    /**
     * tmpBase 应为单一皮肤目录的容器：
     *   - 如果 tmpBase 直接含 meta.json -> 视为非法（不兼容扁平 zip）
     *   - 否则取第一个含 meta.json 的子目录
     */
    private static File locateSkinDir(File tmpBase) {
        if (new File(tmpBase, "meta.json").exists()) return null;
        File[] children = tmpBase.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.isDirectory() && new File(c, "meta.json").exists()) {
                return c;
            }
        }
        return null;
    }

    // -------------------- 导出 --------------------

    public static final class ExportResult {
        public final boolean success;
        public final File zipFile;
        public final String message;

        private ExportResult(boolean s, File f, String m) {
            success = s;
            zipFile = f;
            message = m;
        }

        public static ExportResult ok(File zip) {
            return new ExportResult(true, zip, "导出成功");
        }

        public static ExportResult fail(String msg) {
            return new ExportResult(false, null, msg);
        }
    }

    /**
     * 导出皮肤到 exports/<sanitized>.zip。已存在则覆盖。
     */
    public static ExportResult exportZip(String dirName) {
        if (dirName == null || SkinPaths.isReservedName(dirName)) {
            return ExportResult.fail("非法皮肤名");
        }
        File src = new File(SkinPaths.skinsDir(), dirName);
        if (!src.exists() || !src.isDirectory()) {
            return ExportResult.fail("皮肤不存在: " + dirName);
        }
        File dir = SkinPaths.exportsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            return ExportResult.fail("无法创建 exports 目录");
        }
        String safe = SkinIO.sanitizeFilename(dirName);
        if (safe.isEmpty()) safe = "skin";
        File zip = new File(dir, safe + ".zip");
        try {
            SkinIO.zipDirectory(src, zip);
        } catch (IOException e) {
            return ExportResult.fail("打包失败: " + e.getMessage());
        }
        return ExportResult.ok(zip);
    }

    private static void ensureRoot() {
        File r = SkinPaths.root();
        if (!r.exists()) r.mkdirs();
    }
}
