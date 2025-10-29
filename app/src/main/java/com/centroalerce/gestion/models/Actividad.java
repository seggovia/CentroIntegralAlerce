package com.centroalerce.gestion.models;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Actividad {
    private String id;
    private String nombre;
    private String tipoActividadId;
    private String tipoActividadNombre;
    private String periodicidad; // "Puntual" o "Periodica"
    private int cupo;
    private String oferenteId;
    private String oferenteNombre;
    private String socioComunitarioId;
    private String socioComunitarioNombre;
    private String proyectoId;
    private String proyectoNombre;
    private int diasAvisoPrevio;
    private List<ArchivoAdjunto> archivosAdjuntos;
    private Timestamp fechaCreacion;
    private String creadoPor;
    private boolean activo;
    private Timestamp fechaInicio; // Para actividades periódicas
    private Timestamp fechaFin; // Para actividades periódicas

    public Actividad() {
        this.archivosAdjuntos = new ArrayList<>();
    }

    public Actividad(String id, String nombre, String tipoActividadId, String tipoActividadNombre,
                     String periodicidad, int cupo, String oferenteId, String oferenteNombre,
                     String socioComunitarioId, String socioComunitarioNombre, String proyectoId,
                     String proyectoNombre, int diasAvisoPrevio, List<ArchivoAdjunto> archivosAdjuntos,
                     Timestamp fechaCreacion, String creadoPor, boolean activo,
                     Timestamp fechaInicio, Timestamp fechaFin) {
        this.id = id;
        this.nombre = nombre;
        this.tipoActividadId = tipoActividadId;
        this.tipoActividadNombre = tipoActividadNombre;
        this.periodicidad = periodicidad;
        this.cupo = cupo;
        this.oferenteId = oferenteId;
        this.oferenteNombre = oferenteNombre;
        this.socioComunitarioId = socioComunitarioId;
        this.socioComunitarioNombre = socioComunitarioNombre;
        this.proyectoId = proyectoId;
        this.proyectoNombre = proyectoNombre;
        this.diasAvisoPrevio = diasAvisoPrevio;
        this.archivosAdjuntos = archivosAdjuntos != null ? archivosAdjuntos : new ArrayList<>();
        this.fechaCreacion = fechaCreacion;
        this.creadoPor = creadoPor;
        this.activo = activo;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipoActividadId() { return tipoActividadId; }
    public void setTipoActividadId(String tipoActividadId) { this.tipoActividadId = tipoActividadId; }

    public String getTipoActividadNombre() { return tipoActividadNombre; }
    public void setTipoActividadNombre(String tipoActividadNombre) { this.tipoActividadNombre = tipoActividadNombre; }

    public String getPeriodicidad() { return periodicidad; }
    public void setPeriodicidad(String periodicidad) { this.periodicidad = periodicidad; }

    public int getCupo() { return cupo; }
    public void setCupo(int cupo) { this.cupo = cupo; }

    public String getOferenteId() { return oferenteId; }
    public void setOferenteId(String oferenteId) { this.oferenteId = oferenteId; }

    public String getOferenteNombre() { return oferenteNombre; }
    public void setOferenteNombre(String oferenteNombre) { this.oferenteNombre = oferenteNombre; }

    public String getSocioComunitarioId() { return socioComunitarioId; }
    public void setSocioComunitarioId(String socioComunitarioId) { this.socioComunitarioId = socioComunitarioId; }

    public String getSocioComunitarioNombre() { return socioComunitarioNombre; }
    public void setSocioComunitarioNombre(String socioComunitarioNombre) { this.socioComunitarioNombre = socioComunitarioNombre; }

    public String getProyectoId() { return proyectoId; }
    public void setProyectoId(String proyectoId) { this.proyectoId = proyectoId; }

    public String getProyectoNombre() { return proyectoNombre; }
    public void setProyectoNombre(String proyectoNombre) { this.proyectoNombre = proyectoNombre; }

    public int getDiasAvisoPrevio() { return diasAvisoPrevio; }
    public void setDiasAvisoPrevio(int diasAvisoPrevio) { this.diasAvisoPrevio = diasAvisoPrevio; }

    public List<ArchivoAdjunto> getArchivosAdjuntos() { return archivosAdjuntos; }
    public void setArchivosAdjuntos(List<ArchivoAdjunto> archivosAdjuntos) { this.archivosAdjuntos = archivosAdjuntos; }

    public Timestamp getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Timestamp fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Timestamp getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(Timestamp fechaInicio) { this.fechaInicio = fechaInicio; }

    public Timestamp getFechaFin() { return fechaFin; }
    public void setFechaFin(Timestamp fechaFin) { this.fechaFin = fechaFin; }

    // Métodos de utilidad
    public boolean esPeriodica() {
        return "Periodica".equals(this.periodicidad);
    }




    // Dentro de tu clase Actividad.java, junto a los otros campos

// ... otros campos como nombre, cupo, activo, etc.

    // CAMPO A AÑADIR:
    private List<String> beneficiariosIds;

// ... otros getters y setters que ya tienes

    // MÉTODOS A AÑADIR:
    public List<String> getBeneficiariosIds() {
        return beneficiariosIds;
    }

    public void setBeneficiariosIds(List<String> beneficiariosIds) {
        this.beneficiariosIds = beneficiariosIds;
    }







    public boolean tieneProyecto() {
        return proyectoId != null && !proyectoId.isEmpty();
    }
}