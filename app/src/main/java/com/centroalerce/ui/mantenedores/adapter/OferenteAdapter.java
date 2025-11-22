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
import com.centroalerce.gestion.models.Oferente;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OferenteAdapter extends RecyclerView.Adapter<OferenteAdapter.VH> {

    public interface Callbacks {
        void onEditar(Oferente o);
        void onEliminar(Oferente o);
        void onSelectionChanged(int selectedCount);
    }

    private final List<Oferente> data = new ArrayList<>();
    private final Callbacks cb;

    // Modo selección múltiple
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public OferenteAdapter(Callbacks cb) { this.cb = cb; }

    public void submit(List<Oferente> list){ data.clear(); if(list!=null) data.addAll(list); notifyDataSetChanged(); }

    /** Devuelve los oferentes actualmente seleccionados. */
    public List<Oferente> getSelectedItems() {
        List<Oferente> out = new ArrayList<>();
        for (Oferente o : data) {
            if (o != null && o.getId() != null && selectedIds.contains(o.getId())) {
                out.add(o);
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
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_oferente, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Oferente o = data.get(pos);
        h.tvNombre.setText(o.getNombre()!=null?o.getNombre():"(Sin nombre)");
        h.tvDocente.setText(o.getDocenteResponsable()!=null?o.getDocenteResponsable():"—");

        // Estado visual de selección
        boolean isSelected = o.getId() != null && selectedIds.contains(o.getId());
        int strokeColor = ContextCompat.getColor(
                h.itemView.getContext(),
                isSelected ? R.color.primary : R.color.cardStroke
        );
        h.card.setStrokeColor(strokeColor);

        // Click corto: editar o alternar selección si estamos en modo selección
        h.itemView.setOnClickListener(v -> {
            animatePress(h.itemView);
            if (selectionMode) {
                toggleSelection(o);
            } else {
                if (cb != null) cb.onEditar(o);
            }
        });

        // Long-press: activar modo selección y marcar elemento
        h.itemView.setOnLongClickListener(v -> {
            animatePress(h.itemView);
            if (!selectionMode) {
                selectionMode = true;
            }
            toggleSelection(o);
            return true;
        });
    }

    @Override public int getItemCount(){ return data.size(); }

    private void toggleSelection(Oferente o) {
        if (o == null || o.getId() == null) return;

        if (selectedIds.contains(o.getId())) {
            selectedIds.remove(o.getId());
        } else {
            selectedIds.add(o.getId());
        }

        if (selectedIds.isEmpty()) {
            selectionMode = false;
        }

        if (cb != null) cb.onSelectionChanged(selectedIds.size());
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder{
        MaterialCardView card;
        TextView tvNombre,tvDocente; ImageButton btnMore;
        VH(@NonNull View v){ super(v);
            card = (MaterialCardView) v;
            tvNombre=v.findViewById(R.id.tvNombre);
            tvDocente=v.findViewById(R.id.tvDocente);
            btnMore=v.findViewById(R.id.btnMore);
        }
    }
}
