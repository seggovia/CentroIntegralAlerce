// com.centroalerce.gestion.models.Beneficiario.java
package com.centroalerce.gestion.models;

import java.io.Serializable;

public class Beneficiario implements Serializable {
    private String id;
    private String nombre;
    private String rut;   // opcional

    public Beneficiario() {} // Firestore necesita ctor vacío

    public Beneficiario(String id, String nombre, String rut) {
        this.id = id;
        this.nombre = nombre;
        this.rut = rut;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getRut() { return rut; }
    public void setId(String id) { this.id = id; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setRut(String rut) { this.rut = rut; }
}
