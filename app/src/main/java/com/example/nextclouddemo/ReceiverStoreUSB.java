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

import com.example.nextclouddemo.utils.LocalProfileHelp;
import com.example.nextclouddemo.utils.Log;
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
    private static final String TAG = "remotelog_ReceiverStoreUSB";
    private boolean formatException = false;
    private int requestPermissionCount;

    private final StoreUSBListener storeUSBListener;
    private ExecutorService initStoreUSBThreadExecutor;
    private UsbManager usbManager;

    private FileSystem storeUSBFileSystem;
    private UsbFile storeUSBLogcatDirUsbFile;
    private UsbFile storeUSBPictureDirUsbFile;


    public ReceiverStoreUSB(Context context, StoreUSBListener storeUSBListener) {
        this.storeUSBListener = storeUSBListener;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        storeUSBListener.sendInitUSBTimeOutMessage(1);//40s内无法初始化U盘，相机正常扫描
        initStoreUSBDevicea();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
            case VariableInstance.GET_STORE_USB_PERMISSION: {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverStoreUSB onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverStoreUSB onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (VariableInstance.getInstance().isStroreUSBDevice(usbDevice.getProductName())) {
                    usbConnect(usbDevice);
                } else {
                    Log.e(TAG, "ReceiverStoreUSB onReceive: 111 action =" + action + ", getProductName =" + usbDevice.getProductName());
                }
            }
            break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED: {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverStoreUSB onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverStoreUSB onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (VariableInstance.getInstance().isStroreUSBDevice(usbDevice.getProductName())) {
                    usbDissConnect(usbDevice);
                }
            }
            break;
            default:
                break;
        }
    }


    public void initStoreUSBDevicea() {
        if (VariableInstance.getInstance().storeUSBDeviceID != -1) {
            Log.d(TAG, "initStoreUSBDevice: U盘已初始化，直接返回");
            return;
        }


        if (usbManager == null) {
            usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
        }

        if (usbManager == null) {
            Log.e(TAG, "initStoreUSBDevice: 系统异常 usbManager==null");
            if (storeUSBListener != null) {
                storeUSBListener.sendInitUSBTimeOutMessage(2);
            }
            return;
        }


        HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
        if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {
            Log.e(TAG, "initStoreUSBDevice:  没有检测到有设备列表");

            if (storeUSBListener != null) {
                storeUSBListener.sendInitUSBTimeOutMessage(3);
            }
            return;
        }
        Log.d(TAG, "initStoreUSBDevice: " + "当前连接设备列表个数: connectedUSBDeviceList.size = " + connectedUSBDeviceList.size());
        Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
        if (usbDevices == null) {
            Log.e(TAG, "initStoreUSBDevice:  没有检测到有设备接入");
            if (storeUSBListener != null) {
                storeUSBListener.sendInitUSBTimeOutMessage(4);
            }
            return;
        }
        Log.d(TAG, "initStoreUSBDevice: " + "当前连接设备个数:usbDevices.size = " + usbDevices.size());

        for (UsbDevice usbDevice : usbDevices) {
            if (usbDevice == null) {
                Log.e(TAG, "initStoreUSBDevice usbDevice == null ");
                continue;
            }

            String productName = usbDevice.getProductName();
            Log.e(TAG, "run: 当前设备名称：" + productName);
            if (productName == null) {
                continue;
            }
            if (!VariableInstance.getInstance().isStroreUSBDevice(productName)) {
                continue;
            }
            usbConnect(usbDevice);
        }
    }

    private void stopStoreUSBInitThreadExecutor() {
        Log.e(TAG, "stopStoreUSBInitThreadExecutor: ");
        try {
            if (initStoreUSBThreadExecutor != null) {
                initStoreUSBThreadExecutor.shutdownNow();
            }
        } catch (Exception e) {
        }
        initStoreUSBThreadExecutor = null;
    }


    private void resetStoreUSBState() {
        stopStoreUSBInitThreadExecutor();

        formatException = false;
        storeUSBFileSystem = null;
        storeUSBLogcatDirUsbFile = null;
        storeUSBPictureDirUsbFile = null;

        VariableInstance.getInstance().storeUSBDeviceID = -1;
        VariableInstance.getInstance().isScanningStoreUSB = false;
        VariableInstance.getInstance().backupPicrureNum = 0;
        VariableInstance.getInstance().currentUSBPictureCount = 0;
    }

    private void usbConnect(UsbDevice usbDevice) {
        Log.d(TAG, "usbConnect: start ...................... ");
        if (usbDevice == null) {
            Log.e(TAG, "usbConnect: usbDevice == null");
            return;
        }
        if (VariableInstance.getInstance().storeUSBDeviceID != -1) {
            Log.d(TAG, "usbConnect: U盘已初始化，直接返回");
            return;
        }
        Log.d(TAG, "usbConnect 存储U盘设备接入");
        resetStoreUSBState();

        initStoreUSBThreadExecutor = Executors.newSingleThreadExecutor();
        initStoreUSBThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                VariableInstance.getInstance().isScanningStoreUSB = true;

                if (!usbManager.hasPermission(usbDevice)) {

                    if (requestPermissionCount > 20) {
                        VariableInstance.getInstance().isScanningStoreUSB = false;
                        Log.e(TAG, "usbConnect: 当前设备没有授权,申请超过20次仍然失败 :" + usbDevice.getProductName());
                        if (storeUSBListener != null) {
                            storeUSBListener.sendInitUSBTimeOutMessage(5);
                        }
                        return;
                    }
                    requestPermissionCount++;

                    Log.e(TAG, "usbConnect: 当前设备没有授权,productName :" + usbDevice.getProductName());
                    @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(VariableInstance.GET_STORE_USB_PERMISSION), 0);
                    usbManager.requestPermission(usbDevice, pendingIntent);
                    VariableInstance.getInstance().isScanningStoreUSB = false;

                    if (storeUSBListener != null) {
                        storeUSBListener.sendInitUSBTimeOutMessage(6);
                    }
                    return;
                }


                int interfaceCount = usbDevice.getInterfaceCount();
                Log.e(TAG, "usbConnect run:获取接口数量 interfaceCount = " + interfaceCount);

                for (int i = 0; i < interfaceCount; i++) {
                    UsbInterface usbInterface = usbDevice.getInterface(i);
                    if (usbInterface == null) {
                        Log.d(TAG, "run: usbConnect continue 0000000000000");
                        continue;
                    }
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        Log.e(TAG, "usbConnect: 当前设设备为U盘");

                        int count = 0;
                        while (VariableInstance.getInstance().serverApkInitingUSB && count < 20) {
                            Log.e(TAG, "usbConnect: 守护apk 正在操作U盘,等待3S");
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {

                            }
                            count++;
                        }

                        Log.e(TAG, "usbConnect: 当前设设备为U盘 count =" + count);

                        UsbMassStorageDevice device = getUsbMass(usbDevice);
                        if (device == null) {
                            Log.d(TAG, "run: usbConnect continue 111111111");
                            continue;
                        }
                        try {
                            device.init();
                        } catch (Exception e) {
                            Log.e(TAG, "usbConnect : device.init 设备初始化错误 " + e);
                            Log.d(TAG, "run: usbConnect continue 222222222");
                            continue;
                        }

                        if (device.getPartitions().size() <= 0) {
                            Log.e(TAG, "usbConnect: " + "device.getPartitions().size() error, 无法获取到设备分区");
                            continue;
                        }
                        Partition partition = device.getPartitions().get(0);
                        FileSystem fileSystem = partition.getFileSystem();
                        UsbFile usbRootFolder = fileSystem.getRootDirectory();
                        storeUSBFileSystem = fileSystem;

                        if (storeUSBListener != null) {
                            storeUSBListener.storegeUSBSpaceInitData();
                        }

                        try {
                            UsbFile[] usbFileList = usbRootFolder.listFiles();
                            for (UsbFile usbFileItem : usbFileList) {
                                if (usbFileItem.getName().contains(VariableInstance.getInstance().PictureDirName)) {
                                    storeUSBPictureDirUsbFile = usbFileItem;
                                } else if (usbFileItem.getName().contains(VariableInstance.getInstance().LogcatDirName)) {
                                    storeUSBLogcatDirUsbFile = usbFileItem;
                                } else if (usbFileItem.getName().contains(VariableInstance.getInstance().wifiConfigurationFileName)) {
                                    Log.d(TAG, "usbConnect run: 找到配置文件 start");
                                    if (storeUSBListener != null) {
                                        storeUSBListener.checkProfileFile(usbFileItem);
                                    }
                                    Log.d(TAG, "usbConnect run: 找到配置文件 end");
                                }
                            }

                            if (storeUSBPictureDirUsbFile == null) {
                                storeUSBPictureDirUsbFile = usbRootFolder.createDirectory(VariableInstance.getInstance().PictureDirName);
                            }

                            if (storeUSBLogcatDirUsbFile == null) {
                                storeUSBLogcatDirUsbFile = usbRootFolder.createDirectory(VariableInstance.getInstance().LogcatDirName);
                            }

                            if (storeUSBPictureDirUsbFile == null) {
                                Log.e(TAG, "usbConnect: 备份USB无法创建存储照片的目录");
                                continue;
                            }


                            VariableInstance.getInstance().storeUSBDeviceID = usbDevice.getDeviceId();

                            Log.d(TAG, "run:usbConnect storeUSBDeviceID = " + VariableInstance.getInstance().storeUSBDeviceID);

                        } catch (Exception e) {
                            Log.e(TAG, "usbConnect 111 Exception =" + e);
                        }

                        Log.d(TAG, "usbConnect: storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
                        if (VariableInstance.getInstance().storeUSBDeviceID != -1) {
                            intiBackupUSBPictureList();
                            VariableInstance.getInstance().isScanningStoreUSB = false;
                            break;
                        }
                    }
                }
                VariableInstance.getInstance().isScanningStoreUSB = false;
                if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
                    Log.e(TAG, "usbConnect:  遍历完端口，无法找到U盘接口初始化");
                    if (storeUSBListener != null) {
                        storeUSBListener.sendInitUSBTimeOutMessage(7);
                    }
                }
            }
        });
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
        Log.e(TAG, "StoreUSBReceiver onReceive:断开USB设备的 id = " + usbDevice.getDeviceId() + ",name =" + usbDevice.getProductName() + ",storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);

        if (VariableInstance.getInstance().isStroreUSBDevice(usbDevice.getProductName())) {
            requestPermissionCount = 0;
        }

        if (usbDevice.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
            resetStoreUSBState();
        }
    }


    private void intiBackupUSBPictureList() {
        Log.d(TAG, "intiBackupUSBPictureList: start..........................");
        if (storeUSBPictureDirUsbFile == null) {
            Log.e(TAG, "intiBackupUSBPictureList:  storeUSBPictureDirUsbFile == null");
            return;
        }


        if (!LocalProfileHelp.getInstance().initLocalUSBPictureList()) {
            LocalProfileHelp.getInstance().createLocalUSBPictureList(storeUSBPictureDirUsbFile);
        }
        Log.e(TAG, "intiBackupUSBPictureList: 数据库中存储照片张数：" + LocalProfileHelp.getInstance().usbPictureList.size());

        if (storeUSBListener != null) {
            storeUSBListener.historyBackupPictureCount();
        }


        if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
            Log.e(TAG, "getStoreUSBPictureCount: 11 正在格式化，不需要扫描");
            return;
        }

        List<String> picturePathList = Collections.synchronizedList(new ArrayList<>());
        if (storeUSBPictureDirUsbFile != null) {
            try {
                UsbFile[] dateFileList = storeUSBPictureDirUsbFile.listFiles();
                for (UsbFile dateUsbFile : dateFileList) {
                    if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                        Log.e(TAG, "getStoreUSBPictureCount: 22 正在格式化，不需要扫描");
                        return;
                    }
                    if (dateUsbFile.isDirectory()) {
                        UsbFile[] pictureFileList = dateUsbFile.listFiles();
                        for (UsbFile pictureUsbFile : pictureFileList) {
                            if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                                Log.e(TAG, "getStoreUSBPictureCount: 33 正在格式化，不需要扫描");
                                return;
                            }
                            String name = pictureUsbFile.getName();
                            String FileEnd = name.substring(pictureUsbFile.getName().lastIndexOf(".") + 1).toLowerCase();
                            if (pictureFormatFile(FileEnd)) {
                                if (pictureUsbFile.getLength() > 10) {
                                    picturePathList.add(name);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {

            }
        }
        VariableInstance.getInstance().currentUSBPictureCount = picturePathList.size();
        Log.e(TAG, "intiBackupUSBPictureList: U盘当前照片张数：" + VariableInstance.getInstance().currentUSBPictureCount);

        if (storeUSBListener != null) {
            storeUSBListener.storeUSBPictureCount(VariableInstance.getInstance().currentUSBPictureCount);
        }

        if (storeUSBPictureDirUsbFile != null && VariableInstance.getInstance().cyclicDeletion && VariableInstance.getInstance().currentUSBPictureCount > VariableInstance.getInstance().MAX_NUM) {
            Collections.sort(picturePathList, new NewToOldComparator());
            List<String> delectList = picturePathList.subList(VariableInstance.getInstance().MAX_NUM, picturePathList.size());
            cyclicDeletionPicture(delectList);
        }
    }

    public class NewToOldComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o2.compareTo(o1);
        }
    }


    public void cyclicDeletionPicture(List<String> delectList) {
        if (storeUSBPictureDirUsbFile != null && delectList != null && delectList.size() > 0) {
            try {
                UsbFile[] dateFileList = storeUSBPictureDirUsbFile.listFiles();
                for (UsbFile dateUsbFile : dateFileList) {
                    if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
                        return;
                    }
                    if (dateUsbFile.isDirectory()) {
                        UsbFile[] pictureFileList = dateUsbFile.listFiles();
                        for (UsbFile pictureUsbFile : pictureFileList) {
                            if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
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


    public int getStoreUSBCapacity() {
        if (storeUSBFileSystem == null) {
            Log.e(TAG, "getStoreUSBCapacity: U盘未初始化，无法获取总容量");
            return 0;
        }
        int capacity = (int) (storeUSBFileSystem.getCapacity() / (1024 * 1024));
        return capacity;
    }

    public int getStoreUSBFreeSpace() {
        if (storeUSBFileSystem == null) {
            Log.e(TAG, "getStoreUSBFreeSpace: U盘未初始化，无法获取剩余空间");
            return 0;
        }
        int freeSpace = (int) (storeUSBFileSystem.getFreeSpace() / (1024 * 1024));
        return freeSpace;
    }

    public boolean formatStoreUSB() {
        Log.e(TAG, "formatStoreUSB:start .................... ");
        formatException = false;
        if (storeUSBPictureDirUsbFile != null) {
            try {
                UsbFile[] usbDirFileList = storeUSBPictureDirUsbFile.listFiles();
                for (UsbFile dateUsbFile : usbDirFileList) {
                    if (dateUsbFile.isDirectory()) {
                        UsbFile[] pictureFileList = dateUsbFile.listFiles();
                        for (UsbFile pictureUsbFile : pictureFileList) {
                            if (!pictureUsbFile.isDirectory()) {
                                formatDeleteUsbFile(pictureUsbFile);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
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
            } catch (Throwable e) {
                Log.e(TAG, "formatStoreUSB 日志:格式化U盘异常 Exception =" + e);
                formatException = true;
            }
        }

        return formatException;
    }


    private void formatDeleteUsbFile(UsbFile usbFile) {
        if (usbFile == null || usbFile.getLength() == 0) {
            return;
        }

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

        if (storeUSBPictureDirUsbFile == null || storeUSBFileSystem == null) {
            Log.e(TAG, "uploadToUSB: 上传文件到U盘出错，U盘未初始化");
            return false;
        }
        Log.d(TAG, "uploadToUSB: localFile =" + localFile);


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
            byte[] buffer = new byte[storeUSBFileSystem.getChunkSize()];


            while ((bytesRead = inputStream.read(buffer)) != -1) {
                usbFileOutputStream.write(buffer, 0, bytesRead);
                if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                    usbFileDelete(create);
                    break;
                }
            }
            VariableInstance.getInstance().backupPicrureNum++;
            VariableInstance.getInstance().currentUSBPictureCount++;
            LocalProfileHelp.getInstance().addLocalUSBPictureList(localFile.getName());
            long totalTime = ((System.currentTimeMillis() - time) / 1000);
            if (totalTime == 0) {
                totalTime = 1;
            }

            String speed = fileSize / totalTime / 1024 + "";
            Log.d(TAG, "uploadToUSB: 上传USB速度：speed =" + speed + ",fileSize =" + fileSize);
            if (storeUSBListener != null) {
                storeUSBListener.storeUSBSaveOnePictureComplete(speed);
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


        Log.d(TAG, "uploadToUSB:完成 ");
        return true;
    }


    private boolean pictureFormatFile(String FileEnd) {
        if (FileEnd == null || FileEnd.trim().length() == 0) {
            return false;
        }
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

        void sendInitUSBTimeOutMessage(int position);

        void checkProfileFile(UsbFile wifiConfigurationFile);

        void storegeUSBSpaceInitData();

        void historyBackupPictureCount();

        void storeUSBPictureCount(int count);


        void storeUSBSaveOnePictureComplete(String speed);


    }

}

