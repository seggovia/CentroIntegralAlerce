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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BottomSheet para mostrar lista limpia de archivos adjuntos
 * Evita el amontonamiento visual cuando hay muchos archivos
 */
public class ArchivosListSheet extends BottomSheetDialogFragment {

    private static final String ARG_ADJUNTOS = "adjuntos_json";
    private static final String ARG_TITULO = "titulo";
    private static final String ARG_ACTIVIDAD_ID = "actividad_id";
    private static final String ARG_PUEDE_ELIMINAR = "puede_eliminar";

    private RecyclerView recyclerView;
    private AdjuntosAdapter adapter;
    private List<AdjuntoItem> adjuntos = new ArrayList<>();
    private String actividadId;
    private boolean puedeEliminar;

    public static ArchivosListSheet newInstance(List<Map<String, Object>> adjuntos, String titulo) {
        return newInstance(adjuntos, titulo, null, false);
    }

    public static ArchivosListSheet newInstance(List<Map<String, Object>> adjuntos, String titulo, String actividadId, boolean puedeEliminar) {
        ArchivosListSheet sheet = new ArchivosListSheet();
        Bundle args = new Bundle();

        // Convertir lista a items serializables
        ArrayList<AdjuntoItem> items = new ArrayList<>();
        for (Map<String, Object> adj : adjuntos) {
            String nombre = firstNonEmpty(
                    stringOr(adj.get("nombre"), null),
                    stringOr(adj.get("name"), null),
                    "archivo"
            );
            String url = stringOr(adj.get("url"), null);
            String id = stringOr(adj.get("id"), null);

            items.add(new AdjuntoItem(nombre, url, id));
        }

        args.putParcelableArrayList(ARG_ADJUNTOS, items);
        args.putString(ARG_TITULO, titulo != null ? titulo : "Archivos adjuntos");
        args.putString(ARG_ACTIVIDAD_ID, actividadId);
        args.putBoolean(ARG_PUEDE_ELIMINAR, puedeEliminar);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_archivos_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Obtener argumentos
        if (getArguments() != null) {
            adjuntos = getArguments().getParcelableArrayList(ARG_ADJUNTOS);
            String titulo = getArguments().getString(ARG_TITULO, "Archivos adjuntos");
            actividadId = getArguments().getString(ARG_ACTIVIDAD_ID);
            puedeEliminar = getArguments().getBoolean(ARG_PUEDE_ELIMINAR, false);

            TextView tvTitulo = view.findViewById(R.id.tvTituloSheet);
            if (tvTitulo != null) tvTitulo.setText(titulo);
        }

        // Setup RecyclerView
        recyclerView = view.findViewById(R.id.rvArchivos);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new AdjuntosAdapter(adjuntos, puedeEliminar, new AdjuntosAdapter.OnAdjuntoClickListener() {
            @Override
            public void onVerClick(AdjuntoItem item) {
                abrirArchivo(item.url);
            }

            @Override
            public void onDescargarClick(AdjuntoItem item) {
                descargarArchivo(item.nombre, item.url);
            }

            @Override
            public void onEliminarClick(AdjuntoItem item) {
                confirmarEliminarArchivo(item);
            }
        });

        recyclerView.setAdapter(adapter);

        // Botón cerrar
        MaterialButton btnCerrar = view.findViewById(R.id.btnCerrar);
        if (btnCerrar != null) {
            btnCerrar.setOnClickListener(v -> dismiss());
        }

        // Mostrar contador
        TextView tvContador = view.findViewById(R.id.tvContador);
        if (tvContador != null && adjuntos != null) {
            tvContador.setText(adjuntos.size() + " archivo(s)");
        }
    }

    private void abrirArchivo(String url) {
        if (TextUtils.isEmpty(url)) {
            toast("URL no disponible");
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            toast("No se pudo abrir el archivo");
            android.util.Log.e("ARCHIVOS_SHEET", "Error abriendo archivo: " + e.getMessage(), e);
        }
    }

    private void descargarArchivo(String nombre, String url) {
        if (TextUtils.isEmpty(url)) {
            toast("URL no disponible");
            return;
        }

        try {
            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombre);

            dm.enqueue(request);
            toast("Descarga iniciada...");
        } catch (Exception e) {
            toast("Error al descargar: " + e.getMessage());
            android.util.Log.e("ARCHIVOS_SHEET", "Error descargando: " + e.getMessage(), e);
        }
    }

    private void confirmarEliminarArchivo(AdjuntoItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar archivo")
                .setMessage("¿Estás seguro de que deseas eliminar '" + item.nombre + "'?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> eliminarArchivo(item))
                .show();
    }

    private void eliminarArchivo(AdjuntoItem item) {
        if (TextUtils.isEmpty(actividadId)) {
            toast("Error: No se puede eliminar el archivo");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Intentar eliminar de ambas colecciones (EN y ES)
        eliminarArchivoDeColeccion(db, "activities", actividadId, item, () -> {
            eliminarArchivoDeColeccion(db, "actividades", actividadId, item, () -> {
                // Remover del adapter
                adjuntos.remove(item);
                adapter.notifyDataSetChanged();

                // Actualizar contador
                android.view.View view = getView();
                if (view != null) {
                    TextView tvContador = view.findViewById(R.id.tvContador);
                    if (tvContador != null) {
                        tvContador.setText(adjuntos.size() + " archivo(s)");
                    }
                }

                toast("Archivo eliminado");

                // Notificar cambios
                Bundle b = new Bundle();
                try { getParentFragmentManager().setFragmentResult("adjuntos_change", b); } catch (Exception ignore) {}
                try { getParentFragmentManager().setFragmentResult("actividad_change", b); } catch (Exception ignore) {}
                try { requireActivity().getSupportFragmentManager().setFragmentResult("adjuntos_change", b); } catch (Exception ignore) {}
                try { requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b); } catch (Exception ignore) {}

                // Si no quedan archivos, cerrar
                if (adjuntos.isEmpty()) {
                    dismiss();
                }
            });
        });
    }

    private void eliminarArchivoDeColeccion(FirebaseFirestore db, String coleccion, String actividadId, AdjuntoItem item, Runnable onComplete) {
        DocumentReference actRef = db.collection(coleccion).document(actividadId);

        // Primero intentar eliminar del array "adjuntos" en el documento
        actRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                List<Map<String, Object>> adjuntos = (List<Map<String, Object>>) doc.get("adjuntos");
                if (adjuntos != null && !adjuntos.isEmpty()) {
                    // Buscar y remover el adjunto del array
                    List<Map<String, Object>> nuevosAdjuntos = new ArrayList<>();
                    boolean encontrado = false;
                    for (Map<String, Object> adj : adjuntos) {
                        String nombre = firstNonEmpty(
                                stringOr(adj.get("nombre"), null),
                                stringOr(adj.get("name"), null)
                        );
                        String url = stringOr(adj.get("url"), null);

                        // No agregar el que queremos eliminar
                        if (!(item.nombre.equals(nombre) && item.url.equals(url))) {
                            nuevosAdjuntos.add(adj);
                        } else {
                            encontrado = true;
                        }
                    }

                    if (encontrado) {
                        actRef.update("adjuntos", nuevosAdjuntos)
                                .addOnSuccessListener(v -> onComplete.run())
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("ARCHIVOS_SHEET", "Error eliminando de array: " + e.getMessage());
                                    onComplete.run();
                                });
                        return;
                    }
                }

                // Si no está en el array, intentar eliminar de attachments
                List<Map<String, Object>> attachments = (List<Map<String, Object>>) doc.get("attachments");
                if (attachments != null && !attachments.isEmpty()) {
                    List<Map<String, Object>> nuevosAttachments = new ArrayList<>();
                    boolean encontrado = false;
                    for (Map<String, Object> adj : attachments) {
                        String nombre = firstNonEmpty(
                                stringOr(adj.get("nombre"), null),
                                stringOr(adj.get("name"), null)
                        );
                        String url = stringOr(adj.get("url"), null);

                        if (!(item.nombre.equals(nombre) && item.url.equals(url))) {
                            nuevosAttachments.add(adj);
                        } else {
                            encontrado = true;
                        }
                    }

                    if (encontrado) {
                        actRef.update("attachments", nuevosAttachments)
                                .addOnSuccessListener(v -> onComplete.run())
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("ARCHIVOS_SHEET", "Error eliminando de attachments: " + e.getMessage());
                                    onComplete.run();
                                });
                        return;
                    }
                }
            }

            // Si no se encontró en el documento, intentar eliminar de subcolecciones
            if (!TextUtils.isEmpty(item.id)) {
                eliminarDeSubcoleccion(actRef, "adjuntos", item.id, () ->
                        eliminarDeSubcoleccion(actRef, "attachments", item.id, () ->
                                eliminarDeSubcoleccion(actRef, "archivos", item.id, onComplete)
                        )
                );
            } else {
                onComplete.run();
            }
        }).addOnFailureListener(e -> {
            android.util.Log.e("ARCHIVOS_SHEET", "Error obteniendo documento: " + e.getMessage());
            onComplete.run();
        });
    }

    private void eliminarDeSubcoleccion(DocumentReference actRef, String subcoleccion, String adjuntoId, Runnable onComplete) {
        actRef.collection(subcoleccion).document(adjuntoId)
                .delete()
                .addOnSuccessListener(v -> {
                    android.util.Log.d("ARCHIVOS_SHEET", "Eliminado de subcolección: " + subcoleccion);
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ARCHIVOS_SHEET", "Error eliminando de subcolección " + subcoleccion + ": " + e.getMessage());
                    onComplete.run();
                });
    }

    private void toast(String mensaje) {
        android.widget.Toast.makeText(requireContext(), mensaje, android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return null;
    }

    private static String stringOr(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    // ==================== ADAPTER ====================

    static class AdjuntosAdapter extends RecyclerView.Adapter<AdjuntosAdapter.ViewHolder> {

        private final List<AdjuntoItem> items;
        private final boolean mostrarEliminar;
        private final OnAdjuntoClickListener listener;

        interface OnAdjuntoClickListener {
            void onVerClick(AdjuntoItem item);
            void onDescargarClick(AdjuntoItem item);
            void onEliminarClick(AdjuntoItem item);
        }

        AdjuntosAdapter(List<AdjuntoItem> items, boolean mostrarEliminar, OnAdjuntoClickListener listener) {
            this.items = items != null ? items : new ArrayList<>();
            this.mostrarEliminar = mostrarEliminar;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_adjunto, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AdjuntoItem item = items.get(position);
            holder.bind(item, mostrarEliminar, listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNombre, tvTamanio;
            ImageView ivIcono;
            MaterialButton btnVer, btnDescargar, btnEliminar;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvNombre = itemView.findViewById(R.id.tvNombreAdjunto);
                tvTamanio = itemView.findViewById(R.id.tvTamanio);
                ivIcono = itemView.findViewById(R.id.ivIconoTipo);
                btnVer = itemView.findViewById(R.id.btnVerAdjunto);
                btnDescargar = itemView.findViewById(R.id.btnDescargarAdjunto);
                btnEliminar = itemView.findViewById(R.id.btnEliminarAdjunto);
            }

            void bind(AdjuntoItem item, boolean mostrarEliminar, OnAdjuntoClickListener listener) {
                tvNombre.setText(item.nombre);

                // Icono según extensión
                String ext = obtenerExtension(item.nombre);
                ivIcono.setImageResource(obtenerIconoPorExtension(ext));

                // Tamaño/tipo
                tvTamanio.setText(ext.toUpperCase() + " • Archivo");

                // Botones
                btnVer.setOnClickListener(v -> {
                    if (listener != null) listener.onVerClick(item);
                });

                btnDescargar.setOnClickListener(v -> {
                    if (listener != null) listener.onDescargarClick(item);
                });

                // Mostrar/ocultar botón eliminar
                if (btnEliminar != null) {
                    if (mostrarEliminar) {
                        btnEliminar.setVisibility(View.VISIBLE);
                        btnEliminar.setOnClickListener(v -> {
                            if (listener != null) listener.onEliminarClick(item);
                        });
                    } else {
                        btnEliminar.setVisibility(View.GONE);
                    }
                }

                // Deshabilitar si no hay URL
                if (TextUtils.isEmpty(item.url)) {
                    btnVer.setEnabled(false);
                    btnDescargar.setEnabled(false);
                    btnVer.setAlpha(0.5f);
                    btnDescargar.setAlpha(0.5f);
                }
            }

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
        }
    }

    // ==================== MODEL ====================

    public static class AdjuntoItem implements android.os.Parcelable {
        public final String nombre;
        public final String url;
        public final String id;

        public AdjuntoItem(String nombre, String url, String id) {
            this.nombre = nombre;
            this.url = url;
            this.id = id;
        }

        protected AdjuntoItem(android.os.Parcel in) {
            nombre = in.readString();
            url = in.readString();
            id = in.readString();
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(nombre);
            dest.writeString(url);
            dest.writeString(id);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<AdjuntoItem> CREATOR = new Creator<AdjuntoItem>() {
            @Override
            public AdjuntoItem createFromParcel(android.os.Parcel in) {
                return new AdjuntoItem(in);
            }

            @Override
            public AdjuntoItem[] newArray(int size) {
                return new AdjuntoItem[size];
            }
        };
    }
}