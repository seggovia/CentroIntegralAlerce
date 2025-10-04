package com.centroalerce.ui.mantenedores.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.EditText;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.TipoActividad;

public class TipoActividadDialog extends DialogFragment {

    public interface OnSave { void save(TipoActividad t); }

    @Nullable private final TipoActividad original;
    @NonNull  private final OnSave onSave;

    public TipoActividadDialog(@Nullable TipoActividad original, @NonNull OnSave onSave){
        this.original = original; this.onSave = onSave;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle b){
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tipo_actividad, null, false);
        EditText etNombre = v.findViewById(R.id.etNombre);
        EditText etDesc   = v.findViewById(R.id.etDescripcion);

        if(original!=null){
            etNombre.setText(original.getNombre());
            etDesc.setText(original.getDescripcion());
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle(original==null?"Nuevo tipo":"Editar tipo")
                .setView(v)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar",(d,w)->{
                    String n = etNombre.getText().toString().trim();
                    String ds = TextUtils.isEmpty(etDesc.getText()) ? null : etDesc.getText().toString().trim();
                    TipoActividad t = (original==null)
                            ? new TipoActividad(null, n, ds, true)
                            : new TipoActividad(original.getId(), n, ds, original.isActivo());
                    onSave.save(t);
                }).create();
    }
}
