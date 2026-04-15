package com.agrocontrol.app.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.agrocontrol.app.R;
import com.agrocontrol.app.api.ApiManager;
import com.agrocontrol.app.model.RiegoManager;
import com.agrocontrol.app.model.RiegoRecord;
import com.agrocontrol.app.mqtt.MqttManager;
import com.agrocontrol.app.mqtt.SensorData;
import com.agrocontrol.app.utils.PrefsManager;

public class PumpFragment extends Fragment
    implements MqttManager.MqttCallback, RiegoManager.OnRiegoSavedListener {

    private static final String TAG = "PumpFragment";

    private LinearLayout pumpHeroBg;
    private TextView tvSub, tvTimer, tvThresholdVal, tvAutoStatus, badgeAuto, tvMaxPumps;

    private TextView tvRiegoCount, tvTiempoTotal, tvUltimoRiego, tvSchedBadge;
    private TextView tvDiasRiegos, tvDiasMinutos, tvDiasPromedio;
    private TextView tvSched1Time, tvSched1Duration;
    private TextView tvSched2Time, tvSched2Duration;
    private TextView tvSched3Time, tvSched3Duration;
    private androidx.cardview.widget.CardView cardResumen, cardResumenDias;
    private SwitchMaterial swPump, swSched1, swSched2, swSched3;
    private SeekBar seekThreshold;

    private Handler mainHandler;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private boolean pumpOn        = false;
    private boolean ignoreSwitch  = false;
    private boolean isManualPump  = false;
    private int     seconds       = 0;
    private int     threshold     = 40;
    private int     maxPumps      = 6;  // Límite diario configurable
    private float   currentSoil   = -1;
    private int     lastPumpsToday = -1;

    private PrefsManager prefs;

    private int sched1H, sched1M, sched1Duration;
    private int sched2H, sched2M, sched2Duration;
    private int sched3H, sched3M, sched3Duration;

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

        pumpHeroBg      = v.findViewById(R.id.pump_hero_bg);
        tvSub           = v.findViewById(R.id.tv_pump_hero_sub);
        tvTimer         = v.findViewById(R.id.tv_pump_timer);
        swPump          = v.findViewById(R.id.sw_pump_hero);
        seekThreshold   = v.findViewById(R.id.seekbar_threshold);
        tvThresholdVal  = v.findViewById(R.id.tv_threshold_val);
        tvAutoStatus    = v.findViewById(R.id.tv_auto_status);
        badgeAuto       = v.findViewById(R.id.badge_auto);
        tvMaxPumps      = v.findViewById(R.id.tv_max_pumps);

        // Click en límite diario → NumberPicker
        if (tvMaxPumps != null) tvMaxPumps.setOnClickListener(vv -> showMaxPumpsPicker());
        swSched1        = v.findViewById(R.id.sw_sched1);
        swSched2        = v.findViewById(R.id.sw_sched2);
        swSched3        = v.findViewById(R.id.sw_sched3);
        tvRiegoCount    = v.findViewById(R.id.tv_riego_count);
        tvTiempoTotal   = v.findViewById(R.id.tv_tiempo_total);
        tvUltimoRiego   = v.findViewById(R.id.tv_ultimo_riego);
        tvSchedBadge    = v.findViewById(R.id.tv_sched_badge);
        cardResumen     = v.findViewById(R.id.card_resumen);
        cardResumenDias = v.findViewById(R.id.card_resumen_dias);
        tvDiasRiegos    = v.findViewById(R.id.tv_dias_riegos);
        tvDiasMinutos   = v.findViewById(R.id.tv_dias_minutos);
        tvDiasPromedio  = v.findViewById(R.id.tv_dias_promedio);
        tvSched1Time     = v.findViewById(R.id.tv_sched1_time);
        tvSched1Duration = v.findViewById(R.id.tv_sched1_duration);
        tvSched2Time     = v.findViewById(R.id.tv_sched2_time);
        tvSched2Duration = v.findViewById(R.id.tv_sched2_duration);
        tvSched3Time     = v.findViewById(R.id.tv_sched3_time);
        tvSched3Duration = v.findViewById(R.id.tv_sched3_duration);

        sched1H = prefs.getSchedHour(1, 6);  sched1M = prefs.getSchedMin(1, 30); sched1Duration = prefs.getSchedDuration(1, 15);
        sched2H = prefs.getSchedHour(2, 18); sched2M = prefs.getSchedMin(2, 0);  sched2Duration = prefs.getSchedDuration(2, 10);
        sched3H = prefs.getSchedHour(3, 12); sched3M = prefs.getSchedMin(3, 0);  sched3Duration = prefs.getSchedDuration(3, 20);
        updateSchedViews();

        if (tvSched1Time != null) tvSched1Time.setOnClickListener(vv -> showTimePicker(1));
        if (tvSched2Time != null) tvSched2Time.setOnClickListener(vv -> showTimePicker(2));
        if (tvSched3Time != null) tvSched3Time.setOnClickListener(vv -> showTimePicker(3));
        if (tvSched1Duration != null) tvSched1Duration.setOnClickListener(vv -> showDurationPicker(1));
        if (tvSched2Duration != null) tvSched2Duration.setOnClickListener(vv -> showDurationPicker(2));
        if (tvSched3Duration != null) tvSched3Duration.setOnClickListener(vv -> showDurationPicker(3));

        swSched1.setChecked(prefs.isSchedEnabled(1));
        swSched2.setChecked(prefs.isSchedEnabled(2));
        swSched3.setChecked(prefs.isSchedEnabled(3));
        updateSchedBadge();

        threshold = prefs.getThreshold();
        seekThreshold.setProgress(threshold);
        tvThresholdVal.setText(threshold + "%");

        maxPumps = prefs.getMaxPumpsPerDay();
        if (tvMaxPumps != null) tvMaxPumps.setText(maxPumps + " riegos/día");

        syncPumpState(MqttManager.getInstance().isPumpOn());
        updateEspState(MqttManager.getInstance().isEspOnline());

        float lastSoil = MqttManager.getInstance().getLastSoilHumidity();
        if (lastSoil >= 0) currentSoil = lastSoil;
        updateAutoStatus();
        reloadFromApi();

        if (cardResumen != null)     cardResumen.setOnClickListener(vv -> navigateToRiegos(1));
        if (cardResumenDias != null) cardResumenDias.setOnClickListener(vv -> navigateToRiegos(7));

        swPump.setOnCheckedChangeListener((btn, isOn) -> {
            if (ignoreSwitch) return;

            // Verificar MQTT conectado
            if (!MqttManager.getInstance().isConnected()) {
                ignoreSwitch = true; swPump.setChecked(false); ignoreSwitch = false;
                Toast.makeText(requireContext(), "⚠ Sin conexión MQTT", Toast.LENGTH_LONG).show();
                return;
            }

            // Verificar ESP online según watchdog
            if (isOn && !MqttManager.getInstance().isEspOnline()) {
                ignoreSwitch = true; swPump.setChecked(false); ignoreSwitch = false;
                Toast.makeText(requireContext(),
                    "⚠ ESP desconectado — no se puede encender la bomba",
                    Toast.LENGTH_LONG).show();
                return;
            }

            isManualPump = true; pumpOn = isOn;
            boolean sent = MqttManager.getInstance().setPump(isOn);

            if (!sent) {
                // setPump bloqueó (ESP offline según watchdog)
                ignoreSwitch = true; swPump.setChecked(false); ignoreSwitch = false;
                isManualPump = false; pumpOn = false;
                Toast.makeText(requireContext(),
                    "⚠ ESP no disponible — comando no enviado",
                    Toast.LENGTH_LONG).show();
                return;
            }

            updatePumpUI(isOn);
            if (isOn) { seconds = 0; startTimer(); }
            else { saveManualRiego(seconds); stopTimer(); isManualPump = false; }
        });

        swSched1.setOnCheckedChangeListener((btn, isOn) -> { prefs.setSchedEnabled(1, isOn); sendSchedule(); updateSchedBadge(); });
        swSched2.setOnCheckedChangeListener((btn, isOn) -> { prefs.setSchedEnabled(2, isOn); sendSchedule(); updateSchedBadge(); });
        swSched3.setOnCheckedChangeListener((btn, isOn) -> { prefs.setSchedEnabled(3, isOn); sendSchedule(); updateSchedBadge(); });

        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                threshold = Math.max(p, 10); tvThresholdVal.setText(threshold + "%"); updateAutoStatus();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                prefs.setThreshold(threshold);
                MqttManager.getInstance().setThreshold(threshold);
                Toast.makeText(requireContext(), "Umbral: " + threshold + "%", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateEspState(boolean online) {
        if (swPump == null) return;
        if (online) {
            swPump.setAlpha(1.0f);
            tvSub.setText(pumpOn ? "Encendida · Activa · 110V" : "Apagada · 110V / 50 PSI");
        } else {
            swPump.setAlpha(0.4f);
            if (!pumpOn) tvSub.setText("⚠ ESP desconectado — bomba inactiva");
        }
    }

    private void syncPumpState(boolean isOn) {
        ignoreSwitch = true; swPump.setChecked(isOn); ignoreSwitch = false;
        boolean wasOn = pumpOn; pumpOn = isOn; updatePumpUI(isOn);
        if (isOn) {
            int elapsed = MqttManager.getInstance().getPumpElapsedSeconds();
            seconds = (elapsed > 0 && elapsed < 1800) ? elapsed : 0;
            if (timerHandler == null) startTimer();
        } else {
            stopTimer();
        }
    }

    private void startTimer() {
        stopTimer();
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isAdded() || !pumpOn) return;
                seconds++;
                tvTimer.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
                timerHandler.postDelayed(this, 1000);
            }
        };
        tvTimer.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
            timerHandler = null; timerRunnable = null;
        }
        seconds = 0;
        if (tvTimer != null) tvTimer.setText("00:00");
    }

    private void showTimePicker(int num) {
        int h = num==1?sched1H:num==2?sched2H:sched3H;
        int m = num==1?sched1M:num==2?sched2M:sched3M;
        new TimePickerDialog(requireContext(), (view, hour, minute) -> {
            if (num==1)      { sched1H=hour; sched1M=minute; prefs.setSchedHour(1,hour); prefs.setSchedMin(1,minute); }
            else if (num==2) { sched2H=hour; sched2M=minute; prefs.setSchedHour(2,hour); prefs.setSchedMin(2,minute); }
            else             { sched3H=hour; sched3M=minute; prefs.setSchedHour(3,hour); prefs.setSchedMin(3,minute); }
            updateSchedViews(); sendSchedule();
            Toast.makeText(requireContext(), "Horario " + num + " actualizado", Toast.LENGTH_SHORT).show();
        }, h, m, true).show();
    }

    private void showDurationPicker(int num) {
        int current = num==1?sched1Duration:num==2?sched2Duration:sched3Duration;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Duración riego " + num + " (minutos)");
        NumberPicker np = new NumberPicker(requireContext());
        np.setMinValue(1); np.setMaxValue(60); np.setValue(current); np.setWrapSelectorWheel(false);
        builder.setView(np);
        builder.setPositiveButton("Guardar", (dialog, which) -> {
            int val = np.getValue();
            if (num==1)      { sched1Duration=val; prefs.setSchedDuration(1,val); }
            else if (num==2) { sched2Duration=val; prefs.setSchedDuration(2,val); }
            else             { sched3Duration=val; prefs.setSchedDuration(3,val); }
            updateSchedViews(); sendSchedule();
            Toast.makeText(requireContext(), "Duración: " + val + " min", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancelar", null).show();
    }

    private void updateSchedViews() {
        if (tvSched1Time!=null) tvSched1Time.setText(String.format("%02d:%02d",sched1H,sched1M));
        if (tvSched1Duration!=null) tvSched1Duration.setText(sched1Duration+" min");
        if (tvSched2Time!=null) tvSched2Time.setText(String.format("%02d:%02d",sched2H,sched2M));
        if (tvSched2Duration!=null) tvSched2Duration.setText(sched2Duration+" min");
        if (tvSched3Time!=null) tvSched3Time.setText(String.format("%02d:%02d",sched3H,sched3M));
        if (tvSched3Duration!=null) tvSched3Duration.setText(sched3Duration+" min");
    }

    private void saveManualRiego(int totalSeg) {
        if (totalSeg < 3) return;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String hora = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
        int min = totalSeg/60, seg = totalSeg%60;
        String duracion = min + ":" + (seg<10?"0":"") + seg;
        RiegoRecord r = new RiegoRecord(hora, duracion, "MANUAL", currentSoil < 0 ? 0 : currentSoil);
        RiegoManager.getInstance().addRiego(r);
    }

    private void reloadFromApi() {
        if (tvRiegoCount == null || !isAdded()) return;
        ApiManager.getInstance().getRiegos(1,
            (riegos, totalMin) -> {
                if (!isAdded()) return;
                RiegoManager rm = RiegoManager.getInstance();
                rm.clear();
                for (RiegoRecord r : riegos) rm.getRiegos().add(r);
                refreshSummary(); loadResumenDias();
            },
            err -> { if (!isAdded()) return; refreshSummary(); loadResumenDias(); }
        );
    }

    private void navigateToRiegos(int days) {
        if (getActivity()==null) return;
        HistoryFragment h = new HistoryFragment();
        Bundle args = new Bundle(); args.putBoolean("show_riegos",true); args.putInt("days",days);
        h.setArguments(args);
        getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,h).commit();
        BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
        if (nav!=null) nav.getMenu().findItem(R.id.nav_history).setChecked(true);
    }

    private void loadResumenDias() {
        ApiManager.getInstance().getRiegos(7,
            (riegos, totalMin) -> {
                if (!isAdded()||tvDiasRiegos==null) return;
                tvDiasRiegos.setText(String.valueOf(riegos.size()));
                tvDiasMinutos.setText(totalMin+"m");
                tvDiasPromedio.setText(Math.round((float)riegos.size()/7f)+"/día");
            },
            err -> { if (!isAdded()||tvDiasRiegos==null) return;
                tvDiasRiegos.setText("--"); tvDiasMinutos.setText("--"); tvDiasPromedio.setText("--"); }
        );
    }

    @Override public void onRiegoSaved(RiegoRecord r) {
        mainHandler.post(() -> { if (!isAdded()) return; refreshSummary(); loadResumenDias(); });
    }

    private void refreshSummary() {
        if (tvRiegoCount==null||!isAdded()) return;
        RiegoManager rm = RiegoManager.getInstance();
        tvRiegoCount.setText(String.valueOf(rm.getCount()));
        tvTiempoTotal.setText(rm.getTotalMinutes()+"m");
        tvUltimoRiego.setText(rm.getLastHora());
    }

    private void updatePumpUI(boolean isOn) {
        if (pumpHeroBg==null) return;
        pumpHeroBg.setBackgroundResource(isOn ? R.drawable.bg_hero_on : R.drawable.bg_hero_off);
        tvSub.setText(isOn ? "Encendida · Activa · 110V" : "Apagada · 110V / 50 PSI");
    }

    @Override public void onConnected() {
        mainHandler.post(() -> { if (isAdded()) swPump.setAlpha(1.0f); });
    }
    @Override public void onDisconnected() {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            pumpOn=false; stopTimer();
            ignoreSwitch=true; swPump.setChecked(false); ignoreSwitch=false;
            updatePumpUI(false); swPump.setAlpha(0.4f);
        });
    }
    @Override public void onError(String e) {}

    @Override
    public void onMessageReceived(String topic, String payload) {
        mainHandler.post(() -> {
            if (!isAdded()) return;

            if (topic.equals(MqttManager.TOPIC_STATUS)) {
                boolean online = payload.equals("online");
                MqttManager.getInstance().setEspOnline(online);
                updateEspState(online);
                if (!online) {
                    // ESP offline — apagar bomba en la UI
                    if (pumpOn) {
                        pumpOn=false; stopTimer();
                        ignoreSwitch=true; swPump.setChecked(false); ignoreSwitch=false;
                        updatePumpUI(false);
                        Toast.makeText(requireContext(), "📡 ESP desconectado", Toast.LENGTH_SHORT).show();
                    }
                } else reloadFromApi();
                return;
            }

            if (topic.equals(MqttManager.TOPIC_SENSORS)) {
                SensorData data = SensorData.fromJson(payload);
                if (data.isValid()) {
                    currentSoil=data.soil; updateAutoStatus();
                    if (lastPumpsToday>0 && data.pumpsToday<lastPumpsToday) {
                        lastPumpsToday=data.pumpsToday; reloadFromApi(); return;
                    }
                    lastPumpsToday=data.pumpsToday;
                    if (!isManualPump) {
                        boolean espOn = data.pump==1;
                        if (espOn!=pumpOn) syncPumpState(espOn);
                    }
                }
                return;
            }

            if (topic.equals(MqttManager.TOPIC_PUMP_STATE)) {
                boolean isOn = payload.equals("ON")||payload.equals("ON_AUTO")||payload.equals("ON_SCHED");
                if (!isManualPump && isOn!=pumpOn) {
                    syncPumpState(isOn);
                    if (isOn && payload.equals("ON_AUTO"))
                        Toast.makeText(requireContext(), "💧 Auto-riego activado", Toast.LENGTH_LONG).show();
                    else if (!isOn && payload.contains("OFF"))
                        Toast.makeText(requireContext(), "✓ Riego completado", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (topic.equals(MqttManager.TOPIC_RIEGO_LOG)) {
                mainHandler.postDelayed(() -> reloadFromApi(), 1000);
            }
        });
    }

    private void updateSchedBadge() {
        int a=0;
        if (swSched1!=null&&swSched1.isChecked()) a++;
        if (swSched2!=null&&swSched2.isChecked()) a++;
        if (swSched3!=null&&swSched3.isChecked()) a++;
        tvSchedBadge.setText(a+(a==1?" activo":" activos"));
        if (a==0) { tvSchedBadge.setTextColor(getResources().getColor(R.color.colorTextSecondary,null)); tvSchedBadge.setBackgroundResource(R.drawable.bg_schedule_time); }
        else      { tvSchedBadge.setTextColor(getResources().getColor(R.color.chipInfoText,null));       tvSchedBadge.setBackgroundResource(R.drawable.bg_chip_info); }
    }

    private void showMaxPumpsPicker() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Límite diario de riegos automáticos");
        android.widget.NumberPicker np = new android.widget.NumberPicker(requireContext());
        np.setMinValue(1); np.setMaxValue(50); np.setValue(maxPumps);
        np.setWrapSelectorWheel(false);
        builder.setView(np);
        builder.setPositiveButton("Guardar", (dialog, which) -> {
            maxPumps = np.getValue();
            prefs.setMaxPumpsPerDay(maxPumps);
            MqttManager.getInstance().setMaxPumpsPerDay(maxPumps);
            if (tvMaxPumps != null) tvMaxPumps.setText(maxPumps + " riegos/día");
            Toast.makeText(requireContext(),
                "Límite guardado: " + maxPumps + " riegos/día", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void updateAutoStatus() {
        if (currentSoil<0) {
            tvAutoStatus.setText("Esperando datos...");
            tvAutoStatus.setTextColor(getResources().getColor(R.color.colorTextSecondary,null));
            badgeAuto.setText("Sin datos"); badgeAuto.setBackgroundResource(R.drawable.bg_schedule_time);
            return;
        }
        int soil=(int)currentSoil;
        if (soil<threshold) {
            tvAutoStatus.setText("⚠ Humedad "+soil+"% — bomba se activará");
            tvAutoStatus.setTextColor(getResources().getColor(R.color.chipWarnText,null));
            badgeAuto.setText("Activará bomba");
            badgeAuto.setTextColor(getResources().getColor(R.color.chipWarnText,null));
            badgeAuto.setBackgroundResource(R.drawable.bg_chip_warn);
        } else {
            tvAutoStatus.setText("✓ Humedad "+soil+"% — bomba no se activará");
            tvAutoStatus.setTextColor(getResources().getColor(R.color.chipOkText,null));
            badgeAuto.setText("Activo");
            badgeAuto.setTextColor(getResources().getColor(R.color.chipOkText,null));
            badgeAuto.setBackgroundResource(R.drawable.bg_chip_ok);
        }
    }

    private void sendSchedule() {
        try {
            org.json.JSONObject j = new org.json.JSONObject();
            org.json.JSONObject s1=new org.json.JSONObject(); s1.put("enabled",swSched1.isChecked()); s1.put("hour",sched1H); s1.put("min",sched1M); s1.put("duration",sched1Duration); j.put("s1",s1);
            org.json.JSONObject s2=new org.json.JSONObject(); s2.put("enabled",swSched2.isChecked()); s2.put("hour",sched2H); s2.put("min",sched2M); s2.put("duration",sched2Duration); j.put("s2",s2);
            org.json.JSONObject s3=new org.json.JSONObject(); s3.put("enabled",swSched3.isChecked()); s3.put("hour",sched3H); s3.put("min",sched3M); s3.put("duration",sched3Duration); j.put("s3",s3);
            MqttManager.getInstance().publish("home/schedule", j.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override public void onResume() {
        super.onResume();
        MqttManager.getInstance().addCallback(this);
        RiegoManager.getInstance().setListener(this);
        syncPumpState(MqttManager.getInstance().isPumpOn());
        updateEspState(MqttManager.getInstance().isEspOnline());
        float ls = MqttManager.getInstance().getLastSoilHumidity();
        if (ls>=0) { currentSoil=ls; updateAutoStatus(); }
        reloadFromApi();
    }

    @Override public void onPause() {
        super.onPause();
        MqttManager.getInstance().removeCallback(this);
        RiegoManager.getInstance().setListener(null);
        stopTimer();
    }

    @Override public void onDestroyView() { super.onDestroyView(); stopTimer(); }
}
