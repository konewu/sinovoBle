package com.sinovotec.sinovoble.common;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.UUID;

import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.callback.BleConnCallBack;

import static com.sinovotec.sinovoble.common.ComTool.asciiToString;
import static com.sinovotec.sinovoble.common.ComTool.toByte;

public class BleData {

    private LinkedList<String> commandList = new LinkedList<>();      //所有需要执行的命令 都放入到list中排队 ，等待执行
    private static BleData instance;             //入口操作管理
    private boolean isExeCmding = false;          //是否正在执行命令
    private static String TAG = "SinovoBle";
  //  private int cmdRetry = 0;

    public static BleData getInstance() {
        if (instance == null) {
            synchronized (BleData.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new BleData();
                }
            }
        }
        return instance;
    }

    /**
     * 查询是否正在执行命令
     * @return boolean
     */
    public boolean isExeCmding() {
        return isExeCmding;
    }

    /**
     * 获取当前的命令队列
     * @return LinkedList
     */
    public LinkedList<String> getCommandList() {
        return commandList;
    }

    /**
     * 设置是否正在执行命令
     * @param exeCmding boolean
     */
    public void setExeCmding(boolean exeCmding) {
        isExeCmding = exeCmding;
    }

    /**
     * 计算出命令的 checksum值
     * @param data  待计算的命令
     * @return   计算出来的checksum值, 需要注意，计算出来的结果 需要取最后两位数字
     */
    private static String checkSum(String data){
        int checksum = 0;    //计算校验和

        String returnStr = "";

        for (int i=0; i< data.length(); ){
            if (i+2 > data.length()){
//                Log.d(TAG, "要进行计算的子字符串超过了 数组的大小，异常了， i+2："+(i+2) + "字符串大小："+data.length());
               return "";   //计算错误，
            }
            String sub_str = data.substring(i, i + 2);
            checksum += Integer.parseInt(sub_str, 16);      //16进制字符转10进制的整数
            i = i+2;
        }

        //将十进制转换为 16进制字符串返回
        String hexStr = Integer.toHexString(checksum);
        if (hexStr.length() >2){
            returnStr = hexStr.substring(hexStr.length()-2);
        }

        if (hexStr.length() == 2){
            returnStr = hexStr;
        }

        if (hexStr.length() <2){
            returnStr = "0"+hexStr;
        }

        return  returnStr;
    }


    /**
     * 接受到锁端发过来的数据，进行checksum检验，是否合法
     * @param data s
     * @return b
     */
    private boolean checkSumIsOK(String data){
        String sub_str = data.substring(0, data.length()-2);
        String sum_checksum = data.substring(data.length()-2);
        String checksum_calc = checkSum(sub_str);

        return checksum_calc.equals(sum_checksum);
    }

    /**
     * 处理从ble端发过来的数据
     * @param data s
     * @return String 返回数据格式如下
     * 错误时，返回 errCode 为"-1"，表示数据长度超过20字节
     * 返回 errCode 为 -2 ，checksum 校验失败
     * 返回 errCode 为 -3 ，非fe、fc开头的非法数据
     * 返回 errCode 为 -4 ，推送的日志，蓝牙连接时，暂时定义无法处理推送日志
     * 返回 errCode 为 -5 ，解密数据异常
     * 返回 errCode 为 -6 ，有效数据长度超过16个字节
     */
    public LinkedHashMap getDataFromBle(String data){
        Log.d(TAG, "getDataFromBle 接收的数据："+data);

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("getData", "0");

        //非20字节的数据不处理
        if (data.length() !=40){
//            Log.d(TAG, "数据长度不为40，不是20字节，不处理");
            map.put("errCode", "-1");
            return map;
        }

        //1、先检验checksum 是否ok
        if (!checkSumIsOK(data)){
//            Log.d(TAG, "checksum 校验失败，直接退出");
            map.put("errCode", "-2");
            return map;
        }

        //2、检测是否为fe开头的数据，非fe、fc开头则先不处理
        String headCode = data.substring(0,2);
        if (!(headCode.equals("fe") || headCode.equals("fc"))){
//            Log.d(TAG, "ble发送过来数据包头部不是fe、fc，非法数据不处理");/
            map.put("errCode", "-3");
            return map;
        }

        String funcodebt = data.substring(2,4);
        if (funcodebt.equals("27")){
//            Log.d(TAG, "锁端发送功能码为27的推送数据，暂时无需处理此数据");
            map.put("errCode", "-4");
            return map;
        }

        //收到回复，表示命令执行完成，将此命令从队列中删除
        if (!getCommandList().isEmpty()){
            String funcodelist = getCommandList().getFirst();
            funcodelist = funcodelist.substring(2,4);

            Log.d(TAG, "锁端返回数据的功能码："+funcodebt + ",命令队列中第一个命令的功能码："+funcodelist);

            //需要注意，如果是同步数据或是日志，必须同步完成才能执行下一个命令，否则容易导致失败连接断开
            if (funcodebt.equals("13") || funcodebt.equals("17")){
                setExeCmding(false);
                Log.d(TAG, "正在同步，需要同步完成才能执行下一个命令");
            }else {
              if (funcodebt.equals(funcodelist) || (funcodebt.equals("14") && funcodelist.equals("13")) || funcodebt.equals("18") && funcodelist.equals("17")) {
                  Log.d(TAG, "确认过眼神，就是你了，现在可删除");
                  getCommandList().removeFirst();
                  setExeCmding(false);
                  sendDataToBle();
              }else {
                  Log.e(TAG,"命令对不上：锁端发过来的："+funcodebt + ",本地命令队列中的："+ funcodelist + "，可以处理此数据，但不自动执行下一条命令，也不删除队列的命令");
              }
            }

        }

        //3、获取出来功能码 , 并进行解密处理
        String funCode = data.substring(2,4);
        String dataEncrypt = data.substring(6,data.length()-2);
        String dataDecode ;

        //非绑定功能的数据，需要解密
        if (!funCode.equals("00")){
          //  dataDecode = Aes128.decryptData(dataEncrypt, SinovoBle.getInstance().getLockMAC());
            String mac = SinovoBle.getInstance().getLockMAC().replace(":","");
            dataDecode = SinovoBle.getInstance().getMyJniLib().decryptAes(dataEncrypt, mac);
        }else {
            dataDecode = dataEncrypt;
        }

        //异常情况，解密为空
        if (dataDecode.isEmpty()){
            map.put("errCode", "-5");
            return map;
        }

        //4、获取出来实际的有效数据
        String dataLen_hex = data.substring(4,6);
        int dataLen = Integer.valueOf(dataLen_hex,16);  //转为10进制

        if (dataLen >16){
            map.put("errCode", "-6");
            return map;
        }

        String datavalue = dataDecode.substring(0,dataLen*2);

        //绑定的数据处理
        if (funCode.equals("00")) { return bindPhone(datavalue); }

        //绑定后登录的数据处理
        if (funCode.equals("01")) { return loginAfterBond(datavalue); }

        //创建新用户的数据处理
        if (funCode.equals("02")) { return addNewUser(datavalue); }

        //修改用户名
        if (funCode.equals("03")) { return editUsername(datavalue); }

        //添加数据成功返回值处理
        if (funCode.equals("05")) { return createdData(datavalue); }

        //删除数据返回值处理
        if (funCode.equals("06")) { return delData(datavalue); }

        //修改密码类型
        if (funCode.equals("07")) { return changeCodeType(datavalue); }

        //密码验证，即登录
        if (funCode.equals("08")) { return loginResult(datavalue); }

        //绑定成功后自动创建用户
        if (funCode.equals("09")) { return isAutoCreate(datavalue); }

        //清空用户、密码、卡、指纹、日志、恢复出厂设置
        if (funCode.equals("0c")) { return clearData(datavalue); }

        //校准时间
        if (funCode.equals("10")) {return calcTime(datavalue); }

        //开关门结果
        if (funCode.equals("0a")) {return openOrClose(datavalue);}

        //修改密码的结果
        if (funCode.equals("0d")) {return changeCode(datavalue);}

        //查询电量
        if (funCode.equals("0e")) {return checkPower(datavalue);}

        //查看门锁状态
        if (funCode.equals("0f")) {return lockStatus(datavalue);}

        //设置锁的蓝牙名称
        if (funCode.equals("11")) {return setLockName(datavalue);}

        //查询管理员是否存在
        if (funCode.equals("12")) {return checkAdmin(datavalue);}

        //同步用户数据、绑定手机
        if (funCode.equals("13")) {return syncData(datavalue);}

        //同步结束
        if (funCode.equals("14")) {return syncDataFinish(datavalue);}

        //获取数据的最新修改时间
        if (funCode.equals("15")) {return getLastFixedTime(datavalue);}

        //设置自动锁门时间
        if (funCode.equals("16")) {return autolocktime(datavalue);}

        //日志同步
        if (funCode.equals("17")) {return syncLog(datavalue);}

        //日志同步结束
        if (funCode.equals("18")) {return syncLogFinish(datavalue);}

        //查看锁的固件版本
        if (funCode.equals("1a")) {return checkFirmware(datavalue);}

        //删除蓝牙绑定
        if (funCode.equals("1b")) {return delBoundPhone(datavalue);}

        //查看、设置静音模式
        if (funCode.equals("1c")) {return muteMode(datavalue);}

        //查看、设置基准时间
        if (funCode.equals("1f")) {return baseTime(datavalue);}

        //禁用启用 分享密码
        if (funCode.equals("20")) {return operateSharedCode(datavalue);}

        //查询锁端mac地址
        if (funCode.equals("21")) {return getLockMAC(datavalue);}

        //查看、设置超级用户权限
        if (funCode.equals("23")) {return checkSuperUser(datavalue);}
        return map;
    }


    /**
     * 发送数据 到 ble端；
     * 1、需要计算出有效数据的长度，不足16字节的需要在后面补f
     * 2、需要对数据进行加密
     * 3、计算checksum ，并组成20字节的最终命令
     * @param funcode String
     * @param data String
     */
    public void exeCommand(String funcode, String data, boolean toTop){
        String data_send = data;
        int byteLen = data.length() /2;
        byteLen += data.length() %2;

        //如果data 不够16字节，则在后面补ff
        for (int i=32;i> data.length(); i--) {
            data_send += "f";
        }
        Log.d(TAG, "需要发送的数据，在补f之后："+data_send + ",长度："+data_send.length());

        //加密处理
        if (!funcode.equals("00")){
            String mac = SinovoBle.getInstance().getLockMAC().replace(":","");
            data_send = SinovoBle.getInstance().getMyJniLib().encryptAes(data_send,mac);
            //data_send = encryptData(data_send, SinovoBle.getInstance().getLockMAC());
        }

        Log.d(TAG, "需要发送的数据，加密后："+data_send);

        String data_result = "fe" +funcode ;
        if (byteLen<16){
            data_result = data_result + "0"+ Integer.toHexString(byteLen) + data_send;
        }else {
            data_result = data_result + Integer.toHexString(byteLen) + data_send;
        }

//        data_result = data_result + data_result+ data_result+ data_result+ data_result+data_result + data_result+ data_result+ data_result+ data_result +data_result + data_result+ data_result+ data_result;
//        data_result = "fe1203453d3534921c6acd0461c06b8685fe1203453d3534921c6acd0461c06b0ebf8685fe1203453d3534921c6acd0461c06b0ebf8685fe1203453d3534921c6acd0461c06b0ebf8685fe1203453d3534921c6acd0461c06b0ebf8685fe1203453d3534921c6acd0461c06b0ebf8685fe1203453d3534921c6acd04634532";
//        Log.d(TAG,"加大内容试试：" + data_result);

        data_result = data_result +checkSum(data_result);

        //先判断 此命令是否已经存在队列中，如果已经存在，则不再加入
        if (!commandList.contains(data_result)){
            //命令需要查到队首
            if (toTop){
                if (commandList.size()>0){
                    commandList.add(1,data_result);     //如果队列中已经有命令，则插入到 index 为1的位置，因为index为0 表示是正在执行的命令，不能被中断
                }else {
                    commandList.add(data_result);
                }
            }else{
                commandList.add(data_result);
            }
        }else {
            Log.d(TAG, "命令：" + data_result +"已经存在，无需重复添加");
        }

        //如果当前没有正在执行命令
        if (!isExeCmding()){
            if (SinovoBle.getInstance().isBindMode() || (!SinovoBle.getInstance().isBindMode() && SinovoBle.getInstance().isConnected())){
                Log.d(TAG, "连接成功，且状态为非正在发送命令的状态，可以发送命令");
                sendDataToBle();
            }
        }else {
            Log.e(TAG, "当前正在执行命令。忽略，不执行");
        }
    }

    /**
     *  重新起一个子进程来执行 发送命令的活
     */
    private void sendDataToBle(){
        if (getCommandList().isEmpty()){
            return;
        }

        if (getCommandList().getFirst() == null){
            return;
        }

        if (isExeCmding()){
            return;
        }

        Log.d(TAG,"bledata 发送指令："+getCommandList().getFirst());

        setExeCmding(true);
        final  byte[] write_msg_byte = toByte(getCommandList().getFirst());

        SinovoBle sinovoBle = SinovoBle.getInstance();
        if (sinovoBle.getBleServiceUUID() != null && sinovoBle.getBlecharacteristUUID() != null ){
            final UUID uuid_service = UUID.fromString(sinovoBle.getBleServiceUUID());
            final UUID uuid_characterics = UUID.fromString(sinovoBle.getBlecharacteristUUID());

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    BleConnCallBack.getInstance().writeCharacteristic(write_msg_byte, uuid_service, uuid_characterics);
                }
            }, 200);

        }else {
            Log.d(TAG,"UUID 为空，异常了,断开连接");
            BleConnCallBack.getInstance().disConectBle();
        }
    }

    /**
     * 命令发送之后，2秒后进行检查，是否受到恢复
     */
    public  void checkDataReceive(){
        Log.e(TAG, "发送命令完成后，1.5秒没有收到回复，现在检查是否需要重试或是删除");

        setExeCmding(false);
        SinovoBle.getInstance().getmConnCallBack().onReceiveDataFailed();
    }

    /**
     * 处理手机绑定请求的数据
     * @param datavalue
     * 0xFE 0x00 LEN MAC SNO ERR_CODE checksum
     * @return String 格式如下：
     * xx,yy
     * xx 为错误码
     * yy 为返回的内容, 具体内容的定义，在不同的协议上是不一样的
     * 在本协议中， yy定义如下
     * mac,sno
     */
    private LinkedHashMap bindPhone(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "00");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
//        Log.d(TAG,"手机绑定的错误码："+errCode);

        //绑定成功
        if (errCode.equals("00") || errCode.equals("0b")){

            //绑定成功，退出绑定模式 停止扫描
            SinovoBle.getInstance().setBindMode(false);
            SinovoBle.getInstance().setScanAgain(false);

            //长度为1个字节时，为等待用户确认的回应
            if (len == 2){
                Log.d(TAG,"Waiting for user's confirmation");
                return map;
            }

            //绑定成功后，取消绑定超时检测
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);

            String bleMac = datavalue.substring(0,12);
            String bleSno = datavalue.substring(12,18);

            SinovoBle.getInstance().setConnected(true);
            SinovoBle.getInstance().setLockMAC(bleMac);
            SinovoBle.getInstance().setLockSNO(bleSno);

            //清空自动连接的列表，或许断开只能重连它，不能连接其他设备，除非手动切换
            SinovoBle.getInstance().getAutoConnectList().clear();
            StringBuilder lockMac = new StringBuilder();
            for (int j=0;j<bleMac.length();){
                if (j < bleMac.length() - 2) {
                    lockMac.append(bleMac.substring(j, j + 2)).append(":");
                }else{
                    lockMac.append(bleMac.substring(j, j + 2));
                }
                j = j + 2;
            }
            BleConnectLock myAutoConnectLock = new BleConnectLock(lockMac.toString().toUpperCase(), bleSno);
            SinovoBle.getInstance().getAutoConnectList().add(myAutoConnectLock);

            Log.d(TAG,"绑定成功，blemac："+bleMac + ",bleSno:"+bleSno);

            //生成随机密码进行登录
            String rndCode = ComTool.getRndNumber(6,9,10);
            String data = bleSno + SinovoBle.getInstance().getPhoneIMEI() + rndCode;
            exeCommand("01", data, false);
            exeCommand("11", bleSno, false);     //查询锁的名称
            exeCommand("1a", bleSno, false);     //查询锁的固件版本

            //同步锁端的时间
            String nowtime = ComTool.getSpecialTime("",1,1,0);
            nowtime = nowtime.replace(":","").replace(" ","").replace("-","");
            nowtime = nowtime.substring(2);
            data = bleSno + nowtime;
            exeCommand("10", data, false);       //校准锁端的时间

            map.put("lockMac", bleMac);
            map.put("lockSno", bleSno);
            return map;
        }

        //绑定时，用户没有按set导致超时 失败，也需要 取消 定时检测
        if (errCode.equals("04")){
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        return map;
    }

    /**
     * 绑定成功后进行登录的数据处理
     * @param datavalue
     * 0xFE 0x01 LEN ALLOW  NID TYPE ID PASSWORD ERR_CODE checksum
     * @return String 格式如下：
     * xx,yy
     * xx 为错误码
     * yy 为返回的内容, 具体内容的定义，在不同的协议上是不一样的
     * 在本协议中， yy定义如下
     * mac,sno
     */
    private  LinkedHashMap loginAfterBond(String datavalue) {
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "01");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }

        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
        map.put("lockSno", SinovoBle.getInstance().getLockSNO());
        map.put("lockID", SinovoBle.getInstance().getLockID());

        if (errCode.equals("00")){
            BleData.getInstance().exeCommand("1f", SinovoBle.getInstance().getLockSNO(), false); //查询基准时间
            String allow = datavalue.substring(0,2);
            map.put("autoCreateUser", allow);
            if (allow.equals("00") && len < 6){
                return map;
            }else {
                String nid   = datavalue.substring(2,4);
                String type  = datavalue.substring(4,6);
                String codeid  = datavalue.substring(6,8);
                String password = datavalue.substring(8,len-2);
                password = password.replace("f","");   //密码长度为奇数时，去掉补的f
                map.put("userNid", nid);
                map.put("codeType", type);
                map.put("codeID", codeid);
                map.put("code", password);
                return map;
            }
        }else {
//            Log.d(TAG, "登录失败");
            return map;
        }
    }

    /**
     * 添加用户的返回数据处理
     * @param datavalue
     * 0xFE 0X02 LEN NID USERNAME ERR_CODE checksum
     */
    private LinkedHashMap addNewUser(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "02");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"添加用户的返回的错误码："+errCode);

        if (errCode.equals("00")) {
            String nid  = datavalue.substring(0, 2);
            String username = datavalue.substring(2, len-2);
            username = ComTool.asciiToString(username);
            map.put("userNid", nid);
            map.put("username",username);
//            Log.d(TAG,"添加用户成功， nid："+ nid +",用户名："+ username);
        }
        return map;
    }


    /**
     * 修改用户名
     * @param datavalue
     * 0xFE 0X03 LEN NID USERNAME ERR_CODE checksum
     */
    private LinkedHashMap editUsername(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "03");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"修改用户名的返回的错误码："+errCode);

        if (errCode.equals("00")) {
            String nid  = datavalue.substring(0, 2);
            String username = datavalue.substring(2, len-2);
            username = ComTool.asciiToString(username);
            map.put("userNid", nid);
            map.put("username",username);
//            Log.d(TAG,"修改用户名成功， nid："+ nid +",用户名："+ username);
        }
        return map;
    }

    /**
     * 创建数据成功的返回数据处理
     * @param datavalue
     * 0xFE 0X05 LEN NID TYPE ID PASSWORD/CARDID/指纹ID ERR_CODE checksum
     */
    private LinkedHashMap createdData(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "05");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }

        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"创建数据成功的返回的错误码："+errCode);

        if (errCode.equals("00")) {
            if (datavalue.length() <6){
//                Log.d(TAG,"进入添加卡指纹模式的");
                return map;
            }
            String nid  = datavalue.substring(0, 2);
            String type = datavalue.substring(2, 4);
            String sid  = datavalue.substring(4, 6);
            String data = datavalue.substring(6, len-2);

//            Log.d(TAG,"创建数据成功，返回内容 nid："+ nid + ", type:"+ type + ", sid:" + sid +", data:" + data);

            if (type.equals("01")||type.equals("02")||type.equals("03") || type.equals("05")){
                data = data.replace("f","");
            }

            map.put("userNid", nid);
            map.put("dataType", type);
            map.put("sid", sid);
            map.put("data", data);
        }

        //容量满
        if (errCode.equals("09")) {
            if (datavalue.length() > 4) {
                String nid = datavalue.substring(0, 2);
                String type = datavalue.substring(2, 4);
                map.put("userNid", nid);
                map.put("dataType", type);
            }
        }

        //信息重复
        if (errCode.equals("0b")) {
            if (datavalue.length() > 4) {
                String userNid = datavalue.substring(0, 2);
                String dataType = datavalue.substring(2, 4);
                map.put("userNid", userNid);
                map.put("dataType", dataType);
            }
        }

        return map;
    }

    /**
     * 删除数据成功的返回数据处理
     * @param datavalue
     * 0xFE 0x06 LEN TYPE ID ERR_CODE checksum
     */
    private LinkedHashMap delData(String datavalue) {
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "06");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"删除数据返回的错误码："+errCode);

        if (errCode.equals("00")) {
            String type     = datavalue.substring(0, 2);
            String sid      = datavalue.substring(2, 4);

            map.put("dataType", type);
            map.put("sid", sid);
//            Log.d(TAG,"删除数据返回内容 type:"+ type + ", sid:" + sid );
        }
        return map;
    }

    /**
     * 修改密码的类型 ； 修改超级用户/普通用户的属性
     * @param datavalue
     * 0xFE 0X07 LEN TYPE ID ERR_CODE checksum
     */
    private LinkedHashMap changeCodeType(String datavalue) {
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "07");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"修改密码的类型返回的错误码："+errCode);

        if (errCode.equals("00")) {
            String type  = datavalue.substring(0, 2);
            String sid = datavalue.substring(2, 4);
            map.put("dataType", type);
            map.put("sid", sid);
//            Log.d(TAG,"修改密码的类型返回内容 type:"+ type + ", sid:" + sid );
        }
        return map;
    }


    /**
     * 密码验证,登录
     * @param datavalue
     * 0xFE 0X08 LEN TYPE NID  ID  password ERR_CODE checksum
     */
    private LinkedHashMap loginResult(String datavalue) {
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "08");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"登录 密码验证的错误码："+errCode);

        if (errCode.equals("00")) {

            if (datavalue.length() <6){
//                Log.d(TAG,"密码验证,登录失败，返回内容不合法："+datavalue);
                return map;
            }

            String type = datavalue.substring(0, 2);
            String nid  = datavalue.substring(2, 4);
            String sid  = datavalue.substring(4, 6);
            String code = datavalue.substring(6, len-2);
            code = code.replace("f","");

            map.put("codeType", type);
            map.put("userNid",nid);
            map.put("codeID",sid);
            map.put("code",code);

//            Log.d(TAG,"登录 密码验证的返回内容，type："+type + " ,nid:"+nid + " ,sid:"+sid + " ,code"+code);
        }
        return map;
    }


    /**
     * 绑定成功之后 是否自动创建用户
     * @param datavalue
     * 0xFE 0X09 LEN ENABLED ERR_CODE checksum
     */
    private LinkedHashMap isAutoCreate(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "09");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"绑定成功之后 是否自动创建用户的错误码："+errCode);

        if (errCode.equals("00")) {
            String enableAuto = datavalue.substring(0, 2);
            map.put("enable", enableAuto);
        }
        return map;
    }


    /**
     * 开关门的返回结果
     * @param datavalue
     * 0xFE 0x0a LEN OP_TYPE TYPE ERR_CODE checksum
     */
    private LinkedHashMap openOrClose(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "0a");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"开关门的返回结果的错误码："+errCode);

        if (errCode.equals("00")) {
            String optype = datavalue.substring(0, 2);
            String codetype = datavalue.substring(2, 4);
            map.put("opType", optype);
            map.put("codeType", codetype);
        }
        return map;
    }

    /**
     * 清空数据，用户、密码、卡、指纹、日志，恢复出厂设置
     * @param datavalue
     * 0xFE 0x0c LEN TYPE ERR_CODE checksum
     */
    private LinkedHashMap clearData(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "0c");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"清空数据的错误码："+errCode);

        if (errCode.equals("00")) {
            String dataType = datavalue.substring(0, 2);
            map.put("dataType", dataType);
        }
        return map;
    }

    /**
     * 修改密码的返回结果
     * @param datavalue
     * 0xFE 0x0d LEN NID TYPE ID PASSWORD ERR_CODE checksum
     */
    private LinkedHashMap changeCode(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "0d");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"修改密码返回的错误码："+errCode);

        if (errCode.equals("00")) {
            if (datavalue.length() <6){
//                Log.d(TAG,"修改密码失败，返回内容不合法："+datavalue);
                return map;
            }
            String nid      = datavalue.substring(0, 2);
            String codetype = datavalue.substring(2, 4);
            String sid      = datavalue.substring(4, 6);
            String code     = datavalue.substring(6, len-2);
            code = code.replace("f","");

            map.put("userNid", nid);
            map.put("codeType", codetype);
            map.put("sid", sid);
            map.put("code", code);

//            Log.d(TAG,"修改的结果，修改的nid:"+nid + ",TYPE:"+codetype + ",SID:"+sid + ",code:"+code);
        }
        return map;
    }


    /**
     * 查看电量的结果返回处理
     * @param datavalue
     * 0xFE 0x0E LEN VALUE ERR_CODE checksum
     */
    private LinkedHashMap checkPower(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "0e");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"查看电量的结果返回处理的错误码："+errCode);

        if (errCode.equals("00")) {
            String power = datavalue.substring(0, 2);
            int power_int = Integer.parseInt(power, 16);
//            Log.d(TAG,"当前电量："+power_int);
            map.put("power", power_int);
        }
        return map;
    }


    /**
     * 查看门锁状态
     * @param datavalue
     * 0xFE 0x0F LEN VALUE ERR_CODE checksum
     */
    private LinkedHashMap lockStatus(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "0f");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"查看门锁状态的错误码："+errCode);

        if (errCode.equals("00")) {
            String lockStatus = datavalue.substring(0, 2);
            map.put("lockStatus", lockStatus);
        }
        return map;
    }

    /**
     * 校准/查询时间
     * @param datavalue
     * 0xFE 0x10 LEN YY MM DD HH MM SS ERR_CODE checksum
     */
    private LinkedHashMap calcTime(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "10");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
//        Log.d(TAG,"校准/查询时间的错误码："+errCode);

        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
        map.put("lockSno", SinovoBle.getInstance().getLockSNO());
        map.put("lockName", SinovoBle.getInstance().getLockName());

        if (errCode.equals("00")) {
            String locktime = datavalue.substring(0, len-2);

//            Log.d(TAG,"得到锁端的时间："+locktime);
            locktime = ComTool.getEDate(locktime, 0, false, true,true);   //转换时间格式
            map.put("lockTime",locktime);
        }

        return map;
    }

    /**
     * 设置锁的蓝牙名称
     * @param datavalue
     * 0xFE 0x11 LEN NAME ERR_CODE checksum
     */
    private LinkedHashMap setLockName(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "11");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);

//        Log.d(TAG,"设置锁的蓝牙名称的错误码："+errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

        if (errCode.equals("00")) {
            String lockname = datavalue.substring(0,len-2);
            lockname = ComTool.asciiToString(lockname);
//            Log.d(TAG,"设置锁的蓝牙名称:"+ lockname);
            map.put("lockName", lockname);
            SinovoBle.getInstance().setLockName(lockname);
        }
        return map;

    }

    /**
     * 查询管理员密码是否存在
     * @param datavalue
     * 0xFE 0x12 LEN  NID ID password ERR_CODE checksum
     */
    private LinkedHashMap checkAdmin(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "12");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"查询管理员是否存在的错误码："+errCode);

        if (errCode.equals("00")) {
            if (datavalue.length() <6){
//                Log.d(TAG,"查询管理员密码失败，返回内容不合法："+datavalue);
                return map;
            }
            String nid = datavalue.substring(0,2);
            String sid = datavalue.substring(2,4);
            String pass = datavalue.substring(4,len-2);
            pass = pass.replace("f","");

            map.put("userNid", nid);
            map.put("sid", sid);
            map.put("code", pass);

//            Log.d(TAG,"管理员存在，其信息 nid:"+ nid + ",sid："+sid +",password:"+pass);
        }
        return map;
    }

    /**
     * 同步用户数据、绑定手机数据
     * @param datavalue
     * 0xFE 0x13 LEN TYPE NID ID USERNAME ERR_CODE checksum
     * 0xFE 0x13 LEN TYPE NID ID PASSWORD ERR_CODE checksum
     * 0xFE 0x13 LEN TYPE NID ID CARDID ERR_CODE checksum
     * 0xFE 0x13 LEN TYPE NID ID FINGERID ERR_CODE checksum
     * 0xFE 0x13 LEN TYPE NID ID PHONE_ID ERR_CODE checksum
     */
    private LinkedHashMap syncData(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "13");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

        if (errCode.equals("00")) {
            if (datavalue.length() <6){
//                Log.d(TAG,"同步用户数据失败，返回内容不合法："+datavalue);
                return map;
            }
            String dataNID,storeID,syncData,dataType;
            dataType = datavalue.substring(0, 2);
            dataNID  = datavalue.substring(2, 4);
            storeID  = datavalue.substring(4, 6);
            syncData = datavalue.substring(6, len-2);

            //用户名由ascii码 转 字符串
            if (dataType.equals("00")){
                syncData = asciiToString(syncData);
            }

            map.put("dataType", dataType);
            map.put("userNid", dataNID);
            map.put("sid", storeID);
            map.put("syncData", syncData);
        }
        return map;
    }

    /**
     * 数据同步结束
     * @param datavalue
     * 0xFE 0x14  LEN  TYPE  USER_MTIME  ERR_CODE checksum
     */
    private LinkedHashMap syncDataFinish(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "14");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

        if (errCode.equals("00")) {
            String dataType = datavalue.substring(0, 2);
            String fixTime  = datavalue.substring(2, len-2);

            map.put("dataType", dataType);
            map.put("fixTime", fixTime);
        }
        return map;
    }


    /**
     * APP获取锁端用户、已绑定手机的最新修改时间信息
     * @param datavalue
     * 0xFE 0x15 LEN TYPE COUNT YY MM DD HH MM SS ERR_CODE checksum
     */
    private LinkedHashMap getLastFixedTime(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "15");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

        if (errCode.equals("00")) {
            String dataType = datavalue.substring(0, 2);
            String fixTime  = datavalue.substring(4, len-2);

            map.put("dataType", dataType);
            map.put("fixTime", fixTime);
        }
        return map;
    }


    /**
     * 修改自动锁门时间
     * @param datavalue
     * 0xFE 0x16 LEN TIME ERR_CODE checksum
     */
    private LinkedHashMap autolocktime(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "16");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"修改自动锁门时间的错误码："+errCode);

        if (errCode.equals("00")) {
            String datatime = datavalue.substring(0, 2);
            map.put("autoLockTime", datatime);
        }
        return map;
    }


    /**
     * 日志同步
     * @param datavalue
     * 0xFE 0x17 LEN ID LOG_TYPE YY MM DD HH mm CONTENT checksum
     * ID 日志的存储id
     * LOG_TYPE 日志类型，09为开门日志， 0a为告警日志， 0b为操作日志
     * YY MM DD HH mm 日志的时间， 精确到分钟
     * CONTENT 为日志的内容
     * 如果
     * 1、开门日志时，CONTENT 由 TYPE、PASSWORD/CardID/FingerID 组成
     * 2、告警日志时，CONTENT 由 WARM_TYPE 组成, 1个字节
     * 3、操作日志时，CONTENT 由 OP_TYPE、OP_Content 组成；其中 OP_TYPE表示操作类型，1字节； OP_Content 6字节
     *
     */
    private LinkedHashMap syncLog(String datavalue){

        Log.w(TAG,"同步过来的日志，已解密的内容："+ datavalue);
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "17");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }

        if (len <14){
//            Log.d(TAG, "同步过来的日志 格式不正确，不处理，退出");
            String errCode = datavalue.substring(len-2, len);
            map.put("errCode", errCode);
            return map;
        }

        String logID    = datavalue.substring(0, 2);
        String logType  = datavalue.substring(2, 4);
        String logDate  = datavalue.substring(4, 6) + "-" + datavalue.substring(6, 8) + "-" + datavalue.substring(8, 10);
        String logTime  = datavalue.substring(10, 12) + ":" + datavalue.substring(12, 14);
        String logCont  = datavalue.substring(14, len);

//        Log.d(TAG, "同步过来的日志：id："+logID + " logType："+logType + " logDate:"+ logDate + " logTime:"+logTime + " logCont:"+ logCont);

        String userType = logCont.substring(0, 2);
        String openCont = logCont.substring(2);

        map.put("logType", logType);
        map.put("logID", logID);
        map.put("logDate", logDate);
        map.put("logTime", logTime);
        map.put("userType", userType);

        //开门日志
        if (logType.equals("09")){
            //卡开门
            if (userType.equals("06")){
                openCont = openCont.substring(0, 2);
            }

            //指纹开门（普通指纹、防胁迫指纹）
            if (userType.equals("07") || userType.equals("08") ){
                openCont = openCont.substring(0, 2);
            }

            //密码开门（管理员密码、普通密码、超级用户密码、动态密码、防胁迫密码）
            if (userType.equals("01") || userType.equals("02") || userType.equals("03")|| userType.equals("04")|| userType.equals("05")){
                openCont = openCont.replace("f","");
            }

            map.put("logCont", openCont);
        }

        //告警日志
        if (logType.equals("0a")){
            map.put("logCont", openCont);
        }

        //操作日志
        if (logType.equals("0b")){
            map.put("logCont", openCont);
        }

        return map;
    }

    /**
     * 日志同步结束
     * @param datavalue
     * 0xFE 0x18  LEN ERR_CODE checksum
     */
    private LinkedHashMap syncLogFinish(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "18");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"日志同步结束的错误码："+errCode);
        return map;
    }


    /**
     * 查看固件版本号
     * @param datavalue
     * 0xFE 0x1A  LEN  VER1  VER2  YY  MM  DD  HW_TYPE  ERR_CODE  checksum
     */
    private LinkedHashMap checkFirmware(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "1a");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
//        Log.d(TAG,"查看固件版本号的错误码："+errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

        if (errCode.equals("00")) {
            String version1 = datavalue.substring(0, 2);
            String version2 = datavalue.substring(2, 4);
            String verTime  = datavalue.substring(4, 10);
            String hwType_num   = datavalue.substring(10, 12);

            String verStr = Integer.valueOf(version1)+"."+ Integer.valueOf(version2)+"."+verTime;

            String hwType = "FM60B";
            if (hwType_num.equals("03")){ hwType = "FM60B"; }
            if (hwType_num.equals("04")){ hwType = "FM67"; }
            if (hwType_num.equals("05")){ hwType = "FM810"; }

            map.put("errCode", errCode);
            map.put("fwVersion1", version1);
            map.put("fwVersion2", version2);
            map.put("fwVerTime", verTime);
            map.put("fwType", hwType);

            SinovoBle.getInstance().setLockFirmVersion(verStr);
            SinovoBle.getInstance().setLockType(hwType);
        }

        return map;
    }

    /**
     * 蓝牙绑定删除，清空
     * @param datavalue
     * 0xFE 0x1B  LEN  STOID  ERR_CODE  checksum
     */
    private LinkedHashMap delBoundPhone(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "06");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"蓝牙绑定删除的错误码："+errCode);

        if (errCode.equals("00")) {
            String delID = datavalue.substring(0, len-2);   //id为 ff 时表示清空所有绑定
            map.put("dataType", "0e");
            map.put("sid", delID);
        }
        return map;
    }


    /**
     * 查询/设置静音模式
     * @param datavalue
     * 0xFE 0x1C  LEN ENABLED  ERR_CODE checksum
     */
    private LinkedHashMap muteMode(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "1c");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"查询/设置静音模式的错误码："+errCode);

        if (errCode.equals("00")) {
            String enableAuto = datavalue.substring(0, 2);
            map.put("enable", enableAuto);
        }
        return map;
    }

    /**
     * 查看/设置基准时间
     * @param datavalue
     * 0xFE 0x1F LEN YY MM DD HH mm SS ERR_CODE checksum
     */
    private LinkedHashMap baseTime(String datavalue) {
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "1f");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"查看/设置基准时间的错误码："+errCode);

        if (errCode.equals("00")) {
            String basetime = datavalue.substring(0, 12);
//            Log.d(TAG,"获得到锁端的基准时间：" + basetime);
            map.put("baseTime", basetime);
        }
        if (errCode.equals("0a")){
            String pre_basetime = ComTool.getSpecialTime("",0,1,-2);
//            Log.d(TAG,"还没设置基准时间，准备设置为："+ pre_basetime);
            String data = SinovoBle.getInstance().getLockSNO() +pre_basetime;
            exeCommand("1f",data,true);
        }
        return map;
    }


    /**
     * 启用/禁用 分享密码
     * @param datavalue
     * 0xFE 0x20 LEN  ENABLED  PASSWORD  ERR_CODE checksum
     */
    private LinkedHashMap operateSharedCode(String datavalue) {
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "20");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());
//        Log.d(TAG,"启用/禁用 分享密码的错误码："+errCode);

        if (errCode.equals("00")) {
            String status       = datavalue.substring(0, 2);
            String sharedCode   = datavalue.substring(2, len-2);
            sharedCode = sharedCode.replace("f","");
//            Log.d(TAG,"分享密码【"+sharedCode+"】的状态：" + status);

            map.put("codeStatus", status);
            map.put("sharedCode", sharedCode);
        }
        return map;
    }


    /**
     * 查询锁端的mac地址
     * @param datavalue
     * 0xFE 0x21  LEN  M1 M2 M3 M4 M5 M6  ERR_CODE checksum
     */
    private LinkedHashMap getLockMAC(String datavalue) {
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "21");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);

        if (errCode.equals("00") && len>=12) {
            String lockMAC       = datavalue.substring(0, 12);
            lockMAC = macWithColon(lockMAC).toUpperCase();
            if (!SinovoBle.getInstance().getLockMAC().equals(lockMAC)){
//                Log.d(TAG,"原mac："+SinovoBle.getInstance().getLockMAC() + ",锁端返回的mac："+lockMAC+"不一致，需要更新");
                SinovoBle.getInstance().setLockMAC(lockMAC);
            }
            map.put("lockMac", SinovoBle.getInstance().getLockMAC());
        }
        return map;
    }


    /**
     * 查询/设置超级用户权限
     * @param datavalue
     * 0xFE 0x23  LEN  PRIVILEGE ERR_CODE checksum
     */
    private LinkedHashMap checkSuperUser(String datavalue){
        int len = datavalue.length();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("funCode", "23");

        if (len<2){
            map.put("errCode", "01");   //数据长度有误
            return map;
        }
        String errCode = datavalue.substring(len-2, len);
        map.put("errCode", errCode);   //数据长度有误
        map.put("lockMac", SinovoBle.getInstance().getLockMAC());

//        Log.d(TAG,"查询/设置超级用户权限的错误码："+errCode);

        if (errCode.equals("00")) {
            String enableAuto = datavalue.substring(0, 2);
            map.put("superUserLevel", enableAuto);
        }

        return map;
    }


    /**
     * 转换mac地址，将 00A051F4DC4C转换为00:A0:51:F4:DC:4C
     */
    private String macWithColon(String macAddress){

        StringBuilder lockMac = new StringBuilder();
        for (int j=0;j<macAddress.length();){
            if (j < macAddress.length() - 2) {
                lockMac.append(macAddress.substring(j, j + 2)).append(":");
            }else{
                lockMac.append(macAddress.substring(j, j + 2));
            }
            j = j + 2;
        }

        return lockMac.toString().toUpperCase();
    }

}
