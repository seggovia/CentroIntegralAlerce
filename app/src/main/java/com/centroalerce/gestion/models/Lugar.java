package com.centroalerce.gestion.models;

public class Lugar {
    private String id;
    private String nombre;
    private Integer cupo; // Opcional, puede ser null
    private boolean activo;

    public Lugar() {
    }

    public Lugar(String id, String nombre, Integer cupo, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.cupo = cupo;
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

    public Integer getCupo() {
        return cupo;
    }

    public void setCupo(Integer cupo) {
        this.cupo = cupo;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean tieneCupo() {
        return cupo != null && cupo > 0;
    }
}