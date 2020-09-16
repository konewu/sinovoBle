package com.sinovotec.sinovoble.callback;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.util.Log;
import android.util.SparseArray;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.common.BleConfig;
import com.sinovotec.sinovoble.common.BleConnectLock;
import com.sinovotec.sinovoble.common.BleScanDevice;

import java.util.LinkedHashMap;

import static com.sinovotec.sinovoble.common.ComTool.byte2hex;
import static com.sinovotec.sinovoble.common.ComTool.calTimeDiff;
import static com.sinovotec.sinovoble.common.ComTool.getNowTime;

public class BleScanCallBack extends ScanCallback {
    private static BleScanCallBack instance;                //入口操作管理
    private static String TAG = "SinovoBle";
    private boolean isScanning  = false;          //是否正在扫描

    IScanCallBack iScanCallBack;          //扫描结果回调

    public BleScanCallBack(IScanCallBack scanCallBack){
        this.iScanCallBack = scanCallBack;
        if (iScanCallBack == null){
            throw new NullPointerException("this scanCallback is null!");
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void setScanning(boolean scanning) {
        isScanning = scanning;
    }


    /**
     * 单例方式获取蓝牙通信入口
     */
    public static BleScanCallBack getInstance(IScanCallBack scanCallBack) {

        if (instance != null){
            return  instance;
        }

        synchronized (BleScanCallBack.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
            if (instance == null) {
                instance = new BleScanCallBack(scanCallBack){
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        BleScanDevice bleScanDevice = analyzeScanResult(result);

                        for (int i = 0; i< SinovoBle.getInstance().getScanLockList().size(); i++){
                            if (SinovoBle.getInstance().getScanLockList().get(i).GetDevice().getAddress().equals(bleScanDevice.GetDevice().getAddress())){
                                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                                map.put("scanResult", "1");
                                map.put("lockMac", bleScanDevice.GetDevice().getAddress());
                                map.put("lockType", bleScanDevice.GetDevice().getName());

                                iScanCallBack.onDeviceFound(JSONObject.toJSONString(map));

                                //尝试进行自动连接
                                SinovoBle.getInstance().connectLock();
                                Log.w(TAG, "扫描到符合条件锁，回调通知客户："+bleScanDevice.GetDevice().getAddress());
                                break;
                            }
                        }
                    }

                    @Override
                    public  void  onScanFailed(int errorCode){
                        Log.e(TAG, "蓝牙扫描  失败");
                    }
                };
            }
        }
        return instance;
    }

    /**
     *  处理蓝牙扫描得到的解析结果
     */
    BleScanDevice analyzeScanResult(ScanResult result) {
        ScanRecord mScanRecord = result.getScanRecord();
        byte[] manufacturerData = new byte[0];
        int mRssi = result.getRssi();
        BluetoothDevice scanLock = result.getDevice();
        String scanLockMac  = scanLock.getAddress();
        String scanLockName = scanLock.getName();

        if (mScanRecord != null) {
            SparseArray<byte[]> mandufacturerDatas = mScanRecord.getManufacturerSpecificData();
            for (int i=0; i<mandufacturerDatas.size(); i++){
                manufacturerData = mandufacturerDatas.get(i);
            }
        }
        Log.d(TAG, "Scan result：{ Mac address:" +scanLockMac + " Lock name："+scanLockName + " Rssi:"+mRssi + " Adv_data:"+byte2hex(manufacturerData)+"}");

        //将扫描到的设备 放入到 list中
        boolean deviceExist = false;

        //判断扫描到的锁 是不是已经存在扫描列表中，如果已经存在，则更新其信息
        for (int i = 0; i < SinovoBle.getInstance().getScanLockList().size(); i++) {
            BleScanDevice bleScanDevice = SinovoBle.getInstance().getScanLockList().get(i);
            if (bleScanDevice.GetDevice().getAddress().compareTo(scanLockMac)== 0){
                String nowtime = getNowTime();
                bleScanDevice.ReflashInf(scanLock, mRssi, manufacturerData, nowtime);
                deviceExist = true;
            }
        }

        //判断扫描到的锁 是不是已经存在扫描列表中，如果不存在，则添加此锁
        //增加判断条件，针对绑定的时候，如果该锁已经尝试过绑定了，不能再进入扫描结果中 再次来绑定了
        boolean deviceIn = true;

        if (!deviceExist){
            if (SinovoBle.getInstance().isBindMode()){
                //已经绑定列表中包含此锁，则不再添加
                if (SinovoBle.getInstance().getBondBleMacList().contains(scanLockMac)){
                    Log.i(TAG, "锁" +scanLockMac +" 已经存在 bondBleMacList中，已经绑定过,不是要连接的锁，不需要再添加");
                    deviceIn = false;
                }

                //如果广播包内容为空，旧的lock,需要兼容
                if (manufacturerData == null || manufacturerData.length ==0){
                    deviceIn = true;
                }else {
                    if (scanLockName ==null ||(SinovoBle.getInstance().getLockTypeForAdd().contains(scanLockName))){
                        String advData = byte2hex(manufacturerData);
                        if (advData.length() >=14) {
                            String adLockid = advData.substring(2, 14);
                            //如果锁广播的是lockid，需要判断id是否跟输入的一致
                            if (!adLockid.equals(SinovoBle.getInstance().getLockID()) && advData.substring(0, 2).equals("01")) {
                                deviceIn = false;
                                Log.w(TAG, "Adv_data's lockID:" + adLockid + " is different from the lockID(" + SinovoBle.getInstance().getLockID() + ") entered by user,ignore");
                            }
                        }
                    }else {
                        deviceIn = false;
                        Log.w(TAG, "The device type is different from "+SinovoBle.getInstance().getLockTypeForAdd()+",ignore");
                    }
                }
            }else {     //非绑定模式下，对比mac地址即可
                deviceIn = false;   //默认不符合加入
                for (int i=0; i<SinovoBle.getInstance().getAutoConnectList().size(); i++){
                    BleConnectLock myConnectLock = SinovoBle.getInstance().getAutoConnectList().get(i);
                    if (myConnectLock.getLockMac().equals(scanLockMac)){
                        Log.d(TAG,"该设备是需要自动连接的设备："+ scanLockMac);
                        deviceIn = true;
                        break;
                    }
                }
            }

            if (deviceIn || (SinovoBle.getInstance().isBindMode() &&SinovoBle.getInstance().getLockTypeForAdd().length() <1)) {

                SinovoBle.getInstance().getScanLockList().add(new BleScanDevice(scanLock, mRssi, manufacturerData, getNowTime()));
                Log.i(TAG, "Get a new lock:" + scanLockMac + " time:" + getNowTime());
            }
        }

        //过滤 ，删除掉时间早于 10s之前的锁
        for (int i = 0; i < SinovoBle.getInstance().getScanLockList().size(); ){
            BleScanDevice bleScanDevice = SinovoBle.getInstance().getScanLockList().get(i);
            if (calTimeDiff(bleScanDevice.getJoinTime(),getNowTime())>10){
                SinovoBle.getInstance().getScanLockList().remove(i);
            }else {
                i++;
            }
        }

        return new BleScanDevice(scanLock, mRssi, manufacturerData, getNowTime());
    }


    //停止蓝牙扫描
    //参数 ，是否立即停止（自动扫描的话，停止之后会判断 是否还需要重新扫描）
    public void stopScan() {
        Log.d(TAG, "stopscanBle 函数，停止扫描  stopScan");
        setScanning(false);
        SinovoBle.getInstance().removeHandlerMsg();

        if (SinovoBle.getInstance().getBluetoothAdapter() == null) {
            Log.d(TAG, "蓝牙 mBleAdapter 为null");
            return;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()) {
            Log.d(TAG, "蓝牙 未启动");
            return ;
        }

        SinovoBle.getInstance().getBluetoothAdapter().getBluetoothLeScanner().stopScan(instance);
        if (SinovoBle.getInstance().isScanAgain() ) {
            Log.d(TAG, "继续开始扫描");
            if (BleConfig.getInstance().getScanTimeout() == -1) {
                SinovoBle.getInstance().bleScan(iScanCallBack);
            }
        }
    }
}
