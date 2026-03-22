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
import java.util.Random;

public class DashboardFragment extends Fragment {
    private TextView tvSoil, tvTemp, tvAir, tvLight, chipSoil, chipTemp;
    private TextView tvPumpSt, tvFanSt, tvHeatSt, tvTank, badgeTank, tvSync;
    private ProgressBar progressTank;
    private SwitchMaterial swPump, swFan, swHeat;
    private Handler handler;
    private Runnable runnable;
    private final Random rand = new Random();
    private float soil=68f, temp=34f, air=72f, light=12.1f;
    private int tank=73, syncN=3;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        return i.inflate(R.layout.fragment_dashboard, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        tvSoil      = v.findViewById(R.id.tv_soil_humidity);
        tvTemp      = v.findViewById(R.id.tv_temperature);
        tvAir       = v.findViewById(R.id.tv_air_humidity);
        tvLight     = v.findViewById(R.id.tv_light);
        chipSoil    = v.findViewById(R.id.chip_soil);
        chipTemp    = v.findViewById(R.id.chip_temp);
        tvPumpSt    = v.findViewById(R.id.tv_pump_status);
        tvFanSt     = v.findViewById(R.id.tv_fan_status);
        tvHeatSt    = v.findViewById(R.id.tv_heater_status);
        tvTank      = v.findViewById(R.id.tv_tank_value);
        badgeTank   = v.findViewById(R.id.badge_tank);
        progressTank= v.findViewById(R.id.progress_tank);
        tvSync      = v.findViewById(R.id.tv_sync);
        swPump      = v.findViewById(R.id.sw_pump);
        swFan       = v.findViewById(R.id.sw_fan);
        swHeat      = v.findViewById(R.id.sw_heater);

        swPump.setOnCheckedChangeListener((b,on)-> { tvPumpSt.setText(on?"Encendida":"Apagada"); tvPumpSt.setTextColor(getResources().getColor(on?R.color.green_primary:R.color.colorTextSecondary,null)); });
        swFan.setOnCheckedChangeListener((b,on)-> { tvFanSt.setText(on?"Encendido":"Apagado"); tvFanSt.setTextColor(getResources().getColor(on?R.color.green_primary:R.color.colorTextSecondary,null)); });
        swHeat.setOnCheckedChangeListener((b,on)-> { tvHeatSt.setText(on?"Encendido":"Apagado"); tvHeatSt.setTextColor(getResources().getColor(on?R.color.green_primary:R.color.colorTextSecondary,null)); });

        updateUI();
        handler = new Handler(Looper.getMainLooper());
        runnable = new Runnable() {
            @Override public void run() {
                if (!isAdded()) return;
                soil  = clamp(soil  + (rand.nextFloat()-.5f)*2,   0,100);
                temp  = clamp(temp  + (rand.nextFloat()-.5f)*.5f, 0,50);
                air   = clamp(air   + (rand.nextFloat()-.5f)*1,   0,100);
                light = clamp(light + (rand.nextFloat()-.5f)*.3f, 0,100);
                updateUI();
                syncN = (syncN%10)+1;
                tvSync.setText(syncN+"s");
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(runnable);
    }

    private void updateUI() {
        tvSoil.setText(String.valueOf((int)soil));
        tvTemp.setText(String.valueOf((int)temp));
        tvAir.setText(String.valueOf((int)air));
        tvLight.setText(String.format("%.1f", light));
        progressTank.setProgress(tank);
        tvTank.setText((tank*5)+" L de 500 L");
        badgeTank.setText(tank+"% — "+(tank>=60?"Suficiente":tank>=30?"Moderado":"¡Bajo!"));
        if (soil>=60) { chipSoil.setText("↑ Óptimo"); chipSoil.setTextColor(getResources().getColor(R.color.chipOkText,null)); chipSoil.setBackgroundResource(R.drawable.bg_chip_ok); }
        else if (soil>=40) { chipSoil.setText("↔ Normal"); chipSoil.setTextColor(getResources().getColor(R.color.chipWarnText,null)); chipSoil.setBackgroundResource(R.drawable.bg_chip_warn); }
        else { chipSoil.setText("↓ Seco"); chipSoil.setTextColor(getResources().getColor(R.color.chipBadText,null)); chipSoil.setBackgroundResource(R.drawable.bg_chip_bad); }
        if (temp>32) { chipTemp.setText("↑ Alta"); chipTemp.setTextColor(getResources().getColor(R.color.chipWarnText,null)); chipTemp.setBackgroundResource(R.drawable.bg_chip_warn); }
        else { chipTemp.setText("✓ Normal"); chipTemp.setTextColor(getResources().getColor(R.color.chipOkText,null)); chipTemp.setBackgroundResource(R.drawable.bg_chip_ok); }
    }

    private float clamp(float v,float mn,float mx){return Math.max(mn,Math.min(mx,v));}

    @Override public void onDestroyView(){super.onDestroyView();if(handler!=null)handler.removeCallbacks(runnable);}
}
