package com.sinovotec.mqtt;

public interface iotMqttCallback {
    void initFailed();              //初始化失败
    void onConnectionLost();        //连接丢失
    void onMsgArrived(String topic, String msg);
    void onDeliveryComplete();
    void onConnectSuccess();        //连接成功
    void onConnectFailed();         //连接失败
    void onSubscribeSuccess();      //发表主题成功
    void onSubscribeFailed();       //发表主题失败
    void onPublishSuccess();        //发表主题失败
    void onPublishFailed();         //发表主题失败
    void onReceiveMQTTTimeout();    //响应超时失败，发送命令
    void onReceiveBLETimeout();     //通过mqtt 发送蓝牙数据后的超时

    //使用 UDP 发送数据时的回调
    void onUdpReceiveMsg(String msg);
    void onUdpSendFailed(String msg);

    //使用 TCP 发送数据时的回调
    void onTcpReceiveMsg(String msg);
    void onTcpSendFailed(String msg);
}
