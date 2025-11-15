package com.centroalerce.gestion.utils;

import android.text.TextUtils;
import android.util.Patterns;
import java.util.regex.Pattern;

/**
 * Clase utilitaria con validaciones comunes para todos los mantenedores
 */
public class ValidationUtils {

    // ========== VALIDACIONES DE TEXTO ==========

    /**
     * Valida que un texto no esté vacío
     */
    public static boolean isNotEmpty(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Valida longitud mínima
     */
    public static boolean hasMinLength(String text, int minLength) {
        return text != null && text.trim().length() >= minLength;
    }

    /**
     * Valida longitud máxima
     */
    public static boolean hasMaxLength(String text, int maxLength) {
        return text != null && text.trim().length() <= maxLength;
    }

    /**
     * Valida que solo contenga letras, espacios y acentos
     */
    public static boolean isValidName(String name) {
        if (!isNotEmpty(name)) return false;
        // Permite letras, espacios, acentos, ñ, diéresis
        Pattern pattern = Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$");
        return pattern.matcher(name.trim()).matches();
    }

    /**
     * Valida que no contenga caracteres especiales peligrosos
     */
    public static boolean isSafeText(String text) {
        if (text == null) return true;
        // Rechaza caracteres que puedan causar problemas
        Pattern dangerousChars = Pattern.compile("[<>{}\\[\\]\\\\;'\"`]");
        return !dangerousChars.matcher(text).find();
    }

    // ========== VALIDACIONES DE RUT CHILENO ==========

    /**
     * Valida formato de RUT chileno (12.345.678-9)
     */
    public static boolean isValidRutFormat(String rut) {
        if (!isNotEmpty(rut)) return false;

        // Limpiar puntos y guiones
        String cleanRut = rut.replace(".", "").replace("-", "").trim();

        // Debe tener entre 8 y 9 caracteres (7-8 dígitos + verificador)
        if (cleanRut.length() < 8 || cleanRut.length() > 9) return false;

        // Separar cuerpo y dígito verificador
        String body = cleanRut.substring(0, cleanRut.length() - 1);
        String verifier = cleanRut.substring(cleanRut.length() - 1);

        // El cuerpo debe ser numérico
        if (!body.matches("\\d+")) return false;

        // El verificador debe ser dígito o 'K'
        if (!verifier.matches("[0-9kK]")) return false;

        return true;
    }

    /**
     * Valida RUT chileno con algoritmo del módulo 11
     */
    public static boolean isValidRut(String rut) {
        if (!isValidRutFormat(rut)) return false;

        String cleanRut = rut.replace(".", "").replace("-", "").trim().toUpperCase();
        String body = cleanRut.substring(0, cleanRut.length() - 1);
        String verifier = cleanRut.substring(cleanRut.length() - 1);

        try {
            int rutNumber = Integer.parseInt(body);
            int m = 0, s = 1;

            for (; rutNumber != 0; rutNumber /= 10) {
                s = (s + rutNumber % 10 * (9 - m++ % 6)) % 11;
            }

            String calculatedVerifier = (s > 0) ? String.valueOf(s - 1) : "K";

            return calculatedVerifier.equals(verifier);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ========== VALIDACIONES DE CONTACTO ==========

    /**
     * Valida formato de email
     */
    public static boolean isValidEmail(String email) {
        if (!isNotEmpty(email)) return false;
        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    /**
     * Valida formato de teléfono chileno
     */
    public static boolean isValidPhoneChile(String phone) {
        if (!isNotEmpty(phone)) return false;

        String cleanPhone = phone.replaceAll("[\\s\\-().]", "");

        // Formatos válidos:
        // - Móvil: +56912345678, 912345678, 56912345678
        // - Fijo: +5622123456, 22123456, 5622123456

        // Móvil (9 dígitos)
        if (cleanPhone.matches("^(\\+?56)?9\\d{8}$")) return true;

        // Fijo (8-9 dígitos con código de área)
        if (cleanPhone.matches("^(\\+?56)?[2-9]\\d{7,8}$")) return true;

        return false;
    }

    // ========== VALIDACIONES NUMÉRICAS ==========

    /**
     * Valida que un número sea positivo
     */
    public static boolean isPositiveNumber(String number) {
        try {
            int value = Integer.parseInt(number.trim());
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Valida que un número esté en un rango
     */
    public static boolean isInRange(String number, int min, int max) {
        try {
            int value = Integer.parseInt(number.trim());
            return value >= min && value <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ========== MENSAJES DE ERROR ==========

    public static String getErrorRequired() {
        return "Este campo es obligatorio";
    }

    public static String getErrorMinLength(int minLength) {
        return "Debe tener al menos " + minLength + " caracteres";
    }

    public static String getErrorMaxLength(int maxLength) {
        return "No puede exceder " + maxLength + " caracteres";
    }

    public static String getErrorInvalidName() {
        return "El nombre solo puede contener letras y espacios";
    }

    public static String getErrorInvalidRut() {
        return "RUT inválido (formato: 12.345.678-9)";
    }

    public static String getErrorInvalidEmail() {
        return "Email inválido";
    }

    public static String getErrorInvalidPhone() {
        return "Teléfono inválido (debe tener al menos 8 dígitos)";
    }

    public static String getErrorInvalidNumber() {
        return "Debe ingresar un número válido";
    }

    public static String getErrorUnsafeCharacters() {
        return "Contiene caracteres no permitidos";
    }

    // ========== FORMATEO ==========

    /**
     * Formatea un RUT chileno
     */
    public static String formatRut(String rut) {
        if (!isNotEmpty(rut)) return "";

        String clean = rut.replace(".", "").replace("-", "").trim();
        if (clean.length() < 2) return clean;

        String body = clean.substring(0, clean.length() - 1);
        String verifier = clean.substring(clean.length() - 1);

        // Agregar puntos cada 3 dígitos desde la derecha
        StringBuilder formatted = new StringBuilder(body);
        for (int i = formatted.length() - 3; i > 0; i -= 3) {
            formatted.insert(i, ".");
        }

        return formatted.toString() + "-" + verifier;
    }

    /**
     * Formatea un teléfono chileno
     */
    public static String formatPhone(String phone) {
        if (!isNotEmpty(phone)) return "";

        String clean = phone.replaceAll("[^0-9+]", "");

        // Si empieza con +56, formatear
        if (clean.startsWith("+56") && clean.length() >= 11) {
            return "+56 " + clean.substring(3, 4) + " " +
                    clean.substring(4, 8) + " " + clean.substring(8);
        }

        // Si es móvil sin código
        if (clean.startsWith("9") && clean.length() == 9) {
            return clean.substring(0, 1) + " " +
                    clean.substring(1, 5) + " " + clean.substring(5);
        }

        return phone;
    }
}