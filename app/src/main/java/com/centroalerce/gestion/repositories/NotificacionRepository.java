package com.centroalerce.gestion.repositories;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.centroalerce.gestion.models.Notificacion;
import com.centroalerce.gestion.utils.Constantes;

import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio para gestión de notificaciones en Firestore
 */
public class NotificacionRepository {
    
    private static final String TAG = "NotificacionRepository";
    private final FirebaseFirestore db;
    
    public NotificacionRepository() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    /**
     * Crea una nueva notificación en Firestore
     */
    public void crearNotificacion(Notificacion notificacion, NotificacionCallback callback) {
        if (notificacion == null) {
            callback.onError("La notificación no puede ser nula");
            return;
        }
        
        // Generar ID automático si no existe
        if (notificacion.getId() == null || notificacion.getId().isEmpty()) {
            DocumentReference docRef = db.collection(Constantes.COLLECTION_NOTIFICACIONES).document();
            notificacion.setId(docRef.getId());
        }
        
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .document(notificacion.getId())
            .set(notificacion)
            .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(TAG, "Notificación creada exitosamente: " + notificacion.getId());
                    callback.onSuccess(notificacion);
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Error al crear notificación", e);
                    callback.onError("Error al crear notificación: " + e.getMessage());
                }
            });
    }
    
    /**
     * Obtiene todas las notificaciones de un usuario específico
     */
    public void obtenerNotificacionesUsuario(String usuarioId, NotificacionesCallback callback) {
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .whereArrayContains("usuariosNotificados", usuarioId)
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(Task<QuerySnapshot> task) {
                    if (task.isSuccessful()) {
                        List<Notificacion> notificaciones = new ArrayList<>();
                        for (DocumentSnapshot document : task.getResult()) {
                            Notificacion notificacion = document.toObject(Notificacion.class);
                            if (notificacion != null) {
                                notificacion.setId(document.getId());
                                notificaciones.add(notificacion);
                            }
                        }
                        Log.d(TAG, "Notificaciones obtenidas: " + notificaciones.size());
                        callback.onSuccess(notificaciones);
                    } else {
                        Log.e(TAG, "Error al obtener notificaciones", task.getException());
                        callback.onError("Error al obtener notificaciones: " + task.getException().getMessage());
                    }
                }
            });
    }
    
    /**
     * Obtiene notificaciones pendientes (no leídas y con fecha próxima)
     */
    public void obtenerNotificacionesPendientes(Timestamp fechaLimite, NotificacionesCallback callback) {
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .whereEqualTo("leida", false)
            .whereLessThanOrEqualTo("fecha", fechaLimite)
            .orderBy("fecha", Query.Direction.ASCENDING)
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(Task<QuerySnapshot> task) {
                    if (task.isSuccessful()) {
                        List<Notificacion> notificaciones = new ArrayList<>();
                        for (DocumentSnapshot document : task.getResult()) {
                            Notificacion notificacion = document.toObject(Notificacion.class);
                            if (notificacion != null) {
                                notificacion.setId(document.getId());
                                notificaciones.add(notificacion);
                            }
                        }
                        Log.d(TAG, "Notificaciones pendientes obtenidas: " + notificaciones.size());
                        callback.onSuccess(notificaciones);
                    } else {
                        Log.e(TAG, "Error al obtener notificaciones pendientes", task.getException());
                        callback.onError("Error al obtener notificaciones pendientes: " + task.getException().getMessage());
                    }
                }
            });
    }
    
    /**
     * Obtiene notificaciones asociadas a una cita específica
     */
    public void obtenerNotificacionesPorCita(String citaId, NotificacionesCallback callback) {
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .whereEqualTo("citaId", citaId)
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(Task<QuerySnapshot> task) {
                    if (task.isSuccessful()) {
                        List<Notificacion> notificaciones = new ArrayList<>();
                        for (DocumentSnapshot document : task.getResult()) {
                            Notificacion notificacion = document.toObject(Notificacion.class);
                            if (notificacion != null) {
                                notificacion.setId(document.getId());
                                notificaciones.add(notificacion);
                            }
                        }
                        Log.d(TAG, "Notificaciones de cita obtenidas: " + notificaciones.size());
                        callback.onSuccess(notificaciones);
                    } else {
                        Log.e(TAG, "Error al obtener notificaciones de cita", task.getException());
                        callback.onError("Error al obtener notificaciones de cita: " + task.getException().getMessage());
                    }
                }
            });
    }
    
    /**
     * Actualiza una notificación existente
     */
    public void actualizarNotificacion(Notificacion notificacion, NotificacionCallback callback) {
        if (notificacion == null || notificacion.getId() == null || notificacion.getId().isEmpty()) {
            callback.onError("La notificación debe tener un ID válido");
            return;
        }
        
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .document(notificacion.getId())
            .set(notificacion)
            .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(TAG, "Notificación actualizada exitosamente: " + notificacion.getId());
                    callback.onSuccess(notificacion);
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Error al actualizar notificación", e);
                    callback.onError("Error al actualizar notificación: " + e.getMessage());
                }
            });
    }
    
    /**
     * Marca una notificación como leída
     */
    public void marcarComoLeida(String notificacionId, SimpleCallback callback) {
        if (notificacionId == null || notificacionId.isEmpty()) {
            callback.onError("ID de notificación inválido");
            return;
        }
        
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .document(notificacionId)
            .update("leida", true)
            .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(TAG, "Notificación marcada como leída: " + notificacionId);
                    callback.onSuccess();
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Error al marcar notificación como leída", e);
                    callback.onError("Error al marcar notificación como leída: " + e.getMessage());
                }
            });
    }
    
    /**
     * Elimina una notificación
     */
    public void eliminarNotificacion(String notificacionId, SimpleCallback callback) {
        if (notificacionId == null || notificacionId.isEmpty()) {
            callback.onError("ID de notificación inválido");
            return;
        }
        
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .document(notificacionId)
            .delete()
            .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(TAG, "Notificación eliminada exitosamente: " + notificacionId);
                    callback.onSuccess();
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Error al eliminar notificación", e);
                    callback.onError("Error al eliminar notificación: " + e.getMessage());
                }
            });
    }
    
    /**
     * Elimina todas las notificaciones de una cita específica
     */
    public void eliminarNotificacionesCita(String citaId, SimpleCallback callback) {
        obtenerNotificacionesPorCita(citaId, new NotificacionesCallback() {
            @Override
            public void onSuccess(List<Notificacion> notificaciones) {
                int totalEliminadas = 0;
                int totalErrores = 0;
                
                for (Notificacion notificacion : notificaciones) {
                    eliminarNotificacion(notificacion.getId(), new SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Notificación de cita eliminada: " + notificacion.getId());
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Error al eliminar notificación de cita: " + notificacion.getId());
                        }
                    });
                }
                
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                callback.onError("Error al obtener notificaciones de cita para eliminar");
            }
        });
    }
    
    /**
     * Obtiene el conteo de notificaciones no leídas para un usuario
     */
    public void obtenerConteoNotificacionesNoLeidas(String usuarioId, ConteoCallback callback) {
        db.collection(Constantes.COLLECTION_NOTIFICACIONES)
            .whereArrayContains("usuariosNotificados", usuarioId)
            .whereEqualTo("leida", false)
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(Task<QuerySnapshot> task) {
                    if (task.isSuccessful()) {
                        int count = task.getResult().size();
                        Log.d(TAG, "Conteo de notificaciones no leídas: " + count);
                        callback.onSuccess(count);
                    } else {
                        Log.e(TAG, "Error al obtener conteo de notificaciones", task.getException());
                        callback.onError("Error al obtener conteo de notificaciones: " + task.getException().getMessage());
                    }
                }
            });
    }
    
    // Interfaces para callbacks (siguiendo el patrón de otros repositorios)
    public interface NotificacionCallback {
        void onSuccess(Notificacion notificacion);
        void onError(String error);
    }
    
    public interface NotificacionesCallback {
        void onSuccess(List<Notificacion> notificaciones);
        void onError(String error);
    }
    
    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface ConteoCallback {
        void onSuccess(int count);
        void onError(String error);
    }
}