package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.centroalerce.gestion.R;
import com.google.android.material.card.MaterialCardView;

public class MaintainersFragment extends Fragment {

    public MaintainersFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        // Asegúrate de que el nombre del layout es EXACTO al archivo
        return inf.inflate(R.layout.fragment_maintainers, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        MaterialCardView btnTipos     = v.findViewById(R.id.btnTipos);
        MaterialCardView btnLugares   = v.findViewById(R.id.btnLugares);
        MaterialCardView btnOferentes = v.findViewById(R.id.btnOferentes);
        MaterialCardView btnSocios    = v.findViewById(R.id.btnSocios);
        MaterialCardView btnProyectos = v.findViewById(R.id.btnProyectos);
        MaterialCardView btnBeneficiarios = v.findViewById(R.id.btnBeneficiarios);

        // Animaciones de transición entre fragments
        NavOptions navOptions = new NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.slide_out_left)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)
                .build();

        MaterialCardView[] cards = new MaterialCardView[]{
                btnTipos,
                btnLugares,
                btnOferentes,
                btnSocios,
                btnBeneficiarios,
                btnProyectos
        };

        long baseDelay = 40L;
        for (int i = 0; i < cards.length; i++) {
            MaterialCardView card = cards[i];
            if (card == null) continue;
            long delay = i * baseDelay;
            animateEntry(card, delay);
        }

        // Navegar por ID de destino (más robusto que por action si hay dudas de acciones)
        if (btnTipos != null)     btnTipos.setOnClickListener(_v -> {
            animatePress(btnTipos);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.tiposActividadFragment, null, navOptions);
        });
        if (btnLugares != null)   btnLugares.setOnClickListener(_v -> {
            animatePress(btnLugares);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.lugaresFragment, null, navOptions);
        });
        if (btnOferentes != null) btnOferentes.setOnClickListener(_v -> {
            animatePress(btnOferentes);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.oferentesFragment, null, navOptions);
        });
        if (btnSocios != null)    btnSocios.setOnClickListener(_v -> {
            animatePress(btnSocios);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.sociosFragment, null, navOptions);
        });
        if (btnProyectos != null) btnProyectos.setOnClickListener(_v -> {
            animatePress(btnProyectos);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.proyectosFragment, null, navOptions);
        });
        // ← NUEVO: ir a la lista/CRUD de Beneficiarios
        if (btnBeneficiarios != null)
            btnBeneficiarios.setOnClickListener(_v -> {
                animatePress(btnBeneficiarios);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.beneficiariosFragment, null, navOptions);
            });
    }

    private void animatePress(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setRotationY(0f);
        v.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .rotationY(8f)
                .setDuration(110)
                .withEndAction(() -> v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotationY(0f)
                        .setDuration(130)
                        .setInterpolator(new OvershootInterpolator(1.4f)))
                .start();
    }

    private void animateEntry(View v, long delay) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(32f);
        v.setScaleX(0.95f);
        v.setScaleY(0.95f);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(320)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();
    }
}
