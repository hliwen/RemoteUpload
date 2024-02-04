package com.example.nextclouddemo.operation;

import android.content.Context;

import com.blankj.utilcode.util.AppUtils;
import com.example.nextclouddemo.MainActivity;
import com.example.nextclouddemo.VariableInstance;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.UrlUtils;
import com.example.nextclouddemo.utils.Utils;

import org.greenrobot.eventbus.EventBus;
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

    private static final String TAG = "remotelog_UpdateUtils";


    public static void updateBetaApk(Context context, UpdateListener updateListener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateListener.startUpdate();
                AppUtils.AppInfo appInfo = AppUtils.getAppInfo(context.getPackageName());
                int appVerison = appInfo.getVersionCode();
                int servierVersion = getBetaServiceVersion();
                if (servierVersion == 0) {
                    updateListener.endUpdate(false, "获取远程版本失败");
                    return;
                }

                Log.e(TAG, "updateBetaApk: app当前版本 =" + appVerison + ",远程版本 =" + servierVersion);
                String downloadURL = UrlUtils.appDowloadURL_Beta + servierVersion;

                String downloadPath = startDownloadApk(downloadURL, updateListener);
                if (downloadPath == null) {
                    updateListener.endUpdate(false, "下载远程升级文件失败");
                    updateListener.endDownload(false);
                    return;
                }
                updateListener.endDownload(true);
                try {
                    EventBus.getDefault().post(MainActivity.Update_InstallAPKStart);
                    updateListener.startInstall();
                    boolean installSuccess = installSilent(downloadPath);
                    updateListener.endInstall(installSuccess);
                    updateListener.endUpdate(installSuccess, "installSilent(downloadPath);");
                } catch (Exception e) {
                    Log.e(TAG, "updateBetaApk: Exception =" + e);
                    updateListener.endUpdate(false, "Exception：" + e);
                }

            }
        }).start();
    }

    private static int getBetaServiceVersion() {

        int servierVersion = 0;
        try {
            URL url = new URL(UrlUtils.appVersionURL_Beta);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();

            if (ResponseCode != 200) {
                Log.d(TAG, "getBetaServiceVersion: 无法访问升级链接");
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
            Log.d(TAG, "getBetaServiceVersion:  content = " + content);
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


    private static String startDownloadApk(String downloadURL, UpdateListener updateListener) {
        Utils.makeDir("/storage/emulated/0/Download/");
        String downloadPath_tpm = "/storage/emulated/0/Download/RemoteUpload_tpm.apk";
        String downloadPath = "/storage/emulated/0/Download/RemoteUpload.apk";
        File apkFileTpm = new File(downloadPath_tpm);
        if (apkFileTpm != null && apkFileTpm.exists()) {
            apkFileTpm.delete();
        }
        apkFileTpm = new File(downloadPath_tpm);
        try {
            updateListener.startDownload();
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
                if (apkFileTpm.renameTo(apkFile)) {
                    return downloadPath;
                } else {
                    Log.e(TAG, "startDownloadApk: 重命名失败");
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "startDownloadApp: e =" + e);
        }
        return null;
    }

    public void networkLost() {

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
                EventBus.getDefault().post(MainActivity.Update_InstallAPKSucceed);
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
        void startUpdate();

        void startDownload();

        void endDownload(boolean succeed);

        void startInstall();

        void endInstall(boolean succeed);


        void downloadProgress(int progress);


        void endUpdate(boolean succeed, String message);
    }
}
