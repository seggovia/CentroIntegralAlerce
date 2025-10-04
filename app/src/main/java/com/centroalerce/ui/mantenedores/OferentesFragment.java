package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
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

    private FirebaseFirestore db;
    private OferenteAdapter adapter;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b){
        return i.inflate(R.layout.fragment_lugares, c, false); // reutilizamos el layout de lista
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b){
        super.onViewCreated(v,b);
        ((TextView)v.findViewById(R.id.tvTitulo)).setText("Oferentes de la actividad");

        RecyclerView rv=v.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter=new OferenteAdapter(new OferenteAdapter.Callbacks(){
            @Override public void onEditar(Oferente o){ abrirDialogo(o); }
            @Override public void onEliminar(Oferente o){
                if(o.getId()!=null) db.collection("oferentes").document(o.getId()).delete();
            }
        });
        rv.setAdapter(adapter);

        ((FloatingActionButton)v.findViewById(R.id.fabAgregar)).setOnClickListener(x->abrirDialogo(null));

        db=FirebaseFirestore.getInstance();
        db.collection("oferentes").orderBy("nombre")
                .addSnapshotListener((snap,e)->{
                    if(e!=null||snap==null) return;
                    List<Oferente> lista=new ArrayList<>();
                    for(QueryDocumentSnapshot d: snap){
                        Oferente o=d.toObject(Oferente.class);
                        o.setId(d.getId());
                        lista.add(o);
                    }
                    adapter.submit(lista);
                });
    }

    private void abrirDialogo(@Nullable Oferente original){
        new OferenteDialog(original, o -> {
            if(o.getId()==null) db.collection("oferentes").add(o);
            else db.collection("oferentes").document(o.getId()).set(o);
        }).show(getParentFragmentManager(),"OferenteDialog");
    }
}
