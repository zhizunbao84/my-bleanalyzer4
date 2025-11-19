package com.example.bleanalyzer3;

import android.content.Context;
import org.ini4j.Ini;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ConfigIni {
    public final List<BluetoothDevice> devices;
    public final String mqttBroker;
    public final String mqttUser;
    public final String mqttPass;
    public final String mqttClientId;
    public final String mqttTopicPrefix;
    public int scanIntervalSeconds;

    public ConfigIni(Context ctx) {
        try (InputStream is = ctx.getAssets().open("config.ini")) {
            Ini ini = new Ini(is);
            devices = new ArrayList<>();
            int count = Integer.parseInt(ini.get("bluetooth", "device_count", String.class));
            for (int i = 0; i < count; i++) {
                String mac   = ini.get("bt_" + i, "mac",   String.class);
                String alias = ini.get("bt_" + i, "alias", String.class);
                devices.add(new BluetoothDevice(mac, alias));
            }
            mqttBroker      = ini.get("mqtt", "broker",      String.class);
            mqttUser        = ini.get("mqtt", "username",    String.class);
            mqttPass        = ini.get("mqtt", "password",    String.class);
            mqttClientId    = ini.get("mqtt", "client_id",   String.class);
            mqttTopicPrefix = ini.get("mqtt", "topic_prefix",String.class);
            scanIntervalSeconds = Integer.parseInt(ini.get("scan", "interval_seconds", String.class)); // 默认 60 秒
        } catch (Exception e) {
            throw new RuntimeException("读取 ini 失败", e);
        }
    }
}
