package com.sinovotec.sinovoble.common;

import android.bluetooth.BluetoothDevice;

public class BleScanDevice {
    private BluetoothDevice device;
    private int averagerssi ;
    private int rssi;
    private byte[] scanRecord;

    private String joinTime;        //设备加入队列的时间

    public BleScanDevice(BluetoothDevice device, int rssi, byte[] scanRecord, String joinTime) {
        this.device         = device;
        this.rssi           = rssi;
        this.scanRecord     = scanRecord;
        this.averagerssi    = rssi;
        this.joinTime       = joinTime;
    }

    // 获取设备
    public BluetoothDevice GetDevice() {
        return device;
    }

    // 更新信息
    public void ReflashInf(BluetoothDevice device, int rssi,
                              byte[] scanRecord, String joinTime) {
        this.device         = device;
        this.rssi           = rssi;
        this.scanRecord     = scanRecord;
        this.joinTime       = joinTime;
        averagerssi         = (averagerssi + rssi) / 2;
    }

    public String getJoinTime() {
        return joinTime;
    }

}
