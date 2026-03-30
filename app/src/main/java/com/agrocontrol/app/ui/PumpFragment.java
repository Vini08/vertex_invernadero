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
import com.agrocontrol.app.utils.PrefsManager;

public class PumpFragment extends Fragment implements MqttManager.MqttCallback {

    private LinearLayout pumpHeroBg;
    private TextView tvSub, tvTimer, tvThresholdVal, tvAutoStatus, badgeAuto;
    private TextView tvRiegoCount, tvTiempoTotal, tvUltimoRiego;
    private SwitchMaterial swPump, swSched1, swSched2, swSched3;
    private SeekBar seekThreshold;

    private Handler mainHandler;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private boolean pumpOn    = false;
    private int     seconds   = 0;
    private int     threshold = 40;
    private float   currentSoil = -1;

    private PrefsManager prefs;

    private static final int SCHED1_H = 6,  SCHED1_M = 30;
    private static final int SCHED2_H = 18, SCHED2_M = 0;
    private static final int SCHED3_H = 12, SCHED3_M = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pump, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());
        prefs = new PrefsManager(requireContext());

        // Bind views
        pumpHeroBg     = v.findViewById(R.id.pump_hero_bg);
        tvSub          = v.findViewById(R.id.tv_pump_hero_sub);
        tvTimer        = v.findViewById(R.id.tv_pump_timer);
        swPump         = v.findViewById(R.id.sw_pump_hero);
        seekThreshold  = v.findViewById(R.id.seekbar_threshold);
        tvThresholdVal = v.findViewById(R.id.tv_threshold_val);
        tvAutoStatus   = v.findViewById(R.id.tv_auto_status);
        badgeAuto      = v.findViewById(R.id.badge_auto);
        swSched1       = v.findViewById(R.id.sw_sched1);
        swSched2       = v.findViewById(R.id.sw_sched2);
        swSched3       = v.findViewById(R.id.sw_sched3);
        tvRiegoCount   = v.findViewById(R.id.tv_riego_count);
        tvTiempoTotal  = v.findViewById(R.id.tv_tiempo_total);
        tvUltimoRiego  = v.findViewById(R.id.tv_ultimo_riego);

        // Cargar estados guardados
        swSched1.setChecked(prefs.isSchedEnabled(1));
        swSched2.setChecked(prefs.isSchedEnabled(2));
        swSched3.setChecked(prefs.isSchedEnabled(3));

        float lastSoil = MqttManager.getInstance().getLastSoilHumidity();
        if (lastSoil >= 0) currentSoil = lastSoil;

        seekThreshold.setProgress(threshold);
        tvThresholdVal.setText(threshold + "%");
        updateAutoStatus();

        // Cargar último dato del resumen si hay datos guardados
        updateSummaryFromLastData();

        // Switch bomba manual
        swPump.setOnCheckedChangeListener((btn, isOn) -> {
            pumpOn = isOn;
            MqttManager.getInstance().setPump(isOn);
            if (isOn) {
                pumpHeroBg.setBackgroundResource(R.drawable.bg_hero_on);
                tvSub.setText("Encendida · Activa · 110V");
                startTimer();
            } else {
                pumpHeroBg.setBackgroundResource(R.drawable.bg_hero_off);
                tvSub.setText("Apagada · 110V / 50 PSI");
                stopTimer();
            }
        });

        // Switches horarios
        swSched1.setOnCheckedChangeListener((btn, isOn) -> {
            prefs.setSchedEnabled(1, isOn);
            sendSchedule();
            Toast.makeText(requireContext(),
                isOn ? "Riego 06:30 AM activado" : "Riego 06:30 AM desactivado",
                Toast.LENGTH_SHORT).show();
        });
        swSched2.setOnCheckedChangeListener((btn, isOn) -> {
            prefs.setSchedEnabled(2, isOn);
            sendSchedule();
            Toast.makeText(requireContext(),
                isOn ? "Riego 06:00 PM activado" : "Riego 06:00 PM desactivado",
                Toast.LENGTH_SHORT).show();
        });
        swSched3.setOnCheckedChangeListener((btn, isOn) -> {
            prefs.setSchedEnabled(3, isOn);
            sendSchedule();
            Toast.makeText(requireContext(),
                isOn ? "Riego 12:00 PM (finde) activado" : "Riego 12:00 PM (finde) desactivado",
                Toast.LENGTH_SHORT).show();
        });

        // Seekbar umbral
        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                threshold = Math.max(progress, 10);
                tvThresholdVal.setText(threshold + "%");
                updateAutoStatus();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                MqttManager.getInstance().setThreshold(threshold);
                Toast.makeText(requireContext(),
                    "Umbral enviado: " + threshold + "%", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Actualizar resumen desde el último dato MQTT guardado
    private void updateSummaryFromLastData() {
        com.agrocontrol.app.mqtt.SensorData last = MqttManager.getInstance().getLastData();
        if (last == null) return;
        updateSummary(last.pumpsToday, last.pumpMinutesToday, last.lastPumpHour, last.lastPumpMin);
    }

    // Actualizar la card de resumen
    private void updateSummary(int count, int minutes, int lastH, int lastM) {
        if (tvRiegoCount == null) return;
        tvRiegoCount.setText(String.valueOf(count));
        tvTiempoTotal.setText(minutes + "m");
        if (lastH < 0) {
            tvUltimoRiego.setText("--:--");
        } else {
            tvUltimoRiego.setText(String.format("%02d:%02d", lastH, lastM));
        }
    }

    // Enviar horarios al ESP
    private void sendSchedule() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            org.json.JSONObject s1 = new org.json.JSONObject();
            s1.put("enabled", swSched1.isChecked());
            s1.put("hour", SCHED1_H); s1.put("min", SCHED1_M); s1.put("duration", 15);
            json.put("s1", s1);
            org.json.JSONObject s2 = new org.json.JSONObject();
            s2.put("enabled", swSched2.isChecked());
            s2.put("hour", SCHED2_H); s2.put("min", SCHED2_M); s2.put("duration", 10);
            json.put("s2", s2);
            org.json.JSONObject s3 = new org.json.JSONObject();
            s3.put("enabled", swSched3.isChecked());
            s3.put("hour", SCHED3_H); s3.put("min", SCHED3_M); s3.put("duration", 20);
            json.put("s3", s3);
            MqttManager.getInstance().publish("home/schedule", json.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateAutoStatus() {
        if (currentSoil < 0) {
            tvAutoStatus.setText("Esperando datos del sensor...");
            tvAutoStatus.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
            badgeAuto.setText("Sin datos");
            badgeAuto.setBackgroundResource(R.drawable.bg_schedule_time);
            return;
        }
        int soil = (int) currentSoil;
        if (soil < threshold) {
            tvAutoStatus.setText("⚠ Humedad actual " + soil + "% — bomba se activaría");
            tvAutoStatus.setTextColor(getResources().getColor(R.color.chipWarnText, null));
            badgeAuto.setText("Activaría bomba");
            badgeAuto.setTextColor(getResources().getColor(R.color.chipWarnText, null));
            badgeAuto.setBackgroundResource(R.drawable.bg_chip_warn);
        } else {
            tvAutoStatus.setText("✓ Humedad actual " + soil + "% — bomba no se activará");
            tvAutoStatus.setTextColor(getResources().getColor(R.color.chipOkText, null));
            badgeAuto.setText("Activo");
            badgeAuto.setTextColor(getResources().getColor(R.color.chipOkText, null));
            badgeAuto.setBackgroundResource(R.drawable.bg_chip_ok);
        }
    }

    @Override public void onResume() {
        super.onResume();
        MqttManager.getInstance().addCallback(this);
        float lastSoil = MqttManager.getInstance().getLastSoilHumidity();
        if (lastSoil >= 0) { currentSoil = lastSoil; updateAutoStatus(); }
        updateSummaryFromLastData();
    }

    @Override public void onPause() {
        super.onPause();
        MqttManager.getInstance().removeCallback(this);
    }

    @Override public void onConnected() {}
    @Override public void onDisconnected() {}
    @Override public void onError(String e) {}

    @Override
    public void onMessageReceived(String topic, String payload) {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            if (topic.equals(MqttManager.TOPIC_SENSORS)) {
                try {
                    com.agrocontrol.app.mqtt.SensorData data =
                        com.agrocontrol.app.mqtt.SensorData.fromJson(payload);
                    if (data.isValid()) {
                        currentSoil = data.soil;
                        updateAutoStatus();
                        // Actualizar resumen con datos reales del ESP
                        updateSummary(data.pumpsToday, data.pumpMinutesToday,
                                      data.lastPumpHour, data.lastPumpMin);
                    }
                } catch (Exception e) { }
            }
        });
    }

    private void startTimer() {
        seconds = 0;
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isAdded() || !pumpOn) return;
                seconds++;
                tvTimer.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerHandler != null) timerHandler.removeCallbacks(timerRunnable);
        tvTimer.setText("00:00");
        seconds = 0;
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }
}
