package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModificarActividadSheet extends BottomSheetDialogFragment {

    public static ModificarActividadSheet newInstance(String actividadId) {
        ModificarActividadSheet f = new ModificarActividadSheet();
        Bundle b = new Bundle();
        b.putString("actividadId", actividadId);
        f.setArguments(b);
        return f;
    }

    private String actividadId;

    private EditText etNombre, etCupo;
    private AutoCompleteTextView actTipo, actPeriodicidad, actLugar, actOferente;
    private Button btnGuardar;

    private FirebaseFirestore db;

    // Soporte colección en EN/ES (hay mezcla en el proyecto)
    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";

    private int resId(String name, String defType) {
        return requireContext().getResources()
                .getIdentifier(name, defType, requireContext().getPackageName());
    }
    private int id(String viewIdName) { return resId(viewIdName, "id"); }
    private int layout(String layoutName) { return resId(layoutName, "layout"); }

    private DocumentReference actRef(boolean preferEN) {
        return FirebaseFirestore.getInstance()
                .collection(preferEN ? COL_EN : COL_ES)
                .document(actividadId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        int layoutId = layout("sheet_modificar_actividad");
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        actividadId = (getArguments() != null) ? getArguments().getString("actividadId", "") : "";
        db = FirebaseFirestore.getInstance();

        // Bind
        etNombre       = v.findViewById(id("etNombre"));
        etCupo         = v.findViewById(id("etCupo"));
        actTipo        = v.findViewById(id("actTipo"));
        actPeriodicidad= v.findViewById(id("actPeriodicidad"));
        actLugar       = v.findViewById(id("actLugar"));
        actOferente    = v.findViewById(id("actOferente"));
        btnGuardar     = v.findViewById(id("btnGuardarCambios"));

        // Opciones simples (si usas adapters propios, puedes quitarlos)
        setSimpleDropdown(actTipo, Arrays.asList(
                "Capacitación", "Taller", "Charlas", "Atenciones",
                "Operativo en oficina", "Operativo rural", "Operativo",
                "Práctica profesional", "Diagnostico"
        ));
        setSimpleDropdown(actPeriodicidad, Arrays.asList("PUNTUAL", "PERIODICA"));

        // Carga datos (intenta EN→ES)
        actRef(true).get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) bindDoc(doc);
            else actRef(false).get().addOnSuccessListener(this::bindDoc);
        }).addOnFailureListener(e -> toast("No se pudo cargar la actividad"));

        btnGuardar.setOnClickListener(view -> guardarCambios());
    }

    private void bindDoc(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return;

        String nombre = doc.getString("nombre");
        Long cupo     = (doc.get("cupo") instanceof Number) ? ((Number) doc.get("cupo")).longValue() : null;
        String tipo   = firstNonEmpty(
                doc.getString("tipo"),
                doc.getString("tipoActividad"),
                doc.getString("tipo_actividad"),
                doc.getString("tipoNombre")
        );
        String periodicidad = firstNonEmpty(
                doc.getString("periodicidad"),
                doc.getString("frecuencia"),
                doc.getString("periodicidadNombre"),
                doc.getString("frecuenciaNombre")
        );
        String lugar = firstNonEmpty(
                doc.getString("lugarNombre"),
                doc.getString("lugar"),
                doc.getString("lugar_texto")
        );
        String oferente = firstNonEmpty(
                doc.getString("oferente"),
                doc.getString("oferenteNombre")
        );

        if (nombre != null) etNombre.setText(nombre);
        if (cupo != null)   etCupo.setText(String.valueOf(cupo));
        if (tipo != null)   actTipo.setText(tipo, false);
        if (periodicidad != null) actPeriodicidad.setText(periodicidad, false);
        if (lugar != null)  actLugar.setText(lugar, false);
        if (oferente != null) actOferente.setText(oferente, false);
    }

    private void guardarCambios() {
        String nombre = textOf(etNombre);
        String sCupo  = textOf(etCupo);
        Integer cupo  = null;
        try { if (!sCupo.isEmpty()) cupo = Integer.parseInt(sCupo); } catch (Exception ignored){}

        String tipo         = textOf(actTipo);
        String periodicidad = textOf(actPeriodicidad);
        String lugar        = textOf(actLugar);
        String oferente     = textOf(actOferente);

        if (TextUtils.isEmpty(nombre)) {
            toast("El nombre es obligatorio");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("nombre", nombre);
        if (!TextUtils.isEmpty(tipo)) {
            updates.put("tipo", tipo);
            updates.put("tipoActividad", tipo);
        }
        if (!TextUtils.isEmpty(periodicidad)) {
            updates.put("periodicidad", periodicidad);
        }
        if (cupo != null) updates.put("cupo", cupo);
        if (!TextUtils.isEmpty(lugar)) updates.put("lugarNombre", lugar);
        if (!TextUtils.isEmpty(oferente)) {
            // mantenemos ambos por compatibilidad
            updates.put("oferente", oferente);
            updates.put("oferentes", Arrays.asList(oferente));
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());

        // Primero intenta en EN; si no existe, en ES
        actRef(true).get().addOnSuccessListener(doc -> {
            DocumentReference ref = (doc != null && doc.exists()) ? actRef(true) : actRef(false);
            ref.update(updates)
                    .addOnSuccessListener(unused -> {
                        toast("Actividad actualizada");
                        // Notifica para que quien abrió el sheet refresque (si quiere escuchar)
                        Bundle res = new Bundle();
                        res.putBoolean("actividad_modificada", true);
                        getParentFragmentManager().setFragmentResult("actividad_change", res);
                        dismiss();
                    })
                    .addOnFailureListener(e -> toast("Error al actualizar: " + e.getMessage()));
        }).addOnFailureListener(e -> toast("Error al localizar la actividad"));
    }

    // ---- utils ----
    private void setSimpleDropdown(AutoCompleteTextView view, List<String> items) {
        if (view == null) return;
        android.widget.ArrayAdapter<String> adp =
                new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, items);
        view.setAdapter(adp);
    }

    private String textOf(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
    private String textOf(AutoCompleteTextView act) {
        CharSequence cs = act.getText();
        return cs == null ? "" : cs.toString().trim();
    }

    private String firstNonEmpty(String... xs) {
        if (xs == null) return null;
        for (String s : xs) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private void toast(String m) { Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
}
