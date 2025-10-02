package com.centroalerce.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AdjuntarComunicacionSheet extends BottomSheetDialogFragment {

    private Uri fileUri;
    private ActivityResultLauncher<Intent> pickerLauncher;

    public static AdjuntarComunicacionSheet newInstance(String actividadId) {
        AdjuntarComunicacionSheet f = new AdjuntarComunicacionSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        f.setArguments(b);
        return f;
    }

    private int resId(String name, String defType) {
        return requireContext().getResources().getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    @Override
    public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);

        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        fileUri = result.getData().getData();
                        // Actualiza el TextView del archivo si la vista ya está creada
                        View dialogView = getView();
                        if (dialogView != null) {
                            TextView tv = dialogView.findViewById(id("tvArchivo"));
                            if (tv != null && fileUri != null) {
                                String last = fileUri.getLastPathSegment();
                                tv.setText(last != null ? last : fileUri.toString());
                            }
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // layout file: res/layout/sheet_adjuntar_comunicacion.xml
        return inflater.inflate(layout("sheet_adjuntar_comunicacion"), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        String actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";

        TextView tvArchivo = v.findViewById(id("tvArchivo"));
        Button btnSeleccionar = v.findViewById(id("btnSeleccionarArchivo"));
        Button btnSubir = v.findViewById(id("btnSubir"));

        btnSeleccionar.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            pickerLauncher.launch(intent);
        });

        btnSubir.setOnClickListener(view -> {
            if (fileUri == null) {
                Toast.makeText(requireContext(), "Selecciona un archivo", Toast.LENGTH_SHORT).show();
                return;
            }
            // TODO:
            // - Subir a Firebase Storage: actividades/{actividadId}/comunicaciones/{filename}
            // - Guardar metadata en Firestore (URL, nombre, fecha)
            dismiss();
        });
    }
}
