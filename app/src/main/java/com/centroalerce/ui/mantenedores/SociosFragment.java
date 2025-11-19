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
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
            if (tieneActividades) {
                String mensaje = resultado.construirMensaje("socio comunitario", item.getNombre());
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ No se puede eliminar")
                        .setMessage(mensaje)
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
            ResultadoValidacion resultado = new ResultadoValidacion();
            verificarActividadesActivasDetallado(item.getNombre(), resultado, tieneActividades -> {
                if (tieneActividades) {
                    String mensaje = resultado.construirMensaje("socio comunitario", item.getNombre());
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("❌ No se puede desactivar")
                            .setMessage(mensaje)
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
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(socioNombre, resultado, callback);
    }

    private void verificarActividadesActivasDetallado(String socioNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Socios", "🔍 Verificando actividades y citas para socio: " + socioNombre);

        // Set para evitar duplicados (usar ID del documento como clave)
        java.util.Set<String> actividadesEncontradas = new java.util.HashSet<>();

        // Buscar actividades con campo 'socioComunitario' (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereEqualTo("socioComunitario", socioNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Socios", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    // Recopilar actividades bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        String nombre = doc.getString("nombre");
                        android.util.Log.d("Socios", "  📄 Actividad encontrada (socioComunitario): " + doc.getId() + " estado='" + estado + "' nombre=" + nombre);

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
                                android.util.Log.d("Socios", "  ✅ Agregada como bloqueante: " + nombre);
                            }
                        } else {
                            android.util.Log.d("Socios", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                        }
                    }

                    // Buscar con el campo "socio"
                    db.collection("activities")
                            .whereEqualTo("socio", socioNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Socios", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Socios", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    String nombre = doc.getString("nombre");
                                    android.util.Log.d("Socios", "  📄 Actividad encontrada (socio): " + doc.getId() + " estado='" + estado + "' nombre=" + nombre);

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
                                            android.util.Log.d("Socios", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    } else {
                                        android.util.Log.d("Socios", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                                    }
                                }
                                // Verificar citas programadas
                                verificarCitasProgramadasDetallado(socioNombre, resultado, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Socios", "Error verificando actividades con socio: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                if (resultado.tieneBloqueantes) {
                                    callback.onResult(true);
                                } else {
                                    // Si no había bloqueantes, continuar con citas
                                    verificarCitasProgramadasDetallado(socioNombre, resultado, callback);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Socios", "Error verificando actividades con socioComunitario: " + e.getMessage());
                    // Continuar buscando con el otro campo aunque falle
                    db.collection("activities")
                            .whereEqualTo("socio", socioNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    android.util.Log.d("Socios", "  📄 Actividad encontrada: " + doc.getId() + " estado=" + estado);
                                    if (estado != null && (estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        String nombre = doc.getString("nombre");
                                        if (nombre == null) nombre = "Sin nombre";
                                        // Usar ID para evitar duplicados
                                        if (actividadesEncontradas.add(doc.getId())) {
                                            resultado.actividadesBloqueantes.add(nombre + " (" + estado + ")");
                                            resultado.tieneBloqueantes = true;
                                            android.util.Log.d("Socios", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    }
                                }
                                verificarCitasProgramadasDetallado(socioNombre, resultado, callback);
                            })
                            .addOnFailureListener(e2 -> {
                                // Si ambas búsquedas de actividades fallan, continuar con citas
                                verificarCitasProgramadasDetallado(socioNombre, resultado, callback);
                            });
                });
    }

    /**
     * Verifica si hay citas programadas (no completadas/canceladas) usando este socio comunitario
     */
    private void verificarCitasProgramadas(String socioNombre, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarCitasProgramadasDetallado(socioNombre, resultado, callback);
    }

    private void verificarCitasProgramadasDetallado(String socioNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Socios", "🔍 Verificando citas programadas para socio: " + socioNombre);

        // Usar un Set para evitar duplicados (si una cita tiene ambos campos)
        Set<String> citasEncontradas = new HashSet<>();

        // Primera búsqueda: campo 'socioComunitario'
        db.collectionGroup("citas")
                .whereEqualTo("socioComunitario", socioNombre)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Socios", "  ✅ Query completado. Total encontradas: " + querySnapshot.size());
                    android.util.Log.d("Socios", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                    android.util.Log.d("Socios", "   📄 Encontradas " + querySnapshot.size() + " citas con socioComunitario='" + socioNombre + "'");

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

                            String citaInfo = titulo + " (" + estadoStr + fechaStr + ")";
                            // Usar el ID del documento como clave única para evitar duplicados
                            if (citasEncontradas.add(doc.getId())) {
                                resultado.citasBloqueantes.add(citaInfo);
                                resultado.tieneBloqueantes = true;
                            }
                        }
                    }

                    // Segunda búsqueda anidada: campo 'socio'
                    db.collectionGroup("citas")
                            .whereEqualTo("socio", socioNombre)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Socios", "  ✅ Query completado. Total encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Socios", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));
                                android.util.Log.d("Socios", "   📄 Encontradas " + querySnapshot2.size() + " citas con socio='" + socioNombre + "'");

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

                                        String citaInfo = titulo + " (" + estadoStr + fechaStr + ")";
                                        // Usar el ID del documento como clave única para evitar duplicados
                                        if (citasEncontradas.add(doc.getId())) {
                                            resultado.citasBloqueantes.add(citaInfo);
                                            resultado.tieneBloqueantes = true;
                                        }
                                    }
                                }

                                // Llamar al callback solo después de procesar AMBAS búsquedas
                                android.util.Log.d("Socios", resultado.tieneBloqueantes ? "❌ Encontradas actividades/citas bloqueantes" : "✅ No hay actividades ni citas activas");
                                callback.onResult(resultado.tieneBloqueantes);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Socios", "Error verificando citas con campo 'socio': " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                                callback.onResult(resultado.tieneBloqueantes);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Socios", "Error verificando citas con campo 'socioComunitario': " + e.getMessage());
                    // Si ya encontramos bloqueantes, retornar true aunque falle la búsqueda
                    callback.onResult(resultado.tieneBloqueantes);
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