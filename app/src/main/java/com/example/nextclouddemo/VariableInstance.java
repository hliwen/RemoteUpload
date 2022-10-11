package com.example.nextclouddemo;

import java.io.File;
import java.util.ArrayList;

public class VariableInstance {
    private static VariableInstance instance = null;
    public int UploadMode = 1; //  1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw，4列表下载列表上传RAW
    public ArrayList<Integer> uploadSelectIndexList;


    public int uploadNum = 0;
    public int downdNum = 0;

    public String sdCardDirRoot;

    public String PictureDirName;
    public String VideoDirName;
    public String LogcatDirName;

    public String TFCardPictureDir;
    public String TFCardVideoDir;
    public String LogcatDir;
    public boolean formarCamera;


    private VariableInstance() {
        uploadSelectIndexList = new ArrayList<>();
        PictureDirName = "CameraPath";
        VideoDirName = "VideoPath";
        LogcatDirName = "MLogcat";

        sdCardDirRoot = "/mnt/sdcard" + File.separator;
        TFCardPictureDir = sdCardDirRoot + PictureDirName;
        TFCardVideoDir = sdCardDirRoot + VideoDirName;
        LogcatDir = sdCardDirRoot + LogcatDirName;
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
