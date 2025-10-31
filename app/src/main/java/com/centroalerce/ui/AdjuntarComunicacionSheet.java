package com.centroalerce.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.centroalerce.gestion.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class AdjuntarComunicacionSheet extends BottomSheetDialogFragment {

    private Uri fileUri;
    private ActivityResultLauncher<Intent> pickerLauncher; // ✅ CAMBIO: Intent genérico

    public static AdjuntarComunicacionSheet newInstance(String actividadId) {
        AdjuntarComunicacionSheet f = new AdjuntarComunicacionSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);

        // ✅ CORREGIDO: Usar StartActivityForResult en lugar de OpenMultipleDocuments
        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        fileUri = result.getData().getData();

                        // ✅ Tomar permiso persistente
                        if (fileUri != null) {
                            try {
                                requireContext().getContentResolver()
                                        .takePersistableUriPermission(fileUri,
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignored) {
                                android.util.Log.w("ADJUNTAR", "No se pudo tomar permiso persistente");
                            }
                        }

                        // Actualizar UI
                        View dialogView = getView();
                        if (dialogView != null) {
                            TextView tv = dialogView.findViewById(R.id.tvArchivo);
                            if (tv != null && fileUri != null) {
                                String nombre = obtenerNombreArchivo(fileUri);
                                tv.setText(nombre);
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
        return inflater.inflate(R.layout.sheet_adjuntar_comunicacion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        String actividadId = getArguments() != null ? getArguments().getString("actividadId", "") : "";

        TextView tvArchivo = v.findViewById(R.id.tvArchivo);
        MaterialButton btnSeleccionar = v.findViewById(R.id.btnSeleccionarArchivo);
        MaterialButton btnSubir = v.findViewById(R.id.btnSubir);

        // ✅ CORREGIDO: Intent ACTION_GET_CONTENT para evitar crash
        btnSeleccionar.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*"); // ✅ Acepta cualquier tipo de archivo
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // ✅ Añadir tipos MIME adicionales
                String[] mimeTypes = {
                        "image/*",
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

                pickerLauncher.launch(intent);
            } catch (Exception e) {
                android.util.Log.e("ADJUNTAR", "Error al abrir selector: " + e.getMessage(), e);
                Toast.makeText(requireContext(),
                        "Error al abrir selector de archivos",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnSubir.setOnClickListener(view -> {
            if (fileUri == null) {
                Toast.makeText(requireContext(), "Selecciona un archivo primero", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(actividadId)) {
                Toast.makeText(requireContext(), "Error: Falta actividadId", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Verificar autenticación
            com.google.firebase.auth.FirebaseUser currentUser =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

            if (currentUser == null) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Sesión expirada")
                        .setMessage("Debes iniciar sesión para subir archivos")
                        .setPositiveButton("Entendido", null)
                        .show();
                return;
            }

            // ✅ Deshabilitar botones durante subida
            btnSubir.setEnabled(false);
            btnSeleccionar.setEnabled(false);
            btnSubir.setText("Subiendo...");

            android.util.Log.d("ADJUNTAR", "✅ Usuario: " + currentUser.getEmail());
            android.util.Log.d("ADJUNTAR", "🚀 Actividad: " + actividadId);

            FirebaseStorage storage = FirebaseStorage.getInstance();
            String fileName = obtenerNombreArchivo(fileUri);

            // ✅ Ruta correcta
            StorageReference ref = storage.getReference()
                    .child("activities")
                    .child(actividadId)
                    .child("adjuntos")
                    .child(fileName);

            android.util.Log.d("ADJUNTAR", "📎 Archivo: " + fileName);
            android.util.Log.d("ADJUNTAR", "📂 Ruta: " + ref.getPath());

            // ✅ Subir y guardar en Firestore
            ref.putFile(fileUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            Exception e = task.getException();
                            android.util.Log.e("ADJUNTAR", "❌ Error: " +
                                    (e != null ? e.getMessage() : "unknown"), e);
                            throw task.getException();
                        }
                        android.util.Log.d("ADJUNTAR", "✅ Subido, obteniendo URL...");
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUrl -> {
                        android.util.Log.d("ADJUNTAR", "✅ URL: " + downloadUrl.toString());
                        guardarEnFirestore(actividadId, fileName, downloadUrl.toString());
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("ADJUNTAR", "❌ Error: " + e.getMessage(), e);

                        // ✅ Rehabilitar botones
                        btnSubir.setEnabled(true);
                        btnSeleccionar.setEnabled(true);
                        btnSubir.setText("Guardar archivo");

                        String errorMsg = "Error al subir: ";
                        if (e instanceof com.google.firebase.storage.StorageException) {
                            com.google.firebase.storage.StorageException se =
                                    (com.google.firebase.storage.StorageException) e;
                            errorMsg += se.getMessage();
                        } else {
                            errorMsg += e.getMessage();
                        }

                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                    });
        });
    }


    private void guardarEnFirestore(String actividadId, String fileName, String url) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(url)) {
            android.util.Log.e("ADJUNTAR", "❌ Faltan datos: actividadId=" + actividadId + ", url=" + url);
            Toast.makeText(requireContext(), "Error: Faltan datos para guardar", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // ✅ CRÍTICO: NO usar FieldValue.serverTimestamp() dentro de arrayUnion
        Map<String, Object> adjunto = new HashMap<>();
        adjunto.put("nombre", fileName);
        adjunto.put("name", fileName);
        adjunto.put("url", url);
        adjunto.put("creadoEn", System.currentTimeMillis()); // ✅ Usar timestamp manual
        adjunto.put("id", "adj_" + System.currentTimeMillis());

        android.util.Log.d("ADJUNTAR", "💾 Guardando en Firestore: " + fileName);

        // ✅ Intentar EN primero
        db.collection("activities").document(actividadId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        android.util.Log.d("ADJUNTAR", "📄 Documento EN encontrado");

                        // ✅ Actualizar array de adjuntos
                        doc.getReference()
                                .update("adjuntos", com.google.firebase.firestore.FieldValue.arrayUnion(adjunto))
                                .addOnSuccessListener(u -> {
                                    android.util.Log.d("ADJUNTAR", "✅ Actualizado array en EN");

                                    // ✅ También agregar a subcolección (aquí SÍ podemos usar serverTimestamp)
                                    Map<String, Object> subDoc = new HashMap<>();
                                    subDoc.put("nombre", fileName);
                                    subDoc.put("name", fileName);
                                    subDoc.put("url", url);
                                    subDoc.put("creadoEn", com.google.firebase.firestore.FieldValue.serverTimestamp()); // ✅ Aquí sí es válido
                                    subDoc.put("id", adjunto.get("id"));

                                    doc.getReference()
                                            .collection("adjuntos")
                                            .add(subDoc)
                                            .addOnSuccessListener(docRef -> {
                                                android.util.Log.d("ADJUNTAR", "✅ Agregado a subcolección adjuntos");
                                            })
                                            .addOnFailureListener(e -> {
                                                android.util.Log.w("ADJUNTAR", "⚠️ No se pudo agregar a subcolección: " + e.getMessage());
                                            });

                                    notificarYCerrar();
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("ADJUNTAR", "❌ Error en EN: " + e.getMessage(), e);
                                    intentarEnES(actividadId, adjunto);
                                });
                    } else {
                        android.util.Log.d("ADJUNTAR", "⚠️ Documento EN no existe, probando ES...");
                        intentarEnES(actividadId, adjunto);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ADJUNTAR", "❌ Error verificando EN: " + e.getMessage(), e);
                    intentarEnES(actividadId, adjunto);
                });
    }
    private void intentarEnES(String actividadId, Map<String, Object> adjunto) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("actividades").document(actividadId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        android.util.Log.d("ADJUNTAR", "📄 Documento ES encontrado");

                        doc.getReference()
                                .update("adjuntos", com.google.firebase.firestore.FieldValue.arrayUnion(adjunto))
                                .addOnSuccessListener(u -> {
                                    android.util.Log.d("ADJUNTAR", "✅ Actualizado array en ES");

                                    // ✅ También agregar a subcolección
                                    Map<String, Object> subDoc = new HashMap<>();
                                    subDoc.put("nombre", adjunto.get("nombre"));
                                    subDoc.put("name", adjunto.get("name"));
                                    subDoc.put("url", adjunto.get("url"));
                                    subDoc.put("creadoEn", com.google.firebase.firestore.FieldValue.serverTimestamp());
                                    subDoc.put("id", adjunto.get("id"));

                                    doc.getReference()
                                            .collection("adjuntos")
                                            .add(subDoc)
                                            .addOnSuccessListener(docRef -> {
                                                android.util.Log.d("ADJUNTAR", "✅ Agregado a subcolección adjuntos ES");
                                            })
                                            .addOnFailureListener(e -> {
                                                android.util.Log.w("ADJUNTAR", "⚠️ No se pudo agregar a subcolección ES: " + e.getMessage());
                                            });

                                    notificarYCerrar();
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("ADJUNTAR", "❌ Error en ES: " + e.getMessage(), e);
                                    Toast.makeText(requireContext(),
                                            "Error al guardar: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    } else {
                        android.util.Log.e("ADJUNTAR", "❌ Actividad no encontrada en ninguna colección");
                        Toast.makeText(requireContext(),
                                "Actividad no encontrada en ninguna colección",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ADJUNTAR", "❌ Error verificando ES: " + e.getMessage(), e);
                    Toast.makeText(requireContext(),
                            "Error al buscar actividad: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void notificarYCerrar() {
        try {
            Bundle res = new Bundle();
            res.putBoolean("adjunto_subido", true);
            res.putLong("timestamp", System.currentTimeMillis());

            getParentFragmentManager().setFragmentResult("adjuntos_change", res);
            requireActivity().getSupportFragmentManager().setFragmentResult("adjuntos_change", res);

            Toast.makeText(requireContext(), "✅ Archivo adjuntado exitosamente", Toast.LENGTH_SHORT).show();

            android.util.Log.d("ADJUNTAR", "📢 Notificaciones enviadas, cerrando modal...");

            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> {
                        try {
                            if (isAdded()) {
                                dismiss();
                            }
                        } catch (Exception ignored) {}
                    }, 1500); // Esperar 1.5s para que se vea el Toast
        } catch (Exception e) {
            android.util.Log.e("ADJUNTAR", "Error notificando: " + e.getMessage(), e);
        }
    }


    private void notificarCambios() {
        try {
            Bundle res = new Bundle();
            res.putBoolean("adjunto_subido", true);
            res.putLong("timestamp", System.currentTimeMillis());

            getParentFragmentManager().setFragmentResult("adjuntos_change", res);
            requireActivity().getSupportFragmentManager()
                    .setFragmentResult("adjuntos_change", res);

            android.util.Log.d("ADJUNTAR", "📢 Notificaciones enviadas");
        } catch (Exception e) {
            android.util.Log.e("ADJUNTAR", "Error notificando: " + e.getMessage());
        }
    }

    // ✅ Obtener nombre del archivo
    private String obtenerNombreArchivo(Uri uri) {
        if (uri == null) return "archivo_" + System.currentTimeMillis();

        try {
            android.database.Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    String name = cursor.getString(nameIndex);
                    cursor.close();
                    if (!TextUtils.isEmpty(name)) return name;
                }
                cursor.close();
            }
        } catch (Exception ignored) {}

        String last = uri.getLastPathSegment();
        if (last != null) {
            int idx = last.lastIndexOf('/');
            if (idx >= 0) return last.substring(idx + 1);
            return last;
        }

        return "archivo_" + System.currentTimeMillis();
    }
}