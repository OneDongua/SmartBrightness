package com.onedongua.smartbrightness.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.onedongua.smartbrightness.R;
import com.onedongua.smartbrightness.brightness.BrightnessController;
import com.onedongua.smartbrightness.executor.ShellExecutor;
import com.onedongua.smartbrightness.log.AppLog;
import com.onedongua.smartbrightness.receiver.ScreenReceiver;
import com.onedongua.smartbrightness.sensor.LightSensorManager;
import com.onedongua.smartbrightness.settings.AppSettings;

public class BrightnessService extends Service {
    private static final String TAG = "BrightnessService";
    private static final String CHANNEL_ID = "brightness_monitor";
    private static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_STATUS_CHANGED = "com.onedongua.smartbrightness.action.STATUS_CHANGED";
    public static final String ACTION_UPDATE_INTERVAL = "com.onedongua.smartbrightness.action.UPDATE_INTERVAL";
    public static final String EXTRA_RUNNING = "running";
    private long checkInterval;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable periodicCheck = new Runnable() {
        @Override
        public void run() {
            if (!periodicCheckRunning || !isScreenOn() || checkInterval <= 0) {
                periodicCheckRunning = false;
                return;
            }
            detectOnce();
            handler.postDelayed(this, checkInterval);
        }
    };

    private LightSensorManager lightSensorManager;
    private BrightnessController brightnessController;
    private AppSettings appSettings;
    private AppLog appLog;
    private ScreenReceiver screenReceiver;
    private boolean receiverRegistered;
    private boolean periodicCheckRunning;

    @Override
    public void onCreate() {
        super.onCreate();
        appSettings = new AppSettings(this);
        appSettings.setServiceRunning(true);
        checkInterval = appSettings.getCheckInterval();
        sendStatusChanged(true);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        ShellExecutor shellExecutor = new ShellExecutor(this);
        appLog = new AppLog(this);
        lightSensorManager = new LightSensorManager(this);
        brightnessController = new BrightnessController(this, shellExecutor);
        registerScreenReceiver();
        if (isScreenOn()) {
            startPeriodicCheck();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!appSettings.isServiceEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_UPDATE_INTERVAL.equals(intent.getAction())) {
            updateInterval();
            return START_STICKY;
        }
        if (isScreenOn()) {
            detectOnce();
            startPeriodicCheck();
        }
        return START_STICKY;
    }

    private void updateInterval() {
        checkInterval = appSettings.getCheckInterval();
        if (checkInterval <= 0) {
            stopPeriodicCheck();
        } else if (isScreenOn()) {
            // Restart periodic check with new interval if screen is on
            periodicCheckRunning = true;
            handler.removeCallbacks(periodicCheck);
            handler.postDelayed(periodicCheck, checkInterval);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        if (lightSensorManager != null) {
            lightSensorManager.release();
        }
        if (appSettings != null) {
            appSettings.setServiceRunning(false);
        }
        sendStatusChanged(false);
        super.onDestroy();
    }

    private void sendStatusChanged(boolean running) {
        Intent intent = new Intent(ACTION_STATUS_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_RUNNING, running);
        sendBroadcast(intent);
    }

    private void registerScreenReceiver() {
        screenReceiver = new ScreenReceiver(this::onScreenOn, this::onScreenOff);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void onScreenOn() {
        startPeriodicCheck();
        detectOnce();
    }

    private void onScreenOff() {
        stopPeriodicCheck();
        if (lightSensorManager != null) {
            lightSensorManager.release();
        }
    }

    private void startPeriodicCheck() {
        if (periodicCheckRunning || checkInterval <= 0) {
            return;
        }
        periodicCheckRunning = true;
        handler.removeCallbacks(periodicCheck);
        handler.postDelayed(periodicCheck, checkInterval);
    }

    private void stopPeriodicCheck() {
        periodicCheckRunning = false;
        handler.removeCallbacks(periodicCheck);
    }

    private void detectOnce() {
        if (lightSensorManager == null || !isScreenOn()) {
            return;
        }
        lightSensorManager.detectOnce(new LightSensorManager.Callback() {
            @Override
            public void onLux(float lux) {
                handleLux(lux);
            }

            @Override
            public void onUnavailable(String reason) {
                Log.w(TAG, "Light sensor unavailable: " + reason);
                appLog.add("环境光传感器不可用:" + reason);
            }
        });
    }

    private boolean isScreenOn() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager != null && powerManager.isInteractive();
    }

    private void handleLux(float lux) {
        float thresholdLux = appSettings.getThresholdLux();
        String luxText = "Lux=" + lux;
        Log.d(TAG, luxText);
        if (lux <= thresholdLux) {
            appLog.add("未达到阈值(" + thresholdLux + ")," + luxText);
            return;
        }
        if (brightnessController.isAutoBrightnessEnabled()) {
            appLog.add("已是自动亮度," + luxText);
            return;
        }
        boolean enabled = brightnessController.enableAutoBrightness();
        appLog.add((enabled ? "已开启自动亮度" : "开启自动亮度失败") + "," + luxText);
        Log.i(TAG, enabled ? "Auto brightness enabled" : "Failed to enable auto brightness");
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_brightness)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
