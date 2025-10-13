package com.centroalerce.gestion.utils;

import android.util.Log;
import com.centroalerce.gestion.utils.UserRole;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

/**
 * Clase para migrar usuarios existentes y asignarles roles
 * ⚠️ EJECUTAR SOLO UNA VEZ antes de implementar el sistema de roles
 * ⚠️ ESTA CLASE NO VERIFICA PERMISOS - Es para migración inicial
 *
 * DESPUÉS DE EJECUTAR LA MIGRACIÓN:
 * - Puedes BORRAR esta clase completa
 * - O dejar solo el método verificarRoles() para auditoría
 */
public class MigracionRoles {

    private static final String TAG = "MigracionRoles";
    private final FirebaseFirestore db;

    public MigracionRoles() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * ⚠️ MÉTODO PRINCIPAL DE MIGRACIÓN
     * Asigna el rol de ADMINISTRADOR a TODOS los usuarios existentes
     * Usar SOLO UNA VEZ al implementar el sistema de roles por primera vez
     *
     * @param listener Callback para recibir el resultado de la migración
     */
    public void asignarAdministradorATodos(OnMigrationListener listener) {
        Log.d(TAG, "=== INICIANDO MIGRACIÓN DE ROLES ===");
        Log.d(TAG, "Asignando ADMINISTRADOR a todos los usuarios...");

        db.collection("usuarios")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int total = querySnapshot.size();
                    int[] procesados = {0};
                    int[] actualizados = {0};

                    if (total == 0) {
                        Log.d(TAG, "⚠️ No hay usuarios para migrar");
                        listener.onComplete(0, 0);
                        return;
                    }

                    Log.d(TAG, "📊 Total de usuarios a procesar: " + total);

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String uid = document.getId();
                        String email = document.getString("email");
                        String rolActual = document.getString("rol");

                        // Asignar ADMINISTRADOR a todos (incluso si ya tienen un rol)
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("rol", "administrador");

                        db.collection("usuarios")
                                .document(uid)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    actualizados[0]++;
                                    procesados[0]++;

                                    String rolAnterior = (rolActual != null && !rolActual.isEmpty())
                                            ? rolActual
                                            : "sin_rol";

                                    Log.d(TAG, String.format(
                                            "✅ [%d/%d] Usuario migrado: %s | %s -> administrador",
                                            procesados[0], total, email, rolAnterior
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
                                            "❌ [%d/%d] Error al migrar usuario: %s",
                                            procesados[0], total, email
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
                    Log.e(TAG, "❌ ERROR CRÍTICO: No se pudo obtener usuarios de Firebase", e);
                    listener.onError(e);
                });
    }

    /**
     * Verifica el estado actual de los roles en la base de datos
     * Útil para:
     * - Confirmar que la migración fue exitosa
     * - Auditoría de roles
     * - Debugging
     *
     * ✅ ESTE MÉTODO PUEDES MANTENERLO después de la migración
     *
     * @param listener Callback con el conteo de roles
     */
    public void verificarRoles(OnVerificationListener listener) {
        Log.d(TAG, "🔍 Verificando distribución de roles...");

        db.collection("usuarios")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, Integer> conteoRoles = new HashMap<>();
                    conteoRoles.put("administrador", 0);
                    conteoRoles.put("usuario", 0);
                    conteoRoles.put("visualizador", 0);
                    conteoRoles.put("sin_rol", 0);

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String rol = document.getString("rol");
                        String email = document.getString("email");
                        String uid = document.getId();

                        if (rol == null || rol.isEmpty()) {
                            conteoRoles.put("sin_rol", conteoRoles.get("sin_rol") + 1);
                            Log.d(TAG, "⚠️ Usuario SIN ROL: " + email + " (" + uid + ")");
                        } else {
                            conteoRoles.put(rol, conteoRoles.getOrDefault(rol, 0) + 1);
                            Log.d(TAG, "✓ " + email + " -> Rol: " + rol);
                        }
                    }

                    Log.d(TAG, "=== RESUMEN DE ROLES ===");
                    Log.d(TAG, "👑 Administradores: " + conteoRoles.get("administrador"));
                    Log.d(TAG, "👤 Usuarios Comunes: " + conteoRoles.get("usuario"));
                    Log.d(TAG, "👁️ Visualizadores: " + conteoRoles.get("visualizador"));
                    Log.d(TAG, "❓ Sin rol: " + conteoRoles.get("sin_rol"));
                    Log.d(TAG, "📊 Total: " + querySnapshot.size());

                    listener.onVerificationComplete(conteoRoles);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error al verificar roles", e);
                    listener.onVerificationError(e);
                });
    }

    /**
     * Interface para escuchar el resultado de la migración
     */
    public interface OnMigrationListener {
        /**
         * Se ejecuta cuando la migración se completa
         * @param totalProcesados Número total de usuarios procesados
         * @param totalActualizados Número de usuarios actualizados exitosamente
         */
        void onComplete(int totalProcesados, int totalActualizados);

        /**
         * Se ejecuta si hay un error crítico en la migración
         * @param e La excepción que causó el error
         */
        void onError(Exception e);
    }

    /**
     * Interface para escuchar el resultado de la verificación
     */
    public interface OnVerificationListener {
        /**
         * Se ejecuta cuando la verificación se completa
         * @param conteoRoles Map con el conteo de cada rol
         */
        void onVerificationComplete(Map<String, Integer> conteoRoles);

        /**
         * Se ejecuta si hay un error en la verificación
         * @param e La excepción que causó el error
         */
        void onVerificationError(Exception e);
    }
}