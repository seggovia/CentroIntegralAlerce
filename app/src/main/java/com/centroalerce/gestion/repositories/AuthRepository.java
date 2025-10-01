package com.centroalerce.gestion.repositories;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;
import com.centroalerce.gestion.models.Usuario;

import java.util.HashMap;
import java.util.Map;

public class AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public AuthRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    // Obtener usuario actual
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    // Login
    public void login(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        callback.onSuccess(user.getUid());
                    } else {
                        callback.onError("Error al obtener usuario");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Registrar usuario
    public void register(String email, String password, String nombre, String rol, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser != null) {
                        // Crear documento de usuario en Firestore
                        Usuario usuario = new Usuario(
                                firebaseUser.getUid(),
                                email,
                                nombre,
                                rol,
                                Timestamp.now(),
                                true
                        );
                        saveUserToFirestore(usuario, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Guardar usuario en Firestore
    private void saveUserToFirestore(Usuario usuario, AuthCallback callback) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", usuario.getUid());
        userData.put("email", usuario.getEmail());
        userData.put("nombre", usuario.getNombre());
        userData.put("rol", usuario.getRol());
        userData.put("fechaCreacion", usuario.getFechaCreacion());
        userData.put("activo", usuario.isActivo());

        db.collection("usuarios")
                .document(usuario.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> callback.onSuccess(usuario.getUid()))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Recuperar contraseña
    public void resetPassword(String email, AuthCallback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> callback.onSuccess("Email enviado"))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Cerrar sesión
    public void logout() {
        auth.signOut();
    }

    // Interface para callbacks
    public interface AuthCallback {
        void onSuccess(String message);
        void onError(String error);
    }
}