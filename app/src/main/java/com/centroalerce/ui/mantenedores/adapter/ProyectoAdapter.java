package com.centroalerce.ui.mantenedores.adapter;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Proyecto;
import java.util.*;

public class ProyectoAdapter extends RecyclerView.Adapter<ProyectoAdapter.VH> {

    public interface Callbacks { void onEditar(Proyecto p); void onEliminar(Proyecto p); }

    private final List<Proyecto> data=new ArrayList<>();
    private final Callbacks cb;

    public ProyectoAdapter(Callbacks cb){ this.cb=cb; }
    public void submit(List<Proyecto> l){ data.clear(); if(l!=null) data.addAll(l); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v){
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_proyecto, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos){
        Proyecto p = data.get(pos);
        h.tvNombre.setText(p.getNombre()!=null?p.getNombre():"(Sin nombre)");
        h.btnMore.setOnClickListener(v->{
            PopupMenu pm=new PopupMenu(v.getContext(), h.btnMore);
            pm.getMenuInflater().inflate(R.menu.menu_item_mantenedor, pm.getMenu());
            pm.setOnMenuItemClickListener(i->{
                int id=i.getItemId();
                if(id==R.id.action_edit){ cb.onEditar(p); return true; }
                if(id==R.id.action_delete){ cb.onEliminar(p); return true; }
                return false;
            });
            pm.show();
        });
    }

    @Override public int getItemCount(){ return data.size(); }

    static class VH extends RecyclerView.ViewHolder{
        TextView tvNombre; ImageButton btnMore;
        VH(@NonNull View v){ super(v);
            tvNombre=v.findViewById(R.id.tvNombre);
            btnMore=v.findViewById(R.id.btnMore);
        }
    }
}
