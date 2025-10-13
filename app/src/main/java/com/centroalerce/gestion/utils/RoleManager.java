package com.centroalerce.gestion.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Singleton para gestionar el rol del usuario actual
 * Mantiene en caché el rol y lo sincroniza con Firebase
 */
public class RoleManager {

    private static final String TAG = "RoleManager";
    private static RoleManager instance;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private UserRole currentUserRole;
    private String currentUserId;
    private ListenerRegistration roleListener;

    // Callback para notificar cambios de rol
    public interface OnRoleLoadedListener {
        void onRoleLoaded(UserRole role);
    }

    // ✅ MANTENER COMPATIBILIDAD con el callback anterior
    public interface RoleLoadCallback {
        void onRoleLoaded(UserRole role);
    }

    private RoleManager() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUserRole = UserRole.VISUALIZADOR; // Default seguro
    }

    public static synchronized RoleManager getInstance() {
        if (instance == null) {
            instance = new RoleManager();
        }
        return instance;
    }

    /**
     * Carga el rol del usuario actual desde Firebase
     * y lo mantiene en caché
     */
    public void loadUserRole(@NonNull OnRoleLoadedListener listener) {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Log.w(TAG, "⚠️ No hay usuario autenticado");
            currentUserRole = UserRole.VISUALIZADOR;
            currentUserId = null;
            listener.onRoleLoaded(currentUserRole);
            return;
        }

        String uid = currentUser.getUid();
        currentUserId = uid;
        Log.d(TAG, "🔍 Cargando rol para usuario: " + uid);

        db.collection("usuarios")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String rolString = document.getString("rol");
                        currentUserRole = UserRole.fromString(rolString);

                        Log.d(TAG, "✅ Rol cargado: " + currentUserRole.getValue());
                        listener.onRoleLoaded(currentUserRole);
                    } else {
                        Log.w(TAG, "⚠️ Documento de usuario no existe, asignando VISUALIZADOR");
                        currentUserRole = UserRole.VISUALIZADOR;
                        listener.onRoleLoaded(currentUserRole);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error al cargar rol: " + e.getMessage(), e);
                    currentUserRole = UserRole.VISUALIZADOR; // Fallback seguro
                    listener.onRoleLoaded(currentUserRole);
                });
    }

    /**
     * ✅ Sobrecarga para mantener compatibilidad con código anterior
     */
    public void loadUserRole(@NonNull RoleLoadCallback callback) {
        loadUserRole((OnRoleLoadedListener) callback::onRoleLoaded);
    }

    /**
     * Escucha cambios en el rol del usuario en tiempo real
     * Útil si el administrador cambia roles desde otro lugar
     */
    public void subscribeToRoleChanges(@NonNull OnRoleLoadedListener listener) {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Log.w(TAG, "⚠️ No hay usuario para suscribirse a cambios");
            return;
        }

        String uid = currentUser.getUid();
        currentUserId = uid;

        // Remover listener anterior si existe
        if (roleListener != null) {
            roleListener.remove();
        }

        roleListener = db.collection("usuarios")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "❌ Error en listener de rol: " + error.getMessage(), error);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        String rolString = snapshot.getString("rol");
                        UserRole newRole = UserRole.fromString(rolString);

                        if (newRole != currentUserRole) {
                            Log.d(TAG, "🔄 Rol actualizado: " + currentUserRole.getValue() + " → " + newRole.getValue());
                            currentUserRole = newRole;
                            listener.onRoleLoaded(currentUserRole);
                        }
                    }
                });
    }

    /**
     * ✅ Detiene la escucha de cambios de rol
     * Llamar en onDestroy de Activities/Fragments
     */
    public void unsubscribeFromRoleChanges() {
        if (roleListener != null) {
            roleListener.remove();
            roleListener = null;
            Log.d(TAG, "🛑 Listener de rol removido");
        }
    }

    /**
     * Obtiene el rol actual en caché
     * IMPORTANTE: Debe llamarse loadUserRole() primero
     */
    public UserRole getCurrentUserRole() {
        return currentUserRole;
    }

    /**
     * Obtiene el UID del usuario actual
     */
    public String getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Establece manualmente el rol (útil para testing)
     */
    public void setCurrentUserRole(UserRole role) {
        this.currentUserRole = role;
    }

    /**
     * Verifica si el rol ya está cargado
     */
    public boolean isRoleLoaded() {
        return currentUserRole != null;
    }

    /**
     * ✅ Limpia el caché del rol
     * Útil al cerrar sesión
     */
    public void clearRole() {
        currentUserRole = UserRole.VISUALIZADOR;
        currentUserId = null;
        unsubscribeFromRoleChanges();
        Log.d(TAG, "🧹 Rol limpiado");
    }

    /**
     * Helper para verificar permisos rápidamente
     */
    public boolean canViewMaintainers() {
        return currentUserRole != null && currentUserRole.canViewMaintainers();
    }

    public boolean canModifyActivities() {
        return currentUserRole != null && currentUserRole.canModifyActivity();
    }

    public boolean isAdmin() {
        return currentUserRole != null && currentUserRole.isAdmin();
    }
}