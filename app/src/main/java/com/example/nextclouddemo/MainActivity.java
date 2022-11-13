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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

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
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.owncloud.android.lib.common.OwnCloudClient;
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


public class MainActivity extends Activity {

    private static final String Record1 = "Start,Record1;";
    private static final String Record2 = "Start,Record2;";
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


    private static final int close_device_timeout = 3 * 60 * 1000;
    private static final int close_device_timeout_a = 10 * 60 * 1000;


    private static String TAG = "MainActivitylog";
    private MyHandler mHandler;
    private OwnCloudClient mClient;

    private ScanerUSBReceiver scanerUSBReceiver;
    private StoreUSBReceiver storeUSBReceiver;


    char mGpioCharB = 'b';
    private String returnImei;
    private String deveceName;
    private Communication communication;
    private boolean doingInit;
    private RemoteOperationUtils operationUtils;


    private String messageTextString;

    private CameraHelper mCameraHelper;
    private long lastOpenCameraTime;


    private boolean remoteUploading;
    private boolean localDownling;
    private boolean openDeviceProtFlag;

    private RelativeLayout surfaceViewParent;
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
    private TextView FormatUSBButton;

    private UpdateUtils updateUtils;
    private WifiReceiver mWifiReceiver;
    private int signalStrengthValue;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);

        mHandler = new MyHandler(MainActivity.this);
        communication = new Communication();
        updateUtils = new UpdateUtils(updateListener);
        operationUtils = new RemoteOperationUtils(remoteOperationListener);
        VariableInstance.getInstance().formarCamera = false;
        VariableInstance.getInstance().downdNum = 0;
        VariableInstance.getInstance().uploadNum = 0;
        VariableInstance.getInstance().usbFileNameList.clear();

        initView();

        String[] value = Utils.haveNoPermissions(MainActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }

        mHandler.sendEmptyMessageDelayed(msg_close_device, close_device_timeout);
        EventBus.getDefault().register(this);
        getUploadModel();

        Utils.makeDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.makeDir(VariableInstance.getInstance().TFCardUploadPictureDir);

        openDeviceProt(false);
        openNetworkLed(true);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                registerStoreUSBReceiver();
                openCamera();
            }
        }, 3000);
    }


    @SuppressLint("SetTextI18n")
    private void initView() {
        surfaceViewParent = findViewById(R.id.surfaceViewParent);
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
        FormatUSBButton = findViewById(R.id.FormatUSB);

        AppUtils.AppInfo appInfo = AppUtils.getAppInfo(getPackageName());

        int appVerison = appInfo.getVersionCode();
        currentVersionText.setText("当前版本：" + appVerison);
        Log.d(TAG, "initView: appVerison =" + appVerison);

        serverStateText.setText("服务器状态：false");
        remoteNameText.setText("云端名称：");
        accessNumberText.setText("入网号：");

        accessNumberText.setText("入网号:");
        cameraStateText.setText("相机状态:false");
        isConnectNetworkText.setText("是否连网:false");
        mqttStateText.setText("mqtt状态:false");
    }


    PhoneStateListener MyPhoneListener = new PhoneStateListener() {
        @RequiresApi(api = Build.VERSION_CODES.M)
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
            runOnUiThreadText(updateResultText, "开始升级");
            mHandler.removeMessages(msg_close_device);
            mHandler.removeMessages(msg_send_restart_app);
            mHandler.sendEmptyMessageDelayed(msg_send_restart_app, 3000);
        }

        @Override
        public void updateResult(boolean succeed) {
            mHandler.removeMessages(msg_send_restart_app);
            runOnUiThreadText(updateResultText, "升级" + (succeed ? "成功" : "失败"));
            mHandler.sendEmptyMessageDelayed(msg_close_device, close_device_timeout);
        }
    };

    private StoreUSBReceiver.StoreUSBListener storeUSBListener = new StoreUSBReceiver.StoreUSBListener() {
        @Override
        public void storeUSBPictureCount(int count) {
            UpanPictureCount = count;
            if (count != 0)
                runOnUiThreadText(UpanPictureCountText, "U盘图片总数:" + count);
        }

        @Override
        public void initStoreUSBComplete(UsbFile wifiConfigurationFile) {
            String imei = getPhoneImei(true);
            DeviceModel deviceModelConnect = null;
            if ("0".equals(imei)) {
                if (wifiConfigurationFile == null) {
                    int style = getDeviceStyle(); //0是还不确定是蜂窝板还是WiFi版，1是蜂窝版，2是WiFi版
                    if (style == 0 || style == 1) {
                        saveDeviceStyle(1);
                        //蜂窝板
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
                saveDeviceStyle(1);
                //蜂窝板
            }

            openDeviceProt(true);
            getInfo();

            int capacity = storeUSBReceiver.getStoreUSBCapacity();
            int freeSpace = storeUSBReceiver.getStoreUSBFreeSpace();

            runOnUiThreadText(UpanSpaceText, "U盘空间:" + "\ncapacity:" + capacity + "\nfreeSpace:" + freeSpace);

            if (scanerUSBReceiver == null)
                registerScanerUSBReceiver();

            if (VariableInstance.getInstance().deviceStyle == 1) {
                initCellularNetWork();
            } else if (VariableInstance.getInstance().deviceStyle == 2) {
                registerWifiReceiver();
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager == null) {
                    Log.e(TAG, "initStoreUSBComplete: wifiManager == null");
                } else {
                    boolean wifiEnable = wifiManager.isWifiEnabled();
                    Log.e(TAG, "initStoreUSBComplete: wifiEnable =" + wifiEnable);
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
        public void storeUSBDeviceDetached() {
            if (scanerUSBReceiver != null) {
                scanerUSBReceiver.storeUSBDetached();
                openDeviceProt(false);
            }
        }

        @Override
        public void formatStoreUSBException(boolean exception) {
            runOnUiThreadText(FormatUSBButton, exception ? "格式化USB失败" : "格式化USB成功");
        }

        @Override
        public void storeUSBSaveOnePictureComplete(String speed) {
            runOnUiThreadText(hasDownloadPictureNumberText, "已下载张数:" + VariableInstance.getInstance().downdNum + "\n同步到USB速度:" + speed);
        }

    };


    private ScanerUSBReceiver.ScanerUSBListener scanerUSBListener = new ScanerUSBReceiver.ScanerUSBListener() {
        @Override
        public void addUploadRemoteFile(UploadFileModel uploadFileModel) {
            Log.d(TAG, "downloadOneFileDone: uploading =" + remoteUploading);
            operationUtils.addUploadRemoteFile(uploadFileModel);
        }

        @Override
        public void startDownload() {
            Log.d(TAG, "startDownload: uploading =" + remoteUploading);
            localDownling = true;
            if (!remoteUploading)
                startDownLed(true);
            Log.d(TAG, " remove msg_close_device 444444444444444");
            mHandler.removeMessages(msg_close_device);
        }

        @Override
        public void downloadComplete() {
            Log.d(TAG, "downloadComplete: remoteUploading =" + remoteUploading);
            localDownling = false;
            if (!remoteUploading)
                startDownLed(false);
            openDeviceProt(false);
            Log.d(TAG, " send msg_close_device 55555555555555555");
            mHandler.removeMessages(msg_close_device);
            mHandler.sendEmptyMessageDelayed(msg_close_device, close_device_timeout);
            getInfo();
        }


        @Override
        public void downloadUpanCount(int size) {
            runOnUiThreadText(uploadNumberText, "本次从相机同步到U盘数量:" + size);
        }

        @Override
        public boolean uploadToUSB(File localFile, String yearMonth) {
            if (storeUSBReceiver == null)
                return false;
            return storeUSBReceiver.uploadToUSB(localFile, yearMonth);
        }

        @Override
        public void cameraUSBDetached() {
            storeUSBReceiver.getUSBPictureCount();
        }

        @Override
        public void scanCameraComplete(int pictureCont) {
            Log.e(TAG, "scanCameraComplete: pictureCont =" + pictureCont);
            runOnUiThreadText(cameraPictureCountText, "相机照片总数：" + pictureCont);
        }
    };


    RemoteOperationUtils.RemoteOperationListener remoteOperationListener = new RemoteOperationUtils.RemoteOperationListener() {
        @Override
        public void allFileUploadComplete(long totalTime) {
            Log.d(TAG, "allFileUploadComplete: downling =" + localDownling + ",totalTime =" + totalTime / 1000);
            remoteUploading = false;
            if (!localDownling) {
                restLed();
            }
            if (totalTime != 0)
                UploadUseTime = totalTime / 1000;
            runOnUiThreadText(uploadUseTimeText, "本次同步到服务器耗时:" + totalTime / 1000 + "s");

            getInfo();
            Log.d(TAG, " send msg_close_device 6666666666666666");
            mHandler.removeMessages(msg_close_device);
            mHandler.sendEmptyMessageDelayed(msg_close_device, close_device_timeout);
        }

        @Override
        public void pictureUploadStart() {
            Log.d(TAG, "fileUploadStart: ");
            remoteUploading = true;
            Log.d(TAG, " remove msg_close_device 7777777777777777777");
            mHandler.removeMessages(msg_close_device);
            startDownLed(false);
        }

        @Override
        public void pictureUploadEnd() {
            Log.d(TAG, "fileUploadEnd: ");
            remoteUploading = false;
        }


        @Override
        public void updateUploadSpeed(String speed) {
            Log.d(TAG, "updateUploadSpeed: speed =" + speed);
            saveUploadUploadSpeed(speed);
            runOnUiThreadText(hasUploadpictureNumberText, "已上传张数:" + VariableInstance.getInstance().uploadNum + "\n上传服务器速度：" + speed);
            getInfo();
        }


        @Override
        public void videoUploadStart() {
            Log.d(TAG, "  remove msg_close_device 88888888888888888");
            mHandler.removeMessages(msg_close_device);
            startDownLed(false);
        }

        @Override
        public void uploadVideoComplete(boolean succeed) {
            Log.e(TAG, "uploadVideoComplete: 上传视频结果 " + succeed);
            Log.d(TAG, "  send msg_close_device 9999999999999999");
            mHandler.removeMessages(msg_close_device);
            mHandler.sendEmptyMessageDelayed(msg_close_device, close_device_timeout);
            if (!localDownling && !remoteUploading) {
                restLed();
            }
        }

        @Override
        public void uploadLogcatComplete() {
            getInfo();
            UploadEndUploadUseTime(UploadUseTime);
        }

        @Override
        public boolean isDownling() {
            return localDownling;
        }

        @Override
        public boolean isVideoPreviewing() {
            if (mCameraHelper != null && mCameraHelper.isPreview)
                return true;
            return false;
        }

        @Override
        public void startUploadLogcatToUsb() {
            if (storeUSBReceiver != null)
                storeUSBReceiver.uploadLogcatToUSB();
        }
    };


    private void registerScanerUSBReceiver() {
        scanerUSBReceiver = new ScanerUSBReceiver(getApplicationContext(), scanerUSBListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(ScanerUSBReceiver.CHECK_PERMISSION);
        registerReceiver(scanerUSBReceiver, intentFilter);
    }

    private void registerStoreUSBReceiver() {
        storeUSBReceiver = new StoreUSBReceiver(getApplicationContext(), storeUSBListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(StoreUSBReceiver.INIT_STORE_USB_PERMISSION);
        registerReceiver(storeUSBReceiver, intentFilter);
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
        Log.e(TAG, "initNetWork: ");
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
            }
        });
    }


    private void networkConnect() {
        networkAvailable = true;
        Log.e(TAG, "Network onAvailable: doingInit =" + doingInit);
        runOnUiThreadText(isConnectNetworkText, "是否连网:true");
        if (doingInit)
            return;
        doingInit = true;
        updateUtils.networkAvailable(MainActivity.this);
        initAddress();
    }

    private void netWorkLost() {
        networkAvailable = false;
        runOnUiThreadText(isConnectNetworkText, "是否连网:false");
        runOnUiThreadText(mqttStateText, "mqtt状态:false");
        doingInit = false;
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
                if (textView == null || text == null)
                    return;
                textView.setText(text);
            }
        });
    }


    private boolean networkAvailable;

    private void initAddress() {
        if (!networkAvailable)
            return;

        getInfo();

        Thread workThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerUrlModel serverUrlModel = communication.getServerUrl();
                    Log.e(TAG, "run: serverUrlModel =" + serverUrlModel);
                    if (serverUrlModel == null || serverUrlModel.responseCode != 200) {
                        mHandler.removeMessages(msg_reload_device_info);
                        mHandler.sendEmptyMessageDelayed(msg_reload_device_info, 10000);
                        updateServerStateUI(false);
                        return;
                    }

                    String imei = getPhoneImei(false);
                    if ("0".equals(imei))
                        imei = getPhoneImei(true);
                    DeviceInfoModel deviceInfoModel = communication.getDeviceInfo(imei);

                    if (serverUrlModel == null || deviceInfoModel.responseCode != 200) {
                        mHandler.removeMessages(msg_reload_device_info);
                        mHandler.sendEmptyMessageDelayed(msg_reload_device_info, 10000);
                        updateServerStateUI(false);
                        return;
                    }

                    mHandler.removeMessages(msg_reload_device_info);

                    Log.e(TAG, "run: deviceInfoModel =" + deviceInfoModel);

                    returnImei = deviceInfoModel.returnImei;
                    deveceName = deviceInfoModel.deveceName;
                    EventBus.getDefault().post(return2GImei);

                    mClient = OwnCloudClientFactory.createOwnCloudClient(serverUrlModel.serverUri, MainActivity.this, true);
                    mClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(deviceInfoModel.username, deviceInfoModel.password));
                    operationUtils.mClient = mClient;

                    operationUtils.connectRemote = operationUtils.initRemoteDir(deviceInfoModel.deveceName);
                    updateServerStateUI(operationUtils.connectRemote);
                    if (operationUtils.connectRemote) {
                        Log.d(TAG, " send msg_close_device 3333333333");
                        mHandler.removeMessages(msg_close_device);
                        mHandler.removeMessages(msg_reload_device_info);
                        operationUtils.startUploadThread();
                        operationUtils.startVideoWorkThread();
                    } else {
                        mHandler.removeMessages(msg_reload_device_info);
                        mHandler.sendEmptyMessageDelayed(msg_reload_device_info, 10000);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "run:Exception111 = " + e);
                    updateServerStateUI(false);
                    doingInit = false;
                }
            }
        });
        workThread.start();
    }


    private void initMqtt() {
        MqttManager
                .getInstance()
                .creatConnect(
                        "tcp://120.78.192.66:1883",
                        "devices",
                        "a1237891379",
                        "123",
                        "/camera/v1/device/" + returnImei + "/android");

        MqttManager.getInstance().subscribe("/camera/v2/device/" + returnImei + "/android/send", 1);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scanerUSBReceiver != null) {
            unregisterReceiver(scanerUSBReceiver);
        }

        if (storeUSBReceiver != null) {
            unregisterReceiver(storeUSBReceiver);
        }
        if (mWifiReceiver != null) {
            unregisterReceiver(mWifiReceiver);
        }

        mHandler.removeCallbacksAndMessages(null);
        closeCamera();
        openDeviceProt(false);
        operationUtils.stopUploadThread();
        operationUtils.stopUploadVideoThread();
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_NONE);
        EventBus.getDefault().unregister(this);
        MqttManager.getInstance().release();
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
        Log.e(TAG, "receiveMqttMessage: message =" + message);
        if (message == null)
            return;

        mqttStateText.setText("mqtt状态:true");
        if (message.contains(UploadMode3)) {
            UploadMode3(message);
            getInfo();
            sendMessageToMqtt("ZQ\r\n");
            return;
        }

        if (message.contains(UploadMode4)) {
            UploadMode4(message);
            getInfo();
            sendMessageToMqtt("ZQ\r\n");
            return;
        }

        switch (message) {
            case Record1:
                openCamera();
                sendMessageToMqtt("ZQ\r\n");
                break;
            case Record2:
                openCamera();
                sendMessageToMqtt("ZQ\r\n");
                break;
            case FormatUSB:
                formatUSB();
                sendMessageToMqtt("ZQ\r\n");
                break;
            case FormatTF:
                formatUSB();
                sendMessageToMqtt("ZQ\r\n");
                break;
            case FormatCamera:
                formatCamera();
                sendMessageToMqtt("ZQ\r\n");
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

            case "connectionLost":
                mqttStateText.setText("mqtt状态:false");
                break;
            case "deliveryComplete":
                mqttStateText.setText("mqtt状态:true");
                break;
        }
    }


    private boolean sendShutDown;

    private void UploadEndUploadUseTime(long useTime) {
        sendShutDown = true;
        mHandler.removeMessages(msg_send_ShutDown);
        mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, close_device_timeout_a);
        sendMessageToMqtt(UploadEndUploadUseTime + useTime + ";");
    }

    private void AppShutdownAck() {
        if (sendShutDown) {
            mHandler.removeMessages(msg_send_ShutDown);
            mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, 5000);
            sendShutDown = false;
        }
    }

    private boolean formatingUSB;

    private void formatUSB() {
        if (formatingUSB)
            return;
        formatingUSB = true;
        Log.e(TAG, "formatUSB: ");
        runOnUiThreadText(FormatUSBButton, "开始删除USB图片");
        new Thread(new Runnable() {
            @Override
            public void run() {

                Utils.resetDir(VariableInstance.getInstance().TFCardPictureDir);
                Utils.resetDir(VariableInstance.getInstance().TFCardUploadPictureDir);
                Utils.resetDir(VariableInstance.getInstance().TFCardVideoDir);
                Utils.resetDir(VariableInstance.getInstance().LogcatDir);

                if (storeUSBReceiver != null)
                    storeUSBReceiver.formatStoreUSB();

                if (scanerUSBReceiver != null) {
                    scanerUSBReceiver.storeUSBDetached();
                }
                formatingUSB = false;
                mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, close_device_timeout_a);
            }
        }).start();
    }


    private boolean formatingCamera;

    private void formatCamera() {
        if (formatingCamera)
            return;
        formatingCamera = true;
        Log.e(TAG, "formatCamera: ");
        new Thread(new Runnable() {
            @Override
            public void run() {
                VariableInstance.getInstance().formarCamera = true;

                if (scanerUSBReceiver != null) {
                    scanerUSBReceiver.formatCamera();
                }
                formatingCamera = false;
                mHandler.sendEmptyMessageDelayed(msg_send_ShutDown, close_device_timeout_a);

                openDeviceProt(false);
                openDeviceProt(true);
            }
        }).start();
    }


    private void getInfo() {
        sendMessageToMqtt(serverGetInfo());
    }


    private void sendMessageToMqtt(String message) {
        Log.d(TAG, "sendMessageToMqtt: message =" + message);
        MqttManager.getInstance().publish("/camera/v2/device/" + returnImei + "/android/receive", 1, message);
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

    @SuppressLint("SetTextI18n")
    private void UploadMode3(String message) {
        message = message.substring(UploadMode3.length(), message.length() - 1);
        String[] data = message.split(",");
        VariableInstance.getInstance().UploadMode = 3;

        VariableInstance.getInstance().uploadSelectIndexList.clear();
        if (data != null && data.length > 0) {
            for (String datum : data) {
                try {
                    VariableInstance.getInstance().uploadSelectIndexList.add(Integer.parseInt(datum));
                } catch (Exception e) {

                }
            }
        }
        saveUploadModel(message);
        uploadModelText.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }


    @SuppressLint("SetTextI18n")
    private void UploadMode4(String message) {
        message = message.substring(UploadMode4.length(), message.length() - 1);
        String[] data = message.split(",");
        VariableInstance.getInstance().UploadMode = 4;

        VariableInstance.getInstance().uploadSelectIndexList.clear();
        if (data != null && data.length > 0) {
            for (String datum : data) {
                try {
                    VariableInstance.getInstance().uploadSelectIndexList.add(Integer.parseInt(datum));
                } catch (Exception e) {

                }
            }
        }
        saveUploadModel(message);
        uploadModelText.setText("上传的模式：" + VariableInstance.getInstance().UploadMode);
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera: ");
        if (mCameraHelper != null)
            mCameraHelper.closeCamera();
        mCameraHelper = null;
        surfaceViewParent.removeAllViews();
    }

    private void openCamera() {
        if (System.currentTimeMillis() - lastOpenCameraTime < 2000) {
            Log.d(TAG, "openCamera: 点击录制过于频繁");
            return;
        }
        lastOpenCameraTime = System.currentTimeMillis();

        closeCamera();

        mCameraHelper = new CameraHelper(new CameraHelper.CameraHelperListener() {
            @Override
            public void addPicture(String path) {
                operationUtils.addVideoFile(path);
            }

            @Override
            public void finishPreview() {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        closeCamera();
                    }
                }, 100);
            }
        });

        surfaceViewParent.removeAllViews();
        SurfaceView surfaceView = new SurfaceView(MainActivity.this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        surfaceView.setLayoutParams(layoutParams);
        surfaceViewParent.addView(surfaceView);
        SurfaceHolder mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);


        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                if (mCameraHelper != null) {
                    mCameraHelper.openCamera(holder, MainActivity.this);
                    mCameraHelper.onStartPreview(width, height);
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            }
        });
    }


    @SuppressLint("SetTextI18n")
    public void onClickHandler(View button) {
        if (button.getId() == R.id.rescaner) {
            openDeviceProt(false);
            openDeviceProt(true);
        } else if (button.getId() == R.id.guanji) {
            Utils.closeAndroid();
        } else if (button.getId() == R.id.aaa) {
            startActivity(new Intent(MainActivity.this, GpioActivity.class));
        } else if (button.getId() == R.id.openPreview) {
            openCamera();
        } else if (button.getId() == R.id.closePreview) {
            closeCamera();
        } else if (button.getId() == R.id.clearView) {
            messageTextString = "";
            messageText.setText(messageTextString);
        } else if (button.getId() == R.id.remoteNameText) {
            remoteNameText.setText("云端名称:" + deveceName);
        } else if (button.getId() == R.id.uploadNumberText) {
            uploadNumberText.setText("本次从相机同步到U盘数量:");
        } else if (button.getId() == R.id.cameraStateText) {
            cameraStateText.setText("相机状态:" + openDeviceProtFlag);
        } else if (button.getId() == R.id.FormatUSB) {
            formatUSB();
        } else if (button.getId() == R.id.FormatCamera) {
            formatCamera();
        }
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

    private String serverGetInfo() {
        int capacityA = 0;
        int freeSpaceA = 0;
        int PhotoSumA = 0;

        if (storeUSBReceiver != null) {
            capacityA = storeUSBReceiver.getStoreUSBCapacity();
            if (capacityA != 0)
                capacity = capacityA;
            freeSpaceA = storeUSBReceiver.getStoreUSBFreeSpace();
            if (freeSpaceA != 0)
                freeSpace = freeSpaceA;
            PhotoSumA = storeUSBReceiver.getUSBPictureCount();
            if (PhotoSumA != 0)
                UpanPictureCount = PhotoSumA;
        }

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

        String info = "4gCcid," + getPhoneNumber() +
                ";UploadSpeed," + getUploadploadSpeed() +
                ";4gCsq," + getSignalStrength() +
                ";SdFree," + freeSpace +
                ";SdFull," + capacity +
                ";PhotoSum," + UpanPictureCount +
                ";PhotoUploadThisTime," + VariableInstance.getInstance().uploadNum +
                ";UploadMode," + uploadModelString +
                ";UploadUseTime," + UploadUseTime + ";";
        Log.e(TAG, "serverGetInfo: info =" + info);
        return info;
    }

    private String getSignalStrength() {
        if (signalStrengthValue > 0)
            return "0";
        else if (signalStrengthValue > -55)
            return "4";
        else if (signalStrengthValue > -70)
            return "3";
        else if (signalStrengthValue > -85)
            return "2";
        else if (signalStrengthValue > -100)
            return "1";
        else
            return "1";
    }

    private String getPhoneNumber() {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            @SuppressLint({"HardwareIds", "MissingPermission"}) String number = telephonyManager.getLine1Number();
            Log.d(TAG, "getPhoneNumber: number =" + number);
            if (number == null) {
                number = "0";
            }
            return number;
        } catch (Exception | Error e) {
            Log.e(TAG, "getPhoneNumber: Exception =" + e);
            return "0";
        }
    }

    @SuppressLint("HardwareIds")
    private String getPhoneImei(boolean init) {
        Log.e(TAG, "getPhoneImei: init =" + init);
        String imei = "0";
//      imei = "867706050952138";
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

        if (!"0".equals(imei)) {
            runOnUiThreadText(accessNumberText, "入网号:" + imei);
        }
        return imei;
    }


    private void openDeviceProt(boolean open) {
        Log.d(TAG, "openDeviceProt: 设备通信端口 led: " + (open ? "打开" : "关闭") + ", 当前状态" + (openDeviceProtFlag ? "打开" : "关闭"));
        if (openDeviceProtFlag == true && open)
            return;
        openDeviceProtFlag = open;
        runOnUiThreadText(cameraStateText, "相机状态:" + openDeviceProtFlag);


        if (open) {
            LedControl.writeGpio(mGpioCharB, 2, 1);
        } else {
            LedControl.writeGpio(mGpioCharB, 2, 0);
        }
    }

    private void openNetworkLed(boolean open) {
        Log.d(TAG, "openNetworkLed: 网络端口 led: " + (open ? "打开" : "关闭"));

        if (open) {
            LedControl.writeGpio(mGpioCharB, 3, 1);//打开网络
        } else {
            LedControl.writeGpio(mGpioCharB, 3, 0);//打开网络
        }
    }

    private void startDownLed(boolean start) {
        Log.d(TAG, "startDownLed: 下载 led: " + (start ? "打开" : "关闭"));

        if (start) {
            LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_TIMER);
        } else {
            LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_HEARTBEAT);
        }
    }

    private void restLed() {
        Log.d(TAG, "restLed:  恢复 led ------");

        LedControl.nativeEnableLed(LedControl.LED_RED_TRIGGER_PATH, LedControl.LED_NONE);
        LedControl.nativeEnableLed(LedControl.LED_RED_BRIGHTNESS_PATH, LedControl.LED_OFF);
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

            if (data == null)
                return null;
            for (String datum : data) {
                if (datum == null)
                    continue;
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
        if (wifiName == null || SN == null)
            return null;

        DeviceModel deviceModel = new DeviceModel();
        deviceModel.wifi = wifiName;
        deviceModel.pass = pass;
        deviceModel.SN = SN;
        return deviceModel;
    }

    private void saveDeviceStyle(int style) {
        VariableInstance.getInstance().deviceStyle = style;
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putInt("devicestyle", style);
        editor.apply();
    }

    private int getDeviceStyle() {
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        int style = sharedPreferences.getInt("devicestyle", 0);
        VariableInstance.getInstance().deviceStyle = style;
        return style;
    }

    private void saveDeviceModel(DeviceModel deviceModel) {
        if (deviceModel == null)
            return;
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

        if (wifiName == null)
            return null;
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

    private void saveUploadUploadSpeed(String uploadSpeed) {
        SharedPreferences.Editor editor = getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        editor.putString("uploadSpeed", uploadSpeed);
        editor.apply();
    }

    private String getUploadploadSpeed() {
        SharedPreferences sharedPreferences = getSharedPreferences("Cloud", MODE_PRIVATE);
        String uploadSpeed = sharedPreferences.getString("uploadSpeed", "0");
        return uploadSpeed;
    }

    private boolean canCloseDevice() {
        boolean canCloseDevice;
        if (remoteUploading || localDownling || !operationUtils.pictureIsThreadStop)
            canCloseDevice = false;
        else
            canCloseDevice = true;
        Log.e(TAG, "canCloseDevice: canCloseDevice =" + canCloseDevice);
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
        if (wifiManager == null)
            return;
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
    private static final int msg_send_restart_app = 4;

    private static final int msg_wifi_disconnected = 5;
    private static final int msg_wifi_connected = 6;


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
                        activity.operationUtils.startUploadLocatThread();
                    } else {
                        Log.d(TAG, "  send msg_close_device 11111111");
                        activity.mHandler.removeMessages(msg_close_device);
                        activity.mHandler.sendEmptyMessageDelayed(msg_close_device, close_device_timeout);
                    }
                    break;
                case msg_send_ShutDown:
                    activity.sendShutDown = false;
                    Utils.closeAndroid();
                    break;
                case msg_send_restart_app:
                    activity.updateUtils.execLinuxCommand();
                    break;
                case msg_wifi_disconnected:
                    activity.netWorkLost();
                    break;
                case msg_wifi_connected:
                    activity.networkConnect();
                    break;

            }
        }
    }
}
