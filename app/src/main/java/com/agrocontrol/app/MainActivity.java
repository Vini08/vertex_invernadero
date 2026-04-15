package com.agrocontrol.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.agrocontrol.app.api.ApiManager;
import com.agrocontrol.app.model.RiegoManager;
import com.agrocontrol.app.model.RiegoRecord;
import com.agrocontrol.app.mqtt.MqttManager;
import com.agrocontrol.app.service.MqttForegroundService;
import com.agrocontrol.app.ui.*;

public class MainActivity extends AppCompatActivity implements MqttManager.MqttCallback {

    private static final String TAG       = "MainActivity";
    private static final int    REQ_NOTIF = 100;

    private BottomNavigationView bottomNav;
    private ImageButton btnTheme, btnConfig;
    private FloatingActionButton fabEmergency;
    private LinearLayout bannerEmergency;
    private Button btnResetEmergency;
    private TextView tvStatus;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean emergencyActive   = false;
    private boolean backPressedOnce   = false;
    private final Runnable resetBackFlag = () -> backPressedOnce = false;

    private long lastRiegoLogSaved = 0;
    private static final long RIEGO_LOG_MIN_INTERVAL = 5000;

    private static final String PREFS    = "AgroPrefs";
    private static final String KEY_DARK = "dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(
            prefs.getBoolean(KEY_DARK, true) ?
            AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestNotificationPermission();
        startMqttService();
        MqttManager.getInstance().addCallback(this);

        bottomNav         = findViewById(R.id.bottom_nav);
        btnTheme          = findViewById(R.id.btn_theme);
        btnConfig         = findViewById(R.id.btn_config);
        fabEmergency      = findViewById(R.id.fab_emergency);
        bannerEmergency   = findViewById(R.id.banner_emergency);
        btnResetEmergency = findViewById(R.id.btn_reset_emergency);
        tvStatus          = findViewById(R.id.tv_status);

        TextView tvGreeting = findViewById(R.id.tv_greeting);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12)      tvGreeting.setText("Buenos días");
        else if (hour < 18) tvGreeting.setText("Buenas tardes");
        else                tvGreeting.setText("Buenas noches");

        btnTheme.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            p.edit().putBoolean(KEY_DARK, !p.getBoolean(KEY_DARK, true)).commit();
            recreate();
        });

        // Botón Config → abre ConfigFragment
        btnConfig.setOnClickListener(v -> loadFragment(new ConfigFragment()));

        fabEmergency.setOnClickListener(v -> showEmergencyDialog());
        btnResetEmergency.setOnClickListener(v ->
            new AlertDialog.Builder(this).setTitle("Resetear emergencia")
                .setMessage("¿Confirmar?")
                .setPositiveButton("RESETEAR", (d, w) -> resetEmergency())
                .setNegativeButton("Cancelar", null).show()
        );

        if (savedInstanceState == null) loadFragment(new DashboardFragment());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f = null;
            if      (id == R.id.nav_dashboard) f = new DashboardFragment();
            else if (id == R.id.nav_pump)      f = new PumpFragment();
            else if (id == R.id.nav_history)   f = new HistoryFragment();
            else if (id == R.id.nav_alerts)    f = new AlertsFragment();
            else if (id == R.id.nav_chat)      f = new ChatFragment();
            if (f != null) { loadFragment(f); return true; }
            return false;
        });

        loadTodayRiegos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MqttManager.getInstance().addCallback(this);
        if (!MqttManager.getInstance().isConnected()) {
            tvStatus.setText("Conectando...");
            MqttManager.getInstance().connect(this);
        } else {
            tvStatus.setText("Online");
            tvStatus.setTextColor(getResources().getColor(R.color.green_primary, null));
        }
    }

    @Override protected void onPause() {
        super.onPause();
        MqttManager.getInstance().removeCallback(this);
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed(); return;
        }
        if (backPressedOnce) { mainHandler.removeCallbacks(resetBackFlag); finishAffinity(); return; }
        backPressedOnce = true;
        Toast.makeText(this, "Presiona atrás de nuevo para salir", Toast.LENGTH_SHORT).show();
        mainHandler.postDelayed(resetBackFlag, 2000);
    }

    private void startMqttService() {
        Intent service = new Intent(this, MqttForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        }
    }

    @Override public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                                      @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
    }

    private void loadTodayRiegos() {
        ApiManager.getInstance().getRiegos(1,
            (riegos, totalMin) -> {
                if (riegos == null) return;
                RiegoManager rm = RiegoManager.getInstance();
                rm.clear();
                // BD devuelve DESC (más reciente primero), invertir para orden cronológico
                for (int i = riegos.size() - 1; i >= 0; i--) rm.getRiegos().add(riegos.get(i));
                Log.d(TAG, "✓ " + riegos.size() + " riegos hoy cargados");
            },
            err -> Log.w(TAG, "No se pudieron cargar riegos: " + err)
        );
    }

    public void selectNavItem(int itemId) { bottomNav.setSelectedItemId(itemId); }
    public void loadFragment(Fragment f) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, f).commit();
    }

    private void showEmergencyDialog() {
        if (emergencyActive) {
            new AlertDialog.Builder(this).setTitle("⛔ Emergencia activa").setMessage("¿Resetear?")
                .setPositiveButton("RESETEAR", (d, w) -> resetEmergency())
                .setNegativeButton("Cancelar", null).show();
        } else {
            new AlertDialog.Builder(this).setTitle("⛔ Apagado de emergencia")
                .setMessage("Apagará bomba, ventilador y calentador.\n\n¿Confirmar?")
                .setPositiveButton("SÍ, APAGAR TODO", (d, w) -> activateEmergency())
                .setNegativeButton("Cancelar", null).show();
        }
    }

    private void activateEmergency() {
        emergencyActive = true;
        MqttManager.getInstance().publish("home/emergency", "STOP");
        bannerEmergency.setVisibility(View.VISIBLE);
        fabEmergency.setAlpha(0.6f);
    }

    private void resetEmergency() {
        emergencyActive = false;
        MqttManager.getInstance().publish("home/emergency", "RESET");
        bannerEmergency.setVisibility(View.GONE);
        fabEmergency.setAlpha(1.0f);
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

    @Override
    public void onMessageReceived(String topic, String payload) {
        mainHandler.post(() -> {
            if (topic.equals("home/emergency/state")) {
                if (payload.equals("ACTIVE")) {
                    emergencyActive = true; bannerEmergency.setVisibility(View.VISIBLE);
                } else if (payload.equals("CLEAR")) {
                    emergencyActive = false; bannerEmergency.setVisibility(View.GONE);
                }
            }
            if (topic.equals(MqttManager.TOPIC_RIEGO_LOG)) {
                long now = System.currentTimeMillis();
                if (now - lastRiegoLogSaved < RIEGO_LOG_MIN_INTERVAL) {
                    Log.w(TAG, "Riego/log duplicado ignorado"); return;
                }
                lastRiegoLogSaved = now;
                try {
                    org.json.JSONObject j = new org.json.JSONObject(payload);
                    String hora     = j.optString("hora", "--:--");
                    String duracion = j.optString("duracion", "0:00");
                    String tipo     = j.optString("tipo", "AUTO");
                    float  humedad  = (float) j.optDouble("humedad", 0);
                    RiegoRecord record = new RiegoRecord(hora, duracion, tipo, humedad);
                    RiegoManager.getInstance().addRiego(record);
                    ApiManager.getInstance().saveRiego(record,
                        () -> Log.d(TAG, "✓ Riego BD: " + hora),
                        err -> Log.e(TAG, "✗ BD: " + err)
                    );
                } catch (Exception e) { Log.e(TAG, "Error riego: " + e.getMessage()); }
            }
        });
    }

    @Override public void onError(String error) {
        mainHandler.post(() -> {
            tvStatus.setText("Error");
            tvStatus.setTextColor(getResources().getColor(R.color.statusError, null));
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(resetBackFlag);
        MqttManager.getInstance().removeCallback(this);
    }
}
