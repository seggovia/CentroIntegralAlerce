package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CancelarActividadSheet extends BottomSheetDialogFragment {

    public static CancelarActividadSheet newInstance(String actividadId, String citaId) {
        CancelarActividadSheet f = new CancelarActividadSheet();
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
        // layout file: res/layout/sheet_cancelar_actividad.xml
        return inflater.inflate(layout("sheet_cancelar_actividad"), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        String actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";
        String citaId      = getArguments() != null ? getArguments().getString("citaId", "") : "";

        EditText etMotivo = v.findViewById(id("etMotivo"));
        Button btnCancelar = v.findViewById(id("btnCancelarActividad"));

        btnCancelar.setOnClickListener(view -> {
            String motivo = etMotivo.getText().toString().trim();
            if (motivo.isEmpty()) { etMotivo.setError("Requerido"); return; }

            // TODO: marcar actividad/cita como "cancelada" en Firebase + guardar motivo + notificar
            dismiss();
        });
    }
}
