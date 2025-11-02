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
import androidx.navigation.NavOptions;
import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginFragment extends Fragment {

    private TextInputEditText etEmail, etPass;
    private com.google.android.material.textfield.TextInputLayout tilEmail, tilPass;
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
        // Obtener los TextInputLayout padres para mostrar errores inline
        try { tilEmail = (com.google.android.material.textfield.TextInputLayout) ((View) etEmail.getParent()).getParent(); } catch (Exception ignore) {}
        try { tilPass  = (com.google.android.material.textfield.TextInputLayout) ((View) etPass.getParent()).getParent(); } catch (Exception ignore) {}
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

        // TextWatcher: limpiar errores mientras escribe
        // Las validaciones se harán al hacer clic
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilEmail != null) { tilEmail.setError(null); tilEmail.setErrorEnabled(false); }
                if (tilPass  != null) { tilPass.setError(null); tilPass.setErrorEnabled(false); }
                if (etEmail != null) etEmail.setError(null);
                if (etPass  != null) etPass.setError(null);
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

        // Iniciar animaciones de entrada
        startEntranceAnimations(v);

        return v;
    }

    /**
     * Animaciones profesionales de entrada para la pantalla de login
     */
    private void startEntranceAnimations(View root) {
        // Obtener referencias a los elementos
        View logo = root.findViewById(R.id.ivLogo);
        View title = root.findViewById(R.id.tvTitle);
        View subtitle1 = root.findViewById(R.id.tvSubtitle1);
        View subtitle2 = root.findViewById(R.id.tvSubtitle2);
        View emailLayout = root.findViewById(R.id.tilEmail);
        View passLayout = root.findViewById(R.id.tilPassword);
        View forgotLink = root.findViewById(R.id.tvForgot);
        View loginButton = root.findViewById(R.id.btnLogin);
        View footer = root.findViewById(R.id.llFooter);

        // Hacer todos los elementos invisibles inicialmente
        View[] views = {logo, title, subtitle1, subtitle2, emailLayout, passLayout, forgotLink, loginButton, footer};
        for (View v : views) {
            if (v != null) {
                v.setAlpha(0f);
                v.setTranslationY(30);
            }
        }

        // Animar cada elemento con delay escalonado
        animateView(logo, 0, 100);
        animateView(title, 150, 100);
        animateView(subtitle1, 200, 100);
        animateView(subtitle2, 250, 100);
        animateView(emailLayout, 350, 100);
        animateView(passLayout, 400, 100);
        animateView(forgotLink, 450, 100);
        animateView(loginButton, 500, 100);
        animateView(footer, 600, 100);
    }

    /**
     * Anima un view con fade in y slide up
     */
    private void animateView(View view, long delay, long duration) {
        if (view == null) return;

        view.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(duration + 400) // Duración total: 500ms
                .setStartDelay(delay)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();
    }

    private boolean isEmpty(TextInputEditText e){
        return e.getText()==null || e.getText().toString().trim().isEmpty();
    }

    private void doLogin(View root){
        if (etEmail == null || etPass == null) {
            // Mostrar error inline si hay problema de inicialización
            if (tilEmail != null) { tilEmail.setError("Campo no disponible"); tilEmail.setErrorEnabled(true); }
            if (tilPass  != null) { tilPass.setError("Campo no disponible"); tilPass.setErrorEnabled(true); }
            return;
        }
        
        String email = etEmail.getText()==null ? "" : etEmail.getText().toString().trim();
        String pass  = etPass.getText()==null ? "" : etPass.getText().toString();

        // Validar campos vacíos
        boolean ok = true;
        if (email.isEmpty()){
            if (tilEmail != null) { tilEmail.setError("El correo es requerido"); tilEmail.setErrorEnabled(true); }
            else etEmail.setError("El correo es requerido");
            etEmail.requestFocus();
            ok = false;
        }
        if (pass.isEmpty()){
            if (tilPass != null) { tilPass.setError("La contraseña es requerida"); tilPass.setErrorEnabled(true); }
            else etPass.setError("La contraseña es requerida");
            if (ok) etPass.requestFocus();
            ok = false;
        }
        if (!ok){
            return;
        }

        // Validar formato de email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            if (tilEmail != null) { tilEmail.setError("Formato de correo inválido"); tilEmail.setErrorEnabled(true); }
            else etEmail.setError("El formato del correo no es válido");
            etEmail.requestFocus();
            return;
        }

        // Validar longitud mínima de contraseña
        if (pass.length() < 6){
            if (tilPass != null) { tilPass.setError("Mínimo 6 caracteres"); tilPass.setErrorEnabled(true); }
            else etPass.setError("La contraseña debe tener al menos 6 caracteres");
            etPass.requestFocus();
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
                        if (error.contains("password is invalid") || error.contains("INVALID_PASSWORD")) {
                            if (tilPass != null) { tilPass.setError("Contraseña incorrecta"); tilPass.setErrorEnabled(true); }
                            else etPass.setError("Contraseña incorrecta");
                            etPass.requestFocus();
                        } else if (error.contains("no user record") || error.contains("EMAIL_NOT_FOUND")) {
                            if (tilEmail != null) { tilEmail.setError("Correo no registrado"); tilEmail.setErrorEnabled(true); }
                            else etEmail.setError("No existe una cuenta con este correo");
                            etEmail.requestFocus();
                        } else if (error.contains("network error") || error.contains("network")) {
                            if (tilEmail != null) { tilEmail.setError("Error de red, intenta nuevamente"); tilEmail.setErrorEnabled(true); }
                        } else if (error.contains("too many requests")) {
                            if (tilPass != null) { tilPass.setError("Demasiados intentos. Intenta más tarde"); tilPass.setErrorEnabled(true); }
                        } else if (error.contains("email address is badly formatted") || error.contains("INVALID_EMAIL")) {
                            if (tilEmail != null) { tilEmail.setError("Formato de correo inválido"); tilEmail.setErrorEnabled(true); }
                            else etEmail.setError("El formato del correo es inválido");
                            etEmail.requestFocus();
                        } else {
                            if (tilEmail != null) { tilEmail.setError("Error al iniciar sesión"); tilEmail.setErrorEnabled(true); }
                        }
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
                        btnLogin.setText("Verificando estado...");

                        // Verificar si el usuario existe y está activo en Firestore
                        if (db == null) db = FirebaseFirestore.getInstance();

                        db.collection("usuarios")
                                .document(user.getUid())
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (!documentSnapshot.exists()) {
                                        // El usuario no existe en Firestore
                                        showLoading(false);
                                        btnLogin.setEnabled(true);
                                        btnLogin.setText("Iniciar sesión");
                                        if (tilEmail != null) {
                                            tilEmail.setError("Esta cuenta no existe en el sistema");
                                            tilEmail.setErrorEnabled(true);
                                        }
                                        Toast.makeText(getContext(),
                                                "Esta cuenta ha sido eliminada del sistema",
                                                Toast.LENGTH_LONG).show();
                                        auth.signOut();
                                        return;
                                    }

                                    // Verificar si el usuario está activo
                                    Boolean activo = documentSnapshot.getBoolean("activo");

                                    // Si el campo "activo" no existe, verificar si tiene "estado" (usuarios antiguos)
                                    if (activo == null) {
                                        String estado = documentSnapshot.getString("estado");
                                        // Si tiene estado="activo" o no tiene ninguno de los dos campos, considerar como activo
                                        // Esto es para compatibilidad con usuarios creados antes del cambio
                                        activo = (estado == null || estado.equalsIgnoreCase("activo"));

                                        // Actualizar el campo para futuros logins
                                        if (activo) {
                                            db.collection("usuarios")
                                                    .document(user.getUid())
                                                    .update("activo", true)
                                                    .addOnFailureListener(e ->
                                                        android.util.Log.e("LoginFragment", "Error actualizando campo activo", e)
                                                    );
                                        }
                                    }

                                    if (!activo) {
                                        // El usuario está inactivo (eliminado)
                                        showLoading(false);
                                        btnLogin.setEnabled(true);
                                        btnLogin.setText("Iniciar sesión");
                                        if (tilEmail != null) {
                                            tilEmail.setError("Esta cuenta ha sido desactivada");
                                            tilEmail.setErrorEnabled(true);
                                        }
                                        Toast.makeText(getContext(),
                                                "Tu cuenta ha sido desactivada. Contacta al administrador",
                                                Toast.LENGTH_LONG).show();
                                        auth.signOut();
                                        return;
                                    }

                                    // Usuario activo, continuar con el login
                                    btnLogin.setText("Finalizando...");

                                    // Actualizar emailVerificado en Firestore
                                    db.collection("usuarios")
                                            .document(user.getUid())
                                            .update("emailVerificado", true)
                                            .addOnSuccessListener(aVoid -> {
                                                showLoading(false);
                                                btnLogin.setText("Iniciar sesión");
                                                Toast.makeText(getContext(), "Bienvenido ✅", Toast.LENGTH_SHORT).show();

                                                NavOptions navOptions = new NavOptions.Builder()
                                                        .setEnterAnim(R.anim.fade_in)
                                                        .setExitAnim(R.anim.fade_out)
                                                        .build();
                                                Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_calendarFragment, null, navOptions);
                                            })
                                            .addOnFailureListener(e -> {
                                                // Si falla la actualización, igual permitir continuar
                                                showLoading(false);
                                                btnLogin.setText("Iniciar sesión");
                                                Toast.makeText(getContext(),
                                                        "Sesión iniciada (error al actualizar perfil)",
                                                        Toast.LENGTH_SHORT).show();

                                                NavOptions navOptions = new NavOptions.Builder()
                                                        .setEnterAnim(R.anim.fade_in)
                                                        .setExitAnim(R.anim.fade_out)
                                                        .build();
                                                Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_calendarFragment, null, navOptions);
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    // Error al consultar Firestore
                                    showLoading(false);
                                    btnLogin.setEnabled(true);
                                    btnLogin.setText("Iniciar sesión");
                                    Toast.makeText(getContext(),
                                            "Error al verificar estado de la cuenta: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                    auth.signOut();
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