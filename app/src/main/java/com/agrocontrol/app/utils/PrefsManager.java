package com.agrocontrol.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String P = "AgroControlPrefs";
    private final SharedPreferences p;

    public PrefsManager(Context c) { p = c.getSharedPreferences(P, Context.MODE_PRIVATE); }

    public String getMqttHost()     { return p.getString("mqtt_host", "7cb2c54d3a834515ba6642902ea7c481.s1.eu.hivemq.cloud"); }
    public int    getMqttPort()     { return p.getInt("mqtt_port", 8883); }
    public String getMqttUser()     { return p.getString("mqtt_user", "agrocontrol"); }
    public String getMqttPass()     { return p.getString("mqtt_pass", "udLPGY@B_iXEu8b"); }
    public String getTopicSensors() { return p.getString("topic_sensors", "home/sensors"); }
    public String getTopicPump()    { return p.getString("topic_pump", "home/pump"); }
    public String getTopicStatus()  { return p.getString("topic_status", "home/status"); }
    public String getFarmName()     { return p.getString("farm_name", "Invernadero Vertex"); }
    public int    getInterval()     { return p.getInt("interval", 10); }

    public void setMqttHost(String v)     { p.edit().putString("mqtt_host", v).apply(); }
    public void setMqttPort(int v)        { p.edit().putInt("mqtt_port", v).apply(); }
    public void setMqttUser(String v)     { p.edit().putString("mqtt_user", v).apply(); }
    public void setMqttPass(String v)     { p.edit().putString("mqtt_pass", v).apply(); }
    public void setTopicSensors(String v) { p.edit().putString("topic_sensors", v).apply(); }
    public void setTopicPump(String v)    { p.edit().putString("topic_pump", v).apply(); }
    public void setTopicStatus(String v)  { p.edit().putString("topic_status", v).apply(); }
    public void setFarmName(String v)     { p.edit().putString("farm_name", v).apply(); }
    public void setInterval(int v)        { p.edit().putInt("interval", v).apply(); }

    // Umbral
    public int  getThreshold()      { return p.getInt("threshold", 40); }
    public void setThreshold(int v) { p.edit().putInt("threshold", v).apply(); }

    // Límite diario de riegos
    public int  getMaxPumpsPerDay()      { return p.getInt("max_pumps_day", 6); }
    public void setMaxPumpsPerDay(int v) { p.edit().putInt("max_pumps_day", v).apply(); }

    // Horarios
    public boolean isSchedEnabled(int num) { return p.getBoolean("sched_"+num+"_enabled", num<=2); }
    public void setSchedEnabled(int num, boolean v) { p.edit().putBoolean("sched_"+num+"_enabled", v).apply(); }
    public int getSchedHour(int num, int def) { return p.getInt("sched_"+num+"_hour", def); }
    public int getSchedMin(int num, int def)  { return p.getInt("sched_"+num+"_min",  def); }
    public void setSchedHour(int num, int h)  { p.edit().putInt("sched_"+num+"_hour", h).apply(); }
    public void setSchedMin(int num, int m)   { p.edit().putInt("sched_"+num+"_min",  m).apply(); }
    public int  getSchedDuration(int num, int def) { return p.getInt("sched_"+num+"_duration", def); }
    public void setSchedDuration(int num, int v)   { p.edit().putInt("sched_"+num+"_duration", v).apply(); }
}
