package im.hoho.alipayInstallB.skin;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 文件、目录、zip 工具。Hook 进程与 App 进程共用。
 */
public final class SkinIO {

    private SkinIO() {
    }

    public static boolean deleteRecursive(File file) {
        if (file == null) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        return !file.exists() || file.delete();
    }

    public static void copyDir(File src, File dst) throws IOException {
        if (!src.exists()) return;
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IOException("mkdirs failed: " + dst);
            }
            File[] children = src.listFiles();
            if (children == null) return;
            for (File c : children) {
                copyDir(c, new File(dst, c.getName()));
            }
        } else {
            copyFile(src, dst);
        }
    }

    public static void copyFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * 安全解压：拒绝任何含 .. 或绝对路径的条目，并校验最终路径在 destBase 之内。
     * 解压前清空 destBase。
     */
    public static void safeExtract(File zip, File destBase) throws IOException {
        if (destBase.exists()) deleteRecursive(destBase);
        if (!destBase.mkdirs()) {
            throw new IOException("mkdirs failed: " + destBase);
        }

        ZipFile zf = new ZipFile(zip);
        @SuppressWarnings("unchecked")
        List<FileHeader> headers = (List<FileHeader>) zf.getFileHeaders();
        String basePath = destBase.getCanonicalPath() + File.separator;
        for (FileHeader h : headers) {
            String name = h.getFileName();
            if (name == null || name.isEmpty()) continue;
            String norm = name.replace('\\', '/');
            if (norm.startsWith("/") || norm.contains("..")) {
                throw new IOException("非法 zip 条目: " + name);
            }
            File target = new File(destBase, norm);
            String tp = target.getCanonicalPath();
            if (!tp.startsWith(basePath) && !tp.equals(destBase.getCanonicalPath())) {
                throw new IOException("Zip Slip: " + name);
            }
        }
        zf.extractAll(destBase.getAbsolutePath());
    }

    /**
     * 把 srcDir 整个目录打包到 destZip，zip 内部以 srcDir 自身为顶层文件夹。
     * 已存在则覆盖。
     */
    public static void zipDirectory(File srcDir, File destZip) throws IOException {
        if (!srcDir.exists() || !srcDir.isDirectory()) {
            throw new IOException("源目录不存在: " + srcDir);
        }
        if (destZip.exists() && !destZip.delete()) {
            throw new IOException("无法删除已存在的 zip: " + destZip);
        }
        File parent = destZip.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        ZipFile zf = new ZipFile(destZip);
        zf.addFolder(srcDir);
    }

    public static byte[] readAllBytes(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            long len = f.length();
            if (len > Integer.MAX_VALUE) throw new IOException("file too large");
            byte[] buf = new byte[(int) len];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            if (read < buf.length) {
                byte[] out = new byte[read];
                System.arraycopy(buf, 0, out, 0, read);
                return out;
            }
            return buf;
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    public static String sanitizeFilename(String s) {
        if (s == null) return "_";
        return s.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }
}
