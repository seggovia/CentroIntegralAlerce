package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class DetalleActividadFragment extends Fragment {

    private int resId(String name, String defType) {
        return requireContext().getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String n)     { return resId(n, "id"); }
    private int layout(String n) { return resId(n, "layout"); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 👇 este nombre DEBE ser el archivo en res/layout/
        final int layoutId = layout("fragment_detalle_actividad");
        if (layoutId == 0) {
            Toast.makeText(requireContext(),
                    "No encuentro layout: fragment_detalle_actividad.xml", Toast.LENGTH_LONG).show();
            return new View(requireContext());
        }
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);

        // Intenta localizar los 4 botones por id (sin R)
        Button btnModificar = root.findViewById(id("btnModificar"));
        Button btnCancelar  = root.findViewById(id("btnCancelar"));
        Button btnReagendar = root.findViewById(id("btnReagendar"));
        Button btnAdjuntar  = root.findViewById(id("btnAdjuntar"));

        // Si no los encuentra, avisa cuál faltó (esto indica que NO estás usando el layout esperado)
        if (btnModificar == null) warn("No encontré @id/btnModificar en fragment_detalle_actividad");
        if (btnCancelar  == null) warn("No encontré @id/btnCancelar en fragment_detalle_actividad");
        if (btnReagendar == null) warn("No encontré @id/btnReagendar en fragment_detalle_actividad");
        if (btnAdjuntar  == null) warn("No encontré @id/btnAdjuntar en fragment_detalle_actividad");

        // Listeners con Toast para validar que los clicks llegan
        if (btnModificar != null) {
            btnModificar.setOnClickListener(v -> {
                toast("Modificar → abrir sheet");
                ModificarActividadSheet.newInstance(getArg("actividadId"))
                        .show(getParentFragmentManager(), "ModificarActividadSheet");
            });
        }
        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(v -> {
                toast("Cancelar → abrir sheet");
                CancelarActividadSheet.newInstance(getArg("actividadId"), getArg("citaId"))
                        .show(getParentFragmentManager(), "CancelarActividadSheet");
            });
        }
        if (btnReagendar != null) {
            btnReagendar.setOnClickListener(v -> {
                toast("Reagendar → abrir sheet");
                ReagendarActividadSheet.newInstance(getArg("actividadId"), getArg("citaId"))
                        .show(getParentFragmentManager(), "ReagendarActividadSheet");
            });
        }
        if (btnAdjuntar != null) {
            btnAdjuntar.setOnClickListener(v -> {
                toast("Adjuntar → abrir sheet");
                AdjuntarComunicacionSheet.newInstance(getArg("actividadId"))
                        .show(getParentFragmentManager(), "AdjuntarComunicacionSheet");
            });
        }
    }

    private String getArg(String key) {
        return (getArguments() != null) ? getArguments().getString(key, "") : "";
    }

    private void warn(String msg) { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show(); }
    private void toast(String msg) { Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onResume() {
        super.onResume();
        toast("DetalleActividadFragment listo");
    }
}
