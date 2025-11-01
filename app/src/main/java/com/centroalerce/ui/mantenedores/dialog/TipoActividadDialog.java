package com.centroalerce.ui.mantenedores.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.TipoActividad;
import com.centroalerce.gestion.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class TipoActividadDialog extends DialogFragment {

    public interface OnGuardar {
        void onGuardar(TipoActividad tipo);
    }

    private final TipoActividad original;
    private final OnGuardar callback;

    public TipoActividadDialog(@Nullable TipoActividad original, @NonNull OnGuardar callback) {
        this.original = original;
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_tipo_actividad, null);

        // Referencias
        TextInputLayout tilNombre = v.findViewById(R.id.tilNombreTipo);
        TextInputLayout tilDescripcion = v.findViewById(R.id.tilDescripcionTipo);
        SwitchMaterial swActivo = v.findViewById(R.id.swActivoTipo);
        MaterialButton btnCancelar = v.findViewById(R.id.btnCancelar);
        MaterialButton btnGuardar = v.findViewById(R.id.btnGuardar);

        TextInputEditText etNombre = (TextInputEditText) tilNombre.getEditText();
        TextInputEditText etDescripcion = (TextInputEditText) tilDescripcion.getEditText();

        // Prellenar si es edición
        if (original != null) {
            if (etNombre != null) etNombre.setText(original.getNombre());
            if (etDescripcion != null) etDescripcion.setText(original.getDescripcion());
            swActivo.setChecked(original.isActivo());
        } else {
            swActivo.setChecked(true);
        }

        // Crear diálogo
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // ✅ Limpiar errores al escribir
        etNombre.addTextChangedListener(new SimpleWatcher(() -> tilNombre.setError(null)));
        etDescripcion.addTextChangedListener(new SimpleWatcher(() -> tilDescripcion.setError(null)));

        // Botón cancelar
        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(view -> dialog.dismiss());
        }

        // Botón guardar
        if (btnGuardar != null) {
            btnGuardar.setOnClickListener(view -> {
                String nombre = getText(etNombre);
                String descripcion = getText(etDescripcion);

                // ===== VALIDACIONES =====

                // 1️⃣ Nombre obligatorio
                if (!ValidationUtils.isNotEmpty(nombre)) {
                    tilNombre.setError(ValidationUtils.getErrorRequired());
                    tilNombre.setErrorEnabled(true);
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 2️⃣ Nombre mínimo 3 caracteres
                if (!ValidationUtils.hasMinLength(nombre, 3)) {
                    tilNombre.setError(ValidationUtils.getErrorMinLength(3));
                    tilNombre.setErrorEnabled(true);
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 3️⃣ Nombre máximo 100 caracteres
                if (!ValidationUtils.hasMaxLength(nombre, 100)) {
                    tilNombre.setError(ValidationUtils.getErrorMaxLength(100));
                    tilNombre.setErrorEnabled(true);
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 4️⃣ Caracteres seguros
                if (!ValidationUtils.isSafeText(nombre) || !ValidationUtils.isSafeText(descripcion)) {
                    tilNombre.setError(ValidationUtils.getErrorUnsafeCharacters());
                    tilNombre.setErrorEnabled(true);
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 5️⃣ Descripción máximo 500 caracteres (opcional pero si existe)
                if (ValidationUtils.isNotEmpty(descripcion) && !ValidationUtils.hasMaxLength(descripcion, 500)) {
                    tilDescripcion.setError(ValidationUtils.getErrorMaxLength(500));
                    tilDescripcion.setErrorEnabled(true);
                    if (etDescripcion != null) etDescripcion.requestFocus();
                    return;
                }

                // ✅ Limpiar errores
                tilNombre.setError(null);
                tilNombre.setErrorEnabled(false);
                tilDescripcion.setError(null);
                tilDescripcion.setErrorEnabled(false);

                boolean activo = swActivo.isChecked();

                TipoActividad tipo = new TipoActividad();

                if (original != null) {
                    tipo.setId(original.getId());
                }

                tipo.setNombre(nombre);
                tipo.setDescripcion(descripcion);
                tipo.setActivo(activo);

                callback.onGuardar(tipo);
                dialog.dismiss();
            });
        }

        return dialog;
    }

    private String getText(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private static class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable onAfter;
        SimpleWatcher(Runnable onAfter) { this.onAfter = onAfter; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onAfter.run(); }
    }
}