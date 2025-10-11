package com.centroalerce.gestion.utils;

import java.util.Date; // ← 🔥 NUEVO: import que faltaba

public enum EstadoCita {
    PROGRAMADA("programada", "Programada", "#4CAF50"),
    COMPLETADA("completada", "Completada", "#2196F3"),
    CANCELADA("cancelada", "Cancelada", "#F44336"),
    REAGENDADA("reagendada", "Reagendada", "#FF9800");

    private final String value;
    private final String displayName;
    private final String colorHex;

    EstadoCita(String value, String displayName, String colorHex) {
        this.value = value;
        this.displayName = displayName;
        this.colorHex = colorHex;
    }

    public String getValue() { return value; }
    public String getDisplayName() { return displayName; }
    public String getColorHex() { return colorHex; }

    public static EstadoCita fromValue(String value) {
        for (EstadoCita estado : values()) {
            if (estado.value.equals(value)) {
                return estado;
            }
        }
        return PROGRAMADA; // Default
    }

    /**
     * Determina el estado según la fecha de la cita
     */
    public static EstadoCita determinarEstadoPorFecha(String estadoActual, Date fechaCita) {
        if ("cancelada".equals(estadoActual) || "reagendada".equals(estadoActual)) {
            return fromValue(estadoActual);
        }

        Date ahora = new Date();
        if (fechaCita.before(ahora) && "programada".equals(estadoActual)) {
            return COMPLETADA;
        }

        return fromValue(estadoActual);
    }
}