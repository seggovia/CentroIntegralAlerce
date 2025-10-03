package com.centroalerce.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class LoginFragment extends Fragment {

    // --- helpers para no usar R ---
    private int resId(String name, String defType) {
        return requireContext().getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    private TextInputEditText etEmail, etPass;
    private MaterialButton btnLogin, btnCrearCuenta;
    private TextView tvForgot, tvCreateInlineLink;

    private FirebaseAuth auth;

    public LoginFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(layout("fragment_login"), c, false);

        // bind
        etEmail = v.findViewById(id("etEmail"));
        etPass  = v.findViewById(id("etPass"));
        btnLogin= v.findViewById(id("btnLogin"));
        tvForgot= v.findViewById(id("tvForgot"));
        btnCrearCuenta = v.findViewById(id("btnCrearCuenta"));
        tvCreateInlineLink = v.findViewById(id("tvCreateInlineLink"));

        auth = FirebaseAuth.getInstance();

        // habilitar/deshabilitar botón
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnLogin != null) {
                    btnLogin.setEnabled(!isEmpty(etEmail) && !isEmpty(etPass));
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        if (etEmail != null) etEmail.addTextChangedListener(watcher);
        if (etPass  != null) etPass.addTextChangedListener(watcher);

        if (btnLogin != null) btnLogin.setOnClickListener(x -> doLogin());
        if (tvForgot != null) tvForgot.setOnClickListener(x -> doForgot());

        // Navegar a Signup (sin R) usando el NavController del fragment en el momento del click
        View.OnClickListener goSignup = x -> {
            int signupId = id("signupFragment");
            if (signupId != 0) {
                androidx.navigation.fragment.NavHostFragment
                        .findNavController(this)
                        .navigate(signupId);
            } else {
                toast("Destino 'signupFragment' no encontrado en nav_graph");
            }
        };
        if (btnCrearCuenta != null) btnCrearCuenta.setOnClickListener(goSignup);
        if (tvCreateInlineLink != null) tvCreateInlineLink.setOnClickListener(goSignup);

        return v;
    }

    // 👇️ Importante: SIN auto-redirect aquí.
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        // No redirigir automáticamente si hay sesión previa.
        // Deja que el usuario pulse "Iniciar sesión".
    }

    private boolean isEmpty(@Nullable TextInputEditText e) {
        return e == null || e.getText() == null || e.getText().toString().trim().isEmpty();
    }

    // --------- Login principal ----------
    private void doLogin() {
        if (etEmail == null || etPass == null) return;

        String email = etEmail.getText().toString().trim();
        String pass  = etPass.getText().toString();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
            toast("Completa email y contraseña");
            return;
        }

        hideKeyboard(requireView());
        setBusy(true);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    setBusy(false);

                    if (!task.isSuccessful()) {
                        Throwable e = task.getException();
                        String msg = "No se pudo iniciar sesión";
                        if (e instanceof FirebaseAuthInvalidUserException)       msg = "El usuario no existe o fue deshabilitado";
                        else if (e instanceof FirebaseAuthInvalidCredentialsException) msg = "Credenciales inválidas";
                        else if (e != null && e.getMessage() != null)            msg = e.getMessage();
                        toast(msg);
                        return;
                    }

                    // ✅ Verificación obligatoria de correo
                    com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
                    if (user == null) { toast("No se pudo obtener el usuario"); return; }

                    if (!user.isEmailVerified()) {
                        // Reenviar verificación y bloquear acceso
                        user.sendEmailVerification()
                                .addOnSuccessListener(v -> toast("Debes verificar tu correo. Te reenviamos el email de verificación."))
                                .addOnFailureListener(e -> toast("Debes verificar tu correo (no se pudo reenviar: " + (e!=null?e.getMessage():"") + ")"));
                        auth.signOut(); // ⛔️ Cerrar sesión si no está verificado
                        return;
                    }

                    // OK verificado → Home
                    navigateHome();
                });
    }
    @Override
    public void onStart() {
        super.onStart();
        try {
            com.google.firebase.auth.FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null && !u.isEmailVerified()) {
                FirebaseAuth.getInstance().signOut(); // impide que un usuario no verificado quede autenticado al abrir la app
            }
        } catch (Exception ignored) {}
    }


    // --------- Olvidé mi contraseña ----------
    private void doForgot() {
        if (etEmail == null) return;
        String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            toast("Ingresa tu email para recuperar");
            return;
        }
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> toast("Te enviamos un correo para restablecer"))
                .addOnFailureListener(e ->
                        toast(e != null && e.getMessage() != null ? e.getMessage() : "No se pudo enviar el correo"));
    }

    // --------- Navegación a Home (sin View y sin R) ----------
    private void navigateHome() {
        final int homeId  = requireContext().getResources().getIdentifier(
                "homeFragment", "id", requireContext().getPackageName()
        );
        final int loginId = requireContext().getResources().getIdentifier(
                "loginFragment", "id", requireContext().getPackageName()
        );

        if (homeId == 0) { toast("Destino 'homeFragment' no encontrado en nav_graph"); return; }

        requireView().post(() -> {
            try {
                androidx.navigation.NavController nav =
                        androidx.navigation.fragment.NavHostFragment.findNavController(this);

                androidx.navigation.NavOptions opts = new androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(loginId != 0 ? loginId : nav.getGraph().getStartDestinationId(), true)
                        .setLaunchSingleTop(true)
                        .build();

                nav.navigate(homeId, null, opts);
            } catch (Exception ex) {
                toast("No se pudo navegar a Home: " + ex.getMessage());
            }
        });
    }

    // --------- Utilidades ----------
    private void setBusy(boolean busy) {
        if (btnLogin != null) btnLogin.setEnabled(!busy);
    }

    private void hideKeyboard(View root) {
        try {
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
