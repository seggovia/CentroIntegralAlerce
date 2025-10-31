package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

            if (docEN.exists()) trx.update(refEN, "adjuntos", adjuntos);
            if (docES.exists()) trx.update(refES, "adjuntos", adjuntos);

            return null;
        }).addOnSuccessListener(u -> {
            adapter.notifyDataSetChanged();

            TextView tvContador = getView() != null ?
                    getView().findViewById(R.id.tvContador) : null;
            if (tvContador != null) {
                tvContador.setText(adjuntos.size() + " archivo(s)");
            }

            toast("Archivo eliminado");

            // ✅ CRÍTICO: Notificar cambios a otros fragments
            Bundle res = new Bundle();
            res.putBoolean("adjunto_eliminado", true);
            res.putLong("timestamp", System.currentTimeMillis());

            try {
                getParentFragmentManager().setFragmentResult("adjuntos_change", res);
                requireActivity().getSupportFragmentManager().setFragmentResult("adjuntos_change", res);
            } catch (Exception ignore) {}

        }).addOnFailureListener(e -> {
            toast("Error: " + e.getMessage());
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

            vh.tvNombre.setText(nombre);
            vh.btnEliminar.setOnClickListener(v -> listener.onEliminar(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvNombre;
            MaterialButton btnEliminar;

            VH(@NonNull View v) {
                super(v);
                tvNombre = v.findViewById(R.id.tvNombreAdjunto);
                btnEliminar = v.findViewById(R.id.btnEliminar);
            }
        }
    }
}