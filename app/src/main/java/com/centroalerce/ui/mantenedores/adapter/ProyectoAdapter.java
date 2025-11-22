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
import com.centroalerce.gestion.models.Proyecto;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProyectoAdapter extends RecyclerView.Adapter<ProyectoAdapter.VH> {

    public interface Callbacks {
        void onEditar(Proyecto p);
        void onEliminar(Proyecto p);
        void onSelectionChanged(int selectedCount);
    }

    private final List<Proyecto> data=new ArrayList<>();
    private final Callbacks cb;

    // Modo selección múltiple
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public ProyectoAdapter(Callbacks cb){ this.cb=cb; }
    public void submit(List<Proyecto> l){ data.clear(); if(l!=null) data.addAll(l); notifyDataSetChanged(); }

    /** Devuelve los proyectos actualmente seleccionados. */
    public List<Proyecto> getSelectedItems() {
        List<Proyecto> out = new ArrayList<>();
        for (Proyecto p : data) {
            if (p != null && p.getId() != null && selectedIds.contains(p.getId())) {
                out.add(p);
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

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v){
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_proyecto, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos){
        Proyecto p = data.get(pos);
        h.tvNombre.setText(p.getNombre()!=null?p.getNombre():"(Sin nombre)");

        // Estado visual de selección
        boolean isSelected = p.getId() != null && selectedIds.contains(p.getId());
        int strokeColor = ContextCompat.getColor(
                h.itemView.getContext(),
                isSelected ? R.color.primary : R.color.cardStroke
        );
        h.card.setStrokeColor(strokeColor);

        // Click corto: editar o alternar selección si estamos en modo selección
        h.itemView.setOnClickListener(v->{
            animatePress(h.itemView);
            if (selectionMode) {
                toggleSelection(p);
            } else {
                if (cb != null) cb.onEditar(p);
            }
        });

        // Long-press: activar modo selección y marcar elemento
        h.itemView.setOnLongClickListener(v -> {
            animatePress(h.itemView);
            if (!selectionMode) {
                selectionMode = true;
            }
            toggleSelection(p);
            return true;
        });
    }

    @Override public int getItemCount(){ return data.size(); }

    private void toggleSelection(Proyecto p) {
        if (p == null || p.getId() == null) return;

        if (selectedIds.contains(p.getId())) {
            selectedIds.remove(p.getId());
        } else {
            selectedIds.add(p.getId());
        }

        if (selectedIds.isEmpty()) {
            selectionMode = false;
        }

        if (cb != null) cb.onSelectionChanged(selectedIds.size());
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder{
        MaterialCardView card;
        TextView tvNombre; ImageButton btnMore;
        VH(@NonNull View v){ super(v);
            card = (MaterialCardView) v;
            tvNombre=v.findViewById(R.id.tvNombre);
            btnMore=v.findViewById(R.id.btnMore);
        }
    }
}
