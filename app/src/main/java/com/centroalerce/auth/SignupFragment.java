package com.centroalerce.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.SignInMethodQueryResult;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SignupFragment extends Fragment {

    private static final Set<String> ALLOWED_DOMAINS = new HashSet<>(Arrays.asList(
            "gmail.com"
    ));

    private static final Set<String> DISPOSABLE_DOMAINS = new HashSet<>(Arrays.asList(
            "tempmail.com", "10minutemail.com", "guerrillamail.com",
            "mailinator.com", "yopmail.com", "trashmail.com"
    ));

    private int resId(String name, String defType) {
        return requireContext().getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String name)     { return resId(name, "id"); }
    private int layout(String name) { return resId(name, "layout"); }

    private TextInputLayout tilEmail, tilPass, tilPass2;
    private TextInputEditText etEmail, etPass, etPass2;
    private MaterialButton btnCrear, btnIrLogin;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public SignupFragment(){}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(layout("fragment_signup"), c, false);

        // 1) Intentar con IDs nuevos
        tilEmail = v.findViewById(id("tilEmailSignup"));
        tilPass  = v.findViewById(id("tilPassSignup"));
        tilPass2 = v.findViewById(id("tilPass2Signup"));
        etEmail  = v.findViewById(id("etEmailSignup"));
        etPass   = v.findViewById(id("etPassSignup"));
        etPass2  = v.findViewById(id("etPass2Signup"));
        btnCrear = v.findViewById(id("btnSignup"));
        btnIrLogin = v.findViewById(id("btnGoLogin"));

        // 2) Compat: si alguno es null, prueba con tus IDs antiguos
        if (etEmail == null)  etEmail  = v.findViewById(id("etEmail"));
        if (etPass == null)   etPass   = v.findViewById(id("etPass"));
        if (etPass2 == null)  etPass2  = v.findViewById(id("etPass2"));
        if (btnCrear == null) btnCrear = v.findViewById(id("btnCrearCuenta"));
        if (btnIrLogin == null) btnIrLogin = v.findViewById(id("btnIrALogin"));

        // Si no hay TILs con ID, intenta obtener el parent del EditText
        if (tilEmail == null && etEmail != null && etEmail.getParent() instanceof TextInputLayout) {
            tilEmail = (TextInputLayout) etEmail.getParent();
        }
        if (tilPass == null && etPass != null && etPass.getParent() instanceof TextInputLayout) {
            tilPass = (TextInputLayout) etPass.getParent();
        }
        if (tilPass2 == null && etPass2 != null && etPass2.getParent() instanceof TextInputLayout) {
            tilPass2 = (TextInputLayout) etPass2.getParent();
        }

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateEmailField();
                validatePasswordFields();
                if (btnCrear != null) btnCrear.setEnabled(canEnableSubmit());
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        if (etEmail != null) etEmail.addTextChangedListener(watcher);
        if (etPass  != null) etPass.addTextChangedListener(watcher);
        if (etPass2 != null) etPass2.addTextChangedListener(watcher);

        if (btnCrear != null) {
            btnCrear.setEnabled(false);
            btnCrear.setOnClickListener(x -> doSignup(v));
        }

        if (btnIrLogin != null) {
            btnIrLogin.setOnClickListener(x -> Navigation.findNavController(v).popBackStack());
        }

        return v;
    }

    // ====== Validaciones ======

    private boolean canEnableSubmit() {
        String email = safeText(etEmail);
        String pass  = safeText(etPass);
        String pass2 = safeText(etPass2);
        return !TextUtils.isEmpty(email) && !TextUtils.isEmpty(pass) && !TextUtils.isEmpty(pass2)
                && isEmailAllowed(email) && isStrongPassword(pass) && pass.equals(pass2);
    }

    private void validateEmailField() {
        String email = safeText(etEmail);

        if (TextUtils.isEmpty(email)) { clearFieldError(tilEmail, etEmail); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { setFieldError(tilEmail, etEmail, "Formato de correo no válido"); return; }
        if (!isDomainAllowed(email)) { setFieldError(tilEmail, etEmail, "Usa tu correo institucional permitido"); return; }
        if (isDisposable(email)) { setFieldError(tilEmail, etEmail, "Correos desechables no permitidos"); return; }
        clearFieldError(tilEmail, etEmail);
    }

    private void validatePasswordFields() {
        String p1 = safeText(etPass);
        String p2 = safeText(etPass2);

        if (!TextUtils.isEmpty(p1) && !isStrongPassword(p1)) {
            setFieldError(tilPass, etPass, "Min 8 con mayúscula, minúscula, número y símbolo; sin espacios");
        } else {
            clearFieldError(tilPass, etPass);
        }

        if (!TextUtils.isEmpty(p2) && !p1.equals(p2)) {
            setFieldError(tilPass2, etPass2, "Las contraseñas no coinciden");
        } else {
            clearFieldError(tilPass2, etPass2);
        }
    }

    private boolean isEmailAllowed(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
                && isDomainAllowed(email)
                && !isDisposable(email);
    }

    private boolean isDomainAllowed(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return false;
        String domain = email.substring(at + 1).toLowerCase();
        return ALLOWED_DOMAINS.contains(domain);
    }

    private boolean isDisposable(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return true;
        String domain = email.substring(at + 1).toLowerCase();
        return DISPOSABLE_DOMAINS.contains(domain);
    }

    private boolean isStrongPassword(String pass) {
        if (TextUtils.isEmpty(pass)) return false;
        if (pass.contains(" ")) return false;
        String regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";
        return pass.matches(regex);
    }

    private String safeText(TextInputEditText et) {
        return (et != null && et.getText()!=null) ? et.getText().toString().trim() : "";
    }

    // ====== Flujo de Signup ======

    private void doSignup(View root){
        String email = safeText(etEmail);
        String pass  = safeText(etPass);
        String pass2 = safeText(etPass2);

        boolean valido = true;

        // --- Email ---
        if (TextUtils.isEmpty(email)) {
            setFieldError(tilEmail, etEmail, "Ingresa tu correo institucional");
            valido = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setFieldError(tilEmail, etEmail, "Formato de correo no válido");
            valido = false;
        } else if (!isDomainAllowed(email)) {
            setFieldError(tilEmail, etEmail, "Registro interno: usa tu correo institucional permitido");
            valido = false;
        } else if (isDisposable(email)) {
            setFieldError(tilEmail, etEmail, "Correos desechables no permitidos");
            valido = false;
        } else {
            clearFieldError(tilEmail, etEmail);
        }

        // --- Contraseña ---
        if (TextUtils.isEmpty(pass)) {
            setFieldError(tilPass, etPass, "Ingresa una contraseña");
            valido = false;
        } else if (!isStrongPassword(pass)) {
            setFieldError(tilPass, etPass, "Debe tener mínimo 8 caracteres, mayúscula, minúscula, número y símbolo");
            valido = false;
        } else {
            clearFieldError(tilPass, etPass);
        }

        // --- Confirmación ---
        if (TextUtils.isEmpty(pass2)) {
            setFieldError(tilPass2, etPass2, "Confirma tu contraseña");
            valido = false;
        } else if (!pass.equals(pass2)) {
            setFieldError(tilPass2, etPass2, "Las contraseñas no coinciden");
            valido = false;
        } else {
            clearFieldError(tilPass2, etPass2);
        }

        if (!valido) {
            if (tilEmail != null && tilEmail.getError() != null && etEmail != null) etEmail.requestFocus();
            else if (tilPass != null && tilPass.getError() != null && etPass != null) etPass.requestFocus();
            else if (tilPass2 != null && tilPass2.getError() != null && etPass2 != null) etPass2.requestFocus();
            toast("Revisa los campos marcados ❌");
            return;
        }

        if (btnCrear != null) btnCrear.setEnabled(false);

        // 1) Verificar si el correo ya existe
        FirebaseAuth.getInstance().fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (btnCrear != null) btnCrear.setEnabled(true); // reactivar pase lo que pase

                    if (!task.isSuccessful()) {
                        toast("No se pudo validar el correo, intenta nuevamente");
                        return;
                    }
                    SignInMethodQueryResult r = task.getResult();
                    boolean yaRegistrado = r != null && r.getSignInMethods() != null && !r.getSignInMethods().isEmpty();
                    if (yaRegistrado) {
                        setFieldError(tilEmail, etEmail, "Este correo ya está registrado");
                        toast("El correo ya está registrado");
                    } else {
                        // 2) Crear
                        crearEnFirebase(root, email, pass);
                    }
                });
    }

    private void crearEnFirebase(View root, String email, String pass) {
        if (btnCrear != null) btnCrear.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(t -> {
                    if (!t.isSuccessful()) {
                        if (btnCrear != null) btnCrear.setEnabled(true);
                        toast(t.getException()!=null ? t.getException().getMessage() : "Error al crear la cuenta");
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        if (btnCrear != null) btnCrear.setEnabled(true);
                        toast("Error: usuario no disponible tras crear");
                        return;
                    }

                    Map<String, Object> perfil = new HashMap<>();
                    perfil.put("uid", user.getUid());
                    perfil.put("email", email);
                    perfil.put("rol", "usuario");
                    perfil.put("estado", "activo");
                    perfil.put("emailVerificado", false);
                    perfil.put("creadoEn", System.currentTimeMillis());

                    FirebaseFirestore.getInstance().collection("usuarios").document(user.getUid())
                            .set(perfil)
                            .addOnSuccessListener(v -> {
                                user.sendEmailVerification()
                                        .addOnSuccessListener(x -> {
                                            toast("Cuenta creada. Revisa tu correo para verificarla ✅");
                                            auth.signOut();
                                            if (btnCrear != null) btnCrear.setEnabled(true);
                                            Navigation.findNavController(root).popBackStack();
                                        })
                                        .addOnFailureListener(e -> {
                                            toast("Cuenta creada, pero no se pudo enviar verificación: " + e.getMessage());
                                            auth.signOut();
                                            if (btnCrear != null) btnCrear.setEnabled(true);
                                            Navigation.findNavController(root).popBackStack();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                if (btnCrear != null) btnCrear.setEnabled(true);
                                toast("Error guardando perfil: " + e.getMessage());
                            });
                });
    }

    // ====== Helpers de error: TIL + EditText ======
    private void setFieldError(TextInputLayout til, TextInputEditText et, String msg) {
        if (til != null) {
            til.setErrorEnabled(true);
            til.setError(msg);
        }
        if (et != null) {
            et.setError(msg); // fallback visual extra (burbuja/ícono)
        }
    }
    private void clearFieldError(TextInputLayout til, TextInputEditText et) {
        if (til != null) {
            til.setError(null);
            til.setErrorEnabled(false);
        }
        if (et != null) {
            et.setError(null);
        }
    }

    private void toast(String m){
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
