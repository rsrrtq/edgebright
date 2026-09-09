package com.edgebright;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferencesHolder prefs = new SharedPreferencesHolder(context);
            if (!prefs.isEnabled()) {
                return;
            }
            Intent service = new Intent(context, EdgeService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        }
    }

    static class SharedPreferencesHolder {
        private final android.content.SharedPreferences prefs;

        SharedPreferencesHolder(Context context) {
            prefs = context.getSharedPreferences(EdgeService.PREFS, Context.MODE_PRIVATE);
        }

        boolean isEnabled() {
            return prefs.getBoolean(EdgeService.KEY_ENABLED, true);
        }
    }
}
