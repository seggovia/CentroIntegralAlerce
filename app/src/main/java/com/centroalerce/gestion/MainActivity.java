package com.centroalerce.gestion;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1) Obtener NavController desde el NavHostFragment
        NavHostFragment navHost = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (navHost == null) return; // safety
        NavController navController = navHost.getNavController();

        // 2) Conectar BottomNavigationView con NavController
        BottomNavigationView bottom = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottom, navController);

        // 3) Ocultar BottomNav en login
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.loginFragment) {
                bottom.setVisibility(View.GONE);
            } else {
                bottom.setVisibility(View.VISIBLE);
            }
        });
    }
}
