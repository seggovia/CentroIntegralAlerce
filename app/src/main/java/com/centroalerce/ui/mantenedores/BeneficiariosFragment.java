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
import java.util.HashMap;
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
        // Mostrar diálogo de carga mientras se verifican las actividades
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Verificando actividades asociadas...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 🆕 Validar que no haya actividades activas usando este beneficiario
        // Forzar lectura desde servidor para obtener datos actualizados
        android.util.Log.d("Beneficiarios", "🔄 Iniciando validación con datos del servidor para: " + item.nombre);
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarYActualizarActividadesDetallado(item.id, item.nombre, resultado, tieneActividades -> {
            progressDialog.dismiss();
            if (tieneActividades) {
                String mensaje = resultado.construirMensaje("beneficiario", item.nombre);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ No se puede eliminar")
                        .setMessage(mensaje)
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // Si no tiene actividades activas, permitir eliminar
            String mensajeConfirmacion = "¿Eliminar a \"" + item.nombre + "\" de forma permanente?";
            if (resultado.actividadesCompletadas > 0 || resultado.citasCompletadas > 0) {
                mensajeConfirmacion += "\n\nSe actualizarán " +
                    (resultado.actividadesCompletadas + resultado.citasCompletadas) +
                    " actividades/citas completadas/canceladas a \"--\".";
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar beneficiario")
                    .setMessage(mensajeConfirmacion)
                    .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                    .setPositiveButton("Eliminar", (d, w) -> {
                        // Primero actualizar actividades/citas completadas a "--"
                        actualizarBeneficiarioAGuion(item.id, () -> {
                            // Luego eliminar el beneficiario
                            db.collection("beneficiarios").document(item.id)
                                    .delete()
                                    .addOnSuccessListener(unused ->
                                        Toast.makeText(getContext(), "Eliminado", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        });
                    })
                    .show();
        });
    }

    private void toggleActivo(Beneficiario item) {
        boolean nuevo = item.activo == null || !item.activo;

        // 🆕 Si se va a desactivar, validar que no haya actividades activas
        if (!nuevo) {
            ResultadoValidacion resultado = new ResultadoValidacion();
            verificarActividadesActivasDetallado(item.id, resultado, tieneActividades -> {
                if (tieneActividades) {
                    String mensaje = resultado.construirMensaje("beneficiario", item.nombre);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("❌ No se puede desactivar")
                            .setMessage(mensaje)
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
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarActividadesActivasDetallado(beneficiarioId, resultado, callback);
    }

    private void verificarYActualizarActividadesDetallado(String beneficiarioId, String beneficiarioNombre, ResultadoValidacion resultado, Callback<Boolean> callback) {
        verificarActividadesActivasDetallado(beneficiarioId, resultado, callback);
    }

    private void verificarActividadesActivasDetallado(String beneficiarioId, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Beneficiarios", "🔍 Verificando actividades y citas para beneficiario: " + beneficiarioId);

        // Set para evitar duplicados (usar ID del documento como clave)
        java.util.Set<String> actividadesEncontradas = new java.util.HashSet<>();

        // Buscar actividades con beneficiariosIds
        db.collection("activities")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("Beneficiarios", "  ✅ Query completado. Total actividades encontradas: " + querySnapshot.size());
                    android.util.Log.d("Beneficiarios", "  📡 Fuente de datos: " + (querySnapshot.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));

                    // Recopilar actividades bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        String nombre = doc.getString("nombre");
                        android.util.Log.d("Beneficiarios", "  📄 Actividad: id=" + doc.getId() + ", nombre=" + nombre + ", estado='" + estado + "'");

                        // Excluir explícitamente las completadas
                        boolean esCompletada = estado != null &&
                            (estado.equalsIgnoreCase("completada") ||
                             estado.equalsIgnoreCase("finalizada") ||
                             estado.equalsIgnoreCase("cancelada") ||
                             estado.equalsIgnoreCase("completed") ||
                             estado.equalsIgnoreCase("canceled"));

                        if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                            if (nombre == null) nombre = "Sin nombre";
                            String estadoStr = (estado == null) ? "Programada" : estado;
                            // Usar ID para evitar duplicados
                            if (actividadesEncontradas.add(doc.getId())) {
                                resultado.actividadesBloqueantes.add(nombre + " (" + estadoStr + ")");
                                resultado.tieneBloqueantes = true;
                                android.util.Log.d("Beneficiarios", "  ✅ Agregada como bloqueante: " + nombre);
                            }
                        } else {
                            // Es completada/cancelada, contar para actualizar a "--"
                            if (esCompletada) {
                                resultado.actividadesCompletadas++;
                            }
                            android.util.Log.d("Beneficiarios", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                        }
                    }

                    // Buscar con el otro nombre de campo
                    db.collection("activities")
                            .whereArrayContains("beneficiarios_ids", beneficiarioId)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                android.util.Log.d("Beneficiarios", "  ✅ Segunda query completado. Total actividades encontradas: " + querySnapshot2.size());
                                android.util.Log.d("Beneficiarios", "  📡 Fuente de datos: " + (querySnapshot2.getMetadata().isFromCache() ? "CACHE ⚠️" : "SERVER ✅"));

                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    String nombre = doc.getString("nombre");
                                    android.util.Log.d("Beneficiarios", "  📄 Actividad encontrada (beneficiarios_ids): id=" + doc.getId() + ", nombre=" + nombre + ", estado='" + estado + "'");

                                    // Excluir explícitamente las completadas
                                    boolean esCompletada = estado != null &&
                                        (estado.equalsIgnoreCase("completada") ||
                                         estado.equalsIgnoreCase("finalizada") ||
                                         estado.equalsIgnoreCase("cancelada") ||
                                         estado.equalsIgnoreCase("completed") ||
                                         estado.equalsIgnoreCase("canceled"));

                                    if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        if (nombre == null) nombre = "Sin nombre";
                                        String estadoStr = (estado == null) ? "Programada" : estado;
                                        if (actividadesEncontradas.add(doc.getId())) {
                                            resultado.actividadesBloqueantes.add(nombre + " (" + estadoStr + ")");
                                            resultado.tieneBloqueantes = true;
                                            android.util.Log.d("Beneficiarios", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    } else {
                                        android.util.Log.d("Beneficiarios", "  ⏭️ Ignorada (completada/cancelada): " + nombre);
                                    }
                                }
                                // Verificar citas programadas
                                verificarCitasProgramadasDetallado(beneficiarioId, resultado, callback);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Beneficiarios", "Error verificando actividades con beneficiarios_ids: " + e.getMessage());
                                // Si ya encontramos bloqueantes, retornar true aunque falle esta búsqueda
                                if (resultado.tieneBloqueantes) {
                                    callback.onResult(true);
                                } else {
                                    // Si no había bloqueantes, continuar con citas
                                    verificarCitasProgramadasDetallado(beneficiarioId, resultado, callback);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Beneficiarios", "Error verificando actividades con beneficiariosIds: " + e.getMessage());
                    // Continuar buscando con el otro campo aunque falle
                    db.collection("activities")
                            .whereArrayContains("beneficiarios_ids", beneficiarioId)
                            .get()
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");
                                    android.util.Log.d("Beneficiarios", "  📄 Actividad encontrada: " + doc.getId() + " estado=" + estado);
                                    if (estado != null && (estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        String nombre = doc.getString("nombre");
                                        if (nombre == null) nombre = "Sin nombre";
                                        // Usar ID para evitar duplicados
                                        if (actividadesEncontradas.add(doc.getId())) {
                                            resultado.actividadesBloqueantes.add(nombre + " (" + estado + ")");
                                            resultado.tieneBloqueantes = true;
                                            android.util.Log.d("Beneficiarios", "  ✅ Agregada como bloqueante: " + nombre);
                                        }
                                    }
                                }
                                verificarCitasProgramadasDetallado(beneficiarioId, resultado, callback);
                            })
                            .addOnFailureListener(e2 -> {
                                // Si ambas búsquedas de actividades fallan, continuar con citas
                                verificarCitasProgramadasDetallado(beneficiarioId, resultado, callback);
                            });
                });
    }

    /**
     * 🆕 Verifica si hay citas programadas (no completadas/canceladas) usando este beneficiario
     */
    private void verificarCitasProgramadas(String beneficiarioId, Callback<Boolean> callback) {
        ResultadoValidacion resultado = new ResultadoValidacion();
        verificarCitasProgramadasDetallado(beneficiarioId, resultado, callback);
    }

    private void verificarCitasProgramadasDetallado(String beneficiarioId, ResultadoValidacion resultado, Callback<Boolean> callback) {
        android.util.Log.d("Beneficiarios", "🔍 Verificando citas programadas para beneficiario: " + beneficiarioId);

        db.collectionGroup("citas")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    // Recopilar citas bloqueantes
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");

                        // Excluir explícitamente las completadas
                        boolean esCompletada = estado != null &&
                            (estado.equalsIgnoreCase("completada") ||
                             estado.equalsIgnoreCase("finalizada") ||
                             estado.equalsIgnoreCase("cancelada") ||
                             estado.equalsIgnoreCase("completed") ||
                             estado.equalsIgnoreCase("canceled"));

                        if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                            String titulo = doc.getString("titulo");
                            if (titulo == null) titulo = "Sin título";
                            String estadoStr = (estado == null) ? "Programada" : estado;

                            // Obtener fecha si existe
                            Object fechaObj = doc.get("fecha");
                            String fechaStr = "";
                            if (fechaObj instanceof com.google.firebase.Timestamp) {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MMM/yyyy", java.util.Locale.getDefault());
                                fechaStr = " - " + sdf.format(((com.google.firebase.Timestamp) fechaObj).toDate());
                            }

                            resultado.citasBloqueantes.add(titulo + " (" + estadoStr + fechaStr + ")");
                            resultado.tieneBloqueantes = true;
                        }
                    }

                    // Buscar también con el otro nombre de campo (beneficiarios_ids con guion bajo)
                    db.collectionGroup("citas")
                            .whereArrayContains("beneficiarios_ids", beneficiarioId)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener(querySnapshot2 -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot2) {
                                    String estado = doc.getString("estado");

                                    // Excluir explícitamente las completadas
                                    boolean esCompletada = estado != null &&
                                        (estado.equalsIgnoreCase("completada") ||
                                         estado.equalsIgnoreCase("finalizada") ||
                                         estado.equalsIgnoreCase("cancelada") ||
                                         estado.equalsIgnoreCase("completed") ||
                                         estado.equalsIgnoreCase("canceled"));

                                    if (!esCompletada && (estado == null || estado.equalsIgnoreCase("programada") || estado.equalsIgnoreCase("reagendada"))) {
                                        String titulo = doc.getString("titulo");
                                        if (titulo == null) titulo = "Sin título";
                                        String estadoStr = (estado == null) ? "Programada" : estado;

                                        Object fechaObj = doc.get("fecha");
                                        String fechaStr = "";
                                        if (fechaObj instanceof com.google.firebase.Timestamp) {
                                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MMM/yyyy", java.util.Locale.getDefault());
                                            fechaStr = " - " + sdf.format(((com.google.firebase.Timestamp) fechaObj).toDate());
                                        }

                                        resultado.citasBloqueantes.add(titulo + " (" + estadoStr + fechaStr + ")");
                                        resultado.tieneBloqueantes = true;
                                    }
                                }

                                android.util.Log.d("Beneficiarios", resultado.tieneBloqueantes ? "❌ Encontradas actividades/citas bloqueantes" : "✅ No hay actividades ni citas activas");
                                callback.onResult(resultado.tieneBloqueantes);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("Beneficiarios", "Error verificando citas con beneficiarios_ids: " + e.getMessage());
                                // Si ya encontramos bloqueantes en actividades, retornar true aunque falle la búsqueda de citas
                                callback.onResult(resultado.tieneBloqueantes);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Beneficiarios", "Error verificando citas con beneficiariosIds: " + e.getMessage());
                    // Si ya encontramos bloqueantes en actividades, retornar true aunque falle la búsqueda de citas
                    callback.onResult(resultado.tieneBloqueantes);
                });
    }

    /**
     * Actualiza todas las actividades/citas completadas/canceladas reemplazando el beneficiario por "--"
     */
    private void actualizarBeneficiarioAGuion(String beneficiarioId, Runnable onComplete) {
        android.util.Log.d("Beneficiarios", "🔄 Actualizando beneficiario a '--' en actividades completadas/canceladas");

        // Actualizar actividades
        db.collection("activities")
                .whereArrayContains("beneficiariosIds", beneficiarioId)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    final int[] count = {0};

                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        String estado = doc.getString("estado");
                        boolean esCompletada = estado != null &&
                            (estado.equalsIgnoreCase("completada") ||
                             estado.equalsIgnoreCase("finalizada") ||
                             estado.equalsIgnoreCase("cancelada") ||
                             estado.equalsIgnoreCase("completed") ||
                             estado.equalsIgnoreCase("canceled"));

                        if (esCompletada) {
                            // Actualizar los arrays de beneficiarios
                            List<String> beneficiariosIds = (List<String>) doc.get("beneficiariosIds");
                            if (beneficiariosIds == null) {
                                beneficiariosIds = (List<String>) doc.get("beneficiarios_ids");
                            }

                            if (beneficiariosIds != null && beneficiariosIds.contains(beneficiarioId)) {
                                // Crear nueva lista sin el beneficiario eliminado
                                List<String> nuevosIds = new ArrayList<>(beneficiariosIds);
                                nuevosIds.remove(beneficiarioId);

                                Map<String, Object> updates = new HashMap<>();

                                // Si era el único beneficiario, poner todos los campos en "--"
                                if (nuevosIds.isEmpty()) {
                                    updates.put("beneficiariosTexto", "--");
                                    updates.put("beneficiarios", java.util.Collections.singletonList("--"));
                                    updates.put("beneficiariosNombres", java.util.Collections.singletonList("--"));
                                    updates.put("beneficiarios_nombres", java.util.Collections.singletonList("--"));
                                    updates.put("beneficiariosIds", new ArrayList<>());
                                    updates.put("beneficiarios_ids", new ArrayList<>());
                                } else {
                                    // Si quedan más beneficiarios, solo actualizar los arrays
                                    updates.put("beneficiariosIds", nuevosIds);
                                    updates.put("beneficiarios_ids", nuevosIds);
                                    // También actualizar los arrays de nombres (remover el nombre en la misma posición)
                                    List<String> beneficiariosNombres = (List<String>) doc.get("beneficiariosNombres");
                                    if (beneficiariosNombres == null) {
                                        beneficiariosNombres = (List<String>) doc.get("beneficiarios_nombres");
                                    }
                                    if (beneficiariosNombres != null) {
                                        int index = beneficiariosIds.indexOf(beneficiarioId);
                                        if (index >= 0 && index < beneficiariosNombres.size()) {
                                            List<String> nuevosNombres = new ArrayList<>(beneficiariosNombres);
                                            nuevosNombres.remove(index);
                                            updates.put("beneficiariosNombres", nuevosNombres);
                                            updates.put("beneficiarios_nombres", nuevosNombres);
                                            updates.put("beneficiarios", nuevosNombres);
                                            updates.put("beneficiariosTexto", TextUtils.join(", ", nuevosNombres));
                                        }
                                    }
                                }

                                batch.update(doc.getReference(), updates);
                                count[0]++;
                            }
                        }
                    }

                    if (count[0] > 0) {
                        batch.commit()
                                .addOnSuccessListener(unused -> {
                                    android.util.Log.d("Beneficiarios", "✅ Actualizadas " + count[0] + " actividades");
                                    actualizarCitasAGuion(beneficiarioId, onComplete);
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("Beneficiarios", "❌ Error actualizando actividades: " + e.getMessage());
                                    onComplete.run();
                                });
                    } else {
                        actualizarCitasAGuion(beneficiarioId, onComplete);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("Beneficiarios", "❌ Error obteniendo actividades: " + e.getMessage());
                    onComplete.run();
                });
    }

    /**
     * Actualiza todas las citas completadas/canceladas reemplazando el beneficiario por "--"
     */
    private void actualizarCitasAGuion(String beneficiarioId, Runnable onComplete) {
        // Por ahora solo completar, las citas las haremos después si es necesario
        onComplete.run();
    }

    /**
     * Interfaz para callbacks
     */
    private interface Callback<T> {
        void onResult(T result);
    }

    /**
     * Clase para almacenar información de actividades/citas bloqueantes
     */
    private static class ResultadoValidacion {
        boolean tieneBloqueantes;
        List<String> actividadesBloqueantes;
        List<String> citasBloqueantes;
        int actividadesCompletadas = 0;
        int citasCompletadas = 0;

        ResultadoValidacion() {
            this.tieneBloqueantes = false;
            this.actividadesBloqueantes = new java.util.ArrayList<>();
            this.citasBloqueantes = new java.util.ArrayList<>();
        }

        String construirMensaje(String tipoMantenedor, String nombreMantenedor) {
            if (!tieneBloqueantes) return "";

            StringBuilder mensaje = new StringBuilder();
            mensaje.append("El ").append(tipoMantenedor).append(" \"").append(nombreMantenedor).append("\" está asociado a:\n\n");

            if (!actividadesBloqueantes.isEmpty()) {
                mensaje.append("📋 ACTIVIDADES:\n");
                for (String actividad : actividadesBloqueantes) {
                    mensaje.append("• ").append(actividad).append("\n");
                }
            }

            if (!citasBloqueantes.isEmpty()) {
                if (!actividadesBloqueantes.isEmpty()) mensaje.append("\n");
                mensaje.append("📅 CITAS:\n");
                for (String cita : citasBloqueantes) {
                    mensaje.append("• ").append(cita).append("\n");
                }
            }

            mensaje.append("\nCompleta o cancela estas actividades/citas primero.");
            return mensaje.toString();
        }
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
