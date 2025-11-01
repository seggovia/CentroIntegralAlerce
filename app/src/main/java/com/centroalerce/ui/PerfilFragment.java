package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.centroalerce.gestion.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfilFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView tvAvatarInitials;
    private TextView tvNombreUsuario;
    private TextView tvRolUsuario;
    private TextView tvNombreCompleto;
    private TextView tvEmail;
    private TextView tvRol;
    private TextView tvFechaRegistro;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_perfil, container, false);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        tvAvatarInitials = v.findViewById(R.id.tvAvatarInitials);
        tvNombreUsuario = v.findViewById(R.id.tvNombreUsuario);
        tvRolUsuario = v.findViewById(R.id.tvRolUsuario);
        tvNombreCompleto = v.findViewById(R.id.tvNombreCompleto);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvRol = v.findViewById(R.id.tvRol);
        tvFechaRegistro = v.findViewById(R.id.tvFechaRegistro);

        // Cargar datos del usuario
        cargarDatosUsuario();

        // Botón editar perfil
        v.findViewById(R.id.cardEditarPerfil).setOnClickListener(view -> {
            Toast.makeText(getContext(), "Función en desarrollo", Toast.LENGTH_SHORT).show();
        });

        // Botón cambiar contraseña
        v.findViewById(R.id.cardCambiarPassword).setOnClickListener(view -> {
            enviarCorreoRestablecimiento();
        });

        return v;
    }

    private void cargarDatosUsuario() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "No hay sesión activa", Toast.LENGTH_SHORT).show();
            return;
        }

        // Obtener datos del usuario desde Firestore
        db.collection("usuarios")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Obtener datos
                        String email = documentSnapshot.getString("email");
                        String rol = documentSnapshot.getString("rol");
                        com.google.firebase.Timestamp fechaCreacion = documentSnapshot.getTimestamp("fechaCreacion");

                        // Establecer email
                        if (email != null) {
                            tvEmail.setText(email);
                            tvNombreUsuario.setText(obtenerNombreDeEmail(email));
                            tvNombreCompleto.setText(obtenerNombreDeEmail(email));

                            // Establecer iniciales del avatar
                            String iniciales = obtenerIniciales(email);
                            tvAvatarInitials.setText(iniciales);
                        }

                        // Establecer rol
                        if (rol != null) {
                            tvRol.setText(capitalizarPrimeraLetra(rol));
                            tvRolUsuario.setText(capitalizarPrimeraLetra(rol));
                        }

                        // Establecer fecha de registro
                        if (fechaCreacion != null) {
                            Date fecha = fechaCreacion.toDate();
                            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
                            tvFechaRegistro.setText(capitalizarPrimeraLetra(sdf.format(fecha)));
                        }
                    } else {
                        Toast.makeText(getContext(), "No se encontraron datos del usuario", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error al cargar datos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String obtenerNombreDeEmail(String email) {
        // Extraer la parte antes del @ y convertir en nombre legible
        if (email != null && email.contains("@")) {
            String nombre = email.substring(0, email.indexOf("@"));
            // Reemplazar puntos y guiones por espacios
            nombre = nombre.replace(".", " ").replace("_", " ").replace("-", " ");
            // Capitalizar cada palabra
            String[] palabras = nombre.split(" ");
            StringBuilder nombreFormateado = new StringBuilder();
            for (String palabra : palabras) {
                if (!palabra.isEmpty()) {
                    nombreFormateado.append(capitalizarPrimeraLetra(palabra)).append(" ");
                }
            }
            return nombreFormateado.toString().trim();
        }
        return "Usuario";
    }

    private String obtenerIniciales(String email) {
        if (email != null && email.contains("@")) {
            String nombre = email.substring(0, email.indexOf("@"));
            String[] palabras = nombre.split("[._-]");

            if (palabras.length >= 2) {
                // Si hay dos palabras o más, tomar la primera letra de las dos primeras
                return (palabras[0].substring(0, 1) + palabras[1].substring(0, 1)).toUpperCase();
            } else if (palabras.length == 1 && palabras[0].length() >= 2) {
                // Si hay una sola palabra, tomar las dos primeras letras
                return palabras[0].substring(0, 2).toUpperCase();
            }
        }
        return "US";
    }

    private String capitalizarPrimeraLetra(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }

    private void enviarCorreoRestablecimiento() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null || currentUser.getEmail() == null) {
            Toast.makeText(getContext(), "No hay sesión activa", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = currentUser.getEmail();

        // Mostrar diálogo de confirmación
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cambiar contraseña")
                .setMessage("Se enviará un correo de restablecimiento a:\n\n" + email + "\n\n¿Deseas continuar?")
                .setPositiveButton("Enviar", (dialog, which) -> {
                    // Enviar correo de restablecimiento
                    mAuth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(),
                                        "Correo enviado. Revisa tu bandeja de entrada",
                                        Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(),
                                        "Error al enviar correo: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
