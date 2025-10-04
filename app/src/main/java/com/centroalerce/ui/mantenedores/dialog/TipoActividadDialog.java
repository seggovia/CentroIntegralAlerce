package com.centroalerce.ui.mantenedores.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.TipoActividad;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class TipoActividadDialog extends DialogFragment {

    public interface OnSave { void save(TipoActividad t); }

    @Nullable private final TipoActividad original;
    @NonNull  private final OnSave onSave;

    public TipoActividadDialog(@Nullable TipoActividad original, @NonNull OnSave onSave) {
        this.original = original;
        this.onSave = onSave;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle b) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tipo_actividad, null, false);

        final TextInputEditText etNombre = v.findViewById(R.id.etNombre);
        final TextInputEditText etDescripcion = v.findViewById(R.id.etDescripcion);
        final MaterialButton btnCancelar = v.findViewById(R.id.btnCancelar);
        final MaterialButton btnGuardar = v.findViewById(R.id.btnGuardar);

        if (original != null) {
            if (etNombre != null) etNombre.setText(original.getNombre());
            if (etDescripcion != null) etDescripcion.setText(original.getDescripcion());
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

                String descripcion = "";
                if (etDescripcion != null && etDescripcion.getText() != null) {
                    descripcion = etDescripcion.getText().toString().trim();
                }

                TipoActividad t = (original == null)
                        ? new TipoActividad(null, nombre, descripcion, true)
                        : new TipoActividad(original.getId(), nombre, descripcion, original.isActivo());

                onSave.save(t);
                dialog.dismiss();
            });
        }

        return dialog;
    }
}
