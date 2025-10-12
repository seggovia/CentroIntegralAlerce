// com.centroalerce.ui.beneficiarios.BeneficiarioCheckAdapter.java
package com.centroalerce.ui.mantenedores.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Beneficiario;

import java.util.*;

public class BeneficiarioCheckAdapter extends RecyclerView.Adapter<BeneficiarioCheckAdapter.VH> {
    public interface OnSelectionChanged {
        void onSelectionCountChanged(int count);
    }

    private final List<Beneficiario> fullList = new ArrayList<>();
    private final List<Beneficiario> visibleList = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private final OnSelectionChanged callback;

    public BeneficiarioCheckAdapter(OnSelectionChanged cb, Collection<String> preselected) {
        this.callback = cb;
        if (preselected != null) selectedIds.addAll(preselected);
    }

    public void setData(List<Beneficiario> data) {
        fullList.clear(); fullList.addAll(data);
        visibleList.clear(); visibleList.addAll(data);
        notifyDataSetChanged();
        if (callback != null) callback.onSelectionCountChanged(selectedIds.size());
    }

    public void filter(String query) {
        visibleList.clear();
        if (TextUtils.isEmpty(query)) {
            visibleList.addAll(fullList);
        } else {
            String q = query.toLowerCase(Locale.ROOT);
            for (Beneficiario b : fullList) {
                String rut = b.getRut() != null ? b.getRut() : "";
                if ((b.getNombre()!=null && b.getNombre().toLowerCase(Locale.ROOT).contains(q))
                        || rut.toLowerCase(Locale.ROOT).contains(q)) {
                    visibleList.add(b);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void selectAllVisible(boolean checked) {
        for (Beneficiario b : visibleList) {
            if (checked) selectedIds.add(b.getId());
            else selectedIds.remove(b.getId());
        }
        notifyDataSetChanged();
        if (callback != null) callback.onSelectionCountChanged(selectedIds.size());
    }

    public List<Beneficiario> getSelected(List<Beneficiario> source) {
        List<Beneficiario> out = new ArrayList<>();
        for (Beneficiario b : source) if (selectedIds.contains(b.getId())) out.add(b);
        return out;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_beneficiario_check, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Beneficiario b = visibleList.get(pos);
        h.tvNombre.setText(b.getNombre());
        h.tvRut.setText(b.getRut()==null ? "" : b.getRut());
        h.chk.setOnCheckedChangeListener(null);
        h.chk.setChecked(selectedIds.contains(b.getId()));
        h.itemView.setOnClickListener(v -> h.chk.performClick());
        h.chk.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) selectedIds.add(b.getId());
            else selectedIds.remove(b.getId());
            if (callback != null) callback.onSelectionCountChanged(selectedIds.size());
        });
    }

    @Override public int getItemCount() { return visibleList.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CheckBox chk; TextView tvNombre, tvRut;
        VH(@NonNull View v) {
            super(v);
            chk = v.findViewById(R.id.chkItem);
            tvNombre = v.findViewById(R.id.tvNombre);
            tvRut = v.findViewById(R.id.tvRut);
        }
    }
}
