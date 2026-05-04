package im.hoho.alipayInstallB;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import im.hoho.alipayInstallB.skin.RemoteManifest;
import im.hoho.alipayInstallB.skin.SkinEntry;
import im.hoho.alipayInstallB.skin.SkinLibrary;
import im.hoho.alipayInstallB.skin.SkinMigration;
import im.hoho.alipayInstallB.skin.SkinPaths;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_PICK_ZIP = 2;

    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_FIRST_RUN = "isFirstRun";

    private final String[] memberGrades = {"原有", "普通 (primary)", "黄金 (golden)", "铂金 (platinum)", "钻石 (diamond)"};

    private RecyclerView rvSkins;
    private TextView tvEmpty;
    private final List<SkinModel> skinList = new ArrayList<>();
    private SkinAdapter skinAdapter;
    
    private MaterialToolbar toolbar;
    private MaterialSwitch btnActivate;
    private ExtendedFloatingActionButton fabAdd;
    private AutoCompleteTextView spinnerMemberGrade;
    
    private ExecutorService executorService;
    private Handler mainHandler;

    private static final class BusyDialog {
        final AlertDialog dialog;
        final ProgressBar progressBar;
        final TextView messageView;

        BusyDialog(AlertDialog d, ProgressBar p, TextView m) {
            dialog = d;
            progressBar = p;
            messageView = m;
        }

        void dismiss() {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        }
    }

    static class SkinModel {
        final String dirName;
        final String displayName;
        boolean isSelected;
        String bgPath;
        String logoPath;
        String maskPath;
        String themeColor;

        SkinModel(SkinEntry e) {
            this.dirName = e.dirName;
            this.displayName = e.displayName;
            this.isSelected = e.selected;
        }

        void parsePreview() {
            File skinDir = new File(SkinPaths.skinsDir(), dirName);
            File metaJsonFile = new File(skinDir, "meta.json");
            if (!metaJsonFile.exists()) return;
            try {
                byte[] bytes = im.hoho.alipayInstallB.skin.SkinIO.readAllBytes(metaJsonFile);
                JSONObject json = JSON.parseObject(new String(bytes, "UTF-8"));
                if (json == null) return;
                this.themeColor = json.getString("themeColor");
                JSONArray resources = json.getJSONArray("resource");
                if (resources == null) return;
                for (int i = 0; i < resources.size(); i++) {
                    JSONObject res = resources.getJSONObject(i);
                    if (res == null) continue;
                    String pos = res.getString("position");
                    if ("z01.0001".equals(pos)) {
                        JSONArray imageList = res.getJSONArray("imageList");
                        if (imageList != null && imageList.size() > 0) {
                            for (int j = 0; j < imageList.size(); j++) {
                                JSONObject item = imageList.getJSONObject(j);
                                String path = item == null ? null : item.getString("path");
                                if (path != null && path.contains("2x1")) {
                                    this.bgPath = resolve(skinDir, path);
                                    break;
                                }
                            }
                            if (this.bgPath == null) {
                                JSONObject first = imageList.getJSONObject(0);
                                if (first != null) this.bgPath = resolve(skinDir, first.getString("path"));
                            }
                        }
                    } else if ("z02.0002".equals(pos)) {
                        this.logoPath = resolve(skinDir, res.getString("image"));
                    } else if ("z02.0003".equals(pos)) {
                        this.maskPath = resolve(skinDir, res.getString("image"));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private static String resolve(File dir, String name) {
            if (name == null || name.isEmpty()) return null;
            File f = new File(dir, name);
            if (f.exists()) return f.getAbsolutePath();
            File[] siblings = dir.listFiles();
            if (siblings == null) return null;
            for (File s : siblings) {
                if (s.isFile() && s.getName().startsWith(name)) return s.getAbsolutePath();
            }
            return null;
        }
    }

    class SkinAdapter extends RecyclerView.Adapter<SkinAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.skin_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final SkinModel skin = skinList.get(position);
            holder.tvSkinName.setText(skin.displayName);
            holder.tvSkinPath.setText("目录: " + skin.dirName);

            holder.cbSelected.setOnCheckedChangeListener(null);
            holder.cbSelected.setChecked(skin.isSelected);
            holder.cbSelected.setOnCheckedChangeListener((buttonView, isChecked) -> {
                skin.isSelected = isChecked;
                persistSelectionAndRequestCacheUpdate("已计划更新缓存，重启支付宝付款码生效");
                holder.tvActiveBadge.setVisibility(skin.isSelected && SkinLibrary.isActived() ? View.VISIBLE : View.GONE);
            });

            int color;
            try {
                color = Color.parseColor(skin.themeColor != null ? skin.themeColor : "#1677FF");
            } catch (Exception e) {
                color = Color.parseColor("#1677FF");
            }
            holder.viewThemeAccent.setBackgroundColor(color);
            holder.tvActiveBadge.setChipBackgroundColorResource(android.R.color.transparent);
            holder.tvActiveBadge.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color));

            if (skin.bgPath != null) {
                Glide.with(MainActivity.this).load(new File(skin.bgPath)).into(holder.ivBackground);
            } else {
                holder.ivBackground.setImageResource(android.R.drawable.screen_background_light);
            }

            if (skin.logoPath != null) {
                Glide.with(MainActivity.this).load(new File(skin.logoPath)).into(holder.ivLogo);
                holder.ivLogo.setVisibility(View.VISIBLE);
            } else {
                holder.ivLogo.setVisibility(View.GONE);
            }

            if (skin.maskPath != null) {
                Glide.with(MainActivity.this).load(new File(skin.maskPath)).into(holder.ivMask);
                holder.ivMask.setVisibility(View.VISIBLE);
            } else {
                holder.ivMask.setVisibility(View.GONE);
            }

            holder.tvActiveBadge.setVisibility(skin.isSelected && SkinLibrary.isActived() ? View.VISIBLE : View.GONE);
            
            holder.btnMore.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(MainActivity.this, holder.btnMore);
                popup.getMenu().add(0, 1, 0, "导出为 Zip");
                popup.getMenu().add(0, 2, 0, "删除皮肤");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) exportSkin(skin);
                    else if (item.getItemId() == 2) confirmDeleteSkin(skin);
                    return true;
                });
                popup.show();
            });
        }

        @Override
        public int getItemCount() {
            return skinList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView cardRoot;
            ImageView ivBackground, ivLogo, ivMask;
            TextView tvSkinName, tvSkinPath;
            Chip tvActiveBadge;
            MaterialSwitch cbSelected;
            ImageButton btnMore;
            View viewThemeAccent;

            ViewHolder(View view) {
                super(view);
                cardRoot = view.findViewById(R.id.cardRoot);
                ivBackground = view.findViewById(R.id.ivBackground);
                ivLogo = view.findViewById(R.id.ivLogo);
                ivMask = view.findViewById(R.id.ivMask);
                tvSkinName = view.findViewById(R.id.tvSkinName);
                tvSkinPath = view.findViewById(R.id.tvSkinPath);
                tvActiveBadge = view.findViewById(R.id.tvActiveBadge);
                cbSelected = view.findViewById(R.id.cbSelected);
                btnMore = view.findViewById(R.id.btnMore);
                viewThemeAccent = view.findViewById(R.id.viewThemeAccent);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if (settings.getBoolean(KEY_FIRST_RUN, true)) {
            showPrivacyDialog(settings);
        }
        
        toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            toolbar.getMenu().setGroupDividerEnabled(true);
        }
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_update_cache) {
                doRequestUpdate();
                return true;
            } else if (id == R.id.action_clear_cache) {
                confirmClearCache();
                return true;
            } else if (id == R.id.action_export_builtin) {
                doRequestBuiltinExport();
                return true;
            } else if (id == R.id.action_github) {
                openProjectHomepage();
                return true;
            }
            return false;
        });

        spinnerMemberGrade = findViewById(R.id.spinnerMemberGrade);
        setupMemberGradeSpinner();
        ((TextView) findViewById(R.id.tvVersion)).setText("Version: " + BuildConfig.VERSION_NAME);

        rvSkins = findViewById(R.id.rvSkins);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvSkins.setLayoutManager(new LinearLayoutManager(this));
        skinAdapter = new SkinAdapter();
        rvSkins.setAdapter(skinAdapter);

        btnActivate = findViewById(R.id.btnActivate);
        fabAdd = findViewById(R.id.fabAdd);

        setupButtons();

        if (!checkStoragePermission()) {
            requestStoragePermission();
        } else {
            initAfterPermission();
        }
    }

    private void initAfterPermission() {
        executorService.execute(() -> {
            SkinMigration.Result mig = SkinMigration.runIfNeeded();
            int cleaned = SkinLibrary.cleanupMissingSelections();
            mainHandler.post(() -> {
                if (mig.ran && (!mig.migrated.isEmpty() || !mig.failed.isEmpty())) {
                    Toast.makeText(this,
                            "迁移完成: 成功 " + mig.migrated.size() + ", 失败 " + mig.failed.size(),
                            Toast.LENGTH_SHORT).show();
                }
                if (cleaned > 0) {
                    Toast.makeText(this, "已清理 " + cleaned + " 个失效选中项", Toast.LENGTH_SHORT).show();
                }
                loadSkins();
                refreshActivateButton();
            });
        });
    }

    private void showPrivacyDialog(SharedPreferences settings) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("隐私说明")
                .setMessage("本应用不会收集、不会上传任何用户信息或使用数据。\n\n应用仅在本地运行，除非您点击\"下载更多\"。")
                .setPositiveButton("我知道了", (dialog, which) -> settings.edit().putBoolean(KEY_FIRST_RUN, false).apply())
                .setCancelable(false).show();
    }

    private void setupButtons() {
        btnActivate.setOnCheckedChangeListener((v, isChecked) -> {
            if (SkinLibrary.isActived() != isChecked) {
                toggleActivated(isChecked);
            }
        });
        
        fabAdd.setOnClickListener(v -> {
            String[] options = {"导入本地 Zip", "从 Github 下载更多"};
            new MaterialAlertDialogBuilder(this)
                .setTitle("添加皮肤")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) selectZipForImport();
                    else openRemoteSkins();
                })
                .show();
        });
    }

    private void doRequestUpdate() {
        persistSelectionAndRequestCacheUpdate("已计划更新缓存，重启支付宝付款码生效");
    }

    private void toggleActivated(boolean target) {
        SkinLibrary.setActived(target);
        refreshActivateButton();
        skinAdapter.notifyDataSetChanged();
        if (target) {
            persistSelectionAndRequestCacheUpdate("已启用皮肤替换，已计划更新缓存");
        } else {
            Toast.makeText(this, "已临时关闭皮肤替换", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearCache() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清空 HOHO 缓存")
                .setMessage("将在下次进入支付宝付款码时清空已下发的皮肤缓存（不影响皮肤库）。继续？")
                .setPositiveButton("确定", (d, w) -> {
                    SkinLibrary.requestCacheDelete();
                    Toast.makeText(this, "已计划清空缓存，重启支付宝付款码生效", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void doRequestBuiltinExport() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("导出支付宝内置皮肤")
                .setMessage("将在下次进入支付宝付款码时，把当前账号已下发的内置皮肤自动写入 skins/ 皮肤库。\n\nzip 不会自动生成；需要在皮肤列表中对单个皮肤点导出，zip 会保存到 exports/ 文件夹。继续？")
                .setPositiveButton("确定", (d, w) -> {
                    SkinLibrary.requestBuiltinExport();
                    Toast.makeText(this, "已计划导出到 skins/，请进入支付宝付款码后回到本页刷新", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void openProjectHomepage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/nov30th/AlipayHighHeadsomeRichAndroid")));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshActivateButton() {
        btnActivate.setOnCheckedChangeListener(null);
        btnActivate.setChecked(SkinLibrary.isActived());
        btnActivate.setOnCheckedChangeListener((v, isChecked) -> {
            if (SkinLibrary.isActived() != isChecked) {
                toggleActivated(isChecked);
            }
        });
    }

    private void loadSkins() {
        executorService.execute(() -> {
            List<SkinEntry> entries = SkinLibrary.listSkins();
            List<SkinModel> built = new ArrayList<>(entries.size());
            for (SkinEntry e : entries) {
                SkinModel m = new SkinModel(e);
                m.parsePreview();
                built.add(m);
            }
            mainHandler.post(() -> {
                skinList.clear();
                skinList.addAll(built);
                skinAdapter.notifyDataSetChanged();
                tvEmpty.setVisibility(skinList.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void persistSelectionAndRequestCacheUpdate(String message) {
        List<String> selected = snapshotSelectedSkins();
        executorService.execute(() -> {
            try {
                SkinLibrary.saveSelected(selected);
                SkinLibrary.requestCacheUpdate();
                mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "更新缓存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private List<String> snapshotSelectedSkins() {
        List<String> selected = new ArrayList<>();
        for (SkinModel skin : skinList) {
            if (skin.isSelected) selected.add(skin.dirName);
        }
        return selected;
    }

    private void exportSkin(SkinModel skin) {
        BusyDialog loading = showBusyDialog("导出皮肤", "正在打包 " + skin.displayName + "...", true);
        executorService.execute(() -> {
            SkinLibrary.ExportResult r = SkinLibrary.exportZip(skin.dirName);
            mainHandler.post(() -> {
                loading.dismiss();
                if (r.success) {
                    Toast.makeText(this, "导出成功: exports/" + r.zipFile.getName(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "导出失败: " + r.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void confirmDeleteSkin(SkinModel skin) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除皮肤")
                .setMessage("确定删除 \"" + skin.displayName + "\" (" + skin.dirName + ") ？\n同时移除选中和已导出的 zip。")
                .setPositiveButton("删除", (dialog, which) -> executorService.execute(() -> {
                    boolean ok = SkinLibrary.deleteSkin(skin.dirName);
                    SkinLibrary.requestCacheUpdate();
                    mainHandler.post(() -> {
                        Toast.makeText(this, ok ? "已删除" : "删除失败", Toast.LENGTH_SHORT).show();
                        loadSkins();
                    });
                }))
                .setNegativeButton("取消", null).show();
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("需要存储权限")
                    .setMessage("管理皮肤库需要\"所有文件访问\"权限，请在系统设置中授权。")
                    .setPositiveButton("去授权", (d, w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    })
                    .setNegativeButton("取消", null).show();
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && checkStoragePermission()) {
            initAfterPermission();
        }
    }

    private void selectZipForImport() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择皮肤 zip"), REQUEST_PICK_ZIP);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ZIP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importFromUri(data.getData());
        }
    }

    private void importFromUri(Uri uri) {
        BusyDialog loading = showBusyDialog("导入皮肤", "正在解压并导入...", true);
        executorService.execute(() -> {
            File tmp = new File(getCacheDir(), "import_" + System.currentTimeMillis() + ".zip");
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tmp)) {
                if (is == null) throw new Exception("无法读取文件");
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading.dismiss();
                    Toast.makeText(this, "读取 zip 失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                tmp.delete();
                return;
            }
            SkinLibrary.ImportResult r = SkinLibrary.importZip(tmp);
            tmp.delete();
            mainHandler.post(() -> {
                loading.dismiss();
                if (r.success) {
                    Toast.makeText(this, "导入成功: " + r.dirName, Toast.LENGTH_SHORT).show();
                    loadSkins();
                } else {
                    Toast.makeText(this, "导入失败: " + r.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void openRemoteSkins() {
        BusyDialog loading = showBusyDialog(
                "正在读取在线皮肤",
                "正在连接 Github 并读取皮肤列表，网络较慢时请稍候。",
                false);
        executorService.execute(() -> {
            try {
                String json = RemoteManifest.downloadManifest(SkinPaths.REMOTE_MANIFEST_URL);
                RemoteManifest manifest = RemoteManifest.parse(json);
                mainHandler.post(() -> {
                    loading.dismiss();
                    showRemoteSkinsDialog(manifest);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading.dismiss();
                    Toast.makeText(this, "连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showRemoteSkinsDialog(RemoteManifest manifest) {
        if (manifest.notice != null && (manifest.notice.message != null || manifest.notice.title != null)) {
            String title = manifest.notice.title != null ? manifest.notice.title : "公告";
            StringBuilder msg = new StringBuilder();
            if (manifest.notice.message != null) msg.append(manifest.notice.message);
            if (manifest.notice.updatedAt != null) {
                if (msg.length() > 0) msg.append("\n\n");
                msg.append("更新时间: ").append(manifest.notice.updatedAt);
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(msg.toString())
                    .setPositiveButton("查看皮肤列表", (d, w) -> showRemoteSkinsList(manifest))
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            showRemoteSkinsList(manifest);
        }
    }

    private void showRemoteSkinsList(RemoteManifest manifest) {
        final List<RemoteManifest.RemoteSkin> items = uniqueRemoteSkins(manifest.skins);
        if (items.isEmpty()) {
            Toast.makeText(this, "远程没有可用皮肤", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayAdapter<RemoteManifest.RemoteSkin> adapter =
                new ArrayAdapter<RemoteManifest.RemoteSkin>(this, R.layout.remote_skin_item, items) {
                    @NonNull
                    @Override
                    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                        View view = convertView;
                        if (view == null) {
                            view = LayoutInflater.from(getContext())
                                    .inflate(R.layout.remote_skin_item, parent, false);
                        }
                        RemoteManifest.RemoteSkin s = getItem(position);
                        TextView name = view.findViewById(R.id.tvRemoteSkinName);
                        TextView desc = view.findViewById(R.id.tvRemoteSkinDescription);
                        TextView file = view.findViewById(R.id.tvRemoteSkinFile);

                        String displayName = s != null && s.name != null && !s.name.trim().isEmpty()
                                ? s.name.trim()
                                : (s != null ? s.file : "");
                        name.setText(displayName);

                        String description = s == null ? null : cleanRemoteDescription(s);
                        if (description == null || description.isEmpty()) {
                            desc.setVisibility(View.GONE);
                        } else {
                            desc.setVisibility(View.VISIBLE);
                            desc.setText(description);
                        }

                        file.setText(s != null ? s.file : "");
                        return view;
                    }
                };
        new MaterialAlertDialogBuilder(this)
                .setTitle("在线皮肤")
                .setAdapter(adapter, (d, w) -> downloadRemoteSkin(items.get(w)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadRemoteSkin(RemoteManifest.RemoteSkin s) {
        String title = s.name != null && !s.name.trim().isEmpty() ? s.name.trim() : s.file;
        BusyDialog loading = showBusyDialog("正在下载皮肤 (Github)", title + "\n准备从 Github 下载...", true);
        executorService.execute(() -> {
            try {
                File zip = RemoteManifest.downloadSkinZip(SkinPaths.REMOTE_MANIFEST_URL, s.file,
                        pct -> mainHandler.post(() -> {
                            loading.progressBar.setProgress(pct);
                            loading.messageView.setText(title + "\n正在从 Github 下载，已完成 " + pct + "%");
                        }));
                SkinLibrary.ImportResult r = SkinLibrary.importZip(zip);
                zip.delete();
                mainHandler.post(() -> {
                    loading.dismiss();
                    if (r.success) {
                        Toast.makeText(this, "下载并导入成功: " + r.dirName, Toast.LENGTH_SHORT).show();
                        loadSkins();
                    } else {
                        Toast.makeText(this, "导入失败: " + r.message, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading.dismiss();
                    Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private BusyDialog showBusyDialog(String title, String message, boolean determinate) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        box.setPadding(padding, dp(8), padding, dp(4));

        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextColor(Color.parseColor("#777777"));
        messageView.setTextSize(14);
        messageView.setLineSpacing(dp(2), 1.0f);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(!determinate);
        bar.setMax(100);
        if (determinate) bar.setProgress(0);

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = dp(18);

        box.addView(messageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(bar, barLp);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(box)
                .setCancelable(false)
                .create();
        dialog.show();
        return new BusyDialog(dialog, bar, messageView);
    }

    private List<RemoteManifest.RemoteSkin> uniqueRemoteSkins(List<RemoteManifest.RemoteSkin> source) {
        List<RemoteManifest.RemoteSkin> out = new ArrayList<>();
        for (RemoteManifest.RemoteSkin skin : source) {
            if (skin == null || skin.file == null) continue;
            boolean exists = false;
            for (RemoteManifest.RemoteSkin added : out) {
                if (skin.file.equalsIgnoreCase(added.file)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) out.add(skin);
        }
        return out;
    }

    private String cleanRemoteDescription(RemoteManifest.RemoteSkin s) {
        if (s.description == null) return null;
        String desc = s.description.trim();
        if (desc.isEmpty()) return null;
        String name = s.name == null ? "" : s.name.trim();
        if (desc.equals(name)) return null;
        return desc;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setupMemberGradeSpinner() {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, memberGrades);
        spinnerMemberGrade.setAdapter(a);
        String g = getCurrentMemberGrade();
        spinnerMemberGrade.setText(g, false);
        spinnerMemberGrade.setOnItemClickListener((parent, view, position, id) -> {
            updateMemberGrade(memberGrades[position]);
        });
    }

    private String getCurrentMemberGrade() {
        File root = SkinPaths.root();
        for (String g : memberGrades) {
            if (g.equals("原有")) continue;
            String key = extractGradeKey(g);
            if (key != null && new File(root, "level_" + key).exists()) return g;
        }
        return "原有";
    }

    private void updateMemberGrade(String g) {
        File root = SkinPaths.root();
        if (!root.exists()) root.mkdirs();
        for (String gr : memberGrades) {
            if (gr.equals("原有")) continue;
            String key = extractGradeKey(gr);
            if (key == null) continue;
            File f = new File(root, "level_" + key);
            if (f.exists()) im.hoho.alipayInstallB.skin.SkinIO.deleteRecursive(f);
        }
        if (!g.equals("原有")) {
            String key = extractGradeKey(g);
            if (key != null) new File(root, "level_" + key).mkdirs();
        }
    }

    private static String extractGradeKey(String label) {
        int l = label.indexOf('(');
        int r = label.indexOf(')');
        if (l < 0 || r < 0 || r <= l) return null;
        return label.substring(l + 1, r).trim();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkStoragePermission()) {
            loadSkins();
            refreshActivateButton();
        }
    }
}
