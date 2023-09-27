package com.example.nextclouddemo;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.blankj.utilcode.util.ActivityUtils;
import com.blankj.utilcode.util.AppUtils;
import com.example.gpiotest.GpioActivity;
import com.example.gpiotest.LedControl;
import com.example.nextclouddemo.model.DeviceInfoModel;
import com.example.nextclouddemo.model.MyMessage;

import com.example.nextclouddemo.model.ServerUrlModel;
import com.example.nextclouddemo.model.UploadFileModel;
import com.example.nextclouddemo.mqtt.MqttManager;
import com.example.nextclouddemo.utils.Communication;
import com.example.nextclouddemo.utils.LocalProfileHelp;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.RemoteOperationUtils;
import com.example.nextclouddemo.utils.UpdateUtils;
import com.example.nextclouddemo.utils.Utils;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;

public class MainActivity extends Activity implements View.OnClickListener {
    public static final boolean debug = false;
    private static final String TAG = "remotelog_MainActivityl";
    private static final String FormatUSB = "Start,Format;";
    private static final String FormatTF = "Start,FormatTF;";
    private static final String FormatCamera = "Start,FormatCamera;";
    private static final String Upload = "Start,Upload;";
    private static final String UploadMode1 = "Set,UploadMode,1;";
    private static final String UploadMode2 = "Set,UploadMode,2;";
    private static final String UploadMode3 = "Set,UploadMode,3,";
    private static final String UploadMode4 = "Set,UploadMode,4,";
    private static final String GetInfo = "Get,Info;";

    private static final String UploadEndUploadUseTime = "Upload,End,UploadUseTime,";
    private static final String AppShutdownAck = "App,shutdown,ack;";
    private static final String UploadToday = "Set,UploadToday,";
    private static final String Set_FormatUdisk_All = "Set,FormatUdisk,All;";
    private static final String Set_FormatUdisk_2weeks = "Set,FormatUdisk,2weeks;";
    private static final String Set_FormatCamera_All = "Set,FormatCamera,All;";
    private static final String Set_FormatCamera_2weeks = "Set,FormatCamera,2weeks;";
    private static final String Set_Powoff = "Set,Powoff;";
    private static final String Set_UploadLocat = "Set,UploadLocat;";
    private static final String Set_WakeCamera = "Set,WakeCamera;";
    private static final String Set_ResetApk = "Set,ResetApk;";
    private static final String Set_UpdateBetaApk = "Set,UpdateBetaApk;";
    private static final String Set_UpdateFormalApk = "Set,UpdateFormalApk;";

    public static final String Update_StartDownloadAPK = "Update,StartDownloadAPK;";
    public static final String Update_DownloadAPKSucceed = "Update,DownloadAPKSucceed;";
    public static final String Update_DownloadAPKFaild = "Update,DownloadAPKFaild;";
    public static final String Update_InstallAPKStart = "Update,InstallAPKStart;";
    public static final String Update_InstallAPKSucceed = "Update,InstallAPKSucceed;";
    public static final String Update_InstallAPKFaild = "Update,InstallAPKFaild;";
    public static final String Debug_finish = "Debug_finish;";
    public static final String Debug_reset = "Debug_reset;";
    private static final int UPPOAD_LOGCAT_DELAY_TIME = 2 * 60 * 1000;
    private static final int CLOSE_DEVICE_DELAY_TIME = 3 * 60 * 1000;
    private static final int NETWORK_WAIT_TIME = 2 * 60 * 1000;
    private String returnImei;
    private String deveceName;

    private boolean isUpdating;

    private boolean openDeviceProtFlag;
    private int signalStrengthValue;
    private int appVerison;
    private int cameraPictureCount;
    private String cameraName;
    private String copySpeed;
    private int copyTotalNum;

    private MyHandler mHandler;

    private Communication communication;
    private ReceiverCamera receiverCamera;
    private ReceiverStoreUSB receiverStoreUSB;
    private RemoteOperationUtils remoteOperationUtils;
    private TextView messageText;
    private TextView UpanSpaceText;
    private TextView accessNumberText;
    private TextView cameraStateText;
    private TextView isConnectNetworkText;
    private TextView mqttStateText;
    private TextView UpanPictureCountText;
    private TextView uploadNumberText;
    private TextView uploadUseTimeText;
    private TextView hasUploadpictureNumberText;
    private TextView uploadModelText;
    private TextView remoteNameText;
    private TextView hasDownloadPictureNumberText;
    private TextView serverStateText;
    private TextView phoneNumberText;

    private TextView currentVersionText;
    private TextView serverVersionText;
    private TextView downloadAppProgressText;
    private TextView updateResultText;
    private TextView cameraPictureCountText;
    private TextView cameraDeviceText;

    private Button guanjiBt;
    private Button rescanerBt;
    private Button openProtActivityBt;
    private Button clearViewBt;
    private Button formatUSBt;
    private Button formatCameraBt;


    private UpdateUtils updateUtils;
    private NetWorkReceiver netWorkReceiver;

    private boolean sendShutDown;

    private static final String apkServerPackageName = "com.remoteupload.apkserver";

    private String messageTextString;


    private boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int getServerVersion(Context context, String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            return packageInfo.versionCode;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private boolean copyAPKServer(String localPath, String ASSETS_NAME, Context context) {
        File file = new File(localPath);
        if (file.exists()) {
            file.delete();
        }
        try {
            InputStream is = context.getResources().getAssets().open(ASSETS_NAME);
            FileOutputStream fos = new FileOutputStream(localPath);
            byte[] buffer = new byte[2048];
            int count = 0;
            while ((count = is.read(buffer)) > 0) {
                fos.write(buffer, 0, count);
            }
            fos.close();
            is.close();
            return true;
        } catch (Exception e) {

        }
        return false;
    }

    private void installAPKServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                String apkPath = "/storage/emulated/0/Download/apkServer.apk";
                boolean copyResult = copyAPKServer(apkPath, "apkServer.apk", MainActivity.this);
                if (!copyResult) {
                    Log.e(TAG, "installAPKServer: installAPKServer 拷贝文件出错， 服务安装异常");
                    return;
                }
                File apkFile = new File(apkPath);
                if (apkFile.exists()) {
                    BufferedReader es = null;
                    DataOutputStream os = null;
                    try {
                        Process process = Runtime.getRuntime().exec("su");
                        os = new DataOutputStream(process.getOutputStream());
                        String command = "pm install -r " + apkPath + "\n";
                        os.write(command.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.writeBytes("exit\n");
                        os.flush();

                        process.waitFor();
                        es = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                        String line;
                        StringBuilder builder = new StringBuilder();
                        while ((line = es.readLine()) != null) {
                            builder.append(line);
                        }
                        if (!builder.toString().contains("Failure")) {
                            Log.e(TAG, "installAPKServer: 安装服务apk成功");
                            removeDelayCreateActivity();
                            sendDelayCreateActivity(3000);
                        } else {
                            Log.e(TAG, "installAPKServer: 安装服务apk失败");
                            removeDelayCreateActivity();
                            sendDelayCreateActivity(3000);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "installAPKServer: 安装服务apk失败：" + e);
                        removeDelayCreateActivity();
                        sendDelayCreateActivity(3000);
                    } finally {
                        try {
                            if (os != null) {
                                os.close();
                            }
                            if (es != null) {
                                es.close();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "installAPKServer: 安装服务apk失败：" + e);
                        }
                    }
                }
            }
        }).start();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main_acitivity);
        openCameraDeviceProt(false);
        mHandler = new MyHandler(MainActivity.this);

        removeDelayCreateActivity();
        if (isAppInstalled(MainActivity.this, apkServerPackageName) && getServerVersion(MainActivity.this, apkServerPackageName) > 0) {
            sendDelayCreateActivity(3000);
        } else {
            Log.d(TAG, "onCreate: 需要等待安装守护线程");
            sendDelayCreateActivity(30000);
            installAPKServer();
        }

        messageText = findViewById(R.id.messageText);
        accessNumberText = findViewById(R.id.accessNumberText);
        cameraStateText = findViewById(R.id.cameraStateText);
        isConnectNetworkText = findViewById(R.id.isConnectNetworkText);
        UpanSpaceText = findViewById(R.id.UpanSpaceText);
        UpanPictureCountText = findViewById(R.id.UpanPictureCountText);
        uploadNumberText = findViewById(R.id.uploadNumberText);
        uploadUseTimeText = findViewById(R.id.uploadUseTimeText);
        hasUploadpictureNumberText = findViewById(R.id.hasUploadpictureNumberText);
        mqttStateText = findViewById(R.id.mqttStateText);
        uploadModelText = findViewById(R.id.uploadModelText);
        remoteNameText = findViewById(R.id.remoteNameText);
        hasDownloadPictureNumberText = findViewById(R.id.hasDownloadPictureNumberText);
        serverStateText = findViewById(R.id.serverStateText);
        phoneNumberText = findViewById(R.id.phoneNumberText);
        currentVersionText = findViewById(R.id.currentVersionText);
        serverVersionText = findViewById(R.id.serverVersionText);
        downloadAppProgressText = findViewById(R.id.downloadAppProgressText);
        updateResultText = findViewById(R.id.updateResultText);
        cameraPictureCountText = findViewById(R.id.cameraPictureCountText);
        cameraDeviceText = findViewById(R.id.cameraDeviceText);


        guanjiBt = findViewById(R.id.guanjiBt);
        rescanerBt = findViewById(R.id.rescanerBt);
        openProtActivityBt = findViewById(R.id.openProtActivityBt);
        clearViewBt = findViewById(R.id.clearViewBt);
        formatUSBt = findViewById(R.id.formatUSBt);
        formatCameraBt = findViewById(R.id.formatCameraBt);


        guanjiBt.setOnClickListener(this);
        rescanerBt.setOnClickListener(this);
        openProtActivityBt.setOnClickListener(this);
        clearViewBt.setOnClickListener(this);
        formatUSBt.setOnClickListener(this);
        formatCameraBt.setOnClickListener(this);

    }

    private void sendDelayCreateActivity(int delayTime) {
        mHandler.sendEmptyMessageDelayed(msg_delay_creta_acitivity, delayTime);
    }

    private void removeDelayCreateActivity() {
        mHandler.removeMessages(msg_delay_creta_acitivity);
    }

    private void sendUploadLogcatMessage(int position) {
        Log.e(TAG, "sendUploadLogcatMessage: position =" + position);
        mHandler.sendEmptyMessageDelayed(msg_start_upload_local_logcat_to_remote, UPPOAD_LOGCAT_DELAY_TIME);
    }

    private void removeUploadLogcatMessage(int position) {
        Log.e(TAG, "removeUploadLogcatMessage: position =" + position);
        mHandler.removeMessages(msg_start_upload_local_logcat_to_remote);
    }


    private void sendCloseDeviceMessage(int position, int delayTime) {
        Log.e(TAG, "sendCloseDeviceMessage: position" + position + ",delayTime =" + delayTime);
        mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, delayTime);
    }

    private void removeCloseDeviceMessage(int position) {
        Log.e(TAG, "removeCloseDeviceMessage: position =" + position);
        mHandler.removeMessages(msg_send_ShutDown);
    }

    @SuppressLint("SetTextI18n")
    private void initView() {
        AppUtils.AppInfo appInfo = AppUtils.getAppInfo(getPackageName());
        appVerison = appInfo.getVersionCode();
        currentVersionText.setText("当前版本：" + appVerison);
        Log.d(TAG, "initView: app当前版本 =" + appVerison);

        serverStateText.setText("服务器状态：false");
        remoteNameText.setText("云端名称：");
        accessNumberText.setText("入网号：");

        accessNumberText.setText("入网号:");
        cameraStateText.setText("相机状态:false");
        isConnectNetworkText.setText("是否连网:false");
        mqttStateText.setText("mqtt状态:false");
    }

    boolean isInitView;

    void delayCreate() {
        if (isInitView) {
            return;
        }
        isInitView = true;
        getUploadToday();
        VariableInstance.getInstance().resetAllData();


        communication = new Communication();
        if (updateUtils == null) updateUtils = new UpdateUtils(updateListener);
        remoteOperationUtils = new RemoteOperationUtils(remoteOperationListener);


        initView();

        String[] value = Utils.haveNoPermissions(MainActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }

        EventBus.getDefault().register(this);

        sendUploadLogcatMessage(1);
        getUploadModel();

        Utils.makeDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.makeDir(VariableInstance.getInstance().TFCardUploadPictureDir);

        openNetworkLed(true);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        mHandler.sendEmptyMessageDelayed(msg_connect_server_timeout, NETWORK_WAIT_TIME);

        registerWifiReceiver();
        registerStoreUSBReceiver();

    }


    PhoneStateListener MyPhoneListener = new PhoneStateListener() {
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            int asu = signalStrength.getGsmSignalStrength();
            signalStrengthValue = -113 + 2 * asu;
        }
    };

    private final UpdateUtils.UpdateListener updateListener = new UpdateUtils.UpdateListener() {


        @Override
        public void downloadProgress(int progress) {
            runOnUiThreadText(downloadAppProgressText, "app下载：" + progress + "kb");
        }

        @Override
        public void startUpdate() {
            Log.e(TAG, "startUpdate: ..................................");
            isUpdating = true;
            runOnUiThreadText(updateResultText, "开始升级");
            removeUploadLogcatMessage(1);
        }

        @Override
        public void endUpdate(boolean succeed) {
            isUpdating = false;
            VariableInstance.getInstance().isUpdatingBetaApk = false;
            runOnUiThreadText(updateResultText, "升级" + (succeed ? "成功" : "失败"));
            removeUploadLogcatMessage(2);
            sendUploadLogcatMessage(2);
        }
    };

    private final ReceiverStoreUSB.StoreUSBListener storeUSBListener = new ReceiverStoreUSB.StoreUSBListener() {
        @Override
        public void historyBackupPictureCount() {
            getInfo();
            if (receiverCamera == null) {
                registerReceiverCamera();
            }
        }

        @Override
        public void storeUSBPictureCount(int count) {
            runOnUiThreadText(UpanPictureCountText, "U盘图片总数:" + count);
        }


        @Override
        public void storeUSBSaveOnePictureComplete(String speed) {
            copySpeed = speed;
            runOnUiThreadText(hasDownloadPictureNumberText, "已下载张数:" + VariableInstance.getInstance().backupPicrureNum + "\n同步到USB速度:" + speed);
        }

        @Override
        public void sendInitUSBTimeOutMessage(int position) {
            Log.d(TAG, "sendInitUSBTimeOutMessage: position =" + position);
            mHandler.removeMessages(msg_usb_init_faild_delay);
            if (VariableInstance.getInstance().storeUSBDeviceID != -1) {
                return;
            }
            mHandler.sendEmptyMessageDelayed(msg_usb_init_faild_delay, debug ? 1000 : 40000);
        }

        @Override
        public void checkProfileFile(UsbFile wifiConfigurationFile) {

            int capacity = receiverStoreUSB.getStoreUSBCapacity();
            int freeSpace = receiverStoreUSB.getStoreUSBFreeSpace();
            Log.d(TAG, "checkProfileFile: capacity =" + capacity + ",freeSpace =" + freeSpace);
            runOnUiThreadText(UpanSpaceText, "U盘空间:" + "\ncapacity:" + capacity + "\nfreeSpace:" + freeSpace);

            Log.d(TAG, "checkProfileFile: .....................................");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    checkProfileFileMain(wifiConfigurationFile, 1);
                }
            }).start();

        }
    };

    private void checkProfileFileMain(UsbFile wifiConfigurationFile, int position) {
        Log.e(TAG, "checkProfileFileMain: start  position =" + position + ",wifiConfigurationFile =" + wifiConfigurationFile);
        ProfileModel profileModel = null;
        int deviceStyle = LocalProfileHelp.getInstance().checkDeviceStyle();
        if (deviceStyle == 1 || deviceStyle == 0) {//蜂窝版
            if (deviceStyle == 1) {
                Log.e(TAG, "checkProfileFileMain: 蜂窝版里面怎么放了个配置文件");
            }
            if (deviceStyle == 0) {
                Log.e(TAG, "checkProfileFileMain: 未知原因，有配置文件，模块接口没识别到");
            }
            String imei = getPhoneImei();
            Log.d(TAG, "checkProfileFileMain: 11 imei =" + imei);
            if (imei == null) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                imei = getPhoneImei();
            }

            Log.d(TAG, "checkProfileFileMain: 22 imei =" + imei);
            if (imei == null) {
                ProfileModel parseMode = parseUSBFile(wifiConfigurationFile);
                if (parseMode == null) {
                    Log.e(TAG, "checkProfileFileMain: 读取不到imei,配置文件也无法解析");
                    profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                    if (profileModel == null) {
                        Log.e(TAG, "checkProfileFileMain: 读取不到imei,配置文件也无法解析,本地也没有保存 position =" + position);
                        return;
                    }
                } else {
                    profileModel = new ProfileModel();
                    profileModel.SN = parseMode.SN;
                    profileModel.pass = parseMode.pass;
                    profileModel.wifi = parseMode.wifi;
                    LocalProfileHelp.getInstance().saveProfileFile(MainActivity.this, profileModel);
                }

            } else {
                profileModel = new ProfileModel();
                profileModel.imei = imei;
                LocalProfileHelp.getInstance().saveProfileFile(MainActivity.this, profileModel);
            }
        } else if (deviceStyle == 2) {//wifi版
            ProfileModel parseMode = parseUSBFile(wifiConfigurationFile);
            if (parseMode == null) {
                Log.e(TAG, "checkProfileFileMain: 配置文件也无法解析");
                profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                if (profileModel == null) {
                    Log.e(TAG, "checkProfileFileMain: 配置文件也无法解析,本地也没有保存 positon=" + position);
                    return;
                }
            } else {
                profileModel = new ProfileModel();
                profileModel.SN = parseMode.SN;
                profileModel.pass = parseMode.pass;
                profileModel.wifi = parseMode.wifi;
                LocalProfileHelp.getInstance().saveProfileFile(MainActivity.this, profileModel);
            }
        }

        if (profileModel == null) {
            Log.e(TAG, "checkProfileFileMain: iemi无法读取,配置文件也无法解析,本地也没有保存,不执行联网 mqtt 无法通信 posion =" + position);
            return;
        }

        if (profileModel.wifi != null) {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                boolean wifiEnable = wifiManager.isWifiEnabled();
                if (wifiEnable) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().contains(profileModel.wifi)) {
                        Log.e(TAG, "checkProfileFileMain: 当前自动链接上WiFi " + wifiInfo.getSSID() + ",position=" + position);
                        return;
                    }
                    if (profileModel.pass == null) {
                        connectWifiNoPws(profileModel.wifi, wifiManager);
                    } else {
                        if (profileModel.pass.length() == 0) {
                            connectWifiNoPws(profileModel.wifi, wifiManager);
                        } else {
                            connectWifiPws(profileModel.wifi, profileModel.pass, wifiManager);
                        }
                    }
                } else {
                    wifiManager.setWifiEnabled(true);
                }
            }
        }

        Log.e(TAG, "checkProfileFileMain: end  position =" + position + ",wifiConfigurationFile =" + wifiConfigurationFile);
    }

    private void initUSBFaild() {
        if (VariableInstance.getInstance().storeUSBDeviceID != -1) {
            return;
        }
        Log.e(TAG, "initUSBFaild: U盘初始化失败，仍然连接mqtt通信");
        new Thread(new Runnable() {
            @Override
            public void run() {
                checkProfileFileMain(null, 2);
            }
        }).start();
    }

    private final ReceiverCamera.CameraScanerListener scannerCameraListener = new ReceiverCamera.CameraScanerListener() {

        @Override
        public void cameraOperationStart() {
            mHandler.removeMessages(msg_open_device_timeout);
            if (!VariableInstance.getInstance().isUploadingToRemote) {
                startDownLed(true);
            }
            removeUploadLogcatMessage(3);
        }

        @Override
        public void cameraOperationEnd(int cameraTotalPicture) {

            openCameraDeviceProt(false);

            if (!VariableInstance.getInstance().isUploadingToRemote) {
                startDownLed(false);
            }

            SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
            int scanerCount = sharedPreferences.getInt("ScanerCount", 0);

            Log.d(TAG, "cameraOperationEnd: remoteUploading =" + VariableInstance.getInstance().isUploadingToRemote + ",scanerCount =" + scanerCount + ",cameraTotalPicture =" + cameraTotalPicture);
            if (!isUpdating && cameraTotalPicture == 0 && scanerCount < 5) {
                scanerCount++;
                SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
                editor.putInt("ScanerCount", scanerCount);
                editor.apply();
                mHandler.sendEmptyMessageDelayed(msg_delay_open_device_prot, 3000);
                sendSet_WakeCamera();
                return;
            }

            SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
            editor.putInt("ScanerCount", 0);
            editor.apply();

            if (!VariableInstance.getInstance().isUploadingToRemote) {
                removeUploadLogcatMessage(4);
                sendUploadLogcatMessage(3);
            }
            getInfo();
        }


        @Override
        public boolean uploadToUSB(File localFile, String yearMonth) {
            if (receiverStoreUSB == null) {
                return false;
            }

            boolean uploadResult = receiverStoreUSB.uploadToUSB(localFile, yearMonth);

            return uploadResult;
        }

        @Override
        public void addUploadRemoteFile(UploadFileModel uploadFileModel) {
            remoteOperationUtils.addUploadRemoteFile(uploadFileModel, false);
        }


        @Override
        public void scannerCameraComplete(int needDownloadCount, int cameraTotalPictureCount, String deviceName) {
            Log.e(TAG, "scannerCameraComplete: needDownloadConut =" + needDownloadCount + ",cameraTotalPictureCount =" + cameraTotalPictureCount + ",deviceName =" + deviceName);

            copyTotalNum = needDownloadCount;
            cameraPictureCount = cameraTotalPictureCount;
            cameraName = deviceName;

            runOnUiThreadText(uploadNumberText, "本次从相机同步到U盘数量:" + needDownloadCount);
            runOnUiThreadText(cameraPictureCountText, "相机照片总数：" + cameraTotalPictureCount);
            runOnUiThreadText(cameraDeviceText, "相机名称：" + deviceName);
        }


    };


    RemoteOperationUtils.RemoteOperationListener remoteOperationListener = new RemoteOperationUtils.RemoteOperationListener() {
        @Override
        public void allFileUploadComplete(long totalTime) {
            Log.d(TAG, "allFileUploadComplete: 所有文件上传完成 totalTime =" + totalTime / 1000 + "s");

            VariableInstance.getInstance().isUploadingToRemote = false;

            if (!VariableInstance.getInstance().isOperationCamera) {
                restLed();
            }
            if (totalTime != 0) {
                UploadUseTime = totalTime / 1000;
            }
            runOnUiThreadText(uploadUseTimeText, "本次同步到服务器耗时:" + totalTime / 1000 + "s");

            getInfo();

            removeUploadLogcatMessage(5);
            sendUploadLogcatMessage(4);
        }

        @Override
        public void pictureUploadStart() {
            Log.d(TAG, "fileUploadStart: ");
            VariableInstance.getInstance().isUploadingToRemote = true;
            removeUploadLogcatMessage(6);
            startDownLed(false);
        }

        @Override
        public void pictureUploadEnd(boolean uploadResult) {
            Log.d(TAG, "fileUploadEnd: uploadResult =" + uploadResult);
            VariableInstance.getInstance().isUploadingToRemote = false;
        }


        @Override
        public void updateUploadSpeed(String speed) {
            Log.d(TAG, "updateUploadSpeed: speed =" + speed);
            UploadSpeed = speed;
            runOnUiThreadText(hasUploadpictureNumberText, "已上传张数:" + VariableInstance.getInstance().uploadRemorePictureNum + "\n上传服务器速度：" + speed);
            getInfo();
        }

        @Override
        public void uploadLogcatComplete() {
            getInfo();

            sendShutDown = true;
            removeCloseDeviceMessage(1);
            sendCloseDeviceMessage(1, CLOSE_DEVICE_DELAY_TIME);
            sendMessageToMqtt(UploadEndUploadUseTime + UploadUseTime + ";");
        }

        @Override
        public boolean canStopPictureUploadThread() {
            Log.e(TAG, "canStopPictureUploadThread: 是否可以停止上传服务器线程  isOperationCamera= " + VariableInstance.getInstance().isOperationCamera + ",isScanningStoreUSB =" + VariableInstance.getInstance().isScanningStoreUSB);
            return (!VariableInstance.getInstance().isScanningStoreUSB && !VariableInstance.getInstance().isOperationCamera);
        }


        @Override
        public void startUploadLogcatToUsb() {
            if (receiverStoreUSB != null) {
                receiverStoreUSB.uploadLogcatToUSB();
            }
        }
    };


    public void sendSet_WakeCamera() {
        sendMessageToMqtt(Set_WakeCamera);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiverCamera() {
        Log.d(TAG, "registerReceiverCamera: ");
        receiverCamera = new ReceiverCamera(getApplicationContext(), scannerCameraListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(VariableInstance.GET_STORE_CAMERA_PERMISSION);
        registerReceiver(receiverCamera, intentFilter);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStoreUSBReceiver() {
        receiverStoreUSB = new ReceiverStoreUSB(getApplicationContext(), storeUSBListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(VariableInstance.GET_STORE_USB_PERMISSION);
        registerReceiver(receiverStoreUSB, intentFilter);
    }


    private void registerWifiReceiver() {
        netWorkReceiver = new NetWorkReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(netWorkReceiver, filter);
    }


    private boolean netWorkConnectBroadConnet;

    private void netWorkConnectBroad() {
        netWorkConnectBroadConnet = true;
        if (updateUtils == null) {
            updateUtils = new UpdateUtils(updateListener);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                int version = UpdateUtils.checkServiceVersion();

                while (version == 0 && netWorkConnectBroadConnet) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                    version = UpdateUtils.checkServiceVersion();
                }

                mHandler.sendEmptyMessage(msg_network_available);

                AppUtils.AppInfo appInfo = AppUtils.getAppInfo(MyApplication.getContext().getPackageName());
                int appVerison = appInfo.getVersionCode();
                Log.e(TAG, "run: app当前版本 =" + appVerison + ",远程版本 =" + version);
                runOnUiThreadText(serverVersionText, "最新版本：" + version);

                if (version > appVerison) {
                    updateUtils.startDownloadApk(version);
                }
            }
        }).start();
    }

    private void netWorkDissConnectBroad() {
        Log.e(TAG, "netWorkDissConnectBroad: ");
        netWorkConnectBroadConnet = false;
        VariableInstance.getInstance().remoteServerAvailable = false;
        VariableInstance.getInstance().remoteServerConnecting = false;


        VariableInstance.getInstance().ownCloudClient = null;
        runOnUiThreadText(isConnectNetworkText, "是否连网:false");
        runOnUiThreadText(mqttStateText, "mqtt状态:false");
        remoteOperationUtils.stopUploadThread();
        MqttManager.getInstance().release();
        openNetworkLed(false);
        openNetworkLed(true);
        updateUtils.networkLost();
    }


    private void networkConnect() {
        Log.e(TAG, "networkConnect ");
        runOnUiThreadText(isConnectNetworkText, "是否连网:true");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    while (VariableInstance.getInstance().remoteServerConnecting) {
                        try {
                            Thread.sleep(3000);
                        } catch (Exception e) {
                        }
                    }

                    VariableInstance.getInstance().remoteServerConnecting = true;

                    ServerUrlModel serverUrlModel = communication.getServerUrl();
                    int count = 0;
                    while ((serverUrlModel == null || serverUrlModel.responseCode != 200) && netWorkConnectBroadConnet) {
                        count++;
                        Log.e(TAG, "run: 访问服务器获取url失败次数 ：" + count);
                        try {
                            Thread.sleep(3000);
                        } catch (Exception e) {
                        }
                        updateServerStateUI(false);
                        serverUrlModel = communication.getServerUrl();
                    }
                    Log.e(TAG, "initAddress: serverUrlModel =" + serverUrlModel + ",访问服务器获取url失败次数：" + count);
                    if (serverUrlModel == null || serverUrlModel.responseCode != 200) {
                        updateServerStateUI(false);
                        VariableInstance.getInstance().remoteServerConnecting = false;
                        return;
                    }

                    int deviceStyle = LocalProfileHelp.getInstance().checkDeviceStyle();

                    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                        int type = activeNetworkInfo.getType();
                        Log.e(TAG, "initAddress run:  activeNetworkInfo.getType() =" + type);
                        if (type == ConnectivityManager.TYPE_WIFI) {
                            deviceStyle = 2;
                        } else if (type == ConnectivityManager.TYPE_MOBILE) {
                            deviceStyle = 1;
                        }
                    }


                    ProfileModel profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                    String imei = null;
                    if (profileModel == null) {
                        Log.e(TAG, "initAddress run: 11无法获取到配置信息，无法通信");
                        VariableInstance.getInstance().remoteServerConnecting = false;

                        if (deviceStyle == 1) {
                            imei = getPhoneImei();
                        } else if (deviceStyle == 2) {
                            try {
                                Thread.sleep(10000);
                                profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                            } catch (Exception e) {

                            }

                            if (profileModel == null) {
                                Log.e(TAG, "initAddress run:444 无法获取到配置信息，无法通信");
                                return;
                            } else if (profileModel.SN == null && profileModel.imei == null) {
                                Log.e(TAG, "initAddress run:5555 无法获取到配置信息，无法通信");
                                return;
                            } else {
                                imei = profileModel.SN;
                                if (imei == null) {
                                    imei = profileModel.imei;
                                }
                            }
                            if (imei == null) {
                                Log.e(TAG, "initAddress run:6666 无法获取到配置信息，无法通信");
                                return;
                            }
                        } else {
                            Log.e(TAG, "initAddress run:22无法获取到配置信息，无法通信");
                            return;
                        }

                        if (imei == null) {
                            Log.e(TAG, "initAddress run:33 无法获取到配置信息，无法通信");
                            return;
                        }
                    }

                    if (deviceStyle == 1) {
                        imei = profileModel.imei;
                    } else if (deviceStyle == 2) {
                        imei = profileModel.SN;
                    }
                    if (imei == null) {
                        if (profileModel.imei != null) {
                            imei = profileModel.imei;
                        }
                        if (profileModel.SN != null) {
                            imei = profileModel.SN;
                        }
                    }
                    if (imei == null) {
                        Log.e(TAG, "initAddress run: 无法获取到配置信息，无法通信");
                        VariableInstance.getInstance().remoteServerConnecting = false;
                        return;
                    }

                    Log.e(TAG, "initAddress run: imei =" + imei);
                    count = 0;

                    DeviceInfoModel deviceInfoModel = communication.getDeviceInfo(imei);

                    while ((deviceInfoModel == null || deviceInfoModel.responseCode != 200) && netWorkConnectBroadConnet) {
                        count++;
                        Log.e(TAG, "run: 访问服务器获取设备信息失败次数：" + count);
                        try {
                            Thread.sleep(3000);
                        } catch (Exception e) {
                        }
                        updateServerStateUI(false);
                        deviceInfoModel = communication.getDeviceInfo(imei);
                    }
                    Log.e(TAG, "initAddress: deviceInfoModel =" + deviceInfoModel + ",访问服务器获取设备信息失败次数:" + count);

                    networkUpdateUploadModel(deviceInfoModel);


                    returnImei = deviceInfoModel.returnImei;
                    deveceName = deviceInfoModel.deveceName;


                    boolean mqttConnect = MqttManager.isConnected();
                    Log.e(TAG, "run: mqttConnect =" + mqttConnect);
                    if (!mqttConnect) {
                        MqttManager.getInstance().creatConnect("tcp://120.78.192.66:1883", "devices", "a1237891379", "" + imei, "/camera/v1/device/" + returnImei + "/android");
                        MqttManager.getInstance().subscribe("/camera/v2/device/" + returnImei + "/android/send", 1);
                    }
                    mHandler.sendEmptyMessage(msg_activity_heart);

                    VariableInstance.getInstance().ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(serverUrlModel.serverUri, MainActivity.this, true);
                    VariableInstance.getInstance().ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(deviceInfoModel.username, deviceInfoModel.password));

                    VariableInstance.getInstance().remoteServerAvailable = remoteOperationUtils.initRemoteDir(deviceInfoModel.deveceName);

                    Log.d(TAG, "initAddress:   配置远程服务器是否成功 =" + VariableInstance.getInstance().remoteServerAvailable);
                    updateServerStateUI(VariableInstance.getInstance().remoteServerAvailable);
                    if (VariableInstance.getInstance().remoteServerAvailable) {
                        mHandler.removeMessages(msg_connect_server_timeout);
                        mHandler.sendEmptyMessage(msg_connect_server_complete);
                        removeUploadLogcatMessage(9);
                        remoteOperationUtils.startCameraPictureUploadThread();
                        remoteOperationUtils.startUploadTestLocatThread(false);
                    } else {
                        mHandler.removeMessages(msg_connect_server_timeout);
                        mHandler.sendEmptyMessage(msg_connect_server_timeout);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "initAddress:远程连接出现异常 = " + e);
                    updateServerStateUI(false);
                }
                VariableInstance.getInstance().remoteServerConnecting = false;
            }
        }).start();


    }


    private void runOnUiThreadText(TextView textView, String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (textView == null || text == null) {
                    return;
                }
                textView.setText(text);
            }
        });
    }

    private void networkUpdateUploadModel(DeviceInfoModel deviceInfoModel) {
        if (deviceInfoModel == null) {
            return;
        }
        if (deviceInfoModel.upload_index != null && !deviceInfoModel.upload_index.isEmpty() && deviceInfoModel.upload_mode != null && !deviceInfoModel.upload_mode.isEmpty()) {
            SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
            editor.putInt("UploadMode", VariableInstance.getInstance().UploadMode);
            editor.putString("UploadModeMessage", deviceInfoModel.upload_index);
            editor.apply();
            try {
                VariableInstance.getInstance().UploadMode = Integer.parseInt(deviceInfoModel.upload_mode);
                if (VariableInstance.getInstance().UploadMode == 3 || VariableInstance.getInstance().UploadMode == 4) {
                    VariableInstance.getInstance().uploadSelectIndexList.clear();
                    String[] data = deviceInfoModel.upload_index.split(",");
                    if (data != null) {
                        for (String datum : data) {
                            try {
                                VariableInstance.getInstance().uploadSelectIndexList.add(Integer.parseInt(datum));
                            } catch (Exception e) {

                            }
                        }
                    }
                }
            } catch (Exception e) {

            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiverCamera != null) {
            unregisterReceiver(receiverCamera);
        }
        if (receiverStoreUSB != null) {
            unregisterReceiver(receiverStoreUSB);
        }
        if (netWorkReceiver != null) {
            unregisterReceiver(netWorkReceiver);
        }

        mHandler.removeCallbacksAndMessages(null);

        openCameraDeviceProt(false);
        if (remoteOperationUtils != null) remoteOperationUtils.stopUploadThread();

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_NONE);
        EventBus.getDefault().unregister(this);
        MqttManager.getInstance().release();

        LogcatHelper.getInstance().stopTestLogcat();
        LogcatHelper.getInstance().stopMainLogcat();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                LogcatHelper.getInstance().stopMainLogcat();
                LogcatHelper.getInstance().stopTestLogcatRename();
            }
        }, 300);

        Log.e(TAG, "onDestroy: .................................................");
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void receiveLogMessage(MyMessage message) {
        if (message == null) return;
        messageTextString = messageTextString + "\n" + message.message + "\n";
        messageText.setText(messageTextString);
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void receiveMqttMessage(String message) {
        if (message == null) return;

        mqttStateText.setText("mqtt状态:true");
        if (message.contains(UploadMode3)) {
            UploadMode3(message);
            getInfo();
            return;
        }

        if (message.contains(UploadMode4)) {
            UploadMode4(message);
            getInfo();
            sendMessageToMqtt("ZQ\r\n");
            return;
        }

        if (message.contains(UploadToday)) {
            changeUploadToday(message);
            sendMessageToMqtt("ZQ\r\n");
        }

        switch (message) {

            case FormatUSB:
            case FormatTF:
            case Set_FormatUdisk_All:
                formatUSB(true);
                break;
            case FormatCamera:
            case Set_FormatCamera_All:
                formatCamera(true);
                break;
            case Set_FormatUdisk_2weeks:
                formatUSB(false);
                break;
            case Set_FormatCamera_2weeks:
                formatCamera(false);
                break;
            case Upload:
                break;
            case UploadMode1:
                UploadMode1();
                getInfo();
                sendMessageToMqtt("ZQ\r\n");
                break;
            case UploadMode2:
                UploadMode2();
                getInfo();
                sendMessageToMqtt("ZQ\r\n");
                break;
            case GetInfo:
                getInfo();
                sendMessageToMqtt("ZQ\r\n");
                break;
            case AppShutdownAck:
                AppShutdownAck();
                break;

            case "mqttConnectionLost":
                mqttStateText.setText("mqtt状态:false");
                break;
            case "mqttDeliveryComplete":
                mqttStateText.setText("mqtt状态:true");
                break;
            case Set_Powoff: {
                if (!isUpdating) {
                    Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
                    Utils.closeAndroid();
                }
            }
            break;
            case Set_UploadLocat:
                if (remoteOperationUtils != null) {
                    remoteOperationUtils.startUploadMainLocatThread(false);
                }
                break;
            case Set_ResetApk: {
                if (!isUpdating) {
                    restartDevice();
                }
            }
            break;
            case Set_UpdateBetaApk:
                updateBetaApk();
                break;
            case Set_UpdateFormalApk:
                sendMessageToMqtt(Set_UpdateFormalApk);
                break;
            case Update_StartDownloadAPK:
                sendMessageToMqtt(Update_StartDownloadAPK);
                break;
            case Update_DownloadAPKSucceed:
                sendMessageToMqtt(Update_DownloadAPKSucceed);
                break;
            case Update_DownloadAPKFaild:
                sendMessageToMqtt(Update_DownloadAPKFaild);
                break;
            case Update_InstallAPKStart:
                sendMessageToMqtt(Update_InstallAPKStart);
                break;
            case Update_InstallAPKSucceed:
                sendMessageToMqtt(Update_InstallAPKSucceed);
                break;
            case Update_InstallAPKFaild:
                sendMessageToMqtt(Update_InstallAPKFaild);
                break;
            case Debug_finish:
                finish();
                break;
            case Debug_reset:
                LocalProfileHelp.getInstance().resetBackup();
                break;
        }
    }


    private void updateBetaApk() {
        if (isUpdating) {
            return;
        }
        if (VariableInstance.getInstance().isUpdatingBetaApk) {
            return;
        }
        VariableInstance.getInstance().isUpdatingBetaApk = true;
        if (updateUtils == null) {
            updateUtils = new UpdateUtils(updateListener);
        }
        updateUtils.checkBetaApk(MainActivity.this);

    }

    private void AppShutdownAck() {
        if (sendShutDown) {
            removeCloseDeviceMessage(2);
            sendCloseDeviceMessage(2, 5000);
            sendShutDown = false;
        }
    }


    private void formatUSB(boolean all) {
        if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
            Log.d(TAG, "正在格式化USB，无需重复操作");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (VariableInstance.getInstance().isOperationCamera || VariableInstance.getInstance().isScanningStoreUSB) {
                    try {
                        Log.e(TAG, "run: 等待格式化");
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {

                    }
                }
                openCameraDeviceProt(false);
                Log.e(TAG, "formatUSB: start .......................................");
                VariableInstance.getInstance().isFormatingUSB.formatState = all ? 1 : 2;
                runOnUiThreadText(formatUSBt, "开始删除USB图片");


                if (remoteOperationUtils != null) {
                    remoteOperationUtils.stopUploadThread();
                }

                if (receiverStoreUSB != null) {

                    boolean exception = receiverStoreUSB.formatStoreUSB();
                    runOnUiThreadText(formatUSBt, exception ? "格式化USB失败" : "格式化USB成功");

                    Log.e(TAG, "formatUSB: " + (exception ? "格式化USB失败" : "格式化USB成功"));

                    if (exception) {
                        sendMessageToMqtt("ZR\r\n");
                    } else {
                        sendMessageToMqtt("ZQ\r\n");
                    }
                }

                Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
                Utils.resetDir(VariableInstance.getInstance().TFCardUploadPictureDir);
                Utils.resetDir(VariableInstance.getInstance().LogcatDir);


                VariableInstance.getInstance().isFormatingUSB.formatState = 0;
                if (isUpdating) {
                    return;
                }
                restartDevice();
            }
        }).start();
    }


    private void restartDevice() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
            proc.waitFor();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void formatCamera(boolean all) {
        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            return;
        }
        Log.e(TAG, "formatCamera: start ......................................... delect all =" + all);

        new Thread(new Runnable() {
            @Override
            public void run() {
                VariableInstance.getInstance().isFormaringCamera.formatState = all ? 1 : 2;
                if (receiverCamera != null) {
                    receiverCamera.formatCamera();
                }
                sendCloseDeviceMessage(3, CLOSE_DEVICE_DELAY_TIME);
                openCameraDeviceProt(false);
                openCameraDeviceProt(true);
            }
        }).start();
    }


    private void getInfo() {
        sendMessageToMqtt(serverGetInfo());
    }


    private void sendMessageToMqtt(String message) {
        if (returnImei != null) {
            MqttManager.getInstance().publish("/camera/v2/device/" + returnImei + "/android/receive", 1, message);
        }
    }

    @SuppressLint("SetTextI18n")
    private void UploadMode1() {
        VariableInstance.getInstance().UploadMode = 1;
        saveUploadModel(null);
        uploadModelText.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }

    @SuppressLint("SetTextI18n")
    private void UploadMode2() {
        VariableInstance.getInstance().UploadMode = 2;
        saveUploadModel(null);
        uploadModelText.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }


    private void changeUploadToday(String message) {
        message = message.replace("Set,UploadToday,", "");
        message = message.replace(";", "");
        boolean isUploadToday = "1".equals(message);
        saveUploadToday(isUploadToday);
    }

    @SuppressLint("SetTextI18n")
    private void UploadMode3(String message) {
        message = message.substring(UploadMode3.length(), message.length() - 1);
        String[] data = message.split(",");
        VariableInstance.getInstance().UploadMode = 3;

        boolean setModelResult = true;

        VariableInstance.getInstance().uploadSelectIndexList.clear();
        if (data != null && data.length > 0) {
            for (String datum : data) {
                try {
                    VariableInstance.getInstance().uploadSelectIndexList.add(Integer.parseInt(datum));
                } catch (Exception e) {
                    setModelResult = false;
                }
            }
        } else {
            setModelResult = false;
        }
        if (setModelResult) {
            saveUploadModel(message);
            sendMessageToMqtt("ZQ\r\n");
        } else {
            sendMessageToMqtt("ZR\r\n");
        }
        uploadModelText.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }


    @SuppressLint("SetTextI18n")
    private void UploadMode4(String message) {
        message = message.substring(UploadMode4.length(), message.length() - 1);
        String[] data = message.split(",");
        VariableInstance.getInstance().UploadMode = 4;
        boolean setModelResult = true;
        VariableInstance.getInstance().uploadSelectIndexList.clear();
        if (data != null && data.length > 0) {
            for (String datum : data) {
                try {
                    VariableInstance.getInstance().uploadSelectIndexList.add(Integer.parseInt(datum));
                } catch (Exception e) {
                    setModelResult = false;
                }
            }
        } else {
            setModelResult = false;
        }
        if (setModelResult) {
            saveUploadModel(message);
            sendMessageToMqtt("ZQ\r\n");
        } else {
            sendMessageToMqtt("ZR\r\n");
        }
        uploadModelText.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }


    private void updateServerStateUI(boolean succeed) {
        runOnUiThreadText(serverStateText, "服务器状态：" + succeed);
        if (succeed) {
            runOnUiThreadText(remoteNameText, "云端名称:" + deveceName);
        }
    }

    private int capacity = 0;
    private int freeSpace = 0;

    private long UploadUseTime;
    private String UploadSpeed;

    private String serverGetInfo() {
        int capacityA = 0;
        int freeSpaceA = 0;


        if (receiverStoreUSB != null) {
            capacityA = receiverStoreUSB.getStoreUSBCapacity();
            if (capacityA != 0) {
                capacity = capacityA;
            }
            freeSpaceA = receiverStoreUSB.getStoreUSBFreeSpace();
            if (freeSpaceA != 0) {
                freeSpace = freeSpaceA;
            }

        }

        String uploadModelString;

        if (VariableInstance.getInstance().UploadMode == 1) {
            uploadModelString = "1,0";

        } else if (VariableInstance.getInstance().UploadMode == 2) {
            uploadModelString = "2,0";

        } else if (VariableInstance.getInstance().UploadMode == 3) {
            if (VariableInstance.getInstance().uploadSelectIndexList.size() == 0) uploadModelString = "3,0";
            else {
                uploadModelString = "3";
                for (Integer integer : VariableInstance.getInstance().uploadSelectIndexList) {
                    uploadModelString = uploadModelString + "," + integer;
                }
            }

        } else {
            if (VariableInstance.getInstance().uploadSelectIndexList.size() == 0) uploadModelString = "4,0";
            else {
                uploadModelString = "4";
                for (Integer integer : VariableInstance.getInstance().uploadSelectIndexList) {
                    uploadModelString = uploadModelString + "," + integer;
                }
            }
        }

        if (UploadSpeed == null) {
            UploadSpeed = "0";
        }

        if (copySpeed == null) copySpeed = "0";
        String info = "4gCcid," + getPhoneNumber() + ";UploadSpeed," + UploadSpeed + ";4gCsq," + getSignalStrength() + ";SdFree," + freeSpace + ";SdFull," + capacity + ";PhotoSum," + VariableInstance.getInstance().currentUSBPictureCount + ";PhotoUploadThisTime," + VariableInstance.getInstance().uploadRemorePictureNum + ";UploadMode," + uploadModelString + ";UploadUseTime," + UploadUseTime + ";Version," + appVerison + ";connectCamera," + VariableInstance.getInstance().isConnectCamera + ";cameraPictureCount," + cameraPictureCount + ";cameraName," + cameraName + ";waitUploadPhoto," + (remoteOperationUtils == null ? 0 : remoteOperationUtils.pictureFileListCache.size()) + ";copySpeed," + copySpeed + ";copyTotalNum," + copyTotalNum + ";copyCompleteNum," + VariableInstance.getInstance().backupPicrureNum + ";";
        return info;
    }

    private String getSignalStrength() {
        if (signalStrengthValue > 0) {
            return "0";
        } else if (signalStrengthValue > -55) {
            return "4";
        } else if (signalStrengthValue > -70) {
            return "3";
        } else if (signalStrengthValue > -85) {
            return "2";
        } else if (signalStrengthValue > -100) {
            return "1";
        } else {
            return "1";
        }
    }

    @SuppressLint("HardwareIds")
    private String getPhoneNumber() {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String number = telephonyManager.getLine1Number();

            if (number == null) {
                number = telephonyManager.getSimSerialNumber();
            }

            if (number == null || number.length() == 0) {
                number = "0";
            }
            Log.d(TAG, "getPhoneNumber: 卡号 =" + number);

            runOnUiThreadText(phoneNumberText, "卡号:" + number);

            return number;
        } catch (Exception | Error e) {
            Log.e(TAG, "getPhoneNumber: Exception =" + e);
            return "0";
        }
    }

    @SuppressLint("HardwareIds")
    private String getPhoneImei() {
        String imei = null;
        try {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            imei = telephonyManager.getDeviceId();
        } catch (Exception | Error e) {
            Log.e(TAG, "getPhoneImei: Exception =" + e);
        }
        if (debug) imei = "202302050000001";
        return imei;
    }


    private void openCameraDeviceProt(boolean open) {
        Log.e(TAG, "openCameraDeviceProt: 连接相机通信端口 led: " + (open ? "打开" : "关闭") + ", 当前状态" + (openDeviceProtFlag ? "打开" : "关闭") + "-------------------------------------------");
        if (openDeviceProtFlag && open) return;

        openDeviceProtFlag = open;
        runOnUiThreadText(cameraStateText, "相机状态:" + openDeviceProtFlag);

        if (debug) return;
        if (open) {
            LedControl.writeGpio('b', 2, 1);
            mHandler.removeMessages(msg_open_device_timeout);
            mHandler.sendEmptyMessageDelayed(msg_open_device_timeout, 20000);
        } else {
            LedControl.writeGpio('b', 2, 0);
        }
    }

    private void openNetworkLed(boolean open) {
        Log.d(TAG, "openNetworkLed: 网络端口 led: " + (open ? "打开" : "关闭"));
        if (debug) return;
        if (open) {
            LedControl.writeGpio('b', 3, 1);//打开网络
        } else {
            LedControl.writeGpio('b', 3, 0);//打开网络
        }
    }

    private void startDownLed(boolean start) {
        Log.d(TAG, "startDownLed: 下载 led: " + (start ? "打开" : "关闭"));
        if (debug) return;
        if (start) {
            LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_TIMER);
        } else {
            LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_HEARTBEAT);
        }
    }

    private void restLed() {
        Log.d(TAG, "restLed:  恢复 led ------");
        if (debug) return;
        LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_NONE);
        LedControl.nativeEnableLed(LedControl.LED_RED_BRIGHTNESS_PATH, LedControl.LED_OFF);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.rescanerBt) {
            openCameraDeviceProt(false);
            openCameraDeviceProt(true);
        } else if (view.getId() == R.id.guanjiBt) {
            Utils.closeAndroid();
        } else if (view.getId() == R.id.openProtActivityBt) {
            startActivity(new Intent(MainActivity.this, GpioActivity.class));
        } else if (view.getId() == R.id.clearViewBt) {
            messageTextString = "";
            messageText.setText(messageTextString);
        } else if (view.getId() == R.id.formatUSBt) {
            formatUSB(true);
        } else if (view.getId() == R.id.formatCameraBt) {
            formatCamera(true);
        }
    }


    private ProfileModel parseUSBFile(UsbFile wifiConfigurationFile) {
        if (wifiConfigurationFile == null) {
            return null;
        }
        InputStream instream = null;
        String wifiName = null;
        String pass = null;
        String SN = null;
        try {
            String content = "";
            instream = new UsbFileInputStream(wifiConfigurationFile);
            if (instream != null) {
                InputStreamReader inputreader = new InputStreamReader(instream, "GBK");
                BufferedReader buffreader = new BufferedReader(inputreader);
                String line = "";
                //分行读取
                while ((line = buffreader.readLine()) != null) {
                    content += line + "\n";
                }
                instream.close();        //关闭输入流
            }
            Log.e(TAG, "initWifiConfigurationFile: content =" + content);
            String[] data = content.split("\n");

            if (data == null) return null;
            for (String datum : data) {
                if (datum == null) continue;
                datum.trim();
                if (datum.startsWith("wifi:")) {
                    try {
                        wifiName = datum.substring(5);
                    } catch (Exception e) {

                    }
                } else if (datum.startsWith("pass:")) {
                    try {
                        pass = datum.substring(5);
                    } catch (Exception e) {

                    }
                } else if (datum.startsWith("SN:")) {
                    try {
                        SN = datum.substring(3);
                    } catch (Exception e) {

                    }
                }
            }

            Log.e(TAG, "parseUSBFile: wifi =" + wifiName + ",pass =" + pass + ",SN =" + SN);

        } catch (Exception e) {
            Log.e(TAG, "initWifiConfigurationFile Exception =" + e);
        } finally {
            try {
                if (instream != null) {
                    instream.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "saveUSBFileToPhoneDevice:finally IOException =" + e);
            }
        }
        if (wifiName == null || SN == null) return null;

        ProfileModel deviceModel = new ProfileModel();
        deviceModel.wifi = wifiName;
        deviceModel.pass = pass;
        deviceModel.SN = SN;
        return deviceModel;
    }


    private void saveUploadModel(String message) {
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putInt("UploadMode", VariableInstance.getInstance().UploadMode);
        editor.putString("UploadModeMessage", message);
        editor.apply();
    }


    private void saveUploadToday(boolean isUploadToday) {
        VariableInstance.getInstance().isUploadToday = isUploadToday;
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putBoolean("isUploadToday", isUploadToday);
        editor.apply();
    }

    private void getUploadToday() {
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        VariableInstance.getInstance().isUploadToday = sharedPreferences.getBoolean("isUploadToday", true);
    }

    @SuppressLint("SetTextI18n")
    private void getUploadModel() {
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        VariableInstance.getInstance().UploadMode = sharedPreferences.getInt("UploadMode", 3);
        String mssage = sharedPreferences.getString("UploadModeMessage", "1");

        uploadModelText.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);

        if (VariableInstance.getInstance().UploadMode == 3 || VariableInstance.getInstance().UploadMode == 4) {

            VariableInstance.getInstance().uploadSelectIndexList.clear();
            String[] data = mssage.split(",");
            if (data != null) {
                for (String datum : data) {
                    try {
                        VariableInstance.getInstance().uploadSelectIndexList.add(Integer.parseInt(datum));
                    } catch (Exception e) {

                    }
                }
            }
        }
    }


    private boolean canCloseDevice() {
        boolean canCloseDevice;


        Log.e(TAG, "canCloseDevice: 是否可以关闭设备 " + ",isUploadingToRemote=" + VariableInstance.getInstance().isUploadingToRemote + ",isOperationCamera =" + VariableInstance.getInstance().isOperationCamera + ",isScanningStoreUSB =" + VariableInstance.getInstance().isScanningStoreUSB + ",isUpdating=" + isUpdating + ",remoteServerConnecting=" + VariableInstance.getInstance().remoteServerConnecting);

        canCloseDevice = !VariableInstance.getInstance().isUploadingToRemote && !VariableInstance.getInstance().isOperationCamera && !VariableInstance.getInstance().isScanningStoreUSB && remoteOperationUtils.pictureIsThreadStop && !isUpdating && !VariableInstance.getInstance().remoteServerConnecting;
        Log.e(TAG, "canCloseDevice: 是否可以关闭设备 =" + canCloseDevice);
        return canCloseDevice;
    }


    /**
     * 有密码连接
     *
     * @param ssid
     * @param pws
     */
    public void connectWifiPws(String ssid, String pws, WifiManager wifiManager) {
        wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
        int netId = wifiManager.addNetwork(getWifiConfig(ssid, pws, true, wifiManager));
        boolean enableNetwork = wifiManager.enableNetwork(netId, true);
        Log.d(TAG, "connectWifiPws: enableNetwork =" + enableNetwork);
    }

    /**
     * 无密码连接
     *
     * @param ssid
     */
    public void connectWifiNoPws(String ssid, WifiManager wifiManager) {
        wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
        int netId = wifiManager.addNetwork(getWifiConfig(ssid, "", false, wifiManager));
        wifiManager.enableNetwork(netId, true);
    }

    /**
     * wifi设置
     *
     * @param ssid
     * @param pws
     * @param isHasPws
     */
    private WifiConfiguration getWifiConfig(String ssid, String pws, boolean isHasPws, WifiManager wifiManager) {

        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + ssid + "\"";

        WifiConfiguration tempConfig = isExist(ssid, wifiManager);
        if (tempConfig != null) {
            wifiManager.removeNetwork(tempConfig.networkId);
        }
        if (isHasPws) {
            config.preSharedKey = "\"" + pws + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        return config;
    }

    /**
     * 得到配置好的网络连接
     *
     * @param ssid
     * @return
     */
    private WifiConfiguration isExist(String ssid, WifiManager wifiManager) {
        @SuppressLint("MissingPermission") List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configs) {

            if (config.SSID.equals("\"" + ssid + "\"")) {

                return config;
            }
        }
        return null;
    }


    private class NetWorkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            int statusInt = bundle.getInt("wifi_state");
            switch (statusInt) {

                case WifiManager.WIFI_STATE_ENABLED:
                    Log.d(TAG, "onReceive: WIFI_STATE_ENABLED");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            checkProfileFileMain(null, 3);
                        }
                    }).start();
                    break;
                case WifiManager.WIFI_STATE_DISABLED:
                    Log.d(TAG, "onReceive: WIFI_STATE_DISABLED");
                    break;
                default:
                    break;
            }

            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    Log.d(TAG, "NetWorkReceiver: 网络断开广播");
                    mHandler.removeMessages(msg_network_connect);
                    mHandler.removeMessages(msg_network_disconnect);
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    Log.d(TAG, "NetWorkReceiver: 网络连接广播");
                    mHandler.removeMessages(msg_network_connect);
                    mHandler.sendEmptyMessageDelayed(msg_network_connect, 2000);
                }
            }
        }
    }


    private static final int msg_start_upload_local_logcat_to_remote = 2;
    private static final int msg_send_ShutDown = 3;
    private static final int msg_network_connect = 4;
    private static final int msg_network_disconnect = 5;
    private static final int msg_network_available = 6;

    private static final int msg_delay_creta_acitivity = 7;
    private static final int msg_delay_open_device_prot = 8;
    private static final int msg_usb_init_faild_delay = 9;
    private static final int msg_open_device_timeout = 10;

    private static final int msg_connect_server_timeout = 11;
    private static final int msg_connect_server_complete = 12;

    private static final int msg_activity_heart = 13;


    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> weakReference;

        MyHandler(MainActivity activity) {
            weakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = weakReference.get();
            if (activity == null || activity.mHandler == null) {
                return;
            }
            switch (msg.what) {
                case msg_start_upload_local_logcat_to_remote:
                    if (activity.canCloseDevice()) {
                        activity.remoteOperationUtils.startUploadMainLocatThread(true);
                    } else {
                        activity.removeUploadLogcatMessage(10);
                        activity.sendUploadLogcatMessage(6);
                    }
                    break;
                case msg_send_ShutDown:
                    activity.sendShutDown = false;
                    Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
                    if (true)//TODO hu
                    {
                        activity.sendMessageToMqtt("测试阶段，不关机");
                        return;
                    }
                    Utils.closeAndroid();
                    break;
                case msg_network_connect:
                    activity.netWorkConnectBroad();
                    break;
                case msg_network_disconnect:
                    activity.netWorkDissConnectBroad();
                    break;
                case msg_network_available:
                    activity.networkConnect();
                    break;

                case msg_delay_creta_acitivity:
                    activity.delayCreate();
                    break;
                case msg_usb_init_faild_delay:
                    activity.initUSBFaild();
                    break;
                case msg_open_device_timeout:
                    if (activity.receiverCamera != null) {
                        activity.receiverCamera.openDeviceTimeOut();
                    }
                    break;

                case msg_delay_open_device_prot:
                    activity.openCameraDeviceProt(true);
                    break;
                case msg_connect_server_timeout:
                    activity.openCameraDeviceProt(true);
                    break;
                case msg_connect_server_complete:
                    activity.openCameraDeviceProt(true);
                    break;
                case msg_activity_heart:
                    int isAppRunning = 0;
                    int uid = getPackageUid(activity, apkServerPackageName);
                    if (uid > 0) {
                        boolean rstA = isAppRunning(activity, apkServerPackageName);
                        boolean rstB = isProcessRunning(activity, uid);
                        if (rstA || rstB) {
                            isAppRunning = 1;
                        } else {
                            //指定包名的程序未在运行中
                            isAppRunning = 2;
                        }
                    } else {
                        //应用未安装
                        isAppRunning = 3;
                    }
                    activity.mHandler.sendEmptyMessageDelayed(msg_activity_heart, 10000);
                    activity.sendMessageToMqtt("应用心跳包,isAppRunning =" + isAppRunning + ";");
                    break;
            }
        }
    }


    public static boolean isAppRunning(Context context, String packageName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(100);
        if (list.size() <= 0) {
            return false;
        }
        for (ActivityManager.RunningTaskInfo info : list) {
            if (info.baseActivity.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static int getPackageUid(Context context, String packageName) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (applicationInfo != null) {
                return applicationInfo.uid;
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    /**
     * 判断某一 uid 的程序是否有正在运行的进程，即是否存活
     * Created by cafeting on 2017/2/4.
     *
     * @param context 上下文
     * @param uid     已安装应用的 uid
     * @return true 表示正在运行，false 表示没有运行
     */
    public static boolean isProcessRunning(Context context, int uid) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServiceInfos = am.getRunningServices(200);
        if (runningServiceInfos.size() > 0) {
            for (ActivityManager.RunningServiceInfo appProcess : runningServiceInfos) {
                if (uid == appProcess.uid) {
                    return true;
                }
            }
        }
        return false;
    }

}
