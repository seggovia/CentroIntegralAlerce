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

        // 🆕 Validar que no haya actividades activas usando este proyecto
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
            if (tieneActividades) {
                String mensaje = resultado.construirMensaje("proyecto", item.getNombre());
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ No se puede eliminar")
                        .setMessage(mensaje)
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

        // 🆕 Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            ResultadoValidacion resultado = new ResultadoValidacion();
            verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
                if (tieneActividades) {
                    String mensaje = resultado.construirMensaje("proyecto", item.getNombre());
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("❌ No se puede desactivar")
                            .setMessage(mensaje)
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
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(proyectoNombre, resultado, callback);
    }

    private void verificarActividadesActivasDetallado(String proyectoNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Proyectos", "🔍 Verificando actividades y citas para proyecto: " + proyectoNombre);

        // Set para evitar duplicados (usar ID del documento como clave)
        java.util.Set<String> actividadesEncontradas = new java.util.HashSet<>();

        // Buscar actividades con campo 'proyecto' (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereEqualTo("proyecto", proyectoNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Proyectos", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    // Recopilar actividades bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        String nombre = doc.getString("nombre");
                        android.util.Log.d("Proyectos", "  📄 Actividad encontrada (proyecto): " + doc.getId() + " estado='" + estado + "' nombre=" + nombre);

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
                                android.util.Log.d("Proyectos", "  ✅ Agregada como bloqueante: " + nombre);
                            }
                        } else {
                            android.util.Log.d("Proyectos", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereEqualTo("proyectoNombre", proyectoNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Proyectos", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Proyectos", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    String nombre = doc.getString("nombre");
                                    android.util.Log.d("Proyectos", "  📄 Actividad encontrada (proyectoNombre): " + doc.getId() + " estado='" + estado + "' nombre=" + nombre);

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
                                            android.util.Log.d("Proyectos", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    } else {
                                        android.util.Log.d("Proyectos", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                                    }
                                }
                                // Verificar citas programadas
                                verificarCitasProgramadasDetallado(proyectoNombre, resultado, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Proyectos", "Error verificando actividades con proyectoNombre: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                if (resultado.tieneBloqueantes) {
                                    callback.onResult(true);
                                } else {
                                    // Si no había bloqueantes, continuar con citas
                                    verificarCitasProgramadasDetallado(proyectoNombre, resultado, callback);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Proyectos", "Error verificando actividades con proyecto: " + e.getMessage());
                    // Continuar buscando con el otro campo aunque falle
                    db.collection("activities")
                            .whereEqualTo("proyectoNombre", proyectoNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    android.util.Log.d("Proyectos", "  📄 Actividad encontrada: " + doc.getId() + " estado=" + estado);
                                    if (estado != null && (estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        String nombre = doc.getString("nombre");
                                        if (nombre == null) nombre = "Sin nombre";
                                        // Usar ID para evitar duplicados
                                        if (actividadesEncontradas.add(doc.getId())) {
                                            resultado.actividadesBloqueantes.add(nombre + " (" + estado + ")");
                                            resultado.tieneBloqueantes = true;
                                            android.util.Log.d("Proyectos", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    }
                                }
                                verificarCitasProgramadasDetallado(proyectoNombre, resultado, callback);
                            })
                            .addOnFailureListener(e2 -> {
                                // Si ambas búsquedas de actividades fallan, continuar con citas
                                verificarCitasProgramadasDetallado(proyectoNombre, resultado, callback);
                            });
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este proyecto
     */
    private void verificarCitasProgramadas(String proyectoNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarCitasProgramadasDetallado(proyectoNombre, resultado, callback);
    }

    private void verificarCitasProgramadasDetallado(String proyectoNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Proyectos", "🔍 Verificando citas programadas para proyecto: " + proyectoNombre);

        // Primera búsqueda: whereEqualTo("proyecto", ...)
        db.collectionGroup("citas")
                .whereEqualTo("proyecto", proyectoNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Proyectos", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Proyectos", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    android.util.Log.d("Proyectos", "   📍 Encontradas " + querySnapshot.size() + " citas con proyecto='" + proyectoNombre + "'");

                    // Recopilar citas bloqueantes con campo "proyecto"
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

                    // Segunda búsqueda (anidada): whereEqualTo("proyectoNombre", ...)
                    db.collectionGroup("citas")
                            .whereEqualTo("proyectoNombre", proyectoNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Proyectos", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Proyectos", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                android.util.Log.d("Proyectos", "   📍 Encontradas " + querySnapshot2.size() + " citas con proyectoNombre='" + proyectoNombre + "'");

                                // Recopilar citas bloqueantes con campo "proyectoNombre"
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

                                // Solo llamar al callback después de procesar AMBOS resultados
                                android.util.Log.d("Proyectos", resultado.tieneBloqueantes ? "❌ Encontradas actividades/citas bloqueantes" : "✅ No hay actividades ni citas activas");
                                callback.onResult(resultado.tieneBloqueantes);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Proyectos", "Error verificando citas (proyectoNombre): " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                callback.onResult(resultado.tieneBloqueantes);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Proyectos", "Error verificando citas (proyecto): " + e.getMessage());
                    // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                    callback.onResult(resultado.tieneBloqueantes);
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