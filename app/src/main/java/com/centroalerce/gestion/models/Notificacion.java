package com.centroalerce.gestion.models;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Notificacion {
    private String id;
    private String tipo; // "recordatorio", "cancelacion", "reagendamiento"
    private String citaId;
    private String actividadNombre;
    private Timestamp fecha;
    private String mensaje;
    private boolean leida;
    private Timestamp fechaCreacion;
    private List<String> usuariosNotificados; // Array de UIDs

    public Notificacion() {
        this.usuariosNotificados = new ArrayList<>();
    }

    public Notificacion(String id, String tipo, String citaId, String actividadNombre,
                        Timestamp fecha, String mensaje, boolean leida, Timestamp fechaCreacion,
                        List<String> usuariosNotificados) {
        this.id = id;
        this.tipo = tipo;
        this.citaId = citaId;
        this.actividadNombre = actividadNombre;
        this.fecha = fecha;
        this.mensaje = mensaje;
        this.leida = leida;
        this.fechaCreacion = fechaCreacion;
        this.usuariosNotificados = usuariosNotificados != null ? usuariosNotificados : new ArrayList<>();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getCitaId() { return citaId; }
    public void setCitaId(String citaId) { this.citaId = citaId; }

    public String getActividadNombre() { return actividadNombre; }
    public void setActividadNombre(String actividadNombre) { this.actividadNombre = actividadNombre; }

    public Timestamp getFecha() { return fecha; }
    public void setFecha(Timestamp fecha) { this.fecha = fecha; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }

    public Timestamp getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Timestamp fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public List<String> getUsuariosNotificados() { return usuariosNotificados; }
    public void setUsuariosNotificados(List<String> usuariosNotificados) { this.usuariosNotificados = usuariosNotificados; }

    // Métodos de utilidad
    public boolean esRecordatorio() {
        return "recordatorio".equals(this.tipo);
    }

    public boolean esCancelacion() {
        return "cancelacion".equals(this.tipo);
    }

    public boolean esReagendamiento() {
        return "reagendamiento".equals(this.tipo);
    }
}