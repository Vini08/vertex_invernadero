package com.agrocontrol.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.agrocontrol.app.R;
import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private LineChart chartSoil, chartTemp;
    private BarChart chartPump;

    // Datos simulados 24h
    private final float[] soilData24h  = {55,52,48,43,40,45,68,72,74,70,67,65,62,60,63,68,70,69,66,63,61,64,67,68};
    private final float[] tempData24h  = {22,21,20,21,22,24,26,28,30,32,34,34,33,31,30,31,32,33,33,32,30,28,26,24};
    private final float[] pumpData7d   = {15,10,0,25,15,20,12};
    private final String[] hours24     = {"00","02","04","06","08","10","12","14","16","18","20","22","24"};
    private final String[] days7       = {"Lun","Mar","Mié","Jue","Vie","Sáb","Dom"};

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        chartSoil = v.findViewById(R.id.chart_soil);
        chartTemp = v.findViewById(R.id.chart_temp);
        chartPump = v.findViewById(R.id.chart_pump);

        // Calcular promedios
        TextView tvAvgSoil = v.findViewById(R.id.tv_avg_soil);
        TextView tvAvgTemp = v.findViewById(R.id.tv_avg_temp);
        tvAvgSoil.setText((int) average(soilData24h) + "%");
        tvAvgTemp.setText((int) average(tempData24h) + "°C");

        // Tabs humedad suelo
        setupTabsSoil(v);
        setupTabsTemp(v);

        // Dibujar gráficas
        drawSoilChart(soilData24h, hours24);
        drawTempChart(tempData24h, hours24);
        drawPumpChart(pumpData7d, days7);
    }

    // ─── Gráfica humedad suelo ───────────────────────────────────────────────
    private void drawSoilChart(float[] data, String[] labels) {
        List<Entry> entries = new ArrayList<>();
        int step = data.length / labels.length;
        for (int i = 0; i < data.length; i++) entries.add(new Entry(i, data[i]));

        LineDataSet ds = new LineDataSet(entries, "Humedad suelo");
        ds.setColor(0xFF2ECC71);
        ds.setFillColor(0xFF2ECC71);
        ds.setFillAlpha(30);
        ds.setDrawFilled(true);
        ds.setDrawCircles(false);
        ds.setLineWidth(2.5f);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setDrawValues(false);

        styleLineChart(chartSoil, new LineData(ds), labels, step, 0, 100, "%");
    }

    // ─── Gráfica temperatura ─────────────────────────────────────────────────
    private void drawTempChart(float[] data, String[] labels) {
        List<Entry> entries = new ArrayList<>();
        int step = data.length / labels.length;
        for (int i = 0; i < data.length; i++) entries.add(new Entry(i, data[i]));

        LineDataSet ds = new LineDataSet(entries, "Temperatura");
        ds.setColor(0xFFF39C12);
        ds.setFillColor(0xFFF39C12);
        ds.setFillAlpha(30);
        ds.setDrawFilled(true);
        ds.setDrawCircles(false);
        ds.setLineWidth(2.5f);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setDrawValues(false);

        styleLineChart(chartTemp, new LineData(ds), labels, step, 0, 50, "°C");
    }

    // ─── Gráfica barras riego ────────────────────────────────────────────────
    private void drawPumpChart(float[] data, String[] labels) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < data.length; i++) entries.add(new BarEntry(i, data[i]));

        BarDataSet ds = new BarDataSet(entries, "Minutos");
        ds.setColor(0xFF4A90E2);
        ds.setDrawValues(false);

        BarData barData = new BarData(ds);
        barData.setBarWidth(0.6f);
        chartPump.setData(barData);

        boolean dark = isDarkMode();
        int textColor = dark ? 0xFF7A90B0 : 0xFF6B7A99;
        int gridColor = dark ? 0x15FFFFFF : 0x15000000;
        int bgColor   = dark ? 0xFF1A2B3C : 0xFFFFFFFF;

        chartPump.setBackgroundColor(bgColor);
        chartPump.getDescription().setEnabled(false);
        chartPump.getLegend().setEnabled(false);
        chartPump.setDrawGridBackground(false);
        chartPump.setDrawBorders(false);
        chartPump.setTouchEnabled(false);
        chartPump.animateY(800);

        XAxis xAxis = chartPump.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);

        YAxis left = chartPump.getAxisLeft();
        left.setTextColor(textColor);
        left.setTextSize(10f);
        left.setGridColor(gridColor);
        left.setDrawAxisLine(false);
        left.setAxisMinimum(0f);

        chartPump.getAxisRight().setEnabled(false);
        chartPump.invalidate();
    }

    // ─── Estilo común LineChart ──────────────────────────────────────────────
    private void styleLineChart(LineChart chart, LineData data,
                                 String[] labels, int step,
                                 float yMin, float yMax, String unit) {
        boolean dark = isDarkMode();
        int textColor = dark ? 0xFF7A90B0 : 0xFF6B7A99;
        int gridColor = dark ? 0x15FFFFFF : 0x15000000;
        int bgColor   = dark ? 0xFF1A2B3C : 0xFFFFFFFF;

        chart.setData(data);
        chart.setBackgroundColor(bgColor);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(true);
        chart.setScaleEnabled(false);
        chart.animateX(600);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setLabelCount(labels.length, true);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels) {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int)(value / step);
                if (idx >= 0 && idx < labels.length) return labels[idx];
                return "";
            }
        });

        YAxis left = chart.getAxisLeft();
        left.setTextColor(textColor);
        left.setTextSize(10f);
        left.setGridColor(gridColor);
        left.setDrawAxisLine(false);
        left.setAxisMinimum(yMin);
        left.setAxisMaximum(yMax);

        chart.getAxisRight().setEnabled(false);
        chart.invalidate();
    }

    // ─── Tabs interactivos ───────────────────────────────────────────────────
    private void setupTabsSoil(View v) {
        TextView t24 = v.findViewById(R.id.tab_soil_24h);
        TextView t7d = v.findViewById(R.id.tab_soil_7d);
        TextView t30d= v.findViewById(R.id.tab_soil_30d);

        t24.setOnClickListener(view -> {
            setTabActive(t24, true,  R.color.chipOkText,  R.drawable.bg_chip_ok);
            setTabActive(t7d, false, R.color.colorTextSecondary, 0);
            setTabActive(t30d,false, R.color.colorTextSecondary, 0);
            drawSoilChart(soilData24h, hours24);
        });
        t7d.setOnClickListener(view -> {
            setTabActive(t24, false, R.color.colorTextSecondary, 0);
            setTabActive(t7d, true,  R.color.chipOkText, R.drawable.bg_chip_ok);
            setTabActive(t30d,false, R.color.colorTextSecondary, 0);
            float[] d7 = {68,65,70,72,60,58,68};
            drawSoilChart(d7, days7);
        });
        t30d.setOnClickListener(view -> {
            setTabActive(t24, false, R.color.colorTextSecondary, 0);
            setTabActive(t7d, false, R.color.colorTextSecondary, 0);
            setTabActive(t30d,true,  R.color.chipOkText, R.drawable.bg_chip_ok);
            float[] d30 = {60,62,65,68,70,67,64,61,58,62,65,68,70,72,69,66,63,61,64,67,68,65,62,60,63,67,70,68,65,62};
            String[] lbls30 = new String[d30.length];
            for(int i=0;i<d30.length;i++) lbls30[i] = (i+1)+"";
            drawSoilChart(d30, lbls30);
        });
    }

    private void setupTabsTemp(View v) {
        TextView t24 = v.findViewById(R.id.tab_temp_24h);
        TextView t7d = v.findViewById(R.id.tab_temp_7d);

        t24.setOnClickListener(view -> {
            setTabActive(t24, true,  R.color.chipWarnText, R.drawable.bg_chip_warn);
            setTabActive(t7d, false, R.color.colorTextSecondary, 0);
            drawTempChart(tempData24h, hours24);
        });
        t7d.setOnClickListener(view -> {
            setTabActive(t24, false, R.color.colorTextSecondary, 0);
            setTabActive(t7d, true,  R.color.chipWarnText, R.drawable.bg_chip_warn);
            float[] d7 = {28,30,27,32,34,29,26};
            drawTempChart(d7, days7);
        });
    }

    private void setTabActive(TextView tab, boolean active, int colorRes, int bgRes) {
        tab.setTextColor(getResources().getColor(colorRes, null));
        if (active && bgRes != 0) tab.setBackgroundResource(bgRes);
        else tab.setBackground(null);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private float average(float[] data) {
        float sum = 0;
        for (float v : data) sum += v;
        return sum / data.length;
    }

    private boolean isDarkMode() {
        int mode = requireContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
