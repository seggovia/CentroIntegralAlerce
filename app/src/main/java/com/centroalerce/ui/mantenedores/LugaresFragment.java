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
import com.centroalerce.gestion.utils.CustomToast;
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
    private RecyclerView rv;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fab;
    private android.view.View bottomBarSeleccion;
    private android.widget.TextView tvSeleccionados;
    private com.google.android.material.button.MaterialButton btnEliminarSeleccion;
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

        rv = view.findViewById(R.id.rvLista);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        fab = view.findViewById(R.id.fabAgregar);
        bottomBarSeleccion = view.findViewById(R.id.bottomBarSeleccion);
        tvSeleccionados = view.findViewById(R.id.tvSeleccionados);
        btnEliminarSeleccion = view.findViewById(R.id.btnEliminarSeleccion);

        adapter = new LugarAdapter(new LugarAdapter.Callbacks() {
            @Override public void onEditar(Lugar l) { abrirDialogo(l); }
            @Override public void onEliminar(Lugar l) { confirmarEliminar(l); }
            @Override public void onSelectionChanged(int selectedCount) { actualizarBarraSeleccion(selectedCount); }
        });
        rv.setAdapter(adapter);

        if (fab != null) fab.setOnClickListener(v -> abrirDialogo(null));

        if (btnEliminarSeleccion != null) {
            btnEliminarSeleccion.setOnClickListener(v -> eliminarSeleccionados());
        }

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
        List<Lugar> seleccionados = adapter.getSelectedItems();
        if (seleccionados == null || seleccionados.isEmpty()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar")
                .setMessage(seleccionados.size() == 1
                        ? "¿Eliminar el lugar seleccionado?"
                        : "¿Eliminar los " + seleccionados.size() + " lugares seleccionados?")
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Eliminar", (d, w) -> {
                    for (Lugar l : seleccionados) {
                        if (l != null) {
                            confirmarEliminar(l);
                        }
                    }
                    adapter.clearSelection();
                })
                .show();
    }

    private void abrirDialogo(@Nullable Lugar original) {
        LugarDialog dialog = new LugarDialog(original, lugar -> {
            if (lugar.getId() == null) {
                db.collection("lugares").add(lugar)
                        .addOnSuccessListener(documentReference ->
                            CustomToast.showSuccess(getContext(), "Lugar creado con éxito"))
                        .addOnFailureListener(e ->
                            CustomToast.showError(getContext(), "Error al crear: " + e.getMessage()));
            } else {
                db.collection("lugares").document(lugar.getId()).set(lugar)
                        .addOnSuccessListener(aVoid -> {
                            CustomToast.showSuccess(getContext(), "Lugar actualizado con éxito");
                            // 🆕 Actualizar el nombre en todas las actividades que usan este lugar
                            if (original != null && !lugar.getNombre().equals(original.getNombre())) {
                                android.util.Log.d("Lugares", "🔄 Nombre cambió de '" + original.getNombre() + "' a '" + lugar.getNombre() + "' - actualizando actividades...");
                                actualizarNombreEnActividades(original.getNombre(), lugar.getNombre());
                            }
                        })
                        .addOnFailureListener(e ->
                            CustomToast.showError(getContext(), "Error al actualizar: " + e.getMessage()));
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

        // Mostrar diálogo de carga mientras se verifican las actividades
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Verificando actividades asociadas...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Validar que no haya actividades activas usando este lugar
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
            progressDialog.dismiss();
            if (tieneActividades) {
                String mensaje = resultado.construirMensaje("lugar", item.getNombre());
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ No se puede eliminar")
                        .setMessage(mensaje)
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
                                CustomToast.showSuccess(getContext(), "Lugar eliminado con éxito");
                                // Actualizar actividades poniendo el campo en null
                                actualizarActividadesAlEliminar(item.getNombre());
                            })
                            .addOnFailureListener(e -> CustomToast.showError(getContext(), "Error al eliminar: " + e.getMessage())))
                    .show();
        });
    }

    private void toggleActivo(Lugar item) {
        if (item == null || item.getId() == null) return;

        boolean nuevo = !item.isActivo();

        // Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            ResultadoValidacion resultado = new ResultadoValidacion();
            verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
                if (tieneActividades) {
                    String mensaje = resultado.construirMensaje("lugar", item.getNombre());
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("❌ No se puede desactivar")
                            .setMessage(mensaje)
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection("lugares").document(item.getId())
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> CustomToast.showError(getContext(), "Error: " + e.getMessage()));
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection("lugares").document(item.getId())
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Error: " + e.getMessage()));
        }
    }

    /**
     * Verifica si hay actividades activas usando este lugar
     */
    private void verificarActividadesActivas(String lugarNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(lugarNombre, resultado, callback);
    }

    private void verificarActividadesActivasDetallado(String lugarNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Lugares", "🔍 Verificando actividades y citas para lugar: " + lugarNombre);

        // Set para evitar duplicados (usar ID del documento como clave)
        java.util.Set<String> actividadesEncontradas = new java.util.HashSet<>();

        // Buscar actividades con lugar
        db.collection("activities")
                .whereEqualTo("lugar", lugarNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Lugares", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    android.util.Log.d("Lugares", "  🔍 Primera búsqueda (campo 'lugar'): encontradas " + querySnapshot.size() + " actividades");
                    // Recopilar actividades bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        String nombre = doc.getString("nombre");
                        android.util.Log.d("Lugares", "  📄 Actividad: id=" + doc.getId() + ", nombre=" + nombre + ", estado='" + estado + "'");

                        // Bloquear SOLO si está programada o reagendada
                        // NO bloquear si está completada, cancelada o finalizada
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
                                android.util.Log.d("Lugares", "  ✅ Agregada como bloqueante: " + nombre);
                            }
                        } else {
                            android.util.Log.d("Lugares", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("lugarNombre", lugarNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Lugares", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Lugares", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                android.util.Log.d("Lugares", "  🔍 Segunda búsqueda (campo 'lugarNombre'): encontradas " + querySnapshot2.size() + " actividades");
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    String nombre = doc.getString("nombre");
                                    android.util.Log.d("Lugares", "  📄 Actividad: id=" + doc.getId() + ", nombre=" + nombre + ", estado='" + estado + "'");

                                    // Bloquear SOLO si está programada o reagendada
                                    // NO bloquear si está completada, cancelada o finalizada
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
                                            android.util.Log.d("Lugares", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    } else {
                                        android.util.Log.d("Lugares", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                                    }
                                }
                                // Verificar citas programadas
                                android.util.Log.d("Lugares", "  📊 RESUMEN ACTIVIDADES: Total bloqueantes encontradas: " + resultado.actividadesBloqueantes.size() + ", tieneBloqueantes=" + resultado.tieneBloqueantes);
                                verificarCitasProgramadasDetallado(lugarNombre, resultado, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Lugares", "Error verificando actividades con lugarNombre: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                if (resultado.tieneBloqueantes) {
                                    callback.onResult(true);
                                } else {
                                    // Si no había bloqueantes, continuar con citas
                                    verificarCitasProgramadasDetallado(lugarNombre, resultado, callback);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Lugares", "Error verificando actividades con lugar: " + e.getMessage());
                    // Continuar buscando con el otro campo aunque falle
                    db.collection("activities")
                            .whereEqualTo("lugarNombre", lugarNombre)
                            .get()
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    if (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada")) {
                                        String nombre = doc.getString("nombre");
                                        if (nombre == null) nombre = "Sin nombre";
                                        String estadoStr = (estado == null) ? "Nueva" : estado;
                                        resultado.actividadesBloqueantes.add(nombre + " (" + estadoStr + ")");
                                        resultado.tieneBloqueantes = true;
                                    }
                                }
                                verificarCitasProgramadasDetallado(lugarNombre, resultado, callback);
                            })
                            .addOnFailureListener(e2 -> {
                                // Si ambas búsquedas de actividades fallan, continuar con citas
                                verificarCitasProgramadasDetallado(lugarNombre, resultado, callback);
                            });
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este lugar
     */
    private void verificarCitasProgramadas(String lugarNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarCitasProgramadasDetallado(lugarNombre, resultado, callback);
    }

    private void verificarCitasProgramadasDetallado(String lugarNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Lugares", "🔍 Verificando citas programadas para lugar: " + lugarNombre);

        db.collectionGroup("citas")
                .whereEqualTo("lugar", lugarNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Lugares", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Lugares", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
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
                            .whereEqualTo("lugarNombre", lugarNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Lugares", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Lugares", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
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

                                android.util.Log.d("Lugares", resultado.tieneBloqueantes ? "❌ Encontradas actividades/citas bloqueantes" : "✅ No hay actividades ni citas activas");
                                callback.onResult(resultado.tieneBloqueantes);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Lugares", "Error verificando citas con lugarNombre: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                callback.onResult(resultado.tieneBloqueantes);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Lugares", "Error verificando citas con lugar: " + e.getMessage());
                    // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                    callback.onResult(resultado.tieneBloqueantes);
                });
    }

    /**
     * Actualiza todas las actividades completadas/canceladas que usan este lugar, poniendo el campo como "--"
     */
    private void actualizarActividadesAlEliminar(String nombre) {
        android.util.Log.d("Lugares", "🔄 Actualizando lugar a '--' en actividades completadas/canceladas");

        // Buscar actividades con ese lugar (campo "lugar")
        db.collection("activities")
                .whereEqualTo("lugar", nombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    final int[] count = {0};

                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        boolean esCompletada = estado != null &&
                            (estado.equalsIgnoreCase("completada") ||
                             estado.equalsIgnoreCase("finalizada") ||
                             estado.equalsIgnoreCase("cancelada") ||
                             estado.equalsIgnoreCase("completed") ||
                             estado.equalsIgnoreCase("canceled"));

                        if (esCompletada) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("lugar", "--");
                            updates.put("lugarNombre", "--");
                            batch.update(doc.getReference(), updates);
                            count[0]++;
                        }
                    }

                    if (count[0] > 0) {
                        batch.commit()
                                .addOnSuccessListener(unused -> android.util.Log.d("Lugares", "✅ Actualizadas " + count[0] + " actividades"))
                                .addOnFailureListener(e -> android.util.Log.e("Lugares", "❌ Error: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> android.util.Log.e("Lugares", "❌ Error obteniendo actividades: " + e.getMessage()));

        // Buscar por campo "lugarNombre"
        db.collection("activities")
                .whereEqualTo("lugarNombre", nombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    final int[] count = {0};

                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        boolean esCompletada = estado != null &&
                            (estado.equalsIgnoreCase("completada") ||
                             estado.equalsIgnoreCase("finalizada") ||
                             estado.equalsIgnoreCase("cancelada") ||
                             estado.equalsIgnoreCase("completed") ||
                             estado.equalsIgnoreCase("canceled"));

                        if (esCompletada) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("lugar", "--");
                            updates.put("lugarNombre", "--");
                            batch.update(doc.getReference(), updates);
                            count[0]++;
                        }
                    }

                    if (count[0] > 0) {
                        batch.commit()
                                .addOnSuccessListener(unused -> android.util.Log.d("Lugares", "✅ Actualizadas " + count[0] + " actividades por lugarNombre"))
                                .addOnFailureListener(e -> android.util.Log.e("Lugares", "❌ Error: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> android.util.Log.e("Lugares", "❌ Error: " + e.getMessage()));
    }

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

    /**
     * Interfaz para callbacks
     */
    private interface Callback<T> {
        void onResult(T result);
    }
}
