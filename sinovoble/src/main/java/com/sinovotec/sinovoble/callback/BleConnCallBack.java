package com.sinovotec.sinovoble.callback;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.common.BleCommand;
import com.sinovotec.sinovoble.common.BleConstant;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.sinovotec.sinovoble.common.ComTool.byte2hex;


public class BleConnCallBack extends BluetoothGattCallback {
    private static BleConnCallBack instance;                //入口操作管理
    private static String TAG = "BleLib";

    private boolean isConnectting = false;          //是否正在连接
    private String connectingMAC;                   //记录当前正在连接的锁的mac地址 ，用于在连接断开之后进行重连判断；
    private int reconnectCount = 0;                 //记录某一把锁的重连次数
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBleGattService;
    private BluetoothGattCharacteristic mBleGattCharacteristic;
    protected IConnectCallback iConnectCallback;    //连接结果回调

    public Handler sendDataHandler;                        //设置定时任务的handler，发送命令后，2秒后检查是否收到回复

    public BleConnCallBack(IConnectCallback iConnectCallback){
        this.iConnectCallback = iConnectCallback;
        if (iConnectCallback == null){
            throw new NullPointerException("this connCallback is null!");
        }
    }

    /**
     * 是否正在连接
     *
     * @return
     */
    public boolean isConnectting() {
        return isConnectting;
    }

    /**
     * 获取当前正在连接的锁的mac地址
     *
     * @return
     */
    public String getConnectingMAC() {
        return connectingMAC;
    }

    /**
     * 获取连接用的 BluetoothGatt
     * @return
     */
    public BluetoothGatt getmBluetoothGatt() {
        return mBluetoothGatt;
    }

    /**
     * 设置连接使用的 BluetoothGatt
     * @param mBluetoothGatt
     */
    public void setmBluetoothGatt(BluetoothGatt mBluetoothGatt) {
        this.mBluetoothGatt = mBluetoothGatt;
    }

    public int getReconnectCount() {
        return reconnectCount;
    }

    public void setReconnectCount(int reconnectCount) {
        this.reconnectCount = reconnectCount;
    }

    /**
     * 设置当前正在连接
     *
     * @param connectting
     */
    public void setConnectting(boolean connectting) {
        this.isConnectting = connectting;
    }

    /**
     * 设置当前正在连接的锁的mac地址
     *
     * @param connectingMAC
     */
    public void setConnectingMAC(String connectingMAC) {
        this.connectingMAC = connectingMAC;
    }


    public static BleConnCallBack getInstance() {
        return instance;
    }


    /**
     * 单例方式获取蓝牙通信入口
     *
     * @return 返回BleConnCallBack
     */
    public static BleConnCallBack getInstance(IConnectCallback iConnCallback) {

        if (instance == null) {
            synchronized (BleConnCallBack.class) {
                if (instance == null) {
                    instance = new BleConnCallBack(iConnCallback) {

                        //获取连接状态方法，BLE设备连接上或断开时，会调用到此方
                        @Override
                        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                            super.onConnectionStateChange(gatt, status, newState);
                            Log.d(TAG,"BleConnCallBack：GATT："+gatt.getDevice().getAddress() + " status:"+status + " newstate:"+newState);
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                Log.i(TAG, "Connected to GATT server.");
                                afterConnected();
                                iConnectCallback.onConnectSuccess();
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                Log.i(TAG, "Disconnected from GATT server.");

                                iConnectCallback.onDisconnect(false);
                                afterDisconnected(gatt.getDevice().getAddress());
                            }else if (newState == BluetoothProfile.STATE_CONNECTING){
                                Log.i(TAG, "connecting from GATT server.");
                            }else{
                                Log.i(TAG, "Disconnecting from GATT server.");
                            }
                        }

                        //成功发现设备的services时，调用此方法
                        @Override
                        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                            super.onServicesDiscovered(gatt, status);

                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                afterDiscoverService(gatt);
                            } else {
                                Log.e(TAG, " Discovered Services Failed, errcode:" + status);
                                //错误码中返回 129 ，需要关闭蓝牙，在重新打开
                                if (status == 129) {
                                    Log.e(TAG, "----/错误码中返回 129 ，重新初始化蓝牙");
                                    SinovoBle.getInstance().init(SinovoBle.getInstance().getContext());
                                }
                            }
                        }

                        //修改mtu之后的回调
                        @Override
                        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                            super.onMtuChanged(gatt, mtu, status);
                            Log.d(TAG," onMtuChanged");
                            if (BluetoothGatt.GATT_SUCCESS == status) {
                                Log.d(TAG, "onMtuChanged success MTU = " + mtu);
                            }else {
                                Log.d(TAG,"onMtuChanged fail ");
                            }
                        }

                        @Override
                        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                            super.onDescriptorRead(gatt, descriptor, status);
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                Log.d(TAG, "读取Descriptor成功，status：" + status + " ，并更新广播 ACTION_READ_Descriptor_OVER");
                            }
                        }

                        //读写characteristic时会调用到以下方法
                        @Override
                        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                            super.onCharacteristicRead(gatt, characteristic, status);
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                Log.d(TAG, "读取数据成功：" + Arrays.toString(characteristic.getValue()) + " ,并广播 ACTION_READ_OVER");
                            }
                        }

                        //数据返回的回调（此处接收BLE设备返回数据）
                        @Override
                        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                            super.onCharacteristicChanged(gatt, characteristic);
                            byte[] recvData = characteristic.getValue();
                            String recvStr  = byte2hex(recvData);
                            Log.d(TAG, "回调 onCharacteristicChanged 表示已经收到锁端("+gatt.getDevice().getAddress()+")发来的数据："+recvStr);

                            if (sendDataHandler != null && !recvStr.substring(2,4).equals("27")){
                                Log.d(TAG, "已经收到锁端回复，且不是27的功能码，取消掉 2秒定时检测 是否有 恢复的任务");
                                sendDataHandler.removeCallbacksAndMessages(null);    //取消定时任务
                            }
                            LinkedHashMap resultmap = BleCommand.getInstance().getDataFromBle(recvStr);
                            Log.d(TAG, "数据处理后的结果：" + JSON.toJSONString(resultmap));

                            JSONObject jsonObject =new JSONObject(resultmap);
                            afterReceiveData(jsonObject);
                        }

                        //写操作的回调
                        @Override
                        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                            super.onCharacteristicWrite(gatt, characteristic, status);

                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                Log.d(TAG, "写入失败 ，onCharacteristicWrite，status=" + status);
                            }
                        }

                        /**
                         * 设置 写入的 描述符成功的回调，收到此回调，表示准备工作完毕，可以写入数据了
                         * @param gatt gatt
                         * @param descriptor des
                         * @param status  status
                         */
                        @Override
                        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                            super.onDescriptorWrite(gatt, descriptor, status);

                            String macaddress = gatt.getDevice().getAddress();
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                Log.d(TAG, "onDescriptorWrite 回调成功, 真正连接成功，可以发送命令了");

                                SinovoBle.getInstance().setLockMAC(macaddress);
                                if (SinovoBle.getInstance().isBindMode()){
                                    Log.d(TAG, "绑定模式下连接成功，进行绑定验证,lockid:"+ SinovoBle.getInstance().getLockID() + ",imei:"+ SinovoBle.getInstance().getPhoneIMEI());

                                    if(!SinovoBle.getInstance().getLockID().isEmpty() && !SinovoBle.getInstance().getPhoneIMEI().isEmpty()){
                                        String data = SinovoBle.getInstance().getLockID() + SinovoBle.getInstance().getPhoneIMEI();
                                        BleCommand.getInstance().exeCommand("00", data, true);
                                    }
                                }else {
                                    SinovoBle.getInstance().setScanAgain(false);   //非绑定模式下  就需要停止扫描
                                    Log.d(TAG, "非绑定模式下，进行连接，直接发送相关命令");
//
//                                    isAllowConnToAny = false;   //设定连接断开后 只能连接此锁了
//
//                                    //3、执行命令
//                                    connectOK2cmd();
                                }

                            } else {
                                Log.d(TAG, "onDescriptorWrite 回调失败，status=" + status);
                            }
                        }
                    };
                }
            }
        }
        return instance;
    }

    /**
     * 连接成功的处理, 需要去发现服务
     */
    void afterConnected(){
        if (SinovoBle.getInstance().isConnected()){
            Log.d(TAG, "已经连接成功了，又收到了 连接成功的回调，不处理");
            return;
        }

        //非绑定模式下，直接更新状态
        if (!SinovoBle.getInstance().isBindMode()){
            SinovoBle.getInstance().setConnected(true);
            setConnectting(false);
        }

        //连接成功之后，需要去发现服务
        if (getmBluetoothGatt() != null){
            getmBluetoothGatt().discoverServices();
        }else {
            Log.w(TAG, "异常了，连接成功，但mBluetoothGatt 为null，重新初始化ble");
            SinovoBle.getInstance().init(SinovoBle.getInstance().getContext());
        }
    }

    /**
     * 连接断开的处理
     */
    void afterDisconnected(String disconn_mac){
        Log.e(TAG, "连接断开,当前断开的设备的mac是："+ disconn_mac);

        setConnectting(false);
        SinovoBle.getInstance().setConnected(false);
        BleCommand.getInstance().setExeCmding(false);

        if (SinovoBle.getInstance().isBindMode()){
            String scanDeviceMac0 = SinovoBle.getInstance().getScanLockList().get(0).GetDevice().getAddress();
            if (disconn_mac.equals(scanDeviceMac0)) {
                Log.e(TAG, "绑定模式下，丢失的连接是扫描列表中的第一个设备，需要进行重连："+ disconn_mac);
                setReconnectCount(getReconnectCount() + 1);

                if (getReconnectCount() >3){
                    if (SinovoBle.getInstance().getScanLockList().size()>0){
                        Log.e(TAG, "绑定模式下，重连超过3次，不在重连它："+ disconn_mac);
                        SinovoBle.getInstance().getBondBleMacList().add(SinovoBle.getInstance().getLockMAC());
                        SinovoBle.getInstance().getScanLockList().remove(0);  //删除对首的设备
                    }
                }
            }
            SinovoBle.getInstance().tryBindBle(iConnectCallback);
        }else {
            Log.e(TAG, "非绑定模式下，丢失的连接是："+ disconn_mac);
            if (!disconn_mac.equals(getConnectingMAC())){
                Log.w(TAG, "连接丢失的mac地址是："+disconn_mac + "，当前正在连接的mac地址是："+getConnectingMAC() + ",不一致，不处理");
                return;
            }
        }
    }

    /**
     * 发现服务后 需要设置 蓝牙读写属性
     * @param gatt gatt
     */
    void afterDiscoverService(BluetoothGatt gatt){
        if (getmBluetoothGatt() == null){
            Log.e(TAG, "服务发现失败，mBluetoothGatt 为空，断开重连");
            disConectBle();
            return;
        }

        List<BluetoothGattService> supportedGattServices = gatt.getServices();
        String sUUID = BleConstant.SERVICE_UUID_FM60;
        String characteristUUID = BleConstant.CHARACTERISTIC_UUID_FM60;
        for (int i = 0; i < supportedGattServices.size(); i++) {
            String serUUID = String.valueOf(supportedGattServices.get(i).getUuid());

            if (serUUID.equals(BleConstant.SERVICE_UUID_FM67)) {
                sUUID = BleConstant.SERVICE_UUID_FM67;
                characteristUUID = BleConstant.CHARACTERISTIC_UUID_FM67;
                Log.d(TAG, "根据过滤出来的服务UUID，检测到当前连接的是FM67");
                break;
            }
        }

        SinovoBle.getInstance().setBleServiceUUID(sUUID);
        SinovoBle.getInstance().setBlecharacteristUUID(characteristUUID);

        //延迟500ms 再去设置 读写 描述符
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "延迟500ms再去设置 读写描述符");
                if (mBluetoothGatt == null){
                    Log.d(TAG, "mBluetoothGatt 为空，无法去获取服务");
                    return;
                }

                if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()){
                    Log.d(TAG, "mBleAdapter 蓝牙未开启");
                    return;
                }

                mBleGattService = mBluetoothGatt.getService(UUID.fromString(SinovoBle.getInstance().getBleServiceUUID()));
                if (mBleGattService == null){
                    Log.e(TAG, "服务发现失败，mBleGattService 为空，断开重连");
                    disConectBle();
                    return;
                }

                mBleGattCharacteristic = mBleGattService.getCharacteristic(UUID.fromString(SinovoBle.getInstance().getBlecharacteristUUID()));
                if (mBleGattCharacteristic == null){
                    Log.e(TAG, "mBleGattCharacteristic 为null，断开重连");
                    disConectBle();
                    return;
                }

                //设置特征能够进行通知,设置写 描述符
                mBluetoothGatt.setCharacteristicNotification(mBleGattCharacteristic, true);
                BluetoothGattDescriptor write_descriptor = mBleGattCharacteristic.getDescriptor(UUID.fromString(BleConstant.CLIENT_CHARACTERISTIC_CONFIG));

                if (write_descriptor == null) {
                    Log.e(TAG, "写入的描述符为空，断开重连");
                    disConectBle();
                    return;
                }
                write_descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(write_descriptor);

                //设置读描述符
                BluetoothGattDescriptor read_descriptor = mBleGattCharacteristic.getDescriptor(UUID.fromString(BleConstant.CLIENT_CHARACTERISTIC_CONFIG));
                if (read_descriptor == null){
                    Log.e(TAG, "读取ble特征码的描述符 为空，断开重连");
                    disConectBle();
                    return;
                }
                mBluetoothGatt.readDescriptor(read_descriptor);
            }
        }, 500);
    }


    void afterReceiveData(JSONObject jsonObject){
        Object funCode = jsonObject.get("funCode");
        Object errCode = jsonObject.get("errCode");

        //处理绑定结果 绑定失败的情况下，需要通过接口告知用户 ； 绑定成功不用立马告知用户
        if (Objects.equals(funCode, "00")){

            //非绑定成功时，都要先断开蓝牙连接
            if (!(Objects.equals(errCode, "00") || Objects.equals(errCode, "0b") || Objects.equals(errCode, "04"))){

                disConectBle();
                SinovoBle.getInstance().setConnected(false);
                setConnectting(false);

                //绑定的时候，如果不是绑定成功的，则添加到已经绑定过的列表中
                SinovoBle.getInstance().getBondBleMacList().add(SinovoBle.getInstance().getLockMAC());
                SinovoBle.getInstance().getScanLockList().remove(0);  //删除对首的设备，
                return;
            }

            //二维码是正确的，但用户没有去按set进行确认，导致超时
            if (Objects.equals(errCode, "04")){
                disConectBle();
                SinovoBle.getInstance().setConnected(false);
                setConnectting(false);
                SinovoBle.getInstance().setBindMode(false);   //退出绑定模式
                SinovoBle.getInstance().setScanAgain(false);   //停止扫描
                SinovoBle.getInstance().getBondBleMacList().clear();
                SinovoBle.getInstance().getScanLockList().clear();
                iConnectCallback.onAddLock(jsonObject);
                return;
            }

        }

        //处理绑定之后的自动创建用户
        if (Objects.equals(funCode, "01")){
            iConnectCallback.onAddLock(jsonObject);
        }

        //设置或查询锁的名称
        if (Objects.equals(funCode, "11")){
            iConnectCallback.onSetLockName(jsonObject);
        }
    }

    public void writeCharacteristic(byte[] value, UUID serivceUUID, UUID characterUUID){

        if (mBluetoothGatt == null) {
            disConectBle();
            return;
        }
        long enterTime = System.currentTimeMillis();
      //  long HONEY_CMD_TIMEOUT = 400;
        while ((System.currentTimeMillis() - enterTime) < 400) {
            if(isDeviceBusy()){
                try {
                    Log.e(TAG,"ble 正在忙，休眠200ms 再来检测");
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }else {
                Log.w(TAG,"ble 空闲了，可以通信");
                break;
            }
        }
        try {
            BluetoothGattService service = mBluetoothGatt.getService(serivceUUID);
            if (service == null){
                Log.w(TAG,"mBluetoothGatt.getService  服务为空 ，断开连接");
                disConectBle();
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characterUUID);
            characteristic.setValue(value);
            boolean status = mBluetoothGatt.writeCharacteristic(characteristic);

            if (sendDataHandler == null){
                sendDataHandler = new Handler(Looper.getMainLooper()); //初始化 句柄，用于定时关闭扫描
            }
            //延迟1.5秒后再检测 是否已经收到锁端的恢复
            sendDataHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    BleCommand.getInstance().checkDataReceive();
                }
            }, 1500);
            Log.d(TAG, "指令:"+ BleCommand.getInstance().getCommandList().getFirst()+" 发送出去了，1.5秒后检测是否收到回复");

            if (status) {
                Log.w(TAG, "指令:"+BleCommand.getInstance().getCommandList().getFirst()+" 发送成功");
            }else {
                Log.e(TAG, "指令:"+BleCommand.getInstance().getCommandList().getFirst()+" 发送失败");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    /**
     * 通过反射机制来 判断 当前蓝牙 是否正在忙，如果忙 ，则等待 30ms 再来检测 ，持续检测2s
     * @return bool
     */
    private boolean isDeviceBusy(){
        boolean state = false;
        try {
            state = (boolean)readField(mBluetoothGatt);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return state;
    }

    private static Object readField(Object object) throws IllegalAccessException, NoSuchFieldException {
        Field field = object.getClass().getDeclaredField("mDeviceBusy");
        field.setAccessible(true);
        return field.get(object);
    }


    // 断开连接
    public void disConectBle() {
        Log.d(TAG, "关闭蓝牙连接  disConectBle() ");

        refreshDeviceCache(mBluetoothGatt);

        if (mBluetoothGatt != null) { mBluetoothGatt.disconnect(); }
//        if (mBluetoothGatt != null) { mBluetoothGatt.close(); }
//        if (mBluetoothGatt != null) { mBluetoothGatt = null; }
        setConnectting(false);
        SinovoBle.getInstance().setConnected(false);
    }

    /** * Clears the internal cache and forces a refresh of the services from the * remote device. */
    //清空 蓝牙缓存， 采用反射机制
    private boolean refreshDeviceCache(BluetoothGatt mBluetoothGatt) {
        if (mBluetoothGatt !=null) {
            try {
                Method localMethod = mBluetoothGatt.getClass().getMethod("refresh");
                return (Boolean) Objects.requireNonNull(localMethod.invoke(mBluetoothGatt));
            }catch (Exception localException) {
                localException.printStackTrace();
                Log.i(TAG,"An exception occured while refreshing device");
            }
        }
        return false;
    }
}







