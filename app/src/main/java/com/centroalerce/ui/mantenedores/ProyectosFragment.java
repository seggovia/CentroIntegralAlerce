package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Proyecto;
import com.centroalerce.ui.mantenedores.adapter.ProyectoAdapter;
import com.centroalerce.ui.mantenedores.dialog.ProyectoDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.*;
import java.util.*;

public class ProyectosFragment extends Fragment {

    private FirebaseFirestore db;
    private ProyectoAdapter adapter;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b){
        return i.inflate(R.layout.fragment_proyectos, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b){
        super.onViewCreated(v,b);

        RecyclerView rv=v.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter=new ProyectoAdapter(new ProyectoAdapter.Callbacks(){
            @Override public void onEditar(Proyecto p){ abrirDialogo(p); }
            @Override public void onEliminar(Proyecto p){
                if(p.getId()!=null) db.collection("proyectos").document(p.getId()).delete();
            }
        });
        rv.setAdapter(adapter);

        ((FloatingActionButton)v.findViewById(R.id.fabAgregar)).setOnClickListener(x->abrirDialogo(null));

        db=FirebaseFirestore.getInstance();
        db.collection("proyectos").orderBy("nombre")
                .addSnapshotListener((snap,e)->{
                    if(e!=null||snap==null) return;
                    List<Proyecto> lista=new ArrayList<>();
                    for(QueryDocumentSnapshot d: snap){
                        Proyecto p=d.toObject(Proyecto.class);
                        p.setId(d.getId());
                        lista.add(p);
                    }
                    adapter.submit(lista);
                });
    }

    private void abrirDialogo(@Nullable Proyecto original){
        new ProyectoDialog(original, p -> {
            if(p.getId()==null) db.collection("proyectos").add(p);
            else db.collection("proyectos").document(p.getId()).set(p);
        }).show(getParentFragmentManager(),"ProyectoDialog");
    }
}