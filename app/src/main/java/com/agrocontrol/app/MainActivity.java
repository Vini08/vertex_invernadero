package com.agrocontrol.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.agrocontrol.app.mqtt.MqttManager;
import com.agrocontrol.app.ui.DashboardFragment;
import com.agrocontrol.app.ui.PumpFragment;
import com.agrocontrol.app.ui.HistoryFragment;
import com.agrocontrol.app.ui.AlertsFragment;
import com.agrocontrol.app.ui.ConfigFragment;

public class MainActivity extends AppCompatActivity implements MqttManager.MqttCallback {

    private BottomNavigationView bottomNav;
    private ImageButton btnTheme;
    private FloatingActionButton fabEmergency;
    private LinearLayout bannerEmergency;
    private Button btnResetEmergency;
    private TextView tvStatus;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean emergencyActive = false;

    private static final String PREFS    = "AgroPrefs";
    private static final String KEY_DARK = "dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(KEY_DARK, true);
        AppCompatDelegate.setDefaultNightMode(
            isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MqttManager.getInstance().addCallback(this);
        MqttManager.getInstance().connect(this);

        bindViews();
        setupGreeting();
        setupButtons();

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

    private void bindViews() {
        bottomNav         = findViewById(R.id.bottom_nav);
        btnTheme          = findViewById(R.id.btn_theme);
        fabEmergency      = findViewById(R.id.fab_emergency);
        bannerEmergency   = findViewById(R.id.banner_emergency);
        btnResetEmergency = findViewById(R.id.btn_reset_emergency);
        tvStatus          = findViewById(R.id.tv_status);
    }

    private void setupGreeting() {
        TextView tvGreeting = findViewById(R.id.tv_greeting);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12)      tvGreeting.setText("Buenos días");
        else if (hour < 18) tvGreeting.setText("Buenas tardes");
        else                tvGreeting.setText("Buenas noches");
    }

    private void setupButtons() {
        btnTheme.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean newDark = !p.getBoolean(KEY_DARK, true);
            p.edit().putBoolean(KEY_DARK, newDark).commit();
            recreate();
        });

        fabEmergency.setOnClickListener(v -> showEmergencyDialog());

        btnResetEmergency.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Resetear emergencia")
                .setMessage("El sistema volverá a funcionar normalmente. ¿Confirmar?")
                .setPositiveButton("RESETEAR", (d, w) -> resetEmergency())
                .setNegativeButton("Cancelar", null)
                .show();
        });
    }

    private void showEmergencyDialog() {
        if (emergencyActive) {
            new AlertDialog.Builder(this)
                .setTitle("⛔ Emergencia activa")
                .setMessage("El sistema está bloqueado. ¿Deseas resetear y volver a la operación normal?")
                .setPositiveButton("RESETEAR", (d, w) -> resetEmergency())
                .setNegativeButton("Cancelar", null)
                .show();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("⛔ Apagado de emergencia")
                .setMessage("Esto apagará INMEDIATAMENTE la bomba, ventilador y calentador, y bloqueará el auto-riego.\n\n¿Confirmar?")
                .setPositiveButton("SÍ, APAGAR TODO", (d, w) -> activateEmergency())
                .setNegativeButton("Cancelar", null)
                .show();
        }
    }

    private void activateEmergency() {
        emergencyActive = true;
        MqttManager.getInstance().publish("home/emergency", "STOP");
        showEmergencyBanner(true);
        // FAB parpadea para indicar emergencia activa
        fabEmergency.setAlpha(0.6f);
    }

    private void resetEmergency() {
        emergencyActive = false;
        MqttManager.getInstance().publish("home/emergency", "RESET");
        showEmergencyBanner(false);
        fabEmergency.setAlpha(1.0f);
    }

    private void showEmergencyBanner(boolean show) {
        bannerEmergency.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override public void onConnected() {
        mainHandler.post(() -> {
            tvStatus.setText("Online");
            tvStatus.setTextColor(getResources().getColor(R.color.green_primary, null));
        });
    }

    @Override public void onDisconnected() {
        mainHandler.post(() -> {
            tvStatus.setText("Offline");
            tvStatus.setTextColor(getResources().getColor(R.color.statusError, null));
        });
    }

    @Override public void onMessageReceived(String topic, String payload) {
        mainHandler.post(() -> {
            if (topic.equals("home/emergency/state")) {
                if (payload.equals("ACTIVE")) { emergencyActive = true;  showEmergencyBanner(true); }
                else if (payload.equals("CLEAR")) { emergencyActive = false; showEmergencyBanner(false); }
            }
        });
    }

    @Override public void onError(String error) {
        mainHandler.post(() -> {
            tvStatus.setText("Error");
            tvStatus.setTextColor(getResources().getColor(R.color.statusError, null));
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment).commit();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        MqttManager.getInstance().removeCallback(this);
    }
}
