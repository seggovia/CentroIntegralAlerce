package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.card.MaterialCardView;

public class HomeFragment extends Fragment {

    private int resId(String name, String defType) {
        return requireContext().getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }

    private int id(String name) {
        return resId(name, "id");
    }

    private int layout(String name) {
        return resId(name, "layout");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(layout("fragment_home"), c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        // USAR MaterialCardView en lugar de LinearLayout
        MaterialCardView btnCrearActividad = v.findViewById(id("btnCrearActividad"));
        MaterialCardView btnMantenedores   = v.findViewById(id("btnMantenedores"));
        MaterialCardView btnCalendario     = v.findViewById(id("btnCalendario"));
        MaterialCardView btnActividades    = v.findViewById(id("btnActividades"));
        TextView btnVerTodas = v.findViewById(id("btnVerTodas"));

        if (btnCrearActividad != null) {
            btnCrearActividad.setOnClickListener(x -> {
                int destId = id("activityFormFragment");
                if (destId != 0) {
                    Navigation.findNavController(v).navigate(destId);
                }
            });
        }

        if (btnMantenedores != null) {
            btnMantenedores.setOnClickListener(x -> {
                int destId = id("maintainersFragment");
                if (destId != 0) {
                    Navigation.findNavController(v).navigate(destId);
                }
            });
        }

        if (btnCalendario != null) {
            btnCalendario.setOnClickListener(x -> {
                int destId = id("calendarFragment");
                if (destId != 0) {
                    Navigation.findNavController(v).navigate(destId);
                }
            });
        }

        if (btnActividades != null) {
            btnActividades.setOnClickListener(x -> {
                int destId = id("activitiesListFragment");
                if (destId != 0) {
                    Navigation.findNavController(v).navigate(destId);
                }
            });
        }

        if (btnVerTodas != null) {
            btnVerTodas.setOnClickListener(x -> {
                int destId = id("activitiesListFragment");
                if (destId != 0) {
                    Navigation.findNavController(v).navigate(destId);
                }
            });
        }
    }
}