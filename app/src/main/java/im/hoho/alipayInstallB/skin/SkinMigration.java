package im.hoho.alipayInstallB.skin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 旧版（v1）数据迁移：把 root/ 下符合"含 meta.json 的目录"的项移动到 root/skins/，
 * 并把成功迁移的目录名写入 selected_skins.json。
 *
 * 白名单策略：仅迁移 (a) 是目录、(b) 含 meta.json、(c) 不在保留名列表中。
 *
 * 仅在 migration_v2_done 不存在时执行。
 */
public final class SkinMigration {

    private SkinMigration() {
    }

    public static final class Result {
        public final boolean ran;
        public final List<String> migrated;
        public final List<String> failed;

        Result(boolean ran, List<String> m, List<String> f) {
            this.ran = ran;
            this.migrated = m;
            this.failed = f;
        }
    }

    public static Result runIfNeeded() {
        File done = SkinPaths.migrationDoneFile();
        if (done.exists()) {
            return new Result(false, new ArrayList<String>(), new ArrayList<String>());
        }

        List<String> migrated = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        File root = SkinPaths.root();
        if (!root.exists()) {
            writeDone(done);
            return new Result(true, migrated, failed);
        }
        File skinsDir = SkinPaths.skinsDir();
        if (!skinsDir.exists()) skinsDir.mkdirs();

        File[] children = root.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c == null || !c.isDirectory()) continue;
                String name = c.getName();
                if (SkinPaths.isReservedName(name)) continue;
                if (!new File(c, "meta.json").exists()) continue;

                File target = new File(skinsDir, name);
                if (target.exists()) {
                    // skins/ 已存在同名，旧目录交给手动清理；安全起见跳过
                    continue;
                }
                if (c.renameTo(target)) {
                    migrated.add(name);
                    continue;
                }
                // rename 失败（多见于跨挂载点）：copy + delete
                try {
                    SkinIO.copyDir(c, target);
                    SkinIO.deleteRecursive(c);
                    migrated.add(name);
                } catch (IOException e) {
                    SkinIO.deleteRecursive(target);
                    failed.add(name);
                }
            }
        }

        if (!migrated.isEmpty()) {
            // 硬过滤兜底
            List<String> safe = new ArrayList<>(migrated.size());
            for (String n : migrated) {
                if (!SkinPaths.isReservedName(n)) safe.add(n);
            }
            try {
                SelectedSkins.write(SkinPaths.selectedSkinsJson(), safe);
            } catch (IOException ignored) {
            }
        }
        writeDone(done);
        return new Result(true, migrated, failed);
    }

    private static void writeDone(File f) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream os = new FileOutputStream(f);
            try {
                os.write("{\"version\":2}".getBytes("UTF-8"));
                os.flush();
            } finally {
                try { os.close(); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {
        }
    }
}
