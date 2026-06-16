package com.example.gafhubforwarder.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.gafhubforwarder.api.ApiClient;
import com.example.gafhubforwarder.api.ApiService;
import com.example.gafhubforwarder.models.MessageRequest;
import com.example.gafhubforwarder.utils.EncryptionManager;
import com.example.gafhubforwarder.utils.PreferencesManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageSenderService extends Service {

    private static final String TAG = "MessageSender";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "foreground_channel";
    public static final String ACTION_STOP_SERVICE = "STOP_SERVICE";
    private static final int MAX_RETRIES = 10;
    private static final int BASE_DELAY_MS = 2000;

    private PreferencesManager prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PreferencesManager(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getForegroundNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Эхофон", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getForegroundNotification() {
        Intent stopIntent = new Intent(this, MessageSenderService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Эхофон")
                .setContentText("Активен")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Остановить", stopPendingIntent)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if ("SEND_MESSAGE".equals(intent.getAction())) {
                String appName = intent.getStringExtra("app_name");
                String type = intent.getStringExtra("type");
                String plainText = intent.getStringExtra("encrypted_body");
                sendWithRetry(appName, type, plainText, 1);
            }
        }
        return START_NOT_STICKY;
    }

    private void sendWithRetry(String appName, String type, String plainText, int attempt) {
        String apiKey = prefs.getApiKey();
        if (apiKey == null) return;

        String password = prefs.getUserPassword();
        String salt = prefs.getEncryptionSalt();

        String encryptedBody = plainText;
        if (password != null && salt != null) {
            try {
                javax.crypto.spec.SecretKeySpec key = EncryptionManager.generateKey(password, salt);
                encryptedBody = EncryptionManager.encrypt(plainText, key);
            } catch (Exception e) {
                Log.e(TAG, "Encryption failed: " + e.getMessage());
                return;
            }
        }

        String finalEncryptedBody = encryptedBody;
        executor.execute(() -> {
            try {
                if (attempt > 1) {
                    Thread.sleep(BASE_DELAY_MS * (long) Math.pow(2, attempt - 2));
                }
                ApiService api = ApiClient.getClient().create(ApiService.class);
                MessageRequest request = new MessageRequest(null, appName, type, finalEncryptedBody);
                retrofit2.Response<Void> response = api.sendMessage(apiKey, request).execute();

                if (response.isSuccessful()) {
                    Log.d(TAG, "Sent on attempt " + attempt);
                } else if (response.code() == 409) {
                    Log.d(TAG, "Duplicate, server already has it");
                } else {
                    retryOrDrop(appName, type, finalEncryptedBody, attempt, "HTTP " + response.code());
                }
            } catch (Exception e) {
                retryOrDrop(appName, type, finalEncryptedBody, attempt, e.getMessage());
            }
        });
    }

    private void retryOrDrop(String appName, String type, String encryptedBody, int attempt, String reason) {
        if (attempt < MAX_RETRIES) {
            Log.w(TAG, "Attempt " + attempt + " failed (" + reason + "), retrying...");
            sendWithRetry(appName, type, encryptedBody, attempt + 1);
        } else {
            Log.e(TAG, "All " + MAX_RETRIES + " attempts failed, dropping message");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
