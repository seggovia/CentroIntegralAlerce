package com.centroalerce.ui;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.centroalerce.gestion.R;

public class SettingsFragment extends Fragment {

    public SettingsFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_settings, c, false);

        // Acceso a mantenedores desde configuración
        v.findViewById(R.id.cardMantenedores).setOnClickListener(
                x -> Navigation.findNavController(v).navigate(R.id.maintainersFragment));
        v.findViewById(R.id.cardPerfil).setOnClickListener(view -> {
            Navigation.findNavController(v).navigate(R.id.perfilFragment);
        });

        // Aquí podría ir cerrar sesión, cambiar idioma, etc.
        return v;
    }
}
