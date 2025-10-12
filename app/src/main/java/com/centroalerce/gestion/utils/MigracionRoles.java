package com.centroalerce.gestion.utils;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

/**
 * Clase para migrar usuarios existentes y asignarles roles
 * EJECUTAR SOLO UNA VEZ antes de implementar el sistema de roles
 */
public class MigracionRoles {

    private static final String TAG = "MigracionRoles";
    private final FirebaseFirestore db;

    public MigracionRoles() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Asigna el rol de ADMINISTRADOR a todos los usuarios existentes
     * Usar SOLO UNA VEZ para migrar usuarios creados antes del sistema de roles
     */
    public void asignarAdministradorATodos(OnMigrationListener listener) {
        Log.d(TAG, "Iniciando migración: asignando ADMINISTRADOR a todos los usuarios...");

        db.collection("usuarios")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int total = querySnapshot.size();
                    int[] procesados = {0};
                    int[] actualizados = {0};

                    if (total == 0) {
                        Log.d(TAG, "No hay usuarios para migrar");
                        listener.onComplete(0, 0);
                        return;
                    }

                    Log.d(TAG, "Total de usuarios a procesar: " + total);

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String uid = document.getId();
                        String email = document.getString("email");
                        String rolActual = document.getString("rol");

                        // Siempre asignar administrador (incluso si ya tiene un rol)
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("rol", UserRole.ADMINISTRADOR.getValue());

                        db.collection("usuarios")
                                .document(uid)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    actualizados[0]++;
                                    procesados[0]++;
                                    Log.d(TAG, String.format(
                                            "✓ Usuario migrado [%d/%d]: %s (%s) | Rol anterior: %s -> Nuevo rol: %s",
                                            procesados[0], total, email, uid,
                                            rolActual != null ? rolActual : "sin rol",
                                            UserRole.ADMINISTRADOR.getValue()
                                    ));

                                    if (procesados[0] == total) {
                                        Log.d(TAG, "=== MIGRACIÓN COMPLETADA ===");
                                        Log.d(TAG, "Total procesados: " + total);
                                        Log.d(TAG, "Total actualizados: " + actualizados[0]);
                                        listener.onComplete(total, actualizados[0]);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    procesados[0]++;
                                    Log.e(TAG, String.format(
                                            "✗ Error al migrar usuario [%d/%d]: %s (%s)",
                                            procesados[0], total, email, uid
                                    ), e);

                                    if (procesados[0] == total) {
                                        Log.d(TAG, "=== MIGRACIÓN COMPLETADA CON ERRORES ===");
                                        Log.d(TAG, "Total procesados: " + total);
                                        Log.d(TAG, "Total actualizados: " + actualizados[0]);
                                        Log.d(TAG, "Total fallidos: " + (total - actualizados[0]));
                                        listener.onComplete(total, actualizados[0]);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error crítico al obtener usuarios de Firebase", e);
                    listener.onError(e);
                });
    }

    /**
     * Verifica el estado de los roles en la base de datos
     * Útil para confirmar que la migración fue exitosa
     */
    public void verificarRoles(OnVerificationListener listener) {
        Log.d(TAG, "Verificando roles de usuarios...");

        db.collection("usuarios")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, Integer> conteoRoles = new HashMap<>();
                    conteoRoles.put(UserRole.ADMINISTRADOR.getValue(), 0);
                    conteoRoles.put(UserRole.USUARIO.getValue(), 0);
                    conteoRoles.put(UserRole.VISUALIZADOR.getValue(), 0);
                    conteoRoles.put("sin_rol", 0);

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String rol = document.getString("rol");
                        String email = document.getString("email");

                        if (rol == null || rol.isEmpty()) {
                            conteoRoles.put("sin_rol", conteoRoles.get("sin_rol") + 1);
                            Log.d(TAG, "Usuario SIN ROL: " + email);
                        } else {
                            conteoRoles.put(rol, conteoRoles.getOrDefault(rol, 0) + 1);
                            Log.d(TAG, "Usuario: " + email + " -> Rol: " + rol);
                        }
                    }

                    Log.d(TAG, "=== RESUMEN DE ROLES ===");
                    Log.d(TAG, "Administradores: " + conteoRoles.get(UserRole.ADMINISTRADOR.getValue()));
                    Log.d(TAG, "Usuarios: " + conteoRoles.get(UserRole.USUARIO.getValue()));
                    Log.d(TAG, "Visualizadores: " + conteoRoles.get(UserRole.VISUALIZADOR.getValue()));
                    Log.d(TAG, "Sin rol: " + conteoRoles.get("sin_rol"));

                    listener.onVerificationComplete(conteoRoles);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al verificar roles", e);
                    listener.onVerificationError(e);
                });
    }

    /**
     * Interface para escuchar el resultado de la migración
     */
    public interface OnMigrationListener {
        void onComplete(int totalProcesados, int totalActualizados);
        void onError(Exception e);
    }

    /**
     * Interface para escuchar el resultado de la verificación
     */
    public interface OnVerificationListener {
        void onVerificationComplete(Map<String, Integer> conteoRoles);
        void onVerificationError(Exception e);
    }
}