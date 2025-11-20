package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.*;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.utils.CustomToast;
import com.centroalerce.gestion.utils.PermissionChecker;
import com.centroalerce.gestion.utils.RoleManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class CancelarActividadSheet extends BottomSheetDialogFragment {
    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    private static final String ARG_CITA_ID = "citaId";

    public static CancelarActividadSheet newInstance(@NonNull String actividadId, @Nullable String citaId){
        Bundle b = new Bundle();
        b.putString(ARG_ACTIVIDAD_ID, actividadId);
        if (!TextUtils.isEmpty(citaId)) b.putString(ARG_CITA_ID, citaId);
        CancelarActividadSheet s = new CancelarActividadSheet();
        s.setArguments(b);
        return s;
    }

    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance().collection(preferEN ? COL_EN : COL_ES).document(actividadId);
    }

    // ✅ NUEVO: Sistema de roles
    private PermissionChecker permissionChecker;
    private RoleManager roleManager;

    private FirebaseFirestore db;
    private String actividadId, citaId;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.sheet_cancelar_actividad, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        // ✅ NUEVO: Inicializar sistema de roles
        permissionChecker = new PermissionChecker();
        roleManager = RoleManager.getInstance();

        // ✅ NUEVO: Verificar permisos ANTES de hacer cualquier cosa
        if (!permissionChecker.checkAndNotify(getContext(),
                PermissionChecker.Permission.CANCEL_ACTIVITY)) {
            android.util.Log.d("CancelarSheet", "🚫 Usuario sin permisos para cancelar");
            dismiss();
            return;
        }

        android.util.Log.d("CancelarSheet", "✅ Usuario autorizado para cancelar");

        db = FirebaseFirestore.getInstance();
        Bundle args = getArguments();
        actividadId = args != null ? args.getString(ARG_ACTIVIDAD_ID) : null;
        citaId = args != null ? args.getString(ARG_CITA_ID) : null;

        TextInputEditText etMotivo = v.findViewById(R.id.etMotivo);

        v.findViewById(R.id.btnCancelarAccion).setOnClickListener(x -> dismiss());

        MaterialButton btnCancelar = v.findViewById(R.id.btnCancelarActividad);
        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(x -> {
                // ✅ NUEVO: Doble verificación antes de cancelar
                if (!permissionChecker.checkAndNotify(getContext(),
                        PermissionChecker.Permission.CANCEL_ACTIVITY)) {
                    return;
                }

                String motivo = etMotivo.getText() != null ? etMotivo.getText().toString().trim() : "";
                if (TextUtils.isEmpty(motivo) || motivo.length() < 6) {
                    etMotivo.setError("Describe el motivo (≥ 6)");
                    return;
                }
                if (TextUtils.isEmpty(actividadId)) {
                    toast("Falta actividadId");
                    return;
                }

                // Crear ProgressDialog
                android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
                progressDialog.setMessage("Cancelando actividad...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                // Acción: cancelar cita específica o toda la actividad
                if (!TextUtils.isEmpty(citaId)) cancelarSoloCita(motivo, progressDialog);
                else cancelarActividadCompleta(motivo, progressDialog);
            });
        }
    }

    private void cancelarSoloCita(String motivo, android.app.ProgressDialog progressDialog){
        DocumentReference citaES = act(actividadId, false).collection("citas").document(citaId);
        DocumentReference citaEN = act(actividadId, true).collection("citas").document(citaId);

        citaES.get().addOnSuccessListener(d -> {
            DocumentReference ref = (d != null && d.exists()) ? citaES : citaEN;
            Map<String, Object> updates = new HashMap<>();
            updates.put("estado", "cancelada");
            updates.put("motivo_cancelacion", motivo);
            updates.put("fecha_cancelacion", Timestamp.now());

            // ✅ NUEVO: Log de auditoría con usuario
            String userId = roleManager.getCurrentUserId();
            if (userId != null) {
                updates.put("lastModifiedBy", userId);
            }

            ref.update(updates)
                    .addOnSuccessListener(u -> {
                        android.util.Log.d("CancelarSheet", "✅ Cita cancelada por usuario: " + userId);

                        // También cancelar la actividad principal si es PUNTUAL
                        cancelarActividadSiEsPuntual(actividadId, motivo);

                        progressDialog.dismiss();
                        CustomToast.showSuccess(getContext(), "Cita cancelada con éxito");
                        registrarAuditoria("cancelar_cita", motivo);
                        notifyChanged();
                        dismiss();
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("CancelarSheet", "❌ Error al cancelar cita: " + e.getMessage());
                        progressDialog.dismiss();
                        CustomToast.showError(getContext(), "Error al cancelar: " + e.getMessage());
                    });
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            CustomToast.showError(getContext(), "Error al cancelar: " + e.getMessage());
        });
    }

    private void cancelarActividadCompleta(String motivo, android.app.ProgressDialog progressDialog){
        WriteBatch batch = db.batch();
        DocumentReference actES = act(actividadId, false);
        DocumentReference actEN = act(actividadId, true);

        actES.get().addOnSuccessListener(aes -> {
            DocumentReference actRef = (aes != null && aes.exists()) ? actES : actEN;

            // Actualizar actividad principal
            Map<String, Object> updates = new HashMap<>();
            updates.put("estado", "cancelada");
            updates.put("motivo_cancelacion", motivo);
            updates.put("fecha_cancelacion", Timestamp.now());

            // ✅ NUEVO: Log de auditoría con usuario
            String userId = roleManager.getCurrentUserId();
            if (userId != null) {
                updates.put("lastModifiedBy", userId);
            }

            batch.update(actRef, updates);

            // Cancelar todas las citas dentro de la actividad
            actRef.collection("citas").get().addOnSuccessListener(q -> {
                for (DocumentSnapshot d : q.getDocuments()){
                    batch.update(d.getReference(), updates);
                }
                batch.commit()
                        .addOnSuccessListener(u -> {
                            android.util.Log.d("CancelarSheet", "✅ Actividad cancelada por usuario: " + userId);
                            progressDialog.dismiss();
                            CustomToast.showSuccess(getContext(), "Actividad cancelada con éxito");
                            registrarAuditoria("cancelar_actividad", motivo);
                            notifyChanged();
                            dismiss();
                        })
                        .addOnFailureListener(e -> {
                            android.util.Log.e("CancelarSheet", "❌ Error al cancelar actividad: " + e.getMessage());
                            progressDialog.dismiss();
                            CustomToast.showError(getContext(), "Error al cancelar: " + e.getMessage());
                        });
            }).addOnFailureListener(e -> {
                progressDialog.dismiss();
                CustomToast.showError(getContext(), "Error al listar citas: " + e.getMessage());
            });
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            CustomToast.showError(getContext(), "Error al cancelar: " + e.getMessage());
        });
    }

    /**
     * Cancela la actividad principal si es PUNTUAL
     */
    private void cancelarActividadSiEsPuntual(String actividadId, String motivo) {
        DocumentReference actRef = db.collection("activities").document(actividadId);

        actRef.get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                String periodicidad = doc.getString("periodicidad");
                if ("PUNTUAL".equalsIgnoreCase(periodicidad)) {
                    // Es PUNTUAL, cancelar también la actividad principal
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estado", "cancelada");
                    updates.put("motivo_cancelacion", motivo);
                    updates.put("fecha_cancelacion", Timestamp.now());

                    String userId = roleManager.getCurrentUserId();
                    if (userId != null) {
                        updates.put("lastModifiedBy", userId);
                    }

                    actRef.update(updates)
                            .addOnSuccessListener(unused ->
                                android.util.Log.d("CancelarSheet", "✅ Actividad PUNTUAL cancelada"))
                            .addOnFailureListener(e ->
                                android.util.Log.e("CancelarSheet", "❌ Error cancelando actividad principal: " + e.getMessage()));
                }
            }
        }).addOnFailureListener(e ->
            android.util.Log.e("CancelarSheet", "❌ Error obteniendo actividad: " + e.getMessage()));
    }

    private void registrarAuditoria(String accion, String motivo){
        try {
            Map<String, Object> audit = new HashMap<>();
            audit.put("accion", accion);
            audit.put("motivo", motivo);
            audit.put("timestamp", Timestamp.now());
            audit.put("actividadId", actividadId);
            if (!TextUtils.isEmpty(citaId)) audit.put("citaId", citaId);

            // ✅ NUEVO: Registrar quién hizo la acción
            String userId = roleManager.getCurrentUserId();
            if (userId != null) audit.put("userId", userId);

            db.collection("auditoria").add(audit);
        } catch (Exception ignored) {}
    }

    private void notifyChanged(){
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        if (!TextUtils.isEmpty(citaId)) b.putString("citaId", citaId);

        try {
            getParentFragmentManager().setFragmentResult("actividad_change", b);
        } catch (Exception ignore) {}
        try {
            getParentFragmentManager().setFragmentResult("calendar_refresh", b);
        } catch (Exception ignore) {}
        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b);
        } catch (Exception ignore) {}
        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b);
        } catch (Exception ignore) {}
    }

    private void toast(String m){
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}