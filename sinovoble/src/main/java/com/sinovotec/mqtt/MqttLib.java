package com.sinovotec.mqtt;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.common.BleData;
import com.sinovotec.sinovoble.common.ComTool;
import com.sinovotec.tcpsocket.TcpSocket;
import com.sinovotec.udpsocket.UDPSocket;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MqttLib {

    private static final String TAG = "SinovoBle";
    private static MqttLib instance;
    private iotMqttCallback iotMqttCallback;      //mqtt 通信结果回调

    private String PUB_TOPIC = "";    /* 自动Topic, 用于上报消息 */
    private String SUB_TOPIC = "";    /* 自动Topic, 用于接受消息 */

    private MqttAndroidClient mqttAndroidClient;
    private boolean isMqttOK = false;

    private final Handler mqttSubscribeHandler = new Handler(Looper.getMainLooper());        //超时句柄， 发布主题的超时句柄，默认为5s
    private final Handler mqttSendHandler = new Handler(Looper.getMainLooper());        //超时句柄， app发送命令到网关上的超时，默认为 10s
    private final Handler bleSendHandler  = new Handler(Looper.getMainLooper());        //超时句柄， app发送命令经过网关到锁上的超时，默认为 20s

    public static MqttLib getInstance() {
        if (instance == null) {
            synchronized (MqttLib.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new MqttLib();
                }
            }
        }
        return instance;
    }

    public boolean isMqttOK() {
        return isMqttOK;
    }

    public void setMqttOK(boolean mqttOK) {
        isMqttOK = mqttOK;
    }

    /**
     * 初始化MQTT ，采用的 一机一密方式
     * @param context    上下文对象
     * @param proKey     produceKey
     * @param deName     DeviceName
     * @param deSecret   DeviceSecret
     * @param region     DeviceRegion
     */
    public void init(Context context, String proKey, String deName, String deSecret, String region, iotMqttCallback callback){

        Log.d(TAG,"Initialize MQTT");   //采用一机一密方式
        this.iotMqttCallback = callback;
        this.PUB_TOPIC = "/" + proKey + "/" + deName + "/user/update";
        this.SUB_TOPIC = "/" + proKey + "/" + deName + "/user/get";
        String host = "tcp://" + proKey + ".iot-as-mqtt."+region+".aliyuncs.com:443";
        String clientId = "";
        String userName = "";
        String passWord = "";

        /* 获取Mqtt建连信息clientId, username, password */
        AiotMqttOption aiotMqttOption = new AiotMqttOption().getMqttOption(proKey, deName, deSecret);
        if (aiotMqttOption == null) {
            iotMqttCallback.initFailed();
            Log.e(TAG, "device info error");
        } else {
            clientId = aiotMqttOption.getClientId();
            userName = aiotMqttOption.getUsername();
            passWord = aiotMqttOption.getPassword();
        }

        /* 创建MqttConnectOptions对象并配置username和password */
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setUserName(userName);
        mqttConnectOptions.setPassword(passWord.toCharArray());
        mqttConnectOptions.setAutomaticReconnect(false);  //设置断开自动重连
        mqttConnectOptions.setKeepAliveInterval(120);

        /* 创建MqttAndroidClient对象, 并设置回调接口 */
        mqttAndroidClient = new MqttAndroidClient(context, host, clientId);
        mqttAndroidClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.i(TAG, "connection lost--->" + cause);
                setMqttOK(false);
                iotMqttCallback.onConnectionLost();
                mqttSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                bleSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                mqttSubscribeHandler.removeCallbacksAndMessages(null);    //取消定时任务

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String msg = new String(message.getPayload());
//                Log.i(TAG, "topic: " + topic + ", msg: " + msg);
                if (msg.contains("bledata")){
                    bleSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                }else {
                    mqttSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                }
                iotMqttCallback.onMsgArrived(topic,msg);

                Map maps = (Map) JSON.parse(msg);
                if (maps.containsKey("type")){
                    String type = Objects.requireNonNull(maps.get("type")).toString();
                    if (type.equals("bledata")){
                        if (!BleData.getInstance().getCommandList().isEmpty()) {
                            Log.d(TAG,"receive data from mqtt，delete the first command in list");
                            BleData.getInstance().getCommandList().removeFirst();
                        }
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.i(TAG, "msg delivered");
                iotMqttCallback.onDeliveryComplete();
            }
        });

        /* Mqtt建连 */
        try {
            mqttAndroidClient.connect(mqttConnectOptions,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.w(TAG, "connect mqtt succeed，紧接着需要订阅主题");
                    iotMqttCallback.onConnectSuccess();
                    subscribeTopic(SUB_TOPIC);
                    mqttSubscribeHandler.postDelayed(() -> checkDataReceive("subscribeTopic"), 5*1000);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "connect mqtt failed ,error info:" + exception);
                    setMqttOK(false);
                    iotMqttCallback.onConnectFailed();
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    /**
     * 订阅特定的主题
     * @param topic mqtt主题
     */
    private void subscribeTopic(String topic) {
        try {
            mqttAndroidClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "subscribed succeed");
                    setMqttOK(true);
                    mqttSubscribeHandler.removeCallbacksAndMessages(null);    //取消定时任务
                    iotMqttCallback.onSubscribeSuccess();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "subscribed failed");
                    setMqttOK(false);
                    mqttSubscribeHandler.removeCallbacksAndMessages(null);    //取消定时任务
                    iotMqttCallback.onSubscribeFailed();
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向默认的主题/user/update发布消息
     * @param payload 消息载荷
     */
    private void publishMessage(String payload) {
        try {
            if (mqttAndroidClient == null){
                Log.d(TAG, "publishMessage failed, mqttAndroidClient is null");
                return;
            }

            if (!mqttAndroidClient.isConnected()) {
                mqttAndroidClient.connect();
            }

            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes());
            message.setQos(0);
            mqttAndroidClient.publish(PUB_TOPIC, message,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "publish succeed!，data："+ payload);
                    iotMqttCallback.onPublishSuccess();

                    //延迟30秒后再检测 是否已经收到锁端的恢复
                    if (payload.contains("send2lock")){
                        bleSendHandler.postDelayed(() -> checkDataReceive("send2lock"), 20*1000);
                    }else {
                        mqttSendHandler.postDelayed(() -> checkDataReceive("send2GW"), 10*1000);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "publish failed! ");
                    iotMqttCallback.onPublishFailed();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    //注销mqtt ，切换账户 需要注销
    public void logoutMQTT(){
        if (mqttAndroidClient == null){
            Log.e(TAG,"logoutMQTT mqttAndroidClient is null, exit");
            return;
        }
        try {
            mqttAndroidClient.disconnect();
            mqttAndroidClient.unregisterResources();
        } catch (MqttException e) {
            e.printStackTrace();
        }
        mqttAndroidClient.setCallback(null);
        mqttAndroidClient = null;
        Log.e(TAG,"logoutMQTT disconnect mqtt，exit");

        //注销蓝牙连接
        SinovoBle.getInstance().setScanAgain(false);
        SinovoBle.getInstance().getToConnectLockList().clear();
        if (SinovoBle.getInstance().isBleConnected()){
            SinovoBle.getInstance().disconnBle();
        }

        //设置mqtt为 还没注册
        setMqttOK(false);
    }


    /**
     * MQTT建连选项类，输入设备三元组productKey, deviceName和deviceSecret, 生成Mqtt建连参数clientId，username和password.
     */
    static class AiotMqttOption {
        private String username = "";
        private String password = "";
        private String clientId = "";

        public String getUsername() { return this.username;}
        public String getPassword() { return this.password;}
        public String getClientId() { return this.clientId;}

        /**
         * 获取Mqtt建连选项对象
         * @param productKey 产品秘钥
         * @param deviceName 设备名称
         * @param deviceSecret 设备机密
         * @return AiotMqttOption对象或者NULL
         */
        public AiotMqttOption getMqttOption(String productKey, String deviceName, String deviceSecret) {
            if (productKey == null || deviceName == null || deviceSecret == null) {
                return null;
            }

            try {
                String timestamp = Long.toString(System.currentTimeMillis());

                // clientId
                this.clientId = productKey + "." + deviceName + "|timestamp=" + timestamp +
                        ",_v=paho-android-1.0.0,securemode=2,signmethod=hmacsha256|";

                // userName
                this.username = deviceName + "&" + productKey;

                // password
                String macSrc = "clientId" + productKey + "." + deviceName + "deviceName" +
                        deviceName + "productKey" + productKey + "timestamp" + timestamp;
                String algorithm = "HmacSHA256";
                Mac mac = Mac.getInstance(algorithm);
                SecretKeySpec secretKeySpec = new SecretKeySpec(deviceSecret.getBytes(), algorithm);
                mac.init(secretKeySpec);
                byte[] macRes = mac.doFinal(macSrc.getBytes());
                password = String.format("%064x", new BigInteger(1, macRes));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            return this;
        }
    }

    public void disconnectLock(String gatewayid, ArrayList<String> macList){
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("gateway_id", gatewayid);
        map.put("type", "disconnectLock");
        map.put("mac", macList);   //mac地址要大写
        JSONObject json = new JSONObject(map);
        Log.d(TAG, "Disconnect the Bluetooth connection between the gateway and the lock: "+ json.toString());
        publishMessage(json.toString());
    }

    /**
     * 通过mqtt 发送命令
     * @param gatewayid 网关id
     * @param type  发送的数据类型
     * @param uuid  锁的UUID
     * @param mac   锁的mac地址
     * @param data  发送的数据
     */
    public void getDataToSend(final String gatewayid, final String gwIP, final String type, final String uuid,
                              final String mac, final String data, final boolean sendByMQTT){
        SinovoBle.getInstance().setLockGWid(gatewayid);   //使用网关通信时，需要设置网关id
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("gateway_id", gatewayid);
        map.put("type", type);
        map.put("uuid", uuid);
        map.put("mac", mac.toUpperCase());      //mac地址要大写
        map.put("data", data);
        JSONObject json = new JSONObject(map);

        if (isMqttOK){
            if (sendByMQTT || gwIP.isEmpty()) {
                Log.d(TAG, "send command via mqtt:" + json.toString());
                publishMessage(json.toString());
            }else {
                Log.d(TAG, "send command via TCP:" + json.toString());
               // UDPSocket.getInstance(iotMqttCallback).startUDPSocket(json);
                TcpSocket.getInstance(iotMqttCallback).sendData(gwIP, 8080, json.toString());
            }
        }else{
            Log.d(TAG, "MQTT is not ready, it cann't send command:" + json.toString());
            //回调 mqtt 连接丢失 ？？？？
        }
    }

    /**
     * 直接传递的json格式的数据 ， 绑定的时候用的
     * @param jsonData string
     */
    public void getDataToSend(final String jsonData, final String gwIP, boolean sendByMQTT){
        if (isMqttOK){
            if (sendByMQTT) {
                Log.d(TAG, "send command via mqtt: " + jsonData);
                publishMessage(jsonData);
            }else {
                Log.d(TAG, "send command via TCP: " + jsonData);
                JSONObject jsonObject = JSON.parseObject(jsonData);
              //  UDPSocket.getInstance(iotMqttCallback).startUDPSocket(jsonObject);
                TcpSocket.getInstance(iotMqttCallback).sendData(gwIP, 8080, jsonData);
            }
        }else{
            Log.d(TAG, "MQTT is not ready, it cann't send command:" + jsonData);
            //回调 mqtt 连接丢失 ？？？？
        }
    }

    private void checkDataReceive(String dataType){
        if (dataType.equals("send2lock")){
            Log.d(TAG,"The command sent to lock over mqtt has timed out, 20s");
            iotMqttCallback.onReceiveBLETimeout();
        }

        if (dataType.equals("send2GW")){
            Log.d(TAG,"The command sent to gateway over mqtt has timed out, 10s");
            iotMqttCallback.onReceiveMQTTTimeout();
        }

        if (dataType.equals("subscribeTopic") && !isMqttOK()){
            Log.d(TAG,"subscribeTopic timed out, 5s");
            iotMqttCallback.onSubscribeFailed();
        }
    }

    /**
     * 创建用户,默认创建的是普通用户
     * @param userName string
     */
    public void addUser(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String userName, boolean sendByMQTT){
        if (userName.isEmpty() || userName.length()>10){
            return;
        }

        String data = sno + ComTool.stringToAscii(userName);
        String datasend = mqttCommand("02", data,mac);
      //  Log.d(TAG,"调用 addUser 来添加用户:"+ datasend);
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend,sendByMQTT);
    }

    /**
     * 编辑用户，修改用户名
     * @param updateData 用户名
     * @param userNID   用户的nid
     */
    public void updateUser(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String updateData, String userNID,boolean sendByMQTT){
        if (userNID.isEmpty() || updateData.length()>10 ){
            Log.e(TAG,"Parameter error");
            return;
        }

        String username = "";
        if (!updateData.isEmpty()){
            username = ComTool.stringToAscii(updateData);
        }

        String updateStr = sno +userNID + username;
        String datasend = mqttCommand("03", updateStr, mac);
      //  Log.d(TAG,"调用 addUser 来添加用户:"+ datasend);
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);

    }

    /**
     * 为用户添加一组数据,密码、卡、指纹
     * @param userNID       用户nid
     * @param dataType      数据类型， 02 普通密码，03超级用户密码，06是卡，07是指纹，08是防胁迫指纹
     * @param password      添加密码时具体的密码内容， 如果是添加卡/指纹时，留空即可
     */
    public void addDataForUser(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String userNID, String dataType, String password,boolean sendByMQTT){
        if (userNID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }
        String datasend = "";

        //添加密码时
        if (dataType.equals("02") || dataType.equals("03") || dataType.equals("05")){
            if (password.isEmpty()){
                Log.e(TAG,"Parameter error");
                return;
            }
            String data = sno +userNID + dataType + password;
            datasend = mqttCommand("05", data, mac);
         //   Log.d(TAG,"调用 addUser 来添加用户:"+ datasend);
        }

        //添加卡、指纹、防胁迫指纹
        if (dataType.equals("06") || dataType.equals("07") || dataType.equals("08")){
            String data = sno +userNID + dataType ;
            datasend = mqttCommand("05", data, mac);
        }

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    /**
     * 删除某一项数据，删除一组密码、卡、指纹、绑定
     * @param dataType s
     * @param delID s
     */
    public void delData(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String dataType, String delID,boolean sendByMQTT){
        if (delID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        String datasend ;

        if (!dataType.equals("0e")){
            String data = sno +dataType + delID ;
            datasend = mqttCommand("06", data, mac);
        }else {
            //删除绑定
            String data = sno + delID ;
            datasend = mqttCommand("1b", data, mac);
        }
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);

    }

    /**
     * 修改用户的密码
     * @param userNid  用户的id
     * @param codeType 密码的类型
     * @param codeID   密码的ID
     * @param newCode  新的密码
     */
    public void resetPassword(String gatewayid, String gwip, String type, String uuid, String mac, String sno,
                              String userNid, String codeType, String codeID, String newCode, boolean sendByMQTT){
        if (userNid.isEmpty() || codeType.isEmpty() || codeID.isEmpty() || newCode.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }
        String data = sno + userNid + codeType + codeID + newCode;
        String datasend = mqttCommand("0d", data, mac);
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }


    /**
     * 设置锁的相关属性
     * @param dataType 设置类型
     *                 01 设置锁名称，锁名称不能超过10个字符，如果为空，则表示查询锁端的时间
     *                 02 锁的时间，时间格式YYMMDDHHMMSS ，如果为空，则表示查询锁端的时间
     *                 03 自动锁门时间 ，范围 0-240 ；0表示关闭自动锁门
     *                 04 设置静音模式，其值 00关闭静音，01为开启静音，如果为空，则表示查询当前静音状态
     *                 05 设置绑定成功后，是否自动创建用户，如果为空，则表示查询当前设置
     *                 06 设置超级用户的权限 其值如下：
     *                    具有管理用户权限时，值为01，03，05，07，09，11，13，15
     *                    具有分享密码权限时，值为02，03，06，07，10，11，14，15
     *                    具有对锁设置权限时，值为04，05，06，07，12，13，14，15
     *                    具有查看日志权限时，值为08，09，10，11，12，13，14，15
     * @param data s
     */
    public void setLock(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String dataType, String data,boolean sendByMQTT){

        String datasend = "";
        //设置锁的名称
        if (dataType.equals("01")){
            if (data.length() >10 || data.length() == 0){
                Log.e(TAG,"Parameter error");
                return;
            }

            String locknameAscii = ComTool.stringToAscii(data);
            String setData = sno + locknameAscii;
            datasend = mqttCommand("11", setData, mac);
        }

        //设置锁的时间
        if (dataType.equals("02")){
            String setData = sno + data;
            datasend = mqttCommand("10", setData, mac);
        }

        //设置锁的自动锁门时间
        if (dataType.equals("03")){
            String sixteen = "";
            if (!data.isEmpty()){
                if(Integer.parseInt(data) <0 || Integer.parseInt(data)>240){
                    Log.e(TAG,"Parameter error");
                    return;
                }

                sixteen = Integer.toHexString(Integer.parseInt(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
            }

            String setData = sno + sixteen;
            datasend = mqttCommand("16", setData, mac);
        }

        //设置静音 和绑定后自动创建用户
        if (dataType.equals("04") || dataType.equals("05")){
            if (data.isEmpty()){
                data = "02";
            }
            if (Integer.parseInt(data)<0 || Integer.parseInt(data)>2){
                Log.e(TAG,"Parameter error");
                return;
            }

            String setData = sno + data;
            if (dataType.equals("04")){
                datasend = mqttCommand("1c", setData, mac);
            }else {
                datasend = mqttCommand("09", setData, mac);
            }
        }

        //设置超级用户的权限
        if (dataType.equals("06")){
            String setData = sno + data;
            datasend = mqttCommand("23", setData, mac);
        }
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }


    /**
     * 查询锁端的信息
     * @param dataType 查询的类型
     *                 01 查询管理员信息
     *                 02 查询锁的电量信息
     *                 03 查询锁的当前状态
     *                 04 查询锁的固件版本信息
     */
    public void requestLockInfo(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String dataType, boolean sendByMQTT){
        String datasend = "";

        if (dataType.equals("01")){
            datasend = mqttCommand("12", sno, mac);
        }

        if (dataType.equals("02")){
            datasend = mqttCommand("0e", sno, mac);
        }

        if (dataType.equals("03")){
            datasend = mqttCommand("0f", sno, mac);
        }

        if (dataType.equals("04")){
            datasend = mqttCommand("1a", sno, mac);
        }

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);

    }

    /**
     * 同步数据，包括用户信息
     */
    public void getAllUsers(String gatewayid, String gwip, String type, String uuid, String mac, String sno, boolean sendByMQTT){
        String data = sno +"00";
        String  datasend = mqttCommand("13", data, mac);
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    /**
     * 同步数据，包括用户信息 和绑定的手机
     */
    public void getAllBoundPhone(String gatewayid, String gwip, String type, String uuid, String mac, String sno, boolean sendByMQTT){
        String data = sno +"0e";
        String datasend = mqttCommand("13", data, mac);
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    /**
     * 同步日志
     * @param logID  ，表示当前的日志id ,日志量比较大，所以支持从指定的id开始同步，如果 id为 ff ，则同步所有的日志
     */
    public void getLog(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String logID, boolean sendByMQTT){
        if (logID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        String data = sno + logID;
        String datasend = mqttCommand("17", data, mac);

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    /**
     * 启用/禁用 动态密码
     * @param dynamicCode  对应的 动态密码
     * @param enable  00 表示禁用， 01 表示启动
     */
    public void doDynamicCode(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String dynamicCode, String enable, boolean sendByMQTT){
        if (dynamicCode.isEmpty() ||!(enable.equals("00") || enable.equals("01"))){
            Log.e(TAG,"Parameter error");
            return;
        }

        String data = sno + enable + dynamicCode;
        String datasend = mqttCommand("20", data, mac);

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }


    /**
     * 修改密码的属性，改普通密码、超级用户密码
     * @param oldCodeType  该密码原来的 类型 ，02 普通密码，03 超级用户密码
     * @param codeID       密码的id
     * @param newCodeType  新的密码类型 ，02 普通密码，03 超级用户密码; 该字段为空，则表示查询此密码的类型
     */
    public void updateCodeType(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String oldCodeType,
                               String codeID, String newCodeType, boolean sendByMQTT){
        if (oldCodeType.isEmpty() || codeID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        if (oldCodeType.equals(newCodeType)){
            return;
        }

        String datasend = "";
        if (newCodeType.equals("02")){
            String data = sno + oldCodeType + codeID + "00";
            datasend = mqttCommand("07", data, mac);
        }
        if (newCodeType.equals("03")){
            String data = sno + oldCodeType + codeID + "01";
            datasend = mqttCommand("07", data, mac);
        }
        if (newCodeType.isEmpty()){
            String data = sno + oldCodeType + codeID + "02";
            datasend = mqttCommand("07", data, mac);
        }

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);

    }

    /**
     * 校验密码
     * @param password 密码
     */
    public void verifyPassword(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String password, boolean sendByMQTT){
        if (password.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        String data = sno+ password;
        String datasend = mqttCommand("08", data, mac);

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    /**
     * 通知锁端断开蓝牙连接
     */
    public void toDisconnBle(String gatewayid, String gwip, String type, String uuid, String mac, String sno, boolean sendByMQTT){
      //  Log.d(TAG,"调用 toDisconnBle 来断开蓝牙连接");
        String datasend = mqttCommand("1e", sno, mac);
        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    /**
     * 开关门操作
     * @param unlockType 00 表示锁门，01表示开门
     */
    public void toUnlock(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String unlockType, String password, boolean sendByMQTT){
        if (unlockType.isEmpty() || password.isEmpty() || !(unlockType.equals("00") || unlockType.equals("01"))){
            Log.e(TAG,"Parameter error");
            return;
        }
        String data = sno + unlockType + password;
        String datasend = mqttCommand("0a", data, mac);

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    /**
     * 清空数据
     * @param datakType 表示清空数据的类型；
     *                  00 表示清空用户，不会删除管理员
     *                  0e 表示清空所有的绑定手机
     *                  0c 表示恢复出厂设置
     *
     */
    public void cleanData(String gatewayid, String gwip, String type, String uuid, String mac, String sno, String datakType,boolean sendByMQTT){
        if (datakType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        String datasend;

        //清空绑定的手机
        if (datakType.equals("0e")){
            datasend = mqttCommand("1b", sno, mac);
        }else {
            String data = sno + datakType;
            datasend = mqttCommand("0c", data, mac);
        }

        getDataToSend(gatewayid, gwip, type, uuid, mac,datasend, sendByMQTT);
    }

    public String mqttCommand(String funcode, String data,String mac){
        StringBuilder data_send = new StringBuilder(data);
        int byteLen = data.length() /2;
        byteLen += data.length() %2;

        //如果data 不够16字节，则在后面补ff
        for (int i=32;i> data.length(); i--) {
            data_send.append("f");
        }
       // Log.d(TAG, "mqttCommand 需要发送的数据，在补f之后："+data_send + ",长度："+data_send.length());

        //加密处理
       // Log.d(TAG, "准备加密的mac："+mac);
        data_send = new StringBuilder(SinovoBle.getInstance().getMyJniLib().encryptAes(data_send.toString(), mac));

       // Log.d(TAG, "需要发送的数据，加密后："+data_send);

        String data_result = "fe" +funcode ;
        if (byteLen<16){
            data_result = data_result + "0"+ Integer.toHexString(byteLen) + data_send;
        }else {
            data_result = data_result + Integer.toHexString(byteLen) + data_send;
        }
        data_result = data_result + BleData.getInstance().checkSum(data_result);

        Log.d(TAG, "mqttCommand 生成的命令："+ data_result);
        return data_result;
    }

    public void removeBleHandlerCallback(){
        bleSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
    }

    public void removeMqttHandlerCallback(){
        mqttSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
    }
}
