package com.platypii.baselinexr.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Analytics {

    @SuppressLint("MissingPermission")
    public static void logEvent(@Nullable Context context, @NonNull String eventName, @Nullable Bundle bundle) {
        Log.i("Analytics", eventName + " " + bundle);
    }
}
