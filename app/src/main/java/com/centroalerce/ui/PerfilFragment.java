package com.centroalerce.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.utils.RoleManager;
import com.centroalerce.gestion.utils.UserRole;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfilFragment extends Fragment {

    private static final String TAG = "PerfilFragment";

    // Header
    private TextView tvAvatarInitials;
    private TextView tvNombreUsuario;
    private TextView tvRolUsuario;

    // Información personal
    private TextView tvNombreCompleto;
    private TextView tvEmail;
    private TextView tvRol;
    private TextView tvFechaRegistro;

    private RoleManager roleManager;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_perfil, container, false);

        // Inicializar Firebase y RoleManager
        mAuth = FirebaseAuth.getInstance();
        roleManager = RoleManager.getInstance();

        // Referencias a las vistas del header
        tvAvatarInitials = v.findViewById(R.id.tvAvatarInitials);
        tvNombreUsuario = v.findViewById(R.id.tvNombreUsuario);
        tvRolUsuario = v.findViewById(R.id.tvRolUsuario);

        // Referencias a las vistas de información personal
        tvNombreCompleto = v.findViewById(R.id.tvNombreCompleto);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvRol = v.findViewById(R.id.tvRol);
        tvFechaRegistro = v.findViewById(R.id.tvFechaRegistro);

        // Cargar datos del usuario
        cargarDatosUsuario();

        // Botón editar perfil
        v.findViewById(R.id.cardEditarPerfil).setOnClickListener(view -> {
            // Por ahora solo un mensaje, luego puedes crear otro fragment para editar
            // Toast.makeText(getContext(), "Editar perfil", Toast.LENGTH_SHORT).show();
        });

        // Botón cambiar contraseña
        v.findViewById(R.id.cardCambiarPassword).setOnClickListener(view -> {
            // Por ahora solo un mensaje
            // Toast.makeText(getContext(), "Cambiar contraseña", Toast.LENGTH_SHORT).show();
        });

        return v;
    }

    private void cargarDatosUsuario() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // Obtener nombre del usuario
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            String nombreFinal;

            if (displayName != null && !displayName.isEmpty()) {
                nombreFinal = displayName;
            } else {
                // Si no hay nombre, usar la parte del email antes del @
                if (email != null) {
                    nombreFinal = email.split("@")[0];
                } else {
                    nombreFinal = "Usuario";
                }
            }

            // Actualizar header
            tvNombreUsuario.setText(nombreFinal);

            // Generar iniciales para el avatar
            String iniciales = generarIniciales(nombreFinal);
            tvAvatarInitials.setText(iniciales);

            // Actualizar información personal
            tvNombreCompleto.setText(nombreFinal);
            tvEmail.setText(email != null ? email : "Sin email");

            // Formatear fecha de creación de la cuenta
            if (currentUser.getMetadata() != null) {
                long creationTimestamp = currentUser.getMetadata().getCreationTimestamp();
                String fechaRegistro = formatearFecha(creationTimestamp);
                tvFechaRegistro.setText(fechaRegistro);
            } else {
                tvFechaRegistro.setText("Fecha no disponible");
            }

            // Cargar y mostrar rol
            cargarRolUsuario();
        } else {
            Log.e(TAG, "Usuario no autenticado");
            tvNombreUsuario.setText("Usuario no autenticado");
            tvNombreCompleto.setText("Usuario no autenticado");
            tvEmail.setText("");
            tvRolUsuario.setText("Sin rol");
            tvRol.setText("Sin rol");
            tvFechaRegistro.setText("");
            tvAvatarInitials.setText("?");
        }
    }

    private void cargarRolUsuario() {
        // Mostrar "Cargando..." mientras se obtiene el rol
        tvRolUsuario.setText("Cargando...");
        tvRol.setText("Cargando...");

        roleManager.loadUserRole(new RoleManager.OnRoleLoadedListener() {
            @Override
            public void onRoleLoaded(UserRole role) {
                if (getActivity() != null && isAdded()) {
                    // Actualizar UI en el hilo principal
                    getActivity().runOnUiThread(() -> {
                        // Mostrar el rol en español
                        String rolTexto = obtenerTextoRol(role);
                        tvRolUsuario.setText(rolTexto);
                        tvRol.setText(rolTexto);
                        Log.d(TAG, "✅ Rol cargado: " + role.getValue());
                    });
                }
            }
        });
    }

    /**
     * Convierte el UserRole enum a texto legible en español
     */
    private String obtenerTextoRol(UserRole role) {
        if (role == null) {
            return "Sin rol asignado";
        }

        switch (role) {
            case ADMINISTRADOR:
                return "Administrador";
            case USUARIO:
                return "Usuario";
            case VISUALIZADOR:
                return "Visualizador";
            default:
                return "Sin rol asignado";
        }
    }

    /**
     * Genera las iniciales del nombre (máximo 2 letras)
     */
    private String generarIniciales(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            return "CA"; // Default: Centro Alerce
        }

        String[] palabras = nombre.trim().split("\\s+");
        StringBuilder iniciales = new StringBuilder();

        if (palabras.length == 1) {
            // Si es una sola palabra, tomar las primeras 2 letras
            iniciales.append(palabras[0].substring(0, Math.min(2, palabras[0].length())));
        } else {
            // Si son varias palabras, tomar la primera letra de las primeras 2 palabras
            iniciales.append(palabras[0].charAt(0));
            if (palabras.length > 1) {
                iniciales.append(palabras[1].charAt(0));
            }
        }

        return iniciales.toString().toUpperCase();
    }

    /**
     * Formatea el timestamp a un formato legible en español
     */
    private String formatearFecha(long timestamp) {
        try {
            Date fecha = new Date(timestamp);
            SimpleDateFormat formato = new SimpleDateFormat("MMMM yyyy", new Locale("es", "CL"));
            String fechaFormateada = formato.format(fecha);
            // Capitalizar primera letra
            return fechaFormateada.substring(0, 1).toUpperCase() + fechaFormateada.substring(1);
        } catch (Exception e) {
            Log.e(TAG, "Error al formatear fecha", e);
            return "Fecha no disponible";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Limpiar referencias para evitar memory leaks
        tvAvatarInitials = null;
        tvNombreUsuario = null;
        tvRolUsuario = null;
        tvNombreCompleto = null;
        tvEmail = null;
        tvRol = null;
        tvFechaRegistro = null;
    }
}