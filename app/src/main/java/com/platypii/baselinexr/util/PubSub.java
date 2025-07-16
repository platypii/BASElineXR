package com.platypii.baselinexr.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Publish/subscribe with background and main thread awareness
 */
public class PubSub<T> {
    private static final String TAG = "PubSub";

    @NonNull
    private final List<Subscriber<T>> subs = new ArrayList<>();
    @NonNull
    private final List<Subscriber<T>> mainSubs = new ArrayList<>();

    @NonNull
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void post(T obj) {
        synchronized (subs) {
            for (Subscriber<T> sub : subs) {
                sub.apply(obj);
            }
        }
        // Run on UI thread
        if (!mainSubs.isEmpty()) {
            handler.post(() -> {
                synchronized (mainSubs) {
                    for (Subscriber<T> sub : mainSubs) {
                        sub.apply(obj);
                    }
                }
            });
        }
    }

    /**
     * Post in a thread so that the caller doesn't block
     */
    public void postAsync(T obj) {
        new Thread(() -> post(obj)).start();
    }

    public void subscribe(@NonNull Subscriber<T> sub) {
        synchronized (subs) {
            subs.add(sub);
        }
    }

    public void subscribeMain(@NonNull Subscriber<T> sub) {
        synchronized (mainSubs) {
            mainSubs.add(sub);
        }
    }

    public void unsubscribe(@NonNull Subscriber<T> sub) {
        synchronized (subs) {
            if (!subs.remove(sub)) {
                Log.e(TAG, "Unexpected listener unsubscribed");
            }
        }
    }

    public void unsubscribeMain(@NonNull Subscriber<T> sub) {
        synchronized (mainSubs) {
            if (!mainSubs.remove(sub)) {
                Log.e(TAG, "Unexpected main listener unsubscribed");
            }
        }
    }

    /**
     * Return true if there are no subscribers
     */
    public boolean isEmpty() {
        return subs.isEmpty() && mainSubs.isEmpty();
    }

    public interface Subscriber<S> {
        void apply(S obj);
    }
}
