package com.centroalerce.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.Task;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.NavOptions;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.adapters.EmailAutocompleteAdapter;
import com.centroalerce.gestion.utils.EmailHistoryManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class LoginFragment extends Fragment {

    private AutoCompleteTextView etEmail;
    private TextInputEditText etPass;
    private com.google.android.material.textfield.TextInputLayout tilEmail, tilPass;
    private MaterialButton btnLogin;
    private CheckBox cbRememberMe;
    private TextView tvRememberLabel;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private EmailHistoryManager emailHistoryManager;
    private EmailAutocompleteAdapter emailAdapter;

    public LoginFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_login, c, false);

        // Inicializar componentes
        etEmail = v.findViewById(R.id.etEmail);
        etPass  = v.findViewById(R.id.etPass);
        try { tilEmail = (com.google.android.material.textfield.TextInputLayout) ((View) etEmail.getParent()).getParent(); } catch (Exception ignore) {}
        try { tilPass  = (com.google.android.material.textfield.TextInputLayout) ((View) etPass.getParent()).getParent(); } catch (Exception ignore) {}
        btnLogin= v.findViewById(R.id.btnLogin);
        cbRememberMe = v.findViewById(R.id.cbRememberMe);
        progressBar = v.findViewById(R.id.progressBarLogin);
        TextView tvForgot = v.findViewById(R.id.tvForgot);

        // Inicializar sistema de historial de emails
        emailHistoryManager = new EmailHistoryManager(requireContext());

        // Configurar autocompletado de emails
        setupEmailAutocomplete();

        // Cargar último email si estaba marcado "recordar"
        loadRememberedEmail();

        // TextWatcher: limpiar errores mientras escribe
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

        // Inicializar Firebase
        if (auth == null) auth = FirebaseAuth.getInstance();
        if (db == null) db = FirebaseFirestore.getInstance();
        auth.setLanguageCode("es");

        // Configurar listeners
        btnLogin.setOnClickListener(x -> doLogin(v));
        tvForgot.setOnClickListener(x ->
                Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        );

        // Iniciar animaciones de entrada
        startEntranceAnimations(v);

        return v;
    }

    /**
     * Configura el autocompletado de emails
     */
    private void setupEmailAutocomplete() {
        // Obtener historial de emails
        List<String> emailHistory = emailHistoryManager.getEmailHistory();

        // Crear adaptador
        emailAdapter = new EmailAutocompleteAdapter(requireContext(), emailHistory);
        etEmail.setAdapter(emailAdapter);

        // Configurar threshold (cuántos caracteres antes de mostrar sugerencias)
        etEmail.setThreshold(1);

        // Listener cuando se selecciona un email
        etEmail.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedEmail = (String) parent.getItemAtPosition(position);
                etEmail.setText(selectedEmail);
                etEmail.setSelection(selectedEmail.length());

                // Marcar checkbox si este email estaba recordado
                if (selectedEmail.equals(emailHistoryManager.getLastEmail())) {
                    cbRememberMe.setChecked(true);
                }
            }
        });

        // Mostrar todas las sugerencias al hacer clic
        etEmail.setOnClickListener(v -> {
            if (etEmail.getText().toString().isEmpty()) {
                etEmail.showDropDown();
            }
        });

        // Actualizar adapter cuando cambia el texto
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // El adapter ya tiene su propio filtro, solo aseguramos que se muestre
                if (s.length() == 0) {
                    emailAdapter.updateEmails(emailHistoryManager.getEmailHistory());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Carga el email recordado si existe
     */
    private void loadRememberedEmail() {
        if (emailHistoryManager.shouldRememberEmail()) {
            String lastEmail = emailHistoryManager.getLastEmail();
            if (!lastEmail.isEmpty()) {
                etEmail.setText(lastEmail);
                etEmail.setSelection(lastEmail.length());
                cbRememberMe.setChecked(true);
            }
        }
    }

    /**
     * Animaciones profesionales de entrada para la pantalla de login
     */
    private void startEntranceAnimations(View root) {
        View logo = root.findViewById(R.id.ivLogo);
        View title = root.findViewById(R.id.tvTitle);
        View subtitle1 = root.findViewById(R.id.tvSubtitle1);
        View subtitle2 = root.findViewById(R.id.tvSubtitle2);
        View emailLayout = root.findViewById(R.id.tilEmail);
        View passLayout = root.findViewById(R.id.tilPassword);
        View rememberLayout = root.findViewById(R.id.llRememberMe);
        View forgotLink = root.findViewById(R.id.tvForgot);
        View loginButton = root.findViewById(R.id.btnLogin);

        View[] views = {logo, title, subtitle1, subtitle2, emailLayout, passLayout, rememberLayout, forgotLink, loginButton};
        for (View v : views) {
            if (v != null) {
                v.setAlpha(0f);
                v.setTranslationY(30);
            }
        }

        animateView(logo, 0, 100);
        animateView(title, 150, 100);
        animateView(subtitle1, 200, 100);
        animateView(subtitle2, 250, 100);
        animateView(emailLayout, 350, 100);
        animateView(passLayout, 400, 100);
        animateView(rememberLayout, 450, 100);
        animateView(forgotLink, 500, 100);
        animateView(loginButton, 550, 100);
    }

    private void animateView(View view, long delay, long duration) {
        if (view == null) return;
        view.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(duration + 400)
                .setStartDelay(delay)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();
    }

    private boolean isEmpty(TextInputEditText e){
        return e.getText()==null || e.getText().toString().trim().isEmpty();
    }

    private void doLogin(View root){
        if (etEmail == null || etPass == null) {
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
        if (!ok) return;

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

                    // ✅ LOGIN EXITOSO - Guardar email si está marcado "recordar"
                    boolean rememberMe = cbRememberMe.isChecked();
                    emailHistoryManager.saveLastEmail(email, rememberMe);

                    // Siempre agregar al historial (para autocompletado)
                    emailHistoryManager.saveEmail(email);

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        showLoading(false);
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Iniciar sesión");
                        Toast.makeText(getContext(), "Error: usuario no encontrado", Toast.LENGTH_LONG).show();
                        return;
                    }

                    btnLogin.setText("Verificando cuenta...");

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

                        btnLogin.setText("Verificando estado...");

                        if (db == null) db = FirebaseFirestore.getInstance();

                        db.collection("usuarios")
                                .document(user.getUid())
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (!documentSnapshot.exists()) {
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

                                    Boolean activo = documentSnapshot.getBoolean("activo");

                                    if (activo == null) {
                                        String estado = documentSnapshot.getString("estado");
                                        activo = (estado == null || estado.equalsIgnoreCase("activo"));

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

                                    btnLogin.setText("Finalizando...");

                                    db.collection("usuarios")
                                            .document(user.getUid())
                                            .update("emailVerificado", true)
                                            .addOnSuccessListener(aVoid -> {
                                                showLoading(false);
                                                btnLogin.setText("Iniciar sesión");

                                                NavOptions navOptions = new NavOptions.Builder()
                                                        .setEnterAnim(R.anim.fade_in)
                                                        .setExitAnim(R.anim.fade_out)
                                                        .build();
                                                Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_calendarFragment, null, navOptions);
                                            })
                                            .addOnFailureListener(e -> {
                                                showLoading(false);
                                                btnLogin.setText("Iniciar sesión");

                                                NavOptions navOptions = new NavOptions.Builder()
                                                        .setEnterAnim(R.anim.fade_in)
                                                        .setExitAnim(R.anim.fade_out)
                                                        .build();
                                                Navigation.findNavController(root).navigate(R.id.action_loginFragment_to_calendarFragment, null, navOptions);
                                            });
                                })
                                .addOnFailureListener(e -> {
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
}