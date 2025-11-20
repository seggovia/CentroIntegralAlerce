package com.centroalerce.gestion.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.centroalerce.gestion.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador personalizado para el autocompletado de emails
 */
public class EmailAutocompleteAdapter extends ArrayAdapter<String> {

    private final List<String> emailList;
    private List<String> filteredList;
    private final LayoutInflater inflater;

    public EmailAutocompleteAdapter(@NonNull Context context, List<String> emails) {
        super(context, 0, emails);
        this.emailList = new ArrayList<>(emails);
        this.filteredList = new ArrayList<>(emails);
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return filteredList.size();
    }

    @Nullable
    @Override
    public String getItem(int position) {
        return filteredList.get(position);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_email_suggestion, parent, false);
            holder = new ViewHolder();
            holder.textEmail = convertView.findViewById(R.id.tvEmailSuggestion);
            holder.iconEmail = convertView.findViewById(R.id.ivEmailIcon);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String email = getItem(position);
        if (email != null) {
            holder.textEmail.setText(email);
        }

        return convertView;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();

                if (constraint == null || constraint.length() == 0) {
                    // Si no hay texto, mostrar todos los emails
                    results.values = new ArrayList<>(emailList);
                    results.count = emailList.size();
                } else {
                    // Filtrar emails que contengan el texto
                    String query = constraint.toString().toLowerCase();
                    List<String> filtered = new ArrayList<>();

                    for (String email : emailList) {
                        if (email.toLowerCase().contains(query)) {
                            filtered.add(email);
                        }
                    }

                    results.values = filtered;
                    results.count = filtered.size();
                }

                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList = (List<String>) results.values;

                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return (String) resultValue;
            }
        };
    }

    /**
     * Actualiza la lista de emails
     */
    public void updateEmails(List<String> newEmails) {
        emailList.clear();
        emailList.addAll(newEmails);
        filteredList.clear();
        filteredList.addAll(newEmails);
        notifyDataSetChanged();
    }

    private static class ViewHolder {
        TextView textEmail;
        View iconEmail;
    }
}