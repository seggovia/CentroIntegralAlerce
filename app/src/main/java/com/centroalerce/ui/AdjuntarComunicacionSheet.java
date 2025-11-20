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

import com.centroalerce.gestion.utils.CustomToast;
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
        // layout file: res/layout/sheet_adjuntar_comunicacion.xml
        return inflater.inflate(layout("sheet_adjuntar_comunicacion"), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        String actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";

        TextView tvArchivo = v.findViewById(id("tvArchivo"));
        com.google.android.material.button.MaterialButton btnSeleccionar = v.findViewById(id("btnSeleccionarArchivo")); // ✅ CORREGIDO
        com.google.android.material.button.MaterialButton btnSubir = v.findViewById(id("btnSubir")); // ✅ CORREGIDO

        btnSeleccionar.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            pickerLauncher.launch(intent);
        });

        btnSubir.setOnClickListener(view -> {
            if (fileUri == null) {
                CustomToast.showError(getContext(), "Selecciona un archivo");
                return;
            }
            if (TextUtils.isEmpty(actividadId)) {
                CustomToast.showError(getContext(), "Falta actividadId");
                return;
            }

            // Crear ProgressDialog
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
            progressDialog.setMessage("Subiendo archivo...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            // ✅ Deshabilitar botón mientras sube
            btnSubir.setEnabled(false);

            FirebaseStorage storage = FirebaseStorage.getInstance();
            String fileName = obtenerNombreArchivo(fileUri);
            StorageReference ref = storage.getReference()
                    .child("activities")
                    .child(actividadId)
                    .child("attachments")
                    .child(fileName);

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
                        meta.put("name", fileName);
                        meta.put("url", download.toString());
                        meta.put("creadoEn", Timestamp.now());

                        // Subcolección (para queries ordenadas)
                        db.collection("activities").document(actividadId)
                                .collection("adjuntos").add(meta)
                                .addOnSuccessListener(docRef -> {
                                    // Agregar el ID del documento al metadata
                                    meta.put("id", docRef.getId());
                                    docRef.update("id", docRef.getId());

                                    // ✅ Actualizar array en el documento principal
                                    db.collection("activities").document(actividadId)
                                            .update("adjuntos", FieldValue.arrayUnion(meta))
                                            .addOnSuccessListener(aVoid -> {
                                                // También actualizar en colección ES
                                                db.collection("actividades").document(actividadId)
                                                        .update("adjuntos", FieldValue.arrayUnion(meta))
                                                        .addOnSuccessListener(aVoid2 -> {
                                                            // ✅ AHORA SÍ: Ambas actualizaciones completadas, notificar
                                                            android.util.Log.d("AdjuntarSheet", "✅ Adjuntos actualizados en ambas colecciones - enviando evento");
                                                            enviarEventoYCerrar(progressDialog);
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            // Si falla la actualización en ES, igual notificar (EN ya se actualizó)
                                                            android.util.Log.w("AdjuntarSheet", "⚠️ Error actualizando en ES, pero EN OK: " + e.getMessage());
                                                            enviarEventoYCerrar(progressDialog);
                                                        });
                                            })
                                            .addOnFailureListener(e -> {
                                                android.util.Log.e("AdjuntarSheet", "❌ Error actualizando adjuntos en EN: " + e.getMessage());
                                                progressDialog.dismiss();
                                                btnSubir.setEnabled(true);
                                                CustomToast.showError(getContext(), "Error al actualizar actividad: " + e.getMessage());
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    progressDialog.dismiss();
                                    btnSubir.setEnabled(true);
                                    CustomToast.showError(getContext(), "Error al guardar: " + e.getMessage());
                                });
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        btnSubir.setEnabled(true);
                        CustomToast.showError(getContext(), "Error al subir: " + e.getMessage());
                    });
        });
    }

    /**
     * ✅ NUEVO: Envía el evento y cierra el sheet CON DELAY para que el listener lo procese
     */
    private void enviarEventoYCerrar(android.app.ProgressDialog progressDialog) {
        Bundle res = new Bundle();
        res.putBoolean("adjunto_subido", true);
        res.putLong("timestamp", System.currentTimeMillis());

        android.util.Log.d("AdjuntarSheet", "📤 Enviando evento adjuntos_change...");

        try {
            getParentFragmentManager().setFragmentResult("adjuntos_change", res);
            android.util.Log.d("AdjuntarSheet", "✅ Evento enviado a ParentFragmentManager");
        } catch (Exception e) {
            android.util.Log.w("AdjuntarSheet", "⚠️ Error enviando a ParentFragmentManager: " + e.getMessage());
        }

        try {
            requireActivity().getSupportFragmentManager().setFragmentResult("adjuntos_change", res);
            android.util.Log.d("AdjuntarSheet", "✅ Evento enviado a Activity FragmentManager");
        } catch (Exception e) {
            android.util.Log.w("AdjuntarSheet", "⚠️ Error enviando a Activity: " + e.getMessage());
        }

        progressDialog.dismiss();
        CustomToast.showSuccess(getContext(), "Archivo adjuntado con éxito");

        // ✅ NO cerrar inmediatamente - esperar 300ms para que el listener procese el evento
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.util.Log.d("AdjuntarSheet", "🚪 Cerrando sheet después de enviar evento");
            dismiss();
        }, 300);
    }

    private String obtenerNombreArchivo(Uri uri) {
        String last = uri.getLastPathSegment();
        if (last == null) return "archivo";
        int idx = last.lastIndexOf('/');
        return idx >= 0 ? last.substring(idx + 1) : last;
    }
}
