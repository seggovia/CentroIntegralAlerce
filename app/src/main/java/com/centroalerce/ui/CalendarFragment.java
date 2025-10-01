package com.centroalerce.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.centroalerce.gestion.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CalendarFragment extends Fragment {

    private LocalDate weekStart;      // lunes de la semana visible
    private LocalDate selectedDay;    // día seleccionado
    private DayAdapter dayAdapter;
    private EventAdapter eventAdapter;
    private TextView tvRangoSemana, tvTituloDia;

    public CalendarFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_calendar, c, false);

        tvRangoSemana = v.findViewById(R.id.tvRangoSemana);
        tvTituloDia   = v.findViewById(R.id.tvTituloDia);
        RecyclerView rvDays    = v.findViewById(R.id.rvDays);
        RecyclerView rvEventos = v.findViewById(R.id.rvEventos);
        FloatingActionButton fab = v.findViewById(R.id.fabAdd);

        // Inicial
        LocalDate today = LocalDate.now();
        weekStart = today.minusDays((today.getDayOfWeek().getValue()+6)%7); // Lunes
        selectedDay = today;

        // Lista de días (7 chips) – ancho fijo por código
        rvDays.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvDays.setItemAnimator(null); // evita animaciones/“saltos” al cambiar semana
        dayAdapter = new DayAdapter(new ArrayList<>(), d -> {
            selectedDay = d;
            updateDayTitle();
            loadEventsFor(selectedDay);
        });
        rvDays.setAdapter(dayAdapter);

        // Lista de eventos del día
        rvEventos.setLayoutManager(new LinearLayoutManager(getContext()));
        rvEventos.setItemAnimator(null);
        eventAdapter = new EventAdapter(new ArrayList<>(), event -> {
            ActivityDetailBottomSheet sheet = ActivityDetailBottomSheet.newInstance(
                    event.titulo, event.lugar, event.hora
            );
            sheet.show(getChildFragmentManager(), "activity_detail_sheet");
        });
        rvEventos.setAdapter(eventAdapter);

        // Botones semana anterior/siguiente
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

    private void fillWeek() {
        // Lunes..Domingo: SIEMPRE 7 elementos
        List<LocalDate> week = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) week.add(weekStart.plusDays(i));
        dayAdapter.submit(week);

        // Rango "29 SEPT – 5 OCT"
        DateTimeFormatter d1 = DateTimeFormatter.ofPattern("d MMM", new Locale("es","CL"));
        String start = week.get(0).format(d1).toUpperCase(Locale.ROOT);
        String end   = week.get(6).format(d1).toUpperCase(Locale.ROOT);
        tvRangoSemana.setText(start + " – " + end);

        // Marcar días con eventos (DEMO)
        Set<LocalDate> daysWithEvents = loadEventDaysDemo(weekStart);
        dayAdapter.setEventDays(daysWithEvents);

        // Mantener seleccionado dentro de la semana
        if (selectedDay.isBefore(weekStart) || selectedDay.isAfter(weekStart.plusDays(6))) {
            selectedDay = weekStart;
        }
        updateDayTitle();
        loadEventsFor(selectedDay);
    }

    private void updateDayTitle() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es","CL"));
        String t = selectedDay.format(df);
        tvTituloDia.setText(t.substring(0,1).toUpperCase()+t.substring(1));
    }

    private void loadEventsFor(LocalDate day) {
        // TODO: conectar a Firestore (collectionGroup "citas" por fecha 'day')
        // Demo
        List<EventItem> demo = new ArrayList<>();
        if (day.getDayOfWeek().getValue() <= 5) { // solo L-V demo
            demo.add(new EventItem("10:00","Taller de alfabetización digital","Oficina del centro"));
            demo.add(new EventItem("12:30","Atención psicológica","Sala 2"));
        }
        eventAdapter.submit(demo);
    }

    /** DEMO: retorna días de la semana con eventos para mostrar el punto azul. */
    private Set<LocalDate> loadEventDaysDemo(LocalDate monday) {
        Set<LocalDate> set = new HashSet<>();
        set.add(monday.plusDays(1)); // martes
        set.add(monday.plusDays(3)); // jueves
        return set;
    }

    // --------- Modelos y Adapters ---------

    public static class EventItem {
        public final String hora, titulo, lugar;
        public EventItem(String h,String t,String l){hora=h;titulo=t;lugar=l;}
    }

    static class EventAdapter extends RecyclerView.Adapter<EventVH> {
        private final List<EventItem> data;
        private final OnClick cb;
        interface OnClick { void onTap(EventItem it); }
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
        private final List<LocalDate> days; private final DayClick cb;
        private final Set<LocalDate> eventDays = new HashSet<>();

        DayAdapter(List<LocalDate> d, DayClick c){days=d;cb=c;}

        void submit(List<LocalDate> d){
            days.clear(); days.addAll(d); notifyDataSetChanged();
        }

        void setEventDays(Set<LocalDate> set){
            eventDays.clear();
            if (set != null) eventDays.addAll(set);
            notifyDataSetChanged();
        }

        @NonNull @Override public DayVH onCreateViewHolder(@NonNull ViewGroup p, int vtype){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_day_chip, p, false);
            // Fijar ancho = contenedor/7 para que NO se deforme nunca
            v.post(() -> {
                int pw = p.getWidth();
                if (pw > 0) {
                    RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) v.getLayoutParams();
                    lp.width = pw / 7;
                    v.setLayoutParams(lp);
                }
            });
            return new DayVH(v);
        }

        @Override public void onBindViewHolder(@NonNull DayVH h,int i){
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
            String[] dias = {"LUN","MAR","MIÉ","JUE","VIE","SÁB","DOM"};
            tvDiaCorto.setText(dias[(d.getDayOfWeek().getValue()+6)%7]);
            tvNum.setText(String.valueOf(d.getDayOfMonth()));
            viewDot.setVisibility(hasEvents ? View.VISIBLE : View.GONE);
        }
    }
}
