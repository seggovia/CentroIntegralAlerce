package com.centroalerce.gestion.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Oferente {

    @DocumentId
    private String id;
    private String nombre;
    private String docenteResponsable;
    private String carrera;
    private boolean activo;

    // 🔹 Constructor vacío requerido por Firestore
    public Oferente() {}

    // 🔹 Constructor completo (con id)
    public Oferente(String id, String nombre, String docenteResponsable, String carrera, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.docenteResponsable = docenteResponsable;
        this.carrera = carrera;
        this.activo = activo;
    }

    // 🔹 Constructor para nuevos oferentes (sin id)
    public Oferente(String nombre, String docenteResponsable, String carrera, boolean activo) {
        this.nombre = nombre;
        this.docenteResponsable = docenteResponsable;
        this.carrera = carrera;
        this.activo = activo;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDocenteResponsable() { return docenteResponsable; }
    public void setDocenteResponsable(String docenteResponsable) { this.docenteResponsable = docenteResponsable; }

    public String getCarrera() { return carrera; }
    public void setCarrera(String carrera) { this.carrera = carrera; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override
    public String toString() {
        return nombre + " - " + carrera;
    }
}
