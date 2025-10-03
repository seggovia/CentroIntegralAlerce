package com.centroalerce.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.centroalerce.gestion.R;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ActivityFormFragment extends Fragment {

    private TextInputEditText etNombre, etCupo, etLugar, etOferente, etSocio, etFecha, etHora, etBeneficiarios;
    private TextInputLayout tilFecha, tilHora;
    private MaterialButtonToggleGroup tgPeriodicidad;
    private MaterialButton btnPuntual, btnPeriodica, btnCancelar, btnGuardar;
    private android.widget.AutoCompleteTextView acTipoActividad;

    private LinearLayout boxAdjuntos;
    private TextView tvAdjuntos;

    private final List<Timestamp> citasPeriodicas = new ArrayList<>();
    private boolean esPeriodica = false;

    private final List<Uri> attachmentUris = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // SAF – seleccionar varios archivos
    private final ActivityResultLauncher<String[]> pickFilesLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    attachmentUris.clear();
                    attachmentUris.addAll(uris);
                    tvAdjuntos.setText("Agregar archivos adjuntos  •  " + attachmentUris.size() + " seleccionado(s)");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activity_form, c, false);

        // Inputs
        etNombre = v.findViewById(R.id.etNombre);
        etCupo = v.findViewById(R.id.etCupo);
        etLugar = v.findViewById(R.id.etLugar);
        etOferente = v.findViewById(R.id.etOferente);
        etSocio = v.findViewById(R.id.etSocio);
        etBeneficiarios = v.findViewById(R.id.etBeneficiarios);
        etFecha = v.findViewById(R.id.etFecha);
        etHora = v.findViewById(R.id.etHora);
        tilFecha = (TextInputLayout) etFecha.getParent().getParent();
        tilHora = (TextInputLayout) etHora.getParent().getParent();

        acTipoActividad = v.findViewById(R.id.acTipoActividad);

        // Toggle
        tgPeriodicidad = v.findViewById(R.id.tgPeriodicidad);
        btnPuntual = v.findViewById(R.id.btnPuntual);
        btnPeriodica = v.findViewById(R.id.btnPeriodica);
        tgPeriodicidad.check(R.id.btnPuntual);

        // Botones
        btnCancelar = v.findViewById(R.id.btnCancelar);
        btnGuardar = v.findViewById(R.id.btnGuardar);
        btnGuardar.setEnabled(false);

        // Adjuntos UI
        boxAdjuntos = v.findViewById(R.id.boxAdjuntos);
        tvAdjuntos = v.findViewById(R.id.tvAdjuntos);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Dropdown “Tipo de actividad”
        String[] tipos = new String[]{
                "Capacitación", "Taller", "Charlas", "Atenciones",
                "Operativo en oficina", "Operativo rural", "Operativo",
                "Práctica profesional", "Diagnostico"
        };
        acTipoActividad.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, tipos));

        // Listeners
        etFecha.setOnClickListener(view -> onFechaClick());
        etHora.setOnClickListener(view -> showTimePickerPuntual());
        tgPeriodicidad.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            esPeriodica = (checkedId == R.id.btnPeriodica);
            aplicarModo();
            validarMinimos();
        });

        boxAdjuntos.setOnClickListener(v1 -> pickFilesLauncher.launch(new String[]{"*/*"}));

        TextWatcher watcher = new SimpleWatcher(this::validarMinimos);
        etNombre.addTextChangedListener(watcher);
        etFecha.addTextChangedListener(watcher);
        etHora.addTextChangedListener(watcher);

        btnCancelar.setOnClickListener(view -> Navigation.findNavController(view).popBackStack());
        btnGuardar.setOnClickListener(this::onGuardar);

        aplicarModo();
        return v;
    }

    // ---------- UX/Modo ----------
    private void aplicarModo() {
        if (esPeriodica) {
            etFecha.setText(null);
            etHora.setText(null);
            citasPeriodicas.clear();

            tilFecha.setHint("Agregar fecha + hora");
            tilHora.setHint("Se define al agregar cada fecha");

            etHora.setClickable(false);
            etHora.setFocusable(false);
            tilHora.setEndIconMode(TextInputLayout.END_ICON_NONE);
        } else {
            tilFecha.setHint("Fecha (AAAA-MM-DD)");
            tilHora.setHint("Hora (HH:mm)");

            etHora.setClickable(true);
            etHora.setFocusable(false);
        }
    }

    // ---------- Pickers ----------
    private void onFechaClick() {
        if (esPeriodica) {
            showPickerSecuencialYAgregar();
        } else {
            showDatePickerPuntual();
        }
    }

    private void showDatePickerPuntual() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(
                requireContext(),
                (picker, y, m, d) -> etFecha.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePickerPuntual() {
        if (esPeriodica) {
            Snackbar.make(requireView(), "En 'Periódica' la hora se define con cada fecha agregada.", Snackbar.LENGTH_SHORT).show();
            return;
        }
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(
                requireContext(),
                (picker, h, min) -> etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", h, min)),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true
        ).show();
    }

    /** Periódica: pide fecha+hora y agrega a la lista */
    private void showPickerSecuencialYAgregar() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(
                requireContext(),
                (picker, y, m, d) -> {
                    LocalDate ld = LocalDate.of(y, m + 1, d);
                    Calendar ch = Calendar.getInstance();
                    new TimePickerDialog(
                            requireContext(),
                            (timePicker, h, min) -> {
                                Timestamp ts = toStartAtTimestamp(ld, LocalTime.of(h, min));
                                citasPeriodicas.add(ts);
                                etFecha.setText("Fechas agregadas: " + citasPeriodicas.size());
                                Snackbar.make(requireView(),
                                        "Agregada: " + String.format(Locale.getDefault(),
                                                "%04d-%02d-%02d %02d:%02d",
                                                ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth(), h, min),
                                        Snackbar.LENGTH_SHORT).show();
                                validarMinimos();
                            },
                            ch.get(Calendar.HOUR_OF_DAY), ch.get(Calendar.MINUTE), true
                    ).show();
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    // ---------- Validaciones ----------
    private void validarMinimos() {
        // limpiar errores
        ((TextInputLayout) etNombre.getParent().getParent()).setError(null);
        tilFecha.setError(null);
        tilHora.setError(null);

        boolean nombreOk = !TextUtils.isEmpty(getText(etNombre));
        boolean ok;

        if (esPeriodica) {
            ok = nombreOk && !citasPeriodicas.isEmpty();
            if (!nombreOk) ((TextInputLayout) etNombre.getParent().getParent()).setError("Obligatorio");
            if (citasPeriodicas.isEmpty()) tilFecha.setError("Agrega al menos una fecha + hora");
        } else {
            boolean fechaOk = !TextUtils.isEmpty(getText(etFecha));
            boolean horaOk = !TextUtils.isEmpty(getText(etHora));
            ok = nombreOk && fechaOk && horaOk;
            if (!nombreOk) ((TextInputLayout) etNombre.getParent().getParent()).setError("Obligatorio");
            if (!fechaOk) tilFecha.setError("Requerido");
            if (!horaOk) tilHora.setError("Requerido");
        }
        btnGuardar.setEnabled(ok);
    }

    private boolean validarCupoOpcional() {
        String c = getText(etCupo);
        if (TextUtils.isEmpty(c)) return true;
        try {
            Integer.parseInt(c);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---------- Guardar ----------
    private void onGuardar(View root) {
        if (!validarCupoOpcional()) {
            Snackbar.make(root, "Cupo inválido", Snackbar.LENGTH_LONG).show();
            return;
        }

        btnGuardar.setEnabled(false);

        String nombre = getText(etNombre);
        if (TextUtils.isEmpty(nombre)) {
            Snackbar.make(root, "Ingresa el nombre", Snackbar.LENGTH_LONG).show();
            btnGuardar.setEnabled(true);
            return;
        }

        boolean modoPeriodica = (tgPeriodicidad.getCheckedButtonId() == R.id.btnPeriodica);
        String fecha = getText(etFecha);
        String hora = getText(etHora);

        if (!modoPeriodica && (TextUtils.isEmpty(fecha) || TextUtils.isEmpty(hora))) {
            Snackbar.make(root, "Completa Fecha y Hora", Snackbar.LENGTH_LONG).show();
            btnGuardar.setEnabled(true);
            return;
        }
        if (modoPeriodica && citasPeriodicas.isEmpty()) {
            Snackbar.make(root, "Agrega al menos una fecha + hora", Snackbar.LENGTH_LONG).show();
            btnGuardar.setEnabled(true);
            return;
        }

        String tipoActividad = acTipoActividad.getText() != null ? acTipoActividad.getText().toString().trim() : "";
        Integer cupo = null;
        try {
            String s = getText(etCupo);
            if (!s.isEmpty()) cupo = Integer.parseInt(s);
        } catch (Exception ignored) {
        }
        String lugar = getText(etLugar);
        String oferente = getText(etOferente);
        String socio = getText(etSocio);
        String beneficiarios = getText(etBeneficiarios);

        Timestamp startAtPuntual = null;
        if (!modoPeriodica) {
            startAtPuntual = toStartAtTimestamp(fecha, hora);
            if (startAtPuntual == null) {
                Snackbar.make(root, "Fecha u hora inválida", Snackbar.LENGTH_LONG).show();
                btnGuardar.setEnabled(true);
                return;
            }
        }

        // Datos final para lambdas
        final boolean modoPeriodicaFinal = modoPeriodica;
        final String lugarFinal = lugar;
        final String nombreFinal = nombre;
        final String tipoActividadFinal = tipoActividad;
        final Integer cupoFinal = cupo;
        final String oferenteFinal = oferente;
        final String socioFinal = socio;
        final String beneficiariosFinal = beneficiarios;
        final Timestamp startAtPuntualFinal = startAtPuntual;

        final ArrayList<Timestamp> aRevisar = new ArrayList<>();
        if (modoPeriodicaFinal) aRevisar.addAll(citasPeriodicas);
        else aRevisar.add(startAtPuntualFinal);

        chequearConflictos(lugarFinal, aRevisar)
                .addOnSuccessListener(conflicto -> {
                    if (Boolean.TRUE.equals(conflicto)) {
                        btnGuardar.setEnabled(true);
                        Snackbar.make(root, "Conflicto de horario/lugar detectado. Modifica la fecha/hora o el lugar.", Snackbar.LENGTH_LONG).show();
                    } else {
                        subirAdjuntosYGuardar(
                                root,
                                nombreFinal, tipoActividadFinal, cupoFinal,
                                oferenteFinal, socioFinal, beneficiariosFinal,
                                lugarFinal, modoPeriodicaFinal,
                                startAtPuntualFinal, aRevisar
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    // si falla el chequeo, avisamos pero seguimos guardando
                    Snackbar.make(root, "No se pudo verificar conflictos (" + e.getMessage() + "). Continuando…", Snackbar.LENGTH_LONG).show();
                    subirAdjuntosYGuardar(
                            root,
                            nombreFinal, tipoActividadFinal, cupoFinal,
                            oferenteFinal, socioFinal, beneficiariosFinal,
                            lugarFinal, modoPeriodicaFinal,
                            startAtPuntualFinal, aRevisar
                    );
                });
    }

    /** Sube a Storage y luego guarda actividad+citas en un batch */
    private void subirAdjuntosYGuardar(View root,
                                       String nombre, String tipoActividad, Integer cupo,
                                       String oferente, String socio, String beneficiarios,
                                       String lugar, boolean modoPeriodica,
                                       @Nullable Timestamp startAtPuntual,
                                       List<Timestamp> timestamps) {

        String activityId = db.collection("activities").document().getId();

        if (attachmentUris.isEmpty()) {
            escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                    beneficiarios, lugar, modoPeriodica, startAtPuntual, timestamps, new ArrayList<>());
            return;
        }

        StorageReference baseRef = storage.getReference().child("activities").child(activityId).child("attachments");

        // --- Mapeo indexado 1:1 para evitar desalineo ---
        List<Task<Uri>> urlTasks = new ArrayList<>();
        List<Uri> srcs = new ArrayList<>();
        for (Uri uri : attachmentUris) {
            String fileName = obtenerNombreArchivo(uri);
            StorageReference fileRef = baseRef.child(fileName);
            UploadTask up = fileRef.putFile(uri);
            Task<Uri> urlTask = up.continueWithTask(task -> {
                if (!task.isSuccessful()) throw task.getException();
                return fileRef.getDownloadUrl();
            });
            urlTasks.add(urlTask);
            srcs.add(uri);
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(urlTasks)
                .addOnSuccessListener(list -> {
                    List<Map<String, Object>> adj = new ArrayList<>();
                    for (int j = 0; j < list.size(); j++) {
                        Task<?> t = list.get(j);
                        if (t.isSuccessful() && t.getResult() instanceof Uri) {
                            Uri download = (Uri) t.getResult();
                            Uri src = srcs.get(j);
                            Map<String, Object> item = new HashMap<>();
                            item.put("name", obtenerNombreArchivo(src));
                            String mime = null;
                            try {
                                mime = requireContext().getContentResolver().getType(src);
                            } catch (Exception ignored) {}
                            if (mime != null) item.put("mime", mime);
                            item.put("url", download.toString());
                            adj.add(item);
                        }
                    }
                    if (adj.size() < attachmentUris.size()) {
                        Snackbar.make(root, "Algunos archivos no se pudieron subir. Se guardará el resto.", Snackbar.LENGTH_LONG).show();
                    }
                    escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                            beneficiarios, lugar, modoPeriodica, startAtPuntual, timestamps, adj);
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(root, "No se pudieron subir los adjuntos (" + e.getMessage() + "). Guardando sin archivos.", Snackbar.LENGTH_LONG).show();
                    escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                            beneficiarios, lugar, modoPeriodica, startAtPuntual, timestamps, new ArrayList<>());
                });
    }

    /** Escribe en Firestore: actividad + sus citas (NORMALIZADO) */
    private void escribirActividadYCitas(View root, String activityId,
                                         String nombre, String tipoActividad, Integer cupo,
                                         String oferente, String socio, String beneficiarios,
                                         String lugar, boolean modoPeriodica,
                                         @Nullable Timestamp startAtPuntual,
                                         List<Timestamp> timestamps,
                                         List<Map<String, Object>> adjuntos) {

        // Normalizar a listas
        List<String> oferentesList = splitToList(oferente);
        List<String> beneficiariosList = splitToList(beneficiarios);

        Map<String, Object> activityDoc = new HashMap<>();
        activityDoc.put("nombre", nombre);
        activityDoc.put("periodicidad", modoPeriodica ? "PERIODICA" : "PUNTUAL");

        if (!TextUtils.isEmpty(tipoActividad)) {
            activityDoc.put("tipo", tipoActividad);
            activityDoc.put("tipoActividad", tipoActividad);
        }

        if (cupo != null) activityDoc.put("cupo", cupo);

        if (!oferentesList.isEmpty()) {
            activityDoc.put("oferentes", oferentesList);
            activityDoc.put("oferente", oferentesList.get(0));
        }

        if (!TextUtils.isEmpty(socio)) activityDoc.put("socioComunitario", socio);

        if (!beneficiariosList.isEmpty()) {
            activityDoc.put("beneficiarios", beneficiariosList);
            activityDoc.put("beneficiariosTexto", TextUtils.join(", ", beneficiariosList));
        }

        if (!adjuntos.isEmpty()) activityDoc.put("adjuntos", adjuntos);
        activityDoc.put("diasAvisoPrevio", 1);
        activityDoc.put("createdAt", FieldValue.serverTimestamp());
        activityDoc.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        // Doc de actividad
        batch.set(db.collection("activities").document(activityId), activityDoc);

        // ⬇️ NUEVO: espejo de adjuntos en subcolección para que el detalle los encuentre siempre
        if (!adjuntos.isEmpty()) {
            for (Map<String, Object> a : adjuntos) {
                Map<String, Object> sub = new HashMap<>(a);
                sub.put("creadoEn", FieldValue.serverTimestamp());
                batch.set(
                        db.collection("activities").document(activityId)
                                .collection("adjuntos").document(),
                        sub
                );
            }
        }

        // Citas
        if (modoPeriodica) {
            for (Timestamp ts : timestamps) {
                Map<String, Object> cita = new HashMap<>();
                cita.put("startAt", ts);
                if (!TextUtils.isEmpty(lugar)) cita.put("lugarNombre", lugar);
                cita.put("estado", "PROGRAMADA");
                cita.put("titulo", nombre);
                batch.set(db.collection("activities").document(activityId)
                        .collection("citas").document(), cita);
            }
        } else {
            Map<String, Object> cita = new HashMap<>();
            cita.put("startAt", startAtPuntual);
            if (!TextUtils.isEmpty(lugar)) cita.put("lugarNombre", lugar);
            cita.put("estado", "PROGRAMADA");
            cita.put("titulo", nombre);
            batch.set(db.collection("activities").document(activityId)
                    .collection("citas").document(), cita);
        }

        batch.commit()
                .addOnSuccessListener(ignored -> {
                    Snackbar.make(root,
                            modoPeriodica ? "Actividad creada. " + timestamps.size() + " fechas programadas."
                                    : "Actividad creada y cita generada.",
                            Snackbar.LENGTH_LONG).show();
                    Navigation.findNavController(root).popBackStack();
                })
                .addOnFailureListener(e -> {
                    btnGuardar.setEnabled(true);
                    Snackbar.make(root, "Error al guardar: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
    }

    /** Revisa conflictos (mismo lugar y mismo startAt) sin índices compuestos y SIN fallar. */
    private com.google.android.gms.tasks.Task<Boolean> chequearConflictos(
            final String lugarNombre, final java.util.List<com.google.firebase.Timestamp> fechas) {

        if (TextUtils.isEmpty(lugarNombre) || fechas == null || fechas.isEmpty()) {
            // sin lugar/fechas, no bloquear
            return com.google.android.gms.tasks.Tasks.forResult(false);
        }

        java.util.List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> checks = new java.util.ArrayList<>();
        com.google.firebase.firestore.FirebaseFirestore fdb = com.google.firebase.firestore.FirebaseFirestore.getInstance();

        for (com.google.firebase.Timestamp ts : fechas) {
            // Subcolecciones "citas" (solo por startAt -> sin índice compuesto)
            checks.add(
                    fdb.collectionGroup("citas")
                            .whereEqualTo("startAt", ts)
                            .get()
            );
            // Si además usas una colección top-level "citas", probamos ambos nombres de campo
            checks.add(
                    fdb.collection("citas")
                            .whereEqualTo("startAt", ts)
                            .get()
            );
            checks.add(
                    fdb.collection("citas")
                            .whereEqualTo("fecha", ts) // por si el campo se llama "fecha" en top-level
                            .get()
            );
        }

        // IMPORTANTE: whenAllComplete NO falla si alguna tarea falla.
        return com.google.android.gms.tasks.Tasks.whenAllComplete(checks)
                .continueWith(task -> {
                    java.util.List<?> all = task.getResult();
                    if (all == null || all.isEmpty()) {
                        // No hubo resultados (o todo falló): no bloqueamos
                        return false;
                    }

                    for (Object o : all) {
                        if (!(o instanceof com.google.android.gms.tasks.Task)) continue;
                        com.google.android.gms.tasks.Task<?> t = (com.google.android.gms.tasks.Task<?>) o;

                        if (!t.isSuccessful()) {
                            // Ignora fallas individuales (permiso, colección inexistente, etc.)
                            continue;
                        }

                        Object res = t.getResult();
                        if (!(res instanceof com.google.firebase.firestore.QuerySnapshot)) continue;

                        com.google.firebase.firestore.QuerySnapshot qs = (com.google.firebase.firestore.QuerySnapshot) res;
                        for (com.google.firebase.firestore.DocumentSnapshot d : qs.getDocuments()) {
                            // Normaliza nombre de lugar
                            String lugarDoc = d.getString("lugarNombre");
                            if (lugarDoc == null) lugarDoc = d.getString("lugar");
                            if (lugarNombre.equals(lugarDoc)) {
                                return true; // ⚠️ conflicto
                            }
                        }
                    }
                    return false; // nada conflictivo en las tareas exitosas
                });
    }



    // ---------- Utilidades ----------
    @Nullable
    private Timestamp toStartAtTimestamp(@NonNull String yyyyMMdd, @NonNull String HHmm) {
        try {
            String[] parts = HHmm.split(":");
            LocalDate d = LocalDate.parse(yyyyMMdd);
            LocalTime t = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            return toStartAtTimestamp(d, t);
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private Timestamp toStartAtTimestamp(@NonNull LocalDate d, @NonNull LocalTime t) {
        ZonedDateTime zdt = d.atTime(t).atZone(ZoneId.systemDefault());
        return new Timestamp(Date.from(zdt.toInstant()));
    }

    private String obtenerNombreArchivo(Uri uri) {
        String last = uri.getLastPathSegment();
        if (last == null) return "archivo";
        int idx = last.lastIndexOf('/');
        return idx >= 0 ? last.substring(idx + 1) : last;
    }

    private String getText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
    /** Convierte "a, b; c | d" en lista ["a","b","c","d"], sin duplicados y con trim */
    private List<String> splitToList(String text) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] tokens = text.split("[,;|\\n]+");
        // usar Set para evitar duplicados pero conservando orden
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String t : tokens) {
            String s = t == null ? "" : t.trim();
            if (!s.isEmpty()) set.add(s);
        }
        out.addAll(set);
        return out;
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable onAfter;
        SimpleWatcher(Runnable onAfter) { this.onAfter = onAfter; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onAfter.run(); }
    }
}
