package com.sinovotec.sinovoble.callback;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: 连接设备回调
 */
public interface IConnectCallback {
    //连接成功
    void onConnectSuccess();

    //数据返回
    void onValueReturn();

    //连接失败
    void onConnectFailure();

    //连接断开
    void onDisconnect(boolean isActive);


    //添加锁
    void onAddLock(JSONObject jsonObject);

    //查询或是修改锁的名称
    void onSetLockName(JSONObject jsonObject);

    //查询或是修改锁的时间
    void onSetLockTime(JSONObject jsonObject);

}
