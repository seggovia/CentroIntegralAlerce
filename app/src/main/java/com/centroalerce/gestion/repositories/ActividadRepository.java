package com.centroalerce.gestion.repositories;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.centroalerce.gestion.models.Actividad;
import com.centroalerce.gestion.models.Cita;
import com.centroalerce.gestion.models.Lugar;
import com.centroalerce.gestion.utils.ActividadValidator;
import com.centroalerce.gestion.utils.ValidationResult;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ActividadRepository {
    private final FirebaseFirestore db;
    private final LugarRepository lugarRepository;

    public ActividadRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.lugarRepository = new LugarRepository();
    }

    // Crear actividad con validaciones completas
    public void createActividadConValidacion(Actividad actividad, List<Date> fechasCitas,
                                             String lugarId, String lugarNombre,
                                             CreateCallback callback) {
        // Paso 1: Obtener información del lugar
        lugarRepository.getLugar(lugarId, new LugarRepository.LugarCallback() {
            @Override
            public void onSuccess(Lugar lugar) {
                // Paso 2: Obtener citas existentes en el lugar
                obtenerCitasExistentes(lugar, fechasCitas, new CitasExistentesCallback() {
                    @Override
                    public void onSuccess(List<Date> citasExistentes) {
                        // Paso 3: Validar todo
                        ValidationResult validacion = ActividadValidator.validarActividadCompleta(
                                actividad, lugar, fechasCitas, citasExistentes
                        );

                        if (!validacion.isValid()) {
                            callback.onValidationError(validacion);
                            return;
                        }

                        // Paso 4: Si pasa todas las validaciones, crear la actividad
                        createActividad(actividad, fechasCitas, lugarId, lugarNombre, callback);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Obtener todas las citas existentes en un lugar para las fechas solicitadas
    private void obtenerCitasExistentes(Lugar lugar, List<Date> fechasSolicitadas,
                                        CitasExistentesCallback callback) {
        List<Date> todasLasCitasExistentes = new ArrayList<>();
        int[] contadorPendiente = {fechasSolicitadas.size()};

        if (fechasSolicitadas.isEmpty()) {
            callback.onSuccess(todasLasCitasExistentes);
            return;
        }

        for (Date fecha : fechasSolicitadas) {
            lugarRepository.getCitasEnLugar(lugar.getId(), fecha,
                    new LugarRepository.CitasEnLugarCallback() {
                        @Override
                        public void onSuccess(List<Date> citasDelDia) {
                            synchronized (todasLasCitasExistentes) {
                                todasLasCitasExistentes.addAll(citasDelDia);
                                contadorPendiente[0]--;

                                if (contadorPendiente[0] == 0) {
                                    callback.onSuccess(todasLasCitasExistentes);
                                }
                            }
                        }

                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    });
        }
    }

    // Crear actividad (método original, ahora privado)
    private void createActividad(Actividad actividad, List<Date> fechasCitas,
                                 String lugarId, String lugarNombre, CreateCallback callback) {
        db.collection("actividades")
                .add(actividad)
                .addOnSuccessListener(documentReference -> {
                    String actividadId = documentReference.getId();
                    actividad.setId(actividadId);

                    documentReference.update("id", actividadId);

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
            cita.setStartAt(new Timestamp(fecha)); // Para queries
            cita.setEstado("programada");
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

    // Actualizar actividad con validaciones
    public void updateActividadConValidacion(Actividad actividad, String lugarId,
                                             SimpleCallback callback) {
        lugarRepository.getLugar(lugarId, new LugarRepository.LugarCallback() {
            @Override
            public void onSuccess(Lugar lugar) {
                // Validar campos básicos
                ValidationResult validacion = ActividadValidator.validarCamposObligatorios(actividad);
                if (!validacion.isValid()) {
                    callback.onError(validacion.getErrorMessage());
                    return;
                }

                // Validar cupo del lugar
                validacion = ActividadValidator.validarCupoLugar(lugar, actividad.getCupo());
                if (!validacion.isValid()) {
                    callback.onError(validacion.getErrorMessage());
                    return;
                }

                updateActividad(actividad, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Actualizar actividad (método original)
    public void updateActividad(Actividad actividad, SimpleCallback callback) {
        db.collection("actividades")
                .document(actividad.getId())
                .set(actividad)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Cancelar actividad
    public void cancelarActividad(String actividadId, String motivo, SimpleCallback callback) {
        db.collection("actividades")
                .document(actividadId)
                .update("activo", false)
                .addOnSuccessListener(aVoid -> {
                    cancelarCitasDeActividad(actividadId, motivo, callback);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private void cancelarCitasDeActividad(String actividadId, String motivo, SimpleCallback callback) {
        db.collection("citas")
                .whereEqualTo("actividadId", actividadId)
                .whereEqualTo("estado", "programada")
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

    // Interfaces
    private interface CitasExistentesCallback {
        void onSuccess(List<Date> citasExistentes);
        void onError(String error);
    }

    public interface CreateCallback {
        void onSuccess(String actividadId);
        void onError(String error);
        void onValidationError(ValidationResult validationResult);
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