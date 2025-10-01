package com.centroalerce.gestion.models;

import com.google.firebase.Timestamp;

public class ArchivoAdjunto {
    private String nombre;
    private String url;
    private Timestamp fechaSubida;

    public ArchivoAdjunto() {
    }

    public ArchivoAdjunto(String nombre, String url, Timestamp fechaSubida) {
        this.nombre = nombre;
        this.url = url;
        this.fechaSubida = fechaSubida;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Timestamp getFechaSubida() {
        return fechaSubida;
    }

    public void setFechaSubida(Timestamp fechaSubida) {
        this.fechaSubida = fechaSubida;
    }
}