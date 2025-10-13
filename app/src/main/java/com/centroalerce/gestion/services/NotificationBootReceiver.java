package com.centroalerce.gestion.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Build;
/**
 * Receptor para iniciar el servicio de notificaciones al arrancar el dispositivo
 */
public class NotificationBootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "NotificationBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Recibido broadcast: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {

            Log.d(TAG, "Iniciando servicio de notificaciones después del arranque");
            Intent serviceIntent = new Intent(context, NotificationBackgroundService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent); // API 26+
            } else {
                context.startService(serviceIntent);          // API 24–25
            }
        }
    }

}

