package com.centroalerce.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.centroalerce.gestion.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ActivityFormFragment extends Fragment {

    private EditText etNombre, etCupo, etLugar, etOferente, etSocio, etFecha, etHora;
    private RadioGroup rgPeriodicidad;
    private RadioButton rbPuntual, rbPeriodica;
    private Button btnCancelar, btnGuardar;

    private FirebaseFirestore db;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activity_form, c, false);

        etNombre = v.findViewById(R.id.etNombre);
        etCupo   = v.findViewById(R.id.etCupo);
        etLugar  = v.findViewById(R.id.etLugar);
        etOferente = v.findViewById(R.id.etOferente);
        etSocio    = v.findViewById(R.id.etSocio);
        etFecha  = v.findViewById(R.id.etFecha);
        etHora   = v.findViewById(R.id.etHora);

        rgPeriodicidad = v.findViewById(R.id.rgPeriodicidad);
        rbPuntual      = v.findViewById(R.id.rbPuntual);
        rbPeriodica    = v.findViewById(R.id.rbPeriodica);

        btnCancelar = v.findViewById(R.id.btnCancelar);
        btnGuardar  = v.findViewById(R.id.btnGuardar);

        db = FirebaseFirestore.getInstance();

        // DatePicker
        etFecha.setOnClickListener(view -> showDatePicker());
        // TimePicker
        etHora.setOnClickListener(view -> showTimePicker());

        btnCancelar.setOnClickListener(view -> Navigation.findNavController(view).popBackStack());
        btnGuardar.setOnClickListener(view -> onGuardar(view));

        return v;
    }

    private void showDatePicker() {
        LocalDate base = LocalDate.now();
        DatePickerDialog dp = new DatePickerDialog(
                getContext(),
                (picker, y, m, d) -> etFecha.setText(String.format("%04d-%02d-%02d", y, m+1, d)),
                base.getYear(), base.getMonthValue()-1, base.getDayOfMonth()
        );
        dp.show();
    }

    private void showTimePicker() {
        LocalTime base = LocalTime.now().withSecond(0).withNano(0);
        TimePickerDialog tp = new TimePickerDialog(
                getContext(),
                (picker, h, min) -> etHora.setText(String.format("%02d:%02d", h, min)),
                base.getHour(), base.getMinute(), true
        );
        tp.show();
    }

    private void onGuardar(View root) {
        String nombre = etNombre.getText().toString().trim();
        String fecha  = etFecha.getText().toString().trim(); // yyyy-MM-dd
        String hora   = etHora.getText().toString().trim();  // HH:mm

        if (TextUtils.isEmpty(nombre) || TextUtils.isEmpty(fecha) || TextUtils.isEmpty(hora)) {
            Snackbar.make(root, "Completa Nombre, Fecha y Hora", Snackbar.LENGTH_LONG).show();
            return;
        }

        String periodicidad = rbPeriodica.isChecked() ? "PERIODICA" : "PUNTUAL";

        Integer cupo = null;
        try { if (!TextUtils.isEmpty(etCupo.getText())) cupo = Integer.parseInt(etCupo.getText().toString().trim()); }
        catch (NumberFormatException ignored) {}

        String lugar   = etLugar.getText().toString().trim();
        String oferente= etOferente.getText().toString().trim();
        String socio   = etSocio.getText().toString().trim();

        Timestamp startAt = toStartAtTimestamp(fecha, hora);
        if (startAt == null) {
            Snackbar.make(root, "Fecha u hora inválida", Snackbar.LENGTH_LONG).show();
            return;
        }

        // Doc de actividad
        String activityId = db.collection("activities").document().getId();
        Map<String, Object> activityDoc = new HashMap<>();
        activityDoc.put("nombre", nombre);
        activityDoc.put("periodicidad", periodicidad);
        if (cupo != null) activityDoc.put("cupo", cupo);
        if (!TextUtils.isEmpty(oferente)) activityDoc.put("oferente", oferente);
        if (!TextUtils.isEmpty(socio)) activityDoc.put("socioComunitario", socio);
        activityDoc.put("createdAt", FieldValue.serverTimestamp());
        activityDoc.put("updatedAt", FieldValue.serverTimestamp());

        // Cita única (puntual). Si luego agregas “periódica”, generas varias.
        Map<String, Object> citaDoc = new HashMap<>();
        citaDoc.put("startAt", startAt);
        if (!TextUtils.isEmpty(lugar)) citaDoc.put("lugarNombre", lugar);
        citaDoc.put("estado", "PROGRAMADA");
        // para que el calendario muestre el título sin ir a la actividad
        citaDoc.put("titulo", nombre);

        // Batch
        WriteBatch batch = db.batch();
        batch.set(db.collection("activities").document(activityId), activityDoc);
        batch.set(db.collection("activities").document(activityId)
                .collection("citas").document(), citaDoc);

        btnGuardar.setEnabled(false);
        batch.commit()
                .addOnSuccessListener(ignored -> {
                    Snackbar.make(root, "Actividad creada y cita generada", Snackbar.LENGTH_LONG).show();
                    Navigation.findNavController(root).popBackStack(); // vuelve (calendario debería escucharla)
                })
                .addOnFailureListener(e -> {
                    btnGuardar.setEnabled(true);
                    Snackbar.make(root, "Error al guardar: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
    }

    /** Convierte "yyyy-MM-dd" + "HH:mm" a Timestamp en la zona del dispositivo */
    @Nullable
    private Timestamp toStartAtTimestamp(@NonNull String yyyyMMdd, @NonNull String HHmm) {
        try {
            String[] parts = HHmm.split(":");
            LocalDate d = LocalDate.parse(yyyyMMdd);
            LocalTime t = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            ZonedDateTime zdt = d.atTime(t).atZone(ZoneId.systemDefault());
            return new Timestamp(Date.from(zdt.toInstant()));
        } catch (Exception e) {
            return null;
        }
    }
}
