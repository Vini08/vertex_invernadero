package com.agrocontrol.app.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.agrocontrol.app.R;

public class AlertsFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_alerts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        SwitchMaterial swTemp = v.findViewById(R.id.sw_alert_temp);
        SwitchMaterial swSoil = v.findViewById(R.id.sw_alert_soil);
        SwitchMaterial swTank = v.findViewById(R.id.sw_alert_tank);
        SwitchMaterial swWifi = v.findViewById(R.id.sw_alert_wifi);

        swTemp.setOnCheckedChangeListener((b, on) ->
            Toast.makeText(requireContext(),
                on ? "Alerta temperatura activada" : "Alerta temperatura desactivada",
                Toast.LENGTH_SHORT).show());

        swSoil.setOnCheckedChangeListener((b, on) ->
            Toast.makeText(requireContext(),
                on ? "Alerta suelo activada" : "Alerta suelo desactivada",
                Toast.LENGTH_SHORT).show());

        swTank.setOnCheckedChangeListener((b, on) ->
            Toast.makeText(requireContext(),
                on ? "Alerta depósito activada" : "Alerta depósito desactivada",
                Toast.LENGTH_SHORT).show());

        swWifi.setOnCheckedChangeListener((b, on) ->
            Toast.makeText(requireContext(),
                on ? "Alerta conexión activada" : "Alerta conexión desactivada",
                Toast.LENGTH_SHORT).show());
    }
}
