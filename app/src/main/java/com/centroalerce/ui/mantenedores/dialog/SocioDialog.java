package com.centroalerce.ui.mantenedores.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.SocioComunitario;
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
        try { tilNombre = (com.google.android.material.textfield.TextInputLayout) ((View) etNombre.getParent()).getParent(); } catch (Exception ignore) {}
        final com.google.android.material.textfield.TextInputLayout tilNombreRef = tilNombre;

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

        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(view -> dialog.dismiss());
        }

        if (btnGuardar != null) {
            btnGuardar.setOnClickListener(view -> {
                String nombre = "";
                if (etNombre != null && etNombre.getText() != null) {
                    nombre = etNombre.getText().toString().trim();
                }

                // Validar nombre obligatorio
                if (TextUtils.isEmpty(nombre)) {
                    if (tilNombreRef != null) { tilNombreRef.setError("El nombre es obligatorio"); tilNombreRef.setErrorEnabled(true); }
                    else if (etNombre != null) etNombre.setError("El nombre es obligatorio");
                    if (etNombre != null) etNombre.requestFocus();
                    return;
                }

                String descripcion = "";
                if (etDescripcion != null && etDescripcion.getText() != null) {
                    descripcion = etDescripcion.getText().toString().trim();
                }

                String caracterizacion = "";
                if (etCaracterizacion != null && etCaracterizacion.getText() != null) {
                    caracterizacion = etCaracterizacion.getText().toString().trim();
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
}
