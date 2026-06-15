package com.onedongua.smartbrightness.executor;

import static com.onedongua.smartbrightness.executor.Result.collectResult;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.onedongua.smartbrightness.BuildConfig;
import com.onedongua.smartbrightness.IUserService;
import com.onedongua.smartbrightness.service.UserService;
import com.onedongua.smartbrightness.settings.AppSettings;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public class ShellExecutor {
    private static final String TAG = "ShellExecutor";
    private final AppSettings appSettings;
    private volatile IUserService service;
    private final CountDownLatch connectLatch = new CountDownLatch(1);
    private volatile boolean connectAttempted;

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            if (binder != null && binder.pingBinder()) {
                service = IUserService.Stub.asInterface(binder);
                connectLatch.countDown();
            } else {
                Log.e(TAG, "onServiceConnected: binder has been killed");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            service = null;
        }
    };

    private final Shizuku.UserServiceArgs userServiceArgs =
            new Shizuku.UserServiceArgs(
                    new ComponentName(
                            BuildConfig.APPLICATION_ID,
                            UserService.class.getName()
                    ))
                    .daemon(false)
                    .processNameSuffix("service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);

    private void bindUserService() {
        try {
            if (Shizuku.getVersion() >= 10 && Shizuku.pingBinder()) {
                Shizuku.bindUserService(userServiceArgs, userServiceConnection);
            }
        } catch (Throwable tr) {
            Log.e(TAG, "bindUserService: ", tr);
        }
    }

    private void unbindUserService() {
        try {
            if (Shizuku.getVersion() >= 10) {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true);
            }
        } catch (Throwable tr) {
            Log.e(TAG, "unbindUserService: ", tr);
        }
    }

    public ShellExecutor(Context context) {
        appSettings = new AppSettings(context);
        bindUserService();
    }

    public Result execute(String command) {
        Mode mode = appSettings.getShellMode();
        if (mode == Mode.ROOT) {
            return executeWithRoot(command);
        }
        return executeWithShizuku(command);
    }

    private Result executeWithShizuku(String command) {
        try {
            if (!Shizuku.pingBinder()
                    || Shizuku.isPreV11()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return Result.failure("Shizuku permission unavailable");
            }

            // Fast path: service already connected
            IUserService svc = service;
            if (svc != null) {
                return svc.execute(command);
            }

            // First call: wait for the bind initiated in constructor to complete
            if (!connectAttempted) {
                connectAttempted = true;
                try {
                    connectLatch.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.failure("Interrupted while waiting for UserService");
                }
            }

            svc = service;
            if (svc != null) {
                return svc.execute(command);
            }
            return Result.failure("UserService not connected");
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    private Result executeWithRoot(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).start();
            return collectResult(process);
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public void destroy() {
        IUserService svc = service;
        unbindUserService();
        if (svc != null) {
            try {
                svc.destroy();
            } catch (RemoteException e) {
                Log.e(TAG, "destroy: ", e);
            }
        }
    }

    public enum Mode {
        SHIZUKU,
        ROOT
    }
}
