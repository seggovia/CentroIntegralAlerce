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

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialCardView cardRegistroActividades = view.findViewById(R.id.cardRegistroActividades);
        MaterialCardView cardGestionUsuarios = view.findViewById(R.id.cardGestionUsuarios);
        MaterialCardView cardMantenedores = view.findViewById(R.id.cardMantenedores);
        MaterialCardView cardCerrarSesion = view.findViewById(R.id.cardCerrarSesion);

        // 👉 NUEVO: card Registro de Actividades
        if (cardRegistroActividades != null) {
            cardRegistroActividades.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_registroActividadesFragment)
            );
        }

        // 👉 NUEVO: card Gestión de Usuarios
        if (cardGestionUsuarios != null) {
            cardGestionUsuarios.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_gestionUsuariosFragment)
            );
        }

        // 👉 NUEVO: card Perfil
        MaterialCardView cardPerfil = view.findViewById(R.id.cardPerfil);
        if (cardPerfil != null) {
            cardPerfil.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_perfilFragment)
            );
        }

        // Navega al fragmento de mantenedores
        if (cardMantenedores != null) {
            cardMantenedores.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_maintainersFragment)
            );
        }

        // Cerrar sesión y volver al login
        if (cardCerrarSesion != null) {
            cardCerrarSesion.setOnClickListener(v -> {
                // FirebaseAuth.getInstance().signOut(); // si usas Firebase
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_settingsFragment_to_loginFragment);
            });
        }
    }
}
