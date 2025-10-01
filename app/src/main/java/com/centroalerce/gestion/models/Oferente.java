package com.centroalerce.gestion.models;

public class Oferente {
    private String id;
    private String nombre; // Nombre de la carrera
    private String docenteResponsable;
    private String institucion; // "IP", "CFT", "Universidad"
    private boolean activo;

    public Oferente() {
    }

    public Oferente(String id, String nombre, String docenteResponsable, String institucion, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.docenteResponsable = docenteResponsable;
        this.institucion = institucion;
        this.activo = activo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDocenteResponsable() {
        return docenteResponsable;
    }

    public void setDocenteResponsable(String docenteResponsable) {
        this.docenteResponsable = docenteResponsable;
    }

    public String getInstitucion() {
        return institucion;
    }

    public void setInstitucion(String institucion) {
        this.institucion = institucion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}