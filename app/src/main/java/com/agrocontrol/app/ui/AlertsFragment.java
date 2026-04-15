package com.agrocontrol.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.agrocontrol.app.R;
import com.agrocontrol.app.api.ApiManager;
import org.json.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertsFragment extends Fragment {

    private static final String TAG = "AlertsFragment";

    private LinearLayout listAlertas, emptyAlertas, llFiltros, llFiltros2;
    private TextView tvAlertCount;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentFilter = "today";
    private String currentType   = "ALL";

    // Fila 1 de filtros
    private static final String[][] TIPOS_FILA1 = {
        {"ALL",          "Todos"},
        {"ESP_OFFLINE",  "Desconexion"},
        {"ESP_ONLINE",   "Reconectado"},
        {"HUMIDITY_LOW", "Humedad"},
        {"PUMP_OFF",     "Bomba"},
    };
    // Fila 2 de filtros
    private static final String[][] TIPOS_FILA2 = {
        {"DAILY_LIMIT",   "Limite diario"},
        {"RAPID_DROP",    "Caida rapida"},
        {"LOW_EFFICIENCY","Eficiencia"},
        {"NO_FLOW",       "Sin flujo"},
    };
    private static final String[][] TIPOS = {}; // unused

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_alerts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        listAlertas  = v.findViewById(R.id.list_alertas);
        emptyAlertas = v.findViewById(R.id.empty_alertas);
        tvAlertCount = v.findViewById(R.id.tv_alert_count);

        addPeriodoButtons();
        addFiltrosView();
        loadAlerts();
    }

    @Override public void onResume() {
        super.onResume();
        loadAlerts();
    }

    private void addPeriodoButtons() {
        if (listAlertas == null) return;
        ViewGroup parent = (ViewGroup) listAlertas.getParent();
        if (parent == null) return;
        int index = indexOfView(parent, listAlertas);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, 10);
        row.setLayoutParams(rowLp);

        final TextView btnHoy = makeTabBtn("Hoy", true);
        final TextView btn7d  = makeTabBtn("Ultimos 7 dias", false);
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp1.setMargins(0, 0, 8, 0);
        btnHoy.setLayoutParams(bp1);
        btn7d.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnHoy.setOnClickListener(vv -> {
            currentFilter = "today"; currentType = "ALL";
            setTabActive(btnHoy, btn7d);
            renderFiltros(); loadAlerts();
        });
        btn7d.setOnClickListener(vv -> {
            currentFilter = "all"; currentType = "ALL";
            setTabActive(btn7d, btnHoy);
            renderFiltros(); loadAlerts();
        });

        row.addView(btnHoy);
        row.addView(btn7d);
        parent.addView(row, index);
    }

    private void setTabActive(TextView active, TextView inactive) {
        active.setTextColor(getResources().getColor(R.color.chipOkText, null));
        active.setBackgroundResource(R.drawable.bg_chip_ok);
        inactive.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
        inactive.setBackgroundResource(R.drawable.bg_schedule_time);
    }

    private TextView makeTabBtn(String text, boolean active) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, 20, 0, 20);
        if (active) {
            tv.setTextColor(getResources().getColor(R.color.chipOkText, null));
            tv.setBackgroundResource(R.drawable.bg_chip_ok);
        } else {
            tv.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
            tv.setBackgroundResource(R.drawable.bg_schedule_time);
        }
        return tv;
    }

    private void addFiltrosView() {
        if (listAlertas == null) return;
        ViewGroup parent = (ViewGroup) listAlertas.getParent();
        if (parent == null) return;
        int index = indexOfView(parent, listAlertas);

        // Contenedor de 2 filas
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.setMargins(0, 0, 0, 12);
        container.setLayoutParams(containerLp);

        // Fila 1
        HorizontalScrollView hsv1 = new HorizontalScrollView(requireContext());
        hsv1.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hsv1.setHorizontalScrollBarEnabled(false);
        llFiltros = new LinearLayout(requireContext());
        llFiltros.setOrientation(LinearLayout.HORIZONTAL);
        llFiltros.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hsv1.addView(llFiltros);

        // Fila 2
        HorizontalScrollView hsv2 = new HorizontalScrollView(requireContext());
        LinearLayout.LayoutParams hsv2lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsv2lp.setMargins(0, 8, 0, 0);
        hsv2.setLayoutParams(hsv2lp);
        hsv2.setHorizontalScrollBarEnabled(false);
        llFiltros2 = new LinearLayout(requireContext());
        llFiltros2.setOrientation(LinearLayout.HORIZONTAL);
        llFiltros2.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hsv2.addView(llFiltros2);

        container.addView(hsv1);
        container.addView(hsv2);
        parent.addView(container, index);

        renderFiltros();
    }

    private void renderFiltros() {
        if (!isAdded()) return;
        renderFiltroRow(llFiltros, TIPOS_FILA1);
        renderFiltroRow(llFiltros2, TIPOS_FILA2);
    }

    private void renderFiltroRow(LinearLayout container, String[][] tipos) {
        if (container == null) return;
        container.removeAllViews();
        for (String[] tipo : tipos) {
            final String id = tipo[0];
            String label    = tipo[1];
            TextView btn = new TextView(requireContext());
            btn.setText(label);
            btn.setTextSize(11f);
            btn.setPadding(24, 8, 24, 8);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 8, 0);
            btn.setLayoutParams(lp);
            if (id.equals(currentType)) {
                btn.setTextColor(getResources().getColor(R.color.chipOkText, null));
                btn.setBackgroundResource(R.drawable.bg_chip_ok);
            } else {
                btn.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
                btn.setBackgroundResource(R.drawable.bg_schedule_time);
            }
            btn.setOnClickListener(vv -> {
                currentType = id;
                renderFiltros();
                loadAlerts();
            });
            container.addView(btn);
        }
    }

    private int indexOfView(ViewGroup parent, View child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) == child) return i;
        }
        return parent.getChildCount();
    }

    private void loadAlerts() {
        if (listAlertas == null || !isAdded()) return;
        listAlertas.removeAllViews();
        if (emptyAlertas != null) emptyAlertas.setVisibility(View.GONE);
        if (tvAlertCount != null) tvAlertCount.setText("Cargando...");

        // Llamar API en background thread y procesar resultado en callback real
        executor.execute(() -> {
            try {
                // Usar HttpURLConnection directo para get_alerts
                String base = "https://zxo.vwa.mybluehost.me/agrocontrol/";
                String key  = "agro_secret_2026";
                java.net.URL url = new java.net.URL(base + "get_alerts.php?filter=" + currentFilter + "&key=" + key);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                java.io.InputStream is;
                try { is = conn.getInputStream(); }
                catch (java.io.IOException e) { is = conn.getErrorStream(); }

                if (is == null) {
                    mainHandler.post(() -> showError("Sin respuesta del servidor"));
                    return;
                }

                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String response = sb.toString();

                Log.d(TAG, "Alerts response: " + response);

                JSONObject json = new JSONObject(response);
                if (!json.optBoolean("success", false)) {
                    mainHandler.post(() -> showError("Error: " + json.optString("error")));
                    return;
                }

                JSONArray alerts = json.getJSONArray("alerts");

                // Filtrar por tipo
                List<JSONObject> filtered = new ArrayList<>();
                for (int i = 0; i < alerts.length(); i++) {
                    JSONObject a = alerts.getJSONObject(i);
                    String type = a.optString("type", "");
                    if (currentType.equals("ALL") || type.equals(currentType)) {
                        filtered.add(a);
                    }
                }

                mainHandler.post(() -> renderAlerts(filtered));

            } catch (Exception e) {
                Log.e(TAG, "Error loadAlerts: " + e.getMessage());
                mainHandler.post(() -> showError("Sin conexion: " + e.getMessage()));
            }
        });
    }

    private void showError(String msg) {
        if (!isAdded()) return;
        if (emptyAlertas != null) emptyAlertas.setVisibility(View.VISIBLE);
        if (tvAlertCount != null) tvAlertCount.setText(msg);
    }

    private void renderAlerts(List<JSONObject> filtered) {
        if (!isAdded() || listAlertas == null) return;
        listAlertas.removeAllViews();

        if (filtered.isEmpty()) {
            if (emptyAlertas != null) emptyAlertas.setVisibility(View.VISIBLE);
            if (tvAlertCount != null) tvAlertCount.setText("0 alertas");
            return;
        }

        if (emptyAlertas != null) emptyAlertas.setVisibility(View.GONE);
        if (tvAlertCount != null) tvAlertCount.setText(filtered.size() + " alertas");

        for (JSONObject alert : filtered) {
            try {
                String type      = alert.optString("type", "INFO");
                String message   = alert.optString("message", "");
                String createdAt = alert.optString("created_at", "");
                String hora  = createdAt.length() >= 16 ? createdAt.substring(11, 16) : createdAt;
                String fecha = createdAt.length() >= 10 ? createdAt.substring(5, 10).replace("-", "/") : "";

                View item = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_alert, listAlertas, false);

                TextView tvType = item.findViewById(R.id.tv_alert_type);
                TextView tvMsg  = item.findViewById(R.id.tv_alert_msg);
                TextView tvTime = item.findViewById(R.id.tv_alert_time);
                androidx.cardview.widget.CardView cvIcon = item.findViewById(R.id.cv_alert_icon);

                if (tvMsg  != null) tvMsg.setText(message);
                if (tvTime != null) tvTime.setText(fecha + " " + hora);

                int    color     = 0xFF6B7A99;
                String label     = type;
                int    bgRes     = R.drawable.bg_schedule_time;
                int    textColor = 0xFF6B7A99;

                switch (type) {
                    case "ESP_OFFLINE":
                        label="Desconexion"; color=0xFFE74C3C;
                        bgRes=R.drawable.bg_chip_bad; textColor=0xFFE74C3C; break;
                    case "ESP_ONLINE":
                        label="Reconexion"; color=0xFF27AE60;
                        bgRes=R.drawable.bg_chip_ok; textColor=0xFF27AE60; break;
                    case "HUMIDITY_LOW":
                        label="Humedad baja"; color=0xFF2874CC;
                        bgRes=R.drawable.bg_chip_info; textColor=0xFF2874CC; break;
                    case "DAILY_LIMIT":
                        label="Limite diario"; color=0xFFD68910;
                        bgRes=R.drawable.bg_chip_warn; textColor=0xFFD68910; break;
                    case "RAPID_DROP":
                        label="Caida rapida"; color=0xFFE74C3C;
                        bgRes=R.drawable.bg_chip_bad; textColor=0xFFE74C3C; break;
                    case "LOW_EFFICIENCY":
                        label="Baja eficiencia"; color=0xFFD68910;
                        bgRes=R.drawable.bg_chip_warn; textColor=0xFFD68910; break;
                    case "NO_FLOW":
                        label="Sin flujo"; color=0xFFE74C3C;
                        bgRes=R.drawable.bg_chip_bad; textColor=0xFFE74C3C; break;
                    case "PUMP_OFF":
                        label="Bomba apagada"; color=0xFF2874CC;
                        bgRes=R.drawable.bg_chip_info; textColor=0xFF2874CC; break;
                }

                if (tvType != null) {
                    tvType.setText(label);
                    tvType.setTextColor(textColor);
                    tvType.setBackgroundResource(bgRes);
                }
                if (cvIcon != null) cvIcon.setCardBackgroundColor(color);

                listAlertas.addView(item);
            } catch (Exception e) {
                Log.e(TAG, "Error render: " + e.getMessage());
            }
        }
    }
}
