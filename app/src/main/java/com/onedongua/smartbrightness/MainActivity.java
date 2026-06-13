package com.onedongua.smartbrightness;

import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.onedongua.smartbrightness.databinding.ActivityMainBinding;
import com.onedongua.smartbrightness.log.AppLog;
import com.onedongua.smartbrightness.service.BrightnessService;
import com.onedongua.smartbrightness.settings.AppSettings;
import com.onedongua.smartbrightness.shizuku.ShellExecutor;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSION_SHIZUKU = 1;
    private static final long LOG_AUTO_REFRESH_INTERVAL_MS = 1000;
    private ActivityMainBinding binding;
    private AppSettings appSettings;
    private AppLog appLog;
    private boolean updatingThresholdUi;
    private boolean isLogPanelVisible;
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isLogPanelVisible) {
                refreshLog();
                autoRefreshHandler.postDelayed(this, LOG_AUTO_REFRESH_INTERVAL_MS);
            }
        }
    };
    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionsResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        appSettings = new AppSettings(this);
        appLog = new AppLog(this);

        setContentView(binding.getRoot());
        adaptInsets();
        initSettingsUi();
        initLogUi();

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        startServiceForCurrentMode();
    }

    private void adaptInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_CODE_PERMISSION_SHIZUKU && granted) {
            toast(R.string.permission_granted_shizuku);
            startBrightnessService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLog();
        if (isLogPanelVisible) {
            startAutoRefreshLog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefreshLog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefreshLog();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }

    private void checkAndStartShizuku() {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            toast(R.string.permission_granted_shizuku);
            startBrightnessService();
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
        } else {
            // Request the permission
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION_SHIZUKU);
        }
    }

    private boolean checkRootPermission() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();

            int result = process.waitFor();
            return result == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void startRoot() {
        if (checkRootPermission()) {
            toast(R.string.permission_granted_root);
            startBrightnessService();
        } else {
            toast("no root");
        }
    }

    private void startBrightnessService() {
        Intent intent = new Intent(this, BrightnessService.class);
        startForegroundService(intent);
    }

    private void startServiceForCurrentMode() {
        ShellExecutor.Mode currentMode = appSettings.getShellMode();
        if (currentMode == ShellExecutor.Mode.ROOT) {
            startRoot();
        } else if (currentMode == ShellExecutor.Mode.SHIZUKU) {
            checkAndStartShizuku();
        }
    }

    private void initSettingsUi() {
        binding.pageGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean showSettings = checkedId == R.id.settingsPageButton;
            binding.settingsPanel.setVisibility(showSettings ? View.VISIBLE : View.GONE);
            binding.logPanel.setVisibility(showSettings ? View.GONE : View.VISIBLE);
            isLogPanelVisible = !showSettings;
            if (isLogPanelVisible) {
                refreshLog();
                startAutoRefreshLog();
            } else {
                stopAutoRefreshLog();
            }
        });

        applyThresholdToUi(appSettings.getThresholdLux());
        binding.thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    saveThreshold(progress);
                    applyThresholdToUi(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        binding.thresholdInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updatingThresholdUi) {
                    return;
                }
                String value = s.toString().trim();
                if (value.isEmpty()) {
                    return;
                }
                try {
                    float threshold = Float.parseFloat(value);
                    saveThreshold(threshold);
                    binding.thresholdSeekBar.setProgress(Math.min(2000, Math.round(threshold)));
                } catch (NumberFormatException ignored) {
                    toast(R.string.threshold_invalid);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.shellModeGroup.check(appSettings.getShellMode() == ShellExecutor.Mode.ROOT
                ? R.id.rootModeButton
                : R.id.shizukuModeButton);
        binding.shellModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            ShellExecutor.Mode mode = checkedId == R.id.rootModeButton
                    ? ShellExecutor.Mode.ROOT
                    : ShellExecutor.Mode.SHIZUKU;
            appSettings.setShellMode(mode);
            startServiceForCurrentMode();
        });
    }

    private void initLogUi() {
        binding.refreshLogButton.setOnClickListener(v -> refreshLog());
        binding.clearLogButton.setOnClickListener(v -> {
            appLog.clear();
            refreshLog();
        });
    }

    private void applyThresholdToUi(float threshold) {
        updatingThresholdUi = true;
        binding.thresholdSeekBar.setProgress(Math.min(2000, Math.round(threshold)));
        binding.thresholdInput.setText(formatThreshold(threshold));
        updatingThresholdUi = false;
    }

    private void saveThreshold(float threshold) {
        appSettings.setThresholdLux(threshold);
    }

    private String formatThreshold(float threshold) {
        if (threshold == Math.round(threshold)) {
            return String.valueOf(Math.round(threshold));
        }
        return String.valueOf(threshold);
    }

    private void startAutoRefreshLog() {
        stopAutoRefreshLog();
        autoRefreshHandler.postDelayed(autoRefreshRunnable, LOG_AUTO_REFRESH_INTERVAL_MS);
    }

    private void stopAutoRefreshLog() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void refreshLog() {
        if (binding != null && appLog != null) {
            binding.logText.setText(appLog.getDisplayText());
        }
    }


    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public void toast(@StringRes int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

}
