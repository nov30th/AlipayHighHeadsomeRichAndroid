package im.hoho.alipayInstallB.skin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 远程皮肤目录 manifest.json 解析与单主题 zip 下载。
 */
public final class RemoteManifest {

    public static final class Notice {
        public final String title;
        public final String message;
        public final String updatedAt;

        public Notice(String t, String m, String u) {
            title = t;
            message = m;
            updatedAt = u;
        }
    }

    public static final class RemoteSkin {
        public final String name;
        public final String file;
        public final String description;

        public RemoteSkin(String n, String f, String d) {
            name = n;
            file = f;
            description = d;
        }
    }

    public final Notice notice;
    public final List<RemoteSkin> skins;

    private RemoteManifest(Notice n, List<RemoteSkin> s) {
        notice = n;
        skins = s;
    }

    public static RemoteManifest parse(String json) {
        return parse(json, "skins");
    }

    /**
     * 解析远程 manifest。
     * @param arrayKey 主体数组 key：皮肤为 "skins"，主题为 "themes"。
     */
    public static RemoteManifest parse(String json, String arrayKey) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) return new RemoteManifest(null, Collections.<RemoteSkin>emptyList());

        Notice notice = null;
        JSONObject n = root.getJSONObject("notice");
        if (n != null) {
            notice = new Notice(
                    n.getString("title"),
                    n.getString("message"),
                    n.getString("updatedAt"));
        }

        List<RemoteSkin> skins = new ArrayList<>();
        JSONArray arr = root.getJSONArray(arrayKey);
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj;
                try {
                    obj = arr.getJSONObject(i);
                } catch (Exception e) {
                    continue;
                }
                if (obj == null) continue;
                String file = obj.getString("file");
                if (file == null || file.isEmpty()) continue;
                if (file.contains("..") || file.contains("/") || file.contains("\\")) {
                    // 只允许 manifest 同目录下的简单文件名
                    continue;
                }
                skins.add(new RemoteSkin(
                        obj.getString("name"),
                        file,
                        obj.getString("description")));
            }
        }
        return new RemoteManifest(notice, skins);
    }

    // -------------------- 下载 --------------------

    public interface ProgressCallback {
        void onProgress(int percent);
    }

    public static String downloadManifest(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (conn instanceof HttpURLConnection) {
            ((HttpURLConnection) conn).setRequestMethod("GET");
        }
        conn.connect();
        InputStream in = new BufferedInputStream(conn.getInputStream());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * 下载某个主题 zip 到 imports_tmp/，返回保存的 zip 文件。
     * 调用方应再调用 SkinLibrary.importZip(...)。
     *
     * @param manifestUrl manifest.json 的完整 URL，用于解析 base
     * @param fileRel     skins[].file（必须是简单文件名，已由 parse 过滤）
     */
    public static File downloadSkinZip(String manifestUrl, String fileRel,
                                       ProgressCallback cb) throws IOException {
        if (fileRel == null || fileRel.isEmpty()) {
            throw new IOException("空的远程文件名");
        }
        String base = manifestUrl;
        int slash = base.lastIndexOf('/');
        if (slash < 0) throw new IOException("非法 manifest URL");
        String encoded = URLEncoder.encode(fileRel, "UTF-8").replace("+", "%20");
        String zipUrl = base.substring(0, slash + 1) + encoded;

        File tmpDir = SkinPaths.importsTmpDir();
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            throw new IOException("无法创建 imports_tmp");
        }
        File out = new File(tmpDir, "remote_" + System.currentTimeMillis() + ".zip");

        URL url = new URL(zipUrl);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.connect();
        int total = conn.getContentLength();

        InputStream in = new BufferedInputStream(conn.getInputStream());
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[8192];
            long received = 0;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                received += n;
                if (cb != null && total > 0) {
                    int pct = (int) (received * 100 / total);
                    if (pct != lastPct) {
                        lastPct = pct;
                        cb.onProgress(pct);
                    }
                }
            }
            os.flush();
            return out;
        } finally {
            try { in.close(); } catch (IOException ignored) {}
            try { os.close(); } catch (IOException ignored) {}
        }
    }
}
