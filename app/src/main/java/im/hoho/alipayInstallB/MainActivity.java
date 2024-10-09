package im.hoho.alipayInstallB;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private Button btnExport, btnDelete, btnUpdate, btnActivate;
    private ImageView ivExportStatus, ivDeleteStatus, ivUpdateStatus, ivActivateStatus;
    private Button btnDownload;
    private ProgressBar progressBar;
    private ExecutorService executorService;
    private Handler mainHandler;


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
        setContentView(R.layout.activity_main);

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

    private void updateStatuses() {
        updateStatus(ivExportStatus, EXPORT_FILE);
        updateStatus(ivDeleteStatus, DELETE_FILE);
        updateStatus(ivUpdateStatus, UPDATE_FILE);
        updateStatus(ivActivateStatus, ACTIVATE_FILE);

        btnActivate.setText(new File(ACTIVATE_FILE).exists() ? "Deactivate" : "Activate");
    }

    private void updateStatus(ImageView imageView, String filePath) {
        imageView.setImageResource(new File(filePath).exists() ? R.drawable.green_circle : R.drawable.red_circle);
    }

    private void updateDownloadButtonText() {
        File skinFolder = new File(EXTRACT_PATH + "000_HOHO_ALIPAY_SKIN");
        btnDownload.setText(skinFolder.exists() ? "Redownload Resources" : "Download Resources");
    }

    private void downloadAndExtract() {
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
}