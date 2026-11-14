package com.dshgo.storageatlas;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * 承载 MCP 服务器的前台服务：App 退到后台后 AI 仍能连上。
 * 服务只监听 127.0.0.1，离开手机无法访问。
 */
public class McpService extends Service {

    private static final String CHANNEL = "mcp";
    private static final int NOTI_ID = 41;
    private static McpServer SERVER;

    public static McpServer server() { return SERVER; }

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, McpService.class);
            c.startForegroundService(i);
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, McpService.class));
        } catch (Exception ignored) {
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        Atlas atlas = Atlas.get(this);
        try {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "AI 连接",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("存储星图的本机 AI 接口");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);

            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(this, CHANNEL)
                    .setContentTitle("存储星图 · AI 接口已开启")
                    .setContentText((atlas.mcpLan ? "局域网 " + McpServer.localIp() : "本机 127.0.0.1")
                            + ":" + atlas.mcpPort)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
            startForeground(NOTI_ID, n);
        } catch (Exception ignored) {
        }

        SERVER = new McpServer(this);
        SERVER.setOnDataChanged(new Runnable() {
            @Override public void run() {
                final MainActivity a = MainActivity.CURRENT;
                if (a != null) a.refreshFromMcp();
            }
        });
        try {
            SERVER.start(atlas.mcpPort, atlas.mcpToken, atlas.mcpLan);
        } catch (Exception e) {
            SERVER = null;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (SERVER == null) {
            // 端口被占用等情况：重试一次
            Atlas atlas = Atlas.get(this);
            SERVER = new McpServer(this);
            try { SERVER.start(atlas.mcpPort, atlas.mcpToken, atlas.mcpLan); } catch (Exception ignored) { }
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (SERVER != null) {
            SERVER.stop();
            SERVER = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
