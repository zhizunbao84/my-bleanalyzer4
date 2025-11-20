package com.example.bleanalyzer3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import java.util.*;

public class MainActivity extends AppCompatActivity implements BleCallback {

    private static final int REQ_PERMISSION = 1;
    private static final int MAX_RETRY     = 2;          // 最多连续申请次数
    private int retryCount = 0;

    private TextView tvLog;
    private ScrollView scroll;
    private StringBuilder sb = new StringBuilder();
    private BleScanner scanner;

    /* ===================== 日志 ===================== */
    public void log(final String txt) {
        runOnUiThread(() -> {
            sb.append(txt).append("\n");
            tvLog.setText(sb);
            scroll.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }
    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /* ===================== 权限：只认“是否授予” ===================== */
    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    /* 真正缺少的权限列表 */
    private List<String> missingPerms() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return list;
    }

    /* 申请权限入口 */
    private void requestPerms() {
        List<String> need = missingPerms();
        if (need.isEmpty()) {
            startScan();
            return;
        }
        /* 连续申请记录 */
        retryCount++;
        ActivityCompat.requestPermissions(this,
                need.toArray(new String[0]), REQ_PERMISSION);
    }

    /* 结果回调：简单暴力，只要没全给就再申，超次进设置 */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            /* 只要有一个拒绝就算失败 */
            boolean allGranted = true;
            for (int g : grantResults) {
                if (g != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                retryCount = 0;
                startScan();
                return;
            }
            /* 未全授予 */
            if (retryCount < MAX_RETRY) {
                toast("需要权限才能扫描蓝牙");
                requestPerms();          // 再试一次
            } else {
                /* 两次都拒绝，带用户进设置页 */
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
                toast("请手动授予权限");
                finish();
            }
        }
    }
    /* =================================================== */

    
    /* ===================== 扫描 ===================== */
    private void startScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开蓝牙");
            finish();
            return;
        }

        
        ConfigIni cfg = ConfigIni.getInstance(this);
        List<BluetoothDevice> devices = cfg.getBluetoothDevices();
        int intervalSec = cfg.getScanIntervalSec();
        scanner = new BleScanner(this, devices, this, intervalSec);
        
        if (scanner == null) {
            toast("BluetoothLeScanner 为空");
            finish();
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 再次保护，防止 ROM 异常
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) return;
        }
        log("开始扫描 …");
        scanner.start();
    }
    /* =================================================== */



    
    /* ===================== 界面 ===================== */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        tvLog = new TextView(this);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        scroll = new ScrollView(this);
        scroll.addView(tvLog);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            toast("设备不支持 BLE");
            finish();
            return;
        }

        /* 6.0-11 额外检查定位开关 */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm != null &&
                    !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                toast("请打开定位开关");
                finish();
                return;
            }
        }

        requestPerms();

    }
    /* =================================================== */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null)
            scanner.stop();
    }

    @Override
    public void onData(String mac, String alias, float temp, float humi, int batt) {
        // 温度/湿度回调
        // 打印 + MQTT
        String log = "★ mac="+ mac + " alias=" + alias + "  温度=" + temp + "℃  湿度=" + humi + "%  电池=" + batt + "%";
        log(log);        
    }

    @Override
    public void onRaw(String hex) {
        // 原始报文回调
        log(hex);
    }
}
