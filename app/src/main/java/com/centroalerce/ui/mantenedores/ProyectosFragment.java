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

        // Botón de retroceso
        com.google.android.material.button.MaterialButton btnVolver = v.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(view -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

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
            if(p.getId()==null) {
                db.collection("proyectos").add(p);
            } else {
                db.collection("proyectos").document(p.getId()).set(p)
                        .addOnSuccessListener(unused -> {
                            // 🆕 Actualizar el nombre en todas las actividades que usan este proyecto
                            if (original != null && !p.getNombre().equals(original.getNombre())) {
                                android.util.Log.d("Proyectos", "🔄 Nombre cambió de '" + original.getNombre() + "' a '" + p.getNombre() + "' - actualizando actividades...");
                                actualizarNombreEnActividades(original.getNombre(), p.getNombre());
                            }
                        });
            }
        }).show(getParentFragmentManager(),"ProyectoDialog");
    }

    /**
     * 🆕 Actualiza el nombre del proyecto en todas las actividades que lo usan
     * NOTA: Los proyectos se guardan solo por nombre (no por ID) en las actividades
     */
    private void actualizarNombreEnActividades(String nombreAntiguo, String nuevoNombre) {
        android.util.Log.d("Proyectos", "📝 Buscando actividades con proyecto: '" + nombreAntiguo + "'");

        // Actualizar en colección "activities" (EN) - campo "proyecto"
        db.collection("activities")
                .whereEqualTo("proyecto", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "🔍 Encontradas " + querySnapshot.size() + " actividades con proyecto='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("proyecto", nuevoNombre, "proyectoNombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("Proyectos", "    ✅ Actualizado: " + doc.getId());
                                    // También actualizar las citas de esta actividad
                                    actualizarProyectoEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                                })
                                .addOnFailureListener(e -> android.util.Log.e("Proyectos", "    ❌ Error: " + e.getMessage()));
                    }
                });

        // Actualizar en colección "actividades" (ES) - campo "proyecto"
        db.collection("actividades")
                .whereEqualTo("proyecto", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "🔍 Encontradas " + querySnapshot.size() + " actividades con proyecto='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("proyecto", nuevoNombre, "proyectoNombre", nuevoNombre);
                    }
                });

        // Actualizar también en campo "proyectoNombre" si existe
        db.collection("activities")
                .whereEqualTo("proyectoNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "🔍 Encontradas " + querySnapshot.size() + " actividades con proyectoNombre='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("proyecto", nuevoNombre, "proyectoNombre", nuevoNombre);
                        actualizarProyectoEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("proyectoNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "🔍 Encontradas " + querySnapshot.size() + " actividades con proyectoNombre='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("proyecto", nuevoNombre, "proyectoNombre", nuevoNombre);
                    }
                });
    }

    /**
     * 🆕 Actualiza el proyecto en todas las citas de una actividad
     */
    private void actualizarProyectoEnCitas(String actividadId, String nombreAntiguo, String nuevoNombre) {
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("proyecto", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "      🔍 Encontradas " + querySnapshot.size() + " citas con proyecto='" + nombreAntiguo + "' en actividad " + actividadId);
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("proyecto", nuevoNombre, "proyectoNombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Proyectos", "      ✅ Cita actualizada: " + citaDoc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Proyectos", "      ❌ Error actualizando cita: " + e.getMessage()));
                    }
                });

        // También buscar por proyectoNombre en las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("proyectoNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("proyecto", nuevoNombre, "proyectoNombre", nuevoNombre);
                    }
                });
    }
}