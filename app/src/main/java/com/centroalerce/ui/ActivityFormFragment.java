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

import com.centroalerce.gestion.utils.ActividadValidator;
import com.centroalerce.gestion.utils.DateUtils;
import com.centroalerce.gestion.utils.ValidationResult;
import com.centroalerce.gestion.repositories.LugarRepository;
import com.centroalerce.gestion.models.Lugar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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

// 🔽 NUEVO: selector de beneficiarios (BottomSheet) y modelo
import com.centroalerce.ui.mantenedores.dialog.BeneficiariosPickerSheet;
import com.centroalerce.gestion.models.Beneficiario;
import com.centroalerce.gestion.services.NotificationService;

public class ActivityFormFragment extends Fragment {

    // EditTexts que SÍ existen en tu XML
    private TextInputEditText etNombre, etCupo, etFecha, etHora, etDiasAvisoPrevio; // ← agregamos etDiasAvisoPrevio
    private TextInputLayout   tilFecha, tilHora;

    private MaterialButtonToggleGroup tgPeriodicidad;
    private MaterialButton btnPuntual, btnPeriodica, btnCancelar, btnGuardar;

    // Combos que existen en tu XML
    private AutoCompleteTextView acTipoActividad;
    private AutoCompleteTextView acLugar, acOferente, acSocio, acProyecto;

    // Adjuntos
    private com.google.android.material.card.MaterialCardView boxAdjuntos;
    private TextView tvAdjuntos;

    // 🔽 NUEVO: UI para beneficiarios (coincide con el layout actualizado)
    private com.google.android.material.card.MaterialCardView btnBeneficiarios;
    private TextView tvBeneficiariosHint;
    private ChipGroup chipsBeneficiarios;

    // 🔽 NUEVO: selección de beneficiarios
    private final List<Beneficiario> beneficiariosSeleccionados = new ArrayList<>();
    private final List<String> beneficiariosSeleccionadosIds = new ArrayList<>();

    private boolean esPeriodica = false;
    private final List<Integer> diasSemanaSeleccionados = new ArrayList<>(); // 1=Domingo, 2=Lunes, ..., 7=Sábado
    private Date fechaInicioPeriodo = null;
    private Date fechaFinPeriodo = null;

    private final List<Uri> attachmentUris = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private LugarRepository lugarRepository;
    private Lugar lugarSeleccionado;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (db == null) db = FirebaseFirestore.getInstance();
        if (storage == null) storage = FirebaseStorage.getInstance();
        lugarRepository = new LugarRepository(); // ← EXISTENTE
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
        etFecha          = v.findViewById(R.id.etFecha);
        etHora           = v.findViewById(R.id.etHora);
        etDiasAvisoPrevio = v.findViewById(R.id.etDiasAvisoPrevio);
        tilFecha         = (TextInputLayout) etFecha.getParent().getParent();
        tilHora          = (TextInputLayout) etHora.getParent().getParent();

        // 🔽 NUEVO: referencias de beneficiarios (ids del XML nuevo)
        btnBeneficiarios         = v.findViewById(R.id.btnBeneficiarios);
        tvBeneficiariosHint      = v.findViewById(R.id.tvBeneficiariosHint);
        chipsBeneficiarios       = v.findViewById(R.id.chipsBeneficiarios);

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
        cargarSocios();
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

        // 🔽 NUEVO: abrir selector de beneficiarios
        btnBeneficiarios.setOnClickListener(view -> abrirSelectorBeneficiarios());

        TextWatcher watcher = new SimpleWatcher(this::validarMinimos);
        etNombre.addTextChangedListener(watcher);
        etFecha.addTextChangedListener(watcher);
        etHora.addTextChangedListener(watcher);

        btnCancelar.setOnClickListener(view -> Navigation.findNavController(view).popBackStack());
        btnGuardar.setOnClickListener(this::onGuardar);

        aplicarModo();
        renderChipsBeneficiarios(); // inicial
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
        cargarSocios();
        cargarProyectos();
    }

    // ---------- Cargar catálogos ----------
    // Map para guardar relación nombre -> ID
    private final Map<String, String> lugaresMap = new HashMap<>();

    private void cargarLugares() {
        db.collection("lugares").orderBy("nombre")
                .get()
                .addOnSuccessListener(qs -> {
                    if (!isAdded()) return;
                    List<String> items = new ArrayList<>();
                    lugaresMap.clear(); // Limpiar el mapa

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;

                        String nombre = d.getString("nombre");
                        String id = d.getId();

                        if (nombre != null && !nombre.trim().isEmpty()) {
                            items.add(nombre.trim());
                            lugaresMap.put(nombre.trim(), id); // ← Guardar relación
                        }
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
            @Nullable DocNamePicker customPicker
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
        loadComboWithFallback(cols, acOferente, "oferentes");
    }

    // SOCIOS
    private void cargarSocios() {
        if (!isAdded()) return;
        db.collection("socios").orderBy("nombre")
                .get()
                .addOnSuccessListener(qs -> {
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
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
                .addOnFailureListener(e -> {
                    android.util.Log.w("CATALOG", "socios orderBy(nombre) falló: " + e.getMessage());
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
                                setComboAdapter(acSocio, new ArrayList<>());
                                if (isAdded()) {
                                    Snackbar.make(requireView(), "No pude cargar socios comunitarios", Snackbar.LENGTH_LONG).show();
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
            // Modo periódica: solicita rango de fechas y días de semana
            etFecha.setText(null);
            etHora.setText(null);
            diasSemanaSeleccionados.clear();
            fechaInicioPeriodo = null;
            fechaFinPeriodo = null;

            tilFecha.setHint("Seleccionar rango de fechas");
            tilHora.setHint("Hora (aplicará a todas las fechas)");

            etHora.setClickable(true);
            etHora.setFocusable(false);
            tilHora.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            tilHora.setEndIconDrawable(android.R.drawable.ic_menu_recent_history);
        } else {
            // Modo puntual: fecha y hora únicas
            tilFecha.setHint("Fecha (AAAA-MM-DD)");
            tilHora.setHint("Hora (HH:mm)");

            etHora.setClickable(true);
            etHora.setFocusable(false);
        }
    }

    // ---------- Pickers ----------
    private void onFechaClick() {
        if (esPeriodica) {
            mostrarDialogoSeleccionPeriodicidad();
        } else {
            showDatePickerPuntual();
        }
    }
    private void mostrarDialogoSeleccionPeriodicidad() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_periodicidad, null);

        // Referencias a vistas del diálogo personalizado
        com.google.android.material.chip.ChipGroup chipGroupDias = dialogView.findViewById(R.id.chipGroupDias);
        TextInputEditText etFechaInicio = dialogView.findViewById(R.id.etFechaInicio);
        TextInputEditText etFechaFin = dialogView.findViewById(R.id.etFechaFin);
        TextInputEditText etHoraPeriodicidad = dialogView.findViewById(R.id.etHoraPeriodicidad);

        // Chips de días (Lunes=2, Martes=3, ..., Domingo=1)
        final Map<Integer, com.google.android.material.chip.Chip> chipsMap = new HashMap<>();
        chipsMap.put(2, dialogView.findViewById(R.id.chipLunes));
        chipsMap.put(3, dialogView.findViewById(R.id.chipMartes));
        chipsMap.put(4, dialogView.findViewById(R.id.chipMiercoles));
        chipsMap.put(5, dialogView.findViewById(R.id.chipJueves));
        chipsMap.put(6, dialogView.findViewById(R.id.chipViernes));
        chipsMap.put(7, dialogView.findViewById(R.id.chipSabado));
        chipsMap.put(1, dialogView.findViewById(R.id.chipDomingo));

        // Pickers de fecha
        etFechaInicio.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
                fechaInicioPeriodo = toDate(y, m, d);
                etFechaInicio.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m+1, d));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        etFechaFin.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
                fechaFinPeriodo = toDate(y, m, d);
                etFechaFin.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m+1, d));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Picker de hora
        etHoraPeriodicidad.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(requireContext(), (picker, h, min) -> {
                etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", h, min));
                etHoraPeriodicidad.setText(String.format(Locale.getDefault(), "%02d:%02d", h, min));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });

        // Referencias a los botones del XML
        MaterialButton btnCancelar = dialogView.findViewById(R.id.btnCancelar);
        MaterialButton btnConfirmar = dialogView.findViewById(R.id.btnConfirmar);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        dialog.show();

        // Click del botón Cancelar
        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        // Click del botón Confirmar
        btnConfirmar.setOnClickListener(v -> {
            diasSemanaSeleccionados.clear();
            for (Map.Entry<Integer, com.google.android.material.chip.Chip> entry : chipsMap.entrySet()) {
                if (entry.getValue().isChecked()) {
                    diasSemanaSeleccionados.add(entry.getKey());
                }
            }

            if (diasSemanaSeleccionados.isEmpty()) {
                Snackbar.make(requireView(), "Selecciona al menos un día de la semana", Snackbar.LENGTH_LONG).show();
                return;
            }
            if (fechaInicioPeriodo == null || fechaFinPeriodo == null) {
                Snackbar.make(requireView(), "Selecciona rango de fechas", Snackbar.LENGTH_LONG).show();
                return;
            }
            if (fechaFinPeriodo.before(fechaInicioPeriodo)) {
                Snackbar.make(requireView(), "La fecha de fin debe ser posterior a la de inicio", Snackbar.LENGTH_LONG).show();
                return;
            }

            int totalCitas = calcularCitasPeriodicas(fechaInicioPeriodo, fechaFinPeriodo, diasSemanaSeleccionados).size();
            etFecha.setText("Fechas: " + totalCitas + " citas programadas");
            validarMinimos();
            dialog.dismiss();
        });
    }
    private List<Timestamp> calcularCitasPeriodicas(Date inicio, Date fin, List<Integer> diasSemana) {
        List<Timestamp> citas = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(inicio);

        String horaStr = getText(etHora);
        if (TextUtils.isEmpty(horaStr)) return citas;

        String[] parts = horaStr.split(":");
        int hora = Integer.parseInt(parts[0]);
        int minuto = Integer.parseInt(parts[1]);

        while (!cal.getTime().after(fin)) {
            int diaActual = cal.get(Calendar.DAY_OF_WEEK);

            if (diasSemana.contains(diaActual)) {
                Calendar citaCal = (Calendar) cal.clone();
                citaCal.set(Calendar.HOUR_OF_DAY, hora);
                citaCal.set(Calendar.MINUTE, minuto);
                citaCal.set(Calendar.SECOND, 0);
                citas.add(new Timestamp(citaCal.getTime()));
            }

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        return citas;
    }

    private Date toDate(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(year, month, day, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
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
            Snackbar.make(requireView(), "En modo periódica, la hora se configura en el diálogo de periodicidad", Snackbar.LENGTH_SHORT).show();
            return;
        }
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(
                requireContext(),
                (picker, h, min) -> etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", h, min)),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true
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
            boolean rangoOk = fechaInicioPeriodo != null && fechaFinPeriodo != null;
            boolean diasOk = !diasSemanaSeleccionados.isEmpty();
            boolean horaOk = !TextUtils.isEmpty(getText(etHora));

            ok = nombreOk && rangoOk && diasOk && horaOk;

            if (!nombreOk) ((TextInputLayout) etNombre.getParent().getParent()).setError("Obligatorio");
            if (!rangoOk) tilFecha.setError("Configura el rango de fechas");
            if (!diasOk) tilFecha.setError("Selecciona días de la semana");
            if (!horaOk) tilHora.setError("Selecciona hora");
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
        try { Integer.parseInt(c); return true; }
        catch (NumberFormatException e) { return false; }
    }

    // ---------- Guardar ----------
    private void onGuardar(View root) {
        android.util.Log.d("FORM", "=== INICIO onGuardar ===");

        btnGuardar.setEnabled(false);
        btnGuardar.setText("Validando...");

        if (!validarCupoOpcional()) {
            Snackbar.make(root, "Cupo inválido", Snackbar.LENGTH_LONG).show();
            btnGuardar.setEnabled(true);
            btnGuardar.setText("Guardar actividad");
            return;
        }

        String nombre = getText(etNombre);
        if (TextUtils.isEmpty(nombre)) {
            Snackbar.make(root, "Ingresa el nombre", Snackbar.LENGTH_LONG).show();
            btnGuardar.setEnabled(true);
            btnGuardar.setText("Guardar actividad");
            return;
        }

        boolean modoPeriodica = (tgPeriodicidad.getCheckedButtonId() == R.id.btnPeriodica);
        String fecha = getText(etFecha);
        String hora  = getText(etHora);

        if (!modoPeriodica && (TextUtils.isEmpty(fecha) || TextUtils.isEmpty(hora))) {
            Snackbar.make(root, "Completa Fecha y Hora", Snackbar.LENGTH_LONG).show();
            btnGuardar.setEnabled(true);
            btnGuardar.setText("Guardar actividad");
            return;
        }
        if (modoPeriodica) {
            if (fechaInicioPeriodo == null || fechaFinPeriodo == null || diasSemanaSeleccionados.isEmpty()) {
                Snackbar.make(root, "Configura la periodicidad correctamente", Snackbar.LENGTH_LONG).show();
                btnGuardar.setEnabled(true);
                btnGuardar.setText("Guardar actividad");
                return;
            }
        }

        if (!modoPeriodica) {
            Timestamp startAtPuntual = toStartAtTimestamp(fecha, hora);
            if (startAtPuntual == null) {
                Snackbar.make(root, "Fecha u hora inválida", Snackbar.LENGTH_LONG).show();
                btnGuardar.setEnabled(true);
                btnGuardar.setText("Guardar actividad");
                return;
            }

            ValidationResult validacionFecha = ActividadValidator.validarFechaFutura(startAtPuntual.toDate());
            if (!validacionFecha.isValid()) {
                mostrarDialogoError("Fecha inválida", validacionFecha.getErrorMessage(), root);
                btnGuardar.setEnabled(true);
                btnGuardar.setText("Guardar actividad");
                return;
            }
        } else {
            // Validar todas las fechas periódicas calculadas
            List<Timestamp> citasCalculadas = calcularCitasPeriodicas(fechaInicioPeriodo, fechaFinPeriodo, diasSemanaSeleccionados);
            for (Timestamp ts : citasCalculadas) {
                ValidationResult validacionFecha = ActividadValidator.validarFechaFutura(ts.toDate());
                if (!validacionFecha.isValid()) {
                    mostrarDialogoError("Fecha inválida",
                            "Una de las fechas del rango ya pasó. Por favor ajusta el rango.", root);
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText("Guardar actividad");
                    return;
                }
            }
        }

        String tipoActividad = getText(acTipoActividad);
        Integer cupo = null;
        try {
            String s = getText(etCupo);
            if (!s.isEmpty()) cupo = Integer.parseInt(s);
        } catch (Exception ignored) { }

        if (cupo != null) {
            ValidationResult validacionCupo = ActividadValidator.validarCupoActividad(cupo);
            if (!validacionCupo.isValid()) {
                mostrarDialogoError("Cupo inválido", validacionCupo.getErrorMessage(), root);
                btnGuardar.setEnabled(true);
                btnGuardar.setText("Guardar actividad");
                return;
            }
        }

        String lugar         = getText(acLugar);
        String oferente      = getText(acOferente);
        String socio         = getText(acSocio);
        String proyecto      = getText(acProyecto);

        // Obtener días de aviso previo
        int diasAvisoPrevio = 1;
        try {
            String diasStr = getText(etDiasAvisoPrevio);
            if (!TextUtils.isEmpty(diasStr)) {
                diasAvisoPrevio = Integer.parseInt(diasStr);
                if (diasAvisoPrevio < 0) diasAvisoPrevio = 0;
            }
        } catch (Exception e) {
            // Mantener valor por defecto
        }

        if (TextUtils.isEmpty(tipoActividad)) {
            mostrarDialogoError("Campo obligatorio", "Debes seleccionar un tipo de actividad", root);
            btnGuardar.setEnabled(true);
            btnGuardar.setText("Guardar actividad");
            return;
        }
        if (TextUtils.isEmpty(lugar)) {
            mostrarDialogoError("Campo obligatorio", "Debes seleccionar un lugar", root);
            btnGuardar.setEnabled(true);
            btnGuardar.setText("Guardar actividad");
            return;
        }
        if (TextUtils.isEmpty(oferente)) {
            mostrarDialogoError("Campo obligatorio", "Debes seleccionar un oferente", root);
            btnGuardar.setEnabled(true);
            btnGuardar.setText("Guardar actividad");
            return;
        }
        if (TextUtils.isEmpty(socio)) {
            mostrarDialogoError("Campo obligatorio", "Debes seleccionar un socio comunitario", root);
            btnGuardar.setEnabled(true);
            btnGuardar.setText("Guardar actividad");
            return;
        }

        final boolean   modoPeriodicaFinal   = modoPeriodica;
        final String    lugarFinal           = lugar;
        final String    nombreFinal          = nombre;
        final String    tipoActividadFinal   = tipoActividad;
        final Integer   cupoFinal            = cupo;
        final String    oferenteFinal        = oferente;
        final String    socioFinal           = socio;
        final String    proyectoFinal        = proyecto;
        final int       diasAvisoPrevioFinal = diasAvisoPrevio;

        btnGuardar.setText("Validando lugar...");

        // ✅ CAMBIO CRÍTICO: Usar getLugarPorNombre en lugar de getLugar
        lugarRepository.getLugarPorNombre(lugarFinal, new LugarRepository.LugarCallback() {
            @Override
            public void onSuccess(Lugar lugar) {
                lugarSeleccionado = lugar;

                if (cupoFinal != null) {
                    ValidationResult validacionCupoLugar = ActividadValidator.validarCupoLugar(lugar, cupoFinal);
                    if (!validacionCupoLugar.isValid()) {
                        mostrarDialogoErrorConAlternativa(
                                "Cupo insuficiente",
                                validacionCupoLugar.getErrorMessage(),
                                "Seleccionar otro lugar",
                                root
                        );
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Guardar actividad");
                        return;
                    }
                }

                final ArrayList<Timestamp> aRevisar = new ArrayList<>();
                if (modoPeriodicaFinal) {
                    aRevisar.addAll(calcularCitasPeriodicas(fechaInicioPeriodo, fechaFinPeriodo, diasSemanaSeleccionados));
                } else {
                    Timestamp startAtPuntual = toStartAtTimestamp(getText(etFecha), getText(etHora));
                    aRevisar.add(startAtPuntual);
                }

                btnGuardar.setText("Verificando horarios...");
                android.util.Log.d("FORM", "🔍 Iniciando verificación de conflictos para lugar: " + lugar.getNombre());

                // ✅ Pasar el NOMBRE del lugar (no el ID) porque así se guarda en las citas
                verificarConflictosConValidacion(lugar.getNombre(), aRevisar, new ConflictoCallback() {
                    @Override
                    public void onConflictoDetectado(String mensaje) {
                        android.util.Log.w("FORM", "⚠️ CONFLICTO DETECTADO: " + mensaje);
                        mostrarDialogoConflicto(mensaje, root);
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Guardar actividad");
                    }

                    @Override
                    public void onSinConflictos() {
                        android.util.Log.d("FORM", "✅ Sin conflictos - procediendo a guardar");
                        btnGuardar.setText("Guardando...");
                        subirAdjuntosYGuardar(
                                root,
                                nombreFinal, tipoActividadFinal, cupoFinal,
                                oferenteFinal, socioFinal, null,
                                lugarFinal, modoPeriodicaFinal,
                                aRevisar.get(0), aRevisar,
                                proyectoFinal, diasAvisoPrevioFinal
                        );
                    }

                    @Override
                    public void onError(String error) {
                        android.util.Log.e("FORM", "❌ Error en validación: " + error);
                        Snackbar.make(root, "Error al validar: " + error, Snackbar.LENGTH_LONG).show();
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Guardar actividad");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Snackbar.make(root, "Error al obtener lugar: " + error, Snackbar.LENGTH_LONG).show();
                btnGuardar.setEnabled(true);
                btnGuardar.setText("Guardar actividad");
            }
        });
    }

    private void subirAdjuntosYGuardar(View root,
                                       String nombre, String tipoActividad, Integer cupo,
                                       String oferente, String socio, String beneficiarios /*unused*/,
                                       String lugar, boolean modoPeriodica,
                                       @Nullable Timestamp startAtPuntual,
                                       List<Timestamp> timestamps,
                                       @Nullable String proyecto, int diasAvisoPrevio) {

        String activityId = db.collection("activities").document().getId();

        if (attachmentUris.isEmpty()) {
            escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                    /* beneficiarios texto */ null, lugar, modoPeriodica, startAtPuntual, timestamps, new ArrayList<>(), proyecto, diasAvisoPrevio);
            return;
        }

        // ✅ VERIFICAR AUTENTICACIÓN ANTES DE SUBIR ARCHIVOS
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            android.util.Log.e("FS-UPLOAD", "❌ Usuario no autenticado - no se pueden subir archivos");
            Snackbar.make(root, "Debes estar autenticado para subir archivos. Guardando actividad sin adjuntos.", Snackbar.LENGTH_LONG).show();
            escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                    null, lugar, modoPeriodica, startAtPuntual, timestamps, new ArrayList<>(), proyecto, diasAvisoPrevio);
            return;
        }

        android.util.Log.d("FS-UPLOAD", "✅ Usuario autenticado: " + currentUser.getEmail() + " (UID: " + currentUser.getUid() + ")");

        StorageReference baseRef = storage.getReference().child("activities").child(activityId).child("attachments");
        android.util.Log.d("FS-UPLOAD", "🚀 Iniciando subida de " + attachmentUris.size() + " archivos");
        android.util.Log.d("FS-UPLOAD", "🔧 Firebase Storage bucket: " + storage.getReference().getBucket());
        android.util.Log.d("FS-UPLOAD", "🔧 Ruta de subida: " + baseRef.getPath());

        List<Task<Uri>> urlTasks = new ArrayList<>();
        List<Uri> srcs = new ArrayList<>();
        for (int i = 0; i < attachmentUris.size(); i++) {
            Uri uri = attachmentUris.get(i);
            String fileName = getDisplayName(uri);
            String mime = getMime(uri);
            
            android.util.Log.d("FS-UPLOAD", "📎 Procesando archivo " + (i+1) + "/" + attachmentUris.size() + ": " + fileName + " (MIME: " + mime + ")");
            
            // Validar que el archivo tenga un nombre válido
            if (TextUtils.isEmpty(fileName) || fileName.equals("archivo")) {
                fileName = "archivo_" + System.currentTimeMillis() + "_" + i;
                android.util.Log.w("FS-UPLOAD", "⚠️ Nombre de archivo inválido, usando: " + fileName);
            }
            
            // Validar que la URI sea válida
            if (uri == null) {
                android.util.Log.e("FS-UPLOAD", "❌ URI nula para archivo " + (i+1));
                continue;
            }

            // Crear variables finales para usar en lambdas
            final String finalFileName = fileName;
            final Uri finalUri = uri;
            final String finalMime = mime;

            StorageReference fileRef = baseRef.child(finalFileName);

            com.google.firebase.storage.StorageMetadata md =
                    new com.google.firebase.storage.StorageMetadata.Builder()
                            .setContentType(finalMime != null ? finalMime : "application/octet-stream")
                            .build();

            UploadTask up = fileRef.putFile(finalUri, md);
            up.addOnFailureListener(e -> {
                android.util.Log.e("FS-UPLOAD", "❌ Falló subir " + finalFileName + ": " + e.getMessage(), e);
                android.util.Log.e("FS-UPLOAD", "❌ URI del archivo: " + finalUri.toString());
            });
            
            up.addOnSuccessListener(taskSnapshot -> {
                android.util.Log.d("FS-UPLOAD", "✅ Archivo subido exitosamente: " + finalFileName);
            });

            Task<Uri> urlTask = up.continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    android.util.Log.e("FS-UPLOAD", "❌ Error obteniendo URL para " + finalFileName + ": " + task.getException().getMessage());
                    throw task.getException();
                }
                android.util.Log.d("FS-UPLOAD", "🔗 Obteniendo URL de descarga para: " + finalFileName);
                return fileRef.getDownloadUrl();
            });
            urlTasks.add(urlTask);
            srcs.add(finalUri);
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(urlTasks)
                .addOnSuccessListener(list -> {
                    android.util.Log.d("FS-UPLOAD", "📊 Procesando resultados de " + list.size() + " tareas de subida");
                    List<Map<String, Object>> adj = new ArrayList<>();
                    List<String> archivosFallidos = new ArrayList<>();
                    
                    for (int j = 0; j < list.size(); j++) {
                        Task<?> t = (Task<?>) list.get(j);
                        Uri src = srcs.get(j);
                        String fileName = getDisplayName(src);
                        
                        if (t.isSuccessful() && t.getResult() instanceof Uri) {
                            Uri download = (Uri) t.getResult();
                            Map<String, Object> item = new HashMap<>();
                            item.put("name", fileName);
                            item.put("nombre", fileName); // Agregar también 'nombre' para compatibilidad
                            String mime = getMime(src);
                            if (mime != null) item.put("mime", mime);
                            item.put("url", download.toString());
                            item.put("id", "adj_" + System.currentTimeMillis() + "_" + j); // ID único para cada adjunto
                            adj.add(item);
                            android.util.Log.d("FS-UPLOAD", "✅ Adjunto procesado exitosamente: " + fileName);
                        } else {
                            archivosFallidos.add(fileName);
                            android.util.Log.e("FS-UPLOAD", "❌ Falló procesar adjunto: " + fileName + " - Error: " + 
                                    (t.getException() != null ? t.getException().getMessage() : "Error desconocido"));
                        }
                    }
                    
                    android.util.Log.d("FS-UPLOAD", "📈 Resumen: " + adj.size() + " exitosos, " + archivosFallidos.size() + " fallidos");
                    
                    if (!archivosFallidos.isEmpty()) {
                        String mensaje = "Los siguientes archivos no se pudieron subir: " + String.join(", ", archivosFallidos);
                        Snackbar.make(root, mensaje, Snackbar.LENGTH_LONG).show();
                        android.util.Log.w("FS-UPLOAD", "⚠️ " + mensaje);
                    }
                    
                    if (adj.isEmpty()) {
                        Snackbar.make(root, "No se pudieron subir ningún archivo. Guardando actividad sin adjuntos.", Snackbar.LENGTH_LONG).show();
                        android.util.Log.w("FS-UPLOAD", "⚠️ No se subió ningún archivo - guardando sin adjuntos");
                    }
                    
                    escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                            null, lugar, modoPeriodica, startAtPuntual, timestamps, adj, proyecto, diasAvisoPrevio);
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(root, "No se pudieron subir los adjuntos (" + e.getMessage() + "). Guardando sin archivos.", Snackbar.LENGTH_LONG).show();
                    escribirActividadYCitas(root, activityId, nombre, tipoActividad, cupo, oferente, socio,
                            null, lugar, modoPeriodica, startAtPuntual, timestamps, new ArrayList<>(), proyecto, diasAvisoPrevio);
                });
    }

    private void escribirActividadYCitas(View root, String activityId,
                                         String nombre, String tipoActividad, Integer cupo,
                                         String oferente, String socio, String beneficiarios /*unused*/,
                                         String lugar, boolean modoPeriodica,
                                         @Nullable Timestamp startAtPuntual,
                                         List<Timestamp> timestamps,
                                         List<Map<String, Object>> adjuntos,
                                         @Nullable String proyecto, int diasAvisoPrevio) {

        List<String> oferentesList = splitToList(oferente);

        // Nombres e IDs desde la selección
        List<String> beneficiariosIds = new ArrayList<>(beneficiariosSeleccionadosIds);
        List<String> beneficiariosNombres = new ArrayList<>();
        for (Beneficiario b : beneficiariosSeleccionados) beneficiariosNombres.add(b.getNombre());

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

        // Agregar días de aviso previo
        activityDoc.put("diasAvisoPrevio", diasAvisoPrevio);

        if (!oferentesList.isEmpty()) {
            activityDoc.put("oferentes", oferentesList);
            activityDoc.put("oferente", oferentesList.get(0));
            activityDoc.put("oferenteNombre", oferentesList.get(0)); // ✅ NUEVO
        }

        if (!TextUtils.isEmpty(socio)) {
            activityDoc.put("socioComunitario", socio);
            activityDoc.put("socio_nombre", socio); // ✅ NUEVO
        }

        // Persistir beneficiarios seleccionados
        if (!beneficiariosIds.isEmpty()) {
            activityDoc.put("beneficiariosIds", beneficiariosIds);
            activityDoc.put("beneficiarios", beneficiariosNombres);
            activityDoc.put("beneficiariosTexto", TextUtils.join(", ", beneficiariosNombres));
        }

        // ✅ SIEMPRE guardar lugar en el doc de actividad
        if (!TextUtils.isEmpty(lugar)) {
            activityDoc.put("lugarNombre", lugar);
            activityDoc.put("lugar", lugar); // redundancia
        }

        if (!adjuntos.isEmpty()) {
            activityDoc.put("adjuntos", adjuntos);
            android.util.Log.d("FS", "📎 Guardando " + adjuntos.size() + " adjuntos en documento principal de actividad");
            for (int i = 0; i < adjuntos.size(); i++) {
                Map<String, Object> adj = adjuntos.get(i);
                android.util.Log.d("FS", "📎 Adjunto " + (i+1) + ": " + adj.get("name") + " | URL: " + adj.get("url"));
            }
        }
        activityDoc.put("createdAt", FieldValue.serverTimestamp());
        activityDoc.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(db.collection("activities").document(activityId), activityDoc);

        if (!adjuntos.isEmpty()) {
            android.util.Log.d("FS", "📎 Guardando " + adjuntos.size() + " adjuntos en subcolección adjuntos");
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
            // ✅ CITAS PERIÓDICAS: Copiar TODOS los campos
            for (Timestamp ts : timestamps) {
                Map<String, Object> cita = new HashMap<>();
                cita.put("startAt", ts);
                cita.put("fecha", ts);

                // ✅ Copiar datos de la actividad a CADA cita
                cita.put("actividadNombre", nombre);
                cita.put("titulo", nombre);

                if (!TextUtils.isEmpty(lugar)) {
                    cita.put("lugarNombre", lugar);
                    cita.put("lugar", lugar);
                }

                if (!TextUtils.isEmpty(tipoActividad)) {
                    cita.put("tipoActividad", tipoActividad);
                    cita.put("tipo", tipoActividad);
                }

                if (!oferentesList.isEmpty()) {
                    cita.put("oferenteNombre", oferentesList.get(0));
                    cita.put("oferente", oferentesList.get(0));
                }

                if (!beneficiariosIds.isEmpty()) {
                    cita.put("beneficiariosIds", beneficiariosIds);
                }

                // ✅ AGREGAR ADJUNTOS DIRECTAMENTE AL DOCUMENTO DE CADA CITA PERIÓDICA
                if (!adjuntos.isEmpty()) {
                    cita.put("adjuntos", adjuntos);
                }

                cita.put("estado", "PROGRAMADA"); // mayúsculas consistentes
                cita.put("periodicidad", "PERIODICA");

                com.google.firebase.firestore.DocumentReference citaRef =
                        db.collection("activities").document(activityId)
                                .collection("citas").document();
                citaRefs.add(citaRef);
                batch.set(citaRef, cita);
            }
        } else {
            // ✅ CITA PUNTUAL: Copiar TODOS los campos
            Map<String, Object> cita = new HashMap<>();
            cita.put("startAt", startAtPuntual);
            cita.put("fecha", startAtPuntual);

            cita.put("actividadNombre", nombre);
            cita.put("titulo", nombre);

            if (!TextUtils.isEmpty(lugar)) {
                cita.put("lugarNombre", lugar);
                cita.put("lugar", lugar);
            }

            if (!TextUtils.isEmpty(tipoActividad)) {
                cita.put("tipoActividad", tipoActividad);
                cita.put("tipo", tipoActividad);
            }

            if (!oferentesList.isEmpty()) {
                cita.put("oferenteNombre", oferentesList.get(0));
                cita.put("oferente", oferentesList.get(0));
            }

            if (!beneficiariosIds.isEmpty()) {
                cita.put("beneficiariosIds", beneficiariosIds);
            }

            // ✅ AGREGAR ADJUNTOS DIRECTAMENTE AL DOCUMENTO DE LA CITA
            if (!adjuntos.isEmpty()) {
                cita.put("adjuntos", adjuntos);
                android.util.Log.d("FS", "📎 Agregando " + adjuntos.size() + " adjuntos al documento de cita puntual");
            }

            cita.put("estado", "PROGRAMADA");
            cita.put("periodicidad", "PUNTUAL");

            com.google.firebase.firestore.DocumentReference citaRef =
                    db.collection("activities").document(activityId)
                            .collection("citas").document();
            citaRefs.add(citaRef);
            batch.set(citaRef, cita);
        }

        if (!adjuntos.isEmpty()) {
            android.util.Log.d("FS", "📎 Copiando " + adjuntos.size() + " adjuntos a " + citaRefs.size() + " citas");
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
                            "✅ Actividad " + activityId + " creada con " + citaRefs.size() +
                                    " cita(s) y " + (adjuntos == null ? 0 : adjuntos.size()) + " adjunto(s)");

                    // 1️⃣ Programar notificaciones a nivel Actividad
                    programarNotificacionesActividad(activityId, nombre, modoPeriodica, timestamps, diasAvisoPrevio);

                    // 2️⃣ Programar notificaciones para cada cita
                    NotificationService ns = new NotificationService(requireContext());
                    List<String> usuariosNotificar = new ArrayList<>();
                    usuariosNotificar.add("usuario_actual");

                    com.centroalerce.gestion.models.Actividad act = new com.centroalerce.gestion.models.Actividad();
                    act.setId(activityId);
                    act.setNombre(!TextUtils.isEmpty(nombre) ? nombre : "Actividad");
                    act.setPeriodicidad(modoPeriodica ? "Periodica" : "Puntual");
                    act.setDiasAvisoPrevio(diasAvisoPrevio);

                    if (modoPeriodica) {
                        for (int i = 0; i < timestamps.size(); i++) {
                            Timestamp ts = timestamps.get(i);
                            com.centroalerce.gestion.models.Cita c = new com.centroalerce.gestion.models.Cita();
                            c.setFecha(ts);
                            ns.programarNotificacionesCita(c, act, usuariosNotificar);
                        }
                    } else {
                        com.centroalerce.gestion.models.Cita c = new com.centroalerce.gestion.models.Cita();
                        c.setFecha(startAtPuntual);
                        ns.programarNotificacionesCita(c, act, usuariosNotificar);
                    }

                    Snackbar.make(root,
                            modoPeriodica
                                    ? "Actividad creada. " + timestamps.size() + " fechas programadas."
                                    : "Actividad creada y cita generada.",
                            Snackbar.LENGTH_LONG).show();

                    Navigation.findNavController(root).popBackStack();
                })
                .addOnFailureListener(e -> {
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText("Guardar actividad");
                    Snackbar.make(root, "Error al guardar: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
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
                if (!TextUtils.isEmpty(n)) {
                    android.util.Log.d("FS-UPLOAD", "📝 Nombre obtenido del cursor: " + n);
                    return n;
                }
            }
        } catch (Exception e) {
            android.util.Log.w("FS-UPLOAD", "⚠️ Error obteniendo nombre del cursor: " + e.getMessage());
        }
        
        String result = fallback == null ? "archivo" : fallback;
        android.util.Log.d("FS-UPLOAD", "📝 Usando nombre fallback: " + result);
        return result;
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

    /**
     * Busca el ID del lugar por nombre (simulado - en producción deberías tenerlo)
     */
    private String buscarIdLugar(String nombreLugar) {
        return lugaresMap.getOrDefault(nombreLugar, nombreLugar.toLowerCase().replaceAll("\\s+", "_"));
    }

    /**
     * Verificar conflictos de horario con validaciones robustas
     */
    private void verificarConflictosConValidacion(String lugarId, List<Timestamp> fechas, ConflictoCallback callback) {
        android.util.Log.d("FORM", "🔎 verificarConflictosConValidacion - lugarId: " + lugarId + ", fechas: " + fechas.size());

        if (TextUtils.isEmpty(lugarId) || fechas == null || fechas.isEmpty()) {
            android.util.Log.d("FORM", "✅ Sin datos para validar - continuando");
            callback.onSinConflictos();
            return;
        }

        String lugarNombre = getText(acLugar);
        android.util.Log.d("FORM", "📍 Lugar seleccionado: " + lugarNombre);

        validarTodasLasFechas(lugarId, lugarNombre, fechas, 0, callback);
    }
    private void validarTodasLasFechas(String lugarId, String lugarNombre, List<Timestamp> fechas,
                                       int index, ConflictoCallback callback) {
        if (index >= fechas.size()) {
            android.util.Log.d("FORM", "✅ Todas las fechas validadas - sin conflictos");
            callback.onSinConflictos();
            return;
        }

        Timestamp fechaActual = fechas.get(index);
        Date fechaDate = fechaActual.toDate();

        android.util.Log.d("FORM", "🔍 Validando fecha " + (index + 1) + "/" + fechas.size() + ": " +
                DateUtils.timestampToString(fechaActual));

        lugarRepository.getCitasEnLugar(lugarId, fechaDate, new LugarRepository.CitasEnLugarCallback() {
            @Override
            public void onSuccess(List<Date> citasExistentes) {
                android.util.Log.d("FORM", "📊 Citas existentes encontradas: " + citasExistentes.size());

                ValidationResult validacion = ActividadValidator.validarConflictoHorario(
                        lugarId, fechaDate, citasExistentes, 30
                );

                if (!validacion.isValid()) {
                    android.util.Log.w("FORM", "⚠️ Conflicto detectado: " + validacion.getErrorMessage());
                    callback.onConflictoDetectado(validacion.getErrorMessage());
                    return;
                }

                validarTodasLasFechas(lugarId, lugarNombre, fechas, index + 1, callback);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("FORM", "❌ Error obteniendo citas: " + error);
                callback.onError(error);
            }
        });
    }

    // ---------- Beneficiarios: abrir sheet y render chips ----------
    private void abrirSelectorBeneficiarios() {
        BeneficiariosPickerSheet sheet = BeneficiariosPickerSheet.newInstance(beneficiariosSeleccionadosIds);
        sheet.setListener(seleccionados -> {
            beneficiariosSeleccionados.clear();
            beneficiariosSeleccionados.addAll(seleccionados);

            beneficiariosSeleccionadosIds.clear();
            for (Beneficiario b : seleccionados) beneficiariosSeleccionadosIds.add(b.getId());

            renderChipsBeneficiarios();
        });
        sheet.show(getChildFragmentManager(), "beneficiariosPicker");
    }

    private void renderChipsBeneficiarios() {
        chipsBeneficiarios.removeAllViews();

        if (beneficiariosSeleccionados.isEmpty()) {
            tvBeneficiariosHint.setText("Seleccionar beneficiarios");
            tvBeneficiariosHint.setVisibility(View.VISIBLE);
            return;
        }

        tvBeneficiariosHint.setVisibility(View.GONE);

        for (Beneficiario b : beneficiariosSeleccionados) {
            Chip chip = new Chip(requireContext());
            chip.setText(b.getNombre());
            chip.setCloseIconVisible(true);
            chip.setCheckable(false);
            chip.setOnCloseIconClickListener(v -> {
                beneficiariosSeleccionadosIds.remove(b.getId());
                beneficiariosSeleccionados.remove(b);
                renderChipsBeneficiarios();
            });
            chipsBeneficiarios.addView(chip);
        }
    }

    /**
     * Mostrar diálogo de error simple
     */
    private void mostrarDialogoError(String titulo, String mensaje, View root) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Mostrar diálogo de error con opción de alternativa
     */
    private void mostrarDialogoErrorConAlternativa(String titulo, String mensaje, String accionAlternativa, View root) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton(accionAlternativa, (dialog, which) -> {
                    acLugar.requestFocus();
                    acLugar.showDropDown();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Mostrar diálogo de conflicto de horario con opciones
     */
    private void mostrarDialogoConflicto(String mensaje, View root) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Conflicto de horario")
                .setMessage(mensaje + "\n\n¿Qué deseas hacer?")
                .setPositiveButton("Cambiar fecha/hora", (dialog, which) -> {
                    if (esPeriodica) {
                        // Limpiar configuración periódica
                        diasSemanaSeleccionados.clear();
                        fechaInicioPeriodo = null;
                        fechaFinPeriodo = null;
                        etFecha.setText(null);
                        etHora.setText(null);
                        Snackbar.make(root, "Vuelve a configurar la periodicidad", Snackbar.LENGTH_SHORT).show();
                        // Abrir diálogo nuevamente
                        mostrarDialogoSeleccionPeriodicidad();
                    } else {
                        etFecha.requestFocus();
                    }
                })
                .setNeutralButton("Cambiar lugar", (dialog, which) -> {
                    acLugar.requestFocus();
                    acLugar.showDropDown();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Interface para callback de conflictos
     */
    /**
     * Programa notificaciones para una actividad recién creada
     */
    private void programarNotificacionesActividad(String activityId, String nombreActividad, 
                                                 boolean modoPeriodica, List<Timestamp> timestamps, 
                                                 int diasAvisoPrevio) {
        if (getContext() == null) return;
        
        NotificationService notificationService = new NotificationService(getContext());
        
        // Crear objeto Actividad para el servicio de notificaciones
        com.centroalerce.gestion.models.Actividad actividad = new com.centroalerce.gestion.models.Actividad();
        actividad.setId(activityId);
        actividad.setNombre(nombreActividad);
        actividad.setDiasAvisoPrevio(diasAvisoPrevio);
        actividad.setPeriodicidad(modoPeriodica ? "Periodica" : "Puntual");
        
        if (modoPeriodica && !timestamps.isEmpty()) {
            actividad.setFechaInicio(timestamps.get(0));
        }
        
        // Lista de usuarios a notificar (por ahora solo el usuario actual)
        // TODO: Obtener lista real de usuarios que deben ser notificados
        List<String> usuariosNotificar = new ArrayList<>();
        usuariosNotificar.add("usuario_actual"); // Reemplazar con ID real del usuario
        
        // Programar notificaciones
        notificationService.programarNotificacionesActividad(actividad, usuariosNotificar);
        
        android.util.Log.d("NOTIFICATIONS", "Notificaciones programadas para actividad: " + nombreActividad + 
                          " con " + diasAvisoPrevio + " días de aviso previo");
    }

    private interface ConflictoCallback {
        void onConflictoDetectado(String mensaje);
        void onSinConflictos();
        void onError(String error);
    }
}

