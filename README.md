实现ble扫描蓝牙温湿度计获取温度、湿度等数据，并推送给home assistant。

通过配置assets/config.ini设置扫描哪些蓝牙设备、扫描频率等。

```
[bluetooth]
device_count=3               ; 一共几台

[bt_0]
mac=A4:C1:38:25:F4:AE
alias=客厅温湿度计

[bt_1]
mac=A4:C1:38:26:12:34
alias=卧室温湿度计

[bt_2]
mac=A4:C1:38:27:56:78
alias=书房温湿度计

[scan]
interval_seconds=180   # 180 秒 = 3 分钟，默认是1分钟

[mqtt]
broker=tcp://homeassistant.local:1883
username=mqtt_user
password=mqtt_pass
```

| 功能   | 做法                             |
| ---- | ------------------------------ |
| 多台设备 | `bt_0`, `bt_1`, `bt_2` …       |
| 数量控制 | `device_count=N`               |
| 扩展字段 | 每段随意加 `alias`, `room`, `key` … |
| 读取   | 循环 `bt_0` 到 `bt_{N-1}`         |


| 文件                   | 职责                                    |
| -------------------- | ------------------------------------- |
| `ConfigIni.java`     | 只负责读 ini → 返回 `List<BluetoothDevice>` |
| `BleScanner.java`    | 只负责 BLE 扫描 + 过滤                       |
| `MqttPublisher.java` | 只负责 MQTT 连接 + 发布                      |
| `MainActivity.java`  | 只做 **生命周期 + 组装**                      |


