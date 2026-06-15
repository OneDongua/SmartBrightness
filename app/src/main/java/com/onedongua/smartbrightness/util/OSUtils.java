package com.onedongua.smartbrightness.util;

import android.annotation.SuppressLint;

import java.lang.reflect.Method;

public class OSUtils {
    private static final String KEY_MI_OS_VERSION_NAME = "ro.mi.os.version.name";
    private static final String KEY_MIUI_OS_VERSION_NAME = "ro.miui.ui.version.name";

    @SuppressLint("PrivateApi")
    public static String getSystemProperty(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("get", String.class);
            return (String) method.invoke(null, key);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isMiOS() {
        return !getSystemProperty(KEY_MIUI_OS_VERSION_NAME).isEmpty()
                || !getSystemProperty(KEY_MI_OS_VERSION_NAME).isEmpty();
    }
}
