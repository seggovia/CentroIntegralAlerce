package com.centroalerce.ui.mantenedores.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Lugar;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LugarAdapter extends RecyclerView.Adapter<LugarAdapter.VH> {

    public interface Callbacks {
        void onEditar(Lugar l);
        void onEliminar(Lugar l);
        void onSelectionChanged(int selectedCount);
    }

    private final List<Lugar> data = new ArrayList<>();
    private final Callbacks callbacks;

    // Modo selección múltiple
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public LugarAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submit(List<Lugar> nuevos) {
        data.clear();
        if (nuevos != null) data.addAll(nuevos);
        notifyDataSetChanged();
    }

    /** Devuelve los lugares actualmente seleccionados. */
    public List<Lugar> getSelectedItems() {
        List<Lugar> out = new ArrayList<>();
        for (Lugar l : data) {
            if (l != null && l.getId() != null && selectedIds.contains(l.getId())) {
                out.add(l);
            }
        }
        return out;
    }

    /** Limpia la selección y sale de modo selección. */
    public void clearSelection() {
        selectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        if (callbacks != null) callbacks.onSelectionChanged(0);
    }

    private void animatePress(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(80)
                .withEndAction(() -> v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80))
                .start();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lugar, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Lugar l = data.get(pos);

        String nombre = l.getNombre() != null ? l.getNombre() : "(Sin nombre)";
        h.tvNombre.setText(nombre);

        String cupoTxt = (l.getCupo() == null) ? "Cupo: —" : "Cupo: " + l.getCupo();
        h.tvCupo.setText(cupoTxt);

        // Estado visual de selección
        boolean isSelected = l.getId() != null && selectedIds.contains(l.getId());
        int strokeColor = ContextCompat.getColor(
                h.itemView.getContext(),
                isSelected ? R.color.primary : R.color.cardStroke
        );
        h.card.setStrokeColor(strokeColor);

        // Click corto: editar o alternar selección si estamos en modo selección
        h.itemView.setOnClickListener(v -> {
            animatePress(h.itemView);
            if (selectionMode) {
                toggleSelection(l);
            } else {
                if (callbacks != null) callbacks.onEditar(l);
            }
        });

        // Long-press: activar modo selección y marcar elemento
        h.itemView.setOnLongClickListener(v -> {
            animatePress(h.itemView);
            if (!selectionMode) {
                selectionMode = true;
            }
            toggleSelection(l);
            return true;
        });
    }

    private void toggleSelection(Lugar l) {
        if (l == null || l.getId() == null) return;

        if (selectedIds.contains(l.getId())) {
            selectedIds.remove(l.getId());
        } else {
            selectedIds.add(l.getId());
        }

        if (selectedIds.isEmpty()) {
            selectionMode = false;
        }

        if (callbacks != null) callbacks.onSelectionChanged(selectedIds.size());
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvNombre, tvCupo;
        ImageButton btnMore;
        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            tvNombre = itemView.findViewById(R.id.tvNombre);
            tvCupo   = itemView.findViewById(R.id.tvCupo);
            btnMore  = itemView.findViewById(R.id.btnMore);
        }
    }
}
