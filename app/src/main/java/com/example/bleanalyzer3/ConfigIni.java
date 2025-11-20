package com.example.bleanalyzer3;

import android.content.Context;
import org.ini4j.Ini;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ConfigIni {
    /* 外部私有文件：/sdcard/Android/data/包名/files/config.ini */
    private final File externalIni;
    /* 内部兜底文件（可选） */
    private final File assetsIni;
    /* 当前使用的 ini 对象（外部或兜底） */
    private Ini ini;

    public ConfigIni(Context ctx) {
        externalIni = new File(ctx.getExternalFilesDir(null), "config.ini");
        assetsIni   = new File(ctx.getFilesDir(), "config_copy.ini");
        copyFromAssetsOnce(ctx);          // 首次安装时拷贝
        reload();                           // 读 ini
    }

    /** 用户改完文件后调用：重新加载外部 ini */
    public void reload() {
        try {
            ini = new Ini(externalIni);     // 优先外部
        } catch (Exception e) {
            try {
                ini = new Ini(new FileInputStream(assetsIni)); // 兜底
            } catch (Exception ex) {
                throw new RuntimeException("无 ini 可读", ex);
            }
        }
    }

    /* 首次安装：把 assets/config.ini 拷到外部私有目录 */
    private void copyFromAssetsOnce(Context ctx) {
        if (!externalIni.exists()) {
            try (InputStream in = ctx.getAssets().open("config.ini");
                 OutputStream out = new FileOutputStream(externalIni)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            } catch (IOException e) {
                throw new RuntimeException("首次拷贝 ini 失败", e);
            }
        }
    }

    /* 下面所有 getXXX() 都读 **当前** ini（外部或兜底） */
    public List<BluetoothDevice> getBluetoothDevices() {
        List<BluetoothDevice> list = new ArrayList<>();
        int count = Integer.parseInt(ini.get("bluetooth", "device_count", String.class));
        for (int i = 0; i < count; i++) {
            String mac   = ini.get("bt_" + i, "mac",   String.class);
            String alias = ini.get("bt_" + i, "alias", String.class);
            list.add(new BluetoothDevice(mac, alias));
        }
        return list;
    }

    public String getMqttBroker()      { return ini.get("mqtt", "broker",      String.class); }
    public String getMqttUser()        { return ini.get("mqtt", "username",    String.class); }
    public String getMqttPass()        { return ini.get("mqtt", "password",    String.class); }
    public String getMqttClientId()    { return ini.get("mqtt", "client_id",   String.class); }
    public String getMqttTopicPrefix() { return ini.get("mqtt", "topic_prefix",String.class); }
    public int    getScanIntervalSec() { return Integer.parseInt(ini.get("scan", "interval_seconds", String.class)); }
}
