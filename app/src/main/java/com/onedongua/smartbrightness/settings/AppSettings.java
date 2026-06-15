package com.onedongua.smartbrightness.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.onedongua.smartbrightness.executor.ShellExecutor;

public class AppSettings {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_SERVICE_RUNNING = "service_running";
    private static final String KEY_THRESHOLD_LUX = "threshold_lux";
    private static final String KEY_SHELL_MODE = "shell_mode";
    private static final String KEY_CHECK_INTERVAL = "check_interval";
    private static final String KEY_AUTO_RESTORE_ENABLED = "auto_restore_enabled";
    private static final String KEY_RECORDED_LUX = "recorded_lux";
    private static final String KEY_RECORDED_BRIGHTNESS = "recorded_brightness";
    private static final String KEY_HAS_RECORDED_DATA = "has_recorded_data";
    private static final String KEY_AUTO_RESTORE_MODE = "auto_restore_mode";
    private static final String KEY_AUTO_RESTORE_BELOW = "auto_restore_below";
    private static final float DEFAULT_THRESHOLD_LUX = 1000f;
    private static final long DEFAULT_CHECK_INTERVAL = 0;
    private static final int DEFAULT_AUTO_RESTORE_BELOW = 400;

    private static final String MODE_SHIZUKU = "SHIZUKU";
    private static final String MODE_ROOT = "ROOT";

    private final SharedPreferences preferences;

    public AppSettings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isServiceEnabled() {
        return preferences.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    public void setServiceEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_SERVICE_ENABLED, enabled)
                .apply();
    }

    public boolean isServiceRunning() {
        return preferences.getBoolean(KEY_SERVICE_RUNNING, false);
    }

    public void setServiceRunning(boolean running) {
        preferences.edit()
                .putBoolean(KEY_SERVICE_RUNNING, running)
                .apply();
    }

    public float getThresholdLux() {
        return preferences.getFloat(KEY_THRESHOLD_LUX, DEFAULT_THRESHOLD_LUX);
    }

    public void setThresholdLux(float thresholdLux) {
        preferences.edit()
                .putFloat(KEY_THRESHOLD_LUX, Math.max(0f, thresholdLux))
                .apply();
    }

    public long getCheckInterval() {
        return preferences.getLong(KEY_CHECK_INTERVAL, DEFAULT_CHECK_INTERVAL);
    }

    public void setCheckInterval(long intervalInMillis) {
        preferences.edit()
                .putLong(KEY_CHECK_INTERVAL, Math.max(0, intervalInMillis))
                .apply();
    }

    public ShellExecutor.Mode getShellMode() {
        String value = preferences.getString(KEY_SHELL_MODE, MODE_SHIZUKU);
        if (MODE_ROOT.equals(value)) {
            return ShellExecutor.Mode.ROOT;
        }
        return ShellExecutor.Mode.SHIZUKU;
    }

    public void setShellMode(ShellExecutor.Mode mode) {
        preferences.edit()
                .putString(KEY_SHELL_MODE, mode == ShellExecutor.Mode.ROOT ? MODE_ROOT : MODE_SHIZUKU)
                .apply();
    }

    public boolean isAutoRestoreEnabled() {
        return preferences.getBoolean(KEY_AUTO_RESTORE_ENABLED, false);
    }

    public void setAutoRestoreEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_AUTO_RESTORE_ENABLED, enabled)
                .apply();
    }

    public float getRecordedLux() {
        return preferences.getFloat(KEY_RECORDED_LUX, -1f);
    }

    public void setRecordedLux(float lux) {
        preferences.edit()
                .putFloat(KEY_RECORDED_LUX, lux)
                .apply();
    }

    public int getRecordedBrightness() {
        return preferences.getInt(KEY_RECORDED_BRIGHTNESS, -1);
    }

    public void setRecordedBrightness(int brightness) {
        preferences.edit()
                .putInt(KEY_RECORDED_BRIGHTNESS, brightness)
                .apply();
    }

    public boolean hasRecordedData() {
        return preferences.getBoolean(KEY_HAS_RECORDED_DATA, false);
    }

    public void setHasRecordedData(boolean hasData) {
        preferences.edit()
                .putBoolean(KEY_HAS_RECORDED_DATA, hasData)
                .apply();
    }

    public void clearRecordedData() {
        preferences.edit()
                .remove(KEY_RECORDED_LUX)
                .putBoolean(KEY_HAS_RECORDED_DATA, false)
                .apply();
    }


    public int getAutoRestoreMode() {
        return preferences.getInt(KEY_AUTO_RESTORE_MODE, 0);
    }

    public void setAutoRestoreMode(int mode) {
        preferences.edit()
                .putInt(KEY_AUTO_RESTORE_MODE, mode)
                .apply();
    }

    public int getAutoRestoreBelow() {
        return preferences.getInt(KEY_AUTO_RESTORE_BELOW, DEFAULT_AUTO_RESTORE_BELOW);
    }

    public void setAutoRestoreBelow(int lux) {
        preferences.edit()
                .putInt(KEY_AUTO_RESTORE_BELOW, lux)
                .apply();
    }
}
