package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.view.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Oferente;
import com.centroalerce.gestion.utils.CustomToast;
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
                    db.collection(COL_OFERENTES).add(o)
                            .addOnSuccessListener(documentReference ->
                                CustomToast.showSuccess(getContext(), "Oferente creado con éxito"))
                            .addOnFailureListener(e ->
                                CustomToast.showError(getContext(), "Error al crear: " + e.getMessage()));
                } else {
                    // Guardar el oferente
                    db.collection(COL_OFERENTES).document(o.getId()).set(o)
                            .addOnSuccessListener(aVoid -> {
                                CustomToast.showSuccess(getContext(), "Oferente actualizado con éxito");
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

        // Mostrar diálogo de carga mientras se verifican las actividades
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Verificando actividades asociadas...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 🆕 Validar que no haya actividades activas usando este oferente
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
            progressDialog.dismiss();
            if (tieneActividades) {
                String mensaje = resultado.construirMensaje("oferente", item.getNombre());
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ No se puede eliminar")
                        .setMessage(mensaje)
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
                                CustomToast.showSuccess(getContext(), "Oferente eliminado con éxito");
                                // Actualizar actividades poniendo el campo en null
                                actualizarActividadesAlEliminar(item.getNombre());
                            })
                            .addOnFailureListener(e -> CustomToast.showError(getContext(), "Error al eliminar: " + e.getMessage())))
                    .show();
        });
    }

    private void toggleActivo(Oferente item) {
        if (item == null || item.getId() == null || item.getId().isEmpty()) return;

        boolean nuevo = !item.isActivo();

        // 🆕 Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            ResultadoValidacion resultado = new ResultadoValidacion();
            verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
                if (tieneActividades) {
                    String mensaje = resultado.construirMensaje("oferente", item.getNombre());
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("❌ No se puede desactivar")
                            .setMessage(mensaje)
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
     * 🆕 Verifica si hay actividades o citas programadas usando este oferente
     * IMPORTANTE: Busca actividades sin campo estado (activas por defecto) Y con estado activo
     */
    private void verificarActividadesActivas(String oferenteNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(oferenteNombre, resultado, callback);
    }

    private void verificarActividadesActivasDetallado(String oferenteNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Oferentes", "🔍 Verificando actividades y citas para oferente: " + oferenteNombre);

        // Set para evitar duplicados (usar ID del documento como clave)
        java.util.Set<String> actividadesEncontradas = new java.util.HashSet<>();

        // Buscar actividades con campo 'oferente'
        db.collection("activities")
                .whereEqualTo("oferente", oferenteNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Oferentes", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    // Recopilar actividades bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        String nombre = doc.getString("nombre");
                        android.util.Log.d("Oferentes", "  📄 Actividad encontrada (oferente): " + doc.getId() + " estado='" + estado + "' nombre=" + nombre);

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
                                android.util.Log.d("Oferentes", "  ✅ Agregada como bloqueante: " + nombre);
                            }
                        } else {
                            android.util.Log.d("Oferentes", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("oferenteNombre", oferenteNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Oferentes", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Oferentes", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    String nombre = doc.getString("nombre");
                                    android.util.Log.d("Oferentes", "  📄 Actividad encontrada (oferenteNombre): " + doc.getId() + " estado='" + estado + "' nombre=" + nombre);

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
                                            android.util.Log.d("Oferentes", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    } else {
                                        android.util.Log.d("Oferentes", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                                    }
                                }
                                // Verificar citas programadas
                                verificarCitasProgramadasDetallado(oferenteNombre, resultado, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Oferentes", "Error verificando actividades con oferenteNombre: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                if (resultado.tieneBloqueantes) {
                                    callback.onResult(true);
                                } else {
                                    // Si no había bloqueantes, continuar con citas
                                    verificarCitasProgramadasDetallado(oferenteNombre, resultado, callback);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Oferentes", "Error verificando actividades con oferente: " + e.getMessage());
                    // Continuar buscando con el otro campo aunque falle
                    db.collection("activities")
                            .whereEqualTo("oferenteNombre", oferenteNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    android.util.Log.d("Oferentes", "  📄 Actividad encontrada: " + doc.getId() + " estado=" + estado);
                                    if (estado != null && (estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        String nombre = doc.getString("nombre");
                                        if (nombre == null) nombre = "Sin nombre";
                                        // Usar ID para evitar duplicados
                                        if (actividadesEncontradas.add(doc.getId())) {
                                            resultado.actividadesBloqueantes.add(nombre + " (" + estado + ")");
                                            resultado.tieneBloqueantes = true;
                                            android.util.Log.d("Oferentes", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    }
                                }
                                verificarCitasProgramadasDetallado(oferenteNombre, resultado, callback);
                            })
                            .addOnFailureListener(e2 -> {
                                // Si ambas búsquedas de actividades fallan, continuar con citas
                                verificarCitasProgramadasDetallado(oferenteNombre, resultado, callback);
                            });
                });
    }

    /**
     * 🆕 Verifica si hay citas programadas (no completadas/canceladas) usando este oferente
     */
    private void verificarCitasProgramadas(String oferenteNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarCitasProgramadasDetallado(oferenteNombre, resultado, callback);
    }

    private void verificarCitasProgramadasDetallado(String oferenteNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Oferentes", "🔍 Verificando citas programadas para oferente: " + oferenteNombre);

        db.collectionGroup("citas")
                .whereEqualTo("oferente", oferenteNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Oferentes", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Oferentes", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
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
                            .whereEqualTo("oferenteNombre", oferenteNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Oferentes", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Oferentes", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
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

                                android.util.Log.d("Oferentes", resultado.tieneBloqueantes ? "❌ Encontradas actividades/citas bloqueantes" : "✅ No hay actividades ni citas activas");
                                callback.onResult(resultado.tieneBloqueantes);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Oferentes", "Error verificando citas con oferenteNombre: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                callback.onResult(resultado.tieneBloqueantes);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Oferentes", "Error verificando citas con oferente: " + e.getMessage());
                    // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                    callback.onResult(resultado.tieneBloqueantes);
                });
    }

    /**
     * Actualiza todas las actividades que usan este oferente, poniendo el campo en null
     */
    private void actualizarActividadesAlEliminar(String nombre) {
        android.util.Log.d("Oferentes", "🔄 Actualizando oferente a '--' en actividades completadas/canceladas");

        // Buscar actividades con ese oferente (campo "oferente")
        db.collection("activities")
                .whereEqualTo("oferente", nombre)
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
                            updates.put("oferente", "--");
                            updates.put("oferenteNombre", "--");
                            updates.put("oferentes", java.util.Collections.singletonList("--"));
                            updates.put("oferentesNombres", java.util.Collections.singletonList("--"));
                            batch.update(doc.getReference(), updates);
                            count[0]++;
                        }
                    }

                    if (count[0] > 0) {
                        batch.commit()
                                .addOnSuccessListener(unused -> android.util.Log.d("Oferentes", "✅ Actualizadas " + count[0] + " actividades"))
                                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error obteniendo actividades: " + e.getMessage()));

        // Buscar por campo "oferenteNombre"
        db.collection("activities")
                .whereEqualTo("oferenteNombre", nombre)
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
                            updates.put("oferente", "--");
                            updates.put("oferenteNombre", "--");
                            updates.put("oferentes", java.util.Collections.singletonList("--"));
                            updates.put("oferentesNombres", java.util.Collections.singletonList("--"));
                            batch.update(doc.getReference(), updates);
                            count[0]++;
                        }
                    }

                    if (count[0] > 0) {
                        batch.commit()
                                .addOnSuccessListener(unused -> android.util.Log.d("Oferentes", "✅ Actualizadas " + count[0] + " actividades por oferenteNombre"))
                                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> android.util.Log.e("Oferentes", "❌ Error: " + e.getMessage()));
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