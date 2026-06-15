package com.onedongua.smartbrightness;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.onedongua.smartbrightness.adapter.MainPagerAdapter;
import com.onedongua.smartbrightness.databinding.ActivityMainBinding;
import com.onedongua.smartbrightness.databinding.ViewLogsBinding;
import com.onedongua.smartbrightness.databinding.ViewSettingsBinding;
import com.onedongua.smartbrightness.executor.ShellExecutor;
import com.onedongua.smartbrightness.log.AppLog;
import com.onedongua.smartbrightness.service.BrightnessService;
import com.onedongua.smartbrightness.settings.AppSettings;
import com.onedongua.smartbrightness.util.OSUtils;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSION_SHIZUKU = 1;
    private static final long LOG_AUTO_REFRESH_INTERVAL_MS = 1000;
    private ActivityMainBinding binding;
    private AppSettings appSettings;
    private AppLog appLog;
    private boolean updatingThresholdUi;
    private boolean updatingIntervalUi;
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
    private final Runnable thresholdApplyRunnable = new Runnable() {
        @Override
        public void run() {
            if (settingsBinding == null) return;
            String value = settingsBinding.thresholdInput.getText().toString().trim();
            if (value.isEmpty()) return;
            try {
                float threshold = Float.parseFloat(value);
                saveThreshold(threshold);
            } catch (NumberFormatException ignored) {
                toast(R.string.threshold_invalid);
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
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            refreshNotificationStatus();
                        } else {
                            openNotificationSettings();
                        }
                    });

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        appSettings = new AppSettings(this);
        appLog = new AppLog(this);

        setContentView(binding.getRoot());
        adaptInsets();
        initPager();
        initSettingsUi();
        initLogUi();

        // 清除上次的亮度记录数据
        appSettings.clearRecordedData();
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
            setService(false);
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
        autoRefreshHandler.removeCallbacks(thresholdApplyRunnable);
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }

    private void checkAndStartShizuku() {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            setService(false);
            refreshServiceControlUi();
            toast(R.string.shizuku_error);
            return;
        }

        if (!Shizuku.pingBinder()) {
            // Binder is dead
            setService(false);
            refreshServiceControlUi();
            toast(R.string.shizuku_not_running);
            return;
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            toast(R.string.permission_granted_shizuku);
            startBrightnessService();
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            setService(false);
            refreshServiceControlUi();
            toast(R.string.permission_denied_shizuku);
        } else {
            // Request the permission
            setService(false);
            refreshServiceControlUi();
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
            setService(false);
            refreshServiceControlUi();
        }
    }

    private boolean checkAbility() {
        ShellExecutor.Mode mode = appSettings.getShellMode();
        if (mode == ShellExecutor.Mode.ROOT) {
            if (!checkRootPermission()) {
                toast(R.string.permission_denied_root);
                setService(false);
                refreshServiceControlUi();
                return false;
            }
        } else if (mode == ShellExecutor.Mode.SHIZUKU) {
            if (Shizuku.isPreV11() || !Shizuku.pingBinder() ||
                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                toast(R.string.shizuku_error);
                setService(false);
                refreshServiceControlUi();
                return false;
            }
        }
        return true;
    }

    private void startBrightnessService() {
        setService(true);
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

    private ViewSettingsBinding settingsBinding;
    private ViewLogsBinding logsBinding;

    private void initPager() {
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        binding.pager.setAdapter(adapter);
        settingsBinding = (ViewSettingsBinding) adapter.getViewBindings().get(0);
        logsBinding = (ViewLogsBinding) adapter.getViewBindings().get(1);

        binding.pager.setOffscreenPageLimit(2);
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                isLogPanelVisible = position == 1;
                if (isLogPanelVisible) {
                    refreshLog();
                    startAutoRefreshLog();
                } else {
                    stopAutoRefreshLog();
                }

                if (position == 0) {
                    binding.bottomNav.setSelectedItemId(R.id.nav_settings);
                } else if (position == 1) {
                    binding.bottomNav.setSelectedItemId(R.id.nav_logs);
                }
            }
        });

        binding.bottomNav.setOnItemSelectedListener(item -> {

            if (item.getItemId() == R.id.nav_settings) {
                binding.pager.setCurrentItem(0);
                return true;
            }

            if (item.getItemId() == R.id.nav_logs) {
                binding.pager.setCurrentItem(1);
                return true;
            }

            return false;
        });

        binding.bottomNav.post(() -> {
            for (int i = 0; i < binding.bottomNav.getMenu().size(); i++) {
                MenuItem item = binding.bottomNav.getMenu().getItem(i);

                View view = binding.bottomNav.findViewById(item.getItemId());

                TooltipCompat.setTooltipText(view, null);
                view.setOnLongClickListener(v -> true);
            }
        });

    }

    @SuppressLint("ClickableViewAccessibility")
    private void initSettingsUi() {
        refreshServiceControlUi();
        settingsBinding.serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingServiceSwitch) {
                return;
            }
            setService(isChecked);
            if (isChecked) {
                refreshServiceStatus();
                startServiceForCurrentMode();
            } else {
                stopBrightnessService();
            }
        });

        settingsBinding.autoRestoreSwitch.setChecked(appSettings.isAutoRestoreEnabled());
        settingsBinding.autoRestoreSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettings.setAutoRestoreEnabled(isChecked);
            if (!isChecked) {
                appSettings.clearRecordedData();
            }
        });

        String[] modeItems = {
                ShellExecutor.Mode.SHIZUKU.name(),
                ShellExecutor.Mode.ROOT.name()
        };

        ArrayAdapter<String> modeAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.dropdown_list_item,
                        modeItems);

        ListPopupWindow modePopup = new ListPopupWindow(this);
        modePopup.setAdapter(modeAdapter);
        modePopup.setAnchorView(settingsBinding.modeText);
        modePopup.setHorizontalOffset(dp2px(8));
        modePopup.setModal(true);
        modePopup.setWidth(dp2px(80));

        settingsBinding.modeBg.setOnClickListener(v -> modePopup.show());

        settingsBinding.modeText.setText(appSettings.getShellMode().name());

        modePopup.setOnItemClickListener((parent, view, position, id) -> {
            ShellExecutor.Mode mode = position == 1
                    ? ShellExecutor.Mode.ROOT
                    : ShellExecutor.Mode.SHIZUKU;
            appSettings.setShellMode(mode);
            settingsBinding.modeText.setText(mode.name());
            if (checkAbility()) refreshServiceStatus();
            modePopup.dismiss();
        });

        applyThresholdToUi(appSettings.getThresholdLux());
        settingsBinding.thresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                saveThreshold(value);
                applyThresholdToUi(value);
            }

        });
        settingsBinding.thresholdInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updatingThresholdUi) {
                    return;
                }
                // 滑动条实时更新，延迟应用
                String value = settingsBinding.thresholdInput.getText().toString().trim();
                float threshold = value.isEmpty() ? 0 : Float.parseFloat(value);
                settingsBinding.thresholdSlider.setValue(Math.min(2000, Math.round(threshold)));
                autoRefreshHandler.removeCallbacks(thresholdApplyRunnable);
                autoRefreshHandler.postDelayed(thresholdApplyRunnable, 3000);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        applyIntervalToUi(appSettings.getCheckInterval());
        settingsBinding.applyIntervalButton.setOnClickListener(v -> {
            if (updatingIntervalUi) {
                return;
            }
            String value = settingsBinding.intervalInput.getText().toString().trim();
            if (value.isEmpty()) {
                toast(R.string.interval_invalid);
                return;
            }
            try {
                long interval = Long.parseLong(value);
                saveInterval(interval);

                // Notify service to update interval
                if (isBrightnessServiceRunning()) {
                    Intent intent = new Intent(this, BrightnessService.class);
                    intent.setAction(BrightnessService.ACTION_UPDATE_INTERVAL);
                    startForegroundService(intent);
                }

                if (interval > 0) {
                    toast(getString(R.string.interval_check_toast, value));
                } else {
                    toast(R.string.interval_check_disabled_toast);
                }
            } catch (NumberFormatException ignored) {
                toast(R.string.interval_invalid);
            }
        });


        String[] restoreModeItems = {
                getString(R.string.auto_restore_mode_0),
                getString(R.string.auto_restore_mode_1),
        };

        ArrayAdapter<String> restoreModeAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.dropdown_list_item,
                        restoreModeItems);

        ListPopupWindow restorePopup = new ListPopupWindow(this);
        restorePopup.setAdapter(restoreModeAdapter);
        restorePopup.setAnchorView(settingsBinding.restoreModeText);
        restorePopup.setHorizontalOffset(dp2px(8));
        restorePopup.setModal(true);
        restorePopup.setWidth(dp2px(80));

        settingsBinding.restoreModeBg.setOnClickListener(v -> restorePopup.show());

        settingsBinding.restoreModeText.setText(restoreModeItems[appSettings.getAutoRestoreMode()]);

        restorePopup.setOnItemClickListener((parent, view, position, id) -> {
            appSettings.setAutoRestoreMode(position);
            settingsBinding.restoreModeText.setText(restoreModeItems[position]);
            settingsBinding.belowBg.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
            restorePopup.dismiss();
        });

        settingsBinding.belowBg.setVisibility(appSettings.getAutoRestoreMode() == 0 ? View.GONE : View.VISIBLE);
        settingsBinding.belowInput.setText(String.valueOf(appSettings.getAutoRestoreBelow()));
        settingsBinding.applyBelowButton.setOnClickListener(v -> {
            String value = settingsBinding.belowInput.getText().toString().trim();
            if (value.isEmpty()) {
                toast(R.string.interval_invalid);
                return;
            }
            try {
                int below = Integer.parseInt(value);
                appSettings.setAutoRestoreBelow(below);
                toast(R.string.set_successful);
            } catch (NumberFormatException ignored) {
                toast(R.string.interval_invalid);
            }
        });

        settingsBinding.requestNotificationBg.setOnClickListener(v -> {
            if (checkNotificationPermission()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        refreshNotificationStatus();

        if (OSUtils.isMiOS()) {
            settingsBinding.warning.setVisibility(View.VISIBLE);
            settingsBinding.warningText.setText(R.string.warning_miui);
            settingsBinding.warning.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName("com.miui.securitycenter",
                            "com.miui.powercenter.legacypowerrank.PowerDetailActivity"));
                    intent.putExtra("package_name", BuildConfig.APPLICATION_ID);
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        }

        settingsBinding.exitAndHideBg.setOnClickListener(v -> {
            finishAndRemoveTask();
        });
    }

    private void initLogUi() {
        logsBinding.refreshLogButton.setOnClickListener(v -> refreshLog());
        logsBinding.clearLogButton.setOnClickListener(v -> {
            appLog.clear();
            refreshLog();
        });
    }

    private void applyThresholdToUi(float threshold) {
        updatingThresholdUi = true;
        settingsBinding.thresholdSlider.setValue(Math.min(2000, Math.round(threshold)));
        settingsBinding.thresholdInput.setText(formatThreshold(threshold));
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

    private void applyIntervalToUi(long interval) {
        updatingIntervalUi = true;
        settingsBinding.intervalInput.setText(String.valueOf(interval));
        updatingIntervalUi = false;
    }

    private void saveInterval(long interval) {
        appSettings.setCheckInterval(interval);
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
            logsBinding.logText.setText(appLog.getDisplayText());
        }
    }

    private void refreshServiceControlUi() {
        if (binding == null || appSettings == null) {
            return;
        }
        updatingServiceSwitch = true;
        settingsBinding.serviceSwitch.setChecked(appSettings.isServiceEnabled());
        updatingServiceSwitch = false;
        refreshServiceStatus();
        refreshNotificationStatus();
    }

    private void refreshServiceStatus() {
        if (binding == null || appSettings == null) {
            return;
        }
        boolean running = isBrightnessServiceRunning();
        appSettings.setServiceRunning(running);
        int statusResId;
        int modeResId;
        if (!appSettings.isServiceEnabled()) {
            statusResId = R.string.service_status_disabled;
            modeResId = R.drawable.ic_error;
        } else if (running) {
            statusResId = R.string.service_status_running;
            modeResId = R.drawable.ic_check_circle;
        } else {
            statusResId = R.string.service_status_stopped;
            modeResId = R.drawable.ic_error;
        }
        settingsBinding.serviceModeText.setVisibility(modeResId == R.drawable.ic_error ? View.GONE : View.VISIBLE);
        settingsBinding.serviceStatusText.setText(getString(R.string.service_status_label, getString(statusResId)));
        settingsBinding.serviceModeText.setText(getString(R.string.service_mode_label, appSettings.getShellMode().name()));
        settingsBinding.serviceStatusIcon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), modeResId, getTheme()));
    }

    private void refreshNotificationStatus() {
        if (settingsBinding == null) return;
        boolean granted = checkNotificationPermission();
        settingsBinding.notificationText.setText(granted ?
                R.string.notification_permission_granted_title :
                R.string.notification_permission_request_title);
        settingsBinding.notificationDesc.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private boolean checkNotificationPermission() {
        boolean granted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            granted = true;
        }
        return granted;
    }

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

    private void setService(boolean enabled) {
        if (enabled) {
            appSettings.setServiceEnabled(true);
        } else {
            appSettings.setServiceEnabled(false);
            stopBrightnessService();
        }
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

    public int dp2px(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

}
