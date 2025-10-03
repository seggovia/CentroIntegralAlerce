package com.centroalerce.gestion;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1) Obtener NavController desde el NavHostFragment (id: @id/nav_host)
        NavHostFragment navHost = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (navHost == null) {
            // Si esto pasara, el layout no tiene el id correcto o no infló
            throw new IllegalStateException("No se encontró NavHostFragment con id @id/nav_host");
        }
        NavController navController = Objects.requireNonNull(navHost).getNavController();

        // 2) Conectar BottomNavigationView con NavController
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Evitar re-navegar al re-seleccionar el mismo tab
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });

        // 3) Mostrar/ocultar BottomNav según destino
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();

            boolean hide =
                    id == R.id.loginFragment ||
                            id == R.id.signupFragment ||
                            id == R.id.activityFormFragment ||
                            id == R.id.activityEditFragment ||
                            id == R.id.activityRescheduleFragment ||
                            id == R.id.detalleActividadFragment;

            bottomNav.setVisibility(hide ? View.GONE : View.VISIBLE);
        });
    }
}
