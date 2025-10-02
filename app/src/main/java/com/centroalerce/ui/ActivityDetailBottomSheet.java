package com.centroalerce.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// (opcional) logs
import android.util.Log;

public class ActivityDetailBottomSheet extends BottomSheetDialogFragment {

    public static ActivityDetailBottomSheet newInstance(String actividadId, String citaId) {
        ActivityDetailBottomSheet f = new ActivityDetailBottomSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        b.putString("citaId", citaId);
        f.setArguments(b);
        return f;
    }

    // ---- util ids sin R ----
    private int resId(String name, String defType) {
        return requireContext().getResources().getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String name)     { return resId(name, "id"); }
    private int layout(String name) { return resId(name, "layout"); }

    // ---- views ----
    private TextView tvTitulo, tvTipoYPer, tvHora, tvLugar;
    private TextView tvTipo, tvPeriodicidad, tvCupo, tvOferente, tvSocio, tvBeneficiarios;
    private TextView tvAdjuntosHeader;
    private LinearLayout llAdjuntos;
    private Button btnModificar, btnCancelar, btnReagendar, btnAdjuntar;

    // ---- data ----
    private String actividadId, citaId;

    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        int layoutId = layout("fragment_detalle_actividad");
        if (layoutId == 0) {
            Toast.makeText(requireContext(), "No encuentro fragment_detalle_actividad.xml", Toast.LENGTH_LONG).show();
            return new View(requireContext());
        }
        return inf.inflate(layoutId, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);

        actividadId = getArg("actividadId");
        citaId      = getArg("citaId");

        // Bind
        tvTitulo        = root.findViewById(id("tvTitulo"));
        tvTipoYPer      = root.findViewById(id("tvTipoYPeriodicidad"));
        tvHora          = root.findViewById(id("tvHora"));
        tvLugar         = root.findViewById(id("tvLugar"));
        tvTipo          = root.findViewById(id("tvTipo"));
        tvPeriodicidad  = root.findViewById(id("tvPeriodicidad"));
        tvCupo          = root.findViewById(id("tvCupo"));
        tvOferente      = root.findViewById(id("tvOferente"));
        tvSocio         = root.findViewById(id("tvSocio"));
        tvBeneficiarios = root.findViewById(id("tvBeneficiarios"));
        llAdjuntos      = root.findViewById(id("llAdjuntos"));
        tvAdjuntosHeader= root.findViewById(id("tvAdjuntosHeader"));

        btnModificar = root.findViewById(id("btnModificar"));
        btnCancelar  = root.findViewById(id("btnCancelar"));
        btnReagendar = root.findViewById(id("btnReagendar"));
        btnAdjuntar  = root.findViewById(id("btnAdjuntar"));

        // listeners
        if (btnModificar != null) {
            btnModificar.setOnClickListener(v ->
                    ModificarActividadSheet.newInstance(actividadId)
                            .show(getParentFragmentManager(), "ModificarActividadSheet"));
        }
        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(v ->
                    CancelarActividadSheet.newInstance(actividadId, citaId)
                            .show(getParentFragmentManager(), "CancelarActividadSheet"));
        }
        if (btnReagendar != null) {
            btnReagendar.setOnClickListener(v ->
                    ReagendarActividadSheet.newInstance(actividadId, citaId)
                            .show(getParentFragmentManager(), "ReagendarActividadSheet"));
        }
        if (btnAdjuntar != null) {
            btnAdjuntar.setOnClickListener(v ->
                    AdjuntarComunicacionSheet.newInstance(actividadId)
                            .show(getParentFragmentManager(), "AdjuntarComunicacionSheet"));
        }

        // carga
        db = FirebaseFirestore.getInstance();
        setTextOrDash(tvTitulo, "Actividad");
        if (tvTipoYPer != null) tvTipoYPer.setText("");

        // Inicializa filas con “—”
        setLabeled(tvTipo,         "Tipo: ",          "—");
        setLabeled(tvPeriodicidad, "Periodicidad: ",  "—");
        setLabeled(tvCupo,         "Cupo: ",          "—");
        setTextOrDash(tvLugar,     "—");
        setTextOrDash(tvHora,      "—");
        setLabeled(tvOferente,      "Oferente: ",        "—");
        setLabeled(tvSocio,         "Socio comunitario: ","—");
        setLabeled(tvBeneficiarios, "Beneficiarios: ",   "—");

        if (tvAdjuntosHeader != null) tvAdjuntosHeader.setVisibility(View.VISIBLE);
        if (llAdjuntos != null) {
            llAdjuntos.removeAllViews();
            addNoFilesRow(); // placeholder
        }

        loadActividad(actividadId);
        loadCita(actividadId, citaId);

        // ⬅️ clave: usa el loader unificado (cita -> actividad)
        loadAdjuntosAll(actividadId, citaId);
    }

    // ---------- Firestore: actividad (con alias/formatos) ----------
    private void loadActividad(String actividadId) {
        if (TextUtils.isEmpty(actividadId)) return;

        db.collection("activities").document(actividadId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    String nombre       = pickString(doc, "nombre");
                    String tipo         = pickString(doc, "tipo", "tipoActividad", "tipo_actividad", "tipoNombre");
                    String periodicidad = pickString(doc, "periodicidad", "frecuencia", "periodicidadNombre", "frecuenciaNombre");
                    Long   cupo         = safeLong(doc.get("cupo"));

                    List<String> oferentesList = pickStringList(doc, new String[]{"oferentes", "oferente", "oferentesNombres", "oferenteNombre"});
                    String oferentes = joinListOrText(oferentesList);

                    String socio = pickString(doc, "socioComunitario", "socio", "socio_nombre");

                    List<String> beneficiariosList = pickStringList(doc, new String[]{"beneficiarios", "beneficiario", "beneficiariosNombres"});
                    if (beneficiariosList.isEmpty()) {
                        String beneficiariosTexto = pickString(doc, "beneficiariosTexto");
                        beneficiariosList = splitToList(beneficiariosTexto);
                    }
                    String beneficiarios = joinListOrText(beneficiariosList);

                    setTextOrDash(tvTitulo, nonEmpty(nombre, "Actividad"));
                    setLabeled(tvTipo,         "Tipo: ",          nonEmpty(tipo, "—"));
                    setLabeled(tvPeriodicidad, "Periodicidad: ",  nonEmpty(periodicidad, "—"));
                    setLabeled(tvCupo,         "Cupo: ",          (cupo != null && cupo >= 0) ? String.valueOf(cupo) : "—");
                    setLabeled(tvOferente,     "Oferente: ",      nonEmpty(oferentes, "—"));
                    setLabeled(tvSocio,        "Socio comunitario: ", nonEmpty(socio, "—"));
                    setLabeled(tvBeneficiarios,"Beneficiarios: ", nonEmpty(beneficiarios, "—"));
                })
                .addOnFailureListener(e -> toast("No se pudo cargar la actividad"));
    }

    // ---------- Firestore: cita ----------
    private void loadCita(String actividadId, String citaId) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(citaId)) return;

        db.collection("activities").document(actividadId)
                .collection("citas").document(citaId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    Timestamp ts = doc.getTimestamp("startAt");
                    if (ts == null) {
                        String fecha = doc.getString("fecha");      // yyyy-MM-dd
                        String hora  = doc.getString("horaInicio"); // HH:mm
                        try {
                            String[] hhmm = (hora != null) ? hora.split(":") : new String[]{"00","00"};
                            java.time.LocalDate d = java.time.LocalDate.parse(fecha);
                            java.time.LocalTime t = java.time.LocalTime.of(Integer.parseInt(hhmm[0]), Integer.parseInt(hhmm[1]));
                            ZonedDateTime zdt = d.atTime(t).atZone(ZoneId.systemDefault());
                            ts = new Timestamp(Date.from(zdt.toInstant()));
                        } catch (Exception ignored) {}
                    }

                    String lugar = doc.getString("lugarNombre");

                    if (ts != null) {
                        ZonedDateTime local = ZonedDateTime.ofInstant(
                                Instant.ofEpochMilli(ts.toDate().getTime()),
                                ZoneId.systemDefault()
                        );
                        String fechaStr = local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        String horaStr  = local.format(DateTimeFormatter.ofPattern("HH:mm"));
                        setTextOrDash(tvHora,  fechaStr + " • " + horaStr);
                    } else {
                        setTextOrDash(tvHora, "—");
                    }
                    setTextOrDash(tvLugar, nonEmpty(lugar, "—"));
                })
                .addOnFailureListener(e -> toast("No se pudo cargar la cita"));
    }

    // ================== ADJUNTOS: loader unificado ==================

    /** Busca adjuntos en: CITA (doc + subcols) y luego ACTIVIDAD (doc + subcols) */
    private void loadAdjuntosAll(String actividadId, String citaId) {
        if (llAdjuntos == null) return;

        llAdjuntos.removeAllViews();
        addNoFilesRow();

        if (!TextUtils.isEmpty(actividadId) && !TextUtils.isEmpty(citaId)) {
            // 1) CITA -> campo "adjuntos"
            db.collection("activities").document(actividadId)
                    .collection("citas").document(citaId)
                    .get()
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
                                            String nombre = stringOr(it.get("name"), stringOr(it.get("nombre"), "(archivo)"));
                                            String url    = stringOr(it.get("url"), null);
                                            String id     = stringOr(it.get("id"), null);
                                            addAdjuntoRow(nombre, url, TextUtils.isEmpty(id) ? null : id);
                                            any = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (any) return;

                        // 2) CITA -> subcolecciones archivos/adjuntos
                        loadAdjuntosFromCitaSubcollection(actividadId, citaId, "archivos", () ->
                                loadAdjuntosFromCitaSubcollection(actividadId, citaId, "adjuntos", () -> {
                                    // 3) ACTIVIDAD -> campo "adjuntos"
                                    loadAdjuntosActividad(actividadId);
                                })
                        );
                    })
                    .addOnFailureListener(e -> loadAdjuntosActividad(actividadId));
        } else {
            // sin cita: ir directo a actividad
            loadAdjuntosActividad(actividadId);
        }
    }

    /** Lee adjuntos en el DOC de la ACTIVIDAD y luego en sus subcolecciones */
    private void loadAdjuntosActividad(String actividadId) {
        if (TextUtils.isEmpty(actividadId)) {
            showPlaceholderIfEmpty();
            return;
        }
        db.collection("activities").document(actividadId).get()
                .addOnSuccessListener(doc -> {
                    boolean any = false;
                    if (doc != null && doc.exists()) {
                        Object raw = doc.get("adjuntos"); // <- tu form los guarda aquí y en subcol
                        if (raw instanceof List) {
                            List<?> arr = (List<?>) raw;
                            if (!arr.isEmpty()) {
                                llAdjuntos.removeAllViews();
                                for (Object o : arr) {
                                    if (o instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> it = (Map<String, Object>) o;
                                        String nombre = stringOr(it.get("name"), stringOr(it.get("nombre"), "(archivo)"));
                                        String url    = stringOr(it.get("url"), null);
                                        String id     = stringOr(it.get("id"), null);
                                        addAdjuntoRow(nombre, url, TextUtils.isEmpty(id) ? null : id);
                                        any = true;
                                    }
                                }
                            }
                        }
                    }
                    if (any) return;

                    // subcolecciones en actividad (tu form también crea /adjuntos)
                    loadAdjuntosFromSubcollection(actividadId, "archivos", () ->
                            loadAdjuntosFromSubcollection(actividadId, "adjuntos", this::showPlaceholderIfEmpty)
                    );
                })
                .addOnFailureListener(e -> showPlaceholderIfEmpty());
    }

    /** Lee subcolecciones en la CITA */
    private void loadAdjuntosFromCitaSubcollection(String actividadId, String citaId, String sub, EmptyFallback onEmpty) {
        db.collection("activities").document(actividadId)
                .collection("citas").document(citaId)
                .collection(sub)
                // quita el orderBy si no tienes 'creadoEn' en la cita
                .orderBy("creadoEn", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(q -> {
                    if (q == null || q.isEmpty()) {
                        onEmpty.run();
                        return;
                    }
                    llAdjuntos.removeAllViews();
                    int added = 0;
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String nombre = d.getString("nombre");
                        String url    = d.getString("url");
                        String did    = d.getId();
                        addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url, did);
                        added++;
                    }
                    if (added == 0) onEmpty.run();
                })
                .addOnFailureListener(e -> onEmpty.run());
    }

    // ---------- ya existente: subcolecciones en ACTIVIDAD ----------
    private interface EmptyFallback { void run(); }

    private void loadAdjuntosFromSubcollection(String actividadId, String sub, EmptyFallback onEmpty) {
        db.collection("activities").document(actividadId)
                .collection(sub)
                .orderBy("creadoEn", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(q -> {
                    if (q == null || q.isEmpty()) {
                        onEmpty.run();
                        return;
                    }
                    llAdjuntos.removeAllViews();
                    int added = 0;
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String nombre = d.getString("nombre");
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

    // ---- UI rows ----
    private void addAdjuntoRow(String nombre, @Nullable String url) {
        addAdjuntoRow(nombre, url, null);
    }

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

    // ---- utils ----
    private void setTextOrDash(@Nullable TextView tv, @NonNull String valueOrDash) {
        if (tv == null) return;
        tv.setText(valueOrDash);
        tv.setVisibility(View.VISIBLE);
    }

    private void setLabeled(@Nullable TextView tv, String prefix, String value) {
        if (tv == null) return;
        tv.setText(prefix + value);
        tv.setVisibility(View.VISIBLE);
    }

    private String nonEmpty(String v, String def){ return (v == null || v.trim().isEmpty()) ? def : v; }

    private @Nullable String pickString(DocumentSnapshot doc, String... keys) {
        for (String k : keys) {
            String v = doc.getString(k);
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }

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
                    String ss = t == null ? "" : t.trim();
                    if (!ss.isEmpty()) set.add(ss);
                }
                out.addAll(set);
            }
        }
        return out;
    }

    private String joinListOrText(List<String> xs) {
        return (xs == null || xs.isEmpty()) ? "" : TextUtils.join(", ", xs);
    }

    private String stringOr(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }
    /** Convierte "A, B; C | D" en ["A","B","C","D"], sin duplicados y con trim */
    private List<String> splitToList(String text) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] tokens = text.split("[,;|\\n]+");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String t : tokens) {
            String s = (t == null) ? "" : t.trim();
            if (!s.isEmpty()) set.add(s);
        }
        out.addAll(set);
        return out;
    }

    private String getArg(String key) {
        return (getArguments() != null) ? getArguments().getString(key, "") : "";
    }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
    private Long safeLong(Object v) { try { if (v instanceof Number) return ((Number) v).longValue(); } catch (Exception ignored) {} return null; }
}
