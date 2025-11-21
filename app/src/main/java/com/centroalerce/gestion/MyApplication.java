package com.centroalerce.gestion;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.centroalerce.gestion.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class MyApplication extends Application implements DefaultLifecycleObserver {

    private static final String TAG = "MyApplication";
    private SessionManager sessionManager;
    private boolean shouldCheckSession = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Inicializar el SessionManager
        sessionManager = new SessionManager(this);

        // Registrar el observer del ciclo de vida
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        Log.d(TAG, "✅ MyApplication inicializada con observer de sesión");
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        // La app va a segundo plano
        Log.d(TAG, "📱 App va a background - Guardando timestamp");

        // Solo guardar si hay un usuario autenticado
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            sessionManager.saveBackgroundTime();
            shouldCheckSession = true;
            Log.d(TAG, "⏰ Timestamp guardado. Sesión expirará en 15 segundos");
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // La app vuelve a primer plano
        Log.d(TAG, "📱 App vuelve a foreground - Verificando sesión");

        // Solo verificar si marcamos que debemos hacerlo
        if (shouldCheckSession && FirebaseAuth.getInstance().getCurrentUser() != null) {

            if (sessionManager.isSessionExpired()) {
                Log.d(TAG, "⏰ Sesión expirada (pasaron más de 15 segundos) - Cerrando sesión");

                // Cerrar sesión en Firebase
                FirebaseAuth.getInstance().signOut();

                // Limpiar el timestamp
                sessionManager.clearSession();

                // Notificar a MainActivity que debe mostrar el diálogo
                Intent intent = new Intent("SESSION_EXPIRED");
                sendBroadcast(intent);

            } else {
                Log.d(TAG, "✅ Sesión válida - Continuando (no pasaron 15 segundos)");
            }

            shouldCheckSession = false;
        }
    }
}