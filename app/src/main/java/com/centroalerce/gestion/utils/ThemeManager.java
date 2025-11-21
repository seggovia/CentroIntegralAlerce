package com.centroalerce.gestion.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Gestor del tema oscuro/claro de la aplicación
 * Maneja la persistencia y aplicación del tema seleccionado
 */
public class ThemeManager {

    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    // Modos de tema
    public static final int MODE_LIGHT = 0;
    public static final int MODE_DARK = 1;
    public static final int MODE_SYSTEM = 2;

    private final SharedPreferences prefs;

    public ThemeManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Guarda el modo de tema seleccionado
     * @param mode MODE_LIGHT, MODE_DARK o MODE_SYSTEM
     */
    public void saveThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    /**
     * Obtiene el modo de tema guardado
     * @return MODE_LIGHT, MODE_DARK o MODE_SYSTEM
     */
    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, MODE_LIGHT); // Default: claro
    }

    /**
     * Verifica si el modo oscuro está activo
     * @return true si está en modo oscuro
     */
    public boolean isDarkMode() {
        return getThemeMode() == MODE_DARK;
    }

    /**
     * Aplica el tema guardado a la aplicación
     */
    public void applyTheme() {
        int mode = getThemeMode();
        switch (mode) {
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case MODE_LIGHT:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    /**
     * Aplica un modo de tema específico
     * @param mode MODE_LIGHT, MODE_DARK o MODE_SYSTEM
     */
    public void applyTheme(int mode) {
        saveThemeMode(mode);
        applyTheme();
    }

    /**
     * Alterna entre modo claro y oscuro (sin usar modo sistema)
     */
    public void toggleTheme() {
        int currentMode = getThemeMode();
        int newMode = (currentMode == MODE_DARK) ? MODE_LIGHT : MODE_DARK;
        applyTheme(newMode);
    }

    /**
     * Limpia las preferencias de tema (resetea a claro)
     */
    public void clearThemePreferences() {
        prefs.edit().remove(KEY_THEME_MODE).apply();
        applyTheme(MODE_LIGHT);
    }
}