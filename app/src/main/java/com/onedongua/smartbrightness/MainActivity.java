package com.onedongua.smartbrightness;

import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.onedongua.smartbrightness.databinding.ActivityMainBinding;
import com.onedongua.smartbrightness.service.BrightnessService;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSION_SHIZUKU = 1;
    private ActivityMainBinding binding;
    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionsResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        adaptInsets();

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        checkPermission(REQUEST_CODE_PERMISSION_SHIZUKU);
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
            toast(R.string.permission_granted);
            startBrightnessService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }

    private boolean checkPermission(int code) {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            return false;
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            toast(R.string.permission_granted);
            startBrightnessService();
            return true;
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            return false;
        } else {
            // Request the permission
            Shizuku.requestPermission(code);
            return false;
        }
    }

    private void startBrightnessService() {
        Intent intent = new Intent(this, BrightnessService.class);
        startForegroundService(intent);
    }


    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public void toast(@StringRes int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

}
