package com.centroalerce.ui;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

public class RegistroUsuariosFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseFunctions functions;
    private ProgressDialog progressDialog;
    
    private com.google.android.material.textfield.TextInputLayout tilEmail;
    private com.google.android.material.textfield.TextInputLayout tilPassword;
    private com.google.android.material.textfield.TextInputLayout tilConfirmPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private AutoCompleteTextView actvRol;
    private MaterialButton btnCancelar;
    private MaterialButton btnRegistrar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registro_usuarios, container, false);
    }

    private void showProgress(String message) {
        if (progressDialog == null) return;
        progressDialog.setMessage(message);
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance("us-central1");
        progressDialog = new ProgressDialog(requireContext());
        progressDialog.setCancelable(false);
        progressDialog.setMessage("Procesando...");

        // Ejecutar limpieza automática de datos inconsistentes
        limpiarDatosAutomaticamente();

        // Inicializar vistas
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        // Obtener TILs padres para mostrar errores inline
        try { tilEmail = (com.google.android.material.textfield.TextInputLayout) ((View) etEmail.getParent()).getParent(); } catch (Exception ignore) {}
        try { tilPassword = (com.google.android.material.textfield.TextInputLayout) ((View) etPassword.getParent()).getParent(); } catch (Exception ignore) {}
        try { tilConfirmPassword = (com.google.android.material.textfield.TextInputLayout) ((View) etConfirmPassword.getParent()).getParent(); } catch (Exception ignore) {}
        actvRol = view.findViewById(R.id.actvRol);
        btnCancelar = view.findViewById(R.id.btnCancelar);
        btnRegistrar = view.findViewById(R.id.btnRegistrar);

        // Configurar combobox de roles
        configurarComboBoxRoles();

        // Agregar TextWatchers para limpiar errores mientras el usuario escribe
        etEmail.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilEmail != null) { tilEmail.setError(null); tilEmail.setErrorEnabled(false); }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        etPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilPassword != null) { tilPassword.setError(null); tilPassword.setErrorEnabled(false); }

                // También validar la confirmación si ya tiene texto
                String confirmPassword = etConfirmPassword.getText().toString();
                if (!confirmPassword.isEmpty() && !s.toString().equals(confirmPassword)) {
                    if (tilConfirmPassword != null) {
                        tilConfirmPassword.setError("Las contraseñas no coinciden");
                        tilConfirmPassword.setErrorEnabled(true);
                    } else {
                        etConfirmPassword.setError("Las contraseñas no coinciden");
                    }
                } else if (!confirmPassword.isEmpty()) {
                    if (tilConfirmPassword != null) {
                        tilConfirmPassword.setError(null);
                        tilConfirmPassword.setErrorEnabled(false);
                    } else {
                        etConfirmPassword.setError(null);
                    }
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        etConfirmPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Validar en tiempo real si las contraseñas coinciden
                String password = etPassword.getText().toString();
                String confirmPassword = s.toString();

                if (!confirmPassword.isEmpty() && !password.equals(confirmPassword)) {
                    if (tilConfirmPassword != null) {
                        tilConfirmPassword.setError("Las contraseñas no coinciden");
                        tilConfirmPassword.setErrorEnabled(true);
                    } else {
                        etConfirmPassword.setError("Las contraseñas no coinciden");
                    }
                } else {
                    if (tilConfirmPassword != null) {
                        tilConfirmPassword.setError(null);
                        tilConfirmPassword.setErrorEnabled(false);
                    } else {
                        etConfirmPassword.setError(null);
                    }
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Botón de retroceso
        MaterialButton btnVolver = view.findViewById(R.id.btnVolver);
        if (btnVolver != null) {
            btnVolver.setOnClickListener(v -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            });
        }

        // Botón cancelar
        btnCancelar.setOnClickListener(v -> {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
        });

        // Botón registrar
        btnRegistrar.setOnClickListener(v -> registrarUsuario());
    }

    private void configurarComboBoxRoles() {
        String[] roles = {"Administrador", "Usuario", "Visualizador"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_dropdown_item_1line, roles);
        actvRol.setAdapter(adapter);
        actvRol.setText("Usuario", false); // Valor por defecto
        
        // Configuraciones adicionales para que funcione como dropdown
        actvRol.setThreshold(0); // Mostrar opciones inmediatamente
        actvRol.setOnClickListener(v -> {
            actvRol.showDropDown();
        });
        actvRol.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                actvRol.showDropDown();
            }
        });
    }

    private void registrarUsuario() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String rol = actvRol.getText().toString().trim();

        // Validaciones
        if (TextUtils.isEmpty(email)) {
            if (tilEmail != null) { tilEmail.setError("El email es requerido"); tilEmail.setErrorEnabled(true); }
            else etEmail.setError("El email es requerido");
            etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (tilEmail != null) { tilEmail.setError("Email inválido"); tilEmail.setErrorEnabled(true); }
            else etEmail.setError("Email inválido");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            if (tilPassword != null) { tilPassword.setError("La contraseña es requerida"); tilPassword.setErrorEnabled(true); }
            else etPassword.setError("La contraseña es requerida");
            etPassword.requestFocus();
            return;
        }

        // Validar longitud mínima de contraseña
        if (password.length() < 8) {
            if (tilPassword != null) { tilPassword.setError("Mínimo 8 caracteres"); tilPassword.setErrorEnabled(true); }
            else etPassword.setError("La contraseña debe tener al menos 8 caracteres");
            etPassword.requestFocus();
            return;
        }

        // Validar que la contraseña tenga mayúsculas, minúsculas y números
        boolean hasUpper = false, hasLower = false, hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isLowerCase(c)) hasLower = true;
            if (Character.isDigit(c)) hasDigit = true;
        }

        if (!hasUpper) {
            if (tilPassword != null) { tilPassword.setError("Debe incluir al menos una mayúscula"); tilPassword.setErrorEnabled(true); }
            else etPassword.setError("La contraseña debe tener al menos una mayúscula");
            etPassword.requestFocus();
            return;
        }
        if (!hasLower) {
            if (tilPassword != null) { tilPassword.setError("Debe incluir al menos una minúscula"); tilPassword.setErrorEnabled(true); }
            else etPassword.setError("La contraseña debe tener al menos una minúscula");
            etPassword.requestFocus();
            return;
        }
        if (!hasDigit) {
            if (tilPassword != null) { tilPassword.setError("Debe incluir al menos un número"); tilPassword.setErrorEnabled(true); }
            else etPassword.setError("La contraseña debe tener al menos un número");
            etPassword.requestFocus();
            return;
        }

        // Validar confirmación de contraseña
        if (TextUtils.isEmpty(confirmPassword)) {
            if (tilConfirmPassword != null) { tilConfirmPassword.setError("Confirma tu contraseña"); tilConfirmPassword.setErrorEnabled(true); }
            else etConfirmPassword.setError("Confirma tu contraseña");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            if (tilConfirmPassword != null) { tilConfirmPassword.setError("Las contraseñas no coinciden"); tilConfirmPassword.setErrorEnabled(true); }
            else etConfirmPassword.setError("Las contraseñas no coinciden");
            etConfirmPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(rol)) {
            actvRol.setError("El rol es requerido");
            actvRol.requestFocus();
            return;
        }

        // Deshabilitar botón para evitar múltiples registros
        btnRegistrar.setEnabled(false);
        btnRegistrar.setText("Validando...");
        showProgress("Validando información...");

        // Verificar si el correo ya está registrado ANTES de intentar crear el usuario
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        // Error al verificar
                        btnRegistrar.setEnabled(true);
                        btnRegistrar.setText("Registrar Usuario");
                        hideProgress();
                        mostrarError("No se pudo validar el correo, intenta nuevamente");
                        return;
                    }

                    // Verificar si el correo ya existe
                    com.google.firebase.auth.SignInMethodQueryResult result = task.getResult();
                    boolean emailYaRegistrado = result != null &&
                                                result.getSignInMethods() != null &&
                                                !result.getSignInMethods().isEmpty();

                    if (emailYaRegistrado) {
                        // El correo existe en Firebase Auth, verificar si está inactivo en Firestore
                        btnRegistrar.setText("Verificando estado...");
                        showProgress("Verificando estado del usuario...");

                        db.collection("usuarios")
                                .whereEqualTo("email", email)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    if (!querySnapshot.isEmpty()) {
                                        // El usuario existe en Firestore
                                        com.google.firebase.firestore.DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                                        Boolean activo = doc.getBoolean("activo");

                                        if (activo != null && !activo) {
                                            // Usuario inactivo, reactivarlo
                                            showProgress("Reactivando usuario...");
                                            reactivarUsuario(doc.getId(), email, rol);
                                        } else {
                                            // Usuario activo, no se puede registrar
                                            btnRegistrar.setEnabled(true);
                                            btnRegistrar.setText("Registrar Usuario");
                                            hideProgress();
                                            if (tilEmail != null) {
                                                tilEmail.setError("Este correo ya está registrado y activo");
                                                tilEmail.setErrorEnabled(true);
                                            } else {
                                                etEmail.setError("Este correo ya está registrado y activo");
                                            }
                                            etEmail.requestFocus();
                                            com.google.android.material.snackbar.Snackbar.make(
                                                requireView(),
                                                "Este correo ya está registrado en el sistema",
                                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                                            ).show();
                                        }
                                    } else {
                                        // Existe en Auth pero no en Firestore
                                        // Intentar crearlo nuevamente (Firebase Auth rechazará si ya existe)
                                        btnRegistrar.setText("Registrando...");
                                        showProgress("Registrando usuario...");
                                        crearUsuarioEnFirebase(email, password, rol);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    btnRegistrar.setEnabled(true);
                                    btnRegistrar.setText("Registrar Usuario");
                                    hideProgress();
                                    mostrarError("Error al verificar usuario: " + e.getMessage());
                                });
                    } else {
                        // El correo está disponible, proceder con el registro
                        btnRegistrar.setText("Registrando...");
                        showProgress("Registrando usuario...");
                        crearUsuarioEnFirebase(email, password, rol);
                    }
                });
    }

    private void crearUsuarioEnFirebase(String email, String password, String rol) {
        // Verificar estado de autenticación ANTES de llamar a la función
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            com.google.android.material.snackbar.Snackbar.make(
                requireView(),
                "Error: No hay usuario autenticado",
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).show();
            android.util.Log.e("RegistroUsuarios", "getCurrentUser() retornó null");
            btnRegistrar.setEnabled(true);
            btnRegistrar.setText("Registrar Usuario");
            hideProgress();
            return;
        }

        android.util.Log.d("RegistroUsuarios", "Usuario autenticado: " + currentUser.getEmail() + " (UID: " + currentUser.getUid() + ")");

        // Cambiar botón a estado de carga
        btnRegistrar.setEnabled(false);
        btnRegistrar.setText("Registrando...");
        showProgress("Registrando usuario...");

        // Forzar refresh del token antes de llamar a la Cloud Function
        currentUser.getIdToken(true)
                .addOnSuccessListener(getTokenResult -> {
                    android.util.Log.d("RegistroUsuarios", "Token refreshed exitosamente");

                    // Usar Cloud Function para crear usuario sin cerrar sesión del administrador
                    Map<String, Object> data = new HashMap<>();
                    data.put("email", email);
                    data.put("password", password);
                    data.put("rol", rol);

                    functions.getHttpsCallable("createUser")
                            .call(data)
                            .addOnSuccessListener(result -> {
                                // Limpiar campos
                                etEmail.setText("");
                                etPassword.setText("");
                                etConfirmPassword.setText("");
                                actvRol.setText("", false);

                                // Rehabilitar botón
                                btnRegistrar.setEnabled(true);
                                btnRegistrar.setText("Registrar Usuario");

                                // Mostrar Snackbar de éxito
                                Snackbar snackbar = Snackbar.make(
                                    requireView(),
                                    "✓ Usuario registrado correctamente",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                                );
                                snackbar.setBackgroundTint(getResources().getColor(android.R.color.holo_green_dark));
                                snackbar.setTextColor(getResources().getColor(android.R.color.white));
                                snackbar.show();

                                // Volver a la pantalla anterior
                                hideProgress();
                                requireActivity().onBackPressed();
                            })
                            .addOnFailureListener(e -> {
                                hideProgress();
                                android.util.Log.e("RegistroUsuarios", "Error al crear usuario", e);

                                // Rehabilitar botón cuando hay error
                                btnRegistrar.setEnabled(true);
                                btnRegistrar.setText("Registrar Usuario");

                                String msg = e.getMessage();
                                if (msg != null && msg.toLowerCase().contains("already in use")) {
                                    // El correo ya está en uso - mostrar error en el campo
                                    if (tilEmail != null) {
                                        tilEmail.setError("Este correo ya está registrado");
                                        tilEmail.setErrorEnabled(true);
                                    } else {
                                        etEmail.setError("Este correo ya está registrado");
                                    }
                                    etEmail.requestFocus();
                                } else {
                                    mostrarError("Error al crear usuario: " + msg);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("RegistroUsuarios", "Error al refrescar token", e);

                    // Rehabilitar botón
                    btnRegistrar.setEnabled(true);
                    btnRegistrar.setText("Registrar Usuario");
                    hideProgress();

                    com.google.android.material.snackbar.Snackbar.make(
                        requireView(),
                        "Error de autenticación. Intenta cerrar sesión y volver a iniciar.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show();
                });
    }

    private void guardarUsuarioEnFirestore(String uid, String email, String rol) {
        Map<String, Object> usuario = new HashMap<>();
        usuario.put("uid", uid);
        usuario.put("email", email);
        usuario.put("rol", rol);
        usuario.put("fechaCreacion", com.google.firebase.Timestamp.now());
        usuario.put("activo", true);

        db.collection("usuarios")
                .document(uid)
                .set(usuario)
                .addOnSuccessListener(aVoid -> {
                    com.google.android.material.snackbar.Snackbar snackbar = com.google.android.material.snackbar.Snackbar.make(
                        requireView(),
                        "✓ Usuario registrado correctamente",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    );
                    snackbar.setBackgroundTint(getResources().getColor(android.R.color.holo_green_dark));
                    snackbar.setTextColor(getResources().getColor(android.R.color.white));
                    snackbar.show();

                    // Limpiar formulario
                    limpiarFormulario();

                    // Volver a la pantalla anterior
                    androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
                })
                .addOnFailureListener(e -> {
                    mostrarError("Error al guardar información del usuario: " + e.getMessage());
                });
    }

    private void reactivarUsuario(String uid, String email, String rol) {
        // Reactivar usuario que estaba marcado como inactivo
        Map<String, Object> updates = new HashMap<>();
        updates.put("activo", true);
        updates.put("rol", rol);
        updates.put("fechaCreacion", com.google.firebase.Timestamp.now());

        db.collection("usuarios")
                .document(uid)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    com.google.android.material.snackbar.Snackbar snackbar = com.google.android.material.snackbar.Snackbar.make(
                        requireView(),
                        "✓ Usuario reactivado correctamente",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    );
                    snackbar.setBackgroundTint(getResources().getColor(android.R.color.holo_green_dark));
                    snackbar.setTextColor(getResources().getColor(android.R.color.white));
                    snackbar.show();

                    // Limpiar formulario
                    limpiarFormulario();

                    // Volver a la pantalla anterior
                    androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
                    hideProgress();
                })
                .addOnFailureListener(e -> {
                    mostrarError("Error al reactivar usuario: " + e.getMessage());
                    hideProgress();
                });
    }

    private void limpiarFormulario() {
        etEmail.setText("");
        etPassword.setText("");
        etConfirmPassword.setText("");
        actvRol.setText("Usuario", false);
    }

    private void mostrarError(String mensaje) {
        // Mostrar error en el campo de email
        if (tilEmail != null) { tilEmail.setError(mensaje); tilEmail.setErrorEnabled(true); }
        else etEmail.setError(mensaje);

        // Mostrar Snackbar con el mensaje de error
        com.google.android.material.snackbar.Snackbar.make(
            requireView(),
            mensaje,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show();

        // Rehabilitar botón
        btnRegistrar.setEnabled(true);
        btnRegistrar.setText("Registrar Usuario");
        hideProgress();
    }

    private void mostrarDialogoEliminarCuentaAuth(String email, String password, String rol) {
        // Crear diálogo de confirmación
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());

        View dialogView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        android.widget.TextView tvTitulo = new android.widget.TextView(requireContext());
        android.widget.TextView tvMensaje = new android.widget.TextView(requireContext());

        tvTitulo.setText("Cuenta existente detectada");
        tvTitulo.setTextSize(20);
        tvTitulo.setPadding(50, 40, 50, 20);
        tvTitulo.setTextColor(getResources().getColor(android.R.color.black, null));

        tvMensaje.setText("Este correo ya existe en el sistema de autenticación pero fue eliminado de la base de datos.\n\n" +
                "¿Deseas eliminar completamente la cuenta anterior y crear una nueva?\n\n" +
                "Nota: Se usará la contraseña actual que ingresaste para verificar y eliminar la cuenta anterior.");
        tvMensaje.setPadding(50, 20, 50, 40);
        tvMensaje.setTextColor(getResources().getColor(android.R.color.darker_gray, null));

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(tvTitulo);
        layout.addView(tvMensaje);

        builder.setView(layout);
        builder.setPositiveButton("Eliminar y crear nuevo", (dialog, which) -> {
            eliminarCuentaAuthYCrearNueva(email, password, rol);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            btnRegistrar.setEnabled(true);
            btnRegistrar.setText("Registrar Usuario");
            dialog.dismiss();
        });

        builder.create().show();
    }

    private void eliminarCuentaAuthYCrearNueva(String email, String password, String rol) {
        btnRegistrar.setEnabled(false);
        btnRegistrar.setText("Eliminando cuenta anterior...");
        showProgress("Eliminando cuenta anterior...");

        // Iniciar sesión con la cuenta existente para poder eliminarla
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(signInTask -> {
                    if (signInTask.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Eliminar el usuario de Firebase Auth
                            user.delete()
                                    .addOnCompleteListener(deleteTask -> {
                                        if (deleteTask.isSuccessful()) {
                                            // Cuenta eliminada, ahora crear una nueva
                                            btnRegistrar.setText("Creando cuenta nueva...");
                                            showProgress("Creando cuenta nueva...");
                                            crearUsuarioEnFirebase(email, password, rol);
                                        } else {
                                            btnRegistrar.setEnabled(true);
                                            btnRegistrar.setText("Registrar Usuario");
                                            hideProgress();
                                            mostrarError("Error al eliminar cuenta anterior: " + deleteTask.getException().getMessage());
                                        }
                                    });
                        } else {
                            btnRegistrar.setEnabled(true);
                            btnRegistrar.setText("Registrar Usuario");
                            hideProgress();
                            mostrarError("Error: no se pudo obtener el usuario");
                        }
                    } else {
                        // La contraseña no coincide
                        btnRegistrar.setEnabled(true);
                        btnRegistrar.setText("Registrar Usuario");
                        hideProgress();

                        if (tilPassword != null) {
                            tilPassword.setError("La contraseña no coincide con la cuenta existente");
                            tilPassword.setErrorEnabled(true);
                        }

                        com.google.android.material.snackbar.Snackbar.make(
                                requireView(),
                                "Error: La contraseña no coincide con la cuenta existente en el sistema. " +
                                "Contacta al administrador del sistema para eliminar la cuenta manualmente.",
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void limpiarDatosAutomaticamente() {
        // Ejecutar limpieza silenciosa en segundo plano
        db.collection("usuarios")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int usuariosActualizados = 0;

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        Boolean activo = doc.getBoolean("activo");
                        String estado = doc.getString("estado");

                        // Si no existe el campo "activo", migrar desde "estado"
                        if (activo == null) {
                            // Si tiene estado="activo" o no tiene ninguno de los dos campos, considerar como activo
                            boolean nuevoActivo = (estado == null || estado.equalsIgnoreCase("activo"));

                            Map<String, Object> updates = new HashMap<>();
                            updates.put("activo", nuevoActivo);

                            db.collection("usuarios")
                                    .document(doc.getId())
                                    .update(updates)
                                    .addOnSuccessListener(aVoid ->
                                        android.util.Log.d("RegistroUsuarios", "Auto-migración exitosa: " + doc.getId())
                                    )
                                    .addOnFailureListener(e ->
                                        android.util.Log.e("RegistroUsuarios", "Error en auto-migración: " + doc.getId(), e)
                                    );

                            usuariosActualizados++;
                        }
                    }

                    if (usuariosActualizados > 0) {
                        android.util.Log.i("RegistroUsuarios", "Limpieza automática completada: " + usuariosActualizados + " usuarios actualizados");
                    }
                })
                .addOnFailureListener(e ->
                    android.util.Log.e("RegistroUsuarios", "Error en limpieza automática", e)
                );
    }
}
