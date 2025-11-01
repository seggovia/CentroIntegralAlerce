package com.centroalerce.gestion.utils;

import android.content.Context;
import android.widget.Toast;


public class PermissionChecker {

    private final RoleManager roleManager;

    public PermissionChecker() {
        this.roleManager = RoleManager.getInstance();
    }

    /**
     * Verifica permiso y muestra mensaje de error si no lo tiene
     */
    public boolean checkAndNotify(Context context, Permission permission) {
        if (!hasPermission(permission)) {
            showPermissionDeniedMessage(context, permission);
            return false;
        }
        return true;
    }

    /**
     * Verifica si el usuario actual tiene el permiso especificado
     */
    public boolean hasPermission(Permission permission) {
        UserRole role = roleManager.getCurrentUserRole();

        switch (permission) {
            case VIEW_CALENDAR:
                return role.canViewCalendar();

            case MODIFY_ACTIVITY:
                return role.canModifyActivity();

            case RESCHEDULE_ACTIVITY:
                return role.canRescheduleActivity();

            case ATTACH_FILES:
                return role.canAttachFiles();

            case CANCEL_ACTIVITY:
                return role.canCancelActivity();

            case MARK_COMPLETED:
                return role.canMarkCompleted();

            case VIEW_MAINTAINERS:
                return role.canViewMaintainers();

            case MANAGE_MAINTAINERS:
                return role.canManageMaintainers();

            default:
                return false;
        }
    }

    /**
     * Verifica si el usuario es administrador
     */
    public boolean isAdmin() {
        return roleManager.getCurrentUserRole() == UserRole.ADMINISTRADOR;
    }

    /**
     * Verifica si el usuario es Usuario Común o Administrador
     */
    public boolean canInteractWithActivities() {
        UserRole role = roleManager.getCurrentUserRole();
        return role == UserRole.USUARIO || role == UserRole.ADMINISTRADOR;
    }

    /**
     * Verifica si el usuario es solo Visualizador
     */
    public boolean isViewer() {
        return roleManager.getCurrentUserRole() == UserRole.VISUALIZADOR;
    }

    private void showPermissionDeniedMessage(Context context, Permission permission) {
        String message;
        switch (permission) {
            case MODIFY_ACTIVITY:
                message = "No tienes permisos para modificar actividades";
                break;
            case RESCHEDULE_ACTIVITY:
                message = "No tienes permisos para reagendar actividades";
                break;
            case ATTACH_FILES:
                message = "No tienes permisos para adjuntar archivos";
                break;
            case CANCEL_ACTIVITY:
                message = "No tienes permisos para cancelar actividades";
                break;
            case MARK_COMPLETED:
                message = "No tienes permisos para marcar actividades como completadas";
                break;
            case VIEW_MAINTAINERS:
                message = "Solo los administradores pueden acceder a mantenedores";
                break;
            case MANAGE_MAINTAINERS:
                message = "Solo los administradores pueden gestionar mantenedores";
                break;
            case CREATE_ACTIVITY:
                message = "No tienes permisos para crear actividades";
                break;
            case REGISTER_TO_ACTIVITY:
                message = "No tienes permisos para inscribirte a actividades";
                break;
            case MANAGE_USERS:
                message = "Solo los administradores pueden gestionar usuarios";
                break;
            default:
                message = "No tienes permisos para realizar esta acción";
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Enum con todos los permisos del sistema
     */
    public enum Permission {
        VIEW_CALENDAR,           // Ver calendario
        MODIFY_ACTIVITY,         // Modificar actividad
        RESCHEDULE_ACTIVITY,     // Reagendar actividad
        ATTACH_FILES,            // Adjuntar archivos
        CANCEL_ACTIVITY,         // Cancelar actividad
        MARK_COMPLETED,          // Marcar como completada
        VIEW_MAINTAINERS,        // Ver mantenedores
        MANAGE_MAINTAINERS,      // Gestionar mantenedores
        CREATE_ACTIVITY,         // Crear actividad
        REGISTER_TO_ACTIVITY,    // Inscribirse a actividad
        MANAGE_USERS             // Gestionar usuarios
    }
}