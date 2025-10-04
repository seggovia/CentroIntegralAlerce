package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.centroalerce.gestion.R;

public class PerfilFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_perfil, container, false);

        // Botón editar perfil (por ahora solo un Toast o mensaje)
        v.findViewById(R.id.cardEditarPerfil).setOnClickListener(view -> {
            // Por ahora solo un mensaje, luego puedes crear otro fragment para editar
            // Toast.makeText(getContext(), "Editar perfil", Toast.LENGTH_SHORT).show();
        });

        // Botón cambiar contraseña
        v.findViewById(R.id.cardCambiarPassword).setOnClickListener(view -> {
            // Por ahora solo un mensaje
            // Toast.makeText(getContext(), "Cambiar contraseña", Toast.LENGTH_SHORT).show();
        });

        return v;
    }
}