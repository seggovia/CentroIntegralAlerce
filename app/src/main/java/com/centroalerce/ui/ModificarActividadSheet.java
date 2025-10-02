package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ModificarActividadSheet extends BottomSheetDialogFragment {

    public static ModificarActividadSheet newInstance(String actividadId) {
        ModificarActividadSheet f = new ModificarActividadSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        f.setArguments(b);
        return f;
    }

    private int resId(String name, String defType) {
        return requireContext().getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }

    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // IMPORTANTE: el archivo debe llamarse exactamente "sheet_modificar_actividad.xml"
        int layoutId = layout("sheet_modificar_actividad");
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        String actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";

        // IMPORTANTE: estos IDs deben existir en tu XML
        EditText etNombre = v.findViewById(id("etNombre"));
        AutoCompleteTextView actTipo = v.findViewById(id("actTipo"));
        AutoCompleteTextView actPeriodicidad = v.findViewById(id("actPeriodicidad"));
        EditText etCupo = v.findViewById(id("etCupo"));
        AutoCompleteTextView actLugar = v.findViewById(id("actLugar"));
        AutoCompleteTextView actOferente = v.findViewById(id("actOferente"));
        Button btnGuardar = v.findViewById(id("btnGuardarCambios"));

        // TODO: cargar datos actuales desde Firebase y setearlos
        // TODO: poblar listas (Tipos, Lugares, Oferentes) desde Firebase

        btnGuardar.setOnClickListener(view -> {
            // TODO: validar y actualizar documento de "actividades/{actividadId}" en Firebase
            dismiss();
        });
    }
}
