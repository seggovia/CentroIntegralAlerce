package com.centroalerce.gestion.models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class SocioComunitario {

    @DocumentId
    private String id;
    private String nombre;
    private String descripcion;
    private String caracterizacionBeneficiarios;
    private boolean activo;

    // 🔹 Constructor vacío requerido por Firestore
    public SocioComunitario() {}

    // 🔹 Constructor completo (con ID)
    public SocioComunitario(String id, String nombre, String descripcion,
                            String caracterizacionBeneficiarios, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.caracterizacionBeneficiarios = caracterizacionBeneficiarios;
        this.activo = activo;
    }

    // 🔹 Constructor alternativo (sin ID, Firestore lo genera)
    public SocioComunitario(String nombre, String descripcion,
                            String caracterizacionBeneficiarios, boolean activo) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.caracterizacionBeneficiarios = caracterizacionBeneficiarios;
        this.activo = activo;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCaracterizacionBeneficiarios() { return caracterizacionBeneficiarios; }
    public void setCaracterizacionBeneficiarios(String caracterizacionBeneficiarios) {
        this.caracterizacionBeneficiarios = caracterizacionBeneficiarios;
    }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    // 🔹 Representación útil para mostrar en listas o logs
    @Override
    public String toString() {
        return nombre +
                (descripcion != null && !descripcion.isEmpty() ? " - " + descripcion : "") +
                (caracterizacionBeneficiarios != null && !caracterizacionBeneficiarios.isEmpty()
                        ? " (" + caracterizacionBeneficiarios + ")" : "");
    }
}
