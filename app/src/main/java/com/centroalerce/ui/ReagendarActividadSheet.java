package com.centroalerce.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Calendar;
import java.util.Locale;

public class ReagendarActividadSheet extends BottomSheetDialogFragment {

    public static ReagendarActividadSheet newInstance(String actividadId, String citaId) {
        ReagendarActividadSheet f = new ReagendarActividadSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        b.putString("citaId", citaId);
        f.setArguments(b);
        return f;
    }

    private int resId(String name, String defType) {
        return requireContext().getResources().getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // layout file: res/layout/sheet_reagendar_actividad.xml
        return inflater.inflate(layout("sheet_reagendar_actividad"), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        String actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";
        String citaId      = getArguments() != null ? getArguments().getString("citaId", "") : "";

        EditText etMotivo = v.findViewById(id("etMotivo"));
        EditText etFecha  = v.findViewById(id("etFecha"));
        EditText etHora   = v.findViewById(id("etHora"));
        Button btnGuardar = v.findViewById(id("btnGuardarNuevaFecha"));

        etFecha.setOnClickListener(view -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(requireContext(),
                    (picker, y, m, d) -> etFecha.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    .show();
        });

        etHora.setOnClickListener(view -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(requireContext(),
                    (picker, h, min) -> etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", h, min)),
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
                    .show();
        });

        btnGuardar.setOnClickListener(view -> {
            String motivo = etMotivo.getText().toString().trim();
            String fecha  = etFecha.getText().toString().trim();
            String hora   = etHora.getText().toString().trim();

            if (motivo.isEmpty()) { etMotivo.setError("Requerido"); return; }
            if (fecha.isEmpty())  { etFecha.setError("Requerido");  return; }
            if (hora.isEmpty())   { etHora.setError("Requerido");   return; }

            // TODO: validar conflictos + actualizar cita en Firebase + registrar motivo + notificar
            dismiss();
        });
    }
}
