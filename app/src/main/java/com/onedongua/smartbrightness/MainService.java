package com.onedongua.smartbrightness;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import rikka.shizuku.Shizuku;

public class MainService extends Service {

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private SensorEventListener listener;

    private final float LUX_THRESHOLD = 500f;
    private long lastChange = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float lux = event.values[0];
                long now = System.currentTimeMillis();

                // 10秒间隔防抖
                if(lux > LUX_THRESHOLD && now - lastChange > 10000) {
                    try {
                        if (Shizuku.pingBinder()) {
                            Settings.System.putInt(
                                    getContentResolver(),
                                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                            );
                            Log.d("BrightnessService", "自动亮度已开启");
                        }
                    } catch (Exception e) {
                        Log.e("BrightnessService", "修改失败", e);
                    }
                    lastChange = now;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(listener);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}