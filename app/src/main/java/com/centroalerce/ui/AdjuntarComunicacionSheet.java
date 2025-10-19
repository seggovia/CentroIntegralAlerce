package com.centroalerce.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.centroalerce.gestion.utils.PermissionChecker;
import com.centroalerce.gestion.utils.RoleManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class AdjuntarComunicacionSheet extends BottomSheetDialogFragment {

    private Uri fileUri;
    private ActivityResultLauncher<Intent> pickerLauncher;

    // ✅ NUEVO: Sistema de roles
    private PermissionChecker permissionChecker;
    private RoleManager roleManager;

    public static AdjuntarComunicacionSheet newInstance(String actividadId) {
        AdjuntarComunicacionSheet f = new AdjuntarComunicacionSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        f.setArguments(b);
        return f;
    }

    private int resId(String name, String defType) {
        return requireContext().getResources().getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    @Override
    public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);

        // ✅ NUEVO: Inicializar sistema de roles
        permissionChecker = new PermissionChecker();
        roleManager = RoleManager.getInstance();

        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        fileUri = result.getData().getData();
                        View dialogView = getView();
                        if (dialogView != null) {
                            TextView tv = dialogView.findViewById(id("tvArchivo"));
                            if (tv != null && fileUri != null) {
                                String last = fileUri.getLastPathSegment();
                                tv.setText(last != null ? last : fileUri.toString());
                            }
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(layout("sheet_adjuntar_comunicacion"), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // ✅ NUEVO: Verificar permisos ANTES de hacer cualquier cosa
        if (!permissionChecker.checkAndNotify(getContext(),
                PermissionChecker.Permission.ATTACH_FILES)) {
            android.util.Log.d("AdjuntarSheet", "🚫 Usuario sin permisos para adjuntar archivos");
            dismiss();
            return;
        }

        android.util.Log.d("AdjuntarSheet", "✅ Usuario autorizado para adjuntar archivos");

        String actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";

        TextView tvArchivo = v.findViewById(id("tvArchivo"));
        Button btnSeleccionar = v.findViewById(id("btnSeleccionarArchivo"));
        Button btnSubir = v.findViewById(id("btnSubir"));

        btnSeleccionar.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            pickerLauncher.launch(intent);
        });

        btnSubir.setOnClickListener(view -> {
            // ✅ NUEVO: Doble verificación antes de subir
            if (!permissionChecker.checkAndNotify(getContext(),
                    PermissionChecker.Permission.ATTACH_FILES)) {
                return;
            }

            if (fileUri == null) {
                Toast.makeText(requireContext(), "Selecciona un archivo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(actividadId)) {
                Toast.makeText(requireContext(), "Falta actividadId", Toast.LENGTH_SHORT).show();
                return;
            }

            subirArchivo(actividadId);
        });
    }

    private void subirArchivo(String actividadId) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        String fileName = obtenerNombreArchivo(fileUri);
        StorageReference ref =
                storage.getReference().child("activities").child(actividadId).child("adjuntos").child(fileName);

        // Subir a Storage y luego guardar metadata
        ref.putFile(fileUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(download -> {
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("nombre", fileName);
                    meta.put("url", download.toString());
                    meta.put("creadoEn", Timestamp.now());

                    // ✅ NUEVO: Registrar quién adjuntó el archivo
                    String userId = roleManager.getCurrentUserId();
                    if (userId != null) {
                        meta.put("subidoPor", userId);
                    }

                    // Subcolección
                    db.collection("activities").document(actividadId)
                            .collection("adjuntos").add(meta);

                    // Espejo en array del doc principal (opcional)
                    db.collection("activities").document(actividadId)
                            .update("adjuntos", FieldValue.arrayUnion(meta));

                    // ✅ NUEVO: Registrar auditoría
                    registrarAuditoria("adjuntar_archivo", actividadId, fileName);

                    // Notifica al detalle para que recargue
                    Bundle res = new Bundle();
                    res.putBoolean("adjunto_subido", true);
                    res.putString("actividadId", actividadId);
                    try {
                        getParentFragmentManager().setFragmentResult("adjuntos_change", res);
                    } catch (Exception ignore) {}

                    android.util.Log.d("AdjuntarSheet", "✅ Archivo adjuntado por usuario: " + userId);
                    Toast.makeText(requireContext(), "Adjunto subido", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("AdjuntarSheet", "❌ Error al subir archivo: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error al subir: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * ✅ NUEVO: Registra la acción de adjuntar en auditoría
     */
    private void registrarAuditoria(String accion, String actividadId, String nombreArchivo) {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> audit = new HashMap<>();
            audit.put("accion", accion);
            audit.put("actividadId", actividadId);
            audit.put("nombreArchivo", nombreArchivo);
            audit.put("timestamp", Timestamp.now());

            String userId = roleManager.getCurrentUserId();
            if (userId != null) {
                audit.put("userId", userId);
            }

            db.collection("auditoria").add(audit);
        } catch (Exception ignored) {}
    }

    private String obtenerNombreArchivo(Uri uri) {
        String last = uri.getLastPathSegment();
        if (last == null) return "archivo";
        int idx = last.lastIndexOf('/');
        return idx >= 0 ? last.substring(idx + 1) : last;
    }
}