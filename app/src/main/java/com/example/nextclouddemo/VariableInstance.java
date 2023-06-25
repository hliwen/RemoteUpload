package com.example.nextclouddemo;

import java.io.File;
import java.util.ArrayList;
import java.util.Vector;

public class VariableInstance {
    private static VariableInstance instance = null;
    public int UploadMode = 1; //  1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw，4列表下载列表上传RAW
    public ArrayList<Integer> uploadSelectIndexList;

    //0 是还不确定是蜂窝板还是WiFi版，1是蜂窝版，2是WiFi版
    public int deviceStyle;

    public int uploadNum = 0;
    public int downdNum = 0;

    public String sdCardDirRoot;

    public String PictureDirName;
    public String PictureUploadDirName;
    public String VideoDirName;
    public String LogcatDirName;
    public String wifiConfigurationFileName;

    public String TFCardPictureDir;
    public String TFCardUploadPictureDir;
    public String TFCardVideoDir;
    public String LogcatDir;
    public boolean formarCamera;
    public boolean connectCamera;
    public boolean initUSB;
    public boolean initingUSB;

    public boolean isUploadToday = true;

    public Vector<String> usbFileNameList;
    public int LastPictureCount;
    public boolean isScanerStoreUSB;
    public int storeUSBDeviceID = -1;


    private VariableInstance() {
        uploadSelectIndexList = new ArrayList<>();
        PictureDirName = "CameraPath";
        wifiConfigurationFileName = "wifiConfiguration";
        PictureUploadDirName = "CameraUploadPath";
        VideoDirName = "VideoPath";
        LogcatDirName = "MLogcat";

        sdCardDirRoot = "/mnt/sdcard" + File.separator;
        TFCardPictureDir = sdCardDirRoot + PictureDirName;
        TFCardUploadPictureDir = sdCardDirRoot + PictureUploadDirName;
        TFCardVideoDir = sdCardDirRoot + VideoDirName;
        LogcatDir = sdCardDirRoot + LogcatDirName;
        usbFileNameList = new Vector<>();
        storeUSBDeviceID = -1;
    }


    public static VariableInstance getInstance() {
        if (instance == null) {
            synchronized (VariableInstance.class) {
                if (instance == null) {
                    instance = new VariableInstance();
                }
            }
        }
        return instance;
    }

}
