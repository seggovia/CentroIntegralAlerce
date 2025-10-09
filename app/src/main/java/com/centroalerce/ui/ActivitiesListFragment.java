package com.centroalerce.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.centroalerce.gestion.R;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ActivitiesListFragment extends Fragment {

    private static final String TAG = "ActivitiesListFragment";

    private RecyclerView rvActivities;
    private LinearLayout layoutEmpty;
    private EditText etBuscar;
    private ChipGroup chipGroupFiltros;

    private ActivityAdapter adapter;
    private FirebaseFirestore db;
    private ListenerRegistration activitiesListener;

    // Todas las actividades cargadas desde Firestore
    private final List<ActivityItem> allActivities = new ArrayList<>();
    // Actividades filtradas (lo que se muestra)
    private final List<ActivityItem> filteredActivities = new ArrayList<>();

    private String currentFilter = "todas"; // todas, proximas, pasadas, canceladas
    private String searchQuery = "";

    // === NUEVO: cache de metadatos de la actividad padre ===
    private final Map<String, String> activityTipoMap  = new HashMap<>();
    private final Map<String, String> activityPerMap   = new HashMap<>();
    private final Map<String, String> activityLugarMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activities_list, c, false);

        rvActivities = v.findViewById(R.id.rvActivities);
        layoutEmpty = v.findViewById(R.id.layoutEmpty);
        etBuscar = v.findViewById(R.id.etBuscar);
        chipGroupFiltros = v.findViewById(R.id.chipGroupFiltros);

        rvActivities.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ActivityAdapter(filteredActivities, this::onActivityClick);
        rvActivities.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        setupSearchBar();
        setupFilters();

        // Escuchar cambios cuando se guarda/modifica/cancela una actividad desde otra pantalla
        getParentFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> loadActivitiesFromFirestore()
        );
        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> loadActivitiesFromFirestore()
        );

        loadActivitiesFromFirestore();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activitiesListener != null) {
            activitiesListener.remove();
            activitiesListener = null;
        }
    }

    // ============ CARGA DESDE FIRESTORE ============
    private void loadActivitiesFromFirestore() {
        Log.d(TAG, "🔍 Precargando activities...");

        if (activitiesListener != null) {
            activitiesListener.remove();
            activitiesListener = null;
        }

        // 1) Precargar metadatos de la colección padre "activities"
        db.collection("activities") // ajusta el nombre si tu colección es distinta
                .get()
                .addOnSuccessListener(qs -> {
                    activityTipoMap.clear();
                    activityPerMap.clear();
                    activityLugarMap.clear();

                    for (DocumentSnapshot aDoc : qs.getDocuments()) {
                        String id   = aDoc.getId();
                        String tipo = firstNonEmpty(aDoc.getString("tipoActividad"), aDoc.getString("tipo"));
                        String per  = firstNonEmpty(aDoc.getString("periodicidad"), aDoc.getString("frecuencia"));
                        String lug  = firstNonEmpty(aDoc.getString("lugarNombre"), aDoc.getString("lugar"));

                        if (!TextUtils.isEmpty(id)) {
                            if (!TextUtils.isEmpty(tipo)) activityTipoMap.put(id, tipo);
                            if (!TextUtils.isEmpty(per))  activityPerMap.put(id, per);
                            if (!TextUtils.isEmpty(lug))  activityLugarMap.put(id, lug);
                        }
                    }
                    Log.d(TAG, "✅ Activities precargadas: " + activityTipoMap.size());
                    // 2) Ahora sí, escuchar todas las citas
                    attachCitasListener();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error precargando activities", e);
                    // Aun así, enganchamos citas para no dejar de mostrar
                    attachCitasListener();
                });
    }

    // === NUEVO: listener separado para citas ===
    private void attachCitasListener() {
        activitiesListener = db.collectionGroup("citas")
                .orderBy("startAt", Query.Direction.ASCENDING) // temporal ASC si no hay índice DESC
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "❌ Error cargando actividades", error);
                        showEmptyState();
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.w(TAG, "⚠️ No se encontraron citas en Firestore");
                        showEmptyState();
                        return;
                    }

                    Log.d(TAG, "✅ Se encontraron " + snapshot.size() + " citas");
                    allActivities.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        try {
                            ActivityItem item = parseActivityFromCita(doc);
                            if (item != null) {
                                allActivities.add(item);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parseando documento: " + doc.getId(), e);
                        }
                    }
                    Log.d(TAG, "📋 Total de actividades parseadas: " + allActivities.size());
                    applyFilters();
                });
    }

    // ============ PARSEAR DOCUMENTO A ActivityItem ============
    @Nullable
    private ActivityItem parseActivityFromCita(DocumentSnapshot doc) {
        // 1) IDs
        String activityId = getActivityIdFromRef(doc);
        String citaId = doc.getId();

        // 2) Título
        String titulo = doc.getString("titulo");
        if (TextUtils.isEmpty(titulo)) titulo = doc.getString("actividadNombre");
        if (TextUtils.isEmpty(titulo)) titulo = "Actividad sin nombre";

        // 3) Tipo / Periodicidad / Lugar (primero desde la cita)
        String tipo = firstNonEmpty(
                doc.getString("tipo"),
                doc.getString("tipoActividad")
        );
        String periodicidad = firstNonEmpty(
                doc.getString("periodicidad"),
                doc.getString("actividadPeriodicidad"),
                doc.getString("frecuencia")
        );
        String lugar = firstNonEmpty(
                doc.getString("lugarNombre"),
                doc.getString("lugar")
        );

        // 3b) Fallback inmediato al cache de la actividad padre (precargado)
        if (TextUtils.isEmpty(tipo) && activityId != null) {
            String cached = activityTipoMap.get(activityId);
            if (!TextUtils.isEmpty(cached)) tipo = cached;
        }
        if (TextUtils.isEmpty(periodicidad) && activityId != null) {
            String cached = activityPerMap.get(activityId);
            if (!TextUtils.isEmpty(cached)) periodicidad = cached;
        }
        if (TextUtils.isEmpty(lugar) && activityId != null) {
            String cached = activityLugarMap.get(activityId);
            if (!TextUtils.isEmpty(cached)) lugar = cached;
        }

        // 3c) Último recurso: leer padre sincrónicamente (solo si aún falta algo)
        if ((TextUtils.isEmpty(tipo) || TextUtils.isEmpty(periodicidad) || TextUtils.isEmpty(lugar)) && activityId != null) {
            try {
                DocumentSnapshot parent = doc.getReference()
                        .getParent()  // .../citas
                        .getParent()  // .../activities/{id}
                        .get()
                        .getResult();

                if (parent != null && parent.exists()) {
                    if (TextUtils.isEmpty(tipo)) {
                        tipo = firstNonEmpty(parent.getString("tipoActividad"), parent.getString("tipo"));
                    }
                    if (TextUtils.isEmpty(periodicidad)) {
                        periodicidad = firstNonEmpty(parent.getString("periodicidad"), parent.getString("frecuencia"));
                    }
                    if (TextUtils.isEmpty(lugar)) {
                        lugar = firstNonEmpty(parent.getString("lugarNombre"), parent.getString("lugar"));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "No se pudo leer la actividad padre para completar campos", e);
            }
        }

        // Defaults finales
        if (TextUtils.isEmpty(tipo)) tipo = "Tipo no especificado";
        if (TextUtils.isEmpty(periodicidad)) periodicidad = "PUNTUAL";
        periodicidad = periodicidad.toUpperCase(Locale.ROOT);
        if (TextUtils.isEmpty(lugar)) lugar = "—";

        // Subtítulo final: Tipo • PERIODICIDAD
        String subtitle = tipo + " • " + periodicidad;

        // 4) Fecha / Estado
        Timestamp startAt = doc.getTimestamp("startAt");
        if (startAt == null) startAt = doc.getTimestamp("fecha");

        String estado = firstNonEmpty(
                doc.getString("estado"),
                doc.getString("status"),
                "programada"
        ).toLowerCase();

        // 5) "dd/MM/yyyy HH:mm · Lugar"
        String fechaStr = "Sin fecha";
        if (startAt != null) {
            try {
                ZonedDateTime zdt = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(startAt.toDate().getTime()),
                        ZoneId.systemDefault()
                );
                fechaStr = zdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " · " + lugar;
            } catch (Exception e) {
                Log.e(TAG, "Error formateando fecha", e);
                fechaStr = "Sin fecha · " + lugar;
            }
        } else {
            fechaStr = "Sin fecha · " + lugar;
        }

        Log.d(TAG, "parse: tipo=" + tipo + ", periodicidad=" + periodicidad + ", lugar=" + lugar +
                " | title=" + titulo + " | actId=" + activityId + " | citaId=" + citaId);

        return new ActivityItem(activityId, citaId, titulo, subtitle, fechaStr, estado, startAt);
    }

    @Nullable
    private String getActivityIdFromRef(DocumentSnapshot doc) {
        try {
            return doc.getReference().getParent().getParent().getId();
        } catch (Exception e) {
            return null;
        }
    }

    // ============ BÚSQUEDA Y FILTROS ============
    private void setupSearchBar() {
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase().trim();
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilters() {
        int checkedId = chipGroupFiltros.getCheckedChipId();
        if (checkedId == R.id.chipTodas) currentFilter = "todas";
        else if (checkedId == R.id.chipProximas) currentFilter = "proximas";
        else if (checkedId == R.id.chipPasadas) currentFilter = "pasadas";
        else if (checkedId == R.id.chipCanceladas) currentFilter = "canceladas";
        else currentFilter = "todas";

        chipGroupFiltros.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentFilter = "todas";
            } else {
                int newCheckedId = checkedIds.get(0);
                if (newCheckedId == R.id.chipTodas) currentFilter = "todas";
                else if (newCheckedId == R.id.chipProximas) currentFilter = "proximas";
                else if (newCheckedId == R.id.chipPasadas) currentFilter = "pasadas";
                else if (newCheckedId == R.id.chipCanceladas) currentFilter = "canceladas";
            }
            if (!allActivities.isEmpty()) applyFilters();
        });
    }

    private void applyFilters() {
        filteredActivities.clear();

        if (allActivities.isEmpty()) {
            Log.d(TAG, "⏳ applyFilters() llamado pero allActivities está vacío - esperando carga...");
            return;
        }

        Date now = new Date();

        for (ActivityItem item : allActivities) {
            // 1. Búsqueda
            if (!searchQuery.isEmpty()) {
                if (!item.title.toLowerCase().contains(searchQuery) &&
                        !item.subtitle.toLowerCase().contains(searchQuery)) {
                    continue;
                }
            }

            // 2. Estado / fecha
            switch (currentFilter) {
                case "proximas":
                    if (item.startAt == null || item.startAt.toDate().before(now) ||
                            item.estado.equals("cancelada") || item.estado.equals("finalizada")) {
                        continue;
                    }
                    break;
                case "pasadas":
                    if (item.startAt == null || item.startAt.toDate().after(now)) {
                        continue;
                    }
                    break;
                case "canceladas":
                    if (!item.estado.equals("cancelada")) {
                        continue;
                    }
                    break;
                case "todas":
                default:
                    break;
            }
            filteredActivities.add(item);
        }

        Log.d(TAG, "📊 Filtros aplicados: " + filteredActivities.size() + " de " + allActivities.size() + " actividades");
        adapter.notifyDataSetChanged();

        if (filteredActivities.isEmpty()) showEmptyState();
        else hideEmptyState();
    }

    private void showEmptyState() {
        rvActivities.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        rvActivities.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
    }

    // ============ CLICK EN ACTIVIDAD ============
    private void onActivityClick(ActivityItem item) {
        Log.d(TAG, "🔘 Click en actividad: " + item.title + " | ID: " + item.activityId);

        ActivityDetailBottomSheet sheet = ActivityDetailBottomSheet.newInstance(
                item.activityId,
                item.citaId
        );
        sheet.show(getChildFragmentManager(), "activity_detail_sheet");
    }

    // ============ MODELO Y ADAPTER ============
    static class ActivityItem {
        String activityId;
        String citaId;
        String title;
        String subtitle;
        String fecha;
        String estado;
        Timestamp startAt;

        ActivityItem(String activityId, String citaId, String title, String subtitle,
                     String fecha, String estado, Timestamp startAt) {
            this.activityId = activityId;
            this.citaId = citaId;
            this.title = title;
            this.subtitle = subtitle;
            this.fecha = fecha;
            this.estado = estado;
            this.startAt = startAt;
        }
    }

    static class ActivityAdapter extends RecyclerView.Adapter<ActivityVH> {
        interface Click { void onTap(ActivityItem it); }
        private final List<ActivityItem> data;
        private final Click cb;

        ActivityAdapter(List<ActivityItem> d, Click c) {
            data = d;
            cb = c;
        }

        @NonNull
        @Override
        public ActivityVH onCreateViewHolder(@NonNull ViewGroup p, int vtype) {
            return new ActivityVH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_activity_row, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ActivityVH h, int i) {
            ActivityItem it = data.get(i);
            h.title.setText(it.title);
            h.subtitle.setText(it.subtitle);
            h.fecha.setText(it.fecha);

            // Color del estado
            int colorRes;
            switch (it.estado) {
                case "cancelada":
                case "canceled":
                    colorRes = R.color.state_cancelada_pill;
                    h.estado.setText("Cancelada");
                    break;
                case "reagendada":
                case "rescheduled":
                    colorRes = R.color.state_reagendada_pill;
                    h.estado.setText("Reagendada");
                    break;
                case "finalizada":
                case "completed":
                    colorRes = R.color.state_finalizada_pill;
                    h.estado.setText("Finalizada");
                    break;
                default:
                    colorRes = R.color.state_programada_stroke;
                    h.estado.setText("Programada");
                    break;
            }

            h.estado.setTextColor(ContextCompat.getColor(h.itemView.getContext(), colorRes));
            h.itemView.setOnClickListener(x -> cb.onTap(it));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    static class ActivityVH extends RecyclerView.ViewHolder {
        TextView title, subtitle, fecha, estado;

        ActivityVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tvTitle);
            subtitle = v.findViewById(R.id.tvSubtitle);
            fecha = v.findViewById(R.id.tvFecha);
            estado = v.findViewById(R.id.tvEstado);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return "";
    }
}
