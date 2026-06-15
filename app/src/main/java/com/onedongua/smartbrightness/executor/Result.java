package com.onedongua.smartbrightness.executor;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Result implements Parcelable {
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

    protected Result(Parcel in) {
        exitCode = in.readInt();
        stdout = in.readString();
        stderr = in.readString();
        success = exitCode == 0;
    }

    public static final Creator<Result> CREATOR = new Creator<Result>() {
        @Override
        public Result createFromParcel(Parcel in) {
            return new Result(in);
        }

        @Override
        public Result[] newArray(int size) {
            return new Result[size];
        }
    };

    public static Result failure(String stderr) {
        return new Result(-1, "", stderr == null ? "" : stderr);
    }

    public static Result collectResult(Process process) throws IOException, InterruptedException {
        int exitCode = process.waitFor();
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        return new Result(exitCode, stdout, stderr);
    }

    public static String readAll(InputStream stream) throws IOException {
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(exitCode);
        dest.writeString(stdout);
        dest.writeString(stderr);
    }
}
