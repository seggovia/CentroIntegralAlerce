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
import com.centroalerce.gestion.models.Proyecto;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class ProyectoDialog extends DialogFragment {

    public interface OnSave { void save(Proyecto p); }

    @Nullable private final Proyecto original;
    @NonNull  private final OnSave onSave;

    public ProyectoDialog(@Nullable Proyecto original, @NonNull OnSave onSave) {
        this.original = original;
        this.onSave = onSave;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle b) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_proyecto, null, false);

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

                Proyecto p = (original == null)
                        ? new Proyecto(null, nombre, descripcion, true)
                        : new Proyecto(original.getId(), nombre, descripcion, original.isActivo());

                onSave.save(p);
                dialog.dismiss();
            });
        }

        return dialog;
    }
}
