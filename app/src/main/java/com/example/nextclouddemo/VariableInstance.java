package com.example.nextclouddemo;

import android.os.Environment;

import com.example.nextclouddemo.utils.Utils;
import com.owncloud.android.lib.common.OwnCloudClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Vector;

public class VariableInstance {

    public static final String GET_STORE_USB_PERMISSION = "GET_STORE_USB_PERMISSION";
    public static final String GET_STORE_CAMERA_PERMISSION = "GET_STORE_CAMERA_PERMISSION";

    public static final int FormatCameraDay = 14;//删除两周前相机照片

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


    public int UploadMode = 1; //  1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw,jpg，4列表下载列表上传RAW  5全部下载全部上传按照时间建文件夹
    public ArrayList<Integer> uploadSelectIndexList;

    public int MAX_NUM = 1000;//U盘最多保留张数
    public int currentUSBPictureCount;
    public int uploadRemorePictureNum = 0;//已上传张数
    public int backupPicrureNum = 0;//当次从相机同步到U盘的张数
    public int storeUSBDeviceID = -1;//U盘设备号

    public boolean isScanningStoreUSB;//正在扫描U盘
    public boolean isOperationCamera;//正在操作相机
    public boolean isUploadingToRemote;//正在上传照片到远程服务器
    public boolean isUpdatingBetaApk;//正在升级测试版APK
    public boolean isConnectCamera;

    public boolean isUploadToday = true;
    public boolean backupListInit;//已初始化备份列表
    public boolean remoteListInit;//已初始化远程列表
    public boolean cyclicDeletion = true;//开启循环删除，只保留最新的MAX_NUM张数

    public boolean remoteServerAvailable;
    public boolean remoteServerConnecting;
    public boolean serverApkInitingUSB;




    private VariableInstance() {
        uploadSelectIndexList = new ArrayList<>();
        PictureDirName = "CameraPath";
        wifiConfigurationFileName = "wifiConfiguration";
        PictureUploadDirName = "CameraUploadPath";
        LogcatDirName = "MLogcat";
        sdCardDirRoot = Environment.getExternalStorageDirectory() + File.separator;

        TFCardPictureDir = sdCardDirRoot + PictureDirName;
        TFCardUploadPictureDir = sdCardDirRoot + PictureUploadDirName;
        LogcatDir = sdCardDirRoot + LogcatDirName;


        storeUSBDeviceID = -1;


        Utils.makeDir(TFCardPictureDir);
        Utils.makeDir(TFCardUploadPictureDir);
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

    public void resetAllData() {

        currentUSBPictureCount = 0;
        uploadRemorePictureNum = 0;//已上传张数
        backupPicrureNum = 0;//当次从相机同步到U盘的张数
        storeUSBDeviceID = -1;//U盘设备号

        isScanningStoreUSB = false;//正在扫描U盘
        isOperationCamera = false;//正在操作相机

        isUploadingToRemote = false;//正在上传照片到远程服务器
        isConnectCamera = false;
        remoteServerAvailable = false;
        remoteServerConnecting = false;
    }

    public boolean isStroreUSBDevice(String deviceName) {
        if (deviceName == null) {
            return false;
        }
        return deviceName.contains("USB Storage");
    }

    public boolean isOthreDevice(String deviceName) {
        if (deviceName == null) {
            return true;
        }
        if (deviceName.contains("802.11n NIC") || deviceName.contains("USB Optical Mouse") || deviceName.contains("USB Charger") || deviceName.contains("Usb Mouse") || deviceName.startsWith("EC25") || deviceName.startsWith("EG25") || deviceName.startsWith("EC20") || deviceName.startsWith("EC200T")) {
            return true;
        }
        return false;
    }

    public boolean isCameraDevice(String deviceName) {
        if (deviceName == null) {
            return false;
        }
        deviceName = deviceName.trim();

        if (isStroreUSBDevice(deviceName) || isOthreDevice(deviceName)) {
            return false;
        }
        return true;
    }


}
