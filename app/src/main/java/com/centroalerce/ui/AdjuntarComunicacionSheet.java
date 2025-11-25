package com.centroalerce.ui;

import android.app.Activity;
import android.content.ClipData;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdjuntarComunicacionSheet extends BottomSheetDialogFragment {

    private final List<Uri> selectedFiles = new ArrayList<>();
    private final List<String> selectedFileNames = new ArrayList<>();
    private ActivityResultLauncher<Intent> pickerLauncher;
    private TextView tvArchivo;
    private com.google.android.material.button.MaterialButton btnEliminar;
    private String actividadId = "";
    private String citaId = "";
    @Nullable
    private Runnable onDismissCallback;

    public static AdjuntarComunicacionSheet newInstance(String actividadId, @Nullable String citaId) {
        AdjuntarComunicacionSheet f = new AdjuntarComunicacionSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        if (!android.text.TextUtils.isEmpty(citaId)) {
            b.putString("citaId", citaId);
        }
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
                        handleSelectionResult(result.getData());
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

        actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";
        citaId      = getArguments() != null ? getArguments().getString("citaId", "")      : "";

        tvArchivo = v.findViewById(id("tvArchivo"));
        com.google.android.material.button.MaterialButton btnSeleccionar = v.findViewById(id("btnSeleccionarArchivo")); // ✅ CORREGIDO
        com.google.android.material.button.MaterialButton btnSubir = v.findViewById(id("btnSubir")); // ✅ CORREGIDO
        btnEliminar = v.findViewById(id("btnEliminar"));

        // Inicialmente ocultar botón eliminar
        if (btnEliminar != null) {
            btnEliminar.setVisibility(View.GONE);
        }

        btnSeleccionar.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pickerLauncher.launch(intent);
        });

        // Configurar botón eliminar
        if (btnEliminar != null) {
            btnEliminar.setOnClickListener(view -> {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
                builder.setTitle("¿Limpiar selección?")
                       .setMessage("Se eliminarán todos los archivos seleccionados.")
                       .setPositiveButton("Limpiar", (dialog, which) -> {
                           selectedFiles.clear();
                           selectedFileNames.clear();
                           actualizarTextoSeleccion();
                           CustomToast.showSuccess(getContext(), "Selección limpiada");
                       })
                       .setNegativeButton("Cancelar", null)
                       .show();
            });
        }

        btnSubir.setOnClickListener(view -> {
            if (selectedFiles.isEmpty()) {
                CustomToast.showError(getContext(), "Selecciona al menos un archivo");
                return;
            }
            if (TextUtils.isEmpty(actividadId)) {
                CustomToast.showError(getContext(), "Falta actividadId");
                return;
            }

            // Crear ProgressDialog
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(requireContext());
            progressDialog.setMessage("Preparando subida...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            // ✅ Deshabilitar botón mientras sube y cambiar texto
            btnSubir.setEnabled(false);
            CharSequence originalText = btnSubir.getText();
            btnSubir.setText("Subiendo...");
            uploadFileSequential(0, selectedFiles.size(), btnSubir, originalText, progressDialog);
        });
    }

    /**
     *  NUEVO: Envía el evento y cierra el sheet CON DELAY para que el listener lo procese
     */
    private void enviarEventoYCerrar(android.app.ProgressDialog progressDialog) {
        Bundle res = new Bundle();
        res.putBoolean("adjunto_subido", true);
        res.putLong("timestamp", System.currentTimeMillis());
        res.putString("actividadId", actividadId);
        res.putString("citaId", citaId);

        android.util.Log.d("AdjuntarSheet", " Enviando evento adjuntos_change...");

        try {
            getParentFragmentManager().setFragmentResult("adjuntos_change", res);
            android.util.Log.d("AdjuntarSheet", " Evento enviado a ParentFragmentManager");
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

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }

    public void setOnDismissCallback(@Nullable Runnable callback) {
        this.onDismissCallback = callback;
    }

    private void handleSelectionResult(Intent data) {
        if (data == null) return;
        boolean added = false;

        if (data.getClipData() != null) {
            ClipData clip = data.getClipData();
            for (int i = 0; clip != null && i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null && agregarArchivoSeleccionado(uri)) {
                    added = true;
                }
            }
        } else if (data.getData() != null) {
            if (agregarArchivoSeleccionado(data.getData())) {
                added = true;
            }
        }

        if (!added) {
            CustomToast.showInfo(getContext(), "No se agregó ningún archivo");
        }
        actualizarTextoSeleccion();
    }

    private boolean agregarArchivoSeleccionado(@NonNull Uri uri) {
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            requireContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ignored) {}

        String fileName = obtenerNombreArchivo(uri);
        if (TextUtils.isEmpty(fileName)) {
            fileName = "archivo" + (selectedFiles.size() + 1);
        }
        selectedFiles.add(uri);
        selectedFileNames.add(fileName);
        return true;
    }

    private void actualizarTextoSeleccion() {
        if (tvArchivo == null) return;

        if (selectedFiles.isEmpty()) {
            tvArchivo.setText("Ningún archivo seleccionado");
            if (btnEliminar != null) {
                btnEliminar.setVisibility(View.GONE);
            }
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < selectedFileNames.size(); i++) {
                builder.append(i + 1)
                        .append(". ")
                        .append(selectedFileNames.get(i))
                        .append('\n');
            }
            tvArchivo.setText(builder.toString().trim());
            if (btnEliminar != null) {
                btnEliminar.setVisibility(View.VISIBLE);
            }
        }
    }

    private void uploadFileSequential(int index, int total, com.google.android.material.button.MaterialButton btnSubir,
                                      CharSequence originalText, android.app.ProgressDialog progressDialog) {
        if (index >= total) {
            btnSubir.setEnabled(true);
            btnSubir.setText(originalText);
            selectedFiles.clear();
            selectedFileNames.clear();
            actualizarTextoSeleccion();
            enviarEventoYCerrar(progressDialog);
            return;
        }

        progressDialog.setMessage("Subiendo archivo " + (index + 1) + " de " + total + "...");

        Uri currentFile = selectedFiles.get(index);
        String fileName = selectedFileNames.get(index);

        subirArchivoIndividual(currentFile, fileName, new UploadCallbacks() {
            @Override
            public void onSuccess() {
                uploadFileSequential(index + 1, total, btnSubir, originalText, progressDialog);
            }

            @Override
            public void onError(String errorMessage) {
                progressDialog.dismiss();
                btnSubir.setEnabled(true);
                btnSubir.setText(originalText);
                CustomToast.showError(getContext(), errorMessage);
            }
        });
    }

    private void subirArchivoIndividual(Uri fileUri, String fileName, UploadCallbacks callbacks) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference ref = storage.getReference()
                .child("activities")
                .child(actividadId)
                .child("attachments")
                .child(fileName);

        ref.putFile(fileUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(download -> guardarMetadata(download.toString(), fileName, callbacks))
                .addOnFailureListener(e -> callbacks.onError("Error al subir: " + e.getMessage()));
    }

    private void guardarMetadata(String downloadUrl, String fileName, UploadCallbacks callbacks) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> meta = new HashMap<>();
        meta.put("nombre", fileName);
        meta.put("name", fileName);
        meta.put("url", downloadUrl);
        meta.put("creadoEn", Timestamp.now());

        db.collection("activities").document(actividadId)
                .collection("adjuntos").add(meta)
                .addOnSuccessListener(docRef -> {
                    meta.put("id", docRef.getId());
                    docRef.update("id", docRef.getId());
                    android.util.Log.d("AdjuntarSheet", "✅ Metadata guardada en subcolección EN (" + docRef.getId() + ")");
                    actualizarAdjuntosPrincipales(meta, callbacks);
                })
                .addOnFailureListener(e -> callbacks.onError("Error al guardar: " + e.getMessage()));
    }

    private void actualizarAdjuntosPrincipales(Map<String, Object> meta, UploadCallbacks callbacks) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("activities").document(actividadId)
                .update("adjuntos", FieldValue.arrayUnion(meta))
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("AdjuntarSheet", "✅ Array adjuntos EN actualizado");
                    db.collection("actividades").document(actividadId)
                            .update("adjuntos", FieldValue.arrayUnion(meta))
                            .addOnSuccessListener(aVoid2 -> {
                                android.util.Log.d("AdjuntarSheet", "✅ Array adjuntos ES actualizado");
                                guardarEnCitaSiCorresponde(meta, callbacks);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.w("AdjuntarSheet", "⚠️ Error actualizando array ES: " + e.getMessage());
                                guardarEnCitaSiCorresponde(meta, callbacks);
                            });
                })
                .addOnFailureListener(e -> callbacks.onError("Error al actualizar actividad: " + e.getMessage()));
    }

    private void guardarEnCitaSiCorresponde(Map<String, Object> meta, UploadCallbacks callbacks) {
        if (TextUtils.isEmpty(citaId)) {
            callbacks.onSuccess();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference citaEn = db.collection("activities").document(actividadId)
                .collection("citas").document(citaId);

        citaEn.collection("adjuntos").add(meta)
                .addOnSuccessListener(citaDoc -> {
                    android.util.Log.d("AdjuntarSheet", "✅ Adjunto guardado en cita EN " + citaId);
                    citaEn.update("adjuntos", FieldValue.arrayUnion(meta))
                            .addOnFailureListener(e -> android.util.Log.w("AdjuntarSheet", "⚠️ Error actualizando array adjuntos EN: " + e.getMessage()));
                    guardarAdjuntoEnCitaEs(meta, callbacks);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("AdjuntarSheet", "⚠️ Error guardando adjunto en cita EN: " + e.getMessage());
                    guardarAdjuntoEnCitaEs(meta, callbacks);
                });
    }

    private void guardarAdjuntoEnCitaEs(Map<String, Object> meta, UploadCallbacks callbacks) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference citaEs = db.collection("actividades").document(actividadId)
                .collection("citas").document(citaId);

        citaEs.collection("adjuntos").add(meta)
                .addOnSuccessListener(citaDoc -> {
                    android.util.Log.d("AdjuntarSheet", "✅ Adjunto guardado en cita ES " + citaId);
                    citaEs.update("adjuntos", FieldValue.arrayUnion(meta))
                            .addOnFailureListener(e -> android.util.Log.w("AdjuntarSheet", "⚠️ Error actualizando array adjuntos ES: " + e.getMessage()));
                    callbacks.onSuccess();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("AdjuntarSheet", "⚠️ Error guardando adjunto en cita ES: " + e.getMessage());
                    callbacks.onSuccess();
                });
    }

    private interface UploadCallbacks {
        void onSuccess();
        void onError(String errorMessage);
    }

    private String obtenerNombreArchivo(Uri uri) {
        String last = uri.getLastPathSegment();
        if (last == null) return "archivo";
        int idx = last.lastIndexOf('/');
        return idx >= 0 ? last.substring(idx + 1) : last;
    }
}
