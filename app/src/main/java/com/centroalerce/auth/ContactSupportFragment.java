package com.centroalerce.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ContactSupportFragment extends Fragment {

    private int resId(String name, String defType) {
        return requireContext().getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String name)     { return resId(name, "id"); }
    private int layout(String name) { return resId(name, "layout"); }

    private TextInputLayout tilEmail, tilTipo, tilMensaje;
    private TextInputEditText etEmail, etMensaje;
    private AutoCompleteTextView acTipoProblema;
    private MaterialButton btnEnviar, btnVolver;
    private FirebaseFirestore db;

    private static final String[] TIPOS_PROBLEMA = {
            "No puedo iniciar sesión",
            "Olvidé mi contraseña",
            "Problemas con mi cuenta",
            "Error en la aplicación",
            "Sugerencia o mejora",
            "Otro"
    };

    public ContactSupportFragment(){}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(layout("fragment_contact_support"), c, false);

        tilEmail   = v.findViewById(id("tilEmailContacto"));
        tilTipo    = v.findViewById(id("tilTipoProblema"));
        tilMensaje = v.findViewById(id("tilMensaje"));

        etEmail    = v.findViewById(id("etEmailContacto"));
        etMensaje  = v.findViewById(id("etMensaje"));
        acTipoProblema = v.findViewById(id("acTipoProblema"));

        btnEnviar  = v.findViewById(id("btnEnviarSolicitud"));
        btnVolver  = v.findViewById(id("btnVolverLogin"));

        db = FirebaseFirestore.getInstance();

        // Configurar dropdown de tipos de problema
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                TIPOS_PROBLEMA
        );
        if (acTipoProblema != null) {
            acTipoProblema.setAdapter(adapter);
        }

        // TextWatcher para habilitar/deshabilitar botón
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateFields();
                updateButtonState();
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        if (etEmail != null) etEmail.addTextChangedListener(watcher);
        if (etMensaje != null) etMensaje.addTextChangedListener(watcher);
        if (acTipoProblema != null) {
            acTipoProblema.addTextChangedListener(watcher);
        }

        if (btnEnviar != null) {
            btnEnviar.setEnabled(false);
            btnEnviar.setOnClickListener(x -> enviarSolicitud(v));
        }

        if (btnVolver != null) {
            btnVolver.setOnClickListener(x -> Navigation.findNavController(v).popBackStack());
        }

        return v;
    }

    private void validateFields() {
        String email = safeText(etEmail);
        String tipo = safeText(acTipoProblema);
        String mensaje = safeText(etMensaje);

        // Validar email
        if (!TextUtils.isEmpty(email)) {
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                setError(tilEmail, etEmail, "Formato de correo no válido");
            } else {
                clearError(tilEmail, etEmail);
            }
        } else {
            clearError(tilEmail, etEmail);
        }

        // Validar tipo (opcional, ya que es dropdown)
        if (!TextUtils.isEmpty(tipo)) {
            clearError(tilTipo, acTipoProblema);
        }

        // Validar mensaje
        if (!TextUtils.isEmpty(mensaje) && mensaje.length() < 10) {
            setError(tilMensaje, etMensaje, "El mensaje debe tener al menos 10 caracteres");
        } else {
            clearError(tilMensaje, etMensaje);
        }
    }

    private void updateButtonState() {
        String email = safeText(etEmail);
        String tipo = safeText(acTipoProblema);
        String mensaje = safeText(etMensaje);

        boolean valid = !TextUtils.isEmpty(email)
                && Patterns.EMAIL_ADDRESS.matcher(email).matches()
                && !TextUtils.isEmpty(tipo)
                && !TextUtils.isEmpty(mensaje)
                && mensaje.length() >= 10;

        if (btnEnviar != null) {
            btnEnviar.setEnabled(valid);
        }
    }

    private void enviarSolicitud(View root) {
        String email = safeText(etEmail);
        String tipo = safeText(acTipoProblema);
        String mensaje = safeText(etMensaje);

        // Validación final
        boolean valido = true;

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setError(tilEmail, etEmail, "Ingresa un correo válido");
            valido = false;
        }

        if (TextUtils.isEmpty(tipo)) {
            setError(tilTipo, acTipoProblema, "Selecciona el tipo de problema");
            valido = false;
        }

        if (TextUtils.isEmpty(mensaje) || mensaje.length() < 10) {
            setError(tilMensaje, etMensaje, "El mensaje debe tener al menos 10 caracteres");
            valido = false;
        }

        if (!valido) {
            toast("Completa todos los campos correctamente");
            return;
        }

        if (btnEnviar != null) btnEnviar.setEnabled(false);

        // Guardar en Firestore
        Map<String, Object> solicitud = new HashMap<>();
        solicitud.put("email", email);
        solicitud.put("tipoProblema", tipo);
        solicitud.put("mensaje", mensaje);
        solicitud.put("estado", "pendiente");
        solicitud.put("fechaCreacion", System.currentTimeMillis());

        db.collection("solicitudes_soporte")
                .add(solicitud)
                .addOnSuccessListener(docRef -> {
                    toast("Solicitud enviada correctamente. Te contactaremos pronto ✅");
                    Navigation.findNavController(root).popBackStack();
                })
                .addOnFailureListener(e -> {
                    if (btnEnviar != null) btnEnviar.setEnabled(true);
                    toast("Error al enviar la solicitud: " + e.getMessage());
                });
    }

    private String safeText(TextInputEditText et) {
        return (et != null && et.getText() != null) ? et.getText().toString().trim() : "";
    }

    private String safeText(AutoCompleteTextView ac) {
        return (ac != null && ac.getText() != null) ? ac.getText().toString().trim() : "";
    }

    private void setError(TextInputLayout til, View input, String msg) {
        if (til != null) {
            til.setErrorEnabled(true);
            til.setError(msg);
        }
        if (input instanceof TextInputEditText) {
            ((TextInputEditText) input).setError(msg);
        } else if (input instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) input).setError(msg);
        }
    }

    private void clearError(TextInputLayout til, View input) {
        if (til != null) {
            til.setError(null);
            til.setErrorEnabled(false);
        }
        if (input instanceof TextInputEditText) {
            ((TextInputEditText) input).setError(null);
        } else if (input instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) input).setError(null);
        }
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}