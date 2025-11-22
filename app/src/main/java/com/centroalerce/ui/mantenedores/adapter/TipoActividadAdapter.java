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
import com.centroalerce.gestion.models.TipoActividad;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TipoActividadAdapter extends RecyclerView.Adapter<TipoActividadAdapter.VH> {

    public interface Callbacks {
        void onEditar(TipoActividad t);
        void onEliminar(TipoActividad t);
        void onSelectionChanged(int selectedCount);
    }

    private final List<TipoActividad> data = new ArrayList<>();
    private final Callbacks cb;

    // Modo selección múltiple
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public TipoActividadAdapter(Callbacks cb){ this.cb = cb; }

    public void submit(List<TipoActividad> list){ data.clear(); if(list!=null) data.addAll(list); notifyDataSetChanged(); }

    /** Devuelve los tipos actualmente seleccionados. */
    public List<TipoActividad> getSelectedItems() {
        List<TipoActividad> out = new ArrayList<>();
        for (TipoActividad t : data) {
            if (t != null && t.getId() != null && selectedIds.contains(t.getId())) {
                out.add(t);
            }
        }
        return out;
    }

    /** Limpia la selección y sale de modo selección. */
    public void clearSelection() {
        selectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        if (cb != null) cb.onSelectionChanged(0);
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

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_tipo_actividad, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        TipoActividad t = data.get(pos);
        h.tvNombre.setText(t.getNombre()!=null?t.getNombre():"(Sin nombre)");
        h.tvDescripcion.setText(t.getDescripcion()!=null?t.getDescripcion():"");

        // Estado visual de selección
        boolean isSelected = t.getId() != null && selectedIds.contains(t.getId());
        int strokeColor = ContextCompat.getColor(
                h.itemView.getContext(),
                isSelected ? R.color.primary : R.color.cardStroke
        );
        h.card.setStrokeColor(strokeColor);

        // Click corto: editar o alternar selección si estamos en modo selección
        h.itemView.setOnClickListener(v -> {
            animatePress(h.itemView);
            if (selectionMode) {
                toggleSelection(t);
            } else {
                if (cb != null) cb.onEditar(t);
            }
        });

        // Long-press: activar modo selección y marcar elemento
        h.itemView.setOnLongClickListener(v -> {
            animatePress(h.itemView);
            if (!selectionMode) {
                selectionMode = true;
            }
            toggleSelection(t);
            return true;
        });
    }

    @Override public int getItemCount(){ return data.size(); }

    private void toggleSelection(TipoActividad t) {
        if (t == null || t.getId() == null) return;

        if (selectedIds.contains(t.getId())) {
            selectedIds.remove(t.getId());
        } else {
            selectedIds.add(t.getId());
        }

        if (selectedIds.isEmpty()) {
            selectionMode = false;
        }

        if (cb != null) cb.onSelectionChanged(selectedIds.size());
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder{
        MaterialCardView card;
        TextView tvNombre, tvDescripcion; ImageButton btnMore;
        VH(@NonNull View v){ super(v);
            card = (MaterialCardView) v;
            tvNombre=v.findViewById(R.id.tvNombre);
            tvDescripcion=v.findViewById(R.id.tvDescripcion);
            btnMore=v.findViewById(R.id.btnMore);
        }
    }
}
