package com.centroalerce.gestion.repositories;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MantenedorRepository<T> {
    private final FirebaseFirestore db;
    private final String collectionName;
    private final Class<T> type;

    public MantenedorRepository(String collectionName, Class<T> type) {
        this.db = FirebaseFirestore.getInstance();
        this.collectionName = collectionName;
        this.type = type;
    }

    // Crear
    public void create(Map<String, Object> data, CreateCallback callback) {
        db.collection(collectionName)
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    String id = documentReference.getId();
                    documentReference.update("id", id);
                    callback.onSuccess(id);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener todos
    public void getAll(ListCallback<T> callback) {
        db.collection(collectionName)
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<T> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        T item = doc.toObject(type);
                        items.add(item);
                    }
                    callback.onSuccess(items);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Obtener por ID
    public void getById(String id, ItemCallback<T> callback) {
        db.collection(collectionName)
                .document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        T item = documentSnapshot.toObject(type);
                        callback.onSuccess(item);
                    } else {
                        callback.onError("No encontrado");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Actualizar
    public void update(String id, Map<String, Object> data, SimpleCallback callback) {
        db.collection(collectionName)
                .document(id)
                .update(data)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Eliminar (soft delete)
    public void delete(String id, SimpleCallback callback) {
        db.collection(collectionName)
                .document(id)
                .update("activo", false)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Interfaces para callbacks
    public interface CreateCallback {
        void onSuccess(String id);
        void onError(String error);
    }

    public interface ItemCallback<T> {
        void onSuccess(T item);
        void onError(String error);
    }

    public interface ListCallback<T> {
        void onSuccess(List<T> items);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }
}