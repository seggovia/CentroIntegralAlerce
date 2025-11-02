package com.centroalerce.gestion.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.centroalerce.gestion.MainActivity;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Actividad;
import com.centroalerce.gestion.models.Cita;
import com.centroalerce.gestion.models.Notificacion;
import com.centroalerce.gestion.repositories.NotificacionRepository;
import com.centroalerce.gestion.utils.Constantes;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Servicio para manejar notificaciones automáticas del sistema
 */
public class NotificationService {

    private static final String TAG = "NotificationService";
    // NUEVO: usa un ID de canal distinto para forzar recreación con IMPORTANCE_HIGH
    private static final String CHANNEL_ID = "actividades_alertas_v2"; // <— antes: "actividades_channel"
    private static final String CHANNEL_NAME = "Notificaciones de Actividades";
    private static final String CHANNEL_DESCRIPTION = "Alertas sobre actividades programadas";

    private final Context context;
    private final NotificacionRepository notificacionRepository;

    public NotificationService(Context context) {
        this.context = context;
        this.notificacionRepository = new NotificacionRepository();
        createNotificationChannel();
    }

    /**
     * Crea el canal de notificaciones para Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH // NUEVO: alto para heads-up
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC); // NUEVO
            channel.setVibrationPattern(new long[]{0, 300, 200, 300}); // NUEVO (opcional)

            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Programa notificaciones para una actividad cuando se crea
     */
    public void programarNotificacionesActividad(Actividad actividad, List<String> usuariosNotificar) {
        if (actividad == null || usuariosNotificar == null || usuariosNotificar.isEmpty()) {
            Log.w(TAG, "No se pueden programar notificaciones: datos inválidos");
            return;
        }

        Log.d(TAG, "Programando notificaciones para actividad: " + actividad.getNombre());

        // Para actividades puntuales, programar notificación basada en diasAvisoPrevio
        if (Constantes.PERIODICIDAD_PUNTUAL.equals(actividad.getPeriodicidad())) {
            // Las actividades puntuales necesitan una cita para tener fecha específica
            Log.i(TAG, "Actividad puntual detectada - las notificaciones se programarán al crear la cita");
            return;
        }

        // Para actividades periódicas, programar notificaciones basadas en fechaInicio
        if (Constantes.PERIODICIDAD_PERIODICA.equals(actividad.getPeriodicidad()) &&
                actividad.getFechaInicio() != null) {

            programarNotificacionPeriodica(actividad, usuariosNotificar);
        }
    }

    /**
     * Programa notificaciones para una cita específica
     */
    public void programarNotificacionesCita(Cita cita, Actividad actividad, List<String> usuariosNotificar) {
        if (cita == null || actividad == null || usuariosNotificar == null || usuariosNotificar.isEmpty()) {
            Log.w(TAG, "No se pueden programar notificaciones: datos inválidos");
            return;
        }

        Log.d(TAG, "Programando notificaciones para cita: " + cita.getId());

        // Calcular fecha de notificación basada en diasAvisoPrevio
        Timestamp fechaNotificacion = calcularFechaNotificacion(cita.getFecha(), actividad.getDiasAvisoPrevio());

        if (fechaNotificacion != null) {
            crearNotificacionRecordatorio(cita, actividad, fechaNotificacion, usuariosNotificar);
        }
    }

    /**
     * Programa notificación para actividad periódica
     */
    private void programarNotificacionPeriodica(Actividad actividad, List<String> usuariosNotificar) {
        Timestamp fechaNotificacion = calcularFechaNotificacion(
                actividad.getFechaInicio(),
                actividad.getDiasAvisoPrevio()
        );

        if (fechaNotificacion != null) {
            // Crear notificación para actividad periódica
            Notificacion notificacion = new Notificacion();
            notificacion.setTipo(Constantes.NOTIF_RECORDATORIO);
            notificacion.setActividadNombre(actividad.getNombre());
            notificacion.setFecha(fechaNotificacion);
            notificacion.setMensaje(generarMensajeRecordatorio(actividad, fechaNotificacion));
            notificacion.setLeida(false);
            notificacion.setFechaCreacion(Timestamp.now());
            notificacion.setUsuariosNotificados(usuariosNotificar);

            // Guardar en base de datos
            notificacionRepository.crearNotificacion(notificacion, new NotificacionRepository.NotificacionCallback() {
                @Override
                public void onSuccess(Notificacion notificacion) {
                    Log.d(TAG, "Notificación periódica programada exitosamente");
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error al programar notificación periódica: " + error);
                }
            });
        }
    }

    /**
     * Crea notificación de recordatorio para una cita
     */
    private void crearNotificacionRecordatorio(Cita cita, Actividad actividad,
                                               Timestamp fechaNotificacion, List<String> usuariosNotificar) {
        Notificacion notificacion = new Notificacion();
        notificacion.setTipo(Constantes.NOTIF_RECORDATORIO);
        notificacion.setCitaId(cita.getId());
        notificacion.setActividadNombre(actividad.getNombre());
        notificacion.setFecha(fechaNotificacion);
        notificacion.setMensaje(generarMensajeRecordatorio(actividad, fechaNotificacion));
        notificacion.setLeida(false);
        notificacion.setFechaCreacion(Timestamp.now());
        notificacion.setUsuariosNotificados(usuariosNotificar);

        // Guardar en base de datos
        notificacionRepository.crearNotificacion(notificacion, new NotificacionRepository.NotificacionCallback() {
            @Override
            public void onSuccess(Notificacion notificacion) {
                Log.d(TAG, "Notificación de recordatorio programada exitosamente");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error al programar notificación de recordatorio: " + error);
            }
        });
    }

    /**
     * Calcula la fecha de notificación basada en la fecha de la actividad y días de aviso previo
     */
    private Timestamp calcularFechaNotificacion(Timestamp fechaActividad, int diasAvisoPrevio) {
        if (fechaActividad == null || diasAvisoPrevio < 0) {
            return null;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(fechaActividad.toDate());

        // Restar los días de aviso previo configurados por el usuario
        calendar.add(Calendar.DAY_OF_MONTH, -diasAvisoPrevio);

        // Si la fecha calculada ya pasó, no programar notificación
        Date ahora = new Date();
        if (!calendar.getTime().after(ahora)) {
            Log.w(TAG, "Fecha de notificación ya pasó. No se programará notificación para esta actividad.");
            return null; // No programar notificaciones para fechas pasadas
        }

        return new Timestamp(calendar.getTime());
    }

    /**
     * Genera el mensaje de recordatorio personalizado
     */
    private String generarMensajeRecordatorio(Actividad actividad, Timestamp fechaNotificacion) {
        return String.format(
                "Recordatorio: La actividad '%s' está programada para %s. ¡No olvides asistir!",
                actividad.getNombre(),
                formatFecha(fechaNotificacion)
        );
    }

    /**
     * Formatea una fecha para mostrar en el mensaje
     */
    private String formatFecha(Timestamp timestamp) {
        if (timestamp == null) return "";

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(timestamp.toDate());

        return String.format("%02d/%02d/%04d a las %02d:%02d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
        );
    }

    /**
     * Envía notificación push local inmediata
     */
    public void enviarNotificacionInmediata(String titulo, String mensaje, int notificationId) {
        // NUEVO: checar permiso en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS no concedido; no se puede notificar.");
                return;
            }
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell_24)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje)) // NUEVO: texto expandido
                .setPriority(NotificationCompat.PRIORITY_MAX) // NUEVO: heads-up en <26
                .setCategory(NotificationCompat.CATEGORY_REMINDER) // NUEVO
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // NUEVO
                .setDefaults(NotificationCompat.DEFAULT_ALL) // NUEVO: sonido/vibración/luz por defecto
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Notificación enviada: " + titulo);
        } catch (SecurityException e) {
            Log.e(TAG, "Error al enviar notificación: " + e.getMessage());
        }
    }

    /**
     * Cancela notificaciones programadas para una cita específica
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
     * Procesa notificaciones pendientes (llamar periódicamente)
     */
    public void procesarNotificacionesPendientes() {
        Timestamp ahora = Timestamp.now();

        notificacionRepository.obtenerNotificacionesPendientes(ahora, new NotificacionRepository.NotificacionesCallback() {
            @Override
            public void onSuccess(List<Notificacion> notificaciones) {
                for (Notificacion notificacion : notificaciones) {
                    enviarNotificacionInmediata(
                            "Recordatorio de Actividad",
                            notificacion.getMensaje(),
                            notificacion.getId().hashCode()
                    );

                    // Marcar como procesada
                    notificacion.setLeida(true);
                    notificacionRepository.actualizarNotificacion(notificacion, new NotificacionRepository.NotificacionCallback() {
                        @Override
                        public void onSuccess(Notificacion notificacion) {
                            Log.d(TAG, "Notificación marcada como procesada");
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Error al marcar notificación como procesada: " + error);
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error al obtener notificaciones pendientes: " + error);
            }
        });
    }
}
