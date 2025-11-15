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
    private boolean isFirstSnapshot = true; // Para ejecutar listener en primer snapshot

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
        Log.d(TAG, "🔧 loadUserRole() llamado con listener: " + listener.getClass().getName());
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Log.w(TAG, "⚠️ No hay usuario autenticado");
            currentUserRole = UserRole.VISUALIZADOR;
            currentUserId = null;
            Log.d(TAG, "📞 Invocando listener.onRoleLoaded(VISUALIZADOR) - sin usuario");
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
                    Log.d(TAG, "✅ Documento obtenido de Firestore, exists: " + document.exists());
                    if (document.exists()) {
                        String rolString = document.getString("rol");
                        Log.d(TAG, "   Rol raw del documento: '" + rolString + "'");
                        currentUserRole = UserRole.fromString(rolString);

                        Log.d(TAG, "✅ Rol cargado: " + currentUserRole.getValue());
                        Log.d(TAG, "📞 Invocando listener.onRoleLoaded(" + currentUserRole.getValue() + ")");
                        listener.onRoleLoaded(currentUserRole);
                        Log.d(TAG, "✅ listener.onRoleLoaded() completado");
                    } else {
                        Log.w(TAG, "⚠️ Documento de usuario no existe, asignando VISUALIZADOR");
                        currentUserRole = UserRole.VISUALIZADOR;
                        Log.d(TAG, "📞 Invocando listener.onRoleLoaded(VISUALIZADOR) - doc no existe");
                        listener.onRoleLoaded(currentUserRole);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error al cargar rol: " + e.getMessage(), e);
                    currentUserRole = UserRole.VISUALIZADOR; // Fallback seguro
                    Log.d(TAG, "📞 Invocando listener.onRoleLoaded(VISUALIZADOR) - error");
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
     * IMPORTANTE: Ejecuta el listener inmediatamente cuando obtiene el primer snapshot
     */
    public void subscribeToRoleChanges(@NonNull OnRoleLoadedListener listener) {
        Log.d(TAG, "🔧 subscribeToRoleChanges() llamado con listener: " + listener.getClass().getName());
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Log.w(TAG, "⚠️ No hay usuario para suscribirse a cambios");
            currentUserRole = UserRole.VISUALIZADOR;
            listener.onRoleLoaded(currentUserRole);
            return;
        }

        String uid = currentUser.getUid();
        currentUserId = uid;
        Log.d(TAG, "🔍 Suscribiéndose a cambios de rol para usuario: " + uid);

        // Remover listener anterior si existe
        if (roleListener != null) {
            Log.d(TAG, "🧹 Removiendo listener anterior");
            roleListener.remove();
        }

        // Resetear flag de primer snapshot
        isFirstSnapshot = true;

        roleListener = db.collection("usuarios")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "❌ Error en listener de rol: " + error.getMessage(), error);
                        currentUserRole = UserRole.VISUALIZADOR;
                        Log.d(TAG, "📞 Invocando listener.onRoleLoaded(VISUALIZADOR) - error en snapshot");
                        listener.onRoleLoaded(currentUserRole);
                        isFirstSnapshot = false;
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        String rolString = snapshot.getString("rol");
                        Log.d(TAG, "📸 Snapshot recibido, rol raw: '" + rolString + "', isFirstSnapshot: " + isFirstSnapshot);
                        UserRole newRole = UserRole.fromString(rolString);

                        // ✅ Ejecutar listener SIEMPRE en el primer snapshot O cuando el rol cambie
                        if (isFirstSnapshot || newRole != currentUserRole) {
                            Log.d(TAG, "🔄 Rol " + (isFirstSnapshot ? "inicial" : "actualizado") + ": " + currentUserRole.getValue() + " → " + newRole.getValue());
                            currentUserRole = newRole;
                            Log.d(TAG, "📞 Invocando listener.onRoleLoaded(" + newRole.getValue() + ")");
                            listener.onRoleLoaded(currentUserRole);
                            Log.d(TAG, "✅ listener.onRoleLoaded() completado");
                            isFirstSnapshot = false;
                        } else {
                            Log.d(TAG, "ℹ️ Rol sin cambios: " + currentUserRole.getValue() + ", no ejecutando listener");
                        }
                    } else {
                        Log.w(TAG, "⚠️ Snapshot no existe o es null");
                        currentUserRole = UserRole.VISUALIZADOR;
                        Log.d(TAG, "📞 Invocando listener.onRoleLoaded(VISUALIZADOR) - snapshot no existe");
                        listener.onRoleLoaded(currentUserRole);
                        isFirstSnapshot = false;
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