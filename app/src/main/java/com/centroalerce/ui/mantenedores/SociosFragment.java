package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.SocioComunitario;
import com.centroalerce.ui.mantenedores.adapter.SocioAdapter;
import com.centroalerce.ui.mantenedores.dialog.SocioDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.*;
import java.util.*;

public class SociosFragment extends Fragment {

    private FirebaseFirestore db;
    private SocioAdapter adapter;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b){
        return i.inflate(R.layout.fragment_lugares, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b){
        super.onViewCreated(v,b);
        ((TextView)v.findViewById(R.id.tvTitulo)).setText("Socios comunitarios");

        RecyclerView rv=v.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter=new SocioAdapter(new SocioAdapter.Callbacks(){
            @Override public void onEditar(SocioComunitario s){ abrirDialogo(s); }
            @Override public void onEliminar(SocioComunitario s){
                if(s.getId()!=null) db.collection("socios").document(s.getId()).delete();
            }
        });
        rv.setAdapter(adapter);

        ((FloatingActionButton)v.findViewById(R.id.fabAgregar)).setOnClickListener(x->abrirDialogo(null));

        db=FirebaseFirestore.getInstance();
        db.collection("socios").orderBy("nombre")
                .addSnapshotListener((snap,e)->{
                    if(e!=null||snap==null) return;
                    List<SocioComunitario> lista=new ArrayList<>();
                    for(QueryDocumentSnapshot d: snap){
                        SocioComunitario s=d.toObject(SocioComunitario.class);
                        s.setId(d.getId());
                        lista.add(s);
                    }
                    adapter.submit(lista);
                });
    }

    private void abrirDialogo(@Nullable SocioComunitario original){
        new SocioDialog(original, s -> {
            if(s.getId()==null) db.collection("socios").add(s);
            else db.collection("socios").document(s.getId()).set(s);
        }).show(getParentFragmentManager(),"SocioDialog");
    }
}
