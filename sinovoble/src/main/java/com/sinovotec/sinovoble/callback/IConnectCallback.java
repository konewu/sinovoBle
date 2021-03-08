package com.sinovotec.sinovoble.callback;

/**
 * @Description: 连接设备回调
 */
public interface IConnectCallback {
    //连接成功
    void onConnectSuccess(String macAddress);

    //连接失败
    void onConnectFailure();

    //关闭手机蓝牙时的返回
    void onBluetoothOff();

    //打开手机蓝牙时的返回
    void onBluetoothOn();

    //连接断开
    void onDisconnect(String macaddress);

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

    //设置锁的属性，锁名、绑定后自动创建用户、自动锁门时间、静音设置、超级用户权限设置
    void onSetLockInfo(String result);

    //开关门
    void onUnlock(String result);

    //清空用户、密码、卡、指纹、恢复出厂设置、绑定列表
    void onCleanData(String result);

    //查询锁端的信息,电量、锁状态、锁的型号和固件版本、查询锁的mac地址、查询指定数据的最新修改时间、管理员是否存在
    void onRequestLockInfo(String result);

    //查询数据，同步用户、绑定列表
    void onRequestData(String result);

    //查询日志
    void onRequestLog(String result);

    //启用禁用 动态密码的返回结果
    void onDynamicCodeStatus(String result);

    //授权新用户的返回结果
    void onAuthorOther(String result);

    //锁锁死的返回
    void onLockFrozen(String result);

    //发送数据没有回应
    void onReceiveDataFailed();

}
