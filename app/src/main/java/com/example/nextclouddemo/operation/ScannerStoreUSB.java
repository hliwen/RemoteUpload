package com.example.nextclouddemo.operation;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;

import com.example.nextclouddemo.MyApplication;
import com.example.nextclouddemo.model.DeviceMemory;
import com.example.nextclouddemo.model.ProfileModel;
import com.example.nextclouddemo.VariableInstance;
import com.example.nextclouddemo.utils.DeviceUtils;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.PictureDateInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;
import me.jahnen.libaums.core.fs.UsbFileOutputStream;
import me.jahnen.libaums.core.partition.Partition;

public class ScannerStoreUSB extends BroadcastReceiver {
    private static final String TAG = "remotelog_ReceiverStoreUSB";

    private final DeviceScannerListener deviceScannerListener;
    private ExecutorService scannerThreadExecutor;
    private UsbManager usbManager;
    private FileSystem storeUSBFileSystem;
    private UsbFile storeUSBLogcatDirUsbFile;
    private UsbFile storeUSBPictureDirUsbFile;

    public String errorMessage = "";
    public UsbDevice operatingDevice;
    public boolean completedScanner = false;
    public boolean isOperatingDevice = false;
    public boolean uploadingLogcat = false;
    public boolean uploadingPicture = false;

    private ProfileModel profileModel;
    public int backupUSBCompletePictureNum = 0;//当次已经从相机同步到U盘的张数
    public int deviceTotalPicture = 0;
    private MyHandler mHandler;


    private static class MyHandler extends Handler {
        private final WeakReference<ScannerStoreUSB> weakReference;

        MyHandler(ScannerStoreUSB object) {
            weakReference = new WeakReference<>(object);
        }

        @Override
        public void handleMessage(Message msg) {
            ScannerStoreUSB device = weakReference.get();
            if (device == null || device.mHandler == null) {
                return;
            }
            switch (msg.what) {

            }
        }
    }


    public ScannerStoreUSB(Context context, DeviceScannerListener storeUSBListener) {
        errorMessage = "";
        mHandler = new MyHandler(ScannerStoreUSB.this);
        this.deviceScannerListener = storeUSBListener;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        if (usbManager == null) {
            errorMessage = "系统异常 usbManager==null";
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                Log.d(TAG, "ReceiverStoreUSB onReceive: ACTION_USB_DEVICE_ATTACHED");
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverStoreUSB onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (DeviceUtils.isStroreUSBDevice(usbDevice.getProductName())) {
                    startScannerDevice();
                }
            }
            break;

            case UsbManager.ACTION_USB_DEVICE_DETACHED: {
                Log.e(TAG, "ReceiverStoreUSB ACTION_USB_DEVICE_DETACHED");
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (operatingDevice != null && usbDevice.equals(operatingDevice)) {
                    detachedDevice(true);
                }
            }
            break;
            default:
                break;
        }
    }

    public void detachedDevice(boolean broadcast) {//TODO
        Log.d(TAG, "U盘 detachedDevice: broadcast =" + broadcast);
        completedScanner = false;
        if (isOperatingDevice) {
            errorMessage = "detachedDevice 设备断开";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false, null);
        }
        if (operatingDevice != null) {
            try {
                UsbMassStorageDevice usbMassStorageDevice = getUsbMass(operatingDevice);
                if (usbMassStorageDevice != null) {
                    usbMassStorageDevice.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "usbDissConnect 设备断开异常 e =" + e);
            }
        }
        operatingDevice = null;
        isOperatingDevice = false;
        storeUSBFileSystem = null;
        storeUSBLogcatDirUsbFile = null;
        storeUSBPictureDirUsbFile = null;
        deviceTotalPicture = 0;

        try {
            if (scannerThreadExecutor != null) {
                scannerThreadExecutor.shutdownNow();
            }
        } catch (Exception e) {
        }
        scannerThreadExecutor = null;
    }

    public void startScannerDevice() {
        detachedDevice(false);
        deviceTotalPicture = 0;
        isOperatingDevice = true;
        deviceScannerListener.startScannerDevice();


        if (usbManager == null) {
            Log.e(TAG, "startScannerDevice: 系统异常 usbManager==null");
            errorMessage = "系统异常 usbManager==null";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false, null);
            return;
        }

        HashMap<String, UsbDevice> deviceMap = usbManager.getDeviceList();
        Collection<UsbDevice> usbDeviceList = null;
        if (deviceMap != null) {
            usbDeviceList = deviceMap.values();
        }
        if (deviceMap == null || deviceMap.size() <= 0 || usbDeviceList == null) {
            errorMessage = "没有检测到有设备列表";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false, null);
            return;
        }

        for (UsbDevice usbDevice : usbDeviceList) {
            if (usbDevice == null) {
                continue;
            }

            String productName = usbDevice.getProductName();
            Log.e(TAG, "startScannerDevice: 当前设备名称：" + productName);
            if (productName == null || !DeviceUtils.isStroreUSBDevice(productName)) {
                continue;
            }
            operatingDevice = usbDevice;
        }

        if (operatingDevice == null) {
            Log.e(TAG, "startScannerDevice:  没有检测到有U盘设备");
            errorMessage = "没有检测到有U盘设备";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false, null);
            return;
        }

        scannerThreadExecutor = Executors.newSingleThreadExecutor();
        scannerThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {

                int count = 0;
                while (VariableInstance.getInstance().serverApkInitingUSB && count < 20) {
                    Log.e(TAG, "startScannerDevice: 守护apk 正在操作U盘,等待3S");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {

                    }
                    deviceScannerListener.checkServerUSBOperation();
                    count++;
                }
                Log.e(TAG, "startScannerDevice: 等待守护apk操作U盘次数 count =" + count);
                if (!isOperatingDevice) {
                    Log.e(TAG, "startScannerDevice:  111 等待守护apk操作U盘异常");
                    errorMessage = "等待守护apk操作U盘异常1";
                    deviceScannerListener.endScannerDevice(false, null);
                    return;
                }

                int requestPermissionCount = 0;
                while (isOperatingDevice && !usbManager.hasPermission(operatingDevice) && requestPermissionCount < 10) {
                    requestPermissionCount++;
                    try {
                        Thread.sleep(2000);
                        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(VariableInstance.GET_DEVICE_PERMISSION), 0);
                        usbManager.requestPermission(operatingDevice, pendingIntent);
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                }

                if (!isOperatingDevice) {
                    Log.e(TAG, "startScannerDevice:  等待U盘授权异常");
                    errorMessage = "等待U盘授权异常";
                    isOperatingDevice = false;
                    deviceScannerListener.endScannerDevice(false, null);
                    return;
                }

                if (usbManager.hasPermission(operatingDevice)) {
                    Log.e(TAG, "startScannerDevice: 授权完成，真正开始操作U盘");
                    UsbMassStorageDevice usbMassStorageDevice = getUsbMass(operatingDevice);
                    if (usbMassStorageDevice == null) {
                        Log.e(TAG, "usbDeviceScanner: USBDevice 转换成UsbMassStorageDevice 对象 失败");
                        errorMessage = "USBDevice 转换成UsbMassStorageDevice 对象 失败";
                        isOperatingDevice = false;
                        deviceScannerListener.endScannerDevice(false, null);
                        return;
                    }

                    try {
                        usbMassStorageDevice.init();
                    } catch (Exception e) {
                        Log.e(TAG, "startScannerDevice : usbMassStorageDevice.init 设备初始化错误 " + e);
                        errorMessage = "usbMassStorageDevice.init 设备初始化错误:" + e;
                        isOperatingDevice = false;
                        deviceScannerListener.endScannerDevice(false, null);
                        return;
                    }

                    if (usbMassStorageDevice.getPartitions().size() <= 0) {
                        Log.e(TAG, "startScannerDevice: " + "device.getPartitions().size() error, 无法获取到设备分区");
                        errorMessage = "device.getPartitions().size() error, 无法获取到设备分区";
                        isOperatingDevice = false;
                        deviceScannerListener.endScannerDevice(false, null);
                        return;
                    }


                    Partition partition = usbMassStorageDevice.getPartitions().get(0);
                    FileSystem fileSystem = partition.getFileSystem();
                    UsbFile usbRootFolder = fileSystem.getRootDirectory();
                    storeUSBFileSystem = fileSystem;

                    UsbFile[] usbFileList = null;
                    try {
                        usbFileList = usbRootFolder.listFiles();
                    } catch (Exception e) {
                        Log.e(TAG, "startScannerDevice: " + "usbRootFolder.listFiles() error:" + e);
                        errorMessage = "usbRootFolder.listFiles() error:" + e;
                        isOperatingDevice = false;
                        deviceScannerListener.endScannerDevice(false, null);
                        return;
                    }

                    if (usbFileList != null) {
                        for (UsbFile usbFileItem : usbFileList) {
                            if (usbFileItem.getName().contains(VariableInstance.getInstance().PictureDirName)) {
                                storeUSBPictureDirUsbFile = usbFileItem;
                            } else if (usbFileItem.getName().contains(VariableInstance.getInstance().LogcatDirName)) {
                                storeUSBLogcatDirUsbFile = usbFileItem;
                            } else if (usbFileItem.getName().contains(VariableInstance.getInstance().wifiConfigurationFileName)) {
                                profileModel = parseUSBFile(usbFileItem);
                            }
                        }
                    }

                    if (storeUSBPictureDirUsbFile == null) {
                        try {
                            storeUSBPictureDirUsbFile = usbRootFolder.createDirectory(VariableInstance.getInstance().PictureDirName);
                        } catch (Exception e) {
                            Log.e(TAG, "run: 创建U盘存储照片目录异常 error:" + e);
                            errorMessage = "创建U盘存储照片目录异常 error:" + e;
                            isOperatingDevice = false;
                            deviceScannerListener.endScannerDevice(false, null);
                            return;
                        }
                    }

                    if (storeUSBPictureDirUsbFile == null) {
                        Log.e(TAG, "usbConnect: 备份USB无法创建存储照片的目录");
                        errorMessage = "usbConnect: 备份USB无法创建存储照片的目录";
                        isOperatingDevice = false;
                        deviceScannerListener.endScannerDevice(false, null);
                        return;
                    }

                    if (storeUSBLogcatDirUsbFile == null) {
                        try {
                            storeUSBLogcatDirUsbFile = usbRootFolder.createDirectory(VariableInstance.getInstance().LogcatDirName);
                        } catch (Exception e) {
                            Log.e(TAG, "创建U盘存储日志目录异常 error:" + e);
                        }
                    }

                    if (!LocalProfileHelp.getInstance().initLocalUSBPictureList()) {
                        LocalProfileHelp.getInstance().createLocalUSBPictureList(storeUSBPictureDirUsbFile);
                    }


                    List<String> picturePathList = Collections.synchronizedList(new ArrayList<>());
                    UsbFile[] dateFileList = null;
                    try {
                        dateFileList = storeUSBPictureDirUsbFile.listFiles();
                    } catch (Exception e) {

                    }
                    if (dateFileList != null && dateFileList.length > 0) {
                        for (UsbFile dateUsbFile : dateFileList) {
                            if (dateUsbFile.isDirectory()) {
                                UsbFile[] pictureFileList = null;
                                try {
                                    pictureFileList = dateUsbFile.listFiles();
                                } catch (Exception e) {

                                }

                                if (pictureFileList != null && pictureFileList.length > 0) {
                                    for (UsbFile pictureUsbFile : pictureFileList) {
                                        try {
                                            String name = pictureUsbFile.getName();
                                            if (DeviceUtils.fileIsPicture(name)) {
                                                if (pictureUsbFile.getLength() > 10) {
                                                    picturePathList.add(name);
                                                    if (!LocalProfileHelp.getInstance().usbPictureList.contains(name)) {
                                                        LocalProfileHelp.getInstance().addLocalUSBPictureList(name);
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {

                                        }
                                    }
                                }
                            }
                        }
                    }

                    deviceTotalPicture = picturePathList.size();
                    Log.e(TAG, "intiBackupUSBPictureList: U盘当前照片张数：" + deviceTotalPicture);

                    if (VariableInstance.getInstance().cyclicDeletion && deviceTotalPicture > VariableInstance.getInstance().MAX_NUM) {
                        Collections.sort(picturePathList, new NewToOldComparator());
                        List<String> delectList = picturePathList.subList(VariableInstance.getInstance().MAX_NUM, picturePathList.size());
                        cyclicDeletionPicture(delectList);
                    }
                    isOperatingDevice = false;
                    completedScanner = true;
                    deviceScannerListener.endScannerDevice(true, profileModel);
                } else {
                    Log.e(TAG, "startScannerDevice: 授权失败");
                    errorMessage = "多次授权失败";
                    isOperatingDevice = false;
                    deviceScannerListener.endScannerDevice(false, null);
                }
            }
        });
    }

    private ProfileModel parseUSBFile(UsbFile wifiConfigurationFile) {
        if (wifiConfigurationFile == null) {
            return null;
        }
        Log.e(TAG, "parseUSBFile: 找到配置文件");
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
        if (wifiName == null || SN == null)
            return null;

        ProfileModel deviceModel = new ProfileModel();
        deviceModel.wifi = wifiName;
        deviceModel.pass = pass;
        deviceModel.SN = SN;
        return deviceModel;
    }

    public class NewToOldComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o2.compareTo(o1);
        }
    }

    public void cyclicDeletionPicture(List<String> delectList) {
        if (isOperatingDevice && storeUSBPictureDirUsbFile != null && delectList != null && delectList.size() > 0) {
            try {
                UsbFile[] dateFileList = storeUSBPictureDirUsbFile.listFiles();
                for (UsbFile dateUsbFile : dateFileList) {
                    if (!isOperatingDevice) {
                        return;
                    }
                    if (dateUsbFile.isDirectory()) {
                        UsbFile[] pictureFileList = dateUsbFile.listFiles();
                        for (UsbFile pictureUsbFile : pictureFileList) {
                            if (!isOperatingDevice) {
                                return;
                            }
                            String name = pictureUsbFile.getName();
                            if (name != null && delectList.contains(name)) {
                                usbFileDelete(pictureUsbFile);
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    private void usbFileDelete(UsbFile usbFile) {
        if (usbFile == null) {
            return;
        }

        try {
            long length = usbFile.getLength();
            if (length > 0) {
                Log.e(TAG, "usbFileDelete: usbFile =" + usbFile.getName() + ",length =" + length);
                usbFile.delete();
            } else {
                Log.e(TAG, "usbFileDelete: 错误文件:" + usbFile.getName());
            }

        } catch (Throwable e) {
            Log.e(TAG, "usbFileDelete: Exception =" + e);
            try {
                usbFile.close();
            } catch (Exception ex) {

            }
        }
    }


    public DeviceMemory getDeviceMemory() {
        DeviceMemory deviceMemory = new DeviceMemory();
        if (storeUSBFileSystem == null) {
            Log.e(TAG, "getDeviceMemory: U盘未初始化");
            return deviceMemory;
        }
        deviceMemory.capacity = (int) (storeUSBFileSystem.getCapacity() / (1024 * 1024));
        deviceMemory.freeSpace = (int) (storeUSBFileSystem.getFreeSpace() / (1024 * 1024));
        return deviceMemory;
    }


    public void uploadLogcatToUSB() {
        Log.e(TAG, "uploadLogcatToUSB start ...........................");
        if (storeUSBLogcatDirUsbFile == null) {
            Log.e(TAG, "uploadLogcatToUSB: 出错，U盘未初始化");
            return;
        }

        String logcatFilePath = LogcatHelper.getInstance().getMainLogcatPath();
        if (logcatFilePath == null) {
            Log.e(TAG, "uploadLogcatToUSB: 出错，日志文件路径不存在");
            return;
        }
        File logcatFile = new File(logcatFilePath);

        if (logcatFile == null || !logcatFile.exists()) {
            return;
        }
        uploadingLogcat = true;
        UsbFileOutputStream usbFileOutputStream = null;
        InputStream inputStream = null;

        try {
            String name = logcatFile.getName();
            UsbFile create = storeUSBLogcatDirUsbFile.createFile(name);
            usbFileOutputStream = new UsbFileOutputStream(create);
            inputStream = new FileInputStream(logcatFile);

            int bytesRead;
            byte[] buffer = new byte[storeUSBFileSystem.getChunkSize()];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                usbFileOutputStream.write(buffer, 0, bytesRead);
            }
            create.close();
        } catch (Exception e) {
            Log.e(TAG, "uploadLogcatToUSB: 1Exception =" + e);
        } finally {
            try {
                if (usbFileOutputStream != null) {
                    usbFileOutputStream.flush();
                    usbFileOutputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadLogcatToUSB: 2Exception =" + e);
            }
        }
        uploadingLogcat = false;
    }

    public boolean uploadToUSB(String picturePath) {

        if (picturePath == null) {
            Log.e(TAG, "uploadToUSB: picturePath==null");
            return false;
        }

        File pictureFile = new File(picturePath);

        if (pictureFile == null || !pictureFile.exists() || pictureFile.length() < 10) {
            Log.e(TAG, "uploadToUSB: 上传文件到U盘出错，pictureFile =" + pictureFile);
            return false;
        }

        if (storeUSBPictureDirUsbFile == null || storeUSBFileSystem == null) {
            Log.e(TAG, "uploadToUSB: 上传文件到U盘出错，U盘未初始化");
            return false;
        }


        Log.d(TAG, "uploadToUSB: pictureFile =" + pictureFile);
        String logcalFileName = pictureFile.getName();
        PictureDateInfo pictureDataInfo = new PictureDateInfo(logcalFileName);

        uploadingPicture = true;
        long time = System.currentTimeMillis();
        long fileSize = 0;
        UsbFileOutputStream usbFileOutputStream = null;
        InputStream inputStream = null;
        try {
            UsbFile yearMonthUsbFile = null;
            UsbFile[] usbFileList = storeUSBPictureDirUsbFile.listFiles();
            if (usbFileList != null) {
                for (UsbFile usbFile : usbFileList) {
                    if (usbFile.getName().contains(pictureDataInfo.yyyyMM)) {
                        yearMonthUsbFile = usbFile;
                        break;
                    }
                }
            }

            if (yearMonthUsbFile == null) {
                yearMonthUsbFile = storeUSBPictureDirUsbFile.createDirectory(pictureDataInfo.yyyyMM);
            }


            UsbFile create = yearMonthUsbFile.search(pictureDataInfo.showName);
            if (create == null) {
                create = yearMonthUsbFile.createFile(pictureDataInfo.showName);
            } else {
                Log.e(TAG, "uploadToUSB: U盘已存在这个文件，size=" + create.getLength());
                create.setLength(pictureFile.length());
            }

            Log.d(TAG, "uploadToUSB.................................: name =" + pictureDataInfo.showName);
            usbFileOutputStream = new UsbFileOutputStream(create);
            inputStream = new FileInputStream(pictureFile);
            fileSize = inputStream.available();
            int bytesRead;
            byte[] buffer = new byte[storeUSBFileSystem.getChunkSize()];


            while ((bytesRead = inputStream.read(buffer)) != -1) {
                usbFileOutputStream.write(buffer, 0, bytesRead);
            }
            backupUSBCompletePictureNum++;
            deviceTotalPicture++;
            LocalProfileHelp.getInstance().addLocalUSBPictureList(pictureDataInfo.showName);
            long totalTime = ((System.currentTimeMillis() - time) / 1000);
            if (totalTime == 0) {
                totalTime = 1;
            }

            String speed = fileSize / totalTime / 1024 + "";
            Log.d(TAG, "uploadToUSB: 上传USB速度：speed =" + speed + ",fileSize =" + fileSize);
            if (deviceScannerListener != null) {
                deviceScannerListener.buckupOnePictureComplete(speed);
            }
            create.close();
        } catch (Exception e) {
            if (e.toString().contains("Item already exists")) {
                Log.d(TAG, "uploadToUSB: U盘已存在同名文件");
            } else {
                Log.e(TAG, "uploadToUSB: 上传U盘出错 Exception =" + e);

            }
        } finally {
            try {
                if (usbFileOutputStream != null) {
                    usbFileOutputStream.flush();
                    usbFileOutputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception | OutOfMemoryError e) {
            }
        }

        uploadingPicture = false;
        Log.d(TAG, "uploadToUSB:完成 ");
        return true;
    }


    private UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(MyApplication.getContext());
        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                return device;
            }
        }
        return null;
    }

    public interface DeviceScannerListener {

        void buckupOnePictureComplete(String speed);

        void checkServerUSBOperation();

        void startScannerDevice();

        void endScannerDevice(boolean isSuccess, ProfileModel profileModel);

    }
}

