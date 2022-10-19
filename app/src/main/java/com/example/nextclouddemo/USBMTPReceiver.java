package com.example.nextclouddemo;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;

import com.example.nextclouddemo.model.UploadFileModel;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.Utils;
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;
import com.github.mjdev.libaums.partition.Partition;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class USBMTPReceiver extends BroadcastReceiver {
    private static final String TAG = "MainActivitylog2";
    public static final String CHECK_PERMISSION = "CHECK_PERMISSION";
    public static final String CHECK_UPLOAD_PERMISSION = "CHECK_UPLOAD_PERMISSION";
    private ExecutorService scanerThreadExecutor;
    private ExecutorService initUploadUSBThreadExecutor;

    private UsbManager usbManager;
    private String tfcardpicturedir;
    private String tfcarduploadpicturedir;

    public int deviceID = -1;
    public FileSystem uploadFs;
    public UsbFile logcatFileDirUsbFile;
    public UsbFile uploadFileDirUsbFile;
    public UsbFile todayMonthUsbFile;



    public String todayMonthDirString;
    private DownloadFlieListener downloadFlieListener;
    private UsbDevice mUSBDevice;

    private ArrayList<PictureInfo> pictureInfos;
    private ArrayList<PictureInfo> rowPictureInfos;
    private ArrayList<PictureInfo> jpgPictureInfos;

    public USBMTPReceiver(Context context, DownloadFlieListener downloadFlieListener) {
        this.tfcardpicturedir = VariableInstance.getInstance().TFCardPictureDir;
        this.tfcarduploadpicturedir = VariableInstance.getInstance().TFCardUploadPictureDir;
        this.downloadFlieListener = downloadFlieListener;
        todayMonthDirString = Utils.getyyyyMMString();


        currentDataMillis = System.currentTimeMillis();

        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        pictureInfos = new ArrayList<>();
        rowPictureInfos = new ArrayList<>();
        jpgPictureInfos = new ArrayList<>();
        usbFileNameList = new Vector<>();


        Utils.makeDir(tfcardpicturedir);
        Utils.makeDir(tfcarduploadpicturedir);


        initUploadUSBDevice();
    }


    public void uploadLogcatToUSB() {
        Log.e(TAG, "uploadLogcatToUSB:asdfadsfad logcatFileDirUsbFile =" + logcatFileDirUsbFile + ",logcatFilePath =" + LogcatHelper.getInstance().logcatFilePath);
        if (logcatFileDirUsbFile == null || LogcatHelper.getInstance().logcatFilePath == null)
            return;
        UsbFileOutputStream os = null;
        InputStream is = null;
        try {
            File localFile = new File(LogcatHelper.getInstance().logcatFilePath);
            UsbFile create = logcatFileDirUsbFile.createFile(localFile.getName());
            os = new UsbFileOutputStream(create);
            is = new FileInputStream(localFile);

            int bytesRead;
            byte[] buffer = new byte[uploadFs.getChunkSize()];
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Log.e(TAG, "asdfadsfad uploadLogcatToUSB: Exception =" + e);
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadLogcatToUSB: asdfadsfad Exception =" + e);
            }
        }


    }

    public void release() {
        Log.e(TAG, "release: ");
        stopInitUploadUSBThreadExecutor();
        stopScanerThread();
    }

    public void stopInitUploadUSBThreadExecutor() {
        Log.e(TAG, "stopInitUploadUSBThreadExecutor: ");
        try {
            if (initUploadUSBThreadExecutor != null)
                initUploadUSBThreadExecutor.shutdown();
        } catch (Exception e) {
        }
        initUploadUSBThreadExecutor = null;
        uploadFileDirUsbFile = null;
        logcatFileDirUsbFile = null;
        todayMonthUsbFile = null;
        uploadFs = null;
        deviceID = -1;
    }

    private boolean isStopScanerThread;

    private void stopScanerThread() {
        Log.d(TAG, "stopScanerThread: ");
        isStopScanerThread = true;
        try {
            if (scanerThreadExecutor != null)
                scanerThreadExecutor.shutdown();
        } catch (Exception e) {

        }
        scanerThreadExecutor = null;
    }

    public int getCapacity() {
        if (uploadFs == null)
            return 0;
        int capacity = (int) (uploadFs.getCapacity() / (1024 * 1024));
        return capacity;
    }

    public int getFreeSpace() {
        if (uploadFs == null)
            return 0;
        int freeSpace = (int) (uploadFs.getFreeSpace() / (1024 * 1024));
        return freeSpace;
    }

    public void formatUSB() {
        stopScanerThread();
        Log.e(TAG, "delectUploadUSBFile: uploadFileDirUsbFile =" + uploadFileDirUsbFile);
        if (uploadFileDirUsbFile == null)
            return;
        Log.e(TAG, "delectUploadUSBFile: " + uploadFileDirUsbFile.getName());
        try {
            UsbFile[] usbFileList = uploadFileDirUsbFile.listFiles();
            for (UsbFile file : usbFileList) {
                file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "delectUploadUSBFile: Exception =" + e);
        }
        Log.d(TAG, "delectUploadUSBFile: 删除文件完成");
    }

    public void formatCamera() {
        try {
            stopScanerThread();
            pictureInfos.clear();
            rowPictureInfos.clear();
            jpgPictureInfos.clear();
            downloadFlieListener.downloadComplete();
        } catch (Exception e) {

        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                mUSBDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.e(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED ");
                if (mUSBDevice == null) {
                    return;
                }
                try {
                    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                    if (usbManager.hasPermission(mUSBDevice)) {
                        Log.e(TAG, "onReceive: hasPermission");
                        checkConnectedDevice();
                    } else {
                        @SuppressLint("UnspecifiedImmutableFlag")
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, new Intent(CHECK_PERMISSION), 0);
                        usbManager.requestPermission(mUSBDevice, pendingIntent);
                        Log.e(TAG, "onReceive: no hasPermission");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "ACTION_USB_DEVICE_ATTACHED: Exception =" + e);
                }
            }
            break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED: {
                Log.e(TAG, "onReceive: 断开USB设备");
                mUSBDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (mUSBDevice == null) {
                    Log.e(TAG, "onReceive: mUSBDevice == null");
                    return;
                }
                try {
                    UsbMassStorageDevice usbMassStorageDevice = getUsbMass(mUSBDevice);
                    if (usbMassStorageDevice != null) {
                        usbMassStorageDevice.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onReceive: 设备断开异常 e =" + e);
                }
                Log.e(TAG, "onReceive:断开USB设备 mUSBDevice id = " + mUSBDevice.getDeviceId() + ",deviceID =" + deviceID);
                if (mUSBDevice.getDeviceId() == deviceID) {
                    release();
                } else {
                    getUSBPictureCount();
                    downloadFlieListener.usbFileScanrFinishi(usbFileNameList.size());
                }
                mUSBDevice = null;
            }
            break;
            case CHECK_PERMISSION:
                Log.e(TAG, "onReceive: CHECK_PERMISSION deviceID =" + deviceID);
                if (mUSBDevice != null && mUSBDevice.getDeviceId() == deviceID) {
                    Log.e(TAG, "onReceive: CHECK_PERMISSION mUSBDevice != null && mUSBDevice.getDeviceId() == deviceID");
                    return;
                }
                checkConnectedDevice();
                break;
            case CHECK_UPLOAD_PERMISSION:
                Log.d(TAG, "onReceive: CHECK_UPLOAD_PERMISSION");
                initUploadUSBDevice();
                break;
            default:
                break;
        }
    }


    public void initUploadUSBDevice() {
        Log.e(TAG, "initUploadUSBDevice: ");
        stopInitUploadUSBThreadExecutor();
        initUploadUSBThreadExecutor = Executors.newSingleThreadExecutor();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (usbManager == null)
                    usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
                if (usbManager == null) {
                    Log.e(TAG, "initUploadUSBDevice: usbManager==null");
                    return;
                }
                //获取所有已连接上的USB列表
                HashMap<String, UsbDevice> allConnectedUSBDeviceList = usbManager.getDeviceList();
                if (allConnectedUSBDeviceList == null || allConnectedUSBDeviceList.size() <= 0) {
                    downloadFlieListener.usbIntError("没有检查到上传USB设备");
                    return;
                }


                Log.d(TAG, "initUploadUSBDevice: " + "当前连接设备个数:" + allConnectedUSBDeviceList.size());
                for (UsbDevice usbDevice : allConnectedUSBDeviceList.values()) {
                    if (usbDevice == null) {
                        Log.e(TAG, "run: allConnectedUSBDeviceList usbDevice = null ");
                        continue;
                    }
                    //遍历连接的设备接口
                    for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                        UsbInterface usbInterface = usbDevice.getInterface(i);
                        if (usbInterface == null)
                            continue;
                        switch (usbInterface.getInterfaceClass()) {
                            case UsbConstants.USB_CLASS_MASS_STORAGE: {
                                Log.d(TAG, "initUploadUSBDevice: deviceID =" + deviceID);
                                if (usbManager.hasPermission(usbDevice)) {
                                    UsbMassStorageDevice device = getUsbMass(usbDevice);
                                    if (device == null) {
                                        Log.e(TAG, "usbDeviceScaner: device == null");
                                        continue;
                                    }
                                    try {
                                        device.init();
                                    } catch (Exception e) {
                                        Log.e(TAG, "readUSBDevice:device.init() error:" + e);
                                        continue;
                                    }

                                    if (device.getPartitions().size() <= 0) {
                                        Log.e(TAG, "readUSBDevice: " + "device.getPartitions().size() error");
                                        continue;
                                    }
                                    Partition partition = device.getPartitions().get(0);
                                    FileSystem currentFs = partition.getFileSystem();
                                    UsbFile mRootFolder = currentFs.getRootDirectory();


                                    try {
                                        UsbFile[] usbFileList = mRootFolder.listFiles();
                                        for (UsbFile usbFileItem : usbFileList) {
                                            if (usbFileItem.getName().contains(VariableInstance.getInstance().LogcatDirName)) {
                                                logcatFileDirUsbFile = usbFileItem;
                                                break;
                                            }
                                        }

                                        if (logcatFileDirUsbFile == null)
                                            logcatFileDirUsbFile = mRootFolder.createDirectory(VariableInstance.getInstance().LogcatDirName);
                                    } catch (Exception e) {
                                    }


                                    try {
                                        UsbFile[] usbFileList = mRootFolder.listFiles();
                                        for (UsbFile usbFileItem : usbFileList) {
                                            if (usbFileItem.getName().contains(VariableInstance.getInstance().PictureDirName)) {
                                                uploadFs = currentFs;
                                                uploadFileDirUsbFile = usbFileItem;
                                                deviceID = usbDevice.getDeviceId();
                                                UsbFile[] usbFileList2 = uploadFileDirUsbFile.listFiles();
                                                for (UsbFile usbFileItem2 : usbFileList2) {
                                                    if (usbFileItem2.getName().contains(todayMonthDirString)) {
                                                        todayMonthUsbFile = usbFileItem2;
                                                        break;
                                                    }
                                                }
                                                if (todayMonthUsbFile == null)
                                                    todayMonthUsbFile = uploadFileDirUsbFile.createDirectory(todayMonthDirString);
                                            }
                                        }

                                        if (uploadFileDirUsbFile == null) {
                                            uploadFs = currentFs;
                                            uploadFileDirUsbFile = mRootFolder.createDirectory(VariableInstance.getInstance().PictureDirName);
                                            todayMonthUsbFile = uploadFileDirUsbFile.createDirectory(todayMonthDirString);
                                            deviceID = usbDevice.getDeviceId();
                                        }

                                    } catch (Exception e) {
                                        Log.e(TAG, "readUSBDevice: Exception =" + e);
                                    }
                                    Log.d(TAG, "usbDeviceScaner: deviceID =" + deviceID);
                                    if (deviceID == -1) {
                                        downloadFlieListener.usbIntError("deviceID == -1");
                                        continue;
                                    } else {
                                        usbFileNameList.clear();
                                        getUploadDeviceUSBPictureCount(uploadFileDirUsbFile);
                                        downloadFlieListener.initUploadUSBComplete(usbFileNameList.size());
                                    }
                                } else {
                                    Log.e(TAG, "usbDeviceScaner: hasPermission =false");
                                    @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(CHECK_UPLOAD_PERMISSION), 0);
                                    usbManager.requestPermission(usbDevice, pendingIntent);
                                }
                            }
                            break;
                            default:
                                break;
                        }
                    }
                }
            }
        };
        initUploadUSBThreadExecutor.execute(runnable);
    }


    public int getUSBPictureCount() {
        if (uploadFileDirUsbFile == null)
            return -1;
        usbFileNameList.clear();
        getUploadDeviceUSBPictureCount(uploadFileDirUsbFile);
        return usbFileNameList.size();
    }

    private void checkConnectedDevice() {
        Log.e(TAG, "checkConnectedDevice:  ");
        if (mUSBDevice == null)
            return;
        stopScanerThread();
        scanerThreadExecutor = Executors.newSingleThreadExecutor();
        isStopScanerThread = false;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (deviceID == -1 && !isStopScanerThread) {
                    Log.d(TAG, "checkConnectedDevice: 初始化上传的usb未完成，deviceID =" + deviceID);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (isStopScanerThread)
                    return;
                Log.d(TAG, "checkConnectedDevice: 开始检查USB连接设备");

                if (usbManager == null)
                    usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
                if (usbManager == null) {
                    Log.e(TAG, "run: usbManager==null");
                    return;
                }
                if (mUSBDevice == null)
                    return;


                try {
                    for (int i = 0; i < mUSBDevice.getInterfaceCount(); i++) {
                        if (mUSBDevice == null)
                            return;
                        UsbInterface usbInterface = mUSBDevice.getInterface(i);
                        if (usbInterface == null)
                            return;
                        switch (usbInterface.getInterfaceClass()) {
                            case UsbConstants.USB_CLASS_STILL_IMAGE:
                                downloadFlieListener.startDownload();
                                mtpDeviceScaner(mUSBDevice);
                                downloadFlieListener.downloadComplete();
                                break;
                            case UsbConstants.USB_CLASS_MASS_STORAGE:
                                downloadFlieListener.startDownload();
                                usbDeviceScaner(mUSBDevice);
                                downloadFlieListener.downloadComplete();
                                break;
                            default:
                                break;
                        }

                    }
                } catch (Exception e) {
                }

            }
        };
        scanerThreadExecutor.execute(runnable);
    }


    private void mtpDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "开始扫描数码相机");
        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            Log.e(TAG, "数码相机打开失败 mtpDeviceScaner: usbDeviceConnection == null 结束扫描");
            return;
        }
        MtpDevice mtpDevice = new MtpDevice(usbDevice);
        if (mtpDevice == null || !mtpDevice.open(usbDeviceConnection)) {
            Log.e(TAG, "数码相机打开失败 mtpDevice == null || !mtpDevice.open(usbDeviceConnection) 结束扫描");
            return;
        }
        int[] storageIds = mtpDevice.getStorageIds();
        if (storageIds == null) {
            Log.e(TAG, "mtpDeviceScaner: 数码相机存储卷不可用 storageIds == null 结束扫描");
            return;
        }


        pictureInfos.clear();
        rowPictureInfos.clear();
        jpgPictureInfos.clear();
        for (int storageId : storageIds) {
            int[] pictureHandlesItem = mtpDevice.getObjectHandles(storageId, 0, 0);
            if (pictureHandlesItem != null)
                for (int i : pictureHandlesItem) {
                    MtpObjectInfo mtpObjectInfo = mtpDevice.getObjectInfo(i);

                    if (mtpObjectInfo == null) {
                        continue;
                    }
                    String pictureName = Utils.getyyMMddtring() + "-" + mtpObjectInfo.getName();
                    String FileEnd = pictureName.substring(pictureName.lastIndexOf(".") + 1).toLowerCase();

                    if (VariableInstance.getInstance().formarCamera) {
                        mtpDevice.deleteObject(i);
                        continue;
                    }


                    long createDate = mtpObjectInfo.getDateCreated();

                    if (!usbFileNameList.contains(pictureName)) {
                        if (rowFormatFile(FileEnd)) {
                            PictureInfo pictureInfo = new PictureInfo(true, pictureName, createDate, i, null, null, false);
                            rowPictureInfos.add(pictureInfo);
                        } else if (jPGFormatFile(FileEnd)) {
                            PictureInfo pictureInfo = new PictureInfo(true, pictureName, createDate, i, null, null, true);
                            jpgPictureInfos.add(pictureInfo);
                        }
                    }
//                    if (isOnTheSameDay(createDate)) {
//
//
//                    }
                }
        }

        Collections.sort(rowPictureInfos, new order());
        Collections.sort(jpgPictureInfos, new order());

        pictureInfos.addAll(rowPictureInfos);
        pictureInfos.addAll(jpgPictureInfos);


        Log.d(TAG, "readAllFileFromMTPDevice: 扫描到图片size =" + pictureInfos.size());
        downloadFlieListener.scanerSize(pictureInfos.size());

        saveMTPPictureToUploadUSB(mtpDevice);
        usbDeviceConnection.close();
        mtpDevice.close();
    }

    private void usbDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "开始扫描数码usb");
        if (usbDevice == null) {
            Log.e(TAG, "usbDeviceScaner: USB设备为空 结束扫描");
            return;
        }
        if (usbDevice.getDeviceId() == deviceID) {
            Log.e(TAG, "usbDeviceScaner 检测到当前的U盘设备为上传usb, 结束扫描");
            return;
        }
        UsbMassStorageDevice device = getUsbMass(usbDevice);
        if (device == null) {
            Log.e(TAG, "usbDeviceScaner: device == null 结束扫描");
            return;
        }
        try {
            device.init();
        } catch (Exception e) {
            Log.e(TAG, "usbDeviceScaner: 结束扫描 device.init() error:" + e);
            return;
        }

        if (device.getPartitions().size() <= 0) {
            Log.e(TAG, "usbDeviceScaner: " + "device.getPartitions().size() error 结束扫描");
            return;
        }
        Partition partition = device.getPartitions().get(0);
        FileSystem currentFs = partition.getFileSystem();
        UsbFile mRootFolder = currentFs.getRootDirectory();

        pictureInfos.clear();
        rowPictureInfos.clear();
        jpgPictureInfos.clear();
        readAllPicFileFromUSBDevice(currentFs, mRootFolder);
        Log.d(TAG, "usbDeviceScaner: 扫描到图片size =" + pictureInfos.size());
        Collections.sort(pictureInfos, new order());
        downloadFlieListener.scanerSize(pictureInfos.size());
        saveUSBFileToPhoneDevice();

    }


    private void saveMTPPictureToUploadUSB(MtpDevice mtpDevice) {
        Log.d(TAG, "readAllFileFromMTPDevice: readAllFileFromMTPDevice start ");
        if (mtpDevice == null) {
            return;
        }

        try {
            for (PictureInfo pictureItem : pictureInfos) {
                saveMTPPictureToUploadUSB(mtpDevice, pictureItem);
            }
        } catch (Exception e) {
        }
    }


    private void saveUSBFileToPhoneDevice() {
        for (PictureInfo pictureInfo : pictureInfos) {
            saveUSBFileToPhoneDevice(pictureInfo);
        }
    }

    private void saveUSBFileToPhoneDevice(PictureInfo pictureInfo) {
        boolean needDownload = false;
        boolean needUpload = false;
        if (VariableInstance.getInstance().UploadMode == 1) {
            needDownload = true;
            if (pictureInfo.isJpg)
                needUpload = false;
            else
                needUpload = true;
        } else if (VariableInstance.getInstance().UploadMode == 2) {
            needDownload = true;
            if (pictureInfo.isJpg)
                needUpload = true;
            else
                needUpload = false;
        } else if (VariableInstance.getInstance().UploadMode == 3) {
            needDownload = true;
            try {
                int index = rowPictureInfos.indexOf(pictureInfo);
                if (index > -1) {
                    Integer integer = index + 1;
                    index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        needUpload = true;
                    }
                }
            } catch (Exception e) {
            }
        } else if (VariableInstance.getInstance().UploadMode == 4) {
            try {
                int index = rowPictureInfos.indexOf(pictureInfo);
                if (index > -1) {
                    Integer integer = index + 1;
                    index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        needDownload = true;
                        needUpload = true;
                    }
                }

            } catch (Exception e) {
            }
        }
        Log.e(TAG, "saveMTPPictureToUploadUSB: needDownload =" + needDownload + ",needUpload =" + needUpload + ",pictureInfo=" + pictureInfo);
        if (!needDownload)
            return;

        //写入文件
        FileOutputStream os = null;
        FileOutputStream uploados = null;
        InputStream is = null;
        String pictureSaveLocalPath = null;
        String pictureSaveUploadLocalPath = null;
        File pictureSaveLocalFile = null;
        File pictureSaveUploadLocalFile = null;
        try {
            pictureSaveLocalPath = tfcardpicturedir + File.separator + pictureInfo.pictureName;
            pictureSaveUploadLocalPath = tfcarduploadpicturedir + File.separator + pictureInfo.pictureName;
            pictureSaveLocalFile = new File(pictureSaveLocalPath);

            if (pictureSaveLocalFile.exists())
                pictureSaveLocalFile.delete();


            Log.d(TAG, "saveUSBFileToPhoneDevice: savePicturePath =" + pictureSaveLocalPath);
            os = new FileOutputStream(pictureSaveLocalPath);

            if (needUpload) {
                pictureSaveUploadLocalFile = new File(pictureSaveUploadLocalPath);
                if (pictureSaveUploadLocalFile.exists())
                    pictureSaveUploadLocalFile.delete();
                uploados = new FileOutputStream(pictureSaveUploadLocalPath);
            }


            is = new UsbFileInputStream(pictureInfo.usbFile);

            int bytesRead = 0;
            byte[] buffer = new byte[pictureInfo.usbFileSystem.getChunkSize()];//作者的推荐写法是currentFs.getChunkSize()为buffer长度
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                if (needUpload)
                    uploados.write(buffer, 0, bytesRead);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "saveUSBFileToPhoneDevice: FileNotFoundException =" + e);
        } catch (IOException e) {
            Log.e(TAG, "saveUSBFileToPhoneDevice: IOException =" + e);
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (uploados != null) {
                    uploados.flush();
                    uploados.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "saveUSBFileToPhoneDevice:finally IOException =" + e);
            }
        }
        try {
            if (pictureSaveLocalFile != null && pictureSaveLocalFile.exists()) {
                boolean uploadSucceed = uploadToUSB(pictureSaveLocalFile);
                if (uploadSucceed) {
                    if (needUpload) {
                        if (pictureSaveUploadLocalFile != null && pictureSaveUploadLocalFile.exists())
                            downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
                    } else {
                        pictureSaveLocalFile.delete();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private boolean uploadToUSB(File localFile) {
        if (localFile == null || !localFile.exists()) {
            Log.e(TAG, "uploadToUSB: \ntodayDir =" + "\n localFile =" + localFile);
            return false;
        }

        if (uploadFileDirUsbFile == null) {
            Log.e(TAG, "uploadToUSB: uploadFileDirUsbFile == null");
            return false;
        }

        long time = System.currentTimeMillis();
        long fileSize = 0;
        Log.d(TAG, "uploadToUSB: localFile =" + localFile);
        UsbFileOutputStream os = null;
        InputStream is = null;
        try {
            UsbFile create = todayMonthUsbFile.createFile(localFile.getName());
            os = new UsbFileOutputStream(create);
            is = new FileInputStream(localFile);
            fileSize = is.available();
            int bytesRead;
            byte[] buffer = new byte[uploadFs.getChunkSize()];
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            VariableInstance.getInstance().downdNum++;
            String speed = fileSize / ((System.currentTimeMillis() - time) / 1000) / 1024 + "";
            Log.d(TAG, "uploadToUSB: 上传USB速度：speed =" + speed + ",fileSize =" + fileSize);
            downloadFlieListener.downloadNum(VariableInstance.getInstance().downdNum, speed);
        } catch (Exception e) {
            if (e.toString().contains("Item already exists")) {
                Log.d(TAG, "uploadToUSB: U盘已存在同名文件");
            } else
                Log.e(TAG, "uploadToUSB: Exception =" + e);

        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (Exception e) {
            }
        }

        return true;
    }


    private void readAllPicFileFromUSBDevice(FileSystem fileSystem, UsbFile usbFile) {
        try {
            UsbFile[] usbFileList = usbFile.listFiles();
            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.isDirectory()) {
                    readAllPicFileFromUSBDevice(fileSystem, usbFileItem);
                } else {
                    //获取文件后缀
                    String fileName = Utils.getyyMMddtring() + "-" + usbFileItem.getName();
                    String FileEnd = fileName.substring(usbFileItem.getName().lastIndexOf(".") + 1).toLowerCase();

                    if (VariableInstance.getInstance().formarCamera) {
                        if (pictureFormatFile(FileEnd))
                            usbFile.delete();
                        continue;
                    }


                    long createDate = usbFileItem.createdAt();
                    if (!usbFileNameList.contains(fileName)) {
                        if (rowFormatFile(FileEnd)) {
                            PictureInfo pictureInfo = new PictureInfo(false, fileName, createDate, 0, fileSystem, usbFileItem, false);
                            rowPictureInfos.add(pictureInfo);
                        } else if (jPGFormatFile(FileEnd)) {
                            PictureInfo pictureInfo = new PictureInfo(false, fileName, createDate, 0, fileSystem, usbFileItem, true);
                            jpgPictureInfos.add(pictureInfo);
                        }
                    }
//                    if (isOnTheSameDay(createDate)) {
//
//                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "readAllPicFileFromUSBDevice: 遍历USB文件异常");
        }
    }

    public long MILLIS_IN_DAY = 1000L * 60 * 60 * 24;
    private long currentDataMillis;

    private boolean isOnTheSameDay(long createDate) {
        createDate = createDate - 1000L * 60 * 60 * 8;
        return toDay(createDate) == toDay(currentDataMillis);
    }

    private long toDay(long millis) {
        return (millis / MILLIS_IN_DAY);
    }


    private Vector<String> usbFileNameList;

    private void getUploadDeviceUSBPictureCount(UsbFile usbFile) {
        try {
            UsbFile[] usbFileList = usbFile.listFiles();
            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.isDirectory()) {
                    getUploadDeviceUSBPictureCount(usbFileItem);
                } else {
                    String name = usbFileItem.getName();
                    String FileEnd = name.substring(usbFileItem.getName().lastIndexOf(".") + 1).toLowerCase();
                    if (pictureFormatFile(FileEnd)) {
                        usbFileNameList.add(name);
                    }
                }
            }
        } catch (Exception e) {

        }
    }


    private boolean pictureFormatFile(String FileEnd) {
        if ((FileEnd.equals("nif") || FileEnd.equals("crw") || FileEnd.equals("raw")
                || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw")
                || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2")) || (FileEnd.equals("jpg")))
            return true;
        return false;
    }

    private boolean rowFormatFile(String FileEnd) {
        if ((FileEnd.equals("nif") || FileEnd.equals("crw") || FileEnd.equals("raw")
                || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw")
                || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2")))
            return true;
        return false;
    }

    private boolean jPGFormatFile(String FileEnd) {
        if ((FileEnd.equals("jpg")))
            return true;
        return false;
    }


    private void saveMTPPictureToUploadUSB(MtpDevice mtpDevice, PictureInfo pictureInfo) {
        //每一个mtpObjectInfo 是一个图片对象 检索MtpObjectInfo对象
        try {


            boolean needDownload = false;
            boolean needUpload = false;
            if (VariableInstance.getInstance().UploadMode == 1) {
                needDownload = true;
                if (pictureInfo.isJpg)
                    needUpload = false;
                else
                    needUpload = true;
            } else if (VariableInstance.getInstance().UploadMode == 2) {
                needDownload = true;
                if (pictureInfo.isJpg)
                    needUpload = true;
                else
                    needUpload = false;
            } else if (VariableInstance.getInstance().UploadMode == 3) {
                needDownload = true;

                try {
                    int index = rowPictureInfos.indexOf(pictureInfo);
                    if (index > -1) {
                        Integer integer = index + 1;
                        index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                        if (index > -1) {
                            needUpload = true;
                        }
                    }

                } catch (Exception e) {
                }
            } else if (VariableInstance.getInstance().UploadMode == 4) {
                try {
                    int index = rowPictureInfos.indexOf(pictureInfo);
                    if (index > -1) {
                        Integer integer = index + 1;
                        index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                        if (index > -1) {
                            needDownload = true;
                            needUpload = true;
                        }
                    }

                } catch (Exception e) {
                }
            }

            Log.e(TAG, "saveMTPPictureToUploadUSB: needDownload =" + needDownload + ",needUpload =" + needUpload + ",pictureInfo =" + pictureInfo);
            if (!needDownload)
                return;

            String pictureSaveLocalPath = tfcardpicturedir + File.separator + pictureInfo.pictureName;
            String pictureSaveUploadLocalPath = tfcarduploadpicturedir + File.separator + pictureInfo.pictureName;

            File pictureTpmSaveFile = new File(pictureSaveLocalPath);
            if (pictureTpmSaveFile != null && pictureTpmSaveFile.exists())
                pictureTpmSaveFile.delete();

            mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveLocalPath);

            File pictureTpmUploadSaveFile = null;
            if (needUpload) {
                pictureTpmUploadSaveFile = new File(pictureSaveUploadLocalPath);
                if (pictureTpmUploadSaveFile != null && pictureTpmUploadSaveFile.exists())
                    pictureTpmUploadSaveFile.delete();

                mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveUploadLocalPath);
            }

            if (pictureTpmSaveFile != null && pictureTpmSaveFile.exists()) {
                boolean uploadSucceed = uploadToUSB(pictureTpmSaveFile);
                if (uploadSucceed) {
                    if (needUpload) {
                        if (pictureTpmUploadSaveFile != null && pictureTpmUploadSaveFile.exists())
                            downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
                    } else {
                        pictureTpmSaveFile.delete();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "readAllFileFromMTPDevice: 图片复制出错");
        }
    }


    /**
     * USBDevice 转换成UsbMassStorageDevice 对象
     */
    private UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(MyApplication.getContext());

        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                return device;
            }
        }
        return null;
    }


    class PictureInfo {
        public boolean mtpModel;
        public String pictureName;
        public long pictureCreateData;

        public int mtpPictureID;

        public UsbFile usbFile;
        public FileSystem usbFileSystem;
        public boolean isJpg;


        public PictureInfo(boolean mtpModel, String pictureName, long pictureCreateData, int mtpPictureID, FileSystem usbFileSystem, UsbFile usbFile, boolean isJpg) {
            this.mtpModel = mtpModel;
            this.pictureName = pictureName;
            this.pictureCreateData = pictureCreateData;
            this.mtpPictureID = mtpPictureID;
            this.usbFile = usbFile;
            this.usbFileSystem = usbFileSystem;
            this.isJpg = isJpg;
        }

        @Override
        public String toString() {
            return pictureName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PictureInfo)) return false;
            PictureInfo that = (PictureInfo) o;
            return isJpg == that.isJpg && Objects.equals(pictureName, that.pictureName);
        }


    }


    public class order implements Comparator<PictureInfo> {

        @Override
        public int compare(PictureInfo lhs, PictureInfo rhs) {
            if (rhs.pictureCreateData > lhs.pictureCreateData)
                return -1;
            else if (rhs.pictureCreateData == lhs.pictureCreateData)
                return 0;
            return 1;
        }

    }

    public interface DownloadFlieListener {
        void addUploadRemoteFile(UploadFileModel uploadFileModel);

        void startDownload();

        void downloadComplete();

        void initUploadUSBComplete(int pictureCount);

        void usbIntError(String message);

        void scanerSize(int size);

        void downloadNum(int num, String speed);

        void usbFileScanrFinishi(int num);
    }


}

