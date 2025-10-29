package com.centroalerce.gestion.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Notificacion;
import com.centroalerce.gestion.repositories.NotificacionRepository;
import com.centroalerce.gestion.utils.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragmento para mostrar las notificaciones del usuario
 */
public class NotificacionesFragment extends Fragment {
    
    private RecyclerView recyclerViewNotificaciones;
    private NotificacionesAdapter adapter;
    private NotificacionRepository notificacionRepository;
    private List<Notificacion> notificaciones = new ArrayList<>();
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notificaciones, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Inicializar componentes
        notificacionRepository = new NotificacionRepository();
        
        // Configurar RecyclerView
        recyclerViewNotificaciones = view.findViewById(R.id.recyclerViewNotificaciones);
        recyclerViewNotificaciones.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new NotificacionesAdapter(notificaciones, this::onNotificacionClick);
        recyclerViewNotificaciones.setAdapter(adapter);
        
        // Cargar notificaciones
        cargarNotificaciones();
    }
    
    /**
     * Carga las notificaciones del usuario actual
     */
    private void cargarNotificaciones() {
        // TODO: Obtener el ID del usuario actual desde la sesión
        String usuarioId = "usuario_actual"; // Reemplazar con el ID real del usuario
        
        notificacionRepository.obtenerNotificacionesUsuario(usuarioId, new NotificacionRepository.NotificacionesCallback() {
            @Override
            public void onSuccess(List<Notificacion> notificaciones) {
                NotificacionesFragment.this.notificaciones.clear();
                NotificacionesFragment.this.notificaciones.addAll(notificaciones);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(getContext(), "Error al cargar notificaciones: " + error, 
                             Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Maneja el click en una notificación
     */
    private void onNotificacionClick(Notificacion notificacion) {
        // Marcar como leída si no lo está
        if (!notificacion.isLeida()) {
            notificacionRepository.marcarComoLeida(notificacion.getId(), new NotificacionRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    notificacion.setLeida(true);
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(getContext(), "Error al marcar como leída: " + error, 
                                 Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // TODO: Navegar a la actividad o cita relacionada
        Toast.makeText(getContext(), "Notificación: " + notificacion.getMensaje(), 
                     Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Recargar notificaciones cuando el fragmento se vuelve visible
        cargarNotificaciones();
    }
}

