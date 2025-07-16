package com.platypii.baselinexr.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {
    private static final String TAG = "MD5";

    /**
     * Compute MD5 hash of a file.
     * Does not throw exceptions, just returns null.
     */
    @Nullable
    public static String md5(@NonNull File file) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final InputStream inputStream = new DigestInputStream(new FileInputStream(file), md);
            final byte[] buffer = new byte[1024];
            while (inputStream.read(buffer) != -1) {
                // Do nothing
            }
            inputStream.close();
            // Format digest as hex
            return String.format("%1$032x", new BigInteger(1, md.digest()));
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to compute MD5", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file", e);
            return null;
        }
    }

}
