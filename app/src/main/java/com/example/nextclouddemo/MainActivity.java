package com.example.nextclouddemo;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.Switch;
import android.widget.TextView;

import com.blankj.utilcode.util.AppUtils;
import com.example.gpiotest.GpioActivity;
import com.example.gpiotest.LedControl;
import com.example.nextclouddemo.model.DeviceInfoModel;
import com.example.nextclouddemo.model.MyMessage;

import com.example.nextclouddemo.model.ServerUrlModel;
import com.example.nextclouddemo.model.UploadFileModel;
import com.example.nextclouddemo.mqtt.MqttManager;
import com.example.nextclouddemo.utils.Communication;
import com.example.nextclouddemo.utils.FormatLisener;
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
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.List;

import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;

public class MainActivity extends Activity implements View.OnClickListener {
    public static final boolean debug = false;
    private static final String TAG = "remotelog_MainActivityl";
    private static final String Exit_UploadAPP_Action = "Exit_UploadAPP_Action";

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

    private static final int UPPOAD_LOGCAT_DELAY_TIME = 5 * 60 * 1000;
    private static final int CLOSE_DEVICE_DELAY_TIME = 2 * 60 * 1000;
    private static final int NETWORK_WAIT_TIME = 3 * 60 * 1000;

    private static final String FormatFlagBroadcast = "FormatFlagBroadcast";

    private String returnImei;
    private String deviceImei;
    private String deveceName;

    private boolean isUpdating;

    private boolean openDeviceProtFlag = true;
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
    private TextView backupNumberText;
    private TextView uploadUseTimeText;
    private TextView hasUploadpictureNumberText;
    private TextView uploadModelText;
    private TextView remoteNameText;
    private TextView hasDownloadPictureNumberText;
    private TextView serverStateText;
    private TextView phoneNumberText;
    private TextView imeiNumberText;
    private TextView shutDownTimeText;

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
    private CommunicationReceiver communicationReceiver;

    private boolean sendShutDown;

    private static final String apkServerPackageName = "com.remoteupload.apkserver";

    private String messageTextString;


    private void installAPKServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                String apkPath = "/storage/emulated/0/Download/apkServer.apk";
                boolean copyResult = Utils.copyAPKServer(apkPath, "app-release.apk", MainActivity.this);
                if (!copyResult) {
                    Log.e(TAG, "installAPKServer: installAPKServer 拷贝文件出错， 服务安装异常");
                    return;
                }
                File apkFile = new File(apkPath);
                if (apkFile.exists()) {

                    boolean installSucceed = Utils.installApk(apkPath);
                    if (installSucceed) {
                        sendBroadcastToServer("安装serverApk成功");
                        Log.w(TAG, "run: 安装serverApk成功");
                        Utils.startRemoteActivity();
                        removeDelayCreateActivity();
                        sendDelayCreateActivity(3000);
                    } else {
                        Log.w(TAG, "run: 安装失败，卸载serverApk");
                        Utils.uninstallapk();
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                        }
                        installSucceed = Utils.installApk(apkPath);

                        if (installSucceed) {
                            Log.w(TAG, "run:安装serverApk成功");
                            sendBroadcastToServer("安装serverApk成功");
                            Utils.startRemoteActivity();
                            removeDelayCreateActivity();
                            sendDelayCreateActivity(3000);
                        } else {
                            Log.w(TAG, "run:安装serverApk失败");
                            sendBroadcastToServer("安装serverApk失败");
                            removeDelayCreateActivity();
                            sendDelayCreateActivity(3000);
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
        shutDownTimeText = findViewById(R.id.shutDownTimeText);

        openCameraDeviceProt(false, 6);
        mHandler = new MyHandler(MainActivity.this);
        openNetworkLed(true);


        registerCommunicationReceiver();


        if (!checkFormatFlag()) {
            startService(new Intent(MainActivity.this, MyServer.class));

            setLEDState(1);

            removeDelayCreateActivity();
            if (Utils.isAppInstalled(MainActivity.this, apkServerPackageName) && Utils.getServerVersionName(MainActivity.this, apkServerPackageName).contains("1.0.22")) {
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
            backupNumberText = findViewById(R.id.backupNumberText);
            uploadUseTimeText = findViewById(R.id.uploadUseTimeText);
            hasUploadpictureNumberText = findViewById(R.id.hasUploadpictureNumberText);
            mqttStateText = findViewById(R.id.mqttStateText);
            uploadModelText = findViewById(R.id.uploadModelText);
            remoteNameText = findViewById(R.id.remoteNameText);
            hasDownloadPictureNumberText = findViewById(R.id.hasDownloadPictureNumberText);
            serverStateText = findViewById(R.id.serverStateText);
            imeiNumberText = findViewById(R.id.imeiNumberText);
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


        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        mHandler.sendEmptyMessageDelayed(msg_connect_server_timeout, NETWORK_WAIT_TIME);

        registerWifiReceiver();
        registerStoreUSBReceiver();

    }


    private boolean checkFormatFlag() {
        int type = getFormatFlag();//0: 不需要格式化，1：格式化U盘 2：格式化相机
        Log.e(TAG, "checkFormatFlag: type =" + type);
        if (type == 1) {
            saveFormatFlag(0);
            formatStoregeUSB();
            return true;
        } else if (type == 2) {
            saveFormatFlag(0);
            formatCameraDevice();
            return true;
        }
        return false;
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
            sendBroadcastToServer("startUpdate");
        }

        @Override
        public void endUpdate(boolean succeed) {
            isUpdating = false;
            VariableInstance.getInstance().isUpdatingBetaApk = false;
            runOnUiThreadText(updateResultText, "升级" + (succeed ? "成功" : "失败"));
            removeUploadLogcatMessage(2);
            sendUploadLogcatMessage(2);
            sendBroadcastToServer("endUpdate：" + succeed);
        }
    };

    private final ReceiverStoreUSB.StoreUSBListener storeUSBListener = new ReceiverStoreUSB.StoreUSBListener() {
        @Override
        public void historyBackupPictureCount() {
            getInfo();
            removeUploadLogcatMessage(3);
            sendUploadLogcatMessage(3);
            if (receiverCamera == null) {
                registerReceiverCamera();
            }
            sendBroadcastToServer("historyBackupPictureCount");
        }

        @Override
        public void storeUSBPictureCount(int count) {
            removeUploadLogcatMessage(4);
            sendUploadLogcatMessage(4);
            runOnUiThreadText(UpanPictureCountText, "U盘图片总数:" + count);
            sendBroadcastToServer("storeUSBPictureCount：count=" + count);
        }


        @Override
        public void storeUSBSaveOnePictureComplete(String speed) {
            copySpeed = speed;
            runOnUiThreadText(hasDownloadPictureNumberText, "已下载张数:" + VariableInstance.getInstance().backupPicrureNum + "\n同步到USB速度:" + speed);
        }

        @Override
        public void requestPermissionUSBStart() {
            mHandler.removeMessages(msg_usb_request_permission_timeout);
            mHandler.sendEmptyMessageDelayed(msg_usb_request_permission_timeout, 10000);
        }

        @Override
        public void getPermissionUSB() {
            mHandler.removeMessages(msg_usb_request_permission_timeout);
        }

        @Override
        public void checkServerUSBOpenration() {
            sendOrderedBroadcast(new Intent("checkServerUSBOpenration"), null);
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
            Log.d(TAG, "checkProfileFile: .....................................");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    checkProfileFileMain(wifiConfigurationFile, 1);
                }
            }).start();

        }

        @Override
        public void storegeUSBSpaceInitData() {
            int capacity = receiverStoreUSB.getStoreUSBCapacity();
            int freeSpace = receiverStoreUSB.getStoreUSBFreeSpace();
            Log.d(TAG, "storegeUSBSpaceInitData: capacity =" + capacity + ",freeSpace =" + freeSpace);
            runOnUiThreadText(UpanSpaceText, "U盘空间:" + "\ncapacity:" + capacity + "\nfreeSpace:" + freeSpace);

        }
    };

    private void checkProfileFileMain(UsbFile wifiConfigurationFile, int position) {
        Log.e(TAG, "checkProfileFileMain: start  position =" + position + ",wifiConfigurationFile =" + wifiConfigurationFile);
        ProfileModel profileModel = null;
        int deviceStyle = LocalProfileHelp.getInstance().checkDeviceStyle();
        if (deviceStyle == 1 || deviceStyle == 0) {//蜂窝版
            if (deviceStyle == 1 && wifiConfigurationFile != null) {
                Log.e(TAG, "checkProfileFileMain: 蜂窝版里面怎么放了个配置文件");
            }
            if (deviceStyle == 0 && wifiConfigurationFile != null) {
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

        sendBroadcastToServer("initUSBFaild");
        if (receiverCamera == null) {
            registerReceiverCamera();
        }
        openCameraDeviceProt(true, 7);
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
            removeUploadLogcatMessage(5);
            sendBroadcastToServer("cameraOperationStart");
        }

        @Override
        public void cameraOperationEnd(int cameraTotalPicture) {
            sendBroadcastToServer("cameraOperationEnd：cameraTotalPicture =" + cameraTotalPicture);
            openCameraDeviceProt(false, 8);


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
                removeUploadLogcatMessage(6);
                sendUploadLogcatMessage(5);
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
        public void scannerCameraComplete(int needDownloadCount, int needUpload, int cameraTotalPictureCount, String deviceName) {
            Log.e(TAG, "scannerCameraComplete: needDownloadConut =" + needDownloadCount + ",cameraTotalPictureCount =" + cameraTotalPictureCount + ",deviceName =" + deviceName);

            copyTotalNum = needDownloadCount;
            cameraPictureCount = cameraTotalPictureCount;
            cameraName = deviceName;

            runOnUiThreadText(uploadNumberText, "本次从相机同步到服务器数量:" + needUpload);
            runOnUiThreadText(backupNumberText, "本次从相机同步到U盘数量:" + needDownloadCount);
            runOnUiThreadText(cameraPictureCountText, "相机照片总数：" + cameraTotalPictureCount);
            runOnUiThreadText(cameraDeviceText, "相机名称：" + deviceName);
            sendBroadcastToServer("scannerCameraComplete");
        }


    };


    RemoteOperationUtils.RemoteOperationListener remoteOperationListener = new RemoteOperationUtils.RemoteOperationListener() {
        @Override
        public void allFileUploadComplete(long totalTime) {
            Log.d(TAG, "allFileUploadComplete: 所有文件上传完成 totalTime =" + totalTime / 1000 + "s");

            VariableInstance.getInstance().isUploadingToRemote = false;


            if (totalTime != 0) {
                UploadUseTime = totalTime / 1000;
            }
            runOnUiThreadText(uploadUseTimeText, "本次同步到服务器耗时:" + totalTime / 1000 + "s");

            getInfo();

            removeUploadLogcatMessage(7);
            sendUploadLogcatMessage(6);
            sendBroadcastToServer("allFileUploadComplete");
        }

        @Override
        public void pictureUploadStart() {
            Log.d(TAG, "fileUploadStart: ");
            VariableInstance.getInstance().isUploadingToRemote = true;
            removeUploadLogcatMessage(8);

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
            sendBroadcastToServer("uploadLogcatComplete");

            if (netWorkConnectBroadConnet) {
                setLEDState(3);
            } else {
                setLEDState(1);
            }
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


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerWifiReceiver() {
        netWorkReceiver = new NetWorkReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(netWorkReceiver, filter);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerCommunicationReceiver() {
        communicationReceiver = new CommunicationReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Exit_UploadAPP_Action);
        registerReceiver(communicationReceiver, filter);
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

        setLEDState(1);

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

        removeUploadLogcatMessage(9);
        sendUploadLogcatMessage(7);
        setLEDState(2);

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

                    int deviceStyle = 0;
                    String imei = null;

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

                    if (deviceStyle == 1) {
                        imei = getPhoneImei();
                        int checktime = 0;
                        while (imei == null && netWorkConnectBroadConnet && checktime < 20) {
                            try {
                                Thread.sleep(5000);
                            } catch (Exception e) {

                            }
                            checktime++;
                            imei = getPhoneImei();
                            Log.e(TAG, "initAddress run: imei =" + imei + ",checktime =" + checktime);
                        }

                        if (imei == null) {
                            ProfileModel profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                            if (profileModel == null) {
                                Log.e(TAG, "initAddress run: 11无法获取到配置信息，无法通信");
                                VariableInstance.getInstance().remoteServerConnecting = false;
                                return;
                            }
                            imei = profileModel.imei;
                            if (imei == null) {
                                imei = profileModel.SN;
                            }
                        }
                        if (imei != null) {
                            ProfileModel profileModel = new ProfileModel();
                            profileModel.imei = imei;
                            LocalProfileHelp.getInstance().saveProfileFile(MainActivity.this, profileModel);
                        }
                    } else {
                        ProfileModel profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                        if (profileModel != null) {
                            imei = profileModel.SN;
                            if (imei == null) {
                                imei = profileModel.imei;
                            }
                        }
                        int checktime = 0;
                        while (netWorkConnectBroadConnet && imei == null) {
                            try {
                                Thread.sleep(5000);
                            } catch (Exception e) {
                            }
                            profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                            if (profileModel != null) {
                                imei = profileModel.SN;
                                if (imei == null) {
                                    imei = profileModel.imei;
                                }
                            }
                            checktime++;
                            Log.e(TAG, "initAddress run:111 imei =" + imei + ",checktime =" + checktime);
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

                    runOnUiThreadText(imeiNumberText, "imei:" + imei);

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


                    deviceImei = imei;

                    returnImei = deviceInfoModel.returnImei;
                    deveceName = deviceInfoModel.deveceName;


                    boolean mqttConnect = MqttManager.isConnected();
                    Log.e(TAG, "run: mqttConnect =" + mqttConnect);
                    if (!mqttConnect) {
                        MqttManager.getInstance().creatConnect("tcp://120.78.192.66:1883", "devices", "a1237891379", "" + imei, "/camera/v1/device/" + returnImei + "/android");
                        MqttManager.getInstance().subscribe("/camera/v2/device/" + returnImei + "/android/send", 1);
                    }


                    if (getShowFormatResultFlag()) {//0: 格式化U盘失败，1：格式化U盘成功 2：格式化相机成功 3：格式化相机失败
                        saveShowFormatResultFlag(false);
                        int type = getFormatResultFlag();
                        Log.e(TAG, "getFormatResultFlag: type =" + type);
                        if (type == 0) {
                            sendMessageToMqtt("格式化U盘成功;");
                        } else if (type == 1) {
                            sendMessageToMqtt("格式化U盘失败;");
                        } else if (type == 2) {
                            sendMessageToMqtt("格式化相机成功;");
                        } else if (type == 3) {
                            sendMessageToMqtt("格式化相机失败;");
                        }
                    }


                    VariableInstance.getInstance().ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(serverUrlModel.serverUri, MainActivity.this, true);
                    VariableInstance.getInstance().ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(deviceInfoModel.username, deviceInfoModel.password));

                    VariableInstance.getInstance().remoteServerAvailable = remoteOperationUtils.initRemoteDir(deviceInfoModel.deveceName);

                    Log.d(TAG, "initAddress:   配置远程服务器是否成功 =" + VariableInstance.getInstance().remoteServerAvailable);
                    sendBroadcastToServer("配置远程服务器是否成功：" + VariableInstance.getInstance().remoteServerAvailable);
                    updateServerStateUI(VariableInstance.getInstance().remoteServerAvailable);
                    if (VariableInstance.getInstance().remoteServerAvailable) {
                        mHandler.removeMessages(msg_connect_server_timeout);
                        mHandler.sendEmptyMessage(msg_connect_server_complete);
                        removeUploadLogcatMessage(10);
                        remoteOperationUtils.startCameraPictureUploadThread();

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
        stopService(new Intent(MainActivity.this, MyServer.class));
        if (receiverCamera != null) {
            unregisterReceiver(receiverCamera);
        }
        if (receiverStoreUSB != null) {
            unregisterReceiver(receiverStoreUSB);
        }
        if (netWorkReceiver != null) {
            unregisterReceiver(netWorkReceiver);
        }

        if (communicationReceiver != null) {
            unregisterReceiver(communicationReceiver);
        }

        mHandler.removeCallbacksAndMessages(null);

        openCameraDeviceProt(false, 9);
        if (remoteOperationUtils != null) remoteOperationUtils.stopUploadThread();

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_NONE);
        EventBus.getDefault().unregister(this);
        MqttManager.getInstance().release();


        LogcatHelper.getInstance().stopMainLogcat();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                LogcatHelper.getInstance().stopMainLogcat();
            }
        }, 300);
        stopService(new Intent(MainActivity.this, MyServer.class));
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
            case Set_FormatUdisk_2weeks:
                prepareFormatUSB();
                break;
            case FormatCamera:
            case Set_FormatCamera_All:
            case Set_FormatCamera_2weeks:
                prepareFormatCamera();
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


    private void formatCameraDevice() {
        Log.d(TAG, "formatCameraDevice: ");
        saveShowFormatResultFlag(true);
        mHandler.sendEmptyMessageDelayed(msg_formatusb_timeout, 120000);

        ReceiverCamera.cameraFormat(new FormatLisener() {
            @Override
            public void formatResult(boolean formatSucced) {
                saveFormatResultFlag(formatSucced ? 2 : 3);
                Log.d(TAG, " formatCameraDevice 格式化相机完成 formatSucced:" + formatSucced);
                mHandler.removeMessages(msg_formatusb_timeout);
                mHandler.sendEmptyMessageDelayed(msg_formatusb_timeout, 30000);
            }

            @Override
            public void resetTimeOutTime() {
                mHandler.sendEmptyMessageDelayed(msg_formatusb_timeout, 1000 * 60 * 5);
            }
        });
    }

    private void formatStoregeUSB() {
        Log.d(TAG, "formatStoregeUSB: start ......................");
        saveShowFormatResultFlag(true);
        mHandler.sendEmptyMessageDelayed(msg_formatusb_timeout, 120000);
        ReceiverStoreUSB.formatStoreUSBCaeraDevice(new FormatLisener() {
            @Override
            public void formatResult(boolean formatSucced) {
                saveFormatResultFlag(formatSucced ? 0 : 1);
                Log.d(TAG, "formatStoregeUSB 格式化完成U盘 formatSucced:" + formatSucced);
                mHandler.removeMessages(msg_formatusb_timeout);
                mHandler.sendEmptyMessageDelayed(msg_formatusb_timeout, 30000);
            }

            @Override
            public void resetTimeOutTime() {

            }
        });
    }

    private void prepareFormatUSB() {
        saveFormatFlag(1);
        sendOrderedBroadcast(new Intent(FormatFlagBroadcast), null);

        Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.resetDir(VariableInstance.getInstance().TFCardUploadPictureDir);
        Utils.resetDir(VariableInstance.getInstance().LogcatDir);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isUpdating) {
                    return;
                }
                restartDevice();
            }
        }, 2000);
    }


    private void prepareFormatCamera() {
        saveFormatFlag(2);
        sendOrderedBroadcast(new Intent(FormatFlagBroadcast), null);
        Utils.resetDir(VariableInstance.getInstance().TFCardUploadPictureDir);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isUpdating) {
                    return;
                }
                restartDevice();
            }
        }, 2000);
    }

    private void restartDevice() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
            proc.waitFor();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void getInfo() {
        sendMessageToMqtt(serverGetInfo());
    }


    private void sendMessageToMqtt(String message) {
        if (MqttManager.isConnected()) {
            if (returnImei != null) {
                MqttManager.getInstance().publish("/camera/v2/device/" + returnImei + "/android/receive", 1, message);
            }
        } else {
            if (netWorkConnectBroadConnet && returnImei != null) {
                Message message1 = new Message();
                message1.what = msg_resend_mqtt;
                message1.obj = message;
                mHandler.sendMessageDelayed(message1, 1000);
                android.util.Log.d(TAG, "publishMessage: mqtt 未连接 重发 message =" + message);
            }
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

        boolean initUSB;
        if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
            initUSB = false;
        } else {
            initUSB = true;
        }

        if (copySpeed == null) copySpeed = "0";
        String info = "4gCcid," + getPhoneNumber() + ";UploadSpeed," + UploadSpeed + ";4gCsq," + getSignalStrength() + ";SdFree," + freeSpace + ";SdFull," + capacity + ";PhotoSum," + VariableInstance.getInstance().currentUSBPictureCount + ";PhotoUploadThisTime," + VariableInstance.getInstance().uploadRemorePictureNum + ";UploadMode," + uploadModelString + ";UploadUseTime," + UploadUseTime + ";Version," + appVerison + ";initUSB," + initUSB + ";connectCamera," + VariableInstance.getInstance().isConnectCamera + ";cameraPictureCount," + cameraPictureCount + ";cameraName," + cameraName + ";waitUploadPhoto," + (remoteOperationUtils == null ? 0 : remoteOperationUtils.pictureFileListCache.size()) + ";copySpeed," + copySpeed + ";copyTotalNum," + copyTotalNum + ";copyCompleteNum," + VariableInstance.getInstance().backupPicrureNum + ";";

        Log.d(TAG, "serverGetInfo: info =" + info);
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
//            imei = "202302050000001";//TODO hu
        } catch (Exception | Error e) {
            Log.e(TAG, "getPhoneImei: Exception =" + e);
        }
        if (debug) imei = "202302050000001";

        runOnUiThreadText(imeiNumberText, "imei:" + imei);
        return imei;
    }


    private void openCameraDeviceProt(boolean open, int positon) {
        Log.e(TAG, "openCameraDeviceProt: 连接相机通信端口 led: " + (open ? "打开" : "关闭") + ",positon =" + positon + ", 当前状态" + (openDeviceProtFlag ? "打开" : "关闭") + "-------------------------------------------");
        if (openDeviceProtFlag && open) return;

        openDeviceProtFlag = open;
        runOnUiThreadText(cameraStateText, "相机状态:" + openDeviceProtFlag);
        sendBroadcastToServer("openCameraDeviceProt：" + positon);
        if (debug) return;
        if (open) {
            if (receiverCamera == null) {
                registerReceiverCamera();
            }

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


    private void setLEDState(int state) {
        Log.e(TAG, "setLEDState: state =" + state);
        if (debug) return;

        switch (state) {
            case 1://快闪
            {
                LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_HEARTBEAT);
            }
            break;
            case 2://慢闪
            {
                LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_TIMER);
            }
            break;
            case 3://常亮
            {
                LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_NONE);
                LedControl.nativeEnableLed(LedControl.LED_RED_BRIGHTNESS_PATH, LedControl.LED_OFF);
            }
            break;
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.rescanerBt) {
            openCameraDeviceProt(false, 1);
            openCameraDeviceProt(true, 2);
        } else if (view.getId() == R.id.guanjiBt) {
            Utils.closeAndroid();
        } else if (view.getId() == R.id.openProtActivityBt) {
            startActivity(new Intent(MainActivity.this, GpioActivity.class));
        } else if (view.getId() == R.id.clearViewBt) {
            messageTextString = "";
            messageText.setText(messageTextString);
        } else if (view.getId() == R.id.formatUSBt) {
            prepareFormatUSB();
        } else if (view.getId() == R.id.formatCameraBt) {
            prepareFormatCamera();
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
                        runOnUiThreadText(imeiNumberText, "imei:" + SN);
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


    private class CommunicationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case Exit_UploadAPP_Action:
                    if (MqttManager.isConnected()) {
                        MqttManager.getInstance().publish("/camera/v2/device/" + deviceImei + "AAA/android/receive", 1, "收到退出上传APP命令");
                    }
                    try {
                        stopService(new Intent(MainActivity.this, MyServer.class));
                    } catch (Exception e) {

                    }
                    finish();
                    break;

            }
        }

    }

    private class NetWorkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null) return;
            String action = intent.getAction();
            if (action == null) return;

            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
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
            } else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    Log.d(TAG, "NetWorkReceiver: 网络断开广播");
                    mHandler.removeMessages(msg_network_connect);
                    mHandler.sendEmptyMessage(msg_network_disconnect);
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    Log.d(TAG, "NetWorkReceiver: 网络连接广播");
                    mHandler.removeMessages(msg_network_connect);
                    mHandler.sendEmptyMessageDelayed(msg_network_connect, 2000);
                }
            }
        }
    }

    private void sendBroadcastToServer(String fucntion) {
        if (true)//TODO hu
            return;
        Intent intent = new Intent("sendBroadcastToServer");
        intent.putExtra("fucntion", fucntion);
        sendOrderedBroadcast(intent, null);
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
    private static final int msg_usb_request_permission_timeout = 13;
    private static final int msg_formatusb_timeout = 14;

    private static final int msg_resend_mqtt = 15;


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
                        activity.removeUploadLogcatMessage(11);
                        activity.sendUploadLogcatMessage(8);
                    }
                    break;
                case msg_send_ShutDown:
                    activity.sendShutDown = false;
                    activity.sendBroadcastToServer("closeAndroid");
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
                    activity.removeUploadLogcatMessage(12);
                    activity.sendUploadLogcatMessage(9);
                    activity.openCameraDeviceProt(true, 3);
                    break;
                case msg_connect_server_timeout:
                    activity.openCameraDeviceProt(true, 4);
                    break;
                case msg_connect_server_complete:
                    activity.openCameraDeviceProt(true, 5);
                    break;
                case msg_usb_request_permission_timeout:
                    if (activity.receiverStoreUSB != null) {
                        activity.receiverStoreUSB.initStoreUSBDevicea();
                    }
                    break;
                case msg_formatusb_timeout:
                    activity.restartDevice();
                    break;
                case msg_resend_mqtt:
                    if (msg.obj != null) {
                        activity.sendMessageToMqtt((String) msg.obj);
                    }
                    break;
            }
        }
    }

    private void saveFormatFlag(int type) {//0: 不需要格式化，1：格式化U盘 2：格式化相机
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putInt("formatflag", type);
        editor.apply();
    }

    private int getFormatFlag() {//0: 不需要格式化，1：格式化U盘 2：格式化相机
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        return sharedPreferences.getInt("formatflag", 0);
    }


    private void saveFormatResultFlag(int format) {//0: 格式化U盘失败，1：格式化U盘成功 2：格式化相机失败 3：格式化相机失败
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putInt("formatResult", format);
        editor.apply();
    }

    private int getFormatResultFlag() {//0: 格式化U盘成功 1：格式化U盘失败  2：格式化相机成功 3：格式化相机失败
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        return sharedPreferences.getInt("formatResult", 0);
    }


    private void saveShowFormatResultFlag(boolean format) {
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putBoolean("showFormatResult", format);
        editor.apply();
    }

    private boolean getShowFormatResultFlag() {
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        return sharedPreferences.getBoolean("showFormatResult", false);
    }

}
