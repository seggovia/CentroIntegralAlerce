package com.centroalerce.gestion.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Comportamiento personalizado para FAB que se oculta al hacer scroll hacia abajo
 * y reaparece al hacer scroll hacia arriba
 */
public class ScrollAwareFABBehavior extends FloatingActionButton.Behavior {

    public ScrollAwareFABBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout,
                                       FloatingActionButton child,
                                       View directTargetChild,
                                       View target,
                                       int axes,
                                       int type) {
        // Solo interceptar scroll vertical
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL ||
               super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type);
    }

    @Override
    public void onNestedScroll(CoordinatorLayout coordinatorLayout,
                               FloatingActionButton child,
                               View target,
                               int dxConsumed,
                               int dyConsumed,
                               int dxUnconsumed,
                               int dyUnconsumed,
                               int type,
                               int[] consumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed);

        // dyConsumed > 0 significa scroll hacia abajo, < 0 significa scroll hacia arriba
        if (dyConsumed > 0 && child.isShown()) {
            // Ocultar FAB al hacer scroll hacia abajo
            child.hide();
        } else if (dyConsumed < 0 && !child.isShown()) {
            // Mostrar FAB al hacer scroll hacia arriba
            child.show();
        }
    }
}
