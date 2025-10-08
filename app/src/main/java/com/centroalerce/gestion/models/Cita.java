package com.centroalerce.gestion.models;

import com.google.firebase.Timestamp;

public class Cita {
    private String id;
    private String actividadId;
    private String actividadNombre;
    private String lugarId;
    private String lugarNombre;

    // 🔥 NUEVO: Campo que faltaba
    private Timestamp startAt;  // ← Campo principal para queries
    private Timestamp fecha;    // ← Mantener por compatibilidad

    private String estado; // "agendada", "cancelada", "reagendada", "completada"
    private String motivoCancelacion;
    private String motivoReagendamiento;
    private String citaOriginalId;
    private Timestamp fechaCreacion;
    private Timestamp fechaModificacion;
    private String creadoPor;
    private boolean notificacionEnviada;

    // 🔥 NUEVO: Campo que faltaba
    private String patientId; // ← ID del paciente (si aplica)

    public Cita() {
    }

    // Getters y Setters COMPLETOS
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

    // 🔥 NUEVO
    public Timestamp getStartAt() { return startAt; }
    public void setStartAt(Timestamp startAt) { this.startAt = startAt; }

    public Timestamp getFecha() { return fecha; }
    public void setFecha(Timestamp fecha) {
        this.fecha = fecha;
        // Sincronizar con startAt si no existe
        if (this.startAt == null) this.startAt = fecha;
    }

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

    // 🔥 NUEVO
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

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