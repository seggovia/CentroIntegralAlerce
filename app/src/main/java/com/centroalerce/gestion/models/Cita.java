package com.centroalerce.gestion.models;

import com.google.firebase.Timestamp;

public class Cita {
    private String id;
    private String actividadId;
    private String actividadNombre;
    private Timestamp startAt;
    private String lugarId;
    private String lugarNombre;
    private Timestamp fecha; // Fecha y hora de la cita
    private String estado; // "agendada", "cancelada", "reagendada", "completada"
    private String motivoCancelacion;
    private String motivoReagendamiento;
    private String citaOriginalId; // Si es un reagendamiento
    private Timestamp fechaCreacion;
    private Timestamp fechaModificacion;
    private String creadoPor;
    private boolean notificacionEnviada;

    public Cita() {
    }

    public Cita(String id, String actividadId, String actividadNombre, String lugarId,
                String lugarNombre, Timestamp fecha, String estado, String motivoCancelacion,
                String motivoReagendamiento, String citaOriginalId, Timestamp fechaCreacion,
                Timestamp fechaModificacion, String creadoPor, boolean notificacionEnviada) {
        this.id = id;
        this.actividadId = actividadId;
        this.actividadNombre = actividadNombre;
        this.lugarId = lugarId;
        this.lugarNombre = lugarNombre;
        this.fecha = fecha;
        this.estado = estado;
        this.motivoCancelacion = motivoCancelacion;
        this.motivoReagendamiento = motivoReagendamiento;
        this.citaOriginalId = citaOriginalId;
        this.fechaCreacion = fechaCreacion;
        this.fechaModificacion = fechaModificacion;
        this.creadoPor = creadoPor;
        this.notificacionEnviada = notificacionEnviada;
    }
    public Timestamp getStartAt() {
        return startAt;
    }

    // Setter
    public void setStartAt(Timestamp startAt) {
        this.startAt = startAt;
    }
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getActividadId() { return actividadId; }
    public void setActividadId(String actividadId) { this.actividadId = actividadId; }

    public String getActividadNombre() { return actividadNombre; }
    public void setActividadNombre(String actividadNombre) { this.actividadNombre = actividadNombre; }

    public String getLugarId() { return lugarId; }
    public void setLugarId(String lugarId) { this.lugarId = lugarId; }

    public String getLugarNombre() { return lugarNombre; }
    public void setLugarNombre(String lugarNombre) { this.lugarNombre = lugarNombre; }

    public Timestamp getFecha() { return fecha; }
    public void setFecha(Timestamp fecha) { this.fecha = fecha; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }

    public String getMotivoReagendamiento() { return motivoReagendamiento; }
    public void setMotivoReagendamiento(String motivoReagendamiento) { this.motivoReagendamiento = motivoReagendamiento; }

    public String getCitaOriginalId() { return citaOriginalId; }
    public void setCitaOriginalId(String citaOriginalId) { this.citaOriginalId = citaOriginalId; }

    public Timestamp getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Timestamp fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Timestamp getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(Timestamp fechaModificacion) { this.fechaModificacion = fechaModificacion; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }

    public boolean isNotificacionEnviada() { return notificacionEnviada; }
    public void setNotificacionEnviada(boolean notificacionEnviada) { this.notificacionEnviada = notificacionEnviada; }

    // Métodos de utilidad
    public boolean estaAgendada() {
        return "agendada".equals(this.estado);
    }

    public boolean estaCancelada() {
        return "cancelada".equals(this.estado);
    }

    public boolean estaCompletada() {
        return "completada".equals(this.estado);
    }

    public boolean fueReagendada() {
        return "reagendada".equals(this.estado);
    }
}