package com.centroalerce.gestion.utils;

import com.centroalerce.gestion.models.Actividad;
import com.centroalerce.gestion.models.Lugar;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.List;

public class ActividadValidator {

    // Códigos de error
    public static final String ERROR_CAMPO_VACIO = "CAMPO_VACIO";
    public static final String ERROR_LUGAR_SIN_CUPO = "LUGAR_SIN_CUPO";
    public static final String ERROR_CONFLICTO_HORARIO = "CONFLICTO_HORARIO";
    public static final String ERROR_FECHA_PASADA = "FECHA_PASADA";
    public static final String ERROR_CUPO_INVALIDO = "CUPO_INVALIDO";

    /**
     * Valida que todos los campos obligatorios estén completos
     */
    public static ValidationResult validarCamposObligatorios(Actividad actividad) {
        if (Validador.esCampoVacio(actividad.getNombre())) {
            return ValidationResult.error(
                    "El nombre de la actividad es obligatorio",
                    ERROR_CAMPO_VACIO
            );
        }

        if (Validador.esCampoVacio(actividad.getTipoActividadId())) {
            return ValidationResult.error(
                    "Debe seleccionar un tipo de actividad",
                    ERROR_CAMPO_VACIO
            );
        }

        if (Validador.esCampoVacio(actividad.getPeriodicidad())) {
            return ValidationResult.error(
                    "Debe seleccionar la periodicidad",
                    ERROR_CAMPO_VACIO
            );
        }

        if (Validador.esCampoVacio(actividad.getOferenteId())) {
            return ValidationResult.error(
                    "Debe seleccionar un oferente",
                    ERROR_CAMPO_VACIO
            );
        }

        if (Validador.esCampoVacio(actividad.getSocioComunitarioId())) {
            return ValidationResult.error(
                    "Debe seleccionar un socio comunitario",
                    ERROR_CAMPO_VACIO
            );
        }

        return ValidationResult.success();
    }

    /**
     * Valida que el lugar tenga cupo disponible
     */
    public static ValidationResult validarCupoLugar(Lugar lugar, int cupoSolicitado) {
        if (lugar == null) {
            return ValidationResult.error(
                    "Debe seleccionar un lugar",
                    ERROR_CAMPO_VACIO
            );
        }

        // Si el lugar no tiene cupo definido, no hay restricción
        if (!lugar.tieneCupo()) {
            return ValidationResult.success();
        }

        // Validar que el cupo solicitado no exceda el cupo del lugar
        if (cupoSolicitado > lugar.getCupo()) {
            return ValidationResult.error(
                    "El cupo solicitado (" + cupoSolicitado + ") excede el cupo disponible del lugar (" + lugar.getCupo() + ")",
                    ERROR_LUGAR_SIN_CUPO
            );
        }

        return ValidationResult.success();
    }

    /**
     * Valida que no haya conflicto de horarios en el lugar
     */
    public static ValidationResult validarConflictoHorario(
            String lugarId,
            Date fechaHora,
            List<Date> citasExistentes,
            int margenMinutos) {

        if (lugarId == null || fechaHora == null) {
            return ValidationResult.success(); // No se puede validar sin datos
        }

        for (Date citaExistente : citasExistentes) {
            long diferencia = Math.abs(fechaHora.getTime() - citaExistente.getTime());
            long diferenciaMinutos = diferencia / (60 * 1000);

            if (diferenciaMinutos < margenMinutos) {
                return ValidationResult.error(
                        "Ya existe una actividad programada en este lugar a las " +
                                DateUtils.timestampToTimeString(new Timestamp(citaExistente)) +
                                ". Por favor, seleccione otro horario.",
                        ERROR_CONFLICTO_HORARIO
                );
            }
        }

        return ValidationResult.success();
    }

    /**
     * Valida que la fecha no sea en el pasado
     */
    public static ValidationResult validarFechaFutura(Date fecha) {
        if (fecha == null) {
            return ValidationResult.error(
                    "Debe seleccionar una fecha",
                    ERROR_CAMPO_VACIO
            );
        }

        Date ahora = new Date();
        if (fecha.before(ahora)) {
            return ValidationResult.error(
                    "No puede programar actividades en fechas pasadas",
                    ERROR_FECHA_PASADA
            );
        }

        return ValidationResult.success();
    }

    /**
     * Valida el cupo de la actividad
     */
    public static ValidationResult validarCupoActividad(int cupo) {
        if (cupo < 0) {
            return ValidationResult.error(
                    "El cupo no puede ser negativo",
                    ERROR_CUPO_INVALIDO
            );
        }

        if (cupo > 1000) {
            return ValidationResult.error(
                    "El cupo máximo permitido es 1000 personas",
                    ERROR_CUPO_INVALIDO
            );
        }

        return ValidationResult.success();
    }

    /**
     * Valida todos los aspectos de una actividad antes de crearla/modificarla
     */
    public static ValidationResult validarActividadCompleta(
            Actividad actividad,
            Lugar lugar,
            List<Date> fechasCitas,
            List<Date> citasExistentesEnLugar) {

        // 1. Validar campos obligatorios
        ValidationResult resultado = validarCamposObligatorios(actividad);
        if (!resultado.isValid()) return resultado;

        // 2. Validar cupo de la actividad
        resultado = validarCupoActividad(actividad.getCupo());
        if (!resultado.isValid()) return resultado;

        // 3. Validar cupo del lugar
        resultado = validarCupoLugar(lugar, actividad.getCupo());
        if (!resultado.isValid()) return resultado;

        // 4. Validar fechas y conflictos
        if (fechasCitas != null && !fechasCitas.isEmpty()) {
            for (Date fecha : fechasCitas) {
                // Validar que no sea fecha pasada
                resultado = validarFechaFutura(fecha);
                if (!resultado.isValid()) return resultado;

                // Validar conflictos de horario (margen de 30 minutos)
                resultado = validarConflictoHorario(
                        lugar.getId(),
                        fecha,
                        citasExistentesEnLugar,
                        30
                );
                if (!resultado.isValid()) return resultado;
            }
        } else {
            return ValidationResult.error(
                    "Debe seleccionar al menos una fecha para la actividad",
                    ERROR_CAMPO_VACIO
            );
        }

        return ValidationResult.success();
    }
}