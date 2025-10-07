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

import java.text.DateFormat;
import java.util.Date;

public class DetalleActividadFragment extends Fragment {

    private static final String ARG_ACTIVIDAD_ID = "actividadId";

    public static DetalleActividadFragment newInstance(@NonNull String actividadId){
        Bundle b = new Bundle(); b.putString(ARG_ACTIVIDAD_ID, actividadId);
        DetalleActividadFragment f = new DetalleActividadFragment(); f.setArguments(b); return f;
    }

    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String id, boolean en){ return FirebaseFirestore.getInstance().collection(en?COL_EN:COL_ES).document(id); }

    private String actividadId;

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
        chEstado.setText(estado);

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

        // TODO: si manejas adjuntos, aquí recárgalos en llAdjuntos (query a subcolección)
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
}
