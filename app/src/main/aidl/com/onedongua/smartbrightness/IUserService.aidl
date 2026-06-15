// IUserService.aidl
package com.onedongua.smartbrightness;

import com.onedongua.smartbrightness.executor.Result;

// Declare any non-default types here with import statements

interface IUserService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    Result execute(String command) = 2;
}