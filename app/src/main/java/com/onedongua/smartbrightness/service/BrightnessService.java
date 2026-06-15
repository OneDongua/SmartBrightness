package com.onedongua.smartbrightness.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.onedongua.smartbrightness.BuildConfig;
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
    private static final float LUX_RESTORE_TOLERANCE = 100.0f;
    public static final String ACTION_STATUS_CHANGED = "com.onedongua.smartbrightness.action.STATUS_CHANGED";
    public static final String ACTION_UPDATE_INTERVAL = "com.onedongua.smartbrightness.action.UPDATE_INTERVAL";
    public static final String EXTRA_RUNNING = "running";
    private long checkInterval;

    private HandlerThread workerThread;
    private Handler workerHandler;
    private final Runnable periodicCheck = new Runnable() {
        @Override
        public void run() {
            if (!periodicCheckRunning || !isScreenOn() || checkInterval <= 0) {
                periodicCheckRunning = false;
                return;
            }
            detectOnce();
            if (workerHandler != null) {
                workerHandler.postDelayed(this, checkInterval);
            }
        }
    };

    private LightSensorManager lightSensorManager;
    private BrightnessController brightnessController;
    private AppSettings appSettings;
    private AppLog appLog;
    private ShellExecutor shellExecutor;
    private ScreenReceiver screenReceiver;
    private volatile boolean receiverRegistered;
    private volatile boolean periodicCheckRunning;
    private boolean isFirstCheck = true;

    @Override
    public void onCreate() {
        super.onCreate();
        workerThread = new HandlerThread("BrightnessWorker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());

        appSettings = new AppSettings(this);
        appSettings.setServiceRunning(true);
        checkInterval = appSettings.getCheckInterval();
        sendStatusChanged(true);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        shellExecutor = new ShellExecutor(this);
        appLog = new AppLog(this);
        lightSensorManager = new LightSensorManager(this, workerHandler);
        brightnessController = new BrightnessController(this, shellExecutor);
        registerScreenReceiver();

        workerHandler.post(() -> {
            if (isScreenOn()) {
                startPeriodicCheck();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!appSettings.isServiceEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        workerHandler.post(() -> {
            if (intent != null && ACTION_UPDATE_INTERVAL.equals(intent.getAction())) {
                updateInterval();
            } else if (isScreenOn()) {
                detectOnce();
                startPeriodicCheck();
            }
        });
        return START_STICKY;
    }

    private void updateInterval() {
        checkInterval = appSettings.getCheckInterval();
        if (checkInterval <= 0) {
            stopPeriodicCheck();
        } else if (isScreenOn()) {
            // Restart periodic check with new interval if screen is on
            periodicCheckRunning = true;
            workerHandler.removeCallbacks(periodicCheck);
            workerHandler.postDelayed(periodicCheck, checkInterval);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (workerHandler != null) {
            workerHandler.removeCallbacksAndMessages(null);
        }
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        if (lightSensorManager != null) {
            lightSensorManager.release();
        }
        if (shellExecutor != null) {
            shellExecutor.destroy();
        }
        if (appSettings != null) {
            appSettings.setServiceRunning(false);
        }
        sendStatusChanged(false);
        if (workerThread != null) {
            workerThread.quitSafely();
        }
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
            registerReceiver(screenReceiver, filter, null, workerHandler, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter, null, workerHandler);
        }
        receiverRegistered = true;
    }

    private void onScreenOn() {
        startPeriodicCheck();
        workerHandler.post(this::detectOnce);
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
        workerHandler.removeCallbacks(periodicCheck);
        workerHandler.postDelayed(periodicCheck, checkInterval);
    }

    private void stopPeriodicCheck() {
        periodicCheckRunning = false;
        workerHandler.removeCallbacks(periodicCheck);
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
        if (BuildConfig.DEBUG) Log.d(TAG, luxText);

        boolean autoBrightnessEnabled = brightnessController.isAutoBrightnessEnabled();

        if (appSettings.isAutoRestoreEnabled()) {

            if (autoBrightnessEnabled) {
                if (appSettings.getAutoRestoreMode() == 0) {
                    if (appSettings.hasRecordedData()) {
                        if (Math.abs(lux - thresholdLux) <= LUX_RESTORE_TOLERANCE) {
                            // 环境光接近阈值，不恢复避免反复开关
                            appLog.add("环境光(" + lux + ")接近阈值(" + thresholdLux + ")");
                        } else {
                            float recordedLux = appSettings.getRecordedLux();
                            if (Math.abs(lux - recordedLux) <= LUX_RESTORE_TOLERANCE) {
                                int recordedBrightness = appSettings.getRecordedBrightness();
                                boolean disabled = brightnessController.disableAutoBrightness();
                                boolean restored = false;
                                if (disabled) {
                                    restored = brightnessController.setBrightness(recordedBrightness);
                                }
                                appLog.add("恢复亮度(" + recordedBrightness + "),环境光(" + lux + ")接近记录值(" + recordedLux + ")");
                                Log.i(TAG, "Restored brightness to " + recordedBrightness + ", disable auto: " + disabled + ", restore brightness: " + restored);
                            } else {
                                appLog.add("环境光(" + lux + ")未接近记录值(" + recordedLux + ")");
                            }
                        }
                    } else {
                        appLog.add("未记录数据");
                    }
                } else if (appSettings.getAutoRestoreMode() == 1) {
                    int belowLux = appSettings.getAutoRestoreBelow();
                    if (lux < belowLux) {
                        int recordedBrightness = appSettings.getRecordedBrightness();
                        if (recordedBrightness != -1) {
                            boolean disabled = brightnessController.disableAutoBrightness();
                            boolean restored = false;
                            if (disabled) {
                                restored = brightnessController.setBrightness(recordedBrightness);
                            }
                            appLog.add("恢复亮度(" + recordedBrightness + "),环境光(" + lux + ")低于设置值(" + belowLux + ")");
                            Log.i(TAG, "Restored brightness to " + recordedBrightness + ", disable auto: " + disabled + ", restore brightness: " + restored);
                        }
                    }
                }
            } else {
                int currentBrightness = brightnessController.getCurrentBrightness();

                if (lux > thresholdLux) {
                    if (isFirstCheck) {
                        appSettings.setRecordedLux(lux);
                        appSettings.setRecordedBrightness(currentBrightness);
                        appSettings.setHasRecordedData(true);
                        appLog.add("首次超过阈值,记录Lux=" + lux + ",亮度=" + currentBrightness);
                    }
                    brightnessController.enableAutoBrightness();
                    appLog.add("开启自动亮度," + luxText);
                    Log.i(TAG, "Auto brightness enabled (recorded lux=" + lux + ", brightness=" + currentBrightness + ")");
                } else {
                    appSettings.setRecordedLux(lux);
                    appSettings.setRecordedBrightness(currentBrightness);
                    appSettings.setHasRecordedData(true);
                    appLog.add("未达到阈值(" + thresholdLux + "),记录Lux=" + lux + ",亮度=" + currentBrightness);
                }
            }
            isFirstCheck = false;
            return;
        }

        // Legacy logic if Auto-Restore is disabled
        if (lux <= thresholdLux) {
            appLog.add("未达到阈值(" + thresholdLux + ")," + luxText);
            return;
        }
        if (autoBrightnessEnabled) {
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
