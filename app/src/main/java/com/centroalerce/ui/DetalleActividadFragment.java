package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.DateFormat;
import java.util.Date;

public class DetalleActividadFragment extends Fragment {

    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    private static final String TAG = "DetalleActividad";

    public static DetalleActividadFragment newInstance(@NonNull String actividadId){
        Bundle b = new Bundle(); b.putString(ARG_ACTIVIDAD_ID, actividadId);
        DetalleActividadFragment f = new DetalleActividadFragment(); f.setArguments(b); return f;
    }

    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String id, boolean en){ return FirebaseFirestore.getInstance().collection(en?COL_EN:COL_ES).document(id); }

    private String actividadId;
    private FirebaseFirestore db;

    // 🆕 Listeners para mantenedores
    private ListenerRegistration tipoActReg;
    private ListenerRegistration lugarReg;
    private ListenerRegistration oferenteReg;
    private ListenerRegistration socioReg;
    private ListenerRegistration proyectoReg;
    private final java.util.Map<String, ListenerRegistration> beneficiariosRegs = new java.util.HashMap<>();

    // 🆕 IDs de mantenedores para listeners
    private String tipoActId;
    private String lugarId;
    private String oferenteId;
    private String socioId;
    private String proyectoId;
    private java.util.List<String> beneficiariosIds = new java.util.ArrayList<>();

    // Views
    private TextView tvNombre, tvTipoYPeriodicidad;
    private Chip chEstado, chFechaHora, chLugar;
    private TextView tvTipo, tvPeriodicidad, tvCupo, tvOferente, tvSocio, tvBeneficiarios, tvProyecto, tvDiasAviso;
    private TextView tvMotivoReagendo, tvFechaReagendo, tvMotivoCancelacion, tvFechaCancelacion;
    private LinearLayout llAdjuntos;
    private MaterialButton btnModificar, btnReagendar, btnAdjuntar, btnCancelar;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.fragment_detalle_actividad, c, false); // usa tu layout id real
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        actividadId = getArguments()!=null ? getArguments().getString(ARG_ACTIVIDAD_ID) : null;
        db = FirebaseFirestore.getInstance();

        // Bind
        tvNombre = v.findViewById(R.id.tvNombre);
        tvTipoYPeriodicidad = v.findViewById(R.id.tvTipoYPeriodicidad);
        chEstado = v.findViewById(R.id.chEstado);
        chFechaHora = v.findViewById(R.id.chFechaHora);
        chLugar = v.findViewById(R.id.chLugar);

        tvTipo = v.findViewById(R.id.tvTipo);
        tvPeriodicidad = v.findViewById(R.id.tvPeriodicidad);
        tvCupo = v.findViewById(R.id.tvCupo);
        tvOferente = v.findViewById(R.id.tvOferente);
        tvSocio = v.findViewById(R.id.tvSocio);
        tvBeneficiarios = v.findViewById(R.id.tvBeneficiarios);
        tvProyecto = v.findViewById(R.id.tvProyecto);
        tvDiasAviso = v.findViewById(R.id.tvDiasAviso);
        tvMotivoReagendo = v.findViewById(R.id.tvMotivoReagendo);
        tvFechaReagendo = v.findViewById(R.id.tvFechaReagendo);
        tvMotivoCancelacion = v.findViewById(R.id.tvMotivoCancelacion);
        tvFechaCancelacion = v.findViewById(R.id.tvFechaCancelacion);
        llAdjuntos = v.findViewById(R.id.llAdjuntos);

        btnModificar = v.findViewById(R.id.btnModificar);
        btnReagendar = v.findViewById(R.id.btnReagendar);
        btnAdjuntar = v.findViewById(R.id.btnAdjuntar);
        btnCancelar = v.findViewById(R.id.btnCancelar);

        // Abrir hoja de modificación
        btnModificar.setOnClickListener(x -> {
            if (actividadId == null) return;
            ModificarActividadSheet.newInstance(actividadId)
                    .show(getParentFragmentManager(), "modificar_actividad");
        });

        // Escuchar cambios desde la hoja (mismo FM)…
        getParentFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> recargarDetalleDesdeFirestore()
        );
        // …y también si el evento viene por el SupportFragmentManager de la Activity
        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> recargarDetalleDesdeFirestore()
        );

        recargarDetalleDesdeFirestore();
    }

    @Override
    public void onDestroyView() {
        // 🆕 Limpiar listeners de mantenedores
        removeMantenedorListeners();
        super.onDestroyView();
    }

    // 🆕 Método para remover listeners de mantenedores
    private void removeMantenedorListeners() {
        if (tipoActReg != null) {
            try { tipoActReg.remove(); } catch (Exception e) { android.util.Log.w(TAG, "Error removiendo tipoActReg", e); }
            tipoActReg = null;
        }
        if (lugarReg != null) {
            try { lugarReg.remove(); } catch (Exception e) { android.util.Log.w(TAG, "Error removiendo lugarReg", e); }
            lugarReg = null;
        }
        if (oferenteReg != null) {
            try { oferenteReg.remove(); } catch (Exception e) { android.util.Log.w(TAG, "Error removiendo oferenteReg", e); }
            oferenteReg = null;
        }
        if (socioReg != null) {
            try { socioReg.remove(); } catch (Exception e) { android.util.Log.w(TAG, "Error removiendo socioReg", e); }
            socioReg = null;
        }
        if (proyectoReg != null) {
            try { proyectoReg.remove(); } catch (Exception e) { android.util.Log.w(TAG, "Error removiendo proyectoReg", e); }
            proyectoReg = null;
        }
        // 🆕 Remover listeners de beneficiarios
        for (ListenerRegistration reg : beneficiariosRegs.values()) {
            try { reg.remove(); } catch (Exception e) { android.util.Log.w(TAG, "Error removiendo listener de beneficiario", e); }
        }
        beneficiariosRegs.clear();
    }

    // ========= Carga =========
    private void recargarDetalleDesdeFirestore(){
        if (TextUtils.isEmpty(actividadId)) return;

        act(actividadId,true).get().addOnSuccessListener(dEn -> {
            if (dEn!=null && dEn.exists()) bind(dEn);
            else act(actividadId,false).get().addOnSuccessListener(this::bind);
        });
    }

    private void bind(@Nullable DocumentSnapshot doc){
        if (doc==null || !doc.exists()) return;

        // 🆕 Extraer IDs de mantenedores para configurar listeners
        String newTipoActId = firstNonEmpty(doc.getString("tipoActividad_id"), doc.getString("tipoActividadId"), doc.getString("tipo_id"), doc.getString("tipoId"));
        String newLugarId = firstNonEmpty(doc.getString("lugar_id"), doc.getString("lugarId"));
        String newOferenteId = firstNonEmpty(doc.getString("oferente_id"), doc.getString("oferenteId"));
        String newSocioId = firstNonEmpty(doc.getString("socio_id"), doc.getString("socioId"), doc.getString("socioComunitario_id"));
        String newProyectoId = firstNonEmpty(doc.getString("proyecto_id"), doc.getString("proyectoId"));

        // 🆕 Extraer IDs de beneficiarios (lista)
        java.util.List<String> newBeneficiariosIds = new java.util.ArrayList<>();
        Object beneficiariosIdsObj = doc.get("beneficiarios_ids");
        if (beneficiariosIdsObj == null) beneficiariosIdsObj = doc.get("beneficiariosIds");
        if (beneficiariosIdsObj instanceof java.util.List) {
            for (Object id : (java.util.List<?>) beneficiariosIdsObj) {
                if (id != null) newBeneficiariosIds.add(String.valueOf(id));
            }
        }

        // 🆕 Configurar listeners para mantenedores si los IDs cambiaron
        setupMantenedorListeners(newTipoActId, newLugarId, newOferenteId, newSocioId, newProyectoId, newBeneficiariosIds);

        // Campos base
        set(tvNombre, doc.getString("nombre"));

        String tipo = firstNonEmpty(doc.getString("tipoActividad"), doc.getString("tipo"));
        String periodicidad = firstNonEmpty(doc.getString("periodicidad"), doc.getString("frecuencia"));
        set(tvTipoYPeriodicidad, joinWithDot(tipo, periodicidad));
        set(tvTipo, "Tipo: " + orDash(tipo));
        set(tvPeriodicidad, "Periodicidad: " + orDash(periodicidad));

        Long cupo = doc.getLong("cupo");
        set(tvCupo, "Cupo: " + (cupo!=null? String.valueOf(cupo) : "—"));

        // Estado (si usas un enum/clave diferente, ajusta aquí)
        String estado = firstNonEmpty(doc.getString("estado"), "Programada");
        updateEstadoChip(estado);

        // Fecha/Hora (adapta a tu modelo real: timestamp, start_at, etc.)
        Date fecha = asDate(doc.get("fecha"), doc.get("fechaHora"), doc.get("start_at"));
        chFechaHora.setText(fecha!=null ? DateFormat.getDateTimeInstance().format(fecha) : "dd/MM/yyyy • HH:mm");

        // Lugar
        String lugarNombre = firstNonEmpty(doc.getString("lugarNombre"), doc.getString("lugar"));
        setChip(chLugar, lugarNombre);

        // Oferente
        String oferenteNombre = firstNonEmpty(doc.getString("oferenteNombre"), doc.getString("oferente"));
        set(tvOferente, "Oferente: " + orDash(oferenteNombre));

        // Socio
        String socioNombre = firstNonEmpty(doc.getString("socio_nombre"), doc.getString("socioComunitario"));
        set(tvSocio, "Socio comunitario: " + orDash(socioNombre));

        // Beneficiarios
        String beneficiariosTxt = firstNonEmpty(doc.getString("beneficiariosTexto"));
        set(tvBeneficiarios, "Beneficiarios: " + orDash(beneficiariosTxt));

        // Proyecto
        String proyectoNombre = firstNonEmpty(doc.getString("proyectoNombre"), doc.getString("proyecto"));
        set(tvProyecto, "Proyecto: " + orDash(proyectoNombre));

        // Días aviso
        Long diasAviso = firstNonNull(doc.getLong("diasAviso"), doc.getLong("dias_aviso"),
                doc.getLong("diasAvisoPrevio"), doc.getLong("diasAvisoCancelacion"));
        set(tvDiasAviso, "Días de aviso previo: " + (diasAviso!=null? String.valueOf(diasAviso) : "—"));

        // Reagendo / Cancelación (ajusta claves si difieren)
        set(tvMotivoReagendo, "Motivo de reagendo: " + orDash(doc.getString("motivo_reagendo")));
        set(tvFechaReagendo,  "Fecha de reagendo: " + fmtDate(asDate(doc.get("fecha_reagendo"))));
        set(tvMotivoCancelacion, "Motivo de cancelación: " + orDash(doc.getString("motivo_cancelacion")));
        set(tvFechaCancelacion,  "Fecha de cancelación: " + fmtDate(asDate(doc.get("fecha_cancelacion"))));

        // Mostrar mensaje informativo sobre archivos adjuntos
        mostrarMensajeArchivosAdjuntos();
    }

    // ========= Helpers =========
    private void set(TextView tv, String v){ if (tv!=null) tv.setText(v!=null? v : ""); }
    private void setChip(Chip chip, String v){ if (chip!=null) chip.setText(!TextUtils.isEmpty(v)? v : "Lugar"); }
    private String orDash(String s){ return TextUtils.isEmpty(s) ? "—" : s; }
    private String joinWithDot(String a, String b){
        if (TextUtils.isEmpty(a) && TextUtils.isEmpty(b)) return "—";
        if (TextUtils.isEmpty(a)) return b;
        if (TextUtils.isEmpty(b)) return a;
        return a + " • " + b;
    }
    private String fmtDate(@Nullable Date d){
        return d==null ? "—" : DateFormat.getDateTimeInstance().format(d);
    }
    private Date asDate(Object... xs){
        if (xs==null) return null;
        for (Object o: xs){
            if (o instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) o).toDate();
            if (o instanceof Date) return (Date) o;
            // si guardaste epoch millis:
            if (o instanceof Long) return new Date((Long) o);
        }
        return null;
    }
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
        tvMensaje.setPadding(16, 12, 16, 12);

        // Agregar bordes redondeados y fondo con borde
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(12); // Bordes redondeados
        shape.setColor(0xFFFEF3C7); // Fondo amarillo claro (#FEF3C7)
        shape.setStroke(2, 0xFFF59E0B); // Borde amarillo (#F59E0B)
        tvMensaje.setBackground(shape);

        // Agregar margen vertical para separación
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        tvMensaje.setLayoutParams(params);

        llAdjuntos.addView(tvMensaje);
    }

    /**
     * Actualiza el chip de estado con el color y texto apropiados
     */
    private void updateEstadoChip(String estadoRaw) {
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
        try {
            chEstado.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bg));
        } catch (Exception ignored) {}
        chEstado.setTextColor(fg);
    }

    // 🆕 Configurar listeners para mantenedores
    // NOTA: Simplificado - el batch update actualiza Firestore automáticamente
    private void setupMantenedorListeners(String newTipoActId, String newLugarId, String newOferenteId,
                                          String newSocioId, String newProyectoId, java.util.List<String> newBeneficiariosIds) {
        // Los listeners de mantenedores fueron removidos porque:
        // 1. El batch update ya actualiza la actividad en Firestore
        // 2. get() en recargarDetalleDesdeFirestore() obtiene datos frescos
        android.util.Log.d(TAG, "✅ setupMantenedorListeners simplificado");
    }
}
