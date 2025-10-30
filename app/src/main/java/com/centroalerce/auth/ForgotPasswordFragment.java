package com.centroalerce.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
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
        btnEnviar.setEnabled(true); // Siempre habilitado, las validaciones se hacen al hacer clic
        Button btnVolver = v.findViewById(R.id.btnVolver);

        // Validación de email en tiempo real para limpiar errores
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Limpiar errores mientras escribe
                if (etEmail != null) etEmail.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Botón enviar
        btnEnviar.setOnClickListener(view -> {
            if (etEmail == null || etEmail.getText() == null) {
                Toast.makeText(getContext(), "Error: campo no inicializado", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String email = etEmail.getText().toString().trim();
            
            // Validar campo vacío
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("El correo es requerido");
                etEmail.requestFocus();
                Toast.makeText(getContext(), "Por favor ingresa tu correo electrónico", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validar formato de email
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("El formato del correo no es válido");
                etEmail.requestFocus();
                Toast.makeText(getContext(), "Corrige el formato del correo", Toast.LENGTH_SHORT).show();
                return;
            }
            
            enviarRecuperacion(email);
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

}