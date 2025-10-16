package com.centroalerce.ui;

import android.content.res.ColorStateList; // 👈 NUEVO
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat; // 👈 NUEVO
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

// ===== Firebase / Firestore =====
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CalendarFragment extends Fragment {

    private LocalDate weekStart;      // lunes visible
    private LocalDate selectedDay;    // día seleccionado
    private DayAdapter dayAdapter;
    private EventAdapter eventAdapter;
    private TextView tvRangoSemana, tvTituloDia;
    private RecyclerView rvDays;

    // Citas de la semana visible (día -> lista de eventos)
    private final Map<LocalDate, List<EventItem>> weekEvents = new HashMap<>();

    // Firestore
    private FirebaseFirestore db;
    private ListenerRegistration weekListener;

    // Cache de nombres y tipos de actividad
    private final Map<String, String> activityNameCache = new HashMap<>();
    private final Map<String, String> activityTypeCache = new HashMap<>(); // 👈 NUEVO

    // listeners por actividad para refrescar datos al vuelo
    private final Map<String, ListenerRegistration> activityNameRegs = new HashMap<>();

    // ========== MÉTODO 1: onCreateView (COMPLETO) ==========

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_calendar, c, false);

        tvRangoSemana = v.findViewById(R.id.tvRangoSemana);
        tvTituloDia   = v.findViewById(R.id.tvTituloDia);
        rvDays        = v.findViewById(R.id.rvDays);
        RecyclerView rvEventos = v.findViewById(R.id.rvEventos);

        // Inicial
        LocalDate today = LocalDate.now();
        weekStart   = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7); // lunes
        selectedDay = today;

        db = FirebaseFirestore.getInstance();

        // ---- DÍAS (fila semanal) ----
        rvDays.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvDays.setItemAnimator(null);

        dayAdapter = new DayAdapter(new ArrayList<>(), d -> {
            selectedDay = d;
            updateDayTitle();
            loadEventsFor(selectedDay);
        });
        rvDays.setAdapter(dayAdapter);

        rvDays.addOnLayoutChangeListener((view, l, t, r, btm, ol, ot, orr, ob) -> applyAdaptiveLayoutForWeek());
        rvDays.post(this::applyAdaptiveLayoutForWeek);

        // ---- EVENTOS DEL DÍA ----
        rvEventos.setLayoutManager(new LinearLayoutManager(getContext()));
        rvEventos.setItemAnimator(null);

        eventAdapter = new EventAdapter(new ArrayList<>(), event -> {
            ActivityDetailBottomSheet sheet = ActivityDetailBottomSheet.newInstance(
                    event.activityId,
                    event.citaId
            );
            sheet.show(getChildFragmentManager(), "activity_detail_sheet");
        });
        rvEventos.setAdapter(eventAdapter);

        // ✅ Evita que los últimos ítems del calendario queden ocultos por el FAB o el BottomSheet
        rvEventos.setClipToPadding(false);

        // Espacio extra al final de la lista
        int extraBottom = dp(96);
        rvEventos.setPadding(
                rvEventos.getPaddingLeft(),
                rvEventos.getPaddingTop(),
                rvEventos.getPaddingRight(),
                rvEventos.getPaddingBottom() + extraBottom
        );

        // Ajuste dinámico por la barra de navegación o gestos del sistema
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rvEventos, (view, insets) -> {
            int sysBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    extraBottom + sysBottom
            );
            return insets;
        });

        // Navegación de semana
        v.findViewById(R.id.btnSemanaAnterior).setOnClickListener(x -> {
            weekStart = weekStart.minusWeeks(1);
            fillWeek();
        });
        v.findViewById(R.id.btnSemanaSiguiente).setOnClickListener(x -> {
            weekStart = weekStart.plusWeeks(1);
            fillWeek();
        });

        // ✅ CORREGIDO: refrescar calendario con fuerza al guardar cambios
        getParentFragmentManager().setFragmentResultListener(
                "calendar_refresh", getViewLifecycleOwner(),
                (req, bundle) -> {
                    boolean force = bundle.getBoolean("forceRefresh", false);
                    if (force) {
                        // Forzar recarga completa limpiando caches
                        activityNameCache.clear();
                        activityTypeCache.clear();
                        clearActivityNameListeners();
                        weekEvents.clear(); // 👈 CRÍTICO: limpiar eventos actuales
                    }
                    refreshFromExternalChange();
                }
        );

        // También si algún sitio solo emite "actividad_change"
        getParentFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    weekEvents.clear(); // 👈 CRÍTICO: forzar recarga
                    refreshFromExternalChange();
                }
        );

        // ✅ NUEVO: escuchar también los eventos emitidos a nivel de Activity
        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "calendar_refresh", getViewLifecycleOwner(),
                (req, bundle) -> {
                    boolean force = bundle.getBoolean("forceRefresh", false);
                    if (force) {
                        activityNameCache.clear();
                        activityTypeCache.clear();
                        clearActivityNameListeners();
                        weekEvents.clear(); // 👈 CRÍTICO: limpiar eventos
                    }
                    refreshFromExternalChange();
                }
        );

        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> {
                    weekEvents.clear(); // 👈 CRÍTICO: forzar recarga
                    refreshFromExternalChange();
                }
        );

        fillWeek();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (weekListener != null) {
            weekListener.remove();
            weekListener = null;
        }
        clearActivityNameListeners();
    }


    private void refreshFromExternalChange() {
        android.util.Log.d("CAL", "🔄 refreshFromExternalChange() iniciado");

        // Limpia caches y vuelve a suscribirse a la semana actual para pintar cambios al instante
        activityNameCache.clear();
        activityTypeCache.clear();
        clearActivityNameListeners();
        weekEvents.clear(); // 👈 CRÍTICO: forzar actualización completa

        // 👇 NUEVO: Detener listener anterior para evitar duplicados
        if (weekListener != null) {
            weekListener.remove();
            weekListener = null;
            android.util.Log.d("CAL", "🛑 Listener anterior detenido");
        }

        // 👇 NUEVO: Esperar 300ms para que Firestore sincronice
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.util.Log.d("CAL", "⏰ Ejecutando fillWeek() después del delay");
            fillWeek(); // Recarga completa de la semana
            android.util.Log.d("CAL", "✅ fillWeek() completado");
        }, 300);
    }

    private void clearActivityNameListeners() {
        for (ListenerRegistration r : activityNameRegs.values()) {
            try { r.remove(); } catch (Exception ignore) {}
        }
        activityNameRegs.clear();
    }

    private void applyAdaptiveLayoutForWeek() {
        if (rvDays == null || rvDays.getWidth() == 0) return;

        int available = rvDays.getWidth() - rvDays.getPaddingStart() - rvDays.getPaddingEnd();
        int minChipPx = dp(52);
        int maxSpacing = dp(8);
        int minSpacing = dp(2);
        int spacing    = maxSpacing;

        int extra = available - (minChipPx * 7);

        if (extra < spacing * 6) {
            spacing = Math.max(minSpacing, extra / 6);
        }

        while (rvDays.getItemDecorationCount() > 0) {
            rvDays.removeItemDecorationAt(0);
        }
        rvDays.addItemDecoration(new SpacingDecoration(spacing));

        int itemWidth = (available - spacing * 6) / 7;
        dayAdapter.setItemWidth(itemWidth);
    }

    private void fillWeek() {
        List<LocalDate> week = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) week.add(weekStart.plusDays(i));
        dayAdapter.submit(week);

        DateTimeFormatter d1 = DateTimeFormatter.ofPattern("d MMM", new Locale("es", "CL"));
        String start = week.get(0).format(d1).toUpperCase(Locale.ROOT);
        String end   = week.get(6).format(d1).toUpperCase(Locale.ROOT);
        tvRangoSemana.setText(getString(R.string.rango_semana, start, end));

        listenWeekFromFirestore(weekStart, weekStart.plusDays(6));

        if (selectedDay.isBefore(weekStart) || selectedDay.isAfter(weekStart.plusDays(6))) {
            selectedDay = weekStart;
        }

        updateDayTitle();
        loadEventsFor(selectedDay);
    }

    private void updateDayTitle() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es","CL"));
        String t = selectedDay.format(df);
        tvTituloDia.setText(t.substring(0, 1).toUpperCase() + t.substring(1));
    }

    private void loadEventsFor(LocalDate day) {
        List<EventItem> list = weekEvents.get(day);
        if (list == null) list = Collections.emptyList();
        eventAdapter.submit(list);
    }

    private int dp(int v){
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static class SpacingDecoration extends RecyclerView.ItemDecoration {
        private final int space;
        SpacingDecoration(int space){ this.space = space; }
        @Override
        public void getItemOffsets(@NonNull Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            outRect.left  = (pos == 0) ? 0 : space;
            outRect.right = 0;
        }
    }

    // ==================== FIRESTORE ====================

    // ========== MÉTODO 3: listenWeekFromFirestore (COMPLETO) ==========

// ========== CalendarFragment.java - Método listenWeekFromFirestore (COMPLETO CON SOURCE.SERVER) ==========

    private void listenWeekFromFirestore(LocalDate monday, LocalDate sunday) {
        android.util.Log.d("CAL", "=== listenWeekFromFirestore INICIADO ===");
        android.util.Log.d("CAL", "monday=" + monday + ", sunday=" + sunday);

        if (weekListener != null) {
            weekListener.remove();
            weekListener = null;
        }

        ZonedDateTime zStart = monday.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime zEnd   = sunday.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        Timestamp tsStart = new Timestamp(Date.from(zStart.toInstant()));
        Timestamp tsEnd   = new Timestamp(Date.from(zEnd.toInstant()));

        android.util.Log.d("CAL", "tsStart=" + tsStart.toDate());
        android.util.Log.d("CAL", "tsEnd=" + tsEnd.toDate());
        android.util.Log.d("CAL", "Ejecutando query collectionGroup...");

        // 👇 CRÍTICO: Primero forzar carga del servidor
        db.collectionGroup("citas")
                .whereGreaterThanOrEqualTo("startAt", tsStart)
                .whereLessThan("startAt", tsEnd)
                .orderBy("startAt", Query.Direction.ASCENDING)
                .get(com.google.firebase.firestore.Source.SERVER) // 👈 FORZAR SERVER
                .addOnSuccessListener(serverSnapshot -> {
                    android.util.Log.d("CAL", "🌐 Datos del servidor cargados: " + serverSnapshot.size());
                    procesarSnapshot(serverSnapshot);

                    // Ahora sí, escuchar cambios en tiempo real
                    weekListener = db.collectionGroup("citas")
                            .whereGreaterThanOrEqualTo("startAt", tsStart)
                            .whereLessThan("startAt", tsEnd)
                            .orderBy("startAt", Query.Direction.ASCENDING)
                            .addSnapshotListener((snap, err) -> {
                                if (err != null) {
                                    android.util.Log.e("CAL", "❌ ERROR ESCUCHANDO CITAS", err);
                                    return;
                                }
                                if (snap == null) return;

                                android.util.Log.d("CAL", "✅ Snapshot recibido: " + snap.size() + " docs | fuente: " +
                                        (snap.getMetadata().isFromCache() ? "CACHE" : "SERVER"));
                                procesarSnapshot(snap);
                            });

                    android.util.Log.d("CAL", "Listener registrado correctamente");
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CAL", "❌ Error cargando del servidor", e);
                    // Fallback al cache si falla el servidor
                    db.collectionGroup("citas")
                            .whereGreaterThanOrEqualTo("startAt", tsStart)
                            .whereLessThan("startAt", tsEnd)
                            .orderBy("startAt", Query.Direction.ASCENDING)
                            .get()
                            .addOnSuccessListener(this::procesarSnapshot);
                });
    }

    // 👇 NUEVO: Método helper para procesar snapshots
    private void procesarSnapshot(com.google.firebase.firestore.QuerySnapshot snap) {
        weekEvents.clear();
        Map<LocalDate, List<EventItem>> map = new HashMap<>();
        Set<String> activityIdsInWeek = new HashSet<>();

        mapSnapshotToWeekEvents(snap, map, activityIdsInWeek);
        ensureActivityNameListeners(activityIdsInWeek);

        weekEvents.putAll(map);
        dayAdapter.setEventDays(new HashSet<>(weekEvents.keySet()));
        loadEventsFor(selectedDay);
        dayAdapter.notifyDataSetChanged();

        android.util.Log.d("CAL", "📅 weekEvents.size()=" + weekEvents.size());
    }
    // crea/actualiza listeners para actividades referenciadas en la semana
    private void ensureActivityNameListeners(Set<String> activityIdsInWeek) {
        // Eliminar listeners de actividades que ya no están visibles esta semana
        Iterator<Map.Entry<String, ListenerRegistration>> it = activityNameRegs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ListenerRegistration> e = it.next();
            String key = e.getKey(); // formato "EN:ID" o "ES:ID"
            String id = key.substring(3);
            if (!activityIdsInWeek.contains(id)) {
                try { e.getValue().remove(); } catch (Exception ignore) {}
                it.remove();
            }
        }

        // Crear listeners para nuevas actividades (EN y ES)
        for (String actId : activityIdsInWeek) {
            if (actId == null) continue;

            String keyEN = "EN:" + actId;
            String keyES = "ES:" + actId;

            if (!activityNameRegs.containsKey(keyEN)) {
                ListenerRegistration regEN = db.collection("activities").document(actId)
                        .addSnapshotListener((doc, e) -> {
                            if (e != null) return;
                            if (doc != null && doc.exists()) {
                                String name = firstNonEmpty(doc.getString("nombre"),
                                        doc.getString("titulo"),
                                        doc.getString("name"));
                                String tipo = firstNonEmpty(doc.getString("tipo"),
                                        doc.getString("tipoActividad"),
                                        doc.getString("tipo_actividad"),
                                        doc.getString("tipoNombre"));
                                boolean changed = false;
                                if (name != null && !name.equals(activityNameCache.get(actId))) {
                                    activityNameCache.put(actId, name);
                                    changed = true;
                                }
                                if (tipo != null && !tipo.equals(activityTypeCache.get(actId))) { // 👈 NUEVO
                                    activityTypeCache.put(actId, tipo);
                                    changed = true;
                                }
                                if (changed) loadEventsFor(selectedDay);
                            }
                        });
                activityNameRegs.put(keyEN, regEN);
            }

            if (!activityNameRegs.containsKey(keyES)) {
                ListenerRegistration regES = db.collection("actividades").document(actId)
                        .addSnapshotListener((doc, e) -> {
                            if (e != null) return;
                            if (doc != null && doc.exists()) {
                                String name = firstNonEmpty(doc.getString("nombre"),
                                        doc.getString("titulo"),
                                        doc.getString("name"));
                                String tipo = firstNonEmpty(doc.getString("tipo"),
                                        doc.getString("tipoActividad"),
                                        doc.getString("tipo_actividad"),
                                        doc.getString("tipoNombre"));
                                boolean changed = false;
                                if (name != null && !name.equals(activityNameCache.get(actId))) {
                                    activityNameCache.put(actId, name);
                                    changed = true;
                                }
                                if (tipo != null && !tipo.equals(activityTypeCache.get(actId))) { // 👈 NUEVO
                                    activityTypeCache.put(actId, tipo);
                                    changed = true;
                                }
                                if (changed) loadEventsFor(selectedDay);
                            }
                        });
                activityNameRegs.put(keyES, regES);
            }
        }
    }


    private void mapSnapshotToWeekEvents(@NonNull QuerySnapshot snap,
                                         @NonNull Map<LocalDate, List<EventItem>> map) {
        // método viejo preservado para compatibilidad
        Set<String> ignore = new HashSet<>();
        mapSnapshotToWeekEvents(snap, map, ignore);
    }

    // ========== CalendarFragment.java - Método mapSnapshotToWeekEvents (AÑADIR LOGS) ==========

    private void mapSnapshotToWeekEvents(@NonNull QuerySnapshot snap,
                                         @NonNull Map<LocalDate, List<EventItem>> map,
                                         @NonNull Set<String> activityIdsOut) {
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Timestamp startTs = doc.getTimestamp("startAt");

            if (startTs == null) {
                String fecha = doc.getString("fecha");
                String hora  = doc.getString("horaInicio");
                try {
                    java.time.LocalDate d = java.time.LocalDate.parse(fecha);
                    String[] hhmm = (hora != null) ? hora.split(":") : new String[]{"00","00"};
                    ZonedDateTime zdt = d.atTime(
                            Integer.parseInt(hhmm[0]),
                            Integer.parseInt(hhmm[1])
                    ).atZone(ZoneId.systemDefault());
                    startTs = new Timestamp(Date.from(zdt.toInstant()));
                } catch (Exception ignored) { }
            }
            if (startTs == null) continue;

            ZonedDateTime local = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(startTs.toDate().getTime()),
                    ZoneId.systemDefault()
            );

            String horaStr = String.format(Locale.getDefault(), "%02d:%02d",
                    local.getHour(), local.getMinute());

            String lugarNombre = doc.getString("lugarNombre");
            if (lugarNombre == null) lugarNombre = "Lugar";

            String activityId = getActivityIdFromRef(doc.getReference());
            String citaId = doc.getId();

            String titulo = doc.getString("titulo");
            if (titulo == null) titulo = getActivityNameSync(activityId);

            String tipo = getActivityTypeSync(activityId);

            String estado = doc.getString("estado");
            if (estado == null) estado = "programada";

            // 👇 AÑADIR LOG CRÍTICO AQUÍ
            android.util.Log.d("CAL", "📄 Procesando cita: id=" + citaId +
                    " | estado=" + estado +
                    " | título=" + titulo);

            EventItem item = new EventItem(horaStr, titulo, lugarNombre, estado, activityId, citaId, tipo);

            LocalDate key = local.toLocalDate();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);

            if (activityId != null) activityIdsOut.add(activityId);
        }
    }

    @Nullable
    private String getActivityIdFromRef(DocumentReference ref) {
        try {
            DocumentReference parentActivity = ref.getParent().getParent();
            return parentActivity != null ? parentActivity.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private String getActivityNameSync(@Nullable String activityId) {
        if (activityId == null) return "Actividad";
        String cached = activityNameCache.get(activityId);
        if (cached != null) return cached;

        // Consulta puntual EN y luego ES
        db.collection("activities").document(activityId).get()
                .addOnSuccessListener(doc -> {
                    String name = firstNonEmpty(doc.getString("nombre"), doc.getString("titulo"), doc.getString("name"));
                    String tipo = firstNonEmpty(doc.getString("tipo"), doc.getString("tipoActividad"),
                            doc.getString("tipo_actividad"), doc.getString("tipoNombre"));
                    boolean changed = false;
                    if (name != null) {
                        activityNameCache.put(activityId, name);
                        changed = true;
                    } else {
                        db.collection("actividades").document(activityId).get()
                                .addOnSuccessListener(docEs -> {
                                    String nameEs = firstNonEmpty(docEs.getString("nombre"), docEs.getString("titulo"), docEs.getString("name"));
                                    String tipoEs = firstNonEmpty(docEs.getString("tipo"), docEs.getString("tipoActividad"),
                                            docEs.getString("tipo_actividad"), docEs.getString("tipoNombre"));
                                    if (nameEs != null) {
                                        activityNameCache.put(activityId, nameEs);
                                    }
                                    if (tipoEs != null) activityTypeCache.put(activityId, tipoEs); // 👈 NUEVO
                                    loadEventsFor(selectedDay);
                                });
                    }
                    if (tipo != null) { activityTypeCache.put(activityId, tipo); changed = true; } // 👈 NUEVO
                    if (changed) loadEventsFor(selectedDay);
                });
        return "Actividad";
    }

    // 👇 NUEVO: helper para tipo
    @Nullable
    private String getActivityTypeSync(@Nullable String activityId) {
        if (activityId == null) return null;
        String cached = activityTypeCache.get(activityId);
        if (cached != null) return cached;

        db.collection("activities").document(activityId).get()
                .addOnSuccessListener(doc -> {
                    String tipo = firstNonEmpty(doc.getString("tipo"), doc.getString("tipoActividad"),
                            doc.getString("tipo_actividad"), doc.getString("tipoNombre"));
                    if (tipo != null) {
                        activityTypeCache.put(activityId, tipo);
                        loadEventsFor(selectedDay);
                    } else {
                        db.collection("actividades").document(activityId).get()
                                .addOnSuccessListener(docEs -> {
                                    String tipoEs = firstNonEmpty(docEs.getString("tipo"), docEs.getString("tipoActividad"),
                                            docEs.getString("tipo_actividad"), docEs.getString("tipoNombre"));
                                    if (tipoEs != null) {
                                        activityTypeCache.put(activityId, tipoEs);
                                        loadEventsFor(selectedDay);
                                    }
                                });
                    }
                });
        return null;
    }

    // ----------------- MODELOS / ADAPTERS -----------------

    public static class EventItem {
        public final String hora, titulo, lugar, estado;
        public final String activityId, citaId;
        public final String tipo; // 👈 NUEVO

        public EventItem(String h, String t, String l, String e, String activityId, String citaId){
            this(h, t, l, e, activityId, citaId, null);
        }
        public EventItem(String h, String t, String l, String e, String activityId, String citaId, String tipo){
            this.hora = h;
            this.titulo = t;
            this.lugar = l;
            this.estado = e;
            this.activityId = activityId;
            this.citaId = citaId;
            this.tipo = tipo; // 👈 NUEVO
        }
    }

    static class EventAdapter extends RecyclerView.Adapter<EventVH> {
        private final List<EventItem> data;
        interface OnClick { void onTap(EventItem it); }
        private final OnClick cb;
        EventAdapter(List<EventItem> d, OnClick c){data=d;cb=c;}
        void submit(List<EventItem> d){ data.clear(); data.addAll(d); notifyDataSetChanged(); }
        @NonNull @Override public EventVH onCreateViewHolder(@NonNull ViewGroup p, int vtype){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_event, p, false);
            return new EventVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull EventVH h, int i) {
            EventItem it = data.get(i);

            // ✅ Hora
            h.tvHora.setText(it.hora == null ? "" : it.hora);

            // ✅ Mostrar nombre y lugarNombre (de la cita)
            h.tvTitulo.setText(it.titulo == null ? "Sin nombre" : it.titulo);  // ← usa campo "nombre"
            h.tvLugar.setText(it.lugar == null ? "Sin lugar" : it.lugar);      // ← usa campo "lugarNombre"

            // ================== ESTADOS ==================
            String estado = it.estado == null ? "programada" : it.estado.toLowerCase(Locale.ROOT);
            switch (estado) {
                case "cancelada":
                    h.tvEstado.setText("Cancelada");
                    h.tvEstado.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(h.itemView.getContext(), R.color.state_cancelada_pill)
                    ));
                    h.containerGradient.setBackgroundColor(
                            ContextCompat.getColor(h.itemView.getContext(), R.color.state_cancelada_bg)
                    );
                    break;
                case "reagendada":
                    h.tvEstado.setText("Reagendada");
                    h.tvEstado.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(h.itemView.getContext(), R.color.state_reagendada_pill)
                    ));
                    h.containerGradient.setBackgroundColor(
                            ContextCompat.getColor(h.itemView.getContext(), R.color.state_reagendada_bg)
                    );
                    break;
                case "finalizada":
                    h.tvEstado.setText("Finalizada");
                    h.tvEstado.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(h.itemView.getContext(), R.color.state_finalizada_pill)
                    ));
                    h.containerGradient.setBackgroundColor(
                            ContextCompat.getColor(h.itemView.getContext(), R.color.state_finalizada_bg)
                    );
                    break;
                default:
                    h.tvEstado.setText("Programada");
                    h.tvEstado.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(h.itemView.getContext(), R.color.state_programada_stroke)
                    ));
                    h.containerGradient.setBackgroundResource(R.drawable.bg_header_gradient);
                    break;
            }

            // Acción al tocar el item
            h.itemView.setOnClickListener(x -> cb.onTap(it));
        }



        @Override public int getItemCount(){ return data.size(); }
    }

    static class EventVH extends RecyclerView.ViewHolder {
        TextView tvHora, tvTitulo, tvLugar;
        TextView tvEstado;
        View containerGradient;
        EventVH(@NonNull View v){
            super(v);
            tvHora  = v.findViewById(R.id.tvHora);
            tvTitulo= v.findViewById(R.id.tvNombre);
            tvLugar = v.findViewById(R.id.tvLugar);
            tvEstado = v.findViewById(R.id.tvEstado);
            containerGradient = v.findViewById(R.id.containerGradient);
        }
    }

    static class DayAdapter extends RecyclerView.Adapter<DayVH>{
        interface DayClick { void onTap(LocalDate day); }
        private final List<LocalDate> days;
        private final DayClick cb;
        private final Set<LocalDate> eventDays = new HashSet<>();
        private int itemWidthPx = 0;

        DayAdapter(List<LocalDate> d, DayClick c){days=d;cb=c;}

        void submit(List<LocalDate> d){ days.clear(); days.addAll(d); notifyDataSetChanged(); }
        void setEventDays(Set<LocalDate> set){
            eventDays.clear();
            if (set != null) eventDays.addAll(set);
            notifyDataSetChanged();
        }
        void setItemWidth(int w){
            if (w > 0 && w != itemWidthPx) {
                itemWidthPx = w;
                notifyDataSetChanged();
            }
        }

        @NonNull @Override public DayVH onCreateViewHolder(@NonNull ViewGroup p, int vtype){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_day_chip, p, false);
            return new DayVH(v);
        }

        @Override public void onBindViewHolder(@NonNull DayVH h,int i){
            if (itemWidthPx > 0) {
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) h.itemView.getLayoutParams();
                lp.width = itemWidthPx;
                h.itemView.setLayoutParams(lp);
            }
            LocalDate d = days.get(i);
            h.bind(d, eventDays.contains(d));
            h.itemView.setOnClickListener(x -> cb.onTap(d));
        }
        @Override public int getItemCount(){ return days.size(); }
    }

    static class DayVH extends RecyclerView.ViewHolder{
        TextView tvDiaCorto, tvNum;
        View viewDot;
        DayVH(@NonNull View v){ super(v);
            tvDiaCorto = v.findViewById(R.id.tvDiaCorto);
            tvNum      = v.findViewById(R.id.tvNumDia);
            viewDot    = v.findViewById(R.id.viewDot);
        }
        void bind(LocalDate d, boolean hasEvents){
            String[] dias = {"LU","MA","MI","JU","VI","SÁ","DO"};
            tvDiaCorto.setText(dias[(d.getDayOfWeek().getValue()+6)%7]);
            tvNum.setText(String.valueOf(d.getDayOfMonth()));
            viewDot.setVisibility(hasEvents ? View.VISIBLE : View.GONE);
        }
    }

    private static String firstNonEmpty(String... xs) {
        if (xs == null) return null;
        for (String s : xs) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }
}
