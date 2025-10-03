// ActivityDetailBottomSheet.java
package com.centroalerce.ui;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log; // 👈 NUEVO
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ActivityDetailBottomSheet extends BottomSheetDialogFragment {

    // ---------- LOG TAG ----------
    private static final String TAG = "DetalleAdjuntos"; // 👈 NUEVO

    // ---------- Factory ----------
    public static ActivityDetailBottomSheet newInstance(String actividadId, String citaId) {
        ActivityDetailBottomSheet f = new ActivityDetailBottomSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        b.putString("citaId", citaId);
        f.setArguments(b);
        return f;
    }

    // ---------- Helpers para NO usar R ----------
    private int resId(String name, String defType) {
        return requireContext()
                .getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    // ---------- Views ----------
    private TextView tvNombre, tvTipoYPer;
    private Chip chFechaHora, chLugar;
    private TextView tvTipo, tvPeriodicidad, tvCupo, tvOferente, tvSocio, tvBeneficiarios;
    private LinearLayout llAdjuntos;
    private Button btnModificar, btnCancelar, btnReagendar, btnAdjuntar;

    // ---------- Data ----------
    private String actividadId, citaId;
    private FirebaseFirestore db;

    // Soporte colecciones EN/ES
    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance()
                .collection(preferEN ? COL_EN : COL_ES)
                .document(actividadId);
    }

    // ---------- Forzar fondo BLANCO sin R de tu app ----------
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackgroundColor(android.graphics.Color.WHITE);
    }

    // ---------- Lifecycle ----------
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
        citaId      = getArg("citaId");

        tvNombre        = root.findViewById(id("tvNombre"));
        tvTipoYPer      = root.findViewById(id("tvTipoYPeriodicidad"));
        chFechaHora     = root.findViewById(id("chFechaHora"));
        chLugar         = root.findViewById(id("chLugar"));
        tvTipo          = root.findViewById(id("tvTipo"));
        tvPeriodicidad  = root.findViewById(id("tvPeriodicidad"));
        tvCupo          = root.findViewById(id("tvCupo"));
        tvOferente      = root.findViewById(id("tvOferente"));
        tvSocio         = root.findViewById(id("tvSocio"));
        tvBeneficiarios = root.findViewById(id("tvBeneficiarios"));
        llAdjuntos      = root.findViewById(id("llAdjuntos"));
        btnModificar    = root.findViewById(id("btnModificar"));
        btnCancelar     = root.findViewById(id("btnCancelar"));
        btnReagendar    = root.findViewById(id("btnReagendar"));
        btnAdjuntar     = root.findViewById(id("btnAdjuntar"));

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

        getParentFragmentManager().setFragmentResultListener(
                "adjuntos_change", this, (req, bundle) -> loadAdjuntosAll(actividadId, citaId)
        );

        setTextOrDash(tvNombre, "Nombre actividad");
        if (tvTipoYPer != null) tvTipoYPer.setText("Tipo • Periodicidad");
        if (chFechaHora != null) chFechaHora.setText("dd/MM/yyyy • HH:mm");
        if (chLugar != null)      chLugar.setText("Lugar");
        if (llAdjuntos != null) {
            llAdjuntos.removeAllViews();
            addNoFilesRow();
        }

        db = FirebaseFirestore.getInstance();

        loadActividad(actividadId);
        loadCita(actividadId, citaId);
        loadAdjuntosAll(actividadId, citaId);
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

        setTextOrDash(tvNombre, nonEmpty(nombre, "Nombre actividad"));
        if (tvTipoYPer != null) tvTipoYPer.setText(nonEmpty(tipo, "—") + " • " + nonEmpty(periodicidad, "—"));

        setLabeled(tvTipo,         "Tipo: ",          nonEmpty(tipo, "—"));
        setLabeled(tvPeriodicidad, "Periodicidad: ",  nonEmpty(periodicidad, "—"));
        setLabeled(tvCupo,         "Cupo: ",          (cupo != null && cupo >= 0) ? String.valueOf(cupo) : "—");
        setLabeled(tvOferente,     "Oferente: ",      nonEmpty(oferentes, "—"));
        setLabeled(tvSocio,        "Socio comunitario: ", nonEmpty(socio, "—"));
        setLabeled(tvBeneficiarios,"Beneficiarios: ", nonEmpty(beneficiarios, "—"));
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
                    Instant.ofEpochMilli(ts.toDate().getTime()),
                    ZoneId.systemDefault()
            );
            String fechaStr = local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String horaStr  = local.format(DateTimeFormatter.ofPattern("HH:mm"));
            chFechaHora.setText(fechaStr + " • " + horaStr);
        }
        if (chLugar != null) chLugar.setText(nonEmpty(lugar, "Lugar"));
    }

    // ---------- Adjuntos ----------
    private interface Done { void run(); }

    private void loadAdjuntosAll(String actividadId, String citaId) {
        if (llAdjuntos == null) return;
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
                                            addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url,
                                                    TextUtils.isEmpty(id) ? null : id);
                                            any = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (any) return;

                        // 👇 orden: adjuntos → archivos → attachments
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
                                        addAdjuntoRow(nonEmpty(nombre, "(archivo)"), url,
                                                TextUtils.isEmpty(id) ? null : id);
                                        any = true;
                                    }
                                }
                            }
                        }
                    }
                    if (any) return;

                    // 👇 orden: adjuntos → archivos → attachments
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

    private void showPlaceholderIfEmpty() {
        if (llAdjuntos == null) return;
        if (llAdjuntos.getChildCount() == 0) addNoFilesRow();
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

        // 👇 NUEVO: log de adjunto agregado
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

    // ---------- utils ----------
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
                    String st = (t == null) ? "" : t.trim();
                    if (!st.isEmpty()) set.add(st);
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
    private void descargarConDownloadManager(String nombreArchivo, String url) {
        try {
            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (TextUtils.isEmpty(nombreArchivo)) nombreArchivo = nombreDesdeUrl(url);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombreArchivo);
            long enqId = dm.enqueue(req);
            toast("Descarga iniciada…");
            // 👇 NUEVO: log de descarga encolada
            Log.d(TAG, "Descarga encolada id=" + enqId + " | archivo=" + nombreArchivo);
        } catch (Exception e) {
            toast("No se pudo iniciar la descarga");
            // 👇 NUEVO: log de error
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

    private String getArg(String key) {
        return (getArguments() != null) ? getArguments().getString(key, "") : "";
    }
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
    private Long safeLong(Object v) { try { if (v instanceof Number) return ((Number) v).longValue(); } catch (Exception ignored) {} return null; }
}
