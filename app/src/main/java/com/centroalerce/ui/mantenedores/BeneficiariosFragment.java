package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.ui.mantenedores.adapter.BeneficiarioAdapter;
import com.google.android.material.button.MaterialButton;            // ← NUEVO
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BeneficiariosFragment extends Fragment {

    private RecyclerView rv;
    private FloatingActionButton fab;
    private BeneficiarioAdapter adapter;
    private FirebaseFirestore db;
    private ListenerRegistration reg;

    // cache socios para dropdown
    private final Map<String, String> socioNombreToId = new LinkedHashMap<>();
    private final List<String> socioNombres = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_beneficiarios, c, false);
        rv = v.findViewById(R.id.rvLista);
        fab = v.findViewById(R.id.fabAgregar);

        db = FirebaseFirestore.getInstance();

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        adapter = new BeneficiarioAdapter(new ArrayList<>(), new BeneficiarioAdapter.Callback() {
            @Override public void onEdit(Beneficiario item) { mostrarDialogoBeneficiario(item); }
            @Override public void onDelete(Beneficiario item) { confirmarEliminar(item); }
            @Override public void onToggleActivo(Beneficiario item) { toggleActivo(item); }
        });
        rv.setAdapter(adapter);

        fab.setOnClickListener(v1 -> mostrarDialogoBeneficiario(null));

        cargarSocios();
        suscribirCambios();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reg != null) reg.remove();
    }

    private void suscribirCambios() {
        reg = db.collection("beneficiarios")
                .orderBy("nombre", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snap == null) return;
                    List<Beneficiario> items = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Beneficiario b = fromDoc(d);
                        if (b != null) items.add(b);
                    }
                    adapter.submit(items);
                });
    }

    private void cargarSocios() {
        socioNombreToId.clear();
        socioNombres.clear();

        db.collection("socios").orderBy("nombre")
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String nombre = d.getString("nombre");
                        if (!TextUtils.isEmpty(nombre)) {
                            socioNombreToId.put(nombre, d.getId());
                            socioNombres.add(nombre);
                        }
                    }
                })
                .addOnFailureListener(err -> { /* silencioso */ });
    }

    private void mostrarDialogoBeneficiario(@Nullable Beneficiario editar) {
        View dlg = getLayoutInflater().inflate(R.layout.dialog_beneficiarios, null, false);

        TextInputEditText  etNombre   = dlg.findViewById(R.id.etNombreBenef);
        TextInputEditText  etRut      = dlg.findViewById(R.id.etRutBenef);
        TextInputEditText  etTel      = dlg.findViewById(R.id.etTelefonoBenef);
        TextInputEditText  etEmail    = dlg.findViewById(R.id.etEmailBenef);
        AutoCompleteTextView acSocio  = dlg.findViewById(R.id.acSocioBenef);
        SwitchMaterial     swActivo   = dlg.findViewById(R.id.swActivoBenef);
        TextInputEditText  etTags     = dlg.findViewById(R.id.etTagsBenef);

        // Botones DEL LAYOUT (no usamos botones del AlertDialog)
        MaterialButton btnCancelar = dlg.findViewById(R.id.btnCancelar);
        MaterialButton btnGuardar  = dlg.findViewById(R.id.btnGuardar);

        // Dropdown Socio
        if (!socioNombres.isEmpty()) {
            ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, socioNombres);
            acSocio.setAdapter(ad);
        }
        acSocio.setThreshold(0);
        acSocio.setOnClickListener(v -> acSocio.showDropDown());
        acSocio.setOnFocusChangeListener((v, has) -> { if (has) acSocio.showDropDown(); });

        // Prefill si es edición
        if (editar != null) {
            etNombre.setText(editar.nombre);
            if (!TextUtils.isEmpty(editar.rut))      etRut.setText(editar.rut);
            if (!TextUtils.isEmpty(editar.telefono)) etTel.setText(editar.telefono);
            if (!TextUtils.isEmpty(editar.email))    etEmail.setText(editar.email);
            if (!TextUtils.isEmpty(editar.socioNombre)) acSocio.setText(editar.socioNombre, false);
            swActivo.setChecked(editar.activo != null ? editar.activo : true);
            if (editar.caracterizacion != null && !editar.caracterizacion.isEmpty()) {
                etTags.setText(TextUtils.join(", ", editar.caracterizacion));
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dlg)        // solo la vista; sin setPositive/Negative
                .create();

        dialog.show();

        // Clicks de los botones del layout
        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnGuardar.setOnClickListener(v -> {
            String nombre = getText(etNombre);
            if (TextUtils.isEmpty(nombre)) {
                etNombre.setError("Obligatorio");
                return;
            }
            String rut = getText(etRut);
            String tel = getText(etTel);
            String email = getText(etEmail);
            String socioNombre = getText(acSocio);
            String socioId = socioNombreToId.get(socioNombre);
            boolean activo = swActivo.isChecked();
            List<String> tags = splitToList(getText(etTags));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nombre", nombre);
            if (!TextUtils.isEmpty(rut))  data.put("rut", rut);
            if (!TextUtils.isEmpty(tel))  data.put("telefono", tel);
            if (!TextUtils.isEmpty(email)) data.put("email", email);
            if (!TextUtils.isEmpty(socioId)) data.put("socioId", socioId);
            data.put("caracterizacion", tags);
            data.put("activo", activo);
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (editar == null) data.put("createdAt", FieldValue.serverTimestamp());

            if (editar == null) {
                db.collection("beneficiarios").add(data)
                        .addOnSuccessListener(ref -> {
                            Toast.makeText(getContext(), "Beneficiario creado", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {
                db.collection("beneficiarios").document(editar.id).update(data)
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(getContext(), "Beneficiario actualizado", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmarEliminar(Beneficiario item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar beneficiario")
                .setMessage("¿Eliminar a \"" + item.nombre + "\" de forma permanente?")
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Eliminar", (d, w) -> db.collection("beneficiarios").document(item.id)
                        .delete()
                        .addOnSuccessListener(unused -> Toast.makeText(getContext(), "Eliminado", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .show();
    }

    private void toggleActivo(Beneficiario item) {
        boolean nuevo = item.activo == null || !item.activo;
        db.collection("beneficiarios").document(item.id)
                .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // --- helpers / modelos ---

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
        for (String t : tokens) {
            String s = t == null ? "" : t.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    @Nullable
    private Beneficiario fromDoc(DocumentSnapshot d) {
        String id = d.getId();
        String nombre = d.getString("nombre");
        if (TextUtils.isEmpty(nombre)) return null;
        Beneficiario b = new Beneficiario();
        b.id = id;
        b.nombre = nombre;
        b.rut = d.getString("rut");
        b.telefono = d.getString("telefono");
        b.email = d.getString("email");
        b.socioId = d.getString("socioId");
        // socioNombre si lo tenemos en cache
        if (!TextUtils.isEmpty(b.socioId)) {
            for (Map.Entry<String, String> e : socioNombreToId.entrySet()) {
                if (e.getValue().equals(b.socioId)) { b.socioNombre = e.getKey(); break; }
            }
        }
        b.activo = d.getBoolean("activo");
        List<String> tags = (List<String>) d.get("caracterizacion");
        b.caracterizacion = tags != null ? tags : new ArrayList<>();
        return b;
    }

    public static class Beneficiario {
        public String id;
        public String nombre;
        public String rut;
        public String telefono;
        public String email;
        public String socioId;
        public String socioNombre;
        public Boolean activo;
        public List<String> caracterizacion;
    }
}
