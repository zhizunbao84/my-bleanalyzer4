package com.example.bleanalyzer;

import android.bluetooth.*;
import android.util.Log;
import java.util.Locale;
import java.util.UUID;

class BleGattCallback extends BluetoothGattCallback {

    private final BleWrapper.Callback log;

    BleGattCallback(BleWrapper.Callback log) {
        this.log = log;
    }

    /* ---------- 连接状态 ---------- */
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            log.onLog("已连接，开始发现服务…");
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            log.onLog("连接断开");
            gatt.close();
        }
    }

    /* ---------- 发现服务 ---------- */
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            log.onLog("发现服务失败 status=" + status);
            return;
        }

        for (BluetoothGattService svc : gatt.getServices()) {
            log.onLog(String.format(Locale.US, "Service  %s", svc.getUuid()));

            for (BluetoothGattCharacteristic ch : svc.getCharacteristics()) {
                int props = ch.getProperties();
                log.onLog(String.format(Locale.US, "  Characteristic  %s  props=0x%02X", ch.getUuid(), props));

                /* 1. 能通知/指示就全部打开 */
                boolean canNotify  = (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY)  != 0;
                boolean canIndicate = (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                if (canNotify || canIndicate) {
                    boolean ok = gatt.setCharacteristicNotification(ch, true);
                    if (!ok) {
                        log.onLog("    打开通知失败");
                        continue;
                    }

                    /* 2. 写 CCC Descriptor 使能 notify/indicate */
                    UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                    BluetoothGattDescriptor desc = ch.getDescriptor(CCC);
                    if (desc != null) {
                        byte[] value = canIndicate
                                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                        desc.setValue(value);
                        boolean writeOk = gatt.writeDescriptor(desc);
                        log.onLog(String.format(Locale.US,
                                "    %s 已使能 (%s) ret=%b",
                                ch.getUuid(), canIndicate ? "INDICATE" : "NOTIFY", writeOk));
                    }
                }

                /* 3. 打印所有 Descriptor（调试用） */
                for (BluetoothGattDescriptor d : ch.getDescriptors()) {
                    log.onLog("    Descriptor  " + d.getUuid());
                }
            }
        }
    }

    /* ---------- 实时数据 ---------- */
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        log.onLog(String.format(Locale.US, "<<< 收到通知  %s  数据=%s",
                characteristic.getUuid(), bytesToHex(data)));
        /* TODO: 如果一条 Characteristic 里含多种数据，在此按协议解析即可 */
    }

    /* ---------- 工具 ---------- */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }
}
