package com.centroalerce.gestion.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Lugar {
    @DocumentId
    private String id;

    private String nombre;
    private Integer cupo; // Opcional
    private boolean activo;

    // Constructor vacío requerido por Firestore
    public Lugar() {}

    public Lugar(String id, String nombre, Integer cupo, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.cupo = cupo;
        this.activo = activo;
    }

    // 🔹 Constructor alternativo para crear nuevos lugares sin ID (Firestore lo genera)
    public Lugar(String nombre, Integer cupo, boolean activo) {
        this.nombre = nombre;
        this.cupo = cupo;
        this.activo = activo;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getCupo() { return cupo; }
    public void setCupo(Integer cupo) { this.cupo = cupo; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    // 🔹 Método utilitario
    public boolean tieneCupo() {
        return cupo != null && cupo > 0;
    }

    // 🔹 Para mostrar en listas o logs
    @Override
    public String toString() {
        return nombre + (cupo != null ? " (Cupo: " + cupo + ")" : "");
    }
}
