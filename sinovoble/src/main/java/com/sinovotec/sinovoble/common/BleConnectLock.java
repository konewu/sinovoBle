package com.sinovotec.sinovoble.common;

public class BleConnectLock {
    private String lockMac;        //蓝牙锁的mac地址
    private String lockSno;        //蓝牙锁通信用的 sno地址

    public BleConnectLock(String lockMac, String lockSno) {
        this.lockMac         = lockMac;
        this.lockSno         = lockSno;
    }

    public String getLockMac() {
        return lockMac;
    }

    public String getLockSno() {
        return lockSno;
    }

    public void setLockMac(String lockMac) {
        this.lockMac = lockMac;
    }

    public void setLockSno(String lockSno) {
        this.lockSno = lockSno;
    }
}
