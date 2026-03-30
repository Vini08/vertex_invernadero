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

public class DashboardFragment extends Fragment implements MqttManager.MqttCallback {

    private TextView tvSoil, tvTemp, tvAir, tvLight;
    private TextView chipSoil, chipTemp;
    private TextView tvPumpSt, tvFanSt, tvHeatSt;
    private TextView tvTank, badgeTank, tvSync, tvMqttSt, tvEspSt;
    private ProgressBar progressTank;
    private SwitchMaterial swPump, swFan, swHeat;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int syncN = 0;

    private float soil=0, temp=0, air=0, light=0;
    private int tank=0;

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

        // Mostrar último dato guardado inmediatamente
        SensorData last = MqttManager.getInstance().getLastData();
        if (last != null) {
            soil = last.soil; temp = last.temp;
            air  = last.air;  light = last.light; tank = last.tank;
            updateUI();
        }

        // Estado de conexión actual
        if (MqttManager.getInstance().isConnected()) {
            tvMqttSt.setText("Online");
            tvMqttSt.setTextColor(getResources().getColor(R.color.green_primary, null));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Registrar callback SOLO cuando el fragment está visible
        MqttManager.getInstance().addCallback(this);

        // Refrescar con el último dato al volver a esta pantalla
        SensorData last = MqttManager.getInstance().getLastData();
        if (last != null) {
            soil = last.soil; temp = last.temp;
            air  = last.air;  light = last.light; tank = last.tank;
            updateUI();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Quitar callback cuando el fragment NO está visible
        MqttManager.getInstance().removeCallback(this);
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

    @Override
    public void onConnected() {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            tvMqttSt.setText("Online");
            tvMqttSt.setTextColor(getResources().getColor(R.color.green_primary, null));
        });
    }

    @Override
    public void onDisconnected() {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            tvMqttSt.setText("Offline");
            tvMqttSt.setTextColor(getResources().getColor(R.color.statusError, null));
        });
    }

    @Override
    public void onMessageReceived(String topic, String payload) {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            if (topic.equals(MqttManager.TOPIC_SENSORS)) {
                SensorData data = SensorData.fromJson(payload);
                if (data.isValid()) {
                    soil = data.soil; temp = data.temp;
                    air  = data.air;  light = data.light; tank = data.tank;
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

    private void updateUI() {
        tvSoil.setText(String.valueOf((int) soil));
        tvTemp.setText(String.valueOf((int) temp));
        tvAir.setText(String.valueOf((int) air));
        tvLight.setText(String.format("%.1f", light));
        progressTank.setProgress(tank);
        tvTank.setText((tank * 5) + " L de 500 L");
        badgeTank.setText(tank + "% — " + (tank >= 60 ? "Suficiente" : tank >= 30 ? "Moderado" : "¡Bajo!"));

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
}
