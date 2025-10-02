package com.centroalerce.ui;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

// ===== NUEVO: Firebase / Firestore =====
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
import java.util.*; // List, Map, HashMap, etc.

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
    private final Map<String, String> activityNameCache = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_calendar, c, false);

        tvRangoSemana = v.findViewById(R.id.tvRangoSemana);
        tvTituloDia   = v.findViewById(R.id.tvTituloDia);
        rvDays        = v.findViewById(R.id.rvDays);
        RecyclerView rvEventos = v.findViewById(R.id.rvEventos);
        FloatingActionButton fab = v.findViewById(R.id.fabAdd);

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
            // ✅ ahora pasamos los IDs correctos
            ActivityDetailBottomSheet sheet = ActivityDetailBottomSheet.newInstance(
                    event.activityId,
                    event.citaId
            );
            sheet.show(getChildFragmentManager(), "activity_detail_sheet");
        });
        rvEventos.setAdapter(eventAdapter);

        // Navegación de semana
        v.findViewById(R.id.btnSemanaAnterior).setOnClickListener(x -> {
            weekStart = weekStart.minusWeeks(1);
            fillWeek();
        });
        v.findViewById(R.id.btnSemanaSiguiente).setOnClickListener(x -> {
            weekStart = weekStart.plusWeeks(1);
            fillWeek();
        });

        // FAB crear actividad
        fab.setOnClickListener(x -> Navigation.findNavController(v).navigate(R.id.activityFormFragment));

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
                        android.util.Log.e("CAL", "Error escuchando citas", err);
                        return;
                    }
                    if (snap == null) return;

                    Map<LocalDate, List<EventItem>> map = new HashMap<>();
                    mapSnapshotToWeekEvents(snap, map);

                    weekEvents.clear();
                    weekEvents.putAll(map);
                    dayAdapter.setEventDays(new HashSet<>(weekEvents.keySet()));
                    loadEventsFor(selectedDay);
                });
    }

    private void mapSnapshotToWeekEvents(@NonNull QuerySnapshot snap,
                                         @NonNull Map<LocalDate, List<EventItem>> map) {
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Timestamp startTs = doc.getTimestamp("startAt");

            if (startTs == null) {
                String fecha = doc.getString("fecha");
                String hora  = doc.getString("horaInicio");
                try {
                    LocalDate d = LocalDate.parse(fecha);
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
            String citaId = doc.getId(); // ✅

            String titulo = doc.getString("titulo");
            if (titulo == null) titulo = getActivityNameSync(activityId);

            // ✅ EventItem ahora incluye activityId y citaId
            EventItem item = new EventItem(horaStr, titulo, lugarNombre, activityId, citaId);

            LocalDate key = local.toLocalDate();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
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

    private String getActivityNameSync(@Nullable String activityId) {
        if (activityId == null) return "Actividad";
        String cached = activityNameCache.get(activityId);
        if (cached != null) return cached;

        db.collection("activities").document(activityId).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("nombre");
                    if (name != null) {
                        activityNameCache.put(activityId, name);
                        loadEventsFor(selectedDay);
                    }
                });
        return "Actividad";
    }

    // ----------------- MODELOS / ADAPTERS -----------------

    public static class EventItem {
        public final String hora, titulo, lugar;
        public final String activityId, citaId; // ✅ nuevos
        public EventItem(String h, String t, String l, String activityId, String citaId){
            this.hora = h;
            this.titulo = t;
            this.lugar = l;
            this.activityId = activityId;
            this.citaId = citaId;
        }
    }

    static class EventAdapter extends RecyclerView.Adapter<EventVH> {
        private final List<EventItem> data;
        interface OnClick { void onTap(EventItem it); }
        private final OnClick cb;
        EventAdapter(List<EventItem> d, OnClick c){data=d;cb=c;}
        void submit(List<EventItem> d){ data.clear(); data.addAll(d); notifyDataSetChanged();}
        @NonNull @Override public EventVH onCreateViewHolder(@NonNull ViewGroup p, int vtype){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_event, p, false);
            return new EventVH(v);
        }
        @Override public void onBindViewHolder(@NonNull EventVH h, int i){
            EventItem it = data.get(i);
            h.tvHora.setText(it.hora);
            h.tvTitulo.setText(it.titulo);
            h.tvLugar.setText(it.lugar);
            h.itemView.setOnClickListener(x -> cb.onTap(it));
        }
        @Override public int getItemCount(){ return data.size(); }
    }

    static class EventVH extends RecyclerView.ViewHolder {
        TextView tvHora, tvTitulo, tvLugar;
        EventVH(@NonNull View v){
            super(v);
            tvHora  = v.findViewById(R.id.tvHora);
            tvTitulo= v.findViewById(R.id.tvNombre);
            tvLugar = v.findViewById(R.id.tvLugar);
        }
    }

    static class DayAdapter extends RecyclerView.Adapter<DayVH>{
        interface DayClick { void onTap(LocalDate day); }
        private final List<LocalDate> days;
        private final DayClick cb;
        private final Set<LocalDate> eventDays = new HashSet<>();
        private int itemWidthPx = 0;

        DayAdapter(List<LocalDate> d, DayClick c){days=d;cb=c;}

        void submit(List<LocalDate> d){ days.clear(); days.addAll(d); notifyDataSetChanged();}
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
}
