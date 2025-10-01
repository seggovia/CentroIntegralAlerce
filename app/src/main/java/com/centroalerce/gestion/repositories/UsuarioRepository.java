package com.centroalerce.gestion.repositories;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.centroalerce.gestion.models.Usuario;

import java.util.ArrayList;
import java.util.List;

public class UsuarioRepository {
    private final FirebaseFirestore db;

    public UsuarioRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // Obtener un usuario por UID
    public void getUsuario(String uid, UsuarioCallback callback) {
        db.collection("usuarios")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Usuario usuario = documentSnapshot.toObject(Usuario.class);
                        callback.onSuccess(usuario);
                    } else {
                        callback.onError("Usuario no encontrado");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener todos los usuarios
    public void getAllUsuarios(UsuariosCallback callback) {
        db.collection("usuarios")
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Usuario> usuarios = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Usuario usuario = doc.toObject(Usuario.class);
                        usuarios.add(usuario);
                    }
                    callback.onSuccess(usuarios);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Actualizar usuario
    public void updateUsuario(String uid, Usuario usuario, SimpleCallback callback) {
        db.collection("usuarios")
                .document(uid)
                .set(usuario)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Desactivar usuario (soft delete)
    public void deactivateUsuario(String uid, SimpleCallback callback) {
        db.collection("usuarios")
                .document(uid)
                .update("activo", false)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Interfaces para callbacks
    public interface UsuarioCallback {
        void onSuccess(Usuario usuario);
        void onError(String error);
    }

    public interface UsuariosCallback {
        void onSuccess(List<Usuario> usuarios);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }
}