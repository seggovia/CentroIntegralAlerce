package com.centroalerce.gestion.repositories;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;
import com.centroalerce.gestion.models.Lugar;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LugarRepository {
    private final FirebaseFirestore db;

    public LugarRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // Obtener todos los lugares activos
    public void getAllLugares(LugaresCallback callback) {
        db.collection("lugares")
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Lugar> lugares = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Lugar lugar = doc.toObject(Lugar.class);
                        lugares.add(lugar);
                    }
                    callback.onSuccess(lugares);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener lugar por ID
    public void getLugar(String id, LugarCallback callback) {
        db.collection("lugares")
                .document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Lugar lugar = documentSnapshot.toObject(Lugar.class);
                        callback.onSuccess(lugar);
                    } else {
                        callback.onError("Lugar no encontrado");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Verificar disponibilidad de cupo en un lugar
    public void verificarDisponibilidadCupo(String lugarId, int cupoSolicitado,
                                            DisponibilidadCallback callback) {
        getLugar(lugarId, new LugarCallback() {
            @Override
            public void onSuccess(Lugar lugar) {
                if (!lugar.tieneCupo()) {
                    // Lugar sin restricción de cupo
                    callback.onDisponible(true, 0);
                    return;
                }

                int cupoDisponible = lugar.getCupo();
                boolean hayEspacio = cupoSolicitado <= cupoDisponible;
                callback.onDisponible(hayEspacio, cupoDisponible);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Obtener citas existentes en un lugar para una fecha específica
    public void getCitasEnLugar(String lugarId, Date fecha, CitasEnLugarCallback callback) {
        // Obtener inicio y fin del día
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        Date inicioDia = cal.getTime();

        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        Date finDia = cal.getTime();

        db.collection("citas")
                .whereEqualTo("lugarId", lugarId)
                .whereEqualTo("estado", "agendada")
                .whereGreaterThanOrEqualTo("fecha", new Timestamp(inicioDia))
                .whereLessThan("fecha", new Timestamp(finDia))
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Date> fechasCitas = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Timestamp timestamp = doc.getTimestamp("fecha");
                        if (timestamp != null) {
                            fechasCitas.add(timestamp.toDate());
                        }
                    }
                    callback.onSuccess(fechasCitas);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Interfaces para callbacks
    public interface LugarCallback {
        void onSuccess(Lugar lugar);
        void onError(String error);
    }

    public interface LugaresCallback {
        void onSuccess(List<Lugar> lugares);
        void onError(String error);
    }

    public interface DisponibilidadCallback {
        void onDisponible(boolean hayEspacio, int cupoDisponible);
        void onError(String error);
    }

    public interface CitasEnLugarCallback {
        void onSuccess(List<Date> fechasCitas);
        void onError(String error);
    }
}