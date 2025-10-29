package com.centroalerce.ui;

import android.os.Bundle;
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

    private FirebaseFirestore db;
    private RecyclerView recyclerViewActividades;
    private TextView tvTotalActividades;
    private TextView tvActividadesMes;
    private MaterialButton btnVolver;

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

        // Configurar RecyclerView
        recyclerViewActividades.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewActividades.setAdapter(new ActividadRegistroAdapter(new ArrayList<>()));

        // Botón de retroceso
        btnVolver.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).popBackStack();
        });

        // Cargar datos
        cargarEstadisticas();
        cargarActividades();
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
                .limit(50)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<ActividadRegistro> actividades = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ActividadRegistro actividad = new ActividadRegistro();
                        actividad.id = doc.getId();
                        actividad.nombre = doc.getString("nombre");
                        actividad.tipo = doc.getString("tipoActividad");
                        actividad.fechaCreacion = doc.getDate("createdAt");
                        actividad.estado = doc.getString("estado");
                        
                        actividades.add(actividad);
                    }
                    
                    recyclerViewActividades.setAdapter(new ActividadRegistroAdapter(actividades));
                });
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
