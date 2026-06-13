package com.onedongua.smartbrightness.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

public class LightSensorManager {
    private static final long SENSOR_TIMEOUT_MS = 2_000L;

    private final SensorManager sensorManager;
    private final Sensor lightSensor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private OneShotLightListener currentListener;

    public LightSensorManager(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }

    public void detectOnce(Callback callback) {
        if (sensorManager == null || lightSensor == null) {
            callback.onUnavailable("TYPE_LIGHT not found");
            return;
        }

        release();
        OneShotLightListener listener = new OneShotLightListener(callback);
        currentListener = listener;

        boolean registered = sensorManager.registerListener(
                currentListener,
                lightSensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                handler
        );
        if (!registered) {
            release();
            callback.onUnavailable("register failed");
            return;
        }
        listener.startTimeout();
    }

    public void release() {
        if (sensorManager != null && currentListener != null) {
            currentListener.cancelTimeout();
            sensorManager.unregisterListener(currentListener);
            currentListener = null;
        }
    }

    public interface Callback {
        void onLux(float lux);

        void onUnavailable(String reason);
    }

    private class OneShotLightListener implements SensorEventListener {
        private final Callback callback;
        private final Runnable timeout;
        private boolean completed;

        OneShotLightListener(Callback callback) {
            this.callback = callback;
            this.timeout = () -> {
                if (!completed) {
                completed = true;
                release(this);
                callback.onUnavailable("timeout");
            }
        };
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (completed || event.values.length == 0) {
                return;
            }
            completed = true;
            handler.removeCallbacks(timeout);
            float lux = event.values[0];
            release(this);
            callback.onLux(lux);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        void startTimeout() {
            handler.postDelayed(timeout, SENSOR_TIMEOUT_MS);
        }

        void cancelTimeout() {
            handler.removeCallbacks(timeout);
        }
    }

    private void release(OneShotLightListener listener) {
        if (sensorManager != null && currentListener == listener) {
            listener.cancelTimeout();
            sensorManager.unregisterListener(listener);
            currentListener = null;
        }
    }
}
