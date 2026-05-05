package im.hoho.alipayInstallB.editor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import im.hoho.alipayInstallB.MainActivity;
import im.hoho.alipayInstallB.R;

public final class EditorService extends Service {

    private static final String CHANNEL_ID = "editor_http_server";
    private static final int NOTIFICATION_ID = 0x484F484F; // 'HOHO'
    private static boolean running;

    public static final String ACTION_START = "im.hoho.alipayInstallB.editor.START";
    public static final String ACTION_STOP = "im.hoho.alipayInstallB.editor.STOP";

    private EditorHttpServer server;

    public static void start(Context context) {
        running = true;
        Intent intent = new Intent(context, EditorService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        if (!running) return;
        running = false;
        Intent intent = new Intent(context, EditorService.class).setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundServer();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopServer();
        running = false;
        super.onDestroy();
    }

    private void ensureServerRunning() {
        try {
            if (server == null || !server.isAlive()) {
                server = new EditorHttpServer(getApplicationContext());
                server.start();
            }
        } catch (Exception ignored) {
        }
    }

    private void startForegroundServer() {
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
            running = true;
        } catch (Throwable t) {
            Log.e("EditorService", "startForeground failed", t);
            Toast.makeText(this, "前台服务启动失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
        ensureServerRunning();
        Toast.makeText(this, "皮肤修改器服务已启动 (端口 " + EditorHttpServer.PORT + ")", Toast.LENGTH_SHORT).show();
    }

    private void stopServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
            server = null;
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
        if (channel != null) return;
        channel = new NotificationChannel(
                CHANNEL_ID,
                "皮肤修改器服务",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持皮肤修改器可通过浏览器访问。");
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open, piFlags);

        Intent stop = new Intent(this, EditorService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, piFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("皮肤修改器运行中")
                .setContentText(EditorAddress.displayUrl())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .addAction(0, "Stop", stopIntent)
                .build();
    }
}
