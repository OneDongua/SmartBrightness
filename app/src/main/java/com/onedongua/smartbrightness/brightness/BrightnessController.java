package com.onedongua.smartbrightness.brightness;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.onedongua.smartbrightness.executor.ShellExecutor;

public class BrightnessController {
    private static final String TAG = "BrightnessController";
    private static final String ENABLE_AUTO_BRIGHTNESS_COMMAND =
            "settings put system screen_brightness_mode 1";
    private static final String DISABLE_AUTO_BRIGHTNESS_COMMAND =
            "settings put system screen_brightness_mode 0";
    private static final String SET_BRIGHTNESS_COMMAND_FORMAT =
            "settings put system screen_brightness %d";

    private final Context context;
    private final ShellExecutor shellExecutor;

    public BrightnessController(Context context, ShellExecutor shellExecutor) {
        this.context = context.getApplicationContext();
        this.shellExecutor = shellExecutor;
    }

    public boolean isAutoBrightnessEnabled() {
        try {
            int mode = Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE
            );
            return mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "screen_brightness_mode not found", e);
            return false;
        }
    }

    public int getCurrentBrightness() {
        try {
            return Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS
            );
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "screen_brightness not found", e);
            return -1;
        }
    }

    public boolean enableAutoBrightness() {
        ShellExecutor.Result result = shellExecutor.execute(ENABLE_AUTO_BRIGHTNESS_COMMAND);
        if (!result.success) {
            Log.w(TAG, "Enable auto brightness failed: " + result.stderr);
        }
        return result.success;
    }

    public boolean disableAutoBrightness() {
        ShellExecutor.Result result = shellExecutor.execute(DISABLE_AUTO_BRIGHTNESS_COMMAND);
        if (!result.success) {
            Log.w(TAG, "Disable auto brightness failed: " + result.stderr);
        }
        return result.success;
    }

    public boolean setBrightness(int brightness) {
        String command = String.format(SET_BRIGHTNESS_COMMAND_FORMAT, brightness);
        ShellExecutor.Result result = shellExecutor.execute(command);
        if (!result.success) {
            Log.w(TAG, "Set brightness failed: " + result.stderr);
        }
        return result.success;
    }
}
