package com.centroalerce.gestion;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

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
    private FloatingActionButton fabCalendar;
    private NavController navController;
    private BottomNavigationView bottomNav;

    // ✅ Sistema de roles (TU CÓDIGO)
    private RoleManager roleManager;
    private PermissionChecker permissionChecker;
    private FirebaseAuth auth;
    private UserRole currentUserRole;
    private boolean navControllerReady = false;

    // Control de navegación para evitar clicks múltiples durante animaciones
    private boolean isNavigating = false;
    private static final long NAVIGATION_DELAY = 220; // Duración de la animación nativa + margen

    // 🆕 Sistema de notificaciones (CÓDIGO DE TU COMPAÑERO)
    private final ActivityResultLauncher<String> requestNotifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    mostrarDialogoIrAjustes();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ Inicializar Firebase Auth (TU CÓDIGO)
        auth = FirebaseAuth.getInstance();

        // ✅ Verificar usuario autenticado (TU CÓDIGO)
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "⚠️ No hay usuario autenticado");
        }

        // 🆕 Pedir permiso de notificaciones (CÓDIGO DE TU COMPAÑERO)
        asegurarPermisoNotificaciones();

        // 1) Obtener NavController desde el NavHostFragment
        NavHostFragment navHost = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (navHost == null) {
            throw new IllegalStateException("No se encontró NavHostFragment con id @id/nav_host");
        }
        navController = Objects.requireNonNull(navHost).getNavController();
        navControllerReady = true;

        // 2) Conectar BottomNavigationView con NavController
        bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Evitar re-navegar al re-seleccionar el mismo tab
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });

        // Interceptar clicks en el calendario (item central invisible - manejado por FAB)
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.calendarFragment) {
                return false; // Ignorar clicks en el item de calendario (lo maneja el FAB)
            }
            // Permitir navegación normal para otros items
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // 3) Obtener referencia a los FABs
        fabGlobal = findViewById(R.id.fabAddGlobal);
        fabCalendar = findViewById(R.id.fabCalendar);

        // 4) ✅ Configurar click del FAB "+" CON VALIDACIÓN DE PERMISOS
        fabGlobal.setOnClickListener(v -> {
            // Solo usuarios comunes y admins pueden crear actividades
            if (currentUserRole != null && currentUserRole.canInteractWithActivities()) {
                navController.navigate(R.id.activityFormFragment);
            } else {
                Toast.makeText(this, "No tienes permisos para crear actividades", Toast.LENGTH_SHORT).show();
            }
        });

        // 5) Configurar click del FAB circular del calendario
        fabCalendar.setOnClickListener(v -> {
            navController.navigate(R.id.calendarFragment);
        });

        // ✅ Inicializar sistema de roles ANTES de los listeners (TU CÓDIGO)
        initializeRoleSystem();

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
                            id == R.id.registroActividadesFragment ||
                            id == R.id.gestionUsuariosFragment ||
                            id == R.id.maintainersFragment ||
                            id == R.id.tiposActividadFragment ||
                            id == R.id.lugaresFragment ||
                            id == R.id.oferentesFragment ||
                            id == R.id.sociosFragment ||
                            id == R.id.proyectosFragment ||
                            id == R.id.beneficiariosFragment;

            bottomNav.setVisibility(hideBottomNav ? View.GONE : View.VISIBLE);

            // Mostrar/ocultar FAB del calendario (solo visible cuando el BottomNav está visible)
            fabCalendar.setVisibility(hideBottomNav ? View.GONE : View.VISIBLE);

            // ✅ LÓGICA MEJORADA: Mostrar FAB "+" en CalendarFragment y ActivitiesListFragment
            // pero SOLO si el usuario tiene permisos
            if (id == R.id.calendarFragment || id == R.id.activitiesListFragment) {
                // Verificar permisos antes de mostrar
                if (currentUserRole != null && currentUserRole.canInteractWithActivities()) {
                    fabGlobal.show(); // ✅ Usuario/Admin pueden crear
                } else {
                    fabGlobal.hide(); // ❌ Visualizador no puede crear
                }
            } else {
                fabGlobal.hide(); // Ocultar en otros fragments
            }
        });
    }

    // ✅ ==================== MÉTODOS DE ROLES (TU CÓDIGO) ====================

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

            // Actualizar visibilidad del FAB si ya estamos en CalendarFragment o ActivitiesListFragment
            runOnUiThread(() -> {
                if (navControllerReady && navController.getCurrentDestination() != null) {
                    int currentDestination = navController.getCurrentDestination().getId();
                    if (currentDestination == R.id.calendarFragment || currentDestination == R.id.activitiesListFragment) {
                        if (role.canInteractWithActivities()) {
                            fabGlobal.show();
                        } else {
                            fabGlobal.hide();
                        }
                    }
                }
            });
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
     * ✅ Método para verificar roles actuales (debug)
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

    // 🆕 ==================== MÉTODOS DE NOTIFICACIONES (CÓDIGO DE TU COMPAÑERO) ====================

    /**
     * 🆕 Pedir permiso de notificaciones en Android 13+
     */
    private void asegurarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
            if (!enabled) {
                mostrarDialogoIrAjustes();
            }
        }
    }

    /**
     * 🆕 Abrir ajustes si el usuario niega el permiso
     */
    private void mostrarDialogoIrAjustes() {
        new AlertDialog.Builder(this)
                .setTitle("Permitir notificaciones")
                .setMessage("Para recibir recordatorios, activa las notificaciones de la aplicación.")
                .setPositiveButton("Abrir ajustes", (d, w) -> abrirAjustesNotificaciones())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * 🆕 Dirigir al usuario a la pantalla de ajustes
     */
    private void abrirAjustesNotificaciones() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }

    // ✅ ==================== MÉTODOS DE LIFECYCLE ====================

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ Recargar el rol por si cambió (TU CÓDIGO)
        if (roleManager != null) {
            roleManager.loadUserRole((RoleManager.OnRoleLoadedListener) role -> {
                currentUserRole = role;
                configureMenuByRole(role);

                // Verificar que navController esté listo
                if (navControllerReady && navController.getCurrentDestination() != null) {
                    int currentDestId = navController.getCurrentDestination().getId();
                    if (currentDestId == R.id.calendarFragment || currentDestId == R.id.activitiesListFragment) {
                        if (role.canInteractWithActivities()) {
                            fabGlobal.show();
                        } else {
                            fabGlobal.hide();
                        }
                    }
                }
            });
        }
    }

    /**
     * Determina si debemos animar hacia la derecha basado en el orden de las pestañas
     * Orden: Actividades (izq) -> Calendario (centro) -> Configuración (der)
     */
    private boolean shouldMoveRight(int currentDestId, int newDestId) {
        // Orden de los items en el bottom nav
        int currentOrder = getDestinationOrder(currentDestId);
        int newOrder = getDestinationOrder(newDestId);
        return newOrder > currentOrder;
    }

    /**
     * Obtiene el orden de la vista en el bottom navigation
     */
    private int getDestinationOrder(int destId) {
        if (destId == R.id.activitiesListFragment) return 0;
        if (destId == R.id.calendarFragment) return 1;
        if (destId == R.id.settingsFragment) return 2;
        return 1; // Default al centro
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ✅ Limpiar listener de roles (TU CÓDIGO)
        if (roleManager != null) {
            roleManager.unsubscribeFromRoleChanges();
        }
    }
}
