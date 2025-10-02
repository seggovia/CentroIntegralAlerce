package com.centroalerce.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.centroalerce.gestion.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActivityBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ACTIVITY_ID = "activityId";
    private static final String ARG_CITA_ID = "citaId"; // opcional

    public static ActivityBottomSheet newInstance(String activityId, @Nullable String citaId) {
        ActivityBottomSheet f = new ActivityBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_ACTIVITY_ID, activityId);
        if (citaId != null) b.putString(ARG_CITA_ID, citaId);
        f.setArguments(b);
        return f;
    }

    private String activityId, citaId;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // Views
    private TextView tvNombre, tvTipoYPeriodicidad, tvTipo, tvPeriodicidad, tvCupo, tvOferente, tvSocio, tvBeneficiarios;
    private TextView chFechaHora, chLugar;
    private LinearLayout llAdjuntos;

    // Adjuntar desde el modal
    private final List<Uri> nuevosAdjuntos = new ArrayList<>();
    private final ActivityResultLauncher<String[]> pickFiles =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    nuevosAdjuntos.clear();
                    nuevosAdjuntos.addAll(uris);
                    subirAdjuntosYMergear();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        View v = inflater.inflate(R.layout.bottomsheet_activity_detail, container, false);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        if (getArguments() != null) {
            activityId = getArguments().getString(ARG_ACTIVITY_ID);
            citaId     = getArguments().getString(ARG_CITA_ID);
        }

        bindViews(v);
        cargarActividadYcita();

        // ---- Acciones ----
        v.findViewById(R.id.btnModificar).setOnClickListener(x -> {
            Bundle args = new Bundle();
            args.putString("activityId", activityId);
            try {
                nav().navigate(R.id.activityEditFragment, args);
                dismiss();
            } catch (Exception e) {
                toast("No se pudo navegar a edición");
            }
        });

        v.findViewById(R.id.btnReagendar).setOnClickListener(x -> {
            Bundle args = new Bundle();
            args.putString("activityId", activityId);
            try {
                nav().navigate(R.id.activityRescheduleFragment, args);
                dismiss();
            } catch (Exception e) {
                toast("No se pudo navegar a reagendar");
            }
        });

        v.findViewById(R.id.btnAdjuntar).setOnClickListener(x -> pickFiles.launch(new String[]{"*/*"}));

        v.findViewById(R.id.btnCancelar).setOnClickListener(x ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cancelar actividad")
                        .setMessage("¿Seguro que deseas cancelar la actividad y todas sus citas?")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Sí, cancelar", (d, which) -> cancelarActividad(v))
                        .show()
        );

        return v;
    }

    private void bindViews(View v){
        tvNombre = v.findViewById(R.id.tvNombre);
        tvTipoYPeriodicidad = v.findViewById(R.id.tvTipoYPeriodicidad);
        chFechaHora = v.findViewById(R.id.chFechaHora);
        chLugar     = v.findViewById(R.id.chLugar);

        tvTipo = v.findViewById(R.id.tvTipo);
        tvPeriodicidad = v.findViewById(R.id.tvPeriodicidad);
        tvCupo = v.findViewById(R.id.tvCupo);
        tvOferente = v.findViewById(R.id.tvOferente);
        tvSocio = v.findViewById(R.id.tvSocio);
        tvBeneficiarios = v.findViewById(R.id.tvBeneficiarios);

        llAdjuntos = v.findViewById(R.id.llAdjuntos);
    }

    private void cargarActividadYcita() {
        db.collection("activities").document(activityId).get()
                .addOnSuccessListener(a -> {
                    if (!a.exists()) { toast("Actividad no encontrada"); return; }

                    String nombre = a.getString("nombre");
                    String tipo = a.getString("tipoActividad");
                    String periodicidad = a.getString("periodicidad"); // PUNTUAL / PERIODICA
                    Long cupo = a.getLong("cupo");
                    String oferente = a.getString("oferente");
                    String socio = a.getString("socioComunitario");
                    String beneficiarios = a.getString("beneficiariosTexto");

                    tvNombre.setText(notEmpty(nombre, "Actividad sin nombre"));
                    tvTipoYPeriodicidad.setText(join(nonNull(tipo), "•", readable(periodicidad)));
                    tvTipo.setText("Tipo: " + notEmpty(tipo, "—"));
                    tvPeriodicidad.setText("Periodicidad: " + readable(periodicidad));
                    tvCupo.setText("Cupo: " + (cupo!=null ? cupo : "—"));
                    tvOferente.setText("Oferente: " + notEmpty(oferente, "—"));
                    tvSocio.setText("Socio comunitario: " + notEmpty(socio, "—"));
                    tvBeneficiarios.setText("Beneficiarios: " + notEmpty(beneficiarios, "—"));

                    // 1) Campo en el doc: adjuntos o attachments
                    @SuppressWarnings("unchecked")
                    List<Map<String,Object>> adjDoc = (List<Map<String, Object>>) a.get("adjuntos");
                    if (adjDoc == null || adjDoc.isEmpty()) {
                        // fallback al alias "attachments"
                        //noinspection unchecked
                        adjDoc = (List<Map<String, Object>>) a.get("attachments");
                    }
                    if (adjDoc != null && !adjDoc.isEmpty()) {
                        renderAdjuntos(adjDoc);
                    } else {
                        // 2) Fallback: subcolecciones (adjuntos, attachments, archivos)
                        cargarAdjuntosDesdeSubcolecciones(a.getReference());
                    }

                    cargarCita(a);
                })
                .addOnFailureListener(e -> toast("Error cargando: " + e.getMessage()));
    }

    private void cargarCita(DocumentSnapshot act){
        if (!TextUtils.isEmpty(citaId)) {
            act.getReference().collection("citas").document(citaId).get()
                    .addOnSuccessListener(this::setCitaEnUI)
                    .addOnFailureListener(e -> toast("Error cita: " + e.getMessage()));
            return;
        }
        act.getReference().collection("citas")
                .orderBy("startAt", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) setCitaEnUI(qs.getDocuments().get(0));
                })
                .addOnFailureListener(e -> toast("Error citas: " + e.getMessage()));
    }

    private void setCitaEnUI(DocumentSnapshot c){
        Timestamp ts = c.getTimestamp("startAt");
        String lugar = c.getString("lugarNombre");
        chFechaHora.setText(ts != null ? fmt(ts) : "Sin fecha");
        chLugar.setText(!TextUtils.isEmpty(lugar) ? lugar : "Sin lugar");
    }

    // ====== Adjuntar + mergear en actividad ======
    private void subirAdjuntosYMergear() {
        if (nuevosAdjuntos.isEmpty()) return;

        StorageReference base = storage.getReference()
                .child("activities").child(activityId).child("attachments");

        List<com.google.android.gms.tasks.Task<Uri>> urlTasks = new ArrayList<>();

        for (Uri u : nuevosAdjuntos) {
            StorageReference ref = base.child(nombreArchivo(u));
            com.google.firebase.storage.UploadTask up = ref.putFile(u);
            urlTasks.add(up.continueWithTask(t -> {
                if (!t.isSuccessful()) throw t.getException();
                return ref.getDownloadUrl();
            }));
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(urlTasks)
                .addOnSuccessListener(urls -> {
                    db.collection("activities").document(activityId).get()
                            .addOnSuccessListener(a -> {
                                List<Map<String, Object>> adj = new ArrayList<>();
                                Object rawA = a.get("adjuntos");
                                if (rawA instanceof List) {
                                    for (Object it : (List<?>) rawA) {
                                        if (it instanceof Map) {
                                            adj.add(new HashMap<>((Map<String, Object>) it));
                                        }
                                    }
                                } else {
                                    // también aceptar 'attachments' legacy como base
                                    Object rawB = a.get("attachments");
                                    if (rawB instanceof List) {
                                        for (Object it : (List<?>) rawB) {
                                            if (it instanceof Map) {
                                                adj.add(new HashMap<>((Map<String, Object>) it));
                                            }
                                        }
                                    }
                                }

                                // Nuevos ítems (para escribir también en subcolecciones)
                                List<Map<String, Object>> nuevosItems = new ArrayList<>();

                                int n = Math.min(urls.size(), nuevosAdjuntos.size());
                                for (int i = 0; i < n; i++) {
                                    Uri dl = (Uri) urls.get(i);
                                    Uri src = nuevosAdjuntos.get(i);

                                    Map<String, Object> item = new HashMap<>();
                                    item.put("name", nombreArchivo(src));
                                    try {
                                        String mime = requireContext().getContentResolver().getType(src);
                                        if (mime != null) item.put("mime", mime);
                                    } catch (Exception ignored) {}
                                    item.put("url", dl.toString());

                                    adj.add(item);
                                    nuevosItems.add(item);
                                }

                                final List<Map<String, Object>> adjFinal = new ArrayList<>(adj);

                                // Actualiza array + alias, y espejo en subcolecciones
                                WriteBatch batch = db.batch();
                                DocumentReference actRef = a.getReference();
                                batch.update(
                                        actRef,
                                        "adjuntos", adjFinal,
                                        "attachments", adjFinal,
                                        "updatedAt", FieldValue.serverTimestamp()
                                );
                                for (Map<String, Object> it : nuevosItems) {
                                    Map<String, Object> sub = new HashMap<>(it);
                                    sub.put("creadoEn", FieldValue.serverTimestamp());
                                    batch.set(actRef.collection("adjuntos").document(), sub);
                                    batch.set(actRef.collection("attachments").document(), sub);
                                }

                                batch.commit()
                                        .addOnSuccessListener(x -> {
                                            toast("Adjuntos agregados");
                                            renderAdjuntos(adjFinal);
                                            nuevosAdjuntos.clear();
                                        })
                                        .addOnFailureListener(e -> toast("Error guardando adjuntos: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> toast("Error leyendo actividad: " + e.getMessage()));
                })
                .addOnFailureListener(e -> toast("Error subiendo: " + e.getMessage()));
    }

    private void cancelarActividad(View root){
        final DocumentReference actRef = db.collection("activities").document(activityId);
        actRef.update("estado","CANCELADA","updatedAt",FieldValue.serverTimestamp())
                .addOnSuccessListener(x -> actRef.collection("citas").get()
                        .addOnSuccessListener(qs -> {
                            WriteBatch batch = db.batch();
                            for (DocumentSnapshot d: qs) batch.update(d.getReference(),"estado","CANCELADA");
                            batch.commit()
                                    .addOnSuccessListener(ok -> {
                                        Snackbar.make(root,"Actividad cancelada",Snackbar.LENGTH_LONG).show();
                                        dismiss();
                                    })
                                    .addOnFailureListener(e -> toast("Error al cancelar citas: " + e.getMessage()));
                        })
                        .addOnFailureListener(e -> toast("Error cargando citas: " + e.getMessage())))
                .addOnFailureListener(e -> toast("Error al cancelar: " + e.getMessage()));
    }

    // ====== helpers UI / carga de adjuntos ======
    private void renderAdjuntos(@Nullable List<Map<String,Object>> adj){
        llAdjuntos.removeAllViews();
        if (adj == null || adj.isEmpty()) {
            addAdjuntoRow("Sin adjuntos", null);
            return;
        }
        for (Map<String,Object> it : adj) {
            String name = it.get("name") != null ? it.get("name").toString()
                    : (it.get("nombre") != null ? it.get("nombre").toString() : "archivo");
            String url  = it.get("url")  != null ? it.get("url").toString()  : null;
            addAdjuntoRow(name, url);
        }
    }

    /** Si el campo del doc está vacío, intenta en subcolecciones: adjuntos, attachments y archivos. */
    private void cargarAdjuntosDesdeSubcolecciones(DocumentReference actRef) {
        // Intentar en orden: adjuntos -> attachments -> archivos
        actRef.collection("adjuntos").get()
                .addOnSuccessListener(q -> {
                    if (q != null && !q.isEmpty()) {
                        renderAdjuntos(mapDocs(q));
                    } else {
                        actRef.collection("attachments").get()
                                .addOnSuccessListener(q2 -> {
                                    if (q2 != null && !q2.isEmpty()) {
                                        renderAdjuntos(mapDocs(q2));
                                    } else {
                                        actRef.collection("archivos").get()
                                                .addOnSuccessListener(q3 -> {
                                                    if (q3 != null && !q3.isEmpty()) {
                                                        renderAdjuntos(mapDocs(q3));
                                                    } else {
                                                        renderAdjuntos(new ArrayList<>());
                                                    }
                                                })
                                                .addOnFailureListener(e -> renderAdjuntos(new ArrayList<>()));
                                    }
                                })
                                .addOnFailureListener(e -> renderAdjuntos(new ArrayList<>()));
                    }
                })
                .addOnFailureListener(e -> renderAdjuntos(new ArrayList<>()));
    }

    private List<Map<String,Object>> mapDocs(@NonNull QuerySnapshot qs){
        List<Map<String,Object>> list = new ArrayList<>();
        for (DocumentSnapshot d : qs.getDocuments()) {
            Map<String,Object> m = new HashMap<>();
            String name = d.getString("name");
            if (TextUtils.isEmpty(name)) name = d.getString("nombre");
            String url = d.getString("url");
            if (!TextUtils.isEmpty(name)) m.put("name", name);
            if (!TextUtils.isEmpty(url)) m.put("url", url);
            if (!m.isEmpty()) list.add(m);
        }
        return list;
    }

    private void addAdjuntoRow(String label, @Nullable String url){
        TextView tv = new TextView(requireContext());
        tv.setText("• " + label);
        tv.setTextColor(0xFF111827);
        tv.setTextSize(14);
        tv.setPadding(0,8,0,0);
        if (url != null) {
            tv.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        }
        llAdjuntos.addView(tv);
    }

    private static String nombreArchivo(Uri uri){
        String last = uri.getLastPathSegment(); if (last==null) return "archivo";
        int i = last.lastIndexOf('/'); return i>=0 ? last.substring(i+1) : last;
    }

    private static String fmt(Timestamp ts){
        Date d = ts.toDate();
        return android.text.format.DateFormat.format("dd/MM/yyyy • HH:mm", d).toString();
    }

    private static String notEmpty(String s, String fallback){ return TextUtils.isEmpty(s) ? fallback : s; }
    private static String nonNull(String s){ return s==null? "—": s; }
    private static String join(String a, String sep, String b){
        if (TextUtils.isEmpty(a) && TextUtils.isEmpty(b)) return "—";
        if (TextUtils.isEmpty(a)) return b;
        if (TextUtils.isEmpty(b)) return a;
        return a + " " + sep + " " + b;
    }
    private static String readable(String periodicidad){
        if ("PERIODICA".equalsIgnoreCase(periodicidad)) return "Periódica";
        if ("PUNTUAL".equalsIgnoreCase(periodicidad))   return "Puntual";
        return "—";
    }

    private void toast(String m){
        View anchor = getView();
        if (anchor == null) anchor = requireActivity().findViewById(android.R.id.content);
        Snackbar.make(anchor, m, Snackbar.LENGTH_LONG).show();
    }

    /** Obtiene el NavController del NavHost del Activity (@id/nav_host). */
    private NavController nav() {
        NavHostFragment host = (NavHostFragment)
                requireActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) throw new IllegalStateException("No se encontró @id/nav_host");
        return host.getNavController();
    }
}
