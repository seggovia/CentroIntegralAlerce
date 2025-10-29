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

    private MaterialCardView cardRegistroActividades;
    private MaterialCardView cardGestionUsuarios;
    private MaterialCardView cardMantenedores;
    private MaterialCardView cardPerfil;
    private MaterialCardView cardCerrarSesion;

    // Sistema de roles
    private PermissionChecker permissionChecker;
    private RoleManager roleManager;
    private FirebaseAuth auth;
    private UserRole currentUserRole;

    // TextViews para mostrar información del usuario (opcional)
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

        // Inicializar componentes
        initializeComponents();

        // Inicializar vistas
        initializeViews(view);

        // Cargar rol y configurar UI
        loadUserRoleAndConfigureUI();

        // Configurar listeners
        setupListeners();
    }

    /**
     * Inicializa componentes del sistema de roles
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
        cardRegistroActividades = view.findViewById(R.id.cardRegistroActividades);
        cardGestionUsuarios = view.findViewById(R.id.cardGestionUsuarios);
        cardMantenedores = view.findViewById(R.id.cardMantenedores);
        cardPerfil = view.findViewById(R.id.cardPerfil);
        cardCerrarSesion = view.findViewById(R.id.cardCerrarSesion);

        // Opcional - TextViews para mostrar info del usuario
        // tvUserInfo = view.findViewById(R.id.tvUserInfo);
        // tvUserRole = view.findViewById(R.id.tvUserRole);
    }

    /**
     * Carga el rol del usuario y configura la UI según permisos
     */
    private void loadUserRoleAndConfigureUI() {
        roleManager.loadUserRole((RoleManager.OnRoleLoadedListener) role -> {
            currentUserRole = role;
            android.util.Log.d("SettingsFragment", "✅ Rol cargado: " + role.getValue());

            // Configurar UI según el rol
            configureUIByRole(role);

            // Mostrar información del usuario (opcional)
            displayUserInfo(role);
        });
    }

    /**
     * Configura la visibilidad de las opciones según el rol
     */
    private void configureUIByRole(UserRole role) {
        // REGLA: Solo ADMINISTRADORES pueden ver estas opciones
        boolean isAdmin = (role == UserRole.ADMINISTRADOR);

        if (cardRegistroActividades != null) {
            cardRegistroActividades.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        }

        if (cardGestionUsuarios != null) {
            cardGestionUsuarios.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        }

        if (cardMantenedores != null) {
            cardMantenedores.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
            android.util.Log.d("SettingsFragment", isAdmin ? "✅ Mostrando opciones admin" : "🚫 Ocultando opciones admin");
        }

        // Perfil y Cerrar sesión: Todos pueden verlo
        if (cardPerfil != null) {
            cardPerfil.setVisibility(View.VISIBLE);
        }

        if (cardCerrarSesion != null) {
            cardCerrarSesion.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Muestra información del usuario en la UI (opcional)
     */
    private void displayUserInfo(UserRole role) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Si tienes TextViews para mostrar info del usuario, descomenta:
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
     * Obtiene el nombre del rol para mostrar
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
        // Registro de Actividades - Solo administradores
        if (cardRegistroActividades != null) {
            cardRegistroActividades.setOnClickListener(v -> {
                if (permissionChecker.checkAndNotify(getContext(),
                        PermissionChecker.Permission.VIEW_MAINTAINERS)) {
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_registroActividadesFragment);
                }
            });
        }

        // Gestión de Usuarios - Solo administradores
        if (cardGestionUsuarios != null) {
            cardGestionUsuarios.setOnClickListener(v -> {
                if (permissionChecker.checkAndNotify(getContext(),
                        PermissionChecker.Permission.VIEW_MAINTAINERS)) {
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_gestionUsuariosFragment);
                }
            });
        }

        // Mantenedores - Solo administradores
        if (cardMantenedores != null) {
            cardMantenedores.setOnClickListener(v -> {
                if (permissionChecker.checkAndNotify(getContext(),
                        PermissionChecker.Permission.VIEW_MAINTAINERS)) {
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_maintainersFragment);
                }
            });
        }

        // Perfil - Todos pueden acceder
        if (cardPerfil != null) {
            cardPerfil.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_settingsFragment_to_perfilFragment)
            );
        }

        // Cerrar sesión con diálogo de confirmación
        if (cardCerrarSesion != null) {
            cardCerrarSesion.setOnClickListener(v -> mostrarDialogoCerrarSesion());
        }
    }

    /**
     * Muestra diálogo de confirmación para cerrar sesión
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
     * Cierra la sesión y limpia los datos
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

        // Mostrar mensaje
        Toast.makeText(getContext(), "Sesión cerrada exitosamente", Toast.LENGTH_SHORT).show();

        // Limpiar back stack para que no pueda volver atrás
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Recargar el rol por si cambió
        loadUserRoleAndConfigureUI();
    }
}
