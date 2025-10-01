package com.centroalerce.gestion.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.centroalerce.gestion.models.Cita;
import com.centroalerce.gestion.repositories.CitaRepository;

import java.util.Date;
import java.util.List;

public class CitaViewModel extends ViewModel {
    private final CitaRepository citaRepository;
    private final MutableLiveData<List<Cita>> citasSemana = new MutableLiveData<>();
    private final MutableLiveData<List<Cita>> citasAgendadas = new MutableLiveData<>();
    private final MutableLiveData<Cita> citaSeleccionada = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public CitaViewModel() {
        this.citaRepository = new CitaRepository();
    }

    // Cargar citas de la semana
    public void loadCitasSemana(Date inicioSemana, Date finSemana) {
        isLoading.setValue(true);
        citaRepository.getCitasSemana(inicioSemana, finSemana, new CitaRepository.CitasCallback() {
            @Override
            public void onSuccess(List<Cita> citas) {
                isLoading.setValue(false);
                citasSemana.setValue(citas);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Cargar todas las citas agendadas
    public void loadCitasAgendadas() {
        isLoading.setValue(true);
        citaRepository.getCitasAgendadas(new CitaRepository.CitasCallback() {
            @Override
            public void onSuccess(List<Cita> citas) {
                isLoading.setValue(false);
                citasAgendadas.setValue(citas);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Cargar cita específica
    public void loadCita(String id) {
        isLoading.setValue(true);
        citaRepository.getCita(id, new CitaRepository.CitaCallback() {
            @Override
            public void onSuccess(Cita cita) {
                isLoading.setValue(false);
                citaSeleccionada.setValue(cita);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Cancelar cita
    public void cancelarCita(String citaId, String motivo) {
        isLoading.setValue(true);
        citaRepository.cancelarCita(citaId, motivo, new CitaRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                successMessage.setValue("Cita cancelada");
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Reagendar cita
    public void reagendarCita(String citaId, Date nuevaFecha, String motivo) {
        isLoading.setValue(true);
        citaRepository.reagendarCita(citaId, nuevaFecha, motivo, new CitaRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                successMessage.setValue("Cita reagendada");
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Completar cita
    public void completarCita(String citaId) {
        isLoading.setValue(true);
        citaRepository.completarCita(citaId, new CitaRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                successMessage.setValue("Cita completada");
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Observables
    public LiveData<List<Cita>> getCitasSemana() {
        return citasSemana;
    }

    public LiveData<List<Cita>> getCitasAgendadas() {
        return citasAgendadas;
    }

    public LiveData<Cita> getCitaSeleccionada() {
        return citaSeleccionada;
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