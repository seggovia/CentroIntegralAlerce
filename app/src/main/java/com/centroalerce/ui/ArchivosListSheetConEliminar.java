package com.centroalerce.ui;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchivosListSheetConEliminar extends BottomSheetDialogFragment {

    private static final String ARG_ADJUNTOS = "adjuntos";
    private static final String ARG_TITULO = "titulo";
    private static final String ARG_ACTIVIDAD_ID = "actividadId";

    private RecyclerView recyclerView;
    private AdjuntosConEliminarAdapter adapter;
    private List<Map<String, Object>> adjuntos = new ArrayList<>();
    private String actividadId;
    private Runnable onDismissListener;

    public static ArchivosListSheetConEliminar newInstance(
            List<Map<String, Object>> adjuntos,
            String titulo,
            String actividadId) {

        ArchivosListSheetConEliminar sheet = new ArchivosListSheetConEliminar();
        Bundle args = new Bundle();

        args.putSerializable(ARG_ADJUNTOS, new ArrayList<>(adjuntos));
        args.putString(ARG_TITULO, titulo != null ? titulo : "Archivos adjuntos");
        args.putString(ARG_ACTIVIDAD_ID, actividadId);

        sheet.setArguments(args);
        return sheet;
    }

    public void setOnDismissListener(Runnable listener) {
        this.onDismissListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_archivos_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tempList =
                    (List<Map<String, Object>>) getArguments().getSerializable(ARG_ADJUNTOS);
            if (tempList != null) adjuntos = tempList;

            String titulo = getArguments().getString(ARG_TITULO, "Archivos adjuntos");
            actividadId = getArguments().getString(ARG_ACTIVIDAD_ID);

            TextView tvTitulo = view.findViewById(R.id.tvTituloSheet);
            if (tvTitulo != null) tvTitulo.setText(titulo);
        }

        recyclerView = view.findViewById(R.id.rvArchivos);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new AdjuntosConEliminarAdapter(adjuntos, this::eliminarAdjunto);
        recyclerView.setAdapter(adapter);

        MaterialButton btnCerrar = view.findViewById(R.id.btnCerrar);
        if (btnCerrar != null) {
            btnCerrar.setOnClickListener(v -> dismiss());
        }

        TextView tvContador = view.findViewById(R.id.tvContador);
        if (tvContador != null) {
            tvContador.setText(adjuntos.size() + " archivo(s)");
        }
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.run();
        }
    }

    private void eliminarAdjunto(Map<String, Object> adjunto) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar archivo")
                .setMessage("¿Estás seguro de eliminar este archivo?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    adjuntos.remove(adjunto);
                    actualizarFirestore();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void actualizarFirestore() {
        if (TextUtils.isEmpty(actividadId)) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.runTransaction(trx -> {
            DocumentReference refEN = db.collection("activities").document(actividadId);
            DocumentReference refES = db.collection("actividades").document(actividadId);

            DocumentSnapshot docEN = trx.get(refEN);
            DocumentSnapshot docES = trx.get(refES);

            // ✅ Actualizar array principal en ambas colecciones
            if (docEN.exists()) trx.update(refEN, "adjuntos", adjuntos);
            if (docES.exists()) trx.update(refES, "adjuntos", adjuntos);

            return null;
        }).addOnSuccessListener(u -> {
            android.util.Log.d("ELIMINAR", "✅ Array actualizado, eliminando de subcolecciones...");

            // ✅ CRÍTICO: Eliminar de TODAS las subcolecciones
            eliminarDeSubcolecciones();

            adapter.notifyDataSetChanged();

            TextView tvContador = getView() != null ?
                    getView().findViewById(R.id.tvContador) : null;
            if (tvContador != null) {
                tvContador.setText(adjuntos.size() + " archivo(s)");
            }

            toast("Archivo eliminado");

            // ✅ Notificar cambios
            Bundle res = new Bundle();
            res.putBoolean("adjunto_eliminado", true);
            res.putLong("timestamp", System.currentTimeMillis());

            try {
                getParentFragmentManager().setFragmentResult("adjuntos_change", res);
                requireActivity().getSupportFragmentManager().setFragmentResult("adjuntos_change", res);
            } catch (Exception ignore) {}

        }).addOnFailureListener(e -> {
            android.util.Log.e("ELIMINAR", "❌ Error: " + e.getMessage(), e);
            toast("Error: " + e.getMessage());
        });
    }
    /**
     * Elimina archivos de TODAS las subcolecciones que no estén en el array principal
     */
    private void eliminarDeSubcolecciones() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // ✅ Crear lista de URLs válidas del array principal
        java.util.Set<String> urlsValidas = new java.util.HashSet<>();
        for (Map<String, Object> adj : adjuntos) {
            String url = adj.get("url") != null ? adj.get("url").toString() : null;
            if (!TextUtils.isEmpty(url)) {
                urlsValidas.add(url);
            }
        }

        android.util.Log.d("ELIMINAR", "📋 URLs válidas: " + urlsValidas.size());

        // ✅ Eliminar de subcolecciones EN
        eliminarDeSubcoleccion(db.collection("activities").document(actividadId), "adjuntos", urlsValidas);
        eliminarDeSubcoleccion(db.collection("activities").document(actividadId), "attachments", urlsValidas);
        eliminarDeSubcoleccion(db.collection("activities").document(actividadId), "archivos", urlsValidas);

        // ✅ Eliminar de subcolecciones ES
        eliminarDeSubcoleccion(db.collection("actividades").document(actividadId), "adjuntos", urlsValidas);
        eliminarDeSubcoleccion(db.collection("actividades").document(actividadId), "attachments", urlsValidas);
        eliminarDeSubcoleccion(db.collection("actividades").document(actividadId), "archivos", urlsValidas);
    }

    /**
     * Helper: Elimina docs de una subcolección que no estén en urlsValidas
     */
    private void eliminarDeSubcoleccion(com.google.firebase.firestore.DocumentReference actRef,
                                        String subcoleccion,
                                        java.util.Set<String> urlsValidas) {
        actRef.collection(subcoleccion)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty()) {
                        android.util.Log.d("ELIMINAR", "📂 Subcolección " + subcoleccion + " vacía");
                        return;
                    }

                    android.util.Log.d("ELIMINAR", "🔍 Revisando " + qs.size() + " docs en " + subcoleccion);

                    for (com.google.firebase.firestore.DocumentSnapshot doc : qs.getDocuments()) {
                        String url = doc.getString("url");

                        if (!urlsValidas.contains(url)) {
                            // ✅ Este documento debe eliminarse
                            doc.getReference().delete()
                                    .addOnSuccessListener(u -> {
                                        android.util.Log.d("ELIMINAR", "🗑️ Eliminado de " + subcoleccion + ": " + doc.getId());
                                    })
                                    .addOnFailureListener(e -> {
                                        android.util.Log.e("ELIMINAR", "❌ Error eliminando " + doc.getId() + ": " + e.getMessage());
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ELIMINAR", "❌ Error leyendo " + subcoleccion + ": " + e.getMessage());
                });
    }


    private void toast(String m) {
        android.widget.Toast.makeText(requireContext(), m,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    // Adapter interno
    static class AdjuntosConEliminarAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Map<String, Object>> items;
        private final OnEliminarListener listener;

        interface OnEliminarListener {
            void onEliminar(Map<String, Object> item);
        }

        AdjuntosConEliminarAdapter(List<Map<String, Object>> items, OnEliminarListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // ✅ CRÍTICO: Usar item_adjunto_eliminar.xml que tiene btnEliminar
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_adjunto_eliminar, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            VH vh = (VH) holder;
            Map<String, Object> item = items.get(position);

            String nombre = item.get("nombre") != null ? item.get("nombre").toString() :
                    (item.get("name") != null ? item.get("name").toString() : "archivo");
            String url = item.get("url") != null ? item.get("url").toString() : null;

            vh.tvNombre.setText(nombre);

            // ✅ Configurar ícono según extensión
            String ext = obtenerExtension(nombre);
            int iconoRes = obtenerIconoPorExtension(ext);
            if (vh.ivIcono != null) {
                vh.ivIcono.setImageResource(iconoRes);
            }

            // ✅ Configurar tamaño/tipo
            if (vh.tvTamanio != null) {
                vh.tvTamanio.setText(ext.toUpperCase() + " • Archivo");
            }

            // ✅ BOTÓN VER
            if (vh.btnVer != null) {
                if (!TextUtils.isEmpty(url)) {
                    vh.btnVer.setEnabled(true);
                    vh.btnVer.setAlpha(1f);
                    vh.btnVer.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(url), getMimeType(nombre));
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            v.getContext().startActivity(intent);
                        } catch (Exception e) {
                            android.util.Log.e("ADAPTER", "❌ Error abriendo archivo: " + e.getMessage(), e);
                            Toast.makeText(v.getContext(), "No se pudo abrir el archivo", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    vh.btnVer.setEnabled(false);
                    vh.btnVer.setAlpha(0.5f);
                }
            }

            // ✅ BOTÓN DESCARGAR (CORREGIDO)
            if (vh.btnDescargar != null) {
                if (!TextUtils.isEmpty(url)) {
                    vh.btnDescargar.setEnabled(true);
                    vh.btnDescargar.setAlpha(1f);
                    vh.btnDescargar.setOnClickListener(v -> {
                        try {
                            // ✅ CRÍTICO: Usar Context.DOWNLOAD_SERVICE en vez de DOWNLOAD_MANAGER_SERVICE
                            DownloadManager dm = (DownloadManager) v.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                            if (dm == null) {
                                Toast.makeText(v.getContext(), "Servicio de descarga no disponible", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                            request.setTitle(nombre);
                            request.setDescription("Descargando archivo...");
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombre);
                            request.setMimeType(getMimeType(nombre));

                            dm.enqueue(request);
                            Toast.makeText(v.getContext(), "Descarga iniciada: " + nombre, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            android.util.Log.e("ADAPTER", "❌ Error descargando: " + e.getMessage(), e);
                            Toast.makeText(v.getContext(), "Error al descargar", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    vh.btnDescargar.setEnabled(false);
                    vh.btnDescargar.setAlpha(0.5f);
                }
            }

            // ✅ BOTÓN ELIMINAR
            if (vh.btnEliminar != null) {
                vh.btnEliminar.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEliminar(item);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // ✅ Métodos helper
        private String obtenerExtension(String nombre) {
            if (TextUtils.isEmpty(nombre)) return "";
            int idx = nombre.lastIndexOf('.');
            return idx >= 0 ? nombre.substring(idx + 1).toLowerCase() : "";
        }

        private int obtenerIconoPorExtension(String ext) {
            switch (ext) {
                case "pdf":
                    return android.R.drawable.ic_menu_report_image;
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                    return android.R.drawable.ic_menu_gallery;
                case "doc":
                case "docx":
                    return android.R.drawable.ic_menu_edit;
                case "xls":
                case "xlsx":
                    return android.R.drawable.ic_menu_sort_by_size;
                default:
                    return android.R.drawable.ic_menu_save;
            }
        }

        private String getMimeType(String nombre) {
            String ext = obtenerExtension(nombre);
            switch (ext) {
                case "pdf": return "application/pdf";
                case "jpg":
                case "jpeg": return "image/jpeg";
                case "png": return "image/png";
                case "gif": return "image/gif";
                case "doc": return "application/msword";
                case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xls": return "application/vnd.ms-excel";
                case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                default: return "*/*";
            }
        }

        // ✅ ViewHolder CORREGIDO
        static class VH extends RecyclerView.ViewHolder {
            TextView tvNombre, tvTamanio;
            ImageView ivIcono;
            MaterialButton btnVer, btnDescargar, btnEliminar;

            VH(@NonNull View v) {
                super(v);
                tvNombre = v.findViewById(R.id.tvNombreAdjunto);
                tvTamanio = v.findViewById(R.id.tvTamanio);
                ivIcono = v.findViewById(R.id.ivIconoTipo);
                btnVer = v.findViewById(R.id.btnVerAdjunto);
                btnDescargar = v.findViewById(R.id.btnDescargarAdjunto);
                btnEliminar = v.findViewById(R.id.btnEliminar);

                // ✅ Validación de vistas
                if (tvNombre == null) android.util.Log.e("VH", "❌ tvNombreAdjunto es null");
                if (btnVer == null) android.util.Log.e("VH", "❌ btnVerAdjunto es null");
                if (btnDescargar == null) android.util.Log.e("VH", "❌ btnDescargarAdjunto es null");
                if (btnEliminar == null) android.util.Log.e("VH", "❌ btnEliminar es null");
            }
        }
    }
}