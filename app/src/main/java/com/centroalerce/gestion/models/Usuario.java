package com.centroalerce.gestion.models;

import com.centroalerce.gestion.utils.UserRole;
import com.google.firebase.Timestamp;

/**
 * Modelo de Usuario del sistema
 * Representa a un usuario con su información básica y rol
 */
public class Usuario {
    private String uid;
    private String email;
    private String nombre;
    private String rol; // "visualizador", "usuario", "administrador"
    private String estado; // "activo", "inactivo", "bloqueado"
    private boolean emailVerificado;
    private Timestamp fechaCreacion;
    private long creadoEn; // timestamp en milisegundos
    private boolean activo; // mantener compatibilidad con código anterior

    // ==================== CONSTRUCTORES ====================

    /**
     * Constructor vacío requerido por Firebase
     */
    public Usuario() {
    }

    /**
     * Constructor completo
     */
    public Usuario(String uid, String email, String nombre, String rol,
                   Timestamp fechaCreacion, boolean activo) {
        this.uid = uid;
        this.email = email;
        this.nombre = nombre;
        this.rol = rol != null ? rol : "visualizador"; // Default más restrictivo
        this.fechaCreacion = fechaCreacion;
        this.activo = activo;
        this.estado = activo ? "activo" : "inactivo";
        this.emailVerificado = false;
        this.creadoEn = fechaCreacion != null ?
                fechaCreacion.toDate().getTime() : System.currentTimeMillis();
    }

    /**
     * Constructor simplificado para nuevos usuarios
     */
    public Usuario(String uid, String email, String rol) {
        this.uid = uid;
        this.email = email;
        this.rol = rol != null ? rol : "usuario"; // Para nuevos usuarios, default "usuario"
        this.estado = "activo";
        this.activo = true;
        this.emailVerificado = false;
        this.fechaCreacion = Timestamp.now();
        this.creadoEn = System.currentTimeMillis();
    }

    // ==================== GETTERS Y SETTERS ====================

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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    public void setEmailVerificado(boolean emailVerificado) {
        this.emailVerificado = emailVerificado;
    }

    public Timestamp getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Timestamp fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public long getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(long creadoEn) {
        this.creadoEn = creadoEn;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
        // Sincronizar con campo estado
        if (activo && "inactivo".equals(this.estado)) {
            this.estado = "activo";
        } else if (!activo && "activo".equals(this.estado)) {
            this.estado = "inactivo";
        }
    }

    // ==================== MÉTODOS DE UTILIDAD PARA ROLES ====================

    /**
     * Obtiene el rol del usuario como enum UserRole
     * @return UserRole correspondiente al rol del usuario
     */
    public UserRole getUserRole() {
        return UserRole.fromString(this.rol);
    }

    /**
     * Verifica si el usuario es administrador
     * Compatible con rol "admin" y "administrador"
     * @return true si el usuario es administrador
     */
    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(this.rol) ||
                "administrador".equalsIgnoreCase(this.rol) ||
                getUserRole() == UserRole.ADMINISTRADOR;
    }

    /**
     * Verifica si el usuario es Usuario Común
     * @return true si el usuario es Usuario Común
     */
    public boolean isUsuario() {
        return "usuario".equalsIgnoreCase(this.rol) ||
                getUserRole() == UserRole.USUARIO;
    }

    /**
     * Verifica si el usuario es solo Visualizador
     * @return true si el usuario es Visualizador
     */
    public boolean isVisualizador() {
        return "visualizador".equalsIgnoreCase(this.rol) ||
                getUserRole() == UserRole.VISUALIZADOR;
    }

    /**
     * Verifica si el usuario puede interactuar con actividades
     * (modificar, reagendar, cancelar, etc.)
     * @return true si es Usuario o Administrador
     */
    public boolean canInteractWithActivities() {
        return isUsuario() || isAdmin();
    }

    /**
     * Verifica si el usuario puede acceder a mantenedores
     * @return true si es Administrador
     */
    public boolean canAccessMaintainers() {
        return isAdmin();
    }

    /**
     * Verifica si el usuario puede ver el calendario
     * @return true (todos pueden ver el calendario)
     */
    public boolean canViewCalendar() {
        return true;
    }

    // ==================== MÉTODOS DE VALIDACIÓN ====================

    /**
     * Verifica si el usuario está activo y puede usar el sistema
     * @return true si el usuario está activo
     */
    public boolean isUsuarioActivo() {
        return activo && "activo".equalsIgnoreCase(estado);
    }

    /**
     * Verifica si el usuario está bloqueado
     * @return true si el usuario está bloqueado
     */
    public boolean isBloqueado() {
        return "bloqueado".equalsIgnoreCase(estado);
    }

    // ==================== MÉTODOS PARA FIREBASE ====================

    /**
     * Convierte el usuario a un Map para guardar en Firebase
     * @return Map con los datos del usuario
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("uid", uid);
        map.put("email", email);
        map.put("nombre", nombre);
        map.put("rol", rol);
        map.put("estado", estado);
        map.put("emailVerificado", emailVerificado);
        map.put("fechaCreacion", fechaCreacion);
        map.put("creadoEn", creadoEn);
        map.put("activo", activo);
        return map;
    }

    // ==================== MÉTODOS ESTÁTICOS ====================

    /**
     * Crea un nuevo usuario con rol por defecto "usuario"
     * @param uid UID de Firebase Auth
     * @param email Email del usuario
     * @return Usuario nuevo con rol "usuario"
     */
    public static Usuario crearUsuarioNuevo(String uid, String email) {
        return new Usuario(uid, email, "usuario");
    }

    /**
     * Crea un nuevo administrador
     * @param uid UID de Firebase Auth
     * @param email Email del usuario
     * @return Usuario nuevo con rol "administrador"
     */
    public static Usuario crearAdministrador(String uid, String email) {
        return new Usuario(uid, email, "administrador");
    }

    /**
     * Crea un nuevo visualizador
     * @param uid UID de Firebase Auth
     * @param email Email del usuario
     * @return Usuario nuevo con rol "visualizador"
     */
    public static Usuario crearVisualizador(String uid, String email) {
        return new Usuario(uid, email, "visualizador");
    }

    // ==================== TOSTRING ====================

    @Override
    public String toString() {
        return "Usuario{" +
                "uid='" + uid + '\'' +
                ", email='" + email + '\'' +
                ", nombre='" + nombre + '\'' +
                ", rol='" + rol + '\'' +
                ", estado='" + estado + '\'' +
                ", activo=" + activo +
                '}';
    }
}