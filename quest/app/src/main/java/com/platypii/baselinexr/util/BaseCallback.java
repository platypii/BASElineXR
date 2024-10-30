package com.platypii.baselinexr.util;

import androidx.annotation.NonNull;

public interface BaseCallback<T> {
    void onSuccess(@NonNull T result);
    void onFailure(@NonNull Throwable ex);
}
