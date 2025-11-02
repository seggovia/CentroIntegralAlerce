package com.centroalerce.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RegistroActividadesFragment extends Fragment {

    private static final String TAG = "RegistroActividades";

    private FirebaseFirestore db;
    private RecyclerView recyclerViewActividades;
    private TextView tvTotalActividades;
    private TextView tvActividadesMes;
    private MaterialButton btnVolver;
    private MaterialCardView cardTotalActividades;
    private MaterialCardView cardActividadesMes;

    // Lista completa de actividades cargadas
    private List<ActividadRegistro> todasActividades = new ArrayList<>();

    // Filtro actual: "todas" o "mes"
    private String filtroActual = "todas";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registro_actividades, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        recyclerViewActividades = view.findViewById(R.id.recyclerViewActividades);
        tvTotalActividades = view.findViewById(R.id.tvTotalActividades);
        tvActividadesMes = view.findViewById(R.id.tvActividadesMes);
        btnVolver = view.findViewById(R.id.btnVolver);
        cardTotalActividades = view.findViewById(R.id.cardTotalActividades);
        cardActividadesMes = view.findViewById(R.id.cardActividadesMes);

        // Configurar RecyclerView
        recyclerViewActividades.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewActividades.setAdapter(new ActividadRegistroAdapter(new ArrayList<>()));

        // Botón de retroceso
        btnVolver.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).popBackStack();
        });

        // Configurar listeners de filtrado en los cards
        setupCardListeners();

        // Cargar datos
        cargarEstadisticas();
        cargarActividades();
    }

    private void setupCardListeners() {
        // Click en "Total Actividades" -> Mostrar todas
        cardTotalActividades.setOnClickListener(v -> {
            filtroActual = "todas";
            Log.d(TAG, "Filtro aplicado: TODAS las actividades");
            aplicarFiltro();
        });

        // Click en "Este Mes" -> Mostrar solo del mes actual
        cardActividadesMes.setOnClickListener(v -> {
            filtroActual = "mes";
            Log.d(TAG, "Filtro aplicado: Actividades de ESTE MES");
            aplicarFiltro();
        });
    }

    private void cargarEstadisticas() {
        // Cargar total de actividades
        db.collection("activities")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int total = queryDocumentSnapshots.size();
                    tvTotalActividades.setText(String.valueOf(total));
                });

        // Cargar actividades de este mes
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        Date inicioMes = calendar.getTime();
        
        db.collection("activities")
                .whereGreaterThanOrEqualTo("createdAt", inicioMes)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int esteMes = queryDocumentSnapshots.size();
                    tvActividadesMes.setText(String.valueOf(esteMes));
                });
    }

    private void cargarActividades() {
        db.collection("activities")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                // ✅ REMOVIDO limit(50) para cargar TODAS las actividades reales
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    todasActividades.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ActividadRegistro actividad = new ActividadRegistro();
                        actividad.id = doc.getId();
                        actividad.nombre = doc.getString("nombre");
                        actividad.tipo = doc.getString("tipoActividad");
                        actividad.fechaCreacion = doc.getDate("createdAt");
                        actividad.estado = doc.getString("estado");

                        todasActividades.add(actividad);
                    }

                    Log.d(TAG, "✅ Cargadas " + todasActividades.size() + " actividades reales desde Firestore");

                    // Aplicar el filtro actual (por defecto "todas")
                    aplicarFiltro();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error cargando actividades: " + e.getMessage(), e);
                });
    }

    /**
     * Aplica el filtro seleccionado y actualiza el RecyclerView
     */
    private void aplicarFiltro() {
        List<ActividadRegistro> actividadesFiltradas = new ArrayList<>();

        if (filtroActual.equals("todas")) {
            // Mostrar todas las actividades
            actividadesFiltradas.addAll(todasActividades);
            Log.d(TAG, "Mostrando todas: " + actividadesFiltradas.size() + " actividades");
        } else if (filtroActual.equals("mes")) {
            // Filtrar solo las del mes actual
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            Date inicioMes = calendar.getTime();

            for (ActividadRegistro actividad : todasActividades) {
                if (actividad.fechaCreacion != null && !actividad.fechaCreacion.before(inicioMes)) {
                    actividadesFiltradas.add(actividad);
                }
            }

            Log.d(TAG, "Mostrando este mes: " + actividadesFiltradas.size() + " actividades");
        }

        // Actualizar el RecyclerView con las actividades filtradas
        recyclerViewActividades.setAdapter(new ActividadRegistroAdapter(actividadesFiltradas));
    }

    // Clase para representar una actividad en el registro
    public static class ActividadRegistro {
        public String id;
        public String nombre;
        public String tipo;
        public Date fechaCreacion;
        public String estado;
    }

    // Adapter simple para el RecyclerView
    private static class ActividadRegistroAdapter extends RecyclerView.Adapter<ActividadRegistroAdapter.ViewHolder> {
        private List<ActividadRegistro> actividades;

        public ActividadRegistroAdapter(List<ActividadRegistro> actividades) {
            this.actividades = actividades;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_actividad_registro, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActividadRegistro actividad = actividades.get(position);
            
            holder.text1.setText(actividad.nombre);
            
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String fecha = actividad.fechaCreacion != null ? 
                    sdf.format(actividad.fechaCreacion) : "Sin fecha";
            
            holder.text2.setText(String.format("%s - %s", actividad.tipo, fecha));
        }

        @Override
        public int getItemCount() {
            return actividades.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1;
            TextView text2;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(R.id.text1);
                text2 = itemView.findViewById(R.id.text2);
            }
        }
    }
}
