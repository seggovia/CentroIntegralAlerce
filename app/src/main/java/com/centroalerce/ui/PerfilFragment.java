package com.centroalerce.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.utils.RoleManager;
import com.centroalerce.gestion.utils.ThemeManager;
import com.centroalerce.gestion.utils.UserRole;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfilFragment extends Fragment {

    private static final String TAG = "PerfilFragment";

    // Servicios
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RoleManager roleManager;
    private ThemeManager themeManager;
    // Header
    private TextView tvAvatarInitials;
    private TextView tvNombreUsuario;
    private TextView tvRolUsuario;

    // Información personal
    private TextView tvNombreCompleto;
    private TextView tvEmail;
    private TextView tvRol;
    private TextView tvFechaRegistro;
    private MaterialSwitch switchDarkMode;

    // Rol obtenido de Firestore por si RoleManager falla/tarda
    private String rolDesdeFirestore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_perfil, container, false);

        // Inicializar servicios
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        roleManager = RoleManager.getInstance();
        themeManager = new ThemeManager(requireContext());

        // Referencias UI
        tvAvatarInitials = v.findViewById(R.id.tvAvatarInitials);
        tvNombreUsuario = v.findViewById(R.id.tvNombreUsuario);
        tvRolUsuario = v.findViewById(R.id.tvRolUsuario);

        tvNombreCompleto = v.findViewById(R.id.tvNombreCompleto);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvRol = v.findViewById(R.id.tvRol);
        tvFechaRegistro = v.findViewById(R.id.tvFechaRegistro);
        switchDarkMode = v.findViewById(R.id.switchDarkMode);

        // Cargar datos
        cargarDatosAuthInmediatos();
        cargarDatosDesdeFirestore();
        cargarRolConRoleManager();
        configurarSwitchModoOscuro();
        // Botón cambiar contraseña
        v.findViewById(R.id.cardCambiarPassword).setOnClickListener(view -> enviarCorreoRestablecimiento());

        return v;
    }
    private void configurarSwitchModoOscuro() {
        if (switchDarkMode == null) {
            Log.e(TAG, "❌ Switch de modo oscuro no encontrado en el layout");
            return;
        }

        // Cargar estado guardado
        boolean isDarkMode = themeManager.isDarkMode();
        switchDarkMode.setChecked(isDarkMode);
        Log.d(TAG, "🌙 Modo oscuro actual: " + (isDarkMode ? "ACTIVO" : "INACTIVO"));

        // Listener para cambios
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "🔄 Switch cambiado a: " + (isChecked ? "OSCURO" : "CLARO"));

            // Guardar preferencia
            int newMode = isChecked ? ThemeManager.MODE_DARK : ThemeManager.MODE_LIGHT;
            themeManager.saveThemeMode(newMode);

            // Aplicar tema inmediatamente
            themeManager.applyTheme();

            // Mostrar feedback
            Toast.makeText(getContext(),
                    isChecked ? "Modo oscuro activado" : "Modo claro activado",
                    Toast.LENGTH_SHORT).show();

            // Recrear la actividad para aplicar el tema
            if (getActivity() != null) {
                getActivity().recreate();
            }
        });
    }


    private void cargarDatosAuthInmediatos() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            actualizarTextoSeguro(tvNombreUsuario, "Usuario no autenticado");
            actualizarTextoSeguro(tvNombreCompleto, "Usuario no autenticado");
            actualizarTextoSeguro(tvEmail, "");
            actualizarTextoSeguro(tvRolUsuario, "Sin rol");
            actualizarTextoSeguro(tvRol, "Sin rol");
            actualizarTextoSeguro(tvFechaRegistro, "");
            actualizarTextoSeguro(tvAvatarInitials, "?");
            Log.e(TAG, "Usuario no autenticado");
            return;
        }

        String displayName = currentUser.getDisplayName();
        String email = currentUser.getEmail();
        String nombreFinal = (displayName != null && !displayName.isEmpty())
                ? displayName
                : (email != null ? obtenerNombreDeEmail(email) : "Usuario");

        actualizarTextoSeguro(tvNombreUsuario, nombreFinal);
        actualizarTextoSeguro(tvNombreCompleto, nombreFinal);
        actualizarTextoSeguro(tvEmail, email != null ? email : "Sin email");
        actualizarTextoSeguro(tvAvatarInitials, generarIniciales(nombreFinal));

        if (currentUser.getMetadata() != null) {
            long creationTimestamp = currentUser.getMetadata().getCreationTimestamp();
            String fechaRegistro = formatearFecha(creationTimestamp, new Locale("es", "CL"));
            actualizarTextoSeguro(tvFechaRegistro, fechaRegistro);
        } else {
            actualizarTextoSeguro(tvFechaRegistro, "Fecha no disponible");
        }
    }

    private void cargarDatosDesdeFirestore() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("usuarios")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;
                    if (documentSnapshot.exists()) {
                        String email = documentSnapshot.getString("email");
                        String rol = documentSnapshot.getString("rol");
                        com.google.firebase.Timestamp fechaCreacion = documentSnapshot.getTimestamp("fechaCreacion");

                        if (email != null) {
                            actualizarTextoSeguro(tvEmail, email);
                            String nombre = obtenerNombreDeEmail(email);
                            actualizarTextoSeguro(tvNombreUsuario, nombre);
                            actualizarTextoSeguro(tvNombreCompleto, nombre);
                            actualizarTextoSeguro(tvAvatarInitials, obtenerIniciales(email));
                        }

                        if (rol != null) {
                            rolDesdeFirestore = capitalizarPrimeraLetra(rol);
                            // Solo mostrar aquí si aún no llegó RoleManager
                            if (tvRolUsuario.getText() == null || "Cargando...".contentEquals(tvRolUsuario.getText())) {
                                actualizarTextoSeguro(tvRolUsuario, rolDesdeFirestore);
                                actualizarTextoSeguro(tvRol, rolDesdeFirestore);
                            }
                        }

                        if (fechaCreacion != null) {
                            Date fecha = fechaCreacion.toDate();
                            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
                            String texto = capitalizarPrimeraLetra(sdf.format(fecha));
                            actualizarTextoSeguro(tvFechaRegistro, texto);
                        }
                    } else {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "No se encontraron datos del usuario", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Error al cargar datos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void cargarRolConRoleManager() {
        actualizarTextoSeguro(tvRolUsuario, "Cargando...");
        actualizarTextoSeguro(tvRol, "Cargando...");

        roleManager.loadUserRole(new RoleManager.OnRoleLoadedListener() {
            @Override
            public void onRoleLoaded(UserRole role) {
                if (getActivity() == null || !isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    String rolTexto = obtenerTextoRol(role);
                    // Prioriza RoleManager; si es null, cae a Firestore si estaba disponible
                    if (role != null) {
                        actualizarTextoSeguro(tvRolUsuario, rolTexto);
                        actualizarTextoSeguro(tvRol, rolTexto);
                        Log.d(TAG, "Rol cargado (RoleManager): " + role.getValue());
                    } else if (rolDesdeFirestore != null) {
                        actualizarTextoSeguro(tvRolUsuario, rolDesdeFirestore);
                        actualizarTextoSeguro(tvRol, rolDesdeFirestore);
                        Log.d(TAG, "Rol desde Firestore utilizado como fallback");
                    } else {
                        actualizarTextoSeguro(tvRolUsuario, "Sin rol asignado");
                        actualizarTextoSeguro(tvRol, "Sin rol asignado");
                        Log.d(TAG, "Sin rol disponible");
                    }
                });
            }
        });
    }

    private void enviarCorreoRestablecimiento() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "No hay sesión activa", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String email = currentUser.getEmail();

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cambiar contraseña")
                .setMessage("Se enviará un correo de restablecimiento a:\n\n" + email + "\n\n¿Deseas continuar?")
                .setPositiveButton("Enviar", (dialog, which) -> {
                    mAuth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(aVoid -> {
                                if (getContext() != null) {
                                    Toast.makeText(getContext(),
                                            "Correo enviado. Revisa tu bandeja de entrada",
                                            Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (getContext() != null) {
                                    Toast.makeText(getContext(),
                                            "Error al enviar correo: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Utilidades

    private void actualizarTextoSeguro(TextView tv, String texto) {
        if (tv != null) {
            tv.setText(texto);
        }
    }

    private String obtenerNombreDeEmail(String email) {
        if (email != null && email.contains("@")) {
            String nombre = email.substring(0, email.indexOf("@"));
            nombre = nombre.replace(".", " ").replace("_", " ").replace("-", " ");
            String[] palabras = nombre.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String p : palabras) {
                if (!p.isEmpty()) {
                    sb.append(capitalizarPrimeraLetra(p)).append(" ");
                }
            }
            return sb.toString().trim();
        }
        return "Usuario";
    }

    private String obtenerIniciales(String email) {
        if (email != null && email.contains("@")) {
            String nombre = email.substring(0, email.indexOf("@"));
            String[] palabras = nombre.split("[._-]");
            if (palabras.length >= 2) {
                return (palabras[0].substring(0, 1) + palabras[1].substring(0, 1)).toUpperCase();
            } else if (palabras.length == 1 && palabras[0].length() >= 2) {
                return palabras[0].substring(0, 2).toUpperCase();
            }
        }
        return "US";
    }

    private String generarIniciales(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            return "CA";
        }
        String[] palabras = nombre.trim().split("\\s+");
        StringBuilder iniciales = new StringBuilder();
        if (palabras.length == 1) {
            iniciales.append(palabras[0].substring(0, Math.min(2, palabras[0].length())));
        } else {
            iniciales.append(palabras[0].charAt(0));
            if (palabras.length > 1) {
                iniciales.append(palabras[1].charAt(0));
            }
        }
        return iniciales.toString().toUpperCase();
    }

    private String formatearFecha(long timestamp, Locale locale) {
        try {
            Date fecha = new Date(timestamp);
            SimpleDateFormat formato = new SimpleDateFormat("MMMM yyyy", locale);
            String out = formato.format(fecha);
            return out.substring(0, 1).toUpperCase(locale) + out.substring(1);
        } catch (Exception e) {
            Log.e(TAG, "Error al formatear fecha", e);
            return "Fecha no disponible";
        }
    }

    private String obtenerTextoRol(UserRole role) {
        if (role == null) return "Sin rol asignado";
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

    private String capitalizarPrimeraLetra(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tvAvatarInitials = null;
        tvNombreUsuario = null;
        tvRolUsuario = null;
        tvNombreCompleto = null;
        tvEmail = null;
        tvRol = null;
        tvFechaRegistro = null;
        switchDarkMode = null;
    }
}
