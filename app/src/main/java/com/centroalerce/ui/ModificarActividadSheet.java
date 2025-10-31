package com.centroalerce.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.centroalerce.gestion.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FieldValue;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModificarActividadSheet extends BottomSheetDialogFragment {
    private static final String ARG_ACTIVIDAD_ID = "actividadId";
    public static ModificarActividadSheet newInstance(@NonNull String actividadId){
        Bundle b = new Bundle(); b.putString(ARG_ACTIVIDAD_ID, actividadId);
        ModificarActividadSheet s = new ModificarActividadSheet(); s.setArguments(b); return s;
    }

    // ===== Utils Firestore multi-colección (ES/EN) =====
    private static final String COL_EN = "activities";
    private static final String COL_ES = "actividades";
    private DocumentReference act(String actividadId, boolean preferEN) {
        return FirebaseFirestore.getInstance().collection(preferEN ? COL_EN : COL_ES).document(actividadId);
    }

    // ===== Modelo para dropdowns con id y nombre =====
    public static class OptionItem {
        public final String id;
        public final String nombre;
        public OptionItem(String id, String nombre) {
            this.id = id;
            this.nombre = nombre == null ? "" : nombre;
        }
        @Override public String toString() { return nombre; } // lo que ve el usuario
    }

    private FirebaseFirestore db;
    private String actividadId;

    private TextInputEditText etNombre, etCupo, etBeneficiarios, etDiasAviso;
    private AutoCompleteTextView actTipo, actPeriodicidad, actLugar, actOferente, actSocio, actProyecto;

    // Fallbacks
    private final String[] tiposFijos = new String[]{
            "Capacitación","Taller","Charlas","Atenciones","Operativo en oficina","Operativo rural","Operativo","Práctica profesional","Diagnostico"
    };
    private final String[] periodicidades = new String[]{"Puntual","Periódica"};

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.sheet_modificar_actividad, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v,b);
        db = FirebaseFirestore.getInstance();
        actividadId = getArguments()!=null ? getArguments().getString(ARG_ACTIVIDAD_ID) : null;

        etNombre = v.findViewById(R.id.etNombre);
        etCupo = v.findViewById(R.id.etCupo);
        etBeneficiarios = v.findViewById(R.id.etBeneficiarios);
        etDiasAviso = v.findViewById(R.id.etDiasAviso);

        actTipo = v.findViewById(R.id.actTipo);
        actPeriodicidad = v.findViewById(R.id.actPeriodicidad);
        actLugar = v.findViewById(R.id.actLugar);
        actOferente = v.findViewById(R.id.actOferente);
        actSocio = v.findViewById(R.id.actSocio);
        actProyecto = v.findViewById(R.id.actProyecto);

        // Estáticos
        actPeriodicidad.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, periodicidades));

        // Dinámicos con {id, nombre}
        cargarTiposActividad();
        cargarColeccionAOptions("lugares", actLugar);
        cargarColeccionAOptions("oferentes", actOferente);
        cargarColeccionAOptionsMulti(Arrays.asList("socios_comunitarios", "socios", "sociosComunitarios"), actSocio);
        cargarColeccionAOptions("proyectos", actProyecto);

        precargar();

        ((MaterialButton) v.findViewById(R.id.btnGuardarCambios)).setOnClickListener(x -> guardar());
    }

    // ================== Precargar & bind ==================
    private void precargar(){
        if (TextUtils.isEmpty(actividadId)) { toast("Falta actividadId"); return; }
        act(actividadId,true).get().addOnSuccessListener(doc -> {
            if (doc!=null && doc.exists()) bind(doc);
            else act(actividadId,false).get().addOnSuccessListener(this::bind)
                    .addOnFailureListener(e -> toast("No se pudo cargar"));
        }).addOnFailureListener(e -> toast("No se pudo cargar"));
    }

    private void bind(DocumentSnapshot doc){
        if (doc==null || !doc.exists()) return;

        // Texto
        set(etNombre, doc.getString("nombre"));
        Long cupo = doc.getLong("cupo"); if (cupo!=null) etCupo.setText(String.valueOf(cupo));

        // Tipo / Periodicidad (pueden venir en distintas claves)
        setDropText(actTipo, firstNonEmpty(doc.getString("tipoActividad"), doc.getString("tipo")));
        setDropText(actPeriodicidad, firstNonEmpty(doc.getString("periodicidad"), doc.getString("frecuencia")));

        // Beneficiarios texto
        String beneficiariosTxt = firstNonEmpty(doc.getString("beneficiariosTexto"));
        if (TextUtils.isEmpty(beneficiariosTxt)) {
            try {
                @SuppressWarnings("unchecked")
                List<String> lista = (List<String>) doc.get("beneficiarios");
                if (lista != null && !lista.isEmpty()) beneficiariosTxt = TextUtils.join(", ", lista);
            } catch (Exception ignored) {}
        }
        if (!TextUtils.isEmpty(beneficiariosTxt)) etBeneficiarios.setText(beneficiariosTxt);

        Long diasAviso = firstNonNull(doc.getLong("diasAviso"), doc.getLong("dias_aviso"),
                doc.getLong("diasAvisoPrevio"), doc.getLong("diasAvisoCancelacion"));
        if (diasAviso != null) etDiasAviso.setText(String.valueOf(diasAviso));

        // Dropdowns por id/nombre
        selectByIdOrName(actLugar, firstNonEmpty(doc.getString("lugar_id"), doc.getString("lugarId"), doc.getString("lugar")),
                firstNonEmpty(doc.getString("lugarNombre"), doc.getString("lugar")));
        selectByIdOrName(actOferente, firstNonEmpty(doc.getString("oferente_id"), doc.getString("oferenteId"), doc.getString("oferente")),
                firstNonEmpty(doc.getString("oferenteNombre"), doc.getString("oferente")));
        selectByIdOrName(actSocio, firstNonEmpty(doc.getString("socio_id"), doc.getString("socioId"), doc.getString("socioComunitario")),
                firstNonEmpty(doc.getString("socio_nombre"), doc.getString("socioComunitario")));
        selectByIdOrName(actProyecto, firstNonEmpty(doc.getString("proyecto_id"), doc.getString("project_id"), doc.getString("proyecto")),
                firstNonEmpty(doc.getString("proyectoNombre"), doc.getString("proyecto")));
    }

    // ================== Guardar ==================
    private void guardar(){
        String nombre = val(etNombre); if (nombre.isEmpty()){ etNombre.setError("Requerido"); return; }
        String tipo = val(actTipo); if (tipo.isEmpty()){ actTipo.setError("Selecciona tipo"); return; }
        String periodicidad = val(actPeriodicidad); if (periodicidad.isEmpty()){ actPeriodicidad.setError("Selecciona periodicidad"); return; }

        String lugar = val(actLugar);            // se usa en citas, lo dejamos opcional aquí
        String oferente = val(actOferente);
        String cupoStr = val(etCupo);

        // NUEVO
        String socio = val(actSocio);
        String beneficiariosTxt = val(etBeneficiarios);
        String proyecto = val(actProyecto);
        String diasAvisoStr = val(etDiasAviso);

        Map<String,Object> up = new HashMap<>();

        // Campos reales de tu doc
        up.put("nombre", nombre);
        up.put("tipoActividad", tipo);
        up.put("tipo", tipo); // compat
        up.put("periodicidad", periodicidad.toUpperCase(java.util.Locale.ROOT)); // tú guardas "PUNTUAL"
        if (!TextUtils.isEmpty(cupoStr)) {
            try { up.put("cupo", Integer.parseInt(cupoStr)); }
            catch (NumberFormatException e){ etCupo.setError("Número inválido"); return; }
        }

        if (!TextUtils.isEmpty(oferente)) {
            up.put("oferente", oferente);                 // clave real que tienes
            up.put("oferentes", java.util.Arrays.asList(oferente)); // compat con array si alguna vista lo usa
            up.put("oferenteNombre", oferente);           // por si alguna vista antigua lo lee
        }

        if (!TextUtils.isEmpty(socio)) up.put("socioComunitario", socio);

        if (!TextUtils.isEmpty(beneficiariosTxt)) {
            up.put("beneficiariosTexto", beneficiariosTxt);
            // si quieres mantener también array:
            java.util.List<String> lista = new java.util.ArrayList<>();
            for (String s : beneficiariosTxt.split(",")) {
                String t = s.trim(); if (!t.isEmpty()) lista.add(t);
            }
            if (!lista.isEmpty()) up.put("beneficiarios", lista);
        }

        // Proyecto es opcional en tu modelo; lo dejamos si lo usas en algunas pantallas
        if (!TextUtils.isEmpty(proyecto)) {
            up.put("proyecto", proyecto);
            up.put("proyectoNombre", proyecto);
        }

        // Días de aviso: tu clave real es diasAvisoPrevio
        if (!TextUtils.isEmpty(diasAvisoStr)) {
            try {
                int dias = Integer.parseInt(diasAvisoStr);
                up.put("diasAvisoPrevio", dias);  // clave real
                // compat con variantes si en algún lado las leen:
                up.put("diasAviso", dias);
                up.put("dias_aviso", dias);
                up.put("diasAvisoCancelacion", dias);
                up.put("diasAvisoPrevio", dias);
            } catch (NumberFormatException e){ etDiasAviso.setError("Número inválido"); return; }
        }

        // Aunque el lugar no está en el doc de actividad en tu modelo, lo dejamos por compat si alguna UI lo lee
        if (!TextUtils.isEmpty(lugar)) {
            up.put("lugar", lugar);
            up.put("lugarNombre", lugar);
        }

        // Marca de actualización
        up.put("updatedAt", com.google.firebase.Timestamp.now());

        // ==== Actualizar EN y ES en batch si existen (mantengo tu lógica) ====
        db.runTransaction(trx -> {
            DocumentReference en = act(actividadId,true);
            DocumentReference es = act(actividadId,false);

            DocumentSnapshot dEn = trx.get(en);
            DocumentSnapshot dEs = trx.get(es);

            if (dEn.exists()) trx.update(en, up);
            if (dEs.exists()) trx.update(es, up);
            if (!dEn.exists() && !dEs.exists()) trx.set(en, up, SetOptions.merge());

            return null;
        }).addOnSuccessListener(u -> {
            toast("Cambios guardados");
            notifyChanged();   // ya refresca Detalle y Calendario
            dismiss();
        }).addOnFailureListener(e -> toast("Error: "+e.getMessage()));
    }



    // ================== Carga de combos ==================
    private void cargarTiposActividad(){
        // Intenta colecciones conocidas; si no, fallback a estáticos usando id = nombre
        List<String> cols = Arrays.asList("tipos_actividad", "tiposActividad");
        cargarColeccionAOptionsMulti(cols, actTipo, new ArrayList<>(Arrays.asList(tiposFijos)));
    }

    private void cargarColeccionAOptions(String collection, AutoCompleteTextView view){
        db.collection(collection).orderBy("nombre")
                .get()
                .addOnSuccessListener(q -> setAdapter(view, mapQueryToOptions(q)))
                .addOnFailureListener(e -> setAdapter(view, new ArrayList<>()));
    }

    private void cargarColeccionAOptionsMulti(List<String> colecciones, AutoCompleteTextView view){
        cargarColeccionAOptionsMulti(colecciones, view, null);
    }

    private void cargarColeccionAOptionsMulti(List<String> colecciones, AutoCompleteTextView view, @Nullable ArrayList<String> fallback){
        if (colecciones==null || colecciones.isEmpty()){
            if (fallback!=null && !fallback.isEmpty()){
                ArrayList<OptionItem> xs = new ArrayList<>();
                for (String s: fallback) xs.add(new OptionItem(s, s));
                setAdapter(view, xs);
            }
            return;
        }
        String col = colecciones.get(0);
        db.collection(col).orderBy("nombre").get()
                .addOnSuccessListener(q -> {
                    ArrayList<OptionItem> xs = mapQueryToOptions(q);
                    if (!xs.isEmpty()) setAdapter(view, xs);
                    else cargarColeccionAOptionsMulti(colecciones.subList(1, colecciones.size()), view, fallback);
                })
                .addOnFailureListener(e ->
                        cargarColeccionAOptionsMulti(colecciones.subList(1, colecciones.size()), view, fallback));
    }

    private ArrayList<OptionItem> mapQueryToOptions(QuerySnapshot q){
        ArrayList<OptionItem> xs = new ArrayList<>();
        if (q!=null && !q.isEmpty()){
            for (DocumentSnapshot d: q){
                String id = d.getId();
                String nombre = d.getString("nombre");
                if (!TextUtils.isEmpty(nombre)) xs.add(new OptionItem(id, nombre));
            }
        }
        return xs;
    }

    private void setAdapter(AutoCompleteTextView view, ArrayList<OptionItem> items){
        view.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, items));
    }

    // ================== Helpers UI ==================
    private void set(TextInputEditText et, String v){ if (et!=null && v!=null) et.setText(v); }
    private void setDropText(AutoCompleteTextView v, String s){ if (!TextUtils.isEmpty(s)) v.setText(s,false); }

    private @Nullable OptionItem selected(@NonNull AutoCompleteTextView v) {
        CharSequence txt = v.getText();
        String s = (txt == null) ? "" : txt.toString().trim();
        if (s.isEmpty() || !(v.getAdapter() instanceof ArrayAdapter)) return null;

        ArrayAdapter<?> ad = (ArrayAdapter<?>) v.getAdapter();

        // 1) Match exacto por nombre mostrado
        for (int i = 0; i < ad.getCount(); i++) {
            Object it = ad.getItem(i);
            if (it instanceof OptionItem) {
                OptionItem oi = (OptionItem) it;
                if (s.equals(oi.nombre)) return oi;
            }
        }

        // 2) Match case-insensitive y sin tildes; también permite escribir el id
        String norm = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < ad.getCount(); i++) {
            Object it = ad.getItem(i);
            if (it instanceof OptionItem) {
                OptionItem oi = (OptionItem) it;
                String on = java.text.Normalizer.normalize(oi.nombre, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
                if (norm.equals(on) || norm.equals(oi.id.toLowerCase(java.util.Locale.ROOT))) return oi;
            }
        }
        return null;
    }


    private void selectByIdOrName(AutoCompleteTextView v, String id, String nombre){
        if (v.getAdapter() instanceof ArrayAdapter){
            ArrayAdapter<?> ad = (ArrayAdapter<?>) v.getAdapter();
            // intenta por id
            if (!TextUtils.isEmpty(id)) {
                for (int i=0;i<ad.getCount();i++){
                    Object it = ad.getItem(i);
                    if (it instanceof OptionItem && id.equals(((OptionItem) it).id)){
                        v.setText(((OptionItem) it).nombre, false);
                        return;
                    }
                }
            }
            // intenta por nombre
            if (!TextUtils.isEmpty(nombre)) {
                for (int i=0;i<ad.getCount();i++){
                    Object it = ad.getItem(i);
                    if (it instanceof OptionItem && nombre.equals(((OptionItem) it).nombre)){
                        v.setText(((OptionItem) it).nombre, false);
                        return;
                    }
                }
            }
        }
        // si aún no hay adapter, deja texto visible para que no quede vacío
        if (!TextUtils.isEmpty(nombre)) v.setText(nombre, false);
    }

    private String val(TextInputEditText et){ return et.getText()!=null ? et.getText().toString().trim() : ""; }
    private String val(AutoCompleteTextView et){ return et.getText()!=null ? et.getText().toString().trim() : ""; }
    private String firstNonEmpty(String... xs){
        if (xs==null) return null;
        for (String s: xs){ if (!TextUtils.isEmpty(s)) return s; }
        return null;
    }
    private Long firstNonNull(Long... xs){
        if (xs==null) return null;
        for (Long x: xs){ if (x!=null) return x; }
        return null;
    }
    private void notifyChanged(){
        Bundle b = new Bundle();
        try { getParentFragmentManager().setFragmentResult("actividad_change", b); } catch (Exception ignore) {}
        try { getParentFragmentManager().setFragmentResult("calendar_refresh", b); } catch (Exception ignore) {}
        try { requireActivity().getSupportFragmentManager().setFragmentResult("actividad_change", b); } catch (Exception ignore) {}
        try { requireActivity().getSupportFragmentManager().setFragmentResult("calendar_refresh", b); } catch (Exception ignore) {}
    }

    private void toast(String m){ Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }
}
