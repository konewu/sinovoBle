package com.sinovotec.sinovoble.common;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sinovotec.sinovoble.SinovoBle;

import java.util.Objects;

public class BluetoothListenerReceiver extends BroadcastReceiver {
    private String TAG = "SinovoBle";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(Objects.requireNonNull(intent.getAction()))) {
            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            switch (blueState) {
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.e(TAG, "onReceive---------蓝牙正在打开中");

                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.e(TAG, "onReceive---------蓝牙已经打开");
                    if (SinovoBle.getInstance().getmConnCallBack()!= null) {
                        SinovoBle.getInstance().setConnected(false);
                        SinovoBle.getInstance().setConnectting(false);
                        SinovoBle.getInstance().getmConnCallBack().onBluetoothOn();
                    }
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.e(TAG, "onReceive---------蓝牙正在关闭中");

                    break;
                case BluetoothAdapter.STATE_OFF:
                    Log.e(TAG, "onReceive---------蓝牙已经关闭");
                    if (SinovoBle.getInstance().getmConnCallBack()!= null) {
                        SinovoBle.getInstance().setConnected(false);
                        SinovoBle.getInstance().setConnectting(false);
                        SinovoBle.getInstance().getmConnCallBack().onBluetoothOff();
                    }

                    break;
            }
        }
    }
}
