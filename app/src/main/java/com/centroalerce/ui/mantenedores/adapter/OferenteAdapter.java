package com.centroalerce.ui.mantenedores.adapter;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Oferente;
import java.util.*;

public class OferenteAdapter extends RecyclerView.Adapter<OferenteAdapter.VH> {

    public interface Callbacks { void onEditar(Oferente o); void onEliminar(Oferente o); }

    private final List<Oferente> data = new ArrayList<>();
    private final Callbacks cb;

    public OferenteAdapter(Callbacks cb) { this.cb = cb; }

    public void submit(List<Oferente> list){ data.clear(); if(list!=null) data.addAll(list); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_oferente, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Oferente o = data.get(pos);
        h.tvNombre.setText(o.getNombre()!=null?o.getNombre():"(Sin nombre)");
        h.tvDocente.setText(o.getDocenteResponsable()!=null?o.getDocenteResponsable():"—");
        h.btnMore.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(v.getContext(), h.btnMore);
            pm.getMenuInflater().inflate(R.menu.menu_item_mantenedor, pm.getMenu());
            pm.setOnMenuItemClickListener(i -> {
                int id = i.getItemId();
                if (id == R.id.action_edit) { cb.onEditar(o); return true; }
                if (id == R.id.action_delete) { cb.onEliminar(o); return true; }
                return false;
            });
            pm.show();
        });
    }

    @Override public int getItemCount(){ return data.size(); }

    static class VH extends RecyclerView.ViewHolder{
        TextView tvNombre,tvDocente; ImageButton btnMore;
        VH(@NonNull View v){ super(v);
            tvNombre=v.findViewById(R.id.tvNombre);
            tvDocente=v.findViewById(R.id.tvDocente);
            btnMore=v.findViewById(R.id.btnMore);
        }
    }
}
