package com.platypii.baselinexr.util;

import android.util.Log;

import androidx.annotation.NonNull;

public class Exceptions {
    private static final String TAG = "Exceptions";

    public static void report(@NonNull Throwable e) {
        Log.e(TAG, "Crash report exception", e);
    }

    public static void log(@NonNull String msg) {
        Log.e(TAG, "Log message" + msg);
    }

}
