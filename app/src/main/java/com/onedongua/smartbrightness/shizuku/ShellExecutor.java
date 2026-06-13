package com.onedongua.smartbrightness.shizuku;

import android.content.Context;
import android.content.pm.PackageManager;

import com.onedongua.smartbrightness.settings.AppSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;

public class ShellExecutor {
    private final AppSettings appSettings;

    public ShellExecutor(Context context) {
        appSettings = new AppSettings(context);
    }

    public Result execute(String command) {
        Mode mode = getMode();
        if (mode == Mode.ROOT) {
            return executeWithRoot(command);
        }
        return executeWithShizuku(command);
    }

    public Mode getMode() {
        return appSettings.getShellMode();
    }

    public void setMode(Mode mode) {
        appSettings.setShellMode(mode);
    }

    private Result executeWithShizuku(String command) {
        try {
            if (!Shizuku.pingBinder()
                    || Shizuku.isPreV11()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return Result.failure("Shizuku permission unavailable");
            }
            Method newProcess = Shizuku.class.getDeclaredMethod(
                    "newProcess",
                    String[].class,
                    String[].class,
                    String.class
            );
            newProcess.setAccessible(true);
            Process process = (Process) newProcess.invoke(
                    null,
                    new String[]{"sh", "-c", command},
                    null,
                    null
            );
            return collectResult(process);
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public enum Mode {
        SHIZUKU,
        ROOT
    }

    private Result executeWithRoot(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).start();
            return collectResult(process);
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    private Result collectResult(Process process) throws IOException, InterruptedException {
        int exitCode = process.waitFor();
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        return new Result(exitCode, stdout, stderr);
    }

    private String readAll(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    public static class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean success;

        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.success = exitCode == 0;
        }

        static Result failure(String stderr) {
            return new Result(-1, "", stderr == null ? "" : stderr);
        }
    }
}
