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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
            @Override public void onEliminar(Oferente o){ confirmarEliminar(o); }
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
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("Oferentes", "    ✅ Actualizado: " + doc.getId());
                                    // También actualizar las citas de esta actividad
                                    actualizarOferenteEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                                })
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
                        actualizarOferenteEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
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
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("Oferentes", "    ✅ Array actualizado en: " + doc.getId());
                    // También actualizar citas
                    actualizarOferenteEnCitas(doc.getId(), nombreAntiguo, nuevoNombre);
                })
                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "    ❌ Error actualizando array: " + e.getMessage()));
    }

    /**
     * 🆕 Actualiza el oferente en todas las citas de una actividad
     */
    private void actualizarOferenteEnCitas(String actividadId, String nombreAntiguo, String nuevoNombre) {
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("oferente", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "      🔍 Encontradas " + querySnapshot.size() + " citas con oferente='" + nombreAntiguo + "' en actividad " + actividadId);
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("oferente", nuevoNombre, "oferenteNombre", nuevoNombre)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Oferentes", "      ✅ Cita actualizada: " + citaDoc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "      ❌ Error actualizando cita: " + e.getMessage()));
                    }
                });

        // También buscar por oferenteNombre en las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereEqualTo("oferenteNombre", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        citaDoc.getReference().update("oferente", nuevoNombre, "oferenteNombre", nuevoNombre);
                    }
                });

        // Actualizar oferentes en arrays dentro de las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereArrayContains("oferentes", nombreAntiguo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "      🔍 Encontradas " + querySnapshot.size() + " citas con oferentes[] contiene '" + nombreAntiguo + "'");
                    for (QueryDocumentSnapshot citaDoc : querySnapshot) {
                        List<String> oferentes = (List<String>) citaDoc.get("oferentes");
                        if (oferentes != null) {
                            List<String> nuevosOferentes = new ArrayList<>();
                            for (String oferente : oferentes) {
                                nuevosOferentes.add(oferente.equals(nombreAntiguo) ? nuevoNombre : oferente);
                            }
                            citaDoc.getReference().update("oferentes", nuevosOferentes)
                                    .addOnSuccessListener(aVoid -> android.util.Log.d("Oferentes", "      ✅ Cita array actualizado: " + citaDoc.getId()));
                        }
                    }
                });
    }

    private void confirmarEliminar(Oferente item) {
        if (item == null || item.getId() == null || item.getId().isEmpty()) return;

        // Validar que no haya actividades activas usando este oferente
        verificarActividadesActivas(item.getNombre(), tieneActividades -> {
            if (tieneActividades) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("No se puede eliminar")
                        .setMessage("El oferente \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                "Primero debes eliminar o modificar esas actividades.")
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // Si no tiene actividades, permitir eliminar
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar oferente")
                    .setMessage("¿Eliminar \"" + item.getNombre() + "\" de forma permanente?")
                    .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                    .setPositiveButton("Eliminar", (d, w) -> db.collection(COL_OFERENTES).document(item.getId())
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

    private void toggleActivo(Oferente item) {
        if (item == null || item.getId() == null || item.getId().isEmpty()) return;

        boolean nuevo = !item.isActivo();

        // Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            verificarActividadesActivas(item.getNombre(), tieneActividades -> {
                if (tieneActividades) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No se puede desactivar")
                            .setMessage("El oferente \"" + item.getNombre() + "\" está asociado a actividades activas.\n\n" +
                                    "Primero debes eliminar o modificar esas actividades.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection(COL_OFERENTES).document(item.getId())
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection(COL_OFERENTES).document(item.getId())
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> android.widget.Toast.makeText(getContext(), "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Verifica si hay actividades activas usando este oferente
     */
    private void verificarActividadesActivas(String oferenteNombre, Callback<Boolean> callback) {
        android.util.Log.d("Oferentes", "🔍 Verificando actividades y citas para oferente: " + oferenteNombre);

        // Buscar actividades con campo 'oferente' (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereEqualTo("oferente", oferenteNombre)
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
                            android.util.Log.d("Oferentes", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("oferenteNombre", oferenteNombre)
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
                                        android.util.Log.d("Oferentes", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                // Si no hay actividades activas, verificar citas programadas
                                verificarCitasProgramadas(oferenteNombre, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Oferentes", "Error verificando actividades: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Oferentes", "Error verificando actividades: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este oferente
     */
    private void verificarCitasProgramadas(String oferenteNombre, Callback<Boolean> callback) {
        android.util.Log.d("Oferentes", "🔍 Verificando citas programadas para oferente: " + oferenteNombre);

        db.collectionGroup("citas")
                .whereEqualTo("oferente", oferenteNombre)
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
                            android.util.Log.d("Oferentes", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collectionGroup("citas")
                            .whereEqualTo("oferenteNombre", oferenteNombre)
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
                                        android.util.Log.d("Oferentes", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                android.util.Log.d("Oferentes", "✅ No hay actividades ni citas activas");
                                callback.onResult(false);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Oferentes", "Error verificando citas: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Oferentes", "Error verificando citas: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Actualiza todas las actividades que usan este oferente, poniendo el campo en null
     */
    private void actualizarActividadesAlEliminar(String nombre) {
        android.util.Log.d("Oferentes", "🗑️ Actualizando actividades que usaban oferente: " + nombre);

        // Buscar actividades con ese oferente (campo "oferente")
        db.collection("activities")
                .whereEqualTo("oferente", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferente='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("oferente", null);
                        updates.put("oferenteNombre", null);
                        updates.put("oferenteId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Oferentes", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar por campo "oferenteNombre"
        db.collection("activities")
                .whereEqualTo("oferenteNombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "🔍 Encontradas " + querySnapshot.size() + " actividades con oferenteNombre='" + nombre + "'");
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("oferente", null);
                        updates.put("oferenteNombre", null);
                        updates.put("oferenteId", null);

                        doc.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("Oferentes", "✅ Actividad actualizada: " + doc.getId()))
                                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error: " + e.getMessage()));
                    }
                });

        // Buscar en colección "actividades" (ES)
        db.collection("actividades")
                .whereEqualTo("oferente", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("oferente", null);
                        updates.put("oferenteNombre", null);
                        updates.put("oferenteId", null);

                        doc.getReference().update(updates);
                    }
                });

        db.collection("actividades")
                .whereEqualTo("oferenteNombre", nombre)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("oferente", null);
                        updates.put("oferenteNombre", null);
                        updates.put("oferenteId", null);

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