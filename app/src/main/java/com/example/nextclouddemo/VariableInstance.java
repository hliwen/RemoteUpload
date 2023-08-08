package com.example.nextclouddemo;

import android.os.Environment;

import com.example.nextclouddemo.utils.Utils;
import com.owncloud.android.lib.common.OwnCloudClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Vector;

public class VariableInstance {
    private static VariableInstance instance = null;
    public String sdCardDirRoot;
    public String wifiConfigurationFileName;
    public String PictureDirName;
    public String PictureUploadDirName;
    public String LogcatDirName;
    public String TFCardPictureDir;
    public String TFCardUploadPictureDir;
    public String LogcatDir;

    public OwnCloudClient ownCloudClient;

   public class FormatState {
        public int formatState;//0:不需要格式化，1：格式化全部 2：格式化两周前
    }

    public FormatState isFormaringCamera;
    public FormatState isFormatingUSB;
    public boolean isConnectCamera;
    public boolean isInitUSB;
    public boolean initingUSB;
    public boolean isConnectedRemote;
    public boolean isUploadToday = true;


    public int UploadMode = 1; //  1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw，4列表下载列表上传RAW
    public ArrayList<Integer> uploadSelectIndexList;
    public int deviceStyle;//0 是还不确定是蜂窝板还是WiFi版，1是蜂窝版，2是WiFi版

    public int uploadRemorePictureNum = 0;//已上传张数
    public int downdCameraPicrureNum = 0;//从相机同步到U盘的张数
    public Vector<String> usbFileNameList;

    public Vector<String> errorLogNameList;
    public int LastPictureCount;
    public int storeUSBDeviceID = -1;//U盘设备号
    public boolean isScanningStoreUSB;//正在扫描U盘
    public boolean isDownloadingUSB;//正在下载相机的照片到U盘
    public boolean isScanningCamera;//正在扫描相机
    public boolean isUploadingToRemote;//正在上传照片到远程服务器
    public boolean isUpdatingBetaApk;//正在升级测试版APK


    private VariableInstance() {
        uploadSelectIndexList = new ArrayList<>();
        PictureDirName = "CameraPath";
        wifiConfigurationFileName = "wifiConfiguration";
        PictureUploadDirName = "CameraUploadPath";
        LogcatDirName = "MLogcat";
//        sdCardDirRoot = "/mnt/sdcard" + File.separator;
        sdCardDirRoot = Environment.getExternalStorageDirectory() + File.separator;

        TFCardPictureDir = sdCardDirRoot + PictureDirName;
        TFCardUploadPictureDir = sdCardDirRoot + PictureUploadDirName;
        LogcatDir = sdCardDirRoot + LogcatDirName;
        usbFileNameList = new Vector<>();
        errorLogNameList = new Vector<>();
        storeUSBDeviceID = -1;

        isFormaringCamera = new FormatState();
        isFormatingUSB = new FormatState();

        Utils.makeDir(TFCardPictureDir);
        Utils.makeDir(TFCardUploadPictureDir);
        Utils.makeDir(LogcatDir);
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
