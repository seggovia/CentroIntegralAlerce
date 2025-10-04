// ActivityFormFragment.java
package com.centroalerce.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.AutoCompleteTextView;

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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ActivityFormFragment extends Fragment {

    // EditTexts que SÍ existen en tu XML
    private TextInputEditText etNombre, etCupo, etFecha, etHora, etBeneficiarios;
    private TextInputLayout   tilFecha, tilHora;

    private MaterialButtonToggleGroup tgPeriodicidad;
    private MaterialButton btnPuntual, btnPeriodica, btnCancelar, btnGuardar;

    // Combos que existen en tu XML
    private AutoCompleteTextView acTipoActividad;
    private AutoCompleteTextView acLugar, acOferente, acSocio, acProyecto;

    // Adjuntos
    private com.google.android.material.card.MaterialCardView boxAdjuntos;
    private TextView tvAdjuntos;

    private final List<Timestamp> citasPeriodicas = new ArrayList<>();
    private boolean esPeriodica = false;

    private final List<Uri> attachmentUris = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseStorage storage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (db == null) db = FirebaseFirestore.getInstance();
        if (storage == null) storage = FirebaseStorage.getInstance();
    }

    // SAF – seleccionar varios archivos
    private final ActivityResultLauncher<String[]> pickFilesLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    for (Uri u : uris) {
                        try {
                            requireContext().getContentResolver()
                                    .takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {}
                    }
                    attachmentUris.clear();
                    attachmentUris.addAll(uris);
                    tvAdjuntos.setText("Agregar archivos adjuntos  •  " + attachmentUris.size() + " seleccionado(s)");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activity_form, c, false);

        // Inputs del layout
        etNombre         = v.findViewById(R.id.etNombre);
        etCupo           = v.findViewById(R.id.etCupo);
        etBeneficiarios  = v.findViewById(R.id.etBeneficiarios);
        etFecha          = v.findViewById(R.id.etFecha);
        etHora           = v.findViewById(R.id.etHora);
        tilFecha         = (TextInputLayout) etFecha.getParent().getParent();
        tilHora          = (TextInputLayout) etHora.getParent().getParent();

        // Combos del layout
        acTipoActividad  = v.findViewById(R.id.acTipoActividad);
        acLugar          = v.findViewById(R.id.acLugar);
        acOferente       = v.findViewById(R.id.acOferente);
        acSocio          = v.findViewById(R.id.acSocio);
        acProyecto       = v.findViewById(R.id.acProyecto);

        // Toggle
        tgPeriodicidad   = v.findViewById(R.id.tgPeriodicidad);
        btnPuntual       = v.findViewById(R.id.btnPuntual);
        btnPeriodica     = v.findViewById(R.id.btnPeriodica);
        tgPeriodicidad.check(R.id.btnPuntual);

        // Botones
        btnCancelar      = v.findViewById(R.id.btnCancelar);
        btnGuardar       = v.findViewById(R.id.btnGuardar);
        btnGuardar.setEnabled(false);

        // Adjuntos UI
        boxAdjuntos      = v.findViewById(R.id.boxAdjuntos);
        tvAdjuntos       = v.findViewById(R.id.tvAdjuntos);

        db       = FirebaseFirestore.getInstance();
        storage  = FirebaseStorage.getInstance();

        // Configurar dropdowns
        setupDropdownBehavior(acTipoActividad);
        setupDropdownBehavior(acLugar);
        setupDropdownBehavior(acOferente);
        setupDropdownBehavior(acSocio);
        setupDropdownBehavior(acProyecto);

        // Cargar catálogos
        cargarTiposActividad();
        cargarLugares();
        cargarOferentes();
        cargarSocios();       // ← ⭐ NUEVO (SOCIO) robusto
        cargarProyectos();

        // Fallback tipos
        if (acTipoActividad != null && acTipoActividad.getAdapter() == null) {
            android.content.Context ctx = getContext();
            if (ctx != null) {
                String[] tiposFallback = new String[]{
                        "Capacitación", "Taller", "Charlas", "Atenciones",
                        "Operativo en oficina", "Operativo rural", "Operativo",
                        "Práctica profesional", "Diagnostico"
                };
                acTipoActividad.setAdapter(new ArrayAdapter<>(ctx,
                        android.R.layout.simple_list_item_1, tiposFallback));
            }
        }

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

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded() || getView() == null) return;
        recargarCombos();
    }

    private void recargarCombos() {
        cargarTiposActividad();
        cargarLugares();
        cargarOferentes();
        cargarSocios();     // ← ⭐ NUEVO (SOCIO) vuelve a recargar al regresar
        cargarProyectos();
    }

    // ---------- Cargar catálogos ----------
    private void cargarLugares() {
        db.collection("lugares").orderBy("nombre")
                .get()
                .addOnSuccessListener(qs -> {
                    if (!isAdded()) return;
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;
                        String nombre = d.getString("nombre");
                        if (nombre != null && !nombre.trim().isEmpty()) items.add(nombre.trim());
                    }
                    setComboAdapter(acLugar, items);
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("CATALOG", "lugares: " + e.getMessage(), e));
    }

    private void cargarProyectos() {
        db.collection("proyectos").orderBy("nombre")
                .get()
                .addOnSuccessListener(qs -> {
                    if (!isAdded()) return;
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;
                        String nombre = d.getString("nombre");
                        if (nombre != null && !nombre.trim().isEmpty()) items.add(nombre.trim());
                    }
                    setComboAdapter(acProyecto, items);
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("CATALOG", "proyectos: " + e.getMessage(), e));
    }

    // Picker de nombre genérico
    private interface DocNamePicker { @Nullable String pick(DocumentSnapshot d); }

    private void loadComboWithFallback(
            @NonNull List<String> collectionCandidates,
            @NonNull AutoCompleteTextView combo,
            @NonNull String humanLabelForErrors,
            @Nullable DocNamePicker customPicker // ← ⭐ NUEVO (SOCIO) permite picker específico
    ) {
        if (!isAdded()) return;

        final DocNamePicker defaultPicker = d -> {
            String nombre   = d.getString("nombre");
            String docente  = d.getString("docenteResponsable");
            String display  = d.getString("displayName");
            String titulo   = d.getString("titulo");
            String name     = d.getString("name");

            String base = !TextUtils.isEmpty(nombre) ? nombre :
                    !TextUtils.isEmpty(display) ? display :
                            !TextUtils.isEmpty(titulo)  ? titulo  :
                                    !TextUtils.isEmpty(name)    ? name    :
                                            !TextUtils.isEmpty(docente) ? docente : null;

            if (base == null) return null;
            if (!TextUtils.isEmpty(nombre) && !TextUtils.isEmpty(docente)) {
                return (nombre.trim() + " — " + docente.trim());
            }
            return base.trim();
        };

        DocNamePicker picker = (customPicker != null) ? customPicker : defaultPicker;
        tryLoadNextCollection(collectionCandidates, 0, combo, humanLabelForErrors, picker);
    }

    // Sobrecarga para casos genéricos (sin picker custom)
    private void loadComboWithFallback(
            @NonNull List<String> collectionCandidates,
            @NonNull AutoCompleteTextView combo,
            @NonNull String humanLabelForErrors
    ) {
        loadComboWithFallback(collectionCandidates, combo, humanLabelForErrors, null);
    }

    private void tryLoadNextCollection(
            @NonNull List<String> candidates, int index,
            @NonNull AutoCompleteTextView combo,
            @NonNull String label,
            @NonNull DocNamePicker namePicker
    ) {
        if (index >= candidates.size()) {
            setComboAdapter(combo, new ArrayList<>());
            Snackbar.make(requireView(), "No pude cargar " + label, Snackbar.LENGTH_LONG).show();
            return;
        }

        final String col = candidates.get(index);

        db.collection(col).whereEqualTo("activo", true).get()
                .addOnSuccessListener(qs -> {
                    List<String> items = mapNames(qs.getDocuments(), namePicker);
                    if (!items.isEmpty()) { setComboAdapter(combo, items); return; }

                    db.collection(col).get()
                            .addOnSuccessListener(qs2 -> {
                                List<String> items2 = mapNames(qs2.getDocuments(), namePicker);
                                if (!items2.isEmpty()) { setComboAdapter(combo, items2); return; }

                                db.collection(col).orderBy("nombre").whereEqualTo("activo", true).get()
                                        .addOnSuccessListener(qs3 -> {
                                            List<String> items3 = mapNames(qs3.getDocuments(), namePicker);
                                            if (!items3.isEmpty()) { setComboAdapter(combo, items3); return; }

                                            db.collection(col).orderBy("nombre").get()
                                                    .addOnSuccessListener(qs4 -> {
                                                        List<String> items4 = mapNames(qs4.getDocuments(), namePicker);
                                                        if (!items4.isEmpty()) { setComboAdapter(combo, items4); }
                                                        else { tryLoadNextCollection(candidates, index + 1, combo, label, namePicker); }
                                                    })
                                                    .addOnFailureListener(e4 -> {
                                                        android.util.Log.e("CATALOG", col + " orderBy(nombre): " + e4.getMessage(), e4);
                                                        tryLoadNextCollection(candidates, index + 1, combo, label, namePicker);
                                                    });

                                        })
                                        .addOnFailureListener(e3 -> {
                                            android.util.Log.e("CATALOG", col + " orderBy+activo: " + e3.getMessage(), e3);
                                            tryLoadNextCollection(candidates, index + 1, combo, label, namePicker);
                                        });
                            })
                            .addOnFailureListener(e2 -> {
                                android.util.Log.e("CATALOG", col + " get(): " + e2.getMessage(), e2);
                                tryLoadNextCollection(candidates, index + 1, combo, label, namePicker);
                            });
                })
                .addOnFailureListener(e1 -> {
                    android.util.Log.e("CATALOG", col + " activo==true: " + e1.getMessage(), e1);
                    tryLoadNextCollection(candidates, index + 1, combo, label, namePicker);
                });
    }

    @NonNull
    private List<String> mapNames(@NonNull List<DocumentSnapshot> docs, @NonNull DocNamePicker picker) {
        List<String> out = new ArrayList<>();
        for (DocumentSnapshot d : docs) {
            Boolean activo = d.getBoolean("activo");
            if (activo != null && !activo) continue;
            String n = picker.pick(d);
            if (!TextUtils.isEmpty(n)) out.add(n);
        }
        return out;
    }

    private void setEmptyState(@Nullable AutoCompleteTextView combo, @NonNull String hint) {
        if (combo == null || !isAdded()) return;
        android.content.Context ctx = getContext();
        if (ctx == null) return;
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, new ArrayList<>());
        combo.setAdapter(ad);
        View p = (View) combo.getParent();
        if (p != null) p = (View) p.getParent();
        if (p instanceof TextInputLayout) ((TextInputLayout) p).setHelperText(hint);
    }

    private void cargarTiposActividad() {
        List<String> cols = new ArrayList<>();
        cols.add("tipos_actividad");
        cols.add("tiposActividad");
        cols.add("tipo_actividad");
        cols.add("tipoActividad");
        loadComboWithFallback(cols, acTipoActividad, "tipos de actividad");
    }

    private void cargarOferentes() {
        List<String> cols = new ArrayList<>();
        cols.add("oferentes");
        cols.add("Oferentes");
        cols.add("oferente");
        cols.add("Oferente");
        loadComboWithFallback(cols, acOferente, "oferentes"); // usa picker por defecto (con docenteResponsable)
    }

    // ⭐ NUEVO (SOCIO): Picker específico para socios comunitarios
    // Reemplaza COMPLETO tu método cargarSocios() por este:
    private void cargarSocios() {
        if (!isAdded()) return;

        // 1) intenta como en tu SociosFragment (root/socios + orderBy nombre)
        db.collection("socios").orderBy("nombre")
                .get()
                .addOnSuccessListener(qs -> {
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue; // si tienes flag, respétalo
                        String nombre = d.getString("nombre");

                        // Fallbacks muy comunes por si algunos docs viejos no tienen "nombre"
                        if (TextUtils.isEmpty(nombre)) nombre = d.getString("organizacion");
                        if (TextUtils.isEmpty(nombre)) nombre = d.getString("institucion");
                        if (TextUtils.isEmpty(nombre)) nombre = d.getString("razonSocial");
                        if (TextUtils.isEmpty(nombre)) nombre = d.getString("displayName");
                        if (TextUtils.isEmpty(nombre)) nombre = d.getString("name");
                        if (TextUtils.isEmpty(nombre)) nombre = d.getString("titulo");

                        if (!TextUtils.isEmpty(nombre)) items.add(nombre.trim());
                    }
                    setComboAdapter(acSocio, items);
                })
                .addOnFailureListener(e -> {
                    // 2) si falla el índice/orden, prueba sin orderBy
                    android.util.Log.w("CATALOG", "socios orderBy(nombre) falló: " + e.getMessage() + " — reintentando sin ordenar");
                    db.collection("socios").get()
                            .addOnSuccessListener(qs2 -> {
                                List<String> items = new ArrayList<>();
                                for (DocumentSnapshot d : qs2.getDocuments()) {
                                    Boolean activo = d.getBoolean("activo");
                                    if (activo != null && !activo) continue;
                                    String nombre = d.getString("nombre");
                                    if (TextUtils.isEmpty(nombre)) nombre = d.getString("organizacion");
                                    if (TextUtils.isEmpty(nombre)) nombre = d.getString("institucion");
                                    if (TextUtils.isEmpty(nombre)) nombre = d.getString("razonSocial");
                                    if (TextUtils.isEmpty(nombre)) nombre = d.getString("displayName");
                                    if (TextUtils.isEmpty(nombre)) nombre = d.getString("name");
                                    if (TextUtils.isEmpty(nombre)) nombre = d.getString("titulo");
                                    if (!TextUtils.isEmpty(nombre)) items.add(nombre.trim());
                                }
                                setComboAdapter(acSocio, items);
                            })
                            .addOnFailureListener(e2 -> {
                                android.util.Log.e("CATALOG", "socios get(): " + e2.getMessage(), e2);
                                setComboAdapter(acSocio, new ArrayList<>()); // deja adapter vacío y helper
                                if (isAdded()) {
                                    com.google.android.material.snackbar.Snackbar
                                            .make(requireView(), "No pude cargar socios comunitarios", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                            .show();
                                }
                            });
                });
    }


    private void setupDropdownBehavior(@Nullable AutoCompleteTextView combo) {
        if (combo == null) return;

        combo.setThreshold(0);
        linkEndIconToDropdown(combo);

        combo.setOnClickListener(v -> {
            if (combo.getAdapter() != null && combo.getAdapter().getCount() > 0) {
                combo.showDropDown();
            }
        });

        combo.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && combo.getAdapter() != null && combo.getAdapter().getCount() > 0) {
                combo.showDropDown();
            }
        });
    }

    private void setComboAdapter(@Nullable AutoCompleteTextView combo, @NonNull List<String> items) {
        if (combo == null || !isAdded()) return;
        android.content.Context ctx = getContext();
        if (ctx == null) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, items);
        combo.setAdapter(adapter);

        if (items.isEmpty()) {
            View p = (View) combo.getParent();
            if (p != null) p = (View) p.getParent();
            if (p instanceof TextInputLayout) {
                ((TextInputLayout) p).setHelperText("Sin datos disponibles");
            }
            return;
        }

        if (combo.hasFocus()) {
            combo.showDropDown();
        }
    }

    private void linkEndIconToDropdown(@Nullable AutoCompleteTextView ac) {
        if (ac == null) return;
        View p = (View) ac.getParent();
        if (p != null) p = (View) p.getParent();
        if (p instanceof TextInputLayout) {
            TextInputLayout til = (TextInputLayout) p;
            til.setEndIconOnClickListener(v -> {
                if (ac.getAdapter() != null && ac.getAdapter().getCount() > 0) {
                    ac.showDropDown();
                }
            });
        }
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
            boolean horaOk  = !TextUtils.isEmpty(getText(etHora));
            ok = nombreOk && fechaOk && horaOk;
            if (!nombreOk) ((TextInputLayout) etNombre.getParent().getParent()).setError("Obligatorio");
            if (!fechaOk)  tilFecha.setError("Requerido");
            if (!horaOk)   tilHora.setError("Requerido");
        }
        btnGuardar.setEnabled(ok);
    }

    private boolean validarCupoOpcional() {
        String c = getText(etCupo);
        if (TextUtils.isEmpty(c)) return true;
        try { Integer.parseInt(c); return true; }
        catch (NumberFormatException e) { return false; }
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
        String hora  = getText(etHora);

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

        String tipoActividad = getText(acTipoActividad);
        Integer cupo = null;
        try {
            String s = getText(etCupo);
            if (!s.isEmpty()) cupo = Integer.parseInt(s);
        } catch (Exception ignored) { }

        String lugar         = getText(acLugar);
        String oferente      = getText(acOferente);
        String socio         = getText(acSocio);
        String beneficiarios = getText(etBeneficiarios);
        String proyecto      = getText(acProyecto);

        Timestamp startAtPuntual = null;
        if (!modoPeriodica) {
            startAtPuntual = toStartAtTimestamp(fecha, hora);
            if (startAtPuntual == null) {
                Snackbar.make(root, "Fecha u hora inválida", Snackbar.LENGTH_LONG).show();
                btnGuardar.setEnabled(true);
                return;
            }
        }

        final boolean   modoPeriodicaFinal   = modoPeriodica;
        final String    lugarFinal           = lugar;
        final String    nombreFinal          = nombre;
        final String    tipoActividadFinal   = tipoActividad;
        final Integer   cupoFinal            = cupo;
        final String    oferenteFinal        = oferente;
        final String    socioFinal           = socio;
        final String    beneficiariosFinal   = beneficiarios;
        final String    proyectoFinal        = proyecto;
        final Timestamp startAtPuntualFinal  = startAtPuntual;

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
                                startAtPuntualFinal, aRevisar,
                                proyectoFinal
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(root, "No se pudo verificar conflictos (" + e.getMessage() + "). Continuando…", Snackbar.LENGTH_LONG).show();
                    subirAdjuntosYGuardar(
                            root,
                            nombreFinal, tipoActividadFinal, cupoFinal,
                            oferenteFinal, socioFinal, beneficiariosFinal,
                            lugarFinal, modoPeriodicaFinal,
                            startAtPuntualFinal, aRevisar,
                            proyectoFinal
                    );
                });
    }

    private void subirAdjuntosYGuardar(View root,
                                       String nombre, String tipoActividad, Integer cupo,
                                       String oferente, String socio, String beneficiarios,
                                       String lugar, boolean modoPeriodica,
                                       @Nullable Timestamp startAtPuntual,
                                       List<Timestamp> timestamps,
                                       @Nullable String proyecto) {

        String activityId = db.collection("activities").document().getId();

        if (attachmentUris.isEmpty()) {
            escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                    beneficiarios, lugar, modoPeriodica, startAtPuntual, timestamps, new ArrayList<>(), proyecto);
            return;
        }

        StorageReference baseRef = storage.getReference().child("activities").child(activityId).child("attachments");

        List<Task<Uri>> urlTasks = new ArrayList<>();
        List<Uri> srcs = new ArrayList<>();
        for (Uri uri : attachmentUris) {
            String fileName = getDisplayName(uri);
            String mime = getMime(uri);

            StorageReference fileRef = baseRef.child(fileName);

            com.google.firebase.storage.StorageMetadata md =
                    new com.google.firebase.storage.StorageMetadata.Builder()
                            .setContentType(mime != null ? mime : "application/octet-stream")
                            .build();

            UploadTask up = fileRef.putFile(uri, md);
            up.addOnFailureListener(e ->
                    android.util.Log.e("FS-UPLOAD", "Falló subir " + fileName + ": " + e.getMessage(), e));

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
                        Task<?> t = (Task<?>) list.get(j);
                        if (t.isSuccessful() && t.getResult() instanceof Uri) {
                            Uri download = (Uri) t.getResult();
                            Uri src = srcs.get(j);
                            Map<String, Object> item = new HashMap<>();
                            item.put("name", getDisplayName(src));
                            String mime = getMime(src);
                            if (mime != null) item.put("mime", mime);
                            item.put("url", download.toString());
                            adj.add(item);
                        }
                    }
                    if (adj.size() < attachmentUris.size()) {
                        Snackbar.make(root, "Algunos archivos no se pudieron subir. Se guardará el resto.", Snackbar.LENGTH_LONG).show();
                    }
                    escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                            beneficiarios, lugar, modoPeriodica, startAtPuntual, timestamps, adj, proyecto);
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(root, "No se pudieron subir los adjuntos (" + e.getMessage() + "). Guardando sin archivos.", Snackbar.LENGTH_LONG).show();
                    escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                            beneficiarios, lugar, modoPeriodica, startAtPuntual, timestamps, new ArrayList<>(), proyecto);
                });
    }

    private void escribirActividadYCitas(View root, String activityId,
                                         String nombre, String tipoActividad, Integer cupo,
                                         String oferente, String socio, String beneficiarios,
                                         String lugar, boolean modoPeriodica,
                                         @Nullable Timestamp startAtPuntual,
                                         List<Timestamp> timestamps,
                                         List<Map<String, Object>> adjuntos,
                                         @Nullable String proyecto) {

        List<String> oferentesList = splitToList(oferente);
        List<String> beneficiariosList = splitToList(beneficiarios);

        Map<String, Object> activityDoc = new HashMap<>();
        activityDoc.put("nombre", nombre);
        activityDoc.put("periodicidad", modoPeriodica ? "PERIODICA" : "PUNTUAL");

        if (!TextUtils.isEmpty(tipoActividad)) {
            activityDoc.put("tipo", tipoActividad);
            activityDoc.put("tipoActividad", tipoActividad);
        }
        if (!TextUtils.isEmpty(proyecto)) {
            activityDoc.put("proyecto", proyecto);
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

        if (!TextUtils.isEmpty(lugar)) activityDoc.put("lugarNombre", lugar);

        if (!adjuntos.isEmpty()) activityDoc.put("adjuntos", adjuntos);
        activityDoc.put("diasAvisoPrevio", 1);
        activityDoc.put("createdAt", FieldValue.serverTimestamp());
        activityDoc.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(db.collection("activities").document(activityId), activityDoc);

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

        List<com.google.firebase.firestore.DocumentReference> citaRefs = new ArrayList<>();

        if (modoPeriodica) {
            for (Timestamp ts : timestamps) {
                Map<String, Object> cita = new HashMap<>();
                cita.put("startAt", ts);
                if (!TextUtils.isEmpty(lugar)) cita.put("lugarNombre", lugar);
                cita.put("estado", "PROGRAMADA");
                cita.put("titulo", nombre);

                com.google.firebase.firestore.DocumentReference citaRef =
                        db.collection("activities").document(activityId)
                                .collection("citas").document();
                citaRefs.add(citaRef);
                batch.set(citaRef, cita);
            }
        } else {
            Map<String, Object> cita = new HashMap<>();
            cita.put("startAt", startAtPuntual);
            if (!TextUtils.isEmpty(lugar)) cita.put("lugarNombre", lugar);
            cita.put("estado", "PROGRAMADA");
            cita.put("titulo", nombre);

            com.google.firebase.firestore.DocumentReference citaRef =
                    db.collection("activities").document(activityId)
                            .collection("citas").document();
            citaRefs.add(citaRef);
            batch.set(citaRef, cita);
        }

        if (!adjuntos.isEmpty()) {
            for (com.google.firebase.firestore.DocumentReference citaRef : citaRefs) {
                for (Map<String, Object> a : adjuntos) {
                    Map<String, Object> sub = new HashMap<>(a);
                    sub.put("creadoEn", FieldValue.serverTimestamp());
                    batch.set(citaRef.collection("adjuntos").document(), sub);
                }
            }
        }

        batch.commit()
                .addOnSuccessListener(ignored -> {
                    android.util.Log.d("FS",
                            "Actividad " + activityId + " creada con " + citaRefs.size() +
                                    " cita(s) y " + (adjuntos == null ? 0 : adjuntos.size()) + " adjunto(s)");
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

    private com.google.android.gms.tasks.Task<Boolean> chequearConflictos(
            final String lugarNombre, final List<com.google.firebase.Timestamp> fechas) {

        if (TextUtils.isEmpty(lugarNombre) || fechas == null || fechas.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forResult(false);
        }

        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> checks = new ArrayList<>();
        FirebaseFirestore fdb = FirebaseFirestore.getInstance();

        for (com.google.firebase.Timestamp ts : fechas) {
            checks.add(fdb.collectionGroup("citas").whereEqualTo("startAt", ts).get());
            checks.add(fdb.collection("citas").whereEqualTo("startAt", ts).get());
            checks.add(fdb.collection("citas").whereEqualTo("fecha", ts).get());
        }

        return com.google.android.gms.tasks.Tasks.whenAllComplete(checks)
                .continueWith(task -> {
                    List<?> all = task.getResult();
                    if (all == null || all.isEmpty()) return false;

                    for (Object o : all) {
                        if (!(o instanceof com.google.android.gms.tasks.Task)) continue;
                        com.google.android.gms.tasks.Task<?> t = (com.google.android.gms.tasks.Task<?>) o;
                        if (!t.isSuccessful()) continue;

                        Object res = t.getResult();
                        if (!(res instanceof com.google.firebase.firestore.QuerySnapshot)) continue;
                        com.google.firebase.firestore.QuerySnapshot qs = (com.google.firebase.firestore.QuerySnapshot) res;

                        for (com.google.firebase.firestore.DocumentSnapshot d : qs.getDocuments()) {
                            String lugarDoc = d.getString("lugarNombre");
                            if (lugarDoc == null) lugarDoc = d.getString("lugar");
                            if (lugarNombre.equals(lugarDoc)) return true;
                        }
                    }
                    return false;
                });
    }

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

    private String getDisplayName(Uri uri) {
        String fallback = obtenerNombreArchivo(uri);
        try (Cursor c = requireContext().getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (!TextUtils.isEmpty(n)) return n;
            }
        } catch (Exception ignored) {}
        return fallback == null ? "archivo" : fallback;
    }

    @Nullable
    private String getMime(Uri uri) {
        try { return requireContext().getContentResolver().getType(uri); }
        catch (Exception e) { return null; }
    }

    private String getText(TextInputEditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private String getText(AutoCompleteTextView ac) {
        return (ac == null || ac.getText() == null) ? "" : ac.getText().toString().trim();
    }

    private List<String> splitToList(String text) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] tokens = text.split("[,;|\\n]+");
        LinkedHashSet<String> set = new LinkedHashSet<>();
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
