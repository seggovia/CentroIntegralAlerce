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
        // 🔍 Log para debug: verificar el nombre original
        if (original != null) {
            android.util.Log.d("Oferentes", "🔍 DEBUG: Abriendo diálogo con original.nombre = '" + original.getNombre() + "'");
        } else {
            android.util.Log.d("Oferentes", "🔍 DEBUG: Abriendo diálogo para NUEVO oferente");
        }

        new OferenteDialog(original, new OferenteDialog.OnGuardar() {
            @Override public void onGuardar(Oferente o) {
                // 🔍 Log para debug: verificar el nombre recibido
                android.util.Log.d("Oferentes", "🔍 DEBUG: onGuardar recibió o.nombre = '" + o.getNombre() + "'");

                if (o.getId() == null || o.getId().isEmpty()) {
                    // Aseguramos que los nuevos queden activos
                    o.setActivo(true);
                    db.collection(COL_OFERENTES).add(o);
                } else {
                    // Guardar el oferente
                    db.collection(COL_OFERENTES).document(o.getId()).set(o)
                            .addOnSuccessListener(aVoid -> {
                                // 🆕 Actualizar el nombre en todas las actividades que usan este oferente
                                if (original != null) {
                                    android.util.Log.d("Oferentes", "🔍 DEBUG: Comparando nombres:");
                                    android.util.Log.d("Oferentes", "    - original.getNombre() = '" + original.getNombre() + "'");
                                    android.util.Log.d("Oferentes", "    - o.getNombre() = '" + o.getNombre() + "'");
                                    android.util.Log.d("Oferentes", "    - ¿Son iguales? " + o.getNombre().equals(original.getNombre()));

                                    if (!o.getNombre().equals(original.getNombre())) {
                                        android.util.Log.d("Oferentes", "🔄 Nombre cambió de '" + original.getNombre() + "' a '" + o.getNombre() + "' - actualizando actividades...");
                                        actualizarNombreEnActividades(original.getNombre(), o.getNombre());
                                    } else {
                                        android.util.Log.d("Oferentes", "ℹ️ Nombre no cambió, no se actualiza");
                                    }
                                } else {
                                    android.util.Log.d("Oferentes", "ℹ️ Nuevo oferente, no hay original para comparar");
                                }
                            });
                }
            }
        }).show(getParentFragmentManager(), "OferenteDialog");
    }

    /**
     * 🆕 Actualiza el nombre del oferente en todas las actividades que lo usan
     * NOTA: Los oferentes se guardan solo por nombre (no por ID) en las actividades,
     * por lo que buscamos por el nombre antiguo y lo reemplazamos por el nuevo
     */
    private void actualizarNombreEnActividades(String nombreAntiguo, String nuevoNombre) {
        android.util.Log.d("Oferentes", "📝 Buscando actividades con oferente: '" + nombreAntiguo + "'");

        // Actualizar en colección "activities" (EN) - campo "oferente"
        db.collection("activities")
                .whereEqualTo("oferente", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferente='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        android.util.Log.d("Oferentes", "  📄 Actualizando actividad: " + doc.getId());
                        doc.getReference().update("oferente", nuevoNombre, "oferenteNombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Oferentes", "    ✅ Actualizado: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "    ❌ Error: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error buscando en 'activities': " + e.getMessage()));

        // Actualizar en colección "actividades" (ES) - campo "oferente"
        db.collection("actividades")
                .whereEqualTo("oferente", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferente='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("oferente", nuevoNombre, "oferenteNombre", nuevoNombre);
                    }
                })
                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error buscando en 'actividades': " + e.getMessage()));

        // Actualizar también en campo "oferenteNombre" si existe
        db.collection("activities")
                .whereEqualTo("oferenteNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferenteNombre='" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("oferente", nuevoNombre, "oferenteNombre", nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("oferenteNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferenteNombre='" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().update("oferente", nuevoNombre, "oferenteNombre", nuevoNombre);
                    }
                });

        // Actualizar en arrays "oferentes" (múltiples oferentes)
        db.collection("activities")
                .whereArrayContains("oferentes", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferentes[] contiene '" + nombreAntiguo + "' en 'activities'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        actualizarOferenteEnArray(doc, nombreAntiguo, nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereArrayContains("oferentes", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferentes[] contiene '" + nombreAntiguo + "' en 'actividades'");
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        actualizarOferenteEnArray(doc, nombreAntiguo, nuevoNombre);
                    }
                });
    }

    /**
     * 🆕 Actualiza el nombre de un oferente dentro de un array de oferentes
     */
    private void actualizarOferenteEnArray(QueryDocumentSnapshot doc, String nombreAntiguo, String nuevoNombre) {
        List<String> oferentes = (List<String>) doc.get("oferentes");
        if (oferentes == null) return;

        // Crear nueva lista con el nombre actualizado
        List<String> nuevosOferentes = new ArrayList<>();
        for (String oferente : oferentes) {
            if (oferente.equals(nombreAntiguo)) {
                nuevosOferentes.add(nuevoNombre);
            } else {
                nuevosOferentes.add(oferente);
            }
        }

        // Actualizar el documento
        doc.getReference().update("oferentes", nuevosOferentes)
                .addOnSuccessListener(aVoid -> android.util.Log.d("Oferentes", "    ✅ Array actualizado en: " + doc.getId()))
                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "    ❌ Error actualizando array: " + e.getMessage()));
    }
}