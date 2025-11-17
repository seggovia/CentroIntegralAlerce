package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;

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
        return i.inflate(R.layout.fragment_socios, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b){
        super.onViewCreated(v,b);

        // Botón de retroceso
        com.google.android.material.button.MaterialButton btnVolver = v.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(view -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

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
            if(s.getId()==null) {
                db.collection("socios").add(s);
            } else {
                db.collection("socios").document(s.getId()).set(s)
                        .addOnSuccessListener(unused -> {
                            // 🆕 Actualizar el nombre en todas las actividades que usan este socio
                            if (original != null && !s.getNombre().equals(original.getNombre())) {
                                actualizarNombreEnActividades(s.getId(), s.getNombre());
                            }
                        });
            }
        }).show(getParentFragmentManager(),"SocioDialog");
    }

    /**
     * 🆕 Actualiza el nombre del socio comunitario en todas las actividades que lo usan
     */
    private void actualizarNombreEnActividades(String socioId, String nuevoNombre) {
        // Actualizar en colección "activities" (EN) - campo socio_id
        db.collection("activities")
                .whereEqualTo("socio_id", socioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                    android.util.Log.d("Socios", "✅ Actualizado en " + querySnapshot.size() + " actividades (EN - socio_id)");
                });

        // Actualizar en colección "actividades" (ES) - campo socio_id
        db.collection("actividades")
                .whereEqualTo("socio_id", socioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                    android.util.Log.d("Socios", "✅ Actualizado en " + querySnapshot.size() + " actividades (ES - socio_id)");
                });

        // También buscar por socioId sin guion bajo
        db.collection("activities")
                .whereEqualTo("socioId", socioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                    android.util.Log.d("Socios", "✅ Actualizado en " + querySnapshot.size() + " actividades (EN - socioId)");
                });

        db.collection("actividades")
                .whereEqualTo("socioId", socioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                    android.util.Log.d("Socios", "✅ Actualizado en " + querySnapshot.size() + " actividades (ES - socioId)");
                });
    }
}