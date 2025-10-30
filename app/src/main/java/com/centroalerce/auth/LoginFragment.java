package com.centroalerce.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.Task;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginFragment extends Fragment {

    private TextInputEditText etEmail, etPass;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public LoginFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_login, c, false);

        etEmail = v.findViewById(R.id.etEmail);
        etPass  = v.findViewById(R.id.etPass);
        btnLogin= v.findViewById(R.id.btnLogin);
        btnLogin.setEnabled(true); // Siempre habilitado, las validaciones se hacen al hacer clic
        progressBar = v.findViewById(R.id.progressBarLogin);
        TextView tvForgot = v.findViewById(R.id.tvForgot);
        TextView tvContacto = v.findViewById(R.id.tvContacto);

        // Botón "Crear cuenta"
        MaterialButton btnSignup = v.findViewById(R.id.btnSignup);
        if (btnSignup != null) {
            btnSignup.setOnClickListener(x ->
                    Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_signupFragment)
            );
        }

        // TextWatcher para dar feedback visual pero NO deshabilitar botón
        // Las validaciones se harán al hacer clic
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Limpiar errores mientras escribe
                if (etEmail != null) etEmail.setError(null);
                if (etPass != null) etPass.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etEmail.addTextChangedListener(watcher);
        etPass.addTextChangedListener(watcher);

        // Inicializa Auth y Firestore
        if (auth == null) auth = FirebaseAuth.getInstance();
        if (db == null) db = FirebaseFirestore.getInstance();
        auth.setLanguageCode("es");

        btnLogin.setOnClickListener(x -> doLogin(v));
        tvForgot.setOnClickListener(x ->
                Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        );

        // Navegar a contacto/soporte
        if (tvContacto != null) {
            tvContacto.setOnClickListener(x -> openContactSupport(v));
        }

        return v;
    }

    private boolean isEmpty(TextInputEditText e){
        return e.getText()==null || e.getText().toString().trim().isEmpty();
    }

    private void doLogin(View root){
        if (etEmail == null || etPass == null) {
            Toast.makeText(getContext(), "Error: campos no inicializados", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String email = etEmail.getText()==null ? "" : etEmail.getText().toString().trim();
        String pass  = etPass.getText()==null ? "" : etPass.getText().toString();

        // Validar campos vacíos
        boolean ok = true;
        if (email.isEmpty()){
            etEmail.setError("El correo es requerido");
            etEmail.requestFocus();
            ok = false;
        }
        if (pass.isEmpty()){
            etPass.setError("La contraseña es requerida");
            if (ok) etPass.requestFocus();
            ok = false;
        }
        if (!ok){
            Toast.makeText(getContext(), "Por favor completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validar formato de email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            etEmail.setError("El formato del correo no es válido");
            etEmail.requestFocus();
            Toast.makeText(getContext(), "Corrige el formato del correo", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validar longitud mínima de contraseña
        if (pass.length() < 6){
            etPass.setError("La contraseña debe tener al menos 6 caracteres");
            etPass.requestFocus();
            Toast.makeText(getContext(), "La contraseña es muy corta", Toast.LENGTH_SHORT).show();
            return;
        }

        if (auth == null) auth = FirebaseAuth.getInstance();
        auth.setLanguageCode("es");

        // Mostrar loading
        showLoading(true);
        btnLogin.setEnabled(false);
        btnLogin.setText("Iniciando sesión...");

        auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()){
                        // Ocultar loading y restaurar botón
                        showLoading(false);
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Iniciar sesión");

                        String error = task.getException() != null ? task.getException().getMessage() : "";
                        String msg;
                        if (error.contains("password is invalid") || error.contains("INVALID_PASSWORD")) {
                            msg = "Contraseña incorrecta";
                        } else if (error.contains("no user record") || error.contains("EMAIL_NOT_FOUND")) {
                            msg = "No existe una cuenta con este correo";
                        } else if (error.contains("network error") || error.contains("network")) {
                            msg = "Error de red, verifica tu conexión a Internet";
                        } else if (error.contains("too many requests")) {
                            msg = "Demasiados intentos fallidos. Intenta nuevamente más tarde";
                        } else if (error.contains("email address is badly formatted") || error.contains("INVALID_EMAIL")) {
                            msg = "El formato del correo es inválido";
                        } else {
                            msg = "Error al iniciar sesión: " + error;
                        }
                        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        showLoading(false);
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Iniciar sesión");
                        Toast.makeText(getContext(), "Error: usuario no encontrado", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Actualizar texto del loading
                    btnLogin.setText("Verificando cuenta...");

                    // Refresca info antes de verificar
                    user.reload().addOnCompleteListener((Task<Void> r) -> {
                        if (!r.isSuccessful()) {
                            showLoading(false);
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Iniciar sesión");
                            Toast.makeText(getContext(), "Error al verificar estado de correo", Toast.LENGTH_LONG).show();
                            auth.signOut();
                            return;
                        }

                        if (!user.isEmailVerified()) {
                            showLoading(false);
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Iniciar sesión");

                            Toast.makeText(getContext(),
                                    "Tu cuenta aún no está verificada. Revisa tu correo (incluye carpeta de spam).",
                                    Toast.LENGTH_LONG).show();

                            user.sendEmailVerification()
                                    .addOnSuccessListener(x ->
                                            Toast.makeText(getContext(),
                                                    "Te reenviamos el enlace de verificación ✅",
                                                    Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(getContext(),
                                                    "No se pudo reenviar el correo: " + e.getMessage(),
                                                    Toast.LENGTH_LONG).show());

                            auth.signOut();
                            return;
                        }

                        // Actualizar texto del loading
                        btnLogin.setText("Finalizando...");

                        // Actualizar emailVerificado en Firestore
                        if (db == null) db = FirebaseFirestore.getInstance();

                        db.collection("usuarios")
                                .document(user.getUid())
                                .update("emailVerificado", true)
                                .addOnSuccessListener(aVoid -> {
                                    showLoading(false);
                                    btnLogin.setText("Iniciar sesión");
                                    Toast.makeText(getContext(), "Bienvenido ✅", Toast.LENGTH_SHORT).show();
                                    Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_calendarFragment);
                                })
                                .addOnFailureListener(e -> {
                                    // Si falla la actualización, igual permitir continuar
                                    showLoading(false);
                                    btnLogin.setText("Iniciar sesión");
                                    Toast.makeText(getContext(),
                                            "Sesión iniciada (error al actualizar perfil)",
                                            Toast.LENGTH_SHORT).show();
                                    Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_calendarFragment);
                                });
                    });
                });
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void openContactSupport(View root){
        Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_contactSupportFragment);
    }
}