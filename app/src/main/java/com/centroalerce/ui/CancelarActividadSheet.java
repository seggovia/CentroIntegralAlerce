package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.*;
import com.centroalerce.gestion.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class CancelarActividadSheet extends BottomSheetDialogFragment {
    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    private static final String ARG_CITA_ID = "citaId"; // opcional

    public static CancelarActividadSheet newInstance(@NonNull String actividadId, @Nullable String citaId){
        Bundle b = new Bundle();
        b.putString(ARG_ACTIVIDAD_ID, actividadId);
        if (!TextUtils.isEmpty(citaId)) b.putString(ARG_CITA_ID, citaId);
        CancelarActividadSheet s = new CancelarActividadSheet();
        s.setArguments(b);
        return s;
    }

    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance().collection(preferEN ? COL_EN : COL_ES).document(actividadId);
    }

    private FirebaseFirestore db;
    private String actividadId, citaId;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.sheet_cancelar_actividad, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v,b);
        db = FirebaseFirestore.getInstance();
        Bundle args = getArguments();
        actividadId = args!=null ? args.getString(ARG_ACTIVIDAD_ID) : null;
        citaId      = args!=null ? args.getString(ARG_CITA_ID)      : null;

        TextInputEditText etMotivo = v.findViewById(R.id.etMotivo);
        v.findViewById(R.id.btnCancelarAccion).setOnClickListener(x -> dismiss());
        v.findViewById(R.id.btnCancelarActividad).setOnClickListener(x -> {
            String motivo = etMotivo.getText()!=null ? etMotivo.getText().toString().trim() : "";
            if (TextUtils.isEmpty(motivo) || motivo.length()<6) {
                etMotivo.setError("Describe el motivo (≥ 6)");
                return;
            }
            if (TextUtils.isEmpty(actividadId)) { toast("Falta actividadId"); return; }

            // Acción: cancelar cita específica o toda la actividad
            if (!TextUtils.isEmpty(citaId)) cancelarSoloCita(motivo);
            else cancelarActividadCompleta(motivo);
        });
    }

    private void cancelarSoloCita(String motivo){
        DocumentReference citaES = act(actividadId,false).collection("citas").document(citaId);
        DocumentReference citaEN = act(actividadId,true ).collection("citas").document(citaId);

        citaES.get().addOnSuccessListener(d -> {
            DocumentReference ref = (d!=null && d.exists()) ? citaES : citaEN;
            Map<String,Object> updates = new HashMap<>();
            updates.put("estado","cancelada");
            updates.put("motivo_cancelacion", motivo);
            updates.put("fecha_cancelacion", Timestamp.now());

            ref.update(updates)
                    .addOnSuccessListener(u -> {
                        toast("Cita cancelada");
                        registrarAuditoria("cancelar_cita", motivo);
                        notifyChanged();
                        dismiss();
                    })
                    .addOnFailureListener(e -> toast("Error: "+e.getMessage()));
        }).addOnFailureListener(e -> toast("Error: "+e.getMessage()));
    }

    private void cancelarActividadCompleta(String motivo){
        WriteBatch batch = db.batch();
        DocumentReference actES = act(actividadId,false);
        DocumentReference actEN = act(actividadId,true);

        actES.get().addOnSuccessListener(aes -> {
            DocumentReference actRef = (aes!=null && aes.exists()) ? actES : actEN;

            // 🔹 Actualizar actividad principal
            Map<String,Object> updates = new HashMap<>();
            updates.put("estado","cancelada");
            updates.put("motivo_cancelacion", motivo);
            updates.put("fecha_cancelacion", Timestamp.now());
            batch.update(actRef, updates);

            // 🔹 Cancelar todas las citas dentro de la actividad
            actRef.collection("citas").get().addOnSuccessListener(q -> {
                for (DocumentSnapshot d : q.getDocuments()){
                    batch.update(d.getReference(), updates);
                }
                batch.commit()
                        .addOnSuccessListener(u -> {
                            toast("Actividad cancelada");
                            registrarAuditoria("cancelar_actividad", motivo);
                            notifyChanged();
                            dismiss();
                        })
                        .addOnFailureListener(e -> toast("Error: "+e.getMessage()));
            }).addOnFailureListener(e -> toast("Error listando citas: "+e.getMessage()));
        }).addOnFailureListener(e -> toast("Error: "+e.getMessage()));
    }

    private void registrarAuditoria(String accion, String motivo){
        try {
            Map<String,Object> audit = new HashMap<>();
            audit.put("accion", accion);
            audit.put("motivo", motivo);
            audit.put("timestamp", Timestamp.now());
            audit.put("actividadId", actividadId);
            if (!TextUtils.isEmpty(citaId)) audit.put("citaId", citaId);

            db.collection("auditoria").add(audit);
        } catch (Exception ignored) {}
    }

    private void notifyChanged(){
        // para que tu ActivityDetailBottomSheet refresque su estado y color
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        if (!TextUtils.isEmpty(citaId)) b.putString("citaId", citaId);
        getParentFragmentManager().setFragmentResult("actividad_change", b);
    }

    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
}
