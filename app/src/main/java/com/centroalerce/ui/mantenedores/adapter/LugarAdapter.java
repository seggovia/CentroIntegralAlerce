package com.centroalerce.ui.mantenedores.adapter;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu; // <- IMPORT CORRECTO
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Lugar;

import java.util.ArrayList;
import java.util.List;

public class LugarAdapter extends RecyclerView.Adapter<LugarAdapter.VH> {

    public interface Callbacks {
        void onEditar(Lugar l);
        void onEliminar(Lugar l);
    }

    private final List<Lugar> data = new ArrayList<>();
    private final Callbacks callbacks;

    public LugarAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submit(List<Lugar> nuevos) {
        data.clear();
        if (nuevos != null) data.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lugar, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Lugar l = data.get(pos);

        String nombre = l.getNombre() != null ? l.getNombre() : "(Sin nombre)";
        h.tvNombre.setText(nombre);

        String cupoTxt = (l.getCupo() == null) ? "Cupo: —" : "Cupo: " + l.getCupo();
        h.tvCupo.setText(cupoTxt);

        h.btnMore.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(v.getContext(), h.btnMore);
            pm.getMenuInflater().inflate(R.menu.menu_item_mantenedor, pm.getMenu());
            pm.setOnMenuItemClickListener(item -> handle(item, l));
            pm.show();
        });
    }

    private boolean handle(MenuItem item, Lugar l) {
        int id = item.getItemId();
        if (id == R.id.action_edit)   { callbacks.onEditar(l);   return true; }
        if (id == R.id.action_delete) { callbacks.onEliminar(l); return true; }
        return false;
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNombre, tvCupo;
        ImageButton btnMore;
        VH(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvNombre);
            tvCupo   = itemView.findViewById(R.id.tvCupo);
            btnMore  = itemView.findViewById(R.id.btnMore);
        }
    }
}
