package com.agrocontrol.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.agrocontrol.app.ui.DashboardFragment;
import com.agrocontrol.app.ui.PumpFragment;
import com.agrocontrol.app.ui.HistoryFragment;
import com.agrocontrol.app.ui.AlertsFragment;
import com.agrocontrol.app.ui.ConfigFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private ImageButton btnTheme;
    private static final String PREFS   = "AgroPrefs";
    private static final String KEY_DARK = "dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aplicar tema guardado ANTES de inflar vistas
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(KEY_DARK, true);
        AppCompatDelegate.setDefaultNightMode(
            isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        btnTheme  = findViewById(R.id.btn_theme);

        // Saludo dinámico
        TextView tvGreeting = findViewById(R.id.tv_greeting);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12)      tvGreeting.setText("Buenos días");
        else if (hour < 18) tvGreeting.setText("Buenas tardes");
        else                tvGreeting.setText("Buenas noches");

        // Toggle tema — guarda y recrea la Activity
        btnTheme.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean newDark = !p.getBoolean(KEY_DARK, true);
            p.edit().putBoolean(KEY_DARK, newDark).commit();
            // recreate() aplica el nuevo tema a TODAS las vistas de una vez
            recreate();
        });

        if (savedInstanceState == null) {
            loadFragment(new DashboardFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();
            if      (id == R.id.nav_dashboard) fragment = new DashboardFragment();
            else if (id == R.id.nav_pump)      fragment = new PumpFragment();
            else if (id == R.id.nav_history)   fragment = new HistoryFragment();
            else if (id == R.id.nav_alerts)    fragment = new AlertsFragment();
            else if (id == R.id.nav_config)    fragment = new ConfigFragment();
            if (fragment != null) { loadFragment(fragment); return true; }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment).commit();
    }

    public void navigateTo(int menuItemId) {
        bottomNav.setSelectedItemId(menuItemId);
    }
}
