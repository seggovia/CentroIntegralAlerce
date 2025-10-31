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

    private RecyclerView recyclerView;
    private AdjuntosAdapter adapter;
    private List<AdjuntoItem> adjuntos = new ArrayList<>();

    public static ArchivosListSheet newInstance(List<Map<String, Object>> adjuntos, String titulo) {
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

            TextView tvTitulo = view.findViewById(R.id.tvTituloSheet);
            if (tvTitulo != null) tvTitulo.setText(titulo);
        }

        // Setup RecyclerView
        recyclerView = view.findViewById(R.id.rvArchivos);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new AdjuntosAdapter(adjuntos, new AdjuntosAdapter.OnAdjuntoClickListener() {
            @Override
            public void onVerClick(AdjuntoItem item) {
                abrirArchivo(item.url);
            }

            @Override
            public void onDescargarClick(AdjuntoItem item) {
                descargarArchivo(item.nombre, item.url);
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
        private final OnAdjuntoClickListener listener;

        interface OnAdjuntoClickListener {
            void onVerClick(AdjuntoItem item);
            void onDescargarClick(AdjuntoItem item);
        }

        AdjuntosAdapter(List<AdjuntoItem> items, OnAdjuntoClickListener listener) {
            this.items = items != null ? items : new ArrayList<>();
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
            holder.bind(item, listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNombre, tvTamanio;
            ImageView ivIcono;
            MaterialButton btnVer, btnDescargar;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvNombre = itemView.findViewById(R.id.tvNombreAdjunto);
                tvTamanio = itemView.findViewById(R.id.tvTamanio);
                ivIcono = itemView.findViewById(R.id.ivIconoTipo);
                btnVer = itemView.findViewById(R.id.btnVerAdjunto);
                btnDescargar = itemView.findViewById(R.id.btnDescargarAdjunto);
            }

            void bind(AdjuntoItem item, OnAdjuntoClickListener listener) {
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