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
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.centroalerce.gestion.utils.PermissionChecker;
import com.centroalerce.gestion.utils.RoleManager;
import com.centroalerce.gestion.utils.UserRole;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
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

    // Flag para evitar loops infinitos al sincronizar el bottom nav
    private boolean isUpdatingBottomNav = false;

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

        // ✅ Inicializar Firebase App Check - TEMPORALMENTE DESACTIVADO PARA DEBUG
        FirebaseApp.initializeApp(this);
        // FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        // Usar Debug provider en desarrollo (BuildConfig.DEBUG)
        // Cambiar a Play Integrity para producción
        // if (BuildConfig.DEBUG) {
        //     firebaseAppCheck.installAppCheckProviderFactory(
        //             DebugAppCheckProviderFactory.getInstance());
        //     Log.d(TAG, "✅ App Check inicializado con DEBUG provider");
        // } else {
        //     firebaseAppCheck.installAppCheckProviderFactory(
        //             PlayIntegrityAppCheckProviderFactory.getInstance());
        //     Log.d(TAG, "✅ App Check inicializado con Play Integrity");
        // }
        Log.d(TAG, "⚠️ App Check DESACTIVADO temporalmente para debug");

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

        // Evitar re-navegar al re-seleccionar el mismo tab
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });

        // Interceptar clicks en el calendario (item central invisible - manejado por FAB)
        bottomNav.setOnItemSelectedListener(item -> {
            // Si estamos actualizando programáticamente, ignorar
            if (isUpdatingBottomNav) {
                return true;
            }

            int itemId = item.getItemId();

            if (itemId == R.id.calendarFragment) {
                return false; // Ignorar clicks en el item de calendario (lo maneja el FAB)
            }

            // Navegar con animaciones profesionales
            NavOptions navOptions = new NavOptions.Builder()
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopEnterAnim(R.anim.fade_in)
                    .setPopExitAnim(R.anim.fade_out)
                    .build();

            try {
                navController.navigate(itemId, null, navOptions);
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        // 3) Obtener referencia a los FABs
        fabGlobal = findViewById(R.id.fabAddGlobal);
        fabCalendar = findViewById(R.id.fabCalendar);

        // 4) ✅ Configurar click del FAB "+" CON VALIDACIÓN DE PERMISOS
        fabGlobal.setOnClickListener(v -> {
            // Solo usuarios comunes y admins pueden crear actividades
            if (currentUserRole != null && currentUserRole.canInteractWithActivities()) {
                // Navegar con animaciones slide desde la izquierda
                NavOptions navOptions = new NavOptions.Builder()
                        .setEnterAnim(R.anim.slide_in_left)
                        .setExitAnim(R.anim.slide_out_right)
                        .setPopEnterAnim(R.anim.slide_in_right)
                        .setPopExitAnim(R.anim.slide_out_left)
                        .build();
                navController.navigate(R.id.activityFormFragment, null, navOptions);
            } else {
                Toast.makeText(this, "No tienes permisos para crear actividades", Toast.LENGTH_SHORT).show();
            }
        });

        // 5) Configurar click del FAB circular del calendario
        fabCalendar.setOnClickListener(v -> {
            // Navegar con animaciones fade
            NavOptions navOptions = new NavOptions.Builder()
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopEnterAnim(R.anim.fade_in)
                    .setPopExitAnim(R.anim.fade_out)
                    .build();
            navController.navigate(R.id.calendarFragment, null, navOptions);
        });

        // ✅ Inicializar sistema de roles ANTES de los listeners (TU CÓDIGO)
        initializeRoleSystem();

        // 5) Mostrar/ocultar BottomNav y FAB según destino
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();

            // Sincronizar el estado del BottomNavigationView con el destino actual
            if (id == R.id.activitiesListFragment || id == R.id.calendarFragment || id == R.id.settingsFragment) {
                isUpdatingBottomNav = true;
                bottomNav.setSelectedItemId(id);
                isUpdatingBottomNav = false;
            }

            // Pantallas donde se oculta el BottomNav
            boolean hideBottomNav =
                    id == R.id.splashFragment ||
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
                // Mostrar por defecto mientras se carga el rol, solo ocultar si es VISUALIZADOR
                if (currentUserRole == null) {
                    // Rol aún no cargado, mostrar FAB por defecto (optimista)
                    Log.d(TAG, "⚠️ FAB: Rol null, mostrando optimistamente");
                    fabGlobal.show();
                } else if (currentUserRole.canInteractWithActivities()) {
                    Log.d(TAG, "✅ FAB: Mostrando (rol: " + currentUserRole.getValue() + ")");
                    fabGlobal.show(); // ✅ Usuario/Admin pueden crear
                } else {
                    Log.d(TAG, "❌ FAB: Ocultando (rol: " + currentUserRole.getValue() + ")");
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
        Log.d(TAG, "🔧 initializeRoleSystem() llamado");
        roleManager = RoleManager.getInstance();
        permissionChecker = new PermissionChecker();

        // Usar listener en tiempo real en lugar de get() único
        // Esto asegura que obtenemos actualizaciones cuando el rol está disponible
        Log.d(TAG, "📞 Suscribiéndose a cambios de rol en tiempo real...");
        roleManager.subscribeToRoleChanges(new RoleManager.OnRoleLoadedListener() {
            @Override
            public void onRoleLoaded(UserRole role) {
                Log.d(TAG, "🎯 LISTENER EJECUTADO en MainActivity! (desde subscribeToRoleChanges)");
                currentUserRole = role;
                Log.d(TAG, "✅ Rol del usuario cargado en MainActivity: " + role.getValue());

                // Configurar el menú según el rol
                configureMenuByRole(role);

                // Actualizar visibilidad del FAB si ya estamos en CalendarFragment o ActivitiesListFragment
                runOnUiThread(() -> {
                    Log.d(TAG, "🔄 Actualizando FAB después de cargar rol: " + role.getValue());
                    Log.d(TAG, "   navControllerReady: " + navControllerReady);

                    if (navControllerReady && navController != null && navController.getCurrentDestination() != null) {
                        int currentDestination = navController.getCurrentDestination().getId();
                        Log.d(TAG, "   currentDestination: " + currentDestination);

                        if (currentDestination == R.id.calendarFragment || currentDestination == R.id.activitiesListFragment) {
                            if (role.canInteractWithActivities()) {
                                Log.d(TAG, "   ✅ Mostrando FAB (puede interactuar)");
                                fabGlobal.show();
                            } else {
                                Log.d(TAG, "   ❌ Ocultando FAB (no puede interactuar)");
                                fabGlobal.hide();
                            }
                        } else {
                            Log.d(TAG, "   ℹ️ No estamos en Calendar/Activities, ignorando");
                        }
                    } else {
                        Log.w(TAG, "   ⚠️ NavController no listo o destino null");
                    }
                });
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

        // ✅ Detectar si cambió el usuario y reinicializar sistema de roles
        if (roleManager != null) {
            com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String currentUid = roleManager.getCurrentUserId();

            // Si hay un usuario autenticado y es diferente al que tenemos en caché
            if (currentUser != null && (currentUid == null || !currentUser.getUid().equals(currentUid))) {
                Log.d(TAG, "🔄 Usuario cambió (anterior: " + currentUid + ", nuevo: " + currentUser.getUid() + ")");
                Log.d(TAG, "🔄 Reinicializando sistema de roles...");

                // Limpiar el rol anterior
                roleManager.clearRole();

                // Reinicializar para el nuevo usuario
                initializeRoleSystem();
            } else if (currentUser == null && currentUid != null) {
                // Se cerró sesión, limpiar rol
                Log.d(TAG, "🔄 Sesión cerrada, limpiando roles...");
                roleManager.clearRole();
                currentUserRole = null;
            } else {
                // Usuario no cambió, solo recargar rol por si cambió
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
