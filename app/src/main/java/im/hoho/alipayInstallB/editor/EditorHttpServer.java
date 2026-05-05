package im.hoho.alipayInstallB.editor;

import android.content.Context;
import android.content.res.AssetManager;
import android.webkit.MimeTypeMap;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import im.hoho.alipayInstallB.skin.SkinIO;
import im.hoho.alipayInstallB.skin.SkinMeta;
import im.hoho.alipayInstallB.skin.SkinPaths;

public final class EditorHttpServer extends NanoHTTPD {
    public static final int PORT = 8787;

    private final Context context;

    public EditorHttpServer(Context context) {
        super("0.0.0.0", PORT);
        this.context = context.getApplicationContext();
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Method method = session.getMethod();
            String uri = normalizeUri(session.getUri());
            if (uri.startsWith("/api/library")) {
                return serveLibrary(session, method, uri);
            }
            if (uri.startsWith("/api/debug/")) {
                return serveDebug(uri);
            }
            return serveEditorAsset(uri);
        } catch (Exception e) {
            return jsonError(Response.Status.INTERNAL_ERROR, e.getMessage());
        }
    }

    private Response serveDebug(String uri) throws Exception {
        String prefix = "/api/debug/";
        String rest = uri.substring(prefix.length());
        String[] parts = rest.split("/", 2);
        if (parts.length < 2) return jsonError(Response.Status.BAD_REQUEST, "Usage: /api/debug/{kind}/{dirName}");
        String kind = decode(parts[0]);
        String dirName = decode(parts[1]);
        File itemDir = resolveItemDir(kind, dirName);
        File editableRoot = findEditableRoot(itemDir);
        boolean isTheme = "themes".equals(kind);
        File themeMeta = isTheme && editableRoot != null ? new File(editableRoot, "meta.json") : null;
        File skinMeta = editableRoot == null ? null
                : new File(isTheme ? new File(editableRoot, "ltp") : editableRoot, "meta.json");

        StringBuilder sb = new StringBuilder();
        sb.append("kind=").append(kind).append("\n");
        sb.append("dirName=").append(dirName).append("\n");
        sb.append("itemDir=").append(itemDir.getAbsolutePath()).append("\n");
        sb.append("editableRoot=").append(editableRoot == null ? "null" : editableRoot.getAbsolutePath()).append("\n");
        sb.append("\n=== files in editableRoot ===\n");
        if (editableRoot != null) {
            File[] kids = editableRoot.listFiles();
            if (kids != null) for (File f : kids) sb.append(f.getName()).append(f.isDirectory() ? "/" : "").append("\n");
        }
        if (themeMeta != null) {
            sb.append("\n=== theme meta.json (").append(themeMeta.getAbsolutePath()).append(") ===\n");
            sb.append(themeMeta.isFile() ? new String(SkinIO.readAllBytes(themeMeta), StandardCharsets.UTF_8) : "<missing>");
        }
        if (skinMeta != null) {
            sb.append("\n=== skin meta.json (").append(skinMeta.getAbsolutePath()).append(") ===\n");
            sb.append(skinMeta.isFile() ? new String(SkinIO.readAllBytes(skinMeta), StandardCharsets.UTF_8) : "<missing>");
        }
        sb.append("\n\n=== /api/library payload ===\n");
        try {
            JSONObject payload = itemPayload(kind, dirName, itemDir);
            sb.append(JSON.toJSONString(payload, SerializerFeature.DisableCircularReferenceDetect, SerializerFeature.PrettyFormat));
        } catch (Exception e) {
            sb.append("ERROR building payload: ").append(e.getMessage());
        }

        Response response = newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", sb.toString());
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    private Response serveLibrary(IHTTPSession session, Method method, String uri) throws Exception {
        if ("/api/library".equals(uri) && method == Method.GET) {
            JSONObject out = new JSONObject(true);
            out.put("skins", listItems(SkinPaths.skinsDir()));
            out.put("themes", listItems(SkinPaths.themesDir()));
            return json(Response.Status.OK, out);
        }

        String prefix = "/api/library/";
        if (!uri.startsWith(prefix)) return jsonError(Response.Status.NOT_FOUND, "Not found");
        String rest = uri.substring(prefix.length());
        String[] parts = rest.split("/", 4);
        if (parts.length < 2) return jsonError(Response.Status.NOT_FOUND, "Not found");

        String kind = decode(parts[0]);
        String dirName = decode(parts[1]);
        File itemDir = resolveItemDir(kind, dirName);

        if (parts.length == 2) {
            if (method == Method.GET) return json(Response.Status.OK, itemPayload(kind, dirName, itemDir));
            if (method == Method.PUT) {
                saveMetadata(session, kind, itemDir);
                return json(Response.Status.OK, ok());
            }
        }

        if (parts.length == 4 && "assets".equals(parts[2])) {
            String[] assetParts = parts[3].split("/", 2);
            if (assetParts.length != 2) return jsonError(Response.Status.BAD_REQUEST, "Missing asset area or path");
            String area = decode(assetParts[0]);
            String assetPath = decode(assetParts[1]);
            File areaRoot = resolveAreaRoot(kind, itemDir, area);
            File asset = safeChild(areaRoot, assetPath);
            if (method == Method.GET) return fileResponse(asset);
            if (method == Method.POST) {
                saveAssetUpload(session, areaRoot);
                return json(Response.Status.OK, ok());
            }
        }

        return jsonError(Response.Status.METHOD_NOT_ALLOWED, "Unsupported request");
    }

    private JSONArray listItems(File root) {
        JSONArray arr = new JSONArray();
        File[] children = root.exists() && root.isDirectory() ? root.listFiles() : null;
        if (children == null) return arr;
        List<File> dirs = new ArrayList<>();
        for (File child : children) {
            if (child == null || !child.isDirectory()) continue;
            if (SkinPaths.isReservedName(child.getName())) continue;
            if (findEditableRoot(child) == null) continue;
            dirs.add(child);
        }
        Collections.sort(dirs, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return SkinMeta.displayName(a).compareTo(SkinMeta.displayName(b));
            }
        });
        for (File dir : dirs) {
            JSONObject item = new JSONObject(true);
            item.put("dirName", dir.getName());
            item.put("displayName", SkinMeta.displayName(dir));
            arr.add(item);
        }
        return arr;
    }

    private JSONObject itemPayload(String kind, String dirName, File itemDir) throws IOException {
        boolean isTheme = "themes".equals(kind);
        File editableRoot = findEditableRoot(itemDir);
        if (editableRoot == null) throw new IOException("meta.json not found");
        File themeRoot = isTheme ? editableRoot : null;
        File themeMetaFile = isTheme ? new File(themeRoot, "meta.json") : null;
        File skinRoot = isTheme ? new File(themeRoot, "ltp") : editableRoot;
        File skinMetaFile = new File(skinRoot, "meta.json");
        boolean hasSkin = skinMetaFile.isFile();

        JSONObject out = new JSONObject(true);
        out.put("kind", kind);
        out.put("dirName", dirName);
        out.put("assetsBase", "/api/library/" + encode(kind) + "/" + encode(dirName) + "/assets");
        out.put("theme", resourcePayload(isTheme, isTheme ? readJson(themeMetaFile) : new JSONObject(), isTheme ? themeRoot : null));
        out.put("skin", resourcePayload(hasSkin, hasSkin ? readJson(skinMetaFile) : new JSONObject(), hasSkin ? skinRoot : null));
        return out;
    }

    private JSONObject resourcePayload(boolean available, JSONObject meta, File resourceRoot) {
        JSONObject out = new JSONObject(true);
        out.put("available", available);
        out.put("meta", available ? meta : new JSONObject());
        JSONArray resources = available && meta.getJSONArray("resource") != null
                ? meta.getJSONArray("resource") : new JSONArray();
        if (available && resources.isEmpty() && resourceRoot != null) {
            resources = synthesizeImageResources(resourceRoot);
        }
        out.put("resources", resources);
        return out;
    }

    private JSONArray synthesizeImageResources(File root) {
        JSONArray arr = new JSONArray();
        String[] names = {
                "home_navi_bg", "me_navi_bg", "tab_bar_bg_200",
                "tab_bar_home_icon_normal", "tab_bar_home_icon_selected",
                "tab_bar_wealth_icon_normal", "tab_bar_wealth_icon_selected",
                "tab_bar_life_icon_normal", "tab_bar_life_icon_selected",
                "tab_bar_msg_icon_normal", "tab_bar_msg_icon_selected",
                "tab_bar_mime_icon_normal", "tab_bar_mime_icon_selected",
                "home_scan_icon", "home_pay_collect_icon", "home_transport_icon",
                "home_pocket_icon", "home_pay_icon", "home_collect_icon",
                "background_2x1", "background_16x9", "background_4x3", "logo", "mask"
        };
        for (String name : names) {
            if (!new File(root, name).isFile()) continue;
            JSONObject item = new JSONObject(true);
            item.put("type", "image");
            item.put("position", name);
            item.put("description", name);
            item.put("image", name);
            arr.add(item);
        }
        return arr;
    }

    private void saveMetadata(IHTTPSession session, String kind, File itemDir) throws Exception {
        JSONObject body = parseBodyJson(session);
        File editableRoot = findEditableRoot(itemDir);
        if (editableRoot == null) throw new IOException("meta.json not found");
        if ("themes".equals(kind) && body.containsKey("theme")) {
            saveResourceMeta(new File(editableRoot, "meta.json"), body.getJSONObject("theme"));
        }
        if (body.containsKey("skin")) {
            File skinRoot = "themes".equals(kind) ? new File(editableRoot, "ltp") : editableRoot;
            File skinMeta = new File(skinRoot, "meta.json");
            if (skinMeta.isFile()) saveResourceMeta(skinMeta, body.getJSONObject("skin"));
        }
    }

    private void saveResourceMeta(File metaFile, JSONObject update) throws IOException {
        JSONObject existing = metaFile.isFile() ? readJson(metaFile) : new JSONObject(true);
        if (update != null && update.getJSONArray("resource") != null) {
            existing.put("resource", update.getJSONArray("resource"));
        }
        writeUtf8(metaFile, existing.toJSONString());
    }

    private void saveAssetUpload(IHTTPSession session, File areaRoot) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String name = session.getParms().get("name");
        String temp = files.get("file");
        if (name == null || name.trim().isEmpty() || temp == null) {
            throw new IOException("Missing uploaded file or asset name");
        }
        File target = safeChild(areaRoot, name);
        SkinIO.copyFile(new File(temp), target);
    }

    private JSONObject parseBodyJson(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String postData = files.get("postData");
        if (postData == null) postData = "";
        return JSONObject.parseObject(postData);
    }

    private File resolveItemDir(String kind, String dirName) throws IOException {
        if (!"skins".equals(kind) && !"themes".equals(kind)) throw new IOException("Unknown library kind");
        if (SkinPaths.isReservedName(dirName)) throw new IOException("Invalid item name");
        File root = "skins".equals(kind) ? SkinPaths.skinsDir() : SkinPaths.themesDir();
        File dir = safeChild(root, dirName);
        if (!dir.isDirectory()) throw new IOException("Item not found");
        return dir;
    }

    private File resolveAreaRoot(String kind, File itemDir, String area) throws IOException {
        File editableRoot = findEditableRoot(itemDir);
        if (editableRoot == null) throw new IOException("meta.json not found");
        if ("skins".equals(kind)) {
            if (!"skin".equals(area)) throw new IOException("Skin packages do not contain theme assets");
            return editableRoot;
        }
        if ("theme".equals(area)) return editableRoot;
        if ("skin".equals(area)) return new File(editableRoot, "ltp");
        throw new IOException("Unknown asset area");
    }

    private File findEditableRoot(File root) {
        if (root == null || !root.isDirectory()) return null;
        if (new File(root, "meta.json").isFile()) return root;
        File[] children = root.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child != null && child.isDirectory() && new File(child, "meta.json").isFile()) {
                return child;
            }
        }
        return null;
    }

    private File safeChild(File root, String relativePath) throws IOException {
        String clean = relativePath == null ? "" : relativePath.replace('\\', '/');
        if (clean.startsWith("/") || clean.contains("..")) throw new IOException("Invalid path");
        File base = root.getCanonicalFile();
        File child = new File(base, clean).getCanonicalFile();
        String basePath = base.getAbsolutePath();
        String childPath = child.getAbsolutePath();
        if (!childPath.equals(basePath) && !childPath.startsWith(basePath + File.separator)) {
            throw new IOException("Path is outside library root");
        }
        return child;
    }

    private Response serveEditorAsset(String uri) throws IOException {
        String path = "/".equals(uri) ? "index.html" : uri.substring(1);
        File override = safeEditorOverride(path);
        if (override != null && override.isFile()) {
            return streamResponse(new FileInputStream(override), mime(path));
        }

        String assetPath = "editor/" + path;
        AssetManager assets = context.getAssets();
        try {
            return streamResponse(assets.open(assetPath), mime(path));
        } catch (IOException e) {
            if (!"index.html".equals(path)) {
                return streamResponse(assets.open("editor/index.html"), "text/html");
            }
            throw e;
        }
    }

    private File safeEditorOverride(String path) throws IOException {
        File root = new File(context.getFilesDir(), "editor_web");
        if (!root.isDirectory()) return null;
        return safeChild(root, path);
    }

    private Response fileResponse(File file) throws IOException {
        if (!file.isFile()) return jsonError(Response.Status.NOT_FOUND, "Asset not found");
        return streamResponse(new FileInputStream(file), mime(file.getName()));
    }

    private Response streamResponse(InputStream input, String mime) {
        Response response = newChunkedResponse(Response.Status.OK, mime, input);
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    private Response json(Response.Status status, JSONObject body) {
        String text = JSON.toJSONString(body, SerializerFeature.DisableCircularReferenceDetect);
        Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", text);
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    private Response jsonError(Response.Status status, String message) {
        JSONObject body = new JSONObject(true);
        body.put("error", message == null ? "Unknown error" : message);
        return json(status, body);
    }

    private JSONObject ok() {
        JSONObject out = new JSONObject(true);
        out.put("ok", true);
        return out;
    }

    private JSONObject readJson(File file) throws IOException {
        return JSONObject.parseObject(new String(SkinIO.readAllBytes(file), StandardCharsets.UTF_8));
    }

    private void writeUtf8(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        java.io.FileOutputStream out = new java.io.FileOutputStream(file);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        } finally {
            out.close();
        }
    }

    private String mime(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        String ext = MimeTypeMap.getFileExtensionFromUrl(path);
        String type = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return type == null ? "application/octet-stream" : type;
    }

    private String normalizeUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) return "/";
        return uri.split("\\?", 2)[0];
    }

    private String decode(String value) throws IOException {
        return URLDecoder.decode(value, "UTF-8");
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}
