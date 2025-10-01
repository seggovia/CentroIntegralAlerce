package com.centroalerce.gestion.repositories;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.centroalerce.gestion.models.Cita;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CitaRepository {
    private final FirebaseFirestore db;

    public CitaRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // Obtener citas de una semana específica
    public void getCitasSemana(Date inicioSemana, Date finSemana, CitasCallback callback) {
        db.collection("citas")
                .whereGreaterThanOrEqualTo("fecha", new Timestamp(inicioSemana))
                .whereLessThan("fecha", new Timestamp(finSemana))
                .orderBy("fecha")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Cita> citas = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Cita cita = doc.toObject(Cita.class);
                        citas.add(cita);
                    }
                    callback.onSuccess(citas);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener todas las citas agendadas
    public void getCitasAgendadas(CitasCallback callback) {
        db.collection("citas")
                .whereEqualTo("estado", "agendada")
                .orderBy("fecha")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Cita> citas = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Cita cita = doc.toObject(Cita.class);
                        citas.add(cita);
                    }
                    callback.onSuccess(citas);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener cita por ID
    public void getCita(String id, CitaCallback callback) {
        db.collection("citas")
                .document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Cita cita = documentSnapshot.toObject(Cita.class);
                        callback.onSuccess(cita);
                    } else {
                        callback.onError("Cita no encontrada");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Cancelar cita
    public void cancelarCita(String citaId, String motivo, SimpleCallback callback) {
        db.collection("citas")
                .document(citaId)
                .update(
                        "estado", "cancelada",
                        "motivoCancelacion", motivo,
                        "fechaModificacion", Timestamp.now()
                )
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Reagendar cita
    public void reagendarCita(String citaId, Date nuevaFecha, String motivo, SimpleCallback callback) {
        // Primero obtener la cita original
        getCita(citaId, new CitaCallback() {
            @Override
            public void onSuccess(Cita citaOriginal) {
                // Marcar la cita original como reagendada
                db.collection("citas")
                        .document(citaId)
                        .update(
                                "estado", "reagendada",
                                "motivoReagendamiento", motivo,
                                "fechaModificacion", Timestamp.now()
                        );

                // Crear nueva cita
                Cita nuevaCita = new Cita();
                nuevaCita.setActividadId(citaOriginal.getActividadId());
                nuevaCita.setActividadNombre(citaOriginal.getActividadNombre());
                nuevaCita.setLugarId(citaOriginal.getLugarId());
                nuevaCita.setLugarNombre(citaOriginal.getLugarNombre());
                nuevaCita.setFecha(new Timestamp(nuevaFecha));
                nuevaCita.setEstado("agendada");
                nuevaCita.setCitaOriginalId(citaId);
                nuevaCita.setFechaCreacion(Timestamp.now());
                nuevaCita.setCreadoPor(citaOriginal.getCreadoPor());
                nuevaCita.setNotificacionEnviada(false);

                db.collection("citas")
                        .add(nuevaCita)
                        .addOnSuccessListener(docRef -> {
                            docRef.update("id", docRef.getId());
                            callback.onSuccess();
                        })
                        .addOnFailureListener(e -> callback.onError(e.getMessage()));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Completar cita
    public void completarCita(String citaId, SimpleCallback callback) {
        db.collection("citas")
                .document(citaId)
                .update(
                        "estado", "completada",
                        "fechaModificacion", Timestamp.now()
                )
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Interfaces para callbacks
    public interface CitaCallback {
        void onSuccess(Cita cita);
        void onError(String error);
    }

    public interface CitasCallback {
        void onSuccess(List<Cita> citas);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }
}