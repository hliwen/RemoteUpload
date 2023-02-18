package com.example.nextclouddemo.utils;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.LogUtils;
import com.example.nextclouddemo.VariableInstance;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateUtils {

    private static final String TAG = "UpdateUtils";
    private String downloadPath;
    private UpdateListener updateListener;

    public UpdateUtils(UpdateListener updateListener) {
        this.updateListener = updateListener;
    }

    public void networkAvailable(Context context) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                AppUtils.AppInfo appInfo = AppUtils.getAppInfo(context.getPackageName());

                int appVerison = appInfo.getVersionCode();
                int servierVersion = getServiceVersion();

                if (updateListener != null)
                    updateListener.serverVersion(servierVersion);

                Log.e(TAG, "run: appVerison =" + appVerison + ",servierVersion =" + servierVersion);

                if (servierVersion > appVerison) {
                    boolean downloadSucced = startDownloadApp(UrlUtils.appDowloadURL + servierVersion);
                    Log.d(TAG, "run: startDownloadApp downloadSucced =" + downloadSucced);
                    if (downloadSucced) {
                        downloadSucceed(context, downloadPath);
                    } else {
                        downloadFaild();
                    }
                }
            }
        }).start();


    }

    public void networkLost() {

    }


    private int getServiceVersion() {

        int servierVersion = 0;
        try {
            URL url = new URL(UrlUtils.appVersionURL);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();

            Log.e(TAG, "getServiceVersion: ResponseCode =" + ResponseCode);
            if (ResponseCode != 200)
                return 0;

            InputStream inputStream = urlcon.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);
            String line;
            StringBuffer buffer = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            String content = buffer.toString();
            Log.d(TAG, "run:  nccontent = " + content);
            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
            jsonObject = new JSONObject(jsonArray.getString(0));
            String version = jsonObject.getString("version");
            servierVersion = Integer.parseInt(version);
        } catch (Exception e) {
            Log.e(TAG, "getServiceVersion: Exception =" + e);
        }
        Log.e(TAG, "getServiceVersion: servierVersion =" + servierVersion);
        return servierVersion;
    }


    private boolean startDownloadApp(String downloadURL) {
        Log.d(TAG, "startDownloadApp: ");
        boolean downloadSucced = false;
        Utils.makeDir("/storage/emulated/0/Download/");
        String appName = "RemoteUpload.apk";
        downloadPath = "/storage/emulated/0/Download/" + appName;
        File apkFile = new File(downloadPath);
        if (apkFile != null && apkFile.exists()) {
            apkFile.delete();
        }
        apkFile = new File("/storage/emulated/0/Download/" + appName);
        try {
            URL downloadurl = new URL(downloadURL);
            HttpURLConnection connection = (HttpURLConnection) downloadurl.openConnection();
            int ResponseCode = connection.getResponseCode();
            if (ResponseCode == 200 || ResponseCode == 206) {
                InputStream downloadInputStream = connection.getInputStream();
                FileOutputStream downloadFileOutputStream = new FileOutputStream(apkFile);
                byte[] buffer = new byte[2048 * 8];
                int lenght;

                long curentLength = 0;

                while ((lenght = downloadInputStream.read(buffer)) != -1) {
                    downloadFileOutputStream.write(buffer, 0, lenght);
                    curentLength += lenght;

                    if (updateListener != null)
                        updateListener.downloadProgress((int) (curentLength / 1024));

                }
                downloadFileOutputStream.flush();
                downloadInputStream.close();
                downloadFileOutputStream.close();
                downloadSucced = true;
            } else {

            }

        } catch (Exception e) {

            Log.d(TAG, "startDownloadApp: e =" + e);
        }


        return downloadSucced;
    }

    private void downloadSucceed(Context context, String filaPath) {
        try {
            if (updateListener != null)
                updateListener.startUpdate();
            execLinuxCommand();
            boolean installSuccess = SilentInstallUtils.installSilent(filaPath);
            if (updateListener != null)
                updateListener.updateResult(installSuccess);
            Log.e(TAG, "downloadSucceed: installSuccess =" + installSuccess);
        } catch (Exception e) {
            Log.e(TAG, "downloadSucceed: Exception =" + e);
        }
    }


    public void execLinuxCommand() {
        String cmd = "sleep 180; am start -n com.example.nextclouddemo/com.example.nextclouddemo.MainActivity";
        //Runtime对象
        Runtime runtime = Runtime.getRuntime();
        try {
            Process localProcess = runtime.exec("su");
            OutputStream localOutputStream = localProcess.getOutputStream();
            DataOutputStream localDataOutputStream = new DataOutputStream(localOutputStream);
            localDataOutputStream.writeBytes(cmd);
            localDataOutputStream.flush();
        } catch (IOException e) {

        }
    }

    private void downloadFaild() {

    }


    public interface UpdateListener {
        void serverVersion(int version);

        void downloadProgress(int progress);

        void startUpdate();

        void updateResult(boolean succeed);
    }
}
