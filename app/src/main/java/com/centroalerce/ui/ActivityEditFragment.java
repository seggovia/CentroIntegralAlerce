package com.centroalerce.ui;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActivityEditFragment extends Fragment {

    private String activityId;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // UI
    private TextInputEditText etNombre, etCupo, etLugar, etOferente, etSocio, etBeneficiarios;
    private android.widget.AutoCompleteTextView acTipoActividad;
    private LinearLayout boxAdjuntos;
    private TextView tvAdjuntos;
    private MaterialButton btnCancelar, btnGuardar;

    // Adjuntos
    private final List<Uri> nuevosAdjuntos = new ArrayList<>();
    private List<Map<String, Object>> adjuntosExistentes = new ArrayList<>();

    private final ActivityResultLauncher<String[]> pickFiles =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    nuevosAdjuntos.clear();
                    nuevosAdjuntos.addAll(uris);
                    if (tvAdjuntos != null) {
                        tvAdjuntos.setText("Adjuntar más archivos  •  " + nuevosAdjuntos.size() + " seleccionado(s)");
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        // Reutilizamos el mismo layout del form de creación
        View v = inf.inflate(R.layout.fragment_activity_form, c, false);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        if (getArguments() != null) {
            activityId = getArguments().getString("activityId");
        }

        // Cambiar encabezado / botón para modo edición
        TextView tvTitle = v.findViewById(R.id.tvTitle);
        TextView tvSubtitle = v.findViewById(R.id.tvSubtitle);
        btnGuardar = v.findViewById(R.id.btnGuardar);
        btnCancelar = v.findViewById(R.id.btnCancelar);

        if (tvTitle != null) tvTitle.setText("Modificar actividad");
        if (tvSubtitle != null) tvSubtitle.setText("Edita los datos y guarda");
        if (btnGuardar != null) {
            btnGuardar.setText("Guardar cambios");
            btnGuardar.setEnabled(true); // en edición permitimos guardar sin esperar pickers
        }

        // Bind de campos
        etNombre = v.findViewById(R.id.etNombre);
        etCupo = v.findViewById(R.id.etCupo);
        etLugar = v.findViewById(R.id.etLugar);
        etOferente = v.findViewById(R.id.etOferente);
        etSocio = v.findViewById(R.id.etSocio);
        etBeneficiarios = v.findViewById(R.id.etBeneficiarios);
        acTipoActividad = v.findViewById(R.id.acTipoActividad);
        boxAdjuntos = v.findViewById(R.id.boxAdjuntos);
        tvAdjuntos = v.findViewById(R.id.tvAdjuntos);

        // Dropdown tipos
        String[] tipos = new String[]{"Capacitación", "Taller", "Charlas", "Atenciones",
                "Operativo en oficina", "Operativo rural", "Operativo", "Práctica profesional", "Diagnostico"};
        acTipoActividad.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, tipos));

        // Acciones
        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(view -> Navigation.findNavController(view).popBackStack());
        }
        if (btnGuardar != null) {
            btnGuardar.setOnClickListener(this::onGuardar);
        }
        if (boxAdjuntos != null) {
            boxAdjuntos.setOnClickListener(v1 -> pickFiles.launch(new String[]{"*/*"}));
        }

        // Cargar datos actuales
        if (activityId != null) cargar();

        return v;
    }

    @SuppressWarnings("unchecked")
    private void cargar() {
        db.collection("activities").document(activityId).get()
                .addOnSuccessListener(d -> {
                    if (!d.exists()) {
                        Snackbar.make(requireView(), "Actividad no encontrada", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    // Campos básicos
                    if (etNombre != null) etNombre.setText(d.getString("nombre"));
                    String tipo = d.getString("tipoActividad");
                    if (tipo != null && acTipoActividad != null) acTipoActividad.setText(tipo, false);
                    Long cupo = d.getLong("cupo");
                    if (cupo != null && etCupo != null) etCupo.setText(String.valueOf(cupo));
                    if (etLugar != null) etLugar.setText(d.getString("lugarDefault"));
                    if (etOferente != null) etOferente.setText(d.getString("oferente"));
                    if (etSocio != null) etSocio.setText(d.getString("socioComunitario"));
                    if (etBeneficiarios != null) etBeneficiarios.setText(d.getString("beneficiariosTexto"));

                    // Adjuntos existentes
                    List<Map<String, Object>> adj = (List<Map<String, Object>>) d.get("adjuntos");
                    if (adj != null) adjuntosExistentes = new ArrayList<>(adj);

                    // Migración suave: si hay adjuntos sin 'id', genera y guarda
                    ensureAdjuntosHaveIds();
                })
                .addOnFailureListener(e ->
                        Snackbar.make(requireView(), "Error cargando: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
    }

    private void onGuardar(View v) {
        String nombre = txt(etNombre);
        if (TextUtils.isEmpty(nombre)) {
            Snackbar.make(v, "Nombre obligatorio", Snackbar.LENGTH_LONG).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("nombre", nombre);

        String tipo = acTipoActividad.getText() != null ? acTipoActividad.getText().toString().trim() : "";
        if (!tipo.isEmpty()) updates.put("tipoActividad", tipo);

        Integer cupo = null;
        try {
            String s = txt(etCupo);
            if (!s.isEmpty()) cupo = Integer.parseInt(s);
        } catch (Exception ignored) {}
        if (cupo != null) updates.put("cupo", cupo);

        putIfNotEmpty(updates, "lugarDefault", txt(etLugar));
        putIfNotEmpty(updates, "oferente", txt(etOferente));
        putIfNotEmpty(updates, "socioComunitario", txt(etSocio));
        putIfNotEmpty(updates, "beneficiariosTexto", txt(etBeneficiarios));

        updates.put("updatedAt", FieldValue.serverTimestamp());

        // ¿Hay nuevos adjuntos? súbelos y mergea; si no, actualiza directo.
        if (nuevosAdjuntos.isEmpty()) {
            updates.put("adjuntos", adjuntosExistentes);
            aplicarActualizacion(v, updates);
            return;
        }

        StorageReference base = storage.getReference()
                .child("activities").child(activityId).child("attachments");

        // Subir archivos con nombre único y recolectar URLs
        List<com.google.android.gms.tasks.Task<Uri>> urlTasks = new ArrayList<>();
        List<StorageReference> refs = new ArrayList<>();

        for (Uri u : nuevosAdjuntos) {
            // nombre único para evitar colisiones
            String uniqueName = java.util.UUID.randomUUID().toString() + "_" + nombreArchivo(u);
            StorageReference ref = base.child(uniqueName);
            refs.add(ref);

            com.google.firebase.storage.UploadTask up = ref.putFile(u);
            urlTasks.add(up.continueWithTask(t -> {
                if (!t.isSuccessful()) throw t.getException();
                return ref.getDownloadUrl();
            }));
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(urlTasks)
                .addOnSuccessListener(urls -> {
                    int i = 0;
                    for (Object o : urls) {
                        Uri dl = (Uri) o;
                        Uri src = nuevosAdjuntos.get(i);
                        StorageReference ref = refs.get(i);
                        i++;

                        Map<String, Object> item = new HashMap<>();
                        item.put("id", java.util.UUID.randomUUID().toString()); // <-- ID estable para mostrar en detalle
                        item.put("name", nombreArchivo(src));
                        String mime = null;
                        try { mime = requireContext().getContentResolver().getType(src); } catch (Exception ignored) {}
                        if (mime != null) item.put("mime", mime);
                        item.put("url", dl.toString());
                        item.put("storagePath", ref.getPath()); // útil para mantenimiento/borrado

                        adjuntosExistentes.add(item);
                    }
                    updates.put("adjuntos", adjuntosExistentes);
                    aplicarActualizacion(v, updates);
                })
                .addOnFailureListener(e ->
                        Snackbar.make(v, "Error subiendo adjuntos: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
    }

    private void aplicarActualizacion(View v, Map<String, Object> updates) {
        db.collection("activities").document(activityId).update(updates)
                .addOnSuccessListener(x -> {
                    Snackbar.make(v, "Cambios guardados", Snackbar.LENGTH_LONG).show();
                    Navigation.findNavController(v).popBackStack();
                })
                .addOnFailureListener(e ->
                        Snackbar.make(v, "Error al guardar: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
    }

    // === Migración: asegura que todos los adjuntos tengan 'id'
    private void ensureAdjuntosHaveIds() {
        if (adjuntosExistentes == null || adjuntosExistentes.isEmpty()) return;

        boolean changed = false;
        List<Map<String, Object>> patched = new ArrayList<>();

        for (Map<String, Object> it : adjuntosExistentes) {
            if (it == null) continue;
            Map<String, Object> copy = new HashMap<>(it);
            Object existingId = copy.get("id");
            if (existingId == null || String.valueOf(existingId).trim().isEmpty()) {
                copy.put("id", java.util.UUID.randomUUID().toString());
                changed = true;
            }
            patched.add(copy);
        }

        if (changed) {
            adjuntosExistentes = patched;
            db.collection("activities").document(activityId)
                    .update("adjuntos", adjuntosExistentes)
                    .addOnFailureListener(e -> {
                        // No crítico; si falla, igual los IDs se verán tras el próximo guardado.
                    });
        }
    }

    // Helpers
    private static void putIfNotEmpty(Map<String, Object> map, String k, String v) {
        if (!TextUtils.isEmpty(v)) map.put(k, v);
    }

    private static String txt(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String nombreArchivo(Uri uri) {
        String last = uri.getLastPathSegment();
        if (last == null) return "archivo";
        int i = last.lastIndexOf('/');
        return i >= 0 ? last.substring(i + 1) : last;
    }
}
