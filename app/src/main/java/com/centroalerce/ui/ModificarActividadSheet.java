package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Beneficiario;
import com.centroalerce.ui.mantenedores.dialog.BeneficiariosPickerSheet;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FieldValue;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModificarActividadSheet extends BottomSheetDialogFragment {
    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    public static ModificarActividadSheet newInstance(@NonNull String actividadId){
        Bundle b = new Bundle(); b.putString(ARG_ACTIVIDAD_ID, actividadId);
        ModificarActividadSheet s = new ModificarActividadSheet(); s.setArguments(b); return s;
    }

    // ===== Utils Firestore multi-colección (ES/EN) =====
    private static final String COL_EN = "activities";
    private LinearLayout llAdjuntos;
    private final List<Map<String, Object>> adjuntosCargados = new ArrayList<>();
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance().collection(preferEN ? COL_EN : COL_ES).document(actividadId);
    }

    // ===== Modelo para dropdowns con id y nombre =====
    public static class OptionItem {
        public final String id;
        public final String nombre;
        public OptionItem(String id, String nombre) {
            this.id = id;
            this.nombre = nombre == null ? "" : nombre;
        }
        @Override public String toString() { return nombre; } // lo que ve el usuario
    }

    private FirebaseFirestore db;
    private String actividadId;

    private TextInputEditText etNombre, etCupo, etBeneficiarios, etDiasAviso;
    private AutoCompleteTextView actTipo, actPeriodicidad, actLugar, actOferente, actSocio, actProyecto;
    
    // UI para beneficiarios (como en ActivityFormFragment)
    private MaterialCardView btnBeneficiarios;
    private TextView tvBeneficiariosHint;
    private ChipGroup chipsBeneficiarios;
    
    // Datos de beneficiarios seleccionados
    private final List<Beneficiario> beneficiariosSeleccionados = new ArrayList<>();
    private final List<String> beneficiariosSeleccionadosIds = new ArrayList<>();

    // Fallbacks
    private final String[] tiposFijos = new String[]{
            "Capacitación","Taller","Charlas","Atenciones","Operativo en oficina","Operativo rural","Operativo","Práctica profesional","Diagnostico"
    };
    private final String[] periodicidades = new String[]{"Puntual","Periódica"};

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.sheet_modificar_actividad, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        db = FirebaseFirestore.getInstance();
        actividadId = getArguments() != null ? getArguments().getString(ARG_ACTIVIDAD_ID) : null;

        etNombre = v.findViewById(R.id.etNombre);
        etCupo = v.findViewById(R.id.etCupo);
        etBeneficiarios = v.findViewById(R.id.etBeneficiarios);
        etDiasAviso = v.findViewById(R.id.etDiasAviso);

        actTipo = v.findViewById(R.id.actTipo);
        actPeriodicidad = v.findViewById(R.id.actPeriodicidad);
        actLugar = v.findViewById(R.id.actLugar);
        actOferente = v.findViewById(R.id.actOferente);
        actSocio = v.findViewById(R.id.actSocio);
        actProyecto = v.findViewById(R.id.actProyecto);

        llAdjuntos = v.findViewById(R.id.llAdjuntos);
        
        // Referencias de beneficiarios
        btnBeneficiarios = v.findViewById(R.id.btnBeneficiarios);
        tvBeneficiariosHint = v.findViewById(R.id.tvBeneficiariosHint);
        chipsBeneficiarios = v.findViewById(R.id.chipsBeneficiarios);
        
        // Listener para abrir selector de beneficiarios
        if (btnBeneficiarios != null) {
            btnBeneficiarios.setOnClickListener(view -> abrirSelectorBeneficiarios());
        }

        // Estáticos
        actPeriodicidad.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, periodicidades));

        // --- NUEVO: Listener para detectar cambio de periodicidad ---
        final String[] periodicidadOriginal = {null};

        actPeriodicidad.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && periodicidadOriginal[0] == null) {
                periodicidadOriginal[0] = actPeriodicidad.getText().toString();
            }
        });

        actPeriodicidad.setOnItemClickListener((parent, view, position, id) -> {
            String seleccionNueva = parent.getItemAtPosition(position).toString();

            if (periodicidadOriginal[0] != null && !periodicidadOriginal[0].equals(seleccionNueva)) {
                mostrarDialogoCambiarPeriodicidad();
                actPeriodicidad.post(() -> actPeriodicidad.setText(periodicidadOriginal[0], false));
            }
        });

        // Dinámicos con {id, nombre}
        cargarTiposActividad();
        cargarColeccionAOptions("lugares", actLugar);
        cargarColeccionAOptions("oferentes", actOferente);
        cargarColeccionAOptionsMulti(Arrays.asList("socios_comunitarios", "socios", "sociosComunitarios"), actSocio);
        cargarColeccionAOptions("proyectos", actProyecto);

        precargar();
        // ✅ ESCUCHAR CAMBIOS DE ADJUNTOS
        getParentFragmentManager().setFragmentResultListener(
                "adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("MODIFICAR", "🔄 Recibido evento adjuntos_change");
                    cargarAdjuntos();
                }
        );

        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("MODIFICAR", "🔄 Recibido evento adjuntos_change (Activity)");
                    cargarAdjuntos();
                }
        );

        getParentFragmentManager().setFragmentResultListener(
                "adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("MODIFICAR", "🔄 Recibido evento adjuntos_change");
                    cargarAdjuntos();
                }
        );

        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("MODIFICAR", "🔄 Recibido evento adjuntos_change (Activity)");
                    cargarAdjuntos();
                }
        );
        
        // Render inicial de beneficiarios
        if (chipsBeneficiarios != null) {
            renderChipsBeneficiarios();
        }

        ((MaterialButton) v.findViewById(R.id.btnGuardarCambios)).setOnClickListener(x -> guardar());
    }

    // ================== Precargar & bind ==================
    private void precargar(){
        if (TextUtils.isEmpty(actividadId)) { toast("Falta actividadId"); return; }
        act(actividadId,true).get().addOnSuccessListener(doc -> {
            if (doc!=null && doc.exists()) {
                bind(doc);
                cargarAdjuntos(); // NUEVO
            } else {
                act(actividadId,false).get().addOnSuccessListener(d -> {
                    bind(d);
                    cargarAdjuntos(); // NUEVO
                }).addOnFailureListener(e -> toast("No se pudo cargar"));
            }
        }).addOnFailureListener(e -> toast("No se pudo cargar"));
    }
    private void cargarAdjuntos() {
        if (TextUtils.isEmpty(actividadId) || llAdjuntos == null) return;

        android.util.Log.d("MODIFICAR", "🔍 Cargando adjuntos para: " + actividadId);

        llAdjuntos.removeAllViews();

        // Intentar EN primero
        act(actividadId, true).get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                android.util.Log.d("MODIFICAR", "📄 Documento EN encontrado");
                mostrarAdjuntosDelDoc(doc);
            } else {
                // Intentar ES
                android.util.Log.d("MODIFICAR", "⚠️ Documento EN no existe, probando ES...");
                act(actividadId, false).get()
                        .addOnSuccessListener(this::mostrarAdjuntosDelDoc)
                        .addOnFailureListener(e -> {
                            android.util.Log.e("MODIFICAR", "❌ Error: " + e.getMessage(), e);
                            mostrarMensajeSinAdjuntos();
                        });
            }
        }).addOnFailureListener(e -> {
            android.util.Log.e("MODIFICAR", "❌ Error cargando: " + e.getMessage(), e);
            mostrarMensajeSinAdjuntos();
        });
    }
    private void mostrarAdjuntosDelDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            mostrarMensajeSinAdjuntos();
            return;
        }

        llAdjuntos.removeAllViews();
        adjuntosCargados.clear();

        Object rawAdj = doc.get("adjuntos");

        android.util.Log.d("MODIFICAR", "📎 Campo adjuntos: " +
                (rawAdj != null ? rawAdj.getClass().getSimpleName() : "null"));

        if (rawAdj instanceof List) {
            List<?> lista = (List<?>) rawAdj;
            android.util.Log.d("MODIFICAR", "📎 Lista con " + lista.size() + " elementos");

            for (Object item : lista) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> adj = (Map<String, Object>) item;
                    adjuntosCargados.add(adj);
                }
            }
        }

        if (adjuntosCargados.isEmpty()) {
            android.util.Log.d("MODIFICAR", "📂 Array vacío, intentando subcolección...");
            cargarDesdeSubcoleccion(doc.getReference());
        } else {
            android.util.Log.d("MODIFICAR", "✅ Cargados " + adjuntosCargados.size() + " adjuntos");

            // ✅ SIEMPRE mostrar botón compacto (sin importar la cantidad)
            mostrarBotonVerArchivosConEliminar(adjuntosCargados.size());
        }
    }




    private void mostrarBotonVerArchivos(int cantidad) {
        llAdjuntos.removeAllViews();

        // Card clickeable
        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(requireContext());

        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        card.setCardElevation(dp(2));
        card.setRadius(dp(12));
        card.setClickable(true);
        card.setFocusable(true);
        card.setForeground(requireContext().getDrawable(
                android.R.drawable.list_selector_background));

        android.widget.LinearLayout container =
                new android.widget.LinearLayout(requireContext());
        container.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Icono
        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        icon.setImageResource(android.R.drawable.ic_menu_gallery);
        icon.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(40), dp(40)));
        icon.setColorFilter(0xFF2D5F4F); // primary color
        container.addView(icon);

        // Textos
        android.widget.LinearLayout textContainer =
                new android.widget.LinearLayout(requireContext());
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams textParams =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMargins(dp(12), 0, dp(12), 0);
        textContainer.setLayoutParams(textParams);

        android.widget.TextView tvTitulo = new android.widget.TextView(requireContext());
        tvTitulo.setText("Ver archivos adjuntos");
        tvTitulo.setTextSize(16);
        tvTitulo.setTextColor(0xFF1F2937); // textPrimary
        tvTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        textContainer.addView(tvTitulo);

        android.widget.TextView tvCantidad = new android.widget.TextView(requireContext());
        tvCantidad.setText(cantidad + " archivo(s) disponible(s)");
        tvCantidad.setTextSize(14);
        tvCantidad.setTextColor(0xFF6B7280); // textSecondary
        textContainer.addView(tvCantidad);

        container.addView(textContainer);

        // Icono flecha
        android.widget.ImageView iconArrow = new android.widget.ImageView(requireContext());
        iconArrow.setImageResource(android.R.drawable.ic_menu_view);
        iconArrow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(24), dp(24)));
        iconArrow.setColorFilter(0xFF2D5F4F);
        container.addView(iconArrow);

        card.addView(container);

        // Click para abrir modal
        card.setOnClickListener(v -> {
            ArchivosListSheet sheet = ArchivosListSheet.newInstance(
                    adjuntosCargados,
                    "Archivos adjuntos"
            );
            sheet.show(getParentFragmentManager(), "archivos_list");
        });

        llAdjuntos.addView(card);
    }



    private void cargarDesdeSubcoleccion(com.google.firebase.firestore.DocumentReference actRef) {
        actRef.collection("adjuntos")
                .orderBy("creadoEn", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    android.util.Log.d("MODIFICAR", "📂 Subcolección: " +
                            (qs != null ? qs.size() : 0) + " documentos");

                    if (qs == null || qs.isEmpty()) {
                        mostrarMensajeSinAdjuntos();
                        return;
                    }

                    llAdjuntos.removeAllViews();
                    adjuntosCargados.clear();

                    for (com.google.firebase.firestore.DocumentSnapshot d : qs.getDocuments()) {
                        Map<String, Object> adj = new HashMap<>();
                        adj.put("nombre", d.getString("nombre"));
                        adj.put("name", d.getString("name"));
                        adj.put("url", d.getString("url"));
                        adj.put("id", d.getId());

                        adjuntosCargados.add(adj);
                    }

                    android.util.Log.d("MODIFICAR", "✅ Cargados " + adjuntosCargados.size() +
                            " desde subcolección");

                    // ✅ Mostrar botón con eliminar
                    mostrarBotonVerArchivosConEliminar(adjuntosCargados.size());
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MODIFICAR", "❌ Error subcolección: " + e.getMessage(), e);
                    mostrarMensajeSinAdjuntos();
                });
    }
    private void mostrarBotonVerArchivosConEliminar(int cantidad) {
        llAdjuntos.removeAllViews();

        // Card clickeable
        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(requireContext());

        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        card.setCardElevation(dp(2));
        card.setRadius(dp(12));
        card.setClickable(true);
        card.setFocusable(true);
        card.setForeground(requireContext().getDrawable(
                android.R.drawable.list_selector_background));

        android.widget.LinearLayout container =
                new android.widget.LinearLayout(requireContext());
        container.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Icono
        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        icon.setImageResource(android.R.drawable.ic_menu_gallery);
        icon.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(40), dp(40)));
        icon.setColorFilter(0xFF2D5F4F); // primary color
        container.addView(icon);

        // Textos
        android.widget.LinearLayout textContainer =
                new android.widget.LinearLayout(requireContext());
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams textParams =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMargins(dp(12), 0, dp(12), 0);
        textContainer.setLayoutParams(textParams);

        android.widget.TextView tvTitulo = new android.widget.TextView(requireContext());
        tvTitulo.setText("Gestionar archivos adjuntos");
        tvTitulo.setTextSize(16);
        tvTitulo.setTextColor(0xFF1F2937); // textPrimary
        tvTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        textContainer.addView(tvTitulo);

        android.widget.TextView tvCantidad = new android.widget.TextView(requireContext());
        tvCantidad.setText(cantidad + " archivo(s) • Toca para ver y eliminar");
        tvCantidad.setTextSize(14);
        tvCantidad.setTextColor(0xFF6B7280); // textSecondary
        textContainer.addView(tvCantidad);

        container.addView(textContainer);

        // Icono flecha
        android.widget.ImageView iconArrow = new android.widget.ImageView(requireContext());
        iconArrow.setImageResource(android.R.drawable.ic_menu_view);
        iconArrow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(24), dp(24)));
        iconArrow.setColorFilter(0xFF2D5F4F);
        container.addView(iconArrow);

        card.addView(container);

        // Click para abrir modal CON opción de eliminar
        card.setOnClickListener(v -> {
            ArchivosListSheetConEliminar sheet = ArchivosListSheetConEliminar.newInstance(
                    adjuntosCargados,
                    "Archivos adjuntos",
                    actividadId
            );
            sheet.show(getParentFragmentManager(), "archivos_list_eliminar");

            // ✅ Recargar cuando se cierre el modal
            sheet.setOnDismissListener(() -> {
                android.util.Log.d("MODIFICAR", "🔄 Modal cerrado, recargando adjuntos...");
                cargarAdjuntos();
            });
        });

        llAdjuntos.addView(card);
    }
    private void limpiarTodosLosAdjuntos() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Eliminar todos los archivos")
                .setMessage("Esto eliminará TODOS los archivos adjuntos de esta actividad. ¿Estás seguro?")
                .setPositiveButton("Eliminar todo", (d, w) -> {
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    db.runTransaction(trx -> {
                        DocumentReference refEN = db.collection("activities").document(actividadId);
                        DocumentReference refES = db.collection("actividades").document(actividadId);

                        try {
                            if (trx.get(refEN).exists()) {
                                trx.update(refEN, "adjuntos", new ArrayList<>());
                            }
                        } catch (Exception ignored) {}

                        try {
                            if (trx.get(refES).exists()) {
                                trx.update(refES, "adjuntos", new ArrayList<>());
                            }
                        } catch (Exception ignored) {}

                        return null;
                    }).addOnSuccessListener(u -> {
                        adjuntosCargados.clear();
                        llAdjuntos.removeAllViews();
                        mostrarMensajeSinAdjuntos();
                        toast("Todos los archivos eliminados");
                    }).addOnFailureListener(e -> {
                        toast("Error: " + e.getMessage());
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarMensajeSinAdjuntos() {
        if (llAdjuntos == null) return;

        llAdjuntos.removeAllViews();

        TextView tvVacio = new TextView(requireContext());
        tvVacio.setText("No hay archivos adjuntos");
        tvVacio.setTextColor(0xFF6B7280); // textSecondary
        tvVacio.setPadding(dp(16), dp(16), dp(16), dp(16));
        tvVacio.setGravity(android.view.Gravity.CENTER);

        llAdjuntos.addView(tvVacio);
    }




    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void bind(DocumentSnapshot doc){
        if (doc==null || !doc.exists()) return;

        // Texto
        set(etNombre, doc.getString("nombre"));
        Long cupo = doc.getLong("cupo"); if (cupo!=null) etCupo.setText(String.valueOf(cupo));

        // Tipo / Periodicidad (pueden venir en distintas claves)
        setDropText(actTipo, firstNonEmpty(doc.getString("tipoActividad"), doc.getString("tipo")));
        setDropText(actPeriodicidad, firstNonEmpty(doc.getString("periodicidad"), doc.getString("frecuencia")));

        // Beneficiarios - Cargar desde beneficiariosIds (array de IDs)
        beneficiariosSeleccionados.clear();
        beneficiariosSeleccionadosIds.clear();
        
        try {
            @SuppressWarnings("unchecked")
            List<String> beneficiariosIds = (List<String>) doc.get("beneficiariosIds");
            if (beneficiariosIds != null && !beneficiariosIds.isEmpty()) {
                beneficiariosSeleccionadosIds.addAll(beneficiariosIds);
                cargarBeneficiariosDesdeIds(beneficiariosIds);
            } else {
                // Fallback: cargar desde beneficiariosTexto o beneficiarios (array de nombres)
                String beneficiariosTxt = firstNonEmpty(doc.getString("beneficiariosTexto"));
                if (TextUtils.isEmpty(beneficiariosTxt)) {
                    @SuppressWarnings("unchecked")
                    List<String> lista = (List<String>) doc.get("beneficiarios");
                    if (lista != null && !lista.isEmpty()) {
                        beneficiariosTxt = TextUtils.join(", ", lista);
                    }
                }
                // Si hay texto pero no IDs, dejamos el campo de texto como fallback
                if (!TextUtils.isEmpty(beneficiariosTxt) && etBeneficiarios != null) {
                    etBeneficiarios.setText(beneficiariosTxt);
                }
            }
            renderChipsBeneficiarios();
        } catch (Exception ignored) {}

        Long diasAviso = firstNonNull(doc.getLong("diasAviso"), doc.getLong("dias_aviso"),
                doc.getLong("diasAvisoPrevio"), doc.getLong("diasAvisoCancelacion"));
        if (diasAviso != null) etDiasAviso.setText(String.valueOf(diasAviso));

        // Dropdowns por id/nombre
        selectByIdOrName(actLugar, firstNonEmpty(doc.getString("lugar_id"), doc.getString("lugarId"), doc.getString("lugar")),
                firstNonEmpty(doc.getString("lugarNombre"), doc.getString("lugar")));
        selectByIdOrName(actOferente, firstNonEmpty(doc.getString("oferente_id"), doc.getString("oferenteId"), doc.getString("oferente")),
                firstNonEmpty(doc.getString("oferenteNombre"), doc.getString("oferente")));
        selectByIdOrName(actSocio, firstNonEmpty(doc.getString("socio_id"), doc.getString("socioId"), doc.getString("socioComunitario")),
                firstNonEmpty(doc.getString("socio_nombre"), doc.getString("socioComunitario")));
        selectByIdOrName(actProyecto, firstNonEmpty(doc.getString("proyecto_id"), doc.getString("project_id"), doc.getString("proyecto")),
                firstNonEmpty(doc.getString("proyectoNombre"), doc.getString("proyecto")));
    }
    
    private void cargarBeneficiariosDesdeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        
        db.collection("beneficiarios")
                .get()
                .addOnSuccessListener(snapshot -> {
                    beneficiariosSeleccionados.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (ids.contains(doc.getId())) {
                            Beneficiario b = doc.toObject(Beneficiario.class);
                            if (b != null) {
                                b.setId(doc.getId());
                                beneficiariosSeleccionados.add(b);
                            }
                        }
                    }
                    renderChipsBeneficiarios();
                })
                .addOnFailureListener(e -> {
                    toast("Error cargando beneficiarios");
                });
    }
    
    private void abrirSelectorBeneficiarios() {
        BeneficiariosPickerSheet sheet = BeneficiariosPickerSheet.newInstance(beneficiariosSeleccionadosIds);
        sheet.setListener(seleccionados -> {
            beneficiariosSeleccionados.clear();
            beneficiariosSeleccionados.addAll(seleccionados);
            
            beneficiariosSeleccionadosIds.clear();
            for (Beneficiario b : seleccionados) {
                beneficiariosSeleccionadosIds.add(b.getId());
            }
            
            renderChipsBeneficiarios();
        });
        sheet.show(getChildFragmentManager(), "beneficiariosPicker");
    }
    
    private void renderChipsBeneficiarios() {
        if (chipsBeneficiarios == null) return;
        
        chipsBeneficiarios.removeAllViews();
        
        if (beneficiariosSeleccionados.isEmpty()) {
            if (tvBeneficiariosHint != null) {
                tvBeneficiariosHint.setText("Seleccionar beneficiarios");
                tvBeneficiariosHint.setVisibility(View.VISIBLE);
            }
            return;
        }
        
        if (tvBeneficiariosHint != null) {
            tvBeneficiariosHint.setVisibility(View.GONE);
        }
        
        for (Beneficiario b : beneficiariosSeleccionados) {
            Chip chip = new Chip(requireContext());
            chip.setText(b.getNombre());
            chip.setCloseIconVisible(true);
            chip.setCheckable(false);
            chip.setOnCloseIconClickListener(v -> {
                beneficiariosSeleccionadosIds.remove(b.getId());
                beneficiariosSeleccionados.remove(b);
                renderChipsBeneficiarios();
            });
            chipsBeneficiarios.addView(chip);
        }
    }

    // ================== Guardar ==================
    private void guardar(){
        String nombre = val(etNombre); if (nombre.isEmpty()){ etNombre.setError("Requerido"); return; }
        String tipo = val(actTipo); if (tipo.isEmpty()){ actTipo.setError("Selecciona tipo"); return; }
        String periodicidad = val(actPeriodicidad); if (periodicidad.isEmpty()){ actPeriodicidad.setError("Selecciona periodicidad"); return; }

        String lugar = val(actLugar);            // se usa en citas, lo dejamos opcional aquí
        String oferente = val(actOferente);
        String cupoStr = val(etCupo);

        // NUEVO
        String socio = val(actSocio);
        String beneficiariosTxt = "";
        if (etBeneficiarios != null && etBeneficiarios.getText() != null) {
            beneficiariosTxt = etBeneficiarios.getText().toString().trim();
        }
        String proyecto = val(actProyecto);
        String diasAvisoStr = val(etDiasAviso);

        Map<String,Object> up = new HashMap<>();

        // Campos reales de tu doc
        up.put("nombre", nombre);
        up.put("tipoActividad", tipo);
        up.put("tipo", tipo); // compat
        up.put("periodicidad", periodicidad.toUpperCase(java.util.Locale.ROOT)); // tú guardas "PUNTUAL"
        if (!TextUtils.isEmpty(cupoStr)) {
            try { up.put("cupo", Integer.parseInt(cupoStr)); }
            catch (NumberFormatException e){ etCupo.setError("Número inválido"); return; }
        }

        if (!TextUtils.isEmpty(oferente)) {
            up.put("oferente", oferente);                 // clave real que tienes
            up.put("oferentes", java.util.Arrays.asList(oferente)); // compat con array si alguna vista lo usa
            up.put("oferenteNombre", oferente);           // por si alguna vista antigua lo lee
        }

        if (!TextUtils.isEmpty(socio)) up.put("socioComunitario", socio);

        // Guardar beneficiarios - Priorizar IDs sobre texto
        if (!beneficiariosSeleccionadosIds.isEmpty()) {
            up.put("beneficiariosIds", beneficiariosSeleccionadosIds);
            // También guardar nombres como texto para compatibilidad
            java.util.List<String> nombres = new java.util.ArrayList<>();
            for (Beneficiario b : beneficiariosSeleccionados) {
                if (b != null && !TextUtils.isEmpty(b.getNombre())) {
                    nombres.add(b.getNombre());
                }
            }
            if (!nombres.isEmpty()) {
                up.put("beneficiarios", nombres);
                up.put("beneficiariosTexto", TextUtils.join(", ", nombres));
            }
        } else if (!TextUtils.isEmpty(beneficiariosTxt)) {
            // Fallback: si hay texto manual pero no IDs
            up.put("beneficiariosTexto", beneficiariosTxt);
            java.util.List<String> lista = new java.util.ArrayList<>();
            for (String s : beneficiariosTxt.split(",")) {
                String t = s.trim(); if (!t.isEmpty()) lista.add(t);
            }
            if (!lista.isEmpty()) up.put("beneficiarios", lista);
        }

        // Proyecto es opcional en tu modelo; lo dejamos si lo usas en algunas pantallas
        if (!TextUtils.isEmpty(proyecto)) {
            up.put("proyecto", proyecto);
            up.put("proyectoNombre", proyecto);
        }

        // Días de aviso: tu clave real es diasAvisoPrevio
        if (!TextUtils.isEmpty(diasAvisoStr)) {
            try {
                int dias = Integer.parseInt(diasAvisoStr);
                up.put("diasAvisoPrevio", dias);  // clave real
                // compat con variantes si en algún lado las leen:
                up.put("diasAviso", dias);
                up.put("dias_aviso", dias);
                up.put("diasAvisoCancelacion", dias);
                up.put("diasAvisoPrevio", dias);
            } catch (NumberFormatException e){ etDiasAviso.setError("Número inválido"); return; }
        }

        // Aunque el lugar no está en el doc de actividad en tu modelo, lo dejamos por compat si alguna UI lo lee
        if (!TextUtils.isEmpty(lugar)) {
            up.put("lugar", lugar);
            up.put("lugarNombre", lugar);
        }

        // Marca de actualización
        up.put("updatedAt", com.google.firebase.Timestamp.now());

        // ==== Actualizar EN y ES en batch si existen (mantengo tu lógica) ====
        db.runTransaction(trx -> {
            DocumentReference en = act(actividadId,true);
            DocumentReference es = act(actividadId,false);

            DocumentSnapshot dEn = trx.get(en);
            DocumentSnapshot dEs = trx.get(es);

            if (dEn.exists()) trx.update(en, up);
            if (dEs.exists()) trx.update(es, up);
            if (!dEn.exists() && !dEs.exists()) trx.set(en, up, SetOptions.merge());

            return null;
        }).addOnSuccessListener(u -> {
            toast("Cambios guardados");
            notifyChanged();   // ya refresca Detalle y Calendario
            dismiss();
        }).addOnFailureListener(e -> toast("Error: "+e.getMessage()));
    }



    // ================== Carga de combos ==================
    private void cargarTiposActividad(){
        // Intenta colecciones conocidas; si no, fallback a estáticos usando id = nombre
        List<String> cols = Arrays.asList("tipos_actividad", "tiposActividad");
        cargarColeccionAOptionsMulti(cols, actTipo, new ArrayList<>(Arrays.asList(tiposFijos)));
    }

    private void cargarColeccionAOptions(String collection, AutoCompleteTextView view){
        db.collection(collection).orderBy("nombre")
                .get()
                .addOnSuccessListener(q -> setAdapter(view, mapQueryToOptions(q)))
                .addOnFailureListener(e -> setAdapter(view, new ArrayList<>()));
    }

    private void cargarColeccionAOptionsMulti(List<String> colecciones, AutoCompleteTextView view){
        cargarColeccionAOptionsMulti(colecciones, view, null);
    }

    private void cargarColeccionAOptionsMulti(List<String> colecciones, AutoCompleteTextView view, @Nullable ArrayList<String> fallback){
        if (colecciones==null || colecciones.isEmpty()){
            if (fallback!=null && !fallback.isEmpty()){
                ArrayList<OptionItem> xs = new ArrayList<>();
                for (String s: fallback) xs.add(new OptionItem(s, s));
                setAdapter(view, xs);
            }
            return;
        }
        String col = colecciones.get(0);
        db.collection(col).orderBy("nombre").get()
                .addOnSuccessListener(q -> {
                    ArrayList<OptionItem> xs = mapQueryToOptions(q);
                    if (!xs.isEmpty()) setAdapter(view, xs);
                    else cargarColeccionAOptionsMulti(colecciones.subList(1, colecciones.size()), view, fallback);
                })
                .addOnFailureListener(e ->
                        cargarColeccionAOptionsMulti(colecciones.subList(1, colecciones.size()), view, fallback));
    }

    private ArrayList<OptionItem> mapQueryToOptions(QuerySnapshot q){
        ArrayList<OptionItem> xs = new ArrayList<>();
        if (q!=null && !q.isEmpty()){
            for (DocumentSnapshot d: q){
                String id = d.getId();
                String nombre = d.getString("nombre");
                if (!TextUtils.isEmpty(nombre)) xs.add(new OptionItem(id, nombre));
            }
        }
        return xs;
    }

    private void setAdapter(AutoCompleteTextView view, ArrayList<OptionItem> items){
        view.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, items));
    }

    // ================== Helpers UI ==================
    private void set(TextInputEditText et, String v){ if (et!=null && v!=null) et.setText(v); }
    private void setDropText(AutoCompleteTextView v, String s){ if (!TextUtils.isEmpty(s)) v.setText(s,false); }

    private @Nullable OptionItem selected(@NonNull AutoCompleteTextView v) {
        CharSequence txt = v.getText();
        String s = (txt == null) ? "" : txt.toString().trim();
        if (s.isEmpty() || !(v.getAdapter() instanceof ArrayAdapter)) return null;

        ArrayAdapter<?> ad = (ArrayAdapter<?>) v.getAdapter();

        // 1) Match exacto por nombre mostrado
        for (int i = 0; i < ad.getCount(); i++) {
            Object it = ad.getItem(i);
            if (it instanceof OptionItem) {
                OptionItem oi = (OptionItem) it;
                if (s.equals(oi.nombre)) return oi;
            }
        }

        // 2) Match case-insensitive y sin tildes; también permite escribir el id
        String norm = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < ad.getCount(); i++) {
            Object it = ad.getItem(i);
            if (it instanceof OptionItem) {
                OptionItem oi = (OptionItem) it;
                String on = java.text.Normalizer.normalize(oi.nombre, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
                if (norm.equals(on) || norm.equals(oi.id.toLowerCase(java.util.Locale.ROOT))) return oi;
            }
        }
        return null;
    }


    private void selectByIdOrName(AutoCompleteTextView v, String id, String nombre){
        if (v.getAdapter() instanceof ArrayAdapter){
            ArrayAdapter<?> ad = (ArrayAdapter<?>) v.getAdapter();
            // intenta por id
            if (!TextUtils.isEmpty(id)) {
                for (int i=0;i<ad.getCount();i++){
                    Object it = ad.getItem(i);
                    if (it instanceof OptionItem && id.equals(((OptionItem) it).id)){
                        v.setText(((OptionItem) it).nombre, false);
                        return;
                    }
                }
            }
            // intenta por nombre
            if (!TextUtils.isEmpty(nombre)) {
                for (int i=0;i<ad.getCount();i++){
                    Object it = ad.getItem(i);
                    if (it instanceof OptionItem && nombre.equals(((OptionItem) it).nombre)){
                        v.setText(((OptionItem) it).nombre, false);
                        return;
                    }
                }
            }
        }
        // si aún no hay adapter, deja texto visible para que no quede vacío
        if (!TextUtils.isEmpty(nombre)) v.setText(nombre, false);
    }

    private String val(TextInputEditText et){ return et.getText()!=null ? et.getText().toString().trim() : ""; }
    private String val(AutoCompleteTextView et){ return et.getText()!=null ? et.getText().toString().trim() : ""; }
    private String firstNonEmpty(String... xs){
        if (xs==null) return null;
        for (String s: xs){ if (!TextUtils.isEmpty(s)) return s; }
        return null;
    }
    private Long firstNonNull(Long... xs){
        if (xs==null) return null;
        for (Long x: xs){ if (x!=null) return x; }
        return null;
    }
    private void notifyChanged(){
        Bundle b = new Bundle();
        try { getParentFragmentManager().setFragmentResult("actividad_change", b); } catch (Exception ignore) {}
        try { getParentFragmentManager().setFragmentResult("calendar_refresh", b); } catch (Exception ignore) {}
        try { requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b); } catch (Exception ignore) {}
        try { requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b); } catch (Exception ignore) {}
    }
    private void mostrarDialogoCambiarPeriodicidad() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Cambiar periodicidad")
                .setMessage("Para cambiar la periodicidad de una actividad (de puntual a periódica o viceversa), es necesario crear una nueva actividad.\n\n" +
                        "Esto se debe a que cambiar la periodicidad afecta la forma en que se generan las citas.\n\n" +
                        "¿Qué deseas hacer?")
                .setPositiveButton("Crear nueva actividad", (d, w) -> {
                    // Navegar al formulario de creación
                    try {
                        dismiss();
                        // Usar el NavController del Activity
                        androidx.navigation.NavController navController =
                                androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host);
                        navController.navigate(R.id.activityFormFragment);
                    } catch (Exception e) {
                        toast("No se pudo navegar al formulario de creación");
                    }
                })
                .setNegativeButton("Continuar editando", (d, w) -> {
                    // No hacer nada, mantener el valor original
                    d.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
}
