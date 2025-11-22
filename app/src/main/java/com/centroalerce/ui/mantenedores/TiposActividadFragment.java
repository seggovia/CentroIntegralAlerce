package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.TipoActividad;
import com.centroalerce.gestion.utils.CustomToast;
import com.centroalerce.ui.mantenedores.adapter.TipoActividadAdapter;
import com.centroalerce.ui.mantenedores.dialog.TipoActividadDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.*;
import java.util.*;

public class TiposActividadFragment extends Fragment {

    private FirebaseFirestore db;
    private RecyclerView rv;
    private FloatingActionButton fab;
    private View bottomBarSeleccion;
    private TextView tvSeleccionados;
    private MaterialButton btnEliminarSeleccion;
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

        rv = v.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        fab = v.findViewById(R.id.fabAgregar);
        bottomBarSeleccion = v.findViewById(R.id.bottomBarSeleccion);
        tvSeleccionados = v.findViewById(R.id.tvSeleccionados);
        btnEliminarSeleccion = v.findViewById(R.id.btnEliminarSeleccion);

        adapter=new TipoActividadAdapter(new TipoActividadAdapter.Callbacks(){
            @Override public void onEditar(TipoActividad t){ abrirDialogo(t); }
            @Override public void onEliminar(TipoActividad t){ confirmarEliminar(t); }
            @Override public void onSelectionChanged(int selectedCount) { actualizarBarraSeleccion(selectedCount); }
        });
        rv.setAdapter(adapter);

        fab.setOnClickListener(x->abrirDialogo(null));

        if (btnEliminarSeleccion != null) {
            btnEliminarSeleccion.setOnClickListener(x -> eliminarSeleccionados());
        }

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

    private void actualizarBarraSeleccion(int selectedCount) {
        if (bottomBarSeleccion == null || tvSeleccionados == null || fab == null) return;

        if (selectedCount > 0) {
            bottomBarSeleccion.setVisibility(View.VISIBLE);
            tvSeleccionados.setText(selectedCount == 1
                    ? "1 elemento seleccionado"
                    : selectedCount + " elementos seleccionados");
            fab.hide();
        } else {
            bottomBarSeleccion.setVisibility(View.GONE);
            fab.show();
        }
    }

    private void eliminarSeleccionados() {
        if (adapter == null) return;
        List<TipoActividad> seleccionados = adapter.getSelectedItems();
        if (seleccionados == null || seleccionados.isEmpty()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar")
                .setMessage(seleccionados.size() == 1
                        ? "¿Eliminar el tipo de actividad seleccionado?"
                        : "¿Eliminar los " + seleccionados.size() + " tipos de actividad seleccionados?")
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Eliminar", (d, w) -> {
                    for (TipoActividad t : seleccionados) {
                        if (t != null) {
                            confirmarEliminar(t);
                        }
                    }
                    adapter.clearSelection();
                })
                .show();
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
                            CustomToast.showSuccess(getContext(), "Tipo de actividad creado con éxito");
                        })
                        .addOnFailureListener(e ->
                                CustomToast.showError(getContext(), "Error al crear: " + e.getMessage()));
            } else {
                // ACTUALIZAR EXISTENTE
                db.collection("tiposActividad").document(t.getId()).set(t)
                        .addOnSuccessListener(unused -> {
                            // 🆕 Actualizar el nombre en todas las actividades que usan este tipo
                            if (original != null && !t.getNombre().equals(original.getNombre())) {
                                android.util.Log.d("TiposActividad", "🔄 Nombre cambió de '" + original.getNombre() + "' a '" + t.getNombre() + "' - actualizando actividades...");
                                actualizarNombreEnActividades(original.getNombre(), t.getNombre());

                                // 🔔 Notificar a la lista de actividades para que recargue
                                android.os.Bundle result = new android.os.Bundle();
                                result.putBoolean("tipoActividadUpdated", true);
                                try {
                                    getParentFragmentManager().setFragmentResult("actividad_change", result);
                                } catch (Exception ignored) {}
                                try {
                                    requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", result);
                                } catch (Exception ignored) {}
                            }
                            CustomToast.showSuccess(getContext(), "Tipo de actividad actualizado con éxito");
                        })
                        .addOnFailureListener(e ->
                                CustomToast.showError(getContext(), "Error al actualizar: " + e.getMessage()));
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

        // Mostrar diálogo de carga mientras se verifican las actividades
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Verificando actividades asociadas...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 🆕 Validar que no haya actividades activas usando este tipo
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
            progressDialog.dismiss();
            if (tieneActividades) {
                String mensaje = resultado.construirMensaje("tipo de actividad", item.getNombre());
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ No se puede eliminar")
                        .setMessage(mensaje)
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
                                CustomToast.showSuccess(getContext(), "Tipo de actividad eliminado con éxito");
                                // Actualizar actividades poniendo el campo en null
                                actualizarActividadesAlEliminar(item.getNombre());
                            })
                            .addOnFailureListener(e -> CustomToast.showError(getContext(), "Error al eliminar: " + e.getMessage())))
                    .show();
        });
    }

    private void toggleActivo(TipoActividad item) {
        if (item == null || item.getId() == null) return;

        boolean nuevo = !item.isActivo();

        // 🆕 Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            ResultadoValidacion resultado = new ResultadoValidacion();
            verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
                if (tieneActividades) {
                    String mensaje = resultado.construirMensaje("tipo de actividad", item.getNombre());
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("❌ No se puede desactivar")
                            .setMessage(mensaje)
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection("tiposActividad").document(item.getId())
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> CustomToast.showError(getContext(), "Error: " + e.getMessage()));
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection("tiposActividad").document(item.getId())
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Error: " + e.getMessage()));
        }
    }

    /**
     * 🆕 Verifica si hay actividades o citas programadas usando este tipo de actividad
     * IMPORTANTE: Busca actividades sin campo estado (activas por defecto) Y con estado activo
     */
    private void verificarActividadesActivas(String tipoNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(tipoNombre, resultado, callback);
    }

    private void verificarActividadesActivasDetallado(String tipoNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("TiposActividad", "🔍 Verificando actividades y citas para tipo: " + tipoNombre);

        // Set para evitar duplicados (usar ID del documento como clave)
        java.util.Set<String> actividadesEncontradas = new java.util.HashSet<>();

        // Buscar actividades con campo 'tipo'
        db.collection("activities")
                .whereEqualTo("tipo", tipoNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("TiposActividad", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    // Recopilar actividades bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        String nombre = doc.getString("nombre");
                        android.util.Log.d("TiposActividad", "  📄 Actividad: id=" + doc.getId() + ", nombre=" + nombre + ", estado='" + estado + "'");

                        // Excluir explícitamente las completadas
                        boolean esCompletada = estado != null &&
                            (estado.equalsIgnoreCase("completada") ||
                             estado.equalsIgnoreCase("finalizada") ||
                             estado.equalsIgnoreCase("cancelada") ||
                             estado.equalsIgnoreCase("completed") ||
                             estado.equalsIgnoreCase("canceled"));

                        if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                            if (nombre == null) nombre = "Sin nombre";
                            String estadoStr = (estado == null) ? "Programada" : estado;
                            // Usar ID para evitar duplicados
                            if (actividadesEncontradas.add(doc.getId())) {
                                resultado.actividadesBloqueantes.add(nombre + " (" + estadoStr + ")");
                                resultado.tieneBloqueantes = true;
                                android.util.Log.d("TiposActividad", "  ✅ Agregada como bloqueante: " + nombre);
                            }
                        } else {
                            android.util.Log.d("TiposActividad", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("tipoActividad", tipoNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("TiposActividad", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("TiposActividad", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    String nombre = doc.getString("nombre");
                                    android.util.Log.d("TiposActividad", "  📄 Actividad encontrada (tipoActividad): " + doc.getId() + " estado='" + estado + "' nombre=" + nombre);

                                    // Excluir explícitamente las completadas
                                    boolean esCompletada = estado != null &&
                                        (estado.equalsIgnoreCase("completada") ||
                                         estado.equalsIgnoreCase("finalizada") ||
                                         estado.equalsIgnoreCase("cancelada") ||
                                         estado.equalsIgnoreCase("completed") ||
                                         estado.equalsIgnoreCase("canceled"));

                                    if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        if (nombre == null) nombre = "Sin nombre";
                                        String estadoStr = (estado == null) ? "Programada" : estado;
                                        if (actividadesEncontradas.add(doc.getId())) {
                                            resultado.actividadesBloqueantes.add(nombre + " (" + estadoStr + ")");
                                            resultado.tieneBloqueantes = true;
                                            android.util.Log.d("TiposActividad", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    } else {
                                        android.util.Log.d("TiposActividad", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                                    }
                                }
                                // Verificar citas programadas
                                verificarCitasProgramadasDetallado(tipoNombre, resultado, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("TiposActividad", "Error verificando actividades con tipoActividad: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                if (resultado.tieneBloqueantes) {
                                    callback.onResult(true);
                                } else {
                                    // Si no había bloqueantes, continuar con citas
                                    verificarCitasProgramadasDetallado(tipoNombre, resultado, callback);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("TiposActividad", "Error verificando actividades con tipo: " + e.getMessage());
                    // Continuar buscando con el otro campo aunque falle
                    db.collection("activities")
                            .whereEqualTo("tipoActividad", tipoNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    android.util.Log.d("TiposActividad", "  📄 Actividad encontrada: " + doc.getId() + " estado=" + estado);
                                    if (estado != null && (estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        String nombre = doc.getString("nombre");
                                        if (nombre == null) nombre = "Sin nombre";
                                        // Usar ID para evitar duplicados
                                        if (actividadesEncontradas.add(doc.getId())) {
                                            resultado.actividadesBloqueantes.add(nombre + " (" + estado + ")");
                                            resultado.tieneBloqueantes = true;
                                            android.util.Log.d("TiposActividad", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    }
                                }
                                verificarCitasProgramadasDetallado(tipoNombre, resultado, callback);
                            })
                            .addOnFailureListener(e2 -> {
                                // Si ambas búsquedas de actividades fallan, continuar con citas
                                verificarCitasProgramadasDetallado(tipoNombre, resultado, callback);
                            });
                });
    }

    /**
     * 🆕 Verifica si hay citas programadas (no completadas/canceladas) usando este tipo de actividad
     */
    private void verificarCitasProgramadas(String tipoNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarCitasProgramadasDetallado(tipoNombre, resultado, callback);
    }

    private void verificarCitasProgramadasDetallado(String tipoNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("TiposActividad", "🔍 Verificando citas programadas para tipo: " + tipoNombre);

        db.collectionGroup("citas")
                .whereEqualTo("tipo", tipoNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("TiposActividad", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("TiposActividad", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    // Recopilar citas bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");

                        // Excluir explícitamente las completadas
                        boolean esCompletada = estado != null &&
                            (estado.equalsIgnoreCase("completada") ||
                             estado.equalsIgnoreCase("finalizada") ||
                             estado.equalsIgnoreCase("cancelada") ||
                             estado.equalsIgnoreCase("completed") ||
                             estado.equalsIgnoreCase("canceled"));

                        if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                            String titulo = doc.getString("titulo");
                            if (titulo == null) titulo = "Sin título";
                            String estadoStr = (estado == null) ? "Programada" : estado;

                            // Obtener fecha si existe
                            Object fechaObj = doc.get("fecha");
                            String fechaStr = "";
                            if (fechaObj instanceof com.google.firebase.Timestamp) {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MMM/yyyy", java.util.Locale.getDefault());
                                fechaStr = " - " + sdf.format(((com.google.firebase.Timestamp) fechaObj).toDate());
                            }

                            resultado.citasBloqueantes.add(titulo + " (" + estadoStr + fechaStr + ")");
                            resultado.tieneBloqueantes = true;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collectionGroup("citas")
                            .whereEqualTo("tipoActividad", tipoNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("TiposActividad", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("TiposActividad", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");

                                    // Excluir explícitamente las completadas
                                    boolean esCompletada = estado != null &&
                                        (estado.equalsIgnoreCase("completada") ||
                                         estado.equalsIgnoreCase("finalizada") ||
                                         estado.equalsIgnoreCase("cancelada") ||
                                         estado.equalsIgnoreCase("completed") ||
                                         estado.equalsIgnoreCase("canceled"));

                                    if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        String titulo = doc.getString("titulo");
                                        if (titulo == null) titulo = "Sin título";
                                        String estadoStr = (estado == null) ? "Programada" : estado;

                                        // Obtener fecha si existe
                                        Object fechaObj = doc.get("fecha");
                                        String fechaStr = "";
                                        if (fechaObj instanceof com.google.firebase.Timestamp) {
                                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MMM/yyyy", java.util.Locale.getDefault());
                                            fechaStr = " - " + sdf.format(((com.google.firebase.Timestamp) fechaObj).toDate());
                                        }

                                        resultado.citasBloqueantes.add(titulo + " (" + estadoStr + fechaStr + ")");
                                        resultado.tieneBloqueantes = true;
                                    }
                                }

                                android.util.Log.d("TiposActividad", resultado.tieneBloqueantes ? "❌ Encontradas actividades/citas bloqueantes" : "✅ No hay actividades ni citas activas");
                                callback.onResult(resultado.tieneBloqueantes);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("TiposActividad", "Error verificando citas con tipoActividad: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                callback.onResult(resultado.tieneBloqueantes);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("TiposActividad", "Error verificando citas con tipo: " + e.getMessage());
                    // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                    callback.onResult(resultado.tieneBloqueantes);
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

    /**
     * Clase para almacenar información de actividades/citas bloqueantes
     */
    private static class ResultadoValidacion {
        boolean tieneBloqueantes;
        List<String> actividadesBloqueantes;
        List<String> citasBloqueantes;

        ResultadoValidacion() {
            this.tieneBloqueantes = false;
            this.actividadesBloqueantes = new java.util.ArrayList<>();
            this.citasBloqueantes = new java.util.ArrayList<>();
        }

        String construirMensaje(String tipoMantenedor, String nombreMantenedor) {
            if (!tieneBloqueantes) return "";

            StringBuilder mensaje = new StringBuilder();
            mensaje.append("El ").append(tipoMantenedor).append(" \"").append(nombreMantenedor).append("\" está asociado a:\n\n");

            if (!actividadesBloqueantes.isEmpty()) {
                mensaje.append("📋 ACTIVIDADES:\n");
                for (String actividad : actividadesBloqueantes) {
                    mensaje.append("• ").append(actividad).append("\n");
                }
            }

            if (!citasBloqueantes.isEmpty()) {
                if (!actividadesBloqueantes.isEmpty()) mensaje.append("\n");
                mensaje.append("📅 CITAS:\n");
                for (String cita : citasBloqueantes) {
                    mensaje.append("• ").append(cita).append("\n");
                }
            }

            mensaje.append("\nCompleta o cancela estas actividades/citas primero.");
            return mensaje.toString();
        }
    }
}