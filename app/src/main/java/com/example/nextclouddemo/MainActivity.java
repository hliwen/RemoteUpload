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

import com.blankj.utilcode.util.AppUtils;
import com.example.gpiotest.GpioActivity;
import com.example.gpiotest.LedControl;
import com.example.nextclouddemo.model.DeviceInfoModel;
import com.example.nextclouddemo.model.DeviceMemory;
import com.example.nextclouddemo.model.MyMessage;

import com.example.nextclouddemo.model.ProfileModel;
import com.example.nextclouddemo.mqtt.MqttManager;
import com.example.nextclouddemo.operation.LogcatHelper;
import com.example.nextclouddemo.operation.ScannerCamera;
import com.example.nextclouddemo.operation.ScannerStoreUSB;
import com.example.nextclouddemo.utils.DeviceUtils;
import com.example.nextclouddemo.operation.FormatLisener;
import com.example.nextclouddemo.operation.LocalProfileHelp;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.operation.NetworkOperation;
import com.example.nextclouddemo.operation.UpdateUtils;
import com.example.nextclouddemo.utils.Utils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity implements View.OnClickListener {
    public static boolean debug = false;
    public boolean remoteDebug = false;
    private static final String TAG = "remotelog_MainActivityl";
    private static final String CheckAppStateAction = "CheckAppStateAction";
    private static final String ResponseAppStateAction = "ResponseAppStateAction";
    private static final String Exit_UploadAPP_Action = "Exit_UploadAPP_Action";
    private static final String Enter_UploadAPP_Debug_Model = "Enter_UploadAPP_Debug_Model";
    private static final String Exit_UploadAPP_Debug_Model = "Exit_UploadAPP_Debug_Model";
    private static final String FormatUSB = "Start,Format;";
    private static final String FormatCamera = "Start,FormatCamera;";

    private static final String UploadMode1 = "Set,UploadMode,1;";
    private static final String UploadMode2 = "Set,UploadMode,2;";
    private static final String UploadMode3 = "Set,UploadMode,3,";
    private static final String UploadMode4 = "Set,UploadMode,4,";
    private static final String UploadMode5 = "Set,UploadMode,5;";
    private static final String GetInfo = "Get,Info;";

    private static final String UploadEndUploadUseTime = "Upload,End,UploadUseTime,";
    private static final String AppShutdownAck = "App,shutdown,ack;";
    private static final String UploadToday = "Set,UploadToday,";
    private static final String Set_Powoff = "Set,Powoff;";
    private static final String Set_UploadLocat = "Set,UploadLocat;";
    private static final String Set_WakeCamera = "Set,WakeCamera;";
    private static final String Set_ResetApk = "Set,ResetApk;";

    private static final String Set_UpdateBetaApk = "Set,UpdateBetaApk;";
    public static final String Update_StartDownloadAPK = "Update,StartDownloadAPK;";
    public static final String Update_DownloadAPKSucceed = "Update,DownloadAPKSucceed;";
    public static final String Update_DownloadAPKFaild = "Update,DownloadAPKFaild;";
    public static final String Update_InstallAPKStart = "Update,InstallAPKStart;";
    public static final String Update_InstallAPKSucceed = "Update,InstallAPKSucceed;";
    public static final String Update_InstallAPKFaild = "Update,InstallAPKFaild;";
    private static final String FormatFlagBroadcast = "FormatFlagBroadcast";
    private static final int NETWORK_WAIT_TIME = 3 * 60 * 1000;
    private static final int CHECK_WORKING_TIME = 20000;
    private static final int FORMAT_TIME = 3 * 60 * 1000;
    private static final int OPEN_PORT_TIME = 10000;

    private static final String 快闪 = "快闪"; //没网络
    private static final String 慢闪 = "慢闪"; //有网络
    private static final String 常亮 = "常亮"; //回传完成

    private static final int msg_init_network_timeout = 1;
    private static final int msg_open_camera_port_timeout = 2;
    private static final int msg_formatusb_timeout = 3;
    private static final int msg_resend_mqtt = 4;
    private static final int msg_check_working_timeout = 5;
    private boolean isUpdating;
    private int signalStrengthValue;
    private int appVerison;
    private String cameraName;
    private String copySpeed;
    private String UploadSpeed;

    private View wholeView;
    private TextView 卡号View;
    private TextView imeiView;
    private TextView 当前版本View;
    private TextView 最新版本View;
    private TextView 相机状态View;
    private TextView U盘状态View;
    private TextView 联网状态View;
    private TextView 服务器状态View;
    private TextView mqtt状态View;
    private TextView 云端名称View;
    private TextView 上传模式View;
    private TextView U盘空间View;
    private TextView U盘照片总数View;
    private TextView 相机照片总数View;
    private TextView 相机名称View;
    private TextView 本次从相机同步到服务器数量View;
    private TextView 本次从相机同步到U盘数量View;
    private TextView 本次同步到服务器耗时View;
    private TextView 已上传张数View;
    private TextView 已下载张数View;
    private TextView 升级结果View;
    private TextView 下载升级包进度View;

    private TextView messageText;

    private Button guanjiBt;
    private Button rescanerBt;
    private Button openProtActivityBt;
    private Button clearViewBt;
    private Button formatUSBt;
    private Button formatCameraBt;
    private Button updateBeta;


    private MyHandler mHandler;
    private NetworkOperation networkOperation;

    private NetWorkStateReceiver netWorkStateReceiver;
    private ScannerCamera scannerCamera;
    private ScannerStoreUSB scannerStoreUSB;

    private ServerApkCommunicationReceiver serverApkCommunicationReceiver;

    private static final String apkServerPackageName = "com.remoteupload.apkserver";

    private String messageTextString;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main_acitivity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            debug = true;
        }

        String[] value = Utils.haveNoPermissions(MainActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }
        appVerison = AppUtils.getAppInfo(getPackageName()).getVersionCode();
        Log.d(TAG, "initView: app当前版本 =" + appVerison);

        bindView();

        openCameraDeviceProt(false, 1);
        openNetworkLed(true);

        mHandler = new MyHandler(MainActivity.this);
        EventBus.getDefault().register(this);
        startService(new Intent(MainActivity.this, MyServer.class));

        registerSerVerAPKCommunicationReceiver();
        sendOrderedBroadcast(new Intent(ResponseAppStateAction), null);

        if (DeviceUtils.checkFormatFlag(MainActivity.this, new FormatLisener() {
            @Override
            public void formatStart() {
                mHandler.removeMessages(msg_formatusb_timeout);
                mHandler.sendEmptyMessageDelayed(msg_formatusb_timeout, FORMAT_TIME);
            }

            @Override
            public void formatEnd(boolean formatSucced) {
                DeviceUtils.saveFormatResultFlag(formatSucced ? 0 : 1, MainActivity.this);
                Log.d(TAG, "formatStoregeUSB 格式化完成U盘 formatSucced:" + formatSucced);
                mHandler.removeMessages(msg_formatusb_timeout);
                mHandler.sendEmptyMessageDelayed(msg_formatusb_timeout, 30000);
            }
        })) {
            return;
        }

        mHandler.removeMessages(msg_check_working_timeout);
        mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);

        setLEDState(快闪);

        int assetsServerAPKVersionCode = Utils.getAssetsServerApkFileVersionCode(MainActivity.this);
        boolean installServerAPK = Utils.isAppInstalled(MainActivity.this, apkServerPackageName);
        int installServerAPKVersionCode = 0;
        if (installServerAPK) {
            installServerAPKVersionCode = Utils.getServerVersionCode(MainActivity.this, apkServerPackageName);
        }
        Log.e(TAG, "onCreate: 内嵌守护app版本号:" + assetsServerAPKVersionCode + ",是否安装守护app:" + installServerAPK + ",installServerAPKVersionCode =" + installServerAPKVersionCode);
        if (!debug && (!installServerAPK || installServerAPKVersionCode < assetsServerAPKVersionCode)) {
            Utils.installAPKServer(MainActivity.this);
        }

        initData();
    }

    private void bindView() {
        wholeView = findViewById(R.id.wholeView);
        卡号View = findViewById(R.id.卡号View);
        imeiView = findViewById(R.id.imeiView);
        当前版本View = findViewById(R.id.当前版本View);
        最新版本View = findViewById(R.id.最新版本View);
        相机状态View = findViewById(R.id.相机状态View);
        U盘状态View = findViewById(R.id.U盘状态View);
        联网状态View = findViewById(R.id.联网状态View);
        服务器状态View = findViewById(R.id.服务器状态View);
        mqtt状态View = findViewById(R.id.mqtt状态View);
        云端名称View = findViewById(R.id.云端名称View);
        上传模式View = findViewById(R.id.上传模式View);
        U盘空间View = findViewById(R.id.U盘空间View);
        U盘照片总数View = findViewById(R.id.U盘照片总数View);
        相机照片总数View = findViewById(R.id.相机照片总数View);
        相机名称View = findViewById(R.id.相机名称View);
        本次从相机同步到服务器数量View = findViewById(R.id.本次从相机同步到服务器数量View);
        本次从相机同步到U盘数量View = findViewById(R.id.本次从相机同步到U盘数量View);
        本次同步到服务器耗时View = findViewById(R.id.本次同步到服务器耗时View);
        已上传张数View = findViewById(R.id.已上传张数View);
        已下载张数View = findViewById(R.id.已下载张数View);

        升级结果View = findViewById(R.id.升级结果View);
        下载升级包进度View = findViewById(R.id.下载升级包进度View);


        messageText = findViewById(R.id.messageText);

        guanjiBt = findViewById(R.id.guanjiBt);
        rescanerBt = findViewById(R.id.rescanerBt);
        openProtActivityBt = findViewById(R.id.openProtActivityBt);
        clearViewBt = findViewById(R.id.clearViewBt);
        formatUSBt = findViewById(R.id.formatUSBt);
        formatCameraBt = findViewById(R.id.formatCameraBt);
        updateBeta = findViewById(R.id.updateBeta);

        if (debug) {
            wholeView.setVisibility(View.INVISIBLE);
        }

        guanjiBt.setOnClickListener(this);
        rescanerBt.setOnClickListener(this);
        openProtActivityBt.setOnClickListener(this);
        clearViewBt.setOnClickListener(this);
        formatUSBt.setOnClickListener(this);
        formatCameraBt.setOnClickListener(this);
        updateBeta.setOnClickListener(this);
    }

    @SuppressLint("SetTextI18n")
    void initData() {
        getUploadToday();
        getUploadModel();
        Utils.makeDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.makeDir(VariableInstance.getInstance().TFCardUploadPictureDir);
        networkOperation = new NetworkOperation(remoteOperationListener);
        registerNetworkStateReceiver();
        registerReceiverCamera();
        registerStoreUSBReceiver();
        当前版本View.setText("当前版本：" + appVerison);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiverCamera() {
        scannerCamera = new ScannerCamera(getApplicationContext(), scannerCameraListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(scannerCamera, intentFilter);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStoreUSBReceiver() {
        scannerStoreUSB = new ScannerStoreUSB(getApplicationContext(), scannerStrogeUSBListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(scannerStoreUSB, intentFilter);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerNetworkStateReceiver() {
        mHandler.sendEmptyMessageDelayed(msg_init_network_timeout, NETWORK_WAIT_TIME);
        netWorkStateReceiver = new NetWorkStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(netWorkStateReceiver, filter);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerSerVerAPKCommunicationReceiver() {
        serverApkCommunicationReceiver = new ServerApkCommunicationReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CheckAppStateAction);
        filter.addAction(Exit_UploadAPP_Action);
        filter.addAction(Enter_UploadAPP_Debug_Model);
        filter.addAction(Exit_UploadAPP_Debug_Model);
        registerReceiver(serverApkCommunicationReceiver, filter);
    }

    private class ServerApkCommunicationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;
            String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {
                case Exit_UploadAPP_Action:
                    try {
                        stopService(new Intent(MainActivity.this, MyServer.class));
                    } catch (Exception e) {
                    }
                    finish();
                    break;
                case Enter_UploadAPP_Debug_Model:
                    remoteDebug = true;
                    break;
                case Exit_UploadAPP_Debug_Model:
                    remoteDebug = false;
                    break;
                case CheckAppStateAction:
                    sendOrderedBroadcast(new Intent(ResponseAppStateAction), null);
                    break;
            }
        }

    }

    private class NetWorkStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null)
                return;
            String action = intent.getAction();
            if (action == null)
                return;
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {//TODO
                    Log.d(TAG, "NetWorkReceiver: 网络断开广播");
                    联网状态View.setText("网络状态：断开");
                    openNetworkLed(false);
                    openNetworkLed(true);
                    networkOperation.networkDisconnect(false);
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    联网状态View.setText("网络状态：网络连接（不一定能上网）");
                    Log.d(TAG, "NetWorkReceiver: 网络连接广播");
                    networkOperation.networkConnect(MainActivity.this);
                }
            }
        }
    }

    PhoneStateListener MyPhoneListener = new PhoneStateListener() {
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            int asu = signalStrength.getGsmSignalStrength();
            signalStrengthValue = -113 + 2 * asu;
        }
    };


    private final ScannerStoreUSB.DeviceScannerListener scannerStrogeUSBListener = new ScannerStoreUSB.DeviceScannerListener() {
        @Override
        public void checkServerUSBOperation() {
            sendOrderedBroadcast(new Intent("checkServerUSBOpenration"), null);
        }

        @Override
        public void buckupOnePictureComplete(String speed) {
            copySpeed = speed;
            runOnUiThreadText(已下载张数View, "已同步到U盘张数:" + scannerStoreUSB.backupUSBCompletePictureNum + "\n" + "同步到USB速度:" + speed);
        }

        @Override
        public void startScannerDevice() {
            Log.e(TAG, "startScannerDevice: 开始查找U盘");
            runOnUiThreadText(U盘状态View, "U盘状态:" + "正在扫描U盘");

            if (remoteDebug) {
                sendMessageToMqtt("开始扫描U盘");
            }
        }

        @Override
        public void endScannerDevice(boolean isSuccess, ProfileModel profileModel) {
            runOnUiThreadText(U盘状态View, "U盘状态:" + (isSuccess ? "U盘正常" : "U盘异常:" + scannerStoreUSB.errorMessage));

            if (remoteDebug) {
                sendMessageToMqtt((isSuccess ? "U盘正常" : "U盘异常:" + scannerStoreUSB.errorMessage));
            }

            if (isSuccess) {
                Log.e(TAG, "endScannerDevice: 扫描备份U盘成功");
                DeviceMemory deviceMemory = scannerStoreUSB.getDeviceMemory();
                int capacity = deviceMemory.capacity;
                int freeSpace = deviceMemory.freeSpace;
                int pictureCount = scannerStoreUSB.deviceTotalPicture;
                runOnUiThreadText(U盘空间View, "U盘空间:" + "\ncapacity:" + capacity + "\nfreeSpace:" + freeSpace);
                runOnUiThreadText(U盘照片总数View, "U盘图片总数:" + pictureCount);

                if (remoteDebug) {
                    sendMessageToMqtt("U盘图片总数:" + pictureCount + ",U盘空间:" + "capacity:" + capacity + ",freeSpace:" + freeSpace);
                }

                if (!networkOperation.remoteServerAvailable) {
                    if (profileModel != null) {
                        Log.e(TAG, "U盘endScannerDevice: 服务器不可用 尝试是否需要连接WiFi");
                        LocalProfileHelp.getInstance().saveProfileFile(MainActivity.this, profileModel);
                        checkProfileFileToConnectNetwork(profileModel);
                    }
                }
            } else {
                Log.e(TAG, "U盘endScannerDevice: 备份U盘初始化失败：" + scannerStoreUSB.errorMessage);
            }


            if (networkOperation.remoteServerAvailable || isSuccess) {
                Log.e(TAG, "U盘endScannerDevice: 远程服务可用或者U盘可用，需要打开相机");
                mHandler.removeMessages(msg_open_camera_port_timeout);
                mHandler.sendEmptyMessageDelayed(msg_open_camera_port_timeout, OPEN_PORT_TIME);
                openCameraDeviceProt(true, 2);
            } else {
                Log.e(TAG, "U盘endScannerDevice: 远程服务不可用，并且U盘不可用，不需要打开相机");
            }

        }
    };

    private final ScannerCamera.DeviceScannerListener scannerCameraListener = new ScannerCamera.DeviceScannerListener() {

        @Override
        public boolean uploadToUSB(String localFilePath) {
            if (scannerStoreUSB == null) {
                return false;
            }
            boolean uploadResult = scannerStoreUSB.uploadToUSB(localFilePath);
            return uploadResult;
        }


        @Override
        public void startScannerDevice() {
            mHandler.removeMessages(msg_open_camera_port_timeout);
            Log.d(TAG, "相机startScannerDevice: start ........................................");
            runOnUiThreadText(相机状态View, "相机状态:" + "正在扫描相机");

            if (remoteDebug) {
                sendMessageToMqtt("开始扫描相机");
            }
        }

        @Override
        public void endScannerDevice(boolean isSuccess) {
            runOnUiThreadText(相机状态View, isSuccess ? "相机状态: 相机操作成功" : "相机操作失败 :" + scannerCamera.errorMessage);

            if (remoteDebug) {
                sendMessageToMqtt(isSuccess ? "相机状态: 相机操作成功" : "相机操作失败 :" + scannerCamera.errorMessage);
            }

            if (isSuccess) {
                Log.e(TAG, "endScannerDevice: 相机操作成功");
            } else {
                Log.e(TAG, "endScannerDevice: 相机操作失败 errorMessage =" + scannerCamera.errorMessage);
            }
            if (scannerCamera.deviceTotalPicture == 0) {
                sendMessageToMqtt(Set_WakeCamera);
            }
        }

        @Override
        public void addUploadRemoteFile(String uploadFileModel) {
            networkOperation.addUploadRemoteFile(uploadFileModel, false);
        }


        @Override
        public void scannerCameraComplete(int needDownloadCount, int needUpload, int cameraTotalPictureCount, String deviceName) {
            Log.e(TAG, "scannerCameraComplete：需要备份到U盘张数:" + needDownloadCount + ",需要上传张数：" + needUpload + ",相机照片总数：" + cameraTotalPictureCount + ",相机名称：" + deviceName);
            cameraName = deviceName;
            runOnUiThreadText(本次从相机同步到服务器数量View, "本次从相机同步到服务器数量:" + needUpload);
            runOnUiThreadText(本次从相机同步到U盘数量View, "本次从相机同步到U盘数量:" + needDownloadCount);
            runOnUiThreadText(相机照片总数View, "相机照片总数：" + cameraTotalPictureCount);
            runOnUiThreadText(相机名称View, "相机名称：" + deviceName);

            if(remoteDebug){
                sendMessageToMqtt("扫描相机完成：需要备份到U盘张数:" + needDownloadCount + ",需要上传张数：" + needUpload + ",相机照片总数：" + cameraTotalPictureCount + ",相机名称：" + deviceName);
            }
        }


    };

    NetworkOperation.NetwrokOperationListener remoteOperationListener = new NetworkOperation.NetwrokOperationListener() {

        @Override
        public void startUpdateApp() {
            isUpdating = true;
            mHandler.removeMessages(msg_init_network_timeout);
            Log.e(TAG, "startUpdateApp: ");
            runOnUiThreadText(升级结果View, "开始升级");
        }

        @Override
        public void downloadProgress(int progress) {
            runOnUiThreadText(下载升级包进度View, "app下载：" + progress + "kb");
        }

        @Override
        public void endUpdateApp(boolean succeed) {
            isUpdating = false;
            runOnUiThreadText(升级结果View, "升级" + (succeed ? "成功" : "失败"));
            if (succeed) {
                DeviceUtils.restartDevice();
            } else {
                mHandler.removeMessages(msg_init_network_timeout);
                mHandler.sendEmptyMessageDelayed(msg_init_network_timeout, NETWORK_WAIT_TIME);
            }
        }

        @Override
        public void networkInitStart() {
            Log.e(TAG, "networkInitStart: ...............................");
            mHandler.removeMessages(msg_init_network_timeout);
            mHandler.sendEmptyMessageDelayed(msg_init_network_timeout, NETWORK_WAIT_TIME);
            setLEDState(慢闪);
            scannerCamera.detachedDevice();
            scannerStoreUSB.detachedDevice(false);
        }

        @Override
        public void networkInitEnd(boolean succeed, String message) {
            Log.e(TAG, "networkInitEnd: succeed =" + succeed + ",message =" + message);

            if (remoteDebug) {
                sendMessageToMqtt("网络初始化完成：" + (succeed ? "正常" : "异常：" + message));
            }
            if (succeed) {
                setLEDState(慢闪);
            } else {
                setLEDState(快闪);
            }
            runOnUiThreadText(服务器状态View, "服务器状态：" + (succeed ? "正常" : "异常:" + message));
            mHandler.removeMessages(msg_init_network_timeout);

            scannerCamera.detachedDevice();
            scannerStoreUSB.startScannerDevice();
        }

        @Override
        public void showWorkingImei(String imei) {
            runOnUiThreadText(imeiView, "imei:" + imei);
        }

        @Override
        public void versionCodeResponse(int currentVersionCode, int serverVersionCode) {
            runOnUiThreadText(最新版本View, "最新版本：" + serverVersionCode);
            runOnUiThreadText(当前版本View, "当前版本：" + serverVersionCode);
        }

        @Override
        public void remoteDeviceInfoRespond(String deviceImei, DeviceInfoModel deviceInfoModel) {
            runOnUiThreadText(云端名称View, "云端名称:" + deviceInfoModel.deviceName);

            networkUpdateUploadModel(deviceInfoModel);
            boolean mqttConnect = MqttManager.isConnected();
            Log.d(TAG, "开始连接mqtt: returnImei：" + deviceInfoModel.returnImei + " ,deviceName =" + deviceInfoModel.deviceName);
            if (!mqttConnect) {
                MqttManager.getInstance().creatConnect("tcp://120.78.192.66:1883", "devices", "a1237891379", "" + deviceImei, "/camera/v1/device/" + deviceInfoModel.returnImei + "/android");
                MqttManager.getInstance().subscribe("/camera/v2/device/" + deviceInfoModel.returnImei + "/android/send", 1);
            }

            if (DeviceUtils.getShowFormatResultFlag(MainActivity.this)) {//0: 格式化U盘失败，1
                // ：格式化U盘成功
                // 2：格式化相机成功 3：格式化相机失败
                DeviceUtils.saveShowFormatResultFlag(false, MainActivity.this);
                int type = DeviceUtils.getFormatResultFlag(MainActivity.this);
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

            if (remoteDebug) {
                sendMessageToMqtt("imei:" + deviceImei + ",returnImei:" + deviceInfoModel.returnImei+",deviceName:"+deviceInfoModel.deviceName);
            }

            sendMessageToMqtt(serverGetInfo());

        }

        @Override
        public boolean canStopUploadPictureThread() {
            if (isUpdating) {
                Log.d(TAG, "canStopUploadPictureThread: isUpdating");
                return true;
            }
            if (scannerCamera.isOperatingDevice) {
                Log.d(TAG, "canStopUploadPictureThread: scannerCamera.isOperatingDevice");
                return false;
            }
            if (scannerStoreUSB.isOperatingDevice) {
                Log.d(TAG, "canStopUploadPictureThread: scannerStoreUSB.isOperatingDevice");
                return false;
            }

            return true;
        }

        @Override
        public void allPictureUploadEnd() {
            int useTime = (int) (networkOperation.uploadTatalTime / 1000);
            Log.e(TAG, "allPictureUploadEnd: 所有图片上传完成 ，用时" + useTime + "秒");
            runOnUiThreadText(本次同步到服务器耗时View, "本次同步到服务器耗时:" + useTime + "s");
            sendMessageToMqtt(UploadEndUploadUseTime + useTime + ";");
            sendMessageToMqtt(serverGetInfo());

            if (networkOperation.remoteServerAvailable) {
                setLEDState(常亮);
            }
            if(remoteDebug){
                sendMessageToMqtt("allPictureUploadEnd: 所有图片上传完成 ，用时" + useTime + "秒");
            }
        }

        @Override
        public void pictureUploadStart() {


        }

        @Override
        public void pictureUploadEnd() {

        }


        @Override
        public void updateUploadSpeed(String speed) {
            Log.d(TAG, "updateUploadSpeed: speed =" + speed);
            UploadSpeed = speed;
            runOnUiThreadText(已上传张数View, "已上传张数:" + networkOperation.uploadRemoteCompletePictureNum + "\n" + "上传服务器速度：" + speed);
            sendMessageToMqtt(serverGetInfo());
        }
    };


    private void checkProfileFileToConnectNetwork(ProfileModel profileModel) {
        Log.e(TAG, "checkProfileFileToConnectNetwork start ......");
        int deviceStyle = LocalProfileHelp.getInstance().checkDeviceStyle();
        if (deviceStyle == 1 || deviceStyle == 0) {
            //蜂窝版
            if (deviceStyle == 1 && profileModel != null) {
                Log.e(TAG, "checkProfileFileToConnectNetwork: 蜂窝版里面怎么放了个配置文件");
            }
            if (deviceStyle == 0 && profileModel != null) {
                Log.e(TAG, "checkProfileFileToConnectNetwork: 未知原因，有配置文件，模块接口没识别到");
            }
            String imei = DeviceUtils.getPhoneImei(MainActivity.this);
            Log.d(TAG, "checkProfileFileToConnectNetwork: getPhoneImei imei =" + imei);
            if (imei == null) {
                ProfileModel parseMode = profileModel;
                if (parseMode == null) {
                    Log.e(TAG, "checkProfileFileToConnectNetwork: 读取不到imei,配置文件也无法解析");
                    profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                    if (profileModel == null) {
                        Log.e(TAG, "checkProfileFileToConnectNetwork: 读取不到imei,配置文件也无法解析,本地也没有保存 ");
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
            ProfileModel parseMode = profileModel;
            if (parseMode == null) {
                Log.e(TAG, "checkProfileFileToConnectNetwork: 配置文件也无法解析");
                profileModel = LocalProfileHelp.getInstance().getProfileFile(MainActivity.this);
                if (profileModel == null) {
                    Log.e(TAG, "checkProfileFileToConnectNetwork: 配置文件也无法解析,本地也没有保存 ");
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
            Log.e(TAG, "checkProfileFileToConnectNetwork: iemi无法读取,配置文件也无法解析,本地也没有保存,不执行联网 mqtt 无法通信");
            return;
        }

        if (profileModel.wifi != null) {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                boolean wifiEnable = wifiManager.isWifiEnabled();
                if (wifiEnable) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().contains(profileModel.wifi)) {
                        Log.e(TAG, "checkProfileFileToConnectNetwork: 当前自动链接上WiFi " + wifiInfo.getSSID());
                        return;
                    }
                    if (profileModel.pass == null) {
                        DeviceUtils.connectWifiNoPws(profileModel.wifi, wifiManager);
                    } else {
                        if (profileModel.pass.length() == 0) {
                            DeviceUtils.connectWifiNoPws(profileModel.wifi, wifiManager);
                        } else {
                            DeviceUtils.connectWifiPws(profileModel.wifi, profileModel.pass, wifiManager);
                        }
                    }
                } else {
                    wifiManager.setWifiEnabled(true);
                }
            }
        }
        Log.e(TAG, "checkProfileFileToConnectNetwork: end");
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
        openCameraDeviceProt(false, 3);


        unregisterReceiver(scannerCamera);
        unregisterReceiver(scannerStoreUSB);
        unregisterReceiver(netWorkStateReceiver);
        unregisterReceiver(serverApkCommunicationReceiver);
        mHandler.removeCallbacksAndMessages(null);

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
        if (message == null)
            return;
        messageTextString = messageTextString + "\n" + message.message + "\n";
        messageText.setText(messageTextString);
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void receiveMqttMessage(String message) {
        if (message == null)
            return;

        mqtt状态View.setText("mqtt状态:true");
        if (message.contains(UploadMode3)) {
            UploadMode3(message);
            sendMessageToMqtt(serverGetInfo());
            return;
        }

        if (message.contains(UploadMode4)) {
            UploadMode4(message);
            sendMessageToMqtt(serverGetInfo());
            sendMessageToMqtt("ZQ\r\n");
            return;
        }

        if (message.contains(UploadToday)) {
            changeUploadToday(message);
            sendMessageToMqtt("ZQ\r\n");
        }

        switch (message) {
            case FormatUSB:
                prepareFormatUSB();
                break;
            case FormatCamera:
                prepareFormatCamera();
                break;
            case UploadMode5:
                UploadMode5();
                sendMessageToMqtt(serverGetInfo());
                sendMessageToMqtt("ZQ\r\n");
                break;
            case UploadMode1:
                UploadMode1();
                sendMessageToMqtt(serverGetInfo());
                sendMessageToMqtt("ZQ\r\n");
                break;
            case UploadMode2:
                UploadMode2();
                sendMessageToMqtt(serverGetInfo());
                sendMessageToMqtt("ZQ\r\n");
                break;
            case GetInfo:
                sendMessageToMqtt(serverGetInfo());
                sendMessageToMqtt("ZQ\r\n");
                break;
            case AppShutdownAck:
                Utils.closeAndroid();
                break;
            case "mqttConnectionLost":
                mqtt状态View.setText("mqtt状态:false");
                break;
            case "mqttDeliveryComplete":
                mqtt状态View.setText("mqtt状态:true");
                break;
            case Set_Powoff: {
                if (!isUpdating) {
                    Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
                    Utils.closeAndroid();
                }
            }
            break;
            case Set_UploadLocat:
                if (remoteDebug) {
                    sendMessageToMqtt(Set_UploadLocat);
                }
                networkOperation.uploadLogcatFileToRemote(null);
                break;
            case Set_ResetApk: {
                if (!isUpdating) {
                    DeviceUtils.restartDevice();
                }
            }
            break;
            case Set_UpdateBetaApk:
                updateBetaApk();
                break;
        }
    }


    private void updateBetaApk() {
        if (isUpdating) {
            return;
        }

        UpdateUtils.updateBetaApk(MainActivity.this, new UpdateUtils.UpdateListener() {
            @Override
            public void startUpdate() {
                isUpdating = true;
                runOnUiThreadText(升级结果View, "开始升级");
            }

            @Override
            public void startDownload() {
                sendMessageToMqtt(Update_StartDownloadAPK);
            }

            @Override
            public void endDownload(boolean succeed) {
                if (succeed) {
                    sendMessageToMqtt(Update_DownloadAPKSucceed);
                } else {
                    sendMessageToMqtt(Update_DownloadAPKFaild);
                }

            }

            @Override
            public void startInstall() {
                sendMessageToMqtt(Update_InstallAPKStart);
            }

            @Override
            public void endInstall(boolean succeed) {
                if (succeed) {
                    sendMessageToMqtt(Update_InstallAPKSucceed);
                } else {
                    sendMessageToMqtt(Update_InstallAPKFaild);
                }
            }

            @Override
            public void downloadProgress(int progress) {
                runOnUiThreadText(下载升级包进度View, "app下载：" + progress + "kb");
            }

            @Override
            public void endUpdate(boolean succeed, String message) {
                isUpdating = false;
                runOnUiThreadText(升级结果View, "升级" + (succeed ? "成功" : "失败:" + message));
                //TODO
            }
        });
    }


    private void prepareFormatUSB() {
        if (remoteDebug) {
            sendMessageToMqtt("U盘开始格式化");
        }
        DeviceUtils.saveFormatFlag(1, MainActivity.this);
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
                DeviceUtils.restartDevice();
            }
        }, 1000);
    }


    private void prepareFormatCamera() {
        if (remoteDebug) {
            sendMessageToMqtt("相机开始格式化");
        }
        DeviceUtils.saveFormatFlag(2, MainActivity.this);
        sendOrderedBroadcast(new Intent(FormatFlagBroadcast), null);

        Utils.resetDir(VariableInstance.getInstance().TFCardUploadPictureDir);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isUpdating) {
                    return;
                }
                DeviceUtils.restartDevice();
            }
        }, 1000);
    }

    private void sendMessageToMqtt(String message) {
        if (MqttManager.isConnected()) {
            if (networkOperation.returnImei != null) {
                MqttManager.getInstance().publish("/camera/v2/device/" + networkOperation.returnImei + "/android/receive", 1, message);
            }
        } else {
            if (networkOperation.networkAvailable && networkOperation.returnImei != null) {
                Message message1 = new Message();
                message1.what = msg_resend_mqtt;
                message1.obj = message;
                mHandler.sendMessageDelayed(message1, 1000);
                android.util.Log.d(TAG, "publishMessage: mqtt 未连接 重发 message =" + message);
            }
        }

    }

    @SuppressLint("SetTextI18n")
    private void UploadMode5() {
        VariableInstance.getInstance().UploadMode = 5;
        saveUploadModel(null);
        上传模式View.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }

    @SuppressLint("SetTextI18n")
    private void UploadMode1() {
        VariableInstance.getInstance().UploadMode = 1;
        saveUploadModel(null);
        上传模式View.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }

    @SuppressLint("SetTextI18n")
    private void UploadMode2() {
        VariableInstance.getInstance().UploadMode = 2;
        saveUploadModel(null);
        上传模式View.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
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
        上传模式View.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
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
        上传模式View.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }

    private String serverGetInfo() {

        DeviceMemory deviceMemory = scannerStoreUSB.getDeviceMemory();
        int capacity = deviceMemory.capacity;
        int freeSpace = deviceMemory.freeSpace;
        String uploadModelString;

        if (VariableInstance.getInstance().UploadMode == 1) {
            uploadModelString = "1,0";

        } else if (VariableInstance.getInstance().UploadMode == 2) {
            uploadModelString = "2,0";

        } else if (VariableInstance.getInstance().UploadMode == 3) {
            if (VariableInstance.getInstance().uploadSelectIndexList.size() == 0)
                uploadModelString = "3,0";
            else {
                uploadModelString = "3";
                for (Integer integer : VariableInstance.getInstance().uploadSelectIndexList) {
                    uploadModelString = uploadModelString + "," + integer;
                }
            }

        } else {
            if (VariableInstance.getInstance().uploadSelectIndexList.size() == 0)
                uploadModelString = "4,0";
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
        if (copySpeed == null)
            copySpeed = "0";
        String ccid = DeviceUtils.getPhoneNumber(MainActivity.this);
        runOnUiThreadText(卡号View, "卡号：" + ccid);
        String csg = DeviceUtils.getSignalStrength(signalStrengthValue);
        int PhotoSum = scannerStoreUSB.deviceTotalPicture;
        int PhotoUploadThisTime = networkOperation.uploadRemoteCompletePictureNum;
        boolean initUSB = scannerStoreUSB.completedScanner;
        boolean connectCamera = scannerCamera.isConnectCamera;
        int cameraPictureCount = scannerCamera.deviceTotalPicture;
        int copyTotalNum = scannerCamera.backupUSBTotalPicture;
        int copyCompleteNum = scannerStoreUSB.backupUSBCompletePictureNum;
        int waitUploadPhoto = scannerCamera.backRemoteTotalPicture - PhotoUploadThisTime;
        if (waitUploadPhoto < 0) {
            waitUploadPhoto = networkOperation.pictureFileListCache.size();
        }
        int UploadUseTime = (int) (networkOperation.uploadTatalTime / 1000);


        String info = "4gCcid," + ccid + ";UploadSpeed," + UploadSpeed + ";4gCsq," + csg + ";SdFree," + freeSpace + ";SdFull," + capacity + ";PhotoSum," + PhotoSum + ";PhotoUploadThisTime," + PhotoUploadThisTime + ";UploadMode," + uploadModelString + ";UploadUseTime," + UploadUseTime + ";Version," + appVerison + ";initUSB," + initUSB + ";connectCamera," + connectCamera + ";cameraPictureCount," + cameraPictureCount + ";cameraName," + cameraName + ";waitUploadPhoto," + waitUploadPhoto + ";copySpeed," + copySpeed + ";copyTotalNum," + copyTotalNum + ";copyCompleteNum," + copyCompleteNum + ";";

        Log.d(TAG, "serverGetInfo: info =" + info);
        return info;
    }


    private void openCameraDeviceProt(boolean open, int positon) {
        Log.e(TAG, "openCameraDeviceProt: " + (open ? "打开" : "关闭") + "相机通信端口" + ",positon =" + positon);
        if (debug)
            return;

        if (open) {
            LedControl.writeGpio('b', 2, 1);
        } else {
            LedControl.writeGpio('b', 2, 0);
        }
    }

    private void openNetworkLed(boolean open) {
        Log.d(TAG, "openNetworkLed: " + (open ? "打开" : "关闭") + "网络端口");
        if (debug)
            return;
        if (open) {
            LedControl.writeGpio('b', 3, 1);//打开网络
        } else {
            LedControl.writeGpio('b', 3, 0);//打开网络
        }
    }


    private void setLEDState(String state) {
        Log.e(TAG, "setLEDState: state =" + state);
        if (debug)
            return;

        switch (state) {
            case 快闪://快闪
            {
                LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_HEARTBEAT);
            }
            break;
            case 慢闪://慢闪
            {
                LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_TIMER);
            }
            break;
            case 常亮://常亮
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
            openCameraDeviceProt(false, 4);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mHandler.removeMessages(msg_open_camera_port_timeout);
                    mHandler.sendEmptyMessageDelayed(msg_open_camera_port_timeout, 10000);
                    openCameraDeviceProt(true, 5);

                }
            }, 1000);
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
        } else if (view.getId() == R.id.updateBeta) {
            updateBetaApk();
        }
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

        上传模式View.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);

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

    private void startUploadLogcat() {

        if (mHandler.hasMessages(msg_init_network_timeout)) {
            Log.e(TAG, "startUploadLogcat: 在等待网络初始化");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }


        if (isUpdating) {
            Log.e(TAG, "startUploadLogcat: 在等待应用升级");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }

        if (scannerCamera.isOperatingDevice) {
            Log.e(TAG, "startUploadLogcat: 正在操作相机");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }


        if (mHandler.hasMessages(msg_open_camera_port_timeout)) {
            Log.e(TAG, "startUploadLogcat: 正在等待打开相机端口广播");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }

        if (scannerStoreUSB.isOperatingDevice) {
            Log.e(TAG, "startUploadLogcat: 正在扫描U盘");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }
        if (scannerStoreUSB.uploadingLogcat) {
            Log.e(TAG, "startUploadLogcat: 正在上传日志");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }
        if (scannerStoreUSB.uploadingPicture) {
            Log.e(TAG, "startUploadLogcat: 正在备份图片");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }

        if (networkOperation.networkIniting) {
            Log.e(TAG, "startUploadLogcat: 正在初始化网络");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }

        if (networkOperation.uploadingRemotePicture) {
            Log.e(TAG, "startUploadLogcat: 正在上传远程图片");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, CHECK_WORKING_TIME);
            return;
        }


        Log.e(TAG, "所有错误信息: -------------------------->\n" + scannerStoreUSB.errorMessage + "\n" + scannerCamera.errorMessage);

        networkOperation.uploadLogcatFileToRemote(new NetworkOperation.UploadLogcatListener() {
            @Override
            public void uploadLogcatComplete(boolean succeed, String message) {
                if(remoteDebug){
                    sendMessageToMqtt("日志上传" + (succeed ? ("成功-->" + message) : "失败-->" + message));
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        logcatUploadComplete();
                    }
                }, 1000);
            }

            @Override
            public void startUploadLogcatToUsb() {
                scannerStoreUSB.uploadLogcatToUSB();
            }
        });
    }


    private void logcatUploadComplete() {
        Log.d(TAG, "logcatUploadComplete: ");
        Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
        if (remoteDebug) {
            Log.e(TAG, "logcatUploadComplete: 测试阶段，不关机");
            sendMessageToMqtt("测试阶段，不关机");
            mHandler.removeMessages(msg_check_working_timeout);
            mHandler.sendEmptyMessageDelayed(msg_check_working_timeout, 10 * CHECK_WORKING_TIME);
            return;
        }

        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (!networkOperation.networkAvailable) {
            int checkNetWorkCountt = sharedPreferences.getInt("checkNetWorkCountt", 0);
            if (checkNetWorkCountt == 0) {
                Log.e(TAG, "logcatUploadComplete: 关机前检测一次网络不可用，重启设备");
                editor.putInt("checkNetWorkCountt", 1);
                editor.apply();
                DeviceUtils.restartDevice();
                return;
            }
        }

        editor.putInt("checkNetWorkCountt", 0);
        editor.apply();
        Utils.closeAndroid();
    }


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
                case msg_init_network_timeout:
                    Log.e(TAG, "handleMessage: 初始化网络超时");
                    activity.runOnUiThreadText(activity.服务器状态View, "服务器状态：" + "异常:" + "初始化网络超时");
                    activity.networkOperation.networkDisconnect(true);
                    activity.scannerStoreUSB.startScannerDevice();
                    break;
                case msg_open_camera_port_timeout:
                    Log.e(TAG, "handleMessage: 打开相机端口超时没接收到连接广播");
                    activity.mHandler.removeMessages(msg_open_camera_port_timeout);
                    activity.scannerCamera.startScannerDevice();
                    break;
                case msg_formatusb_timeout:
                    DeviceUtils.restartDevice();
                    break;
                case msg_resend_mqtt:
                    if (msg.obj != null) {
                        activity.sendMessageToMqtt((String) msg.obj);
                    }
                    break;
                case msg_check_working_timeout:
                    activity.startUploadLogcat();
                    break;
            }
        }
    }


}
