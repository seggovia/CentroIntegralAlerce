package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Oferente;
import com.centroalerce.ui.mantenedores.adapter.OferenteAdapter;
import com.centroalerce.ui.mantenedores.dialog.OferenteDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.*;

import java.util.*;

public class OferentesFragment extends Fragment {

    private static final String COL_OFERENTES = "oferentes";

    private FirebaseFirestore db;
    private OferenteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b){
        return i.inflate(R.layout.fragment_oferentes, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b){
        super.onViewCreated(v,b);

        // Botón de retroceso
        com.google.android.material.button.MaterialButton btnVolver = v.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(view -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

        RecyclerView rv = v.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new OferenteAdapter(new OferenteAdapter.Callbacks(){
            @Override public void onEditar(Oferente o){ abrirDialogo(o); }
            @Override public void onEliminar(Oferente o){
                if (o.getId() != null && !o.getId().isEmpty()) {
                    db.collection(COL_OFERENTES).document(o.getId()).delete();
                }
            }
        });
        rv.setAdapter(adapter);

        ((FloatingActionButton)v.findViewById(R.id.fabAgregar))
                .setOnClickListener(x -> abrirDialogo(null));

        db = FirebaseFirestore.getInstance();
        db.collection(COL_OFERENTES).orderBy("nombre")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    List<Oferente> lista = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Oferente o = d.toObject(Oferente.class);
                        o.setId(d.getId());
                        lista.add(o);
                    }
                    adapter.submit(lista);
                });
    }

    private void abrirDialogo(@Nullable Oferente original){
        new OferenteDialog(original, new OferenteDialog.OnGuardar() {
            @Override public void onGuardar(Oferente o) {
                if (o.getId() == null || o.getId().isEmpty()) {
                    // Aseguramos que los nuevos queden activos
                    o.setActivo(true);
                    db.collection(COL_OFERENTES).add(o);
                } else {
                    db.collection(COL_OFERENTES).document(o.getId()).set(o);
                }
            }
        }).show(getParentFragmentManager(), "OferenteDialog");
    }
}