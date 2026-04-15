package com.agrocontrol.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.agrocontrol.app.R;
import com.agrocontrol.app.api.ApiManager;
import com.agrocontrol.app.mqtt.MqttManager;
import com.agrocontrol.app.mqtt.SensorData;
import com.agrocontrol.app.widget.AgroWidget;
import com.agrocontrol.app.utils.HumidityTracker;
import com.agrocontrol.app.utils.PrefsManager;

public class DashboardFragment extends Fragment implements MqttManager.MqttCallback {

    private TextView tvSoil, tvTemp, tvAir, tvLight;
    private TextView chipSoil, chipTemp;
    private TextView tvPumpSt, tvFanSt, tvHeatSt;
    private TextView tvTank, badgeTank, tvSync, tvMqttSt, tvEspSt;
    private ProgressBar progressTank;
    private SwitchMaterial swPump, swFan, swHeat;

    // Banner bomba activa
    private CardView cardPumpActive;
    private TextView tvPumpTimerDash, tvPumpTypeDash;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private int pumpSeconds = 0;
    private boolean pumpActive = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler syncHandler  = new Handler(Looper.getMainLooper());
    private Runnable syncRunnable;

    private int syncN = 0;
    private int saveCounter = 0;
    private PrefsManager prefs;

    private float soil=0, temp=0, air=0, light=0;
    private int   tank=0;

    private boolean waitingForPing  = false;
    private Runnable pingTimeoutRunnable;
    private static final long PING_TIMEOUT_MS        = 8000;
    private long lastSensorReceived                  = 0;
    private static final long SENSOR_ONLINE_WINDOW_MS = 30000;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        bindViews(v);
        setupSwitches();
        prefs = new PrefsManager(requireContext());

        SensorData last = MqttManager.getInstance().getLastData();
        if (last != null) {
            soil=last.soil; temp=last.temp; air=last.air; light=last.light; tank=last.tank;
            updateUI();
        }

        updateMqttStatus();
        updateEspStatus(MqttManager.getInstance().isEspOnline());

        // Mostrar banner si ya hay riego activo
        if (MqttManager.getInstance().isPumpOn()) {
            showPumpBanner(true, MqttManager.getInstance().getPumpElapsedSeconds());
        }

        if (tvEspSt != null) tvEspSt.setOnClickListener(view -> onEspBadgeTapped());
    }

    @Override public void onResume() {
        super.onResume();
        MqttManager.getInstance().addCallback(this);
        syncStateNow();
        startPeriodicSync();

        // Restaurar timer si bomba sigue activa
        if (MqttManager.getInstance().isPumpOn()) {
            pumpSeconds = MqttManager.getInstance().getPumpElapsedSeconds();
            showPumpBanner(true, pumpSeconds);
            startPumpTimer();
        }
    }

    @Override public void onPause() {
        super.onPause();
        MqttManager.getInstance().removeCallback(this);
        stopPeriodicSync();
        cancelPingTimeout();
        stopPumpTimer();
    }

    // ─── Banner bomba ─────────────────────────────────────────────────────────
    private void showPumpBanner(boolean show, int elapsedSeconds) {
        if (cardPumpActive == null || !isAdded()) return;
        if (show) {
            cardPumpActive.setVisibility(View.VISIBLE);
            pumpActive = true;
            pumpSeconds = elapsedSeconds;
            updateTimerDisplay();
        } else {
            cardPumpActive.setVisibility(View.GONE);
            pumpActive = false;
            stopPumpTimer();
        }
    }

    private void startPumpTimer() {
        stopPumpTimer();
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isAdded() || !pumpActive) return;
                pumpSeconds++;
                updateTimerDisplay();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void stopPumpTimer() {
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
            timerHandler = null; timerRunnable = null;
        }
    }

    private void updateTimerDisplay() {
        if (tvPumpTimerDash == null) return;
        tvPumpTimerDash.setText(String.format("%02d:%02d", pumpSeconds / 60, pumpSeconds % 60));
    }

    private void setPumpBannerType(String pumpState) {
        if (tvPumpTypeDash == null) return;
        switch (pumpState) {
            case "ON_AUTO":
                tvPumpTypeDash.setText("AUTO");
                tvPumpTypeDash.setBackgroundResource(R.drawable.bg_chip_ok);
                tvPumpTypeDash.setTextColor(getResources().getColor(R.color.chipOkText, null));
                break;
            case "ON_SCHED":
                tvPumpTypeDash.setText("PROG");
                tvPumpTypeDash.setBackgroundResource(R.drawable.bg_chip_warn);
                tvPumpTypeDash.setTextColor(getResources().getColor(R.color.chipWarnText, null));
                break;
            default:
                tvPumpTypeDash.setText("MANUAL");
                tvPumpTypeDash.setBackgroundResource(R.drawable.bg_chip_info);
                tvPumpTypeDash.setTextColor(getResources().getColor(R.color.chipInfoText, null));
                break;
        }
    }

    // ─── Sync periódico ───────────────────────────────────────────────────────
    private void startPeriodicSync() {
        stopPeriodicSync();
        syncRunnable = new Runnable() {
            @Override public void run() {
                if (!isAdded()) return;
                syncStateNow();
                syncHandler.postDelayed(this, 5000);
            }
        };
        syncHandler.postDelayed(syncRunnable, 5000);
    }

    private void stopPeriodicSync() {
        if (syncRunnable != null) {
            syncHandler.removeCallbacks(syncRunnable);
            syncRunnable = null;
        }
    }

    private void syncStateNow() {
        if (!isAdded()) return;
        boolean mqttOk = MqttManager.getInstance().isConnected();
        boolean espOk  = MqttManager.getInstance().isEspOnline();
        long lastSensor = MqttManager.getInstance().getLastSensorTime();

        if (lastSensor > 0 &&
            System.currentTimeMillis() - lastSensor > SENSOR_ONLINE_WINDOW_MS && espOk) {
            MqttManager.getInstance().setEspOnline(false);
            espOk = false;
            showPumpBanner(false, 0);
        }

        updateMqttStatus();
        updateEspStatus(espOk);

        SensorData last = MqttManager.getInstance().getLastData();
        if (last != null && last.isValid()) {
            soil=last.soil; temp=last.temp; air=last.air; light=last.light; tank=last.tank;
            updateUI();
        }
    }

    private void updateMqttStatus() {
        if (tvMqttSt == null || !isAdded()) return;
        boolean connected = MqttManager.getInstance().isConnected();
        tvMqttSt.setText(connected ? "Online" : "Offline");
        tvMqttSt.setTextColor(getResources().getColor(
            connected ? R.color.green_primary : R.color.statusError, null));
    }

    private void onEspBadgeTapped() {
        if (!isAdded()) return;
        if (!MqttManager.getInstance().isConnected()) {
            tvEspSt.setText("Reconectando...");
            tvEspSt.setTextColor(getResources().getColor(R.color.chipWarnText, null));
            MqttManager.getInstance().connect(requireContext());
            return;
        }
        if (waitingForPing) return;
        waitingForPing = true;
        tvEspSt.setText("Verificando...");
        tvEspSt.setTextColor(getResources().getColor(R.color.chipWarnText, null));
        tvEspSt.setClickable(false);
        MqttManager.getInstance().publish("home/ping", "1");
        pingTimeoutRunnable = () -> {
            if (!isAdded() || !waitingForPing) return;
            waitingForPing = false;
            if (tvEspSt != null) {
                tvEspSt.setClickable(true);
                long since = System.currentTimeMillis() - lastSensorReceived;
                boolean online = since < SENSOR_ONLINE_WINDOW_MS;
                updateEspStatus(online);
                MqttManager.getInstance().setEspOnline(online);
            }
        };
        mainHandler.postDelayed(pingTimeoutRunnable, PING_TIMEOUT_MS);
    }

    private void cancelPingTimeout() {
        if (pingTimeoutRunnable != null) {
            mainHandler.removeCallbacks(pingTimeoutRunnable);
            pingTimeoutRunnable = null;
        }
        waitingForPing = false;
    }

    private void updateEspStatus(boolean online) {
        if (tvEspSt == null || !isAdded()) return;
        tvEspSt.setClickable(true);
        if (online) {
            tvEspSt.setText("Activo");
            tvEspSt.setTextColor(getResources().getColor(R.color.green_primary, null));
        } else {
            tvEspSt.setText("Offline — tap");
            tvEspSt.setTextColor(getResources().getColor(R.color.statusError, null));
        }
    }

    private void bindViews(View v) {
        tvSoil          = v.findViewById(R.id.tv_soil_humidity);
        tvTemp          = v.findViewById(R.id.tv_temperature);
        tvAir           = v.findViewById(R.id.tv_air_humidity);
        tvLight         = v.findViewById(R.id.tv_light);
        chipSoil        = v.findViewById(R.id.chip_soil);
        chipTemp        = v.findViewById(R.id.chip_temp);
        tvPumpSt        = v.findViewById(R.id.tv_pump_status);
        tvFanSt         = v.findViewById(R.id.tv_fan_status);
        tvHeatSt        = v.findViewById(R.id.tv_heater_status);
        tvTank          = v.findViewById(R.id.tv_tank_value);
        badgeTank       = v.findViewById(R.id.badge_tank);
        progressTank    = v.findViewById(R.id.progress_tank);
        tvSync          = v.findViewById(R.id.tv_sync);
        tvMqttSt        = v.findViewById(R.id.tv_mqtt_st);
        tvEspSt         = v.findViewById(R.id.tv_esp_st);
        swPump          = v.findViewById(R.id.sw_pump);
        swFan           = v.findViewById(R.id.sw_fan);
        swHeat          = v.findViewById(R.id.sw_heater);
        cardPumpActive  = v.findViewById(R.id.card_pump_active);
        tvPumpTimerDash = v.findViewById(R.id.tv_pump_timer_dash);
        tvPumpTypeDash  = v.findViewById(R.id.tv_pump_type_dash);
    }

    private void setupSwitches() {
        swPump.setOnCheckedChangeListener((b, on) -> {
            tvPumpSt.setText(on ? "Encendida" : "Apagada");
            tvPumpSt.setTextColor(getResources().getColor(
                on ? R.color.green_primary : R.color.colorTextSecondary, null));
            MqttManager.getInstance().setPump(on);
        });
        swFan.setOnCheckedChangeListener((b, on) -> {
            tvFanSt.setText(on ? "Encendido" : "Apagado");
            tvFanSt.setTextColor(getResources().getColor(
                on ? R.color.green_primary : R.color.colorTextSecondary, null));
            MqttManager.getInstance().setFan(on);
        });
        swHeat.setOnCheckedChangeListener((b, on) -> {
            tvHeatSt.setText(on ? "Encendido" : "Apagado");
            tvHeatSt.setTextColor(getResources().getColor(
                on ? R.color.green_primary : R.color.colorTextSecondary, null));
            MqttManager.getInstance().setHeater(on);
        });
    }

    @Override public void onConnected() {
        mainHandler.post(() -> { if (!isAdded()) return; updateMqttStatus(); });
    }

    @Override public void onDisconnected() {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            updateMqttStatus();
            updateEspStatus(false);
            showPumpBanner(false, 0);
            cancelPingTimeout();
        });
    }

    @Override
    public void onMessageReceived(String topic, String payload) {
        mainHandler.post(() -> {
            if (!isAdded()) return;

            if (topic.equals(MqttManager.TOPIC_SENSORS)) {
                SensorData data = SensorData.fromJson(payload);
                if (data.isValid()) {
                    soil=data.soil; temp=data.temp; air=data.air;
                    light=data.light; tank=data.tank;
                    syncN++; tvSync.setText(syncN + "s");
                    lastSensorReceived = System.currentTimeMillis();
                    updateUI();
                    updateEspStatus(true);
                    MqttManager.getInstance().setEspOnline(true);
                    cancelPingTimeout(); waitingForPing = false;
                    HumidityTracker.getInstance().addReading(data.soil);

                    // Actualizar widget
                    if (getContext() != null)
                        AgroWidget.pushUpdate(getContext(),
                            (int)data.soil, (int)data.temp,
                            data.pump == 1, true, data.tank);

                    // Mostrar/ocultar banner bomba según datos del sensor
                    boolean pumpOn = data.pump == 1;
                    if (pumpOn && !pumpActive) {
                        pumpSeconds = MqttManager.getInstance().getPumpElapsedSeconds();
                        showPumpBanner(true, pumpSeconds);
                        startPumpTimer();
                    } else if (!pumpOn && pumpActive) {
                        showPumpBanner(false, 0);
                    }

                    saveCounter++;
                    if (saveCounter >= 60) {
                        saveCounter = 0;
                        ApiManager.getInstance().saveSensorData(
                            data.soil, data.temp, data.air, data.light,
                            data.tank, data.pump,
                            () -> android.util.Log.d("Dashboard", "BD ✓"),
                            err -> android.util.Log.w("Dashboard", "BD error: " + err)
                        );
                    }
                }

            } else if (topic.equals(MqttManager.TOPIC_STATUS)) {
                boolean online = payload.equals("online");
                updateEspStatus(online);
                MqttManager.getInstance().setEspOnline(online);
                if (!online) {
                    lastSensorReceived = 0;
                    cancelPingTimeout();
                    showPumpBanner(false, 0);
                    if (getContext() != null)
                        AgroWidget.pushUpdate(getContext(), (int)soil, (int)temp, false, false, tank);
                }

            } else if (topic.equals(MqttManager.TOPIC_PUMP_STATE)) {
                // Detectar tipo de riego para el badge
                boolean isOn = payload.equals("ON") || payload.equals("ON_AUTO") || payload.equals("ON_SCHED");
                if (isOn && !pumpActive) {
                    pumpSeconds = 0;
                    showPumpBanner(true, 0);
                    startPumpTimer();
                    setPumpBannerType(payload);
                } else if (!isOn && pumpActive) {
                    showPumpBanner(false, 0);
                }
            }
        });
    }

    @Override public void onError(String e) {
        mainHandler.post(() -> { if (!isAdded()) return; updateMqttStatus(); });
    }

    private void updateUI() {
        tvSoil.setText(String.valueOf((int) soil));
        tvTemp.setText(String.valueOf((int) temp));
        tvAir.setText(String.valueOf((int) air));
        tvLight.setText(String.format("%.1f", light));
        progressTank.setProgress(tank);
        tvTank.setText((tank * 5) + " L de 500 L");
        badgeTank.setText(tank + "% — " +
            (tank >= 60 ? "Suficiente" : tank >= 30 ? "Moderado" : "¡Bajo!"));

        if (soil >= 60) { chipSoil.setText("↑ Óptimo"); chipSoil.setTextColor(getResources().getColor(R.color.chipOkText,null)); chipSoil.setBackgroundResource(R.drawable.bg_chip_ok); }
        else if (soil >= 40) { chipSoil.setText("↔ Normal"); chipSoil.setTextColor(getResources().getColor(R.color.chipWarnText,null)); chipSoil.setBackgroundResource(R.drawable.bg_chip_warn); }
        else { chipSoil.setText("↓ Seco"); chipSoil.setTextColor(getResources().getColor(R.color.chipBadText,null)); chipSoil.setBackgroundResource(R.drawable.bg_chip_bad); }

        if (temp > 32) { chipTemp.setText("↑ Alta"); chipTemp.setTextColor(getResources().getColor(R.color.chipWarnText,null)); chipTemp.setBackgroundResource(R.drawable.bg_chip_warn); }
        else if (temp < 15) { chipTemp.setText("↓ Baja"); chipTemp.setTextColor(getResources().getColor(R.color.chipInfoText,null)); chipTemp.setBackgroundResource(R.drawable.bg_chip_info); }
        else { chipTemp.setText("✓ Normal"); chipTemp.setTextColor(getResources().getColor(R.color.chipOkText,null)); chipTemp.setBackgroundResource(R.drawable.bg_chip_ok); }
    }
}
