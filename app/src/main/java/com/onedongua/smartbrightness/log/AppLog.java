package com.onedongua.smartbrightness.log;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLog {
    private static final String PREFS_NAME = "app_log";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 80;

    private final SharedPreferences preferences;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss]", Locale.getDefault());

    public AppLog(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void add(String message) {
        String entry = timeFormat.format(new Date()) + message;
        List<String> entries = getEntries();
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        preferences.edit()
                .putString(KEY_ENTRIES, encode(entries))
                .apply();
    }

    public List<String> getEntries() {
        String raw = preferences.getString(KEY_ENTRIES, "");
        List<String> entries = new ArrayList<>();
        if (raw.isEmpty()) {
            return entries;
        }
        String[] parts = raw.split("\u001E", -1);
        for (String part : parts) {
            if (!part.isEmpty()) {
                entries.add(part.replace("\\n", "\n"));
            }
        }
        return entries;
    }

    public String getDisplayText() {
        List<String> entries = getEntries();
        if (entries.isEmpty()) {
            return "暂无日志";
        }
        StringBuilder builder = new StringBuilder();
        for (String entry : entries) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(entry);
        }
        return builder.toString();
    }

    public void clear() {
        preferences.edit().remove(KEY_ENTRIES).apply();
    }

    private String encode(List<String> entries) {
        StringBuilder builder = new StringBuilder();
        for (String entry : entries) {
            if (builder.length() > 0) {
                builder.append('\u001E');
            }
            builder.append(entry.replace("\n", "\\n"));
        }
        return builder.toString();
    }
}
