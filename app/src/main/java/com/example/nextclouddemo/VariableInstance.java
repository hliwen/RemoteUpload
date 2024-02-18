package com.example.nextclouddemo;

import android.os.Environment;

import com.example.nextclouddemo.utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class VariableInstance {
    public static final String GET_DEVICE_PERMISSION = "GET_DEVICE_PERMISSION";
    public static final int FormatCameraDay = 14;//删除两周前相机照片

    private static VariableInstance instance = null;

    public static final String remoteCameraRootDirName = "CameraPicture";
    public static final String remoteLogcatRootDirName = "Locat";
    public String sdCardDirRoot;
    public String wifiConfigurationFileName;
    public String PictureDirName;
    public String PictureUploadDirName;
    public String LogcatDirName;
    public String TFCardPictureDir;
    public String TFCardUploadPictureDir;
    public String LogcatDir;


    public int UploadMode = 1; //  1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw,jpg，4列表下载列表上传RAW  5全部下载全部上传按照时间建文件夹
    public ArrayList<Integer> uploadSelectIndexList;

    public int MAX_NUM = 1000;//U盘最多保留张数

    public boolean isUploadToday = true;

    public boolean cyclicDeletion = true;//开启循环删除，只保留最新的MAX_NUM张数

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


}
