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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            @Override public void onEliminar(Lugar l) { confirmarEliminar(l); }
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

    private void confirmarEliminar(Lugar item) {
        if (item == null || item.getId() == null) return;

        // Validar que no haya actividades activas usando este lugar
        verificarActividadesActivas(item.getNombre(), tieneActividades -> {
            if (tieneActividades) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("No se puede eliminar")
                        .setMessage("El lugar \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                "Primero debes eliminar o modificar esas actividades.")
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // Si no tiene actividades, permitir eliminar
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar lugar")
                    .setMessage("¿Eliminar \"" + item.getNombre() + "\" de forma permanente?")
                    .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                    .setPositiveButton("Eliminar", (d, w) -> db.collection("lugares").document(item.getId())
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

    private void toggleActivo(Lugar item) {
        if (item == null || item.getId() == null) return;

        boolean nuevo = !item.isActivo();

        // Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            verificarActividadesActivas(item.getNombre(), tieneActividades -> {
                if (tieneActividades) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No se puede desactivar")
                            .setMessage("El lugar \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                    "Primero debes eliminar o modificar esas actividades.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection("lugares").document(item.getId())
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection("lugares").document(item.getId())
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Verifica si hay actividades activas usando este lugar
     */
    private void verificarActividadesActivas(String lugarNombre, Callback<Boolean> callback) {
        android.util.Log.d("Lugares", "🔍 Verificando actividades y citas para lugar: " + lugarNombre);

        // Buscar actividades con campo 'lugar' (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereEqualTo("lugar", lugarNombre)
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
                            android.util.Log.d("Lugares", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("lugarNombre", lugarNombre)
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
                                        android.util.Log.d("Lugares", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                // Si no hay actividades activas, verificar citas programadas
                                verificarCitasProgramadas(lugarNombre, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Lugares", "Error verificando actividades: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Lugares", "Error verificando actividades: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este lugar
     */
    private void verificarCitasProgramadas(String lugarNombre, Callback<Boolean> callback) {
        android.util.Log.d("Lugares", "🔍 Verificando citas programadas para lugar: " + lugarNombre);

        db.collectionGroup("citas")
                .whereEqualTo("lugar", lugarNombre)
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
                            android.util.Log.d("Lugares", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collectionGroup("citas")
                            .whereEqualTo("lugarNombre", lugarNombre)
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
                                        android.util.Log.d("Lugares", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                android.util.Log.d("Lugares", "✅ No hay actividades ni citas activas");
                                callback.onResult(false);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Lugares", "Error verificando citas: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Lugares", "Error verificando citas: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Actualiza todas las actividades que usan este lugar, poniendo el campo en null
     */
    private void actualizarActividadesAlEliminar(String nombre) {
        android.util.Log.d("Lugares", "🗑️ Actualizando actividades que usaban lugar: " + nombre);

        // Buscar actividades con ese lugar (campo "lugar")
        db.collection("activities")
                .whereEqualTo("lugar", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "🔍 Encontradas " + querySnapshot.size() + " actividades con lugar='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lugar", null);
                        updates.put("lugarNombre", null);
                        updates.put("lugarId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Lugares", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Lugares", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar por campo "lugarNombre"
        db.collection("activities")
                .whereEqualTo("lugarNombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "🔍 Encontradas " + querySnapshot.size() + " actividades con lugarNombre='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lugar", null);
                        updates.put("lugarNombre", null);
                        updates.put("lugarId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Lugares", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Lugares", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar en colección "actividades" (ES)
        db.collection("actividades")
                .whereEqualTo("lugar", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lugar", null);
                        updates.put("lugarNombre", null);
                        updates.put("lugarId", null);

                        doc.getReference().update(updates);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("lugarNombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lugar", null);
                        updates.put("lugarNombre", null);
                        updates.put("lugarId", null);

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
