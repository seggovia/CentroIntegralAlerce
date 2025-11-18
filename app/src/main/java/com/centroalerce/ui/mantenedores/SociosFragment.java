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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
            @Override public void onEliminar(SocioComunitario s){ confirmarEliminar(s); }
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

    private void confirmarEliminar(SocioComunitario item) {
        if (item == null || item.getId() == null) return;

        // Validar que no haya actividades activas usando este socio
        verificarActividadesActivas(item.getNombre(), tieneActividades -> {
            if (tieneActividades) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("No se puede eliminar")
                        .setMessage("El socio comunitario \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                "Primero debes eliminar o modificar esas actividades.")
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // Si no tiene actividades, permitir eliminar
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar socio comunitario")
                    .setMessage("¿Eliminar \"" + item.getNombre() + "\" de forma permanente?")
                    .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                    .setPositiveButton("Eliminar", (d, w) -> db.collection("socios").document(item.getId())
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

    private void toggleActivo(SocioComunitario item) {
        if (item == null || item.getId() == null) return;

        boolean nuevo = !item.isActivo();

        // Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            verificarActividadesActivas(item.getNombre(), tieneActividades -> {
                if (tieneActividades) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No se puede desactivar")
                            .setMessage("El socio comunitario \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                    "Primero debes eliminar o modificar esas actividades.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection("socios").document(item.getId())
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection("socios").document(item.getId())
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Verifica si hay actividades activas usando este socio comunitario
     */
    private void verificarActividadesActivas(String socioNombre, Callback<Boolean> callback) {
        android.util.Log.d("Socios", "🔍 Verificando actividades y citas para socio: " + socioNombre);

        // Buscar actividades con campo 'socioComunitario' (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereEqualTo("socioComunitario", socioNombre)
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
                            android.util.Log.d("Socios", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el campo "socio"
                    db.collection("activities")
                            .whereEqualTo("socio", socioNombre)
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
                                        android.util.Log.d("Socios", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                // Si no hay actividades activas, verificar citas programadas
                                verificarCitasProgramadas(socioNombre, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Socios", "Error verificando actividades: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Socios", "Error verificando actividades: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este socio comunitario
     */
    private void verificarCitasProgramadas(String socioNombre, Callback<Boolean> callback) {
        android.util.Log.d("Socios", "🔍 Verificando citas programadas para socio: " + socioNombre);

        db.collectionGroup("citas")
                .whereEqualTo("socioComunitario", socioNombre)
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
                            android.util.Log.d("Socios", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collectionGroup("citas")
                            .whereEqualTo("socio", socioNombre)
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
                                        android.util.Log.d("Socios", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                android.util.Log.d("Socios", "✅ No hay actividades ni citas activas");
                                callback.onResult(false);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Socios", "Error verificando citas: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Socios", "Error verificando citas: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Actualiza todas las actividades que usan este socio, poniendo el campo en null
     */
    private void actualizarActividadesAlEliminar(String nombre) {
        android.util.Log.d("Socios", "🗑️ Actualizando actividades que usaban socio: " + nombre);

        // Buscar actividades con ese socio (campo "socioComunitario")
        db.collection("activities")
                .whereEqualTo("socioComunitario", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socioComunitario='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("socioComunitario", null);
                        updates.put("socio", null);
                        updates.put("socio_nombre", null);
                        updates.put("socioId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Socios", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Socios", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar por campo "socio"
        db.collection("activities")
                .whereEqualTo("socio", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socio='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("socioComunitario", null);
                        updates.put("socio", null);
                        updates.put("socio_nombre", null);
                        updates.put("socioId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Socios", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Socios", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar por campo "socio_nombre"
        db.collection("activities")
                .whereEqualTo("socio_nombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "🔍 Encontradas " + querySnapshot.size() + " actividades con socio_nombre='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("socioComunitario", null);
                        updates.put("socio", null);
                        updates.put("socio_nombre", null);
                        updates.put("socioId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Socios", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Socios", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar en colección "actividades" (ES)
        db.collection("actividades")
                .whereEqualTo("socioComunitario", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("socioComunitario", null);
                        updates.put("socio", null);
                        updates.put("socio_nombre", null);
                        updates.put("socioId", null);

                        doc.getReference().update(updates);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("socio", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("socioComunitario", null);
                        updates.put("socio", null);
                        updates.put("socio_nombre", null);
                        updates.put("socioId", null);

                        doc.getReference().update(updates);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("socio_nombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("socioComunitario", null);
                        updates.put("socio", null);
                        updates.put("socio_nombre", null);
                        updates.put("socioId", null);

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