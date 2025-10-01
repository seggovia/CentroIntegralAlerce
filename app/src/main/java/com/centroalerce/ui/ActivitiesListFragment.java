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
import java.util.*;

public class ActivitiesListFragment extends Fragment {

    public ActivitiesListFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_activities_list, c, false);
        RecyclerView rv = v.findViewById(R.id.rvActivities);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        ActivityAdapter ad = new ActivityAdapter(new ArrayList<>(), it ->
                Navigation.findNavController(v).navigate(R.id.activityDetailFragment)); // TODO: pasar id
        rv.setAdapter(ad);

        // TODO: cargar desde Firestore. Demo:
        ad.submit(Arrays.asList(
                new Item("Taller de alfabetización digital","Taller • Oficina del centro"),
                new Item("Atención psicológica","Atención • Sala 2")
        ));
        return v;
    }

    // ---- Adapter mínimo ----
    static class Item { String title, subtitle; Item(String t,String s){title=t;subtitle=s;} }

    static class ActivityAdapter extends RecyclerView.Adapter<ActivityVH>{
        interface Click { void onTap(Item it); }
        private final List<Item> data; private final Click cb;
        ActivityAdapter(List<Item> d, Click c){ data=d; cb=c; }
        void submit(List<Item> d){ data.clear(); data.addAll(d); notifyDataSetChanged(); }
        @NonNull @Override public ActivityVH onCreateViewHolder(@NonNull ViewGroup p, int vtype){
            return new ActivityVH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_activity_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ActivityVH h,int i){
            Item it = data.get(i);
            h.title.setText(it.title); h.subtitle.setText(it.subtitle);
            h.itemView.setOnClickListener(x -> cb.onTap(it));
        }
        @Override public int getItemCount(){ return data.size(); }
    }

    static class ActivityVH extends RecyclerView.ViewHolder{
        TextView title, subtitle;
        ActivityVH(@NonNull View v){ super(v);
            title=v.findViewById(R.id.tvTitle); subtitle=v.findViewById(R.id.tvSubtitle);
        }
    }
}
