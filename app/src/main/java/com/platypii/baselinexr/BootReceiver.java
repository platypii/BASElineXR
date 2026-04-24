package com.platypii.baselinexr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Fires once after each cold boot and foregrounds BaselineActivity. Requires:
 *  - <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 *  - Register this receiver for the BOOT_COMPLETED intent in AndroidManifest.xml
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.e("BaselineBoot", "Auto-starting baseline on boot");
            final Intent launch = new Intent(context, BaselineActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        }
    }
}
