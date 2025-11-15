// com.centroalerce.gestion.models.Beneficiario.java
package com.centroalerce.gestion.models;

import java.io.Serializable;

public class Beneficiario implements Serializable {
    private String id;
    private String nombre;
    private String rut;   // opcional
    private boolean activo = true; // por defecto activo

    public Beneficiario() {} // Firestore necesita ctor vacío

    public Beneficiario(String id, String nombre, String rut) {
        this.id = id;
        this.nombre = nombre;
        this.rut = rut;
        this.activo = true;
    }

    public Beneficiario(String id, String nombre, String rut, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.rut = rut;
        this.activo = activo;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getRut() { return rut; }
    public boolean isActivo() { return activo; }

    public void setId(String id) { this.id = id; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setRut(String rut) { this.rut = rut; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
