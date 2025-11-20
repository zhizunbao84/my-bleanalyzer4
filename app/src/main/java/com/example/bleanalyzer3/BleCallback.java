package com.example.bleanalyzer3;

public interface BleCallback {
    /* 温度/湿度/电池数据 */
    void onData(String mac, String alias, float temp, float humi, int batt);

    /* 原始报文（可选） */
    void onRaw(String hex);
}
