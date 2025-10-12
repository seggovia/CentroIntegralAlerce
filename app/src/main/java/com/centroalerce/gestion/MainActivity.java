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

    // ✅ NUEVO: Sistema de roles
    private RoleManager roleManager;
    private PermissionChecker permissionChecker;
    private FirebaseAuth auth;
    private UserRole currentUserRole;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ NUEVO: Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance();

        // ✅ NUEVO: Verificar si hay usuario autenticado
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            // Si no hay usuario, redirigir al login
            // (esto debería estar manejado por el grafo de navegación)
            Log.d(TAG, "⚠️ No hay usuario autenticado");
        }

        // ✅ NUEVO: Inicializar sistema de roles
        initializeRoleSystem();

        // ✅ NUEVO: Ejecutar migración de roles (solo una vez)
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
            // ✅ MODIFICADO: Solo usuarios comunes y admins pueden crear actividades
            if (permissionChecker.checkAndNotify(this,
                    PermissionChecker.Permission.MODIFY_ACTIVITY)) {
                navController.navigate(R.id.activityFormFragment);
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

            // ✅ MODIFICADO: Mostrar FAB solo en CalendarFragment Y solo si tiene permisos
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
     * ✅ NUEVO: Inicializa el sistema de roles
     */
    private void initializeRoleSystem() {
        roleManager = RoleManager.getInstance();
        permissionChecker = new PermissionChecker();

        // Cargar el rol del usuario actual
        roleManager.loadUserRole(role -> {
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
     * ✅ NUEVO: Configura el menú de navegación según el rol del usuario
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
     * ✅ NUEVO: Ejecuta la migración de roles una sola vez
     * Esto normaliza roles antiguos ("admin" -> "administrador")
     * y asigna rol "usuario" a usuarios sin rol
     */
    private void ejecutarMigracionRolesUnaVez() {
        SharedPreferences prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        boolean migracionRealizada = prefs.getBoolean("migracion_roles_v1", false);

        if (!migracionRealizada) {
            Log.d(TAG, "🔄 Iniciando migración de roles...");

            MigracionRoles migracion = new MigracionRoles();

            // Primero normalizar roles existentes
            migracion.normalizarRolesExistentes(new MigracionRoles.OnMigrationListener() {
                @Override
                public void onComplete(int total, int actualizados) {
                    Log.d(TAG, "✅ Migración completada: " + actualizados + "/" + total + " usuarios actualizados");

                    // Marcar migración como realizada
                    prefs.edit().putBoolean("migracion_roles_v1", true).apply();

                    // Opcional: Mostrar mensaje al usuario
                    // Toast.makeText(MainActivity.this,
                    //     "Sistema de roles actualizado",
                    //     Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "❌ Error en migración de roles", e);
                }
            });
        } else {
            Log.d(TAG, "✅ Migración de roles ya ejecutada anteriormente");
        }
    }

    /**
     * ✅ NUEVO: Método para verificar roles actuales (debug)
     * Llama a este método si quieres ver qué roles existen en tu base de datos
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
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error al verificar roles", e);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ NUEVO: Recargar el rol por si cambió (ej: admin cambió permisos)
        if (roleManager != null) {
            roleManager.loadUserRole(role -> {
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
        // Limpiar recursos si es necesario
    }
}