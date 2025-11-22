package com.centroalerce.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.utils.RoleManager;
import com.centroalerce.gestion.utils.UserRole;
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
    private ImageView btnOrdenar;

    private ActivityAdapter adapter;
    private FirebaseFirestore db;
    private ListenerRegistration activitiesListener;

    // Detector de gestos para swipe horizontal
    private GestureDetector gestureDetector;

    // ✅ NUEVO: Sistema de roles
    private RoleManager roleManager;
    private UserRole currentUserRole;

    // Todas las actividades cargadas desde Firestore
    private final List<ActivityItem> allActivities = new ArrayList<>();
    // Actividades filtradas (lo que se muestra)
    private final List<ActivityItem> filteredActivities = new ArrayList<>();

    private String currentFilter = "todas";
    private String searchQuery = "";
    private String currentSort = "recientes"; // "recientes" o "antiguos"
    private String currentPeriodicidadFilter = "ninguno"; // "ninguno", "puntuales", "periodicas"

    // Cache de metadatos de la actividad padre
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
        btnOrdenar = v.findViewById(R.id.btnOrdenar);

        rvActivities.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ActivityAdapter(filteredActivities, this::onActivityClick);
        rvActivities.setAdapter(adapter);

        // Gestos de swipe para navegar entre pestañas principales (máxima sensibilidad razonable)
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 15;

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD) {
                    if (diffX < 0) {
                        // Swipe izquierda: ir a Calendario
                        try {
                            androidx.navigation.fragment.NavHostFragment.findNavController(ActivitiesListFragment.this)
                                    .navigate(R.id.action_activitiesListFragment_to_calendarFragment);
                        } catch (Exception ignored) {}
                    } else {
                        // Swipe derecha: ir a Configuración
                        try {
                            androidx.navigation.fragment.NavHostFragment.findNavController(ActivitiesListFragment.this)
                                    .navigate(R.id.action_activitiesListFragment_to_settingsFragment);
                        } catch (Exception ignored) {}
                    }
                    return true;
                }
                return false;
            }
        });

        View.OnTouchListener swipeListener = (view, event) -> gestureDetector != null && gestureDetector.onTouchEvent(event);
        v.setOnTouchListener(swipeListener);
        rvActivities.setOnTouchListener(swipeListener);
        View header = v.findViewById(R.id.headerActivities);
        if (header != null) header.setOnTouchListener(swipeListener);
        View cardSearch = v.findViewById(R.id.cardSearchActivities);
        if (cardSearch != null) cardSearch.setOnTouchListener(swipeListener);
        View scrollFiltros = v.findViewById(R.id.scrollFiltrosActivities);
        if (scrollFiltros != null) scrollFiltros.setOnTouchListener(swipeListener);
        if (layoutEmpty != null) layoutEmpty.setOnTouchListener(swipeListener);

        db = FirebaseFirestore.getInstance();

        // ✅ NUEVO: Inicializar sistema de roles
        initializeRoleSystem();

        setupSearchBar();
        setupFilters();
        setupSortButton();

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

    /**
     * ✅ NUEVO: Inicializa el sistema de roles
     */
    private void initializeRoleSystem() {
        roleManager = RoleManager.getInstance();

        roleManager.loadUserRole((RoleManager.RoleLoadCallback) role -> {
            currentUserRole = role;
            Log.d(TAG, "✅ Rol cargado: " + role.getValue());
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activitiesListener != null) {
            activitiesListener.remove();
            activitiesListener = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Asegura que la lista siempre refleje cambios recientes
        loadActivitiesFromFirestore();
    }

    // ============ CARGA DESDE FIRESTORE ============
    private void loadActivitiesFromFirestore() {
        Log.d(TAG, "🔍 Precargando activities...");

        if (activitiesListener != null) {
            activitiesListener.remove();
            activitiesListener = null;
        }

        // Precargar metadatos de la colección padre "activities"
        db.collection("activities")
                .get()
                .addOnSuccessListener(qs -> {
                    activityTipoMap.clear();
                    activityPerMap.clear();
                    activityLugarMap.clear();

                    for (DocumentSnapshot aDoc : qs.getDocuments()) {
                        String id   = aDoc.getId();
                        String tipo = firstNonEmpty(
                                aDoc.getString("tipoActividad"),
                                aDoc.getString("tipo"),
                                aDoc.getString("tipoActividadNombre")
                        );
                        String per  = firstNonEmpty(aDoc.getString("periodicidad"), aDoc.getString("frecuencia"));
                        String lug  = firstNonEmpty(aDoc.getString("lugarNombre"), aDoc.getString("lugar"));

                        if (!TextUtils.isEmpty(id)) {
                            if (!TextUtils.isEmpty(tipo)) activityTipoMap.put(id, tipo);
                            if (!TextUtils.isEmpty(per))  activityPerMap.put(id, per);
                            if (!TextUtils.isEmpty(lug))  activityLugarMap.put(id, lug);
                        }
                    }
                    Log.d(TAG, "✅ Activities precargadas: " + activityTipoMap.size());
                    attachCitasListener();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error precargando activities", e);
                    attachCitasListener();
                });
    }

    private void attachCitasListener() {
        activitiesListener = db.collectionGroup("citas")
                .orderBy("startAt", Query.Direction.ASCENDING)
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

                    // 🔥 NUEVO: Agrupar actividades periódicas
                    Map<String, List<DocumentSnapshot>> actividadesPorId = new HashMap<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        try {
                            String activityId = getActivityIdFromRef(doc);
                            if (activityId == null) continue;

                            // Agrupar por activityId
                            if (!actividadesPorId.containsKey(activityId)) {
                                actividadesPorId.put(activityId, new ArrayList<>());
                            }
                            actividadesPorId.get(activityId).add(doc);
                        } catch (Exception e) {
                            Log.e(TAG, "Error agrupando documento: " + doc.getId(), e);
                        }
                    }

                    // Procesar cada grupo de actividades
                    for (Map.Entry<String, List<DocumentSnapshot>> entry : actividadesPorId.entrySet()) {
                        String activityId = entry.getKey();
                        List<DocumentSnapshot> citas = entry.getValue();

                        if (citas.isEmpty()) continue;

                        // Obtener periodicidad del primer documento
                        DocumentSnapshot firstDoc = citas.get(0);
                        String periodicidad = firstNonEmpty(
                                firstDoc.getString("periodicidad"),
                                firstDoc.getString("actividadPeriodicidad"),
                                firstDoc.getString("frecuencia")
                        );

                        if (TextUtils.isEmpty(periodicidad) && activityId != null) {
                            String cached = activityPerMap.get(activityId);
                            if (!TextUtils.isEmpty(cached)) periodicidad = cached;
                        }

                        if (TextUtils.isEmpty(periodicidad)) periodicidad = "PUNTUAL";
                        periodicidad = periodicidad.toUpperCase(Locale.ROOT);

                        // Si es PERIODICA: crear solo 1 item con todas las citas
                        if (periodicidad.equals("PERIODICA")) {
                            ActivityItem item = parseActivityFromCita(firstDoc, citas);
                            if (item != null) {
                                allActivities.add(item);
                                Log.d(TAG, "✅ Agrupada actividad PERIODICA: " + item.title + " (" + citas.size() + " citas)");
                            }
                        }
                        // Si es PUNTUAL: crear 1 item por cada cita
                        else {
                            for (DocumentSnapshot doc : citas) {
                                ActivityItem item = parseActivityFromCita(doc, null);
                                if (item != null) {
                                    allActivities.add(item);
                                }
                            }
                        }
                    }

                    Log.d(TAG, "📋 Total de actividades parseadas: " + allActivities.size());
                    applyFilters();
                });
    }

    // ============ PARSEAR DOCUMENTO A ActivityItem ============
    @Nullable
    private ActivityItem parseActivityFromCita(DocumentSnapshot doc, @Nullable List<DocumentSnapshot> allCitas) {
        String activityId = getActivityIdFromRef(doc);
        String citaId = doc.getId();

        String titulo = doc.getString("titulo");
        if (TextUtils.isEmpty(titulo)) titulo = doc.getString("actividadNombre");
        if (TextUtils.isEmpty(titulo)) titulo = "Actividad sin nombre";

        String tipo = firstNonEmpty(
                doc.getString("tipo"),
                doc.getString("tipoActividad"),
                doc.getString("tipoActividadNombre")
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

        if ((TextUtils.isEmpty(tipo) || TextUtils.isEmpty(periodicidad) || TextUtils.isEmpty(lugar)) && activityId != null) {
            try {
                DocumentSnapshot parent = doc.getReference()
                        .getParent()
                        .getParent()
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

        if (TextUtils.isEmpty(tipo)) tipo = "Tipo no especificado";
        if (TextUtils.isEmpty(periodicidad)) periodicidad = "PUNTUAL";
        periodicidad = periodicidad.toUpperCase(Locale.ROOT);
        if (TextUtils.isEmpty(lugar)) lugar = "—";

        String subtitle = tipo + " • " + periodicidad;

        Timestamp startAt = doc.getTimestamp("startAt");
        if (startAt == null) startAt = doc.getTimestamp("fecha");

        String estado = firstNonEmpty(
                doc.getString("estado"),
                doc.getString("status"),
                "programada"
        ).toLowerCase();

        String fechaStr = "Sin fecha";
        String horaStr = "";

        // 🔥 NUEVO: Si es actividad agrupada (PERIODICA con múltiples citas)
        int totalCitas = (allCitas != null) ? allCitas.size() : 1;

        if (allCitas != null && allCitas.size() > 1) {
            // Es una actividad PERIODICA agrupada - mostrar rango de fechas
            try {
                // Encontrar primera y última fecha
                Timestamp primeraFecha = null;
                Timestamp ultimaFecha = null;

                for (DocumentSnapshot cita : allCitas) {
                    Timestamp citaTime = cita.getTimestamp("startAt");
                    if (citaTime == null) citaTime = cita.getTimestamp("fecha");

                    if (citaTime != null) {
                        if (primeraFecha == null || citaTime.toDate().before(primeraFecha.toDate())) {
                            primeraFecha = citaTime;
                        }
                        if (ultimaFecha == null || citaTime.toDate().after(ultimaFecha.toDate())) {
                            ultimaFecha = citaTime;
                        }
                    }
                }

                if (primeraFecha != null && ultimaFecha != null) {
                    ZonedDateTime primera = ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(primeraFecha.toDate().getTime()),
                            ZoneId.systemDefault()
                    );
                    ZonedDateTime ultima = ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(ultimaFecha.toDate().getTime()),
                            ZoneId.systemDefault()
                    );

                    // Obtener información de repetición
                    String diasRepeticion = obtenerDiasRepeticion(allCitas);
                    String frecuenciaStr = doc.getString("frecuencia");
                    if (TextUtils.isEmpty(frecuenciaStr)) frecuenciaStr = doc.getString("periodicidad");

                    String infoRepeticion = "";
                    if (!TextUtils.isEmpty(diasRepeticion)) {
                        String frecuencia = (frecuenciaStr != null) ? frecuenciaStr.toLowerCase() : "";
                        if (frecuencia.contains("semanal")) {
                            infoRepeticion = " • " + diasRepeticion + " • semanal";
                        } else if (frecuencia.contains("mensual")) {
                            infoRepeticion = " • " + diasRepeticion + " • mensual";
                        } else {
                            infoRepeticion = " • " + diasRepeticion;
                        }
                    }

                    // Rango de fechas con emoji de repetición (separado de la hora)
                    String rangoFechas = primera.format(DateTimeFormatter.ofPattern("dd/MM/yy")) +
                                        " → " +
                                        ultima.format(DateTimeFormatter.ofPattern("dd/MM/yy")) +
                                        infoRepeticion;
                    horaStr = primera.format(DateTimeFormatter.ofPattern("HH:mm"));
                    // Formato: "RANGO|hora · lugar" (RANGO se mostrará aparte, hora al lado del icono)
                    fechaStr = "🔁 " + rangoFechas + "|" + horaStr + " · " + lugar;
                } else {
                    fechaStr = "🔁 Periódica · " + lugar;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error formateando rango de fechas", e);
                fechaStr = "🔁 Periódica · " + lugar;
            }
        } else {
            // Actividad PUNTUAL o cita individual
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
        }

        Log.d(TAG, "parse: tipo=" + tipo + ", periodicidad=" + periodicidad + ", lugar=" + lugar +
                " | title=" + titulo + " | actId=" + activityId + " | citaId=" + citaId);

        return new ActivityItem(activityId, citaId, titulo, subtitle, fechaStr, estado, startAt, periodicidad, totalCitas);
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
        else if (checkedId == R.id.chipProgramadas) currentFilter = "programadas";
        else if (checkedId == R.id.chipCompletadas) currentFilter = "completadas";
        else if (checkedId == R.id.chipReagendadas) currentFilter = "reagendadas";
        else if (checkedId == R.id.chipCanceladas) currentFilter = "canceladas";
        else currentFilter = "todas";

        chipGroupFiltros.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentFilter = "todas";
            } else {
                int newCheckedId = checkedIds.get(0);
                if (newCheckedId == R.id.chipTodas) currentFilter = "todas";
                else if (newCheckedId == R.id.chipProgramadas) currentFilter = "programadas";
                else if (newCheckedId == R.id.chipCompletadas) currentFilter = "completadas";
                else if (newCheckedId == R.id.chipReagendadas) currentFilter = "reagendadas";
                else if (newCheckedId == R.id.chipCanceladas) currentFilter = "canceladas";
            }
            if (!allActivities.isEmpty()) applyFilters();
        });
    }

    private void setupSortButton() {
        btnOrdenar.setOnClickListener(v -> {
            // Crear menú popup
            android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), btnOrdenar);
            popup.getMenuInflater().inflate(R.menu.menu_ordenar_actividades, popup.getMenu());

            // Forzar el estado correcto de los items de ordenamiento
            if (currentSort.equals("recientes")) {
                popup.getMenu().findItem(R.id.menu_mas_recientes).setChecked(true);
                popup.getMenu().findItem(R.id.menu_menos_recientes).setChecked(false);
            } else {
                popup.getMenu().findItem(R.id.menu_mas_recientes).setChecked(false);
                popup.getMenu().findItem(R.id.menu_menos_recientes).setChecked(true);
            }

            // Marcar filtro de periodicidad actual
            popup.getMenu().findItem(R.id.menu_puntuales).setChecked(currentPeriodicidadFilter.equals("puntuales"));
            popup.getMenu().findItem(R.id.menu_periodicas).setChecked(currentPeriodicidadFilter.equals("periodicas"));

            popup.setOnMenuItemClickListener(menuItem -> {
                int itemId = menuItem.getItemId();

                // Ordenamiento - Control manual completo
                if (itemId == R.id.menu_mas_recientes) {
                    currentSort = "recientes";
                    // Marcar este, desmarcar el otro
                    menuItem.setChecked(true);
                    popup.getMenu().findItem(R.id.menu_menos_recientes).setChecked(false);
                    applyFilters();
                    return true;
                } else if (itemId == R.id.menu_menos_recientes) {
                    currentSort = "antiguos";
                    // Marcar este, desmarcar el otro
                    menuItem.setChecked(true);
                    popup.getMenu().findItem(R.id.menu_mas_recientes).setChecked(false);
                    applyFilters();
                    return true;
                }

                // Filtros de periodicidad
                else if (itemId == R.id.menu_puntuales) {
                    // Toggle: Si ya está en puntuales, desactivar
                    if (currentPeriodicidadFilter.equals("puntuales")) {
                        currentPeriodicidadFilter = "ninguno";
                        menuItem.setChecked(false);
                    } else {
                        // Activar puntuales y desactivar periódicas
                        currentPeriodicidadFilter = "puntuales";
                        menuItem.setChecked(true);
                        popup.getMenu().findItem(R.id.menu_periodicas).setChecked(false);
                    }
                    applyFilters();
                    return true;
                } else if (itemId == R.id.menu_periodicas) {
                    // Toggle: Si ya está en periódicas, desactivar
                    if (currentPeriodicidadFilter.equals("periodicas")) {
                        currentPeriodicidadFilter = "ninguno";
                        menuItem.setChecked(false);
                    } else {
                        // Activar periódicas y desactivar puntuales
                        currentPeriodicidadFilter = "periodicas";
                        menuItem.setChecked(true);
                        popup.getMenu().findItem(R.id.menu_puntuales).setChecked(false);
                    }
                    applyFilters();
                    return true;
                }

                return false;
            });

            popup.show();
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
            // Búsqueda
            if (!searchQuery.isEmpty()) {
                if (!item.title.toLowerCase().contains(searchQuery) &&
                        !item.subtitle.toLowerCase().contains(searchQuery)) {
                    continue;
                }
            }

            String estadoNorm = item.estado == null ? "" : item.estado.toLowerCase();

            // Filtro por estado
            switch (currentFilter) {
                case "programadas":
                    if (!estadoNorm.equals("programada") && !estadoNorm.equals("scheduled")) {
                        continue;
                    }
                    if (item.startAt != null && item.startAt.toDate().before(now)) {
                        continue;
                    }
                    break;

                case "completadas":
                    if (!estadoNorm.equals("completada") &&
                            !estadoNorm.equals("finalizada") &&
                            !estadoNorm.equals("completed")) {
                        continue;
                    }
                    break;

                case "reagendadas":
                    if (!estadoNorm.equals("reagendada") && !estadoNorm.equals("rescheduled")) {
                        continue;
                    }
                    break;

                case "canceladas":
                    if (!estadoNorm.equals("cancelada") && !estadoNorm.equals("canceled")) {
                        continue;
                    }
                    break;

                case "todas":
                default:
                    break;
            }

            // 🔥 NUEVO: Filtro adicional por periodicidad
            if (!currentPeriodicidadFilter.equals("ninguno")) {
                String periItem = item.periodicidad == null ? "" : item.periodicidad.toUpperCase();

                if (currentPeriodicidadFilter.equals("puntuales")) {
                    if (!periItem.equals("PUNTUAL")) {
                        continue;
                    }
                } else if (currentPeriodicidadFilter.equals("periodicas")) {
                    if (!periItem.equals("PERIODICA")) {
                        continue;
                    }
                }
            }

            filteredActivities.add(item);
        }

        // Ordenar según la opción seleccionada
        if (currentSort.equals("recientes")) {
            // Más recientes primero (descendente por fecha)
            filteredActivities.sort((a, b) -> {
                if (a.startAt == null && b.startAt == null) return 0;
                if (a.startAt == null) return 1;
                if (b.startAt == null) return -1;
                return b.startAt.compareTo(a.startAt); // Invertido para descendente
            });
        } else {
            // Menos recientes primero (ascendente por fecha)
            filteredActivities.sort((a, b) -> {
                if (a.startAt == null && b.startAt == null) return 0;
                if (a.startAt == null) return 1;
                if (b.startAt == null) return -1;
                return a.startAt.compareTo(b.startAt); // Normal para ascendente
            });
        }

        Log.d(TAG, "📊 Filtros aplicados: " + filteredActivities.size() + " de " + allActivities.size() + " actividades (Orden: " + currentSort + ")");

        // 🔥 MEJORADO: Animación más notoria al actualizar
        if (rvActivities != null) {
            rvActivities.setAlpha(0.0f); // Empezar completamente transparente
            rvActivities.animate()
                .alpha(1.0f)
                .setDuration(400) // Duración más larga para mejor visibilidad
                .setInterpolator(new android.view.animation.DecelerateInterpolator()) // Animación más suave
                .start();
        }

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

        // ✅ NUEVO: Pasar el rol al bottom sheet
        ActivityDetailBottomSheet sheet = ActivityDetailBottomSheet.newInstance(
                item.activityId,
                item.citaId,
                currentUserRole // ✅ AGREGADO
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
        String periodicidad;
        int totalCitas; // 🔥 NUEVO: número de citas (1 para puntuales, >1 para periódicas agrupadas)

        ActivityItem(String activityId, String citaId, String title, String subtitle,
                     String fecha, String estado, Timestamp startAt, String periodicidad, int totalCitas) {
            this.activityId = activityId;
            this.citaId = citaId;
            this.title = title;
            this.subtitle = subtitle;
            this.fecha = fecha;
            this.estado = estado;
            this.startAt = startAt;
            this.periodicidad = periodicidad;
            this.totalCitas = totalCitas; // 🔥 NUEVO
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

            // Título
            h.title.setText(it.title);

            // Subtítulo (Tipo • Periodicidad)
            h.subtitle.setText(it.subtitle);

            // ✅ CORRECCIÓN: Separar rango de fechas, hora y lugar
            String fechaCompleta = it.fecha; // "🔁 rango|hora · lugar" o "dd/MM/yyyy HH:mm · lugar"
            String[] partes = fechaCompleta.split(" · ");

            if (partes.length >= 2) {
                String fechaYHora = partes[0];

                // Si es PERIODICA (contiene | y emoji 🔁)
                if (fechaYHora.contains("|") && fechaYHora.startsWith("🔁")) {
                    String[] rangoYHora = fechaYHora.split("\\|");
                    if (rangoYHora.length == 2) {
                        // Mostrar rango en TextView separado
                        h.rangoFechas.setText(rangoYHora[0]); // "🔁 02/12/25 → 02/03/26"
                        h.rangoFechas.setVisibility(android.view.View.VISIBLE);
                        // Mostrar solo hora al lado del icono de reloj
                        h.fecha.setText(rangoYHora[1]); // "17:30"
                    } else {
                        h.rangoFechas.setVisibility(android.view.View.GONE);
                        h.fecha.setText(fechaYHora);
                    }
                } else {
                    // PUNTUAL: ocultar rango y mostrar fecha/hora completa
                    h.rangoFechas.setVisibility(android.view.View.GONE);
                    h.fecha.setText(fechaYHora); // "dd/MM/yyyy HH:mm"
                }

                h.lugar.setText(partes[1]); // Solo "Lugar"
            } else {
                h.rangoFechas.setVisibility(android.view.View.GONE);
                h.fecha.setText(fechaCompleta);
                h.lugar.setText("Sin lugar");
            }

            String estadoNorm = it.estado == null ? "" : it.estado.toLowerCase();

            int colorRes;
            String textoEstado;
            switch (estadoNorm) {
                case "cancelada":
                case "canceled":
                    colorRes = R.color.state_cancelada_pill;
                    textoEstado = "❌ Cancelada";
                    break;
                case "reagendada":
                case "rescheduled":
                    colorRes = R.color.state_reagendada_pill;
                    textoEstado = "🔄 Reagendada";
                    break;
                case "finalizada":
                case "completada":
                case "completed":
                    colorRes = R.color.state_finalizada_pill;
                    textoEstado = "✅ Completada";
                    break;
                default:
                    colorRes = R.color.state_programada_stroke;
                    textoEstado = "⏰ Programada";
                    break;
            }

            h.estado.setText(textoEstado);
            h.estado.setTextColor(ContextCompat.getColor(h.itemView.getContext(), colorRes));
            h.itemView.setOnClickListener(x -> cb.onTap(it));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    static class ActivityVH extends RecyclerView.ViewHolder {
        TextView title, subtitle, fecha, estado, lugar, rangoFechas;

        ActivityVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tvTitle);
            subtitle = v.findViewById(R.id.tvSubtitle);
            fecha = v.findViewById(R.id.tvFecha);
            estado = v.findViewById(R.id.tvEstado);
            lugar = v.findViewById(R.id.tvLugar);
            rangoFechas = v.findViewById(R.id.tvRangoFechas);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return "";
    }

    /**
     * Analiza las citas para determinar los días de repetición
     */
    private String obtenerDiasRepeticion(List<DocumentSnapshot> citas) {
        if (citas == null || citas.size() < 2) return "";

        try {
            // Contar días de la semana
            java.util.Map<Integer, Integer> diasSemana = new java.util.HashMap<>();
            java.util.Set<Integer> diasDelMes = new java.util.HashSet<>();

            for (DocumentSnapshot cita : citas) {
                Timestamp citaTime = cita.getTimestamp("startAt");
                if (citaTime == null) citaTime = cita.getTimestamp("fecha");

                if (citaTime != null) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(citaTime.toDate());

                    int diaSemana = cal.get(java.util.Calendar.DAY_OF_WEEK);
                    int diaDelMes = cal.get(java.util.Calendar.DAY_OF_MONTH);

                    diasSemana.put(diaSemana, diasSemana.getOrDefault(diaSemana, 0) + 1);
                    diasDelMes.add(diaDelMes);
                }
            }

            // Si todos los días del mes son iguales, es repetición mensual
            if (diasDelMes.size() == 1) {
                int dia = diasDelMes.iterator().next();
                return dia + " de cada mes";
            }

            // Si hay múltiples días de la semana, es repetición semanal
            if (diasSemana.size() > 0) {
                StringBuilder dias = new StringBuilder();
                String[] nombresDias = {"domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"};

                // Ordenar días
                java.util.List<Integer> diasOrdenados = new java.util.ArrayList<>(diasSemana.keySet());
                java.util.Collections.sort(diasOrdenados);

                for (int i = 0; i < diasOrdenados.size(); i++) {
                    int dia = diasOrdenados.get(i);
                    if (i > 0) {
                        if (i == diasOrdenados.size() - 1) {
                            dias.append(" y ");
                        } else {
                            dias.append(", ");
                        }
                    }
                    dias.append(nombresDias[dia - 1]);
                }

                return dias.toString();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo días de repetición", e);
        }

        return "";
    }
}