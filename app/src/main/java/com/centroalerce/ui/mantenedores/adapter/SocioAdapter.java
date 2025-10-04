package com.centroalerce.ui.mantenedores.adapter;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.SocioComunitario;
import java.util.*;

public class SocioAdapter extends RecyclerView.Adapter<SocioAdapter.VH> {

    public interface Callbacks { void onEditar(SocioComunitario s); void onEliminar(SocioComunitario s); }

    private final List<SocioComunitario> data = new ArrayList<>();
    private final Callbacks cb;

    public SocioAdapter(Callbacks cb){ this.cb=cb; }
    public void submit(List<SocioComunitario> l){ data.clear(); if(l!=null) data.addAll(l); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v){
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_socio, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos){
        SocioComunitario s = data.get(pos);
        h.tvNombre.setText(s.getNombre()!=null?s.getNombre():"(Sin nombre)");
        h.btnMore.setOnClickListener(v->{
            PopupMenu pm=new PopupMenu(v.getContext(), h.btnMore);
            pm.getMenuInflater().inflate(R.menu.menu_item_mantenedor, pm.getMenu());
            pm.setOnMenuItemClickListener(i->{
                int id=i.getItemId();
                if(id==R.id.action_edit){ cb.onEditar(s); return true; }
                if(id==R.id.action_delete){ cb.onEliminar(s); return true; }
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
