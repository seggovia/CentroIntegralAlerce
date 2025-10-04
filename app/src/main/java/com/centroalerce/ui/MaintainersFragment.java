package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.centroalerce.gestion.R;
import com.google.android.material.card.MaterialCardView;

public class MaintainersFragment extends Fragment {

    public MaintainersFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        // Asegúrate de que el nombre del layout es EXACTO al archivo
        return inf.inflate(R.layout.fragment_maintainers, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        MaterialCardView btnTipos     = v.findViewById(R.id.btnTipos);
        MaterialCardView btnLugares   = v.findViewById(R.id.btnLugares);
        MaterialCardView btnOferentes = v.findViewById(R.id.btnOferentes);
        MaterialCardView btnSocios    = v.findViewById(R.id.btnSocios);
        MaterialCardView btnProyectos = v.findViewById(R.id.btnProyectos);

        // Navegar por ID de destino (más robusto que por action si hay dudas de acciones)
        if (btnTipos != null)     btnTipos.setOnClickListener(_v -> NavHostFragment.findNavController(this).navigate(R.id.tiposActividadFragment));
        if (btnLugares != null)   btnLugares.setOnClickListener(_v -> NavHostFragment.findNavController(this).navigate(R.id.lugaresFragment));
        if (btnOferentes != null) btnOferentes.setOnClickListener(_v -> NavHostFragment.findNavController(this).navigate(R.id.oferentesFragment));
        if (btnSocios != null)    btnSocios.setOnClickListener(_v -> NavHostFragment.findNavController(this).navigate(R.id.sociosFragment));
        if (btnProyectos != null) btnProyectos.setOnClickListener(_v -> NavHostFragment.findNavController(this).navigate(R.id.proyectosFragment));
    }
}
