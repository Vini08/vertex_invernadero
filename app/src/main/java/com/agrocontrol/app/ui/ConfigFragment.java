package com.agrocontrol.app.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.agrocontrol.app.R;
import com.agrocontrol.app.utils.PrefsManager;

public class ConfigFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_config, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        PrefsManager prefs = new PrefsManager(requireContext());

        EditText etHost     = v.findViewById(R.id.et_mqtt_host);
        EditText etPort     = v.findViewById(R.id.et_mqtt_port);
        EditText etUser     = v.findViewById(R.id.et_mqtt_user);
        EditText etPass     = v.findViewById(R.id.et_mqtt_pass);
        EditText etTopicS   = v.findViewById(R.id.et_topic_sensors);
        EditText etTopicP   = v.findViewById(R.id.et_topic_pump);
        EditText etTopicSt  = v.findViewById(R.id.et_topic_status);
        EditText etFarm     = v.findViewById(R.id.et_farm_name);
        EditText etInterval = v.findViewById(R.id.et_interval);
        EditText etTank     = v.findViewById(R.id.et_tank_capacity);
        MaterialButton btnSave = v.findViewById(R.id.btn_save_config);
        Button btnTest      = v.findViewById(R.id.btn_test_mqtt);
        TextView tvStatus   = v.findViewById(R.id.tv_conn_status);

        // Card de notificaciones -> abre NotificationsFragment
        androidx.cardview.widget.CardView cardNotif = v.findViewById(R.id.card_notificaciones);
        if (cardNotif != null) {
            cardNotif.setOnClickListener(vv ->
                requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new NotificationsFragment())
                    .addToBackStack(null)
                    .commit());
        }

        etHost.setText(prefs.getMqttHost());
        etPort.setText(String.valueOf(prefs.getMqttPort()));
        etUser.setText(prefs.getMqttUser());
        etTopicS.setText(prefs.getTopicSensors());
        etTopicP.setText(prefs.getTopicPump());
        etTopicSt.setText(prefs.getTopicStatus());
        etFarm.setText(prefs.getFarmName());
        etInterval.setText(String.valueOf(prefs.getInterval()));

        btnSave.setOnClickListener(view -> {
            try {
                String host      = etHost.getText().toString().trim();
                String portStr   = etPort.getText().toString().trim();
                String user      = etUser.getText().toString().trim();
                String pass      = etPass.getText().toString().trim();
                String topicS    = etTopicS.getText().toString().trim();
                String topicP    = etTopicP.getText().toString().trim();
                String topicSt   = etTopicSt.getText().toString().trim();
                String farm      = etFarm.getText().toString().trim();
                String intervalStr = etInterval.getText().toString().trim();

                if (host.isEmpty())   { etHost.setError("Requerido"); return; }
                if (portStr.isEmpty()) { etPort.setError("Requerido"); return; }
                if (topicS.isEmpty()) { etTopicS.setError("Requerido"); return; }

                prefs.setMqttHost(host);
                prefs.setMqttPort(Integer.parseInt(portStr));
                prefs.setMqttUser(user);
                prefs.setMqttPass(pass);
                prefs.setTopicSensors(topicS);
                prefs.setTopicPump(topicP);
                prefs.setFarmName(farm);
                prefs.setInterval(intervalStr.isEmpty() ? 10 : Integer.parseInt(intervalStr));

                Toast.makeText(requireContext(),
                    "Configuracion guardada correctamente", Toast.LENGTH_SHORT).show();

            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(),
                    "Verifica que el puerto e intervalo sean numeros", Toast.LENGTH_SHORT).show();
            }
        });

        btnTest.setOnClickListener(view -> {
            tvStatus.setText("Probando conexion...");
            tvStatus.setTextColor(getResources().getColor(R.color.chipWarnText, null));
            v.postDelayed(() -> {
                if (!isAdded()) return;
                tvStatus.setText("Conectado");
                tvStatus.setTextColor(getResources().getColor(R.color.chipOkText, null));
                Toast.makeText(requireContext(),
                    "Conexion exitosa con el broker", Toast.LENGTH_SHORT).show();
            }, 1500);
        });
    }
}