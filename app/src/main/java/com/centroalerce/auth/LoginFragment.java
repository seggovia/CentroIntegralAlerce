package com.centroalerce.auth;
import com.google.firebase.auth.FirebaseAuth;      // ya lo tenías / confírmalo
import com.google.firebase.auth.FirebaseUser;      // ⭐ requerido
import com.google.android.gms.tasks.Task;          // para los callbacks de Task

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
// Firebase (opcional si ya configuraste BoM)
 import com.google.firebase.auth.FirebaseAuth;
// 👉 NUEVO
import com.google.firebase.auth.FirebaseAuth;
import android.util.Patterns;

public class LoginFragment extends Fragment {

    private TextInputEditText etEmail, etPass;
    private MaterialButton btnLogin;
    // private FirebaseAuth auth;
    // 👉 NUEVO
    private FirebaseAuth auth;

    public LoginFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_login, c, false);

        etEmail = v.findViewById(R.id.etEmail);
        etPass  = v.findViewById(R.id.etPass);
        btnLogin= v.findViewById(R.id.btnLogin);
        TextView tvForgot = v.findViewById(R.id.tvForgot);
        TextView tvContacto = v.findViewById(R.id.tvContacto);

        // 👉 NUEVO: referencia al botón "Crear cuenta"
        com.google.android.material.button.MaterialButton btnSignup = v.findViewById(R.id.btnSignup);
        if (btnSignup != null) {
            btnSignup.setOnClickListener(x ->
                    Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_signupFragment)
            );
        }

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean enable = !isEmpty(etEmail) && !isEmpty(etPass);
                btnLogin.setEnabled(enable);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etEmail.addTextChangedListener(watcher);
        etPass.addTextChangedListener(watcher);

        // auth = FirebaseAuth.getInstance();
        // 👉 NUEVO: inicializa Auth y fuerza idioma español para el email
        if (auth == null) auth = FirebaseAuth.getInstance();
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
        String email = etEmail.getText()==null ? "" : etEmail.getText().toString().trim();
        String pass  = etPass.getText()==null ? "" : etPass.getText().toString();

        boolean ok = true;
        if (email.isEmpty()){ etEmail.setError("Ingresa tu correo"); ok = false; }
        if (pass.isEmpty()){ etPass.setError("Ingresa tu contraseña"); ok = false; }
        if (!ok){
            Toast.makeText(getContext(), "Revisa los campos ❌", Toast.LENGTH_SHORT).show();
            return;
        }

        if (auth == null) auth = FirebaseAuth.getInstance();
        auth.setLanguageCode("es"); // 🔹 Fuerza idioma español para correos y mensajes
        btnLogin.setEnabled(false);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    btnLogin.setEnabled(true);

                    if (!task.isSuccessful()){
                        // 🔹 Traduce los errores más comunes al español
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
                        Toast.makeText(getContext(), "Error: usuario no encontrado", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Refresca info antes de verificar
                    user.reload().addOnCompleteListener((Task<Void> r) -> {
                        if (!r.isSuccessful()) {
                            Toast.makeText(getContext(), "Error al verificar estado de correo", Toast.LENGTH_LONG).show();
                            auth.signOut();
                            return;
                        }

                        if (!user.isEmailVerified()) {
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

                        // ✅ Solo si está verificado
                        Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_homeFragment);
                    });
                });
    }



    private void openSignup(View root){
        Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_signupFragment);
    }

    private void openContactSupport(View root){
        Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_contactSupportFragment);
    }
}
