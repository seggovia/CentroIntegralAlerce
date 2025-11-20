package com.centroalerce.gestion.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gestor de historial de emails para autocompletado
 * Maneja el almacenamiento y recuperación de emails usados anteriormente
 */
public class EmailHistoryManager {

    private static final String PREFS_NAME = "EmailHistoryPrefs";
    private static final String KEY_EMAIL_HISTORY = "email_history";
    private static final String KEY_REMEMBER_EMAIL = "remember_email";
    private static final String KEY_LAST_EMAIL = "last_email";
    private static final int MAX_HISTORY_SIZE = 5; // Máximo 5 emails en el historial

    private final SharedPreferences prefs;

    public EmailHistoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Guarda un email en el historial
     * @param email Email a guardar
     */
    public void saveEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }

        email = email.trim().toLowerCase();

        // Obtener historial actual
        Set<String> history = prefs.getStringSet(KEY_EMAIL_HISTORY, new HashSet<>());
        Set<String> newHistory = new HashSet<>(history);

        // Agregar el nuevo email
        newHistory.add(email);

        // Si excede el límite, eliminar el más antiguo
        if (newHistory.size() > MAX_HISTORY_SIZE) {
            List<String> list = new ArrayList<>(newHistory);
            list.remove(0); // Eliminar el primero (más antiguo)
            newHistory = new HashSet<>(list);
        }

        // Guardar
        prefs.edit()
                .putStringSet(KEY_EMAIL_HISTORY, newHistory)
                .apply();
    }

    /**
     * Obtiene el historial de emails
     * @return Lista de emails guardados
     */
    public List<String> getEmailHistory() {
        Set<String> history = prefs.getStringSet(KEY_EMAIL_HISTORY, new HashSet<>());
        return new ArrayList<>(history);
    }

    /**
     * Guarda el último email usado y la opción de recordar
     * @param email Email a recordar
     * @param remember Si debe recordarse
     */
    public void saveLastEmail(String email, boolean remember) {
        prefs.edit()
                .putString(KEY_LAST_EMAIL, remember ? email : "")
                .putBoolean(KEY_REMEMBER_EMAIL, remember)
                .apply();

        // Si se marca "recordar", también guardarlo en el historial
        if (remember && email != null && !email.isEmpty()) {
            saveEmail(email);
        }
    }

    /**
     * Obtiene el último email recordado
     * @return Email recordado o cadena vacía
     */
    public String getLastEmail() {
        if (shouldRememberEmail()) {
            return prefs.getString(KEY_LAST_EMAIL, "");
        }
        return "";
    }

    /**
     * Verifica si debe recordarse el email
     * @return true si está activada la opción de recordar
     */
    public boolean shouldRememberEmail() {
        return prefs.getBoolean(KEY_REMEMBER_EMAIL, false);
    }

    /**
     * Elimina un email del historial
     * @param email Email a eliminar
     */
    public void removeEmail(String email) {
        Set<String> history = prefs.getStringSet(KEY_EMAIL_HISTORY, new HashSet<>());
        Set<String> newHistory = new HashSet<>(history);
        newHistory.remove(email.toLowerCase());

        prefs.edit()
                .putStringSet(KEY_EMAIL_HISTORY, newHistory)
                .apply();
    }

    /**
     * Limpia todo el historial
     */
    public void clearHistory() {
        prefs.edit()
                .remove(KEY_EMAIL_HISTORY)
                .remove(KEY_LAST_EMAIL)
                .putBoolean(KEY_REMEMBER_EMAIL, false)
                .apply();
    }

    /**
     * Busca emails que coincidan con el texto ingresado
     * @param query Texto a buscar
     * @return Lista de emails que coinciden
     */
    public List<String> searchEmails(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getEmailHistory();
        }

        query = query.toLowerCase();
        List<String> results = new ArrayList<>();

        for (String email : getEmailHistory()) {
            if (email.toLowerCase().contains(query)) {
                results.add(email);
            }
        }

        return results;
    }
}