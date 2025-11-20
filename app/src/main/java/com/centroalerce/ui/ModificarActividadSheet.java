package com.centroalerce.ui;

import android.content.Intent;
import android.net.Uri;
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
import androidx.fragment.app.Fragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Beneficiario;
import com.centroalerce.gestion.repositories.LugarRepository;
import com.centroalerce.gestion.utils.ActividadValidator;
import com.centroalerce.gestion.utils.CustomToast;
import com.centroalerce.gestion.utils.DateUtils;
import com.centroalerce.gestion.utils.ValidationResult;
import com.centroalerce.ui.mantenedores.dialog.BeneficiariosPickerSheet;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
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

import com.centroalerce.gestion.services.NotificationService;
import com.centroalerce.gestion.models.Actividad;
import com.centroalerce.gestion.models.Cita;

public class ModificarActividadSheet extends BottomSheetDialogFragment {
    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    public static ModificarActividadSheet newInstance(@NonNull String actividadId){
        Bundle b = new Bundle(); b.putString(ARG_ACTIVIDAD_ID, actividadId);
        ModificarActividadSheet s = new ModificarActividadSheet(); s.setArguments(b); return s;
    }

    // ===== Utils Firestore multi-colección (ES/EN) =====
    private static final String COL_EN = "activities";
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
    private LugarRepository lugarRepository;
    private String actividadId;
    private String lugarIdActual; // ID del lugar actual (para validar conflictos)
    private Timestamp fechaHoraActual; // Fecha/hora actual de la actividad

    private TextInputEditText etNombre, etCupo, etBeneficiarios, etDiasAviso;
    private AutoCompleteTextView actTipo, actPeriodicidad, actLugar, actOferente, actSocio, actProyecto;

    // UI para archivos adjuntos
    private LinearLayout llAdjuntos;

    // UI para beneficiarios
    private MaterialCardView btnBeneficiarios;
    private TextView tvBeneficiariosHint;
    private ChipGroup chipsBeneficiarios;
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

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v,b);
        db = FirebaseFirestore.getInstance();
        lugarRepository = new LugarRepository();
        actividadId = getArguments()!=null ? getArguments().getString(ARG_ACTIVIDAD_ID) : null;

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

        // Referencias UI para archivos adjuntos
        llAdjuntos = v.findViewById(R.id.llAdjuntos);

        // Referencias UI para beneficiarios
        btnBeneficiarios = v.findViewById(R.id.btnBeneficiarios);
        tvBeneficiariosHint = v.findViewById(R.id.tvBeneficiariosHint);
        chipsBeneficiarios = v.findViewById(R.id.chipsBeneficiarios);

        // Listener para abrir selector de beneficiarios
        if (btnBeneficiarios != null) {
            btnBeneficiarios.setOnClickListener(view -> abrirSelectorBeneficiarios());
        }

        // Listener para recargar adjuntos cuando se eliminan
        getParentFragmentManager().setFragmentResultListener("adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> precargar());
        requireActivity().getSupportFragmentManager().setFragmentResultListener("adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> precargar());

        // Estáticos
        actPeriodicidad.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, periodicidades));

        // Dinámicos con {id, nombre}
        cargarTiposActividad();
        cargarColeccionAOptions("lugares", actLugar);
        cargarColeccionAOptions("oferentes", actOferente);
        cargarColeccionAOptionsMulti(Arrays.asList("socios_comunitarios", "socios", "sociosComunitarios"), actSocio);
        cargarColeccionAOptions("proyectos", actProyecto);

        precargar();

        ((MaterialButton) v.findViewById(R.id.btnGuardarCambios)).setOnClickListener(x -> guardar());
    }

    // ================== Precargar & bind ==================
    private void precargar(){
        if (TextUtils.isEmpty(actividadId)) { toast("Falta actividadId"); return; }
        act(actividadId,true).get().addOnSuccessListener(doc -> {
            if (doc!=null && doc.exists()) bind(doc);
            else act(actividadId,false).get().addOnSuccessListener(this::bind)
                    .addOnFailureListener(e -> toast("No se pudo cargar"));
        }).addOnFailureListener(e -> toast("No se pudo cargar"));
    }

    private void bind(DocumentSnapshot doc){
        if (doc==null || !doc.exists()) return;

        // Texto
        set(etNombre, doc.getString("nombre"));
        Long cupo = doc.getLong("cupo"); if (cupo!=null) etCupo.setText(String.valueOf(cupo));

        // Tipo / Periodicidad (pueden venir en distintas claves)
        setDropText(actTipo, firstNonEmpty(doc.getString("tipoActividad"), doc.getString("tipo")));

        // 🔥 NUEVO: Guardar periodicidad actual SIN bloquear el campo
        final String periodicidadActual = firstNonEmpty(doc.getString("periodicidad"), doc.getString("frecuencia"));
        setDropText(actPeriodicidad, periodicidadActual);

        // 🔥 NUEVO: Detectar cambio de periodicidad y mostrar modal
        actPeriodicidad.setOnItemClickListener((parent, view, position, id) -> {
            String periodicidadSeleccionada = actPeriodicidad.getText().toString().trim().toUpperCase();
            String periodicidadActualUpper = periodicidadActual != null ? periodicidadActual.toUpperCase() : "";

            // Si intenta cambiar la periodicidad, mostrar diálogo
            if (!periodicidadSeleccionada.equals(periodicidadActualUpper)) {
                mostrarDialogoCambioPeriodicidad(periodicidadActual);
                // Revertir al valor original
                actPeriodicidad.setText(periodicidadActual, false);
            }
        });

        // Beneficiarios texto
        String beneficiariosTxt = firstNonEmpty(doc.getString("beneficiariosTexto"));
        if (TextUtils.isEmpty(beneficiariosTxt)) {
            try {
                @SuppressWarnings("unchecked")
                List<String> lista = (List<String>) doc.get("beneficiarios");
                if (lista != null && !lista.isEmpty()) beneficiariosTxt = TextUtils.join(", ", lista);
            } catch (Exception ignored) {}
        }
        if (!TextUtils.isEmpty(beneficiariosTxt)) etBeneficiarios.setText(beneficiariosTxt);

        Long diasAviso = firstNonNull(doc.getLong("diasAviso"), doc.getLong("dias_aviso"),
                doc.getLong("diasAvisoPrevio"), doc.getLong("diasAvisoCancelacion"));
        if (diasAviso != null) etDiasAviso.setText(String.valueOf(diasAviso));

        // Guardar lugar actual para validar cambios
        lugarIdActual = firstNonEmpty(doc.getString("lugar_id"), doc.getString("lugarId"), doc.getString("lugar"));

        // Obtener fecha/hora actual de la actividad (primera cita)
        doc.getReference().collection("citas")
                .orderBy("startAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        DocumentSnapshot citaDoc = qs.getDocuments().get(0);
                        fechaHoraActual = citaDoc.getTimestamp("startAt");
                        if (fechaHoraActual == null) fechaHoraActual = citaDoc.getTimestamp("fecha");
                    }
                });

        // Dropdowns por id/nombre
        selectByIdOrName(actLugar, firstNonEmpty(doc.getString("lugar_id"), doc.getString("lugarId"), doc.getString("lugar")),
                firstNonEmpty(doc.getString("lugarNombre"), doc.getString("lugar")));
        selectByIdOrName(actOferente, firstNonEmpty(doc.getString("oferente_id"), doc.getString("oferenteId"), doc.getString("oferente")),
                firstNonEmpty(doc.getString("oferenteNombre"), doc.getString("oferente")));
        selectByIdOrName(actSocio, firstNonEmpty(doc.getString("socio_id"), doc.getString("socioId"), doc.getString("socioComunitario")),
                firstNonEmpty(doc.getString("socio_nombre"), doc.getString("socioComunitario")));
        selectByIdOrName(actProyecto, firstNonEmpty(doc.getString("proyecto_id"), doc.getString("project_id"), doc.getString("proyecto")),
                firstNonEmpty(doc.getString("proyectoNombre"), doc.getString("proyecto")));

        // Cargar archivos adjuntos
        cargarArchivosAdjuntos(doc);

        // Cargar beneficiarios seleccionados
        cargarBeneficiariosDesdeDocumento(doc);
    }
    /**
     * Muestra diálogo cuando el usuario intenta cambiar la periodicidad
     */
    private void mostrarDialogoCambioPeriodicidad(String periodicidadActual) {
        String tipoPeriodo = "PERIODICA".equalsIgnoreCase(periodicidadActual) ? "periódica" : "puntual";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ No se puede cambiar la periodicidad")
                .setMessage("Esta actividad es " + tipoPeriodo + " y ya tiene citas asociadas.\n\n" +
                        "Para cambiar la periodicidad necesitas crear una nueva actividad desde cero.")
                .setPositiveButton("Crear nueva actividad", (dialog, which) -> {
                    // Cerrar el diálogo y el sheet
                    dialog.dismiss();
                    dismiss();

                    // Navegar al formulario de crear actividad usando el NavController de la Activity
                    try {
                        // Obtener el NavController desde el nav_host del activity_main
                        androidx.navigation.NavController navController =
                                androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host);

                        // Navegar al activityFormFragment (ya existe en nav_graph.xml)
                        navController.navigate(R.id.activityFormFragment);

                        android.util.Log.d("ModificarActividad", "✅ Navegando a crear nueva actividad");
                    } catch (Exception e) {
                        android.util.Log.e("ModificarActividad", "❌ Error navegando: " + e.getMessage(), e);
                        toast("Cierra este formulario y crea una nueva actividad desde el menú");
                    }
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    // Solo cerrar el diálogo
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    // ================== Guardar ==================
    private void guardar(){
        // Mostrar ProgressDialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Guardando cambios...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String nombre = val(etNombre);
        if (nombre.isEmpty()){
            progressDialog.dismiss();
            etNombre.setError("Requerido");
            return;
        }
        String tipo = val(actTipo);
        if (tipo.isEmpty()){
            progressDialog.dismiss();
            actTipo.setError("Selecciona tipo");
            return;
        }
        String periodicidad = val(actPeriodicidad);
        if (periodicidad.isEmpty()){
            progressDialog.dismiss();
            actPeriodicidad.setError("Selecciona periodicidad");
            return;
        }

        String lugarNuevo = val(actLugar);
        String oferente = val(actOferente);
        String cupoStr = val(etCupo);
        String socio = val(actSocio);
        String beneficiariosTxt = val(etBeneficiarios);
        String proyecto = val(actProyecto);
        String diasAvisoStr = val(etDiasAviso);

        // 🔥 DECLARAR como final FUERA del if para que sea accesible en callbacks
        final Integer cupoNuevo;
        if (!TextUtils.isEmpty(cupoStr)) {
            try {
                cupoNuevo = Integer.parseInt(cupoStr);
                ValidationResult validacionCupo = ActividadValidator.validarCupoActividad(cupoNuevo);
                if (!validacionCupo.isValid()) {
                    progressDialog.dismiss();
                    etCupo.setError(validacionCupo.getErrorMessage());
                    toast(validacionCupo.getErrorMessage());
                    return;
                }
            } catch (NumberFormatException e) {
                progressDialog.dismiss();
                etCupo.setError("Número inválido");
                return;
            }
        } else {
            cupoNuevo = null; // Si no hay cupo, asignar null
        }

        // Si el lugar cambió, verificar conflictos de horario antes de guardar
        if (!TextUtils.isEmpty(lugarNuevo) && !lugarNuevo.equals(lugarIdActual)) {
            android.util.Log.d("ModificarActividad", "🔍 Lugar cambió de '" + lugarIdActual + "' a '" + lugarNuevo + "' - validando conflictos");

            // Validar cupo del nuevo lugar
            lugarRepository.getLugarPorNombre(lugarNuevo, new LugarRepository.LugarCallback() {
                @Override
                public void onSuccess(com.centroalerce.gestion.models.Lugar lugar) {
                    // Validar cupo del lugar
                    if (cupoNuevo != null) {
                        ValidationResult validacionCupoLugar = ActividadValidator.validarCupoLugar(lugar, cupoNuevo);
                        if (!validacionCupoLugar.isValid()) {
                            progressDialog.dismiss();
                            mostrarDialogoErrorCupo(validacionCupoLugar.getErrorMessage());
                            return;
                        }
                    }

                    // Si pasa validación de cupo, continuar con validación de conflictos
                    verificarConflictosYGuardar(progressDialog, nombre, tipo, periodicidad, lugarNuevo, oferente, cupoStr, socio, beneficiariosTxt, proyecto, diasAvisoStr);
                }

                @Override
                public void onError(String error) {
                    progressDialog.dismiss();
                    toast("Error al obtener lugar: " + error);
                }
            });
            return;
        }

        // Si no cambió el lugar, validar cupo del lugar actual si cambió el cupo
        if (cupoNuevo != null && !TextUtils.isEmpty(lugarNuevo)) {
            lugarRepository.getLugarPorNombre(lugarNuevo, new LugarRepository.LugarCallback() {
                @Override
                public void onSuccess(com.centroalerce.gestion.models.Lugar lugar) {
                    ValidationResult validacionCupoLugar = ActividadValidator.validarCupoLugar(lugar, cupoNuevo);
                    if (!validacionCupoLugar.isValid()) {
                        progressDialog.dismiss();
                        mostrarDialogoErrorCupo(validacionCupoLugar.getErrorMessage());
                        return;
                    }

                    // Si pasa la validación, guardar
                    realizarGuardado(progressDialog, nombre, tipo, periodicidad, lugarNuevo, oferente, cupoStr, socio, beneficiariosTxt, proyecto, diasAvisoStr);
                }

                @Override
                public void onError(String error) {
                    progressDialog.dismiss();
                    toast("Error al obtener lugar: " + error);
                }
            });
            return;
        }

        // Si no hay cambios que requieran validación, guardar directamente
        realizarGuardado(progressDialog, nombre, tipo, periodicidad, lugarNuevo, oferente, cupoStr, socio, beneficiariosTxt, proyecto, diasAvisoStr);
    }

    /**
     * Muestra diálogo de error cuando el cupo excede la capacidad del lugar
     */
    private void mostrarDialogoErrorCupo(String mensaje) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Cupo insuficiente")
                .setMessage(mensaje + "\n\n¿Qué deseas hacer?")
                .setPositiveButton("Reducir cupo", (dialog, which) -> {
                    etCupo.requestFocus();
                    etCupo.selectAll();
                })
                .setNeutralButton("Cambiar lugar", (dialog, which) -> {
                    actLugar.requestFocus();
                    actLugar.showDropDown();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Verifica conflictos de horario con el nuevo lugar antes de guardar
     */
    private void verificarConflictosYGuardar(android.app.ProgressDialog progressDialog, String nombre, String tipo, String periodicidad, String lugar,
                                             String oferente, String cupoStr, String socio, String beneficiariosTxt,
                                             String proyecto, String diasAvisoStr) {
        // Obtener todas las citas de esta actividad para verificar conflictos
        db.collection("activities").document(actividadId)
                .collection("citas")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        android.util.Log.d("ModificarActividad", "✅ Sin citas - guardando sin validar");
                        realizarGuardado(progressDialog, nombre, tipo, periodicidad, lugar, oferente, cupoStr, socio, beneficiariosTxt, proyecto, diasAvisoStr);
                        return;
                    }

                    // Recolectar todas las fechas de las citas
                    List<java.util.Date> fechasCitas = new ArrayList<>();
                    for (DocumentSnapshot citaDoc : querySnapshot.getDocuments()) {
                        Timestamp ts = citaDoc.getTimestamp("startAt");
                        if (ts == null) ts = citaDoc.getTimestamp("fecha");
                        if (ts != null) {
                            fechasCitas.add(ts.toDate());
                        }
                    }

                    if (fechasCitas.isEmpty()) {
                        android.util.Log.d("ModificarActividad", "✅ Sin fechas en citas - guardando sin validar");
                        realizarGuardado(progressDialog, nombre, tipo, periodicidad, lugar, oferente, cupoStr, socio, beneficiariosTxt, proyecto, diasAvisoStr);
                        return;
                    }

                    android.util.Log.d("ModificarActividad", "🔍 Verificando " + fechasCitas.size() + " citas para conflictos");

                    // Verificar cada fecha para conflictos
                    verificarTodasLasFechas(lugar, fechasCitas, 0, new ConflictoCallback() {
                        @Override
                        public void onConflictoDetectado(String mensaje) {
                            progressDialog.dismiss();
                            android.util.Log.w("ModificarActividad", "⚠️ CONFLICTO: " + mensaje);
                            mostrarDialogoConflicto(mensaje);
                        }

                        @Override
                        public void onSinConflictos() {
                            android.util.Log.d("ModificarActividad", "✅ Sin conflictos - procediendo a guardar");
                            realizarGuardado(progressDialog, nombre, tipo, periodicidad, lugar, oferente, cupoStr, socio, beneficiariosTxt, proyecto, diasAvisoStr);
                        }

                        @Override
                        public void onError(String error) {
                            progressDialog.dismiss();
                            android.util.Log.e("ModificarActividad", "❌ Error: " + error);
                            toast("Error al verificar conflictos: " + error);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    android.util.Log.e("ModificarActividad", "❌ Error obteniendo citas: " + e.getMessage());
                    toast("Error al verificar conflictos: " + e.getMessage());
                });
    }
    /**
     * Verifica recursivamente todas las fechas para conflictos
     */
    private void verificarTodasLasFechas(String lugarNombre, List<java.util.Date> fechas, int index, ConflictoCallback callback) {
        if (index >= fechas.size()) {
            callback.onSinConflictos();
            return;
        }

        java.util.Date fechaActual = fechas.get(index);
        android.util.Log.d("ModificarActividad", "🔍 Verificando fecha " + (index + 1) + "/" + fechas.size() + ": " + fechaActual);

        lugarRepository.getCitasEnLugar(lugarNombre, fechaActual, new LugarRepository.CitasEnLugarCallback() {
            @Override
            public void onSuccess(List<java.util.Date> citasExistentes) {
                android.util.Log.d("ModificarActividad", "📊 Citas existentes en lugar: " + citasExistentes.size());

                // 🔥 FILTRAR: Excluir las citas de esta actividad
                // Para esto necesitamos comparar con nuestras propias citas
                db.collection("activities").document(actividadId)
                        .collection("citas")
                        .get()
                        .addOnSuccessListener(qs -> {
                            List<java.util.Date> citasPropias = new ArrayList<>();
                            for (DocumentSnapshot citaDoc : qs.getDocuments()) {
                                Timestamp ts = citaDoc.getTimestamp("startAt");
                                if (ts == null) ts = citaDoc.getTimestamp("fecha");
                                if (ts != null) citasPropias.add(ts.toDate());
                            }

                            // Filtrar citas que NO son de esta actividad
                            List<java.util.Date> citasOtrasActividades = new ArrayList<>();
                            for (java.util.Date cita : citasExistentes) {
                                boolean esPropia = false;
                                for (java.util.Date citaPropia : citasPropias) {
                                    // Comparar con margen de 1 minuto
                                    long diff = Math.abs(cita.getTime() - citaPropia.getTime());
                                    if (diff < 60000) { // menos de 1 minuto
                                        esPropia = true;
                                        break;
                                    }
                                }
                                if (!esPropia) citasOtrasActividades.add(cita);
                            }

                            android.util.Log.d("ModificarActividad", "📊 Citas de otras actividades: " + citasOtrasActividades.size());

                            ValidationResult validacion = ActividadValidator.validarConflictoHorario(
                                    lugarNombre, fechaActual, citasOtrasActividades, 30
                            );

                            if (!validacion.isValid()) {
                                callback.onConflictoDetectado(validacion.getErrorMessage());
                                return;
                            }

                            // Continuar con la siguiente fecha
                            verificarTodasLasFechas(lugarNombre, fechas, index + 1, callback);
                        })
                        .addOnFailureListener(e -> callback.onError("Error al obtener citas propias: " + e.getMessage()));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Realiza el guardado efectivo de los cambios
     */
    private void realizarGuardado(android.app.ProgressDialog progressDialog, String nombre, String tipo, String periodicidad, String lugar,
                                  String oferente, String cupoStr, String socio, String beneficiariosTxt,
                                  String proyecto, String diasAvisoStr) {

        Map<String,Object> up = new HashMap<>();

        // Campos reales de tu doc
        up.put("nombre", nombre);
        up.put("tipoActividad", tipo);
        up.put("tipo", tipo); // compat
        up.put("periodicidad", periodicidad.toUpperCase(java.util.Locale.ROOT)); // tú guardas "PUNTUAL"
        if (!TextUtils.isEmpty(cupoStr)) {
            try { up.put("cupo", Integer.parseInt(cupoStr)); }
            catch (NumberFormatException e){
                progressDialog.dismiss();
                etCupo.setError("Número inválido");
                return;
            }
        }

        if (!TextUtils.isEmpty(oferente)) {
            up.put("oferente", oferente);                 // clave real que tienes
            up.put("oferentes", java.util.Arrays.asList(oferente)); // compat con array si alguna vista lo usa
            up.put("oferenteNombre", oferente);           // por si alguna vista antigua lo lee
        }

        if (!TextUtils.isEmpty(socio)) up.put("socioComunitario", socio);

        // Beneficiarios: priorizar la selección con chips, fallback a texto
        if (!beneficiariosSeleccionados.isEmpty()) {
            // Guardar IDs y nombres de beneficiarios seleccionados con chips
            List<String> nombresSeleccionados = new ArrayList<>();
            for (Beneficiario b : beneficiariosSeleccionados) {
                nombresSeleccionados.add(b.getNombre());
            }
            up.put("beneficiarios", nombresSeleccionados);
            up.put("beneficiariosNombres", nombresSeleccionados);
            up.put("beneficiarios_ids", beneficiariosSeleccionadosIds);
            up.put("beneficiariosIds", beneficiariosSeleccionadosIds);
        } else if (!TextUtils.isEmpty(beneficiariosTxt)) {
            // Fallback: usar texto de beneficiarios si no hay selección con chips
            up.put("beneficiariosTexto", beneficiariosTxt);
            // Convertir texto a array
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
            } catch (NumberFormatException e){
                progressDialog.dismiss();
                etDiasAviso.setError("Número inválido");
                return;
            }
        }

        // Aunque el lugar no está en el doc de actividad en tu modelo, lo dejamos por compat si alguna UI lo lee
        if (!TextUtils.isEmpty(lugar)) {
            up.put("lugar", lugar);
            up.put("lugarNombre", lugar);
        }

        // Marca de actualización
        up.put("updatedAt", com.google.firebase.Timestamp.now());

        // ==== Actualizar EN y ES en batch si existen (mantengo tu lógica) ====
        android.util.Log.d("MOD_ACT", "💾 Iniciando transacción para guardar cambios. ActividadId: " + actividadId);
        android.util.Log.d("MOD_ACT", "📝 Datos a guardar: nombre=" + nombre + ", lugar=" + lugar + ", tipo=" + tipo);

        db.runTransaction(trx -> {
            DocumentReference en = act(actividadId,true);
            DocumentReference es = act(actividadId,false);

            DocumentSnapshot dEn = trx.get(en);
            DocumentSnapshot dEs = trx.get(es);

            android.util.Log.d("MOD_ACT", "📄 Doc EN exists: " + dEn.exists() + ", Doc ES exists: " + dEs.exists());

            if (dEn.exists()) {
                trx.update(en, up);
                android.util.Log.d("MOD_ACT", "✏️ Actualizando documento EN (activities)");
            }
            if (dEs.exists()) {
                trx.update(es, up);
                android.util.Log.d("MOD_ACT", "✏️ Actualizando documento ES (actividades)");
            }
            if (!dEn.exists() && !dEs.exists()) {
                trx.set(en, up, SetOptions.merge());
                android.util.Log.d("MOD_ACT", "➕ Creando nuevo documento EN");
            }

            return null;
        }).addOnSuccessListener(u -> {
            progressDialog.dismiss();
            android.util.Log.d("MOD_ACT", "✅ Transacción exitosa - cambios guardados en Firestore");
            CustomToast.showSuccess(getContext(), "Cambios guardados con éxito");

            // 🔥 NUEVO: Actualizar las citas asociadas con el nuevo nombre y lugar
            actualizarCitasAsociadas(nombre, lugar);

            // Reprogramar notificaciones si se modificó diasAvisoPrevio
            if (!TextUtils.isEmpty(diasAvisoStr)) {
                reprogramarNotificaciones(actividadId, Integer.parseInt(diasAvisoStr));
            }

            notifyChanged();   // ya refresca Detalle y Calendario
            dismiss();
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            android.util.Log.e("MOD_ACT", "❌ Error en transacción: " + e.getMessage(), e);
            CustomToast.showError(getContext(), "Error al guardar: " + e.getMessage());
        });
    }

    /**
     * Actualiza las citas asociadas con el nuevo nombre y lugar de la actividad
     */
    private void actualizarCitasAsociadas(String nuevoNombre, String nuevoLugar) {
        android.util.Log.d("MOD_ACT", "📝 Actualizando citas asociadas con nombre='" + nuevoNombre + "', lugar='" + nuevoLugar + "'");

        // Actualizar citas en la colección global "citas"
        db.collection("citas")
            .whereEqualTo("actividadId", actividadId)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                android.util.Log.d("MOD_ACT", "📊 Encontradas " + querySnapshot.size() + " citas en colección global");

                Map<String, Object> updates = new HashMap<>();
                updates.put("nombre", nuevoNombre);
                updates.put("titulo", nuevoNombre);
                updates.put("activityName", nuevoNombre);
                if (!TextUtils.isEmpty(nuevoLugar)) {
                    updates.put("lugar", nuevoLugar);
                    updates.put("lugarNombre", nuevoLugar);
                }

                for (DocumentSnapshot citaDoc : querySnapshot.getDocuments()) {
                    citaDoc.getReference().update(updates)
                        .addOnSuccessListener(aVoid ->
                            android.util.Log.d("MOD_ACT", "✅ Cita " + citaDoc.getId() + " actualizada en colección global"))
                        .addOnFailureListener(e ->
                            android.util.Log.e("MOD_ACT", "❌ Error actualizando cita " + citaDoc.getId() + ": " + e.getMessage()));
                }
            })
            .addOnFailureListener(e ->
                android.util.Log.e("MOD_ACT", "❌ Error consultando citas en colección global: " + e.getMessage()));

        // Actualizar citas en la subcolección activities/{actividadId}/citas
        db.collection("activities").document(actividadId)
            .collection("citas")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                android.util.Log.d("MOD_ACT", "📊 Encontradas " + querySnapshot.size() + " citas en subcolección EN");

                Map<String, Object> updates = new HashMap<>();
                updates.put("nombre", nuevoNombre);
                updates.put("titulo", nuevoNombre);
                updates.put("activityName", nuevoNombre);
                if (!TextUtils.isEmpty(nuevoLugar)) {
                    updates.put("lugar", nuevoLugar);
                    updates.put("lugarNombre", nuevoLugar);
                }

                for (DocumentSnapshot citaDoc : querySnapshot.getDocuments()) {
                    citaDoc.getReference().update(updates)
                        .addOnSuccessListener(aVoid ->
                            android.util.Log.d("MOD_ACT", "✅ Cita " + citaDoc.getId() + " actualizada en subcolección EN"))
                        .addOnFailureListener(e ->
                            android.util.Log.e("MOD_ACT", "❌ Error actualizando cita " + citaDoc.getId() + ": " + e.getMessage()));
                }
            })
            .addOnFailureListener(e ->
                android.util.Log.e("MOD_ACT", "❌ Error consultando citas en subcolección EN: " + e.getMessage()));

        // Actualizar citas en la subcolección actividades/{actividadId}/citas
        db.collection("actividades").document(actividadId)
            .collection("citas")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                android.util.Log.d("MOD_ACT", "📊 Encontradas " + querySnapshot.size() + " citas en subcolección ES");

                Map<String, Object> updates = new HashMap<>();
                updates.put("nombre", nuevoNombre);
                updates.put("titulo", nuevoNombre);
                updates.put("activityName", nuevoNombre);
                if (!TextUtils.isEmpty(nuevoLugar)) {
                    updates.put("lugar", nuevoLugar);
                    updates.put("lugarNombre", nuevoLugar);
                }

                for (DocumentSnapshot citaDoc : querySnapshot.getDocuments()) {
                    citaDoc.getReference().update(updates)
                        .addOnSuccessListener(aVoid ->
                            android.util.Log.d("MOD_ACT", "✅ Cita " + citaDoc.getId() + " actualizada en subcolección ES"))
                        .addOnFailureListener(e ->
                            android.util.Log.e("MOD_ACT", "❌ Error actualizando cita " + citaDoc.getId() + ": " + e.getMessage()));
                }
            })
            .addOnFailureListener(e ->
                android.util.Log.e("MOD_ACT", "❌ Error consultando citas en subcolección ES: " + e.getMessage()));
    }

    /**
     * Reprograma las notificaciones de la actividad cuando se modifican los días de aviso previo
     */
    private void reprogramarNotificaciones(String actividadId, int nuevosDiasAviso) {
        // Obtener la actividad actualizada
        db.collection("actividades").document(actividadId)
            .get()
            .addOnSuccessListener(docActividad -> {
                if (!docActividad.exists()) return;

                // Convertir documento a objeto Actividad
                Actividad actividad = docActividad.toObject(Actividad.class);
                if (actividad == null) return;
                actividad.setId(docActividad.getId());
                actividad.setDiasAvisoPrevio(nuevosDiasAviso); // Actualizar con el nuevo valor

                // Obtener todas las citas de esta actividad
                db.collection("citas")
                    .whereEqualTo("actividadId", actividadId)
                    .get()
                    .addOnSuccessListener(queryCitas -> {
                        NotificationService notificationService = new NotificationService(requireContext());

                        for (DocumentSnapshot docCita : queryCitas.getDocuments()) {
                            Cita cita = docCita.toObject(Cita.class);
                            if (cita == null) continue;
                            cita.setId(docCita.getId());

                            // Cancelar notificaciones anteriores de esta cita
                            notificationService.cancelarNotificacionesCita(cita.getId());

                            // Programar nuevas notificaciones con los nuevos días de aviso
                            List<String> usuariosNotificar = new ArrayList<>();
                            // Aquí deberías obtener la lista de usuarios a notificar
                            // Por simplicidad, podrías notificar a todos los usuarios o usar un criterio específico

                            notificationService.programarNotificacionesCita(cita, actividad, usuariosNotificar);
                        }

                        android.util.Log.d("ModificarActividad", "Notificaciones reprogramadas para actividad: " + actividadId);
                    })
                    .addOnFailureListener(e ->
                        android.util.Log.e("ModificarActividad", "Error al obtener citas: " + e.getMessage())
                    );
            })
            .addOnFailureListener(e ->
                android.util.Log.e("ModificarActividad", "Error al obtener actividad: " + e.getMessage())
            );
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
        android.util.Log.d("MOD_ACT", "📢 Enviando eventos de actualización (actividad_change + calendar_refresh)");
        Bundle b = new Bundle();

        // Enviar a TODOS los FragmentManagers posibles para asegurar que se reciba
        try {
            getParentFragmentManager().setFragmentResult("actividad_change", b);
            android.util.Log.d("MOD_ACT", "✅ Enviado actividad_change a parentFragmentManager");
        } catch (Exception e) {
            android.util.Log.e("MOD_ACT", "❌ Error enviando actividad_change a parent: " + e.getMessage());
        }

        try {
            getParentFragmentManager().setFragmentResult("calendar_refresh", b);
            android.util.Log.d("MOD_ACT", "✅ Enviado calendar_refresh a parentFragmentManager");
        } catch (Exception e) {
            android.util.Log.e("MOD_ACT", "❌ Error enviando calendar_refresh a parent: " + e.getMessage());
        }

        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b);
            android.util.Log.d("MOD_ACT", "✅ Enviado actividad_change a activity supportFragmentManager");
        } catch (Exception e) {
            android.util.Log.e("MOD_ACT", "❌ Error enviando actividad_change a activity: " + e.getMessage());
        }

        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b);
            android.util.Log.d("MOD_ACT", "✅ Enviado calendar_refresh a activity supportFragmentManager");
        } catch (Exception e) {
            android.util.Log.e("MOD_ACT", "❌ Error enviando calendar_refresh a activity: " + e.getMessage());
        }

        // 🔥 NUEVO: También enviar al childFragmentManager del parent (para que CalendarFragment lo reciba)
        try {
            // ActivityDetailBottomSheet es el parent de ModificarActividadSheet
            // CalendarFragment escucha en su childFragmentManager
            // Necesitamos llegar al grandparent (CalendarFragment)
            Fragment parentFragment = getParentFragment();
            if (parentFragment != null) {
                Fragment grandparentFragment = parentFragment.getParentFragment();
                if (grandparentFragment != null) {
                    grandparentFragment.getChildFragmentManager().setFragmentResult("calendar_refresh", b);
                    android.util.Log.d("MOD_ACT", "✅ Enviado calendar_refresh a grandparent childFragmentManager (CalendarFragment)");
                } else {
                    android.util.Log.w("MOD_ACT", "⚠️ No hay grandparent fragment");
                }
            } else {
                android.util.Log.w("MOD_ACT", "⚠️ No hay parent fragment");
            }
        } catch (Exception e) {
            android.util.Log.e("MOD_ACT", "❌ Error enviando a grandparent: " + e.getMessage());
        }
    }

    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }

    /**
     * Muestra diálogo de conflicto de horario
     */
    private void mostrarDialogoConflicto(String mensaje) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Conflicto de horario")
                .setMessage(mensaje + "\n\n¿Qué deseas hacer?")
                .setPositiveButton("Elegir otro lugar", (dialog, which) -> {
                    actLugar.requestFocus();
                    actLugar.showDropDown();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Interface para callbacks de validación de conflictos
     */
    private interface ConflictoCallback {
        void onConflictoDetectado(String mensaje);
        void onSinConflictos();
        void onError(String error);
    }

    // ==================== ARCHIVOS ADJUNTOS ====================

    /**
     * Carga archivos adjuntos desde el documento de Firestore
     */
    private void cargarArchivosAdjuntos(DocumentSnapshot doc) {
        if (llAdjuntos == null) return;

        // 1) Intentar cargar desde el campo del documento
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adjDoc = (List<Map<String, Object>>) doc.get("adjuntos");
        if (adjDoc == null || adjDoc.isEmpty()) {
            // Fallback al alias "attachments"
            //noinspection unchecked
            adjDoc = (List<Map<String, Object>>) doc.get("attachments");
        }

        if (adjDoc != null && !adjDoc.isEmpty()) {
            renderizarArchivosAdjuntos(adjDoc);
        } else {
            // 2) Fallback: cargar desde subcolecciones
            cargarAdjuntosDesdeSubcolecciones(doc.getReference());
        }
    }

    /**
     * Carga adjuntos desde subcolecciones si no están en el documento principal
     */
    private void cargarAdjuntosDesdeSubcolecciones(DocumentReference actRef) {
        // Intentar en orden: adjuntos -> attachments -> archivos
        actRef.collection("adjuntos").get()
                .addOnSuccessListener(q -> {
                    if (q != null && !q.isEmpty()) {
                        renderizarArchivosAdjuntos(mapDocsToList(q));
                    } else {
                        actRef.collection("attachments").get()
                                .addOnSuccessListener(q2 -> {
                                    if (q2 != null && !q2.isEmpty()) {
                                        renderizarArchivosAdjuntos(mapDocsToList(q2));
                                    } else {
                                        actRef.collection("archivos").get()
                                                .addOnSuccessListener(q3 -> {
                                                    if (q3 != null && !q3.isEmpty()) {
                                                        renderizarArchivosAdjuntos(mapDocsToList(q3));
                                                    } else {
                                                        mostrarMensajeSinAdjuntos();
                                                    }
                                                })
                                                .addOnFailureListener(e -> mostrarMensajeSinAdjuntos());
                                    }
                                })
                                .addOnFailureListener(e -> mostrarMensajeSinAdjuntos());
                    }
                })
                .addOnFailureListener(e -> mostrarMensajeSinAdjuntos());
    }

    /**
     * Convierte QuerySnapshot a lista de mapas
     */
    private List<Map<String, Object>> mapDocsToList(QuerySnapshot qs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DocumentSnapshot d : qs.getDocuments()) {
            Map<String, Object> m = new HashMap<>();
            String name = d.getString("name");
            if (TextUtils.isEmpty(name)) name = d.getString("nombre");
            String url = d.getString("url");
            if (!TextUtils.isEmpty(name)) m.put("name", name);
            if (!TextUtils.isEmpty(name)) m.put("nombre", name);
            if (!TextUtils.isEmpty(url)) m.put("url", url);
            list.add(m);
        }
        return list;
    }

    /**
     * Renderiza los archivos adjuntos con botón para ver/descargar/eliminar
     */
    private void renderizarArchivosAdjuntos(List<Map<String, Object>> adjuntos) {
        if (llAdjuntos == null) return;

        llAdjuntos.removeAllViews();

        if (adjuntos == null || adjuntos.isEmpty()) {
            mostrarMensajeSinAdjuntos();
            return;
        }

        //  Crear botón CON EL ESTILO ORIGINAL
        MaterialButton btnVerArchivos = new MaterialButton(requireContext());
        btnVerArchivos.setText(adjuntos.size() + " archivo(s) adjunto(s) - Ver/Descargar/Eliminar");
        btnVerArchivos.setIcon(requireContext().getDrawable(android.R.drawable.ic_menu_view));

        // ✅ SIN modificar colores - usa el estilo por defecto del tema
        btnVerArchivos.setOnClickListener(v -> {
            // Abrir ArchivosListSheetConEliminar para poder eliminar archivos
            ArchivosListSheetConEliminar sheet = ArchivosListSheetConEliminar.newInstance(
                    adjuntos,
                    "Archivos adjuntos",
                    actividadId
            );

            // Listener para recargar cuando se cierre el sheet
            sheet.setOnDismissListener(() -> {
                android.util.Log.d("ModificarActividad", "🔄 Sheet cerrado, recargando actividad...");
                precargar(); // Recargar toda la actividad para reflejar cambios
            });

            sheet.show(getParentFragmentManager(), "archivos_list_eliminar");
        });

        llAdjuntos.addView(btnVerArchivos);
    }

    /**
     * Muestra mensaje cuando no hay archivos adjuntos
     */
    private void mostrarMensajeSinAdjuntos() {
        if (llAdjuntos == null) return;

        llAdjuntos.removeAllViews();

        TextView tvSinArchivos = new TextView(requireContext());
        tvSinArchivos.setText("Sin archivos adjuntos");
        tvSinArchivos.setTextColor(0xFF6B7280);
        tvSinArchivos.setTextSize(14);
        tvSinArchivos.setPadding(0, 8, 0, 8);

        llAdjuntos.addView(tvSinArchivos);
    }

    // ==================== BENEFICIARIOS ====================

    /**
     * Carga los beneficiarios seleccionados desde el documento
     */
    private void cargarBeneficiariosDesdeDocumento(DocumentSnapshot doc) {
        beneficiariosSeleccionados.clear();
        beneficiariosSeleccionadosIds.clear();

        // Intentar cargar IDs de beneficiarios
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) doc.get("beneficiarios_ids");
        if (ids == null || ids.isEmpty()) {
            //noinspection unchecked
            ids = (List<String>) doc.get("beneficiariosIds");
        }

        // 🆕 IMPORTANTE: Buscar primero en campos actualizados (con "Nombres") antes que en campos antiguos
        @SuppressWarnings("unchecked")
        List<String> nombres = (List<String>) doc.get("beneficiariosNombres");
        if (nombres == null || nombres.isEmpty()) {
            //noinspection unchecked
            nombres = (List<String>) doc.get("beneficiarios_nombres");
        }
        if (nombres == null || nombres.isEmpty()) {
            //noinspection unchecked
            nombres = (List<String>) doc.get("beneficiarios");
        }

        // Si tenemos IDs y nombres, crear objetos Beneficiario y configurar listeners
        if (ids != null && !ids.isEmpty() && nombres != null && !nombres.isEmpty()) {
            int count = Math.min(ids.size(), nombres.size());
            for (int i = 0; i < count; i++) {
                String id = ids.get(i);
                String nombre = nombres.get(i);
                beneficiariosSeleccionadosIds.add(id);
                beneficiariosSeleccionados.add(new Beneficiario(id, nombre, null));
            }
            renderChipsBeneficiarios();
            // 🆕 Configurar listeners en tiempo real para actualizar nombres
            configurarListenersBeneficiarios(ids);
        } else if (nombres != null && !nombres.isEmpty()) {
            // Solo tenemos nombres, usarlos como ID y nombre
            for (String nombre : nombres) {
                beneficiariosSeleccionadosIds.add(nombre);
                beneficiariosSeleccionados.add(new Beneficiario(nombre, nombre, null));
            }
            renderChipsBeneficiarios();
        }
    }

    /**
     * 🆕 Configura listeners en tiempo real para cada beneficiario
     */
    private void configurarListenersBeneficiarios(List<String> ids) {
        for (int i = 0; i < ids.size(); i++) {
            String beneficiarioId = ids.get(i);
            final int index = i;

            db.collection("beneficiarios").document(beneficiarioId)
                    .addSnapshotListener((doc, error) -> {
                        if (error != null || doc == null || !doc.exists()) return;

                        String nuevoNombre = doc.getString("nombre");
                        if (nuevoNombre != null && index < beneficiariosSeleccionados.size()) {
                            Beneficiario beneficiario = beneficiariosSeleccionados.get(index);
                            if (!nuevoNombre.equals(beneficiario.getNombre())) {
                                android.util.Log.d("ModificarActividad", "📝 Beneficiario actualizado: " + beneficiario.getNombre() + " → " + nuevoNombre);
                                beneficiario.setNombre(nuevoNombre);
                                // Actualizar solo el chip correspondiente
                                actualizarChipBeneficiario(index, nuevoNombre);
                            }
                        }
                    });
        }
    }

    /**
     * 🆕 Actualiza un chip específico sin re-renderizar todos
     */
    private void actualizarChipBeneficiario(int index, String nuevoNombre) {
        if (chipsBeneficiarios == null || index >= chipsBeneficiarios.getChildCount()) return;

        View childView = chipsBeneficiarios.getChildAt(index);
        if (childView instanceof Chip) {
            Chip chip = (Chip) childView;
            chip.setText(nuevoNombre);
            android.util.Log.d("ModificarActividad", "✅ Chip actualizado en posición " + index + ": " + nuevoNombre);
        }
    }

    /**
     * Abre el selector de beneficiarios
     */
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
        sheet.show(getParentFragmentManager(), "beneficiarios_picker");
    }

    /**
     * Renderiza los chips de beneficiarios seleccionados
     */
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
}
