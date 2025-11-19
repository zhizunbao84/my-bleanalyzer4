package com.example.bleanalyzer3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.Context;
import android.util.Log;

import java.util.List;

public class BleScanner {
    public interface Callback {
        void onData(String mac, String alias, float temp, float humi, int batt);
    }

    private final List<BluetoothDevice> devices;
    private final Callback callback;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private final int intervalSec;          // 从 ConfigIni 传入
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;

    public BleScanner(Context ctx, List<BluetoothDevice> devices, Callback callback, int intervalSec) {
        this.devices = devices;
        this.callback = callback;
        this.intervalSec = intervalSec;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) scanner = adapter.getBluetoothLeScanner();
    }

    public void start() {
      // 立即扫一次
      startSingleScan();
      // 定时循环
      scanRunnable = () -> {
          startSingleScan();
          handler.postDelayed(scanRunnable, intervalSec * 1000L);
      };
      handler.postDelayed(scanRunnable, intervalSec * 1000L);
    }

    public void stop() {
      handler.removeCallbacks(scanRunnable);
      if (scanner != null && scanCallback != null) {
          scanner.stopScan(scanCallback);
      }
    }

    /* 只扫一次，3~5 秒后自动停止（降低占空比） */
    private void startSingleScan() {
        if (scanner == null) return;
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                String mac = result.getDevice().getAddress();
                for (BluetoothDevice dev : devices) {
                    if (dev.mac.equalsIgnoreCase(mac)) {
                        // 只解析 BTHome v2 明文 0x00D2FC40
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
    
        // 5 秒后自动停止本次扫描
        handler.postDelayed(() -> {
            if (scanner != null && scanCallback != null)
                scanner.stopScan(scanCallback);
        }, 5000L);   // 只扫 5 秒
    }
  
    /* 与 Python 完全一致的字节解析 */
    private void parseBTHome(String mac, String alias, byte[] raw, int rssi) {
        int idx = 0;
        StringBuilder hex = new StringBuilder("收到广播  MAC=");
        hex.append(mac).append("  Len=")
           .append(raw.length).append("  Data=");
        for (byte b : raw) {
            hex.append(String.format("%02X ", b & 0xFF));
        }
        android.util.Log.d("BLE", hex.toString());
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
        callback.onData(mac, alias, temperature, humidity, (int) battery);
    }
}
