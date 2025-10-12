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

    @Nullable
    private final TipoActividad original;
    @NonNull
    private final OnSave onSave;

    public TipoActividadDialog(@Nullable TipoActividad original, @NonNull OnSave onSave){
        this.original = original;
        this.onSave = onSave;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle b){
        // Inflamos el layout personalizado
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tipo_actividad, null, false);
        EditText etNombre = v.findViewById(R.id.etNombre);
        EditText etDesc   = v.findViewById(R.id.etDescripcion);

        if (original != null) {
            etNombre.setText(original.getNombre());
            etDesc.setText(original.getDescripcion());
        }

        // 🔹 Eliminado el título y los botones del AlertDialog
        // 🔹 Ahora solo devuelve el layout sin botones (estos estarán en el XML)
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(v)
                .create();

        // Ajuste visual del diálogo (para ancho completo y correcto resize del teclado)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }

        return dialog;
    }
}
