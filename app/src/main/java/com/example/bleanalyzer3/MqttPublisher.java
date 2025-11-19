package com.example.bleanalyzer3;

import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;

public class MqttPublisher {
    private static final String TAG = "MqttPublisher";

    public static void publish(ConfigIni cfg, String alias,
                               float temp, float humi, int batt) {
        String broker = cfg.mqttBroker;
        String clientId = cfg.mqttClientId + "_" + System.currentTimeMillis();
        String topicTemp = cfg.mqttTopicPrefix + "/" + alias + "/temperature";
        String topicHumi = cfg.mqttTopicPrefix + "/" + alias + "/humidity";
        String topicBatt = cfg.mqttTopicPrefix + "/" + alias + "/battery";

        try {
            MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(cfg.mqttUser);
            options.setPassword(cfg.mqttPass.toCharArray());
            options.setCleanSession(true);
            client.connect(options);

            client.publish(topicTemp, ("{\"temperature\": " + String.format("%.1f", temp) + "}").getBytes(), 0, false);
            client.publish(topicHumi, ("{\"humidity\": " + String.format("%.1f", humi) + "}").getBytes(), 0, false);
            client.publish(topicBatt, ("{\"battery\": " + batt + "}").getBytes(), 0, false);

            client.disconnect();
        } catch (MqttException e) {
            Log.e(TAG, "MQTT error", e);
        }
    }
}
