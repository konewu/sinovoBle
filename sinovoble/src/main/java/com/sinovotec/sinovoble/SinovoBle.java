package com.sinovotec.sinovoble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.callback.BleConnCallBack;
import com.sinovotec.sinovoble.callback.BleScanCallBack;
import com.sinovotec.sinovoble.callback.IConnectCallback;
import com.sinovotec.sinovoble.callback.IScanCallBack;
import com.sinovotec.sinovoble.common.BleCommand;
import com.sinovotec.sinovoble.common.BleConfig;
import com.sinovotec.sinovoble.common.BleConnectLock;
import com.sinovotec.sinovoble.common.BleConstant;
import com.sinovotec.sinovoble.common.BleScanDevice;
import com.sinovotec.sinovoble.common.BluetoothListenerReceiver;
import com.sinovotec.sinovoble.common.ComTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;


public class SinovoBle {
    private String TAG = "SinovoBle";

    private String lockID ;                       //锁的ID，用户输入的，用于添加锁的
    private String lockTypeForAdd;                //锁的类型，用户添加锁时需要指定 要添加的设备类型
    private String phoneIMEI;                     //手机的imei，作为手机id
    private String lockMAC;                       //当前连接锁的蓝牙mac地址
    private String lockSNO;                       //手机与锁进行蓝牙通信使用的 校验码
    private String lockName;                      //锁的名称
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
    private Handler connectTimeoutHandle = new Handler(Looper.getMainLooper());  //连接超时的定时器，连接指定锁响应超时时间

    private ArrayList<BleScanDevice> scanLockList ;         //保存扫描的结果
    private ArrayList<String> bondBleMacList;               //保存在绑定时，已经尝试连接过的锁 ,避免已经尝试过绑定不合适的锁，还会重复绑定
    private ArrayList<BleConnectLock> autoConnectList;      //自动连接的设备列表，内容为mac地址

    @SuppressLint("StaticFieldLeak")
    private static SinovoBle instance;                //入口操作管理
    private static BleConfig bleConfig = BleConfig.getInstance();

    private IScanCallBack mBleScanCallBack;     //蓝牙扫描的回调
    private IConnectCallback mConnCallBack;     //蓝牙连接的回调

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

    public String getLockTypeForAdd() {
        return lockTypeForAdd;
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

    public Handler getConnectTimeoutHandle() {
        return connectTimeoutHandle;
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


    public void setLockID(String lockID) {
        this.lockID = lockID;
    }

    public void setLockTypeForAdd(String lockType) {
        this.lockTypeForAdd = lockType;
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

    public static BleConfig config() {
        return bleConfig;
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

            //蓝牙相关配置修改
            config().setScanTimeout(-1)                 //扫描超时时间，这里设置为永久扫描
                    .setScanRepeatInterval(5 * 1000)    //扫描间隔5秒,扫描5s即停下，然后再扫描
                    .setConnectTimeout(8 * 1000)       //连接超时时间
                    .setOperateTimeout(5 * 1000)        //设置数据操作超时时间
                    .setConnectRetryCount(3)            //设置连接失败重试次数
                    .setConnectRetryInterval(1000)      //设置连接失败重试间隔时间
                    .setOperateRetryCount(3)            //设置数据操作失败重试次数
                    .setOperateRetryInterval(1000)      //设置数据操作失败重试间隔时间
                    .setMaxConnectCount(1);             //设置最大连接设备数量


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
        SinovoBle.getInstance().getBondBleMacList().clear();   //clean the bondBleMacList before starting scan
        SinovoBle.getInstance().setScanAgain(true);

        //绑定模式下，如果
        if (isBindMode()) {
            bindTimeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkScanResult();
                }
            }, 2*60*1000);
        }
        return bleScan(SinovoBle.getInstance().getmBleScanCallBack());
    }

    //绑定超时检测
    private void checkScanResult(){
        if (isBindMode() && !isConnected()){
            Log.d(TAG,"绑定超时检测，需要告知回调");
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("scanResult", "0");
            SinovoBle.getInstance().setScanAgain(false);
            setBindMode(false);
            SinovoBle.getInstance().getmBleScanCallBack().onScanTimeout(JSONObject.toJSONString(map));
        }
    }

    /**
     * * 添加锁，进行绑定锁
     * @param lockqrID  设置锁的二维码
     * @param phoneID   手机的imei
     * @param lockType   准备添加锁的类型，多个类型之前用逗号隔开，如果为空，则过滤所有的锁
     * @param mBleScanCallBack  蓝牙扫描回调的接口
     */
    public void addLock(String lockqrID, String phoneID, String lockType, IScanCallBack mBleScanCallBack, IConnectCallback mConnCallBack){
        SinovoBle.getInstance().setBindMode(true);
        SinovoBle.getInstance().setConnectting(false);
        SinovoBle.getInstance().setLockID(lockqrID);
        SinovoBle.getInstance().setPhoneIMEI(phoneID);

        SinovoBle.getInstance().setmBleScanCallBack(mBleScanCallBack);
        SinovoBle.getInstance().setmConnCallBack(mConnCallBack);

        lockType = lockType.replace("M","");
        lockType = lockType.replace("m","");
        SinovoBle.getInstance().setLockTypeForAdd(lockType);
        SinovoBle.getInstance().startBleScan();
    }

    /**
     * 非绑定模式下，自动连接指定的锁，可以指定多把，蓝牙先扫描到哪一把就连哪一把
     * @param autoConnectList  需要自动连接的锁列表
     * @param mBleScanCallBack  扫描回调
     * @param mConnCallBack     连接回调
     */
    public void autoConnectLock(ArrayList<BleConnectLock> autoConnectList, IScanCallBack mBleScanCallBack, IConnectCallback mConnCallBack){
        SinovoBle.getInstance().setBindMode(false);
        SinovoBle.getInstance().setConnectting(false);
        SinovoBle.getInstance().setmBleScanCallBack(mBleScanCallBack);
        SinovoBle.getInstance().setmConnCallBack(mConnCallBack);
        SinovoBle.getInstance().setAutoConnectList(autoConnectList);
        SinovoBle.getInstance().startBleScan();
    }

    public int  checkEnvir(){
        int result = 0;
        if (!isConnected()){
            Log.d(TAG,"蓝牙未连接");
            result = 1;
        }

        if (SinovoBle.getInstance().getLockSNO() == null){
            Log.d(TAG,"SNO 错误");
            result = -1;
        }

        if (SinovoBle.getInstance().getLockSNO().length() != 6){
            Log.d(TAG,"SNO 错误");
            result = -1;
        }
        return result;
    }

    /**
     * 创建用户,默认创建的是普通用户
     * @param userName
     */
    public int addUser(String userName){
        if (userName.isEmpty() || userName.length()>10){
            Log.d(TAG,"用户名能为空,长度不能超过10个字符");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +ComTool.stringToAscii(userName);
        BleCommand.getInstance().exeCommand("02", data, true);
        return 0;
    }

    /**
     * 编辑用户，修改用户名
     * @param updateData 用户名
     * @param userNID   用户的nid
     */
    public int updateUser(String updateData, String userNID){
        if (updateData.isEmpty() || userNID.isEmpty() || updateData.length()>10 ){
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String updateStr = SinovoBle.getInstance().getLockSNO() +userNID + ComTool.stringToAscii(updateData);
        BleCommand.getInstance().exeCommand("03", updateStr, true);
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
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //添加密码时
        if (dataType.equals("02") || dataType.equals("03")){
            if (password.isEmpty()){
                Log.d(TAG,"输入参数异常");
                return 2;
            }

            String data = SinovoBle.getInstance().getLockSNO() +userNID + dataType + password;
            BleCommand.getInstance().exeCommand("05", data, true);
        }

        //添加卡、指纹、防胁迫指纹
        if (dataType.equals("06") || dataType.equals("07") || dataType.equals("08")){
            String data = SinovoBle.getInstance().getLockSNO() +userNID + dataType ;
            BleCommand.getInstance().exeCommand("05", data, true);
        }
        return 0;
    }

    /**
     * 删除某一项数据，删除一组密码、卡、指纹、绑定
     * @param dataType
     * @param delID
     */
    public int delData(String dataType, String delID){
        if (delID.isEmpty() || dataType.isEmpty()){
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        if (!dataType.equals("0e")){
            String data = SinovoBle.getInstance().getLockSNO() +dataType + delID ;
            BleCommand.getInstance().exeCommand("06", data, true);
        }else {
            //删除绑定
            String data = SinovoBle.getInstance().getLockSNO() + delID ;
            BleCommand.getInstance().exeCommand("1b", data, true);
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
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + userNid + codeType + codeID + newCode;
        BleCommand.getInstance().exeCommand("0d", data, true);
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
     * @param data
     */
    public int setLock(String dataType, String data){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //设置锁的名称
        if (dataType.equals("01")){
            if (data.length() >10 || data.length() == 0){
                Log.d(TAG,"锁的名称不合法");
                return 2;
            }

            String locknameAscii = "";
            if (!lockName.isEmpty()){
                locknameAscii = ComTool.stringToAscii(lockName);
            }
            String setData = SinovoBle.getInstance().getLockSNO() + locknameAscii;
            BleCommand.getInstance().exeCommand("11", setData, true);
        }

        //设置锁的时间
        if (dataType.equals("02")){
            String setData = SinovoBle.getInstance().getLockSNO() + data;
            BleCommand.getInstance().exeCommand("10", setData, true);
        }

        //设置锁的自动锁门时间
        if (dataType.equals("03")){
            String sixteen = "";
            if (!data.isEmpty()){
                if(Integer.valueOf(data) <0 || Integer.valueOf(data)>240){
                    Log.d(TAG,"自动锁门时间范围为 0-240");
                    return 2;
                }

                sixteen = Integer.toHexString(Integer.valueOf(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
            }

            String setData = SinovoBle.getInstance().getLockSNO() + sixteen;
            BleCommand.getInstance().exeCommand("10", setData, true);
        }

        //设置静音 和绑定后自动创建用户
        if (dataType.equals("04") || dataType.equals("05")){
            if (data.isEmpty()){
                data = "02";
            }
            if (Integer.valueOf(data)<0 || Integer.valueOf(data)>2){
                Log.d(TAG,"输入参数有误");
                return 2;
            }

            String setData = SinovoBle.getInstance().getLockSNO() + data;
            if (dataType.equals("04")){
                BleCommand.getInstance().exeCommand("1c", setData, true);
            }else {
                BleCommand.getInstance().exeCommand("09", setData, true);
            }
        }

        //设置超级用户的权限
        if (dataType.equals("06")){
            String setData = SinovoBle.getInstance().getLockSNO() + data;
            BleCommand.getInstance().exeCommand("23", setData, true);
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
            BleCommand.getInstance().exeCommand("12", data, true);
        }

        if (dataType.equals("02")){
            BleCommand.getInstance().exeCommand("0e", data, true);
        }

        if (dataType.equals("03")){
            BleCommand.getInstance().exeCommand("0f", data, true);
        }

        if (dataType.equals("04")){
            BleCommand.getInstance().exeCommand("1a", data, true);
        }

        return 0;
    }

    /**
     * 同步数据，包括用户信息 和绑定的手机
     */
    public int getAllUsers(){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +"00";
        BleCommand.getInstance().exeCommand("13", data, true);
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
        BleCommand.getInstance().exeCommand("13", data, true);

        return 0;
    }

    /**
     * 同步日志
     * @param logID  ，表示当前的日志id ,日志量比较大，所以支持从指定的id开始同步，如果 id为 ff ，则同步所有的日志
     */
    public int getLog(String logID){
        if (logID.isEmpty()){
            Log.d(TAG,"输入参数有误");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }
        String data = SinovoBle.getInstance().getLockSNO() + logID;

        BleCommand.getInstance().exeCommand("17", data, true);

        return 0;
    }

    /**
     * 启用/禁用 动态密码
     * @param enable  00 表示禁用， 01 表示启动
     * @param dynamicCode  对应的 动态密码
     */
    public int doDynamicCode(String enable, String dynamicCode){
        if (dynamicCode.isEmpty() ||!(enable.equals("00") || enable.equals("01"))){
            Log.d(TAG,"输入参数有误");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + enable + dynamicCode;
        BleCommand.getInstance().exeCommand("20", data, true);
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
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        if (oldCodeType.equals(newCodeType)){
            Log.d(TAG,"新旧类型是一样的，无需处理");
            return 0;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        if (newCodeType.equals("02")){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "00";
            BleCommand.getInstance().exeCommand("07", data, true);
        }
        if (newCodeType.equals("03")){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "01";
            BleCommand.getInstance().exeCommand("07", data, true);
        }
        if (newCodeType.isEmpty()){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "02";
            BleCommand.getInstance().exeCommand("07", data, true);
        }
        return 0;
    }

    /**
     * 校验密码
     * @param password 密码
     */
    public int verifyPassword(String password){
        if (password.isEmpty()){
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + password;
        BleCommand.getInstance().exeCommand("08", data, true);
        return 0;
    }


    /**
     * 开关门操作
     * @param unlockType 00 表示锁门，01表示开门
     */
    public int toUnlock(String unlockType, String password){
        if (unlockType.isEmpty() || password.isEmpty() || !(unlockType.equals("00") || unlockType.equals("01"))){
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + unlockType + password;
        BleCommand.getInstance().exeCommand("0a", data, true);

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
            Log.d(TAG,"输入参数异常");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //清空绑定的手机
        if (datakType.equals("0e")){
            String data = SinovoBle.getInstance().getLockSNO();
            BleCommand.getInstance().exeCommand("1b", data, true);
        }else {
            String data = SinovoBle.getInstance().getLockSNO() + datakType;
            BleCommand.getInstance().exeCommand("0c", data, true);
        }
        return 0;
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
    public void connectLock(){
        if (!getScanLockList().isEmpty() && !SinovoBle.getInstance().isConnectting() && !SinovoBle.getInstance().isConnected()){
            BleScanDevice bleScanLock = SinovoBle.getInstance().getScanLockList().get(0);
            Log.d(TAG, "调用tryBindBle 准备绑定ble设备："+ bleScanLock.GetDevice().getAddress());
            SinovoBle.getInstance().connectBle(bleScanLock.GetDevice());

        }else {
            Log.d(TAG, "不符合连接条件，getScanLockList()大小："+ SinovoBle.getInstance().getScanLockList().size()
                    +", isconnecting:"+SinovoBle.getInstance().isConnectting() + " isConnected:"+SinovoBle.getInstance().isConnected());
        }
    }


    /**
     * 连接蓝牙设备
     * @param bluetoothDevice  待连接的设备
     * @return      //是否连接成功
     */
    public boolean connectBle(BluetoothDevice bluetoothDevice) {
        if (SinovoBle.getInstance().getBluetoothAdapter() == null || bluetoothDevice == null) {
            Log.w(TAG, "BluetoothAdapter 没有初始化或是没有指定地址.");
            return false;
        }

        if (SinovoBle.getInstance().isConnectting()){
            Log.d(TAG, "当前正在连接中，忽略本次的连接请求："+ bluetoothDevice.getAddress());
            return false;
        }

        SinovoBle.getInstance().setConnectting(true);       //标记 已经在开始进行连接
        BleConnCallBack.getInstance().setConnectingMAC(bluetoothDevice.getAddress());       //标记 当前准备连接的地址，以便后面断开进行重连
        BleConnCallBack.getInstance().setmBluetoothGatt(bluetoothDevice.connectGatt(getContext(), false, BleConnCallBack.getInstance()));

        Log.d(TAG, "调用connectGatt 来建立蓝牙连接,连接设备："+BleConnCallBack.getInstance().getConnectingMAC());

        //8秒后 检测，连接是否有回应
        connectTimeoutHandle.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkConnectTime();
            }
        }, bleConfig.getConnectTimeout());
        return true;
    }

    /**
     * 连接超时处理
     */
    private void checkConnectTime(){
        Log.d(TAG, "连接超时检测,开始连接"+ bleConfig.getConnectTimeout() +" 毫秒都没反应，需要设置为未连接");
        setConnectting(false);
        setConnected(false);
    }

    //对外提供断开连接
    public void disconnBle(){
        BleConnCallBack.getInstance().disConectBle();
    }

    /**
     * 释放ble资源
     */
    public void releaseBle(){
        if (BleConnCallBack.getInstance().getmBluetoothGatt() != null) { BleConnCallBack.getInstance().getmBluetoothGatt().disconnect(); }
        if (BleConnCallBack.getInstance().getmBluetoothGatt() != null) { BleConnCallBack.getInstance().getmBluetoothGatt().close(); }
        if (BleConnCallBack.getInstance().getmBluetoothGatt() != null) { BleConnCallBack.getInstance().setmBluetoothGatt(null);}
    }

}
