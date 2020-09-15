package com.sinovotec.sinovoble.callback;

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

    //关闭手机蓝牙时的返回
    void onBluetoothOff();

    //打开手机蓝牙时的返回
    void onBluetoothOn();

    //连接断开
    void onDisconnect();

    //添加锁
    void onAddLock(String result);

    //创建用户,默认创建的是普通用户
    void onCreateUser(String result);

    //更新用户
    void onUpdateUser(String result);

    //增加数据
    void onAddData(String result);

    //删除数据
    void onDelData(String result);

    //校验密码是否正确
    void onVerifyCode(String result);

    //设置查询锁的属性，锁名、绑定后自动创建用户、自动锁门时间、静音设置、超级用户权限设置
    void onSetLockInfo(String result);

    //开门
    void onUnlock(String result);

    //清空用户、密码、卡、指纹、恢复出厂设置、绑定列表
    void onCleanData(String result);

    //查询锁端的信息
    void onRequestLockInfo(String result);

//    //查询电量
//    void onRequestPower(String result);
//
//    //查询锁状态
//    void onRequestLock(String result);
//
//    //查询锁的版本
//    void onRequestVersion(String result);
//
//    //查询管理员
//    void onRequestAdmin(String result);

    //查询数据，同步用户、绑定列表
    void onRequestData(String result);

    //查询日志
    void onRequestLog(String result);


}
