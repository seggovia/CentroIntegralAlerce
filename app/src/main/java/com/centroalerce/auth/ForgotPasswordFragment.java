package com.centroalerce.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.viewmodels.AuthViewModel;
import com.google.android.material.textfield.TextInputEditText;

public class ForgotPasswordFragment extends Fragment {

    private AuthViewModel authViewModel;
    private TextInputEditText etEmail;
    private Button btnEnviar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_forgot_password, container, false);

        // Inicializar ViewModel
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Referencias
        etEmail = v.findViewById(R.id.etEmail);
        btnEnviar = v.findViewById(R.id.btnEnviar);
        Button btnVolver = v.findViewById(R.id.btnVolver);

        // Validación de email en tiempo real
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnEnviar.setEnabled(isValidEmail(s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Botón enviar
        btnEnviar.setOnClickListener(view -> {
            String email = etEmail.getText().toString().trim();
            if (!email.isEmpty()) {
                enviarRecuperacion(email);
            }
        });

        // Botón volver
        btnVolver.setOnClickListener(view -> {
            Navigation.findNavController(v).navigateUp();
        });

        // Observar resultados del ViewModel
        observarViewModel();

        return v;
    }

    private void enviarRecuperacion(String email) {
        btnEnviar.setEnabled(false);
        btnEnviar.setText("Enviando...");

        authViewModel.resetPassword(email);
    }

    private void observarViewModel() {
        // Observar éxito
        authViewModel.getLoginResult().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                btnEnviar.setText("Enviar enlace de recuperación");

                // Volver al login después de éxito
                Navigation.findNavController(requireView()).navigateUp();
            }
        });

        // Observar errores
        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                btnEnviar.setEnabled(true);
                btnEnviar.setText("Enviar enlace de recuperación");
            }
        });
    }

    private boolean isValidEmail(String email) {
        return email != null &&
                email.contains("@") &&
                email.length() > 5;
    }
}