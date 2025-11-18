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
                                android.util.Log.d("Socios", "🔄 Nombre cambió de '" + original.getNombre() + "' a '" + s.getNombre() + "' - actualizando actividades...");
                                actualizarNombreEnActividades(original.getNombre(), s.getNombre());
                            }
                        });
            }
        }).show(getParentFragmentManager(),"SocioDialog");
    }

    /**
     * 🆕 Actualiza el nombre del socio comunitario en todas las actividades que lo usan
     * NOTA: Los socios se guardan solo por nombre (no por ID) en las actividades
     */
    private void actualizarNombreEnActividades(String nombreAntiguo, String nuevoNombre) {
        android.util.Log.d("Socios", "📝 Buscando actividades con socio: '" + nombreAntiguo + "'");

        // Actualizar en colección "activities" (EN) - campo "socioComunitario"
        db.collection("activities")
                .whereEqualTo("socioComunitario", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socioComunitario='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("Socios", "    ✅ Actualizado: " + doc.getId());
                                    // También actualizar las citas de esta actividad
                                    actualizarSocioEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                                })
                                .addOnFailureListener(e -> android.util.Log.e("Socios", "    ❌ Error: " + e.getMessage()));
                    }
                });

        // Actualizar en colección "actividades" (ES) - campo "socioComunitario"
        db.collection("actividades")
                .whereEqualTo("socioComunitario", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socioComunitario='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                });

        // Actualizar también en campo "socio" si existe
        db.collection("activities")
                .whereEqualTo("socio", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socio='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                        actualizarSocioEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("socio", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socio='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                });

        // Actualizar también en campo "socio_nombre" si existe
        db.collection("activities")
                .whereEqualTo("socio_nombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socio_nombre='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                        actualizarSocioEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("socio_nombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socio_nombre='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                });
    }

    /**
     * 🆕 Actualiza el socio comunitario en todas las citas de una actividad
     */
    private void actualizarSocioEnCitas(String actividadId, String nombreAntiguo, String nuevoNombre) {
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("socioComunitario", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "      🔍 Encontradas " + querySnapshot.size() + " citas con socioComunitario='" + nombreAntiguo + "' en actividad " + actividadId);
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Socios", "      ✅ Cita actualizada: " + citaDoc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Socios", "      ❌ Error actualizando cita: " + e.getMessage()));
                    }
                });

        // También buscar por socio en las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("socio", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                });

        // También buscar por socio_nombre en las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("socio_nombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("socio", nuevoNombre, "socioComunitario", nuevoNombre, "socio_nombre", nuevoNombre);
                    }
                });
    }
}