package com.centroalerce.gestion.examples;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Actividad;
import com.centroalerce.gestion.models.Cita;
import com.centroalerce.gestion.services.NotificationService;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Fragmento de ejemplo que demuestra cómo usar el sistema de notificaciones
 */
public class NotificationExampleFragment extends Fragment {
    
    private NotificationService notificationService;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification_example, container, false);
        
        notificationService = new NotificationService(requireContext());
        
        // Configurar botones de ejemplo
        Button btnTestActividad = view.findViewById(R.id.btnTestActividad);
        Button btnTestCita = view.findViewById(R.id.btnTestCita);
        Button btnTestNotificacionInmediata = view.findViewById(R.id.btnTestNotificacionInmediata);
        
        btnTestActividad.setOnClickListener(v -> testNotificacionActividad());
        btnTestCita.setOnClickListener(v -> testNotificacionCita());
        btnTestNotificacionInmediata.setOnClickListener(v -> testNotificacionInmediata());
        
        return view;
    }
    
    /**
     * Ejemplo: Programar notificaciones para una actividad
     */
    private void testNotificacionActividad() {
        // Crear una actividad de ejemplo
        Actividad actividad = new Actividad();
        actividad.setId("actividad_ejemplo_001");
        actividad.setNombre("Taller de Cocina Saludable");
        actividad.setDiasAvisoPrevio(2); // Notificar 2 días antes
        actividad.setPeriodicidad("Periodica");
        
        // Fecha de inicio (mañana)
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        actividad.setFechaInicio(new Timestamp(calendar.getTime()));
        
        // Lista de usuarios a notificar
        List<String> usuariosNotificar = new ArrayList<>();
        usuariosNotificar.add("usuario_001");
        usuariosNotificar.add("usuario_002");
        
        // Programar notificaciones
        notificationService.programarNotificacionesActividad(actividad, usuariosNotificar);
        
        Toast.makeText(getContext(), "Notificaciones programadas para actividad", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Ejemplo: Programar notificaciones para una cita específica
     */
    private void testNotificacionCita() {
        // Crear una cita de ejemplo
        Cita cita = new Cita();
        cita.setId("cita_ejemplo_001");
        cita.setActividadNombre("Consulta Médica");
        cita.setLugarNombre("Centro de Salud");
        
        // Fecha de la cita (en 3 días)
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 3);
        cita.setFecha(new Timestamp(calendar.getTime()));
        
        // Crear actividad asociada
        Actividad actividad = new Actividad();
        actividad.setNombre("Consulta Médica");
        actividad.setDiasAvisoPrevio(1); // Notificar 1 día antes
        
        // Lista de usuarios a notificar
        List<String> usuariosNotificar = new ArrayList<>();
        usuariosNotificar.add("usuario_001");
        
        // Programar notificaciones
        notificationService.programarNotificacionesCita(cita, actividad, usuariosNotificar);
        
        Toast.makeText(getContext(), "Notificaciones programadas para cita", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Ejemplo: Enviar notificación inmediata
     */
    private void testNotificacionInmediata() {
        notificationService.enviarNotificacionInmediata(
            "Recordatorio de Actividad",
            "La actividad 'Taller de Cocina' está programada para mañana a las 10:00 AM. ¡No olvides asistir!",
            12345 // ID único para la notificación
        );
        
        Toast.makeText(getContext(), "Notificación enviada inmediatamente", Toast.LENGTH_SHORT).show();
    }
}

