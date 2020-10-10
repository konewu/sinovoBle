package com.sinovotec.sinovoble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.callback.BleConnCallBack;
import com.sinovotec.sinovoble.callback.BleScanCallBack;
import com.sinovotec.sinovoble.callback.IConnectCallback;
import com.sinovotec.sinovoble.callback.IScanCallBack;
import com.sinovotec.sinovoble.common.BleData;
import com.sinovotec.sinovoble.common.BleConnectLock;
import com.sinovotec.sinovoble.common.BleConstant;
import com.sinovotec.sinovoble.common.BleScanDevice;
import com.sinovotec.sinovoble.common.BluetoothListenerReceiver;
import com.sinovotec.sinovoble.common.ComTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;


public class SinovoBle {
    private String TAG = "SinovoBle";
    private String lockID ;                       //锁的ID，用户输入的，用于添加锁的
    private String phoneIMEI;                     //手机的imei，作为手机id
    private String lockMAC;                       //当前连接锁的蓝牙mac地址
    private String lockSNO;                       //手机与锁进行蓝牙通信使用的 校验码
    private String lockName;                      //锁的名称
    private String lockFirmVersion;
    private String lockType;

    private String bleServiceUUID;                //蓝牙服务的UUID
    private String blecharacteristUUID;           //蓝牙特征字的UUID

    private boolean isBindMode  = false;          //是否为绑定模式
    private boolean isScanAgain = false;          //扫描停止后是否继续扫描
    private boolean isConnected = false;          //是否已经连接成功
    private boolean isConnectting = false;        //是否正在连接

    private Context context;                       //上下文
    private BluetoothManager bluetoothManager;     //蓝牙管理
    private BluetoothAdapter bluetoothAdapter;     //蓝牙适配器

    private Handler scanBleHandler       = new Handler(Looper.getMainLooper());  //设置定时任务的handler，扫描5s后 定时调用 停止扫描的函数
    private Handler bindTimeoutHandler   = new Handler(Looper.getMainLooper());  //设置定时任务的handler，绑定2分钟后，超时失败

    private ArrayList<BleScanDevice> scanLockList ;         //保存扫描的结果
    private ArrayList<String> bondBleMacList;               //保存在绑定时，已经尝试连接过的锁 ,避免已经尝试过绑定不合适的锁，还会重复绑定
    private ArrayList<BleConnectLock> autoConnectList;      //自动连接的设备列表，内容为mac地址

    @SuppressLint("StaticFieldLeak")
    private static SinovoBle instance;                //入口操作管理

    private IScanCallBack mBleScanCallBack;     //蓝牙扫描的回调
    private IConnectCallback mConnCallBack;     //蓝牙连接的回调

    private int connectTimeout = 8*1000;        //连接超时检测，发起连接后，在超时的时间内得不到响应，则断开进行处理
    private int scanRepeatInterval = 6*1000;    //扫描间隔，开始扫描之后，间隔到指定时间，然后停止扫描，再重新开始扫描

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
        autoConnectList = new ArrayList<>();
    }

    public String getLockID() {
        return lockID;
    }

    public boolean isBindMode() {
        return isBindMode;
    }

    public boolean isConnectting() {
        return isConnectting;
    }

    public String getPhoneIMEI() {
        return phoneIMEI;
    }

    public String getLockMAC() {
        return lockMAC;
    }

    public boolean isScanAgain() {
        return isScanAgain;
    }

    public String getBleServiceUUID() {
        return bleServiceUUID;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public Handler getBindTimeoutHandler() {
        return bindTimeoutHandler;
    }

    public String getBlecharacteristUUID() {
        return blecharacteristUUID;
    }

    public String getLockSNO() {
        return lockSNO;
    }

    public String getLockName() {
        return lockName;
    }

    public String getLockType() {
        return lockType;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getScanRepeatInterval() {
        return scanRepeatInterval;
    }

    public void setScanRepeatInterval(int scanRepeatInterval) {
        this.scanRepeatInterval = scanRepeatInterval;
    }

    public void setLockFirmVersion(String lockFirmVersion) {
        this.lockFirmVersion = lockFirmVersion;
    }

    public ArrayList<String> getBondBleMacList() {
        return bondBleMacList;
    }

    public ArrayList<BleScanDevice> getScanLockList() {
        return scanLockList;
    }

    public ArrayList<BleConnectLock> getAutoConnectList() {
        return autoConnectList;
    }

    public Context getContext() {
        return context;
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public IConnectCallback getmConnCallBack() {
        return mConnCallBack;
    }

    public IScanCallBack getmBleScanCallBack() {
        return mBleScanCallBack;
    }

    public Handler getScanBleHandler() {
        return scanBleHandler;
    }

    public void setLockID(String lockID) {
        this.lockID = lockID;
    }

    public void setBindMode(boolean bindMode) {
        isBindMode = bindMode;
    }

    public void setConnectting(boolean connectting) {
        isConnectting = connectting;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public void setPhoneIMEI(String phoneIMEI) {
        this.phoneIMEI = phoneIMEI;
    }

    public void setLockMAC(String lockMAC) {
        this.lockMAC = lockMAC;
    }

    public void setLockSNO(String lockSNO) {
        this.lockSNO = lockSNO;
    }

    public void setLockName(String lockName) {
        this.lockName = lockName;
    }

    public void setScanAgain(boolean scanAgain) {
        isScanAgain = scanAgain;
    }

    public void setBleServiceUUID(String bleServiceUUID) {
        this.bleServiceUUID = bleServiceUUID;
    }

    public void setBlecharacteristUUID(String blecharacteristUUID) {
        this.blecharacteristUUID = blecharacteristUUID;
    }

    public void setmBleScanCallBack(IScanCallBack mBleScanCallBack) {
        this.mBleScanCallBack = mBleScanCallBack;
    }

    public void setmConnCallBack(IConnectCallback mConnCallBack) {
        this.mConnCallBack = mConnCallBack;
    }

    public void setAutoConnectList(ArrayList<BleConnectLock> autoConnectList) {
        this.autoConnectList = autoConnectList;
    }

    public void setLockType(String lockType) {
        this.lockType = lockType;
    }

    public String getLockFirmVersion() {
        return lockFirmVersion;
    }


    /**
     * 初始化
     *
     * @param context 上下文
     */
    public void init(Context context, IScanCallBack iScanCallBack, IConnectCallback iConnectCallback) {
        if (this.context == null && context != null) {
            this.context = context.getApplicationContext();
            bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            mBleScanCallBack = iScanCallBack;
            mConnCallBack    = iConnectCallback;

            //注册广播，监听蓝牙 状态的该表
            BluetoothListenerReceiver receiver = new BluetoothListenerReceiver();
            context.registerReceiver(receiver,makeFilter());
        }
    }
    private IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return filter;
    }

    public int startBleScan(){
        getBondBleMacList().clear();   //clean the bondBleMacList before starting scan
        setScanAgain(true);

        //绑定模式下，如果
        if (isBindMode()) {
            bindTimeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkScanResult();
                }
            }, 60*1000);
        }
        return bleScan(getmBleScanCallBack());
    }

    //绑定超时检测
    private void checkScanResult(){
        Log.e(TAG,"绑定超时检测，超时为1分钟");
        if (isBindMode() && !isConnected()){
            Log.d(TAG,"绑定超时检测，需要告知回调");
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("scanResult", "0");
            setScanAgain(false);
            setBindMode(false);
            getmBleScanCallBack().onScanTimeout(JSONObject.toJSONString(map));
        }
    }

    /**
     * * 添加锁，进行绑定锁
     * @param lockqrID  设置锁的二维码
     * @param phoneID   手机的imei
     */
    public void addLock(String lockqrID, String phoneID){

        //每次准备绑定之前，先取消上次的绑定超时检测
        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            Log.w(TAG,"取消 绑定超时检测");
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanAgain(true);
        setBindMode(true);
        setConnectting(false);
        setLockID(lockqrID);
        setPhoneIMEI(phoneID);
        startBleScan();

        //开始绑定，需要清空自动连接列表
        autoConnectList.clear();
    }

    /**
     * 非绑定模式下，自动连接指定的锁，可以指定多把，蓝牙先扫描到哪一把就连哪一把
     * @param autoConnectList  需要自动连接的锁列表
     */
    public void autoConnectLock(ArrayList<BleConnectLock> autoConnectList){

        if (mBleScanCallBack == null || mConnCallBack ==null){
            Log.e(TAG,"ScanCallBack or mConnCallBack is null");
            return;
        }
//        Log.w(TAG, "autoConnectLock 开始进行自动连接。。。。");
        setScanAgain(true);
        setBindMode(false);
        setConnectting(false);
        setAutoConnectList(autoConnectList);
        startBleScan();
    }

    public int  checkEnvir(){
        if (!isConnected()){
            Log.e(TAG,"Bluetooth not connected");
            return 1;
        }

        if (SinovoBle.getInstance().getLockSNO() == null){
            Log.e(TAG,"SNO error");
            return -1;
        }

        if (SinovoBle.getInstance().getLockSNO().length() != 6){
            Log.e(TAG,"SNO error");
            return -1;
        }
        return 0;
    }

    /**
     * 创建用户,默认创建的是普通用户
     * @param userName
     */
    public int addUser(String userName){
        if (userName.isEmpty() || userName.length()>10){
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +ComTool.stringToAscii(userName);
        BleData.getInstance().exeCommand("02", data, false);
        return 0;
    }

    /**
     * 编辑用户，修改用户名
     * @param updateData 用户名
     * @param userNID   用户的nid
     */
    public int updateUser(String updateData, String userNID){
        if (userNID.isEmpty() || updateData.length()>10 ){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String username = "";
        if (!updateData.isEmpty()){
            username = ComTool.stringToAscii(updateData);
        }
        String updateStr = SinovoBle.getInstance().getLockSNO() +userNID + username;
        BleData.getInstance().exeCommand("03", updateStr, false);
        return 0;
    }

    /**
     * 为用户添加一组数据,密码、卡、指纹
     * @param userNID       用户nid
     * @param dataType      数据类型， 02 普通密码，03超级用户密码，06是卡，07是指纹，08是防胁迫指纹
     * @param password      添加密码时具体的密码内容， 如果是添加卡/指纹时，留空即可
     */
    public int addDataForUser(String userNID, String dataType, String password){
        if (userNID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //添加密码时
        if (dataType.equals("02") || dataType.equals("03") || dataType.equals("05")){
            if (password.isEmpty()){
                Log.e(TAG,"Parameter error");
                return 2;
            }

            String data = SinovoBle.getInstance().getLockSNO() +userNID + dataType + password;
            BleData.getInstance().exeCommand("05", data, false);
        }

        //添加卡、指纹、防胁迫指纹
        if (dataType.equals("06") || dataType.equals("07") || dataType.equals("08")){
            String data = SinovoBle.getInstance().getLockSNO() +userNID + dataType ;
            BleData.getInstance().exeCommand("05", data, true);
        }
        return 0;
    }

    /**
     * 删除某一项数据，删除一组密码、卡、指纹、绑定
     * @param dataType s
     * @param delID s
     */
    public int delData(String dataType, String delID){
        if (delID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        if (!dataType.equals("0e")){
            String data = SinovoBle.getInstance().getLockSNO() +dataType + delID ;
            BleData.getInstance().exeCommand("06", data, false);
        }else {
            //删除绑定
            String data = SinovoBle.getInstance().getLockSNO() + delID ;
            BleData.getInstance().exeCommand("1b", data, false);
        }
        return 0;
    }

    /**
     * 修改用户的密码
     * @param userNid  用户的id
     * @param codeType 密码的类型
     * @param codeID   密码的ID
     * @param newCode  新的密码
     */
    public int resetPassword(String userNid, String codeType, String codeID, String newCode){
        if (userNid.isEmpty() || codeType.isEmpty() || codeID.isEmpty() || newCode.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + userNid + codeType + codeID + newCode;
        BleData.getInstance().exeCommand("0d", data, false);
        return 0;
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
    public int setLock(String dataType, String data){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //设置锁的名称
        if (dataType.equals("01")){
            if (data.length() >10 || data.length() == 0){
                Log.e(TAG,"Parameter error");
                return 2;
            }

            String locknameAscii = "";
            locknameAscii = ComTool.stringToAscii(data);
            String setData = SinovoBle.getInstance().getLockSNO() + locknameAscii;
            BleData.getInstance().exeCommand("11", setData, false);
        }

        //设置锁的时间
        if (dataType.equals("02")){
            String setData = SinovoBle.getInstance().getLockSNO() + data;
            BleData.getInstance().exeCommand("10", setData, false);
        }

        //设置锁的自动锁门时间
        if (dataType.equals("03")){
            String sixteen = "";
            if (!data.isEmpty()){
                if(Integer.valueOf(data) <0 || Integer.valueOf(data)>240){
                    Log.e(TAG,"Parameter error");
                    return 2;
                }

                sixteen = Integer.toHexString(Integer.valueOf(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
            }

            String setData = SinovoBle.getInstance().getLockSNO() + sixteen;
            BleData.getInstance().exeCommand("16", setData, false);
        }

        //设置静音 和绑定后自动创建用户
        if (dataType.equals("04") || dataType.equals("05")){
            if (data.isEmpty()){
                data = "02";
            }
            if (Integer.valueOf(data)<0 || Integer.valueOf(data)>2){
                Log.e(TAG,"Parameter error");
                return 2;
            }

            String setData = SinovoBle.getInstance().getLockSNO() + data;
            if (dataType.equals("04")){
                BleData.getInstance().exeCommand("1c", setData, false);
            }else {
                BleData.getInstance().exeCommand("09", setData, false);
            }
        }

        //设置超级用户的权限
        if (dataType.equals("06")){
            String setData = SinovoBle.getInstance().getLockSNO() + data;
            BleData.getInstance().exeCommand("23", setData, false);
        }

        return 0;
    }

    /**
     * 查询锁端的信息
     * @param dataType 查询的类型
     *                 01 查询管理员信息
     *                 02 查询锁的电量信息
     *                 03 查询锁的当前状态
     *                 04 查询锁的固件版本信息
     */
    public int requestLockInfo(String dataType){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() ;
        if (dataType.equals("01")){
            BleData.getInstance().exeCommand("12", data, false);
        }

        if (dataType.equals("02")){
            BleData.getInstance().exeCommand("0e", data, false);
        }

        if (dataType.equals("03")){
            BleData.getInstance().exeCommand("0f", data, false);
        }

        if (dataType.equals("04")){
            BleData.getInstance().exeCommand("1a", data, false);
        }

        return 0;
    }

    /**
     * 同步数据，包括用户信息
     */
    public int getAllUsers(){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +"00";
        BleData.getInstance().exeCommand("13", data, false);
        return 0;
    }

    /**
     * 同步数据，包括用户信息 和绑定的手机
     */
    public int getAllBoundPhone(){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +"0e";
        BleData.getInstance().exeCommand("13", data, false);

        return 0;
    }

    /**
     * 同步日志
     * @param logID  ，表示当前的日志id ,日志量比较大，所以支持从指定的id开始同步，如果 id为 ff ，则同步所有的日志
     */
    public int getLog(String logID){
        if (logID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }
        String data = SinovoBle.getInstance().getLockSNO() + logID;

        BleData.getInstance().exeCommand("17", data, false);

        return 0;
    }

    /**
     * 启用/禁用 动态密码
     * @param enable  00 表示禁用， 01 表示启动
     * @param dynamicCode  对应的 动态密码
     */
    public int doDynamicCode(String dynamicCode, String enable){
        if (dynamicCode.isEmpty() ||!(enable.equals("00") || enable.equals("01"))){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + enable + dynamicCode;
        BleData.getInstance().exeCommand("20", data, false);
        return 0;
    }


    /**
     * 修改密码的属性，改普通密码、超级用户密码
     * @param oldCodeType  该密码原来的 类型 ，02 普通密码，03 超级用户密码
     * @param codeID       密码的id
     * @param newCodeType  新的密码类型 ，02 普通密码，03 超级用户密码; 该字段为空，则表示查询此密码的类型
     */
    public int updateCodeType(String oldCodeType, String codeID, String newCodeType){
        if (oldCodeType.isEmpty() || codeID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        if (oldCodeType.equals(newCodeType)){
            return 0;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        if (newCodeType.equals("02")){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "00";
            BleData.getInstance().exeCommand("07", data, false);
        }
        if (newCodeType.equals("03")){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "01";
            BleData.getInstance().exeCommand("07", data, false);
        }
        if (newCodeType.isEmpty()){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "02";
            BleData.getInstance().exeCommand("07", data, false);
        }
        return 0;
    }

    /**
     * 校验密码
     * @param password 密码
     */
    public int verifyPassword(String password){
        if (password.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + password;
        BleData.getInstance().exeCommand("08", data, true);
        return 0;
    }


    /**
     * 开关门操作
     * @param unlockType 00 表示锁门，01表示开门
     */
    public int toUnlock(String unlockType, String password){
        if (unlockType.isEmpty() || password.isEmpty() || !(unlockType.equals("00") || unlockType.equals("01"))){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + unlockType + password;
        BleData.getInstance().exeCommand("0a", data, true);

        return 0;
    }

    /**
     * 清空数据
     * @param datakType 表示清空数据的类型；
     *                  00 表示清空用户，不会删除管理员
     *                  0e 表示清空所有的绑定手机
     *                  0c 表示恢复出厂设置
     *
     */
    public int cleanData(String datakType){
        if (datakType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //清空绑定的手机
        if (datakType.equals("0e")){
            String data = SinovoBle.getInstance().getLockSNO();
            BleData.getInstance().exeCommand("1b", data, false);
        }else {
            String data = SinovoBle.getInstance().getLockSNO() + datakType;
            BleData.getInstance().exeCommand("0c", data, false);
        }
        return 0;
    }


    //用户取消了绑定
    public void cancelAddLock(){
        if (!isBindMode()){
            return;
        }

        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanAgain(false);
        setBindMode(false);
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
            setScanAgain(false);
            Log.e(TAG, "Bluetooth Adapter is null");
            return -1;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()){
            setScanAgain(false);
            Log.e(TAG, "Bluetooth not enabled");
            return -2;
        }

        if (autoConnectList.isEmpty() && !isBindMode()){
            setScanAgain(false);
            return 1;
        }

        if (BleScanCallBack.getInstance(iScanCallBack).isScanning()){
            Log.e(TAG, "Bluetooth scanning is already underway");
            return 1;
        }

        SinovoBle.getInstance().removeScanHandlerMsg();

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
        }, getScanRepeatInterval());

        BleScanCallBack.getInstance(iScanCallBack).setScanning(true);

        ScanSettings bleScanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)  //扫描到结果，立马报告
                .build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, bleScanSettings, BleScanCallBack.getInstance(iScanCallBack));      //根据指定参数来过滤
        return 0;
    }

    //取消蓝牙扫描的定时任务
    public void removeScanHandlerMsg(){
        if (scanBleHandler !=null) {
            scanBleHandler.removeCallbacksAndMessages(null);    //取消定时任务
        }
    }

    /**
     * 对外提供连接锁的接口函数
     */
    public void connectLock(final BleScanDevice bleScanLock){
        if (!getScanLockList().isEmpty() && !SinovoBle.getInstance().isConnectting() && !SinovoBle.getInstance().isConnected()){
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    SinovoBle.getInstance().connectBle(bleScanLock.GetDevice());
                }
            }, 100);

//        }else {
//            Log.d(TAG, "不符合连接条件，getScanLockList()大小："+ SinovoBle.getInstance().getScanLockList().size()
//                    +", isconnecting:"+ SinovoBle.getInstance().isConnectting() + " isConnected:"+ SinovoBle.getInstance().isConnected());
        }
    }


    /**
     * 连接蓝牙设备
     * @param bluetoothDevice  待连接的设备
     * @return      //是否连接成功
     */
    public boolean connectBle(final BluetoothDevice bluetoothDevice) {
        if (SinovoBle.getInstance().getBluetoothAdapter() == null || bluetoothDevice == null) {
            Log.e(TAG, "Bluetooth Adapter is null");
            return false;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            return false;
        }

        if (SinovoBle.getInstance().isConnectting()){
//            Log.d(TAG, "当前正在连接中，忽略本次的连接请求："+ bluetoothDevice.getAddress());
            return false;
        }

        SinovoBle.getInstance().setConnectting(true);       //标记 已经在开始进行连接
        BleConnCallBack.getInstance().setConnectingMAC(bluetoothDevice.getAddress());       //标记 当前准备连接的地址，以便后面断开进行重连

        //防止连接出现133错误, 不能发现Services
        if (BleConnCallBack.getInstance().getmBluetoothGatt() != null ) {
            Log.d(TAG, "connectDevice: closeGatt");
            BleConnCallBack.getInstance().getmBluetoothGatt().disconnect();
            BleConnCallBack.getInstance().getmBluetoothGatt().close();
            BleConnCallBack.getInstance().setmBluetoothGatt(null);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BleConnCallBack.getInstance().setmBluetoothGatt(bluetoothDevice.connectGatt(getContext(), false, BleConnCallBack.getInstance(), BluetoothDevice.TRANSPORT_LE));
        } else {
            BleConnCallBack.getInstance().setmBluetoothGatt(bluetoothDevice.connectGatt(getContext(), false, BleConnCallBack.getInstance()));
        }

        Log.d(TAG, "connectGatt to："+BleConnCallBack.getInstance().getConnectingMAC());
        return true;
    }

    //对外提供断开连接
    public void disconnBle(){
        BleConnCallBack.getInstance().disConectBle();
    }

    /**
     * 释放ble资源
     */
    public void releaseBle(){
        if (BleConnCallBack.getInstance().getmBluetoothGatt() != null) {
            BleConnCallBack.getInstance().getmBluetoothGatt().close();
            BleConnCallBack.getInstance().setmBluetoothGatt(null);
        }
    }

}
