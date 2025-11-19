package com.centroalerce.gestion.utils;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.centroalerce.gestion.R;

public class CustomToast {

    public static final int TYPE_SUCCESS = 1;
    public static final int TYPE_ERROR = 2;
    public static final int TYPE_INFO = 3;

    public static void show(Context context, String message, int type) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.custom_toast, null);

        ImageView icon = layout.findViewById(R.id.toast_icon);
        TextView text = layout.findViewById(R.id.toast_message);

        text.setText(message);

        // Crear drawable con fondo redondeado y color según el tipo
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(24f); // Bordes más redondeados para look moderno

        // Configurar icono y color de fondo según el tipo
        switch (type) {
            case TYPE_SUCCESS:
                icon.setImageResource(android.R.drawable.ic_menu_save);
                icon.setColorFilter(android.graphics.Color.WHITE);
                background.setColor(android.graphics.Color.parseColor("#4CAF50")); // Verde brillante
                break;
            case TYPE_ERROR:
                icon.setImageResource(android.R.drawable.ic_dialog_alert);
                icon.setColorFilter(android.graphics.Color.WHITE);
                background.setColor(android.graphics.Color.parseColor("#F44336")); // Rojo brillante
                break;
            case TYPE_INFO:
                icon.setImageResource(android.R.drawable.ic_dialog_info);
                icon.setColorFilter(android.graphics.Color.WHITE);
                background.setColor(android.graphics.Color.parseColor("#2196F3")); // Azul brillante
                break;
        }

        // Aplicar el fondo al layout
        layout.setBackground(background);

        Toast toast = new Toast(context);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 150);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }

    public static void showSuccess(Context context, String message) {
        show(context, message, TYPE_SUCCESS);
    }

    public static void showError(Context context, String message) {
        show(context, message, TYPE_ERROR);
    }

    public static void showInfo(Context context, String message) {
        show(context, message, TYPE_INFO);
    }
}
