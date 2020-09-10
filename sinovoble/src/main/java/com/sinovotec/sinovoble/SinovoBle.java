package com.sinovotec.sinovoble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.callback.BleConnCallBack;
import com.sinovotec.sinovoble.callback.BleScanCallBack;
import com.sinovotec.sinovoble.callback.IConnectCallback;
import com.sinovotec.sinovoble.callback.IScanCallBack;
import com.sinovotec.sinovoble.common.BleConfig;
import com.sinovotec.sinovoble.common.BleConstant;
import com.sinovotec.sinovoble.common.BleScanDevice;
import com.sinovotec.sinovoble.common.ComTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;


public class SinovoBle {
    private String TAG = "BleLib";

    private String lockID ;                       //锁的ID，用户输入的，用于添加锁的
    private String lockTypeForAdd;                //锁的类型，用户添加锁时需要指定 要添加的设备类型
    private String phoneIMEI;                     //手机的imei，作为手机id
    private String lockMAC;                       //当前连接锁的蓝牙mac地址
    private String lockSNO;                       //手机与锁进行蓝牙通信使用的 校验码
    private String lockName;                      //锁的名称
    private String startBindTime;                 //开始绑定时间

    private String bleServiceUUID;                //蓝牙服务的UUID
    private String blecharacteristUUID;           //蓝牙特征字的UUID
    private boolean isBindMode  = false;          //是否为绑定模式
    private boolean isScanAgain = false;          //扫描停止后是否继续扫描
    private boolean isConnected = false;          //是否已经连接成功
    private boolean ScanSomeLock = false;         //记录是否扫描到设备，用于在扫描超时后，没有任何设备时需要告知用户

    private Context context;                        //上下文
    private BluetoothManager bluetoothManager;      //蓝牙管理
    private BluetoothAdapter bluetoothAdapter;      //蓝牙适配器

    private Handler scanBleHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));  //设置定时任务的handler，扫描5s后 定时调用 停止扫描的函数
    private Handler scanTimeoutHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));  //设置定时任务的handler，扫描5s后 定时调用 停止扫描的函数

    private ArrayList<BleScanDevice> scanLockList ;  //保存扫描的结果
    private ArrayList<String> bondBleMacList;        //保存在绑定时，已经尝试连接过的锁 ,避免已经尝试过绑定不合适的锁，还会重复绑定

    @SuppressLint("StaticFieldLeak")
    private static SinovoBle instance;                //入口操作管理
    private static BleConfig bleConfig = BleConfig.getInstance();

    /**
     * 单例方式获取蓝牙通信入口
     *
     * @return 返回ViseBluetooth
     */
    public static SinovoBle getInstance() {
        if (instance == null) {
            synchronized (SinovoBle.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new SinovoBle();
                }
            }
        }
        return instance;
    }

    private SinovoBle() {
        scanLockList    = new ArrayList<>();
        bondBleMacList  = new ArrayList<>();
    }


    /**
     * 获取锁的二维码id
     * @return
     */
    public String getLockID() {
        return lockID;
    }

    /**
     * 获取锁的类型
     * @return
     */
    public String getLockTypeForAdd() {
        return lockTypeForAdd;
    }

    /**
     * 获取绑定状态
     * @return
     */
    public boolean isBindMode() {
        return isBindMode;
    }

    public boolean isScanSomeLock() {
        return ScanSomeLock;
    }

    /**
     * 获取手机的id
     * @return
     */
    public String getPhoneIMEI() {
        return phoneIMEI;
    }


    /**
     * 获取当前连接的mac地址
     * @return
     */
    public String getLockMAC() {
        return lockMAC;
    }

    /**
     * 获取是否需要再次扫描
     * @return
     */
    public boolean isScanAgain() {
        return isScanAgain;
    }

    public void setScanSomeLock(boolean scanSomeLock) {
        ScanSomeLock = scanSomeLock;
    }

    /**
     * 获取蓝牙服务的UUID
     * @return
     */
    public String getBleServiceUUID() {
        return bleServiceUUID;
    }

    /**
     * 获取绑定的时间
     * @return
     */
    public String getStartBindTime() {
        return startBindTime;
    }


    public boolean isConnected() {
        return isConnected;
    }

    /**
     * 获取特征字的UUID
     * @return
     */
    public String getBlecharacteristUUID() {
        return blecharacteristUUID;
    }

    public String getLockSNO() {
        return lockSNO;
    }

    public String getLockName() {
        return lockName;
    }

    /**
     * 已经绑定过的设备列表
     * @return
     */
    public ArrayList<String> getBondBleMacList() {
        return bondBleMacList;
    }

    /**
     * 扫描得到有效的设备列表
     */
    public ArrayList<BleScanDevice> getScanLockList() {
        return scanLockList;
    }

    /**
     * 设置锁的二维码id
     * @param lockID  string
     */
    public void setLockID(String lockID) {
        this.lockID = lockID;
    }

    /**
     * 设置锁的类型，用于添加锁
     * @param lockType string
     */
    public void setLockTypeForAdd(String lockType) {
        this.lockTypeForAdd = lockType;
    }


    /**
     * 设置绑定状态
     */
    public void setBindMode(boolean bindMode) {
        isBindMode = bindMode;
    }


    /**
     * 设置首次绑定的时间
     * @param startBindTime string
     */
    public void setStartBindTime(String startBindTime) {
        this.startBindTime = startBindTime;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    /**
     * 设置手机的imei
     * @param phoneIMEI string
     */
    public void setPhoneIMEI(String phoneIMEI) {
        this.phoneIMEI = phoneIMEI;
    }

    /**
     * 设置当前连接锁的mac地址
     */
    public void setLockMAC(String lockMAC) {
        this.lockMAC = lockMAC;
    }

    public void setLockSNO(String lockSNO) {
        this.lockSNO = lockSNO;
    }

    public void setLockName(String lockName) {
        this.lockName = lockName;
    }


    /**
     * 设置是否需要再次扫描
     */
    public void setScanAgain(boolean scanAgain) {
        isScanAgain = scanAgain;
    }

    /**
     * 设置蓝牙的服务UUID
     * @param bleServiceUUID uuid
     */
    public void setBleServiceUUID(String bleServiceUUID) {
        this.bleServiceUUID = bleServiceUUID;
    }

    /**
     * 设置蓝牙的特征字UUID
     * @param blecharacteristUUID uuid
     */
    public void setBlecharacteristUUID(String blecharacteristUUID) {
        this.blecharacteristUUID = blecharacteristUUID;
    }

    /**
     * 获取配置对象，可进行相关配置的修改
     *
     * @return
     */
    public static BleConfig config() {
        return bleConfig;
    }

    /**
     * 获取Context
     *
     * @return 返回Context
     */
    public Context getContext() {
        return context;
    }

    /**
     * 获取蓝牙管理
     *
     * @return 返回蓝牙管理
     */
    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    /**
     * 获取蓝牙适配器
     *
     * @return 返回蓝牙适配器
     */
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /**
     * 初始化
     *
     * @param context 上下文
     */
    public void init(Context context) {
        if (this.context == null && context != null) {
            this.context = context.getApplicationContext();
            bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
//            deviceMirrorPool = new DeviceMirrorPool();

            //蓝牙相关配置修改
            config().setScanTimeout(-1)                 //扫描超时时间，这里设置为永久扫描
                    .setScanRepeatInterval(5 * 1000)    //扫描间隔5秒,扫描5s即停下，然后再扫描
                    .setConnectTimeout(10 * 1000)       //连接超时时间
                    .setOperateTimeout(5 * 1000)        //设置数据操作超时时间
                    .setConnectRetryCount(3)            //设置连接失败重试次数
                    .setConnectRetryInterval(1000)      //设置连接失败重试间隔时间
                    .setOperateRetryCount(3)            //设置数据操作失败重试次数
                    .setOperateRetryInterval(1000)      //设置数据操作失败重试间隔时间
                    .setMaxConnectCount(1);             //设置最大连接设备数量
        }
    }

    public int startBleScan(final IScanCallBack iScanCallBack){
        SinovoBle.getInstance().getBondBleMacList().clear();   //clean the bondBleMacList before starting scan
        SinovoBle.getInstance().setScanAgain(true);
        SinovoBle.getInstance().setScanSomeLock(false);

        //绑定模式下，如果
        if (isBindMode()) {
            scanTimeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkScanResult(iScanCallBack);
                }
            }, 2*60*1000);
        }
        return bleScan(iScanCallBack);
    }

    //检测扫描的结果; 开始扫描后，2分钟检测扫描的结果，是否扫到设备
    public void checkScanResult(IScanCallBack iScanCallBack){
        if (!isScanSomeLock() && isBindMode() && !isConnected()){
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("scanResult", "0");
            iScanCallBack.onDeviceFound(JSONObject.toJSONString(map));
        }
    }

    //用户取消了绑定
    public void cancelAddLock(){
        if (!isBindMode()){
            Log.d(TAG,"非绑定模式下，无需取消绑定，直接退出");
            return;
        }

        setBindMode(false);
        setScanAgain(false);
        BleConnCallBack.getInstance().disConectBle();
    }

    /**
     *  定义扫描的实现逻辑；
     *  开始扫描之后，需要扫描一段时间之后 先停下来；否则会一直扫描； 一直扫描的话，部分机型会扫描不到设备
     * @return  int, 定义如下
     *  1：表示已经在扫描，无需重复扫描
     *  -1：蓝牙适配器为空，需要确认手机是否支持蓝牙
     *  -2：手机蓝牙未开启
     *  0：正常扫描
     */
    public int bleScan(final IScanCallBack iScanCallBack){

        if (SinovoBle.getInstance().getBluetoothAdapter() == null){
            Log.d(TAG, "蓝牙 mBleAdapter 为null， 不能进行停止扫描扫描");
            return -1;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()){
            Log.d(TAG, "蓝牙 未启动， 不能进行停止扫描扫描");
            return -2;
        }

        if (BleScanCallBack.getInstance(iScanCallBack).isScanning()){
            Log.d(TAG, "Bluetooth scanning is already underway");
            return 1;
        }

        //更加 UUID 来过滤
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter1 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM60)).build();
        ScanFilter filter2 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM67)).build();
        filters.add(filter1);
        filters.add(filter2);

        Log.d(TAG, "Start scanning");
        scanBleHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BleScanCallBack.getInstance(iScanCallBack).stopScan();
            }
        }, bleConfig.getScanRepeatInterval());

        BleScanCallBack.getInstance(iScanCallBack).setScanning(true);

        ScanSettings bleScanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)  //扫描到结果，立马报告
                .build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, bleScanSettings, BleScanCallBack.getInstance(iScanCallBack));      //根据指定参数来过滤
        return 0;
    }

    //取消蓝牙扫描的定时任务
    public void removeHandlerMsg(){
        if (scanBleHandler !=null) {
            scanBleHandler.removeCallbacksAndMessages(null);    //取消定时任务
        }
    }

    /**
     * 对外提供连接锁的接口函数
     */
    public void connectLock(IConnectCallback mConnCallBack){

        if (isBindMode()){
            String nowtime = ComTool.getSpecialTime("",1,0,0);
            SinovoBle.getInstance().setStartBindTime(nowtime);
            tryBindBle(mConnCallBack);
        }
    }

    /*
     *  尝试进行绑定
     */
    public  void tryBindBle(IConnectCallback mConnCallBack) {
        /*
         *  开始进行蓝牙连接 绑定
         *  条件：
         *  1、扫描得到的结果数组中存在ble设备
         *  2、当前没有正在进行连接
         *  3、当前蓝牙状态为 未连接
         */
        Log.i(TAG,"调用tryBindBle 尝试绑定连接其他设备");
        BleConnCallBack myConnCallBack = BleConnCallBack.getInstance(mConnCallBack);
        if (!getScanLockList().isEmpty() && !myConnCallBack.isConnectting() && !SinovoBle.getInstance().isConnected()){
            BleScanDevice bleScanLock = SinovoBle.getInstance().getScanLockList().get(0);
            Log.d(TAG, "准备绑定ble设备："+ bleScanLock.GetDevice().getAddress());
            SinovoBle.getInstance().connectBle(bleScanLock.GetDevice() ,mConnCallBack);

            //开始连接之后，需要，将此锁从扫描列表中 删除，避免下次再来连接他
//            SinovoBle.getInstance().getBondBleMacList().add(bleScanLock.GetDevice().getAddress());
//            SinovoBle.getInstance().getScanLockList().remove(0);
        }else {
            Log.d(TAG, "不符合连接条件，getScanLockList()大小："+ SinovoBle.getInstance().getScanLockList().size()
                    +", isconnecting:"+myConnCallBack.isConnectting() + " isConnected:"+SinovoBle.getInstance().isConnected());

            String nowtime = ComTool.getSpecialTime("",1,0,0);
            long timeDiff = ComTool.calDateDiff(1, SinovoBle.getInstance().getStartBindTime(), nowtime);

            Log.d(TAG,"开始绑定的时间："+ SinovoBle.getInstance().getStartBindTime() +", 当前时间："+nowtime + ", 时间差："+timeDiff);
            //退出绑定模式
            if (timeDiff>60 && getScanLockList().size() ==0 && !myConnCallBack.isConnectting() && !SinovoBle.getInstance().isConnected()){
                Log.d(TAG, "已经绑定超过 60秒，而且现在扫描队列为空，取消扫描对话框");

                setBindMode(false);
                SinovoBle.getInstance().setScanAgain(false);
            }
        }
    }

    /**
     * 连接蓝牙设备
     * @param bluetoothDevice  待连接的设备
     * @return      //是否连接成功
     */
    public boolean connectBle(BluetoothDevice bluetoothDevice, IConnectCallback iConnectCallback) {
        if (SinovoBle.getInstance().getBluetoothAdapter() == null || bluetoothDevice == null) {
            Log.w(TAG, "BluetoothAdapter 没有初始化或是没有指定地址.");
            return false;
        }

        BleConnCallBack myConnCallBack = BleConnCallBack.getInstance(iConnectCallback);

        if (myConnCallBack.isConnectting()){
            Log.d(TAG, "当前正在连接中，忽略本次的连接请求："+ bluetoothDevice.getAddress());
            return false;
        }

        myConnCallBack.setConnectting(true);       //标记 已经在开始进行连接
        myConnCallBack.setConnectingMAC(bluetoothDevice.getAddress());       //标记 当前准备连接的地址，以便后面断开进行重连
        myConnCallBack.setmBluetoothGatt(bluetoothDevice.connectGatt(getContext(), false, myConnCallBack));

        Log.d(TAG, "调用connectGatt 来建立蓝牙连接,连接设备："+myConnCallBack.getConnectingMAC());

        return true;
    }


}
