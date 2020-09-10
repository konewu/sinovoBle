package com.sinovotec.sinovoble.common;

import android.annotation.SuppressLint;
import android.util.Log;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

public class ComTool {

    private static String TAG = "BleLib";
    /**
     * 将字节转换为 16进制的 字符串
     * @param b
     * @return
     */
    public static String byte2hex(byte[] b) {
        if (b == null){
            return "";
        }
        StringBuffer sb = new StringBuffer();
        String tmp ;
        for (int i = 0; i < b.length; i++) {
            tmp = Integer.toHexString(b[i] & 0XFF);
            if (tmp.length() == 1) {
                sb.append("0" + tmp);
            } else {
                sb.append(tmp);
            }
        }
        return sb.toString();
    }


    /**
     * 将字符串转换为 字节
     * @param hexString
     * @return  字节数组
     */
    public static byte[] toByte(String hexString) {
        int len = hexString.length()/2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
            result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
        return result;
    }

    /**
     * 字符串转换为ACSII码，并以16进制方式保存
     * @param value  字符串
     * @return 16进制的ASCII码
     */
    public static String stringToAscii(String value) {
        StringBuffer sbu = new StringBuffer();
        char[] chars = value.toCharArray();      //将字符串转为字符数组
        for (int i = 0; i < chars.length; i++) {
            //将10进制的acsii码转为16进制
            sbu.append(Integer.toHexString(chars[i]));
        }
        Log.d(TAG, "字符串：" + value + " 转换为16进制的ACSII码：" + sbu.toString());
        return sbu.toString();
    }

    /////////////////////////////////////////////////////////////////////////////////////////
    ////                 ACSII码转换为 字符串                                              ////
    ////                 @参数: ASCII码                                                   ////
    ////                 @结果: 字符串                                                   ////
    ////////////////////////////////////////////////////////////////////////////////////////
    public static String asciiToString(String value) {
        StringBuffer sbu = new StringBuffer();
        String value10="";
        for (int i=0; i<value.length(); ){
            int dval = Integer.valueOf(value.substring(i,i+2),16);   //读取一个字节，并转10进制
            if (dval != 0){  // acsii码 0 为空字符，过滤掉
                value10 += dval + ",";
            }
            i = i + 2;
        }

        String[] chars = value10.split(",");
        for (int i = 0; i < chars.length; i++) {
            sbu.append((char) Integer.parseInt(chars[i]));
        }
        Log.d(TAG,"将16进制的ascii码："+value + " 转换字符串："+sbu.toString());
        return sbu.toString();
    }

    /**
     * 获取当前的时间
     */
    public static String getNowTime(){
        long currentTime = System.currentTimeMillis();
        String timeNow = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime);
        return timeNow;
    }

    /***
     * 计算两个时间差，返回的是的秒s
     * date2 - date1 的时间差
     * @param date1
     * @param date2
     * @return
     */
    public static long calTimeDiff(String date1, String date2) {
        long diff = 0;
        Date d1 ;
        Date d2 ;

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            d1 = simpleDateFormat.parse(date1);
            d2 = simpleDateFormat.parse(date2);

            // 毫秒ms
            if (d2 != null && d1 != null) {
                diff = d2.getTime() - d1.getTime();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return diff / 1000;
    }


    /**
     * 获取当前的时间
     * @param dateForm  表示日期的格式
     * 为0时，表示 yyMMddHHmmss
     * 为1时，表示 yyyy-MM-dd HH:mm:ss
     * 为3时，表示 yyMMddHHmm
     * 为4时，表示 yyyy-MM-dd HH:mm
     * 为5时，表示 HH:mm
     * 为6时，表示 yyyy-MM-dd HH:mm:ss.SSS
     *
     * @param intervalType  表示时间间隔的类型
     *                      为0 表示 月
     *                      为1 表示 天
     *                      为2 表示 小时
     *                      为3 表示 分钟
     *
     * @param intervalValue  表示时间间隔的时间值
     *
     * @return
     *
     */
    public static String getSpecialTime(String nowtime, int dateForm, int intervalType, int intervalValue){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        switch (dateForm){
            case 0:
                simpleDateFormat = new SimpleDateFormat("yyMMddHHmmss");
                break;
            case 1:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                break;
            case 3:
                simpleDateFormat = new SimpleDateFormat("yyMMddHHmm");
                break;
            case 4:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                break;
            case 5:
                simpleDateFormat = new SimpleDateFormat("HH:mm");
                break;
            case 6:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                break;
        }

        Calendar  calendar = Calendar. getInstance();
        if (nowtime.length() >0){
            Date date = null;
            try {
                date = simpleDateFormat.parse(nowtime);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (date != null) {
                calendar.setTime(date);
            }
        }


        switch (intervalType){
            case 0:
                calendar.add( Calendar.MONTH, intervalValue);
                break;
            case 1:
                calendar.add( Calendar.DATE, intervalValue);
                break;
            case 2:
                calendar.add( Calendar.HOUR, intervalValue);
                break;
            case 3:
                calendar.add( Calendar.MINUTE, intervalValue);
                break;
        }

        Date date= calendar.getTime();
        return simpleDateFormat.format(date);
    }


    /***
     * 计算两个时间差，返回的是的秒s
     * date2 - date1 的时间差
     *
     * @return long
     * @param date1
     * @param date2
     * @return
     */
    public static long calDateDiff(int dateForm, String date1, String date2) {
        long diff = 0;
        Date d1 ;
        Date d2 ;

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        switch (dateForm){
            case 0:
                simpleDateFormat = new SimpleDateFormat("yyMMddHHmmss");
                break;
            case 1:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                break;
            case 3:
                simpleDateFormat = new SimpleDateFormat("yyMMddHHmm");
                break;
            case 4:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                break;
            case 5:
                simpleDateFormat = new SimpleDateFormat("HH:mm");
                break;
            case 6:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                break;
            case 7:
                simpleDateFormat = new SimpleDateFormat("yy-MM-dd");
                break;
            case 8:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                break;
        }

        try {
            d1 = simpleDateFormat.parse(date1);
            d2 = simpleDateFormat.parse(date2);

            // 毫秒ms
            if (d2 != null && d1 != null) {
                diff = d2.getTime() - d1.getTime();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return diff / 1000;
    }

    /**
     * 返回美国时间格式 Thu. 02 Jul 2020
     *
     * @param str
     * @dataform
     * @return
     */
    @SuppressLint("SimpleDateFormat")
    public static String getEDate(String str, int dataform, boolean showWeek, boolean showTime, boolean showSec) {
        SimpleDateFormat formatter ;
        switch (dataform){
            case 0:
                formatter = new SimpleDateFormat("yyMMddHHmmss");           //带时间的格式
                break;
            case 1:
                formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");       //带时间的格式
                break;
            case 2:
                formatter = new SimpleDateFormat("yy-MM-dd");               //不带时间的格式
                break;
            case 3:
                formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");    //带时间的格式
                break;
            default:
                formatter = new SimpleDateFormat("yyyy-MM-dd");
        }

        ParsePosition pos = new ParsePosition(0);
        Date strtodate = formatter.parse(str, pos);
        String j = null;
        if (strtodate != null) {
            j = strtodate.toString();
        }
        //Wed Jul 08 15:11:56 GMT+08:00 2020

        String[] k = new String[0];
        if (j != null) {
            k = j.split(" ");
        }

        String retime = k[2] +" "+ k[1] +" "+ k[5].substring(0, 4);  //得到日期

        if (showWeek)
            retime = k[0] +"."+retime;

        if (showTime)
            retime = retime + " "+ k[3].substring(0, 5);

        if (showTime && showSec)
            retime = retime + ":";

        if (showSec)
            retime = retime + k[3].substring(6, 8);

        Log.d(TAG, "转换后得到的时间：" + retime);
        return retime;
    }


    /**
     * 生成 随机数
     * @param count
     * @param maxNum
     * @param scale
     * @return
     */
    public static String getRndNumber(int count, int maxNum, int scale){
        String imeiStr = "";
        for (int i=0; i<count; i++){
            Random rand = new Random();
            int randNum = rand.nextInt(maxNum);

            if (scale == 16){
                imeiStr += Integer.toHexString(randNum);
            }else {
                imeiStr += randNum;
            }
        }
        return imeiStr;
    }


}
