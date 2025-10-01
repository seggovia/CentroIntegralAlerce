package com.centroalerce.gestion.models;

public class SocioComunitario {
    private String id;
    private String nombre;
    private String descripcion;
    private String caracterizacionBeneficiarios;
    private boolean activo;

    public SocioComunitario() {
    }

    public SocioComunitario(String id, String nombre, String descripcion,
                            String caracterizacionBeneficiarios, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.caracterizacionBeneficiarios = caracterizacionBeneficiarios;
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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getCaracterizacionBeneficiarios() {
        return caracterizacionBeneficiarios;
    }

    public void setCaracterizacionBeneficiarios(String caracterizacionBeneficiarios) {
        this.caracterizacionBeneficiarios = caracterizacionBeneficiarios;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}