package com.centroalerce.ui;

import android.app.*;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.*;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.utils.PermissionChecker;
import com.centroalerce.gestion.utils.RoleManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
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

    // ✅ NUEVO: Sistema de roles
    private PermissionChecker permissionChecker;
    private RoleManager roleManager;

    private FirebaseFirestore db;
    private String actividadId, citaId;
    private TextInputEditText etMotivo, etFecha, etHora;

    private final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Santiago"));
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", new Locale("es","CL"));
    private final SimpleDateFormat tf = new SimpleDateFormat("HH:mm", new Locale("es","CL"));

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.sheet_reagendar_actividad, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        // ✅ NUEVO: Inicializar sistema de roles
        permissionChecker = new PermissionChecker();
        roleManager = RoleManager.getInstance();

        // ✅ NUEVO: Verificar permisos ANTES de hacer cualquier cosa
        if (!permissionChecker.checkAndNotify(getContext(),
                PermissionChecker.Permission.RESCHEDULE_ACTIVITY)) {
            android.util.Log.d("ReagendarSheet", "🚫 Usuario sin permisos para reagendar");
            dismiss();
            return;
        }

        android.util.Log.d("ReagendarSheet", "✅ Usuario autorizado para reagendar");

        db = FirebaseFirestore.getInstance();
        Bundle args = getArguments();
        actividadId = args != null ? args.getString(ARG_ACTIVIDAD_ID) : null;
        citaId = args != null ? args.getString(ARG_CITA_ID) : null;

        etMotivo = v.findViewById(R.id.etMotivo);
        etFecha = v.findViewById(R.id.etFecha);
        etHora = v.findViewById(R.id.etHora);

        etFecha.setOnClickListener(x -> abrirDatePicker());
        etHora.setOnClickListener(x -> abrirTimePicker());
        MaterialButton btnGuardar = v.findViewById(R.id.btnGuardarNuevaFecha);
        if (btnGuardar != null) {
            btnGuardar.setOnClickListener(x -> {
                // ✅ NUEVO: Doble verificación antes de guardar
                if (permissionChecker.checkAndNotify(getContext(),
                        PermissionChecker.Permission.RESCHEDULE_ACTIVITY)) {
                    reagendar();
                }
            });
        }
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
        if (motivo.length() < 6){ etMotivo.setError("Describe el motivo (≥ 6)"); return; }
        if (TextUtils.isEmpty(txt(etFecha))){ etFecha.setError("Selecciona fecha"); return; }
        if (TextUtils.isEmpty(txt(etHora))){ etHora.setError("Selecciona hora"); return; }
        if (TextUtils.isEmpty(actividadId)){ toast("Falta actividadId"); return; }

        if (!TextUtils.isEmpty(citaId)) reagendarCita(citaId, motivo);
        else proximaCitaYReagendar(motivo);
    }

    private void proximaCitaYReagendar(String motivo){
        act(actividadId,true).collection("citas")
                .whereGreaterThan("startAt", Timestamp.now())
                .orderBy("startAt", Query.Direction.ASCENDING)
                .limit(1).get().addOnSuccessListener(q -> {
                    if (!q.isEmpty()){ reagendarCita(q.getDocuments().get(0).getId(), motivo); return; }
                    act(actividadId,false).collection("citas")
                            .whereGreaterThan("startAt", Timestamp.now())
                            .orderBy("startAt", Query.Direction.ASCENDING)
                            .limit(1).get().addOnSuccessListener(q2 -> {
                                if (!q2.isEmpty()){ reagendarCita(q2.getDocuments().get(0).getId(), motivo); return; }
                                act(actividadId,true).collection("citas")
                                        .whereGreaterThan("fechaInicio", Timestamp.now())
                                        .orderBy("fechaInicio", Query.Direction.ASCENDING)
                                        .limit(1).get().addOnSuccessListener(q3 -> {
                                            if (!q3.isEmpty()){ reagendarCita(q3.getDocuments().get(0).getId(), motivo); }
                                            else toast("No hay citas futuras para reagendar");
                                        });
                            });
                }).addOnFailureListener(e -> toast("Error: "+e.getMessage()));
    }










    /**
     * Busca conflictos el MISMO día y MISMA HH:mm (minuto exacto) por LUGAR u OFERENTE,
     * mirando tanto collectionGroup("citas") como la colección raíz "citas".
     * Ignora canceladas y excludeCitaId.
     */
    private void validarConflictoMismaHoraGlobal(@NonNull Date nuevaInicio,
                                                 @Nullable String lugarId, @Nullable String lugarNombre,
                                                 @Nullable String oferenteId, @Nullable String oferenteNombre,
                                                 @Nullable String excludeCitaId,
                                                 @NonNull BoolCB cb) {

        TimeZone tz = TimeZone.getTimeZone("America/Santiago");
        Calendar day = Calendar.getInstance(tz);
        day.setTime(nuevaInicio);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        Date dayStart = day.getTime();
        day.add(Calendar.DAY_OF_MONTH, 1);
        Date nextDayStart = day.getTime();

        Calendar tgt = Calendar.getInstance(tz);
        tgt.setTime(nuevaInicio);
        final int targetHour = tgt.get(Calendar.HOUR_OF_DAY);
        final int targetMin  = tgt.get(Calendar.MINUTE);

        final String lugarIdCmp   = emptyToNull(lugarId);
        final String lugarNomCmp  = norm(emptyToNull(lugarNombre));
        final String oferIdCmp    = emptyToNull(oferenteId);
        final String oferNomCmp   = norm(emptyToNull(oferenteNombre));

        // Procesa un snapshot y devuelve si hay conflicto
        java.util.function.Function<QuerySnapshot, Boolean> snapHasConflict = snap -> {
            if (snap == null) return false;
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (excludeCitaId != null && excludeCitaId.equals(d.getId())) continue;

                String estado = d.getString("estado");
                if (estado != null && estado.equalsIgnoreCase("cancelada")) continue;

                Timestamp ts = firstNonNullTimestamp(
                        d.getTimestamp("startAt"),
                        d.getTimestamp("fechaInicio"),
                        d.getTimestamp("fecha")
                );
                if (ts == null) continue;

                Calendar c = Calendar.getInstance(tz);
                c.setTime(ts.toDate());
                if (c.get(Calendar.HOUR_OF_DAY) != targetHour || c.get(Calendar.MINUTE) != targetMin) continue;

                String docLugarId  = emptyToNull(first(d.getString("lugarId"), d.getString("lugar_id")));
                String docLugarNom = norm(emptyToNull(first(d.getString("lugarNombre"), d.getString("lugar"))));
                String docOferId   = emptyToNull(first(d.getString("oferenteId"), d.getString("oferente_id")));
                String docOferNom  = norm(emptyToNull(first(d.getString("oferenteNombre"), d.getString("oferente"))));

                boolean matchLugar = (lugarIdCmp != null && lugarIdCmp.equals(docLugarId))
                        || (lugarNomCmp != null && lugarNomCmp.equals(docLugarNom));

                boolean matchOfer  = (oferIdCmp != null && oferIdCmp.equals(docOferId))
                        || (oferNomCmp != null && oferNomCmp.equals(docOferNom));

                if (matchLugar || matchOfer) return true;
            }
            return false;
        };

        FirebaseFirestore ff = FirebaseFirestore.getInstance();

        // 1) citas en subcolecciones (collectionGroup)
        ff.collectionGroup("citas")
                .whereGreaterThanOrEqualTo("startAt", new Timestamp(dayStart))
                .whereLessThan("startAt", new Timestamp(nextDayStart))
                .get()
                .addOnSuccessListener(q1 -> {
                    if (snapHasConflict.apply(q1)) { cb.call(true); return; }

                    // 2) citas en colección raíz (si las usas también)
                    ff.collection("citas")
                            .whereGreaterThanOrEqualTo("fecha", new Timestamp(dayStart))
                            .whereLessThan("fecha", new Timestamp(nextDayStart))
                            .get()
                            .addOnSuccessListener(q2 -> cb.call(snapHasConflict.apply(q2)))
                            .addOnFailureListener(e -> cb.call(false));
                })
                .addOnFailureListener(e -> cb.call(false));
    }














    // ======= MODIFICADO: se agrega validación de "misma hora mismo día" previa a verificarConflicto =======
    private void reagendarCita(String citaId, String motivo){
        DocumentReference citaES = act(actividadId,false).collection("citas").document(citaId);
        DocumentReference citaEN = act(actividadId,true ).collection("citas").document(citaId);

        citaES.get().addOnSuccessListener(d -> {
            DocumentReference ref = (d!=null && d.exists()) ? citaES : citaEN;
            ref.get().addOnSuccessListener(doc -> {
                if (doc==null || !doc.exists()){ toast("La cita no existe"); return; }

                Timestamp oldStart = doc.getTimestamp(firstKey(doc,"startAt","fechaInicio","fecha"));
                Timestamp oldEnd   = doc.getTimestamp(firstKey(doc,"endAt","fechaFin"));
                long dur = (oldStart!=null && oldEnd!=null)
                        ? (oldEnd.toDate().getTime()-oldStart.toDate().getTime())
                        : (60*60*1000);

                Date nuevaInicio = cal.getTime();
                Date nuevaFin    = new Date(nuevaInicio.getTime()+dur);

                // Normalizar identificadores de lugar / oferente (usa id si existe, si no nombre)
                String lugarKey = first(doc.getString("lugarId"), doc.getString("lugar_id"),
                        doc.getString("lugarNombre"), doc.getString("lugar"));
                String oferKey  = first(doc.getString("oferenteId"), doc.getString("oferente_id"),
                        doc.getString("oferenteNombre"), doc.getString("oferente"));

                // 🔒 BLOQUEO DURO: no permitir MISMA HH:mm mismo día para mismo LUGAR u OFERENTE
                checkSlotConflict(nuevaInicio, lugarKey, oferKey, doc.getId(), hasConflict -> {
                    if (hasConflict){
                        toast("No se puede reagendar: ya existe una cita a la MISMA HORA ese día para ese lugar u oferente.");
                        return;
                    }

                    // (opcional) tu validación por solapamiento de rango en la misma colección
                    String lugarNombre = first(doc.getString("lugarNombre"), doc.getString("lugar"));
                    verificarConflicto(ref.getParent(), lugarNombre, nuevaInicio, nuevaFin, conflicto -> {
                        if (conflicto){ toast("Conflicto: ya existe una cita en ese lugar y horario"); return; }

                        Map<String,Object> up = new HashMap<>();
                        up.put("startAt", new Timestamp(nuevaInicio));
                        up.put("endAt",   new Timestamp(nuevaFin));
                        up.put("fechaInicio", new Timestamp(nuevaInicio));
                        up.put("fechaFin",    new Timestamp(nuevaFin));
                        up.put("motivo_reagendo", motivo);
                        up.put("fecha_reagendo", Timestamp.now());
                        up.put("estado", "reagendada");

                        // Si quieres empezar a persistir claves de slot (recomendado):
                        String dayKey   = dayKey(nuevaInicio);      // yyyyMMdd
                        String timeKey  = timeKey(nuevaInicio);     // HHmm
                        String slotKey  = dayKey + "_" + timeKey;
                        if (!TextUtils.isEmpty(lugarKey)) up.put("slotLugar",  slotKey + "_L_" + norm(lugarKey));
                        if (!TextUtils.isEmpty(oferKey))  up.put("slotOfer",   slotKey + "_O_" + norm(oferKey));
                        up.put("slotDay", dayKey);  // útil para índices
                        up.put("slotHHmm", timeKey);

                        // ✅ NUEVO: Log de auditoría con usuario
                        String userId = roleManager.getCurrentUserId();
                        if (userId != null) {
                            up.put("lastModifiedBy", userId);
                        }

                        ref.update(up)
                                .addOnSuccessListener(u -> {
                                    android.util.Log.d("ReagendarSheet", "✅ Cita reagendada por usuario: " + userId);
                                    toast("Cita reagendada con éxito");
                                    registrarAuditoria("reagendar_cita", motivo);
                                    notifyChanged();
                                    dismiss();
                                })
                                .addOnFailureListener(e -> toast("Error: "+e.getMessage()));
                    });
                });
            });
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

            // ✅ NUEVO: Registrar quién hizo la acción
            String userId = roleManager.getCurrentUserId();
            if (userId != null) audit.put("userId", userId);

            db.collection("auditoria").add(audit);
        } catch (Exception ignored) {}
    }

    private interface BoolCB { void call(boolean v); }

    private void verificarConflicto(CollectionReference citasCol, String lugar, Date ini, Date fin, BoolCB cb){
        if (TextUtils.isEmpty(lugar)){ cb.call(false); return; }
        citasCol.whereEqualTo("lugar", lugar)
                .whereGreaterThan("fechaFin", new Timestamp(new Date(ini.getTime()-1)))
                .whereLessThan("fechaInicio", new Timestamp(new Date(fin.getTime()+1)))
                .get().addOnSuccessListener(q -> cb.call(!q.isEmpty()))
                .addOnFailureListener(e -> cb.call(false));
    }

    /**
     * Valida que no exista OTRA cita (en cualquier actividad) en el MISMO día y MISMA hora (HH:mm)
     * que choque por LUGAR u OFERENTE. (Ignora canceladas y la misma cita que estás moviendo).
     * Considera campos: startAt, fechaInicio (y fallback: fecha).
     */
    /**
     * Valida que no exista OTRA cita (en cualquier actividad) en el MISMO día y MISMA HORA (HH:mm)
     * para el MISMO LUGAR (comparando por id o nombre). Ignora canceladas y la misma cita.
     * Revisa collectionGroup("citas") y soporta campos de fecha: startAt / fechaInicio / fecha.
     */
    private void validarMismaHoraMismoDiaGlobal(@NonNull Date nuevaInicio,
                                                @Nullable String lugarIdParam,
                                                @Nullable String lugarNombreParam,
                                                @Nullable String oferenteIdParam,
                                                @Nullable String oferenteNombreParam,
                                                @Nullable String excludeCitaId,
                                                @NonNull BoolCB cb) {

        TimeZone tz = TimeZone.getTimeZone("America/Santiago");
        Calendar calDay = Calendar.getInstance(tz);
        calDay.setTime(nuevaInicio);
        // rango del día
        calDay.set(Calendar.HOUR_OF_DAY, 0);
        calDay.set(Calendar.MINUTE, 0);
        calDay.set(Calendar.SECOND, 0);
        calDay.set(Calendar.MILLISECOND, 0);
        Date dayStart = calDay.getTime();
        calDay.add(Calendar.DAY_OF_MONTH, 1);
        Date nextDayStart = calDay.getTime();

        Calendar tgt = Calendar.getInstance(tz);
        tgt.setTime(nuevaInicio);
        final int targetHour = tgt.get(Calendar.HOUR_OF_DAY);
        final int targetMin  = tgt.get(Calendar.MINUTE);

        // normalizar comparadores
        final String lugarIdCmp   = emptyToNull(lugarIdParam);
        final String lugarNomCmp  = norm(emptyToNull(lugarNombreParam));
        final String oferIdCmp    = emptyToNull(oferenteIdParam);
        final String oferNomCmp   = norm(emptyToNull(oferenteNombreParam));

        // función que analiza un snapshot y determina conflicto
        java.util.function.Function<QuerySnapshot, Boolean> hasConflict = snap -> {
            if (snap == null) return false;
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (excludeCitaId != null && excludeCitaId.equals(d.getId())) continue;

                String estado = d.getString("estado");
                if (estado != null && estado.equalsIgnoreCase("cancelada")) continue;

                Timestamp ts = firstNonNullTimestamp(
                        d.getTimestamp("startAt"),
                        d.getTimestamp("fechaInicio"),
                        d.getTimestamp("fecha")
                );
                if (ts == null) continue;

                Calendar c = Calendar.getInstance(tz);
                c.setTime(ts.toDate());
                if (c.get(Calendar.HOUR_OF_DAY) != targetHour || c.get(Calendar.MINUTE) != targetMin) continue;

                // obtener ambos del doc candidato
                String docLugarId   = emptyToNull(first(d.getString("lugarId"), d.getString("lugar_id")));
                String docLugarNom  = norm(emptyToNull(first(d.getString("lugarNombre"), d.getString("lugar"))));
                String docOferId    = emptyToNull(first(d.getString("oferenteId"), d.getString("oferente_id")));
                String docOferNom   = norm(emptyToNull(first(d.getString("oferenteNombre"), d.getString("oferente"))));

                boolean matchLugar = (lugarIdCmp != null && lugarIdCmp.equals(docLugarId))
                        || (lugarNomCmp != null && lugarNomCmp.equals(docLugarNom));

                boolean matchOfer  = (oferIdCmp != null && oferIdCmp.equals(docOferId))
                        || (oferNomCmp != null && oferNomCmp.equals(docOferNom));

                return true; // bloquear cualquier cita a esa HH:mm


                // Si quieres bloquear por cualquier cita a esa HH:mm (sin considerar lugar/ofer):
                // return true;
            }
            return false;
        };

        // encadenamos 3 consultas por cada posible campo de fecha
        FirebaseFirestore.getInstance().collectionGroup("citas")
                .whereGreaterThanOrEqualTo("startAt", new Timestamp(dayStart))
                .whereLessThan("startAt", new Timestamp(nextDayStart))
                .get()
                .addOnSuccessListener(q1 -> {
                    if (hasConflict.apply(q1)) { cb.call(true); return; }
                    FirebaseFirestore.getInstance().collectionGroup("citas")
                            .whereGreaterThanOrEqualTo("fechaInicio", new Timestamp(dayStart))
                            .whereLessThan("fechaInicio", new Timestamp(nextDayStart))
                            .get()
                            .addOnSuccessListener(q2 -> {
                                if (hasConflict.apply(q2)) { cb.call(true); return; }
                                FirebaseFirestore.getInstance().collectionGroup("citas")
                                        .whereGreaterThanOrEqualTo("fecha", new Timestamp(dayStart))
                                        .whereLessThan("fecha", new Timestamp(nextDayStart))
                                        .get()
                                        .addOnSuccessListener(q3 -> cb.call(hasConflict.apply(q3)))
                                        .addOnFailureListener(e -> cb.call(false));
                            })
                            .addOnFailureListener(e -> cb.call(false));
                })
                .addOnFailureListener(e -> cb.call(false));
    }



    private @Nullable String firstNonNullStr(String... xs){
        if (xs == null) return null;
        for (String s: xs) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private String first(String... xs){ for (String s: xs) if (!TextUtils.isEmpty(s)) return s; return null; }
    private String firstKey(DocumentSnapshot d, String... ks){ for (String k: ks) if (d.contains(k)) return k; return ks[0]; }
    private String txt(TextInputEditText et){ return et.getText()!=null ? et.getText().toString().trim() : ""; }

    // ======= MODIFICADO: emitir resultados también a nivel de Activity para refrescar calendario/detalle =======
    private void notifyChanged(){
        getParentFragmentManager().setFragmentResult("actividad_change", new Bundle());
        getParentFragmentManager().setFragmentResult("calendar_refresh", new Bundle());
        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", new Bundle());
            requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", new Bundle());
        } catch (Exception ignore) {}
    }
    private @Nullable Timestamp firstNonNullTimestamp(Timestamp... xs){
        if (xs == null) return null;
        for (Timestamp t: xs) if (t != null) return t;
        return null;
    }
    private @Nullable String emptyToNull(@Nullable String s){
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
    private @Nullable String norm(@Nullable String s){
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }
    private String dayKey(Date d){
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd", new java.util.Locale("es","CL"));
        f.setTimeZone(java.util.TimeZone.getTimeZone("America/Santiago"));
        return f.format(d);
    }
    private String timeKey(Date d){
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HHmm", new java.util.Locale("es","CL"));
        f.setTimeZone(java.util.TimeZone.getTimeZone("America/Santiago"));
        return f.format(d);
    }


    /**
     * Revisa conflictos MISMO día + MISMA HH:mm para mismo LUGAR u OFERENTE
     * en ambas fuentes: collectionGroup("citas") y colección raíz "citas".
     * Usa:
     *  - claves de slot si existen (slotLugar / slotOfer)
     *  - si no existen, trae citas del día y compara en cliente (HH:mm + lugar/ofer normalizados)
     */
    private void checkSlotConflict(@NonNull Date nuevaInicio,
                                   @Nullable String lugarKey,
                                   @Nullable String oferKey,
                                   @Nullable String excludeCitaId,
                                   @NonNull BoolCB cb) {

        TimeZone tz = TimeZone.getTimeZone("America/Santiago");
        Calendar day = Calendar.getInstance(tz);
        day.setTime(nuevaInicio);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        Date dayStart = day.getTime();
        day.add(Calendar.DAY_OF_MONTH, 1);
        Date nextDayStart = day.getTime();

        String dayK  = dayKey(nuevaInicio);     // yyyyMMdd
        String timeK = timeKey(nuevaInicio);    // HHmm
        String slot  = dayK + "_" + timeK;

        final String lugarNorm = norm(lugarKey);
        final String oferNorm  = norm(oferKey);

        FirebaseFirestore ff = FirebaseFirestore.getInstance();

        // Preferencia: usar campos índice si existen (rápido y simple)
        List<com.google.android.gms.tasks.Task<QuerySnapshot>> tasks = new ArrayList<>();

        if (!TextUtils.isEmpty(lugarNorm)) {
            tasks.add(ff.collectionGroup("citas").whereEqualTo("slotLugar", slot + "_L_" + lugarNorm).get());
            tasks.add(ff.collection("citas").whereEqualTo("slotLugar", slot + "_L_" + lugarNorm).get());
        }
        if (!TextUtils.isEmpty(oferNorm)) {
            tasks.add(ff.collectionGroup("citas").whereEqualTo("slotOfer", slot + "_O_" + oferNorm).get());
            tasks.add(ff.collection("citas").whereEqualTo("slotOfer", slot + "_O_" + oferNorm).get());
        }

        // Si no hay claves índice, caemos a rango por día
        boolean willFallback = tasks.isEmpty();
        if (willFallback) {
            tasks.add(ff.collectionGroup("citas")
                    .whereGreaterThanOrEqualTo("startAt", new Timestamp(dayStart))
                    .whereLessThan("startAt", new Timestamp(nextDayStart)).get());
            tasks.add(ff.collection("citas")
                    .whereGreaterThanOrEqualTo("fecha", new Timestamp(dayStart))
                    .whereLessThan("fecha", new Timestamp(nextDayStart)).get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(list -> {
                    boolean conflict = false;
                    for (Object o : list) {
                        QuerySnapshot snap = (QuerySnapshot) o;
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            if (excludeCitaId != null && excludeCitaId.equals(d.getId())) continue;
                            String estado = d.getString("estado");
                            if (estado != null && estado.equalsIgnoreCase("cancelada")) continue;

                            if (!willFallback) { // con slotKey es directo
                                conflict = true;
                                break;
                            } else {
                                // fallback: validar HH:mm + lugar/u oferente normalizados
                                Timestamp ts = firstNonNullTimestamp(
                                        d.getTimestamp("startAt"),
                                        d.getTimestamp("fechaInicio"),
                                        d.getTimestamp("fecha")
                                );
                                if (ts == null) continue;

                                String hhmmDoc = timeKey(ts.toDate());
                                if (!timeK.equals(hhmmDoc)) continue;

                                String docLugar = norm(first(d.getString("lugarId"), d.getString("lugar_id"),
                                        d.getString("lugarNombre"), d.getString("lugar")));
                                String docOfer  = norm(first(d.getString("oferenteId"), d.getString("oferente_id"),
                                        d.getString("oferenteNombre"), d.getString("oferente")));

                                boolean matchLugar = !TextUtils.isEmpty(lugarNorm) && lugarNorm.equals(docLugar);
                                boolean matchOfer  = !TextUtils.isEmpty(oferNorm)  && oferNorm.equals(docOfer);

                                if (matchLugar || matchOfer) { conflict = true; break; }
                            }
                        }
                        if (conflict) break;
                    }
                    cb.call(conflict);
                })
                .addOnFailureListener(e -> cb.call(false));
    }


    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
}
