package com.onedongua.smartbrightness.service;

import static com.onedongua.smartbrightness.executor.Result.collectResult;

import android.util.Log;

import com.onedongua.smartbrightness.BuildConfig;
import com.onedongua.smartbrightness.IUserService;
import com.onedongua.smartbrightness.executor.Result;

import java.io.IOException;

public class UserService extends IUserService.Stub {

    /**
     * Constructor is required.
     */
    public UserService() {
        if (BuildConfig.DEBUG) Log.d("UserService", "constructor");
    }

    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        if (BuildConfig.DEBUG) Log.d("UserService", "destroy");
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public Result execute(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            if (process == null) throw new IOException("Failed to create process");
            return collectResult(process);
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }
}