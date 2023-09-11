package com.example.nextclouddemo.utils;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.LogUtils;
import com.example.nextclouddemo.ErrorName;
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
import java.nio.charset.Charset;

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

                if (updateListener != null) {
                    updateListener.serverVersion(servierVersion);
                }

                Log.e(TAG, "run: app当前版本 =" + appVerison + ",远程版本 =" + servierVersion);

                if (servierVersion > appVerison) {
                    startDownloadApk(servierVersion);
                }
            }
        }).start();
    }

    public void checkBetaApk(Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppUtils.AppInfo appInfo = AppUtils.getAppInfo(context.getPackageName());

                int appVerison = appInfo.getVersionCode();
                int servierVersion = getServiceVersion();

                if (updateListener != null) updateListener.serverVersion(servierVersion);

                Log.e(TAG, "run: app当前版本 =" + appVerison + ",远程版本 =" + servierVersion);

//                if (servierVersion > appVerison) {
                startDownloadApk(servierVersion);
//                }
            }
        }).start();
    }


    void startDownloadApk(int servierVersion) {
        String downloadURL = VariableInstance.getInstance().isUpdatingBetaApk ? UrlUtils.appDowloadURL_Beta : UrlUtils.appDowloadURL;
        boolean downloadSucced = startDownloadApp(downloadURL + servierVersion);
        Log.d(TAG, "run: startDownloadApp downloadSucced =" + downloadSucced);
        if (downloadSucced) {
            downloadSucceed(downloadPath);
        } else {
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.下载升级文件失败无法升级);
        }
    }

    public void networkLost() {

    }


    private int getServiceVersion() {

        int servierVersion = 0;
        try {
            URL url = new URL(VariableInstance.getInstance().isUpdatingBetaApk ? UrlUtils.appVersionURL_Beta : UrlUtils.appVersionURL);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();

            Log.e(TAG, "getServiceVersion: ResponseCode =" + ResponseCode);
            if (ResponseCode != 200) {
                VariableInstance.getInstance().errorLogNameList.add(ErrorName.无法访问升级链接);
                return 0;
            }

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
        String downloadPath_tpm = "/storage/emulated/0/Download/RemoteUpload_tpm.apk";
        downloadPath = "/storage/emulated/0/Download/RemoteUpload.apk";
        File apkFileTpm = new File(downloadPath_tpm);
        if (apkFileTpm != null && apkFileTpm.exists()) {
            apkFileTpm.delete();
        }
        apkFileTpm = new File(downloadPath_tpm);
        try {
            URL downloadurl = new URL(downloadURL);
            HttpURLConnection connection = (HttpURLConnection) downloadurl.openConnection();
            int ResponseCode = connection.getResponseCode();
            if (ResponseCode == 200 || ResponseCode == 206) {
                InputStream downloadInputStream = connection.getInputStream();
                FileOutputStream downloadFileOutputStream = new FileOutputStream(apkFileTpm);
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

                File apkFile = new File(downloadPath);
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                apkFile = new File(downloadPath);
                downloadSucced = apkFileTpm.renameTo(apkFile);
            } else {

            }

        } catch (Exception e) {

            Log.d(TAG, "startDownloadApp: e =" + e);
        }


        return downloadSucced;
    }

    private void downloadSucceed(String filaPath) {
        try {
            if (updateListener != null) {
                updateListener.startUpdate();
            }
            execLinuxCommand();
            boolean installSuccess = installSilent(filaPath);
            if (updateListener != null) {
                updateListener.endUpdate(installSuccess);
            }
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


    public static boolean installSilent(String path) {
        boolean result = false;
        BufferedReader es = null;
        DataOutputStream os = null;

        try {
            Process process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());

            Log.d(TAG, "installSilent:1111 ");

            String command = "pm install -r " + path + "\n";
            os.write(command.getBytes(Charset.forName("utf-8")));
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
            Log.d(TAG, "installSilent:222222 ");

            process.waitFor();
            es = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = es.readLine()) != null) {
                builder.append(line);
            }
            Log.d(TAG, "install msg is " + builder.toString());

        /* Installation is considered a Failure if the result contains
            the Failure character, or a success if it is not.
             */
            if (!builder.toString().contains("Failure")) {
                result = true;
            } else {
                delayInstall(path);
                uninstallapk();
            }
        } catch (Exception e) {
            Log.e(TAG, "installSilent Exception =" + e);
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (es != null) {
                    es.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "installSilent IOException =" + e);
            }
        }
        Log.e(TAG, "installSilent: result = " + result);
        return result;
    }

    private static void delayInstall(String path) {
        DataOutputStream localDataOutputStream = null;
        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("su");
            OutputStream localOutputStream = process.getOutputStream();
            localDataOutputStream = new DataOutputStream(localOutputStream);

            String command = "sleep 10; pm install -r " + path + "\n";
            localDataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            localDataOutputStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "installSilent Exception =" + e);
        } finally {
            try {
                if (localDataOutputStream != null) {
                    localDataOutputStream.close();
                }

            } catch (IOException e) {
                Log.e(TAG, "installSilent IOException =" + e);
            }
        }
        Log.e(TAG, "installSilent: end ");

    }

    private static void uninstallapk() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());

            Log.d(TAG, "installSilent:1111 ");

            String command = "pm uninstall com.example.nextclouddemo" + "\n";
            os.write(command.getBytes(Charset.forName("utf-8")));
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (Exception e) {
            }
        }
    }

    public interface UpdateListener {
        void serverVersion(int version);

        void downloadProgress(int progress);

        void startUpdate();

        void endUpdate(boolean succeed);
    }
}
