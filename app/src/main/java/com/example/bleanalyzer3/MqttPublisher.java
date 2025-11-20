package com.example.bleanalyzer3;

import android.content.Context;
import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;

public class MqttPublisher {

    private static final String TAG = "MqttPublisher";

    /* 1. 发现配置（只发一次，保留消息） */
    public static void publishDiscovery(Context ctx, String alias, float temp, float humi, int batt) {
        ConfigIni cfg = ConfigIni.getInstance(ctx);
        String topicTemp = cfg.getMqttTopicPrefix() + "/sensor/" + alias + "_temp/config";
        String topicHumi = cfg.getMqttTopicPrefix() + "/sensor/" + alias + "_humi/config";
        String topicBatt = cfg.getMqttTopicPrefix() + "/sensor/" + alias + "_batt/config";

        String payloadTemp = "{\"name\":\"" + alias + " 温度\",\"state_topic\":\"" + cfg.getMqttTopicPrefix() + "/" + alias + "/temperature\",\"unit_of_measurement\":\"°C\",\"device_class\":\"temperature\",\"unique_id\":\"" + alias + "_temp\"}";
        String payloadHumi = "{\"name\":\"" + alias + " 湿度\",\"state_topic\":\"" + cfg.getMqttTopicPrefix() + "/" + alias + "/humidity\",\"unit_of_measurement\":\"%\",\"device_class\":\"humidity\",\"unique_id\":\"" + alias + "_humi\"}";
        String payloadBatt = "{\"name\":\"" + alias + " 电池\",\"state_topic\":\"" + cfg.getMqttTopicPrefix() + "/" + alias + "/battery\",\"unit_of_measurement\":\"%\",\"device_class\":\"battery\",\"unique_id\":\"" + alias + "_batt\"}";

        try (MqttClient client = createClient(ctx)) {
            client.publish(topicTemp, payloadTemp.getBytes(), 0, true);
            client.publish(topicHumi, payloadHumi.getBytes(), 0, true);
            client.publish(topicBatt, payloadBatt.getBytes(), 0, true);
        } catch (MqttException e) {
            Log.e(TAG, "discovery error", e);
        }
    }

    /* 2. 实时数据（循环发） */
    public static void publishData(Context ctx, String alias, float temp, float humi, int batt) {
        ConfigIni cfg = ConfigIni.getInstance(ctx);
        String topicTemp = cfg.getMqttTopicPrefix() + "/" + alias + "/temperature";
        String topicHumi = cfg.getMqttTopicPrefix() + "/" + alias + "/humidity";
        String topicBatt = cfg.getMqttTopicPrefix() + "/" + alias + "/battery";

        try (MqttClient client = createClient(ctx)) {
            client.publish(topicTemp, String.valueOf(temp).getBytes(), 0, false);
            client.publish(topicHumi, String.valueOf(humi).getBytes(), 0, false);
            client.publish(topicBatt, String.valueOf(batt).getBytes(), 0, false);
        } catch (MqttException e) {
            Log.e(TAG, "data error", e);
        }
    }

    /* 公共工具：短连接 */
    private static MqttClient createClient(Context ctx) throws MqttException {
        ConfigIni cfg   = ConfigIni.getInstance(ctx);
        String broker   = cfg.getMqttBroker();
        String clientId = cfg.getMqttClientId() + "_" + System.currentTimeMillis();
        MqttClient client = new MqttClient(broker, clientId, new org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(cfg.getMqttUser());
        options.setPassword(cfg.getMqttPass().toCharArray());
        options.setCleanSession(true);
        client.connect(options);
        return client;
    }
}
