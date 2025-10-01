package com.centroalerce.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.centroalerce.gestion.R;
import java.util.Calendar;

public class ActivityFormFragment extends Fragment {

    private EditText etNombre, etFecha, etHora, etCupo, etLugar, etOferente, etSocio;
    private RadioGroup rgPeriodicidad;

    public ActivityFormFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activity_form, c, false);

        etNombre = v.findViewById(R.id.etNombre);
        etFecha  = v.findViewById(R.id.etFecha);
        etHora   = v.findViewById(R.id.etHora);
        etCupo   = v.findViewById(R.id.etCupo);
        etLugar  = v.findViewById(R.id.etLugar);
        etOferente = v.findViewById(R.id.etOferente);
        etSocio    = v.findViewById(R.id.etSocio);
        rgPeriodicidad = v.findViewById(R.id.rgPeriodicidad);

        // DatePicker
        etFecha.setOnClickListener(x -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, y, m, d) -> {
                String mm = String.format("%02d", m+1);
                String dd = String.format("%02d", d);
                etFecha.setText(y+"-"+mm+"-"+dd);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // TimePicker
        etHora.setOnClickListener(x -> {
            Calendar cal = Calendar.getInstance();
            new TimePickerDialog(requireContext(), (view, h, m) ->
                    etHora.setText(String.format("%02d:%02d", h,m)),
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        });

        // Guardar
        Button btnGuardar = v.findViewById(R.id.btnGuardar);
        btnGuardar.setOnClickListener(x -> onSave());

        // Cancelar
        v.findViewById(R.id.btnCancelar).setOnClickListener(x -> requireActivity().onBackPressed());
        return v;
    }

    private void onSave() {
        if (TextUtils.isEmpty(etNombre.getText())) { etNombre.setError("Requerido"); return; }
        if (TextUtils.isEmpty(etFecha.getText()))  { etFecha.setError("Requerido");  return; }
        if (TextUtils.isEmpty(etHora.getText()))   { etHora.setError("Requerido");   return; }

        // TODO: construir objeto Actividad + Cita y guardar en Firestore.
        // TODO: si es periódica, generar múltiples citas.

        Toast.makeText(getContext(), "Actividad guardada (demo)", Toast.LENGTH_SHORT).show();
        requireActivity().onBackPressed();
    }
}
