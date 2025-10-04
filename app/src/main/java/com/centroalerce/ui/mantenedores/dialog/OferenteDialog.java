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
import com.centroalerce.gestion.models.Oferente;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class OferenteDialog extends DialogFragment {

    public interface OnSave { void save(Oferente o); }

    @Nullable private final Oferente original;
    @NonNull  private final OnSave onSave;

    public OferenteDialog(@Nullable Oferente original, @NonNull OnSave onSave){
        this.original = original;
        this.onSave = onSave;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle b) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_oferente, null, false);

        final TextInputEditText etNombre = v.findViewById(R.id.etNombre);
        final TextInputEditText etDocente = v.findViewById(R.id.etDocente);
        final MaterialButton btnCancelar = v.findViewById(R.id.btnCancelar);
        final MaterialButton btnGuardar = v.findViewById(R.id.btnGuardar);

        if (original != null) {
            if (etNombre != null) etNombre.setText(original.getNombre());
            if (etDocente != null) etDocente.setText(original.getDocenteResponsable());
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

                String docente = "";
                if (etDocente != null && etDocente.getText() != null) {
                    docente = etDocente.getText().toString().trim();
                }

                Oferente o = (original == null)
                        ? new Oferente(null, nombre, docente, true)
                        : new Oferente(original.getId(), nombre, docente, original.isActivo());

                onSave.save(o);
                dialog.dismiss();
            });
        }

        return dialog;
    }
}
