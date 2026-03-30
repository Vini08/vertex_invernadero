package com.agrocontrol.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String P = "AgroControlPrefs";
    private final SharedPreferences p;

    public PrefsManager(Context c) { p = c.getSharedPreferences(P, Context.MODE_PRIVATE); }

    // MQTT
    public String getMqttHost()     { return p.getString("mqtt_host", "broker.hivemq.com"); }
    public int    getMqttPort()     { return p.getInt("mqtt_port", 8883); }
    public String getMqttUser()     { return p.getString("mqtt_user", ""); }
    public String getMqttPass()     { return p.getString("mqtt_pass", ""); }
    public String getTopicSensors() { return p.getString("topic_sensors", "home/sensors"); }
    public String getTopicPump()    { return p.getString("topic_pump", "home/pump"); }
    public String getTopicStatus()  { return p.getString("topic_status", "home/status"); }
    public String getFarmName()     { return p.getString("farm_name", "Parcela Norte"); }
    public int    getInterval()     { return p.getInt("interval", 10); }

    public void setMqttHost(String v)     { p.edit().putString("mqtt_host", v).apply(); }
    public void setMqttPort(int v)        { p.edit().putInt("mqtt_port", v).apply(); }
    public void setMqttUser(String v)     { p.edit().putString("mqtt_user", v).apply(); }
    public void setMqttPass(String v)     { p.edit().putString("mqtt_pass", v).apply(); }
    public void setTopicSensors(String v) { p.edit().putString("topic_sensors", v).apply(); }
    public void setTopicPump(String v)    { p.edit().putString("topic_pump", v).apply(); }
    public void setFarmName(String v)     { p.edit().putString("farm_name", v).apply(); }
    public void setInterval(int v)        { p.edit().putInt("interval", v).apply(); }

    // Horarios de riego
    public boolean isSchedEnabled(int num) {
        return p.getBoolean("sched_" + num + "_enabled", num <= 2); // 1 y 2 activos por defecto
    }
    public void setSchedEnabled(int num, boolean enabled) {
        p.edit().putBoolean("sched_" + num + "_enabled", enabled).apply();
    }
}
