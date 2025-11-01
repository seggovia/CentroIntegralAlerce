package com.centroalerce.ui.mantenedores.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.SocioComunitario;
import com.centroalerce.gestion.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SocioDialog extends DialogFragment {

    public interface OnSave { void save(SocioComunitario s); }

    @Nullable private final SocioComunitario original;
    @NonNull  private final OnSave onSave;

    public SocioDialog(@Nullable SocioComunitario original, @NonNull OnSave onSave) {
        this.original = original;
        this.onSave = onSave;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle b) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_socio, null, false);

        final TextInputEditText etNombre = v.findViewById(R.id.etNombre);
        final TextInputEditText etDescripcion = v.findViewById(R.id.etDescripcion);
        final TextInputEditText etCaracterizacion = v.findViewById(R.id.etCaracterizacion);
        final MaterialButton btnCancelar = v.findViewById(R.id.btnCancelar);
        final MaterialButton btnGuardar = v.findViewById(R.id.btnGuardar);

        com.google.android.material.textfield.TextInputLayout tilNombre = null;
        com.google.android.material.textfield.TextInputLayout tilDescripcion = null;
        com.google.android.material.textfield.TextInputLayout tilCaracterizacion = null;

        try { tilNombre = (com.google.android.material.textfield.TextInputLayout) ((View) etNombre.getParent()).getParent(); } catch (Exception ignore) {}
        try { tilDescripcion = (com.google.android.material.textfield.TextInputLayout) ((View) etDescripcion.getParent()).getParent(); } catch (Exception ignore) {}
        try { tilCaracterizacion = (com.google.android.material.textfield.TextInputLayout) ((View) etCaracterizacion.getParent()).getParent(); } catch (Exception ignore) {}

        final com.google.android.material.textfield.TextInputLayout tilNombreRef = tilNombre;
        final com.google.android.material.textfield.TextInputLayout tilDescripcionRef = tilDescripcion;
        final com.google.android.material.textfield.TextInputLayout tilCaracterizacionRef = tilCaracterizacion;

        if (original != null) {
            if (etNombre != null) etNombre.setText(original.getNombre());
            if (etDescripcion != null) etDescripcion.setText(original.getDescripcion());
            if (etCaracterizacion != null) etCaracterizacion.setText(original.getCaracterizacionBeneficiarios());
        }

        final Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // ✅ Limpiar errores al escribir
        etNombre.addTextChangedListener(new SimpleWatcher(() -> {
            if (tilNombreRef != null) tilNombreRef.setError(null);
        }));

        etDescripcion.addTextChangedListener(new SimpleWatcher(() -> {
            if (tilDescripcionRef != null) tilDescripcionRef.setError(null);
        }));

        etCaracterizacion.addTextChangedListener(new SimpleWatcher(() -> {
            if (tilCaracterizacionRef != null) tilCaracterizacionRef.setError(null);
        }));

        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(view -> dialog.dismiss());
        }

        if (btnGuardar != null) {
            btnGuardar.setOnClickListener(view -> {
                String nombre = "";
                if (etNombre != null && etNombre.getText() != null) {
                    nombre = etNombre.getText().toString().trim();
                }

                String descripcion = "";
                if (etDescripcion != null && etDescripcion.getText() != null) {
                    descripcion = etDescripcion.getText().toString().trim();
                }

                String caracterizacion = "";
                if (etCaracterizacion != null && etCaracterizacion.getText() != null) {
                    caracterizacion = etCaracterizacion.getText().toString().trim();
                }

                // ===== VALIDACIONES =====

                // 1️⃣ Nombre obligatorio
                if (!ValidationUtils.isNotEmpty(nombre)) {
                    if (tilNombreRef != null) {
                        tilNombreRef.setError(ValidationUtils.getErrorRequired());
                        tilNombreRef.setErrorEnabled(true);
                    }
                    else if (etNombre != null) etNombre.setError(ValidationUtils.getErrorRequired());
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 2️⃣ Nombre mínimo 3 caracteres
                if (!ValidationUtils.hasMinLength(nombre, 3)) {
                    if (tilNombreRef != null) {
                        tilNombreRef.setError(ValidationUtils.getErrorMinLength(3));
                        tilNombreRef.setErrorEnabled(true);
                    }
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 3️⃣ Nombre máximo 100 caracteres
                if (!ValidationUtils.hasMaxLength(nombre, 150)) {
                    if (tilNombreRef != null) {
                        tilNombreRef.setError(ValidationUtils.getErrorMaxLength(150));
                        tilNombreRef.setErrorEnabled(true);
                    }
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 4️⃣ Descripción máximo 500 caracteres (opcional)
                if (ValidationUtils.isNotEmpty(descripcion) && !ValidationUtils.hasMaxLength(descripcion, 500)) {
                    if (tilDescripcionRef != null) {
                        tilDescripcionRef.setError(ValidationUtils.getErrorMaxLength(500));
                        tilDescripcionRef.setErrorEnabled(true);
                    }
                    if (etDescripcion != null) etDescripcion.requestFocus();
                    return;
                }

                // 5️⃣ Caracterización máximo 500 caracteres (opcional)
                if (ValidationUtils.isNotEmpty(caracterizacion) && !ValidationUtils.hasMaxLength(caracterizacion, 500)) {
                    if (tilCaracterizacionRef != null) {
                        tilCaracterizacionRef.setError(ValidationUtils.getErrorMaxLength(500));
                        tilCaracterizacionRef.setErrorEnabled(true);
                    }
                    if (etCaracterizacion != null) etCaracterizacion.requestFocus();
                    return;
                }

                // 6️⃣ Caracteres seguros
                if (!ValidationUtils.isSafeText(nombre) ||
                        !ValidationUtils.isSafeText(descripcion) ||
                        !ValidationUtils.isSafeText(caracterizacion)) {
                    if (tilNombreRef != null) {
                        tilNombreRef.setError(ValidationUtils.getErrorUnsafeCharacters());
                        tilNombreRef.setErrorEnabled(true);
                    }
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                SocioComunitario s = (original == null)
                        ? new SocioComunitario(null, nombre, descripcion, caracterizacion, true)
                        : new SocioComunitario(original.getId(), nombre, descripcion, caracterizacion, original.isActivo());

                onSave.save(s);
                dialog.dismiss();
            });
        }

        return dialog;
    }

    private static class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable onAfter;
        SimpleWatcher(Runnable onAfter) { this.onAfter = onAfter; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onAfter.run(); }
    }
}
