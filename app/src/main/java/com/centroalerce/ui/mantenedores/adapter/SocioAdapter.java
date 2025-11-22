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
import com.centroalerce.gestion.models.SocioComunitario;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SocioAdapter extends RecyclerView.Adapter<SocioAdapter.VH> {

    public interface Callbacks {
        void onEditar(SocioComunitario s);
        void onEliminar(SocioComunitario s);
        void onSelectionChanged(int selectedCount);
    }

    private final List<SocioComunitario> data = new ArrayList<>();
    private final Callbacks cb;

    // Modo selección múltiple
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public SocioAdapter(Callbacks cb){ this.cb=cb; }
    public void submit(List<SocioComunitario> l){ data.clear(); if(l!=null) data.addAll(l); notifyDataSetChanged(); }

    /** Devuelve los socios actualmente seleccionados. */
    public List<SocioComunitario> getSelectedItems() {
        List<SocioComunitario> out = new ArrayList<>();
        for (SocioComunitario s : data) {
            if (s != null && s.getId() != null && selectedIds.contains(s.getId())) {
                out.add(s);
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
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_socio, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos){
        SocioComunitario s = data.get(pos);
        h.tvNombre.setText(s.getNombre()!=null?s.getNombre():"(Sin nombre)");

        // Estado visual de selección
        boolean isSelected = s.getId() != null && selectedIds.contains(s.getId());
        int strokeColor = ContextCompat.getColor(
                h.itemView.getContext(),
                isSelected ? R.color.primary : R.color.cardStroke
        );
        h.card.setStrokeColor(strokeColor);

        // Click corto: editar o alternar selección si estamos en modo selección
        h.itemView.setOnClickListener(v -> {
            animatePress(h.itemView);
            if (selectionMode) {
                toggleSelection(s);
            } else {
                if (cb != null) cb.onEditar(s);
            }
        });

        // Long-press: activar modo selección y marcar elemento
        h.itemView.setOnLongClickListener(v -> {
            animatePress(h.itemView);
            if (!selectionMode) {
                selectionMode = true;
            }
            toggleSelection(s);
            return true;
        });
    }

    @Override public int getItemCount(){ return data.size(); }

    private void toggleSelection(SocioComunitario s) {
        if (s == null || s.getId() == null) return;

        if (selectedIds.contains(s.getId())) {
            selectedIds.remove(s.getId());
        } else {
            selectedIds.add(s.getId());
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
