package com.centroalerce.ui;

import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.utils.PermissionChecker;
import com.centroalerce.gestion.utils.RoleManager;
import com.centroalerce.gestion.utils.UserRole;
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

    private LocalDate weekStart;
    private LocalDate selectedDay;
    private DayAdapter dayAdapter;
    private EventAdapter eventAdapter;
    private TextView tvRangoSemana, tvTituloDia;
    private RecyclerView rvDays;

    // ✅ NUEVO: Sistema de roles
    private PermissionChecker permissionChecker;
    private RoleManager roleManager;
    private UserRole currentUserRole;

    // Citas de la semana visible (día -> lista de eventos)
    private final Map<LocalDate, List<EventItem>> weekEvents = new HashMap<>();

    // Firestore
    private FirebaseFirestore db;
    private ListenerRegistration weekListener;

    // Cache de nombres y tipos de actividad
    private final Map<String, String> activityNameCache = new HashMap<>();
    private final Map<String, String> activityTypeCache = new HashMap<>();

    // listeners por actividad para refrescar datos al vuelo
    private final Map<String, ListenerRegistration> activityNameRegs = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_calendar, c, false);

        tvRangoSemana = v.findViewById(R.id.tvRangoSemana);
        tvTituloDia   = v.findViewById(R.id.tvTituloDia);
        rvDays        = v.findViewById(R.id.rvDays);
        RecyclerView rvEventos = v.findViewById(R.id.rvEventos);

        // ✅ NUEVO: Inicializar sistema de roles
        initializeRoleSystem();

        // Inicial
        LocalDate today = LocalDate.now();
        weekStart   = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7);
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

        // ✅ MODIFICADO: Pasar el rol al adapter
        eventAdapter = new EventAdapter(new ArrayList<>(), event -> {
            ActivityDetailBottomSheet sheet = ActivityDetailBottomSheet.newInstance(
                    event.activityId,
                    event.citaId,
                    currentUserRole // ✅ NUEVO: Pasar el rol
            );
            sheet.show(getChildFragmentManager(), "activity_detail_sheet");
        }, currentUserRole); // ✅ NUEVO: Pasar el rol al adapter

        rvEventos.setAdapter(eventAdapter);

        rvEventos.setClipToPadding(false);

        int extraBottom = dp(96);
        rvEventos.setPadding(
                rvEventos.getPaddingLeft(),
                rvEventos.getPaddingTop(),
                rvEventos.getPaddingRight(),
                rvEventos.getPaddingBottom() + extraBottom
        );

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

        // Refrescar calendario al guardar cambios en otra pantalla
        getParentFragmentManager().setFragmentResultListener(
                "calendar_refresh", getViewLifecycleOwner(),
                (req, bundle) -> refreshFromExternalChange()
        );
        getParentFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> refreshFromExternalChange()
        );

        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "calendar_refresh", getViewLifecycleOwner(),
                (req, bundle) -> refreshFromExternalChange()
        );
        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                "actividad_change", getViewLifecycleOwner(),
                (req, bundle) -> refreshFromExternalChange()
        );

        fillWeek();
        return v;
    }

    // ✅ NUEVO: Inicializar sistema de roles
    private void initializeRoleSystem() {
        permissionChecker = new PermissionChecker();
        roleManager = RoleManager.getInstance();

        // Cargar rol del usuario
        roleManager.loadUserRole((RoleManager.OnRoleLoadedListener) role -> {
            currentUserRole = role;

            if (eventAdapter != null) {
                eventAdapter.updateUserRole(role);
            }

            android.util.Log.d("CAL", "✅ Rol cargado: " + role.getValue());
        });
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
        activityNameCache.clear();
        activityTypeCache.clear();
        clearActivityNameListeners();
        fillWeek();
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

    private void listenWeekFromFirestore(LocalDate monday, LocalDate sunday) {
        android.util.Log.d("CAL", "=== listenWeekFromFirestore INICIADO ===");

        if (weekListener != null) {
            weekListener.remove();
            weekListener = null;
        }

        ZonedDateTime zStart = monday.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime zEnd   = sunday.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        Timestamp tsStart = new Timestamp(Date.from(zStart.toInstant()));
        Timestamp tsEnd   = new Timestamp(Date.from(zEnd.toInstant()));

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

                    Map<LocalDate, List<EventItem>> map = new HashMap<>();
                    Set<String> activityIdsInWeek = new HashSet<>();

                    mapSnapshotToWeekEvents(snap, map, activityIdsInWeek);

                    ensureActivityNameListeners(activityIdsInWeek);

                    weekEvents.clear();
                    weekEvents.putAll(map);
                    dayAdapter.setEventDays(new HashSet<>(weekEvents.keySet()));
                    loadEventsFor(selectedDay);
                });
    }

    private void ensureActivityNameListeners(Set<String> activityIdsInWeek) {
        Iterator<Map.Entry<String, ListenerRegistration>> it = activityNameRegs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ListenerRegistration> e = it.next();
            String key = e.getKey();
            String id = key.substring(3);
            if (!activityIdsInWeek.contains(id)) {
                try { e.getValue().remove(); } catch (Exception ignore) {}
                it.remove();
            }
        }

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
                                if (tipo != null && !tipo.equals(activityTypeCache.get(actId))) {
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
                                if (tipo != null && !tipo.equals(activityTypeCache.get(actId))) {
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
                                    if (tipoEs != null) activityTypeCache.put(activityId, tipoEs);
                                    loadEventsFor(selectedDay);
                                });
                    }
                    if (tipo != null) { activityTypeCache.put(activityId, tipo); changed = true; }
                    if (changed) loadEventsFor(selectedDay);
                });
        return "Actividad";
    }

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
        public final String tipo;

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
            this.tipo = tipo;
        }
    }

    // ✅ MODIFICADO: EventAdapter ahora recibe y usa el rol
    static class EventAdapter extends RecyclerView.Adapter<EventVH> {
        private final List<EventItem> data;
        interface OnClick { void onTap(EventItem it); }
        private final OnClick cb;
        private UserRole userRole; // ✅ NUEVO

        EventAdapter(List<EventItem> d, OnClick c, UserRole role){
            data = d;
            cb = c;
            userRole = role != null ? role : UserRole.VISUALIZADOR; // ✅ NUEVO
        }

        void submit(List<EventItem> d){
            data.clear();
            data.addAll(d);
            notifyDataSetChanged();
        }

        // ✅ NUEVO: Método para actualizar el rol
        void updateUserRole(UserRole role) {
            this.userRole = role;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EventVH onCreateViewHolder(@NonNull ViewGroup p, int vtype){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_event, p, false);
            return new EventVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull EventVH h, int i) {
            EventItem it = data.get(i);

            h.tvHora.setText(it.hora == null ? "" : it.hora);
            h.tvTitulo.setText(it.titulo == null ? "Sin nombre" : it.titulo);
            h.tvLugar.setText(it.lugar == null ? "Sin lugar" : it.lugar);

            // Estados
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

            // ✅ IMPORTANTE: Solo permitir click si puede interactuar
            // Visualizadores pueden ver detalles pero sin acciones
            h.itemView.setOnClickListener(x -> cb.onTap(it));
        }

        @Override
        public int getItemCount(){
            return data.size();
        }
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