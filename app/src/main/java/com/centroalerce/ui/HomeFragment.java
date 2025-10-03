// app/src/main/java/com/centroalerce/ui/HomeFragment.java
package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class HomeFragment extends Fragment {

    private int resId(View v, String name, String defType) {
        return v.getResources().getIdentifier(name, defType, v.getContext().getPackageName());
    }
    private int id(View v, String name) { return resId(v, name, "id"); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        int layoutId = requireContext().getResources()
                .getIdentifier("fragment_home", "layout", requireContext().getPackageName());
        return inf.inflate(layoutId, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        LinearLayout btnCrearActividad = v.findViewById(id(v, "btnCrearActividad"));
        LinearLayout btnMantenedores   = v.findViewById(id(v, "btnMantenedores"));
        LinearLayout btnCalendario     = v.findViewById(id(v, "btnCalendario"));
        TextView     btnVerTodas       = v.findViewById(id(v, "btnVerTodas"));

        if (btnCrearActividad != null)
            btnCrearActividad.setOnClickListener(x -> navigateByName(v, "activityFormFragment"));

        if (btnMantenedores != null)
            btnMantenedores.setOnClickListener(x -> navigateByName(v, "maintainersFragment"));

        if (btnCalendario != null)
            btnCalendario.setOnClickListener(x -> navigateByName(v, "calendarFragment"));

        if (btnVerTodas != null)
            btnVerTodas.setOnClickListener(x -> navigateByName(v, "activitiesListFragment"));
    }

    private void navigateByName(View v, String destName) {
        int destId = resId(v, destName, "id");
        if (destId != 0) {
            NavHostFragment.findNavController(this).navigate(destId);
        } else {
            // Si quieres, muestra un log/toast aquí para depurar
        }
    }
}
