package com.centroalerce.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.centroalerce.gestion.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class ActivityRescheduleFragment extends Fragment {

    private String activityId;
    private FirebaseFirestore db;

    private AutoCompleteTextView acCita;
    private TextInputEditText etFecha, etHora;

    private static class CitaRef {
        String id; Timestamp ts; String label;
        CitaRef(String id, Timestamp ts, String label){ this.id=id; this.ts=ts; this.label=label; }
        @Override public String toString(){ return label; }
    }
    private final List<CitaRef> citas = new ArrayList<>();
    private CitaRef seleccionada;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activity_reschedule, c, false);
        db = FirebaseFirestore.getInstance();
        if (getArguments()!=null) activityId = getArguments().getString("activityId");

        acCita = v.findViewById(R.id.acCita);
        etFecha = v.findViewById(R.id.etFecha);
        etHora  = v.findViewById(R.id.etHora);

        etFecha.setOnClickListener(x -> pickFecha());
        etHora.setOnClickListener(x -> pickHora());

        v.findViewById(R.id.btnActualizar).setOnClickListener(this::onActualizar);
        v.findViewById(R.id.btnAgregar).setOnClickListener(this::onAgregar);

        cargarCitas();
        return v;
    }

    private void cargarCitas() {
        db.collection("activities").document(activityId)
                .collection("citas").orderBy("startAt", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(qs -> {
                    citas.clear();
                    for (QueryDocumentSnapshot d : qs) {
                        com.google.firebase.Timestamp ts = d.getTimestamp("startAt"); // o (Timestamp) d.get("startAt")
                        String label = fmt(ts);
                        citas.add(new CitaRef(d.getId(), ts, label));
                    }

                    ArrayAdapter<CitaRef> ad = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, citas);
                    acCita.setAdapter(ad);
                    acCita.setOnItemClickListener((parent, view, position, id) -> seleccionada = citas.get(position));
                    if (!citas.isEmpty()) { acCita.setText(citas.get(0).label, false); seleccionada = citas.get(0); }
                })
                .addOnFailureListener(e -> Snackbar.make(requireView(),"Error cargando citas: "+e.getMessage(),Snackbar.LENGTH_LONG).show());
    }

    private void onActualizar(View v) {
        if (seleccionada == null) { Snackbar.make(v,"Selecciona una cita",Snackbar.LENGTH_LONG).show(); return; }
        Timestamp ts = composeTimestamp();
        if (ts==null){ Snackbar.make(v,"Completa fecha y hora",Snackbar.LENGTH_LONG).show(); return; }

        DocumentReference ref = db.collection("activities").document(activityId)
                .collection("citas").document(seleccionada.id);

        ref.update("startAt", ts)
                .addOnSuccessListener(x -> {
                    Snackbar.make(v,"Cita actualizada",Snackbar.LENGTH_LONG).show();
                    Navigation.findNavController(v).popBackStack();
                })
                .addOnFailureListener(e -> Snackbar.make(v,"Error: "+e.getMessage(),Snackbar.LENGTH_LONG).show());
    }

    private void onAgregar(View v) {
        Timestamp ts = composeTimestamp();
        if (ts==null){ Snackbar.make(v,"Completa fecha y hora",Snackbar.LENGTH_LONG).show(); return; }

        Map<String,Object> cita = new HashMap<>();
        cita.put("startAt", ts);
        cita.put("estado", "PROGRAMADA");

        db.collection("activities").document(activityId)
                .collection("citas").add(cita)
                .addOnSuccessListener(x -> {
                    Snackbar.make(v,"Nueva cita agregada",Snackbar.LENGTH_LONG).show();
                    Navigation.findNavController(v).popBackStack();
                })
                .addOnFailureListener(e -> Snackbar.make(v,"Error: "+e.getMessage(),Snackbar.LENGTH_LONG).show());
    }

    private void pickFecha(){
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(),
                (p, y, m, d) -> etFecha.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m+1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void pickHora(){
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(requireContext(),
                (p, h, min) -> etHora.setText(String.format(Locale.getDefault(), "%02d:%02d", h, min)),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private @Nullable Timestamp composeTimestamp(){
        String f = etFecha.getText()==null?"":etFecha.getText().toString().trim();
        String h = etHora.getText()==null?"":etHora.getText().toString().trim();
        if (TextUtils.isEmpty(f) || TextUtils.isEmpty(h)) return null;
        try {
            String[] parts = h.split(":");
            LocalDate d = LocalDate.parse(f);
            LocalTime t = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            ZonedDateTime zdt = d.atTime(t).atZone(ZoneId.systemDefault());
            return new Timestamp(Date.from(zdt.toInstant()));
        } catch (Exception e) { return null; }
    }

    private static String fmt(Timestamp ts){
        Date d = ts.toDate();
        return android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", d).toString();
    }
}
