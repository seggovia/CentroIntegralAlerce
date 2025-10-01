package com.centroalerce.gestion.models;

import com.google.firebase.Timestamp;

public class Usuario {
    private String uid;
    private String email;
    private String nombre;
    private String rol; // "admin" o "usuario"
    private Timestamp fechaCreacion;
    private boolean activo;

    // Constructor vacío requerido por Firebase
    public Usuario() {
    }

    // Constructor completo
    public Usuario(String uid, String email, String nombre, String rol, Timestamp fechaCreacion, boolean activo) {
        this.uid = uid;
        this.email = email;
        this.nombre = nombre;
        this.rol = rol;
        this.fechaCreacion = fechaCreacion;
        this.activo = activo;
    }

    // Getters y Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public Timestamp getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Timestamp fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    // Método para verificar si es administrador
    public boolean isAdmin() {
        return "admin".equals(this.rol);
    }
}