package com.centroalerce.gestion.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.repositories.NotificacionRepository;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Servicio de fondo para procesar notificaciones automáticas
 */
public class NotificationBackgroundService extends Service {
    
    private static final String TAG = "NotificationBgService";
    private static final long INTERVALO_VERIFICACION = 60 * 1000; // 1 minuto (pruebas)

    private static final String CHANNEL_ID = "notification_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private Timer timer;
    private NotificationService notificationService;
    private NotificacionRepository notificacionRepository;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio de notificaciones iniciado");
        
        notificationService = new NotificationService(this);
        notificacionRepository = new NotificacionRepository();
        
        // Crear canal de notificación para servicio foreground
        createNotificationChannel();
        
        // Iniciar timer para verificar notificaciones periódicamente
        iniciarTimerVerificacion();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Servicio iniciado con comando: " + (intent != null ? intent.getAction() : "null"));
        
        // Iniciar como servicio foreground
        startForeground(NOTIFICATION_ID, createForegroundNotification());
        
        // Procesar notificaciones inmediatamente al iniciar
        procesarNotificacionesPendientes();
        
        // Mantener el servicio activo
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Servicio no vinculado
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Servicio de notificaciones detenido");
        
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
    /**
     * Inicia el timer para verificar notificaciones periódicamente
     */
    private void iniciarTimerVerificacion() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "Verificando notificaciones pendientes...");
                procesarNotificacionesPendientes();
            }
        }, 0, INTERVALO_VERIFICACION);
    }
    
    /**
     * Procesa todas las notificaciones pendientes
     */
    private void procesarNotificacionesPendientes() {
        Timestamp ahora = Timestamp.now();
        
        notificacionRepository.obtenerNotificacionesPendientes(ahora, new NotificacionRepository.NotificacionesCallback() {
            @Override
            public void onSuccess(List<com.centroalerce.gestion.models.Notificacion> notificaciones) {
                
                if (!notificaciones.isEmpty()) {
                    Log.d(TAG, "Procesando " + notificaciones.size() + " notificaciones pendientes");
                    
                    for (com.centroalerce.gestion.models.Notificacion notificacion : notificaciones) {
                        procesarNotificacionIndividual(notificacion);
                    }
                } else {
                    Log.d(TAG, "No hay notificaciones pendientes");
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error al obtener notificaciones pendientes: " + error);
            }
        });
    }
    
    /**
     * Procesa una notificación individual
     */
    private void procesarNotificacionIndividual(com.centroalerce.gestion.models.Notificacion notificacion) {
        Log.d(TAG, "Procesando notificación: " + notificacion.getId());
        
        // Enviar notificación push local
        notificationService.enviarNotificacionInmediata(
            "Recordatorio de Actividad",
            notificacion.getMensaje(),
            notificacion.getId().hashCode()
        );
        
        // Marcar como leída/procesada
        notificacion.setLeida(true);
        notificacionRepository.actualizarNotificacion(notificacion, new NotificacionRepository.NotificacionCallback() {
            @Override
            public void onSuccess(com.centroalerce.gestion.models.Notificacion notificacion) {
                Log.d(TAG, "Notificación marcada como procesada: " + notificacion.getId());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error al marcar notificación como procesada: " + error);
            }
        });
    }
    
    /**
     * Programa una nueva notificación
     */
    public void programarNotificacion(com.centroalerce.gestion.models.Notificacion notificacion) {
        notificacionRepository.crearNotificacion(notificacion, new NotificacionRepository.NotificacionCallback() {
            @Override
            public void onSuccess(com.centroalerce.gestion.models.Notificacion notificacion) {
                Log.d(TAG, "Notificación programada exitosamente: " + notificacion.getId());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error al programar notificación: " + error);
            }
        });
    }
    
    /**
     * Cancela notificaciones de una cita específica
     */
    public void cancelarNotificacionesCita(String citaId) {
        notificacionRepository.eliminarNotificacionesCita(citaId, new NotificacionRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Notificaciones de cita canceladas: " + citaId);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error al cancelar notificaciones de cita: " + error);
            }
        });
    }
    
    /**
     * Crea el canal de notificación para el servicio foreground
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Servicio de Notificaciones",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Servicio en segundo plano para procesar notificaciones automáticas");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Crea la notificación para el servicio foreground
     */
    private Notification createForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Centro Integral Alerce")
            .setContentText("Procesando notificaciones automáticas")
            .setSmallIcon(R.drawable.ic_bell_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }
}

