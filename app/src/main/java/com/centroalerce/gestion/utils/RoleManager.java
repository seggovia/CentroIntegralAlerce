package com.centroalerce.gestion.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
public class RoleManager {
    private static RoleManager instance;
    private UserRole currentUserRole;
    private String currentUserId;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    private RoleManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUserRole = UserRole.VISUALIZADOR; // Por defecto el más restrictivo
    }

    /**
     * Obtiene la instancia única del RoleManager
     */
    public static synchronized RoleManager getInstance() {
        if (instance == null) {
            instance = new RoleManager();
        }
        return instance;
    }

    /**
     * Carga el rol del usuario desde Firebase
     * @param callback Callback que se ejecuta cuando el rol está cargado
     */
    public void loadUserRole(RoleLoadCallback callback) {
        if (auth.getCurrentUser() == null) {
            currentUserRole = UserRole.VISUALIZADOR;
            callback.onRoleLoaded(UserRole.VISUALIZADOR);
            return;
        }

        currentUserId = auth.getCurrentUser().getUid();

        db.collection("usuarios")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String rolString = documentSnapshot.getString("rol");
                        currentUserRole = UserRole.fromString(rolString);
                    } else {
                        // Usuario no encontrado en Firestore, asignar rol por defecto
                        currentUserRole = UserRole.VISUALIZADOR;
                    }
                    callback.onRoleLoaded(currentUserRole);
                })
                .addOnFailureListener(e -> {
                    // En caso de error, usar rol más restrictivo
                    currentUserRole = UserRole.VISUALIZADOR;
                    callback.onRoleLoaded(currentUserRole);
                });
    }

    /**
     * Obtiene el rol actual del usuario (desde memoria)
     * IMPORTANTE: Debe haberse llamado a loadUserRole() primero
     */
    public UserRole getCurrentUserRole() {
        return currentUserRole;
    }

    /**
     * Establece manualmente el rol del usuario actual
     * Útil para testing o casos especiales
     */
    public void setCurrentUserRole(UserRole role) {
        this.currentUserRole = role;
    }

    /**
     * Obtiene el UID del usuario actual
     */
    public String getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Limpia el rol almacenado (útil al cerrar sesión)
     */
    public void clearRole() {
        currentUserRole = UserRole.VISUALIZADOR;
        currentUserId = null;
    }

    /**
     * Callback que se ejecuta cuando el rol ha sido cargado
     */
    public interface RoleLoadCallback {
        void onRoleLoaded(UserRole role);
    }
}