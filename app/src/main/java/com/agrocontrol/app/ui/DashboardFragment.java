package com.agrocontrol.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.agrocontrol.app.R;
import com.agrocontrol.app.mqtt.MqttManager;
import com.agrocontrol.app.mqtt.SensorData;
import java.util.Random;

public class DashboardFragment extends Fragment implements MqttManager.MqttCallback {

    private TextView tvSoil, tvTemp, tvAir, tvLight;
    private TextView chipSoil, chipTemp;
    private TextView tvPumpSt, tvFanSt, tvHeatSt;
    private TextView tvTank, badgeTank, tvSync, tvMqttSt, tvEspSt;
    private ProgressBar progressTank;
    private SwitchMaterial swPump, swFan, swHeat;

    private Handler mainHandler;
    private boolean mqttConnected = false;

    // Datos actuales
    private float soil=68f, temp=34f, air=72f, light=12.1f;
    private int tank=73, syncN=0;

    // Simulación fallback si MQTT no está conectado
    private final Random rand = new Random();
    private Handler simHandler;
    private Runnable simRunnable;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());

        bindViews(v);
        setupSwitches();
        updateUI();
        connectMqtt();
    }

    private void bindViews(View v) {
        tvSoil       = v.findViewById(R.id.tv_soil_humidity);
        tvTemp       = v.findViewById(R.id.tv_temperature);
        tvAir        = v.findViewById(R.id.tv_air_humidity);
        tvLight      = v.findViewById(R.id.tv_light);
        chipSoil     = v.findViewById(R.id.chip_soil);
        chipTemp     = v.findViewById(R.id.chip_temp);
        tvPumpSt     = v.findViewById(R.id.tv_pump_status);
        tvFanSt      = v.findViewById(R.id.tv_fan_status);
        tvHeatSt     = v.findViewById(R.id.tv_heater_status);
        tvTank       = v.findViewById(R.id.tv_tank_value);
        badgeTank    = v.findViewById(R.id.badge_tank);
        progressTank = v.findViewById(R.id.progress_tank);
        tvSync       = v.findViewById(R.id.tv_sync);
        tvMqttSt     = v.findViewById(R.id.tv_mqtt_st);
        tvEspSt      = v.findViewById(R.id.tv_esp_st);
        swPump       = v.findViewById(R.id.sw_pump);
        swFan        = v.findViewById(R.id.sw_fan);
        swHeat       = v.findViewById(R.id.sw_heater);
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

    // ─── Conectar MQTT ────────────────────────────────────────────────────────
    private void connectMqtt() {
        MqttManager.getInstance().setCallback(this);
        MqttManager.getInstance().connect(requireContext());
        // Mientras conecta, mostrar simulación
        startSimulation();
    }

    // ─── Callbacks MQTT ───────────────────────────────────────────────────────
    @Override
    public void onConnected() {
        mqttConnected = true;
        mainHandler.post(() -> {
            if (!isAdded()) return;
            tvMqttSt.setText("Online");
            tvMqttSt.setTextColor(getResources().getColor(R.color.green_primary, null));
            stopSimulation(); // Detener simulación cuando hay datos reales
        });
    }

    @Override
    public void onDisconnected() {
        mqttConnected = false;
        mainHandler.post(() -> {
            if (!isAdded()) return;
            tvMqttSt.setText("Offline");
            tvMqttSt.setTextColor(getResources().getColor(R.color.statusError, null));
            startSimulation(); // Reactivar simulación si se pierde conexión
        });
    }

    @Override
    public void onMessageReceived(String topic, String payload) {
        mainHandler.post(() -> {
            if (!isAdded()) return;

            if (topic.equals(MqttManager.TOPIC_SENSORS)) {
                SensorData data = SensorData.fromJson(payload);
                if (data.isValid()) {
                    soil  = data.soil;
                    temp  = data.temp;
                    air   = data.air;
                    light = data.light;
                    tank  = data.tank;
                    syncN++;
                    tvSync.setText(syncN + "");
                    updateUI();
                }
            } else if (topic.equals(MqttManager.TOPIC_STATUS)) {
                tvEspSt.setText(payload.equals("online") ? "Activo" : "Offline");
            }
        });
    }

    @Override
    public void onError(String error) {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            tvMqttSt.setText("Error");
            tvMqttSt.setTextColor(getResources().getColor(R.color.statusError, null));
        });
    }

    // ─── Actualizar UI ────────────────────────────────────────────────────────
    private void updateUI() {
        tvSoil.setText(String.valueOf((int) soil));
        tvTemp.setText(String.valueOf((int) temp));
        tvAir.setText(String.valueOf((int) air));
        tvLight.setText(String.format("%.1f", light));
        progressTank.setProgress(tank);
        tvTank.setText((tank * 5) + " L de 500 L");
        badgeTank.setText(tank + "% — " + (tank >= 60 ? "Suficiente" : tank >= 30 ? "Moderado" : "¡Bajo!"));

        // Chip humedad suelo
        if (soil >= 60) {
            chipSoil.setText("↑ Óptimo");
            chipSoil.setTextColor(getResources().getColor(R.color.chipOkText, null));
            chipSoil.setBackgroundResource(R.drawable.bg_chip_ok);
        } else if (soil >= 40) {
            chipSoil.setText("↔ Normal");
            chipSoil.setTextColor(getResources().getColor(R.color.chipWarnText, null));
            chipSoil.setBackgroundResource(R.drawable.bg_chip_warn);
        } else {
            chipSoil.setText("↓ Seco");
            chipSoil.setTextColor(getResources().getColor(R.color.chipBadText, null));
            chipSoil.setBackgroundResource(R.drawable.bg_chip_bad);
        }

        // Chip temperatura
        if (temp > 32) {
            chipTemp.setText("↑ Alta");
            chipTemp.setTextColor(getResources().getColor(R.color.chipWarnText, null));
            chipTemp.setBackgroundResource(R.drawable.bg_chip_warn);
        } else if (temp < 15) {
            chipTemp.setText("↓ Baja");
            chipTemp.setTextColor(getResources().getColor(R.color.chipInfoText, null));
            chipTemp.setBackgroundResource(R.drawable.bg_chip_info);
        } else {
            chipTemp.setText("✓ Normal");
            chipTemp.setTextColor(getResources().getColor(R.color.chipOkText, null));
            chipTemp.setBackgroundResource(R.drawable.bg_chip_ok);
        }
    }

    // ─── Simulación fallback ──────────────────────────────────────────────────
    private void startSimulation() {
        if (simHandler != null) return; // ya corriendo
        simHandler = new Handler(Looper.getMainLooper());
        simRunnable = new Runnable() {
            @Override public void run() {
                if (!isAdded() || mqttConnected) return;
                soil  = clamp(soil  + (rand.nextFloat()-.5f)*2,   0,100);
                temp  = clamp(temp  + (rand.nextFloat()-.5f)*.5f, 0,50);
                air   = clamp(air   + (rand.nextFloat()-.5f)*1,   0,100);
                light = clamp(light + (rand.nextFloat()-.5f)*.3f, 0,100);
                syncN++;
                tvSync.setText(syncN + "s");
                updateUI();
                simHandler.postDelayed(this, 3000);
            }
        };
        simHandler.postDelayed(simRunnable, 3000);
    }

    private void stopSimulation() {
        if (simHandler != null) {
            simHandler.removeCallbacks(simRunnable);
            simHandler = null;
        }
    }

    private float clamp(float v, float mn, float mx) {
        return Math.max(mn, Math.min(mx, v));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopSimulation();
        MqttManager.getInstance().setCallback(null);
    }
}
