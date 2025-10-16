package com.centroalerce.ui.mantenedores.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.TipoActividad;
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

        // ✅ FONDO TRANSPARENTE (igual que LugarDialog)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Botón cancelar
        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(view -> dialog.dismiss());
        }

        // Botón guardar
        if (btnGuardar != null) {
            btnGuardar.setOnClickListener(view -> {
                String nombre = getText(etNombre);
                if (TextUtils.isEmpty(nombre)) {
                    tilNombre.setError("El nombre es obligatorio");
                    return;
                }
                tilNombre.setError(null);

                String descripcion = getText(etDescripcion);
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
}