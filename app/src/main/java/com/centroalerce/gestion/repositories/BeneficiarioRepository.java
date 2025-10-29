// com.centroalerce.gestion.repositories.BeneficiarioRepository.java
package com.centroalerce.gestion.repositories;

import androidx.annotation.NonNull;

import com.centroalerce.gestion.models.Beneficiario;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;

public class BeneficiarioRepository {
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
                        if (b != null) {
                            b.setId(d.getId());
                            list.add(b);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }
}
