package com.centroalerce.ui.mantenedores.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.ui.mantenedores.BeneficiariosFragment;
import com.google.android.material.chip.Chip;

import java.util.List;

public class BeneficiarioAdapter extends RecyclerView.Adapter<BeneficiarioAdapter.VH> {

    public interface Callback {
        void onEdit(BeneficiariosFragment.Beneficiario item);
        void onDelete(BeneficiariosFragment.Beneficiario item);
        void onToggleActivo(BeneficiariosFragment.Beneficiario item);
    }

    private List<BeneficiariosFragment.Beneficiario> data;
    private final Callback cb;

    public BeneficiarioAdapter(List<BeneficiariosFragment.Beneficiario> data, Callback cb) {
        this.data = data;
        this.cb = cb;
    }

    public void submit(List<BeneficiariosFragment.Beneficiario> items) {
        this.data = items;
        notifyDataSetChanged();
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
    }

    @Override
    public int getItemCount() { return data == null ? 0 : data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNombre, tvInfo;
        Chip chipActivo;
        ImageButton btnMore;

        VH(@NonNull View v) {
            super(v);
            tvNombre = v.findViewById(R.id.tvNombre);
            tvInfo = v.findViewById(R.id.tvInfo);
            chipActivo = v.findViewById(R.id.chipActivo);
            btnMore = v.findViewById(R.id.btnMore);
        }
    }
}