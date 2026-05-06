package im.hoho.alipayInstallB;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.os.PowerManager;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
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
import androidx.core.app.NotificationManagerCompat;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import im.hoho.alipayInstallB.editor.EditorAddress;
import im.hoho.alipayInstallB.editor.EditorService;
import im.hoho.alipayInstallB.skin.RemoteManifest;
import im.hoho.alipayInstallB.skin.SkinEntry;
import im.hoho.alipayInstallB.skin.SkinLibrary;
import im.hoho.alipayInstallB.skin.SkinMigration;
import im.hoho.alipayInstallB.skin.SkinIO;
import im.hoho.alipayInstallB.skin.SkinPaths;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1100;
    private static final int REQUEST_PICK_ZIP = 2;

    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_FIRST_RUN = "isFirstRun";

    private final String[] memberGrades = {"原有", "普通 (primary)", "黄金 (golden)", "铂金 (platinum)", "钻石 (diamond)"};

    private RecyclerView rvSkins;
    private TextView tvEmpty;
    private final List<SkinModel> skinList = new ArrayList<>();
    private SkinAdapter skinAdapter;
    
    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private MaterialSwitch btnActivate;
    private ExtendedFloatingActionButton fabAdd;
    private AutoCompleteTextView spinnerMemberGrade;
    private View skinPanel;
    private View themePanel;
    private View editorPanel;
    private MaterialButton btnExportThemes;
    private MaterialButton btnEditorStartStop;
    private MaterialButton btnEditorOpen;
    private MaterialButton btnEditorCopyLan;
    private MaterialButton btnEditorBatterySettings;
    private MaterialButton btnEditorNotificationSettings;
    private Chip chipThemeExportStatus;
    private Chip chipEditorStatus;
    private TextView tvThemeExportPath;
    private TextView tvEditorLocalUrl;
    private TextView tvEditorLanUrl;
    private TextView tvEditorBatteryStatus;
    private TextView tvEditorNotificationStatus;
    private LinearLayout themeListContainer;
    private final List<ThemeModel> themeList = new ArrayList<>();
    private int currentTab = 0;
    
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean pendingStartWebEditorAfterNotificationPermission;

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

    static class ThemeModel {
        final String dirName;
        String displayName;
        String bgPath;
        boolean isPending;
        boolean hasLtpSkin;

        ThemeModel(File dir, String selectedTheme, boolean updatePending) {
            this.dirName = dir.getName();
            this.displayName = dir.getName();
            this.isPending = updatePending && dirName.equals(selectedTheme);
            this.hasLtpSkin = new File(new File(dir, "ltp"), "meta.json").isFile();
            parseMeta(dir);
        }

        private void parseMeta(File themeDir) {
            File metaJsonFile = new File(themeDir, "meta.json");
            if (!metaJsonFile.exists()) {
                pickFallbackImage(themeDir);
                return;
            }
            try {
                byte[] bytes = im.hoho.alipayInstallB.skin.SkinIO.readAllBytes(metaJsonFile);
                JSONObject json = JSON.parseObject(new String(bytes, "UTF-8"));
                if (json == null) {
                    pickFallbackImage(themeDir);
                    return;
                }
                String desc = json.getString("description");
                if (desc != null && !desc.trim().isEmpty()) {
                    displayName = desc.trim();
                }
                JSONArray resources = json.getJSONArray("resource");
                if (resources != null) {
                    String firstImage = null;
                    for (int i = 0; i < resources.size(); i++) {
                        JSONObject res = resources.getJSONObject(i);
                        if (res == null) continue;
                        String image = res.getString("image");
                        if (image == null || image.isEmpty()) continue;
                        String resolved = resolve(themeDir, image);
                        if (resolved == null) continue;
                        if (firstImage == null) firstImage = resolved;
                        String pos = res.getString("position");
                        if ("home_navi_bg".equals(pos) || "me_navi_bg".equals(pos)) {
                            bgPath = resolved;
                            break;
                        }
                    }
                    if (bgPath == null) bgPath = firstImage;
                }
                if (bgPath == null) pickFallbackImage(themeDir);
            } catch (Exception ignored) {
                pickFallbackImage(themeDir);
            }
        }

        private void pickFallbackImage(File themeDir) {
            String[] names = {"home_navi_bg", "me_navi_bg", "tab_bar_bg_200"};
            for (String name : names) {
                String resolved = resolve(themeDir, name);
                if (resolved != null) {
                    bgPath = resolved;
                    return;
                }
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

        cleanupTempFiles();
        if (savedInstanceState == null) {
            EditorService.stop(this);
        }

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

        tabLayout = findViewById(R.id.tabLayout);
        setupTabs();

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
        skinPanel = findViewById(R.id.skinPanel);
        themePanel = findViewById(R.id.themePanel);
        editorPanel = findViewById(R.id.editorPanel);
        btnExportThemes = findViewById(R.id.btnExportThemes);
        btnEditorStartStop = findViewById(R.id.btnEditorStartStop);
        btnEditorOpen = findViewById(R.id.btnEditorOpen);
        btnEditorCopyLan = findViewById(R.id.btnEditorCopyLan);
        btnEditorBatterySettings = findViewById(R.id.btnEditorBatterySettings);
        btnEditorNotificationSettings = findViewById(R.id.btnEditorNotificationSettings);
        chipThemeExportStatus = findViewById(R.id.chipThemeExportStatus);
        chipEditorStatus = findViewById(R.id.chipEditorStatus);
        tvThemeExportPath = findViewById(R.id.tvThemeExportPath);
        tvEditorLocalUrl = findViewById(R.id.tvEditorLocalUrl);
        tvEditorLanUrl = findViewById(R.id.tvEditorLanUrl);
        tvEditorBatteryStatus = findViewById(R.id.tvEditorBatteryStatus);
        tvEditorNotificationStatus = findViewById(R.id.tvEditorNotificationStatus);
        themeListContainer = findViewById(R.id.themeListContainer);

        setupButtons();
        setupThemePanel();
        setupEditorPanel();
        showTab(0);

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
                loadThemes();
                refreshActivateButton();
            });
        });
    }

    private void showPrivacyDialog(SharedPreferences settings) {
        CharSequence message = HtmlCompat.fromHtml(
                "<b>欢迎使用支付宝装X模块</b><br/><br/>" +
                "本应用 <b>默认完全不联网</b>：<br/>" +
                "&nbsp;&nbsp;• <b>不联网</b>，不发起任何后台网络请求<br/>" +
                "&nbsp;&nbsp;• <b>不收集</b>任何使用数据<br/>" +
                "&nbsp;&nbsp;• <b>不上传</b>任何用户信息<br/>" +
                "&nbsp;&nbsp;• 所有皮肤与配置仅保存在本设备<br/><br/>" +
                "唯一的例外：当您主动点击 <b>添加皮肤 → 从 Github 下载更多</b> 时，应用才会连接 GitHub 获取在线皮肤清单。<br/><br/>" +
                "<small>项目主页：<a href=\"https://github.com/nov30th/AlipayHighHeadsomeRichAndroid\">nov30th/AlipayHighHeadsomeRichAndroid</a></small>",
                HtmlCompat.FROM_HTML_MODE_LEGACY);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("🔒 隐私说明")
                .setMessage(message)
                .setPositiveButton("我已了解", (d, w) -> settings.edit().putBoolean(KEY_FIRST_RUN, false).apply())
                .setCancelable(false)
                .show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            messageView.setLineSpacing(dp(2), 1.1f);
        }
    }

    private void setupButtons() {
        btnActivate.setOnCheckedChangeListener((v, isChecked) -> {
            if (SkinLibrary.isActived() != isChecked) {
                toggleActivated(isChecked);
            }
        });
        
        fabAdd.setOnClickListener(v -> {
            if (currentTab == 1) {
                openRemoteThemes();
                return;
            }
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

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("付款码皮肤"));
        tabLayout.addTab(tabLayout.newTab().setText("主题"));
        tabLayout.addTab(tabLayout.newTab().setText("皮肤修改器"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void showTab(int tab) {
        currentTab = tab;
        boolean theme = tab == 1;
        boolean editor = tab == 2;
        skinPanel.setVisibility(!theme && !editor ? View.VISIBLE : View.GONE);
        themePanel.setVisibility(theme ? View.VISIBLE : View.GONE);
        editorPanel.setVisibility(editor ? View.VISIBLE : View.GONE);
        fabAdd.setVisibility(editor ? View.GONE : View.VISIBLE);
        fabAdd.setText(theme ? "在线主题" : "添加皮肤");
        if (editor) {
            tvEmpty.setVisibility(View.GONE);
            refreshEditorPanel();
        } else if (theme) {
            tvEmpty.setVisibility(View.GONE);
            refreshThemeExportStatus();
        } else {
            tvEmpty.setVisibility(skinList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setupThemePanel() {
        tvThemeExportPath.setText("导出目录: " + SkinPaths.themesDir().getAbsolutePath());
        btnExportThemes.setOnClickListener(v -> confirmExportThemes());
        refreshThemeExportStatus();
    }

    private void setupEditorPanel() {
        btnEditorStartStop.setOnClickListener(v -> {
            if (EditorService.isRunning()) {
                stopWebEditor();
            } else {
                openWebEditor();
            }
        });
        btnEditorOpen.setOnClickListener(v -> openUrl(editorLocalUrl()));
        btnEditorCopyLan.setOnClickListener(v -> copyEditorLanUrl());
        btnEditorBatterySettings.setOnClickListener(v -> openBatteryOptimizationSettings());
        btnEditorNotificationSettings.setOnClickListener(v -> openNotificationSettings());
        refreshEditorPanel();
    }

    private void refreshEditorPanel() {
        if (chipEditorStatus == null) return;
        boolean running = EditorService.isRunning();
        boolean batteryRestricted = isBatteryRestricted();
        boolean notificationsEnabled = areEditorNotificationsEnabled();
        String localUrl = editorLocalUrl();
        String lanUrl = editorLanUrl();
        chipEditorStatus.setText(running ? "运行中" : "已停止");
        tvEditorLocalUrl.setText("手机浏览器: " + localUrl);
        tvEditorLanUrl.setText("局域网电脑: " + lanUrl);
        tvEditorBatteryStatus.setText(batteryRestricted
                ? "电池限制: 建议在系统设置中把本应用设为无限制，否则切换应用后可能关闭网页服务器。"
                : "电池限制: 已是无限制。");
        btnEditorBatterySettings.setVisibility(batteryRestricted ? View.VISIBLE : View.GONE);
        tvEditorNotificationStatus.setText(notificationsEnabled
                ? "通知权限: 已允许。"
                : "通知权限: 未允许，前台服务通知可能无法显示。");
        btnEditorNotificationSettings.setVisibility(notificationsEnabled ? View.GONE : View.VISIBLE);
        btnEditorStartStop.setText(running ? "停止" : "启动");
        btnEditorStartStop.setIconResource(running
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
        btnEditorOpen.setEnabled(running);
        btnEditorCopyLan.setEnabled(running);
    }

    private void confirmExportThemes() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("导出支付宝主题")
                .setMessage("将创建导出请求。请随后打开支付宝付款码，模块会在支付宝进程中读取当前账号主题并导出到 themes/ 文件夹。")
                .setPositiveButton("创建请求", (d, w) -> {
                    SkinLibrary.requestThemeExport();
                    refreshThemeExportStatus();
                    Toast.makeText(this, "已创建主题导出请求，请打开支付宝付款码", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshThemeExportStatus() {
        if (chipThemeExportStatus == null) return;
        if (SkinPaths.themeExportFlag().exists()) {
            chipThemeExportStatus.setText("已创建请求，等待打开支付宝付款码");
        } else {
            File dir = SkinPaths.themesDir();
            File[] themes = dir.exists() && dir.isDirectory() ? dir.listFiles() : null;
            int count = 0;
            if (themes != null) {
                for (File theme : themes) {
                    if (theme != null && theme.isDirectory()) count++;
                }
            }
            chipThemeExportStatus.setText(count > 0 ? "已导出 " + count + " 个主题" : "未创建导出请求");
        }
    }

    private void loadThemes() {
        executorService.execute(() -> {
            File dir = SkinPaths.themesDir();
            String selected = SkinLibrary.readSelectedTheme();
            boolean updatePending = SkinLibrary.isThemeUpdatePending();
            List<ThemeModel> built = new ArrayList<>();
            File[] themes = dir.exists() && dir.isDirectory() ? dir.listFiles() : null;
            if (themes != null) {
                for (File theme : themes) {
                    if (theme == null || !theme.isDirectory()) continue;
                    if (SkinPaths.isReservedName(theme.getName())) continue;
                    built.add(new ThemeModel(theme, selected, updatePending));
                }
            }
            Collections.sort(built, (a, b) -> a.displayName.compareTo(b.displayName));
            mainHandler.post(() -> {
                themeList.clear();
                themeList.addAll(built);
                renderThemeCards();
                refreshThemeExportStatus();
            });
        });
    }

    private void renderThemeCards() {
        themeListContainer.removeAllViews();
        if (themeList.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无已导出的主题\n\n点击上方导出所有主题，然后打开支付宝付款码");
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setTextColor(Color.parseColor("#777777"));
            empty.setTextSize(15);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            themeListContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (ThemeModel theme : themeList) {
            themeListContainer.addView(createThemeCard(theme));
        }
    }

    private View createThemeCard(ThemeModel theme) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.parseColor(theme.isPending ? "#1677FF" : "#E0E0E0"));
        card.setUseCompatPadding(true);
        card.setClickable(true);
        card.setOnClickListener(v -> selectTheme(theme));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        android.widget.FrameLayout preview = new android.widget.FrameLayout(this);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setBackgroundColor(Color.parseColor("#E8EEF7"));
        if (theme.bgPath != null) {
            Glide.with(this).load(new File(theme.bgPath)).into(bg);
        }
        preview.addView(bg, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackgroundColor(Color.parseColor("#66000000"));
        android.widget.FrameLayout.LayoutParams shadeLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64), android.view.Gravity.BOTTOM);
        preview.addView(shade, shadeLp);

        TextView previewName = new TextView(this);
        previewName.setText(theme.displayName);
        previewName.setTextColor(Color.WHITE);
        previewName.setTextSize(18);
        previewName.setSingleLine(true);
        previewName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        previewName.setPadding(dp(16), 0, dp(16), dp(14));
        android.widget.FrameLayout.LayoutParams previewNameLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM);
        preview.addView(previewName, previewNameLp);

        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(136)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        Chip status = new Chip(this);
        status.setCheckable(false);
        status.setText(theme.isPending ? "准备替换" : "替换");
        status.setClickable(true);
        status.setOnClickListener(v -> selectTheme(theme));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.rightMargin = dp(6);
        row.addView(status, actionLp);

        ImageButton more = new ImageButton(this);
        more.setImageResource(android.R.drawable.ic_menu_more);
        more.setBackgroundColor(Color.TRANSPARENT);
        more.setContentDescription("更多操作");
        more.setOnClickListener(v -> showThemeMoreMenu(more, theme));
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        moreLp.rightMargin = dp(12);
        row.addView(more, moreLp);

        TextView name = new TextView(this);
        name.setText(theme.displayName);
        name.setTextColor(Color.parseColor("#222222"));
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        box.addView(row);
        card.addView(box);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private void showThemeMoreMenu(View anchor, ThemeModel theme) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "导出为 Zip");
        if (theme.hasLtpSkin) {
            popup.getMenu().add(0, 2, 1, "提取付款码皮肤");
        }
        popup.getMenu().add(0, 3, 2, "删除主题");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                exportTheme(theme);
                return true;
            } else if (id == 2) {
                extractThemeLtpSkin(theme);
                return true;
            } else if (id == 3) {
                confirmDeleteTheme(theme);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void selectTheme(ThemeModel theme) {
        executorService.execute(() -> {
            try {
                SkinLibrary.saveSelectedTheme(theme.dirName);
                SkinLibrary.requestThemeUpdate();
                mainHandler.post(() -> {
                    Toast.makeText(this, "已准备替换主题，请打开支付宝付款码触发生效", Toast.LENGTH_LONG).show();
                    loadThemes();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this,
                        "准备替换失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void extractThemeLtpSkin(ThemeModel theme) {
        executorService.execute(() -> {
            SkinLibrary.ImportResult r = SkinLibrary.extractThemeLtpSkin(theme.dirName);
            mainHandler.post(() -> {
                if (r.success) {
                    Toast.makeText(this, "已提取付款码皮肤: " + r.dirName, Toast.LENGTH_LONG).show();
                    loadSkins();
                } else {
                    Toast.makeText(this, "提取失败: " + r.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void exportTheme(ThemeModel theme) {
        BusyDialog loading = showBusyDialog("导出主题", "正在打包 " + theme.displayName + "...", true);
        executorService.execute(() -> {
            SkinLibrary.ExportResult r = SkinLibrary.exportThemeZip(theme.dirName);
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

    private void confirmDeleteTheme(ThemeModel theme) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除主题")
                .setMessage("确定删除 \"" + theme.displayName + "\" (" + theme.dirName + ") ？\n如果该主题正在准备替换，也会取消本次替换请求。")
                .setPositiveButton("删除", (dialog, which) -> executorService.execute(() -> {
                    boolean ok = SkinLibrary.deleteTheme(theme.dirName);
                    mainHandler.post(() -> {
                        Toast.makeText(this, ok ? "已删除主题" : "删除主题失败", Toast.LENGTH_SHORT).show();
                        loadThemes();
                    });
                }))
                .setNegativeButton("取消", null)
                .show();
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
                    Toast.makeText(this, "已计划清空缓存，重新进入支付宝付款码生效", Toast.LENGTH_SHORT).show();
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

    private void openWebEditor() {
        if (!checkStoragePermission()) {
            requestStoragePermission();
            return;
        }
        boolean needsNotificationPermission = needsNotificationPermission();
        boolean batteryRestricted = isBatteryRestricted();
        if (needsNotificationPermission || batteryRestricted) {
            showWebEditorReadinessDialog(needsNotificationPermission, batteryRestricted);
            return;
        }
        startWebEditor();
    }

    private void showWebEditorReadinessDialog(boolean needsNotificationPermission, boolean batteryRestricted) {
        String batteryStatus = batteryRestricted
                ? "当前系统仍在限制本应用后台运行。请把本应用的电池限制设置为 <b>无限制</b>。"
                : "当前电池限制状态：<b>已是无限制</b>。";
        CharSequence message = HtmlCompat.fromHtml(
                "<b>皮肤修改器需要在后台保持网页服务器运行</b><br/><br/>" +
                "请允许通知权限，这样应用可以通过前台服务显示运行状态，并尽量避免网页服务器被系统关闭。<br/><br/>" +
                batteryStatus + "<br/><br/>" +
                "如果没有允许通知，或没有把电池限制设置为无限制，切换到其他应用后 Android 系统可能会关闭网页服务器。",
                HtmlCompat.FROM_HTML_MODE_LEGACY);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("皮肤修改器后台运行提醒")
                .setMessage(message)
                .setPositiveButton(needsNotificationPermission ? "继续授权" : "继续启动", (d, w) -> {
                    if (needsNotificationPermission) {
                        pendingStartWebEditorAfterNotificationPermission = true;
                        requestNotificationPermissionIfNeeded();
                    } else {
                        startWebEditor();
                    }
                })
                .setNegativeButton("取消", null);
        if (batteryRestricted) {
            builder.setNeutralButton("电池设置", (d, w) -> openBatteryOptimizationSettings());
        }

        AlertDialog dialog = builder.show();
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setLineSpacing(dp(2), 1.1f);
        }
    }

    private boolean needsNotificationPermission() {
        return !areEditorNotificationsEnabled();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private boolean areEditorNotificationsEnabled() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isBatteryRestricted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开电池设置: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationSettings() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开通知设置: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startWebEditor() {
        try {
            EditorService.start(this);
            refreshEditorPanel();
            Toast.makeText(this, "皮肤修改器已启动", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "启动皮肤修改器失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopWebEditor() {
        EditorService.stop(this);
        refreshEditorPanel();
        Toast.makeText(this, "皮肤修改器已停止", Toast.LENGTH_SHORT).show();
    }

    private void cleanupTempFiles() {
        try {
            File importsTmp = SkinPaths.importsTmpDir();
            File[] kids = importsTmp.isDirectory() ? importsTmp.listFiles() : null;
            if (kids != null) {
                for (File kid : kids) SkinIO.deleteRecursive(kid);
            }
        } catch (Throwable ignored) {
        }
        try {
            File cache = getCacheDir();
            File[] kids = cache != null && cache.isDirectory() ? cache.listFiles() : null;
            if (kids != null) {
                for (File kid : kids) {
                    String name = kid.getName();
                    if (name.startsWith("NanoHTTPD-") || name.startsWith("nanohttpd-")) {
                        SkinIO.deleteRecursive(kid);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String editorLocalUrl() {
        return EditorAddress.localUrl();
    }

    private String editorLanUrl() {
        return EditorAddress.displayUrl();
    }

    private void copyEditorLanUrl() {
        String lanUrl = editorLanUrl();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("HOHO editor URL", lanUrl));
            Toast.makeText(this, "已复制: " + lanUrl, Toast.LENGTH_SHORT).show();
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
                if (currentTab == 0) {
                    tvEmpty.setVisibility(skinList.isEmpty() ? View.VISIBLE : View.GONE);
                }
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
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE
                && pendingStartWebEditorAfterNotificationPermission) {
            pendingStartWebEditorAfterNotificationPermission = false;
            startWebEditor();
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

    @Override
    protected void onDestroy() {
        if (executorService != null) executorService.shutdownNow();
        super.onDestroy();
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

    private void openRemoteThemes() {
        BusyDialog loading = showBusyDialog(
                "正在读取在线主题",
                "正在连接 Github 并读取主题列表，网络较慢时请稍候。",
                false);
        executorService.execute(() -> {
            try {
                String json = RemoteManifest.downloadManifest(SkinPaths.REMOTE_THEME_MANIFEST_URL);
                RemoteManifest manifest = RemoteManifest.parse(json, "themes");
                mainHandler.post(() -> {
                    loading.dismiss();
                    showRemoteThemesDialog(manifest);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading.dismiss();
                    Toast.makeText(this, "连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showRemoteThemesDialog(RemoteManifest manifest) {
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
                    .setPositiveButton("查看主题列表", (d, w) -> showRemoteThemesList(manifest))
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            showRemoteThemesList(manifest);
        }
    }

    private void showRemoteThemesList(RemoteManifest manifest) {
        final List<RemoteManifest.RemoteSkin> items = uniqueRemoteSkins(manifest.skins);
        if (items.isEmpty()) {
            Toast.makeText(this, "远程没有可用主题", Toast.LENGTH_SHORT).show();
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
                .setTitle("在线主题")
                .setAdapter(adapter, (d, w) -> downloadRemoteTheme(items.get(w)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadRemoteTheme(RemoteManifest.RemoteSkin s) {
        String title = s.name != null && !s.name.trim().isEmpty() ? s.name.trim() : s.file;
        BusyDialog loading = showBusyDialog("正在下载主题 (Github)", title + "\n准备从 Github 下载...", true);
        executorService.execute(() -> {
            try {
                File zip = RemoteManifest.downloadSkinZip(SkinPaths.REMOTE_THEME_MANIFEST_URL, s.file,
                        pct -> mainHandler.post(() -> {
                            loading.progressBar.setProgress(pct);
                            loading.messageView.setText(title + "\n正在从 Github 下载，已完成 " + pct + "%");
                        }));
                SkinLibrary.ImportResult r = SkinLibrary.importThemeZip(zip);
                zip.delete();
                mainHandler.post(() -> {
                    loading.dismiss();
                    if (r.success) {
                        Toast.makeText(this, "下载并导入成功: " + r.dirName, Toast.LENGTH_SHORT).show();
                        loadThemes();
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
            loadThemes();
            refreshActivateButton();
            refreshThemeExportStatus();
            refreshEditorPanel();
        }
    }
}
