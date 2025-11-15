package com.centroalerce.gestion.utils;

/**
 * Enum que define los roles de usuario en el sistema
 * - VISUALIZADOR: Solo puede ver el calendario
 * - USUARIO: Puede ver y realizar acciones en actividades
 * - ADMINISTRADOR: Acceso total al sistema
 */
public enum UserRole {
    VISUALIZADOR("visualizador"),
    USUARIO("usuario"),
    ADMINISTRADOR("administrador");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convierte un string a UserRole
     * @param text El rol en formato string (desde Firebase)
     * @return El UserRole correspondiente, o VISUALIZADOR por defecto
     */
    public static UserRole fromString(String text) {
        if (text != null && !text.trim().isEmpty()) {
            for (UserRole role : UserRole.values()) {
                if (text.trim().equalsIgnoreCase(role.value)) {
                    return role;
                }
            }
        }
        // Por seguridad, el default es el rol más restrictivo
        return VISUALIZADOR;
    }

    // ========== PERMISOS DEL CALENDARIO ==========

    /**
     * @return true si puede ver el calendario (todos pueden)
     */
    public boolean canViewCalendar() {
        return true;
    }

    /**
     * @return true si puede modificar actividades
     * Solo USUARIO y ADMINISTRADOR
     */
    public boolean canModifyActivity() {
        return this == USUARIO || this == ADMINISTRADOR;
    }

    /**
     * @return true si puede reagendar actividades
     * Solo USUARIO y ADMINISTRADOR
     */
    public boolean canRescheduleActivity() {
        return this == USUARIO || this == ADMINISTRADOR;
    }

    /**
     * @return true si puede adjuntar archivos
     * Solo USUARIO y ADMINISTRADOR
     */
    public boolean canAttachFiles() {
        return this == USUARIO || this == ADMINISTRADOR;
    }

    /**
     * @return true si puede cancelar actividades
     * Solo USUARIO y ADMINISTRADOR
     */
    public boolean canCancelActivity() {
        return this == USUARIO || this == ADMINISTRADOR;
    }

    /**
     * @return true si puede marcar actividades como completadas
     * Solo USUARIO y ADMINISTRADOR
     */
    public boolean canMarkCompleted() {
        return this == USUARIO || this == ADMINISTRADOR;
    }

    /**
     * @return true si puede crear actividades
     * Solo USUARIO y ADMINISTRADOR
     */
    public boolean canCreateActivity() {
        return this == USUARIO || this == ADMINISTRADOR;
    }

    // ========== PERMISOS DE MANTENEDORES ==========

    /**
     * @return true si puede ver la sección de mantenedores
     * Solo ADMINISTRADOR
     */
    public boolean canViewMaintainers() {
        return this == ADMINISTRADOR;
    }

    /**
     * @return true si puede gestionar mantenedores
     * Solo ADMINISTRADOR
     */
    public boolean canManageMaintainers() {
        return this == ADMINISTRADOR;
    }

    // ========== MÉTODOS DE UTILIDAD ==========

    /**
     * @return true si puede interactuar con actividades
     * (modificar, reagendar, cancelar, etc.)
     */
    public boolean canInteractWithActivities() {
        return this == USUARIO || this == ADMINISTRADOR;
    }

    /**
     * @return true si es administrador
     */
    public boolean isAdmin() {
        return this == ADMINISTRADOR;
    }

    /**
     * @return true si es usuario común
     */
    public boolean isUsuario() {
        return this == USUARIO;
    }

    /**
     * @return true si es solo visualizador
     */
    public boolean isVisualizador() {
        return this == VISUALIZADOR;
    }

    @Override
    public String toString() {
        return value;
    }
}