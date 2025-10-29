package com.centroalerce.gestion.repositories;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.centroalerce.gestion.models.Cita;
import com.centroalerce.gestion.utils.EstadoCita;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CitaRepository {
    private final FirebaseFirestore db;

    public CitaRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // Obtener citas de una semana específica (actualiza estados automáticamente)
    public void getCitasSemana(Date inicioSemana, Date finSemana, CitasCallback callback) {
        db.collection("citas")
                .whereGreaterThanOrEqualTo("fecha", new Timestamp(inicioSemana))
                .whereLessThan("fecha", new Timestamp(finSemana))
                .orderBy("fecha")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Cita> citas = new ArrayList<>();
                    Date ahora = new Date();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Cita cita = doc.toObject(Cita.class);

                        // Actualizar estado si la cita pasó y sigue programada
                        if (cita.getFecha() != null &&
                                cita.getFecha().toDate().before(ahora) &&
                                "programada".equals(cita.getEstado())) {

                            // Marcar como completada
                            completarCita(cita.getId(), null);
                            cita.setEstado("completada");
                        }

                        citas.add(cita);
                    }
                    callback.onSuccess(citas);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener todas las citas agendadas (filtra y actualiza)
    public void getCitasAgendadas(CitasCallback callback) {
        db.collection("citas")
                .orderBy("fecha")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Cita> citas = new ArrayList<>();
                    Date ahora = new Date();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Cita cita = doc.toObject(Cita.class);

                        // Actualizar estado automáticamente
                        if (cita.getFecha() != null &&
                                cita.getFecha().toDate().before(ahora) &&
                                "programada".equals(cita.getEstado())) {

                            completarCita(cita.getId(), null);
                            cita.setEstado("completada");
                        }

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

                        // Actualizar estado si es necesario
                        if (cita != null && cita.getFecha() != null) {
                            Date ahora = new Date();
                            if (cita.getFecha().toDate().before(ahora) &&
                                    "programada".equals(cita.getEstado())) {

                                completarCita(id, null);
                                cita.setEstado("completada");
                            }
                        }

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
                nuevaCita.setStartAt(new Timestamp(nuevaFecha));
                nuevaCita.setEstado("programada");
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

    // Completar cita (puede ser llamado manualmente o automáticamente)
    // Completar cita (puede ser llamado manualmente o automáticamente)
    public void completarCita(String citaId, SimpleCallback callback) {
        // ✅ CORRECCIÓN: Actualizar en collectionGroup para alcanzar todas las citas
        db.collectionGroup("citas")
                .whereEqualTo("id", citaId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        if (callback != null) callback.onError("Cita no encontrada");
                        return;
                    }

                    // Actualizar el primer documento encontrado
                    querySnapshot.getDocuments().get(0).getReference()
                            .update(
                                    "estado", "completada",
                                    "fechaModificacion", Timestamp.now()
                            )
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onError(e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
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