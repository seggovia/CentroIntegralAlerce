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
import com.centroalerce.gestion.models.Lugar;
import com.centroalerce.gestion.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class LugarDialog extends DialogFragment {

    public interface OnSave { void save(Lugar lugar); }

    @Nullable private final Lugar original;
    @NonNull  private final OnSave onSave;

    public LugarDialog(@Nullable Lugar original, @NonNull OnSave onSave) {
        this.original = original;
        this.onSave   = onSave;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_lugar, null, false);

        final TextInputEditText etNombre = v.findViewById(R.id.etNombre);
        final TextInputEditText etCupo   = v.findViewById(R.id.etCupo);
        com.google.android.material.textfield.TextInputLayout tilNombre = null;
        com.google.android.material.textfield.TextInputLayout tilCupo = null;
        try { tilNombre = (com.google.android.material.textfield.TextInputLayout) ((View) etNombre.getParent()).getParent(); } catch (Exception ignore) {}
        try { tilCupo   = (com.google.android.material.textfield.TextInputLayout) ((View) etCupo.getParent()).getParent(); } catch (Exception ignore) {}
        final com.google.android.material.textfield.TextInputLayout tilNombreRef = tilNombre;
        final com.google.android.material.textfield.TextInputLayout tilCupoRef = tilCupo;
        final MaterialButton btnCancelar = v.findViewById(R.id.btnCancelar);
        final MaterialButton btnGuardar  = v.findViewById(R.id.btnGuardar);

        if (original != null) {
            if (etNombre != null) etNombre.setText(original.getNombre());
            if (etCupo != null && original.getCupo() != null) {
                etCupo.setText(String.valueOf(original.getCupo()));
            }
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

        etCupo.addTextChangedListener(new SimpleWatcher(() -> {
            if (tilCupoRef != null) tilCupoRef.setError(null);
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
                if (!ValidationUtils.hasMaxLength(nombre, 100)) {
                    if (tilNombreRef != null) {
                        tilNombreRef.setError(ValidationUtils.getErrorMaxLength(100));
                        tilNombreRef.setErrorEnabled(true);
                    }
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                // 4️⃣ Caracteres seguros
                if (!ValidationUtils.isSafeText(nombre)) {
                    if (tilNombreRef != null) {
                        tilNombreRef.setError(ValidationUtils.getErrorUnsafeCharacters());
                        tilNombreRef.setErrorEnabled(true);
                    }
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                Integer cupo = null;
                if (etCupo != null && etCupo.getText() != null && etCupo.getText().length() > 0) {
                    String cupoText = etCupo.getText().toString().trim();

                    // 5️⃣ Validar que sea un número
                    if (!ValidationUtils.isPositiveNumber(cupoText)) {
                        if (tilCupoRef != null) {
                            tilCupoRef.setError(ValidationUtils.getErrorInvalidNumber());
                            tilCupoRef.setErrorEnabled(true);
                        }
                        else if (etCupo != null) etCupo.setError(ValidationUtils.getErrorInvalidNumber());
                        if (etCupo != null) etCupo.requestFocus();
                        return;
                    }

                    // 6️⃣ Validar rango (1-10000)
                    if (!ValidationUtils.isInRange(cupoText, 1, 10000)) {
                        if (tilCupoRef != null) {
                            tilCupoRef.setError("El cupo debe estar entre 1 y 10,000");
                            tilCupoRef.setErrorEnabled(true);
                        }
                        if (etCupo != null) etCupo.requestFocus();
                        return;
                    }

                    cupo = Integer.valueOf(cupoText);
                }

                Lugar l = (original == null)
                        ? new Lugar(null, nombre, cupo, true)
                        : new Lugar(original.getId(), nombre, cupo, original.isActivo());

                onSave.save(l);
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
