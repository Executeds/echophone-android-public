package com.example.gafhubforwarder.services;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.gafhubforwarder.utils.PreferencesManager;
import java.util.HashMap;
import java.util.Map;

public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "NotificationListener";
    private PreferencesManager prefs;
    
    private final Map<String, Long> recentNotifications = new HashMap<>();
    private static final long DEDUP_WINDOW_MS = 10_000;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PreferencesManager(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        if (!prefs.getSelectedApps().contains(packageName)) {
            return;
        }

        String title = "";
        String text = "";

        try {
            android.app.Notification notification = sbn.getNotification();
            android.os.Bundle extras = notification.extras;
            title = extras.getString(NotificationCompat.EXTRA_TITLE, "");
            text = extras.getString(NotificationCompat.EXTRA_TEXT, "");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting notification: " + e.getMessage());
        }

        String dedupKey = packageName + "|" + title + "|" + text;
        Long lastSent = recentNotifications.get(dedupKey);
        long now = System.currentTimeMillis();

        if (lastSent != null && (now - lastSent) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Skipping duplicate notification from " + packageName);
            return;
        }

        recentNotifications.put(dedupKey, now);

        if (recentNotifications.size() > 100) {
            recentNotifications.entrySet().removeIf(entry -> (now - entry.getValue()) > DEDUP_WINDOW_MS);
        }

        String message = buildMessage(title, text);
        if (!message.isEmpty()) {
            sendToServer(packageName, message);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed
    }

    private String buildMessage(String title, String text) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(title);
        }
        if (text != null && !text.isEmpty()) {
            if (sb.length() > 0) sb.append(": ");
            sb.append(text);
        }
        return sb.toString();
    }

    private void sendToServer(String packageName, String message) {
        if (!isServiceRunning()) {
            Log.d(TAG, "Service not running, skipping");
            return;
        }

        Intent intent = new Intent(this, MessageSenderService.class);
        intent.setAction("SEND_MESSAGE");
        intent.putExtra("app_name", packageName);
        intent.putExtra("type", "notification");
        intent.putExtra("encrypted_body", message);
        startService(intent);

        Log.d(TAG, "Notification from " + packageName + ": " + message);
    }

    private boolean isServiceRunning() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MessageSenderService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
