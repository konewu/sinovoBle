package com.sinovotec.udpsocket;

import android.annotation.SuppressLint;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.mqtt.iotMqttCallback;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPSocket {
    private static final String TAG = "Sinovoble";
    private static final int POOL_SIZE = 5;   // 单个CPU线程池大小
    private static final int BUFFER_LENGTH = 1024;
    private final byte[] receiveByte = new byte[BUFFER_LENGTH];
    private static final String BROADCAST_IP = "255.255.255.255";

    // 端口号8080
    private static final int CLIENT_PORT = 0x1F90;
    private static final int LOCAL_PORT = 0x1F95;
    private boolean isThreadRunning = false;

    private DatagramSocket client;
    private DatagramPacket receivePacket;
    private final ExecutorService mThreadPool;
    private Thread clientThread;

    private final iotMqttCallback iotMqttCallback;      //mqtt 通信结果回调

    @SuppressLint("StaticFieldLeak")
    private static UDPSocket instance;

    private UDPSocket(iotMqttCallback callback) {
        int cpuNumbers = Runtime.getRuntime().availableProcessors();
        mThreadPool = Executors.newFixedThreadPool(cpuNumbers * POOL_SIZE);        // 根据CPU数目初始化线程池
        this.iotMqttCallback = callback;
    }

    public static UDPSocket getInstance(iotMqttCallback callback) {
        if (instance == null) {
            synchronized (UDPSocket.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new UDPSocket(callback);
                }
            }
        }
        return instance;
    }

    public void startUDPSocket(JSONObject jsonData) {
        if (client == null){
            try {
                client = new DatagramSocket(LOCAL_PORT);       // 表明这个 Socket 在设置的端口上监听数据。
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        if (receivePacket == null) {
            receivePacket = new DatagramPacket(receiveByte, BUFFER_LENGTH);
        }
        startSocketThread(jsonData);
    }

    /**
     * 开启发送数据的线程
     */
    private void startSocketThread(JSONObject jsonData) {
        if (clientThread == null) {
            clientThread = new Thread(() -> {
                Log.d(TAG, "clientThread is running...");
                receiveMessage(jsonData.toString());
            });

            isThreadRunning = true;
            clientThread.start();
        }

        //if (cmdList.size() == 0) {
            Log.w(TAG, " 发送广播包数据：" + jsonData.toString());
            sendMessage(jsonData.toString());
       // }
    }

    /**
     * 处理接受到的消息
     */
    private void receiveMessage(String sendData) {
        while (isThreadRunning) {
        Log.d(TAG, "监听接收广播数据");
            try {
                if (client != null) {
                    client.receive(receivePacket);
                }
                Log.d(TAG, "receive packet success...");
            } catch (IOException e) {
                Log.e(TAG, "UDP数据包接收失败！线程停止");
                stopUDPSocket();
                iotMqttCallback.onUdpSendFailed(sendData);
                e.printStackTrace();
                return;
            }

            if (receivePacket == null || receivePacket.getLength() == 0) {
                Log.e(TAG, "无法接收UDP数据或者接收到的UDP数据为空");
                continue;
            }

            String strReceive = new String(receivePacket.getData(), 0, receivePacket.getLength());
            strReceive = strReceive.replaceAll("\\p{C}", "");   //去掉不可见的字符
            Log.w(TAG, strReceive + " from " + receivePacket.getAddress().getHostAddress() + ":" + receivePacket.getPort());
            iotMqttCallback.onUdpReceiveMsg(strReceive);

            // 每次接收完UDP数据后，重置长度。否则可能会导致下次收到数据包被截断。
            if (receivePacket != null) {
                receivePacket.setLength(BUFFER_LENGTH);
            }
        }
    }

    public void stopUDPSocket() {
        isThreadRunning = false;
        receivePacket = null;
        if (clientThread != null) {
            clientThread.interrupt();
        }
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /**
     * 发送心跳包
     *
     * @param message msg
     */
    private void sendMessage(final String message) {
        mThreadPool.execute(() -> {
            try {
                InetAddress targetAddress = InetAddress.getByName(BROADCAST_IP);
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), targetAddress, CLIENT_PORT);
                client.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public  boolean isJson(String content) {
        if(content.isEmpty()){
            return false;
        }
        boolean isJsonObject = true;
        boolean isJsonArray = true;
        try {
            com.alibaba.fastjson.JSONObject.parseObject(content);
        } catch (Exception e) {
            isJsonObject = false;
        }
        try {
            com.alibaba.fastjson.JSONObject.parseArray(content);
        } catch (Exception e) {
            isJsonArray = false;
        }
        if(!isJsonObject && !isJsonArray){ //不是json格式
            return false;
        }
        return true;
    }
}
