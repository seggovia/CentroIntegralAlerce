package com.centroalerce.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ActivityEditFragment extends Fragment {

    // Campos de texto
    private TextInputEditText etNombre, etCupo, etFecha, etHora, etBeneficiarios;

    // Combos (IDs del layout actual)
    private AutoCompleteTextView acTipoActividad, acLugar, acOferente, acSocio, acProyecto;

    private MaterialButton btnGuardar, btnCancelar;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Cambia si tu layout de edición tiene otro nombre
        View v = inflater.inflate(R.layout.fragment_activity_form, container, false);

        // Inputs base
        etNombre        = v.findViewById(R.id.etNombre);
        etCupo          = v.findViewById(R.id.etCupo);
        etFecha         = v.findViewById(R.id.etFecha);
        etHora          = v.findViewById(R.id.etHora);
        etBeneficiarios = v.findViewById(R.id.etBeneficiarios);

        // Combos actuales (coinciden con tu XML de “form”)
        acTipoActividad = v.findViewById(R.id.acTipoActividad);
        acLugar         = v.findViewById(R.id.acLugar);
        acOferente      = v.findViewById(R.id.acOferente);
        acSocio         = v.findViewById(R.id.acSocio);
        acProyecto      = v.findViewById(R.id.acProyecto);

        btnGuardar = v.findViewById(R.id.btnGuardar);
        btnCancelar= v.findViewById(R.id.btnCancelar);
        btnGuardar.setEnabled(false);

        db = FirebaseFirestore.getInstance();

        // Cargar catálogos en los combos
        cargarTiposActividad();
        cargarLugares();
        cargarOferentes();
        cargarSocios();
        cargarProyectos();

        // Habilita Guardar cuando hay cambios mínimos
        TextWatcher watcher = new SimpleWatcher(this::validarMinimos);
        etNombre.addTextChangedListener(watcher);
        etFecha.addTextChangedListener(watcher);
        etHora.addTextChangedListener(watcher);

        if (acTipoActividad != null) acTipoActividad.addTextChangedListener(watcher);
        if (acLugar != null)         acLugar.addTextChangedListener(watcher);
        if (acOferente != null)      acOferente.addTextChangedListener(watcher);
        if (acSocio != null)         acSocio.addTextChangedListener(watcher);
        if (acProyecto != null)      acProyecto.addTextChangedListener(watcher);

        // Acciones
        btnCancelar.setOnClickListener(view ->
                Navigation.findNavController(view).popBackStack());

        btnGuardar.setOnClickListener(this::onGuardar);

        // Si recibes activityId por argumentos, aquí podrías cargar los datos
        // String activityId = ActivityEditFragmentArgs.fromBundle(getArguments()).getActivityId();
        // cargarActividad(activityId);

        return v;
    }

    // ---------- Guardado ----------
    private void onGuardar(View root) {
        if (!validarMinimos()) return;

        String nombre        = getText(etNombre);
        String fecha         = getText(etFecha);
        String hora          = getText(etHora);
        String tipoActividad = getText(acTipoActividad);

        Integer cupo = null;
        try {
            String s = getText(etCupo);
            if (!TextUtils.isEmpty(s)) cupo = Integer.parseInt(s);
        } catch (Exception ignored) {}

        String lugar         = getText(acLugar);
        String oferente      = getText(acOferente);
        String socio         = getText(acSocio);
        String proyecto      = getText(acProyecto);
        String beneficiarios = getText(etBeneficiarios);

        // TODO: aquí actualiza el documento en Firestore con estos valores.
        // db.collection("activities").document(activityId).update(...)

        Snackbar.make(root, "Actividad actualizada", Snackbar.LENGTH_LONG).show();
        Navigation.findNavController(root).popBackStack();
    }

    // ---------- Validaciones mínimas ----------
    private boolean validarMinimos() {
        TextInputLayout tilNombre = (TextInputLayout) etNombre.getParent().getParent();
        TextInputLayout tilFecha  = (TextInputLayout) etFecha.getParent().getParent();
        TextInputLayout tilHora   = (TextInputLayout) etHora.getParent().getParent();

        if (tilNombre != null) tilNombre.setError(null);
        if (tilFecha  != null) tilFecha.setError(null);
        if (tilHora   != null) tilHora.setError(null);

        boolean nombreOk = !TextUtils.isEmpty(getText(etNombre));
        boolean fechaOk  = !TextUtils.isEmpty(getText(etFecha));
        boolean horaOk   = !TextUtils.isEmpty(getText(etHora));

        if (!nombreOk && tilNombre != null) tilNombre.setError("Obligatorio");
        if (!fechaOk  && tilFecha  != null) tilFecha.setError("Requerido");
        if (!horaOk   && tilHora   != null) tilHora.setError("Requerido");

        boolean ok = nombreOk && fechaOk && horaOk;
        btnGuardar.setEnabled(ok);
        return ok;
    }

    // ---------- Cargar combos desde mantenedores ----------
    private void cargarTiposActividad() {
        db.collection("tipos_actividad").orderBy("nombre").get()
                .addOnSuccessListener(qs -> {
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;
                        String nombre = d.getString("nombre");
                        if (!TextUtils.isEmpty(nombre)) items.add(nombre.trim());
                    }
                    setCombo(acTipoActividad, items);
                });
    }

    private void cargarLugares() {
        db.collection("lugares").orderBy("nombre").get()
                .addOnSuccessListener(qs -> {
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;
                        String nombre = d.getString("nombre");
                        if (!TextUtils.isEmpty(nombre)) items.add(nombre.trim());
                    }
                    setCombo(acLugar, items);
                });
    }

    private void cargarOferentes() {
        db.collection("oferentes").orderBy("nombre").get()
                .addOnSuccessListener(qs -> {
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;
                        String nombre = d.getString("nombre");
                        if (!TextUtils.isEmpty(nombre)) items.add(nombre.trim());
                    }
                    setCombo(acOferente, items);
                });
    }

    private void cargarSocios() {
        db.collection("socios_comunitarios").orderBy("nombre").get()
                .addOnSuccessListener(qs -> {
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;
                        String nombre = d.getString("nombre");
                        if (!TextUtils.isEmpty(nombre)) items.add(nombre.trim());
                    }
                    setCombo(acSocio, items);
                });
    }

    private void cargarProyectos() {
        db.collection("proyectos").orderBy("nombre").get()
                .addOnSuccessListener(qs -> {
                    List<String> items = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;
                        String nombre = d.getString("nombre");
                        if (!TextUtils.isEmpty(nombre)) items.add(nombre.trim());
                    }
                    setCombo(acProyecto, items);
                });
    }

    private void setCombo(@Nullable AutoCompleteTextView combo, @NonNull List<String> items) {
        if (combo == null || items.isEmpty()) return;
        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, items);
        combo.setAdapter(ad);
        combo.setOnItemClickListener((parent, view, position, id) ->
                combo.setText(items.get(position), false));
    }

    // ---------- Helpers ----------
    private String getText(TextInputEditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private String getText(AutoCompleteTextView ac) {
        return (ac == null || ac.getText() == null) ? "" : ac.getText().toString().trim();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable onAfter;
        SimpleWatcher(Runnable onAfter) { this.onAfter = onAfter; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onAfter.run(); }
    }
}
