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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// 👇 NUEVO: para aplicar insets de la barra de navegación
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.centroalerce.gestion.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.Source;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton; // 👈 ya lo tenías
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
// 👇 NUEVO: para escuchar updates en vivo
import com.google.firebase.firestore.ListenerRegistration;

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

    public static ActivityDetailBottomSheet newInstance(String actividadId, String citaId) {
        ActivityDetailBottomSheet f = new ActivityDetailBottomSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        b.putString("citaId", citaId);
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
    // 👇 NUEVO: tipar como MaterialButton para estilarlos correctamente
    private MaterialButton btnModificar, btnCancelar, btnReagendar, btnAdjuntar;

    // ---------- Data ----------
    private String actividadId, citaId;
    private FirebaseFirestore db;

    private boolean actividadCancelada = false;
    private boolean citaCancelada = false;
    private String estadoActual = "programada";
    private String actividadLugarFallback = null;

    // 👇 NUEVO: listeners para datos en vivo
    private ListenerRegistration actReg;   // escucha de actividad
    private ListenerRegistration citaReg;  // escucha de cita
    private final List<Map<String, Object>> adjuntosCargados = new ArrayList<>();
    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance().collection(preferEN ? COL_EN : COL_ES).document(actividadId);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackgroundColor(android.graphics.Color.WHITE);
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

        actividadId = getArg("actividadId");
        citaId = getArg("citaId");

        tvNombre = root.findViewById(id("tvNombre"));
        tvTipoYPer = root.findViewById(id("tvTipoYPeriodicidad"));
        chFechaHora = root.findViewById(id("chFechaHora"));
        chLugar = root.findViewById(id("chLugar"));
        chEstado = root.findViewById(id("chEstado"));

        tvTipo = root.findViewById(id("tvTipo"));
        tvPeriodicidad = root.findViewById(id("tvPeriodicidad"));
        tvCupo = root.findViewById(id("tvCupo"));
        tvOferente = root.findViewById(id("tvOferente"));
        tvSocio = root.findViewById(id("tvSocio"));
        tvBeneficiarios = root.findViewById(id("tvBeneficiarios"));

        tvProyecto = root.findViewById(id("tvProyecto"));
        tvDiasAviso = root.findViewById(id("tvDiasAviso"));

        llAdjuntos = root.findViewById(id("llAdjuntos"));
        btnModificar = root.findViewById(id("btnModificar"));
        btnCancelar = root.findViewById(id("btnCancelar"));
        btnReagendar = root.findViewById(id("btnReagendar"));
        btnAdjuntar = root.findViewById(id("btnAdjuntar"));
        btnCompletar = root.findViewById(id("btnCompletar"));

        // Reservar espacio para navbar
        View spacer = root.findViewById(id("navBarSpacer"));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            if (spacer != null) {
                ViewGroup.LayoutParams lp = spacer.getLayoutParams();
                lp.height = Math.max(dp(16), bottom);
                spacer.setLayoutParams(lp);
            }
            return insets;
        });

        // ✅ BOTÓN ADJUNTAR - CORREGIDO
        if (btnAdjuntar != null) {
            View.OnClickListener listenerAdjuntar = v -> {
                if (actividadCancelada || citaCancelada) {
                    toast("No se pueden agregar archivos a una actividad cancelada");
                    return;
                }
                emitActionToParent("attach", actividadId, citaId);
                AdjuntarComunicacionSheet.newInstance(actividadId)
                        .show(getParentFragmentManager(), "AdjuntarComunicacionSheet");
            };
            rememberClickListener(btnAdjuntar, listenerAdjuntar);
            btnAdjuntar.setOnClickListener(listenerAdjuntar);
        }

        // Resto de botones (sin cambios)
        if (btnModificar != null) {
            View.OnClickListener l = v -> {
                emitActionToParent("edit", actividadId, citaId);
                ModificarActividadSheet.newInstance(actividadId)
                        .show(getParentFragmentManager(), "ModificarActividadSheet");
            };
            rememberClickListener(btnModificar, l);
            btnModificar.setOnClickListener(l);
        }

        if (btnCancelar != null) {
            View.OnClickListener l = v -> {
                emitActionToParent("cancel", actividadId, citaId);
                CancelarActividadSheet.newInstance(actividadId, citaId)
                        .show(getParentFragmentManager(), "CancelarActividadSheet");
            };
            rememberClickListener(btnCancelar, l);
            btnCancelar.setOnClickListener(l);
        }

        if (btnReagendar != null) {
            View.OnClickListener l = v -> {
                emitActionToParent("reschedule", actividadId, citaId);
                ReagendarActividadSheet.newInstance(actividadId, citaId)
                        .show(getParentFragmentManager(), "ReagendarActividadSheet");
            };
            rememberClickListener(btnReagendar, l);
            btnReagendar.setOnClickListener(l);
        }

        if (btnCompletar != null) {
            View.OnClickListener l = view -> {
                emitActionToParent("completar", actividadId, citaId);
                completarCita(actividadId, citaId);
            };
            rememberClickListener(btnCompletar, l);
            btnCompletar.setOnClickListener(l);
        }

        restyleButtonsActive();

        // Listeners de resultados
        getParentFragmentManager().setFragmentResultListener(
                "adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("ADJUNTOS", "🔄 Recibido evento adjuntos_change");
                    loadAdjuntosAll(actividadId, citaId);
                }
        );

        getParentFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("ADJUNTOS", "🔄 Recibido evento actividad_change");
                    loadActividad(actividadId);
                    loadCita(actividadId, citaId);
                    loadAdjuntosAll(actividadId, citaId);
                }
        );

        // También en Activity FragmentManager
        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "adjuntos_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("ADJUNTOS", "🔄 Recibido evento adjuntos_change (Activity)");
                    loadAdjuntosAll(actividadId, citaId);
                }
        );

        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    android.util.Log.d("ADJUNTOS", "🔄 Recibido evento actividad_change (Activity)");
                    loadActividad(actividadId);
                    loadCita(actividadId, citaId);
                    loadAdjuntosAll(actividadId, citaId);
                }
        );

        setTextOrDash(tvNombre, "Nombre actividad");
        if (tvTipoYPer != null) tvTipoYPer.setText("Tipo • Periodicidad");
        if (chFechaHora != null) chFechaHora.setText("dd/MM/yyyy • HH:mm");
        if (chLugar != null) chLugar.setText("Lugar");
        if (chEstado != null) chEstado.setText("Programada");
        if (llAdjuntos != null) {
            llAdjuntos.removeAllViews();
            addNoFilesRow();
        }

        db = FirebaseFirestore.getInstance();

        subscribeActividad(actividadId);
        subscribeCita(actividadId, citaId);
        loadActividad(actividadId);
        loadCita(actividadId, citaId);
        loadAdjuntosAll(actividadId, citaId);
    }

    // === NUEVO helper privado dentro de la clase ===
    private void emitActionToParent(@NonNull String action, @Nullable String activityId, @Nullable String citaId) {
        Bundle b = new Bundle();
        b.putString("action", action);
        if (!TextUtils.isEmpty(activityId)) b.putString("activityId", activityId);
        if (!TextUtils.isEmpty(citaId))     b.putString("citaId", citaId);
        getParentFragmentManager().setFragmentResult("activity_detail_action", b);
    }

    // 👇 NUEVO: liberar listeners para evitar fugas
    @Override
    public void onDestroyView() {
        if (actReg != null) { actReg.remove(); actReg = null; }
        if (citaReg != null) { citaReg.remove(); citaReg = null; }
        super.onDestroyView();
    }

    // ====== ESTILOS BOTONES (forzar visibilidad/contraste) ======
    private static final int COLOR_PRIMARY = 0xFF6366F1; // Indigo-500 aprox
    private static final int COLOR_ERROR   = 0xFFDC2626; // Red-600
    private static final int COLOR_TEXT    = 0xFF111827; // Gray-900
    private static final int COLOR_OUTLINE = 0xFF94A3B8; // Slate-400 para deshabilitado

    private void styleOutlined(MaterialButton b, int color){
        if (b == null) return;
        b.setStrokeColor(ColorStateList.valueOf(color));
        b.setStrokeWidth(dp(1));
        b.setTextColor(color);
        b.setRippleColor(ColorStateList.valueOf((color & 0x00FFFFFF) | 0x33000000));
        b.setIconTint(ColorStateList.valueOf(color));
        b.setBackgroundTintList(ColorStateList.valueOf(0x00000000)); // transparente
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
        b.setAlpha(1f); // mantenemos 1f para que se "vea"; contrastamos con gris
        styleOutlined(b, COLOR_OUTLINE);
    }

    private void restyleButtonsActive(){
        try {
            styleOutlined(btnModificar, COLOR_PRIMARY);
            styleOutlined(btnReagendar, COLOR_PRIMARY);
            styleFilled(btnAdjuntar,  COLOR_PRIMARY);
            styleFilled(btnCancelar,  COLOR_ERROR);
            styleFilled(btnCompletar, 0xFF10B981); // Verde para completar
        } catch (Exception ignored) {}
    }

    private void restyleButtonsCanceled(){
        try {
            styleOutlinedDisabled(btnModificar);
            styleOutlinedDisabled(btnReagendar);
            styleOutlinedDisabled(btnAdjuntar);
            styleOutlinedDisabled(btnCompletar); // Deshabilitar también
            styleFilled(btnCancelar, COLOR_ERROR);
            btnCancelar.setEnabled(true);
            btnCancelar.setAlpha(1f);
        } catch (Exception ignored) {}
    }


    // ====== FIN estilos ======

    // ---------- Escuchas en vivo (NUEVO) ----------
    private void subscribeActividad(String actividadId) { // 👈 NUEVO
        if (TextUtils.isEmpty(actividadId)) return;
        // intentamos EN, si no existe caemos a ES
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

    // ========== ActivityDetailBottomSheet.java - Método subscribeCita (COMPLETO CORREGIDO) ==========

    private void subscribeCita(String actividadId, String citaId) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(citaId)) return;

        // 👇 CRÍTICO: Forzar lectura del SERVER primero, luego escuchar cambios
        DocumentReference citaRefEN = act(actividadId, true).collection("citas").document(citaId);
        DocumentReference citaRefES = act(actividadId, false).collection("citas").document(citaId);

        // Intentar EN primero
        citaRefEN.get(com.google.firebase.firestore.Source.SERVER) // 👈 FORZAR SERVER
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        bindCitaDoc(doc);
                        // Ahora sí, escuchar cambios en tiempo real
                        citaReg = citaRefEN.addSnapshotListener((doc2, e) -> {
                            if (e != null) {
                                Log.w(TAG, "listen cita EN error", e);
                                return;
                            }
                            Log.d(TAG, "cita EN snapshot recibido");
                            if (doc2 != null && doc2.exists()) bindCitaDoc(doc2);
                        });
                    } else {
                        // Probar ES
                        citaRefES.get(com.google.firebase.firestore.Source.SERVER) // 👈 FORZAR SERVER
                                .addOnSuccessListener(docES -> {
                                    if (docES != null && docES.exists()) {
                                        bindCitaDoc(docES);
                                        // Escuchar cambios
                                        citaReg = citaRefES.addSnapshotListener((doc2, e2) -> {
                                            if (e2 != null) {
                                                Log.w(TAG, "listen cita ES error", e2);
                                                return;
                                            }
                                            Log.d(TAG, "cita ES snapshot recibido");
                                            if (doc2 != null && doc2.exists()) bindCitaDoc(doc2);
                                        });
                                    }
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Error cargando cita ES", e));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando cita EN", e);
                    // Intentar ES como fallback
                    citaRefES.get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(docES -> {
                                if (docES != null && docES.exists()) bindCitaDoc(docES);
                            });
                });
    }
    // ---------- Fin escuchas en vivo ----------

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

            try {
                // 🔹 Resetear el estilo completamente
                chLugar.setChipBackgroundColorResource(0); // Limpiar recurso
                chLugar.setBackgroundColor(0xFFD1FAE5); // Fondo directo
                chLugar.setChipBackgroundColor(ColorStateList.valueOf(0xFFD1FAE5));

                // 🔹 Colores
                chLugar.setTextColor(0xFF065F46);
                chLugar.setChipIconTint(ColorStateList.valueOf(0xFF059669));
                chLugar.setChipStrokeColor(ColorStateList.valueOf(0xFF6EE7B7));
                chLugar.setChipStrokeWidth(dp(1));

                // 🔹 Asegurar que no haya tema sobrescribiendo
                chLugar.setEnsureMinTouchTargetSize(false);
            } catch (Exception ignored) {}
        }
    }

// ========== MÉTODO COMPLETO: completarCita (SIN FieldValue) ==========

    private void completarCita(String actividadId, String citaId) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(citaId)) {
            toast("Faltan datos para completar la cita");
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Marcar como completada")
                .setMessage("¿Confirmas que esta cita fue completada exitosamente?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sí, completar", (d, which) -> {
                    DocumentReference citaES = act(actividadId, false).collection("citas").document(citaId);
                    DocumentReference citaEN = act(actividadId, true).collection("citas").document(citaId);

                    citaES.get().addOnSuccessListener(doc -> {
                        DocumentReference ref = (doc != null && doc.exists()) ? citaES : citaEN;

                        // 👇 LOG: Ver qué colección estamos usando
                        android.util.Log.d("CAL", "🔧 Actualizando en: " + ref.getPath());

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("estado", "completada");
                        updates.put("fechaModificacion", com.google.firebase.Timestamp.now());
                        updates.put("_lastUpdate", System.currentTimeMillis());

                        // 👇 LOG: Ver qué vamos a guardar
                        android.util.Log.d("CAL", "📝 Updates a guardar: " + updates);

                        ref.update(updates)
                                .addOnSuccessListener(u -> {
                                    android.util.Log.d("CAL", "✅ Update exitoso en Firestore");

                                    // Verificar que se guardó correctamente
                                    ref.get(com.google.firebase.firestore.Source.SERVER)
                                            .addOnSuccessListener(docVerify -> {
                                                String estadoGuardado = docVerify.getString("estado");
                                                android.util.Log.d("CAL", "🔍 Verificación - estado guardado: " + estadoGuardado);
                                            });

                                    toast("Cita marcada como completada ✅");

                                    Bundle b = new Bundle();
                                    b.putBoolean("forceRefresh", true);
                                    b.putString("actividadId", actividadId);
                                    b.putString("citaId", citaId);
                                    b.putLong("timestamp", System.currentTimeMillis());

                                    getParentFragmentManager().setFragmentResult("calendar_refresh", b);
                                    getParentFragmentManager().setFragmentResult("actividad_change", b);
                                    requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b);
                                    requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b);

                                    // Refresh con delays
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        android.util.Log.d("CAL", "🔄 Refresh forzado 1/3");
                                        try {
                                            getParentFragmentManager().setFragmentResult("calendar_refresh", b);
                                            requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b);
                                        } catch (Exception ignore) {}
                                    }, 300);

                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        android.util.Log.d("CAL", "🔄 Refresh forzado 2/3");
                                        try {
                                            getParentFragmentManager().setFragmentResult("calendar_refresh", b);
                                            requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b);
                                        } catch (Exception ignore) {}
                                    }, 800);

                                    dismiss();
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("CAL", "❌ Error al guardar: " + e.getMessage());
                                    toast("Error: " + e.getMessage());
                                });
                    }).addOnFailureListener(e -> {
                        android.util.Log.e("CAL", "❌ Error al buscar documento: " + e.getMessage());
                        toast("Error: " + e.getMessage());
                    });
                })
                .show();
    }
    private void notifyChanged(){
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        if (!TextUtils.isEmpty(citaId)) b.putString("citaId", citaId);

        // Notificar al ParentFragmentManager
        getParentFragmentManager().setFragmentResult("actividad_change", b);
        getParentFragmentManager().setFragmentResult("calendar_refresh", b);

        // Notificar también al Activity (para refrescar calendario/lista)
        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b);
            requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b);
        } catch (Exception ignore) {}
    }

    // ---------- Cita ----------
    // ========== ActivityDetailBottomSheet.java - Método loadCita (COMPLETO CORREGIDO) ==========

    private void loadCita(String actividadId, String citaId) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(citaId)) return;

        // 👇 FORZAR LECTURA DEL SERVIDOR
        act(actividadId, true).collection("citas").document(citaId)
                .get(com.google.firebase.firestore.Source.SERVER) // 👈 CRÍTICO
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        bindCitaDoc(doc);
                    } else {
                        act(actividadId, false).collection("citas").document(citaId)
                                .get(com.google.firebase.firestore.Source.SERVER) // 👈 CRÍTICO
                                .addOnSuccessListener(this::bindCitaDoc)
                                .addOnFailureListener(e -> toast("No se pudo cargar la cita"));
                    }
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

            try {
                chFechaHora.setChipBackgroundColor(ColorStateList.valueOf(0xFFDBEAFE));
                chFechaHora.setTextColor(0xFF1E40AF);
                chFechaHora.setChipIconTint(ColorStateList.valueOf(0xFF3B82F6));
            } catch (Exception ignored) {}
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
    }

    // ---------- Adjuntos ----------
    private interface Done { void run(); }
    private void loadAdjuntosAll(String actividadId, String citaId) {
        if (llAdjuntos == null) return;

        android.util.Log.d("DETAIL", "🚀 loadAdjuntosAll - Mostrando mensaje para modificar");

        mostrarMensajeArchivosEnModificar();
    }

    private void mostrarMensajeArchivosEnModificar() {
        if (llAdjuntos == null) return;

        llAdjuntos.removeAllViews();

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
        card.setCardBackgroundColor(0xFFFEF3C7);

        android.widget.LinearLayout container =
                new android.widget.LinearLayout(requireContext());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));

        android.widget.TextView tvTitulo = new android.widget.TextView(requireContext());
        tvTitulo.setText("📎 Gestión de archivos");
        tvTitulo.setTextSize(16);
        tvTitulo.setTextColor(0xFF92400E);
        tvTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(tvTitulo);

        android.widget.TextView tvMensaje = new android.widget.TextView(requireContext());
        tvMensaje.setText("Para ver o eliminar archivos adjuntos, dirígete a 'Modificar Actividad'");
        tvMensaje.setTextSize(14);
        tvMensaje.setTextColor(0xFF92400E);
        tvMensaje.setPadding(0, dp(8), 0, 0);
        container.addView(tvMensaje);

        card.addView(container);
        llAdjuntos.addView(card);
    }

    private void cargarAdjuntosDeActividad(String actividadId) {
        if (TextUtils.isEmpty(actividadId)) {
            showPlaceholderIfEmpty();
            return;
        }

        android.util.Log.d("DETAIL", "🔍 Cargando adjuntos de actividad: " + actividadId);

        act(actividadId, true).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        Object rawAdj = doc.get("adjuntos");
                        android.util.Log.d("DETAIL", "📎 Campo adjuntos: " +
                                (rawAdj != null ? rawAdj.getClass().getSimpleName() : "null"));

                        if (rawAdj instanceof List && !((List<?>) rawAdj).isEmpty()) {
                            llAdjuntos.removeAllViews();

                            // ✅ CORREGIDO: Agregar adjuntos al cache
                            adjuntosCargados.clear();
                            for (Object item : (List<?>) rawAdj) {
                                if (item instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> adj = (Map<String, Object>) item;
                                    adjuntosCargados.add(adj);
                                }
                            }

                            // ✅ Mostrar según cantidad
                            if (adjuntosCargados.size() > 3) {
                                mostrarBotonVerArchivos(adjuntosCargados.size());
                            } else {
                                mostrarAdjuntosDesdeArray((List<?>) rawAdj);
                            }
                            return;
                        }
                    }

                    // Si no hay en documento, intentar subcolección
                    android.util.Log.d("DETAIL", "🔍 Intentando subcolección adjuntos...");
                    cargarDesdeSubcoleccion(actividadId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("DETAIL", "❌ Error: " + e.getMessage(), e);
                    showPlaceholderIfEmpty();
                });
    }
    private void cargarDesdeSubcoleccion(String actividadId) {
        act(actividadId, true).collection("adjuntos")
                .orderBy("creadoEn", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    android.util.Log.d("DETAIL", "📂 Subcolección: " +
                            (qs != null ? qs.size() : 0) + " documentos");

                    if (qs == null || qs.isEmpty()) {
                        showPlaceholderIfEmpty();
                        return;
                    }

                    llAdjuntos.removeAllViews();
                    adjuntosCargados.clear(); // ✅ Limpiar cache

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String nombre = firstNonEmpty(d.getString("nombre"), d.getString("name"));
                        String url = d.getString("url");
                        String id = d.getId();

                        Map<String, Object> adj = new HashMap<>();
                        adj.put("nombre", nombre);
                        adj.put("name", nombre);
                        adj.put("url", url);
                        adj.put("id", id);

                        adjuntosCargados.add(adj); // ✅ Agregar al cache
                    }

                    // ✅ Mostrar según cantidad
                    if (adjuntosCargados.size() > 3) {
                        mostrarBotonVerArchivos(adjuntosCargados.size());
                    } else {
                        for (Map<String, Object> adj : adjuntosCargados) {
                            addAdjuntoRow(
                                    nonEmpty(stringOr(adj.get("nombre"), null), "(archivo)"),
                                    stringOr(adj.get("url"), null),
                                    stringOr(adj.get("id"), null)
                            );
                        }
                    }

                    android.util.Log.d("DETAIL", "✅ Adjuntos cargados desde subcolección");
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("DETAIL", "❌ Error subcolección: " + e.getMessage(), e);
                    showPlaceholderIfEmpty();
                });
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
                    "Archivos de la actividad"
            );
            sheet.show(getParentFragmentManager(), "archivos_list");
        });

        llAdjuntos.addView(card);
    }


    private void mostrarAdjuntosDesdeArray(List<?> array) {
        for (Object item : array) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> adj = (Map<String, Object>) item;

                String nombre = firstNonEmpty(
                        stringOr(adj.get("nombre"), null),
                        stringOr(adj.get("name"), null)
                );
                String url = stringOr(adj.get("url"), null);
                String id = stringOr(adj.get("id"), null);

                addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, id);
            }
        }
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
        android.util.Log.d("DETAIL", "🔍 Cargando adjuntos para actividad: " + actividadId);
        act(actividadId, preferEN).get()
                .addOnSuccessListener(doc -> {
                    android.util.Log.d("DETAIL", "📄 Documento obtenido: " + (doc != null && doc.exists()));
                    boolean any = false;
                    if (doc != null && doc.exists()) {
                        Object raw = doc.get("adjuntos");
                        android.util.Log.d("DETAIL", "📎 Campo adjuntos: " + (raw != null ? raw.getClass().getSimpleName() : "null"));
                        if (raw instanceof List) {
                            List<?> arr = (List<?>) raw;
                            android.util.Log.d("DETAIL", "📎 Lista de adjuntos con " + arr.size() + " elementos");
                            if (!arr.isEmpty()) {
                                llAdjuntos.removeAllViews();
                                for (int i = 0; i < arr.size(); i++) {
                                    Object o = arr.get(i);
                                    if (o instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> it = (Map<String, Object>) o;
                                        String nombre = firstNonEmpty(
                                                stringOr(it.get("name"), null),
                                                stringOr(it.get("nombre"), null));
                                        String url = stringOr(it.get("url"), null);
                                        String id = stringOr(it.get("id"), null);
                                        android.util.Log.d("DETAIL", "📎 Adjunto " + (i+1) + ": " + nombre + " | URL: " + url);
                                        addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, TextUtils.isEmpty(id) ? null : id);
                                        any = true;
                                    }
                                }
                            }
                        }
                    }
                    if (any) {
                        android.util.Log.d("DETAIL", "✅ Adjuntos encontrados en documento principal");
                        return;
                    }
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
        android.util.Log.d("DETAIL", "🔍 Cargando desde subcolección: " + sub + " para actividad: " + actividadId);
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
                        android.util.Log.d("DETAIL", "📎 Adjunto desde " + sub + ": " + nombre + " | URL: " + url + " | ID: " + did);
                        addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, did);
                        added++;
                    }
                    android.util.Log.d("DETAIL", "✅ Agregados " + added + " adjuntos desde subcolección " + sub);
                    if (added == 0) onEmpty.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("DETAIL", "❌ Error cargando subcolección " + sub + ": " + e.getMessage());
                    onEmpty.run();
                });
    }
    private void showPlaceholderIfEmpty() {
        if (llAdjuntos == null) return;
        if (llAdjuntos.getChildCount() == 0) addNoFilesRow();
    }

    // ---------- UI helpers ----------
    private void addAdjuntoRow(String nombre, @Nullable String url) { addAdjuntoRow(nombre, url, null); }
    private void addAdjuntoRow(String nombre, @Nullable String url, @Nullable String adjuntoId) {
        if (llAdjuntos == null) return;

        // Inflar el layout del item
        View itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_adjunto, llAdjuntos, false);

        // Referencias a vistas
        TextView tvNombre = itemView.findViewById(R.id.tvNombreAdjunto);
        TextView tvTamanio = itemView.findViewById(R.id.tvTamanio);
        ImageView ivIcono = itemView.findViewById(R.id.ivIconoTipo);
        com.google.android.material.button.MaterialButton btnVer = itemView.findViewById(R.id.btnVerAdjunto);
        com.google.android.material.button.MaterialButton btnDescargar = itemView.findViewById(R.id.btnDescargarAdjunto);

        // Configurar nombre
        if (tvNombre != null) {
            tvNombre.setText(TextUtils.isEmpty(nombre) ? "archivo" : nombre);
        }

        // Configurar ícono según extensión
        if (ivIcono != null) {
            String ext = obtenerExtension(nombre);
            int iconoRes = obtenerIconoPorExtension(ext);
            ivIcono.setImageResource(iconoRes);
        }

        // Configurar tamaño (por ahora placeholder)
        if (tvTamanio != null) {
            String ext = obtenerExtension(nombre);
            tvTamanio.setText(ext.toUpperCase() + " • Archivo");
        }

        // Botón VER
        if (btnVer != null && !TextUtils.isEmpty(url)) {
            btnVer.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception e) {
                    toast("No se pudo abrir el archivo");
                    android.util.Log.e("ADJUNTO", "Error al abrir: " + e.getMessage(), e);
                }
            });
        } else if (btnVer != null) {
            btnVer.setEnabled(false);
            btnVer.setAlpha(0.5f);
        }


        // Botón DESCARGAR
        if (btnDescargar != null && !TextUtils.isEmpty(url)) {
            btnDescargar.setOnClickListener(v -> {
                String nombreFinal = TextUtils.isEmpty(nombre) ? nombreDesdeUrl(url) : nombre;
                descargarConDownloadManager(nombreFinal, url);
            });
        } else if (btnDescargar != null) {
            btnDescargar.setEnabled(false);
            btnDescargar.setAlpha(0.5f);
        }

        llAdjuntos.addView(itemView);

        android.util.Log.d("ADJUNTO", "✅ Adjunto agregado: " + nombre + " | URL: " + url);
    }
    private String obtenerExtension(String nombre) {
        if (TextUtils.isEmpty(nombre)) return "";
        int idx = nombre.lastIndexOf('.');
        return idx >= 0 ? nombre.substring(idx + 1).toLowerCase() : "";
    }

    private int obtenerIconoPorExtension(String ext) {
        switch (ext) {
            case "pdf":
                return android.R.drawable.ic_menu_report_image;
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
                return android.R.drawable.ic_menu_gallery;
            case "doc":
            case "docx":
                return android.R.drawable.ic_menu_edit;
            case "xls":
            case "xlsx":
                return android.R.drawable.ic_menu_sort_by_size;
            default:
                return android.R.drawable.ic_menu_save;
        }
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
        int bg, fg;
        String text;

        switch (e) {
            case "cancelada":
            case "canceled":
                bg = 0xFFDC2626; // Rojo
                fg = 0xFFFFFFFF; // Blanco
                text = "Cancelada";
                break;
            case "reagendada":
            case "rescheduled":
                bg = 0xFFF59E0B; // Naranja
                fg = 0xFFFFFFFF; // Blanco
                text = "Reagendada";
                break;
            case "finalizada":
            case "completada":
            case "completed":
                bg = 0xFF059669; // Verde éxito
                fg = 0xFFFFFFFF; // Blanco
                text = "Completada";
                break;
            default:
                bg = 0xFF5A9B82; // Verde Alerce
                fg = 0xFFFFFFFF; // Blanco
                text = "Programada";
                break;
        }

        chEstado.setText(text);
        try {
            chEstado.setChipBackgroundColor(ColorStateList.valueOf(bg));
        } catch (Exception ignored) {}
        chEstado.setTextColor(fg);

        // 👇 NUEVO: Asegurar que ícono sea visible
        try {
            chEstado.setChipIconTint(ColorStateList.valueOf(fg));
        } catch (Exception ignored) {}
    }

    private void applyCanceledStateUI() {
        // ❌ ELIMINAR: Ya NO cambiar el color del título
        // El título SIEMPRE debe estar en blanco (se define en el XML)

        // ✅ Solo agregar el texto "(CANCELADA)" si no existe
        if (tvNombre != null) {
            CharSequence cur = tvNombre.getText();
            String s = (cur == null) ? "" : cur.toString();
            if (!s.toUpperCase().contains("CANCELADA")) {
                tvNombre.setText(s + "  (CANCELADA)");
            }
            // El color se mantiene BLANCO del XML
        }

        // ✅ Chip Fecha/Hora mantiene colores AZULES normales
        if (chFechaHora != null) {
            try {
                chFechaHora.setChipBackgroundColor(ColorStateList.valueOf(0xFFDBEAFE)); // Azul claro
                chFechaHora.setTextColor(0xFF1E40AF); // Azul oscuro
                chFechaHora.setChipIconTint(ColorStateList.valueOf(0xFF3B82F6)); // Azul ícono
                chFechaHora.setChipStrokeColor(ColorStateList.valueOf(0xFF93C5FD)); // Azul borde
                chFechaHora.setChipStrokeWidth(dp(1));
            } catch (Exception ignored) {}
        }

        // ✅ Chip Lugar mantiene colores VERDES normales
        if (chLugar != null) {
            try {
                chLugar.setChipBackgroundColor(ColorStateList.valueOf(0xFFD1FAE5)); // Verde claro
                chLugar.setTextColor(0xFF065F46); // Verde oscuro
                chLugar.setChipIconTint(ColorStateList.valueOf(0xFF059669)); // Verde ícono
                chLugar.setChipStrokeColor(ColorStateList.valueOf(0xFF6EE7B7)); // Verde borde
                chLugar.setChipStrokeWidth(dp(1));
            } catch (Exception ignored) {}
        }

        // ✅ Chip Estado YA está en rojo (se maneja en updateEstadoChip())

        // Deshabilitar botones con estilos grises
        restyleButtonsCanceled();
    }

    private void applyActiveStateUI() {
        // ❌ ELIMINAR: Ya NO cambiar el color del título
        // El título SIEMPRE debe estar en blanco (del XML)

        // ✅ Chip Fecha/Hora con colores normales
        if (chFechaHora != null) {
            try {
                chFechaHora.setChipBackgroundColor(ColorStateList.valueOf(0xFFDBEAFE));
                chFechaHora.setTextColor(0xFF1E40AF);
                chFechaHora.setChipIconTint(ColorStateList.valueOf(0xFF3B82F6));
                chFechaHora.setChipStrokeColor(ColorStateList.valueOf(0xFF93C5FD));
                chFechaHora.setChipStrokeWidth(dp(1));
            } catch (Exception ignored) {}
        }

        // ✅ Chip Lugar con colores normales
        if (chLugar != null) {
            try {
                chLugar.setChipBackgroundColor(ColorStateList.valueOf(0xFFD1FAE5));
                chLugar.setTextColor(0xFF065F46);
                chLugar.setChipIconTint(ColorStateList.valueOf(0xFF059669));
                chLugar.setChipStrokeColor(ColorStateList.valueOf(0xFF6EE7B7));
                chLugar.setChipStrokeWidth(dp(1));
            } catch (Exception ignored) {}
        }

        // Reestiliza botones ACTIVOS
        restyleButtonsActive();

        // Habilita todos los botones
        enableButton(btnModificar);
        enableButton(btnReagendar);
        enableButton(btnAdjuntar);
        if (btnCompletar != null) enableButton(btnCompletar);
        if (btnCancelar != null) {
            btnCancelar.setEnabled(true);
            btnCancelar.setAlpha(1f);
        }
    }

    private void disableButton(@Nullable View b){
        if (b == null) return;
        b.setEnabled(false);
        b.setClickable(false);
        // alpha lo maneja restyleButtonsCanceled()
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
            if (!s.isEmpty()) set.add(s);   // 👈 aquí era "st" por error
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
    private String getArg(String key) { return (getArguments() != null) ? getArguments().getString(key, "") : ""; }
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
    private Long safeLong(Object v) { try { if (v instanceof Number) return ((Number) v).longValue(); } catch (Exception ignored) {} return null; }
}