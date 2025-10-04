package com.centroalerce.gestion.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Proyecto {

    @DocumentId
    private String id;
    private String nombre;
    private String descripcion;
    private boolean activo;

    // 🔹 Constructor vacío requerido por Firestore
    public Proyecto() {}

    // 🔹 Constructor completo (con ID)
    public Proyecto(String id, String nombre, String descripcion, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
    }

    // 🔹 Constructor alternativo (sin ID)
    public Proyecto(String nombre, String descripcion, boolean activo) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    // 🔹 Representación útil para listas o logs
    @Override
    public String toString() {
        return nombre + (descripcion != null && !descripcion.isEmpty()
                ? " - " + descripcion
                : "");
    }
}
