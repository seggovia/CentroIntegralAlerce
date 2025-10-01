package com.centroalerce.gestion.repositories;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.centroalerce.gestion.models.Actividad;
import com.centroalerce.gestion.models.Cita;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ActividadRepository {
    private final FirebaseFirestore db;

    public ActividadRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // Crear actividad con sus citas
    public void createActividad(Actividad actividad, List<Date> fechasCitas,
                                String lugarId, String lugarNombre, CreateCallback callback) {
        // Primero guardar la actividad
        db.collection("actividades")
                .add(actividad)
                .addOnSuccessListener(documentReference -> {
                    String actividadId = documentReference.getId();
                    actividad.setId(actividadId);

                    // Actualizar el documento con el ID
                    documentReference.update("id", actividadId);

                    // Crear las citas asociadas
                    createCitas(actividadId, actividad.getNombre(), fechasCitas,
                            lugarId, lugarNombre, actividad.getCreadoPor(), callback);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Crear citas para una actividad
    private void createCitas(String actividadId, String actividadNombre,
                             List<Date> fechasCitas, String lugarId, String lugarNombre,
                             String creadoPor, CreateCallback callback) {
        int totalCitas = fechasCitas.size();
        int[] citasCreadas = {0};

        for (Date fecha : fechasCitas) {
            Cita cita = new Cita();
            cita.setActividadId(actividadId);
            cita.setActividadNombre(actividadNombre);
            cita.setLugarId(lugarId);
            cita.setLugarNombre(lugarNombre);
            cita.setFecha(new Timestamp(fecha));
            cita.setEstado("agendada");
            cita.setFechaCreacion(Timestamp.now());
            cita.setCreadoPor(creadoPor);
            cita.setNotificacionEnviada(false);

            db.collection("citas")
                    .add(cita)
                    .addOnSuccessListener(docRef -> {
                        docRef.update("id", docRef.getId());
                        citasCreadas[0]++;
                        if (citasCreadas[0] == totalCitas) {
                            callback.onSuccess(actividadId);
                        }
                    })
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
        }
    }

    // Obtener todas las actividades activas
    public void getAllActividades(ActividadesCallback callback) {
        db.collection("actividades")
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Actividad> actividades = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Actividad actividad = doc.toObject(Actividad.class);
                        actividades.add(actividad);
                    }
                    callback.onSuccess(actividades);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener actividad por ID
    public void getActividad(String id, ActividadCallback callback) {
        db.collection("actividades")
                .document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Actividad actividad = documentSnapshot.toObject(Actividad.class);
                        callback.onSuccess(actividad);
                    } else {
                        callback.onError("Actividad no encontrada");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Actualizar actividad
    public void updateActividad(Actividad actividad, SimpleCallback callback) {
        db.collection("actividades")
                .document(actividad.getId())
                .set(actividad)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Cancelar actividad (desactiva la actividad y todas sus citas)
    public void cancelarActividad(String actividadId, String motivo, SimpleCallback callback) {
        // Desactivar la actividad
        db.collection("actividades")
                .document(actividadId)
                .update("activo", false)
                .addOnSuccessListener(aVoid -> {
                    // Cancelar todas las citas de esta actividad
                    cancelarCitasDeActividad(actividadId, motivo, callback);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private void cancelarCitasDeActividad(String actividadId, String motivo, SimpleCallback callback) {
        db.collection("citas")
                .whereEqualTo("actividadId", actividadId)
                .whereEqualTo("estado", "agendada")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().update(
                                "estado", "cancelada",
                                "motivoCancelacion", motivo,
                                "fechaModificacion", Timestamp.now()
                        );
                    }
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Interfaces para callbacks
    public interface CreateCallback {
        void onSuccess(String actividadId);
        void onError(String error);
    }

    public interface ActividadCallback {
        void onSuccess(Actividad actividad);
        void onError(String error);
    }

    public interface ActividadesCallback {
        void onSuccess(List<Actividad> actividades);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }
}