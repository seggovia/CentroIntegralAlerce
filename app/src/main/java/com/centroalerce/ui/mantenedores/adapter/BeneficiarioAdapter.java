package com.centroalerce.ui.mantenedores.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.ui.mantenedores.BeneficiariosFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BeneficiarioAdapter extends RecyclerView.Adapter<BeneficiarioAdapter.VH> {

    public interface Callback {
        void onEdit(BeneficiariosFragment.Beneficiario item);
        void onDelete(BeneficiariosFragment.Beneficiario item);
        void onToggleActivo(BeneficiariosFragment.Beneficiario item);
        void onSelectionChanged(int selectedCount);
    }

    private List<BeneficiariosFragment.Beneficiario> data;
    private final Callback cb;

    // Modo seleccion multiple
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public BeneficiarioAdapter(List<BeneficiariosFragment.Beneficiario> data, Callback cb) {
        this.data = data;
        this.cb = cb;
    }

    public void submit(List<BeneficiariosFragment.Beneficiario> items) {
        this.data = items;
        notifyDataSetChanged();
    }

    /**
     * Devuelve los beneficiarios actualmente seleccionados.
     */
    public List<BeneficiariosFragment.Beneficiario> getSelectedItems() {
        List<BeneficiariosFragment.Beneficiario> out = new ArrayList<>();
        if (data == null) return out;
        for (BeneficiariosFragment.Beneficiario b : data) {
            if (b != null && b.id != null && selectedIds.contains(b.id)) {
                out.add(b);
            }
        }
        return out;
    }

    /**
     * Limpia la seleccion y sale de modo seleccion.
     */
    public void clearSelection() {
        selectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        cb.onSelectionChanged(0);
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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_beneficiario, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        BeneficiariosFragment.Beneficiario it = data.get(pos);
        h.tvNombre.setText(it.nombre);

        // Info secundaria
        String info = "";
        if (!TextUtils.isEmpty(it.email)) info += it.email;
        if (!TextUtils.isEmpty(it.telefono)) info += (info.isEmpty() ? "" : " • ") + it.telefono;
        if (!TextUtils.isEmpty(it.socioNombre)) info += (info.isEmpty() ? "" : " • ") + it.socioNombre;
        h.tvInfo.setText(info.isEmpty() ? "—" : info);

        // Chip de estado
        boolean active = it.activo == null || it.activo;
        h.chipActivo.setText(active ? "Activo" : "Inactivo");
        h.chipActivo.setOnClickListener(v -> cb.onToggleActivo(it));

        // Estado visual de seleccion
        boolean isSelected = it.id != null && selectedIds.contains(it.id);
        int strokeColor = ContextCompat.getColor(
                h.itemView.getContext(),
                isSelected ? R.color.primary : R.color.cardStroke
        );
        h.card.setStrokeColor(strokeColor);

        // Botón More con PopupMenu
        h.btnMore.setOnClickListener(v -> {

            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.inflate(R.menu.menu_item_mantenedor);
            popup.setOnMenuItemClickListener(menuItem -> {
                int id = menuItem.getItemId();
                if (id == R.id.action_edit) {
                    cb.onEdit(it);
                    return true;
                } else if (id == R.id.action_delete) {
                    cb.onDelete(it);
                    return true;
                }
                return false;
            });
            popup.show();
        });

        // Click corto: editar o alternar seleccion si estamos en modo seleccion
        h.itemView.setOnClickListener(v -> {
            animatePress(h.itemView);
            if (selectionMode) {
                toggleSelection(it);
            } else {
                cb.onEdit(it);
            }
        });

        // Long-press: activar modo seleccion y marcar elemento
        h.itemView.setOnLongClickListener(v -> {
            animatePress(h.itemView);
            if (!selectionMode) {
                selectionMode = true;
            }
            toggleSelection(it);
            return true;
        });
    }

    @Override
    public int getItemCount() { return data == null ? 0 : data.size(); }

    private void toggleSelection(BeneficiariosFragment.Beneficiario it) {
        if (it == null || it.id == null) return;

        if (selectedIds.contains(it.id)) {
            selectedIds.remove(it.id);
        } else {
            selectedIds.add(it.id);
        }

        if (selectedIds.isEmpty()) {
            selectionMode = false;
        }

        cb.onSelectionChanged(selectedIds.size());
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvNombre, tvInfo;
        Chip chipActivo;
        ImageButton btnMore;

        VH(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            tvNombre = v.findViewById(R.id.tvNombre);
            tvInfo = v.findViewById(R.id.tvInfo);
            chipActivo = v.findViewById(R.id.chipActivo);

            btnMore = v.findViewById(R.id.btnMore);
        }
    }
}