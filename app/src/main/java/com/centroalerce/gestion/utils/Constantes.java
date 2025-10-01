package com.centroalerce.gestion.utils;

public class Constantes {

    // Roles de usuario
    public static final String ROL_ADMIN = "admin";
    public static final String ROL_USUARIO = "usuario";

    // Estados de cita
    public static final String ESTADO_AGENDADA = "agendada";
    public static final String ESTADO_CANCELADA = "cancelada";
    public static final String ESTADO_REAGENDADA = "reagendada";
    public static final String ESTADO_COMPLETADA = "completada";

    // Periodicidad
    public static final String PERIODICIDAD_PUNTUAL = "Puntual";
    public static final String PERIODICIDAD_PERIODICA = "Periodica";

    // Tipos de notificación
    public static final String NOTIF_RECORDATORIO = "recordatorio";
    public static final String NOTIF_CANCELACION = "cancelacion";
    public static final String NOTIF_REAGENDAMIENTO = "reagendamiento";

    // Instituciones
    public static final String INSTITUCION_IP = "IP";
    public static final String INSTITUCION_CFT = "CFT";
    public static final String INSTITUCION_UNIVERSIDAD = "Universidad";

    // Colecciones de Firestore
    public static final String COLLECTION_USUARIOS = "usuarios";
    public static final String COLLECTION_TIPOS_ACTIVIDAD = "tiposActividad";
    public static final String COLLECTION_LUGARES = "lugares";
    public static final String COLLECTION_OFERENTES = "oferentes";
    public static final String COLLECTION_SOCIOS = "sociosComunitarios";
    public static final String COLLECTION_PROYECTOS = "proyectos";
    public static final String COLLECTION_ACTIVIDADES = "actividades";
    public static final String COLLECTION_CITAS = "citas";
    public static final String COLLECTION_NOTIFICACIONES = "notificaciones";

    // Intent extras
    public static final String EXTRA_ACTIVIDAD_ID = "actividad_id";
    public static final String EXTRA_CITA_ID = "cita_id";
    public static final String EXTRA_MODO_EDICION = "modo_edicion";
}