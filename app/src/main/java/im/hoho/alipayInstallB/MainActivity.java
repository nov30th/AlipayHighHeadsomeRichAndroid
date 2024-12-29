package im.hoho.alipayInstallB;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String EXTERNAL_STORAGE_PATH = Environment.getExternalStorageDirectory() + "/Android/media/com.eg.android.AlipayGphone/000_HOHO_ALIPAY_SKIN";
    private static final String EXPORT_FILE = EXTERNAL_STORAGE_PATH + "/export";
    private static final String DELETE_FILE = EXTERNAL_STORAGE_PATH + "/delete";
    private static final String UPDATE_FILE = EXTERNAL_STORAGE_PATH + "/update";
    private static final String ACTIVATE_FILE = EXTERNAL_STORAGE_PATH + "/actived";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String DOWNLOAD_URL = "https://github.com/nov30th/AlipayHighHeadsomeRichAndroid/raw/master/SD%E5%8D%A1%E8%B5%84%E6%BA%90%E6%96%87%E4%BB%B6%E5%8C%85/SD%E8%B5%84%E6%BA%90%E6%96%87%E4%BB%B6.zip";
    private static final String EXTRACT_PATH = Environment.getExternalStorageDirectory() + "/Android/media/com.eg.android.AlipayGphone/";
    private final String[] memberGrades = {"原有", "普通 (primary)", "黄金 (golden)", "铂金 (platinum)", "钻石 (diamond)"};
    private Button btnExport, btnDelete, btnUpdate, btnActivate;
    private ImageView ivExportStatus, ivDeleteStatus, ivUpdateStatus, ivActivateStatus;
    private Button btnDownload;
    private ProgressBar progressBar;
    private ExecutorService executorService;
    private Handler mainHandler;
    private Spinner spinnerMemberGrade;

    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_FIRST_RUN = "isFirstRun";

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
            } else {
                // 权限被拒绝
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查是否首次运行
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        boolean isFirstRun = settings.getBoolean(KEY_FIRST_RUN, true);

        if (isFirstRun) {
            new AlertDialog.Builder(this)
                    .setTitle("隐私说明")
                    .setMessage("本应用不会收集、不会上传任何用户信息或使用数据。\n\n" +
                            "应用仅在本地运行，不会与任何服务器通信（除非您主动点击\"下载资源包\"按钮从 Github 下载资源）。\n\n" +
                            "所有操作均在您的设备本地完成，请放心使用。")
                    .setPositiveButton("我知道了", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 标记已经显示过隐私说明
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putBoolean(KEY_FIRST_RUN, false);
                            editor.apply();
                        }
                    })
                    .setCancelable(false)
                    .show();
        }

        setContentView(R.layout.activity_main);

        spinnerMemberGrade = findViewById(R.id.spinnerMemberGrade);
        setupMemberGradeSpinner();


        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText("Version: " + BuildConfig.VERSION_NAME);

        btnExport = findViewById(R.id.btnExport);
        btnDelete = findViewById(R.id.btnDelete);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnActivate = findViewById(R.id.btnActivate);

        ivExportStatus = findViewById(R.id.ivExportStatus);
        ivDeleteStatus = findViewById(R.id.ivDeleteStatus);
        ivUpdateStatus = findViewById(R.id.ivUpdateStatus);
        ivActivateStatus = findViewById(R.id.ivActivateStatus);

        setupButtons();
        updateStatuses();


        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        updateDownloadButtonText();

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadAndExtract();
            }
        });

        Button btnOpenResourceFolder = findViewById(R.id.btnOpenResourceFolder);
        TextView tvGithubLink = findViewById(R.id.tvGithubLink);

        btnOpenResourceFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File resourceFolder = new File(EXTRACT_PATH + "000_HOHO_ALIPAY_SKIN");
                if (resourceFolder.exists() && resourceFolder.isDirectory()) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    Uri uri = Uri.parse(resourceFolder.getAbsolutePath());
                    intent.setDataAndType(uri, "*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

                    try {
                        startActivity(Intent.createChooser(intent, "选择文件浏览器"));
                    } catch (ActivityNotFoundException e) {
                        // 如果没有找到文件管理器应用，尝试使用 ACTION_VIEW
                        intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "resource/folder");

                        if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                            startActivity(intent);
                        } else {
                            Toast.makeText(MainActivity.this, "没有找到文件浏览器应用", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Resource package not installed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        tvGithubLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://github.com/nov30th/AlipayHighHeadsomeRichAndroid";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        });

    }

    private void setupButtons() {
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFile(EXPORT_FILE);
            }
        });
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFile(DELETE_FILE);
            }
        });
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFile(UPDATE_FILE);
            }
        });
        btnActivate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFile(ACTIVATE_FILE);
            }
        });
    }

    private void toggleFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        } else {
            try {
                new File(EXTERNAL_STORAGE_PATH).mkdirs();
                new File(filePath).mkdirs();
//                file.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
            }
        }
        updateStatuses();
        Toast.makeText(this, "Reopen payment code to take effect", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatuses();
        updateDownloadButtonText();
    }

    private void updateStatuses() {
        updateStatus(ivExportStatus, EXPORT_FILE);
        updateStatus(ivDeleteStatus, DELETE_FILE);
        updateStatus(ivUpdateStatus, UPDATE_FILE);
        updateStatus(ivActivateStatus, ACTIVATE_FILE);

        btnActivate.setText(new File(ACTIVATE_FILE).exists() ? "点击禁用皮肤" : "点击启用皮肤");
    }

    private void updateStatus(ImageView imageView, String filePath) {
        imageView.setImageResource(new File(filePath).exists() ? R.drawable.green_circle : R.drawable.red_circle);
    }

    private void updateDownloadButtonText() {
        File skinFolder = new File(EXTRACT_PATH + "000_HOHO_ALIPAY_SKIN");
        btnDownload.setText(skinFolder.exists() ? "重新下载资源包 (Github) 需要SD卡权限" : "下载资源包 (Github) 需要SD卡权限");
    }

    private void downloadAndExtract() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        }

        btnDownload.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(DOWNLOAD_URL);
                    URLConnection connection = url.openConnection();
                    connection.connect();

                    int fileLength = connection.getContentLength();

                    InputStream input = new BufferedInputStream(url.openStream());
                    OutputStream output = new FileOutputStream(EXTRACT_PATH + "temp.zip");

                    byte[] data = new byte[1024];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        final int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setProgress(progress);
                            }
                        });
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();

                    // 解压文件
                    unzip(EXTRACT_PATH + "temp.zip", EXTRACT_PATH);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            btnDownload.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Download and extraction completed", Toast.LENGTH_LONG).show();
                            updateDownloadButtonText();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            btnDownload.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    public void unzip(String zipFilePath, String destDirectory) {
        try {
            ZipFile zipFile = new ZipFile(zipFilePath);
            zipFile.extractAll(destDirectory);
        } catch (ZipException e) {
            e.printStackTrace();
            // 处理异常
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void setupMemberGradeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, memberGrades);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMemberGrade.setAdapter(adapter);

        // 设置当前选中的等级
        String currentGrade = getCurrentMemberGrade();
        int position = adapter.getPosition(currentGrade);
        spinnerMemberGrade.setSelection(position);

        spinnerMemberGrade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedGrade = parent.getItemAtPosition(position).toString();
                updateMemberGrade(selectedGrade);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private String getCurrentMemberGrade() {
        for (String grade : memberGrades) {
            if (grade.equals("原有")) continue;
            String folderName = "level_" + grade.split(" ")[1].replace("(", "").replace(")", "");
            File folder = new File(EXTERNAL_STORAGE_PATH, folderName);
            if (folder.exists()) {
                return grade;
            }
        }
        return "原有";
    }

    private void updateMemberGrade(String selectedGrade) {
        // 删除所有 level_ 文件夹
        for (String grade : memberGrades) {
            if (grade.equals("原有")) continue;
            String folderName = "level_" + grade.split(" ")[1].replace("(", "").replace(")", "");
            File folder = new File(EXTERNAL_STORAGE_PATH, folderName);
            if (folder.exists()) {
                deleteRecursive(folder);
            }
        }

        // 如果选择不是"原有"，创建新的 level_ 文件夹
        if (!selectedGrade.equals("原有")) {
            String folderName = "level_" + selectedGrade.split(" ")[1].replace("(", "").replace(")", "");
            File newFolder = new File(EXTERNAL_STORAGE_PATH, folderName);
            newFolder.mkdirs();
        }

        Toast.makeText(this, "会员等级已更新为：" + selectedGrade, Toast.LENGTH_SHORT).show();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }
}