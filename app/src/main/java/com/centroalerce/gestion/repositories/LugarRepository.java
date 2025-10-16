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

    // ✅ NUEVO: Buscar lugar por nombre exacto
    public void getLugarPorNombre(String nombre, LugarCallback callback) {
        db.collection("lugares")
                .whereEqualTo("nombre", nombre)
                .whereEqualTo("activo", true)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) {
                        callback.onError("Lugar '" + nombre + "' no encontrado");
                        return;
                    }
                    Lugar lugar = qs.getDocuments().get(0).toObject(Lugar.class);
                    callback.onSuccess(lugar);
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

    // ✅ CORREGIDO: Busca citas en AMBAS colecciones y TODOS los estados activos
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

        android.util.Log.d("LUGAR_REPO", "🔍 Buscando citas para lugar: " + lugarId +
                " entre " + inicioDia + " y " + finDia);

        // ✅ NUEVO: Buscar en collectionGroup (incluye activities/{id}/citas)
        db.collectionGroup("citas")
                .whereGreaterThanOrEqualTo("startAt", new Timestamp(inicioDia))
                .whereLessThan("startAt", new Timestamp(finDia))
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Date> fechasCitas = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        // ✅ Verificar nombre del lugar (tu modelo usa "lugarNombre")
                        String docLugar = doc.getString("lugarNombre");
                        if (docLugar == null) docLugar = doc.getString("lugar");

                        // ✅ Comparar por nombre (ya que lugarId puede ser el nombre)
                        if (docLugar != null && docLugar.equals(lugarId)) {
                            // ✅ Verificar que NO esté cancelada
                            String estado = doc.getString("estado");
                            if (estado != null) {
                                estado = estado.toLowerCase();
                                if (estado.equals("cancelada") || estado.equals("canceled")) {
                                    continue; // Ignorar canceladas
                                }
                            }

                            Timestamp timestamp = doc.getTimestamp("startAt");
                            if (timestamp == null) timestamp = doc.getTimestamp("fecha");

                            if (timestamp != null) {
                                fechasCitas.add(timestamp.toDate());
                                android.util.Log.d("LUGAR_REPO", "✅ Cita encontrada: " +
                                        timestamp.toDate() + " | lugar: " + docLugar + " | estado: " + estado);
                            }
                        }
                    }

                    android.util.Log.d("LUGAR_REPO", "📊 Total citas en lugar '" + lugarId +
                            "': " + fechasCitas.size());
                    callback.onSuccess(fechasCitas);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("LUGAR_REPO", "❌ Error buscando citas", e);
                    callback.onError(e.getMessage());
                });
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