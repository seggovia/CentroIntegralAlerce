package com.centroalerce.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.centroalerce.gestion.R;

public class HomePagerFragment extends Fragment {

    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_pager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager = view.findViewById(R.id.viewPagerHome);
        if (viewPager != null) {
            viewPager.setAdapter(new HomePagerAdapter(this));
            viewPager.setOffscreenPageLimit(2);
            // Empezar en la página central (Calendario)
            viewPager.setCurrentItem(1, false);
        }
    }

    public ViewPager2 getViewPager() {
        return viewPager;
    }

    private static class HomePagerAdapter extends FragmentStateAdapter {

        public HomePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new ActivitiesListFragment();
                case 1:
                    return new CalendarFragment();
                case 2:
                    return new SettingsFragment();
                default:
                    return new CalendarFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
