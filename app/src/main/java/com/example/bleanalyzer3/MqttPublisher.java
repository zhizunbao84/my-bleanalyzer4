package com.example.bleanalyzer3;

import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;

public class MqttPublisher {
    private static final String TAG = "MqttPublisher";

    /**
     * 发布一次温湿度/电池数据
     * @param cfg        配置对象（外部 ini）
     * @param alias      设备别名（用于 topic）
     * @param temp       温度
     * @param humi       湿度
     * @param batt       电池 %
     */
    public static void publish(ConfigIni cfg, String alias,
                               float temp, float humi, int batt) {

        String broker      = cfg.getMqttBroker();
        String clientId    = cfg.getMqttClientId() + "_" + System.currentTimeMillis();
        String user        = cfg.getMqttUser();
        String pass        = cfg.getMqttPass();
        String prefix      = cfg.getMqttTopicPrefix();

        String topicTemp = prefix + "/" + alias + "/temperature";
        String topicHumi = prefix + "/" + alias + "/humidity";
        String topicBatt = prefix + "/" + alias + "/battery";

        try {
            MqttClient client = new MqttClient(broker, clientId, new MqttDefaultFilePersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(user);
            options.setPassword(pass.toCharArray());
            options.setCleanSession(true);
            client.connect(options);

            client.publish(topicTemp, ("{\"temperature\": " + String.format("%.1f", temp) + "}").getBytes(), 0, false);
            client.publish(topicHumi, ("{\"humidity\": " + String.format("%.1f", humi) + "}").getBytes(), 0, false);
            client.publish(topicBatt, ("{\"battery\": " + batt + "}").getBytes(), 0, false);

            client.disconnect();
        } catch (MqttException e) {
            Log.e(TAG, "MQTT publish error", e);
        }
    }
}
