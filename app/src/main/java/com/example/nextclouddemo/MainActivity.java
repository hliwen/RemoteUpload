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
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
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

import com.blankj.utilcode.util.AppUtils;
import com.example.gpiotest.GpioActivity;
import com.example.gpiotest.LedControl;
import com.example.nextclouddemo.model.DeviceInfoModel;
import com.example.nextclouddemo.model.MyMessage;
import com.example.nextclouddemo.model.ServerUrlModel;
import com.example.nextclouddemo.model.UploadFileModel;
import com.example.nextclouddemo.mqtt.MqttManager;
import com.example.nextclouddemo.utils.Communication;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;

public class MainActivity extends Activity implements View.OnClickListener {
    public static final boolean debug = false;

    private static final String FormatUSB = "Start,Format;";
    private static final String FormatTF = "Start,FormatTF;";
    private static final String FormatCamera = "Start,FormatCamera;";
    private static final String Upload = "Start,Upload;";
    private static final String UploadMode1 = "Set,UploadMode,1;";
    private static final String UploadMode2 = "Set,UploadMode,2;";
    private static final String UploadMode3 = "Set,UploadMode,3,";
    private static final String UploadMode4 = "Set,UploadMode,4,";
    private static final String GetInfo = "Get,Info;";
    private static final String return2GImei = "return2GImei";
    private static final String UploadEndUploadUseTime = "Upload,End,UploadUseTime,";
    private static final String AppShutdownAck = "App,shutdown,ack;";
    private static final String UploadToday = "Set,UploadToday,";
    private static final String Set_FormatUdisk_All = "Set,FormatUdisk,All";
    private static final String Set_FormatUdisk_2weeks = "Set,FormatUdisk,2weeks";
    private static final String Set_FormatCamera_All = "Set,FormatCamera,All";
    private static final String Set_FormatCamera_2weeks = "Set,FormatCamera,2weeks";
    private static final String Set_Powoff = "Set,Powoff";
    private static final String Set_UploadLocat = "Set,UploadLocat";
    private static final String Set_WakeCamer = "Set,WakeCamer";
    private static final String Set_ResetApk = "Set,ResetApk";
    private static final String Set_UpdateBetaApk = "Set,UpdateBetaApk";
    private static final String Set_UpdateFormalApk = "Set,UpdateFormalApk";
    private static final int close_device_timeout = 3 * 60 * 1000;
    private static final int close_device_timeout_a = 5 * 60 * 1000;

    private static final int delay_crate_acitivity_time = 5 * 1000;
    private static String TAG = "MainActivitylog";
    private String returnImei;
    private String deveceName;
    private boolean doingInit;
    private String messageTextString;
    private boolean isUpdating;

    private boolean openDeviceProtFlag;
    private int signalStrengthValue;
    private int appVerison;
    private int cameraPictureCount;
    private String cameraName;
    private String copySpeed;
    private int copyTotalNum;
    private String uuidString;
    private MyHandler mHandler;

    private Communication communication;
    private ReceiverCamera receiverCamera;
    private ReceiverStoreUSB receiverStoreUSB;
    private RemoteOperationUtils operationUtils;
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
    private Button catErrorLogcat;


    private UpdateUtils updateUtils;
    private WifiReceiver mWifiReceiver;

    private boolean sendShutDown;
    private boolean networkAvailable;

    boolean hasinitCellularNetWork;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main_acitivity);
        openCameraDeviceProt(false);
        mHandler = new MyHandler(MainActivity.this);
        UUID uuid = UUID.randomUUID();
        uuidString = uuid.toString();
        mHandler.removeMessages(msg_delay_creta_acitivity);
        mHandler.sendEmptyMessageDelayed(msg_delay_creta_acitivity, delay_crate_acitivity_time);

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
        catErrorLogcat = findViewById(R.id.catErrorLogcat);

        guanjiBt.setOnClickListener(this);
        rescanerBt.setOnClickListener(this);
        openProtActivityBt.setOnClickListener(this);
        clearViewBt.setOnClickListener(this);
        formatUSBt.setOnClickListener(this);
        formatCameraBt.setOnClickListener(this);
        catErrorLogcat.setOnClickListener(this);
    }


    void delayCreate() {

        getUploadToday();
        VariableInstance.getInstance().isFormaringCamera.formatState = 0;
        VariableInstance.getInstance().isConnectCamera = false;
        VariableInstance.getInstance().isInitUSB = false;
        VariableInstance.getInstance().initingUSB = false;
        VariableInstance.getInstance().isConnectedRemote = false;
        VariableInstance.getInstance().isFormatingUSB.formatState = 0;
        VariableInstance.getInstance().uploadRemorePictureNum = 0;
        VariableInstance.getInstance().downdCameraPicrureNum = 0;
        VariableInstance.getInstance().LastPictureCount = 0;
        VariableInstance.getInstance().storeUSBDeviceID = -1;
        VariableInstance.getInstance().isScanningStoreUSB = false;
        VariableInstance.getInstance().isDownloadingUSB = false;
        VariableInstance.getInstance().usbFileNameList.clear();

        communication = new Communication();
        updateUtils = new UpdateUtils(updateListener);
        operationUtils = new RemoteOperationUtils(remoteOperationListener);


        initView();

        String[] value = Utils.haveNoPermissions(MainActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }

        EventBus.getDefault().register(this);

        sendCloseDeviceMessage(1);
        getUploadModel();

        Utils.makeDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.makeDir(VariableInstance.getInstance().TFCardUploadPictureDir);

        openNetworkLed(true);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        registerStoreUSBReceiver();

        if (debug) {
            initCellularNetWork();
        }
    }


    private void sendCloseDeviceMessage(int position) {
        Log.e(TAG, "sendCloseDeviceMessage: position =" + position);
        Message message = new Message();
        message.what = msg_close_device;
        message.arg1 = position;
        mHandler.sendMessageDelayed(message, close_device_timeout);
    }

    private void removeCloseDeviceMessage(int position) {
        Log.e(TAG, "removeCloseDeviceMessage: position =" + position);
        mHandler.removeMessages(msg_close_device);
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


    PhoneStateListener MyPhoneListener = new PhoneStateListener() {

        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            int asu = signalStrength.getGsmSignalStrength();
            signalStrengthValue = -113 + 2 * asu;
        }
    };

    private UpdateUtils.UpdateListener updateListener = new UpdateUtils.UpdateListener() {
        @Override
        public void serverVersion(int version) {
            runOnUiThreadText(serverVersionText, "最新版本：" + version);
        }

        @Override
        public void downloadProgress(int progress) {
            runOnUiThreadText(downloadAppProgressText, "app下载：" + progress + "kb");
        }

        @Override
        public void startUpdate() {
            Log.e(TAG, "startUpdate: ..................................");
            isUpdating = true;
            runOnUiThreadText(updateResultText, "开始升级");
            removeCloseDeviceMessage(1);
        }

        @Override
        public void endUpdate(boolean succeed) {
            isUpdating = false;
            VariableInstance.getInstance().isUpdatingBetaApk = false;
            runOnUiThreadText(updateResultText, "升级" + (succeed ? "成功" : "失败"));
            removeCloseDeviceMessage(2);
            sendCloseDeviceMessage(2);
        }
    };

    private ReceiverStoreUSB.StoreUSBListener storeUSBListener = new ReceiverStoreUSB.StoreUSBListener() {
        @Override
        public void storeUSBPictureCount(int count) {
            UpanPictureCount = count;
            if (count != 0) {
                runOnUiThreadText(UpanPictureCountText, "U盘图片总数:" + count);
            }
        }

        @Override
        public void storeUSBDeviceDetached() {
            openCameraDeviceProt(false);
        }

        @Override
        public void storeUSBSaveOnePictureComplete(String speed) {
            copySpeed = speed;
            runOnUiThreadText(hasDownloadPictureNumberText, "已下载张数:" + VariableInstance.getInstance().downdCameraPicrureNum + "\n同步到USB速度:" + speed);
        }

        @Override
        public void initStoreUSBFailed() {
            if (VariableInstance.getInstance().storeUSBDeviceID != 1) {
                return;
            }
            Log.e(TAG, "initStoreUSBFailed: U盘初始化失败，仍然连接mqtt通信");
            String imei = getPhoneImei(true);
            DeviceModel deviceModelConnect = null;
            if ("0".equals(imei)) {
                int style = getDeviceStyle(); //0是还不确定是蜂窝板还是WiFi版，1是蜂窝版，2是WiFi版
                if (style == 0 || style == 1) {
                    saveDeviceStyle(1); //蜂窝板
                } else {
                    DeviceModel deviceModel = getDeviceModel();
                    if (deviceModel == null) {
                        saveDeviceStyle(1);
                        //蜂窝板
                    } else {
                        //wifi版
                        deviceModelConnect = deviceModel;
                        saveDeviceStyle(2);
                    }
                }
            } else {
                saveDeviceStyle(1); //蜂窝板
            }

            if (VariableInstance.getInstance().deviceStyle == 1) {
                initCellularNetWork();
            } else if (VariableInstance.getInstance().deviceStyle == 2) {
                registerWifiReceiver();
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    boolean wifiEnable = wifiManager.isWifiEnabled();
                    if (wifiEnable) {
                        if (deviceModelConnect.wifi != null) {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().contains(deviceModelConnect.wifi)) {
                                Log.e(TAG, "initStoreUSBComplete: 当前自动链接上WiFi " + wifiInfo.getSSID());
                                networkConnect();
                                return;
                            }
                            if (deviceModelConnect.pass == null) {
                                connectWifiNoPws(deviceModelConnect.wifi, wifiManager);
                            } else {
                                if (deviceModelConnect.pass.length() == 0) {
                                    connectWifiNoPws(deviceModelConnect.wifi, wifiManager);
                                } else {
                                    connectWifiPws(deviceModelConnect.wifi, deviceModelConnect.pass, wifiManager);
                                }
                            }
                        } else {
                            Log.e(TAG, "initStoreUSBComplete: 当前配置的WiFi文件有错");
                        }
                    } else {
                        wifiManager.setWifiEnabled(true);
                    }
                }

            }
        }


        @Override
        public void initStoreUSBComplete(UsbFile wifiConfigurationFile) {
            String imei = getPhoneImei(true);
            DeviceModel deviceModelConnect = null;
            if ("0".equals(imei)) {
                if (wifiConfigurationFile == null) {
                    int style = getDeviceStyle(); //0是还不确定是蜂窝板还是WiFi版，1是蜂窝版，2是WiFi版
                    if (style == 0 || style == 1) {
                        saveDeviceStyle(1); //蜂窝板
                    } else {
                        DeviceModel deviceModel = getDeviceModel();
                        if (deviceModel == null) {
                            saveDeviceStyle(1);
                            //蜂窝板
                        } else {
                            //wifi版
                            deviceModelConnect = deviceModel;
                            saveDeviceStyle(2);
                        }
                    }
                } else {
                    DeviceModel parseMode = parseUSBFile(wifiConfigurationFile);
                    DeviceModel deviceModel = getDeviceModel();
                    if (parseMode == null) {
                        if (deviceModel == null) {
                            saveDeviceStyle(1);
                            //蜂窝板
                        } else {
                            deviceModelConnect = deviceModel;
                            saveDeviceStyle(2);
                            //wifi版
                        }
                    } else {
                        saveDeviceStyle(2);
                        saveDeviceModel(parseMode);
                        deviceModelConnect = parseMode;
                        //wifi版
                    }
                }
            } else {
                saveDeviceStyle(1); //蜂窝板
            }

            openCameraDeviceProt(true);
            getInfo();

            int capacity = receiverStoreUSB.getStoreUSBCapacity();
            int freeSpace = receiverStoreUSB.getStoreUSBFreeSpace();

            runOnUiThreadText(UpanSpaceText, "U盘空间:" + "\ncapacity:" + capacity + "\nfreeSpace:" + freeSpace);

            if (receiverCamera == null) {
                registerReceiverCamera();
            }

            if (VariableInstance.getInstance().deviceStyle == 1) {
                initCellularNetWork();
            } else if (VariableInstance.getInstance().deviceStyle == 2) {
                registerWifiReceiver();
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    boolean wifiEnable = wifiManager.isWifiEnabled();
                    if (wifiEnable) {
                        if (deviceModelConnect.wifi != null) {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().contains(deviceModelConnect.wifi)) {
                                Log.e(TAG, "initStoreUSBComplete: 当前自动链接上WiFi " + wifiInfo.getSSID());
                                networkConnect();
                                return;
                            }
                            if (deviceModelConnect.pass == null) {
                                connectWifiNoPws(deviceModelConnect.wifi, wifiManager);
                            } else {
                                if (deviceModelConnect.pass.length() == 0) {
                                    connectWifiNoPws(deviceModelConnect.wifi, wifiManager);
                                } else {
                                    connectWifiPws(deviceModelConnect.wifi, deviceModelConnect.pass, wifiManager);
                                }
                            }
                        } else {
                            Log.e(TAG, "initStoreUSBComplete: 当前配置的WiFi文件有错");
                        }
                    } else {
                        wifiManager.setWifiEnabled(true);
                    }
                }

            }
        }

    };


    private ReceiverCamera.CameraScanerListener scannerCameraListener = new ReceiverCamera.CameraScanerListener() {

        @Override
        public void cameraOperationStart() {
            VariableInstance.getInstance().isScanningCamera = true;

            if (!VariableInstance.getInstance().isUploadingToRemote) {
                startDownLed(true);
            }
            removeCloseDeviceMessage(3);
        }

        @Override
        public void cameraOperationEnd(int cameraTotalPicture) {
            openCameraDeviceProt(false);

            VariableInstance.getInstance().isScanningCamera = false;

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

                VariableInstance.getInstance().errorLogNameList.add(ErrorName.获取相机图片张数为0可能是无法获取照片信息);
                return;
            }

            SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
            editor.putInt("ScanerCount", 0);
            editor.apply();

            if (!VariableInstance.getInstance().isUploadingToRemote) {
                removeCloseDeviceMessage(4);
                sendCloseDeviceMessage(3);
            }
            getInfo();
        }


        @Override
        public boolean uploadToUSB(File localFile, String yearMonth) {
            if (receiverStoreUSB == null) {
                return false;
            }
            return receiverStoreUSB.uploadToUSB(localFile, yearMonth);
        }

        @Override
        public void addUploadRemoteFile(UploadFileModel uploadFileModel) {
            operationUtils.addUploadRemoteFile(uploadFileModel, false);
        }


        @Override
        public void cameraDeviceDetached() {
            if (receiverStoreUSB != null) {
                receiverStoreUSB.getUSBPictureCount();
            }
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
            Log.d(TAG, "allFileUploadComplete: 所有文件上传完成 totalTime =" + totalTime / 1000 + "s,isScanningCamera =" + VariableInstance.getInstance().isScanningCamera);

            VariableInstance.getInstance().isUploadingToRemote = false;

            if (!VariableInstance.getInstance().isScanningCamera) {
                restLed();
            }
            if (totalTime != 0) {
                UploadUseTime = totalTime / 1000;
            }
            runOnUiThreadText(uploadUseTimeText, "本次同步到服务器耗时:" + totalTime / 1000 + "s");

            getInfo();

            removeCloseDeviceMessage(5);
            sendCloseDeviceMessage(4);
        }

        @Override
        public void pictureUploadStart() {
            Log.d(TAG, "fileUploadStart: ");
            VariableInstance.getInstance().isUploadingToRemote = true;
            removeCloseDeviceMessage(6);
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
            mHandler.removeMessages(msg_send_ShutDown);
            mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, close_device_timeout_a);
            Log.d(TAG, "send msg_send_ShutDown 1111111111111111");
            sendMessageToMqtt(UploadEndUploadUseTime + UploadUseTime + ";");
        }

        @Override
        public boolean canStopPictureUploadThread() {
            Log.e(TAG, "canStopPictureUploadThread: 是否可以停止上传服务器线程  isScanningCamera= " + VariableInstance.getInstance().isScanningCamera + ",isScanningStoreUSB =" + VariableInstance.getInstance().isScanningStoreUSB + ",isDownloadingUSB = " + VariableInstance.getInstance().isDownloadingUSB);
            return (!VariableInstance.getInstance().isScanningCamera && !VariableInstance.getInstance().isScanningStoreUSB && !VariableInstance.getInstance().isDownloadingUSB);
        }


        @Override
        public void startUploadLogcatToUsb() {
            if (receiverStoreUSB != null) {
                receiverStoreUSB.uploadLogcatToUSB(LogcatHelper.getInstance().logcatFileSecondPath);
            }
        }
    };


    private void registerReceiverCamera() {
        receiverCamera = new ReceiverCamera(getApplicationContext(), scannerCameraListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(ReceiverCamera.CHECK_PERMISSION);
        registerReceiver(receiverCamera, intentFilter);
    }

    private void registerStoreUSBReceiver() {
        receiverStoreUSB = new ReceiverStoreUSB(getApplicationContext(), storeUSBListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(ReceiverStoreUSB.INIT_STORE_USB_PERMISSION);
        registerReceiver(receiverStoreUSB, intentFilter);
    }


    private void registerWifiReceiver() {
        mWifiReceiver = new WifiReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mWifiReceiver, filter);
    }


    public void initCellularNetWork() {

        if (hasinitCellularNetWork) {
            return;
        }
        hasinitCellularNetWork = true;

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder request = new NetworkRequest.Builder();
        request.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        request.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);

        NetworkRequest build = request.build();
        connectivityManager.requestNetwork(build, new ConnectivityManager.NetworkCallback() {
            public void onAvailable(Network network) {
                networkConnect();
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.e(TAG, "Network  onLost: ");
                netWorkLost();

                VariableInstance.getInstance().errorLogNameList.add(ErrorName.网络断开);
            }
        });
    }


    private void networkConnect() {
        networkAvailable = true;
        Log.e(TAG, "Network onAvailable: doingInit =" + doingInit);
        runOnUiThreadText(isConnectNetworkText, "是否连网:true");
        if (doingInit) {
            return;
        }
        doingInit = true;
        updateUtils.networkAvailable(MainActivity.this);
        initAddress();
    }

    private void netWorkLost() {
        Log.e(TAG, "netWorkLost: ");
        networkAvailable = false;
        doingInit = false;
        runOnUiThreadText(isConnectNetworkText, "是否连网:false");
        runOnUiThreadText(mqttStateText, "mqtt状态:false");
        operationUtils.stopUploadThread();
        MqttManager.getInstance().release();
        openNetworkLed(false);
        openNetworkLed(true);
        updateUtils.networkLost();
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


    private void initAddress() {
        if (!networkAvailable) {
            Log.e(TAG, "initAddress 网络不可用");
        }

        getInfo();

        Thread workThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerUrlModel serverUrlModel = communication.getServerUrl();
                    Log.e(TAG, "initAddress: serverUrlModel =" + serverUrlModel);
                    if (serverUrlModel == null || serverUrlModel.responseCode != 200) {
                        Log.e(TAG, "run: initAddress 无法获取服务器地址");
                        mHandler.removeMessages(msg_reload_device_info);
                        mHandler.sendEmptyMessageDelayed(msg_reload_device_info, 10000);
                        updateServerStateUI(false);
                        return;
                    }

                    String imei = getPhoneImei(false);
                    if ("0".equals(imei)) {
                        imei = getPhoneImei(true);
                    }

                    DeviceInfoModel deviceInfoModel = communication.getDeviceInfo(imei);


                    if (serverUrlModel == null || deviceInfoModel.responseCode != 200) {
                        Log.e(TAG, "run: initAddress 无法获取服务器设备信息");
                        mHandler.removeMessages(msg_reload_device_info);
                        mHandler.sendEmptyMessageDelayed(msg_reload_device_info, 10000);
                        updateServerStateUI(false);
                        VariableInstance.getInstance().errorLogNameList.add(ErrorName.无法获取服务器设备信息);
                        return;
                    }

                    mHandler.removeMessages(msg_reload_device_info);

                    Log.e(TAG, "initAddress: deviceInfoModel =" + deviceInfoModel);

                    returnImei = deviceInfoModel.returnImei;
                    deveceName = deviceInfoModel.deveceName;
                    EventBus.getDefault().post(return2GImei);

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
                                if (data != null && data.length > 0) {
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


                    VariableInstance.getInstance().ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(serverUrlModel.serverUri, MainActivity.this, true);
                    VariableInstance.getInstance().ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(deviceInfoModel.username, deviceInfoModel.password));


                    VariableInstance.getInstance().isConnectedRemote = operationUtils.initRemoteDir(deviceInfoModel.deveceName);
                    Log.d(TAG, "initAddress:   配置远程服务器是否成功 =" + VariableInstance.getInstance().isConnectedRemote);
                    updateServerStateUI(VariableInstance.getInstance().isConnectedRemote);
                    if (VariableInstance.getInstance().isConnectedRemote) {
                        removeCloseDeviceMessage(9);
                        mHandler.removeMessages(msg_reload_device_info);
                        operationUtils.startCameraPictureUploadThread();

                    } else {
                        Log.e(TAG, "initAddress:   配置远程服务器失败，延时10s后继续访问尝试 ");
                        VariableInstance.getInstance().errorLogNameList.add(ErrorName.配置远程服务器失败延时10s后继续访问尝试);
                        mHandler.removeMessages(msg_reload_device_info);
                        mHandler.sendEmptyMessageDelayed(msg_reload_device_info, 10000);
                    }


                } catch (Exception e) {
                    Log.e(TAG, "initAddress:远程连接出现异常 = " + e);
                    VariableInstance.getInstance().errorLogNameList.add(ErrorName.远程连接出现异常 + ":" + e.toString());
                    updateServerStateUI(false);
                    doingInit = false;
                }
            }
        });
        workThread.start();
    }


    private void initMqtt() {
        if (phoneImei == null) {
            phoneImei = uuidString;
        }

        MqttManager.getInstance().creatConnect("tcp://120.78.192.66:1883", "devices", "a1237891379", "" + phoneImei, "/camera/v1/device/" + returnImei + "/android");

        MqttManager.getInstance().subscribe("/camera/v2/device/" + returnImei + "/android/send", 1);
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
        if (mWifiReceiver != null) {
            unregisterReceiver(mWifiReceiver);
        }

        mHandler.removeCallbacksAndMessages(null);

        openCameraDeviceProt(false);
        operationUtils.stopUploadThread();

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_NONE);
        EventBus.getDefault().unregister(this);
        MqttManager.getInstance().release();

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
            case return2GImei:
                initMqtt();
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
                    Utils.resetDir(VariableInstance.getInstance().LogcatDir);
                    Utils.closeAndroid();
                }
            }
            break;
            case Set_UploadLocat:
                operationUtils.startUploadLocatThread(false);
                break;
            case Set_WakeCamer:
                break;
            case Set_ResetApk: {
                if (!isUpdating) {
                    restartAPK();
                    if (receiverStoreUSB != null) {
                        receiverStoreUSB.usbDissConnect(receiverStoreUSB.mUsbDevice);
                    }
                    finish();
                }
            }
            break;
            case Set_UpdateBetaApk:
                updateBetaApk();
                break;
            case Set_UpdateFormalApk:
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

        if (networkAvailable) {
            updateUtils.checkBetaApk(MainActivity.this);
        }
    }

    private void AppShutdownAck() {
        if (sendShutDown) {
            mHandler.removeMessages(msg_send_ShutDown);
            mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, 5000);
            Log.d(TAG, "send msg_send_ShutDown 2222222222222222222");
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


                while (VariableInstance.getInstance().isScanningCamera || VariableInstance.getInstance().isDownloadingUSB || VariableInstance.getInstance().isScanningStoreUSB) {

                    try {
                        Log.e(TAG, "run: 等待格式化");
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                }

                openCameraDeviceProt(false);
                Log.e(TAG, "formatUSB: start .......................................");
                VariableInstance.getInstance().isFormatingUSB.formatState = all ? 1 : 2;
                runOnUiThreadText(formatUSBt, "开始删除USB图片");

                VariableInstance.getInstance().usbFileNameList.clear();

                operationUtils.stopUploadThread();

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

                if (receiverCamera != null) {
                    receiverCamera.storeUSBDetached();
                }
                VariableInstance.getInstance().isFormatingUSB.formatState = 0;
                if (isUpdating) {
                    return;
                }
                restartAPK();
                if (receiverStoreUSB != null) {
                    receiverStoreUSB.usbDissConnect(receiverStoreUSB.mUsbDevice);
                }
                finish();
            }
        }).start();
    }

    private void restartAPK() {
        DataOutputStream localDataOutputStream = null;
        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("su");
            OutputStream localOutputStream = process.getOutputStream();
            localDataOutputStream = new DataOutputStream(localOutputStream);

            String command = "sleep 5 && am start -W -n com.example.nextclouddemo/com.example.nextclouddemo.MainActivity";
            localDataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            localDataOutputStream.flush();
        } catch (Exception e) {

        } finally {
            try {
                if (localDataOutputStream != null) {
                    localDataOutputStream.close();
                }

            } catch (IOException e) {

            }
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
                Log.d(TAG, "send msg_send_ShutDown 4444444444444444");
                mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, close_device_timeout_a);
                openCameraDeviceProt(false);
                openCameraDeviceProt(true);
            }
        }).start();
    }


    private void getInfo() {
        sendMessageToMqtt(serverGetInfo());
    }


    private void sendMessageToMqtt(String message) {
        Log.d(TAG, "sendMessageToMqtt: message =" + message);
        if (returnImei != null) MqttManager.getInstance().publish("/camera/v2/device/" + returnImei + "/android/receive", 1, message);
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
    private int UpanPictureCount = 0;
    private long UploadUseTime;
    private String UploadSpeed;

    private String serverGetInfo() {
        int capacityA = 0;
        int freeSpaceA = 0;
        int PhotoSumA = 0;

        if (receiverStoreUSB != null) {
            capacityA = receiverStoreUSB.getStoreUSBCapacity();
            if (capacityA != 0) {
                capacity = capacityA;
            }
            freeSpaceA = receiverStoreUSB.getStoreUSBFreeSpace();
            if (freeSpaceA != 0) {
                freeSpace = freeSpaceA;
            }

            PhotoSumA = VariableInstance.getInstance().LastPictureCount;
            if (PhotoSumA != 0) {
                UpanPictureCount = PhotoSumA;
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

        String info = "4gCcid," + getPhoneNumber() + ";UploadSpeed," + UploadSpeed + ";4gCsq," + getSignalStrength() + ";SdFree," + freeSpace + ";SdFull," + capacity + ";PhotoSum," + UpanPictureCount + ";PhotoUploadThisTime," + VariableInstance.getInstance().uploadRemorePictureNum + ";UploadMode," + uploadModelString + ";UploadUseTime," + UploadUseTime + ";Version," + appVerison + ";initUSB," + VariableInstance.getInstance().isInitUSB + ";connectCamera," + VariableInstance.getInstance().isConnectCamera + ";cameraPictureCount," + cameraPictureCount + ";cameraName," + cameraName + ";waitUploadPhoto," + (operationUtils == null ? 0 : operationUtils.pictureFileListCache.size()) + ";copySpeed," + copySpeed + ";copyTotalNum," + copyTotalNum + ";copyCompleteNum," + VariableInstance.getInstance().downdCameraPicrureNum + ";";
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

            if (number == null) {
                number = "0";
            }
            Log.d(TAG, "getPhoneNumber: 卡号 =" + number);

            return number;
        } catch (Exception | Error e) {
            Log.e(TAG, "getPhoneNumber: Exception =" + e);
            return "0";
        }
    }


    String phoneImei;

    @SuppressLint("HardwareIds")
    private String getPhoneImei(boolean init) {
        Log.e(TAG, "getPhoneImei: init =" + init);
        String imei = "0";
        if (init) {
            try {
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                imei = telephonyManager.getDeviceId();
                Log.d(TAG, "getPhoneImei:1111 imei =" + imei);
                if (imei == null) {
                    imei = "0";
                }
                Log.d(TAG, "getPhoneImei:2222 imei =" + imei);
            } catch (Exception | Error e) {
                Log.e(TAG, "getPhoneImei:3333 Exception =" + e);
                imei = "0";
            }
        } else {
            DeviceModel deviceModel = getDeviceModel();
            if (deviceModel == null || deviceModel.SN == null || deviceModel.SN.length() == 0) {
                imei = "0";
                Log.e(TAG, "getPhoneImei: 444444");
            } else {
                imei = deviceModel.SN;
            }
        }
        Log.d(TAG, "getPhoneImei:555555 imei =" + imei);

        if (debug) imei = "867706050952138";

        if (!"0".equals(imei)) {
            runOnUiThreadText(accessNumberText, "入网号:" + imei);
        } else {
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.无法获取设备IEMI);
        }

        phoneImei = imei;
        return imei;
    }


    private void openCameraDeviceProt(boolean open) {
        Log.e(TAG, "openCameraDeviceProt: 连接相机通信端口 led: " + (open ? "打开" : "关闭") + ", 当前状态" + (openDeviceProtFlag ? "打开" : "关闭") + "-------------------------------------------");
        if (openDeviceProtFlag == true && open) return;

        openDeviceProtFlag = open;
        runOnUiThreadText(cameraStateText, "相机状态:" + openDeviceProtFlag);

        if (debug) return;
        if (open) {
            LedControl.writeGpio('b', 2, 1);
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
        } else if (view.getId() == R.id.catErrorLogcat) {
            messageTextString = "";
            try {
                for (String error : VariableInstance.getInstance().errorLogNameList) {
                    messageTextString = messageTextString + "\n" + error + "\n";
                    messageText.setText(messageTextString);
                }
            } catch (Exception e) {
            }
        }
    }


    class DeviceModel {
        public String wifi;
        public String pass;
        public String SN;
    }

    private DeviceModel parseUSBFile(UsbFile wifiConfigurationFile) {
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

            Log.e(TAG, "onClick: wifi =" + wifiName + ",pass =" + pass + ",SN =" + SN);

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

        DeviceModel deviceModel = new DeviceModel();
        deviceModel.wifi = wifiName;
        deviceModel.pass = pass;
        deviceModel.SN = SN;
        return deviceModel;
    }

    private void saveDeviceStyle(int style) {
        if (debug) style = 1;

        VariableInstance.getInstance().deviceStyle = style;
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putInt("devicestyle", style);
        editor.apply();
    }

    private int getDeviceStyle() {
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        int style = sharedPreferences.getInt("devicestyle", 0);

        if (debug) style = 1;

        VariableInstance.getInstance().deviceStyle = style;


        return style;
    }

    private void saveDeviceModel(DeviceModel deviceModel) {
        if (deviceModel == null) return;
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putString("wifiName", deviceModel.wifi);
        editor.putString("wifiPass", deviceModel.pass);
        editor.putString("deviceSN", deviceModel.SN);
        editor.apply();
    }

    private DeviceModel getDeviceModel() {
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        String wifiName = sharedPreferences.getString("wifiName", null);
        String wifiPass = sharedPreferences.getString("wifiPass", null);
        String deviceSN = sharedPreferences.getString("deviceSN", null);

        if (wifiName == null) return null;
        DeviceModel deviceModel = new DeviceModel();
        deviceModel.wifi = wifiName;
        deviceModel.pass = wifiPass;
        deviceModel.SN = deviceSN;
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
            if (data != null && data.length > 0) {
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
        if (VariableInstance.getInstance().isUploadingToRemote || VariableInstance.getInstance().isScanningCamera || VariableInstance.getInstance().isScanningStoreUSB || VariableInstance.getInstance().isDownloadingUSB || !operationUtils.pictureIsThreadStop || VariableInstance.getInstance().initingUSB || isUpdating) {
            canCloseDevice = false;
        } else {
            canCloseDevice = true;
        }
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


    private void wifiEnabledBroadcast() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return;
        DeviceModel deviceModelConnect = getDeviceModel();
        if (deviceModelConnect == null) {
            Log.e(TAG, "onReceive: WIFI_STATE_ENABLED deviceModelConnect = null");
            return;
        }
        if (deviceModelConnect.wifi != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().contains(deviceModelConnect.wifi)) {
                Log.e(TAG, "initStoreUSBComplete: 当前自动链接上WiFi " + wifiInfo.getSSID());
                mHandler.removeMessages(msg_wifi_disconnected);
                mHandler.removeMessages(msg_wifi_connected);
                mHandler.sendEmptyMessageDelayed(msg_wifi_connected, 1000);
                return;
            }
            if (deviceModelConnect.pass == null) {
                connectWifiNoPws(deviceModelConnect.wifi, wifiManager);
            } else {
                if (deviceModelConnect.pass.length() == 0) {
                    connectWifiNoPws(deviceModelConnect.wifi, wifiManager);
                } else {
                    connectWifiPws(deviceModelConnect.wifi, deviceModelConnect.pass, wifiManager);
                }
            }
        } else {
            Log.e(TAG, "WIFI_STATE_ENABLED: 当前配置的WiFi文件有错");
        }

    }

    private class WifiReceiver extends BroadcastReceiver {
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
                            wifiEnabledBroadcast();
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
                    Log.d(TAG, "onReceive: 断开wifi =");
                    mHandler.removeMessages(msg_wifi_disconnected);
                    mHandler.removeMessages(msg_wifi_connected);
                    mHandler.sendEmptyMessageDelayed(msg_wifi_disconnected, 1000);
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        Log.d(TAG, "onReceive: 连接wifi ");
                        mHandler.removeMessages(msg_wifi_disconnected);
                        mHandler.removeMessages(msg_wifi_connected);
                        mHandler.sendEmptyMessageDelayed(msg_wifi_connected, 1000);
                    }
                }
            }
        }
    }


    private static final int msg_reload_device_info = 1;
    private static final int msg_close_device = 2;
    private static final int msg_send_ShutDown = 3;

    private static final int msg_wifi_disconnected = 5;
    private static final int msg_wifi_connected = 6;
    private static final int msg_delay_creta_acitivity = 7;
    private static final int msg_delay_open_device_prot = 8;

    private static class MyHandler extends Handler {
        private WeakReference<MainActivity> weakReference;

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
                case msg_reload_device_info:
                    activity.initAddress();
                    break;
                case msg_close_device:
                    if (activity.canCloseDevice()) {
                        activity.operationUtils.startUploadLocatThread(true);
                    } else {

                        activity.removeCloseDeviceMessage(10);
                        activity.sendCloseDeviceMessage(6);
                    }
                    break;
                case msg_send_ShutDown:
                    activity.sendShutDown = false;
                    Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
                    Utils.resetDir(VariableInstance.getInstance().LogcatDir);
                    Utils.closeAndroid();
                    break;
                case msg_wifi_disconnected:
                    activity.netWorkLost();
                    break;
                case msg_wifi_connected:
                    activity.networkConnect();
                    break;
                case msg_delay_creta_acitivity:
                    activity.delayCreate();
                    break;
                case msg_delay_open_device_prot:
                    activity.openCameraDeviceProt(true);
                    break;

            }
        }
    }
}
