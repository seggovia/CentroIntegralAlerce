// ActivityDetailBottomSheet.java
package com.centroalerce.ui;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;

// ✅ NUEVO: Importar UserRole
import com.centroalerce.gestion.utils.UserRole;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ActivityDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "DetalleAdjuntos";
    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    private static final String ARG_CITA_ID = "citaId";
    private static final String ARG_USER_ROLE = "userRole"; // ✅ NUEVO

    // ✅ MODIFICADO: Agregar parámetro userRole
    public static ActivityDetailBottomSheet newInstance(String actividadId, String citaId, UserRole userRole) {
        ActivityDetailBottomSheet f = new ActivityDetailBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_ACTIVIDAD_ID, actividadId);
        b.putString(ARG_CITA_ID, citaId);
        b.putString(ARG_USER_ROLE, userRole != null ? userRole.getValue() : UserRole.VISUALIZADOR.getValue()); // ✅ NUEVO
        f.setArguments(b);
        return f;
    }

    private MaterialButton btnCompletar;

    private int resId(String name, String defType) {
        return requireContext().getResources().getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    // ---------- Views ----------
    private TextView tvNombre, tvTipoYPer;
    private Chip chFechaHora, chLugar, chEstado;
    private TextView tvTipo, tvPeriodicidad, tvCupo, tvOferente, tvSocio, tvBeneficiarios;
    private TextView tvProyecto, tvDiasAviso, tvMotivoReagendo, tvFechaReagendo, tvMotivoCancelacion, tvFechaCancelacion;

    private LinearLayout llAdjuntos;
    private MaterialButton btnModificar, btnCancelar, btnReagendar, btnAdjuntar;

    // ---------- Data ----------
    private String actividadId, citaId;
    private UserRole userRole; // ✅ NUEVO
    private FirebaseFirestore db;

    private boolean actividadCancelada = false;
    private boolean citaCancelada = false;
    private String estadoActual = "programada";
    private String actividadLugarFallback = null;

    // 👇 NUEVO: listeners para datos en vivo
    private ListenerRegistration actReg;   // escucha de actividad
    private ListenerRegistration citaReg;  // escucha de cita

    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance().collection(preferEN ? COL_EN : COL_ES).document(actividadId);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null || getDialog().getWindow() == null) return;

        // Configurar fondo blanco del bottom sheet
        try {
            View bottomSheet = getDialog().findViewById(
                    getResources().getIdentifier("design_bottom_sheet", "id", "com.google.android.material")
            );
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(android.graphics.Color.WHITE);
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo cambiar el fondo del bottom sheet", e);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        int layoutId = layout("bottomsheet_activity_detail");
        if (layoutId == 0) {
            Toast.makeText(requireContext(), "No encuentro bottomsheet_activity_detail.xml", Toast.LENGTH_LONG).show();
            return new View(requireContext());
        }
        return inf.inflate(layoutId, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);

        // 1) Args primero
        actividadId = getArg("actividadId");
        citaId      = getArg("citaId");
        String roleStr = getArg("userRole");
        if (!TextUtils.isEmpty(roleStr)) {
            try { userRole = UserRole.fromString(roleStr); } catch (Exception ignore) {}
        }
        if (userRole == null) userRole = UserRole.VISUALIZADOR;

        // 2) Views
        tvNombre        = root.findViewById(id("tvNombre"));
        tvTipoYPer      = root.findViewById(id("tvTipoYPeriodicidad"));
        chFechaHora     = root.findViewById(id("chFechaHora"));
        chLugar         = root.findViewById(id("chLugar"));
        chEstado        = root.findViewById(id("chEstado"));
        tvTipo          = root.findViewById(id("tvTipo"));
        tvPeriodicidad  = root.findViewById(id("tvPeriodicidad"));
        tvCupo          = root.findViewById(id("tvCupo"));
        tvOferente      = root.findViewById(id("tvOferente"));
        tvSocio         = root.findViewById(id("tvSocio"));
        tvBeneficiarios = root.findViewById(id("tvBeneficiarios"));
        tvProyecto      = root.findViewById(id("tvProyecto"));
        tvDiasAviso     = root.findViewById(id("tvDiasAviso"));
        llAdjuntos      = root.findViewById(id("llAdjuntos"));
        btnModificar    = root.findViewById(id("btnModificar"));
        btnCancelar     = root.findViewById(id("btnCancelar"));
        btnReagendar    = root.findViewById(id("btnReagendar"));
        btnAdjuntar     = root.findViewById(id("btnAdjuntar"));
        btnCompletar    = root.findViewById(id("btnCompletar"));
        View spacer     = root.findViewById(id("navBarSpacer"));

        // 3) Setup por rol una vez que hay views
        setupUIBasedOnRole();

        // 4) Listeners con IDs ya definidos
        Runnable emitEdit       = () -> emitActionToParent("edit", actividadId, citaId);
        Runnable emitReschedule = () -> emitActionToParent("reschedule", actividadId, citaId);
        Runnable emitAttach     = () -> emitActionToParent("attach", actividadId, citaId);
        Runnable emitCancel     = () -> emitActionToParent("cancel", actividadId, citaId);

        // ========== LISTENERS DE BOTONES - REEMPLAZAR TODA ESTA SECCIÓN ==========

        if (btnModificar != null) {
            View.OnClickListener l = v -> {
                // Validar estado antes de acción
                if (!shouldEnableButton(id("btnModificar"))) {
                    toast("No se puede modificar una actividad " + estadoActual);
                    return;
                }

                emitEdit.run();
                ModificarActividadSheet.newInstance(actividadId)
                        .show(getParentFragmentManager(), "ModificarActividadSheet");
            };
            rememberClickListener(btnModificar, l);
            btnModificar.setOnClickListener(l);
        }

        if (btnCancelar != null) {
            View.OnClickListener l = v -> {
                // Validar estado antes de acción
                if (!shouldEnableButton(id("btnCancelar"))) {
                    toast("Esta actividad ya está cancelada");
                    return;
                }

                emitCancel.run();
                CancelarActividadSheet.newInstance(actividadId, citaId)
                        .show(getParentFragmentManager(), "CancelarActividadSheet");
            };
            rememberClickListener(btnCancelar, l);
            btnCancelar.setOnClickListener(l);
        }

        if (btnReagendar != null) {
            View.OnClickListener l = v -> {
                // Validar estado antes de acción
                if (!shouldEnableButton(id("btnReagendar"))) {
                    String mensaje = "completada".equals(estadoActual) || "completed".equals(estadoActual)
                            ? "No se puede reagendar una cita completada"
                            : "No se puede reagendar una cita " + estadoActual;
                    toast(mensaje);
                    return;
                }

                emitReschedule.run();
                ReagendarActividadSheet.newInstance(actividadId, citaId)
                        .show(getParentFragmentManager(), "ReagendarActividadSheet");
            };
            rememberClickListener(btnReagendar, l);
            btnReagendar.setOnClickListener(l);
        }

        if (btnAdjuntar != null) {
            View.OnClickListener l = v -> {
                // Validar estado antes de acción
                if (!shouldEnableButton(id("btnAdjuntar"))) {
                    toast("No se pueden adjuntar archivos a una actividad cancelada");
                    return;
                }

                emitAttach.run();
                AdjuntarComunicacionSheet.newInstance(actividadId)
                        .show(getParentFragmentManager(), "AdjuntarComunicacionSheet");
            };
            rememberClickListener(btnAdjuntar, l);
            btnAdjuntar.setOnClickListener(l);
        }

        if (btnCompletar != null) {
            View.OnClickListener l = v -> {
                // Validar estado antes de acción
                if (!shouldEnableButton(id("btnCompletar"))) {
                    String mensaje;
                    if ("completada".equals(estadoActual) || "completed".equals(estadoActual)) {
                        mensaje = "Esta cita ya está completada";
                    } else if ("cancelada".equals(estadoActual) || "canceled".equals(estadoActual)) {
                        mensaje = "No se puede completar una cita cancelada";
                    } else {
                        mensaje = "No se puede completar una cita en estado: " + estadoActual;
                    }
                    toast(mensaje);
                    return;
                }

                emitActionToParent("completar", actividadId, citaId);
                completarCita(actividadId, citaId);
            };
            rememberClickListener(btnCompletar, l);
            btnCompletar.setOnClickListener(l);
        }

// NO MODIFICAR: wrapClickWithGuard ya existentes
        wrapClickWithGuard(btnModificar);
        wrapClickWithGuard(btnReagendar);
        wrapClickWithGuard(btnAdjuntar);
        wrapClickWithGuard(btnCancelar);
        wrapClickWithGuard(btnCompletar);

// ========== FIN SECCIÓN LISTENERS ==========

        // 6) Insets spacer
        ViewCompat.setOnApplyWindowInsetsListener(root, (v,insets)->{
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            if (spacer != null) {
                ViewGroup.LayoutParams lp = spacer.getLayoutParams();
                lp.height = Math.max(dp(16), bottom);
                spacer.setLayoutParams(lp);
            }
            return insets;
        });

        // 7) UI inicial y data
        restyleButtonsActive();
        setTextOrDash(tvNombre, "Nombre actividad");
        if (tvTipoYPer != null) tvTipoYPer.setText("Tipo • Periodicidad");
        if (chFechaHora != null) chFechaHora.setText("dd/MM/yyyy • HH:mm");
        if (chLugar != null) chLugar.setText("Lugar");
        if (chEstado != null) chEstado.setText("Programada");
        if (llAdjuntos != null) { llAdjuntos.removeAllViews(); addNoFilesRow(); }

        db = FirebaseFirestore.getInstance();
        subscribeActividad(actividadId);
        subscribeCita(actividadId, citaId);
        loadActividad(actividadId);
        loadCita(actividadId, citaId);
        mostrarMensajeArchivosAdjuntos();

        // Listeners de resultados (ambos managers)
        getParentFragmentManager().setFragmentResultListener("actividad_change", getViewLifecycleOwner(),
                (req,b) -> { loadActividad(actividadId); loadCita(actividadId, citaId); });
        requireActivity().getSupportFragmentManager().setFragmentResultListener("actividad_change", getViewLifecycleOwner(),
                (req,b) -> { loadActividad(actividadId); loadCita(actividadId, citaId); });
    }


    // ✅ NUEVO: Configurar la UI según el rol del usuario
    private void setupUIBasedOnRole() {
        Log.d(TAG, "🔐 Configurando UI para rol: " + userRole.getValue());

        // VISUALIZADOR: Ocultar TODOS los botones de acción
        if (userRole.isVisualizador()) {
            Log.d(TAG, "👁️ Ocultando botones para VISUALIZADOR");
            hideButton(btnModificar);
            hideButton(btnReagendar);
            hideButton(btnAdjuntar);
            hideButton(btnCancelar);
            hideButton(btnCompletar);
            return;
        }

        // USUARIO o ADMINISTRADOR: Mostrar botones según permisos
        if (!userRole.canModifyActivity()) {
            hideButton(btnModificar);
        }

        if (!userRole.canRescheduleActivity()) {
            hideButton(btnReagendar);
        }

        if (!userRole.canAttachFiles()) {
            hideButton(btnAdjuntar);
        }

        if (!userRole.canCancelActivity()) {
            hideButton(btnCancelar);
        }

        if (!userRole.canMarkCompleted()) {
            hideButton(btnCompletar);
        }

        Log.d(TAG, "✅ UI configurada según permisos");
    }

    // ✅ NUEVO: Helper para ocultar botones
    private void hideButton(@Nullable MaterialButton button) {
        if (button != null) {
            button.setVisibility(View.GONE);
        }
    }

    private void emitActionToParent(@NonNull String action, @Nullable String activityId, @Nullable String citaId) {
        Bundle b = new Bundle();
        b.putString("action", action);
        if (!TextUtils.isEmpty(activityId)) b.putString("activityId", activityId);
        if (!TextUtils.isEmpty(citaId))     b.putString("citaId", citaId);
        getParentFragmentManager().setFragmentResult("activity_detail_action", b);
    }

    @Override
    public void onDestroyView() {
        if (actReg != null) { actReg.remove(); actReg = null; }
        if (citaReg != null) { citaReg.remove(); citaReg = null; }
        super.onDestroyView();
    }

    // ====== ESTILOS BOTONES (forzar visibilidad/contraste) ======
    private static final int COLOR_PRIMARY = 0xFF6366F1;
    private static final int COLOR_ERROR   = 0xFFDC2626;
    private static final int COLOR_TEXT    = 0xFF111827;
    private static final int COLOR_OUTLINE = 0xFF94A3B8;

    private void styleOutlined(MaterialButton b, int color){
        if (b == null) return;
        b.setStrokeColor(ColorStateList.valueOf(color));
        b.setStrokeWidth(dp(1));
        b.setTextColor(color);
        b.setRippleColor(ColorStateList.valueOf((color & 0x00FFFFFF) | 0x33000000));
        b.setIconTint(ColorStateList.valueOf(color));
        b.setBackgroundTintList(ColorStateList.valueOf(0x00000000));
    }
    private void styleFilled(MaterialButton b, int bg){
        if (b == null) return;
        b.setBackgroundTintList(ColorStateList.valueOf(bg));
        b.setTextColor(0xFFFFFFFF);
        b.setRippleColor(ColorStateList.valueOf((bg & 0x00FFFFFF) | 0x33000000));
    }
    private void styleOutlinedDisabled(MaterialButton b){
        if (b == null) return;
        b.setEnabled(false);
        b.setAlpha(1f);
        styleOutlined(b, COLOR_OUTLINE);
    }

    private void restyleButtonsActive(){
        try {
            styleOutlined(btnModificar, COLOR_PRIMARY);
            styleOutlined(btnReagendar, COLOR_PRIMARY);
            styleFilled(btnAdjuntar,  COLOR_PRIMARY);
            styleFilled(btnCancelar,  COLOR_ERROR);
            styleFilled(btnCompletar, 0xFF10B981);
        } catch (Exception ignored) {}
    }

    private void restyleButtonsCanceled(){
        try {
            styleOutlinedDisabled(btnModificar);
            styleOutlinedDisabled(btnReagendar);
            styleOutlinedDisabled(btnAdjuntar);
            styleOutlinedDisabled(btnCompletar);
            styleFilled(btnCancelar, COLOR_ERROR);
            btnCancelar.setEnabled(true);
            btnCancelar.setAlpha(1f);
        } catch (Exception ignored) {}
    }

    // ====== FIN estilos ======

    // ---------- Escuchas en vivo ----------
    private void subscribeActividad(String actividadId) {
        if (TextUtils.isEmpty(actividadId)) return;
        actReg = act(actividadId, true).addSnapshotListener((doc, e) -> {
            if (e != null) { Log.w(TAG, "listen actividad EN error", e); return; }
            Log.d(TAG, "actividad EN snapshot recibido");
            if (doc != null && doc.exists()) {
                bindActividadDoc(doc);
            } else {
                if (actReg != null) { actReg.remove(); actReg = null; }
                actReg = act(actividadId, false).addSnapshotListener((doc2, e2) -> {
                    if (e2 != null) { Log.w(TAG, "listen actividad ES error", e2); return; }
                    Log.d(TAG, "actividad ES snapshot recibido");
                    if (doc2 != null && doc2.exists()) bindActividadDoc(doc2);
                });
            }
        });
    }

    private void subscribeCita(String actividadId, String citaId) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(citaId)) return;
        citaReg = act(actividadId, true).collection("citas").document(citaId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null) { Log.w(TAG, "listen cita EN error", e); return; }
                    Log.d(TAG, "cita EN snapshot recibido");
                    if (doc != null && doc.exists()) {
                        bindCitaDoc(doc);
                    } else {
                        if (citaReg != null) { citaReg.remove(); citaReg = null; }
                        citaReg = act(actividadId, false).collection("citas").document(citaId)
                                .addSnapshotListener((doc2, e2) -> {
                                    if (e2 != null) { Log.w(TAG, "listen cita ES error", e2); return; }
                                    Log.d(TAG, "cita ES snapshot recibido");
                                    if (doc2 != null && doc2.exists()) bindCitaDoc(doc2);
                                });
                    }
                });
    }

    // ---------- Actividad ----------
    private void loadActividad(String actividadId) {
        if (TextUtils.isEmpty(actividadId)) return;
        act(actividadId, true).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) bindActividadDoc(doc);
                    else act(actividadId, false).get()
                            .addOnSuccessListener(this::bindActividadDoc)
                            .addOnFailureListener(e -> toast("No se pudo cargar la actividad"));
                })
                .addOnFailureListener(e -> toast("No se pudo cargar la actividad"));
    }

    private void bindActividadDoc(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return;

        String nombre       = pickString(doc, "nombre", "titulo", "name");
        String tipo         = pickString(doc, "tipo", "tipoActividad", "tipo_actividad", "tipoNombre");
        String periodicidad = pickString(doc, "periodicidad", "frecuencia", "periodicidadNombre", "frecuenciaNombre");
        Long cupo           = safeLong(doc.get("cupo"));

        List<String> oferentesList = pickStringList(doc, new String[]{"oferentes", "oferente", "oferentesNombres", "oferenteNombre"});
        String oferentes = joinListOrText(oferentesList);

        String socio = pickString(doc, "socioComunitario", "socio", "socio_nombre");

        List<String> beneficiariosList = pickStringList(doc, new String[]{"beneficiarios", "beneficiario", "beneficiariosNombres"});
        if (beneficiariosList.isEmpty()) {
            String beneficiariosTexto = pickString(doc, "beneficiariosTexto");
            beneficiariosList = splitToList(beneficiariosTexto);
        }
        String beneficiarios = joinListOrText(beneficiariosList);

        String proyecto = pickString(doc, "proyectoNombre", "proyecto", "projectName");
        Long diasAviso  = safeLong(doc.get("diasAvisoPrevio"));
        if (diasAviso == null) diasAviso = safeLong(doc.get("diasAviso"));
        if (diasAviso == null) diasAviso = safeLong(doc.get("dias_aviso"));
        if (diasAviso == null) diasAviso = safeLong(doc.get("diasAvisoCancelacion"));

        actividadLugarFallback = pickString(doc, "lugarNombre", "lugar");

        setTextOrDash(tvNombre, nonEmpty(nombre, "Nombre actividad"));
        if (tvTipoYPer != null) tvTipoYPer.setText(nonEmpty(tipo, "—") + " • " + nonEmpty(periodicidad, "—"));
        setLabeled(tvTipo, "Tipo: ", nonEmpty(tipo, "—"));
        setLabeled(tvPeriodicidad, "Periodicidad: ", nonEmpty(periodicidad, "—"));
        setLabeled(tvCupo, "Cupo: ", (cupo != null && cupo >= 0) ? String.valueOf(cupo) : "—");
        setLabeled(tvOferente, "Oferente: ", nonEmpty(oferentes, "—"));
        setLabeled(tvSocio, "Socio comunitario: ", nonEmpty(socio, "—"));
        setLabeled(tvBeneficiarios, "Beneficiarios: ", nonEmpty(beneficiarios, "—"));
        setLabeled(tvProyecto, "Proyecto: ", nonEmpty(proyecto, "—"));
        setLabeled(tvDiasAviso, "Días de aviso previo: ", (diasAviso != null) ? String.valueOf(diasAviso) : "—");

        String estado = pickString(doc, "estado", "status");
        if (!TextUtils.isEmpty(estado)) {
            estadoActual = estado;
            actividadCancelada = "cancelada".equalsIgnoreCase(estado) || "canceled".equalsIgnoreCase(estado);
        }
        updateEstadoChip(estadoActual);

        if (actividadCancelada || citaCancelada) applyCanceledStateUI();
        else applyActiveStateUI();

        if (chLugar != null && !TextUtils.isEmpty(actividadLugarFallback)) {
            chLugar.setText(actividadLugarFallback);
        }
        updateButtonStates();
    }

    /**
     * Completa una cita marcándola como finalizada
     * VERSIÓN CORREGIDA - desuscribe listeners antes de cerrar
     */
    private void completarCita(String actividadId, String citaId) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(citaId)) {
            toast("Faltan datos para completar la cita");
            return;
        }

        // Validación adicional de estado
        String estado = estadoActual != null ? estadoActual.toLowerCase() : "";
        if ("cancelada".equals(estado) || "canceled".equals(estado)) {
            toast("No se puede completar una cita cancelada");
            return;
        }

        if ("completada".equals(estado) || "completed".equals(estado) || "finalizada".equals(estado)) {
            toast("Esta cita ya está completada");
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Marcar como completada")
                .setMessage("¿Confirmas que esta cita fue completada exitosamente?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sí, completar", (d, which) -> {
                    // ✅ DESUSCRIBIR LISTENERS ANTES DE ACTUALIZAR
                    if (actReg != null) {
                        actReg.remove();
                        actReg = null;
                    }
                    if (citaReg != null) {
                        citaReg.remove();
                        citaReg = null;
                    }

                    // Buscar en ambas colecciones de forma segura
                    completarCitaEnColeccion(actividadId, citaId, true, success -> {
                        if (success) {
                            toast("Cita marcada como completada ✅");
                            estadoActual = "completada"; // Actualizar estado local
                            notifyChanged();

                            // ✅ CERRAR CON DELAY para dar tiempo a la notificación
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(this::dismiss, 300);
                        } else {
                            // Intentar en la colección alternativa
                            completarCitaEnColeccion(actividadId, citaId, false, success2 -> {
                                if (success2) {
                                    toast("Cita marcada como completada ✅");
                                    estadoActual = "completada";
                                    notifyChanged();

                                    // ✅ CERRAR CON DELAY
                                    new android.os.Handler(android.os.Looper.getMainLooper())
                                            .postDelayed(this::dismiss, 300);
                                } else {
                                    toast("Error: No se pudo encontrar la cita");
                                }
                            });
                        }
                    });
                })
                .show();
    }
    private void completarCitaEnColeccion(String actividadId, String citaId, boolean preferEN,
                                          CompletarCallback callback) {
        DocumentReference citaRef = act(actividadId, preferEN)
                .collection("citas")
                .document(citaId);

        citaRef.get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        callback.onResult(false);
                        return;
                    }

                    // Validar estado antes de actualizar
                    String estadoActual = doc.getString("estado");
                    if ("completada".equalsIgnoreCase(estadoActual) ||
                            "completed".equalsIgnoreCase(estadoActual) ||
                            "finalizada".equalsIgnoreCase(estadoActual)) {
                        // Ya está completada, no hacer nada pero reportar éxito
                        callback.onResult(true);
                        return;
                    }

                    if ("cancelada".equalsIgnoreCase(estadoActual) ||
                            "canceled".equalsIgnoreCase(estadoActual)) {
                        callback.onResult(false);
                        return;
                    }

                    // Actualizar a completada
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estado", "completada");
                    updates.put("fechaModificacion", Timestamp.now());

                    citaRef.update(updates)
                            .addOnSuccessListener(u -> callback.onResult(true))
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error actualizando cita: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo cita: " + e.getMessage());
                    callback.onResult(false);
                });
    }
    private interface CompletarCallback {
        void onResult(boolean success);
    }



    private void notifyChanged(){
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        if (!TextUtils.isEmpty(citaId)) b.putString("citaId", citaId);

        getParentFragmentManager().setFragmentResult("actividad_change", b);
        getParentFragmentManager().setFragmentResult("calendar_refresh", b);

        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b);
            requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b);
        } catch (Exception ignore) {}
    }

    // ---------- Cita ----------
    private void loadCita(String actividadId, String citaId) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(citaId)) return;
        act(actividadId, true).collection("citas").document(citaId).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) bindCitaDoc(doc);
                    else act(actividadId, false).collection("citas").document(citaId).get()
                            .addOnSuccessListener(this::bindCitaDoc)
                            .addOnFailureListener(e -> toast("No se pudo cargar la cita"));
                })
                .addOnFailureListener(e -> toast("No se pudo cargar la cita"));
    }

    private void bindCitaDoc(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return;

        Timestamp ts = doc.getTimestamp("startAt");
        if (ts == null) {
            String fecha = doc.getString("fecha");
            String hora  = doc.getString("horaInicio");
            try {
                String[] hhmm = (hora != null) ? hora.split(":") : new String[]{"00","00"};
                java.time.LocalDate d = java.time.LocalDate.parse(fecha);
                java.time.LocalTime t = java.time.LocalTime.of(Integer.parseInt(hhmm[0]), Integer.parseInt(hhmm[1]));
                ZonedDateTime zdt = d.atTime(t).atZone(ZoneId.systemDefault());
                ts = new Timestamp(Date.from(zdt.toInstant()));
            } catch (Exception ignored) {}
        }

        String lugar = firstNonEmpty(doc.getString("lugarNombre"), doc.getString("lugar"));
        String tituloCita = firstNonEmpty(doc.getString("titulo"), doc.getString("nombre"));

        if (tvNombre != null) {
            CharSequence cur = tvNombre.getText();
            if (cur == null || cur.toString().trim().isEmpty() || "Nombre actividad".contentEquals(cur)) {
                if (!TextUtils.isEmpty(tituloCita)) setTextOrDash(tvNombre, tituloCita);
            }
        }

        if (ts != null && chFechaHora != null) {
            ZonedDateTime local = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(ts.toDate().getTime()), ZoneId.systemDefault());
            String fechaStr = local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String horaStr  = local.format(DateTimeFormatter.ofPattern("HH:mm"));
            chFechaHora.setText(fechaStr + " • " + horaStr);
        }

        if (chLugar != null) {
            String prefer = !TextUtils.isEmpty(actividadLugarFallback) ? actividadLugarFallback : lugar;
            if (!TextUtils.isEmpty(prefer)) chLugar.setText(prefer);
        }

        String estadoCita = firstNonEmpty(doc.getString("estado"), doc.getString("status"));
        if (!TextUtils.isEmpty(estadoCita)) {
            estadoActual = estadoCita;
            updateEstadoChip(estadoActual);
        }
        citaCancelada = "cancelada".equalsIgnoreCase(estadoCita) || "canceled".equalsIgnoreCase(estadoCita);

        if (actividadCancelada || citaCancelada) applyCanceledStateUI();
        else applyActiveStateUI();

        setLabeled(tvMotivoReagendo,    "Motivo de reagendo: ", nonEmpty(doc.getString("motivo_reagendo"), "—"));
        setLabeled(tvFechaReagendo,     "Fecha de reagendo: ",  nonEmpty(formatTs(doc.getTimestamp("fecha_reagendo")), "—"));
        setLabeled(tvMotivoCancelacion, "Motivo de cancelación: ", nonEmpty(doc.getString("motivo_cancelacion"), "—"));
        setLabeled(tvFechaCancelacion,  "Fecha de cancelación: ",  nonEmpty(formatTs(doc.getTimestamp("fecha_cancelacion")), "—"));
        updateButtonStates();
    }

    // ---------- Adjuntos ----------
    private interface Done { void run(); }
    private void loadAdjuntosAll(String actividadId, String citaId) {
        if (llAdjuntos == null) return;
        android.util.Log.d("DETAIL", "🚀 Iniciando carga de adjuntos - Actividad: " + actividadId + ", Cita: " + citaId);
        llAdjuntos.removeAllViews();
        addNoFilesRow();

        loadAdjuntosAllInCollection(actividadId, citaId, true, () ->
                loadAdjuntosAllInCollection(actividadId, citaId, false, this::showPlaceholderIfEmpty));
    }
    private void loadAdjuntosAllInCollection(String actividadId, String citaId, boolean preferEN, Done onEmpty) {
        if (!TextUtils.isEmpty(actividadId) && !TextUtils.isEmpty(citaId)) {
            act(actividadId, preferEN).collection("citas").document(citaId).get()
                    .addOnSuccessListener(doc -> {
                        boolean any = false;
                        if (doc != null && doc.exists()) {
                            Object raw = doc.get("adjuntos");
                            if (raw instanceof List) {
                                List<?> arr = (List<?>) raw;
                                if (!arr.isEmpty()) {
                                    llAdjuntos.removeAllViews();
                                    for (Object o : arr) {
                                        if (o instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> it = (Map<String, Object>) o;
                                            String nombre = firstNonEmpty(
                                                    stringOr(it.get("name"), null),
                                                    stringOr(it.get("nombre"), null));
                                            String url = stringOr(it.get("url"), null);
                                            String id = stringOr(it.get("id"), null);
                                            addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, TextUtils.isEmpty(id) ? null : id);
                                            any = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (any) return;

                        loadAdjuntosFromCitaSubcollection(actividadId, citaId, "adjuntos", preferEN, () ->
                                loadAdjuntosFromCitaSubcollection(actividadId, citaId, "archivos", preferEN, () ->
                                        loadAdjuntosFromCitaSubcollection(actividadId, citaId, "attachments", preferEN, () ->
                                                loadAdjuntosActividad(actividadId, preferEN, onEmpty))));
                    })
                    .addOnFailureListener(e -> loadAdjuntosActividad(actividadId, preferEN, onEmpty));
        } else {
            loadAdjuntosActividad(actividadId, preferEN, onEmpty);
        }
    }
    private void loadAdjuntosActividad(String actividadId, boolean preferEN, Done onEmpty) {
        if (TextUtils.isEmpty(actividadId)) { onEmpty.run(); return; }
        act(actividadId, preferEN).get()
                .addOnSuccessListener(doc -> {
                    boolean any = false;
                    if (doc != null && doc.exists()) {
                        Object raw = doc.get("adjuntos");
                        if (raw instanceof List) {
                            List<?> arr = (List<?>) raw;
                            if (!arr.isEmpty()) {
                                llAdjuntos.removeAllViews();
                                for (Object o : arr) {
                                    if (o instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> it = (Map<String, Object>) o;
                                        String nombre = firstNonEmpty(
                                                stringOr(it.get("name"), null),
                                                stringOr(it.get("nombre"), null));
                                        String url = stringOr(it.get("url"), null);
                                        String id = stringOr(it.get("id"), null);
                                        addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, TextUtils.isEmpty(id) ? null : id);
                                        any = true;
                                    }
                                }
                            }
                        }
                    }
                    if (any) return;
                    loadAdjuntosFromSubcollection(actividadId, "adjuntos", preferEN, () ->
                            loadAdjuntosFromSubcollection(actividadId, "archivos", preferEN, () ->
                                    loadAdjuntosFromSubcollection(actividadId, "attachments", preferEN, onEmpty)));
                })
                .addOnFailureListener(e -> onEmpty.run());
    }
    private void loadAdjuntosFromCitaSubcollection(String actividadId, String citaId, String sub, boolean preferEN, Done onEmpty) {
        act(actividadId, preferEN).collection("citas").document(citaId)
                .collection(sub)
                .orderBy("creadoEn", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(q -> {
                    if (q == null || q.isEmpty()) { onEmpty.run(); return; }
                    llAdjuntos.removeAllViews();
                    int added = 0;
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String nombre = firstNonEmpty(d.getString("nombre"), d.getString("name"));
                        String url    = d.getString("url");
                        String did    = d.getId();
                        addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, did);
                        added++;
                    }
                    if (added == 0) onEmpty.run();
                })
                .addOnFailureListener(e -> onEmpty.run());
    }
    private void loadAdjuntosFromSubcollection(String actividadId, String sub, boolean preferEN, Done onEmpty) {
        act(actividadId, preferEN).collection(sub)
                .orderBy("creadoEn", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(q -> {
                    android.util.Log.d("DETAIL", "📄 Query resultado para " + sub + ": " + (q != null ? q.size() : "null") + " documentos");
                    if (q == null || q.isEmpty()) { 
                        android.util.Log.d("DETAIL", "❌ Sin documentos en subcolección " + sub);
                        onEmpty.run(); 
                        return; 
                    }
                    llAdjuntos.removeAllViews();
                    int added = 0;
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String nombre = firstNonEmpty(d.getString("nombre"), d.getString("name"));
                        String url    = d.getString("url");
                        String did    = d.getId();
                        addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, did);
                        added++;
                    }
                    if (added == 0) onEmpty.run();
                })
                .addOnFailureListener(e -> onEmpty.run());
    }
    private void showPlaceholderIfEmpty() {
        if (llAdjuntos == null) return;
        if (llAdjuntos.getChildCount() == 0) addNoFilesRow();
    }

    /**
     * Muestra un mensaje informativo en amarillo sobre archivos adjuntos
     * SIEMPRE muestra solo el mensaje, sin cargar los archivos adjuntos
     */
    private void mostrarMensajeArchivosAdjuntos() {
        if (llAdjuntos == null) return;

        // Limpiar cualquier contenido previo
        llAdjuntos.removeAllViews();

        // Crear TextView con mensaje informativo en amarillo
        TextView tvMensaje = new TextView(requireContext());
        tvMensaje.setText("ℹ️ Para ver y gestionar los archivos adjuntos, utiliza el botón 'Modificar'");
        tvMensaje.setTextColor(0xFFF59E0B); // Amarillo/Ámbar (#F59E0B)
        tvMensaje.setTextSize(14);
        tvMensaje.setPadding(dp(16), dp(12), dp(16), dp(12));

        // Agregar bordes redondeados y fondo con borde
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12)); // Bordes redondeados
        shape.setColor(0xFFFEF3C7); // Fondo amarillo claro (#FEF3C7)
        shape.setStroke(dp(2), 0xFFF59E0B); // Borde amarillo (#F59E0B)
        tvMensaje.setBackground(shape);

        // Agregar margen vertical para separación
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, dp(8));
        tvMensaje.setLayoutParams(params);

        llAdjuntos.addView(tvMensaje);
    }

    // ---------- UI helpers ----------
    private void addAdjuntoRow(String nombre, @Nullable String url) { addAdjuntoRow(nombre, url, null); }
    private void addAdjuntoRow(String nombre, @Nullable String url, @Nullable String adjuntoId) {
        if (llAdjuntos == null) return;
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dp(6), 0, dp(6));

        TextView tvName = new TextView(requireContext());
        tvName.setText(nombre);
        tvName.setTextSize(14);
        tvName.setSingleLine(true);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (!TextUtils.isEmpty(url)) {
            tvName.setTextColor(0xFF1D4ED8);
            tvName.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { toast("No se pudo abrir el archivo"); }
            });
        } else {
            tvName.setTextColor(0xFF374151);
        }
        item.addView(tvName);

        if (!TextUtils.isEmpty(url)) {
            LinearLayout actions = new LinearLayout(requireContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);

            TextView btnVer = new TextView(requireContext());
            btnVer.setText("Ver");
            btnVer.setTextColor(0xFF1D4ED8);
            btnVer.setPadding(0, dp(4), dp(16), 0);
            btnVer.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { toast("No se pudo abrir el archivo"); }
            });

            TextView btnDesc = new TextView(requireContext());
            btnDesc.setText("Descargar");
            btnDesc.setTextColor(0xFF1D4ED8);
            btnDesc.setPadding(0, dp(4), 0, 0);
            final String nombreFinal = (nombre == null || nombre.trim().isEmpty()) ? nombreDesdeUrl(url) : nombre;
            btnDesc.setOnClickListener(v -> descargarConDownloadManager(nombreFinal, url));

            actions.addView(btnVer);
            actions.addView(btnDesc);
            item.addView(actions);
        }

        if (!TextUtils.isEmpty(adjuntoId)) {
            TextView tvId = new TextView(requireContext());
            tvId.setText("ID: " + adjuntoId);
            tvId.setTextSize(12);
            tvId.setTextColor(0xFF6B7280);
            tvId.setTypeface(android.graphics.Typeface.MONOSPACE);
            item.addView(tvId);
        }

        llAdjuntos.addView(item);

        View sep = new View(requireContext());
        sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        sep.setBackgroundColor(0xFFE5E7EB);
        llAdjuntos.addView(sep);

        Log.d(TAG, "Adjunto agregado: " + nombre + " | url=" + url + (adjuntoId != null ? " | id=" + adjuntoId : ""));
    }

    private void addNoFilesRow() {
        if (llAdjuntos == null) return;
        TextView tv = new TextView(requireContext());
        tv.setText("Sin archivos adjuntos");
        tv.setTextSize(14);
        tv.setTextColor(0xFF6B7280);
        tv.setPadding(0, dp(6), 0, dp(6));
        llAdjuntos.addView(tv);
    }

    // ---------- Estado / estilos ----------
    private void updateEstadoChip(@Nullable String estadoRaw) {
        if (chEstado == null) return;
        String e = (estadoRaw == null) ? "" : estadoRaw.toLowerCase();
        int bg, fg = 0xFFFFFFFF;
        String text;
        switch (e) {
            case "cancelada":
            case "canceled":
                bg = 0xFFDC2626; text = "Cancelada"; break;
            case "reagendada":
            case "rescheduled":
                bg = 0xFFF59E0B; text = "Reagendada"; break;
            case "finalizada":
            case "completada":
            case "completed":
                bg = 0xFF10B981; text = "Completada"; break;
            default:
                bg = 0xFF6366F1; text = "Programada"; break;
        }
        chEstado.setText(text);
        try { chEstado.setChipBackgroundColor(ColorStateList.valueOf(bg)); } catch (Exception ignored) {}
        chEstado.setTextColor(fg);
    }

    private void applyCanceledStateUI() {
        if (tvNombre != null) {
            tvNombre.setTextColor(0xFFB91C1C);
            CharSequence cur = tvNombre.getText();
            String s = (cur == null) ? "" : cur.toString();
            if (!s.toUpperCase().contains("CANCELADA")) tvNombre.setText(s + "  (CANCELADA)");
        }
        if (chFechaHora != null) {
            chFechaHora.setTextColor(0xFFFFFFFF);
            try { chFechaHora.setChipBackgroundColor(ColorStateList.valueOf(0xFFDC2626)); } catch (Exception ignored) {}
        }
        if (chLugar != null) {
            chLugar.setTextColor(0xFFFFFFFF);
            try { chLugar.setChipBackgroundColor(ColorStateList.valueOf(0xFFEF4444)); } catch (Exception ignored) {}
        }
        restyleButtonsCanceled();
    }

    private void applyActiveStateUI() {
        if (tvNombre != null) tvNombre.setTextColor(0xFFFFFFFF);
        if (chFechaHora != null) { try { chFechaHora.setChipBackgroundColor(null); chFechaHora.setTextColor(0xFF000000); } catch (Exception ignored) {} }
        if (chLugar != null) { try { chLugar.setChipBackgroundColor(null); chLugar.setTextColor(0xFF000000); } catch (Exception ignored) {} }
        restyleButtonsActive();
        enableButton(btnModificar);
        enableButton(btnReagendar);
        enableButton(btnAdjuntar);
        btnCancelar.setEnabled(true);
        btnCancelar.setAlpha(1f);
    }

    private void disableButton(@Nullable View b){
        if (b == null) return;
        b.setEnabled(false);
        b.setClickable(false);
    }
    private void enableButton(@Nullable View b){
        if (b == null) return;
        b.setEnabled(true);
        b.setClickable(true);
        b.setAlpha(1f);
    }

    private void wrapClickWithGuard(@Nullable View b){
        if (b == null) return;
        View.OnClickListener original = getExistingClickListener(b);
        b.setOnClickListener(v -> {
            if (actividadCancelada || citaCancelada) {
                toast("Actividad/cita cancelada: no es posible editar");
            } else if (original != null) {
                original.onClick(v);
            }
        });
    }
    private void rememberClickListener(View b, View.OnClickListener l){ b.setTag(b.getId(), l); }
    @Nullable private View.OnClickListener getExistingClickListener(View b){
        Object t = b.getTag(b.getId());
        return (t instanceof View.OnClickListener) ? (View.OnClickListener) t : null;
    }

    // ---------- utils ----------
    private void setTextOrDash(@Nullable TextView tv, @NonNull String valueOrDash) { if (tv == null) return; tv.setText(valueOrDash); tv.setVisibility(View.VISIBLE); }
    private void setLabeled(@Nullable TextView tv, String prefix, String value) { if (tv == null) return; tv.setText(prefix + value); tv.setVisibility(View.VISIBLE); }
    private String nonEmpty(String v, String def){ return (v == null || v.trim().isEmpty()) ? def : v; }
    private @Nullable String pickString(DocumentSnapshot doc, String... keys) { for (String k : keys) { String v = doc.getString(k); if (v != null && !v.trim().isEmpty()) return v; } return null; }
    private List<String> pickStringList(DocumentSnapshot doc, String[] keys) {
        for (String k : keys) {
            Object raw = doc.get(k);
            List<String> parsed = parseStringList(raw);
            if (!parsed.isEmpty()) return parsed;
        }
        return Collections.emptyList();
    }
    private List<String> parseStringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o != null) {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
        } else {
            String s = String.valueOf(raw).trim();
            if (!s.isEmpty()) {
                String[] tokens = s.split("[,;|\\n]+");
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (String t : tokens) {
                    String st = (t == null) ? "" : t.trim();
                    if (!st.isEmpty()) set.add(st);
                }
                out.addAll(set);
            }
        }
        return out;
    }
    private String joinListOrText(List<String> xs) { return (xs == null || xs.isEmpty()) ? "" : TextUtils.join(", ", xs); }
    private String stringOr(Object v, String def) { if (v == null) return def; String s = String.valueOf(v).trim(); return s.isEmpty() ? def : s; }
    private List<String> splitToList(String text) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] tokens = text.split("[,;|\\n]+");
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : tokens) {
            String s = (t == null) ? "" : t.trim();
            if (!s.isEmpty()) set.add(s);
        }
        out.addAll(set);
        return out;
    }

    private String firstNonEmpty(String... xs) {
        if (xs == null) return null;
        for (String s : xs) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }
    private String formatTs(@Nullable Timestamp ts){
        if (ts == null) return null;
        ZonedDateTime z = ZonedDateTime.ofInstant(ts.toDate().toInstant(), ZoneId.systemDefault());
        return z.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
    private void descargarConDownloadManager(String nombreArchivo, String url) {
        try {
            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (TextUtils.isEmpty(nombreArchivo)) nombreArchivo = nombreDesdeUrl(url);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombreArchivo);
            long enqId = dm.enqueue(req);
            toast("Descarga iniciada…");
            Log.d(TAG, "Descarga encolada id=" + enqId + " | archivo=" + nombreArchivo);
        } catch (Exception e) {
            toast("No se pudo iniciar la descarga");
            Log.e(TAG, "Fallo al iniciar descarga: " + e.getMessage(), e);
        }
    }
    private String nombreDesdeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "archivo";
        int q = url.indexOf('?');
        String clean = q >= 0 ? url.substring(0, q) : url;
        int idx = clean.lastIndexOf('/');
        return idx >= 0 ? clean.substring(idx + 1) : clean;
    }
    // ========== NUEVOS MÉTODOS PARA CONTROL DE BOTONES ==========

    /**
     * Determina si un botón debe estar habilitado según el estado actual
     * @param buttonId ID del recurso del botón
     * @return true si el botón debe estar habilitado
     */
    private boolean shouldEnableButton(int buttonId) {
        String estado = estadoActual != null ? estadoActual.toLowerCase() : "programada";

        int btnModificarId = id("btnModificar");
        int btnReagendarId = id("btnReagendar");
        int btnAdjuntarId = id("btnAdjuntar");
        int btnCancelarId = id("btnCancelar");
        int btnCompletarId = id("btnCompletar");

        // ✅ ESTADOS FINALES: completada, cancelada
        boolean esEstadoFinal =
                "completada".equals(estado) ||
                        "completed".equals(estado) ||
                        "finalizada".equals(estado) ||
                        "cancelada".equals(estado) ||
                        "canceled".equals(estado);

        // Botón MODIFICAR
        if (buttonId == btnModificarId) {
            // ✅ MODIFICAR solo permitido en estados NO finales
            // (Permite editar datos mientras la cita está activa)
            return !esEstadoFinal;
        }

        // Botón REAGENDAR
        if (buttonId == btnReagendarId) {
            // ❌ NO se puede reagendar citas completadas o canceladas
            return !esEstadoFinal;
        }

        // Botón ADJUNTAR
        if (buttonId == btnAdjuntarId) {
            // ❌ NO se pueden adjuntar archivos a citas completadas o canceladas
            return !esEstadoFinal;
        }

        // Botón CANCELAR
        if (buttonId == btnCancelarId) {
            // ❌ Solo se puede cancelar si NO está cancelada ni completada
            return !("cancelada".equals(estado) ||
                    "canceled".equals(estado) ||
                    "completada".equals(estado) ||
                    "completed".equals(estado) ||
                    "finalizada".equals(estado));
        }

        // Botón COMPLETAR
        if (buttonId == btnCompletarId) {
            // ✅ Solo se puede completar citas programadas o reagendadas
            return ("programada".equals(estado) ||
                    "scheduled".equals(estado) ||
                    "reagendada".equals(estado) ||
                    "rescheduled".equals(estado));
        }

        // Por defecto, deshabilitar
        return false;
    }


    /**
     * Actualiza el estado de todos los botones según el estado actual
     * Debe llamarse cada vez que cambie el estado de la actividad/cita
     */
    private void updateButtonStates() {
        if (btnModificar != null) {
            boolean enabled = shouldEnableButton(id("btnModificar"));
            btnModificar.setEnabled(enabled);
            btnModificar.setAlpha(enabled ? 1f : 0.5f);
        }

        if (btnReagendar != null) {
            boolean enabled = shouldEnableButton(id("btnReagendar"));
            btnReagendar.setEnabled(enabled);
            btnReagendar.setAlpha(enabled ? 1f : 0.5f);
        }

        if (btnAdjuntar != null) {
            boolean enabled = shouldEnableButton(id("btnAdjuntar"));
            btnAdjuntar.setEnabled(enabled);
            btnAdjuntar.setAlpha(enabled ? 1f : 0.5f);
        }

        if (btnCancelar != null) {
            boolean enabled = shouldEnableButton(id("btnCancelar"));
            btnCancelar.setEnabled(enabled);
            btnCancelar.setAlpha(enabled ? 1f : 0.5f);
        }

        if (btnCompletar != null) {
            boolean enabled = shouldEnableButton(id("btnCompletar"));
            btnCompletar.setEnabled(enabled);
            btnCompletar.setAlpha(enabled ? 1f : 0.5f);
        }
    }


    private String getArg(String key) { return (getArguments() != null) ? getArguments().getString(key, "") : ""; }
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
    private Long safeLong(Object v) { try { if (v instanceof Number) return ((Number) v).longValue(); } catch (Exception ignored) {} return null; }
}