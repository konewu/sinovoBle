package com.sinovotec.sinovobledemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.callback.IConnectCallback;
import com.sinovotec.sinovoble.callback.IScanCallBack;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    /**
     * 扫描回调
     */
    private IScanCallBack mBleScanCallBack = new IScanCallBack() {
        @Override
        public void onDeviceFound(String scanResult) {
            Log.w("xxx", "onDeviceFound:" + scanResult);
            SinovoBle.getInstance().connectLock(mConnCallBack);
        }

        @Override
        public void onScanTimeout() {
            Log.w("xxx", "scan timeout");
        }
    };

    /**
     * 连接回调
     */
    private IConnectCallback mConnCallBack = new IConnectCallback() {
        @Override
        public void onConnectSuccess() {
            Log.w("xxx","连接回调 IConnectCallback 连接成功");
        }

        @Override
        public void onValueReturn() {

        }

        @Override
        public void onConnectFailure() {

        }

        @Override
        public void onDisconnect(boolean isActive) {
            Log.i("xxx","连接回调 IConnectCallback 连接断开");

        }

        @Override
        public void onAddLock(com.alibaba.fastjson.JSONObject jsonObject) {
            Log.i("xxx","添加锁成功，结果是：" + jsonObject);
        }

        @Override
        public void onSetLockName(com.alibaba.fastjson.JSONObject jsonObject) {
            Log.i("xxx","修改锁名，结果是：" + jsonObject);

        }

        @Override
        public void onSetLockTime(com.alibaba.fastjson.JSONObject jsonObject) {
            Log.i("xxx","修改时间，结果是：" + jsonObject);

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button newbtn = findViewById(R.id.button1);
        newbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SinovoBle.getInstance().setBindMode(true);
                SinovoBle.getInstance().setLockID("123456789099");
                SinovoBle.getInstance().setPhoneIMEI("3ea2c312780d");
                SinovoBle.getInstance().setLockTypeForAdd("F67,F81");
                SinovoBle.getInstance().startBleScan(mBleScanCallBack);
            }
        });

        //初始化蓝牙
        SinovoBle.getInstance().init(this.getApplicationContext());
    }
}
