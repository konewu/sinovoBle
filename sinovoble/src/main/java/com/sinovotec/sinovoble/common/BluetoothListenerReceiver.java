package com.sinovotec.sinovoble.common;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.callback.BleConnCallBack;
import java.util.Objects;

/**
 *  用于监听手机的蓝牙开启与关闭的
 */

public class BluetoothListenerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(Objects.requireNonNull(intent.getAction()))) {
            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            String TAG = "SinovoBle";
            switch (blueState) {
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.e(TAG, "onReceive---------Bluetooth is turning on");
                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.e(TAG, "onReceive---------Bluetooth is on");
                    if (SinovoBle.getInstance().getLockGWid().isEmpty()) {
                        Log.e(TAG, "Gateway's id is null. it's connect via bluetooth");
                        BleConnCallBack.getInstance().setmBluetoothGatt(null);
                        if (SinovoBle.getInstance().getmConnCallBack() != null) {
                            SinovoBle.getInstance().setConnected(false);
                            SinovoBle.getInstance().setLinked(false);
                            SinovoBle.getInstance().getmConnCallBack().onBluetoothOn();
                        }
                    }else {
                        Log.e(TAG, "Gateway's id is not null. it's connect via gateway");
                    }
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.e(TAG, "onReceive---------Bluetooth is turning off");
                    if (SinovoBle.getInstance().isBleConnected()){
                        BleConnCallBack.getInstance().releaseBle();
                        if (SinovoBle.getInstance().getmConnCallBack()!= null) {
                            SinovoBle.getInstance().setConnected(false);
                            SinovoBle.getInstance().setLinked(false);
                            SinovoBle.getInstance().getmConnCallBack().onBluetoothOff();
                        }
//                    }else {
//                        Log.e(TAG, "蓝牙未连接，关闭蓝牙不受影响");
                    }

                    break;
                case BluetoothAdapter.STATE_OFF:
                    Log.e(TAG, "onReceive---------Bluetooth is off");
                    BleConnCallBack.getInstance().releaseBle();
                    break;
            }
        }
    }
}
