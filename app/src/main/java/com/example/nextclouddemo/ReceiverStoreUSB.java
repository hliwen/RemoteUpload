package com.example.nextclouddemo;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.example.nextclouddemo.model.UploadFileModel;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.RemoteOperationUtils;
import com.example.nextclouddemo.utils.Utils;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileOutputStream;
import me.jahnen.libaums.core.partition.Partition;

public class ReceiverStoreUSB extends BroadcastReceiver {
    private static final String TAG = "MainActivitylog2";
    public static final String INIT_STORE_USB_PERMISSION = "INIT_STORE_USB_PERMISSION";
    private final StoreUSBListener storeUSBListener;
    private ExecutorService initStoreUSBThreadExecutor;
    private UsbManager usbManager;
    private boolean formatException = false;
    private FileSystem storeUSBFs;
    private UsbFile storeUSBLogcatDirUsbFile;
    private UsbFile storeUSBPictureDirUsbFile;
    private UsbFile storeUSBWifiConfigurationFile;

    public UsbDevice mUsbDevice;


    public ReceiverStoreUSB(Context context, StoreUSBListener storeUSBListener) {
        this.storeUSBListener = storeUSBListener;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        initStoreUSBDevice();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                usbConnect(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                usbDissConnect(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                break;
            case INIT_STORE_USB_PERMISSION:
                Log.d(TAG, "StoreUSBReceiver onReceive: INIT_STORE_USB_PERMISSION");
                initStoreUSBDevice();
                break;
            default:
                break;
        }
    }


    private void usbConnect(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return;
        }
        if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
            Log.d(TAG, "存储U盘设备接入");
            mUsbDevice = usbDevice;
            initStoreUSBDevice();
        }
    }

    public void usbDissConnect(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return;
        }
        try {
            UsbMassStorageDevice usbMassStorageDevice = getUsbMass(usbDevice);
            if (usbMassStorageDevice != null) {
                usbMassStorageDevice.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "StoreUSBReceiver onReceive: 设备断开异常 e =" + e);
        }
        Log.e(TAG, "StoreUSBReceiver onReceive:断开USB设备的 id = " + usbDevice.getDeviceId() + ",storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
        if (usbDevice.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {

            stopStoreUSBInitThreadExecutor();
            formatException = false;
            storeUSBFs = null;
            storeUSBLogcatDirUsbFile = null;
            storeUSBPictureDirUsbFile = null;
            storeUSBWifiConfigurationFile = null;
            mUsbDevice = null;

            VariableInstance.getInstance().storeUSBDeviceID = -1;
            VariableInstance.getInstance().isScanningStoreUSB = false;
            VariableInstance.getInstance().isDownloadingUSB = false;

            VariableInstance.getInstance().downdCameraPicrureNum = 0;
            VariableInstance.getInstance().uploadRemorePictureNum = 0;
            VariableInstance.getInstance().initingUSB = false;
            VariableInstance.getInstance().usbFileNameList.clear();

            storeUSBListener.storeUSBDeviceDetached();

        } else {
            getUSBPictureCount();
        }
    }


    public void initStoreUSBDevice() {

        if (VariableInstance.getInstance().storeUSBDeviceID != -1) {
            Log.d(TAG, "initStoreUSBDevice: U盘已初始化，直接返回");
            return;
        }

        Log.d(TAG, "initStoreUSBDevice: start ......................");
        stopStoreUSBInitThreadExecutor();

        storeUSBFs = null;
        mUsbDevice = null;
        storeUSBLogcatDirUsbFile = null;
        storeUSBPictureDirUsbFile = null;
        storeUSBWifiConfigurationFile = null;
        VariableInstance.getInstance().storeUSBDeviceID = -1;
        VariableInstance.getInstance().isScanningStoreUSB = false;
        VariableInstance.getInstance().isDownloadingUSB = false;

        VariableInstance.getInstance().downdCameraPicrureNum = 0;
        VariableInstance.getInstance().uploadRemorePictureNum = 0;
        VariableInstance.getInstance().initingUSB = false;

        initStoreUSBThreadExecutor = Executors.newSingleThreadExecutor();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                VariableInstance.getInstance().initingUSB = true;
                if (usbManager == null) {
                    usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
                }
                if (usbManager == null) {
                    Log.e(TAG, "initStoreUSBDevice: 系统异常 usbManager==null");
                    VariableInstance.getInstance().initingUSB = false;
                    return;
                }
                HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
                if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {
                    Log.e(TAG, "initStoreUSBDevice:  没有检测到有设备列表");
                    VariableInstance.getInstance().initingUSB = false;
                    if (storeUSBListener != null) {
                        storeUSBListener.initStoreUSBFailed(true);
                    }
                    return;
                }
                Log.d(TAG, "initStoreUSBDevice: " + "当前连接设备列表个数: connectedUSBDeviceList.size = " + connectedUSBDeviceList.size());
                Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
                if (usbDevices == null) {
                    Log.e(TAG, "initStoreUSBDevice:  没有检测到有设备接入");
                    if (storeUSBListener != null) {
                        storeUSBListener.initStoreUSBFailed(true);
                    }
                    VariableInstance.getInstance().initingUSB = false;
                    return;
                }
                Log.d(TAG, "initStoreUSBDevice: " + "当前连接设备个数:usbDevices.size = " + usbDevices.size());
                boolean initStoreUSBSucceed = false;
                boolean isPermission = false;
                for (UsbDevice usbDevice : usbDevices) {
                    if (usbDevice == null) {
                        Log.e(TAG, "initStoreUSBDevice usbDevice == null ");
                        continue;
                    }
                    Log.e(TAG, "run: 当前设备名称：" + usbDevice.getProductName());
                    if (!usbManager.hasPermission(usbDevice)) {
                        Log.e(TAG, "initStoreUSBDevice: 当前设备没有授权");
                        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(INIT_STORE_USB_PERMISSION), 0);
                        usbManager.requestPermission(usbDevice, pendingIntent);
                        isPermission = true;
                        continue;
                    }

                    int interfaceCount = usbDevice.getInterfaceCount();
                    Log.e(TAG, "initStoreUSBDevice run:获取接口数量 interfaceCount = " + interfaceCount);
                    for (int i = 0; i < interfaceCount; i++) {
                        UsbInterface usbInterface = usbDevice.getInterface(i);
                        if (usbInterface == null) {
                            continue;
                        }
                        int interfaceClass = usbInterface.getInterfaceClass();

                        if (interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                            Log.e(TAG, "initStoreUSBDevice: 当前设设备为U盘");
                            UsbMassStorageDevice device = getUsbMass(usbDevice);
                            initStoreUSBSucceed = initDevice(device, usbDevice);
                            Log.e(TAG, "initStoreUSBDevice run: initSucceed =" + initStoreUSBSucceed);
                            VariableInstance.getInstance().initingUSB = false;
                            if (initStoreUSBSucceed) {
                                return;
                            }
                        }
                    }
                }

                if (!initStoreUSBSucceed) {
                    if (storeUSBListener != null) {
                        storeUSBListener.initStoreUSBFailed(isPermission);
                    }
                }
                VariableInstance.getInstance().initingUSB = false;
            }
        };
        initStoreUSBThreadExecutor.execute(runnable);
    }

    private void stopStoreUSBInitThreadExecutor() {
        Log.e(TAG, "stopStoreUSBInitThreadExecutor: ");
        try {
            if (initStoreUSBThreadExecutor != null) {
                initStoreUSBThreadExecutor.shutdown();
            }
        } catch (Exception e) {
        }
        initStoreUSBThreadExecutor = null;
    }


    private boolean initDevice(UsbMassStorageDevice device, UsbDevice usbDevice) {
        Log.e(TAG, "initDevice : start ........................................ ");
        if (device == null) {
            Log.e(TAG, "initDevice: device == null");
            return false;
        }
        try {
            device.init();
        } catch (Exception e) {
            Log.e(TAG, "initDevice : device.init 设备初始化错误 " + e);
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.存储USB初始化错误);
            return false;
        }

        if (device.getPartitions().size() <= 0) {
            Log.e(TAG, "initDevice: " + "device.getPartitions().size() error, 无法获取到设备分区");

            VariableInstance.getInstance().errorLogNameList.add(ErrorName.存储USB无法获取到设备分区);
            return false;
        }
        Partition partition = device.getPartitions().get(0);
        FileSystem currentFs = partition.getFileSystem();
        UsbFile mRootFolder = currentFs.getRootDirectory();

        try {
            UsbFile[] usbFileList = mRootFolder.listFiles();

            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.getName().contains(VariableInstance.getInstance().PictureDirName)) {
                    storeUSBFs = currentFs;
                    storeUSBPictureDirUsbFile = usbFileItem;
                    VariableInstance.getInstance().storeUSBDeviceID = usbDevice.getDeviceId();
                } else if (usbFileItem.getName().contains(VariableInstance.getInstance().LogcatDirName)) {
                    storeUSBLogcatDirUsbFile = usbFileItem;
                } else if (usbFileItem.getName().contains(VariableInstance.getInstance().wifiConfigurationFileName)) {
                    storeUSBWifiConfigurationFile = usbFileItem;
                }
            }

            if (storeUSBPictureDirUsbFile == null) {
                storeUSBFs = currentFs;
                storeUSBPictureDirUsbFile = mRootFolder.createDirectory(VariableInstance.getInstance().PictureDirName);
                VariableInstance.getInstance().storeUSBDeviceID = usbDevice.getDeviceId();
            }

            if (storeUSBLogcatDirUsbFile == null) {
                storeUSBLogcatDirUsbFile = mRootFolder.createDirectory(VariableInstance.getInstance().LogcatDirName);
            }


        } catch (Exception e) {
            Log.e(TAG, "run: initDevice Exception =" + e);
        }


        Log.d(TAG, "usbDeviceScaner: storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
        if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.存储USB无法获取到设备ID);
            return false;
        } else {
            VariableInstance.getInstance().isInitUSB = true;
            getUSBPictureCount();
            storeUSBListener.initStoreUSBComplete(storeUSBWifiConfigurationFile);
        }
        return true;
    }


    public Vector<String> picturePathList;

    public void getUSBPictureCount() {
        Log.d(TAG, "getUSBPictureCount: start..........................");
        if (storeUSBPictureDirUsbFile == null) {
            Log.e(TAG, "getUSBPictureCount:  storeUSBPictureDirUsbFile == null");
            return;
        }
        if (VariableInstance.getInstance().isScanningStoreUSB) {
            Log.e(TAG, "getUSBPictureCount: 正在扫描文件，直接返回，不需要重复扫描");
            return;
        }

        if (picturePathList == null) {
            picturePathList = new Vector<>();
        }
        picturePathList.clear();
        VariableInstance.getInstance().usbFileNameList.clear();


        VariableInstance.getInstance().isScanningStoreUSB = true;
        getStoreUSBPictureCount(storeUSBPictureDirUsbFile);

        Log.d(TAG, "getUSBPictureCount: end..........................");

        Collections.sort(picturePathList, new MyOrder());

        if (VariableInstance.getInstance().cyclicDeletion && !VariableInstance.getInstance().syncAllCameraPicture) {
            cyclicDeletion();
        } else {
            VariableInstance.getInstance().usbFileNameList.addAll(picturePathList);
        }
        VariableInstance.getInstance().isScanningStoreUSB = false;
        int usbTotalPictureSize = VariableInstance.getInstance().usbFileNameList.size();


        storeUSBListener.storeUSBPictureCount(usbTotalPictureSize);
        VariableInstance.getInstance().LastPictureCount = usbTotalPictureSize;
    }


    private void getStoreUSBPictureCount(UsbFile usbFile) {
        if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
            Log.e(TAG, "getStoreUSBPictureCount: 11 正在格式化，不需要扫描");
            return;
        }
        try {
            UsbFile[] usbFileList = usbFile.listFiles();

            for (UsbFile usbFileItem : usbFileList) {
                if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                    Log.e(TAG, "getStoreUSBPictureCount: 22 正在格式化，不需要扫描");
                    return;
                }
                if (usbFileItem.isDirectory()) {
                    getStoreUSBPictureCount(usbFileItem);
                } else {
                    String name = usbFileItem.getName();
                    String FileEnd = name.substring(usbFileItem.getName().lastIndexOf(".") + 1).toLowerCase();
                    if (pictureFormatFile(FileEnd)) {
                        if (usbFileItem.getLength() > 10) {
                            picturePathList.add(name);
                        } else {
                            usbFileDelete(usbFileItem);
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
    }


    private void cyclicDeletion() {

        Vector<String> delectPicturePathList = new Vector<>();

        for (int i = 0; i < picturePathList.size(); i++) {
            if (i < VariableInstance.getInstance().MAX_NUM) {
                VariableInstance.getInstance().usbFileNameList.add(picturePathList.get(i));
            } else {
                delectPicturePathList.add(picturePathList.get(i));
            }
        }
        if (delectPicturePathList.size() > 0) {
            delectUSBPicture(storeUSBPictureDirUsbFile, delectPicturePathList);
        }
    }


    private void delectUSBPicture(UsbFile usbFile, Vector<String> delectPicturePathList) {

        try {
            UsbFile[] usbFileList = usbFile.listFiles();
            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.isDirectory()) {
                    delectUSBPicture(usbFileItem, delectPicturePathList);
                } else {
                    String name = usbFileItem.getName();
                    if (delectPicturePathList.contains(name)) {
                        usbFileDelete(usbFileItem);
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    private void usbFileDelete(UsbFile usbFile) {
        if (usbFile == null) {
            return;
        }
        Log.e(TAG, "usbFileDelete: usbFile =" + usbFile.getName());
        try {
            if (usbFile.getLength() > 0) {
                usbFile.delete();
            } else {
                Log.e(TAG, "usbFileDelete: 错误文件");
            }

        } catch (Throwable e) {
            Log.e(TAG, "usbFileDelete: Exception =" + e);
            try {
                usbFile.close();
            } catch (Exception ex) {

            }
        }
    }

    public class MyOrder implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o2.compareTo(o1);
        }
    }


    public int getStoreUSBCapacity() {
        if (storeUSBFs == null) {
            Log.e(TAG, "getStoreUSBCapacity: U盘未初始化，无法获取总容量");
            return 0;
        }
        int capacity = (int) (storeUSBFs.getCapacity() / (1024 * 1024));
        return capacity;
    }

    public int getStoreUSBFreeSpace() {
        if (storeUSBFs == null) {
            Log.e(TAG, "getStoreUSBFreeSpace: U盘未初始化，无法获取剩余空间");
            return 0;
        }
        int freeSpace = (int) (storeUSBFs.getFreeSpace() / (1024 * 1024));
        return freeSpace;
    }

    public boolean formatStoreUSB() {
        Log.e(TAG, "formatStoreUSB:start .................... ");
        formatException = false;
        if (storeUSBPictureDirUsbFile != null) {
            try {
                formatStoreUSB(storeUSBPictureDirUsbFile);
            } catch (Exception e) {
                Log.e(TAG, "formatStoreUSB 照片:格式化U盘异常 Exception =" + e);
                formatException = true;
            }
        }
        if (storeUSBLogcatDirUsbFile != null) {
            try {
                UsbFile[] usbLogcatDirFileList = storeUSBLogcatDirUsbFile.listFiles();
                for (UsbFile file : usbLogcatDirFileList) {
                    usbFileDelete(file);
                }
            } catch (Exception | OutOfMemoryError e) {
                Log.e(TAG, "formatStoreUSB 日志:格式化U盘异常 Exception =" + e);
                formatException = true;
            }
        }

        return formatException;
    }

    public void formatStoreUSB(UsbFile usbFile) {
        try {
            if (usbFile.isDirectory()) {
                UsbFile[] usbDirFileList = usbFile.listFiles();
                for (UsbFile file : usbDirFileList) {
                    if (file.isDirectory()) {
                        formatStoreUSB(file);
                    } else {
                        deleteUsbFile(file);
                    }
                }
            } else {
                deleteUsbFile(usbFile);
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "formatStoreUSB: 3删除文件异常Exception =" + e);
            formatException = true;
        }
    }


    private void deleteUsbFile(UsbFile usbFile) {

        if (usbFile == null || usbFile.getLength() == 0) return;

        Log.e(TAG, "deleteUsbFile: usbFile = " + usbFile.getName());

        if (VariableInstance.getInstance().isFormatingUSB.formatState == 2) {
            long createDate = usbFile.createdAt() - 1000L * 60 * 60 * 8;
            int yymmdd = Utils.getyyMMddtringInt(createDate);

            int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
            if (systemTime == 900101) {
                return;
            }
            if (systemTime - yymmdd > 14) {
                usbFileDelete(usbFile);
            }
        } else {
            usbFileDelete(usbFile);
        }
    }

    public void uploadLogcatToUSB(String logcatFilePath) {
        Log.e(TAG, "uploadLogcatToUSB start ...........................");
        if (storeUSBLogcatDirUsbFile == null || storeUSBFs == null) {
            Log.e(TAG, "uploadLogcatToUSB: 出错，U盘未初始化");
            return;
        }

        if (logcatFilePath == null) {
            Log.e(TAG, "uploadLogcatToUSB: 出错，日志文件路径不存在");
            return;
        }

        UsbFileOutputStream usbFileOutputStream = null;
        InputStream inputStream = null;
        File localFile = null;


        try {
            localFile = new File(logcatFilePath);

            String name = localFile.getName();
            if (name.contains("logcat1970")) {
                String date = LogcatHelper.getInstance().getFileName();
                name = "logcat" + date + ".txt";
            }
            UsbFile create = storeUSBLogcatDirUsbFile.createFile(name);
            usbFileOutputStream = new UsbFileOutputStream(create);
            inputStream = new FileInputStream(localFile);

            int bytesRead;
            byte[] buffer = new byte[storeUSBFs.getChunkSize()];
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
    }

    public boolean uploadToUSB(File localFile, String yearMonth) {
        if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
            Log.e(TAG, "uploadLogcatToUSB: 正在格式化，不需要上传到U盘");
            return false;
        }


        if (localFile == null || !localFile.exists() || localFile.length() < 10) {
            Log.e(TAG, "uploadToUSB: 上传文件到U盘出错，文件不存在 \ntodayDir =" + "\n localFile =" + localFile);
            return false;
        }

        if (storeUSBPictureDirUsbFile == null || storeUSBFs == null) {
            Log.e(TAG, "uploadToUSB: 上传文件到U盘出错，U盘未初始化");
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.存储USB未初始化上传到USB出错);
            return false;
        }
        Log.d(TAG, "uploadToUSB: localFile =" + localFile);

        VariableInstance.getInstance().isDownloadingUSB = true;

        long time = System.currentTimeMillis();
        long fileSize = 0;
        UsbFileOutputStream usbFileOutputStream = null;
        InputStream inputStream = null;
        try {
            UsbFile yearMonthUsbFile = null;
            UsbFile[] usbFileList = storeUSBPictureDirUsbFile.listFiles();
            if (usbFileList != null) {
                for (UsbFile usbFile : usbFileList) {
                    if (usbFile.getName().contains(yearMonth)) {
                        yearMonthUsbFile = usbFile;
                        break;
                    }
                }
            }

            if (yearMonthUsbFile == null) {
                yearMonthUsbFile = storeUSBPictureDirUsbFile.createDirectory(yearMonth);
            }

            if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                return false;
            }

            UsbFile create = yearMonthUsbFile.search(localFile.getName());
            if (create == null) {
                create = yearMonthUsbFile.createFile(localFile.getName());
            } else {
                Log.e(TAG, "uploadToUSB: U盘已存在这个文件，size=" + create.getLength());
                create.setLength(localFile.length());
            }

            Log.d(TAG, "uploadToUSB.................................: name =" + localFile.getName());
            usbFileOutputStream = new UsbFileOutputStream(create);
            inputStream = new FileInputStream(localFile);
            fileSize = inputStream.available();
            int bytesRead;
            byte[] buffer = new byte[storeUSBFs.getChunkSize()];


            while ((bytesRead = inputStream.read(buffer)) != -1) {
                usbFileOutputStream.write(buffer, 0, bytesRead);
                if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                    usbFileDelete(create);
                    break;
                }
            }
            VariableInstance.getInstance().downdCameraPicrureNum++;
            VariableInstance.getInstance().LastPictureCount++;
            long totalTime = ((System.currentTimeMillis() - time) / 1000);
            if (totalTime == 0) {
                totalTime = 1;
            }

            String speed = fileSize / totalTime / 1024 + "";
            Log.d(TAG, "uploadToUSB: 上传USB速度：speed =" + speed + ",fileSize =" + fileSize);
            storeUSBListener.storeUSBSaveOnePictureComplete(speed);
            create.close();
        } catch (Exception e) {
            if (e.toString().contains("Item already exists")) {
                Log.d(TAG, "uploadToUSB: U盘已存在同名文件");
            } else {
                Log.e(TAG, "uploadToUSB: 上传U盘出错 Exception =" + e);
                String error = ErrorName.上传图片到USB出错 + ":" + e;
                VariableInstance.getInstance().errorLogNameList.add(error);
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

        VariableInstance.getInstance().isDownloadingUSB = false;
        Log.d(TAG, "uploadToUSB:完成 ");
        return true;
    }


    private boolean pictureFormatFile(String FileEnd) {
        return FileEnd.equals("nif") || FileEnd.equals("crw") || FileEnd.equals("raw") || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2") || FileEnd.equals("cr3") || FileEnd.equals("jpg");
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

    public interface StoreUSBListener {
        void storeUSBPictureCount(int count);

        void initStoreUSBComplete(UsbFile wifiConfigurationFile);

        void storeUSBDeviceDetached();

        void storeUSBSaveOnePictureComplete(String speed);

        void initStoreUSBFailed(boolean delay);

    }

}

