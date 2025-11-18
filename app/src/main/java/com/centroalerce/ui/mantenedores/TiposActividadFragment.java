package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;
import android.widget.Toast;

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

        // Botón de retroceso
        com.google.android.material.button.MaterialButton btnVolver = v.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(view -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

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

    private void abrirDialogo(@Nullable TipoActividad original) {
        new TipoActividadDialog(original, t -> {
            // Diferenciar creación de edición
            if (t.getId() == null || t.getId().isEmpty()) {
                // CREAR NUEVO
                db.collection("tiposActividad").add(t)
                        .addOnSuccessListener(docRef -> {
                            // Actualizar el ID generado
                            docRef.update("id", docRef.getId());
                            Toast.makeText(getContext(), "Tipo creado exitosamente", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {
                // ACTUALIZAR EXISTENTE
                db.collection("tiposActividad").document(t.getId()).set(t)
                        .addOnSuccessListener(unused -> {
                            // 🆕 Actualizar el nombre en todas las actividades que usan este tipo
                            if (original != null && !t.getNombre().equals(original.getNombre())) {
                                android.util.Log.d("TiposActividad", "🔄 Nombre cambió de '" + original.getNombre() + "' a '" + t.getNombre() + "' - actualizando actividades...");
                                actualizarNombreEnActividades(original.getNombre(), t.getNombre());
                            }
                            Toast.makeText(getContext(), "Tipo actualizado exitosamente", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).show(getParentFragmentManager(), "TipoActividadDialog");
    }

    /**
     * 🆕 Actualiza el nombre del tipo de actividad en todas las actividades que lo usan
     * NOTA: Los tipos se guardan solo por nombre (no por ID) en las actividades
     */
    private void actualizarNombreEnActividades(String nombreAntiguo, String nuevoNombre) {
        android.util.Log.d("TiposActividad", "📝 Buscando actividades con tipo: '" + nombreAntiguo + "'");

        // Actualizar en colección "activities" (EN) - campo "tipo"
        db.collection("activities")
                .whereEqualTo("tipo", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "🔍 Encontradas " + querySnapshot.size() + " actividades con tipo='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("tipo", nuevoNombre, "tipoActividad", nuevoNombre)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("TiposActividad", "    ✅ Actualizado: " + doc.getId());
                                    // También actualizar las citas de esta actividad
                                    actualizarTipoEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                                })
                                .addOnFailureListener(e -> android.util.Log.e("TiposActividad", "    ❌ Error: " + e.getMessage()));
                    }
                });

        // Actualizar en colección "actividades" (ES) - campo "tipo"
        db.collection("actividades")
                .whereEqualTo("tipo", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "🔍 Encontradas " + querySnapshot.size() + " actividades con tipo='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("tipo", nuevoNombre, "tipoActividad", nuevoNombre);
                    }
                });

        // Actualizar también en campo "tipoActividad" si existe
        db.collection("activities")
                .whereEqualTo("tipoActividad", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "🔍 Encontradas " + querySnapshot.size() + " actividades con tipoActividad='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("tipo", nuevoNombre, "tipoActividad", nuevoNombre);
                        actualizarTipoEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("tipoActividad", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "🔍 Encontradas " + querySnapshot.size() + " actividades con tipoActividad='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("tipo", nuevoNombre, "tipoActividad", nuevoNombre);
                    }
                });
    }

    /**
     * 🆕 Actualiza el tipo en todas las citas de una actividad
     */
    private void actualizarTipoEnCitas(String actividadId, String nombreAntiguo, String nuevoNombre) {
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("tipo", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "      🔍 Encontradas " + querySnapshot.size() + " citas con tipo='" + nombreAntiguo + "' en actividad " + actividadId);
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("tipo", nuevoNombre, "tipoActividad", nuevoNombre)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("TiposActividad", "      ✅ Cita actualizada: " + citaDoc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("TiposActividad", "      ❌ Error actualizando cita: " + e.getMessage()));
                    }
                });

        // También buscar por tipoActividad en las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("tipoActividad", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("tipo", nuevoNombre, "tipoActividad", nuevoNombre);
                    }
                });
    }
}