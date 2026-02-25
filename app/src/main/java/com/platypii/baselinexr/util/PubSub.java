package com.platypii.baselinexr.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Publish/subscribe with background and main thread awareness
 */
public class PubSub<T> {
    private static final String TAG = "PubSub";

    // Shared single-thread executor for async posts - avoids creating a new thread per message
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PubSub-Async");
        t.setDaemon(true);
        return t;
    });

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
     * Post in a background thread so that the caller doesn't block.
     * Uses a shared single-thread executor to avoid creating a new thread per message.
     */
    public void postAsync(T obj) {
        executor.execute(() -> post(obj));
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
                Log.e(TAG, "Unexpected main listener unsubscribed " + sub);
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
