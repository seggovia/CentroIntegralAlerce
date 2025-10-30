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
import com.centroalerce.gestion.models.Lugar;
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
                    if (etNombre != null) {
                        etNombre.setError("El nombre es obligatorio");
                        etNombre.requestFocus();
                    }
                    return;
                }

                Integer cupo = null;
                if (etCupo != null && etCupo.getText() != null && etCupo.getText().length() > 0) {
                    try { 
                        cupo = Integer.valueOf(etCupo.getText().toString());
                        if (cupo < 0) {
                            if (etCupo != null) {
                                etCupo.setError("El cupo no puede ser negativo");
                                etCupo.requestFocus();
                            }
                            return;
                        }
                    } catch (NumberFormatException e) {
                        if (etCupo != null) {
                            etCupo.setError("Ingresa un número válido");
                            etCupo.requestFocus();
                        }
                        return;
                    }
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
}
