package com.onedongua.smartbrightness.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.onedongua.smartbrightness.shizuku.ShellExecutor;

public class AppSettings {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_SERVICE_RUNNING = "service_running";
    private static final String KEY_THRESHOLD_LUX = "threshold_lux";
    private static final String KEY_SHELL_MODE = "shell_mode";
    private static final float DEFAULT_THRESHOLD_LUX = 500f;
    private static final String MODE_SHIZUKU = "SHIZUKU";
    private static final String MODE_ROOT = "ROOT";

    private final SharedPreferences preferences;

    public AppSettings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isServiceEnabled() {
        return preferences.getBoolean(KEY_SERVICE_ENABLED, true);
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
}
