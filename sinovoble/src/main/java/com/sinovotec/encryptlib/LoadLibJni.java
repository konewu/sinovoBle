package com.sinovotec.encryptlib;

public class LoadLibJni {

    public LoadLibJni() {

    }

    public static boolean LoadLib() {
        try {
            System.loadLibrary("native-lib");
            return true;
        } catch (Exception ex) {
            System.err.println("WARNING: Could not load library");
            return false;
        }
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
  //  public native String stringFromJNI();  //nativi调用

    public native String encryptAes(String text, String lockmac);       //encrypt， lockmac Need to remove :
    public native String decryptAes(String ciphertext, String lockmac);  //decrypt， lockmac Need to remove :
    public native String getDyCode(String lockmac, String diff, String starttime, String valid, String vt, String codetype);  //generate  one-Time code、Timed code
    public native String getIntervalCode(String lockmac, String starttime, String endtime, String codetype);     //generate  periodic code
}
