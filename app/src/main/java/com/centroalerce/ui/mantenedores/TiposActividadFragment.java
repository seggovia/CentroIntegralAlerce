package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.TipoActividad;
import com.centroalerce.ui.mantenedores.adapter.TipoActividadAdapter;
import com.centroalerce.ui.mantenedores.dialog.TipoActividadDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.*;
import java.util.*;

public class TiposActividadFragment extends Fragment {

    private FirebaseFirestore db;
    private TipoActividadAdapter adapter;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b){
        return i.inflate(R.layout.fragment_tipos_actividad, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b){
        super.onViewCreated(v,b);
        ((TextView)v.findViewById(R.id.tvTitulo)).setText("Tipos de actividad");

        RecyclerView rv=v.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter=new TipoActividadAdapter(new TipoActividadAdapter.Callbacks(){
            @Override public void onEditar(TipoActividad t){ abrirDialogo(t); }
            @Override public void onEliminar(TipoActividad t){
                if(t.getId()!=null) db.collection("tiposActividad").document(t.getId()).delete();
            }
        });
        rv.setAdapter(adapter);

        ((FloatingActionButton)v.findViewById(R.id.fabAgregar)).setOnClickListener(x->abrirDialogo(null));

        db=FirebaseFirestore.getInstance();
        db.collection("tiposActividad").orderBy("nombre")
                .addSnapshotListener((snap,e)->{
                    if(e!=null||snap==null) return;
                    List<TipoActividad> lista=new ArrayList<>();
                    for(QueryDocumentSnapshot d: snap){
                        TipoActividad t=d.toObject(TipoActividad.class);
                        t.setId(d.getId());
                        lista.add(t);
                    }
                    adapter.submit(lista);
                });
    }

    private void abrirDialogo(@Nullable TipoActividad original){
        new TipoActividadDialog(original, t -> {
            if(t.getId()==null) db.collection("tiposActividad").add(t);
            else db.collection("tiposActividad").document(t.getId()).set(t);
        }).show(getParentFragmentManager(),"TipoActividadDialog");
    }
}
