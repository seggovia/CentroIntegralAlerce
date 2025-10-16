package com.centroalerce.gestion;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton fabGlobal;
    private NavController navController;

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

        // 🆕 NUEVO: pedir permiso de notificaciones
        asegurarPermisoNotificaciones();

        // 1) Obtener NavController desde el NavHostFragment
        NavHostFragment navHost = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (navHost == null) {
            throw new IllegalStateException("No se encontró NavHostFragment con id @id/nav_host");
        }
        navController = Objects.requireNonNull(navHost).getNavController();

        // 2) Conectar BottomNavigationView con NavController
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Evitar re-navegar al re-seleccionar el mismo tab
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });

        // 3) Obtener referencia al FAB global
        fabGlobal = findViewById(R.id.fabAddGlobal);

        // 4) Configurar click del FAB
        fabGlobal.setOnClickListener(v -> {
            // Navegar a crear actividad
            navController.navigate(R.id.activityFormFragment);
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

            // 🆕 Mostrar FAB SOLO en CalendarFragment
            if (id == R.id.calendarFragment) {
                fabGlobal.show(); // Animación de entrada
            } else {
                fabGlobal.hide(); // Animación de salida
            }
        });
    }

    // 🆕 NUEVO: pedir permiso de notificaciones en Android 13+
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

    // 🆕 NUEVO: abrir ajustes si el usuario niega el permiso
    private void mostrarDialogoIrAjustes() {
        new AlertDialog.Builder(this)
                .setTitle("Permitir notificaciones")
                .setMessage("Para recibir recordatorios, activa las notificaciones de la aplicación.")
                .setPositiveButton("Abrir ajustes", (d, w) -> abrirAjustesNotificaciones())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // 🆕 NUEVO: dirigir al usuario a la pantalla de ajustes
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
}
