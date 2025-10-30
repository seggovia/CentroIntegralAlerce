package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegistroUsuariosFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private AutoCompleteTextView actvRol;
    private MaterialButton btnCancelar;
    private MaterialButton btnRegistrar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registro_usuarios, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        actvRol = view.findViewById(R.id.actvRol);
        btnCancelar = view.findViewById(R.id.btnCancelar);
        btnRegistrar = view.findViewById(R.id.btnRegistrar);

        // Configurar combobox de roles
        configurarComboBoxRoles();

        // Botón de retroceso
        MaterialButton btnVolver = view.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(v -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

        // Botón cancelar
        btnCancelar.setOnClickListener(v -> {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
        });

        // Botón registrar
        btnRegistrar.setOnClickListener(v -> registrarUsuario());
    }

    private void configurarComboBoxRoles() {
        String[] roles = {"Administrador", "Usuario", "Visualizador"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_dropdown_item_1line, roles);
        actvRol.setAdapter(adapter);
        actvRol.setText("Usuario", false); // Valor por defecto
        
        // Configuraciones adicionales para que funcione como dropdown
        actvRol.setThreshold(0); // Mostrar opciones inmediatamente
        actvRol.setOnClickListener(v -> {
            actvRol.showDropDown();
        });
        actvRol.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                actvRol.showDropDown();
            }
        });
    }

    private void registrarUsuario() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String rol = actvRol.getText().toString().trim();

        // Validaciones
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("El email es requerido");
            etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email inválido");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("La contraseña es requerida");
            etPassword.requestFocus();
            return;
        }

        // Validar longitud mínima de contraseña
        if (password.length() < 8) {
            etPassword.setError("La contraseña debe tener al menos 8 caracteres");
            etPassword.requestFocus();
            return;
        }
        
        // Validar que la contraseña tenga mayúsculas, minúsculas y números
        boolean hasUpper = false, hasLower = false, hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isLowerCase(c)) hasLower = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        
        if (!hasUpper) {
            etPassword.setError("La contraseña debe tener al menos una mayúscula");
            etPassword.requestFocus();
            return;
        }
        if (!hasLower) {
            etPassword.setError("La contraseña debe tener al menos una minúscula");
            etPassword.requestFocus();
            return;
        }
        if (!hasDigit) {
            etPassword.setError("La contraseña debe tener al menos un número");
            etPassword.requestFocus();
            return;
        }

        // Validar confirmación de contraseña
        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError("Confirma tu contraseña");
            etConfirmPassword.requestFocus();
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Las contraseñas no coinciden");
            etConfirmPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(rol)) {
            actvRol.setError("El rol es requerido");
            actvRol.requestFocus();
            return;
        }

        // Deshabilitar botón para evitar múltiples registros
        btnRegistrar.setEnabled(false);
        btnRegistrar.setText("Registrando...");

        // Crear usuario en Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Actualizar perfil del usuario
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(email)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        if (profileTask.isSuccessful()) {
                                            // Guardar información adicional en Firestore
                                            guardarUsuarioEnFirestore(user.getUid(), email, rol);
                                        } else {
                                            mostrarError("Error al actualizar perfil: " + profileTask.getException().getMessage());
                                        }
                                    });
                        }
                    } else {
                        mostrarError("Error al crear usuario: " + task.getException().getMessage());
                    }
                });
    }

    private void guardarUsuarioEnFirestore(String uid, String email, String rol) {
        Map<String, Object> usuario = new HashMap<>();
        usuario.put("uid", uid);
        usuario.put("email", email);
        usuario.put("rol", rol);
        usuario.put("fechaCreacion", com.google.firebase.Timestamp.now());
        usuario.put("activo", true);

        db.collection("usuarios")
                .document(uid)
                .set(usuario)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Usuario registrado exitosamente", Toast.LENGTH_LONG).show();
                    
                    // Limpiar formulario
                    limpiarFormulario();
                    
                    // Volver a la pantalla anterior
                    androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
                })
                .addOnFailureListener(e -> {
                    mostrarError("Error al guardar información del usuario: " + e.getMessage());
                });
    }

    private void limpiarFormulario() {
        etEmail.setText("");
        etPassword.setText("");
        etConfirmPassword.setText("");
        actvRol.setText("Usuario", false);
    }

    private void mostrarError(String mensaje) {
        Toast.makeText(getContext(), mensaje, Toast.LENGTH_LONG).show();
        
        // Rehabilitar botón
        btnRegistrar.setEnabled(true);
        btnRegistrar.setText("Registrar Usuario");
    }
}
