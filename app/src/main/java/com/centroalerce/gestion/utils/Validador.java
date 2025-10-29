package com.centroalerce.gestion.utils;

import android.util.Patterns;

public class Validador {

    // Validar email
    public static boolean esEmailValido(String email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    // Validar contraseña (mínimo 6 caracteres)
    public static boolean esPasswordValida(String password) {
        return password != null && password.length() >= 6;
    }

    // Validar que un campo no esté vacío
    public static boolean esCampoVacio(String campo) {
        return campo == null || campo.trim().isEmpty();
    }

    // Validar nombre (no vacío y solo letras)
    public static boolean esNombreValido(String nombre) {
        return !esCampoVacio(nombre) && nombre.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+$");
    }

    // Validar número positivo
    public static boolean esNumeroPositivo(int numero) {
        return numero > 0;
    }

    // Validar número positivo o cero
    public static boolean esNumeroNoNegativo(int numero) {
        return numero >= 0;
    }

    // ✅ NUEVO: Validar si un número Integer es válido
    public static boolean esNumeroValido(Integer numero) {
        return numero != null && numero >= 0;
    }
}