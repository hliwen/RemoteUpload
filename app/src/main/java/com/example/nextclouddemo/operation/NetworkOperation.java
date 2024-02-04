package com.example.nextclouddemo.operation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;

import com.blankj.utilcode.util.AppUtils;
import com.example.nextclouddemo.MyApplication;
import com.example.nextclouddemo.VariableInstance;
import com.example.nextclouddemo.model.DeviceInfoModel;
import com.example.nextclouddemo.model.ProfileModel;
import com.example.nextclouddemo.model.ServerUrlModel;
import com.example.nextclouddemo.utils.DeviceUtils;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.PictureDateInfo;
import com.example.nextclouddemo.utils.UrlUtils;
import com.example.nextclouddemo.utils.Utils;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.CreateFolderRemoteOperation;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.files.ReadFolderRemoteOperation;
import com.owncloud.android.lib.resources.files.UploadFileRemoteOperation;
import com.owncloud.android.lib.resources.files.model.RemoteFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NetworkOperation {
    private static final String TAG = "remotelog_RemoteOperationUtils";
    private static int requestFailure = -1;
    private static int fileExist = 1;
    private static int noExist = 0;

    private NetwrokOperationListener operationListener;
    private ExecutorService uploadPictureThreadExecutor;
    private ExecutorService networkThreadExecutor;
    public volatile BlockingQueue<String> pictureFileListCache;

    private String userNameDir;
    private String remoteLogcatDir;
    private String remoteCameraPictureDir;
    private List<String> remotePictureDirYYYYMMList;

    public long uploadTatalTime;
    public String returnImei;
    public boolean networkAvailable;
    public boolean networkIniting;
    public boolean uploadingRemotePicture;
    public OwnCloudClient ownCloudClient;
    public boolean remoteServerAvailable;
    public int uploadRemoteCompletePictureNum = 0;//已上传张数

    public NetworkOperation(NetwrokOperationListener remoteOperationListener) {
        this.operationListener = remoteOperationListener;
        uploadRemoteCompletePictureNum = 0;
        pictureFileListCache = new LinkedBlockingQueue<>(20000);
        remotePictureDirYYYYMMList = Collections.synchronizedList(new ArrayList<>());
    }


    public void networkDisconnect(boolean initTimeout) {
        Log.d(TAG, "networkDisconnect: initTimeout =" + initTimeout);
        networkAvailable = false;
        remoteServerAvailable = false;
        ownCloudClient = null;
        uploadingRemotePicture = false;

        if (networkIniting) {
            operationListener.networkInitEnd(false, "网络断开");
        }

        networkIniting = false;

        if (networkThreadExecutor != null) {
            try {
                networkThreadExecutor.shutdownNow();
            } catch (Exception e) {

            }
            networkThreadExecutor = null;
        }

        if (uploadPictureThreadExecutor != null) {
            try {
                uploadPictureThreadExecutor.shutdownNow();
            } catch (Exception e) {

            }
            uploadPictureThreadExecutor = null;
        }


    }

    public void networkConnect(Context context) {
        if (networkThreadExecutor != null) {
            return;
        }
        networkIniting = true;
        operationListener.networkInitStart();
        networkThreadExecutor = Executors.newSingleThreadExecutor();
        networkThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                int serviceVersion = getServiceVersion();
                int requestCount = 0;
                while (serviceVersion == 0 && networkIniting) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                    requestCount++;
                    Log.e(TAG, "networkConnect: 获取最新版本失败次数 ：" + requestCount);
                    serviceVersion = getServiceVersion();
                }

                if (serviceVersion == 0) {
                    networkIniting = false;
                    operationListener.networkInitEnd(false, "无法获取最新版本");
                    return;
                }
                networkAvailable = true;

                int currentVersionCode = AppUtils.getAppInfo(MyApplication.getContext().getPackageName()).getVersionCode();
                operationListener.versionCodeResponse(currentVersionCode, serviceVersion);
                if (serviceVersion > currentVersionCode) {
                    Log.e(TAG, "networkConnect: 需要升级");
                    operationListener.startUpdateApp();
                    boolean installResult = updateApp(serviceVersion);
                    operationListener.endUpdateApp(installResult);
                    if (installResult) {
                        return;
                    } else {
                        Log.e(TAG, "networkConnect : 升级失败 ");
                    }
                }

                ServerUrlModel serverUrlModel = getServerUrl();
                requestCount = 0;
                while ((serverUrlModel == null || serverUrlModel.responseCode != 200) && networkIniting) {
                    try {
                        Thread.sleep(2000);
                    } catch (Exception e) {
                    }
                    requestCount++;
                    Log.e(TAG, "networkConnect: 访问服务器获取url失败次数 ：" + requestCount);
                    serverUrlModel = getServerUrl();
                }

                if (serverUrlModel == null || serverUrlModel.responseCode != 200) {
                    Log.e(TAG, "networkConnect: 访问服务器获取url失败 ");
                    networkIniting = false;
                    operationListener.networkInitEnd(false, "访问服务器获取url失败");
                    return;
                }
                String imei = getWorkingImei(context);
                Log.e(TAG, "networkConnect : getWorkingImei =" + imei);
                if (imei == null) {
                    networkIniting = false;
                    operationListener.networkInitEnd(false, "getWorkingImei  == null");
                    return;
                }
                DeviceInfoModel deviceInfoModel = getDeviceInfo(imei);
                operationListener.showWorkingImei(imei);
                requestCount = 0;
                while ((deviceInfoModel == null || deviceInfoModel.responseCode != 200) && networkIniting) {
                    try {
                        Thread.sleep(3000);
                    } catch (Exception e) {
                    }
                    requestCount++;
                    Log.e(TAG, "networkConnect: 访问服务器获取设备信息失败次数：" + requestCount);
                    deviceInfoModel = getDeviceInfo(imei);
                }

                if ((deviceInfoModel == null || deviceInfoModel.responseCode != 200)) {
                    networkIniting = false;
                    operationListener.networkInitEnd(false, "访问服务器获取设备信息失败");
                    return;
                }
                Log.e(TAG, "networkConnect: deviceInfoModel =" + deviceInfoModel + ",访问服务器获取设备信息失败次数:" + requestCount);

                returnImei = deviceInfoModel.returnImei;
                operationListener.remoteDeviceInfoRespond(imei, deviceInfoModel);

                ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(serverUrlModel.serverUri, context, true);
                ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(deviceInfoModel.username, deviceInfoModel.password));

                remoteServerAvailable = initRemoteDir(deviceInfoModel.deviceName);

                requestCount = 0;

                while (!remoteServerAvailable && requestCount < 5 && networkIniting) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                    requestCount++;
                    Log.e(TAG, "networkConnect: initRemoteDir失败次数：" + requestCount);
                    remoteServerAvailable = initRemoteDir(deviceInfoModel.deviceName);
                }

                if (!remoteServerAvailable) {
                    networkIniting = false;
                    operationListener.networkInitEnd(false, "初始化远程文件夹");
                    return;
                }

                networkIniting = false;
                operationListener.networkInitEnd(true, "初始化成功");
            }
        });
    }


    private String getWorkingImei(Context context) {
        Log.d(TAG, "getWorkingImei: start .............................");
        int deviceStyle = 0;
        String imei = null;

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
            int type = activeNetworkInfo.getType();
            if (type == ConnectivityManager.TYPE_WIFI) {
                deviceStyle = 2;
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                deviceStyle = 1;
            }
        }

        if (deviceStyle == 1) {
            Log.d(TAG, "getWorkingImei: 当前是蜂窝设备");
            imei = DeviceUtils.getPhoneImei(context);
            int checktime = 0;
            while (imei == null && checktime < 20 && networkIniting) {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {

                }
                checktime++;
                imei = DeviceUtils.getPhoneImei(context);
                Log.e(TAG, "getWorkingImei run: imei =" + imei + ",checktime =" + checktime);
            }

            if (imei == null) {
                Log.d(TAG, "networkConnect:  读取20次 设备imei 失败，尝试从本地保存读取");
                ProfileModel profileModel = LocalProfileHelp.getInstance().getProfileFile(context);
                if (profileModel == null) {
                    Log.d(TAG, "networkConnect:  读取20次 设备imei 失败，尝试从本地保存读取，本地没有保存过，直接返回");
                    return null;
                }
                imei = profileModel.imei;
                if (imei == null) {
                    Log.d(TAG, "networkConnect:  读取20次 设备imei 失败，尝试从本地保存读取，本地有保存过，但是没有imei写失败");
                    return null;
                }
            }

            ProfileModel profileModel = new ProfileModel();
            profileModel.imei = imei;
            LocalProfileHelp.getInstance().saveProfileFile(context, profileModel);

        } else {
            Log.d(TAG, "getWorkingImei: 当前是wifi设备");
            ProfileModel profileModel = LocalProfileHelp.getInstance().getProfileFile(context);
            if (profileModel != null) {
                imei = profileModel.SN;
            }

            int checktime = 0;
            while (imei == null && networkIniting) {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                }
                profileModel = LocalProfileHelp.getInstance().getProfileFile(context);
                if (profileModel != null) {
                    imei = profileModel.SN;
                }
                checktime++;
                Log.e(TAG, "networkConnect  本地没有保存SN,等待U盘读取SN，checktime =" + checktime);
            }
        }
        return imei;
    }

    public static ServerUrlModel getServerUrl() {
        ServerUrlModel serverUrlModel = new ServerUrlModel();
        try {
            URL url = new URL(UrlUtils.serverUri);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();
            serverUrlModel.responseCode = ResponseCode;
            if (ResponseCode != 200) {
                return serverUrlModel;
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
            Log.d(TAG, "getServerUrl:  content = " + content);
            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
            jsonObject = new JSONObject(jsonArray.getString(0));
            serverUrlModel.stringServerUri = jsonObject.getString("url");
            Uri serverUri = Uri.parse(serverUrlModel.stringServerUri);
            serverUrlModel.serverUri = serverUri;
        } catch (Exception e) {
            Log.e(TAG, "getServerUrl: e =" + e);
        }
        return serverUrlModel;
    }

    public DeviceInfoModel getDeviceInfo(String phoneImei) {
        DeviceInfoModel deviceInfoModel = new DeviceInfoModel();
        try {
            URL url = new URL(UrlUtils.deviceInfoPrefix + phoneImei + UrlUtils.deviceInfoSuffix);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();
            deviceInfoModel.responseCode = ResponseCode;
            if (ResponseCode != 200) {
                return deviceInfoModel;
            }
            InputStream inputStream = urlcon.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);

            StringBuffer buffer = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            String content = buffer.toString();

            if (content != null && content.contains("无法查询到键值4g_imei为")) {
                return getDeviceInfoTest("202302050000001");
            }

            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
            jsonObject = new JSONObject(jsonArray.getString(0));

            JSONObject deviceObject = new JSONObject(jsonObject.getString("device"));
            deviceInfoModel.deviceName = deviceObject.getString("name");

            jsonArray = jsonObject.getJSONArray("deviceAttrList");

            for (int i = 0; i < jsonArray.length(); i++) {
                jsonObject = new JSONObject(jsonArray.getString(i));
                String attrKey = jsonObject.getString("attrKey");
                String attrVal = jsonObject.getString("attrVal");

                if ("imei".equals(attrKey)) {
                    deviceInfoModel.returnImei = attrVal;
                } else if ("upload_index".equals(attrKey)) {
                    deviceInfoModel.upload_index = attrVal;
                } else if ("upload_mode".equals(attrKey)) {
                    deviceInfoModel.upload_mode = attrVal;
                } else if ("yunpan_password".equals(attrKey)) {
                    deviceInfoModel.password = attrVal;
                } else if ("monitor_email".equals(attrKey)) {
                    deviceInfoModel.username = attrVal;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "getDeviceInfo: e =" + e);
        }
        return deviceInfoModel;
    }

    public DeviceInfoModel getDeviceInfoTest(String phoneImei) {
        Log.e(TAG, "getDeviceInfoTest: phoneImei =" + phoneImei);
        DeviceInfoModel deviceInfoModel = new DeviceInfoModel();
        try {
            URL url = new URL(UrlUtils.deviceInfoPrefix + phoneImei + UrlUtils.deviceInfoSuffix);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();
            deviceInfoModel.responseCode = ResponseCode;
            if (ResponseCode != 200) {
                return deviceInfoModel;
            }
            InputStream inputStream = urlcon.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);

            StringBuffer buffer = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            String content = buffer.toString();

            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
            jsonObject = new JSONObject(jsonArray.getString(0));

            JSONObject deviceObject = new JSONObject(jsonObject.getString("device"));
            deviceInfoModel.deviceName = deviceObject.getString("name");

            jsonArray = jsonObject.getJSONArray("deviceAttrList");

            for (int i = 0; i < jsonArray.length(); i++) {
                jsonObject = new JSONObject(jsonArray.getString(i));
                String attrKey = jsonObject.getString("attrKey");
                String attrVal = jsonObject.getString("attrVal");

                if ("imei".equals(attrKey)) {
                    deviceInfoModel.returnImei = attrVal;
                } else if ("upload_index".equals(attrKey)) {
                    deviceInfoModel.upload_index = attrVal;
                } else if ("upload_mode".equals(attrKey)) {
                    deviceInfoModel.upload_mode = attrVal;
                } else if ("yunpan_password".equals(attrKey)) {
                    deviceInfoModel.password = attrVal;
                } else if ("monitor_email".equals(attrKey)) {
                    deviceInfoModel.username = attrVal;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "getDeviceInfoTest: e =" + e);
        }
        return deviceInfoModel;
    }


    public boolean initRemoteDir(String deveceName) {

        userNameDir = FileUtils.PATH_SEPARATOR + deveceName + FileUtils.PATH_SEPARATOR;
        remoteCameraPictureDir = userNameDir + VariableInstance.remoteCameraRootDirName + FileUtils.PATH_SEPARATOR;
        remoteLogcatDir = userNameDir + VariableInstance.remoteLogcatRootDirName + FileUtils.PATH_SEPARATOR;
        Log.d(TAG, "initRemoteDir: " + "\n userNameDir =" + userNameDir + "\n remoteLogcatDir =" + remoteLogcatDir + "\n remoteCameraDir =" + remoteCameraPictureDir);

        int result = checkFileExit("/", userNameDir);
        if (result == requestFailure) {
            Log.d(TAG, "initRemoteDir: 请求远程用户路径失败");
            return false;
        }

        if (result == fileExist) {
            ReadFolderRemoteOperation refreshOperation = new ReadFolderRemoteOperation(deveceName);
            RemoteOperationResult remoteOperationResult = refreshOperation.execute(ownCloudClient);
            if (remoteOperationResult == null || !remoteOperationResult.isSuccess()) {
                Log.e(TAG, "initRemoteDir 获取用户文件列表失败: " + result);
                return false;
            }
            boolean exictP = false;
            boolean exictL = false;
            for (Object obj : remoteOperationResult.getData()) {
                RemoteFile remoteFile = (RemoteFile) obj;
                if (remoteFile.getRemotePath().contains(VariableInstance.remoteCameraRootDirName)) {
                    exictP = true;
                } else if (remoteFile.getRemotePath().contains(VariableInstance.remoteLogcatRootDirName)) {
                    exictL = true;
                }
            }
            if (!exictP && !createFilefolder(remoteCameraPictureDir)) {
                Log.e(TAG, "initRemoteDir: 创建远程相机照片根目录失败");
                return false;
            }
            if (!exictL && !createFilefolder(remoteLogcatDir)) {
                Log.e(TAG, "initRemoteDir: 创建远程日志根目录失败");
                return false;
            }
        } else {
            boolean createResult = createFilefolder(userNameDir);
            if (!createResult) {
                Log.d(TAG, "initRemoteDir: 远程创建客户名称路径出错");
                return false;
            }

            if (!createFilefolder(remoteCameraPictureDir)) {
                Log.e(TAG, "initRemoteDir: 创建远程相机照片根目录失败");
                return false;
            }
            if (!createFilefolder(remoteLogcatDir)) {
                Log.e(TAG, "initRemoteDir: 创建远程日志根目录失败");
                return false;
            }
        }
        getRemoteAllPictureDir(deveceName);
        if (!LocalProfileHelp.getInstance().initLocalRemotePictureList()) {
            createRemotePictureList(deveceName);
        }
        return true;
    }

    private boolean getRemoteAllPictureDir(String userName) {
        ReadFolderRemoteOperation dateReadFolderRemoteOperation = new ReadFolderRemoteOperation(userName + "/" + VariableInstance.remoteCameraRootDirName);
        RemoteOperationResult dateRemoteOperationResult = dateReadFolderRemoteOperation.execute(ownCloudClient);
        if (dateRemoteOperationResult == null || !dateRemoteOperationResult.isSuccess()) {
            Log.e(TAG, "getRemoteAllPictureDir: dateRemoteOperationResult =" + dateRemoteOperationResult);
            return false;
        }

        for (Object obj : dateRemoteOperationResult.getData()) {
            RemoteFile pictureDir = (RemoteFile) obj;
            String remotePath = pictureDir.getRemotePath();
            try {
                remotePath = remotePath.substring(remoteCameraPictureDir.length(), remotePath.length() - 1);
                if (remotePath == null || remotePath.trim().isEmpty()) {
                    continue;
                }

                if (!remotePictureDirYYYYMMList.contains(remotePath)) {
                    remotePictureDirYYYYMMList.add(remotePath);
                }
            } catch (Exception e) {

            }
        }
        return true;
    }

    public int checkFileExit(String parentDir, String sunDir) {
        Log.v(TAG, "checkFileExit: parentDir =" + parentDir + ",sunDir =" + sunDir);
        if (ownCloudClient == null)
            return requestFailure;
        ReadFolderRemoteOperation refreshOperation = new ReadFolderRemoteOperation(parentDir);
        RemoteOperationResult result = refreshOperation.execute(ownCloudClient);
        if (result == null || !result.isSuccess()) {
            Log.e(TAG, "checkFileExit 获取文件列表失败: " + result);
            return requestFailure;
        }
        boolean exict = false;
        for (Object obj : result.getData()) {
            RemoteFile remoteFile = (RemoteFile) obj;
            if (remoteFile.getRemotePath().equals(sunDir)) {
                exict = true;
                break;
            }
        }
        if (exict) {
            return fileExist;
        } else {
            return noExist;
        }
    }

    public boolean createFilefolder(String flieFolder) {
        Log.i(TAG, "createFilefolder: flieFolder =" + flieFolder);
        if (ownCloudClient == null)
            return false;
        CreateFolderRemoteOperation createOperation = new CreateFolderRemoteOperation(flieFolder, false);
        RemoteOperationResult result = createOperation.execute(ownCloudClient);
        if (result == null || !result.isSuccess()) {
            Log.e(TAG, "createImeiFilefolder 创建文件夹失败: " + result);
            return false;
        }
        return true;
    }

    public void createRemotePictureList(String userName) {
        Log.e(TAG, "createRemotePictureList: userName =" + userName);

        ReadFolderRemoteOperation dateReadFolderRemoteOperation = new ReadFolderRemoteOperation(remoteCameraPictureDir);
        RemoteOperationResult dateRemoteOperationResult = dateReadFolderRemoteOperation.execute(ownCloudClient);
        if (dateRemoteOperationResult == null || !dateRemoteOperationResult.isSuccess()) {
            Log.e(TAG, "createRemotePictureList: dateRemoteOperationResult =" + dateRemoteOperationResult);
            return;
        }

        FileWriter remotePictureFileWriter = null;
        try {
            File file = new File(LocalProfileHelp.remoteFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            remotePictureFileWriter = new FileWriter(file, true);
        } catch (Exception e) {
            Log.e(TAG, "createRemotePictureList: Exception =" + e);
            return;
        }

        for (Object obj : dateRemoteOperationResult.getData()) {
            RemoteFile dateRemoteFile = (RemoteFile) obj;
            Log.d(TAG, "createRemotePictureList:  dateRemoteFile =" + dateRemoteFile.getRemotePath());
            if (remoteCameraPictureDir.equals(dateRemoteFile.getRemotePath())) {
                Log.w(TAG, "createRemotePictureList:  dateRemoteFile =" + dateRemoteFile.getRemotePath());
                continue;
            }

            ReadFolderRemoteOperation dirReadFolderRemoteOperation = new ReadFolderRemoteOperation(dateRemoteFile.getRemotePath());
            RemoteOperationResult dirRemoteOperationResult = dirReadFolderRemoteOperation.execute(ownCloudClient);
            if (dirRemoteOperationResult == null || !dirRemoteOperationResult.isSuccess()) {
                Log.e(TAG, "createRemotePictureList: dirRemoteOperationResult =" + dirRemoteOperationResult);
                continue;
            }
            for (Object obj1 : dirRemoteOperationResult.getData()) {
                RemoteFile pictureRemoteFile = (RemoteFile) obj1;
                String pictureRemotePath = pictureRemoteFile.getRemotePath();
                pictureRemotePath = pictureRemotePath.substring(dateRemoteFile.getRemotePath().length());
                if (pictureRemotePath != null && pictureRemotePath.length() > 5) {
                    if (!LocalProfileHelp.getInstance().remotePictureList.contains(pictureRemotePath)) {
                        LocalProfileHelp.getInstance().remotePictureList.add(pictureRemotePath);
                        pictureRemotePath = pictureRemotePath + "\n";
                        try {
                            remotePictureFileWriter.write(pictureRemotePath);
                        } catch (IOException e) {
                            Log.e(TAG, "createRemotePictureList appendPathToRemotePictureFile: IOException =" + e);
                        }
                    }
                }
            }
        }

        try {
            remotePictureFileWriter.flush();
            remotePictureFileWriter.close();
        } catch (Exception e) {
            Log.e(TAG, "createRemotePictureList: remotePictureFileWriter FileNotFoundException =" + e);
        }
        LocalProfileHelp.getInstance().remoteListInit = true;
        Log.e(TAG, "createRemotePictureList: ............................ remotePictureList =" + LocalProfileHelp.getInstance().remotePictureList.size());
    }

    public void addUploadRemoteFile(String uploadFileModel, boolean uploadFaild) {
        Log.e(TAG, "addUploadRemoteFile: uploadFaild =" + uploadFaild + ",fileListCache =" + pictureFileListCache.size() + ",uploadFileModel =" + uploadFileModel);
        if (uploadFileModel == null)
            return;
        if (!pictureFileListCache.contains(uploadFileModel)) {
            pictureFileListCache.add(uploadFileModel);
        }

        if (remoteServerAvailable) {
            startCameraPictureUploadThread();
        }
    }

    public void startCameraPictureUploadThread() {

        if (uploadPictureThreadExecutor != null || !remoteServerAvailable) {
            Log.e(TAG, "startCameraPictureUploadThread: 已经存在上传线程 remoteServerAvailable =" + remoteServerAvailable  );
            return;
        }
        Log.d(TAG, "startCameraPictureUploadThread: ");

        uploadTatalTime = 0;
        uploadPictureThreadExecutor = Executors.newSingleThreadExecutor();
        uploadPictureThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                uploadingRemotePicture = true;
                while (!canStopUploadPictureThread()) {
                    try {
                        Log.e(TAG, "run: 缓存需要上传到服务器的照片数量，fileListCache.size =" + pictureFileListCache.size());
                        List<String> list = new ArrayList<>(pictureFileListCache);
                        Collections.sort(list, new MyOrder());
                        pictureFileListCache = new LinkedBlockingQueue<>(list);
                        String fileLocalPath = pictureFileListCache.poll(10, TimeUnit.SECONDS);

                        if (fileLocalPath == null) {
                            continue;
                        }
                        File file = new File(fileLocalPath);
                        if (file == null || !file.exists()) {
                            Log.e(TAG, "uploadImageFileToRemote: " + fileLocalPath + ",文件不存在");
                            continue;
                        }

                        long fileSize = 0;
                        FileInputStream fis = null;
                        try {
                            fis = new FileInputStream(file);
                            fileSize = fis.available();
                            fis.close();
                        } catch (Exception e) {
                            Log.e(TAG, "uploadImageFileToRemote: Exception =" + e);
                        }

                        long startTime = System.currentTimeMillis();
                        operationListener.pictureUploadStart();
                        boolean uploadResult = uploadImageFileToRemote(file);
                        operationListener.pictureUploadEnd();
                        if (uploadResult) {
                            PictureDateInfo pictureDateInfo = new PictureDateInfo(file.getName());
                            LocalProfileHelp.getInstance().addLocalRemotePictureList(pictureDateInfo.showName);
                            uploadRemoteCompletePictureNum++;
                            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
                            if (totalTime == 0) {
                                totalTime = 1;
                            }
                            int uploadSpeed = (int) (fileSize / totalTime / 1024);
                            Log.e(TAG, "uploadImageFileToRemote: 单张用时" + totalTime + "S,fileSize =" + fileSize + ",uploadSpeed =" + uploadSpeed);
                            if (file.exists()) {
                                file.delete();
                            }
                            operationListener.updateUploadSpeed(("" + uploadSpeed));
                        } else {
                            Log.e(TAG, "uploadImageFileToRemote: 上传失败");
                        }
                        uploadTatalTime += (System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        Log.e(TAG, "startUploadThread: Exception =" + e);
                        if (pictureFileListCache.size() == 0) {//TODO
                        }
                    }
                }
                operationListener.allPictureUploadEnd();
                uploadingRemotePicture = false;
            }
        });
    }


    private boolean canStopUploadPictureThread() {
        if (!remoteServerAvailable) {
            Log.e(TAG, "canStopUploadPictureThread: 网络不可用");
            return true;
        }

        if (pictureFileListCache.size() != 0) {
            Log.e(TAG, "canStopUploadPictureThread: pictureFileListCache.size() != 0");
            return false;
        }
        boolean canStop = operationListener.canStopUploadPictureThread();
        Log.e(TAG, "canStopUploadPictureThread: canStop =" + canStop);
        return canStop;
    }


    private boolean uploadImageFileToRemote(File file) {

        if (file == null || !file.exists()) {
            return false;
        }

        if (!remoteServerAvailable) {
            addUploadRemoteFile(file.getAbsolutePath(), true);
            return false;
        }

        String logcalFileName = file.getName();
        if (!DeviceUtils.fileIsPicture(logcalFileName)) {
            return false;
        }

        PictureDateInfo pictureDateInfo = new PictureDateInfo(logcalFileName);

        if (VariableInstance.getInstance().isUploadToday) {
            try {
                int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
                if (systemTime == 900101) {
                    if (file.exists()) {
                        file.delete();
                    }
                    return false;
                }
                if (Utils.isMoreThanThreeDate(pictureDateInfo.pictureCreateData)) {
                    Log.e(TAG, "uploadImageFileToRemote: 只上传最近3天的照片，当前照片不是最近3天不上传， 照片路径：" + file.getAbsolutePath());
                    if (file.exists()) {
                        file.delete();
                    }
                    return false;
                }
            } catch (Exception e) {

            }
        }


        String remoteDir;
        if (VariableInstance.getInstance().UploadMode == 5) {
            remoteDir = remoteCameraPictureDir + pictureDateInfo.hhmm + FileUtils.PATH_SEPARATOR;
            if (!remotePictureDirYYYYMMList.contains(pictureDateInfo.hhmm)) {
                if (createFilefolder(remoteDir)) {
                    remotePictureDirYYYYMMList.add(pictureDateInfo.hhmm);
                } else {
                    Log.e(TAG, "uploadImageFileToRemote: 创建远程hhmm文件夹失败 remoteDir =" + remoteDir);
                    return false;
                }
            }
        } else {
            remoteDir = remoteCameraPictureDir + pictureDateInfo.yyyyMM + FileUtils.PATH_SEPARATOR;
            if (!remotePictureDirYYYYMMList.contains(pictureDateInfo.yyyyMM)) {
                if (createFilefolder(remoteDir)) {
                    remotePictureDirYYYYMMList.add(pictureDateInfo.yyyyMM);
                } else {
                    Log.e(TAG, "uploadImageFileToRemote: 创建远程yyyymm文件夹失败 remoteDir =" + remoteDir);
                    return false;
                }
            }
        }


        String remotePath = remoteDir + pictureDateInfo.showName;
        Long timeStampLong = file.lastModified() / 1000;
        String timeStamp = timeStampLong.toString();
        Log.e(TAG, "uploadImageFileToRemote:remotePath =" + remotePath);
        UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(file.getAbsolutePath(), remotePath, "image/png", timeStamp);
        RemoteOperationResult result = uploadOperation.execute(ownCloudClient);

        int uploadFaildCount = 0;

        while ((result == null || !result.isSuccess()) && uploadFaildCount < 3 && remoteServerAvailable) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            uploadFaildCount++;
            Log.e(TAG, "uploadImageFileToRemote: 上传失败次数：" + uploadFaildCount);
            result = uploadOperation.execute(ownCloudClient);
        }

        if (result == null || !result.isSuccess()) {
            Log.e(TAG, "uploadImageFileToRemote: 上传失败 result =" + result);
            if (file.exists()) {
                file.delete();
            }
            return false;
        }
        Log.e(TAG, "uploadImageFileToRemote: 图片上传成功 .................................");
        return true;
    }


    public void uploadLogcatFileToRemote(UploadLogcatListener uploadLogcatListener) {
        Log.e(TAG, "uploadLogcatFileToRemote:  start ......................");
        new Thread(new Runnable() {
            @Override
            public void run() {

                if (!remoteServerAvailable) {
                    Log.e(TAG, "uploadLogcatFileToRemote: 远程服务器不可用，不需要上传日志");
                    if (uploadLogcatListener != null) {
                        uploadLogcatListener.uploadLogcatComplete(false,"远程服务器不可用，不需要上传日志");
                    }
                    return;
                }

                if (LogcatHelper.getInstance().mLogDumperMain == null) {
                    Log.e(TAG, "uploadLogcatFileToRemote: mLogDumperMain = null 没有找到日志 ");
                    if (uploadLogcatListener != null) {
                        uploadLogcatListener.uploadLogcatComplete(false,"mLogDumperMain = null 没有找到日志");
                    }
                    return;
                }

                try {
                    if (uploadLogcatListener != null) {
                /*        LogcatHelper.getInstance().stopMainLogcat();
                        try {
                            Thread.sleep(1000);
                            LogcatHelper.getInstance().stopMainLogcatRename();
                        } catch (Exception e) {
                        }*/
                        uploadLogcatListener.startUploadLogcatToUsb();
                    }

                    String path = LogcatHelper.getInstance().getMainLogcatPath();
                    File mainLogFile = new File(path);
                    if (mainLogFile == null || !mainLogFile.exists()) {
                        Log.e(TAG, "startUploadMainLocatThread 日志文件不存在：" + path);
                        if (uploadLogcatListener != null) {
                            uploadLogcatListener.uploadLogcatComplete(false,"日志文件不存在");
                        }
                        return;
                    }

                    String fileName = mainLogFile.getName();
                    int lastIndex = fileName.lastIndexOf(".");
                    if (lastIndex != -1) {
                        fileName = fileName.substring(0, lastIndex);
                    }

                    if (fileName.trim().contains("logcat1970")) {
                        Log.e(TAG, "run: 日志开始时1970，需要重命名");
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
                        String date = format.format(new Date(System.currentTimeMillis()));
                        fileName = "logcat" + date;
                    }
                    Long timeStampLong = System.currentTimeMillis() / 1000;
                    String timeStamp = timeStampLong.toString();

                    String remotePath = remoteLogcatDir + fileName + ".txt";
                    if (uploadLogcatListener == null) {
                        remotePath = remoteLogcatDir + fileName + "_" + System.currentTimeMillis() + ".txt";
                    }

                    Log.e(TAG, "run: uploadLogcatFileToRemote = " + remotePath);
                    UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(mainLogFile.getAbsolutePath(), remotePath, "text/plain", timeStamp);
                    RemoteOperationResult result = uploadOperation.execute(ownCloudClient);

                    if (result == null || !result.isSuccess()) {
                        Log.e(TAG, "uploadLogcatFileToRemote: 日志上传失败 .................................");
                        if (uploadLogcatListener != null) {
                            uploadLogcatListener.uploadLogcatComplete(false,"uploadOperation.execute失败");
                        }
                    } else {
                        Log.e(TAG, "uploadLogcatFileToRemote: 日志上传成功 .................................");
                        if (uploadLogcatListener != null) {
                            uploadLogcatListener.uploadLogcatComplete(true, fileName + ".txt");
                        }
                    }
                } catch (Exception e) {

                }
            }
        }).start();
    }

    public int getServiceVersion() {

        int servierVersion = 0;
        try {
            URL url = new URL(UrlUtils.appVersionURL);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();


            if (ResponseCode != 200) {
                Log.d(TAG, "getServiceVersion: 无法访问升级链接");
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
            Log.d(TAG, "getServiceVersion:  content = " + content);
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

    private boolean updateApp(int servierVersion) {
        Log.e(TAG, "updateApp: servierVersion =" + servierVersion);
        String downloadURL = UrlUtils.appDowloadURL + servierVersion;
        Utils.makeDir("/storage/emulated/0/Download/");
        String downloadPath_tpm = "/storage/emulated/0/Download/RemoteUpload_tpm.apk";
        String downloadPath = "/storage/emulated/0/Download/RemoteUpload.apk";
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
                    operationListener.downloadProgress((int) (curentLength / 1024));
                }
                downloadFileOutputStream.flush();
                downloadInputStream.close();
                downloadFileOutputStream.close();

                File apkFile = new File(downloadPath);
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                apkFile = new File(downloadPath);
                apkFileTpm.renameTo(apkFile);

                if (apkFile.exists()) {
                    boolean installResult = UpdateUtils.installSilent(downloadPath);
                    if (installResult) {
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            Log.d(TAG, "startDownloadApp: e =" + e);
        }
        return false;
    }


    public class MyOrder implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o2.toString().compareTo(o1.toString());
        }
    }

    public interface NetwrokOperationListener {
        void startUpdateApp();

        void downloadProgress(int progress);

        void endUpdateApp(boolean updateResult);

        void networkInitStart();

        void showWorkingImei(String imei);

        void networkInitEnd(boolean succeed, String message);

        void versionCodeResponse(int currentVersionCode, int serverVersionCode);

        void remoteDeviceInfoRespond(String deviceImei, DeviceInfoModel deviceInfoModel);

        boolean canStopUploadPictureThread();

        void allPictureUploadEnd();

        void pictureUploadStart();

        void pictureUploadEnd();

        void updateUploadSpeed(String speed);
    }

    public interface UploadLogcatListener {
        void uploadLogcatComplete(boolean succeed,String message);

        void startUploadLogcatToUsb();
    }
}
