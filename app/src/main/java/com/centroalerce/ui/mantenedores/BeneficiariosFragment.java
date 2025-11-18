package com.centroalerce.ui.mantenedores;

import android.os.Bundle;
import android.text.Editable;
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
import com.centroalerce.gestion.utils.ValidationUtils;
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

        // Botón de retroceso
        com.google.android.material.button.MaterialButton btnVolver = v.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(view -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

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

        MaterialButton btnCancelar = dlg.findViewById(R.id.btnCancelar);
        MaterialButton btnGuardar  = dlg.findViewById(R.id.btnGuardar);

        // Obtener TextInputLayouts para mostrar errores
        com.google.android.material.textfield.TextInputLayout tilNombre =
                (com.google.android.material.textfield.TextInputLayout) ((View) etNombre.getParent()).getParent();
        com.google.android.material.textfield.TextInputLayout tilRut =
                (com.google.android.material.textfield.TextInputLayout) ((View) etRut.getParent()).getParent();
        com.google.android.material.textfield.TextInputLayout tilTel =
                (com.google.android.material.textfield.TextInputLayout) ((View) etTel.getParent()).getParent();
        com.google.android.material.textfield.TextInputLayout tilEmail =
                (com.google.android.material.textfield.TextInputLayout) ((View) etEmail.getParent()).getParent();

        // Dropdown Socio
        if (!socioNombres.isEmpty()) {
            ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, socioNombres);
            acSocio.setAdapter(ad);
        }
        acSocio.setThreshold(0);
        acSocio.setOnClickListener(v -> acSocio.showDropDown());

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
        } else {
            // Por defecto, nuevo beneficiario debe estar activo
            swActivo.setChecked(true);
        }

        // ✅ Limpiar errores al escribir
        etNombre.addTextChangedListener(new SimpleWatcher(() -> tilNombre.setError(null)));
        etRut.addTextChangedListener(new SimpleWatcher(() -> tilRut.setError(null)));
        etTel.addTextChangedListener(new SimpleWatcher(() -> tilTel.setError(null)));
        etEmail.addTextChangedListener(new SimpleWatcher(() -> tilEmail.setError(null)));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dlg)
                .create();

        dialog.show();

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnGuardar.setOnClickListener(v -> {
            // ===== VALIDACIONES =====
            boolean isValid = true;

            // 1️⃣ Nombre (obligatorio, min 3 caracteres, solo letras)
            String nombre = getText(etNombre);
            if (!ValidationUtils.isNotEmpty(nombre)) {
                tilNombre.setError(ValidationUtils.getErrorRequired());
                tilNombre.setErrorEnabled(true);
                etNombre.requestFocus();
                isValid = false;
            } else if (!ValidationUtils.hasMinLength(nombre, 3)) {
                tilNombre.setError(ValidationUtils.getErrorMinLength(3));
                tilNombre.setErrorEnabled(true);
                etNombre.requestFocus();
                isValid = false;
            } else if (!ValidationUtils.isValidName(nombre)) {
                tilNombre.setError(ValidationUtils.getErrorInvalidName());
                tilNombre.setErrorEnabled(true);
                etNombre.requestFocus();
                isValid = false;
            } else if (!ValidationUtils.hasMaxLength(nombre, 100)) {
                tilNombre.setError(ValidationUtils.getErrorMaxLength(100));
                tilNombre.setErrorEnabled(true);
                etNombre.requestFocus();
                isValid = false;
            }

            // 2️⃣ RUT (opcional, pero si está lleno debe ser válido)
            String rut = getText(etRut);
            if (ValidationUtils.isNotEmpty(rut)) {
                if (!ValidationUtils.isValidRut(rut)) {
                    tilRut.setError(ValidationUtils.getErrorInvalidRut());
                    tilRut.setErrorEnabled(true);
                    etRut.requestFocus();
                    isValid = false;
                } else {
                    // ✅ Formatear RUT automáticamente
                    rut = ValidationUtils.formatRut(rut);
                    etRut.setText(rut);
                }
            }

            // 3️⃣ Teléfono (opcional, pero si está lleno debe ser válido)
            String tel = getText(etTel);
            if (ValidationUtils.isNotEmpty(tel)) {
                if (!ValidationUtils.isValidPhoneChile(tel)) {
                    tilTel.setError(ValidationUtils.getErrorInvalidPhone());
                    tilTel.setErrorEnabled(true);
                    etTel.requestFocus();
                    isValid = false;
                } else {
                    // ✅ Formatear teléfono automáticamente
                    tel = ValidationUtils.formatPhone(tel);
                    etTel.setText(tel);
                }
            }

            // 4️⃣ Email (opcional, pero si está lleno debe ser válido)
            String email = getText(etEmail);
            if (ValidationUtils.isNotEmpty(email)) {
                if (!ValidationUtils.isValidEmail(email)) {
                    tilEmail.setError(ValidationUtils.getErrorInvalidEmail());
                    tilEmail.setErrorEnabled(true);
                    etEmail.requestFocus();
                    isValid = false;
                }
            }

            // Si hay errores, no continuar
            if (!isValid) return;

            // ===== GUARDAR =====
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
                            Toast.makeText(getContext(), "✅ Beneficiario creado", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {
                db.collection("beneficiarios").document(editar.id).update(data)
                        .addOnSuccessListener(unused -> {
                            // 🆕 Actualizar el nombre en todas las actividades que usan este beneficiario
                            if (!nombre.equals(editar.nombre)) {
                                android.util.Log.d("Beneficiarios", "🔄 Nombre cambió de '" + editar.nombre + "' a '" + nombre + "' - actualizando actividades...");
                                actualizarNombreEnActividades(editar.id, nombre);
                            }
                            Toast.makeText(getContext(), "✅ Beneficiario actualizado", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable onAfter;
        SimpleWatcher(Runnable onAfter) { this.onAfter = onAfter; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onAfter.run(); }
    }

    private void confirmarEliminar(Beneficiario item) {
        // 🆕 Validar que no haya actividades activas usando este beneficiario
        verificarActividadesActivas(item.id, tieneActividades -> {
            if (tieneActividades) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ No se puede eliminar")
                        .setMessage("El beneficiario \"" + item.nombre + "\" está asociado a actividades activas.\n\n" +
                                "Primero debes eliminar o modificar esas actividades.")
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // Si no tiene actividades, permitir eliminar
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar beneficiario")
                    .setMessage("¿Eliminar a \"" + item.nombre + "\" de forma permanente?")
                    .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                    .setPositiveButton("Eliminar", (d, w) -> db.collection("beneficiarios").document(item.id)
                            .delete()
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(getContext(), "Eliminado", Toast.LENGTH_SHORT).show();
                                // 🆕 Remover el beneficiario de todas las actividades (si las hay)
                                removerBeneficiarioDeActividades(item.id);
                            })
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                    .show();
        });
    }

    private void toggleActivo(Beneficiario item) {
        boolean nuevo = item.activo == null || !item.activo;

        // 🆕 Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            verificarActividadesActivas(item.id, tieneActividades -> {
                if (tieneActividades) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("❌ No se puede desactivar")
                            .setMessage("El beneficiario \"" + item.nombre + "\" está asociado a actividades activas.\n\n" +
                                    "Primero debes eliminar o modificar esas actividades.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Si no tiene actividades, permitir desactivar
                db.collection("beneficiarios").document(item.id)
                        .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            });
        } else {
            // Si se va a activar, no necesita validación
            db.collection("beneficiarios").document(item.id)
                    .update("activo", nuevo, "updatedAt", FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
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

    /**
     * 🆕 Actualiza el nombre del beneficiario en todas las actividades que lo usan
     * NOTA: Beneficiarios se guardan como arrays de IDs y nombres en las actividades,
     * por lo que buscamos por ID y actualizamos el nombre manteniendo el orden
     */
    private void actualizarNombreEnActividades(String beneficiarioId, String nuevoNombre) {
        android.util.Log.d("Beneficiarios", "📝 Buscando actividades con beneficiarioId: " + beneficiarioId);

        // Actualizar en colección "activities" (EN) - campo beneficiarios_ids (array)
        db.collection("activities")
                .whereArrayContains("beneficiarios_ids", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Beneficiarios", "🔍 Encontradas " + querySnapshot.size() + " actividades con beneficiarios_ids[] contiene '" + beneficiarioId + "' en 'activities'");
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        actualizarBeneficiarioEnDoc(doc, beneficiarioId, nuevoNombre);
                        // También actualizar las citas de esta actividad
                        actualizarBeneficiarioEnCitas(doc.getId(), beneficiarioId, nuevoNombre);
                    }
                });

        // Actualizar en colección "actividades" (ES) - campo beneficiarios_ids
        db.collection("actividades")
                .whereArrayContains("beneficiarios_ids", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Beneficiarios", "🔍 Encontradas " + querySnapshot.size() + " actividades con beneficiarios_ids[] contiene '" + beneficiarioId + "' en 'actividades'");
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        actualizarBeneficiarioEnDoc(doc, beneficiarioId, nuevoNombre);
                    }
                });

        // También buscar por beneficiariosIds sin guion bajo
        db.collection("activities")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Beneficiarios", "🔍 Encontradas " + querySnapshot.size() + " actividades con beneficiariosIds[] contiene '" + beneficiarioId + "' en 'activities'");
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        actualizarBeneficiarioEnDoc(doc, beneficiarioId, nuevoNombre);
                        actualizarBeneficiarioEnCitas(doc.getId(), beneficiarioId, nuevoNombre);
                    }
                });

        db.collection("actividades")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Beneficiarios", "🔍 Encontradas " + querySnapshot.size() + " actividades con beneficiariosIds[] contiene '" + beneficiarioId + "' en 'actividades'");
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        actualizarBeneficiarioEnDoc(doc, beneficiarioId, nuevoNombre);
                    }
                });
    }

    /**
     * 🆕 Actualiza el nombre de un beneficiario específico en un documento de actividad
     * Mantiene el orden sincronizado entre IDs y nombres
     * Si el array de nombres no existe, lo crea consultando Firestore
     */
    private void actualizarBeneficiarioEnDoc(com.google.firebase.firestore.QueryDocumentSnapshot doc,
                                             String beneficiarioId, String nuevoNombre) {
        // Intentar ambas variantes de nombres de campos
        List<String> idsField = (List<String>) doc.get("beneficiarios_ids");
        List<String> nombresField = (List<String>) doc.get("beneficiarios_nombres");

        if (idsField == null) {
            idsField = (List<String>) doc.get("beneficiariosIds");
            nombresField = (List<String>) doc.get("beneficiariosNombres");
        }

        if (idsField == null) {
            android.util.Log.w("Beneficiarios", "    ⚠️ No se encontró array de IDs en actividad: " + doc.getId());
            return;
        }

        // Encontrar el índice del beneficiario
        int index = idsField.indexOf(beneficiarioId);
        if (index == -1) {
            android.util.Log.w("Beneficiarios", "    ⚠️ BeneficiarioId no encontrado en array de IDs: " + beneficiarioId);
            return;
        }

        // 🆕 Si el array de nombres no existe, crearlo consultando Firestore
        if (nombresField == null) {
            android.util.Log.d("Beneficiarios", "    🔧 Array de nombres no existe, creando desde Firestore...");
            crearArrayNombresDesdeIds(doc, idsField, beneficiarioId, nuevoNombre, index);
            return;
        }

        // Crear nueva lista de nombres con el nombre actualizado
        List<String> nuevosNombres = new ArrayList<>(nombresField);

        // 🆕 Si el array de nombres es más corto que el de IDs, completarlo
        while (nuevosNombres.size() < idsField.size()) {
            nuevosNombres.add(""); // Placeholder que se llenará después
        }

        nuevosNombres.set(index, nuevoNombre);

        // Actualizar el documento con ambas posibles variantes de campo
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("beneficiarios_nombres", nuevosNombres);
        updates.put("beneficiariosNombres", nuevosNombres);

        doc.getReference().update(updates)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("Beneficiarios", "    ✅ Actualizado: " + doc.getId());
                    android.util.Log.d("Beneficiarios", "       📊 Nuevos nombres: " + nuevosNombres);
                })
                .addOnFailureListener(e -> android.util.Log.w("Beneficiarios",
                        "Error actualizando beneficiario en actividad: " + e.getMessage()));
    }

    /**
     * 🆕 Crea el array de nombres consultando Firestore por cada ID
     */
    private void crearArrayNombresDesdeIds(com.google.firebase.firestore.QueryDocumentSnapshot doc,
                                           List<String> idsField, String beneficiarioIdActualizado,
                                           String nuevoNombre, int indexActualizado) {
        List<String> nombresArray = new ArrayList<>();
        final int[] consultasPendientes = {idsField.size()};

        android.util.Log.d("Beneficiarios", "       🔍 Consultando " + idsField.size() + " beneficiarios...");

        for (int i = 0; i < idsField.size(); i++) {
            String benefId = idsField.get(i);
            final int currentIndex = i;

            // Placeholder inicial
            nombresArray.add("");

            // Si es el beneficiario que estamos actualizando, usar el nuevo nombre directamente
            if (i == indexActualizado) {
                nombresArray.set(currentIndex, nuevoNombre);
                consultasPendientes[0]--;

                // Si era el único, actualizar inmediatamente
                if (consultasPendientes[0] == 0) {
                    actualizarArrayNombres(doc, nombresArray);
                }
                continue;
            }

            // Consultar el nombre desde Firestore
            db.collection("beneficiarios").document(benefId).get()
                    .addOnSuccessListener(benefDoc -> {
                        String nombre = benefDoc.exists() ? benefDoc.getString("nombre") : "Beneficiario desconocido";
                        nombresArray.set(currentIndex, nombre != null ? nombre : "Sin nombre");

                        consultasPendientes[0]--;
                        android.util.Log.d("Beneficiarios", "       📥 " + (idsField.size() - consultasPendientes[0]) + "/" + idsField.size() + " - " + nombre);

                        // Cuando todas las consultas terminen, actualizar el documento
                        if (consultasPendientes[0] == 0) {
                            actualizarArrayNombres(doc, nombresArray);
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.w("Beneficiarios", "       ❌ Error consultando beneficiario " + benefId + ": " + e.getMessage());
                        nombresArray.set(currentIndex, "Error");

                        consultasPendientes[0]--;
                        if (consultasPendientes[0] == 0) {
                            actualizarArrayNombres(doc, nombresArray);
                        }
                    });
        }
    }

    /**
     * 🆕 Actualiza el documento con el array de nombres completo
     */
    private void actualizarArrayNombres(com.google.firebase.firestore.QueryDocumentSnapshot doc,
                                        List<String> nombresArray) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("beneficiarios_nombres", nombresArray);
        updates.put("beneficiariosNombres", nombresArray);

        doc.getReference().update(updates)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("Beneficiarios", "    ✅✅ Array de nombres CREADO y actualizado: " + doc.getId());
                    android.util.Log.d("Beneficiarios", "       📊 Nombres completos: " + nombresArray);
                })
                .addOnFailureListener(e -> android.util.Log.w("Beneficiarios",
                        "❌ Error creando array de nombres en actividad: " + e.getMessage()));
    }

    /**
     * 🆕 Actualiza el beneficiario en todas las citas de una actividad
     */
    private void actualizarBeneficiarioEnCitas(String actividadId, String beneficiarioId, String nuevoNombre) {
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereArrayContains("beneficiarios_ids", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Beneficiarios", "      🔍 Encontradas " + querySnapshot.size() + " citas con beneficiarios_ids[] contiene '" + beneficiarioId + "' en actividad " + actividadId);
                    for (com.google.firebase.firestore.QueryDocumentSnapshot citaDoc : querySnapshot) {
                        actualizarBeneficiarioEnCita(citaDoc, beneficiarioId, nuevoNombre);
                    }
                });

        // También buscar por beneficiariosIds sin guion bajo en las citas
        db.collection("activities").document(actividadId)
                .collection("citas")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Beneficiarios", "      🔍 Encontradas " + querySnapshot.size() + " citas con beneficiariosIds[] contiene '" + beneficiarioId + "'");
                    for (com.google.firebase.firestore.QueryDocumentSnapshot citaDoc : querySnapshot) {
                        actualizarBeneficiarioEnCita(citaDoc, beneficiarioId, nuevoNombre);
                    }
                });
    }

    /**
     * 🆕 Actualiza el nombre de un beneficiario en una cita específica
     */
    private void actualizarBeneficiarioEnCita(com.google.firebase.firestore.QueryDocumentSnapshot citaDoc,
                                               String beneficiarioId, String nuevoNombre) {
        // Intentar ambas variantes de nombres de campos
        List<String> idsField = (List<String>) citaDoc.get("beneficiarios_ids");
        List<String> nombresField = (List<String>) citaDoc.get("beneficiarios_nombres");

        if (idsField == null) {
            idsField = (List<String>) citaDoc.get("beneficiariosIds");
            nombresField = (List<String>) citaDoc.get("beneficiariosNombres");
        }

        if (idsField == null || nombresField == null) return;

        // Encontrar el índice del beneficiario
        int index = idsField.indexOf(beneficiarioId);
        if (index == -1) return;

        // Crear nueva lista de nombres con el nombre actualizado
        List<String> nuevosNombres = new ArrayList<>(nombresField);
        if (index < nuevosNombres.size()) {
            nuevosNombres.set(index, nuevoNombre);

            // Actualizar la cita con ambas posibles variantes de campo
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("beneficiarios_nombres", nuevosNombres);
            updates.put("beneficiariosNombres", nuevosNombres);

            citaDoc.getReference().update(updates)
                    .addOnSuccessListener(aVoid -> android.util.Log.d("Beneficiarios", "      ✅ Cita actualizada: " + citaDoc.getId()))
                    .addOnFailureListener(e -> android.util.Log.e("Beneficiarios", "      ❌ Error actualizando cita: " + e.getMessage()));
        }
    }

    /**
     * 🆕 Remueve el beneficiario de todas las actividades (cuando se elimina)
     */
    private void removerBeneficiarioDeActividades(String beneficiarioId) {
        android.util.Log.d("Beneficiarios", "🗑️ Removiendo beneficiario " + beneficiarioId + " de todas las actividades");

        // Buscar en ambas variantes de campo
        db.collection("activities")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        removerBeneficiarioDeDoc(doc, beneficiarioId);
                    }
                });

        db.collection("activities")
                .whereArrayContains("beneficiarios_ids", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        removerBeneficiarioDeDoc(doc, beneficiarioId);
                    }
                });
    }

    /**
     * 🆕 Remueve un beneficiario de un documento de actividad
     */
    private void removerBeneficiarioDeDoc(com.google.firebase.firestore.DocumentSnapshot doc, String beneficiarioId) {
        List<String> idsField = (List<String>) doc.get("beneficiarios_ids");
        List<String> nombresField = (List<String>) doc.get("beneficiarios_nombres");

        if (idsField == null) {
            idsField = (List<String>) doc.get("beneficiariosIds");
            nombresField = (List<String>) doc.get("beneficiariosNombres");
        }

        if (idsField == null) return;

        int index = idsField.indexOf(beneficiarioId);
        if (index == -1) return;

        // Crear nuevas listas sin el beneficiario eliminado
        List<String> nuevosIds = new ArrayList<>(idsField);
        nuevosIds.remove(index);

        List<String> nuevosNombres = null;
        if (nombresField != null && index < nombresField.size()) {
            nuevosNombres = new ArrayList<>(nombresField);
            nuevosNombres.remove(index);
        }

        // Actualizar el documento
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("beneficiarios_ids", nuevosIds);
        updates.put("beneficiariosIds", nuevosIds);
        if (nuevosNombres != null) {
            updates.put("beneficiarios_nombres", nuevosNombres);
            updates.put("beneficiariosNombres", nuevosNombres);
        }

        doc.getReference().update(updates)
                .addOnSuccessListener(aVoid -> android.util.Log.d("Beneficiarios", "    ✅ Beneficiario removido de actividad: " + doc.getId()))
                .addOnFailureListener(e -> android.util.Log.e("Beneficiarios", "    ❌ Error removiendo beneficiario: " + e.getMessage()));
    }

    /**
     * 🆕 Verifica si hay actividades o citas programadas usando este beneficiario
     * IMPORTANTE: Busca actividades sin campo estado (activas por defecto) Y con estado activo
     */
    private void verificarActividadesActivas(String beneficiarioId, Callback<Boolean> callback) {
        android.util.Log.d("Beneficiarios", "🔍 Verificando actividades y citas para beneficiario: " + beneficiarioId);

        // Buscar actividades con beneficiariosIds (incluye actividades SIN campo estado)
        db.collection("activities")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Filtrar manualmente para incluir actividades sin estado o con estado activo
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        // Si NO tiene estado, o tiene estado pero NO es cancelada/completada
                        if (estado == null ||
                            (!estado.equalsIgnoreCase("cancelada") &&
                             !estado.equalsIgnoreCase("canceled") &&
                             !estado.equalsIgnoreCase("completada") &&
                             !estado.equalsIgnoreCase("completed") &&
                             !estado.equalsIgnoreCase("finalizada"))) {
                            android.util.Log.d("Beneficiarios", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereArrayContains("beneficiarios_ids", beneficiarioId)
                            .get()
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    if (estado == null ||
                                        (!estado.equalsIgnoreCase("cancelada") &&
                                         !estado.equalsIgnoreCase("canceled") &&
                                         !estado.equalsIgnoreCase("completada") &&
                                         !estado.equalsIgnoreCase("completed") &&
                                         !estado.equalsIgnoreCase("finalizada"))) {
                                        android.util.Log.d("Beneficiarios", "❌ Encontrada actividad activa: " + doc.getId() + " (estado: " + estado + ")");
                                        callback.onResult(true);
                                        return;
                                    }
                                }
                                // Si no hay actividades activas, verificar citas programadas
                                verificarCitasProgramadas(beneficiarioId, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Beneficiarios", "Error verificando actividades: " + e.getMessage());
                                callback.onResult(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Beneficiarios", "Error verificando actividades: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * 🆕 Verifica si hay citas programadas (no completadas/canceladas) usando este beneficiario
     */
    private void verificarCitasProgramadas(String beneficiarioId, Callback<Boolean> callback) {
        android.util.Log.d("Beneficiarios", "🔍 Verificando citas programadas para beneficiario: " + beneficiarioId);

        db.collectionGroup("citas")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Filtrar manualmente para incluir citas sin estado o con estado programada
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        if (estado == null ||
                            (!estado.equalsIgnoreCase("cancelada") &&
                             !estado.equalsIgnoreCase("canceled") &&
                             !estado.equalsIgnoreCase("completada") &&
                             !estado.equalsIgnoreCase("completed") &&
                             !estado.equalsIgnoreCase("finalizada"))) {
                            android.util.Log.d("Beneficiarios", "❌ Encontrada cita programada: " + doc.getId() + " (estado: " + estado + ")");
                            callback.onResult(true);
                            return;
                        }
                    }
                    android.util.Log.d("Beneficiarios", "✅ No hay actividades ni citas activas");
                    callback.onResult(false);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Beneficiarios", "Error verificando citas: " + e.getMessage());
                    callback.onResult(false);
                });
    }

    /**
     * Interfaz para callbacks
     */
    private interface Callback<T> {
        void onResult(T result);
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
