package com.centroalerce.gestion.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.centroalerce.gestion.repositories.MantenedorRepository;

import java.util.List;
import java.util.Map;

public class MantenedorViewModel<T> extends ViewModel {
    private final MantenedorRepository<T> repository;
    private final MutableLiveData<List<T>> items = new MutableLiveData<>();
    private final MutableLiveData<T> selectedItem = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public MantenedorViewModel(String collectionName, Class<T> type) {
        this.repository = new MantenedorRepository<>(collectionName, type);
    }

    // Crear
    public void create(Map<String, Object> data) {
        isLoading.setValue(true);
        repository.create(data, new MantenedorRepository.CreateCallback() {
            @Override
            public void onSuccess(String id) {
                isLoading.setValue(false);
                successMessage.setValue("Creado exitosamente");
                loadAll(); // Recargar lista
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Cargar todos
    public void loadAll() {
        isLoading.setValue(true);
        repository.getAll(new MantenedorRepository.ListCallback<T>() {
            @Override
            public void onSuccess(List<T> itemsList) {
                isLoading.setValue(false);
                items.setValue(itemsList);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Cargar por ID
    public void loadById(String id) {
        isLoading.setValue(true);
        repository.getById(id, new MantenedorRepository.ItemCallback<T>() {
            @Override
            public void onSuccess(T item) {
                isLoading.setValue(false);
                selectedItem.setValue(item);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Actualizar
    public void update(String id, Map<String, Object> data) {
        isLoading.setValue(true);
        repository.update(id, data, new MantenedorRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                successMessage.setValue("Actualizado exitosamente");
                loadAll();
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Eliminar
    public void delete(String id) {
        isLoading.setValue(true);
        repository.delete(id, new MantenedorRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                successMessage.setValue("Eliminado exitosamente");
                loadAll();
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Observables
    public LiveData<List<T>> getItems() {
        return items;
    }

    public LiveData<T> getSelectedItem() {
        return selectedItem;
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