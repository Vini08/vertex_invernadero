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

public class PumpFragment extends Fragment {

    private LinearLayout pumpHeroBg;
    private TextView tvSub, tvTimer, tvThresholdVal, tvAutoStatus;
    private SwitchMaterial swPump;
    private SeekBar seekThreshold;

    private boolean pumpOn = false;
    private int seconds = 0;
    private Handler timerHandler;
    private Runnable timerRunnable;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pump, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        pumpHeroBg     = v.findViewById(R.id.pump_hero_bg);
        tvSub          = v.findViewById(R.id.tv_pump_hero_sub);
        tvTimer        = v.findViewById(R.id.tv_pump_timer);
        swPump         = v.findViewById(R.id.sw_pump_hero);
        seekThreshold  = v.findViewById(R.id.seekbar_threshold);
        tvThresholdVal = v.findViewById(R.id.tv_threshold_val);
        tvAutoStatus   = v.findViewById(R.id.tv_auto_status);

        swPump.setOnCheckedChangeListener((btn, isOn) -> {
            pumpOn = isOn;
            // Enviar comando real al ESP via MQTT
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

        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = Math.max(progress, 10);
                tvThresholdVal.setText(val + "%");
                int currentSoil = 68;
                if (currentSoil < val) {
                    tvAutoStatus.setText("⚠ Humedad " + currentSoil + "% — bomba se activaría");
                    tvAutoStatus.setTextColor(getResources().getColor(R.color.chipWarnText, null));
                } else {
                    tvAutoStatus.setText("Humedad actual " + currentSoil + "% — bomba no se activará");
                    tvAutoStatus.setTextColor(getResources().getColor(R.color.chipOkText, null));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
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

    @Override public void onDestroyView() { super.onDestroyView(); stopTimer(); }
}
