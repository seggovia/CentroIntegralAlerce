package com.centroalerce.gestion;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.centroalerce.gestion.utils.MigracionRoles;
import com.centroalerce.gestion.utils.PermissionChecker;
import com.centroalerce.gestion.utils.RoleManager;
import com.centroalerce.gestion.utils.UserRole;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private FloatingActionButton fabGlobal;
    private NavController navController;
    private BottomNavigationView bottomNav;

    // ✅ Sistema de roles
    private RoleManager roleManager;
    private PermissionChecker permissionChecker;
    private FirebaseAuth auth;
    private UserRole currentUserRole;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance();

        // ✅ Verificar si hay usuario autenticado
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "⚠️ No hay usuario autenticado");
        }

        // ✅ Inicializar sistema de roles
        initializeRoleSystem();

        // ✅ IMPORTANTE: Ejecutar migración de roles (solo una vez)
        // Esto convierte TODOS los usuarios existentes en ADMINISTRADORES
        ejecutarMigracionRolesUnaVez();

        // 1) Obtener NavController desde el NavHostFragment
        NavHostFragment navHost = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (navHost == null) {
            throw new IllegalStateException("No se encontró NavHostFragment con id @id/nav_host");
        }
        navController = Objects.requireNonNull(navHost).getNavController();

        // 2) Conectar BottomNavigationView con NavController
        bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Evitar re-navegar al re-seleccionar el mismo tab
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });

        // 3) Obtener referencia al FAB global
        fabGlobal = findViewById(R.id.fabAddGlobal);

        // 4) Configurar click del FAB con validación de permisos
        fabGlobal.setOnClickListener(v -> {
            // Solo usuarios comunes y admins pueden crear actividades
            if (currentUserRole != null && currentUserRole.canInteractWithActivities()) {
                navController.navigate(R.id.activityFormFragment);
            } else {
                Toast.makeText(this, "No tienes permisos para crear actividades", Toast.LENGTH_SHORT).show();
            }
        });

        // 5) Mostrar/ocultar BottomNav y FAB según destino
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();

            // Pantallas donde se oculta el BottomNav
            boolean hideBottomNav =
                    id == R.id.loginFragment ||
                            id == R.id.signupFragment ||
                            id == R.id.contactSupportFragment ||
                            id == R.id.forgotPasswordFragment ||
                            id == R.id.activityFormFragment ||
                            id == R.id.activityRescheduleFragment ||
                            id == R.id.detalleActividadFragment ||
                            id == R.id.perfilFragment ||
                            id == R.id.maintainersFragment ||
                            id == R.id.tiposActividadFragment ||
                            id == R.id.lugaresFragment ||
                            id == R.id.oferentesFragment ||
                            id == R.id.sociosFragment ||
                            id == R.id.proyectosFragment;

            bottomNav.setVisibility(hideBottomNav ? View.GONE : View.VISIBLE);

            // Mostrar FAB solo en CalendarFragment Y solo si tiene permisos
            if (id == R.id.calendarFragment) {
                // Solo mostrar FAB si puede crear actividades
                if (currentUserRole != null && currentUserRole.canInteractWithActivities()) {
                    fabGlobal.show();
                } else {
                    fabGlobal.hide();
                }
            } else {
                fabGlobal.hide();
            }
        });
    }

    /**
     * ✅ Inicializa el sistema de roles
     */
    private void initializeRoleSystem() {
        roleManager = RoleManager.getInstance();
        permissionChecker = new PermissionChecker();

        // Cargar el rol del usuario actual
        roleManager.loadUserRole((RoleManager.OnRoleLoadedListener) role -> {
            currentUserRole = role;
            Log.d(TAG, "✅ Rol del usuario cargado: " + role.getValue());

            // Configurar el menú según el rol
            configureMenuByRole(role);

            // Actualizar visibilidad del FAB si estamos en el calendario
            if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() == R.id.calendarFragment) {
                if (role.canInteractWithActivities()) {
                    fabGlobal.show();
                } else {
                    fabGlobal.hide();
                }
            }
        });
    }

    /**
     * ✅ Configura el menú de navegación según el rol del usuario
     */
    private void configureMenuByRole(UserRole role) {
        if (bottomNav == null || bottomNav.getMenu() == null) {
            return;
        }

        Log.d(TAG, "🔧 Configurando menú para rol: " + role.getValue());

        // Todos ven el calendario
        if (bottomNav.getMenu().findItem(R.id.calendarFragment) != null) {
            bottomNav.getMenu().findItem(R.id.calendarFragment).setVisible(true);
        }

        // Todos ven configuración/ajustes
        if (bottomNav.getMenu().findItem(R.id.settingsFragment) != null) {
            bottomNav.getMenu().findItem(R.id.settingsFragment).setVisible(true);
        }

        // Solo admins ven mantenedores (si está en el menú)
        if (bottomNav.getMenu().findItem(R.id.maintainersFragment) != null) {
            bottomNav.getMenu().findItem(R.id.maintainersFragment)
                    .setVisible(role == UserRole.ADMINISTRADOR);
        }

        Log.d(TAG, "✅ Menú configurado correctamente");
    }

    /**
     * ✅ Ejecuta la migración de roles una sola vez
     * IMPORTANTE: Esto convierte TODOS los usuarios existentes en ADMINISTRADORES
     */
    private void ejecutarMigracionRolesUnaVez() {
        SharedPreferences prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        boolean migracionRealizada = prefs.getBoolean("migracion_roles_administrador_v1", false);

        if (!migracionRealizada) {
            Log.d(TAG, "🔄 Iniciando migración de roles...");
            Log.d(TAG, "⚠️ TODOS los usuarios existentes serán ADMINISTRADORES");

            MigracionRoles migracion = new MigracionRoles();

            // ✅ CORREGIDO: Usar el método correcto
            migracion.asignarAdministradorATodos(new MigracionRoles.OnMigrationListener() {
                @Override
                public void onComplete(int total, int actualizados) {
                    Log.d(TAG, "✅ Migración completada: " + actualizados + "/" + total + " usuarios son ahora ADMINISTRADORES");

                    // Marcar migración como realizada
                    prefs.edit().putBoolean("migracion_roles_administrador_v1", true).apply();

                    // Opcional: Mostrar mensaje al usuario
                    Toast.makeText(MainActivity.this,
                            "Sistema de roles actualizado. " + actualizados + " usuario(s) migrado(s).",
                            Toast.LENGTH_SHORT).show();

                    // Recargar el rol del usuario actual
                    if (roleManager != null) {
                        roleManager.loadUserRole((RoleManager.OnRoleLoadedListener) role -> {
                            currentUserRole = role;
                            configureMenuByRole(role);
                        });
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "❌ Error en migración de roles", e);
                    Toast.makeText(MainActivity.this,
                            "Error al migrar roles: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Log.d(TAG, "✅ Migración de roles ya ejecutada anteriormente");
        }
    }

    /**
     * ✅ Método para verificar roles actuales (debug)
     * Descomenta la llamada en onCreate si quieres ver qué roles existen
     */
    private void verificarRolesActuales() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("usuarios")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.Map<String, Integer> conteoRoles = new java.util.HashMap<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String rol = doc.getString("rol");
                        if (rol == null) rol = "sin_rol";
                        conteoRoles.put(rol, conteoRoles.getOrDefault(rol, 0) + 1);
                    }

                    Log.d(TAG, "📊 Distribución de roles en Firebase:");
                    for (java.util.Map.Entry<String, Integer> entry : conteoRoles.entrySet()) {
                        Log.d(TAG, "  " + entry.getKey() + ": " + entry.getValue() + " usuario(s)");
                    }

                    // Mostrar en un Toast también
                    StringBuilder mensaje = new StringBuilder("Roles en Firebase:\n");
                    for (java.util.Map.Entry<String, Integer> entry : conteoRoles.entrySet()) {
                        mensaje.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    }
                    Toast.makeText(this, mensaje.toString(), Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error al verificar roles", e);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Recargar el rol por si cambió (ej: admin cambió permisos)
        if (roleManager != null) {
            roleManager.loadUserRole((RoleManager.OnRoleLoadedListener) role -> {
                currentUserRole = role;
                configureMenuByRole(role);

                // Actualizar FAB si estamos en el calendario
                if (navController.getCurrentDestination() != null &&
                        navController.getCurrentDestination().getId() == R.id.calendarFragment) {
                    if (role.canInteractWithActivities()) {
                        fabGlobal.show();
                    } else {
                        fabGlobal.hide();
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Limpiar listener de roles
        if (roleManager != null) {
            roleManager.unsubscribeFromRoleChanges();
        }
    }
}