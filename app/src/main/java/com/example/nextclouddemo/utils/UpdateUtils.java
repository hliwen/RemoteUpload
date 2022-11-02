package com.example.nextclouddemo.utils;

import android.content.Context;
import android.text.TextUtils;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.LogUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UpdateUtils {

    private static final String TAG = "UpdateUtils";
    private String downloadPath;

    public void networkAvailable(Context context) {


//        context = context.getApplicationContext();
//        //加上apk合法性判断
//        AppUtils.AppInfo apkInfo = AppUtils.getApkInfo(file);
//        if (apkInfo == null || TextUtils.isEmpty(apkInfo.getPackageName())) {
//            LogUtils.iTag(TAG, "apk info is null, the file maybe damaged: " + file.getAbsolutePath());
//            return false;
//        }
//
//        //加上本地apk版本判断
//        AppUtils.AppInfo appInfo = AppUtils.getAppInfo(apkInfo.getPackageName());
//        if (appInfo != null) {
//
//            //已安装的版本比apk版本要高, 则不需要安装
//            if (appInfo.getVersionCode() >= apkInfo.getVersionCode()) {
//                LogUtils.iTag(TAG, "The latest version has been installed locally: " + file.getAbsolutePath(),
//                        "app info: packageName: " + appInfo.getPackageName() + "; app name: " + appInfo.getName(),
//                        "apk version code: " + apkInfo.getVersionCode(),
//                        "app version code: " + appInfo.getVersionCode());
//                return true;
//            }
//        }


    }

    public void networkLost() {

    }


    private void getServiceVersion() {
        try {
            URL url = new URL(UrlUtils.appVersionURL);
            HttpURLConnection urlcon = null;
            urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();
            if(ResponseCode==200){}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkUpdate(String currentVersion, String serviceVersion) {
        return false;
    }

    private void startDownloadApp(String downloadURL) {

    }

    private void downloadSucceed() {

    }

    private void downloadFaild() {

    }
}
