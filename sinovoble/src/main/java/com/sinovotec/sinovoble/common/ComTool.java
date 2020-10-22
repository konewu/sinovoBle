package com.sinovotec.sinovoble.common;

import android.annotation.SuppressLint;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class ComTool {

    /**
     * 将字节转换为 16进制的 字符串
     * @param b byte
     * @return string
     */
    public static String byte2hex(byte[] b) {
        if (b == null){
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String tmp ;
        for (byte value : b) {
            tmp = Integer.toHexString(value & 0XFF);
            if (tmp.length() == 1) {
                sb.append("0").append(tmp);
            } else {
                sb.append(tmp);
            }
        }
        return sb.toString();
    }


    /**
     * 将字符串转换为 字节
     * @param hexString s
     * @return  字节数组
     */
    static byte[] toByte(String hexString) {
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
        StringBuilder sbu = new StringBuilder();
        char[] chars = value.toCharArray();      //将字符串转为字符数组
        for (char aChar : chars) {
            sbu.append(Integer.toHexString(aChar)); //将10进制的acsii码转为16进制
        }
        return sbu.toString();
    }

    /////////////////////////////////////////////////////////////////////////////////////////
    ////                 ACSII码转换为 字符串                                              ////
    ////                 @参数: ASCII码                                                   ////
    ////                 @结果: 字符串                                                   ////
    ////////////////////////////////////////////////////////////////////////////////////////
    static String asciiToString(String value) {
        StringBuilder sbu = new StringBuilder();
        StringBuilder value10= new StringBuilder();
        for (int i=0; i<value.length(); ){
            int dval = Integer.valueOf(value.substring(i,i+2),16);   //读取一个字节，并转10进制
            if (dval != 0){  // acsii码 0 为空字符，过滤掉
                value10.append(dval).append(",");
            }
            i = i + 2;
        }

        String[] chars = value10.toString().split(",");
        for (String aChar : chars) {
            sbu.append((char) Integer.parseInt(aChar));
        }
        return sbu.toString();
    }

    /**
     * 获取当前的时间
     */
    public static String getNowTime(){
        long currentTime = System.currentTimeMillis();
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(currentTime);
    }

    /***
     * 计算两个时间差，返回的是的秒s
     * date2 - date1 的时间差
     * @param date1 s
     * @param date2 s
     * @return long
     */
    public static long calTimeDiff(String date1, String date2) {
        long diff = 0;
        Date d1 ;
        Date d2 ;

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

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
     * @param intervalValue  表示时间间隔的时间值
     *
     * @return s
     *
     */
    static String getSpecialTime(int dateForm, int intervalValue){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        switch (dateForm){
            case 0:
                simpleDateFormat = new SimpleDateFormat("yyMMddHHmmss", Locale.ROOT);
                break;
            case 1:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
                break;
            case 3:
                simpleDateFormat = new SimpleDateFormat("yyMMddHHmm", Locale.ROOT);
                break;
            case 4:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
                break;
            case 5:
                simpleDateFormat = new SimpleDateFormat("HH:mm", Locale.ROOT);
                break;
            case 6:
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
                break;
        }

        Calendar  calendar = Calendar. getInstance();
        calendar.add( Calendar.DATE, intervalValue);

        Date date= calendar.getTime();
        return simpleDateFormat.format(date);
    }

    /**
     * 返回美国时间格式 Thu. 02 Jul 2020
     *
     */
    @SuppressLint("SimpleDateFormat")
    static String getEDate(String str) {
        SimpleDateFormat formatter ;

        formatter = new SimpleDateFormat("yyMMddHHmmss");           //带时间的格式

        ParsePosition pos = new ParsePosition(0);
        Date strtodate = formatter.parse(str, pos);
        String j = null;
        if (strtodate != null) {
            j = strtodate.toString();
        }

        String[] k = new String[0];
        if (j != null) {
            k = j.split(" ");
        }

        String retime = k[2] +" "+ k[1] +" "+ k[5].substring(0, 4);  //得到日期
        retime = retime + " "+ k[3].substring(0, 5)+ ":"+ k[3].substring(6, 8);
        return retime;
    }


    /**
     * 生成 随机数
     */
    static String getRndNumber(){
        StringBuilder imeiStr = new StringBuilder();
        for (int i=0; i<6; i++){
            Random rand = new Random();
            int randNum = rand.nextInt(9);
            imeiStr.append(randNum);
        }
        return imeiStr.toString();
    }
}
