package com.centroalerce.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.centroalerce.gestion.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ActivityDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TITULO = "titulo";
    private static final String ARG_LUGAR  = "lugar";
    private static final String ARG_HORA   = "hora";

    public static ActivityDetailBottomSheet newInstance(String titulo, String lugar, String hora) {
        ActivityDetailBottomSheet f = new ActivityDetailBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_TITULO, titulo);
        b.putString(ARG_LUGAR, lugar);
        b.putString(ARG_HORA, hora);
        f.setArguments(b);
        return f;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        View v = LayoutInflater.from(getContext()).inflate(R.layout.layout_bottomsheet_activity_detail, null, false);

        TextView tvTitulo = v.findViewById(R.id.tvTitulo);
        TextView tvLugar  = v.findViewById(R.id.tvLugar);
        TextView tvHora   = v.findViewById(R.id.tvHora);

        Bundle args = getArguments();
        if (args != null) {
            tvTitulo.setText(args.getString(ARG_TITULO, ""));
            tvLugar.setText(args.getString(ARG_LUGAR, ""));
            tvHora.setText(args.getString(ARG_HORA, ""));
        }

        Button btnModificar = v.findViewById(R.id.btnModificar);
        Button btnCancelar  = v.findViewById(R.id.btnCancelar);
        Button btnReagendar = v.findViewById(R.id.btnReagendar);
        Button btnAdjuntar  = v.findViewById(R.id.btnAdjuntar);

        // TODO: conectar acciones reales (navegar / actualizar firebase)
        btnModificar.setOnClickListener(x -> dismiss());
        btnCancelar.setOnClickListener(x -> dismiss());
        btnReagendar.setOnClickListener(x -> dismiss());
        btnAdjuntar.setOnClickListener(x -> dismiss());

        dialog.setContentView(v);
        return dialog;
    }
}
