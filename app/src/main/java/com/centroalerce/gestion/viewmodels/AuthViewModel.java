package com.centroalerce.gestion.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.centroalerce.gestion.repositories.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

public class AuthViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<String> loginResult = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public AuthViewModel() {
        this.authRepository = new AuthRepository();
    }

    // Login
    public void login(String email, String password) {
        isLoading.setValue(true);
        authRepository.login(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                isLoading.setValue(false);
                loginResult.setValue(message);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Registrar
    public void register(String email, String password, String nombre, String rol) {
        isLoading.setValue(true);
        authRepository.register(email, password, nombre, rol, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                isLoading.setValue(false);
                loginResult.setValue(message);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Recuperar contraseña
    public void resetPassword(String email) {
        isLoading.setValue(true);
        authRepository.resetPassword(email, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                isLoading.setValue(false);
                loginResult.setValue(message);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    // Logout
    public void logout() {
        authRepository.logout();
    }

    // Obtener usuario actual
    public FirebaseUser getCurrentUser() {
        return authRepository.getCurrentUser();
    }

    // Observables
    public LiveData<String> getLoginResult() {
        return loginResult;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
}