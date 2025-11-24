package com.centroalerce.gestion.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
        show(context, message, type, Toast.LENGTH_SHORT);
    }

    public static void show(Context context, String message, int type, int duration) {
        if (context == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.custom_toast, null);

        ImageView icon = layout.findViewById(R.id.toast_icon);
        TextView text = layout.findViewById(R.id.toast_message);

        text.setText(message);

        boolean isDarkMode = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isDarkMode = (context.getResources().getConfiguration().uiMode &
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } else {
            isDarkMode = (context.getResources().getConfiguration().uiMode &
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        int backgroundColor;
        int iconTint = Color.WHITE;
        int textColor = Color.WHITE;

        switch (type) {
            case TYPE_SUCCESS:
                backgroundColor = Color.parseColor(isDarkMode ? "#2E7D32" : "#4CAF50");
                icon.setImageResource(R.drawable.ic_checkmark);
                break;
            case TYPE_ERROR:
                backgroundColor = Color.parseColor(isDarkMode ? "#C62828" : "#F44336");
                icon.setImageResource(R.drawable.ic_close);
                break;
            default:
                backgroundColor = Color.parseColor(isDarkMode ? "#1565C0" : "#2196F3");
                icon.setImageResource(R.drawable.ic_info);
                break;
        }

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(28f);
        background.setColor(backgroundColor);
        layout.setBackground(background);

        icon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        text.setTextColor(textColor);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 150);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public static void showSuccess(Context context, String message) {
        show(context, message, TYPE_SUCCESS);
    }

    public static void showSuccessLong(Context context, String message) {
        show(context, message, TYPE_SUCCESS, Toast.LENGTH_LONG);
    }

    public static void showError(Context context, String message) {
        show(context, message, TYPE_ERROR);
    }

    public static void showErrorLong(Context context, String message) {
        show(context, message, TYPE_ERROR, Toast.LENGTH_LONG);
    }

    public static void showInfo(Context context, String message) {
        show(context, message, TYPE_INFO);
    }

    public static void showInfoLong(Context context, String message) {
        show(context, message, TYPE_INFO, Toast.LENGTH_LONG);
    }
}
