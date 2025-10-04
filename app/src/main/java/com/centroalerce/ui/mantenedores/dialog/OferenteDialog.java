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

        TextInputLayout tilNombre   = v.findViewById(R.id.tilNombre); // ✅ usar id del XML
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

        // Habilitar/validar
        btnGuardar.setEnabled(!TextUtils.isEmpty(safeText(etNombre)));
        etNombre.addTextChangedListener(new SimpleWatcher(() -> {
            if (tilNombre != null) tilNombre.setError(null);
            btnGuardar.setEnabled(!TextUtils.isEmpty(safeText(etNombre)));
        }));

        btnCancelar.setOnClickListener(x -> d.dismiss());

        btnGuardar.setOnClickListener(x -> {
            String nombre  = safeText(etNombre);
            String docente = safeText(etDocente);
            String carrera = safeText(etCarrera);

            if (tilNombre != null) tilNombre.setError(null);
            if (TextUtils.isEmpty(nombre)) {
                if (tilNombre != null) tilNombre.setError("Obligatorio");
                etNombre.requestFocus();
                return;
            }

            Oferente o = (original != null) ? original : new Oferente();
            o.setNombre(nombre);
            o.setDocenteResponsable(docente);
            o.setCarrera(carrera);

            // ✅ nuevos por defecto activos
            if (original == null) {
                o.setActivo(true);
            }

            callback.onGuardar(o);
            d.dismiss();
        });

        return d;
    }

    private String safeText(TextInputEditText et) {
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
