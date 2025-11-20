package com.example.bleanalyzer3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

public class BleScanner {
    public interface Callback {
        void onData(String mac, String alias, float temp, float humi, int batt);
    }

    private final List<BluetoothDevice> devices;
    private final Callback callback;
    private final int intervalSec;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /* 首次发现标记：只发一次 MQTT 发现 */
    private static boolean firstPublish = true;

    public BleScanner(Context ctx, List<BluetoothDevice> devices, Callback callback, int intervalSec) {
        this.devices = devices;
        this.callback = callback;
        this.intervalSec = intervalSec;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) scanner = adapter.getBluetoothLeScanner();
    }

    public void start() {
        if (scanner == null) return;
        startSingleScan(); // 立即扫一次
        handler.postDelayed(intervalRunnable, intervalSec * 1000L);
    }

    public void stop() {
        handler.removeCallbacks(intervalRunnable);
        if (scanner != null && scanCallback != null) scanner.stopScan(scanCallback);
    }

    private final Runnable intervalRunnable = new Runnable() {
        @Override
        public void run() {
            startSingleScan();
            handler.postDelayed(this, intervalSec * 1000L);
        }
    };

    private void startSingleScan() {
        if (scanner == null) return;
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                String mac = result.getDevice().getAddress();
                for (BluetoothDevice dev : devices) {
                    if (dev.mac.equalsIgnoreCase(mac)) {
                        byte[] raw = result.getScanRecord().getBytes();
                        if (raw == null) return;
                        parseBTHome(dev.mac, dev.alias, raw, result.getRssi());
                        break;
                    }
                }
            }
        };
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        scanner.startScan(null, settings, scanCallback);
        handler.postDelayed(() -> {
            if (scanner != null && scanCallback != null) scanner.stopScan(scanCallback);
        }, 5000L); // 只扫 5 秒
    }

    private void parseBTHome(String mac, String alias, byte[] raw, int rssi) {
        int idx = 0;
        while (idx < raw.length - 6) {
            if (raw[idx] == 0x16 &&
                raw[idx + 1] == (byte) 0xD2 &&
                raw[idx + 2] == (byte) 0xFC &&
                raw[idx + 3] == 0x40) break;
            idx++;
        }
        if (idx > raw.length - 6) return;
        int offset = idx + 4;
        int i = offset;
        float temperature = 0, humidity = 0, voltage = 0;
        int battery = 0;

        while (i < raw.length - 1) {
            int typeId = raw[i] & 0xFF;
            i++;
            switch (typeId) {
                case 0x01: battery = raw[i] & 0xFF; i += 1; break;
                case 0x02:
                    int tempRaw = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                    temperature = tempRaw / 100.0f;
                    i += 2;
                    break;
                case 0x03:
                    int humRaw = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                    humidity = humRaw / 100.0f;
                    i += 2;
                    break;
                case 0x0C:
                    int voltRaw = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                    voltage = voltRaw / 1000.0f;
                    i += 2;
                    break;
                default: i += 1; break;
            }
        }
        /* ****** 自动 MQTT 发布 ****** */
        if (firstPublish) {   // 只发一次发现
            MqttPublisher.publishDiscovery(getContext(), alias, temperature, humidity, battery);
            firstPublish = false;
        }
        MqttPublisher.publishData(getContext(), alias, temperature, humidity, battery);

        /* ****** 回调给 UI ****** */
        callback.onData(mac, alias, temperature, humidity, battery);
    }

    /* 辅助：获取 Context（单例已持有） */
    private Context getContext() {
        return devices.isEmpty() ? null : (Context) devices.get(0).mac.getClass().getClassLoader() == null ? null : (Context) devices.get(0).mac.getClass().getClassLoader().getSystemClassLoader() == null ? null : (Context) devices.get(0).mac.getClass().getClassLoader().getSystemClassLoader();
    }
}
