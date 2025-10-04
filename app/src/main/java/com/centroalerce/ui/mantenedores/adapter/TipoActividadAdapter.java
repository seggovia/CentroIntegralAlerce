package com.centroalerce.ui.mantenedores.adapter;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.TipoActividad;
import java.util.*;

public class TipoActividadAdapter extends RecyclerView.Adapter<TipoActividadAdapter.VH> {

    public interface Callbacks { void onEditar(TipoActividad t); void onEliminar(TipoActividad t); }

    private final List<TipoActividad> data = new ArrayList<>();
    private final Callbacks cb;

    public TipoActividadAdapter(Callbacks cb){ this.cb = cb; }

    public void submit(List<TipoActividad> list){ data.clear(); if(list!=null) data.addAll(list); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_tipo_actividad, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        TipoActividad t = data.get(pos);
        h.tvNombre.setText(t.getNombre()!=null?t.getNombre():"(Sin nombre)");
        h.tvDescripcion.setText(t.getDescripcion()!=null?t.getDescripcion():"");
        h.btnMore.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(v.getContext(), h.btnMore);
            pm.getMenuInflater().inflate(R.menu.menu_item_mantenedor, pm.getMenu());
            pm.setOnMenuItemClickListener(i -> {
                int id = i.getItemId();
                if (id == R.id.action_edit) { cb.onEditar(t); return true; }
                if (id == R.id.action_delete) { cb.onEliminar(t); return true; }
                return false;
            });
            pm.show();
        });
    }

    @Override public int getItemCount(){ return data.size(); }

    static class VH extends RecyclerView.ViewHolder{
        TextView tvNombre, tvDescripcion; ImageButton btnMore;
        VH(@NonNull View v){ super(v);
            tvNombre=v.findViewById(R.id.tvNombre);
            tvDescripcion=v.findViewById(R.id.tvDescripcion);
            btnMore=v.findViewById(R.id.btnMore);
        }
    }
}
