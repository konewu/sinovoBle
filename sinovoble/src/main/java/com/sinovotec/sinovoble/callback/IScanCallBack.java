package com.sinovotec.sinovoble.callback;

public interface IScanCallBack {
    //发现设备
    void onDeviceFound(String scanResult);

    //扫描超时
    void onScanTimeout(String scanResult);
}
