package com.agrocontrol.app.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.agrocontrol.app.R;
import com.agrocontrol.app.api.ApiManager;
import com.agrocontrol.app.model.RiegoManager;
import com.agrocontrol.app.model.RiegoRecord;
import com.agrocontrol.app.mqtt.MqttManager;
import com.agrocontrol.app.mqtt.SensorData;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryFragment extends Fragment {

    private static final String TAG = "HistoryFragment";

    private View contentSensores, contentRiegos;
    private TextView tabSensores, tabRiegos;
    private TextView period24h, period7d;
    private TextView tvSoilPeriod, tvTempPeriod, tvResumenLabel;
    private LinearLayout listRiegos, emptyRiegos;
    private TextView tvTotalRiegos, tvRiegosTitle;
    private LineChart chartSoil, chartTemp;
    private BarChart chartRiegosDias;
    private androidx.cardview.widget.CardView cardChartRiegos;
    private TextView tvAvgSoil, tvAvgTemp;

    private int riegosDays = 1;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        contentSensores  = v.findViewById(R.id.content_sensores);
        contentRiegos    = v.findViewById(R.id.content_riegos);
        tabSensores      = v.findViewById(R.id.tab_sensores);
        tabRiegos        = v.findViewById(R.id.tab_riegos);
        period24h        = v.findViewById(R.id.period_24h);
        period7d         = v.findViewById(R.id.period_7d);
        tvSoilPeriod     = v.findViewById(R.id.tv_soil_period);
        tvTempPeriod     = v.findViewById(R.id.tv_temp_period);
        tvResumenLabel   = v.findViewById(R.id.tv_resumen_label);
        chartSoil        = v.findViewById(R.id.chart_soil);
        chartTemp        = v.findViewById(R.id.chart_temp);
        chartRiegosDias  = v.findViewById(R.id.chart_riegos_dias);
        cardChartRiegos  = v.findViewById(R.id.card_chart_riegos);
        tvAvgSoil        = v.findViewById(R.id.tv_avg_soil);
        tvAvgTemp        = v.findViewById(R.id.tv_avg_temp);
        listRiegos       = v.findViewById(R.id.list_riegos);
        emptyRiegos      = v.findViewById(R.id.empty_riegos);
        tvTotalRiegos    = v.findViewById(R.id.tv_total_riegos);
        tvRiegosTitle    = v.findViewById(R.id.tv_riegos_title);

        tabSensores.setOnClickListener(view -> showMainTab(false));
        tabRiegos.setOnClickListener(view -> showMainTab(true));
        if (period24h != null) period24h.setOnClickListener(view -> selectPeriod(24));
        if (period7d  != null) period7d.setOnClickListener(view  -> selectPeriod(168));

        Bundle args = getArguments();
        boolean openRiegos = args != null && args.getBoolean("show_riegos", false);
        riegosDays = args != null ? args.getInt("days", 1) : 1;

        showMainTab(openRiegos);
        if (!openRiegos) loadChartData("24h");
    }

    private void showMainTab(boolean riegos) {
        if (contentSensores == null || contentRiegos == null) return;
        if (riegos) {
            contentSensores.setVisibility(View.GONE);
            contentRiegos.setVisibility(View.VISIBLE);
            tabRiegos.setTextColor(getResources().getColor(R.color.chipOkText, null));
            tabRiegos.setBackgroundResource(R.drawable.bg_chip_ok);
            tabSensores.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
            tabSensores.setBackground(null);
            loadRiegosFromApi();
        } else {
            contentRiegos.setVisibility(View.GONE);
            contentSensores.setVisibility(View.VISIBLE);
            tabSensores.setTextColor(getResources().getColor(R.color.chipOkText, null));
            tabSensores.setBackgroundResource(R.drawable.bg_chip_ok);
            tabRiegos.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
            tabRiegos.setBackground(null);
            loadChartData("24h");
        }
    }

    private void selectPeriod(int hours) {
        boolean is24h = hours == 24;
        if (period24h != null && period7d != null) {
            if (is24h) {
                period24h.setTextColor(getResources().getColor(R.color.chipOkText, null));
                period24h.setBackgroundResource(R.drawable.bg_chip_ok);
                period7d.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
                period7d.setBackground(null);
            } else {
                period7d.setTextColor(getResources().getColor(R.color.chipOkText, null));
                period7d.setBackgroundResource(R.drawable.bg_chip_ok);
                period24h.setTextColor(getResources().getColor(R.color.colorTextSecondary, null));
                period24h.setBackground(null);
            }
        }
        if (tvSoilPeriod  != null) tvSoilPeriod.setText(is24h ? "Ultimas 24h" : "Ultimos 7 dias");
        if (tvTempPeriod  != null) tvTempPeriod.setText(is24h ? "Ultimas 24h" : "Ultimos 7 dias");
        if (tvAvgSoil     != null) tvAvgSoil.setText("...");
        if (tvAvgTemp     != null) tvAvgTemp.setText("...");
        loadChartData(is24h ? "24h" : "7d");
    }

    private void loadChartData(String period) {
        if (chartSoil == null) return;
        ApiManager.getInstance().getChartData(period,
            data -> {
                if (!isAdded()) return;
                if (tvAvgSoil != null) tvAvgSoil.setText((int)data.avgSoil + "%");
                if (tvAvgTemp != null) tvAvgTemp.setText((int)data.avgTemp + "C");
                if (!data.labels.isEmpty()) {
                    String[] labels = data.labels.toArray(new String[0]);
                    float[] soilArr = new float[data.soilValues.size()];
                    float[] tempArr = new float[data.tempValues.size()];
                    for (int i=0; i<soilArr.length; i++) soilArr[i] = data.soilValues.get(i);
                    for (int i=0; i<tempArr.length; i++) tempArr[i] = data.tempValues.get(i);
                    drawLineChart(chartSoil, soilArr, labels, 0xFF2ECC71, 0, 100);
                    // Ocultar grafica temperatura si datos son 0 (sin sensor DHT11)
                    if (data.avgTemp > 0) {
                        if (chartTemp != null) chartTemp.setVisibility(View.VISIBLE);
                        drawLineChart(chartTemp, tempArr, labels, 0xFFF39C12, 0, 50);
                        if (tvAvgTemp != null) tvAvgTemp.setText((int)data.avgTemp + "C");
                    } else {
                        if (chartTemp != null) chartTemp.setVisibility(View.GONE);
                        if (tvTempPeriod != null) tvTempPeriod.setVisibility(View.GONE);
                        if (tvAvgTemp != null) tvAvgTemp.setText("Sin sensor");
                    }
                }
            },
            err -> {
                if (!isAdded()) return;
                SensorData last = MqttManager.getInstance().getLastData();
                if (last != null) {
                    if (tvAvgSoil != null) tvAvgSoil.setText((int)last.soil + "%");
                    if (tvAvgTemp != null) tvAvgTemp.setText((int)last.temp + "C");
                }
            }
        );
    }

    private void loadRiegosFromApi() {
        if (listRiegos == null) return;
        listRiegos.removeAllViews();
        if (emptyRiegos  != null) emptyRiegos.setVisibility(View.GONE);
        if (tvTotalRiegos != null) tvTotalRiegos.setText("Cargando...");
        if (tvRiegosTitle != null)
            tvRiegosTitle.setText(riegosDays == 1 ? "Riegos de hoy" : "Riegos ultimos " + riegosDays + " dias");

        ApiManager.getInstance().getRiegos(riegosDays,
            (riegos, totalMin) -> {
                if (!isAdded()) return;
                renderRiegos(new ArrayList<>(riegos), totalMin);
                // Grafica de barras solo para 7 dias
                if (riegosDays > 1) drawBarChart(riegos);
            },
            err -> {
                if (!isAdded()) return;
                if (emptyRiegos  != null) emptyRiegos.setVisibility(View.VISIBLE);
                if (tvTotalRiegos != null) tvTotalRiegos.setText("Sin conexion");
            }
        );
    }

    // ─── Grafica de barras: riegos por dia ────────────────────────────────────
    private void drawBarChart(List<RiegoRecord> riegos) {
        if (chartRiegosDias == null || !isAdded()) return;

        // Agrupar riegos por fecha
        Map<String, Integer> countByDay = new LinkedHashMap<>();
        // Inicializar ultimos 7 dias
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        String[] dayLabels = new String[7];
        for (int i = 6; i >= 0; i--) {
            cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String label = sdf.format(cal.getTime());
            dayLabels[6 - i] = label;
            countByDay.put(label, 0);
        }

        for (RiegoRecord r : riegos) {
            if (r.fecha == null || r.fecha.isEmpty()) continue;
            // fecha viene como "DD/MM/YYYY", convertir a "DD/MM"
            String[] partes = r.fecha.split("/");
            if (partes.length >= 2) {
                String key = partes[0] + "/" + partes[1];
                if (countByDay.containsKey(key))
                    countByDay.put(key, countByDay.get(key) + 1);
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        String[] labels = countByDay.keySet().toArray(new String[0]);
        int idx = 0;
        for (Map.Entry<String, Integer> e : countByDay.entrySet()) {
            entries.add(new BarEntry(idx++, e.getValue()));
        }

        BarDataSet ds = new BarDataSet(entries, "Riegos por dia");
        ds.setColor(0xFF2874CC);
        ds.setValueTextColor(isDarkMode() ? 0xFFFFFFFF : 0xFF333333);
        ds.setValueTextSize(10f);

        BarData barData = new BarData(ds);
        barData.setBarWidth(0.6f);

        boolean dark = isDarkMode();
        int textColor = dark ? 0xFF7A90B0 : 0xFF6B7A99;
        int bgColor   = dark ? 0xFF1A2B3C : 0xFFFFFFFF;

        chartRiegosDias.setData(barData);
        chartRiegosDias.setBackgroundColor(bgColor);
        chartRiegosDias.getDescription().setEnabled(false);
        chartRiegosDias.getLegend().setEnabled(false);
        chartRiegosDias.setDrawGridBackground(false);
        chartRiegosDias.setDrawBorders(false);
        chartRiegosDias.setTouchEnabled(false);
        chartRiegosDias.animateY(600);

        XAxis x = chartRiegosDias.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(textColor); x.setTextSize(9f);
        x.setDrawGridLines(false); x.setDrawAxisLine(false);
        x.setGranularity(1f); x.setLabelCount(7);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis left = chartRiegosDias.getAxisLeft();
        left.setTextColor(textColor); left.setTextSize(9f);
        left.setDrawGridLines(true); left.setDrawAxisLine(false);
        left.setAxisMinimum(0f); left.setGranularity(1f);
        chartRiegosDias.getAxisRight().setEnabled(false);
        chartRiegosDias.invalidate();
        chartRiegosDias.setVisibility(View.VISIBLE);
        if (cardChartRiegos != null) cardChartRiegos.setVisibility(View.VISIBLE);
    }

    private void renderRiegos(List<RiegoRecord> riegos, int totalMin) {
        if (listRiegos == null || !isAdded()) return;
        listRiegos.removeAllViews();
        if (riegos.isEmpty()) {
            if (emptyRiegos  != null) emptyRiegos.setVisibility(View.VISIBLE);
            if (tvTotalRiegos != null) tvTotalRiegos.setText("0 riegos - 0 min");
            return;
        }
        if (emptyRiegos  != null) emptyRiegos.setVisibility(View.GONE);
        int total = totalMin;
        if (total == 0) for (RiegoRecord r : riegos) total += r.minutos;
        if (tvTotalRiegos != null)
            tvTotalRiegos.setText(riegos.size() + " riegos - " + total + " min");

        for (RiegoRecord riego : riegos) {
            View item = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_riego, listRiegos, false);
            TextView tvTipo     = item.findViewById(R.id.tv_tipo);
            TextView tvBadge    = item.findViewById(R.id.tv_tipo_badge);
            TextView tvHora     = item.findViewById(R.id.tv_hora);
            TextView tvDuracion = item.findViewById(R.id.tv_duracion);
            TextView tvHumedad  = item.findViewById(R.id.tv_humedad);
            TextView tvMinBig   = item.findViewById(R.id.tv_min_big);
            androidx.cardview.widget.CardView cvIcon = item.findViewById(R.id.cv_tipo_icon);

            tvTipo.setText(riego.getTipoLabel());

            // Fecha para historial multi-dia
            String horaDisplay;
            if (riegosDays > 1 && riego.fecha != null && !riego.fecha.isEmpty()) {
                String[] partes = riego.fecha.split("/");
                String fechaCorta = partes.length >= 2 ? partes[0] + "/" + partes[1] : riego.fecha;
                horaDisplay = fechaCorta + " " + riego.hora;
            } else {
                horaDisplay = riego.hora;
            }
            tvHora.setText(horaDisplay);

            // Duracion completa MM:SS
            String durStr = (riego.duracion != null && !riego.duracion.isEmpty())
                ? riego.duracion : riego.minutos + ":00";
            tvDuracion.setText(durStr);
            tvMinBig.setText(durStr);
            tvHumedad.setText((int)riego.humedadInicio + "%");

            switch (riego.tipo) {
                case "AUTO":
                    cvIcon.setCardBackgroundColor(0xFF27AE60);
                    tvBadge.setText("AUTO");
                    tvBadge.setTextColor(getResources().getColor(R.color.chipOkText, null));
                    tvBadge.setBackgroundResource(R.drawable.bg_chip_ok);
                    tvMinBig.setTextColor(getResources().getColor(R.color.green_primary, null));
                    break;
                case "MANUAL":
                    cvIcon.setCardBackgroundColor(0xFF2874CC);
                    tvBadge.setText("MANUAL");
                    tvBadge.setTextColor(getResources().getColor(R.color.chipInfoText, null));
                    tvBadge.setBackgroundResource(R.drawable.bg_chip_info);
                    tvMinBig.setTextColor(getResources().getColor(R.color.chipInfoText, null));
                    break;
                case "PROGRAMADO":
                    cvIcon.setCardBackgroundColor(0xFFD68910);
                    tvBadge.setText("PROG.");
                    tvBadge.setTextColor(getResources().getColor(R.color.chipWarnText, null));
                    tvBadge.setBackgroundResource(R.drawable.bg_chip_warn);
                    tvMinBig.setTextColor(getResources().getColor(R.color.chipWarnText, null));
                    break;
            }
            listRiegos.addView(item);
        }
    }

    private void drawLineChart(LineChart chart, float[] data, String[] labels, int color, float yMin, float yMax) {
        if (data == null || data.length == 0 || chart == null) return;
        List<Entry> entries = new ArrayList<>();
        for (int i=0; i<data.length; i++) entries.add(new Entry(i, data[i]));
        LineDataSet ds = new LineDataSet(entries, "");
        ds.setColor(color); ds.setFillColor(color); ds.setFillAlpha(30);
        ds.setDrawFilled(true); ds.setDrawCircles(data.length <= 10);
        ds.setCircleColor(color); ds.setCircleRadius(3f);
        ds.setLineWidth(2.5f); ds.setMode(LineDataSet.Mode.CUBIC_BEZIER); ds.setDrawValues(false);
        boolean dark = isDarkMode();
        int textColor = dark ? 0xFF7A90B0 : 0xFF6B7A99;
        int gridColor = dark ? 0x15FFFFFF : 0x15000000;
        int bgColor   = dark ? 0xFF1A2B3C : 0xFFFFFFFF;
        chart.setData(new LineData(ds));
        chart.setBackgroundColor(bgColor);
        chart.getDescription().setEnabled(false); chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false); chart.setDrawBorders(false);
        chart.setTouchEnabled(true); chart.setScaleEnabled(true); chart.animateX(500);
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM); x.setTextColor(textColor); x.setTextSize(9f);
        x.setDrawGridLines(false); x.setDrawAxisLine(false);
        x.setLabelCount(Math.min(labels.length, 7), false); x.setGranularity(1f);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        YAxis left = chart.getAxisLeft();
        left.setTextColor(textColor); left.setTextSize(10f); left.setGridColor(gridColor);
        left.setDrawAxisLine(false); left.setAxisMinimum(yMin); left.setAxisMaximum(yMax);
        chart.getAxisRight().setEnabled(false); chart.invalidate();
    }

    private boolean isDarkMode() {
        int mode = requireContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
