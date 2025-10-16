package com.centroalerce.ui;

import android.app.*;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.*;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.repositories.LugarRepository;
import com.centroalerce.gestion.models.Lugar;
import com.centroalerce.gestion.utils.ActividadValidator;
import com.centroalerce.gestion.utils.ValidationResult;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class ReagendarActividadSheet extends BottomSheetDialogFragment {
    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    private static final String ARG_CITA_ID = "citaId";

    public static ReagendarActividadSheet newInstance(@NonNull String actividadId, @Nullable String citaId){
        Bundle b = new Bundle();
        b.putString(ARG_ACTIVIDAD_ID, actividadId);
        if (!TextUtils.isEmpty(citaId)) b.putString(ARG_CITA_ID, citaId);
        ReagendarActividadSheet s = new ReagendarActividadSheet();
        s.setArguments(b);
        return s;
    }

    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance().collection(preferEN ? COL_EN : COL_ES).document(actividadId);
    }

    private FirebaseFirestore db;
    private LugarRepository lugarRepository;
    private String actividadId, citaId;
    private TextInputEditText etMotivo, etFecha, etHora;

    private final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Santiago"));
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", new Locale("es","CL"));
    private final SimpleDateFormat tf = new SimpleDateFormat("HH:mm", new Locale("es","CL"));

    // Datos de la cita actual
    private String lugarIdActual;
    private String lugarNombreActual;
    private String oferenteIdActual;
    private String oferenteNombreActual;
    private Integer cupoActividad;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.sheet_reagendar_actividad, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v,b);
        db = FirebaseFirestore.getInstance();
        lugarRepository = new LugarRepository();
        Bundle args = getArguments();
        actividadId = args != null ? args.getString(ARG_ACTIVIDAD_ID) : null;
        citaId      = args != null ? args.getString(ARG_CITA_ID) : null;

        etMotivo = v.findViewById(R.id.etMotivo);
        etFecha  = v.findViewById(R.id.etFecha);
        etHora   = v.findViewById(R.id.etHora);

        etFecha.setOnClickListener(x -> abrirDatePicker());
        etHora.setOnClickListener(x -> abrirTimePicker());
        ((MaterialButton) v.findViewById(R.id.btnGuardarNuevaFecha)).setOnClickListener(x -> reagendar());

        // Cargar datos de la actividad y cita actual
        cargarDatosActuales();
    }

    private void cargarDatosActuales() {
        if (TextUtils.isEmpty(actividadId)) return;

        act(actividadId, true).get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                procesarDatosActividad(doc);
            } else {
                act(actividadId, false).get().addOnSuccessListener(this::procesarDatosActividad);
            }
        });
    }

    private void procesarDatosActividad(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return;

        // Obtener cupo de la actividad
        Long cupo = doc.getLong("cupo");
        cupoActividad = cupo != null ? cupo.intValue() : null;

        // Obtener lugar
        lugarIdActual = firstNonEmpty(
                doc.getString("lugarId"),
                doc.getString("lugar_id")
        );
        lugarNombreActual = firstNonEmpty(
                doc.getString("lugarNombre"),
                doc.getString("lugar")
        );

        // Obtener oferente
        oferenteIdActual = firstNonEmpty(
                doc.getString("oferenteId"),
                doc.getString("oferente_id")
        );
        oferenteNombreActual = firstNonEmpty(
                doc.getString("oferenteNombre"),
                doc.getString("oferente")
        );
    }

    private void abrirDatePicker(){
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("America/Santiago"));
        new DatePickerDialog(requireContext(), (p,y,m,d)->{
            cal.set(y,m,d);
            etFecha.setText(df.format(cal.getTime()));
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void abrirTimePicker(){
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("America/Santiago"));
        new TimePickerDialog(requireContext(), (p,h,min)->{
            cal.set(Calendar.HOUR_OF_DAY,h);
            cal.set(Calendar.MINUTE,min);
            cal.set(Calendar.SECOND,0);
            etHora.setText(tf.format(cal.getTime()));
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void reagendar(){
        String motivo = txt(etMotivo);
        if (motivo.length() < 6){
            etMotivo.setError("Describe el motivo (≥ 6)");
            return;
        }
        if (TextUtils.isEmpty(txt(etFecha))){
            etFecha.setError("Selecciona fecha");
            return;
        }
        if (TextUtils.isEmpty(txt(etHora))){
            etHora.setError("Selecciona hora");
            return;
        }
        if (TextUtils.isEmpty(actividadId)){
            toast("Falta actividadId");
            return;
        }

        // ✅ NUEVO: Validar fecha futura
        Date nuevaFecha = cal.getTime();
        ValidationResult validacionFecha = ActividadValidator.validarFechaFutura(nuevaFecha);
        if (!validacionFecha.isValid()) {
            mostrarDialogoError("Fecha inválida", validacionFecha.getErrorMessage());
            return;
        }

        // ✅ NUEVO: Validar cupo del lugar si está definido
        if (!TextUtils.isEmpty(lugarNombreActual)) {
            validarCupoYConflicto(motivo, nuevaFecha);
        } else {
            // Sin lugar definido, proceder directo
            if (!TextUtils.isEmpty(citaId)) {
                reagendarCita(citaId, motivo, nuevaFecha);
            } else {
                proximaCitaYReagendar(motivo, nuevaFecha);
            }
        }
    }

    // ===== NUEVO: Validación de cupo del lugar =====
    private void validarCupoYConflicto(String motivo, Date nuevaFecha) {
        if (TextUtils.isEmpty(lugarNombreActual)) {
            validarConflictoGlobal(motivo, nuevaFecha);
            return;
        }

        // Buscar ID del lugar por nombre
        db.collection("lugares")
                .whereEqualTo("nombre", lugarNombreActual)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) {
                        // Lugar no encontrado, proceder sin validación de cupo
                        validarConflictoGlobal(motivo, nuevaFecha);
                        return;
                    }

                    String lugarId = qs.getDocuments().get(0).getId();
                    lugarRepository.getLugar(lugarId, new LugarRepository.LugarCallback() {
                        @Override
                        public void onSuccess(Lugar lugar) {
                            // Validar cupo solo si el lugar lo tiene definido Y la actividad también
                            if (lugar.tieneCupo() && cupoActividad != null) {
                                ValidationResult validacionCupo = ActividadValidator.validarCupoLugar(lugar, cupoActividad);
                                if (!validacionCupo.isValid()) {
                                    mostrarDialogoErrorConAlternativa(
                                            "Cupo insuficiente",
                                            validacionCupo.getErrorMessage(),
                                            "Entendido"
                                    );
                                    return;
                                }
                            }

                            // Cupo OK, continuar con validación de conflictos
                            validarConflictoGlobal(motivo, nuevaFecha);
                        }

                        @Override
                        public void onError(String error) {
                            // Error al obtener lugar, proceder sin validación de cupo
                            validarConflictoGlobal(motivo, nuevaFecha);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    // Error al buscar lugar, proceder sin validación
                    validarConflictoGlobal(motivo, nuevaFecha);
                });
    }

    // ===== NUEVO: Validación de conflicto global =====
    private void validarConflictoGlobal(String motivo, Date nuevaFecha) {
        TimeZone tz = TimeZone.getTimeZone("America/Santiago");
        Calendar day = Calendar.getInstance(tz);
        day.setTime(nuevaFecha);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        Date dayStart = day.getTime();
        day.add(Calendar.DAY_OF_MONTH, 1);
        Date nextDayStart = day.getTime();

        Calendar tgt = Calendar.getInstance(tz);
        tgt.setTime(nuevaFecha);
        final int targetHour = tgt.get(Calendar.HOUR_OF_DAY);
        final int targetMin  = tgt.get(Calendar.MINUTE);

        final String lugarNorm = norm(lugarNombreActual);
        final String oferNorm  = norm(oferenteNombreActual);

        // Buscar citas en el mismo día y hora
        db.collectionGroup("citas")
                .whereGreaterThanOrEqualTo("startAt", new Timestamp(dayStart))
                .whereLessThan("startAt", new Timestamp(nextDayStart))
                .get()
                .addOnSuccessListener(snap -> {
                    boolean hayConflicto = false;

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        // Ignorar la cita que estamos reagendando
                        if (citaId != null && citaId.equals(d.getId())) continue;

                        String estado = d.getString("estado");
                        if ("cancelada".equalsIgnoreCase(estado)) continue;

                        Timestamp ts = firstNonNullTimestamp(
                                d.getTimestamp("startAt"),
                                d.getTimestamp("fechaInicio"),
                                d.getTimestamp("fecha")
                        );
                        if (ts == null) continue;

                        Calendar c = Calendar.getInstance(tz);
                        c.setTime(ts.toDate());
                        if (c.get(Calendar.HOUR_OF_DAY) != targetHour ||
                                c.get(Calendar.MINUTE) != targetMin) continue;

                        // Verificar conflicto por lugar
                        String docLugar = norm(firstNonEmpty(
                                d.getString("lugarNombre"),
                                d.getString("lugar")
                        ));
                        if (!TextUtils.isEmpty(lugarNorm) && lugarNorm.equals(docLugar)) {
                            hayConflicto = true;
                            break;
                        }

                        // Verificar conflicto por oferente
                        String docOfer = norm(firstNonEmpty(
                                d.getString("oferenteNombre"),
                                d.getString("oferente")
                        ));
                        if (!TextUtils.isEmpty(oferNorm) && oferNorm.equals(docOfer)) {
                            hayConflicto = true;
                            break;
                        }
                    }

                    if (hayConflicto) {
                        mostrarDialogoConflicto(
                                "Ya existe una cita a esa hora (" + tf.format(nuevaFecha) +
                                        ") para el mismo lugar u oferente.\n\n¿Qué deseas hacer?"
                        );
                    } else {
                        // ✅ No hay conflictos, proceder con el reagendamiento
                        if (!TextUtils.isEmpty(citaId)) {
                            reagendarCita(citaId, motivo, nuevaFecha);
                        } else {
                            proximaCitaYReagendar(motivo, nuevaFecha);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    toast("Error al validar: " + e.getMessage());
                });
    }

    private void proximaCitaYReagendar(String motivo, Date nuevaFecha){
        act(actividadId,true).collection("citas")
                .whereGreaterThan("startAt", Timestamp.now())
                .orderBy("startAt", Query.Direction.ASCENDING)
                .limit(1).get().addOnSuccessListener(q -> {
                    if (!q.isEmpty()){
                        reagendarCita(q.getDocuments().get(0).getId(), motivo, nuevaFecha);
                        return;
                    }
                    act(actividadId,false).collection("citas")
                            .whereGreaterThan("startAt", Timestamp.now())
                            .orderBy("startAt", Query.Direction.ASCENDING)
                            .limit(1).get().addOnSuccessListener(q2 -> {
                                if (!q2.isEmpty()){
                                    reagendarCita(q2.getDocuments().get(0).getId(), motivo, nuevaFecha);
                                    return;
                                }
                                toast("No hay citas futuras para reagendar");
                            });
                }).addOnFailureListener(e -> toast("Error: "+e.getMessage()));
    }

    private void reagendarCita(String citaId, String motivo, Date nuevaFecha){
        DocumentReference citaES = act(actividadId,false).collection("citas").document(citaId);
        DocumentReference citaEN = act(actividadId,true ).collection("citas").document(citaId);

        citaES.get().addOnSuccessListener(d -> {
            DocumentReference ref = (d!=null && d.exists()) ? citaES : citaEN;

            Map<String,Object> up = new HashMap<>();
            up.put("startAt", new Timestamp(nuevaFecha));
            up.put("fecha", new Timestamp(nuevaFecha));
            up.put("fechaInicio", new Timestamp(nuevaFecha));
            up.put("motivo_reagendo", motivo);
            up.put("fecha_reagendo", Timestamp.now());
            up.put("estado", "reagendada");

            ref.update(up)
                    .addOnSuccessListener(u -> {
                        toast("Cita reagendada con éxito ✅");
                        registrarAuditoria("reagendar_cita", motivo);
                        notifyChanged();
                        dismiss();
                    })
                    .addOnFailureListener(e -> toast("Error: "+e.getMessage()));
        }).addOnFailureListener(e -> toast("Error: "+e.getMessage()));
    }

    private void registrarAuditoria(String accion, String motivo){
        try {
            Map<String,Object> audit = new HashMap<>();
            audit.put("accion", accion);
            audit.put("motivo", motivo);
            audit.put("timestamp", Timestamp.now());
            audit.put("actividadId", actividadId);
            if (!TextUtils.isEmpty(citaId)) audit.put("citaId", citaId);

            db.collection("auditoria").add(audit);
        } catch (Exception ignored) {}
    }

    // ===== NUEVO: Diálogos informativos =====
    private void mostrarDialogoError(String titulo, String mensaje) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void mostrarDialogoErrorConAlternativa(String titulo, String mensaje, String textoBoton) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton(textoBoton, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void mostrarDialogoConflicto(String mensaje) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Conflicto de horario")
                .setMessage(mensaje)
                .setPositiveButton("Cambiar fecha/hora", (dialog, which) -> {
                    etFecha.setText(null);
                    etHora.setText(null);
                    toast("Selecciona otra fecha y hora");
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // ===== Helpers =====
    private String txt(TextInputEditText et){
        return et.getText()!=null ? et.getText().toString().trim() : "";
    }

    private String firstNonEmpty(String... xs){
        if (xs==null) return null;
        for (String s: xs){ if (!TextUtils.isEmpty(s)) return s; }
        return null;
    }

    private @Nullable Timestamp firstNonNullTimestamp(Timestamp... xs){
        if (xs == null) return null;
        for (Timestamp t: xs) if (t != null) return t;
        return null;
    }

    private @Nullable String norm(@Nullable String s){
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
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

    private void toast(String m){
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}