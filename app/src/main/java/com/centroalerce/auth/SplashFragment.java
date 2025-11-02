package com.centroalerce.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.centroalerce.gestion.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashFragment extends Fragment {

    private static final long SPLASH_DURATION = 2500; // 2.5 segundos
    private static final long ANIMATION_DURATION = 1000; // 1 segundo para animaciones

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_splash, container, false);

        // Obtener referencias a las vistas
        ImageView logo = view.findViewById(R.id.ivLogoSplash);
        TextView title = view.findViewById(R.id.tvTitleSplash);
        TextView subtitle = view.findViewById(R.id.tvSubtitleSplash);
        View progressBar = view.findViewById(R.id.progressBarSplash);

        // Iniciar animaciones de entrada
        startEntranceAnimations(logo, title, subtitle, progressBar);

        // Navegar después del splash duration
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() != null && isAdded()) {
                navigateToNextScreen(view);
            }
        }, SPLASH_DURATION);

        return view;
    }

    /**
     * Animaciones profesionales de entrada para el splash screen
     */
    private void startEntranceAnimations(View logo, View title, View subtitle, View progressBar) {
        // Hacer todos invisibles inicialmente
        logo.setAlpha(0f);
        logo.setScaleX(0.3f);
        logo.setScaleY(0.3f);

        title.setAlpha(0f);
        title.setTranslationY(30);

        subtitle.setAlpha(0f);
        subtitle.setTranslationY(30);

        progressBar.setAlpha(0f);

        // Animar logo con efecto de zoom + fade
        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        // Animar título con delay
        title.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(800)
                .setStartDelay(400)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        // Animar subtítulo con delay
        subtitle.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(800)
                .setStartDelay(600)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        // Animar progress bar
        progressBar.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(1000)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * Navega a la pantalla de login
     */
    private void navigateToNextScreen(View view) {
        // Siempre ir al login, el usuario debe autenticarse cada vez
        Navigation.findNavController(view).navigate(R.id.action_splashFragment_to_loginFragment);
    }
}
