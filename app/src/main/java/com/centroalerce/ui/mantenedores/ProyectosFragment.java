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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
            @Override public void onEliminar(Proyecto p){ confirmarEliminar(p); }
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

    private void confirmarEliminar(Proyecto item) {
        if (item == null || item.getId() == null) return;

        // Validar que no haya actividades activas usando este proyecto
        verificarActividadesActivas(item.getNombre(), tieneActividades -> {
            if (tieneActividades) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("No se puede eliminar")
                        .setMessage("El proyecto \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                "Primero debes eliminar o modificar esas actividades.")
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // Si no tiene actividades, permitir eliminar
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar proyecto")
                    .setMessage("¿Eliminar \"" + item.getNombre() + "\" de forma permanente?")
                    .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                    .setPositiveButton("Eliminar", (d, w) -> db.collection("proyectos").document(item.getId())
                            .delete()
                            .addOnSuccessListener(unused -> {
                                android.widget.Toast.makeText(getContext(), "Eliminado", android.widget.Toast.LENGTH_SHORT).show();
                                // Actualizar actividades poniendo el campo en null
                                actualizarActividadesAlEliminar(item.getNombre());
                            })
                            .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show()))
                    .show();
        });
    }

    private void toggleActivo(Proyecto item) {
        if (item == null || item.getId() == null) return;

        boolean nuevo = !item.isActivo();

        // Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            verificarActividadesActivas(item.getNombre(), tieneActividades -> {
                if (tieneActividades) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No se puede desactivar")
                            .setMessage("El proyecto \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                    "Primero debes eliminar o modificar esas actividades.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection("proyectos").document(item.getId())
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection("proyectos").document(item.getId())
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Verifica si hay actividades activas usando este proyecto
     */
    private void verificarActividadesActivas(String proyectoNombre, Callback<Boolean> callback) {
        android.util.Log.d("Proyectos", "🔍 Verificando actividades y citas para proyecto: " + proyectoNombre);

        // Buscar actividades con campo 'proyecto' (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereEqualTo("proyecto", proyectoNombre)
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
                            android.util.Log.d("Proyectos", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("proyectoNombre", proyectoNombre)
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
                                        android.util.Log.d("Proyectos", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                // Si no hay actividades activas, verificar citas programadas
                                verificarCitasProgramadas(proyectoNombre, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Proyectos", "Error verificando actividades: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Proyectos", "Error verificando actividades: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este proyecto
     */
    private void verificarCitasProgramadas(String proyectoNombre, Callback<Boolean> callback) {
        android.util.Log.d("Proyectos", "🔍 Verificando citas programadas para proyecto: " + proyectoNombre);

        db.collectionGroup("citas")
                .whereEqualTo("proyecto", proyectoNombre)
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
                            android.util.Log.d("Proyectos", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collectionGroup("citas")
                            .whereEqualTo("proyectoNombre", proyectoNombre)
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
                                        android.util.Log.d("Proyectos", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                android.util.Log.d("Proyectos", "✅ No hay actividades ni citas activas");
                                callback.onResult(false);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Proyectos", "Error verificando citas: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Proyectos", "Error verificando citas: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Actualiza todas las actividades que usan este proyecto, poniendo el campo en null
     */
    private void actualizarActividadesAlEliminar(String nombre) {
        android.util.Log.d("Proyectos", "🗑️ Actualizando actividades que usaban proyecto: " + nombre);

        // Buscar actividades con ese proyecto (campo "proyecto")
        db.collection("activities")
                .whereEqualTo("proyecto", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "🔍 Encontradas " + querySnapshot.size() + " actividades con proyecto='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("proyecto", null);
                        updates.put("proyectoNombre", null);
                        updates.put("proyectoId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Proyectos", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Proyectos", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar por campo "proyectoNombre"
        db.collection("activities")
                .whereEqualTo("proyectoNombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "🔍 Encontradas " + querySnapshot.size() + " actividades con proyectoNombre='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("proyecto", null);
                        updates.put("proyectoNombre", null);
                        updates.put("proyectoId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Proyectos", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Proyectos", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar en colección "actividades" (ES)
        db.collection("actividades")
                .whereEqualTo("proyecto", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("proyecto", null);
                        updates.put("proyectoNombre", null);
                        updates.put("proyectoId", null);

                        doc.getReference().update(updates);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("proyectoNombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("proyecto", null);
                        updates.put("proyectoNombre", null);
                        updates.put("proyectoId", null);

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