package com.centroalerce.gestion;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton fabGlobal;
    private NavController navController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
}