package com.centroalerce.ui;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.centroalerce.gestion.R;

public class MaintainersFragment extends Fragment {

    public MaintainersFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_maintainers, c, false);

        // Tarjetas o botones: Tipos de actividad, Lugares, Oferentes, Socios, Proyectos
        // TODO: cada uno abre una lista CRUD simple (otro fragment o diálogo) conectado a Firestore.
        // Ejemplo de ids en layout: cardTipos, cardLugares, cardOferentes, cardSocios, cardProyectos

        return v;
    }
}
