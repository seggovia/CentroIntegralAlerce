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

public class HomeFragment extends Fragment {

    public HomeFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Botones de la tarjeta principal (crear, mantenedores, calendario)
        v.findViewById(R.id.btnCrearActividad).setOnClickListener(
                x -> Navigation.findNavController(v).navigate(R.id.activityFormFragment));
        v.findViewById(R.id.btnMantenedores).setOnClickListener(
                x -> Navigation.findNavController(v).navigate(R.id.maintainersFragment));
        v.findViewById(R.id.btnCalendario).setOnClickListener(
                x -> Navigation.findNavController(v).navigate(R.id.calendarFragment));

        // “Ver todas” en Mis Citas → lista de actividades
        v.findViewById(R.id.btnVerTodas).setOnClickListener(
                x -> Navigation.findNavController(v).navigate(R.id.activitiesListFragment));

        return v;
    }
}
