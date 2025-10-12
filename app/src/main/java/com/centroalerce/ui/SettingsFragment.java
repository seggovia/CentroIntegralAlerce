package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.utils.PermissionChecker;
import com.centroalerce.gestion.utils.RoleManager;
import com.centroalerce.gestion.utils.UserRole;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {

    private MaterialCardView cardMantenedores;
    private MaterialCardView cardPerfil;
    private MaterialCardView cardCerrarSesion;

    // ✅ NUEVO: Sistema de roles
    private PermissionChecker permissionChecker;
    private RoleManager roleManager;
    private FirebaseAuth auth;
    private UserRole currentUserRole;

    // ✅ NUEVO: TextView para mostrar información del usuario (opcional)
    private TextView tvUserInfo;
    private TextView tvUserRole;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ✅ NUEVO: Inicializar componentes
        initializeComponents();

        // Inicializar vistas
        initializeViews(view);

        // ✅ NUEVO: Cargar rol y configurar UI
        loadUserRoleAndConfigureUI();

        // Configurar listeners
        setupListeners();
    }

    /**
     * ✅ NUEVO: Inicializa componentes del sistema de roles
     */
    private void initializeComponents() {
        permissionChecker = new PermissionChecker();
        roleManager = RoleManager.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Inicializa las vistas del fragment
     */
    private void initializeViews(View view) {
        cardMantenedores = view.findViewById(R.id.cardMantenedores);
        cardPerfil = view.findViewById(R.id.cardPerfil);
        cardCerrarSesion = view.findViewById(R.id.cardCerrarSesion);

        // ✅ NUEVO: Opcional - TextViews para mostrar info del usuario
        // (Si tienes estos en tu layout, descomentar)
        // tvUserInfo = view.findViewById(R.id.tvUserInfo);
        // tvUserRole = view.findViewById(R.id.tvUserRole);
    }

    /**
     * ✅ NUEVO: Carga el rol del usuario y configura la UI según permisos
     */
    private void loadUserRoleAndConfigureUI() {
        roleManager.loadUserRole(role -> {
            currentUserRole = role;
            android.util.Log.d("SettingsFragment", "✅ Rol cargado: " + role.getValue());

            // Configurar UI según el rol
            configureUIByRole(role);

            // ✅ NUEVO: Mostrar información del usuario (opcional)
            displayUserInfo(role);
        });
    }

    /**
     * ✅ NUEVO: Configura la visibilidad de las opciones según el rol
     */
    private void configureUIByRole(UserRole role) {
        // REGLA: Solo ADMINISTRADORES pueden ver Mantenedores
        if (cardMantenedores != null) {
            if (role == UserRole.ADMINISTRADOR) {
                cardMantenedores.setVisibility(View.VISIBLE);
                android.util.Log.d("SettingsFragment", "✅ Mostrando Mantenedores (Admin)");
            } else {
                cardMantenedores.setVisibility(View.GONE);
                android.util.Log.d("SettingsFragment", "🚫 Ocultando Mantenedores (No admin)");
            }
        }

        // Perfil: Todos pueden verlo
        if (cardPerfil != null) {
            cardPerfil.setVisibility(View.VISIBLE);
        }

        // Cerrar sesión: Todos pueden verlo
        if (cardCerrarSesion != null) {
            cardCerrarSesion.setVisibility(View.VISIBLE);
        }
    }

    /**
     * ✅ NUEVO: Muestra información del usuario en la UI (opcional)
     */
    private void displayUserInfo(UserRole role) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Si tienes TextViews para mostrar info del usuario
        // if (tvUserInfo != null) {
        //     tvUserInfo.setText(user.getEmail());
        // }
        //
        // if (tvUserRole != null) {
        //     String roleName = getRoleDisplayName(role);
        //     tvUserRole.setText("Rol: " + roleName);
        // }
    }

    /**
     * ✅ NUEVO: Obtiene el nombre del rol para mostrar
     */
    private String getRoleDisplayName(UserRole role) {
        switch (role) {
            case ADMINISTRADOR:
                return "Administrador";
            case USUARIO:
                return "Usuario Común";
            case VISUALIZADOR:
                return "Visualizador";
            default:
                return "Usuario";
        }
    }

    /**
     * Configura los listeners de los botones
     */
    private void setupListeners() {
        // Perfil - Todos pueden acceder
        if (cardPerfil != null) {
            cardPerfil.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_perfilFragment)
            );
        }

        // ✅ MODIFICADO: Mantenedores - Solo administradores
        if (cardMantenedores != null) {
            cardMantenedores.setOnClickListener(v -> {
                // Doble verificación de permisos
                if (permissionChecker.checkAndNotify(getContext(),
                        PermissionChecker.Permission.VIEW_MAINTAINERS)) {
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_maintainersFragment);
                }
            });
        }

        // ✅ MODIFICADO: Cerrar sesión con diálogo de confirmación
        if (cardCerrarSesion != null) {
            cardCerrarSesion.setOnClickListener(v -> mostrarDialogoCerrarSesion());
        }
    }

    /**
     * ✅ NUEVO: Muestra diálogo de confirmación para cerrar sesión
     */
    private void mostrarDialogoCerrarSesion() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro de que deseas cerrar sesión?")
                .setPositiveButton("Cerrar sesión", (dialog, which) -> {
                    cerrarSesion();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * ✅ NUEVO: Cierra la sesión y limpia los datos
     */
    private void cerrarSesion() {
        // Limpiar rol almacenado en memoria
        roleManager.clearRole();

        // Cerrar sesión en Firebase
        auth.signOut();

        android.util.Log.d("SettingsFragment", "✅ Sesión cerrada");

        // Navegar al login
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_settingsFragment_to_loginFragment);

        // Opcional: Mostrar mensaje
        Toast.makeText(getContext(), "Sesión cerrada exitosamente", Toast.LENGTH_SHORT).show();

        // Limpiar back stack para que no pueda volver atrás
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // ✅ NUEVO: Recargar el rol por si cambió
        loadUserRoleAndConfigureUI();
    }
}