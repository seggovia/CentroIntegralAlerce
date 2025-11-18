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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
            @Override public void onEliminar(TipoActividad t){ confirmarEliminar(t); }
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

    private void confirmarEliminar(TipoActividad item) {
        if (item == null || item.getId() == null) return;

        // Validar que no haya actividades activas usando este tipo
        verificarActividadesActivas(item.getNombre(), tieneActividades -> {
            if (tieneActividades) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("No se puede eliminar")
                        .setMessage("El tipo de actividad \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                "Primero debes eliminar o modificar esas actividades.")
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // Si no tiene actividades, permitir eliminar
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar tipo de actividad")
                    .setMessage("¿Eliminar \"" + item.getNombre() + "\" de forma permanente?")
                    .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                    .setPositiveButton("Eliminar", (d, w) -> db.collection("tiposActividad").document(item.getId())
                            .delete()
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(getContext(), "Eliminado", Toast.LENGTH_SHORT).show();
                                // Actualizar actividades poniendo el campo en null
                                actualizarActividadesAlEliminar(item.getNombre());
                            })
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                    .show();
        });
    }

    private void toggleActivo(TipoActividad item) {
        if (item == null || item.getId() == null) return;

        boolean nuevo = !item.isActivo();

        // Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            verificarActividadesActivas(item.getNombre(), tieneActividades -> {
                if (tieneActividades) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No se puede desactivar")
                            .setMessage("El tipo de actividad \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                    "Primero debes eliminar o modificar esas actividades.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection("tiposActividad").document(item.getId())
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection("tiposActividad").document(item.getId())
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Verifica si hay actividades activas usando este tipo de actividad
     */
    private void verificarActividadesActivas(String tipoNombre, Callback<Boolean> callback) {
        android.util.Log.d("TiposActividad", "🔍 Verificando actividades y citas para tipo: " + tipoNombre);

        // Buscar actividades con campo 'tipo' (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereEqualTo("tipo", tipoNombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Filtrar manualmente para incluir actividades sin estado o con estado activo
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        // Si NO tiene estado, o tiene estado pero NO es cancelada/completada
                        if (estado == null ||
                            (!estado.equalsIgnoreCase("cancelada") &&
                             !estado.equalsIgnoreCase("canceled") &&
                             !estado.equalsIgnoreCase("completada") &&
                             !estado.equalsIgnoreCase("completed") &&
                             !estado.equalsIgnoreCase("finalizada"))) {
                            android.util.Log.d("TiposActividad", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("tipoActividad", tipoNombre)
                            .get()
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    if (estado == null ||
                                        (!estado.equalsIgnoreCase("cancelada") &&
                                         !estado.equalsIgnoreCase("canceled") &&
                                         !estado.equalsIgnoreCase("completada") &&
                                         !estado.equalsIgnoreCase("completed") &&
                                         !estado.equalsIgnoreCase("finalizada"))) {
                                        android.util.Log.d("TiposActividad", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                // Si no hay actividades activas, verificar citas programadas
                                verificarCitasProgramadas(tipoNombre, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("TiposActividad", "Error verificando actividades: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("TiposActividad", "Error verificando actividades: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este tipo de actividad
     */
    private void verificarCitasProgramadas(String tipoNombre, Callback<Boolean> callback) {
        android.util.Log.d("TiposActividad", "🔍 Verificando citas programadas para tipo: " + tipoNombre);

        db.collectionGroup("citas")
                .whereEqualTo("tipo", tipoNombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Filtrar manualmente para incluir citas sin estado o con estado programada
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        if (estado == null ||
                            (!estado.equalsIgnoreCase("cancelada") &&
                             !estado.equalsIgnoreCase("canceled") &&
                             !estado.equalsIgnoreCase("completada") &&
                             !estado.equalsIgnoreCase("completed") &&
                             !estado.equalsIgnoreCase("finalizada"))) {
                            android.util.Log.d("TiposActividad", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collectionGroup("citas")
                            .whereEqualTo("tipoActividad", tipoNombre)
                            .get()
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    if (estado == null ||
                                        (!estado.equalsIgnoreCase("cancelada") &&
                                         !estado.equalsIgnoreCase("canceled") &&
                                         !estado.equalsIgnoreCase("completada") &&
                                         !estado.equalsIgnoreCase("completed") &&
                                         !estado.equalsIgnoreCase("finalizada"))) {
                                        android.util.Log.d("TiposActividad", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                android.util.Log.d("TiposActividad", "✅ No hay actividades ni citas activas");
                                callback.onResult(false);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("TiposActividad", "Error verificando citas: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("TiposActividad", "Error verificando citas: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Actualiza todas las actividades que usan este tipo, poniendo el campo en null
     */
    private void actualizarActividadesAlEliminar(String nombre) {
        android.util.Log.d("TiposActividad", "🗑️ Actualizando actividades que usaban tipo: " + nombre);

        // Buscar actividades con ese tipo (campo "tipo")
        db.collection("activities")
                .whereEqualTo("tipo", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "🔍 Encontradas " + querySnapshot.size() + " actividades con tipo='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("tipo", null);
                        updates.put("tipoActividad", null);
                        updates.put("tipoActividadId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("TiposActividad", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("TiposActividad", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar por campo "tipoActividad"
        db.collection("activities")
                .whereEqualTo("tipoActividad", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "🔍 Encontradas " + querySnapshot.size() + " actividades con tipoActividad='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("tipo", null);
                        updates.put("tipoActividad", null);
                        updates.put("tipoActividadId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("TiposActividad", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("TiposActividad", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar en colección "actividades" (ES)
        db.collection("actividades")
                .whereEqualTo("tipo", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("tipo", null);
                        updates.put("tipoActividad", null);
                        updates.put("tipoActividadId", null);

                        doc.getReference().update(updates);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("tipoActividad", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("tipo", null);
                        updates.put("tipoActividad", null);
                        updates.put("tipoActividadId", null);

                        doc.getReference().update(updates);
                    }
                });
    }

    /**
     * Interfaz para callbacks
     */
    private interface Callback<T> {
        void onResult(T result);
    }
}