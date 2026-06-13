package com.onedongua.smartbrightness;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import androidx.core.content.ContextCompat;
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
    private boolean updatingServiceSwitch;
    private boolean isLogPanelVisible;
    private boolean serviceStatusReceiverRegistered;
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
    private final BroadcastReceiver serviceStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BrightnessService.ACTION_STATUS_CHANGED.equals(intent.getAction())) {
                boolean running = intent.getBooleanExtra(BrightnessService.EXTRA_RUNNING, false);
                appSettings.setServiceRunning(running);
                refreshServiceStatus();
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
        startServiceIfEnabled();
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
        if (requestCode != REQUEST_CODE_PERMISSION_SHIZUKU) {
            return;
        }
        if (granted) {
            toast(R.string.permission_granted_shizuku);
            startBrightnessService();
        } else {
            appSettings.setServiceEnabled(false);
            refreshServiceControlUi();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerServiceStatusReceiver();
        refreshServiceControlUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLog();
        refreshServiceControlUi();
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
    protected void onStop() {
        super.onStop();
        unregisterServiceStatusReceiver();
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
            appSettings.setServiceEnabled(false);
            refreshServiceControlUi();
            return;
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            toast(R.string.permission_granted_shizuku);
            startBrightnessService();
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            appSettings.setServiceEnabled(false);
            refreshServiceControlUi();
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
            toast(R.string.permission_denied_root);
            appSettings.setServiceEnabled(false);
            refreshServiceControlUi();
        }
    }

    private void startBrightnessService() {
        appSettings.setServiceEnabled(true);
        refreshServiceStatus();
        Intent intent = new Intent(this, BrightnessService.class);
        startForegroundService(intent);
    }

    private void startServiceForCurrentMode() {
        if (!appSettings.isServiceEnabled()) {
            stopBrightnessService();
            return;
        }
        ShellExecutor.Mode currentMode = appSettings.getShellMode();
        if (currentMode == ShellExecutor.Mode.ROOT) {
            startRoot();
        } else if (currentMode == ShellExecutor.Mode.SHIZUKU) {
            checkAndStartShizuku();
        }
    }

    private void startServiceIfEnabled() {
        if (appSettings.isServiceEnabled()) {
            startServiceForCurrentMode();
        } else {
            stopBrightnessService();
        }
    }

    private void stopBrightnessService() {
        stopService(new Intent(this, BrightnessService.class));
        appSettings.setServiceRunning(false);
        refreshServiceStatus();
    }

    private void initSettingsUi() {
        refreshServiceControlUi();
        binding.serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingServiceSwitch) {
                return;
            }
            appSettings.setServiceEnabled(isChecked);
            if (isChecked) {
                refreshServiceStatus();
                startServiceForCurrentMode();
            } else {
                stopBrightnessService();
            }
        });

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
            startServiceIfEnabled();
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

    private void refreshServiceControlUi() {
        if (binding == null || appSettings == null) {
            return;
        }
        updatingServiceSwitch = true;
        binding.serviceSwitch.setChecked(appSettings.isServiceEnabled());
        updatingServiceSwitch = false;
        refreshServiceStatus();
    }

    private void refreshServiceStatus() {
        if (binding == null || appSettings == null) {
            return;
        }
        boolean running = isBrightnessServiceRunning();
        appSettings.setServiceRunning(running);
        int statusResId;
        if (!appSettings.isServiceEnabled()) {
            statusResId = R.string.service_status_disabled;
        } else if (running) {
            statusResId = R.string.service_status_running;
        } else {
            statusResId = R.string.service_status_stopped;
        }
        binding.serviceStatusText.setText(getString(R.string.service_status_label, getString(statusResId), appSettings.getShellMode().name()));
    }

    @SuppressWarnings("deprecation")
    private boolean isBrightnessServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) {
            return appSettings.isServiceRunning();
        }
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BrightnessService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void registerServiceStatusReceiver() {
        if (serviceStatusReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(BrightnessService.ACTION_STATUS_CHANGED);
        ContextCompat.registerReceiver(
                this, serviceStatusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        serviceStatusReceiverRegistered = true;
    }

    private void unregisterServiceStatusReceiver() {
        if (!serviceStatusReceiverRegistered) {
            return;
        }
        unregisterReceiver(serviceStatusReceiver);
        serviceStatusReceiverRegistered = false;
    }

    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public void toast(@StringRes int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

}
