package com.jonnysundae.verifiqueosfatos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (AppPrefs.isMonitoringActive(context)) {
                Intent serviceIntent = new Intent(context, FactCheckService.class);
                serviceIntent.setAction(FactCheckService.ACTION_START);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
