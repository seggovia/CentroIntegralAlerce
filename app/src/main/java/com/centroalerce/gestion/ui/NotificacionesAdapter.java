package com.centroalerce.gestion.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Notificacion;
import com.centroalerce.gestion.utils.DateUtils;

import java.util.List;

/**
 * Adaptador para mostrar notificaciones en RecyclerView
 */
public class NotificacionesAdapter extends RecyclerView.Adapter<NotificacionesAdapter.NotificacionViewHolder> {
    
    private final List<Notificacion> notificaciones;
    private final OnNotificacionClickListener clickListener;
    
    public interface OnNotificacionClickListener {
        void onNotificacionClick(Notificacion notificacion);
    }
    
    public NotificacionesAdapter(List<Notificacion> notificaciones, OnNotificacionClickListener clickListener) {
        this.notificaciones = notificaciones;
        this.clickListener = clickListener;
    }
    
    @NonNull
    @Override
    public NotificacionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notificacion, parent, false);
        return new NotificacionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull NotificacionViewHolder holder, int position) {
        Notificacion notificacion = notificaciones.get(position);
        holder.bind(notificacion);
    }
    
    @Override
    public int getItemCount() {
        return notificaciones.size();
    }
    
    class NotificacionViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitulo;
        private final TextView tvMensaje;
        private final TextView tvFecha;
        private final TextView tvTipo;
        private final View indicatorNoLeida;
        
        public NotificacionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitulo = itemView.findViewById(R.id.tvTituloNotificacion);
            tvMensaje = itemView.findViewById(R.id.tvMensajeNotificacion);
            tvFecha = itemView.findViewById(R.id.tvFechaNotificacion);
            tvTipo = itemView.findViewById(R.id.tvTipoNotificacion);
            indicatorNoLeida = itemView.findViewById(R.id.indicatorNoLeida);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    clickListener.onNotificacionClick(notificaciones.get(position));
                }
            });
        }
        
        public void bind(Notificacion notificacion) {
            // Configurar título basado en el tipo
            String titulo = obtenerTituloPorTipo(notificacion.getTipo());
            tvTitulo.setText(titulo);
            
            // Configurar mensaje
            tvMensaje.setText(notificacion.getMensaje());
            
            // Configurar fecha
            if (notificacion.getFechaCreacion() != null) {
                String fechaFormateada = DateUtils.formatFechaHora(notificacion.getFechaCreacion().toDate());
                tvFecha.setText(fechaFormateada);
            }
            
            // Configurar tipo
            tvTipo.setText(obtenerTipoFormateado(notificacion.getTipo()));
            
            // Mostrar indicador de no leída
            indicatorNoLeida.setVisibility(notificacion.isLeida() ? View.GONE : View.VISIBLE);
            
            // Cambiar estilo según si está leída o no
            if (notificacion.isLeida()) {
                itemView.setAlpha(0.7f);
            } else {
                itemView.setAlpha(1.0f);
            }
        }
        
        private String obtenerTituloPorTipo(String tipo) {
            switch (tipo) {
                case "recordatorio":
                    return "Recordatorio de Actividad";
                case "cancelacion":
                    return "Actividad Cancelada";
                case "reagendamiento":
                    return "Actividad Reagendada";
                default:
                    return "Notificación";
            }
        }
        
        private String obtenerTipoFormateado(String tipo) {
            switch (tipo) {
                case "recordatorio":
                    return "Recordatorio";
                case "cancelacion":
                    return "Cancelación";
                case "reagendamiento":
                    return "Reagendamiento";
                default:
                    return "General";
            }
        }
    }
}

