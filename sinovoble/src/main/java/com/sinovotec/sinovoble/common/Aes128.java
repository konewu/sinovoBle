package com.sinovotec.sinovoble.common;

import android.util.Log;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Aes128 {

    //AES是加密方式 CBC是工作模式 PKCS5Padding是填充模式
    private static final String CBC_PKCS5_PADDING = "AES/ECB/NoPadding";

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

    public static String encrypt(String keystr, String cleartext) throws Exception {
        byte[] rawKey   = toByte(keystr);
        byte[] clear    = toByte(cleartext);
        byte[] result   = encrypt(rawKey, clear);
        return  byte2hex(result);
    }

    public static String decrypt(String keystr, String encrypted) throws Exception {
        byte[] rawKey   = toByte(keystr);
        byte[] enc      = toByte(encrypted);
        byte[] result   = decrypt(rawKey, enc);
        return  byte2hex(result);
    }

    /**
     * 通过锁端的mac地址来生成加解密的key
     * @param macAddress
     * @return
     */
    public static String getKeyFromMAC(String macAddress){
        String result = "";
        if (macAddress.length() !=12){
            return result;
        }

        for (int i=0; i<3; ){
            result += macAddress.substring(i,i+2);
            result += macAddress.substring(i+4,i+6);
            result += macAddress.substring(i+8,i+10);
            result += macAddress.substring(i,i+2);
            i=i+2;
        }

        for (int i=12; i>9; ){
            result += macAddress.substring(i-2,i);
            result += macAddress.substring(i-4,i-2);
            result += macAddress.substring(i-6,i-4);
            result += macAddress.substring(i-8,i-6);
            i=i-2;
        }
        return result;
    }


    private static byte[] encrypt(byte[] raw, byte[] clear) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");   //SecretKeySpec是采用某种加密算法加密后的密钥, 指定aes加密算法的密钥
        Cipher cipher = Cipher.getInstance(CBC_PKCS5_PADDING);//"算法/模式/补码方式" ,获得Cypher实例对象
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);     // 初始化模式为加密模式，并指定密匙
        return cipher.doFinal(clear);
    }

    private static byte[] decrypt(byte[] raw, byte[] encrypted) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance(CBC_PKCS5_PADDING);//"算法/模式/补码方式" ,获得Cypher实例对象
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
        return cipher.doFinal(encrypted);
    }

    /**
     * 加密数据，直接调用此函数进行加解密
     * @param cleartext
     * @return string
     */
    public static String encryptData(String cleartext, String lockmac){
        //加解密测试
        String keystr = getKeyFromMAC(lockmac.replace(":",""));
        String result = "";
        try {
            result = encrypt(keystr,cleartext);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 解密数据，直接调用此函数进行加解密
     * @param encryptData
     * @return string
     */
    public static String decryptData(String encryptData, String lockmac){

        //加解密测试
        String keystr = getKeyFromMAC(lockmac.replace(":",""));
        String result = "";

        try {
            result = decrypt(keystr,encryptData);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return result;
    }

}
