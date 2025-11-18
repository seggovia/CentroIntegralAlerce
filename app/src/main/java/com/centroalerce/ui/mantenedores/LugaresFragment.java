package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Lugar;
import com.centroalerce.ui.mantenedores.adapter.LugarAdapter;
import com.centroalerce.ui.mantenedores.dialog.LugarDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class LugaresFragment extends Fragment {

    private FirebaseFirestore db;
    private LugarAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lugares, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tv = view.findViewById(R.id.tvTitulo);
        if (tv != null) tv.setText("Lugares"); // luego pásalo a strings.xml

        // Botón de retroceso
        com.google.android.material.button.MaterialButton btnVolver = view.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(v -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

        RecyclerView rv = view.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new LugarAdapter(new LugarAdapter.Callbacks() {
            @Override public void onEditar(Lugar l) { abrirDialogo(l); }

            @Override public void onEliminar(Lugar l) {
                if (l != null && l.getId() != null) {
                    db.collection("lugares").document(l.getId()).delete();
                }
            }
        });
        rv.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fabAgregar);
        if (fab != null) fab.setOnClickListener(v -> abrirDialogo(null));

        db = FirebaseFirestore.getInstance();
        observarColeccion();
    }

    private void observarColeccion() {
        db.collection("lugares")
                .orderBy("nombre")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    List<Lugar> lista = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Lugar l = d.toObject(Lugar.class);
                        // setea el id del doc para poder editar/eliminar
                        l.setId(d.getId());
                        lista.add(l);
                    }
                    adapter.submit(lista);
                });
    }

    private void abrirDialogo(@Nullable Lugar original) {
        LugarDialog dialog = new LugarDialog(original, lugar -> {
            if (lugar.getId() == null) {
                db.collection("lugares").add(lugar);
            } else {
                db.collection("lugares").document(lugar.getId()).set(lugar)
                        .addOnSuccessListener(aVoid -> {
                            // 🆕 Actualizar el nombre en todas las actividades que usan este lugar
                            if (original != null && !lugar.getNombre().equals(original.getNombre())) {
                                android.util.Log.d("Lugares", "🔄 Nombre cambió de '" + original.getNombre() + "' a '" + lugar.getNombre() + "' - actualizando actividades...");
                                actualizarNombreEnActividades(original.getNombre(), lugar.getNombre());
                            }
                        });
            }
        });
        dialog.show(getParentFragmentManager(), "LugarDialog");
    }

    /**
     * 🆕 Actualizar el nombre del lugar en todas las actividades Y SUS CITAS
     * NOTA: Los lugares se guardan solo por nombre (no por ID) en las actividades
     */
    private void actualizarNombreEnActividades(String nombreAntiguo, String nuevoNombre) {
        android.util.Log.d("Lugares", "📝 Buscando actividades con lugar: '" + nombreAntiguo + "'");

        // Actualizar en colección "activities" (EN) - campo "lugar"
        db.collection("activities")
                .whereEqualTo("lugar", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "🔍 Encontradas " + querySnapshot.size() + " actividades con lugar='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        // Actualizar actividad
                        doc.getReference().update("lugar", nuevoNombre, "lugarNombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("Lugares", "    ✅ Actualizado: " + doc.getId());
                                    // También actualizar las citas de esta actividad
                                    actualizarLugarEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                                })
                                .addOnFailureListener(e -> android.util.Log.e("Lugares", "    ❌ Error: " + e.getMessage()));
                    }
                });

        // Actualizar en colección "actividades" (ES) - campo "lugar"
        db.collection("actividades")
                .whereEqualTo("lugar", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "🔍 Encontradas " + querySnapshot.size() + " actividades con lugar='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("lugar", nuevoNombre, "lugarNombre", nuevoNombre);
                    }
                });

        // Actualizar también en campo "lugarNombre" si existe
        db.collection("activities")
                .whereEqualTo("lugarNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "🔍 Encontradas " + querySnapshot.size() + " actividades con lugarNombre='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("lugar", nuevoNombre, "lugarNombre", nuevoNombre);
                        actualizarLugarEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("lugarNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "🔍 Encontradas " + querySnapshot.size() + " actividades con lugarNombre='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("lugar", nuevoNombre, "lugarNombre", nuevoNombre);
                    }
                });
    }

    /**
     * 🆕 Actualiza el lugar en todas las citas de una actividad
     */
    private void actualizarLugarEnCitas(String actividadId, String nombreAntiguo, String nuevoNombre) {
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("lugar", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "      🔍 Encontradas " + querySnapshot.size() + " citas con lugar='" + nombreAntiguo + "' en actividad " + actividadId);
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("lugar", nuevoNombre, "lugarNombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Lugares", "      ✅ Cita actualizada: " + citaDoc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Lugares", "      ❌ Error actualizando cita: " + e.getMessage()));
                    }
                });

        // También buscar por lugarNombre en las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("lugarNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("lugar", nuevoNombre, "lugarNombre", nuevoNombre);
                    }
                });
    }
}
