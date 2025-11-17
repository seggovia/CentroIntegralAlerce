package com.centroalerce.ui.mantenedores.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Oferente;
import com.centroalerce.gestion.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class OferenteDialog extends DialogFragment {

    public interface OnGuardar { void onGuardar(Oferente o); }

    private final @Nullable Oferente original;
    private final @NonNull OnGuardar callback;

    public OferenteDialog(@Nullable Oferente original, @NonNull OnGuardar callback) {
        this.original = original;
        this.callback = callback;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_oferente, null, false);

        TextInputLayout tilNombre   = v.findViewById(R.id.tilNombre);
        TextInputEditText etNombre  = v.findViewById(R.id.etNombre);
        TextInputEditText etDocente = v.findViewById(R.id.etDocente);
        TextInputEditText etCarrera = v.findViewById(R.id.etCarrera);
        MaterialButton btnCancelar  = v.findViewById(R.id.btnCancelar);
        MaterialButton btnGuardar   = v.findViewById(R.id.btnGuardar);

        if (original != null) {
            if (original.getNombre() != null) etNombre.setText(original.getNombre());
            if (original.getDocenteResponsable() != null) etDocente.setText(original.getDocenteResponsable());
            if (original.getCarrera() != null) etCarrera.setText(original.getCarrera());
        }

        final Dialog d = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .create();

        btnGuardar.setEnabled(true);

        // ✅ Limpiar errores al escribir
        etNombre.addTextChangedListener(new SimpleWatcher(() -> {
            if (tilNombre != null) tilNombre.setError(null);
        }));

        btnCancelar.setOnClickListener(x -> d.dismiss());

        btnGuardar.setOnClickListener(x -> {
            String nombre  = safeText(etNombre);
            String docente = safeText(etDocente);
            String carrera = safeText(etCarrera);

            // ===== VALIDACIONES =====

            // 1️⃣ Nombre obligatorio
            if (!ValidationUtils.isNotEmpty(nombre)) {
                if (tilNombre != null) {
                    tilNombre.setError(ValidationUtils.getErrorRequired());
                    tilNombre.setErrorEnabled(true);
                }
                if (etNombre != null) etNombre.requestFocus();
                return;
            }

            // 2️⃣ Nombre mínimo 3 caracteres
            if (!ValidationUtils.hasMinLength(nombre, 3)) {
                if (tilNombre != null) {
                    tilNombre.setError(ValidationUtils.getErrorMinLength(3));
                    tilNombre.setErrorEnabled(true);
                }
                if (etNombre != null) etNombre.requestFocus();
                return;
            }

            // 3️⃣ Nombre solo letras
            if (!ValidationUtils.isValidName(nombre)) {
                if (tilNombre != null) {
                    tilNombre.setError(ValidationUtils.getErrorInvalidName());
                    tilNombre.setErrorEnabled(true);
                }
                if (etNombre != null) etNombre.requestFocus();
                return;
            }

            // 4️⃣ Nombre máximo 100 caracteres
            if (!ValidationUtils.hasMaxLength(nombre, 100)) {
                if (tilNombre != null) {
                    tilNombre.setError(ValidationUtils.getErrorMaxLength(100));
                    tilNombre.setErrorEnabled(true);
                }
                if (etNombre != null) etNombre.requestFocus();
                return;
            }

            // 5️⃣ Validar caracteres seguros
            if (!ValidationUtils.isSafeText(nombre) ||
                    !ValidationUtils.isSafeText(docente) ||
                    !ValidationUtils.isSafeText(carrera)) {
                if (tilNombre != null) {
                    tilNombre.setError(ValidationUtils.getErrorUnsafeCharacters());
                    tilNombre.setErrorEnabled(true);
                }
                return;
            }

            // ✅ Limpiar errores antes de guardar
            if (tilNombre != null) {
                tilNombre.setError(null);
                tilNombre.setErrorEnabled(false);
            }

            // ✅ Crear nuevo objeto en vez de modificar el original
            Oferente o = (original == null)
                    ? new Oferente(nombre, docente, carrera, true)
                    : new Oferente(original.getId(), nombre, docente, carrera, original.isActivo());

            callback.onGuardar(o);
            d.dismiss();
        });

        return d;
    }



    private static class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable onAfter;
        SimpleWatcher(Runnable onAfter) { this.onAfter = onAfter; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onAfter.run(); }
    }

    private String safeText(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }


}