package com.platypii.baselinexr.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a local cache of a REST object store in the cloud.
 * Stored in shared preferences as JSON.
 * Also manages request TTL.
 *
 * @param <T> the java type of the items
 */
public abstract class LocalCache<T> {

    // Preference keys
    @NonNull
    private final String CACHE_LAST_REQUEST;
    @NonNull
    private final String CACHE_LAST_UPDATE;
    @NonNull
    private final String CACHE_LIST;

    private List<T> memCache = null;

    // Minimum time between requests
    private static final long REQUEST_TTL = 30 * 1000; // 30 seconds
    // Maximum lifetime of a successful listing
    private static final long UPDATE_TTL = 5 * 60 * 1000; // 5 minutes

    private SharedPreferences prefs;

    public LocalCache(@NonNull String keyPrefix) {
        CACHE_LAST_REQUEST = keyPrefix + ".list.request_time";
        CACHE_LAST_UPDATE = keyPrefix + ".list.update_time";
        CACHE_LIST = keyPrefix + ".list";
    }

    public void start(@NonNull Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Return Type of List<T>
     */
    @NonNull
    public abstract Type listType();

    /**
     * Return the unique id for an item
     */
    @NonNull
    public abstract String getId(@NonNull T item);

    /**
     * Return listing from local cache, does NOT request from server, always returns fast.
     */
    @Nullable
    public List<T> list() {
        if (memCache != null) {
            return memCache;
        }
        if (prefs != null) {
            final String jsonString = prefs.getString(CACHE_LIST, null);
            if (jsonString != null) {
                try {
                    memCache = new Gson().fromJson(jsonString, listType());
                    return memCache;
                } catch (JsonSyntaxException e) {
                    Exceptions.report(e);
                }
            }
        }
        return null;
    }

    /**
     * Return a cache item by id
     */
    @Nullable
    public T get(@NonNull String id) {
        final List<T> items = list();
        if (items != null) {
            for (T item : items) {
                if (getId(item).equals(id)) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * Add to listing, and save to preferences
     */
    public void add(@NonNull T item) {
        List<T> items = list();
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(0, item);
        update(items);
    }

    /**
     * Remove item from listing, and save to preferences
     */
    public void remove(@NonNull T item) {
        final List<T> items = list();
        if (items != null) {
            if (items.remove(item)) {
                update(items);
            }
        }
    }

    /**
     * Update the last request time with the current time
     */
    public void request() {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(CACHE_LAST_REQUEST, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * Set the listing cache, and set last update time
     */
    public void update(@NonNull List<T> items) {
        try {
            final String json = new Gson().toJson(items);
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(CACHE_LAST_UPDATE, System.currentTimeMillis());
            editor.putString(CACHE_LIST, json);
            editor.apply();
            memCache = items;
        } catch (Exception e) {
            Exceptions.report(e); // Usually JSON serialization error
        }
    }

    /**
     * Return true if it's time to send a new request
     */
    public boolean shouldRequest() {
        // Compute time since last update
        final long lastUpdateDuration = System.currentTimeMillis() - prefs.getLong(CACHE_LAST_UPDATE, 0);
        final long lastRequestDuration = System.currentTimeMillis() - prefs.getLong(CACHE_LAST_REQUEST, 0);
        // Check that data is expired, and we haven't requested recently
        return UPDATE_TTL < lastUpdateDuration && REQUEST_TTL < lastRequestDuration;
    }

    /**
     * Clear cache list and update times (when user signs out)
     */
    public void clear() {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.remove(CACHE_LAST_REQUEST);
        editor.remove(CACHE_LAST_UPDATE);
        editor.remove(CACHE_LIST);
        editor.apply();
        memCache = null;
    }
}
