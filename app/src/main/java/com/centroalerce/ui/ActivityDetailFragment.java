package com.centroalerce.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.centroalerce.gestion.R;

public class ActivityDetailFragment extends Fragment {

    public ActivityDetailFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activity_detail, c, false);

        TextView tvNombre = v.findViewById(R.id.tvNombreActividad);
        // TODO: obtener args con idActividad y cargar desde Firestore
        tvNombre.setText("Taller de alfabetización digital");

        v.findViewById(R.id.btnModificar).setOnClickListener(
                x -> Navigation.findNavController(v).navigate(R.id.activityFormFragment));
        v.findViewById(R.id.btnCancelar).setOnClickListener(
                x -> {/* TODO: marcar como cancelada en Firestore y mostrar snackbar */});
        v.findViewById(R.id.btnReagendar).setOnClickListener(
                x -> {/* TODO: abrir diálogo Date/TimePicker y actualizar cita */});
        v.findViewById(R.id.btnAdjuntar).setOnClickListener(
                x -> {/* TODO: seleccionar archivo y subir a Storage */});
        return v;
    }
}
