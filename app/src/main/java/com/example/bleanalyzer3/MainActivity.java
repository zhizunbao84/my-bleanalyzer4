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

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSION = 1;
    private TextView tvLog;
    private ScrollView scroll;
    private StringBuilder sb = new StringBuilder();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;

    /* 日志 */
    private void log(final String txt) {
        runOnUiThread(() -> {
            sb.append(txt).append("\n");
            tvLog.setText(sb);
            scroll.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /* ===================== 权限工具 ===================== */
    private boolean shouldShowRationale(String perm) {
        return ActivityCompat.shouldShowRequestPermissionRationale(this, perm);
    }

    private boolean isPermanentlyDenied(List<String> perms) {
        for (String p : perms) {
            if (!shouldShowRationale(p) &&
                    checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
        toast("请手动打开所需权限");
    }
    /* =================================================== */

    /* ===================== 权限申请 ===================== */
    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestNeededPermissions() {
        /* Android 6.0-11 需要定位开关打开 */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm != null &&
                    !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("需要开启定位")
                        .setMessage("Android 11 及以下系统扫描 BLE 必须打开定位开关")
                        .setPositiveButton("去打开", (d, w) -> {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            finish();
                        })
                        .setNegativeButton("退出", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
                return;
            }
        }

        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasScanPermission())
                list.add(android.Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (!hasScanPermission())
                list.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (list.isEmpty()) {
            startScan();
        } else {
            ActivityCompat.requestPermissions(this,
                    list.toArray(new String[0]), REQ_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            List<String> denied = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    denied.add(permissions[i]);
                }
            }

            if (denied.isEmpty()) {
                startScan();
                return;
            }

            if (isPermanentlyDenied(denied)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("需要权限")
                        .setMessage("扫描蓝牙需要授予权限，否则无法继续")
                        .setPositiveButton("去设置", (d, w) -> {
                            openAppSettings();
                            finish();
                        })
                        .setNegativeButton("退出", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            } else {
                toast("请允许权限以扫描蓝牙");
                requestNeededPermissions();
            }
        }
    }
    /* =================================================== */

    /* ===================== 扫描 ===================== */
    private void startScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开蓝牙");
            finish();
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            toast("获取 BluetoothLeScanner 失败");
            finish();
            return;
        }

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                parseXiaomiTempHumi(result);
            }
        };

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        log("开始扫描 …");
        scanner.startScan(null, settings, scanCallback);
    }
    /* =================================================== */

    /* ===================== 解析 ===================== */
    private void parseXiaomiTempHumi(ScanResult result) {
        byte[] raw = result.getScanRecord().getBytes();
        if (raw == null || raw.length < 15) return;

        int idx = 0;
        while (idx < raw.length) {
            int len = raw[idx++] & 0xFF;
            if (len == 0) break;
            int type = raw[idx] & 0xFF;

            if (type == 0x16 && len >= 13) {
                int uuid = (raw[idx + 1] & 0xFF) | ((raw[idx + 2] & 0xFF) << 8);
                if (uuid == 0xFE95 && (raw[idx + 3] & 0xFF) == 0x70) {
                    if ((raw[idx + 4] & 0xFF) == 0x20) {
                        int tempRaw = (raw[idx + 5] & 0xFF)
                                | ((raw[idx + 6] & 0xFF) << 8);
                        int humRaw = raw[idx + 7] & 0xFF;
                        float temp = tempRaw * 0.1f;
                        int hum = humRaw;

                        String mac = result.getDevice().getAddress();
                        log(String.format(java.util.Locale.CHINA,
                                "%s  →  %.1f ℃   %d %%", mac, temp, hum));
                        return;
                    }
                }
            }
            idx += len;
        }
    }
    /* =================================================== */

    /* ===================== 界面初始化 ===================== */
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

        requestNeededPermissions();
    }
    /* =================================================== */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null && scanCallback != null)
            scanner.stopScan(scanCallback);
    }
}
