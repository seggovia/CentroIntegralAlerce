package com.centroalerce.gestion.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "SessionPrefs";
    private static final String KEY_LAST_ACTIVITY_TIME = "last_activity_time";

    // TIEMPO CAMBIABLE XAVAL
    //private static final long SESSION_TIMEOUT = 15 * 1000; // 15 segundos para pruebas
    private static final long SESSION_TIMEOUT = 5 * 60 * 1000; // 5 minutos para producción

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Guarda el timestamp cuando la app va a background
     */
    public void saveBackgroundTime() {
        prefs.edit()
                .putLong(KEY_LAST_ACTIVITY_TIME, System.currentTimeMillis())
                .apply();
    }

    /**
     * Verifica si la sesión ha expirado
     * @return true si pasaron más de 15 segundos (o 5 minutos en producción)
     */
    public boolean isSessionExpired() {
        long lastActivityTime = prefs.getLong(KEY_LAST_ACTIVITY_TIME, 0);

        if (lastActivityTime == 0) {
            return false; // Primera vez, no hay tiempo guardado
        }

        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - lastActivityTime;

        return timeDifference > SESSION_TIMEOUT;
    }

    /**
     * Limpia el tiempo guardado
     */
    public void clearSession() {
        prefs.edit().remove(KEY_LAST_ACTIVITY_TIME).apply();
    }
}