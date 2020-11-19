package com.sinovotec.sinovobledemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.callback.IConnectCallback;
import com.sinovotec.sinovoble.callback.IScanCallBack;
import com.sinovotec.sinovoble.common.BleConnectLock;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private TextView  reslut_tv;
    /**
     * 扫描回调
     */
    private IScanCallBack mBleScanCallBack = new IScanCallBack() {
        @Override
        public void onDeviceFound(String scanResult) {
            Log.w("xxx", "onDeviceFound:" + scanResult);
            reslut_tv.setText(scanResult);
        }

        @Override
        public void onScanTimeout(String scanResult) {
            Log.w("xxx", "scan timeout");
            reslut_tv.setText(scanResult);
        }
    };

    /**
     * 连接回调
     */
    private IConnectCallback mConnCallBack = new IConnectCallback() {

        @Override
        public void onConnectSuccess(String macAddress) {

        }

        @Override
        public void onConnectFailure() {
            Log.i("xxx","onConnectFailure ");
        }

        @Override
        public void onBluetoothOff() {
            Log.e("xxx","手机蓝牙被关闭了");
        }


        @Override
        public void onBluetoothOn() {
            Log.w("xxx","手机蓝牙打开了");
        }

        @Override
        public void onDisconnect() {
            Log.i("xxx","连接回调 IConnectCallback 连接断开");

        }

        @Override
        public void onAddLock(String result) {
            Log.i("xxx","addLock successful：" + result);
            reslut_tv.setText(result);
        }

        @Override
        public void onCreateUser(String result) {

        }

        @Override
        public void onUpdateUser(String result) {

        }

        @Override
        public void onAddData(String result) {

        }

        @Override
        public void onDelData(String result) {

        }

        @Override
        public void onVerifyCode(String result) {

        }

        @Override
        public void onSetLockInfo(String result) {

        }


        @Override
        public void onUnlock(String result) {

        }

        @Override
        public void onCleanData(String result) {

        }

        @Override
        public void onRequestLockInfo(String result) {

        }

        @Override
        public void onRequestData(String result) {

        }

        @Override
        public void onRequestLog(String result) {

        }

        @Override
        public void onDynamicCodeStatus(String result) {

        }

        @Override
        public void onAuthorOther(String result) {

        }

        @Override
        public void onReceiveDataFailed() {

        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button newbtn = findViewById(R.id.button1);
        Button adduserbtn = findViewById(R.id.btn_adduser);
        Button chkp = findViewById(R.id.btn_chkpower);

        final EditText myet = findViewById(R.id.lockid_et);
        final EditText usernameet = findViewById(R.id.username_et);
        reslut_tv = findViewById(R.id.result_tv);

        newbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (myet.getText().length() !=12){
                    Toast.makeText(getApplicationContext(),"LOCKID can only be a 12 bit number",Toast.LENGTH_LONG).show();
                }else {
                    SinovoBle.getInstance().addLock(myet.getText().toString(),"ac1234ed5b8c");
                }
            }
        });

        adduserbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BleConnectLock mylock = new BleConnectLock("00:A0:51:F4:E1:85","37120a");
                ArrayList<BleConnectLock> mylist = new ArrayList<>();
                mylist.add(mylock);

                SinovoBle.getInstance().autoConnectLock(mylist);
            }
        });

        chkp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SinovoBle.getInstance().requestLockInfo("02");
            }
        });

        //初始化蓝牙
        SinovoBle.getInstance().init(this.getApplicationContext(),mBleScanCallBack, mConnCallBack);
    }
}
