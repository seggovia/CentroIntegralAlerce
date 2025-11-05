package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.ArrayList;
import java.util.List;

public class GestionUsuariosFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseFunctions functions;
    private RecyclerView recyclerViewUsuarios;
    private MaterialButton btnVolver;
    private FloatingActionButton fabRegistrarUsuario;
    private MaterialButton btnFiltros;
    private MaterialButton btnVolverAbajo;
    
    // Variables para filtros
    private LinearLayout panelFiltros;
    private AutoCompleteTextView actvFiltroRol;
    private AutoCompleteTextView actvOrdenar;
    private MaterialButton btnLimpiarFiltros;
    private MaterialButton btnAplicarFiltros;
    
    // Variables para paginación
    private LinearLayout panelPaginacion;
    private MaterialButton btnPaginaAnterior;
    private MaterialButton btnPaginaSiguiente;
    private android.widget.TextView textInfoPagina;
    
    private List<RecyclerItem> recyclerItems = new ArrayList<>();
    private UsuarioAdapter adapter;
    private List<Usuario> todosLosUsuarios = new ArrayList<>();
    
    // Variables de paginación
    private int paginaActual = 1;
    private int tamanoPagina = 10;
    private int totalPaginas = 0;
    private List<Usuario> usuariosPaginaActual = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gestion_usuarios, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance("us-central1");
        
        // Inicializar vistas
        recyclerViewUsuarios = view.findViewById(R.id.recyclerViewUsuarios);
        btnVolver = view.findViewById(R.id.btnVolver);
        fabRegistrarUsuario = view.findViewById(R.id.fabRegistrarUsuario);
        btnFiltros = view.findViewById(R.id.btnFiltros);
        btnVolverAbajo = view.findViewById(R.id.btnVolverAbajo);
        
        // Inicializar filtros
        panelFiltros = view.findViewById(R.id.panelFiltros);
        actvFiltroRol = view.findViewById(R.id.actvFiltroRol);
        actvOrdenar = view.findViewById(R.id.actvOrdenar);
        btnLimpiarFiltros = view.findViewById(R.id.btnLimpiarFiltros);
        btnAplicarFiltros = view.findViewById(R.id.btnAplicarFiltros);
        
        // Inicializar paginación
        panelPaginacion = view.findViewById(R.id.panelPaginacion);
        btnPaginaAnterior = view.findViewById(R.id.btnPaginaAnterior);
        btnPaginaSiguiente = view.findViewById(R.id.btnPaginaSiguiente);
        textInfoPagina = view.findViewById(R.id.textInfoPagina);

        // Configurar RecyclerView
        recyclerViewUsuarios.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UsuarioAdapter(recyclerItems, new UsuarioAdapter.Callback() {
            @Override
            public void onDetalles(Usuario usuario) {
                mostrarDetallesUsuario(usuario);
            }

            @Override
            public void onActualizar(Usuario usuario) {
                actualizarUsuario(usuario);
            }

            @Override
            public void onEliminar(Usuario usuario) {
                eliminarUsuario(usuario);
            }
        });
        recyclerViewUsuarios.setAdapter(adapter);

        // Botones de retroceso
        btnVolver.setOnClickListener(v -> {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
        });
        
        btnVolverAbajo.setOnClickListener(v -> {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
        });

        // FAB registrar usuario
        fabRegistrarUsuario.setOnClickListener(v -> {
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.action_gestionUsuariosFragment_to_registroUsuariosFragment);
        });
        
        // Botones de paginación
        btnPaginaAnterior.setOnClickListener(v -> {
            if (paginaActual > 1) {
                paginaActual--;
                cargarPaginaActual();
            }
        });
        
        btnPaginaSiguiente.setOnClickListener(v -> {
            if (paginaActual < totalPaginas) {
                paginaActual++;
                cargarPaginaActual();
            }
        });

        // Configurar filtros
        configurarFiltros();

        // Botón filtros - mostrar/ocultar panel
        btnFiltros.setOnClickListener(v -> {
            if (panelFiltros.getVisibility() == View.GONE) {
                panelFiltros.setVisibility(View.VISIBLE);
                btnFiltros.setText("Ocultar Filtros");
            } else {
                panelFiltros.setVisibility(View.GONE);
                btnFiltros.setText("Filtros");
            }
        });

        // Botón limpiar filtros
        btnLimpiarFiltros.setOnClickListener(v -> {
            actvFiltroRol.setText("Todos", false);
            actvOrdenar.setText("Email (A-Z)", false);
            aplicarFiltros();
        });

        // Botón aplicar filtros
        btnAplicarFiltros.setOnClickListener(v -> {
            aplicarFiltros();
            panelFiltros.setVisibility(View.GONE);
            btnFiltros.setText("Filtros");
        });

        // Cargar usuarios
        cargarUsuarios();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Recargar usuarios cuando se regrese al fragmento
        cargarUsuarios();
    }

    private void configurarFiltros() {
        // Configurar combobox de roles
        String[] roles = {"Todos", "Administrador", "Usuario", "Visualizador"};
        ArrayAdapter<String> adapterRoles = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_dropdown_item_1line, roles);
        actvFiltroRol.setAdapter(adapterRoles);
        actvFiltroRol.setText("Todos", false);
        actvFiltroRol.setThreshold(0);
        actvFiltroRol.setOnClickListener(v -> actvFiltroRol.showDropDown());
        actvFiltroRol.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvFiltroRol.showDropDown();
        });

        // Configurar combobox de ordenamiento
        String[] ordenamientos = {
            "Email (A-Z)", "Email (Z-A)", 
            "Rol (A-Z)", "Rol (Z-A)",
            "Fecha (Más reciente)", "Fecha (Más antiguo)"
        };
        ArrayAdapter<String> adapterOrden = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_dropdown_item_1line, ordenamientos);
        actvOrdenar.setAdapter(adapterOrden);
        actvOrdenar.setText("Email (A-Z)", false);
        actvOrdenar.setThreshold(0);
        actvOrdenar.setOnClickListener(v -> actvOrdenar.showDropDown());
        actvOrdenar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvOrdenar.showDropDown();
        });
    }

    private void aplicarFiltros() {
        String filtroRol = actvFiltroRol.getText().toString();
        String ordenamiento = actvOrdenar.getText().toString();
        
        List<Usuario> usuariosFiltrados = new ArrayList<>(todosLosUsuarios);
        
        // Aplicar filtro por rol
        if (!filtroRol.equals("Todos")) {
            usuariosFiltrados.removeIf(usuario -> !filtroRol.equals(usuario.rol));
        }
        
        // Aplicar ordenamiento
        switch (ordenamiento) {
            case "Email (A-Z)":
                usuariosFiltrados.sort((u1, u2) -> u1.email.compareToIgnoreCase(u2.email));
                break;
            case "Email (Z-A)":
                usuariosFiltrados.sort((u1, u2) -> u2.email.compareToIgnoreCase(u1.email));
                break;
            case "Rol (A-Z)":
                usuariosFiltrados.sort((u1, u2) -> u1.rol.compareToIgnoreCase(u2.rol));
                break;
            case "Rol (Z-A)":
                usuariosFiltrados.sort((u1, u2) -> u2.rol.compareToIgnoreCase(u1.rol));
                break;
            case "Fecha (Más reciente)":
                usuariosFiltrados.sort((u1, u2) -> {
                    if (u1.fechaCreacion == null || u2.fechaCreacion == null) return 0;
                    return u2.fechaCreacion.compareTo(u1.fechaCreacion);
                });
                break;
            case "Fecha (Más antiguo)":
                usuariosFiltrados.sort((u1, u2) -> {
                    if (u1.fechaCreacion == null || u2.fechaCreacion == null) return 0;
                    return u1.fechaCreacion.compareTo(u2.fechaCreacion);
                });
                break;
        }
        
        // Guardar usuarios filtrados y recalcular paginación
        usuariosPaginaActual = usuariosFiltrados;
        totalPaginas = (int) Math.ceil((double) usuariosPaginaActual.size() / tamanoPagina);
        if (totalPaginas == 0) totalPaginas = 1;
        
        // Resetear a página 1 cuando se aplican filtros
        paginaActual = 1;
        
        // Cargar página actual
        cargarPaginaActual();
    }
    
    private void cargarPaginaActual() {
        if (usuariosPaginaActual.isEmpty()) {
            adapter.actualizarUsuarios(new ArrayList<>());
            actualizarControlesPaginacion();
            return;
        }
        
        // Calcular índices de la página actual
        int inicio = (paginaActual - 1) * tamanoPagina;
        int fin = Math.min(inicio + tamanoPagina, usuariosPaginaActual.size());
        
        // Obtener usuarios de la página actual
        List<Usuario> usuariosPagina = usuariosPaginaActual.subList(inicio, fin);
        
        // Actualizar adapter
        adapter.actualizarUsuarios(usuariosPagina);
        
        // Actualizar controles de paginación
        actualizarControlesPaginacion();
    }
    
    private void actualizarControlesPaginacion() {
        // Actualizar texto de información
        textInfoPagina.setText("Página " + paginaActual + " de " + totalPaginas);
        
        // Habilitar/deshabilitar botones
        btnPaginaAnterior.setEnabled(paginaActual > 1);
        btnPaginaSiguiente.setEnabled(paginaActual < totalPaginas);
        
        // Mostrar/ocultar panel de paginación si hay más de una página
        panelPaginacion.setVisibility(totalPaginas > 1 ? View.VISIBLE : View.GONE);
    }

    private void cargarUsuarios() {
        // Cargar todos los usuarios (filtraremos los inactivos después)
        db.collection("usuarios")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    todosLosUsuarios.clear();

                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(getContext(), "No hay usuarios registrados", Toast.LENGTH_SHORT).show();
                        adapter.actualizarUsuarios(new ArrayList<>());
                        actualizarControlesPaginacion();
                        return;
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Usuario usuario = new Usuario();
                        usuario.uid = doc.getId(); // Usar el ID del documento como UID
                        usuario.email = doc.getString("email");
                        usuario.rol = doc.getString("rol");
                        usuario.fechaCreacion = doc.getDate("fechaCreacion");
                        usuario.activo = doc.getBoolean("activo");

                        // Debug logging
                        android.util.Log.d("GestionUsuarios", "Usuario cargado: " + usuario.uid +
                                ", email: " + usuario.email +
                                ", rol: " + usuario.rol +
                                ", activo: " + usuario.activo);

                        // Valores por defecto si son null
                        if (usuario.email == null) usuario.email = "Sin email";
                        if (usuario.rol == null) usuario.rol = "Usuario";

                        // Si el campo "activo" no existe, verificar campo "estado" (usuarios antiguos)
                        if (usuario.activo == null) {
                            String estado = doc.getString("estado");
                            // Si tiene estado="activo" o no tiene ninguno, considerar como activo
                            usuario.activo = (estado == null || estado.equalsIgnoreCase("activo"));

                            // Actualizar el documento con el nuevo campo
                            if (usuario.activo) {
                                db.collection("usuarios").document(usuario.uid)
                                        .update("activo", true)
                                        .addOnFailureListener(e ->
                                            android.util.Log.e("GestionUsuarios", "Error actualizando campo activo", e)
                                        );
                            }
                        }

                        // Solo agregar usuarios activos a la lista
                        if (usuario.activo != null && usuario.activo) {
                            todosLosUsuarios.add(usuario);
                        }
                    }

                    // Ordenar por email después de cargar
                    todosLosUsuarios.sort((u1, u2) -> u1.email.compareToIgnoreCase(u2.email));

                    // Aplicar filtros por defecto y cargar página actual
                    aplicarFiltros();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error al cargar usuarios: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    android.util.Log.e("GestionUsuarios", "Error cargando usuarios", e);
                });
    }

    private void mostrarDetallesUsuario(Usuario usuario) {
        // Crear el dialog personalizado
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());

        // Inflar el layout personalizado
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_detalles_usuario, null);
        builder.setView(dialogView);

        // Configurar los datos
        android.widget.TextView textDetalleEmail = dialogView.findViewById(R.id.textDetalleEmail);
        android.widget.TextView textDetalleRol = dialogView.findViewById(R.id.textDetalleRol);
        android.widget.TextView textDetalleEstado = dialogView.findViewById(R.id.textDetalleEstado);
        MaterialButton btnRestablecerPassword = dialogView.findViewById(R.id.btnRestablecerPassword);
        MaterialButton btnCerrarDetalles = dialogView.findViewById(R.id.btnCerrarDetalles);

        textDetalleEmail.setText(usuario.email);
        textDetalleRol.setText(usuario.rol);
        textDetalleEstado.setText(usuario.activo ? "Activo" : "Inactivo");

        // Crear y mostrar el dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(false);

        // Botón para restablecer contraseña
        btnRestablecerPassword.setOnClickListener(v -> {
            enviarCorreoRestablecimiento(usuario.email);
        });

        btnCerrarDetalles.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void enviarCorreoRestablecimiento(String email) {
        // Mostrar diálogo de confirmación antes de enviar
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Restablecer contraseña")
                .setMessage("¿Deseas enviar un correo de restablecimiento de contraseña a:\n\n" + email + "?")
                .setPositiveButton("Enviar", (dialog, which) -> {
                    // Enviar correo de restablecimiento
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                            .sendPasswordResetEmail(email)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(),
                                        "Correo de restablecimiento enviado a " + email,
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

    private void actualizarUsuario(Usuario usuario) {
        // Crear el dialog personalizado
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        
        // Inflar el layout personalizado
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_actualizar_usuario, null);
        builder.setView(dialogView);
        
        // Configurar los campos
        TextInputEditText etActualizarEmail = dialogView.findViewById(R.id.etActualizarEmail);
        AutoCompleteTextView actvActualizarRol = dialogView.findViewById(R.id.actvActualizarRol);
        MaterialButton btnCancelarActualizar = dialogView.findViewById(R.id.btnCancelarActualizar);
        MaterialButton btnGuardarActualizar = dialogView.findViewById(R.id.btnGuardarActualizar);
        
        // Cargar datos actuales
        etActualizarEmail.setText(usuario.email);
        actvActualizarRol.setText(usuario.rol, false);
        
        // Configurar combobox de roles
        String[] roles = {"Administrador", "Usuario", "Visualizador"};
        ArrayAdapter<String> adapterRoles = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_dropdown_item_1line, roles);
        actvActualizarRol.setAdapter(adapterRoles);
        actvActualizarRol.setThreshold(0);
        actvActualizarRol.setOnClickListener(v -> actvActualizarRol.showDropDown());
        actvActualizarRol.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvActualizarRol.showDropDown();
        });
        
        // Crear y mostrar el dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(false);
        
        btnCancelarActualizar.setOnClickListener(v -> dialog.dismiss());
        
        btnGuardarActualizar.setOnClickListener(v -> {
            String nuevoEmail = etActualizarEmail.getText().toString().trim();
            String nuevoRol = actvActualizarRol.getText().toString();
            
            if (nuevoEmail.isEmpty() || nuevoRol.isEmpty()) {
                Toast.makeText(getContext(), "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Actualizar en Firebase
            db.collection("usuarios").document(usuario.uid)
                    .update("email", nuevoEmail,
                            "rol", nuevoRol)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Usuario actualizado exitosamente", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        cargarUsuarios(); // Recargar la lista
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Error al actualizar usuario: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
        
        dialog.show();
    }

    private void eliminarUsuario(Usuario usuario) {
        // Verificar si el usuario intenta eliminarse a sí mismo
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(usuario.uid)) {
            // El usuario está intentando eliminarse a sí mismo
            mostrarDialogoAutoEliminacion(usuario);
            return;
        }

        // Continuar con eliminación normal
        mostrarDialogoEliminacionNormal(usuario);
    }

    private void mostrarDialogoAutoEliminacion(Usuario usuario) {
        // Crear diálogo de advertencia especial
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_eliminar_usuario, null);
        builder.setView(dialogView);

        android.widget.TextView textEmailEliminar = dialogView.findViewById(R.id.textEmailEliminar);
        android.widget.TextView textMensajeAdicional = dialogView.findViewById(R.id.textMensajeAdicional);
        MaterialButton btnCancelarEliminar = dialogView.findViewById(R.id.btnCancelarEliminar);
        MaterialButton btnConfirmarEliminar = dialogView.findViewById(R.id.btnConfirmarEliminar);

        // Configurar mensaje de advertencia
        textEmailEliminar.setText(usuario.email);

        // Cambiar el mensaje adicional para auto-eliminación
        textMensajeAdicional.setText("⚠️ ADVERTENCIA: Estás a punto de eliminar tu propia cuenta de administrador.\n\n" +
                "Si continúas:\n" +
                "• Perderás acceso al sistema\n" +
                "• Se cerrará tu sesión automáticamente\n" +
                "• No podrás recuperar esta cuenta\n\n" +
                "¿Estás seguro de continuar?");
        textMensajeAdicional.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
        textMensajeAdicional.setTextSize(14);

        // Cambiar color del botón confirmar a rojo
        btnConfirmarEliminar.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark, null));
        btnConfirmarEliminar.setText("SÍ, ELIMINAR MI CUENTA");

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(false);

        btnCancelarEliminar.setOnClickListener(v -> dialog.dismiss());

        btnConfirmarEliminar.setOnClickListener(v -> {
            // Mostrar loading
            Toast.makeText(getContext(), "Eliminando usuario...", Toast.LENGTH_SHORT).show();

            // Llamar a la Cloud Function para eliminar de Auth y Firestore
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("uid", usuario.uid);

            functions.getHttpsCallable("deleteUser")
                    .call(data)
                    .addOnSuccessListener(result -> {
                        Toast.makeText(getContext(),
                            "Tu cuenta ha sido eliminada completamente. Cerrando sesión...",
                            Toast.LENGTH_LONG).show();
                        dialog.dismiss();

                        // Cerrar sesión y volver al login
                        cerrarSesionYVolverAlLogin();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(),
                            "Error al eliminar tu cuenta: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    });
        });

        dialog.show();
    }

    private void mostrarDialogoEliminacionNormal(Usuario usuario) {
        // Crear el dialog personalizado
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());

        // Inflar el layout personalizado
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_eliminar_usuario, null);
        builder.setView(dialogView);

        // Configurar los elementos
        android.widget.TextView textEmailEliminar = dialogView.findViewById(R.id.textEmailEliminar);
        MaterialButton btnCancelarEliminar = dialogView.findViewById(R.id.btnCancelarEliminar);
        MaterialButton btnConfirmarEliminar = dialogView.findViewById(R.id.btnConfirmarEliminar);

        textEmailEliminar.setText(usuario.email);

        // Crear y mostrar el dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(false);

        btnCancelarEliminar.setOnClickListener(v -> dialog.dismiss());

        btnConfirmarEliminar.setOnClickListener(v -> {
            // Verificar estado de autenticación ANTES de llamar a la función
            com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

            if (currentUser == null) {
                com.google.android.material.snackbar.Snackbar.make(
                    requireView(),
                    "Error: No hay usuario autenticado",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show();
                android.util.Log.e("GestionUsuarios", "getCurrentUser() retornó null");
                return;
            }

            android.util.Log.d("GestionUsuarios", "Usuario autenticado: " + currentUser.getEmail() + " (UID: " + currentUser.getUid() + ")");

            // Cambiar texto del botón y deshabilitar
            btnConfirmarEliminar.setEnabled(false);
            btnConfirmarEliminar.setText("Eliminando...");
            btnCancelarEliminar.setEnabled(false);

            // Forzar refresh del token antes de llamar a la Cloud Function
            currentUser.getIdToken(true)
                    .addOnSuccessListener(getTokenResult -> {
                        android.util.Log.d("GestionUsuarios", "Token refreshed exitosamente");

                        // Llamar a la Cloud Function para eliminar de Auth y Firestore
                        java.util.Map<String, Object> data = new java.util.HashMap<>();
                        data.put("uid", usuario.uid);

                        functions.getHttpsCallable("deleteUser")
                                .call(data)
                                .addOnSuccessListener(result -> {
                                    dialog.dismiss();

                                    // Mostrar Snackbar personalizado con mensaje de éxito
                                    com.google.android.material.snackbar.Snackbar snackbar = com.google.android.material.snackbar.Snackbar.make(
                                        requireView(),
                                        "✓ Usuario eliminado correctamente",
                                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                                    );
                                    snackbar.setBackgroundTint(getResources().getColor(android.R.color.holo_green_dark));
                                    snackbar.setTextColor(getResources().getColor(android.R.color.white));
                                    snackbar.show();

                                    cargarUsuarios(); // Recargar la lista
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("GestionUsuarios", "Error al eliminar usuario", e);
                                    android.util.Log.e("GestionUsuarios", "Error class: " + e.getClass().getName());
                                    android.util.Log.e("GestionUsuarios", "Error message: " + e.getMessage());
                                    if (e instanceof com.google.firebase.functions.FirebaseFunctionsException) {
                                        com.google.firebase.functions.FirebaseFunctionsException ffe =
                                            (com.google.firebase.functions.FirebaseFunctionsException) e;
                                        android.util.Log.e("GestionUsuarios", "Function error code: " + ffe.getCode());
                                        android.util.Log.e("GestionUsuarios", "Function error details: " + ffe.getDetails());
                                    }

                                    // Restaurar botones
                                    btnConfirmarEliminar.setEnabled(true);
                                    btnConfirmarEliminar.setText("Eliminar");
                                    btnCancelarEliminar.setEnabled(true);

                                    com.google.android.material.snackbar.Snackbar.make(
                                        requireView(),
                                        "Error al eliminar usuario: " + e.getMessage(),
                                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                                    ).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("GestionUsuarios", "Error al refrescar token", e);

                        // Restaurar botones
                        btnConfirmarEliminar.setEnabled(true);
                        btnConfirmarEliminar.setText("Eliminar");
                        btnCancelarEliminar.setEnabled(true);

                        com.google.android.material.snackbar.Snackbar.make(
                            requireView(),
                            "Error de autenticación. Intenta cerrar sesión y volver a iniciar.",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show();
                    });
        });
        
        dialog.show();
    }

    private void cerrarSesionYVolverAlLogin() {
        // Cerrar sesión de Firebase
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();

        // Mostrar mensaje final
        Toast.makeText(getContext(),
            "Sesión cerrada. Tu cuenta ha sido eliminada.",
            Toast.LENGTH_LONG).show();

        // Navegar de vuelta al LoginFragment
        try {
            // Usar NavController para volver al login
            androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(requireView());

            // Limpiar todo el back stack y navegar al login
            navController.popBackStack(R.id.loginFragment, false);
            navController.navigate(R.id.loginFragment);
        } catch (Exception e) {
            // Si falla la navegación, intentar cerrar la actividad
            android.util.Log.e("GestionUsuarios", "Error navegando al login", e);
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    // Clase para representar un usuario
    public static class Usuario {
        public String uid;
        public String email;
        public String rol;
        public java.util.Date fechaCreacion;
        public Boolean activo;
    }

    // Adapter para el RecyclerView
    // Clase para representar diferentes tipos de items
    private static class RecyclerItem {
        public static final int TYPE_USUARIO = 0;
        public static final int TYPE_ACCIONES = 1;
        
        public int type;
        public Usuario usuario;
        
        public RecyclerItem(int type, Usuario usuario) {
            this.type = type;
            this.usuario = usuario;
        }
    }

    private static class UsuarioAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<RecyclerItem> items;
        private Callback callback;

        public interface Callback {
            void onDetalles(Usuario usuario);
            void onActualizar(Usuario usuario);
            void onEliminar(Usuario usuario);
        }

        public UsuarioAdapter(List<RecyclerItem> items, Callback callback) {
            this.items = items;
            this.callback = callback;
        }

        public void actualizarUsuarios(List<Usuario> usuarios) {
            this.items.clear();
            for (Usuario usuario : usuarios) {
                this.items.add(new RecyclerItem(RecyclerItem.TYPE_USUARIO, usuario));
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == RecyclerItem.TYPE_USUARIO) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_usuario, parent, false);
                return new UsuarioViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_acciones_usuario, parent, false);
                return new AccionesViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RecyclerItem item = items.get(position);
            
            if (holder instanceof UsuarioViewHolder) {
                UsuarioViewHolder usuarioHolder = (UsuarioViewHolder) holder;
                Usuario usuario = item.usuario;
                
                // Configurar datos del usuario
                usuarioHolder.textNombre.setText(usuario.email != null ? usuario.email : "Sin email");
                usuarioHolder.textRol.setText(usuario.rol != null ? usuario.rol : "Usuario");
                
                // Configurar click listener para mostrar acciones
                usuarioHolder.btnMostrarAcciones.setOnClickListener(v -> {
                    // Verificar si ya hay un panel de acciones abierto para este usuario
                    boolean panelAbierto = false;
                    int panelIndex = -1;
                    
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).type == RecyclerItem.TYPE_ACCIONES && 
                            items.get(i).usuario != null && 
                            items.get(i).usuario.uid.equals(usuario.uid)) {
                            panelAbierto = true;
                            panelIndex = i;
                            break;
                        }
                    }
                    
                    if (panelAbierto) {
                        // Si el panel ya está abierto, cerrarlo
                        items.remove(panelIndex);
                        notifyItemRemoved(panelIndex);
                    } else {
                        // Si no está abierto, cerrar todos los demás y abrir este
                        cerrarTodosLosPanelesDeAcciones();
                        
                        // Insertar item de acciones después del usuario actual
                        int actionPosition = position + 1;
                        items.add(actionPosition, new RecyclerItem(RecyclerItem.TYPE_ACCIONES, usuario));
                        notifyItemInserted(actionPosition);
                    }
                });
            } else if (holder instanceof AccionesViewHolder) {
                AccionesViewHolder accionesHolder = (AccionesViewHolder) holder;
                Usuario usuario = item.usuario;
                
                // Configurar click listeners
                accionesHolder.btnDetalles.setOnClickListener(v -> callback.onDetalles(usuario));
                accionesHolder.btnActualizar.setOnClickListener(v -> callback.onActualizar(usuario));
                accionesHolder.btnEliminar.setOnClickListener(v -> callback.onEliminar(usuario));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
        
        // Método para cerrar todos los paneles de acciones abiertos
        private void cerrarTodosLosPanelesDeAcciones() {
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i).type == RecyclerItem.TYPE_ACCIONES) {
                    items.remove(i);
                    notifyItemRemoved(i);
                }
            }
        }

        // ViewHolder para items de usuario
        public static class UsuarioViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textNombre;
            android.widget.TextView textRol;
            MaterialButton btnMostrarAcciones;

            public UsuarioViewHolder(@NonNull View itemView) {
                super(itemView);
                textNombre = itemView.findViewById(R.id.textNombre);
                textRol = itemView.findViewById(R.id.textRol);
                btnMostrarAcciones = itemView.findViewById(R.id.btnMostrarAcciones);
            }
        }

        // ViewHolder para items de acciones
        public static class AccionesViewHolder extends RecyclerView.ViewHolder {
            MaterialButton btnDetalles;
            MaterialButton btnActualizar;
            MaterialButton btnEliminar;

            public AccionesViewHolder(@NonNull View itemView) {
                super(itemView);
                btnDetalles = itemView.findViewById(R.id.btnDetalles);
                btnActualizar = itemView.findViewById(R.id.btnActualizar);
                btnEliminar = itemView.findViewById(R.id.btnEliminar);
            }
        }
    }

    // ===== LIMPIEZA AUTOMÁTICA DE DATOS =====

    private void limpiarDatosAutomaticamente() {
        // Ejecutar limpieza silenciosa en segundo plano
        db.collection("usuarios")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int usuariosActualizados = 0;

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        Boolean activo = doc.getBoolean("activo");
                        String estado = doc.getString("estado");

                        // Si no tiene el campo activo, agregarlo
                        if (activo == null) {
                            boolean nuevoActivo = (estado == null || estado.equalsIgnoreCase("activo"));

                            java.util.Map<String, Object> updates = new java.util.HashMap<>();
                            updates.put("activo", nuevoActivo);

                            // Log silencioso para debugging
                            if (estado != null) {
                                android.util.Log.d("GestionUsuarios",
                                    "Auto-migración: " + doc.getId() + " de estado='" + estado + "' a activo=" + nuevoActivo);
                            } else {
                                android.util.Log.d("GestionUsuarios",
                                    "Auto-migración: " + doc.getId() + " agregando activo=" + nuevoActivo);
                            }

                            db.collection("usuarios")
                                    .document(doc.getId())
                                    .update(updates)
                                    .addOnSuccessListener(aVoid ->
                                        android.util.Log.d("GestionUsuarios", "Auto-migración exitosa: " + doc.getId())
                                    )
                                    .addOnFailureListener(e ->
                                        android.util.Log.e("GestionUsuarios", "Error en auto-migración: " + doc.getId(), e)
                                    );

                            usuariosActualizados++;
                        }
                    }

                    // Solo mostrar mensaje si se actualizaron usuarios
                    if (usuariosActualizados > 0) {
                        final int actualizados = usuariosActualizados;
                        android.util.Log.i("GestionUsuarios",
                            "Limpieza automática completada: " + actualizados + " usuarios actualizados");
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("GestionUsuarios", "Error en limpieza automática", e);
                });
    }
}
