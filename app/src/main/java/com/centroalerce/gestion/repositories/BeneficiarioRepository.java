// com.centroalerce.gestion.repositories.BeneficiarioRepository.java
package com.centroalerce.gestion.repositories;

import android.text.TextUtils;
import androidx.annotation.NonNull;

import com.centroalerce.gestion.models.Beneficiario;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;

public class BeneficiarioRepository {
    private static final String TAG = "BeneficiarioRepo";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface ListCallback {
        void onSuccess(List<Beneficiario> items);
        void onError(Exception e);
    }

    public void getAll(@NonNull ListCallback callback) {
        db.collection("beneficiarios")
                .orderBy("nombre", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Beneficiario> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Beneficiario b = d.toObject(Beneficiario.class);

                        // ✅ VALIDACIÓN CRÍTICA: Filtrar beneficiarios sin nombre
                        if (b != null && !TextUtils.isEmpty(b.getNombre())) {
                            b.setId(d.getId());
                            list.add(b);
                        } else {
                            // ⚠️ LOG de advertencia para detectar datos inválidos
                            android.util.Log.w(TAG,
                                    "⚠️ Beneficiario inválido encontrado - ID: " + d.getId() +
                                            ", Nombre: " + (b != null ? b.getNombre() : "null"));
                        }
                    }

                    android.util.Log.d(TAG, "✅ Cargados " + list.size() + " beneficiarios válidos");
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "❌ Error cargando beneficiarios: " + e.getMessage(), e);
                    callback.onError(e);
                });
    }
}