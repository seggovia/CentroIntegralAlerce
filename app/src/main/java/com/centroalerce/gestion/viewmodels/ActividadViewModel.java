package com.centroalerce.gestion.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.centroalerce.gestion.utils.ValidationResult; // 👈 ⭐ AGREGAR ESTA LÍNEA

import com.centroalerce.gestion.models.Actividad;
import com.centroalerce.gestion.repositories.ActividadRepository;

import java.util.Date;
import java.util.List;

public class ActividadViewModel extends ViewModel {
    private final ActividadRepository actividadRepository;
    private final MutableLiveData<List<Actividad>> actividades = new MutableLiveData<>();
    private final MutableLiveData<Actividad> actividadSeleccionada = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public ActividadViewModel() {
        this.actividadRepository = new ActividadRepository();
    }

    // Crear actividad
    // Crear actividad
    public void createActividad(Actividad actividad, List<Date> fechasCitas,
                                String lugarId, String lugarNombre) {
        isLoading.setValue(true);

        // 🔥 CAMBIO: Usar el método PÚBLICO con validación
        actividadRepository.createActividadConValidacion(
                actividad,
                fechasCitas,
                lugarId,
                lugarNombre,
                new ActividadRepository.CreateCallback() {
                    @Override
                    public void onSuccess(String actividadId) {
                        isLoading.setValue(false);
                        successMessage.setValue("Actividad creada exitosamente");
                        loadActividades(); // Recargar lista
                    }

                    @Override
                    public void onError(String error) {
                        isLoading.setValue(false);
                        errorMessage.setValue(error);
                    }

                    @Override
                    public void onValidationError(ValidationResult validationResult) {
                        isLoading.setValue(false);
                        errorMessage.setValue(validationResult.getErrorMessage());
                    }
                }
        );
    }

    // Cargar todas las actividades
    public void loadActividades() {
        isLoading.setValue(true);
        actividadRepository.getAllActividades(new ActividadRepository.ActividadesCallback() {
            @Override
            public void onSuccess(List<Actividad> actividadesList) {
                isLoading.setValue(false);
                actividades.setValue(actividadesList);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Cargar actividad específica
    public void loadActividad(String id) {
        isLoading.setValue(true);
        actividadRepository.getActividad(id, new ActividadRepository.ActividadCallback() {
            @Override
            public void onSuccess(Actividad actividad) {
                isLoading.setValue(false);
                actividadSeleccionada.setValue(actividad);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Actualizar actividad
    public void updateActividad(Actividad actividad) {
        isLoading.setValue(true);
        actividadRepository.updateActividad(actividad, new ActividadRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                successMessage.setValue("Actividad actualizada");
                loadActividades();
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Cancelar actividad
    public void cancelarActividad(String actividadId, String motivo) {
        isLoading.setValue(true);
        actividadRepository.cancelarActividad(actividadId, motivo,
                new ActividadRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        isLoading.setValue(false);
                        successMessage.setValue("Actividad cancelada");
                        loadActividades();
                    }

                    @Override
                    public void onError(String error) {
                        isLoading.setValue(false);
                        errorMessage.setValue(error);
                    }
                });
    }

    // Observables
    public LiveData<List<Actividad>> getActividades() {
        return actividades;
    }

    public LiveData<Actividad> getActividadSeleccionada() {
        return actividadSeleccionada;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
}